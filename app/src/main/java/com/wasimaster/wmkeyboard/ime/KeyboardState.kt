package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariantIndex
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.Layouts
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.ToolPrefill
import com.wasimaster.wmkeyboard.core.tools.TypedWord
import com.wasimaster.wmkeyboard.core.tools.TypingResult
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import com.wasimaster.wmkeyboard.core.tools.WeatherInfo
import com.wasimaster.wmkeyboard.core.tools.WpmSample

enum class ShiftState { OFF, ON, CAPS_LOCK }

enum class LayoutMode { LETTERS, SYMBOLS, SYMBOLS_SHIFTED }

/**
 * The layouts reachable from the focused field without a new `onStartInput`:
 * the layers the ?123 / ABC keys cycle between, plus the numeric pad when the
 * field has one.
 *
 * Resolved by the service — it owns the layout store — and handed over already
 * compiled, so the rendering code never touches storage and [keyRowsHeight] can
 * stay a pure function of state. The service caches instances by resolution key
 * rather than rebuilding per emission: [KeyboardUiState]'s generated `equals`
 * walks its fields, and a stable instance keeps that comparison cheap instead of
 * walking every key on every recomposition.
 */
data class LayoutSet(
    val letters: KeyboardLayout,
    val symbols: KeyboardLayout,
    val symbolsShifted: KeyboardLayout,
    /** Keypad for the focused field kind; null for TEXT/EMAIL/URI. */
    val numeric: KeyboardLayout? = null,
) {
    /**
     * Rows the key grid reserves.
     *
     * The tallest reachable layer wins, so pressing ?123 never resizes the
     * keyboard under the user's fingers; the shorter layers pad instead. Sizing
     * each layer to its own row count was the alternative and was rejected for
     * exactly that reason — the layer key is pressed mid-word, constantly, and a
     * resize there reflows the whole host app.
     *
     * Not a constructor property, so it stays out of equals/hashCode/copy.
     */
    val rowSpan: Int = maxOf(
        letters.rows.size,
        symbols.rows.size,
        symbolsShifted.rows.size,
        numeric?.rows?.size ?: 0,
    ).coerceAtLeast(1)

    /**
     * Whether every a-z key is present on the letter layer. The gesture decoder
     * scores candidates against key centres, so a layout missing letters would
     * produce confident nonsense rather than nothing.
     */
    val lettersHaveFullAlphabet: Boolean = ('a'..'z').all { char ->
        letters.rows.any { row ->
            row.any {
                it.action == KeyAction.Text &&
                    (it.output ?: it.label).singleOrNull()?.lowercaseChar() == char
            }
        }
    }

    companion object {
        /** The shipped grids, for a state built before the first resolution. */
        val Default = LayoutSet(Layouts.QWERTY, Layouts.SYMBOLS, Layouts.SYMBOLS_SHIFTED)
    }
}

/**
 * What kind of field is focused, from EditorInfo.inputType. The numeric
 * kinds lock the keyboard to a purpose-built keypad; EMAIL/URI keep the
 * letter layouts but adapt the bottom row (@ or / key, .com long-press).
 */
enum class FieldKind { TEXT, EMAIL, URI, NUMBER, PHONE, DATE, TIME, DATETIME }

/** Kinds rendered as a 4-column keypad instead of the letter layouts. */
val FieldKind.isNumericPad: Boolean
    get() = this == FieldKind.NUMBER || this == FieldKind.PHONE ||
        this == FieldKind.DATE || this == FieldKind.TIME || this == FieldKind.DATETIME

/**
 * The layer a numeric field kind types on, or null for the kinds that keep the
 * letter layouts (TEXT, EMAIL, URI). The mapping lives here rather than in
 * `core/layout` because [FieldKind] is an IME concept — the layout package has
 * no business knowing what an EditorInfo is.
 */
val FieldKind.numericLayer: LayoutLayer?
    get() = when (this) {
        FieldKind.NUMBER -> LayoutLayer.NUMBER
        FieldKind.PHONE -> LayoutLayer.PHONE
        FieldKind.DATE -> LayoutLayer.DATE
        FieldKind.TIME -> LayoutLayer.TIME
        FieldKind.DATETIME -> LayoutLayer.DATETIME
        FieldKind.TEXT, FieldKind.EMAIL, FieldKind.URI -> null
    }

enum class PanelMode {
    NONE, EMOJI, CLIPBOARD, SNIPPETS, TOOLBOX, TEXT_EDIT,
    COMPASS, LEVEL, MOON_PHASE, WEATHER, CALENDAR,
    THEMES, SOUND_HAPTICS, NUMPAD, HANDWRITING, CAMERA, DICTIONARY,
    TRANSLATE, GIF, STICKER, WEB_SEARCH, IMAGE_SEARCH,
    OCR, QR_SCAN, VOICE, GRAMMAR,
    WIKIPEDIA, SYMBOLS, CALCULATOR, UNIT_CONVERT, CURRENCY, QR_GEN, PASSWORD_GEN, AI,
    MODES, TYPING_TEST,
}

/**
 * Panels whose search box types into [KeyboardUiState.mediaQuery] (the same
 * key-rerouting trick as emoji search) instead of the editor.
 */
val PanelMode.hasMediaSearch: Boolean
    get() = this == PanelMode.GIF || this == PanelMode.STICKER ||
        this == PanelMode.WEB_SEARCH || this == PanelMode.IMAGE_SEARCH ||
        this == PanelMode.WIKIPEDIA || this == PanelMode.TRANSLATE

/** Readiness of the handwriting panel's recognition model. */
enum class HandwritingStatus { CHECKING, NEED_MODEL, DOWNLOADING, READY, ERROR }

/**
 * Handwriting panel state, owned by the service (it runs recognition).
 * Completed strokes live here so the service can rebuild the ink and clear
 * the canvas after a commit; the stroke being drawn stays local to the
 * panel and is appended through the stroke-finished callback.
 */
data class HandwritingUi(
    val status: HandwritingStatus = HandwritingStatus.CHECKING,
    /** BCP-47 tag of the active recognition model (en-US, bn). */
    val languageTag: String = "en-US",
    val strokes: List<com.wasimaster.wmkeyboard.core.handwriting.HwStroke> = emptyList(),
    /** Recognition in flight — strokes are frozen on screen until it lands. */
    val recognizing: Boolean = false,
    val errorMessage: String? = null,
)

/** Where the voice input panel is in a dictation session. */
enum class VoiceStatus { IDLE, LISTENING, FINISHING, NEED_PERMISSION, UNAVAILABLE, ERROR }

/**
 * On-device recognition model availability for the active language
 * (API 33+). UNKNOWN doubles as "not applicable" — older Android, no
 * on-device recognizer, or the language isn't supported on-device at all.
 */
enum class VoiceModelState { UNKNOWN, INSTALLED, DOWNLOADABLE, DOWNLOADING }

/**
 * Voice input state, owned by the service (it runs the recognizer). The
 * utterance in progress lives in the editor as composing text; [partial]
 * mirrors it for the panel's status line.
 */
data class VoiceUi(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val partial: String = "",
    /** Mic level 0..1, quantized by the service; drives the pulse ring. */
    val level: Float = 0f,
    /** BCP-47 tag sent to the recognizer (en-US, bn-BD). */
    val languageTag: String = "en-US",
    val errorMessage: String? = null,
    /** Dictation runs in the compact bar over the keys instead of the panel. */
    val strip: Boolean = false,
    /** A just-dictated utterance is still at the cursor; the undo chip shows. */
    val canUndo: Boolean = false,
    /** Offline-model chip on the panel (download for offline dictation). */
    val modelState: VoiceModelState = VoiceModelState.UNKNOWN,
    /** Download percent while [modelState] is DOWNLOADING, -1 when unknown. */
    val modelProgress: Int = -1,
)

/** One change made from the password generator panel (all persisted). */
sealed interface PwSettingAction {
    data class PassphraseMode(val on: Boolean) : PwSettingAction
    data class Length(val value: Int) : PwSettingAction
    data class Upper(val on: Boolean) : PwSettingAction
    data class Digits(val on: Boolean) : PwSettingAction
    data class Symbols(val on: Boolean) : PwSettingAction
    data class ExcludeAmbiguous(val on: Boolean) : PwSettingAction
    data class Words(val value: Int) : PwSettingAction
    data class Separator(val value: String) : PwSettingAction
    data class Capitalize(val on: Boolean) : PwSettingAction
    data class IncludeDigit(val on: Boolean) : PwSettingAction
}

/** One change made from the sound & haptics quick panel. */
sealed interface SoundHapticAction {
    data class Haptics(val on: Boolean) : SoundHapticAction
    data class HapticStyleChange(val style: com.wasimaster.wmkeyboard.core.settings.HapticStyle) : SoundHapticAction
    data class HapticAmplitude(val amplitude: Int) : SoundHapticAction
    data class Sound(val on: Boolean) : SoundHapticAction
    data class SoundStyleChange(val style: com.wasimaster.wmkeyboard.core.settings.KeySoundStyle) : SoundHapticAction
    data class SoundVolume(val volume: Float) : SoundHapticAction
}

/** GIF / sticker panel state, owned by the service (it does the fetching). */
sealed interface MediaUi {
    /** No GIF-provider key in the build and none pasted into settings. */
    data object NeedKey : MediaUi
    data object Loading : MediaUi
    data class Error(val message: String) : MediaUi
    /** [query] is what produced [items]; blank means featured/trending. */
    data class Ready(
        val items: List<com.wasimaster.wmkeyboard.core.tools.GifItem>,
        val query: String,
    ) : MediaUi
}

/** Web search panel state. */
sealed interface WebSearchUi {
    data object NeedKey : WebSearchUi
    /** Key present, nothing searched yet. */
    data object Idle : WebSearchUi
    data object Loading : WebSearchUi
    data class Error(val message: String) : WebSearchUi
    data class Ready(
        val results: List<com.wasimaster.wmkeyboard.core.tools.WebResult>,
        val query: String,
    ) : WebSearchUi
}

/** Image search panel state. */
sealed interface ImageSearchUi {
    data object NeedKey : ImageSearchUi
    data object Idle : ImageSearchUi
    data object Loading : ImageSearchUi
    data class Error(val message: String) : ImageSearchUi
    data class Ready(
        val results: List<com.wasimaster.wmkeyboard.core.tools.ImageResult>,
        val query: String,
    ) : ImageSearchUi
}

/**
 * Translate panel state. The panel is its own little window: what gets
 * translated is the query typed into it ([KeyboardUiState.mediaQuery], the
 * same key-rerouting trick as the media panels) — the focused field is
 * never read.
 */
data class TranslateUi(
    /** The typed query the current [translated] corresponds to. */
    val sourceText: String = "",
    val translated: String = "",
    /** ISO code of the auto-detected source language, "" while unknown. */
    val detectedSource: String = "",
    val translating: Boolean = false,
    val error: String? = null,
)

/**
 * Grammar strip state. Like translate, the strip follows the focused field:
 * the service re-extracts and re-lints (debounced) on every change while the
 * panel is open. All linting is offline via the bundled Harper engine.
 */
data class GrammarUi(
    /** Field text the current [lints] correspond to. */
    val sourceText: String = "",
    val lints: List<com.wasimaster.wmkeyboard.core.grammar.GrammarLint> = emptyList(),
    val checking: Boolean = false,
    /** False until the first check of the current field completes. */
    val checkedOnce: Boolean = false,
    /** Native Harper library present in this build. */
    val available: Boolean = true,
)

/** Wikipedia panel state, owned by the service (it does the fetching). */
sealed interface WikiUi {
    /** Nothing searched yet — the panel opens into its search box. */
    data object Idle : WikiUi
    data object Loading : WikiUi
    data class Error(val message: String) : WikiUi
    data class SearchResults(
        val results: List<com.wasimaster.wmkeyboard.core.tools.WikipediaClient.SearchResult>,
        val query: String,
    ) : WikiUi
    /**
     * One article: summary always present; [links] and [fullText] load
     * lazily when their tab is first opened. [canGoBack] returns to the
     * search results this article came from.
     */
    data class Article(
        val summary: com.wasimaster.wmkeyboard.core.tools.WikipediaClient.Summary,
        val links: List<String>? = null,
        val fullText: String? = null,
        val loadingExtra: Boolean = false,
        val canGoBack: Boolean = false,
    ) : WikiUi
}

/** Currency converter state; rates are cross-computed from one USD table. */
sealed interface CurrencyUi {
    data object Loading : CurrencyUi
    data class Error(val message: String) : CurrencyUi
    data class Ready(
        val rates: com.wasimaster.wmkeyboard.core.tools.CurrencyClient.Rates,
        val fetchedAtMs: Long,
    ) : CurrencyUi
}

/** AI tool state, owned by the service (it makes the provider request). */
sealed interface AiUi {
    /** Selected provider has no key/URL configured yet. */
    data object NeedSetup : AiUi
    /** On-device provider selected but no model downloaded/selected. */
    data object NeedModel : AiUi
    data object Idle : AiUi
    data class Loading(
        val action: com.wasimaster.wmkeyboard.core.settings.AiAction,
        /** A reasoning model is inside a `<think>` block (hidden by default). */
        val thinking: Boolean = false,
    ) : AiUi
    data class Ready(
        val action: com.wasimaster.wmkeyboard.core.settings.AiAction,
        val result: String,
        /** Text the action ran on, for the retry button. */
        val sourceText: String,
        /** True while an on-device response is still streaming in. */
        val generating: Boolean = false,
        /**
         * Strip markdown syntax before showing/committing the result. Only
         * surfaced as a checkbox when the result actually contains markdown.
         */
        val stripMarkdown: Boolean = true,
    ) : AiUi
    data class Error(
        val action: com.wasimaster.wmkeyboard.core.settings.AiAction,
        val message: String,
    ) : AiUi
}

/** Weather panel state, owned by the service (it does the fetching). */
sealed interface WeatherUi {
    /** No location configured in the weather tool's settings. */
    data object NoLocation : WeatherUi
    data object Loading : WeatherUi
    data object Error : WeatherUi
    data class Ready(val info: WeatherInfo) : WeatherUi
}

/** Dictionary panel state, owned by the service (it does the fetching). */
sealed interface DictionaryUi {
    /** Nothing looked up yet (no word at the cursor, or auto-lookup off). */
    data object Idle : DictionaryUi
    data class Loading(val word: String) : DictionaryUi
    data class Error(val word: String) : DictionaryUi
    /** The API knows no entry for this word. */
    data class NotFound(val word: String) : DictionaryUi
    data class Ready(val entries: List<com.wasimaster.wmkeyboard.core.tools.DictEntry>) : DictionaryUi
}

/** One button on the text-editing panel (cursor control, selection, clipboard). */
enum class TextEditAction {
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN,
    SELECT, SELECT_ALL, COPY, PASTE, BACKSPACE,
}

/**
 * What the enter key does in the focused field, from EditorInfo.imeOptions.
 *
 * [CUSTOM] is the app supplying its own [android.view.inputmethod.EditorInfo.actionLabel]
 * — the key draws that label as text and fires the app's
 * [android.view.inputmethod.EditorInfo.actionId] rather than a standard action.
 */
enum class EnterAction { DEFAULT, SEARCH, SEND, GO, NEXT, PREVIOUS, DONE, CUSTOM }

/**
 * Typing-speed panel state. The prompt and the typed text live here rather
 * than in the panel so the service can run the clock and the per-second
 * sampler against them; the panel is a pure rendering of this.
 *
 * The run has three phases, told apart without a separate enum: [result]
 * non-null means finished, otherwise [startedAtMs] non-null means running,
 * otherwise the test is armed and waiting for the first keystroke.
 */
data class TypingTestUi(
    val words: List<String> = emptyList(),
    /** Finished words, in prompt order — what the user actually typed for each. */
    val typedWords: List<TypedWord> = emptyList(),
    /** The word being typed right now (the caret is at its end). */
    val current: String = "",
    /** Elapsed-time clock start; null until the first keystroke arms it. */
    val startedAtMs: Long? = null,
    /** Milliseconds elapsed, refreshed by the service's ticker. */
    val elapsedMs: Long = 0,
    /** Every character key pressed, corrections included. */
    val totalKeystrokes: Int = 0,
    /** …of which landed on the right letter first time. */
    val correctKeystrokes: Int = 0,
    val samples: List<WpmSample> = emptyList(),
    /** Non-null once the run is over; the panel switches to the results view. */
    val result: TypingResult? = null,
    /** Set when the finished run beat the stored best for its config. */
    val personalBest: Boolean = false,
) {
    val running: Boolean get() = startedAtMs != null && result == null
    /** Index of the word the caret is in. */
    val wordIndex: Int get() = typedWords.size
}

/** One control on the typing-speed panel. */
sealed interface TypingTestAction {
    /** Throw the run away and deal a fresh prompt with the same settings. */
    data object Restart : TypingTestAction
    data class Mode(val value: TypingTestMode) : TypingTestAction
    data class Duration(val seconds: Int) : TypingTestAction
    data class WordCount(val value: Int) : TypingTestAction
    data class Punctuation(val on: Boolean) : TypingTestAction
    data class Numbers(val on: Boolean) : TypingTestAction
    /** Write the finished run's score into the field the user came from. */
    data object InsertResult : TypingTestAction
}

/**
 * Immutable UI state rendered by the Compose keyboard. The service owns a
 * MutableStateFlow of this and mutates it via copy().
 */
data class KeyboardUiState(
    val settings: KeyboardSettings = KeyboardSettings(),
    val inputMode: InputMode = InputMode.ENGLISH,
    /** The layout being typed on; what the 🌐 cycle steps through. */
    val layoutId: String = BuiltInLayouts.DEFAULT_ID,
    /**
     * The layout's display name, for the spacebar label. Carried on the state
     * rather than looked up, because a custom layout has no [InputMode] of its
     * own to name it by — two layouts can share a base mode and still deserve
     * different labels.
     */
    val layoutName: String = "",
    /**
     * Key grids for the focused field, resolved from the user's layout choice.
     * Defaults to the shipped grids so a state built before the first resolution
     * — unit tests, the very first frame — still renders a working keyboard.
     */
    val layouts: LayoutSet = LayoutSet.Default,
    val layoutMode: LayoutMode = LayoutMode.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val panel: PanelMode = PanelMode.NONE,
    val suggestions: List<String> = emptyList(),
    /** Spacing form of the dead-key accent waiting for a letter, if any. */
    val pendingDeadKey: String? = null,
    /**
     * Chips from the system autofill service, already inflated by *its*
     * process — the keyboard only hosts them. Views rather than data because
     * that is the whole point: their contents are never exposed to the IME.
     */
    val inlineSuggestions: List<android.view.View> = emptyList(),
    /** Best gesture-typing candidate mid-swipe, shown floating above the finger. */
    val glideWord: String? = null,
    val composingPreview: String = "",
    /** Text-edit panel: arrows extend the selection instead of moving the cursor. */
    val textEditSelecting: Boolean = false,
    val emojiQuery: String = "",
    val emojiSearchActive: Boolean = false,
    val emojiResults: List<EmojiEntry> = emptyList(),
    val emojiRecents: List<String> = emptyList(),
    val emojiFrequents: List<String> = emptyList(),
    val emojiFavourites: List<String> = emptyList(),
    /** Palette-grid base emoji → the skin-tone/gender variant it renders as. */
    val emojiVariantPrefs: Map<String, String> = emptyMap(),
    /** Emoji candidates for the suggestion strip (word being typed). */
    val emojiSuggestions: List<String> = emptyList(),
    /**
     * The tool answer recognised in the text before the cursor — an inline
     * sum, currency or unit conversion, or a tool keyword. Rendered as a
     * chip over the suggestion strip.
     */
    val smart: SmartSuggest.SmartHit? = null,
    /** Input a chip loaded into the tool it is about to open; consumed once. */
    val toolPrefill: ToolPrefill? = null,
    val emojiCatalog: List<EmojiEntry> = emptyList(),
    /** RGI toned-sequence lookup; loaded once with the catalog. */
    val emojiVariants: EmojiVariantIndex = EmojiVariantIndex.empty(),
    val clipboardItems: List<ClipItem> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val secureField: Boolean = false,
    /** Field class/variation of the focused editor, from EditorInfo. */
    val fieldKind: FieldKind = FieldKind.TEXT,
    /**
     * The field asked for no IME intelligence (TYPE_TEXT_FLAG_NO_SUGGESTIONS,
     * or an email/URI/filter/password variation): suggestions, autocorrect,
     * gesture typing and lexicon learning all stay off.
     */
    val fieldNoSuggestions: Boolean = false,
    /**
     * The focused editor asked not to be learned from
     * (IME_FLAG_NO_PERSONALIZED_LEARNING), which is what a Chrome incognito
     * tab and most private-browsing modes set. Session-scoped: it follows the
     * field, never the persisted [KeyboardSettings.incognito] switch.
     */
    val fieldIncognito: Boolean = false,
    /**
     * Id of the keyboard mode currently applied to [settings] (per-app /
     * per-field overrides), null when no mode matched. The Modes panel
     * highlights it; the toolbar's mode tool lights up while set.
     */
    val activeModeId: String? = null,
    /**
     * Symbol set picked from the row's chip this session, overriding the
     * (possibly mode-supplied) persisted choice; cleared on field switch.
     */
    val activeSymbolSetId: String? = null,
    val enterAction: EnterAction = EnterAction.DEFAULT,
    /**
     * The app's own label for the enter key (EditorInfo.actionLabel), set
     * only when [enterAction] is [EnterAction.CUSTOM]. Drawn as text on the
     * key in place of an action icon.
     */
    val enterActionLabel: String? = null,
    /** Torch state, mirrored from CameraManager for the flashlight tool. */
    val torchOn: Boolean = false,
    val weather: WeatherUi = WeatherUi.NoLocation,
    val dictionary: DictionaryUi = DictionaryUi.Idle,
    /** Word in the dictionary panel's search field. */
    val dictionaryQuery: String = "",
    /** Typing edits [dictionaryQuery] (letters show under the panel). */
    val dictionarySearchActive: Boolean = false,
    /**
     * What the vowel keys should produce given the character before the
     * cursor: kar (া, ি …) after a consonant, the য়-glide (য়া, য়ে) after
     * a vowel, and the independent letter (আ, ই …) at a word start. Fixed
     * Bengali layouts use it for both the committed output and the
     * displayed key labels.
     */
    val vowelForm: BengaliGraphemes.VowelKeyForm = BengaliGraphemes.VowelKeyForm.INDEPENDENT,
    val handwriting: HandwritingUi = HandwritingUi(),
    val voice: VoiceUi = VoiceUi(),
    /** Query buffer for the GIF/sticker/web/image search panels. */
    val mediaQuery: String = "",
    /** While true, key presses type into [mediaQuery] and the key rows stay visible. */
    val mediaSearchActive: Boolean = false,
    /** Id of the GIF/sticker/image currently downloading for insert, for a cell spinner. */
    val mediaDownloadingId: String? = null,
    /** Selected provider chip on the GIF/sticker panel (tabs mode). */
    val mediaSource: com.wasimaster.wmkeyboard.core.tools.GifSource =
        com.wasimaster.wmkeyboard.core.tools.GifSource.KLIPY,
    val gif: MediaUi = MediaUi.Loading,
    val sticker: MediaUi = MediaUi.Loading,
    val webSearch: WebSearchUi = WebSearchUi.Idle,
    val imageSearch: ImageSearchUi = ImageSearchUi.Idle,
    val translate: TranslateUi = TranslateUi(),
    val grammar: GrammarUi = GrammarUi(),
    val wiki: WikiUi = WikiUi.Idle,
    val currency: CurrencyUi = CurrencyUi.Loading,
    val ai: AiUi = AiUi.Idle,
    /** Focused field's text, mirrored for the QR-generator panel. */
    val qrText: String = "",
    val typingTest: TypingTestUi = TypingTestUi(),
) {
    /**
     * Whether incognito is in force right now, from either source: the
     * persisted switch the user flips, or a field that asked for it. Every
     * gate and indicator reads this rather than the setting alone.
     */
    val incognitoOn: Boolean get() = settings.incognito || fieldIncognito

    /**
     * Whether keystrokes belong to a typing test rather than to the text
     * field. True from the moment the panel opens — the run is armed before
     * the first letter — and false again once the result is up, so the
     * results screen leaves the keyboard usable.
     */
    val typingTestActive: Boolean
        get() = panel == PanelMode.TYPING_TEST && typingTest.result == null
}
