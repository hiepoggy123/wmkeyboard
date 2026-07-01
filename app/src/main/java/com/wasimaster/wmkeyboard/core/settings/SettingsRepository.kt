package com.wasimaster.wmkeyboard.core.settings

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LayoutCodec
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.repair
import com.wasimaster.wmkeyboard.core.layout.resolveLayoutSelection
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.layout.script
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.ScriptDef
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.SymbolSet
import com.wasimaster.wmkeyboard.core.tools.SymbolSetCodec
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Visual theme for the keyboard and settings app. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Shrinks the keyboard toward one edge for thumb reach. */
enum class OneHandedMode { OFF, LEFT, RIGHT }

/** Where a width-reduced keyboard sits horizontally. */
enum class KeyboardAlignment { LEFT, CENTER, RIGHT }

/**
 * How media is offered to the target app through commitContent.
 *
 * There is no "sticker" flag in the platform API — the only signal is the
 * MIME type, and the receiving app decides. [STICKER] means "prefer a
 * sticker MIME where the field advertises one"; apps that don't get a
 * normal image instead. See [MediaMime].
 */
enum class MediaSendMode { IMAGE, STICKER }

/**
 * A tool that can live on the top toolbar. Tools not in
 * [KeyboardSettings.toolbarTools] wait in the toolbox panel; the user drags
 * them between the two while the toolbox is open (Gboard style). Tools not
 * in [KeyboardSettings.enabledTools] are hidden everywhere.
 */
enum class ToolbarTool {
    EMOJI, CLIPBOARD, SNIPPETS, TEXT_EDIT, ONE_HANDED, SPLIT, FLOATING, SETTINGS,
    FLASHLIGHT, COMPASS, LEVEL, UNDO, REDO, MOON_PHASE, WEATHER, CALENDAR,
    INCOGNITO, THEMES, AUTOCORRECT, SOUND_HAPTICS, NUMPAD, HANDWRITING, CAMERA,
    DICTIONARY, TRANSLATE, GIF, STICKER, WEB_SEARCH, IMAGE_SEARCH,
    OCR, QR_SCAN, DOC_SCAN, VOICE, GRAMMAR,
    WIKIPEDIA, SYMBOLS, CALCULATOR, UNIT_CONVERT, CURRENCY, QR_GEN, PASSWORD_GEN, AI,
    MODES, TYPING_TEST,
    // One-tap cursor moves. The text-edit panel already offers these, but on
    // the toolbar they cost a single tap instead of opening a panel first.
    CURSOR_LEFT, CURSOR_RIGHT, CURSOR_UP, CURSOR_DOWN,
    CURSOR_HOME, CURSOR_END, PAGE_UP, PAGE_DOWN,
}

/** The cursor tools, in the order they read on the toolbar. */
val CursorTools: List<ToolbarTool> = listOf(
    ToolbarTool.CURSOR_LEFT, ToolbarTool.CURSOR_RIGHT,
    ToolbarTool.CURSOR_UP, ToolbarTool.CURSOR_DOWN,
    ToolbarTool.CURSOR_HOME, ToolbarTool.CURSOR_END,
    ToolbarTool.PAGE_UP, ToolbarTool.PAGE_DOWN,
)

fun isSupportedTool(tool: ToolbarTool): Boolean = when {
    !BuildConfig.ENABLE_ML_KIT_HANDWRITING && tool == ToolbarTool.HANDWRITING -> false
    !BuildConfig.ENABLE_ML_KIT_SCANNERS && tool in setOf(
        ToolbarTool.OCR, ToolbarTool.QR_SCAN, ToolbarTool.DOC_SCAN
    ) -> false
    !BuildConfig.ENABLE_GRAMMAR && tool == ToolbarTool.GRAMMAR -> false
    else -> true
}

/**
 * Toolbox order until the user rearranges it: what most people reach for
 * most often comes first (expressive media, then everyday text helpers,
 * then keyboard tweaks, then the specialty and novelty tools). A tool
 * missing from the ranked list still shows — appended at the end — so
 * forgetting to rank a new tool is cosmetic, never a disappearance.
 */
private val RankedToolOrder: List<ToolbarTool> = listOf(
    ToolbarTool.EMOJI, ToolbarTool.GIF, ToolbarTool.STICKER, ToolbarTool.CLIPBOARD,
    ToolbarTool.VOICE, ToolbarTool.TRANSLATE, ToolbarTool.SNIPPETS, ToolbarTool.TEXT_EDIT,
    ToolbarTool.UNDO, ToolbarTool.REDO,
    ToolbarTool.SETTINGS, ToolbarTool.THEMES,
    ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH, ToolbarTool.DICTIONARY, ToolbarTool.CALCULATOR,
    ToolbarTool.AI, ToolbarTool.GRAMMAR, ToolbarTool.TYPING_TEST, ToolbarTool.NUMPAD, ToolbarTool.ONE_HANDED,
    ToolbarTool.SPLIT, ToolbarTool.FLOATING, ToolbarTool.INCOGNITO, ToolbarTool.AUTOCORRECT,
    ToolbarTool.SOUND_HAPTICS, ToolbarTool.MODES, ToolbarTool.OCR, ToolbarTool.QR_SCAN,
    ToolbarTool.QR_GEN, ToolbarTool.DOC_SCAN, ToolbarTool.CAMERA, ToolbarTool.HANDWRITING,
    ToolbarTool.SYMBOLS, ToolbarTool.UNIT_CONVERT, ToolbarTool.CURRENCY, ToolbarTool.PASSWORD_GEN,
    ToolbarTool.WIKIPEDIA, ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.FLASHLIGHT,
    ToolbarTool.COMPASS, ToolbarTool.LEVEL, ToolbarTool.MOON_PHASE,
    // Eight one-tap cursor moves last: useful, but they would otherwise push
    // every other tool a full row down in the toolbox.
    ToolbarTool.CURSOR_LEFT, ToolbarTool.CURSOR_RIGHT, ToolbarTool.CURSOR_UP, ToolbarTool.CURSOR_DOWN,
    ToolbarTool.CURSOR_HOME, ToolbarTool.CURSOR_END, ToolbarTool.PAGE_UP, ToolbarTool.PAGE_DOWN,
)

val DefaultToolOrder: List<ToolbarTool> =
    RankedToolOrder + (ToolbarTool.entries - RankedToolOrder.toSet())

/**
 * The tools pinned to the toolbar out of the box, and what "Reset pinned
 * tools" restores — global or, when a mode owns the tool order, for that mode.
 */
val DefaultToolbarTools: List<ToolbarTool> =
    listOf(ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS, ToolbarTool.SETTINGS)

/**
 * The onboarding wizard's starting selection: the everyday tools most people
 * actually use, leaving the specialty ones (sensors, scanners, generators)
 * off until asked for. Only a default — the wizard page and the Tools
 * settings both toggle from here.
 */
val RecommendedTools: Set<ToolbarTool> = setOf(
    ToolbarTool.EMOJI, ToolbarTool.GIF, ToolbarTool.STICKER, ToolbarTool.CLIPBOARD,
    ToolbarTool.VOICE, ToolbarTool.TRANSLATE, ToolbarTool.SNIPPETS, ToolbarTool.TEXT_EDIT,
    ToolbarTool.UNDO, ToolbarTool.REDO, ToolbarTool.SETTINGS, ToolbarTool.THEMES,
    ToolbarTool.WEB_SEARCH, ToolbarTool.DICTIONARY, ToolbarTool.CALCULATOR, ToolbarTool.ONE_HANDED,
)

/**
 * Backend for the AI tool — cloud APIs (bring your own key), a self-hosted
 * server, or a model running entirely on this device (full builds only).
 */
enum class AiProvider(val label: String) {
    ANTHROPIC("Claude"), OPENAI("OpenAI"), GEMINI("Gemini"),
    OLLAMA("Ollama"), LM_STUDIO("LM Studio"), ON_DEVICE("On-device"),
}

/**
 * Compute backend for on-device AI models. GPU is best-effort: the engine
 * falls back to CPU when GPU initialization fails on this device.
 */
enum class LocalLlmBackend(val label: String) { CPU("CPU"), GPU("GPU") }

/** One-tap writing actions on the AI tool's panel. */
enum class AiAction(val label: String) {
    REWRITE("Rewrite"), SUMMARIZE("Summarize"), TRANSLATE("Translate"),
    IMPROVE("Improve"), FIX_GRAMMAR("Fix grammar"), EXPLAIN("Explain"),
    CONTINUE("Continue"),
}

/** Error-correction level for generated QR codes (higher = more redundant). */
enum class QrEccLevel { L, M, Q, H }

/**
 * English dialect the offline grammar tool lints against. Ordinals are the
 * contract with the native Harper library — append only, never reorder.
 */
enum class GrammarDialect(val label: String) {
    AMERICAN("American"), BRITISH("British"), CANADIAN("Canadian"), AUSTRALIAN("Australian"),
}

/** Content filter for the GIF and sticker tools (provider rating levels). */
enum class GifContentFilter { OFF, LOW, MEDIUM, HIGH }

/**
 * How the GIF/sticker panel presents multiple providers (KLIPY, GIPHY,
 * Google): a chip per source, or every source's results interleaved
 * evenly into one grid.
 */
enum class GifSourceMode { TABS, MIX }

/**
 * Key-press sound: which of the system's UI sound effects plays. All come
 * from the device's sound pack, so they match the stock keyboard's palette.
 */
enum class KeySoundStyle { CLICK, STANDARD, POP, THOCK, CHIME }

/**
 * Key-press haptic waveform: [CUSTOM] drives the motor directly with the
 * duration/amplitude settings; [CLICK] and [HEAVY_CLICK] use the device's
 * hardware-tuned predefined effects (Android 10+, falls back to CUSTOM).
 */
enum class HapticStyle { CUSTOM, CLICK, HEAVY_CLICK, SHARP }

/**
 * What a horizontal swipe on the spacebar does. "Short" swipes start
 * moving right away; "long" swipes hold the spacebar past the long-press
 * delay first, then drag — distance is deliberately not the discriminator,
 * a fast flick travels further than a careful drag.
 */
enum class SpaceSwipeAction { NONE, LANGUAGE, CURSOR }

/**
 * What a swipe across the letter keys does. TYPE_WORDS is the classic glide
 * decoder; HANDWRITE turns the same swipe into a handwriting stroke fed to the
 * ML Kit recognizer (full builds only — needs a downloaded handwriting model).
 */
enum class LetterSwipeAction { TYPE_WORDS, HANDWRITE }

/** What the history tab of the emoji panel shows. */
enum class EmojiTabMode { RECENTS, MOST_USED }

/**
 * The dedicated emoji row (Gboard style): [ALWAYS] keeps it as its own row
 * above the keys, [BUTTON] tucks it behind a toggle on the toolbar strip,
 * [OFF] hides it entirely.
 */
enum class EmojiBarMode { OFF, BUTTON, ALWAYS }

/** Which emojis the dedicated emoji row shows (favourites always lead). */
enum class EmojiBarContent { MOST_USED, RECENTS, FAVOURITES }

/**
 * Which font renders emojis on the keyboard itself (panel, emoji row,
 * suggestions). [SYSTEM] uses the device's emoji font (Samsung's own pack
 * on Samsung phones), [NOTO] downloads Google's Noto Color Emoji — the
 * stock-Android look — via the Google Fonts provider, [CUSTOM] uses an
 * emoji font file the user imported. Text committed to apps is plain
 * Unicode either way; the receiving app draws it with its own font.
 */
enum class EmojiFontChoice { SYSTEM, NOTO, CUSTOM }

/**
 * What tapping an emoji suggestion does to the word being typed:
 * [REPLACE] swaps the word for the emoji (Gboard style), [APPEND] keeps
 * the word and adds the emoji after it ("birthday 🎂").
 */
enum class EmojiInsertMode { REPLACE, APPEND }

/**
 * Colour-vision correction applied to the whole resolved keyboard palette.
 * The three dichromacies daltonize (redistribute the hues that eye cannot
 * separate); [GRAYSCALE] strips colour entirely, which both helps monochromacy
 * and makes any luminance-contrast failure in a theme immediately visible.
 */
enum class ColorVisionFilter { NONE, DEUTERANOPIA, PROTANOPIA, TRITANOPIA, GRAYSCALE }

/**
 * How the keyboard exposes itself to TalkBack. [OFF] leaves the keys as raw
 * touch targets (what a screen-reader user gets today: nothing readable).
 * [LABELS] adds spoken labels but keeps direct typing, which suits switch
 * access and low-vision users who still touch-type. [EXPLORE] is the
 * conventional IME behaviour under touch exploration — drag to hear a key,
 * lift to type it — and is what TalkBack users expect.
 */
enum class ScreenReaderMode { OFF, LABELS, EXPLORE }

data class KeyboardSettings(
    /**
     * The layout being typed on: a [BuiltInLayouts] id, or a custom one. This is
     * the stored choice; [inputMode] below is read off it.
     */
    val activeLayoutId: String = BuiltInLayouts.DEFAULT_ID,
    /** Layouts the 🌐 key and the spacebar swipe cycle between, in order. */
    val enabledLayoutIds: List<String> = BuiltInLayouts.defaultEnabledIds,
    /** User-created layouts, and edits shadowing a built-in by reusing its id. */
    val customLayouts: List<LayoutSpec> = emptyList(),
    /** Languages of [enabledLayoutIds], deduped, in switch order. */
    val enabledLanguages: List<LanguageDef> =
        listOf(LanguageRegistry.byId("en"), LanguageRegistry.byId("bn")),
    /**
     * Secondary languages per primary language id: while typing the primary,
     * these languages' dictionaries also feed suggestions (HeliBoard-style
     * multilingual typing). Empty for everyone by default.
     */
    val secondaryLanguages: Map<String, List<String>> = emptyMap(),
    /**
     * The language of [activeLayoutId], resolved from its layout's `langId`. The
     * registry-era replacement for [inputMode]/[KeyboardLanguage]: dictionary,
     * dictation and handwriting keyed by [LanguageDef.id], and [script] behaviour
     * (direction, case, composer, font) alongside it.
     */
    val language: LanguageDef = LanguageRegistry.byId("en"),
    /** The script [language] writes in — direction, letter-case, composer, font. */
    val script: ScriptDef = ScriptRegistry[ScriptId.LATIN],
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Selected keyboard theme: [DEFAULT_THEME_ID], a built-in id, or a custom id. */
    val keyboardThemeId: String = DEFAULT_THEME_ID,
    /** User-created themes; built-ins live in code (BuiltInThemes). */
    val customThemes: List<ThemeSpec> = emptyList(),
    val keyHeightDp: Int = 48,
    val numberRowHeightDp: Int = 42,
    // Edge-to-edge IME windows (enforced on Android 15+) draw behind the
    // gesture bar; a larger default keeps the bottom row comfortably above it.
    val bottomPaddingDp: Int = if (Build.VERSION.SDK_INT >= 35) 32 else 8,
    val splitKeyboard: Boolean = false,
    val splitGapPercent: Int = 12,
    val floatingKeyboard: Boolean = false,
    val floatingWidthDp: Int = 320,
    /** Multiplier on key height while floating, set by the resize grip. */
    val floatingHeightScale: Float = 1f,
    val floatingXFraction: Float = 0.5f,
    val floatingYFraction: Float = 1.0f,
    val keyboardWidthPercent: Int = 100,
    val keyboardAlignment: KeyboardAlignment = KeyboardAlignment.CENTER,
    val keyCornerRadiusDp: Int = 8,
    val fontScale: Float = 1.0f,
    /**
     * Font for the keyboard's own text: "default" (system), "google:<Name>"
     * (a Google Fonts family, fetched via the GMS fonts provider and cached
     * on-device), or "custom" (an imported font file).
     */
    val keyFontId: String = "default",
    /** Display name of the imported custom font file, for the settings UI. */
    val customFontName: String = "",
    /** Font used while a Bengali input mode is active (same id scheme). */
    val bengaliFontId: String = "default",
    /** Display name of the imported custom Bengali font file. */
    val customBengaliFontName: String = "",
    /**
     * Bumped by the settings app whenever it edits the learned-words file
     * directly, so the IME (which keeps the lexicon in memory) reloads it.
     */
    val lexiconVersion: Int = 0,
    /** Bumped when the settings app imports or removes a custom word list. */
    val customDictVersion: Int = 0,
    /** Emoji look on the keyboard: system pack, Noto (stock Android), or custom. */
    val emojiFont: EmojiFontChoice = EmojiFontChoice.SYSTEM,
    val hapticFeedback: Boolean = true,
    val hapticStrengthMs: Int = 15,
    val hapticAmplitude: Int = 255,
    val hapticStyle: HapticStyle = HapticStyle.CUSTOM,
    val hapticOnLongPress: Boolean = true,
    val hapticOnLongPressRelease: Boolean = false,
    val keySound: Boolean = false,
    val keySoundStyle: KeySoundStyle = KeySoundStyle.CLICK,
    /** Sound-effect volume, 0..1 of the system media volume. */
    val keySoundVolume: Float = 0.5f,
    val keyPopup: Boolean = true,
    /**
     * How long the bubble lingers *after* release, so a fast tap still leaves
     * a readable bubble instead of a single-frame flash. This is a comfort
     * floor: raise it for a slower, more deliberate feel.
     */
    val keyPopupMinDurationMs: Int = 150,
    /**
     * Hard ceiling on the bubble's on-screen life, measured from the press —
     * a stuck-bubble backstop, not a comfort knob. Normally the bubble clears
     * on release; if the release is ever dropped under UI-thread lag (e.g. the
     * InputConnection work on a new line), this cap hides it anyway so it can't
     * strand. Kept above the long-press timeout so genuine holds still preview
     * until the alternates popup takes over.
     */
    val keyPopupMaxDurationMs: Int = 750,
    val keyPopupOnKey: Boolean = true,
    val popupFontScale: Float = 1.0f,
    val keyPopupHeightDp: Int = 110,
    // ---- accessibility ----
    /** Daltonization / grayscale applied over the resolved theme palette. */
    val colorVisionFilter: ColorVisionFilter = ColorVisionFilter.NONE,
    /** Force key text to maximum contrast and separate the board from the keys. */
    val highContrastKeys: Boolean = false,
    /** Draw an outline on every key, so key edges don't rely on fill contrast. */
    val keyOutlines: Boolean = false,
    /** Render key labels bold. */
    val boldKeyLabels: Boolean = false,
    /**
     * Suppress non-essential animation across the keyboard and settings app,
     * for vestibular sensitivity. Feedback that carries meaning (the key
     * preview bubble, press colour) is untouched — only motion is removed.
     */
    val reduceMotion: Boolean = false,
    val screenReaderMode: ScreenReaderMode = ScreenReaderMode.LABELS,
    /**
     * Ignore a repeat press of the same key within this many milliseconds
     * (0 = off). The tremor/spasticity counterpart to a long-press delay:
     * it drops the unintended second contact of a bouncing tap.
     */
    val keyDebounceMs: Int = 0,
    val numberRow: Boolean = false,
    /**
     * Sizing overrides per screen shape (landscape, unfolded, both). The
     * plain sizing fields above are the portrait values; anything a variant
     * leaves unset inherits them. Resolve with [resolvedFor].
     */
    val sizingOverrides: Map<ScreenVariant, SizingOverride> = emptyMap(),
    val autocorrect: Boolean = true,
    /**
     * How sure autocorrect must be before it replaces a word: the factor by
     * which the best candidate has to outscore the runner-up. Low corrects
     * eagerly, high only on near-certainty. Mirrors
     * `SuggestionEngine.DEFAULT_AUTOCORRECT_CONFIDENCE`, spelled out here
     * because prediction already depends on this package.
     */
    val autocorrectConfidence: Float = 4f,
    /** Backspace right after an autocorrect puts the typed word back. */
    val revertAutocorrectOnBackspace: Boolean = true,
    /** Fix missing apostrophes on commit: arent → aren't, im → I'm. */
    val autoApostrophe: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    /** Double-tapping space inserts a tab character (wins over the period). */
    val doubleSpaceTab: Boolean = false,
    val suggestions: Boolean = true,
    /**
     * Show the suggestion strip even in fields that ask the IME to stay quiet
     * (the NO_SUGGESTIONS flag, email/URI/filter boxes). Many apps — Instagram,
     * Google Keep — set that flag on ordinary text fields; on (the default),
     * the keyboard shows suggestions anyway, the way most keyboards quietly do.
     * Off respects the app and hides the strip. Password fields are always
     * excluded regardless. Autocorrect, gesture typing and Avro composing are
     * governed separately (KeyboardUiState.allowsTypingIntelligence) and keep
     * working whichever way this is set.
     */
    val showSuggestionsInAllFields: Boolean = true,
    /** Keep the suggestion strip as the default top bar even with nothing typed. */
    val suggestionsFirst: Boolean = false,
    /** Show the primary candidate in the middle slot (Gboard style) instead of the left. */
    val suggestionPrimaryCenter: Boolean = true,
    /** Suggest names from the phone's contacts (needs the Contacts permission). */
    val contactSuggestions: Boolean = false,
    /** Suggest the names of installed apps ("sign" → Signal). No permission needed. */
    val appNameSuggestions: Boolean = false,
    /** Typing ":" then a word searches emoji in the suggestion strip (:smi → 😄). */
    val inlineEmojiSearch: Boolean = true,
    /**
     * Show password-manager entries from the system autofill service in the
     * suggestion strip (Android 11+). The chips are rendered by the manager
     * itself; the keyboard only gives them the space.
     */
    val inlineAutofill: Boolean = true,
    val gestureTyping: Boolean = true,
    /**
     * What a letter-area swipe does when [gestureTyping] is on: glide-type a
     * word (default) or draw handwriting recognized on the keyboard itself.
     */
    val letterSwipeAction: LetterSwipeAction = LetterSwipeAction.TYPE_WORDS,
    /** Swipe that starts moving before the long-press delay elapses. */
    val spaceShortSwipe: SpaceSwipeAction = SpaceSwipeAction.LANGUAGE,
    /** Swipe that begins after holding the spacebar past the long-press delay. */
    val spaceLongSwipe: SpaceSwipeAction = SpaceSwipeAction.CURSOR,
    /**
     * Draw ◀ ▶ arrows around the spacebar language name, hinting that a
     * horizontal swipe switches language. Only shown when a swipe slot is
     * actually set to language switching and more than one mode is enabled.
     */
    val spacebarLanguageArrows: Boolean = true,
    /**
     * Text drawn on the spacebar. Blank keeps the current language name;
     * `%s` inside a custom label is replaced by it, so "— %s —" still
     * tracks the mode.
     */
    val spacebarLabel: String = "",
    /**
     * Dragging sideways on backspace deletes whole words instead of
     * repeating single-character deletes.
     */
    val backspaceSwipeDelete: Boolean = true,
    /** Volume up/down move the text cursor while the keyboard is showing. */
    val volumeCursor: Boolean = false,
    /**
     * Hand the volume keys back to the system while audio is playing, so
     * cursor control never costs you the ability to turn a song down.
     */
    val volumeCursorMediaAware: Boolean = true,
    /** Replace the 🌐 key with an emoji key (language switching moves to spacebar swipes). */
    val globeAsEmoji: Boolean = true,
    val onboardingDone: Boolean = false,
    val conjunctBackspace: Boolean = false,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val learnFromTyping: Boolean = true,
    val clipboardHistory: Boolean = true,
    val clipboardExpiryHours: Int = 24,
    /** Fetch page titles for copied links and show them in the clipboard panel. */
    val clipboardLinkPreviews: Boolean = false,
    /**
     * Record which app a clip was copied from (shown in the press-and-hold info
     * popup). Off by default: needs the Usage Access special permission and is a
     * best-effort guess of the foreground app at copy time.
     */
    val clipboardTrackSource: Boolean = false,
    val longPressDelayMs: Int = 300,
    val keyRepeatIntervalMs: Int = 50,
    /** Small corner label on each key showing its first long-press character. */
    val longPressHints: Boolean = true,
    /** Long-pressing A selects all text in the field. */
    /**
     * Send Ctrl+A/C/V/X to the app as raw key events instead of using the
     * clipboard actions.
     *
     * Off by default because performContextMenuAction works in WebViews and
     * Compose text fields, where a raw Ctrl+C reaches nothing at all. A terminal
     * is the opposite case — it needs Ctrl+C to arrive as an interrupt — so this
     * is a setting rather than a guess: EditorInfo cannot tell a terminal from a
     * code editor or a password box.
     */
    val rawClipboardShortcuts: Boolean = false,
    val longPressASelectAll: Boolean = true,
    /** Long-pressing C copies the selection (selects all first when nothing is selected). */
    val longPressCCopy: Boolean = true,
    /** Long-pressing V pastes the clipboard. */
    val longPressVPaste: Boolean = true,
    /** Long-pressing X cuts the selection (selects all first when nothing is selected). */
    val longPressXCut: Boolean = true,
    val emojiToolbar: Boolean = true,
    /** Tint each tool icon its own accent colour in Settings and the toolbox. */
    val coloredToolIcons: Boolean = true,
    val incognito: Boolean = false,
    val toolbarTools: List<ToolbarTool> = DefaultToolbarTools,
    val toolbarGreedy: Boolean = true,
    /** Height of the top toolbar/suggestion strip, in dp. */
    val toolbarHeightDp: Int = 44,
    /**
     * Let the pinned tools scroll horizontally instead of packing into the
     * bar width — for people who pin more tools than fit at a tappable size.
     * Forces the packed (non-greedy) layout while on.
     */
    val toolbarScrollable: Boolean = false,
    /** Draw each tool's name under its icon on the toolbar. */
    val toolbarLabels: Boolean = false,
    /** Font size of those toolbar labels, in sp. */
    val toolbarLabelSize: Int = 9,
    val toolCircleRadiusDp: Int = 20,
    val commaAsEmoji: Boolean = false,
    /** History tab of the emoji panel: recently used vs most used. */
    val emojiTabMode: EmojiTabMode = EmojiTabMode.RECENTS,
    /** "Clear recents" button on the emoji panel's history tab. Off by default. */
    val emojiClearRecentsButton: Boolean = false,
    /** Show the emoji's Unicode name at the top of its long-press popup. */
    val emojiLongPressName: Boolean = true,
    /** Emoji candidates in the suggestion strip while typing. */
    val emojiPrediction: Boolean = true,
    val emojiBarMode: EmojiBarMode = EmojiBarMode.BUTTON,
    val emojiBarContent: EmojiBarContent = EmojiBarContent.MOST_USED,
    /** Whether an emoji suggestion replaces the typed word or follows it. */
    val emojiInsertMode: EmojiInsertMode = EmojiInsertMode.APPEND,
    /** Tools available anywhere on the keyboard; disabled tools are hidden. */
    val enabledTools: List<ToolbarTool> = ToolbarTool.entries.toList(),
    /**
     * Every tool's position in the toolbox grid, most-used-first by default;
     * the user rearranges it by dragging tools around the toolbox. Always a
     * complete ordering over all tools — pinned/disabled ones keep their
     * rank so they come back where they belong.
     */
    val toolboxOrder: List<ToolbarTool> = DefaultToolOrder,
    /** The toolbox drag hint was dismissed; after that it only rarely reappears. */
    val toolboxHintDismissed: Boolean = false,
    /** Turn the torch off automatically when the keyboard is dismissed. */
    val flashlightAutoOff: Boolean = true,
    val compassShowDegrees: Boolean = true,
    /** Mark the direction of the Kaaba on the compass (needs the saved location). */
    val compassShowQibla: Boolean = false,
    val levelShowAngles: Boolean = true,
    /** Redo sends Ctrl+Y instead of Ctrl+Shift+Z. */
    val redoUsesCtrlY: Boolean = false,
    /** Mirrors the moon drawing for southern-hemisphere viewers. */
    val moonSouthernHemisphere: Boolean = false,
    val weatherFahrenheit: Boolean = false,
    val weatherLatitude: Float? = null,
    val weatherLongitude: Float? = null,
    val weatherPlaceName: String = "",
    val calendarShowBengali: Boolean = true,
    val calendarShowHijri: Boolean = true,
    /** Day offset applied to the tabular Hijri date (moon-sighting drift). */
    val hijriAdjustDays: Int = 0,
    /** Handwriting canvas ignores finger touches; only a stylus draws. */
    val handwritingStylusOnly: Boolean = false,
    /** Pause after the last stroke before recognizing and committing. */
    val handwritingCommitDelayMs: Int = 700,
    /** Insert a space between consecutively handwritten words. */
    val handwritingAutoSpace: Boolean = true,
    /** Voice tool dictates into a compact bar over the keys, not a panel. */
    val voiceStripMode: Boolean = false,
    /** Keep listening after each dictated sentence. */
    val voiceContinuous: Boolean = true,
    /** Saying "comma" / "দাঁড়ি" types the mark instead of the word. */
    val voiceSpokenPunctuation: Boolean = true,
    /** Camera tool opens on the selfie camera. */
    val cameraPreferFront: Boolean = false,
    /** Mirror selfie captures so the photo matches the preview. */
    val cameraMirrorFront: Boolean = true,
    /** Play a shutter click when the camera tool takes a photo. */
    val cameraShutterSound: Boolean = true,
    /** Vibrate on camera controls, countdown ticks and the shutter. */
    val cameraHaptics: Boolean = true,
    /** Copy camera captures into Pictures/WM Keyboard as well as sending them. */
    val cameraSaveToGallery: Boolean = false,
    /** Copy scanned document pages into Pictures/WM Keyboard. */
    val docScanSaveToGallery: Boolean = false,
    /** Copy generated QR codes into Pictures/WM Keyboard. */
    val qrSaveToGallery: Boolean = false,
    /** How sticker-tool picks are sent. WhatsApp shows real stickers for these. */
    val stickerSendMode: MediaSendMode = MediaSendMode.STICKER,
    /** How GIF picks are sent. Sticker mode only applies to WebP-backed GIFs. */
    val gifSendMode: MediaSendMode = MediaSendMode.IMAGE,
    /** How generated QR codes are sent. */
    val qrSendMode: MediaSendMode = MediaSendMode.IMAGE,
    /** Dictionary tool looks up the word at the cursor when it opens. */
    val dictionaryAutoLookup: Boolean = true,
    /** Auto-repeat interval while holding an arrow/backspace in the text-editing tool. */
    val textEditRepeatMs: Int = 60,
    /** Number pad digits phone-style (123 on top) instead of calculator-style (789 on top). */
    val numpadPhoneLayout: Boolean = false,
    /** Incognito stops the clipboard tool from capturing copies. */
    val incognitoPausesClipboard: Boolean = true,
    /** Incognito stops word and emoji learning. */
    val incognitoPausesLearning: Boolean = true,
    /**
     * Turn incognito on by itself for fields that ask not to be learned from
     * (IME_FLAG_NO_PERSONALIZED_LEARNING) — Chrome incognito tabs, private
     * browsing in other browsers, and password-manager notes fields.
     */
    val autoIncognito: Boolean = true,
    /** Text scanner results start with every word selected (deselect to trim). */
    val ocrAutoSelectWords: Boolean = true,
    /** Vibrate when the QR scanner spots a code. */
    val qrScanHaptics: Boolean = true,
    /** Insert a scanned code into the field the moment it is spotted. */
    val qrScanAutoInsert: Boolean = false,
    /** Fetch the page title/description for a scanned link, like clipboard link previews. */
    val qrScanLinkPreviews: Boolean = false,
    /** Decimal places on currency conversion results. */
    val currencyDecimals: Int = 2,
    /** Hours exchange rates stay fresh before the panel refetches on open. */
    val currencyCacheHours: Int = 6,
    /** Pause after typing stops before the grammar tool re-lints the field. */
    val grammarDebounceMs: Int = 350,
    /**
     * Unit converter memory: each category's last from/to pair, last-used
     * category first ("Length|m|ft;Mass|kg|lb"). Restored on open.
     */
    val unitConvertLast: String = "",
    /** Tools per row in the toolbox grid. */
    val toolboxColumns: Int = 4,
    /** ISO 639-1 code the translate tool translates into (source is auto-detected). */
    val translateTargetLang: String = "en",
    /** English dialect the offline grammar tool checks against. */
    val grammarDialect: GrammarDialect = GrammarDialect.AMERICAN,
    /**
     * User-supplied API keys, overriding any key baked into the build.
     * Blank means "use the built-in key" (which may itself be blank).
     */
    val translateApiKey: String = "",
    val klipyApiKey: String = "",
    val giphyApiKey: String = "",
    val braveApiKey: String = "",
    val gifContentFilter: GifContentFilter = GifContentFilter.MEDIUM,
    /** Tabs per provider vs one evenly-mixed grid, when several have keys. */
    val gifSourceMode: GifSourceMode = GifSourceMode.TABS,
    /** SafeSearch for the web and image search tools. */
    val searchSafe: Boolean = true,
    /** Results per web/image search (the API caps a page at 10). */
    val searchResultCount: Int = 8,
    /** Wikipedia subdomain the encyclopedia tool reads (en, bn, de …). */
    val wikiLanguage: String = "en",
    /** Insert Wikipedia links as `[Title](url)` instead of the bare URL. */
    val wikiLinksMarkdown: Boolean = false,
    /** Recently used special symbols, newest first (symbols tool). */
    val symbolRecents: List<String> = emptyList(),
    /** Dedicated symbol row above the keys (special characters & snippets). */
    val symbolRowEnabled: Boolean = false,
    /** Symbol sets offered by the row's picker chip (built-in or custom ids). */
    val symbolRowSetIds: List<String> = BuiltInSymbolSets.defaultEnabledIds,
    /** Set the row currently shows; the picker chip changes it. */
    val symbolRowActiveSetId: String = BuiltInSymbolSets.PUNCTUATION_ID,
    /** User-created symbol sets; built-ins live in code (BuiltInSymbolSets). */
    val customSymbolSets: List<SymbolSet> = emptyList(),
    /**
     * Top-to-bottom order of the rows above the keys. The emoji row sits
     * above the toolbar by default: it is used far more often than the tool
     * buttons, so it belongs closest to the suggestion strip.
     */
    val barOrder: List<BarRow> = listOf(BarRow.EMOJI, BarRow.TOPBAR, BarRow.SYMBOL),
    /**
     * Emoji panel takes over the whole keyboard: the toolbar (and any emoji
     * or symbol row) hides and the category tabs move up into the reclaimed
     * row, next to a back button.
     */
    val emojiFullBleed: Boolean = true,
    /** Same treatment for the GIF and sticker panels, with search up top. */
    val mediaFullBleed: Boolean = true,
    /**
     * While a keyboard mode is active, rearranging tools edits that mode's
     * own tool order instead of the global one — otherwise the change would
     * look like it did nothing, since the mode's order wins while it is on.
     */
    val modeToolOrderEdits: Boolean = true,
    /** The "tool order is per-mode" notice has been shown once. */
    val modeToolOrderHintSeen: Boolean = false,
    /** Keyboard modes (per-app / per-field bundles of overrides). */
    val keyboardModes: List<KeyboardMode> = DefaultKeyboardModes,
    /**
     * Master switch for the smart chips on the suggestion strip — the
     * inline calculator, currency and unit answers plus the tool keywords.
     * The four flags below refine it; this one turns the lot off.
     */
    val smartSuggestions: Boolean = true,
    /** Offer the result when an arithmetic expression is typed. */
    val smartCalc: Boolean = true,
    /** Offer the converted amount when "150 usd" style text is typed. */
    val smartCurrency: Boolean = true,
    /** Offer the converted value when "1 ft" style text is typed. */
    val smartUnits: Boolean = true,
    /** Offer to open a tool when one of its keywords is typed. */
    val smartToolKeywords: Boolean = true,
    /**
     * Per-tool keyword overrides, "TOOL=a,b;TOOL=c". Tools missing from the
     * string use [com.wasimaster.wmkeyboard.core.tools.SmartSuggest.defaultKeywords].
     */
    val toolKeywords: String = "",
    /** Trig in degrees (off = radians) for the calculator tool. */
    val calcDegrees: Boolean = true,
    /** Decimal places in calculator/converter results. */
    val calcPrecision: Int = 8,
    /** Currency codes the converter starts on. */
    val currencyFrom: String = "USD",
    val currencyTo: String = "BDT",
    // Password generator defaults (the panel tweaks these live).
    val pwLength: Int = 16,
    val pwUppercase: Boolean = true,
    val pwDigits: Boolean = true,
    val pwSymbols: Boolean = true,
    /** Skip look-alikes (Il1O0…) for passwords read aloud or retyped. */
    val pwExcludeAmbiguous: Boolean = false,
    /** Generator opens in passphrase mode instead of password mode. */
    val pwPassphraseMode: Boolean = false,
    val ppWordCount: Int = 4,
    val ppSeparator: String = "-",
    val ppCapitalize: Boolean = false,
    val ppIncludeDigit: Boolean = false,
    // Typing-speed test. The panel edits these live, so they double as the
    // tool's own settings and as the memory of how the user last left it.
    val typingTestMode: TypingTestMode = TypingTestMode.TIME,
    val typingTestDuration: Int = 30,
    val typingTestWordCount: Int = 25,
    val typingTestPunctuation: Boolean = false,
    val typingTestNumbers: Boolean = false,
    /** Personal bests per config, encoded by [TypingBests]. */
    val typingTestBests: String = "",
    /** Recent WPM scores, oldest first, encoded by [TypingHistory]. */
    val typingTestHistory: String = "",
    val typingTestsCompleted: Int = 0,
    /** Side length of the QR image the generator inserts. */
    val qrSizePx: Int = 1024,
    val qrEcc: QrEccLevel = QrEccLevel.M,
    // AI tool: provider, per-provider keys/models, self-hosted URLs and
    // per-action prompt overrides (blank = built-in prompt).
    val aiProvider: AiProvider = AiProvider.ANTHROPIC,
    val aiAnthropicKey: String = "",
    val aiOpenAiKey: String = "",
    val aiGeminiKey: String = "",
    val aiAnthropicModel: String = "",
    val aiOpenAiModel: String = "",
    val aiGeminiModel: String = "",
    val aiOllamaUrl: String = "",
    val aiOllamaModel: String = "",
    val aiLmStudioUrl: String = "",
    val aiLmStudioModel: String = "",
    // Reasoning models get a multiple of this at request time (AiClient) —
    // their think block spends the same budget as the answer.
    val aiMaxTokens: Int = 2048,
    /** Target language of the AI translate action. */
    val aiTranslateTo: String = "English",
    val aiPromptRewrite: String = "",
    val aiPromptSummarize: String = "",
    val aiPromptTranslate: String = "",
    val aiPromptImprove: String = "",
    val aiPromptFixGrammar: String = "",
    val aiPromptExplain: String = "",
    val aiPromptContinue: String = "",
    /**
     * Selected on-device model: a LocalLlmCatalog id, or "custom:<fileName>"
     * for an imported file. Blank = none selected.
     */
    val aiLocalModelId: String = "",
    val aiLocalBackend: LocalLlmBackend = LocalLlmBackend.CPU,
    /** Hugging Face access token — only needed to download gated models (Gemma). */
    val hfToken: String = "",
    /**
     * Show reasoning models' <think> passages verbatim while they stream.
     * Off (default) hides them behind a "reasoning" progress bar and strips
     * them from the result.
     */
    val aiShowThinking: Boolean = false,
    /** Show a model/provider switcher row on the AI panel itself. */
    val aiPanelModelPicker: Boolean = true,
)

/**
 * DataStore-backed settings. Every option on the settings screens flows
 * through here; the IME service collects [settings] and re-renders live.
 */
/** Serializes the secondary-language map to a compact `primary=s1,s2;...` string. */
private fun encodeSecondaryLanguages(map: Map<String, List<String>>): String =
    map.entries
        .filter { it.value.isNotEmpty() }
        .joinToString(";") { (primary, secs) -> "$primary=${secs.joinToString(",")}" }

private fun decodeSecondaryLanguages(raw: String): Map<String, List<String>> =
    raw.split(';')
        .filter { it.isNotEmpty() }
        .mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val secs = entry.substring(eq + 1).split(',').filter { it.isNotEmpty() }
            if (secs.isEmpty()) null else entry.substring(0, eq) to secs
        }
        .toMap()

class SettingsRepository(private val context: Context) {

    companion object {
        private val Context.dataStore by preferencesDataStore(name = "keyboard_settings")

        // input_mode and enabled_modes are kept as the compatibility mirror of
        // the two keys below: they are written alongside, never read except by
        // an install that predates the layout registry.
        private val INPUT_MODE = stringPreferencesKey("input_mode")
        private val ENABLED_MODES = stringPreferencesKey("enabled_modes")
        private val ACTIVE_LAYOUT_ID = stringPreferencesKey("active_layout_id")
        private val ENABLED_LAYOUT_IDS = stringPreferencesKey("enabled_layout_ids")
        private val CUSTOM_LAYOUTS = stringPreferencesKey("custom_layouts")
        private val SECONDARY_LANGUAGES = stringPreferencesKey("secondary_languages")
        private val RAW_CLIPBOARD_SHORTCUTS = booleanPreferencesKey("raw_clipboard_shortcuts")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEYBOARD_THEME_ID = stringPreferencesKey("keyboard_theme_id")
        private val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        private val KEY_HEIGHT = intPreferencesKey("key_height")
        private val NUMBER_ROW_HEIGHT = intPreferencesKey("number_row_height")
        private val BOTTOM_PADDING = intPreferencesKey("bottom_padding")
        private val SPLIT_KEYBOARD = booleanPreferencesKey("split_keyboard")
        private val SPLIT_GAP_PERCENT = intPreferencesKey("split_gap_percent")
        private val FLOATING_KEYBOARD = booleanPreferencesKey("floating_keyboard")
        private val FLOATING_WIDTH = intPreferencesKey("floating_width")
        private val FLOATING_HEIGHT_SCALE = floatPreferencesKey("floating_height_scale")
        private val FLOATING_X = floatPreferencesKey("floating_x")
        private val FLOATING_Y = floatPreferencesKey("floating_y")
        private val KEYBOARD_WIDTH_PERCENT = intPreferencesKey("keyboard_width_percent")
        private val KEYBOARD_ALIGNMENT = stringPreferencesKey("keyboard_alignment")
        private val KEY_CORNER_RADIUS = intPreferencesKey("key_corner_radius")
        private val FONT_SCALE = floatPreferencesKey("font_scale")

        // Per-variant sizing overrides. The keys are derived from the base
        // names rather than spelled out, so the four screen shapes times six
        // settings stay in step with each other by construction.
        private fun variantKey(base: String, variant: ScreenVariant) = "${base}_${variant.suffix}"

        private fun keyHeightKey(v: ScreenVariant) = intPreferencesKey(variantKey("key_height", v))
        private fun numberRowHeightKey(v: ScreenVariant) =
            intPreferencesKey(variantKey("number_row_height", v))
        private fun bottomPaddingKey(v: ScreenVariant) =
            intPreferencesKey(variantKey("bottom_padding", v))
        private fun widthPercentKey(v: ScreenVariant) =
            intPreferencesKey(variantKey("keyboard_width_percent", v))
        private fun alignmentKey(v: ScreenVariant) =
            stringPreferencesKey(variantKey("keyboard_alignment", v))
        private fun fontScaleKey(v: ScreenVariant) =
            floatPreferencesKey(variantKey("font_scale", v))
        private val KEY_FONT_ID = stringPreferencesKey("key_font_id")
        private val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        private val BENGALI_FONT_ID = stringPreferencesKey("bengali_font_id")
        private val CUSTOM_BENGALI_FONT_NAME = stringPreferencesKey("custom_bengali_font_name")
        private val LEXICON_VERSION = intPreferencesKey("lexicon_version")
        private val CUSTOM_DICT_VERSION = intPreferencesKey("custom_dict_version")
        private val EMOJI_FONT = stringPreferencesKey("emoji_font")
        private val AUTO_APOSTROPHE = booleanPreferencesKey("auto_apostrophe")
        private val HAPTIC = booleanPreferencesKey("haptic")
        private val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength")
        private val HAPTIC_AMPLITUDE = intPreferencesKey("haptic_amplitude")
        private val HAPTIC_STYLE = stringPreferencesKey("haptic_style")
        private val HAPTIC_ON_LONG_PRESS = booleanPreferencesKey("haptic_on_long_press")
        private val HAPTIC_ON_LONG_PRESS_RELEASE = booleanPreferencesKey("haptic_on_long_press_release")
        private val KEY_SOUND = booleanPreferencesKey("key_sound")
        private val KEY_POPUP = booleanPreferencesKey("key_popup")
        private val KEY_POPUP_MIN_DURATION = intPreferencesKey("key_popup_min_duration")
        private val KEY_POPUP_MAX_DURATION = intPreferencesKey("key_popup_max_duration")
        private val KEY_POPUP_ON_KEY = booleanPreferencesKey("key_popup_on_key")
        private val POPUP_FONT_SCALE = floatPreferencesKey("popup_font_scale")
        private val KEY_POPUP_HEIGHT = intPreferencesKey("key_popup_height")
        private val COLOR_VISION_FILTER = stringPreferencesKey("color_vision_filter")
        private val HIGH_CONTRAST_KEYS = booleanPreferencesKey("high_contrast_keys")
        private val KEY_OUTLINES = booleanPreferencesKey("key_outlines")
        private val BOLD_KEY_LABELS = booleanPreferencesKey("bold_key_labels")
        private val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        private val SCREEN_READER_MODE = stringPreferencesKey("screen_reader_mode")
        private val KEY_DEBOUNCE_MS = intPreferencesKey("key_debounce_ms")
        private val NUMBER_ROW = booleanPreferencesKey("number_row")
        private val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        private val AUTOCORRECT_CONFIDENCE = floatPreferencesKey("autocorrect_confidence")
        private val REVERT_AUTOCORRECT_ON_BACKSPACE =
            booleanPreferencesKey("revert_autocorrect_on_backspace")
        private val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        private val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        private val DOUBLE_SPACE_TAB = booleanPreferencesKey("double_space_tab")
        private val SUGGESTIONS = booleanPreferencesKey("suggestions")
        private val SHOW_SUGGESTIONS_ALL_FIELDS =
            booleanPreferencesKey("show_suggestions_all_fields")
        private val SUGGESTIONS_FIRST = booleanPreferencesKey("suggestions_first")
        private val SUGGESTION_PRIMARY_CENTER = booleanPreferencesKey("suggestion_primary_center")
        private val CONTACT_SUGGESTIONS = booleanPreferencesKey("contact_suggestions")
        private val APP_NAME_SUGGESTIONS = booleanPreferencesKey("app_name_suggestions")
        private val INLINE_EMOJI_SEARCH = booleanPreferencesKey("inline_emoji_search")
        private val INLINE_AUTOFILL = booleanPreferencesKey("inline_autofill")
        private val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
        private val LETTER_SWIPE_ACTION = stringPreferencesKey("letter_swipe_action")
        // Legacy boolean, read only to migrate into SPACE_LONG_SWIPE.
        private val SPACEBAR_CURSOR = booleanPreferencesKey("spacebar_cursor")
        private val SPACE_SHORT_SWIPE = stringPreferencesKey("space_short_swipe")
        private val SPACE_LONG_SWIPE = stringPreferencesKey("space_long_swipe")
        private val SPACEBAR_LANGUAGE_ARROWS = booleanPreferencesKey("spacebar_language_arrows")
        private val SPACEBAR_LABEL = stringPreferencesKey("spacebar_label")
        private val BACKSPACE_SWIPE_DELETE = booleanPreferencesKey("backspace_swipe_delete")
        private val VOLUME_CURSOR = booleanPreferencesKey("volume_cursor")
        private val VOLUME_CURSOR_MEDIA_AWARE = booleanPreferencesKey("volume_cursor_media_aware")
        private val GLOBE_AS_EMOJI = booleanPreferencesKey("globe_as_emoji")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val CONJUNCT_BACKSPACE = booleanPreferencesKey("conjunct_backspace")
        private val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        private val LEARN_FROM_TYPING = booleanPreferencesKey("learn_from_typing")
        private val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        private val CLIPBOARD_EXPIRY_HOURS = intPreferencesKey("clipboard_expiry_hours")
        private val CLIPBOARD_LINK_PREVIEWS = booleanPreferencesKey("clipboard_link_previews")
        private val CLIPBOARD_TRACK_SOURCE = booleanPreferencesKey("clipboard_track_source")
        private val LONG_PRESS_DELAY = intPreferencesKey("long_press_delay")
        private val KEY_REPEAT_INTERVAL = intPreferencesKey("key_repeat_interval")
        private val LONG_PRESS_HINTS = booleanPreferencesKey("long_press_hints")
        private val LONG_PRESS_A_SELECT_ALL = booleanPreferencesKey("long_press_a_select_all")
        private val LONG_PRESS_C_COPY = booleanPreferencesKey("long_press_c_copy")
        private val LONG_PRESS_V_PASTE = booleanPreferencesKey("long_press_v_paste")
        private val LONG_PRESS_X_CUT = booleanPreferencesKey("long_press_x_cut")
        private val EMOJI_TOOLBAR = booleanPreferencesKey("emoji_toolbar")
        private val COLORED_TOOL_ICONS = booleanPreferencesKey("colored_tool_icons")
        private val INCOGNITO = booleanPreferencesKey("incognito")
        private val TOOLBAR_TOOLS = stringPreferencesKey("toolbar_tools")
        private val TOOLBAR_GREEDY = booleanPreferencesKey("toolbar_greedy")
        private val TOOLBAR_HEIGHT = intPreferencesKey("toolbar_height")
        private val TOOLBAR_SCROLLABLE = booleanPreferencesKey("toolbar_scrollable")
        private val TOOLBAR_LABELS = booleanPreferencesKey("toolbar_labels")
        private val TOOLBAR_LABEL_SIZE = intPreferencesKey("toolbar_label_size")
        private val TOOL_CIRCLE_RADIUS = intPreferencesKey("tool_circle_radius")
        private val COMMA_AS_EMOJI = booleanPreferencesKey("comma_as_emoji")
        private val EMOJI_TAB_MODE = stringPreferencesKey("emoji_tab_mode")
        private val EMOJI_CLEAR_RECENTS_BUTTON = booleanPreferencesKey("emoji_clear_recents_button")
        private val EMOJI_LONG_PRESS_NAME = booleanPreferencesKey("emoji_long_press_name")
        private val EMOJI_PREDICTION = booleanPreferencesKey("emoji_prediction")
        private val EMOJI_BAR_MODE = stringPreferencesKey("emoji_bar_mode")
        private val EMOJI_BAR_CONTENT = stringPreferencesKey("emoji_bar_content")
        private val EMOJI_INSERT_MODE = stringPreferencesKey("emoji_insert_mode")
        // Stored as the DISABLED set so tools added in future versions
        // default to enabled even for users who already toggled some off.
        private val DISABLED_TOOLS = stringPreferencesKey("disabled_tools")
        private val TOOLBOX_ORDER = stringPreferencesKey("toolbox_order")
        private val TOOLBOX_HINT_DISMISSED = booleanPreferencesKey("toolbox_hint_dismissed")
        private val FLASHLIGHT_AUTO_OFF = booleanPreferencesKey("flashlight_auto_off")
        private val COMPASS_SHOW_DEGREES = booleanPreferencesKey("compass_show_degrees")
        private val COMPASS_SHOW_QIBLA = booleanPreferencesKey("compass_show_qibla")
        private val KEY_SOUND_STYLE = stringPreferencesKey("key_sound_style")
        private val KEY_SOUND_VOLUME = floatPreferencesKey("key_sound_volume")
        private val LEVEL_SHOW_ANGLES = booleanPreferencesKey("level_show_angles")
        private val REDO_USES_CTRL_Y = booleanPreferencesKey("redo_uses_ctrl_y")
        private val MOON_SOUTHERN = booleanPreferencesKey("moon_southern_hemisphere")
        private val WEATHER_FAHRENHEIT = booleanPreferencesKey("weather_fahrenheit")
        private val WEATHER_LAT = floatPreferencesKey("weather_lat")
        private val WEATHER_LON = floatPreferencesKey("weather_lon")
        private val WEATHER_PLACE = stringPreferencesKey("weather_place")
        private val CALENDAR_SHOW_BENGALI = booleanPreferencesKey("calendar_show_bengali")
        private val CALENDAR_SHOW_HIJRI = booleanPreferencesKey("calendar_show_hijri")
        private val HIJRI_ADJUST_DAYS = intPreferencesKey("hijri_adjust_days")
        private val HANDWRITING_STYLUS_ONLY = booleanPreferencesKey("handwriting_stylus_only")
        private val HANDWRITING_COMMIT_DELAY = intPreferencesKey("handwriting_commit_delay")
        private val HANDWRITING_AUTO_SPACE = booleanPreferencesKey("handwriting_auto_space")
        private val VOICE_STRIP_MODE = booleanPreferencesKey("voice_strip_mode")
        private val VOICE_CONTINUOUS = booleanPreferencesKey("voice_continuous")
        private val VOICE_SPOKEN_PUNCTUATION = booleanPreferencesKey("voice_spoken_punctuation")
        private val CAMERA_PREFER_FRONT = booleanPreferencesKey("camera_prefer_front")
        private val CAMERA_MIRROR_FRONT = booleanPreferencesKey("camera_mirror_front")
        private val CAMERA_SHUTTER_SOUND = booleanPreferencesKey("camera_shutter_sound")
        private val CAMERA_HAPTICS = booleanPreferencesKey("camera_haptics")
        private val CAMERA_SAVE_TO_GALLERY = booleanPreferencesKey("camera_save_to_gallery")
        private val DOC_SCAN_SAVE_TO_GALLERY = booleanPreferencesKey("doc_scan_save_to_gallery")
        private val QR_SAVE_TO_GALLERY = booleanPreferencesKey("qr_save_to_gallery")
        private val STICKER_SEND_MODE = stringPreferencesKey("sticker_send_mode")
        private val GIF_SEND_MODE = stringPreferencesKey("gif_send_mode")
        private val QR_SEND_MODE = stringPreferencesKey("qr_send_mode")
        private val DICTIONARY_AUTO_LOOKUP = booleanPreferencesKey("dictionary_auto_lookup")
        private val TEXT_EDIT_REPEAT_MS = intPreferencesKey("text_edit_repeat_ms")
        private val NUMPAD_PHONE_LAYOUT = booleanPreferencesKey("numpad_phone_layout")
        private val INCOGNITO_PAUSES_CLIPBOARD = booleanPreferencesKey("incognito_pauses_clipboard")
        private val INCOGNITO_PAUSES_LEARNING = booleanPreferencesKey("incognito_pauses_learning")
        private val AUTO_INCOGNITO = booleanPreferencesKey("auto_incognito")
        private val OCR_AUTO_SELECT_WORDS = booleanPreferencesKey("ocr_auto_select_words")
        private val QR_SCAN_HAPTICS = booleanPreferencesKey("qr_scan_haptics")
        private val QR_SCAN_AUTO_INSERT = booleanPreferencesKey("qr_scan_auto_insert")
        private val QR_SCAN_LINK_PREVIEWS = booleanPreferencesKey("qr_scan_link_previews")
        private val CURRENCY_DECIMALS = intPreferencesKey("currency_decimals")
        private val CURRENCY_CACHE_HOURS = intPreferencesKey("currency_cache_hours")
        private val GRAMMAR_DEBOUNCE_MS = intPreferencesKey("grammar_debounce_ms")
        private val UNIT_CONVERT_LAST = stringPreferencesKey("unit_convert_last")
        private val TOOLBOX_COLUMNS = intPreferencesKey("toolbox_columns")
        private val EMOJI_ROW_ABOVE_TOOLBAR = booleanPreferencesKey("emoji_row_above_toolbar")
        private val TRANSLATE_TARGET_LANG = stringPreferencesKey("translate_target_lang")
        private val GRAMMAR_DIALECT = stringPreferencesKey("grammar_dialect")
        private val TRANSLATE_API_KEY = stringPreferencesKey("translate_api_key")
        private val KLIPY_API_KEY = stringPreferencesKey("klipy_api_key")
        private val BRAVE_API_KEY = stringPreferencesKey("brave_api_key")
        private val GIPHY_API_KEY = stringPreferencesKey("giphy_api_key")
        private val GIF_SOURCE_MODE = stringPreferencesKey("gif_source_mode")
        private val GIF_CONTENT_FILTER = stringPreferencesKey("gif_content_filter")
        private val SEARCH_SAFE = booleanPreferencesKey("search_safe")
        private val SEARCH_RESULT_COUNT = intPreferencesKey("search_result_count")
        private val WIKI_LANGUAGE = stringPreferencesKey("wiki_language")
        private val WIKI_LINKS_MARKDOWN = booleanPreferencesKey("wiki_links_markdown")
        // Tab-separated (symbols are single graphemes; some are commas).
        private val SYMBOL_RECENTS = stringPreferencesKey("symbol_recents")
        private val SYMBOL_ROW_ENABLED = booleanPreferencesKey("symbol_row_enabled")
        // Tab-separated ids (custom set names are user text; ids are safe).
        private val SYMBOL_ROW_SETS = stringPreferencesKey("symbol_row_sets")
        private val SYMBOL_ROW_ACTIVE_SET = stringPreferencesKey("symbol_row_active_set")
        private val CUSTOM_SYMBOL_SETS = stringPreferencesKey("custom_symbol_sets")
        private val BAR_ORDER = stringPreferencesKey("bar_order")
        private val EMOJI_FULL_BLEED = booleanPreferencesKey("emoji_full_bleed")
        private val MEDIA_FULL_BLEED = booleanPreferencesKey("media_full_bleed")
        private val MODE_TOOL_ORDER_EDITS = booleanPreferencesKey("mode_tool_order_edits")
        private val MODE_TOOL_ORDER_HINT = booleanPreferencesKey("mode_tool_order_hint")
        private val KEYBOARD_MODES = stringPreferencesKey("keyboard_modes")
        private val MODE_SEED_VERSION = intPreferencesKey("mode_seed_version")
        private val SMART_SUGGESTIONS = booleanPreferencesKey("smart_suggestions")
        private val SMART_CALC = booleanPreferencesKey("smart_calc")
        private val SMART_CURRENCY = booleanPreferencesKey("smart_currency")
        private val SMART_UNITS = booleanPreferencesKey("smart_units")
        private val SMART_TOOL_KEYWORDS = booleanPreferencesKey("smart_tool_keywords")
        private val TOOL_KEYWORDS = stringPreferencesKey("tool_keywords")
        private val CALC_DEGREES = booleanPreferencesKey("calc_degrees")
        private val CALC_PRECISION = intPreferencesKey("calc_precision")
        private val CURRENCY_FROM = stringPreferencesKey("currency_from")
        private val CURRENCY_TO = stringPreferencesKey("currency_to")
        private val PW_LENGTH = intPreferencesKey("pw_length")
        private val PW_UPPERCASE = booleanPreferencesKey("pw_uppercase")
        private val PW_DIGITS = booleanPreferencesKey("pw_digits")
        private val PW_SYMBOLS = booleanPreferencesKey("pw_symbols")
        private val PW_EXCLUDE_AMBIGUOUS = booleanPreferencesKey("pw_exclude_ambiguous")
        private val PW_PASSPHRASE_MODE = booleanPreferencesKey("pw_passphrase_mode")
        private val PP_WORD_COUNT = intPreferencesKey("pp_word_count")
        private val PP_SEPARATOR = stringPreferencesKey("pp_separator")
        private val PP_CAPITALIZE = booleanPreferencesKey("pp_capitalize")
        private val PP_INCLUDE_DIGIT = booleanPreferencesKey("pp_include_digit")
        private val TT_MODE = stringPreferencesKey("tt_mode")
        private val TT_DURATION = intPreferencesKey("tt_duration")
        private val TT_WORD_COUNT = intPreferencesKey("tt_word_count")
        private val TT_PUNCTUATION = booleanPreferencesKey("tt_punctuation")
        private val TT_NUMBERS = booleanPreferencesKey("tt_numbers")
        private val TT_BESTS = stringPreferencesKey("tt_bests")
        private val TT_HISTORY = stringPreferencesKey("tt_history")
        private val TT_COMPLETED = intPreferencesKey("tt_completed")
        private val QR_SIZE_PX = intPreferencesKey("qr_size_px")
        private val QR_ECC = stringPreferencesKey("qr_ecc")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val AI_ANTHROPIC_KEY = stringPreferencesKey("ai_anthropic_key")
        private val AI_OPENAI_KEY = stringPreferencesKey("ai_openai_key")
        private val AI_GEMINI_KEY = stringPreferencesKey("ai_gemini_key")
        private val AI_ANTHROPIC_MODEL = stringPreferencesKey("ai_anthropic_model")
        private val AI_OPENAI_MODEL = stringPreferencesKey("ai_openai_model")
        private val AI_GEMINI_MODEL = stringPreferencesKey("ai_gemini_model")
        private val AI_OLLAMA_URL = stringPreferencesKey("ai_ollama_url")
        private val AI_OLLAMA_MODEL = stringPreferencesKey("ai_ollama_model")
        private val AI_LM_STUDIO_URL = stringPreferencesKey("ai_lm_studio_url")
        private val AI_LM_STUDIO_MODEL = stringPreferencesKey("ai_lm_studio_model")
        private val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        private val AI_TRANSLATE_TO = stringPreferencesKey("ai_translate_to")
        private val AI_PROMPT_REWRITE = stringPreferencesKey("ai_prompt_rewrite")
        private val AI_PROMPT_SUMMARIZE = stringPreferencesKey("ai_prompt_summarize")
        private val AI_PROMPT_TRANSLATE = stringPreferencesKey("ai_prompt_translate")
        private val AI_PROMPT_IMPROVE = stringPreferencesKey("ai_prompt_improve")
        private val AI_PROMPT_FIX_GRAMMAR = stringPreferencesKey("ai_prompt_fix_grammar")
        private val AI_PROMPT_EXPLAIN = stringPreferencesKey("ai_prompt_explain")
        private val AI_PROMPT_CONTINUE = stringPreferencesKey("ai_prompt_continue")
        private val AI_LOCAL_MODEL_ID = stringPreferencesKey("ai_local_model_id")
        private val AI_LOCAL_BACKEND = stringPreferencesKey("ai_local_backend")
        private val HF_TOKEN = stringPreferencesKey("hf_token")
        private val AI_SHOW_THINKING = booleanPreferencesKey("ai_show_thinking")
        private val AI_PANEL_MODEL_PICKER = booleanPreferencesKey("ai_panel_model_picker")
    }

    val settings: Flow<KeyboardSettings> = context.dataStore.data.map { p ->
        val defaults = KeyboardSettings()
        // Layouts resolve first: the input mode is read off the active layout,
        // so it has to be known before the settings object is built. The
        // pre-registry migration lives in resolveLayoutSelection.
        val customLayouts = p[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) }
            ?: defaults.customLayouts
        val layoutSelection = resolveLayoutSelection(
            storedLayoutId = p[ACTIVE_LAYOUT_ID],
            storedInputMode = p[INPUT_MODE],
            storedEnabledLayoutIds = p[ENABLED_LAYOUT_IDS],
            storedEnabledModes = p[ENABLED_MODES],
            customLayouts = customLayouts,
            defaultActiveId = defaults.activeLayoutId,
            defaultEnabledIds = defaults.enabledLayoutIds,
        )
        KeyboardSettings(
            activeLayoutId = layoutSelection.active.id,
            enabledLayoutIds = layoutSelection.enabledLayoutIds,
            customLayouts = customLayouts,
            enabledLanguages = layoutSelection.enabledLanguages,
            secondaryLanguages = p[SECONDARY_LANGUAGES]?.let { decodeSecondaryLanguages(it) }
                ?: defaults.secondaryLanguages,
            language = layoutSelection.active.language(),
            script = layoutSelection.active.script(),
            themeMode = p[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            dynamicColor = p[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            keyboardThemeId = p[KEYBOARD_THEME_ID] ?: defaults.keyboardThemeId,
            customThemes = p[CUSTOM_THEMES]?.let { ThemeCodec.decodeList(it) }
                ?: defaults.customThemes,
            keyHeightDp = p[KEY_HEIGHT] ?: defaults.keyHeightDp,
            numberRowHeightDp = p[NUMBER_ROW_HEIGHT] ?: p[KEY_HEIGHT] ?: defaults.numberRowHeightDp,
            bottomPaddingDp = p[BOTTOM_PADDING] ?: defaults.bottomPaddingDp,
            splitKeyboard = p[SPLIT_KEYBOARD] ?: defaults.splitKeyboard,
            splitGapPercent = p[SPLIT_GAP_PERCENT] ?: defaults.splitGapPercent,
            floatingKeyboard = p[FLOATING_KEYBOARD] ?: defaults.floatingKeyboard,
            floatingWidthDp = p[FLOATING_WIDTH] ?: defaults.floatingWidthDp,
            floatingHeightScale = p[FLOATING_HEIGHT_SCALE] ?: defaults.floatingHeightScale,
            floatingXFraction = p[FLOATING_X] ?: defaults.floatingXFraction,
            floatingYFraction = p[FLOATING_Y] ?: defaults.floatingYFraction,
            keyboardWidthPercent = p[KEYBOARD_WIDTH_PERCENT] ?: defaults.keyboardWidthPercent,
            keyboardAlignment = p[KEYBOARD_ALIGNMENT]
                ?.let { runCatching { KeyboardAlignment.valueOf(it) }.getOrNull() }
                ?: defaults.keyboardAlignment,
            keyCornerRadiusDp = p[KEY_CORNER_RADIUS] ?: defaults.keyCornerRadiusDp,
            fontScale = p[FONT_SCALE] ?: defaults.fontScale,
            sizingOverrides = ScreenVariant.entries
                .filter { it.isOverride }
                .associateWith { v ->
                    SizingOverride(
                        keyHeightDp = p[keyHeightKey(v)],
                        numberRowHeightDp = p[numberRowHeightKey(v)],
                        bottomPaddingDp = p[bottomPaddingKey(v)],
                        keyboardWidthPercent = p[widthPercentKey(v)],
                        fontScale = p[fontScaleKey(v)],
                        keyboardAlignment = p[alignmentKey(v)]
                            ?.let { name -> runCatching { KeyboardAlignment.valueOf(name) }.getOrNull() },
                    )
                }
                .filterValues { !it.isEmpty },
            keyFontId = p[KEY_FONT_ID] ?: defaults.keyFontId,
            customFontName = p[CUSTOM_FONT_NAME] ?: defaults.customFontName,
            bengaliFontId = p[BENGALI_FONT_ID] ?: defaults.bengaliFontId,
            customBengaliFontName = p[CUSTOM_BENGALI_FONT_NAME]
                ?: defaults.customBengaliFontName,
            lexiconVersion = p[LEXICON_VERSION] ?: defaults.lexiconVersion,
            customDictVersion = p[CUSTOM_DICT_VERSION] ?: defaults.customDictVersion,
            emojiFont = p[EMOJI_FONT]
                ?.let { runCatching { EmojiFontChoice.valueOf(it) }.getOrNull() }
                ?: defaults.emojiFont,
            hapticFeedback = p[HAPTIC] ?: defaults.hapticFeedback,
            hapticStrengthMs = p[HAPTIC_STRENGTH] ?: defaults.hapticStrengthMs,
            hapticAmplitude = p[HAPTIC_AMPLITUDE] ?: defaults.hapticAmplitude,
            hapticStyle = p[HAPTIC_STYLE]?.let { runCatching { HapticStyle.valueOf(it) }.getOrNull() }
                ?: defaults.hapticStyle,
            hapticOnLongPress = p[HAPTIC_ON_LONG_PRESS] ?: defaults.hapticOnLongPress,
            hapticOnLongPressRelease = p[HAPTIC_ON_LONG_PRESS_RELEASE]
                ?: defaults.hapticOnLongPressRelease,
            keySound = p[KEY_SOUND] ?: defaults.keySound,
            keySoundStyle = p[KEY_SOUND_STYLE]
                ?.let { runCatching { KeySoundStyle.valueOf(it) }.getOrNull() }
                ?: defaults.keySoundStyle,
            keySoundVolume = p[KEY_SOUND_VOLUME] ?: defaults.keySoundVolume,
            keyPopup = p[KEY_POPUP] ?: defaults.keyPopup,
            keyPopupMinDurationMs = p[KEY_POPUP_MIN_DURATION] ?: defaults.keyPopupMinDurationMs,
            keyPopupMaxDurationMs = p[KEY_POPUP_MAX_DURATION] ?: defaults.keyPopupMaxDurationMs,
            keyPopupOnKey = p[KEY_POPUP_ON_KEY] ?: defaults.keyPopupOnKey,
            popupFontScale = p[POPUP_FONT_SCALE] ?: defaults.popupFontScale,
            keyPopupHeightDp = p[KEY_POPUP_HEIGHT] ?: defaults.keyPopupHeightDp,
            colorVisionFilter = p[COLOR_VISION_FILTER]
                ?.let { runCatching { ColorVisionFilter.valueOf(it) }.getOrNull() }
                ?: defaults.colorVisionFilter,
            highContrastKeys = p[HIGH_CONTRAST_KEYS] ?: defaults.highContrastKeys,
            keyOutlines = p[KEY_OUTLINES] ?: defaults.keyOutlines,
            boldKeyLabels = p[BOLD_KEY_LABELS] ?: defaults.boldKeyLabels,
            reduceMotion = p[REDUCE_MOTION] ?: defaults.reduceMotion,
            screenReaderMode = p[SCREEN_READER_MODE]
                ?.let { runCatching { ScreenReaderMode.valueOf(it) }.getOrNull() }
                ?: defaults.screenReaderMode,
            keyDebounceMs = p[KEY_DEBOUNCE_MS] ?: defaults.keyDebounceMs,
            numberRow = p[NUMBER_ROW] ?: defaults.numberRow,
            autocorrect = p[AUTOCORRECT] ?: defaults.autocorrect,
            autocorrectConfidence = p[AUTOCORRECT_CONFIDENCE] ?: defaults.autocorrectConfidence,
            revertAutocorrectOnBackspace =
                p[REVERT_AUTOCORRECT_ON_BACKSPACE] ?: defaults.revertAutocorrectOnBackspace,
            autoApostrophe = p[AUTO_APOSTROPHE] ?: defaults.autoApostrophe,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
            doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: defaults.doubleSpacePeriod,
            doubleSpaceTab = p[DOUBLE_SPACE_TAB] ?: defaults.doubleSpaceTab,
            suggestions = p[SUGGESTIONS] ?: defaults.suggestions,
            showSuggestionsInAllFields = p[SHOW_SUGGESTIONS_ALL_FIELDS]
                ?: defaults.showSuggestionsInAllFields,
            suggestionsFirst = p[SUGGESTIONS_FIRST] ?: defaults.suggestionsFirst,
            suggestionPrimaryCenter = p[SUGGESTION_PRIMARY_CENTER]
                ?: defaults.suggestionPrimaryCenter,
            contactSuggestions = p[CONTACT_SUGGESTIONS] ?: defaults.contactSuggestions,
            appNameSuggestions = p[APP_NAME_SUGGESTIONS] ?: defaults.appNameSuggestions,
            inlineEmojiSearch = p[INLINE_EMOJI_SEARCH] ?: defaults.inlineEmojiSearch,
            inlineAutofill = p[INLINE_AUTOFILL] ?: defaults.inlineAutofill,
            gestureTyping = p[GESTURE_TYPING] ?: defaults.gestureTyping,
            letterSwipeAction = p[LETTER_SWIPE_ACTION]
                ?.let { runCatching { LetterSwipeAction.valueOf(it) }.getOrNull() }
                ?: defaults.letterSwipeAction,
            spaceShortSwipe = p[SPACE_SHORT_SWIPE]
                ?.let { runCatching { SpaceSwipeAction.valueOf(it) }.getOrNull() }
                ?: defaults.spaceShortSwipe,
            // Users who had explicitly turned spacebar cursor control off
            // keep it off until they pick a new swipe action.
            spaceLongSwipe = p[SPACE_LONG_SWIPE]
                ?.let { runCatching { SpaceSwipeAction.valueOf(it) }.getOrNull() }
                ?: if (p[SPACEBAR_CURSOR] == false) SpaceSwipeAction.NONE else defaults.spaceLongSwipe,
            spacebarLanguageArrows = p[SPACEBAR_LANGUAGE_ARROWS]
                ?: defaults.spacebarLanguageArrows,
            spacebarLabel = p[SPACEBAR_LABEL] ?: defaults.spacebarLabel,
            backspaceSwipeDelete = p[BACKSPACE_SWIPE_DELETE] ?: defaults.backspaceSwipeDelete,
            volumeCursor = p[VOLUME_CURSOR] ?: defaults.volumeCursor,
            volumeCursorMediaAware = p[VOLUME_CURSOR_MEDIA_AWARE] ?: defaults.volumeCursorMediaAware,
            globeAsEmoji = p[GLOBE_AS_EMOJI] ?: defaults.globeAsEmoji,
            onboardingDone = p[ONBOARDING_DONE] ?: defaults.onboardingDone,
            conjunctBackspace = p[CONJUNCT_BACKSPACE] ?: defaults.conjunctBackspace,
            oneHandedMode = p[ONE_HANDED_MODE]
                ?.let { runCatching { OneHandedMode.valueOf(it) }.getOrNull() }
                ?: defaults.oneHandedMode,
            learnFromTyping = p[LEARN_FROM_TYPING] ?: defaults.learnFromTyping,
            clipboardHistory = p[CLIPBOARD_HISTORY] ?: defaults.clipboardHistory,
            clipboardExpiryHours = p[CLIPBOARD_EXPIRY_HOURS] ?: defaults.clipboardExpiryHours,
            clipboardLinkPreviews = p[CLIPBOARD_LINK_PREVIEWS] ?: defaults.clipboardLinkPreviews,
            clipboardTrackSource = p[CLIPBOARD_TRACK_SOURCE] ?: defaults.clipboardTrackSource,
            longPressDelayMs = p[LONG_PRESS_DELAY] ?: defaults.longPressDelayMs,
            keyRepeatIntervalMs = p[KEY_REPEAT_INTERVAL] ?: defaults.keyRepeatIntervalMs,
            longPressHints = p[LONG_PRESS_HINTS] ?: defaults.longPressHints,
            rawClipboardShortcuts = p[RAW_CLIPBOARD_SHORTCUTS] ?: defaults.rawClipboardShortcuts,
            longPressASelectAll = p[LONG_PRESS_A_SELECT_ALL] ?: defaults.longPressASelectAll,
            longPressCCopy = p[LONG_PRESS_C_COPY] ?: defaults.longPressCCopy,
            longPressVPaste = p[LONG_PRESS_V_PASTE] ?: defaults.longPressVPaste,
            longPressXCut = p[LONG_PRESS_X_CUT] ?: defaults.longPressXCut,
            emojiToolbar = p[EMOJI_TOOLBAR] ?: defaults.emojiToolbar,
            coloredToolIcons = p[COLORED_TOOL_ICONS] ?: defaults.coloredToolIcons,
            incognito = p[INCOGNITO] ?: defaults.incognito,
            // Empty stored string is a valid state (everything in the toolbox),
            // distinct from never-set (defaults apply).
            toolbarTools = p[TOOLBAR_TOOLS]?.let { csv ->
                if (csv.isEmpty()) emptyList()
                else csv.split(',').mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            } ?: defaults.toolbarTools,
            toolbarGreedy = p[TOOLBAR_GREEDY] ?: defaults.toolbarGreedy,
            toolbarHeightDp = p[TOOLBAR_HEIGHT] ?: defaults.toolbarHeightDp,
            toolbarScrollable = p[TOOLBAR_SCROLLABLE] ?: defaults.toolbarScrollable,
            toolbarLabels = p[TOOLBAR_LABELS] ?: defaults.toolbarLabels,
            toolbarLabelSize = p[TOOLBAR_LABEL_SIZE] ?: defaults.toolbarLabelSize,
            toolCircleRadiusDp = p[TOOL_CIRCLE_RADIUS] ?: defaults.toolCircleRadiusDp,
            commaAsEmoji = p[COMMA_AS_EMOJI] ?: defaults.commaAsEmoji,
            emojiTabMode = p[EMOJI_TAB_MODE]
                ?.let { runCatching { EmojiTabMode.valueOf(it) }.getOrNull() }
                ?: defaults.emojiTabMode,
            emojiClearRecentsButton = p[EMOJI_CLEAR_RECENTS_BUTTON] ?: defaults.emojiClearRecentsButton,
            emojiLongPressName = p[EMOJI_LONG_PRESS_NAME] ?: defaults.emojiLongPressName,
            emojiPrediction = p[EMOJI_PREDICTION] ?: defaults.emojiPrediction,
            emojiBarMode = p[EMOJI_BAR_MODE]
                ?.let { runCatching { EmojiBarMode.valueOf(it) }.getOrNull() }
                ?: defaults.emojiBarMode,
            emojiBarContent = p[EMOJI_BAR_CONTENT]
                ?.let { runCatching { EmojiBarContent.valueOf(it) }.getOrNull() }
                ?: defaults.emojiBarContent,
            emojiInsertMode = p[EMOJI_INSERT_MODE]
                ?.let { runCatching { EmojiInsertMode.valueOf(it) }.getOrNull() }
                ?: defaults.emojiInsertMode,
            enabledTools = ToolbarTool.entries - decodeDisabledTools(p[DISABLED_TOOLS]),
            toolboxOrder = decodeToolOrder(p[TOOLBOX_ORDER]),
            toolboxHintDismissed = p[TOOLBOX_HINT_DISMISSED] ?: defaults.toolboxHintDismissed,
            flashlightAutoOff = p[FLASHLIGHT_AUTO_OFF] ?: defaults.flashlightAutoOff,
            compassShowDegrees = p[COMPASS_SHOW_DEGREES] ?: defaults.compassShowDegrees,
            compassShowQibla = p[COMPASS_SHOW_QIBLA] ?: defaults.compassShowQibla,
            levelShowAngles = p[LEVEL_SHOW_ANGLES] ?: defaults.levelShowAngles,
            redoUsesCtrlY = p[REDO_USES_CTRL_Y] ?: defaults.redoUsesCtrlY,
            moonSouthernHemisphere = p[MOON_SOUTHERN] ?: defaults.moonSouthernHemisphere,
            weatherFahrenheit = p[WEATHER_FAHRENHEIT] ?: defaults.weatherFahrenheit,
            weatherLatitude = p[WEATHER_LAT],
            weatherLongitude = p[WEATHER_LON],
            weatherPlaceName = p[WEATHER_PLACE] ?: defaults.weatherPlaceName,
            calendarShowBengali = p[CALENDAR_SHOW_BENGALI] ?: defaults.calendarShowBengali,
            calendarShowHijri = p[CALENDAR_SHOW_HIJRI] ?: defaults.calendarShowHijri,
            hijriAdjustDays = p[HIJRI_ADJUST_DAYS] ?: defaults.hijriAdjustDays,
            handwritingStylusOnly = p[HANDWRITING_STYLUS_ONLY] ?: defaults.handwritingStylusOnly,
            handwritingCommitDelayMs = p[HANDWRITING_COMMIT_DELAY]
                ?: defaults.handwritingCommitDelayMs,
            handwritingAutoSpace = p[HANDWRITING_AUTO_SPACE] ?: defaults.handwritingAutoSpace,
            voiceStripMode = p[VOICE_STRIP_MODE] ?: defaults.voiceStripMode,
            voiceContinuous = p[VOICE_CONTINUOUS] ?: defaults.voiceContinuous,
            voiceSpokenPunctuation = p[VOICE_SPOKEN_PUNCTUATION]
                ?: defaults.voiceSpokenPunctuation,
            cameraPreferFront = p[CAMERA_PREFER_FRONT] ?: defaults.cameraPreferFront,
            cameraMirrorFront = p[CAMERA_MIRROR_FRONT] ?: defaults.cameraMirrorFront,
            cameraShutterSound = p[CAMERA_SHUTTER_SOUND] ?: defaults.cameraShutterSound,
            cameraHaptics = p[CAMERA_HAPTICS] ?: defaults.cameraHaptics,
            cameraSaveToGallery = p[CAMERA_SAVE_TO_GALLERY] ?: defaults.cameraSaveToGallery,
            docScanSaveToGallery = p[DOC_SCAN_SAVE_TO_GALLERY] ?: defaults.docScanSaveToGallery,
            qrSaveToGallery = p[QR_SAVE_TO_GALLERY] ?: defaults.qrSaveToGallery,
            stickerSendMode = p[STICKER_SEND_MODE]
                ?.let { runCatching { MediaSendMode.valueOf(it) }.getOrNull() }
                ?: defaults.stickerSendMode,
            gifSendMode = p[GIF_SEND_MODE]
                ?.let { runCatching { MediaSendMode.valueOf(it) }.getOrNull() }
                ?: defaults.gifSendMode,
            qrSendMode = p[QR_SEND_MODE]
                ?.let { runCatching { MediaSendMode.valueOf(it) }.getOrNull() }
                ?: defaults.qrSendMode,
            dictionaryAutoLookup = p[DICTIONARY_AUTO_LOOKUP] ?: defaults.dictionaryAutoLookup,
            textEditRepeatMs = p[TEXT_EDIT_REPEAT_MS] ?: defaults.textEditRepeatMs,
            numpadPhoneLayout = p[NUMPAD_PHONE_LAYOUT] ?: defaults.numpadPhoneLayout,
            incognitoPausesClipboard = p[INCOGNITO_PAUSES_CLIPBOARD] ?: defaults.incognitoPausesClipboard,
            incognitoPausesLearning = p[INCOGNITO_PAUSES_LEARNING] ?: defaults.incognitoPausesLearning,
            autoIncognito = p[AUTO_INCOGNITO] ?: defaults.autoIncognito,
            ocrAutoSelectWords = p[OCR_AUTO_SELECT_WORDS] ?: defaults.ocrAutoSelectWords,
            qrScanHaptics = p[QR_SCAN_HAPTICS] ?: defaults.qrScanHaptics,
            qrScanAutoInsert = p[QR_SCAN_AUTO_INSERT] ?: defaults.qrScanAutoInsert,
            qrScanLinkPreviews = p[QR_SCAN_LINK_PREVIEWS] ?: defaults.qrScanLinkPreviews,
            currencyDecimals = p[CURRENCY_DECIMALS] ?: defaults.currencyDecimals,
            currencyCacheHours = p[CURRENCY_CACHE_HOURS] ?: defaults.currencyCacheHours,
            grammarDebounceMs = p[GRAMMAR_DEBOUNCE_MS] ?: defaults.grammarDebounceMs,
            unitConvertLast = p[UNIT_CONVERT_LAST] ?: defaults.unitConvertLast,
            toolboxColumns = p[TOOLBOX_COLUMNS] ?: defaults.toolboxColumns,
            translateTargetLang = p[TRANSLATE_TARGET_LANG] ?: defaults.translateTargetLang,
            grammarDialect = p[GRAMMAR_DIALECT]
                ?.let { runCatching { GrammarDialect.valueOf(it) }.getOrNull() }
                ?: defaults.grammarDialect,
            translateApiKey = p[TRANSLATE_API_KEY] ?: defaults.translateApiKey,
            klipyApiKey = p[KLIPY_API_KEY] ?: defaults.klipyApiKey,
            braveApiKey = p[BRAVE_API_KEY] ?: defaults.braveApiKey,
            giphyApiKey = p[GIPHY_API_KEY] ?: defaults.giphyApiKey,
            gifSourceMode = p[GIF_SOURCE_MODE]
                ?.let { runCatching { GifSourceMode.valueOf(it) }.getOrNull() }
                ?: defaults.gifSourceMode,
            gifContentFilter = p[GIF_CONTENT_FILTER]
                ?.let { runCatching { GifContentFilter.valueOf(it) }.getOrNull() }
                ?: defaults.gifContentFilter,
            searchSafe = p[SEARCH_SAFE] ?: defaults.searchSafe,
            searchResultCount = p[SEARCH_RESULT_COUNT] ?: defaults.searchResultCount,
            wikiLanguage = p[WIKI_LANGUAGE] ?: defaults.wikiLanguage,
            wikiLinksMarkdown = p[WIKI_LINKS_MARKDOWN] ?: defaults.wikiLinksMarkdown,
            symbolRecents = p[SYMBOL_RECENTS]?.split('\t')?.filter { it.isNotEmpty() }
                ?: defaults.symbolRecents,
            symbolRowEnabled = p[SYMBOL_ROW_ENABLED] ?: defaults.symbolRowEnabled,
            symbolRowSetIds = p[SYMBOL_ROW_SETS]?.split('\t')?.filter { it.isNotEmpty() }
                ?.ifEmpty { null } ?: defaults.symbolRowSetIds,
            symbolRowActiveSetId = p[SYMBOL_ROW_ACTIVE_SET] ?: defaults.symbolRowActiveSetId,
            customSymbolSets = p[CUSTOM_SYMBOL_SETS]?.let { SymbolSetCodec.decodeList(it) }
                ?: defaults.customSymbolSets,
            // Never stored: honor the legacy emoji-row position toggle so
            // existing users keep their arrangement.
            barOrder = p[BAR_ORDER]
                ?.split(',')
                ?.mapNotNull { runCatching { BarRow.valueOf(it) }.getOrNull() }
                ?.let { sanitizeBarOrder(it) }
                ?: if (p[EMOJI_ROW_ABOVE_TOOLBAR] == true) {
                    listOf(BarRow.EMOJI, BarRow.TOPBAR, BarRow.SYMBOL)
                } else {
                    defaults.barOrder
                },
            emojiFullBleed = p[EMOJI_FULL_BLEED] ?: defaults.emojiFullBleed,
            mediaFullBleed = p[MEDIA_FULL_BLEED] ?: defaults.mediaFullBleed,
            modeToolOrderEdits = p[MODE_TOOL_ORDER_EDITS] ?: defaults.modeToolOrderEdits,
            modeToolOrderHintSeen = p[MODE_TOOL_ORDER_HINT] ?: defaults.modeToolOrderHintSeen,
            keyboardModes = p[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: defaults.keyboardModes,
            smartSuggestions = p[SMART_SUGGESTIONS] ?: defaults.smartSuggestions,
            smartCalc = p[SMART_CALC] ?: defaults.smartCalc,
            smartCurrency = p[SMART_CURRENCY] ?: defaults.smartCurrency,
            smartUnits = p[SMART_UNITS] ?: defaults.smartUnits,
            smartToolKeywords = p[SMART_TOOL_KEYWORDS] ?: defaults.smartToolKeywords,
            toolKeywords = p[TOOL_KEYWORDS] ?: defaults.toolKeywords,
            calcDegrees = p[CALC_DEGREES] ?: defaults.calcDegrees,
            calcPrecision = p[CALC_PRECISION] ?: defaults.calcPrecision,
            currencyFrom = p[CURRENCY_FROM] ?: defaults.currencyFrom,
            currencyTo = p[CURRENCY_TO] ?: defaults.currencyTo,
            pwLength = p[PW_LENGTH] ?: defaults.pwLength,
            pwUppercase = p[PW_UPPERCASE] ?: defaults.pwUppercase,
            pwDigits = p[PW_DIGITS] ?: defaults.pwDigits,
            pwSymbols = p[PW_SYMBOLS] ?: defaults.pwSymbols,
            pwExcludeAmbiguous = p[PW_EXCLUDE_AMBIGUOUS] ?: defaults.pwExcludeAmbiguous,
            pwPassphraseMode = p[PW_PASSPHRASE_MODE] ?: defaults.pwPassphraseMode,
            ppWordCount = p[PP_WORD_COUNT] ?: defaults.ppWordCount,
            ppSeparator = p[PP_SEPARATOR] ?: defaults.ppSeparator,
            ppCapitalize = p[PP_CAPITALIZE] ?: defaults.ppCapitalize,
            ppIncludeDigit = p[PP_INCLUDE_DIGIT] ?: defaults.ppIncludeDigit,
            typingTestMode = p[TT_MODE]?.let { runCatching { TypingTestMode.valueOf(it) }.getOrNull() }
                ?: defaults.typingTestMode,
            typingTestDuration = p[TT_DURATION] ?: defaults.typingTestDuration,
            typingTestWordCount = p[TT_WORD_COUNT] ?: defaults.typingTestWordCount,
            typingTestPunctuation = p[TT_PUNCTUATION] ?: defaults.typingTestPunctuation,
            typingTestNumbers = p[TT_NUMBERS] ?: defaults.typingTestNumbers,
            typingTestBests = p[TT_BESTS] ?: defaults.typingTestBests,
            typingTestHistory = p[TT_HISTORY] ?: defaults.typingTestHistory,
            typingTestsCompleted = p[TT_COMPLETED] ?: defaults.typingTestsCompleted,
            qrSizePx = p[QR_SIZE_PX] ?: defaults.qrSizePx,
            qrEcc = p[QR_ECC]?.let { runCatching { QrEccLevel.valueOf(it) }.getOrNull() }
                ?: defaults.qrEcc,
            aiProvider = p[AI_PROVIDER]
                ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
                ?: defaults.aiProvider,
            aiAnthropicKey = p[AI_ANTHROPIC_KEY] ?: defaults.aiAnthropicKey,
            aiOpenAiKey = p[AI_OPENAI_KEY] ?: defaults.aiOpenAiKey,
            aiGeminiKey = p[AI_GEMINI_KEY] ?: defaults.aiGeminiKey,
            aiAnthropicModel = p[AI_ANTHROPIC_MODEL] ?: defaults.aiAnthropicModel,
            aiOpenAiModel = p[AI_OPENAI_MODEL] ?: defaults.aiOpenAiModel,
            aiGeminiModel = p[AI_GEMINI_MODEL] ?: defaults.aiGeminiModel,
            aiOllamaUrl = p[AI_OLLAMA_URL] ?: defaults.aiOllamaUrl,
            aiOllamaModel = p[AI_OLLAMA_MODEL] ?: defaults.aiOllamaModel,
            aiLmStudioUrl = p[AI_LM_STUDIO_URL] ?: defaults.aiLmStudioUrl,
            aiLmStudioModel = p[AI_LM_STUDIO_MODEL] ?: defaults.aiLmStudioModel,
            aiMaxTokens = p[AI_MAX_TOKENS] ?: defaults.aiMaxTokens,
            aiTranslateTo = p[AI_TRANSLATE_TO] ?: defaults.aiTranslateTo,
            aiPromptRewrite = p[AI_PROMPT_REWRITE] ?: defaults.aiPromptRewrite,
            aiPromptSummarize = p[AI_PROMPT_SUMMARIZE] ?: defaults.aiPromptSummarize,
            aiPromptTranslate = p[AI_PROMPT_TRANSLATE] ?: defaults.aiPromptTranslate,
            aiPromptImprove = p[AI_PROMPT_IMPROVE] ?: defaults.aiPromptImprove,
            aiPromptFixGrammar = p[AI_PROMPT_FIX_GRAMMAR] ?: defaults.aiPromptFixGrammar,
            aiPromptExplain = p[AI_PROMPT_EXPLAIN] ?: defaults.aiPromptExplain,
            aiPromptContinue = p[AI_PROMPT_CONTINUE] ?: defaults.aiPromptContinue,
            aiLocalModelId = p[AI_LOCAL_MODEL_ID] ?: defaults.aiLocalModelId,
            aiLocalBackend = p[AI_LOCAL_BACKEND]
                ?.let { runCatching { LocalLlmBackend.valueOf(it) }.getOrNull() }
                ?: defaults.aiLocalBackend,
            hfToken = p[HF_TOKEN] ?: defaults.hfToken,
            aiShowThinking = p[AI_SHOW_THINKING] ?: defaults.aiShowThinking,
            aiPanelModelPicker = p[AI_PANEL_MODEL_PICKER] ?: defaults.aiPanelModelPicker,
        )
    }

    /**
     * Enables or disables one tool everywhere on the keyboard. Disabling
     * leaves [KeyboardSettings.toolbarTools] untouched — the toolbar just
     * skips disabled entries, so re-enabling restores the old position.
     */
    suspend fun setToolEnabled(tool: ToolbarTool, enabled: Boolean) =
        context.dataStore.edit { prefs ->
            val disabled = decodeDisabledTools(prefs[DISABLED_TOOLS])
            val next = if (enabled) disabled - tool else (disabled + tool).distinct()
            prefs[DISABLED_TOOLS] = next.joinToString(",") { it.name }
        }

    /** Replaces the whole enabled set at once (the onboarding tools page). */
    suspend fun setEnabledTools(enabled: Collection<ToolbarTool>) =
        context.dataStore.edit { prefs ->
            prefs[DISABLED_TOOLS] =
                (ToolbarTool.entries - enabled.toSet()).joinToString(",") { it.name }
        }

    suspend fun setToolboxOrder(order: List<ToolbarTool>) =
        context.dataStore.edit {
            it[TOOLBOX_ORDER] = order.distinct().joinToString(",") { tool -> tool.name }
        }

    suspend fun setToolboxHintDismissed(value: Boolean) =
        context.dataStore.edit { it[TOOLBOX_HINT_DISMISSED] = value }

    private fun decodeDisabledTools(csv: String?): List<ToolbarTool> =
        csv?.split(',')?.mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            ?: emptyList()

    /**
     * Stored order, made complete: tools the saved CSV doesn't know (added in
     * a later version, or a corrupt entry dropped) rejoin at their default
     * rank's relative position — slotted in right after the nearest
     * earlier-ranked tool the user already has, rather than all piled at the
     * very end. So a newly shipped tool lands somewhere sensible (e.g. next to
     * its peers) instead of always dead last, while nothing ever vanishes.
     */
    private fun decodeToolOrder(csv: String?): List<ToolbarTool> {
        val stored = csv?.split(',')
            ?.mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        if (stored.isEmpty()) return DefaultToolOrder
        val storedSet = stored.toSet()
        val result = stored.toMutableList()
        // Walk the default order so multiple new tools keep their relative rank;
        // each anchors after the last already-placed tool that outranks it.
        for (tool in DefaultToolOrder) {
            if (tool in storedSet) continue
            val rank = DefaultToolOrder.indexOf(tool)
            val anchor = DefaultToolOrder.take(rank).lastOrNull { it in result }
            val at = if (anchor == null) 0 else result.indexOf(anchor) + 1
            result.add(at, tool)
        }
        return result
    }

    suspend fun setFlashlightAutoOff(value: Boolean) =
        context.dataStore.edit { it[FLASHLIGHT_AUTO_OFF] = value }

    suspend fun setCompassShowDegrees(value: Boolean) =
        context.dataStore.edit { it[COMPASS_SHOW_DEGREES] = value }

    suspend fun setCompassShowQibla(value: Boolean) =
        context.dataStore.edit { it[COMPASS_SHOW_QIBLA] = value }

    suspend fun setKeySoundStyle(value: KeySoundStyle) =
        context.dataStore.edit { it[KEY_SOUND_STYLE] = value.name }

    suspend fun setKeySoundVolume(value: Float) =
        context.dataStore.edit { it[KEY_SOUND_VOLUME] = value.coerceIn(0.05f, 1f) }

    suspend fun setLevelShowAngles(value: Boolean) =
        context.dataStore.edit { it[LEVEL_SHOW_ANGLES] = value }

    suspend fun setRedoUsesCtrlY(value: Boolean) =
        context.dataStore.edit { it[REDO_USES_CTRL_Y] = value }

    suspend fun setMoonSouthernHemisphere(value: Boolean) =
        context.dataStore.edit { it[MOON_SOUTHERN] = value }

    suspend fun setWeatherFahrenheit(value: Boolean) =
        context.dataStore.edit { it[WEATHER_FAHRENHEIT] = value }

    /** Passing nulls clears the stored location. */
    suspend fun setWeatherLocation(latitude: Float?, longitude: Float?, place: String) =
        context.dataStore.edit { prefs ->
            if (latitude == null || longitude == null) {
                prefs.remove(WEATHER_LAT)
                prefs.remove(WEATHER_LON)
                prefs.remove(WEATHER_PLACE)
            } else {
                prefs[WEATHER_LAT] = latitude.coerceIn(-90f, 90f)
                prefs[WEATHER_LON] = longitude.coerceIn(-180f, 180f)
                prefs[WEATHER_PLACE] = place
            }
        }

    suspend fun setCalendarShowBengali(value: Boolean) =
        context.dataStore.edit { it[CALENDAR_SHOW_BENGALI] = value }

    suspend fun setCalendarShowHijri(value: Boolean) =
        context.dataStore.edit { it[CALENDAR_SHOW_HIJRI] = value }

    suspend fun setHijriAdjustDays(value: Int) =
        context.dataStore.edit { it[HIJRI_ADJUST_DAYS] = value.coerceIn(-2, 2) }

    suspend fun setHandwritingStylusOnly(value: Boolean) =
        context.dataStore.edit { it[HANDWRITING_STYLUS_ONLY] = value }

    suspend fun setHandwritingCommitDelayMs(value: Int) =
        context.dataStore.edit { it[HANDWRITING_COMMIT_DELAY] = value.coerceIn(300, 2000) }

    suspend fun setHandwritingAutoSpace(value: Boolean) =
        context.dataStore.edit { it[HANDWRITING_AUTO_SPACE] = value }

    suspend fun setVoiceStripMode(value: Boolean) =
        context.dataStore.edit { it[VOICE_STRIP_MODE] = value }

    suspend fun setVoiceContinuous(value: Boolean) =
        context.dataStore.edit { it[VOICE_CONTINUOUS] = value }

    suspend fun setVoiceSpokenPunctuation(value: Boolean) =
        context.dataStore.edit { it[VOICE_SPOKEN_PUNCTUATION] = value }

    suspend fun setCameraPreferFront(value: Boolean) =
        context.dataStore.edit { it[CAMERA_PREFER_FRONT] = value }

    suspend fun setCameraMirrorFront(value: Boolean) =
        context.dataStore.edit { it[CAMERA_MIRROR_FRONT] = value }

    suspend fun setCameraShutterSound(value: Boolean) =
        context.dataStore.edit { it[CAMERA_SHUTTER_SOUND] = value }

    suspend fun setCameraHaptics(value: Boolean) =
        context.dataStore.edit { it[CAMERA_HAPTICS] = value }

    suspend fun setCameraSaveToGallery(value: Boolean) =
        context.dataStore.edit { it[CAMERA_SAVE_TO_GALLERY] = value }

    suspend fun setDocScanSaveToGallery(value: Boolean) =
        context.dataStore.edit { it[DOC_SCAN_SAVE_TO_GALLERY] = value }

    suspend fun setQrSaveToGallery(value: Boolean) =
        context.dataStore.edit { it[QR_SAVE_TO_GALLERY] = value }

    suspend fun setStickerSendMode(value: MediaSendMode) =
        context.dataStore.edit { it[STICKER_SEND_MODE] = value.name }

    suspend fun setGifSendMode(value: MediaSendMode) =
        context.dataStore.edit { it[GIF_SEND_MODE] = value.name }

    suspend fun setQrSendMode(value: MediaSendMode) =
        context.dataStore.edit { it[QR_SEND_MODE] = value.name }

    suspend fun setTextEditRepeatMs(value: Int) =
        context.dataStore.edit { it[TEXT_EDIT_REPEAT_MS] = value.coerceIn(30, 200) }

    suspend fun setNumpadPhoneLayout(value: Boolean) =
        context.dataStore.edit { it[NUMPAD_PHONE_LAYOUT] = value }

    suspend fun setIncognitoPausesClipboard(value: Boolean) =
        context.dataStore.edit { it[INCOGNITO_PAUSES_CLIPBOARD] = value }

    suspend fun setIncognitoPausesLearning(value: Boolean) =
        context.dataStore.edit { it[INCOGNITO_PAUSES_LEARNING] = value }

    suspend fun setAutoIncognito(value: Boolean) =
        context.dataStore.edit { it[AUTO_INCOGNITO] = value }

    suspend fun setOcrAutoSelectWords(value: Boolean) =
        context.dataStore.edit { it[OCR_AUTO_SELECT_WORDS] = value }

    suspend fun setQrScanHaptics(value: Boolean) =
        context.dataStore.edit { it[QR_SCAN_HAPTICS] = value }

    suspend fun setQrScanAutoInsert(value: Boolean) =
        context.dataStore.edit { it[QR_SCAN_AUTO_INSERT] = value }

    suspend fun setQrScanLinkPreviews(value: Boolean) =
        context.dataStore.edit { it[QR_SCAN_LINK_PREVIEWS] = value }

    suspend fun setCurrencyDecimals(value: Int) =
        context.dataStore.edit { it[CURRENCY_DECIMALS] = value.coerceIn(0, 6) }

    suspend fun setCurrencyCacheHours(value: Int) =
        context.dataStore.edit { it[CURRENCY_CACHE_HOURS] = value.coerceIn(1, 48) }

    suspend fun setGrammarDebounceMs(value: Int) =
        context.dataStore.edit { it[GRAMMAR_DEBOUNCE_MS] = value.coerceIn(100, 1500) }

    suspend fun setUnitConvertLast(value: String) =
        context.dataStore.edit { it[UNIT_CONVERT_LAST] = value }

    suspend fun setDictionaryAutoLookup(value: Boolean) =
        context.dataStore.edit { it[DICTIONARY_AUTO_LOOKUP] = value }

    suspend fun setToolboxColumns(value: Int) =
        context.dataStore.edit { it[TOOLBOX_COLUMNS] = value.coerceIn(3, 6) }

    suspend fun setEmojiRowAboveToolbar(value: Boolean) =
        context.dataStore.edit { it[EMOJI_ROW_ABOVE_TOOLBAR] = value }

    suspend fun setToolbarTools(tools: List<ToolbarTool>) =
        context.dataStore.edit {
            it[TOOLBAR_TOOLS] = tools.distinct().joinToString(",") { tool -> tool.name }
        }

    suspend fun setToolbarGreedy(value: Boolean) =
        context.dataStore.edit { it[TOOLBAR_GREEDY] = value }

    suspend fun setToolbarHeightDp(value: Int) =
        context.dataStore.edit { it[TOOLBAR_HEIGHT] = value.coerceIn(32, 80) }

    suspend fun setToolbarScrollable(value: Boolean) =
        context.dataStore.edit { it[TOOLBAR_SCROLLABLE] = value }

    suspend fun setToolbarLabels(value: Boolean) =
        context.dataStore.edit { it[TOOLBAR_LABELS] = value }

    suspend fun setToolbarLabelSize(value: Int) =
        context.dataStore.edit { it[TOOLBAR_LABEL_SIZE] = value.coerceIn(7, 14) }

    suspend fun setToolCircleRadiusDp(value: Int) =
        context.dataStore.edit { it[TOOL_CIRCLE_RADIUS] = value.coerceIn(0, 20) }

    /**
     * Moving emoji onto the comma key also pulls the emoji tool off the
     * toolbar (it would be redundant); the user can drag it back from the
     * toolbox. Turning the setting off leaves the toolbar as-is.
     */
    suspend fun setCommaAsEmoji(value: Boolean) =
        context.dataStore.edit { prefs ->
            prefs[COMMA_AS_EMOJI] = value
            if (value) {
                val current = prefs[TOOLBAR_TOOLS]
                    ?.split(',')
                    ?.mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
                    ?: KeyboardSettings().toolbarTools
                prefs[TOOLBAR_TOOLS] = current.filter { it != ToolbarTool.EMOJI }
                    .joinToString(",") { it.name }
            }
        }

    /**
     * Switches the active layout.
     *
     * Repairs it on the way in — the one place that has to, along with import.
     * Ordinary saves deliberately do not, so the editor can hold a half-built
     * grid; but the moment a layout becomes the thing you type on it must have
     * a delete key, because you cannot fix the typo that lost you the key.
     */
    suspend fun setActiveLayoutId(id: String) =
        context.dataStore.edit { prefs ->
            val custom = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) } ?: emptyList()
            val stored = custom.firstOrNull { it.id == id }
            // resolveLayout falls back to the default, so an id whose layout was
            // deleted heals here rather than selecting nothing.
            val repaired = resolveLayout(custom, id).repair().spec
            // Only write back when there was a stored layout and the repair
            // actually changed it; an untouched built-in needs no write.
            if (stored != null && repaired != stored) {
                prefs[CUSTOM_LAYOUTS] =
                    LayoutCodec.encodeList(custom.filter { it.id != id } + repaired)
            }
            prefs[ACTIVE_LAYOUT_ID] = repaired.id
        }

    suspend fun setRawClipboardShortcuts(value: Boolean) =
        context.dataStore.edit { it[RAW_CLIPBOARD_SHORTCUTS] = value }

    /** Replaces the secondary-language map (primary langId → secondary langIds). */
    suspend fun setSecondaryLanguages(map: Map<String, List<String>>) =
        context.dataStore.edit { it[SECONDARY_LANGUAGES] = encodeSecondaryLanguages(map) }

    /** The layouts the 🌐 key cycles; an empty pick falls back to the default. */
    suspend fun setEnabledLayoutIds(ids: List<String>) =
        context.dataStore.edit { prefs ->
            val next = ids.distinct().ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) }
            prefs[ENABLED_LAYOUT_IDS] = next.joinToString(",")
        }

    /**
     * Adds a layout, or replaces the stored one with the same id.
     *
     * Deliberately does *not* repair. The editor saves on every keystroke, so
     * repairing here would re-add a delete key the instant the user removed a
     * row, and the undo stack would record the repaired grid rather than the one
     * being built. Repair belongs at the two moments the layout leaves the
     * user's hands: import, and [setActiveLayoutId].
     */
    suspend fun upsertCustomLayout(layout: LayoutSpec) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) } ?: emptyList()
            prefs[CUSTOM_LAYOUTS] =
                LayoutCodec.encodeList(current.filter { it.id != layout.id } + layout)
        }

    /**
     * Applies [transform] to the stored layout, reading it inside the same edit.
     *
     * The editor saves on every keystroke, and the layout it holds comes from
     * the settings flow, which lags the write it just made. Handing back a whole
     * layout built from that stale copy loses the previous edit whenever two land
     * within a frame of each other — type a label, nudge a width, and the label
     * comes back. Reading inside the edit makes each change apply to what is
     * actually stored.
     *
     * An id with no stored layout resolves to the built-in of that id, so the
     * first edit to an inherited built-in writes the override rather than
     * silently doing nothing.
     */
    suspend fun updateCustomLayout(id: String, transform: (LayoutSpec) -> LayoutSpec) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) } ?: emptyList()
            val next = transform(resolveLayout(current, id))
            prefs[CUSTOM_LAYOUTS] =
                LayoutCodec.encodeList(current.filter { it.id != next.id } + next)
        }

    /**
     * Deletes a custom layout and drops every reference to it.
     *
     * Deleting an *edited built-in* only removes the override — the shipped grid
     * comes back under the same id, so every reference to it stays valid, which
     * is why the reference cleanup below is skipped for those.
     */
    suspend fun deleteCustomLayout(id: String) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) } ?: emptyList()
            prefs[CUSTOM_LAYOUTS] = LayoutCodec.encodeList(current.filter { it.id != id })
            if (BuiltInLayouts.byId(id) != null) return@edit
            prefs[ENABLED_LAYOUT_IDS]?.let { stored ->
                val kept = stored.split(',')
                    .filter { it.isNotEmpty() && it != id }
                    .ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) }
                prefs[ENABLED_LAYOUT_IDS] = kept.joinToString(",")
            }
            if (prefs[ACTIVE_LAYOUT_ID] == id) {
                prefs[ACTIVE_LAYOUT_ID] = BuiltInLayouts.DEFAULT_ID
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[THEME_MODE] = mode.name }

    suspend fun setDynamicColor(value: Boolean) =
        context.dataStore.edit { it[DYNAMIC_COLOR] = value }

    suspend fun setKeyboardThemeId(id: String) =
        context.dataStore.edit { it[KEYBOARD_THEME_ID] = id }

    /** Adds the theme or replaces the stored theme with the same id. */
    suspend fun upsertCustomTheme(theme: ThemeSpec) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_THEMES]?.let { ThemeCodec.decodeList(it) } ?: emptyList()
            val next = current.filter { it.id != theme.id } + theme
            prefs[CUSTOM_THEMES] = ThemeCodec.encodeList(next)
        }

    /** Deletes a custom theme; falls back to the default theme if it was selected. */
    suspend fun deleteCustomTheme(id: String) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_THEMES]?.let { ThemeCodec.decodeList(it) } ?: emptyList()
            prefs[CUSTOM_THEMES] = ThemeCodec.encodeList(current.filter { it.id != id })
            if (prefs[KEYBOARD_THEME_ID] == id) prefs[KEYBOARD_THEME_ID] = DEFAULT_THEME_ID
        }

    suspend fun setKeyHeightDp(value: Int) =
        context.dataStore.edit { it[KEY_HEIGHT] = value.coerceIn(32, 100) }

    suspend fun setNumberRowHeightDp(value: Int) =
        context.dataStore.edit { it[NUMBER_ROW_HEIGHT] = value.coerceIn(32, 100) }

    suspend fun setSplitKeyboard(value: Boolean) =
        context.dataStore.edit { it[SPLIT_KEYBOARD] = value }

    suspend fun setSplitGapPercent(value: Int) =
        context.dataStore.edit { it[SPLIT_GAP_PERCENT] = value.coerceIn(5, 40) }

    suspend fun setFloatingKeyboard(value: Boolean) =
        context.dataStore.edit { it[FLOATING_KEYBOARD] = value }

    suspend fun setFloatingWidthDp(value: Int) =
        context.dataStore.edit { it[FLOATING_WIDTH] = value.coerceIn(240, 500) }

    /** Both axes from one resize-grip gesture, persisted in a single edit. */
    suspend fun setFloatingSize(widthDp: Int, heightScale: Float) =
        context.dataStore.edit {
            it[FLOATING_WIDTH] = widthDp.coerceIn(240, 500)
            it[FLOATING_HEIGHT_SCALE] = heightScale.coerceIn(0.6f, 1.6f)
        }

    suspend fun setFloatingPosition(x: Float, y: Float) =
        context.dataStore.edit {
            it[FLOATING_X] = x.coerceIn(0f, 1f)
            it[FLOATING_Y] = y.coerceIn(0f, 1f)
        }

    // ---- per-variant sizing ----
    //
    // Writing to PORTRAIT edits the base values (the plain settings), so the
    // variant editor and the ordinary sliders drive the same preferences
    // instead of shadowing each other. A null value clears the override and
    // lets the variant inherit portrait again.

    suspend fun setVariantKeyHeightDp(variant: ScreenVariant, value: Int?) =
        editVariant(variant, KEY_HEIGHT, keyHeightKey(variant), value?.coerceIn(32, 100))

    suspend fun setVariantNumberRowHeightDp(variant: ScreenVariant, value: Int?) =
        editVariant(
            variant, NUMBER_ROW_HEIGHT, numberRowHeightKey(variant), value?.coerceIn(32, 100),
        )

    suspend fun setVariantBottomPaddingDp(variant: ScreenVariant, value: Int?) =
        editVariant(variant, BOTTOM_PADDING, bottomPaddingKey(variant), value?.coerceIn(0, 40))

    suspend fun setVariantWidthPercent(variant: ScreenVariant, value: Int?) =
        editVariant(
            variant, KEYBOARD_WIDTH_PERCENT, widthPercentKey(variant), value?.coerceIn(50, 100),
        )

    suspend fun setVariantFontScale(variant: ScreenVariant, value: Float?) =
        editVariant(variant, FONT_SCALE, fontScaleKey(variant), value?.coerceIn(0.7f, 1.5f))

    suspend fun setVariantAlignment(variant: ScreenVariant, value: KeyboardAlignment?) =
        editVariant(variant, KEYBOARD_ALIGNMENT, alignmentKey(variant), value?.name)

    /** Clears every override on [variant], returning it to the portrait values. */
    suspend fun clearVariantSizing(variant: ScreenVariant) {
        if (!variant.isOverride) return
        context.dataStore.edit {
            it.remove(keyHeightKey(variant))
            it.remove(numberRowHeightKey(variant))
            it.remove(bottomPaddingKey(variant))
            it.remove(widthPercentKey(variant))
            it.remove(fontScaleKey(variant))
            it.remove(alignmentKey(variant))
        }
    }

    private suspend fun <T : Any> editVariant(
        variant: ScreenVariant,
        baseKey: Preferences.Key<T>,
        overrideKey: Preferences.Key<T>,
        value: T?,
    ) = context.dataStore.edit { prefs ->
        val key = if (variant.isOverride) overrideKey else baseKey
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    suspend fun setKeyboardWidthPercent(value: Int) =
        context.dataStore.edit { it[KEYBOARD_WIDTH_PERCENT] = value.coerceIn(50, 100) }

    suspend fun setKeyboardAlignment(value: KeyboardAlignment) =
        context.dataStore.edit { it[KEYBOARD_ALIGNMENT] = value.name }

    suspend fun setBottomPaddingDp(value: Int) =
        context.dataStore.edit { it[BOTTOM_PADDING] = value.coerceIn(0, 40) }

    suspend fun setKeyCornerRadiusDp(value: Int) =
        context.dataStore.edit { it[KEY_CORNER_RADIUS] = value.coerceIn(0, 28) }

    suspend fun setFontScale(value: Float) =
        context.dataStore.edit { it[FONT_SCALE] = value.coerceIn(0.7f, 1.5f) }

    suspend fun setKeyFontId(value: String) =
        context.dataStore.edit { it[KEY_FONT_ID] = value }

    /** Records both the imported file's display name and selects it. */
    suspend fun setCustomFont(name: String) =
        context.dataStore.edit {
            it[CUSTOM_FONT_NAME] = name
            it[KEY_FONT_ID] = "custom"
        }

    suspend fun setBengaliFontId(value: String) =
        context.dataStore.edit { it[BENGALI_FONT_ID] = value }

    /** Records the imported Bengali font's display name and selects it. */
    suspend fun setCustomBengaliFont(name: String) =
        context.dataStore.edit {
            it[CUSTOM_BENGALI_FONT_NAME] = name
            it[BENGALI_FONT_ID] = "custom_bn"
        }

    /** Signals the IME that the learned-words file changed on disk. */
    // ---- backup ----

    /** Serializes every stored preference; see [SettingsBackup]. */
    suspend fun exportSettings(
        includeSecrets: Boolean,
        appVersion: Int,
        appVersionName: String,
    ): String = SettingsBackup.encode(
        context.dataStore.data.first(),
        includeSecrets,
        appVersion,
        appVersionName,
    )

    sealed interface ImportResult {
        data class Applied(val settings: Int, val skipped: Int) : ImportResult
        /** The file parsed but left the app unable to read its own settings. */
        data object RolledBack : ImportResult
        data object NotABackup : ImportResult
    }

    /**
     * Merges a backup into the current settings: keys named in the file are
     * overwritten, everything else is left alone, so an old backup never
     * resets settings that did not exist when it was made.
     *
     * A hand-edited file can carry a value of the wrong type for its key
     * (a string where an Int is expected), which DataStore stores happily
     * and then throws on at read time — bricking the settings screen and
     * the keyboard with it. So the whole write is verified by reading the
     * settings back, and rolled back to the previous snapshot if that
     * fails.
     */
    suspend fun importSettings(text: String): ImportResult {
        val parsed = SettingsBackup.decode(text) ?: return ImportResult.NotABackup
        val snapshot = context.dataStore.data.first()
        context.dataStore.edit { prefs -> parsed.entries.forEach { prefs.put(it) } }
        val readable = runCatching { settings.first() }.isSuccess
        if (!readable) {
            context.dataStore.edit { prefs ->
                prefs.clear()
                for ((key, value) in snapshot.asMap()) {
                    @Suppress("UNCHECKED_CAST")
                    prefs[key as Preferences.Key<Any>] = value
                }
            }
            return ImportResult.RolledBack
        }
        return ImportResult.Applied(parsed.entries.size, parsed.skipped)
    }

    private fun MutablePreferences.put(entry: SettingsBackup.Entry) {
        when (entry.type) {
            "boolean" -> set(booleanPreferencesKey(entry.name), entry.value as Boolean)
            "int" -> set(intPreferencesKey(entry.name), entry.value as Int)
            "long" -> set(longPreferencesKey(entry.name), entry.value as Long)
            "float" -> set(floatPreferencesKey(entry.name), entry.value as Float)
            "double" -> set(doublePreferencesKey(entry.name), entry.value as Double)
            "string" -> set(stringPreferencesKey(entry.name), entry.value as String)
            "stringSet" -> {
                @Suppress("UNCHECKED_CAST")
                set(stringSetPreferencesKey(entry.name), entry.value as Set<String>)
            }
        }
    }

    suspend fun bumpLexiconVersion() =
        context.dataStore.edit { it[LEXICON_VERSION] = (it[LEXICON_VERSION] ?: 0) + 1 }

    suspend fun bumpCustomDictVersion() =
        context.dataStore.edit { it[CUSTOM_DICT_VERSION] = (it[CUSTOM_DICT_VERSION] ?: 0) + 1 }

    suspend fun setEmojiFont(value: EmojiFontChoice) =
        context.dataStore.edit { it[EMOJI_FONT] = value.name }

    suspend fun setAutoApostrophe(value: Boolean) =
        context.dataStore.edit { it[AUTO_APOSTROPHE] = value }

    suspend fun setHapticFeedback(value: Boolean) =
        context.dataStore.edit { it[HAPTIC] = value }

    suspend fun setHapticStrengthMs(value: Int) =
        context.dataStore.edit { it[HAPTIC_STRENGTH] = value.coerceIn(5, 60) }

    suspend fun setHapticAmplitude(value: Int) =
        context.dataStore.edit { it[HAPTIC_AMPLITUDE] = value.coerceIn(1, 255) }

    suspend fun setHapticStyle(value: HapticStyle) =
        context.dataStore.edit { it[HAPTIC_STYLE] = value.name }

    suspend fun setHapticOnLongPress(value: Boolean) =
        context.dataStore.edit { it[HAPTIC_ON_LONG_PRESS] = value }

    suspend fun setHapticOnLongPressRelease(value: Boolean) =
        context.dataStore.edit { it[HAPTIC_ON_LONG_PRESS_RELEASE] = value }

    suspend fun setKeySound(value: Boolean) =
        context.dataStore.edit { it[KEY_SOUND] = value }

    suspend fun setKeyPopup(value: Boolean) =
        context.dataStore.edit { it[KEY_POPUP] = value }

    suspend fun setKeyPopupMinDurationMs(value: Int) =
        context.dataStore.edit { it[KEY_POPUP_MIN_DURATION] = value.coerceIn(0, 300) }

    suspend fun setKeyPopupMaxDurationMs(value: Int) =
        context.dataStore.edit { it[KEY_POPUP_MAX_DURATION] = value.coerceIn(400, 2000) }

    suspend fun setKeyPopupOnKey(value: Boolean) =
        context.dataStore.edit { it[KEY_POPUP_ON_KEY] = value }

    suspend fun setPopupFontScale(value: Float) =
        context.dataStore.edit { it[POPUP_FONT_SCALE] = value.coerceIn(0.7f, 1.6f) }

    suspend fun setKeyPopupHeightDp(value: Int) =
        context.dataStore.edit { it[KEY_POPUP_HEIGHT] = value.coerceIn(32, 160) }

    // ---- accessibility ----

    suspend fun setColorVisionFilter(value: ColorVisionFilter) =
        context.dataStore.edit { it[COLOR_VISION_FILTER] = value.name }

    suspend fun setHighContrastKeys(value: Boolean) =
        context.dataStore.edit { it[HIGH_CONTRAST_KEYS] = value }

    suspend fun setKeyOutlines(value: Boolean) =
        context.dataStore.edit { it[KEY_OUTLINES] = value }

    suspend fun setBoldKeyLabels(value: Boolean) =
        context.dataStore.edit { it[BOLD_KEY_LABELS] = value }

    suspend fun setReduceMotion(value: Boolean) =
        context.dataStore.edit { it[REDUCE_MOTION] = value }

    suspend fun setScreenReaderMode(value: ScreenReaderMode) =
        context.dataStore.edit { it[SCREEN_READER_MODE] = value.name }

    suspend fun setKeyDebounceMs(value: Int) =
        context.dataStore.edit { it[KEY_DEBOUNCE_MS] = value.coerceIn(0, 500) }

    suspend fun setNumberRow(value: Boolean) =
        context.dataStore.edit { it[NUMBER_ROW] = value }

    suspend fun setAutocorrect(value: Boolean) =
        context.dataStore.edit { it[AUTOCORRECT] = value }

    /** Bounds mirror `SuggestionEngine.MIN/MAX_AUTOCORRECT_CONFIDENCE`. */
    suspend fun setAutocorrectConfidence(value: Float) =
        context.dataStore.edit { it[AUTOCORRECT_CONFIDENCE] = value.coerceIn(1.5f, 10f) }

    suspend fun setRevertAutocorrectOnBackspace(value: Boolean) =
        context.dataStore.edit { it[REVERT_AUTOCORRECT_ON_BACKSPACE] = value }

    suspend fun setAutoCapitalize(value: Boolean) =
        context.dataStore.edit { it[AUTO_CAPITALIZE] = value }

    suspend fun setDoubleSpacePeriod(value: Boolean) =
        context.dataStore.edit { it[DOUBLE_SPACE_PERIOD] = value }

    suspend fun setDoubleSpaceTab(value: Boolean) =
        context.dataStore.edit { it[DOUBLE_SPACE_TAB] = value }

    suspend fun setSuggestions(value: Boolean) =
        context.dataStore.edit { it[SUGGESTIONS] = value }

    suspend fun setShowSuggestionsInAllFields(value: Boolean) =
        context.dataStore.edit { it[SHOW_SUGGESTIONS_ALL_FIELDS] = value }

    suspend fun setSuggestionsFirst(value: Boolean) =
        context.dataStore.edit { it[SUGGESTIONS_FIRST] = value }

    suspend fun setSuggestionPrimaryCenter(value: Boolean) =
        context.dataStore.edit { it[SUGGESTION_PRIMARY_CENTER] = value }

    suspend fun setContactSuggestions(value: Boolean) =
        context.dataStore.edit { it[CONTACT_SUGGESTIONS] = value }

    suspend fun setAppNameSuggestions(value: Boolean) =
        context.dataStore.edit { it[APP_NAME_SUGGESTIONS] = value }

    suspend fun setInlineEmojiSearch(value: Boolean) =
        context.dataStore.edit { it[INLINE_EMOJI_SEARCH] = value }

    suspend fun setInlineAutofill(value: Boolean) =
        context.dataStore.edit { it[INLINE_AUTOFILL] = value }

    suspend fun setGestureTyping(value: Boolean) =
        context.dataStore.edit { it[GESTURE_TYPING] = value }

    suspend fun setLetterSwipeAction(value: LetterSwipeAction) =
        context.dataStore.edit { it[LETTER_SWIPE_ACTION] = value.name }

    suspend fun setSpaceShortSwipe(value: SpaceSwipeAction) =
        context.dataStore.edit { it[SPACE_SHORT_SWIPE] = value.name }

    suspend fun setSpaceLongSwipe(value: SpaceSwipeAction) =
        context.dataStore.edit { it[SPACE_LONG_SWIPE] = value.name }

    suspend fun setSpacebarLanguageArrows(value: Boolean) =
        context.dataStore.edit { it[SPACEBAR_LANGUAGE_ARROWS] = value }

    suspend fun setSpacebarLabel(value: String) =
        context.dataStore.edit { it[SPACEBAR_LABEL] = value.trim() }

    suspend fun setBackspaceSwipeDelete(value: Boolean) =
        context.dataStore.edit { it[BACKSPACE_SWIPE_DELETE] = value }

    suspend fun setVolumeCursor(value: Boolean) =
        context.dataStore.edit { it[VOLUME_CURSOR] = value }

    suspend fun setVolumeCursorMediaAware(value: Boolean) =
        context.dataStore.edit { it[VOLUME_CURSOR_MEDIA_AWARE] = value }

    suspend fun setGlobeAsEmoji(value: Boolean) =
        context.dataStore.edit { it[GLOBE_AS_EMOJI] = value }

    suspend fun setOnboardingDone(value: Boolean) =
        context.dataStore.edit { it[ONBOARDING_DONE] = value }

    suspend fun setConjunctBackspace(value: Boolean) =
        context.dataStore.edit { it[CONJUNCT_BACKSPACE] = value }

    suspend fun setOneHandedMode(value: OneHandedMode) =
        context.dataStore.edit { it[ONE_HANDED_MODE] = value.name }

    suspend fun setLearnFromTyping(value: Boolean) =
        context.dataStore.edit { it[LEARN_FROM_TYPING] = value }

    suspend fun setClipboardHistory(value: Boolean) =
        context.dataStore.edit { it[CLIPBOARD_HISTORY] = value }

    suspend fun setClipboardExpiryHours(value: Int) =
        context.dataStore.edit { it[CLIPBOARD_EXPIRY_HOURS] = value.coerceIn(0, 24 * 7) }

    suspend fun setClipboardLinkPreviews(value: Boolean) =
        context.dataStore.edit { it[CLIPBOARD_LINK_PREVIEWS] = value }

    suspend fun setClipboardTrackSource(value: Boolean) =
        context.dataStore.edit { it[CLIPBOARD_TRACK_SOURCE] = value }

    suspend fun setLongPressDelayMs(value: Int) =
        context.dataStore.edit { it[LONG_PRESS_DELAY] = value.coerceIn(150, 700) }

    suspend fun setKeyRepeatIntervalMs(value: Int) =
        context.dataStore.edit { it[KEY_REPEAT_INTERVAL] = value.coerceIn(20, 200) }

    suspend fun setLongPressHints(value: Boolean) =
        context.dataStore.edit { it[LONG_PRESS_HINTS] = value }

    suspend fun setLongPressASelectAll(value: Boolean) =
        context.dataStore.edit { it[LONG_PRESS_A_SELECT_ALL] = value }

    suspend fun setLongPressCCopy(value: Boolean) =
        context.dataStore.edit { it[LONG_PRESS_C_COPY] = value }

    suspend fun setLongPressVPaste(value: Boolean) =
        context.dataStore.edit { it[LONG_PRESS_V_PASTE] = value }

    suspend fun setLongPressXCut(value: Boolean) =
        context.dataStore.edit { it[LONG_PRESS_X_CUT] = value }

    suspend fun setEmojiToolbar(value: Boolean) =
        context.dataStore.edit { it[EMOJI_TOOLBAR] = value }

    suspend fun setColoredToolIcons(value: Boolean) =
        context.dataStore.edit { it[COLORED_TOOL_ICONS] = value }

    suspend fun setEmojiTabMode(value: EmojiTabMode) =
        context.dataStore.edit { it[EMOJI_TAB_MODE] = value.name }

    suspend fun setEmojiClearRecentsButton(value: Boolean) =
        context.dataStore.edit { it[EMOJI_CLEAR_RECENTS_BUTTON] = value }

    suspend fun setEmojiLongPressName(value: Boolean) =
        context.dataStore.edit { it[EMOJI_LONG_PRESS_NAME] = value }

    suspend fun setEmojiPrediction(value: Boolean) =
        context.dataStore.edit { it[EMOJI_PREDICTION] = value }

    suspend fun setEmojiBarMode(value: EmojiBarMode) =
        context.dataStore.edit { it[EMOJI_BAR_MODE] = value.name }

    suspend fun setEmojiBarContent(value: EmojiBarContent) =
        context.dataStore.edit { it[EMOJI_BAR_CONTENT] = value.name }

    suspend fun setEmojiInsertMode(value: EmojiInsertMode) =
        context.dataStore.edit { it[EMOJI_INSERT_MODE] = value.name }

    suspend fun setIncognito(value: Boolean) =
        context.dataStore.edit { it[INCOGNITO] = value }

    suspend fun setTranslateTargetLang(value: String) =
        context.dataStore.edit { it[TRANSLATE_TARGET_LANG] = value }

    suspend fun setGrammarDialect(value: GrammarDialect) =
        context.dataStore.edit { it[GRAMMAR_DIALECT] = value.name }

    suspend fun setTranslateApiKey(value: String) =
        context.dataStore.edit { it[TRANSLATE_API_KEY] = value.trim() }

    suspend fun setKlipyApiKey(value: String) =
        context.dataStore.edit { it[KLIPY_API_KEY] = value.trim() }

    suspend fun setBraveApiKey(value: String) =
        context.dataStore.edit { it[BRAVE_API_KEY] = value.trim() }

    suspend fun setGiphyApiKey(value: String) =
        context.dataStore.edit { it[GIPHY_API_KEY] = value.trim() }

    suspend fun setGifSourceMode(value: GifSourceMode) =
        context.dataStore.edit { it[GIF_SOURCE_MODE] = value.name }

    suspend fun setGifContentFilter(value: GifContentFilter) =
        context.dataStore.edit { it[GIF_CONTENT_FILTER] = value.name }

    suspend fun setSearchSafe(value: Boolean) =
        context.dataStore.edit { it[SEARCH_SAFE] = value }

    suspend fun setSearchResultCount(value: Int) =
        context.dataStore.edit { it[SEARCH_RESULT_COUNT] = value.coerceIn(1, 10) }

    suspend fun setWikiLanguage(value: String) =
        context.dataStore.edit { it[WIKI_LANGUAGE] = value.trim().lowercase() }

    suspend fun setWikiLinksMarkdown(value: Boolean) =
        context.dataStore.edit { it[WIKI_LINKS_MARKDOWN] = value }

    /** Pushes one symbol to the front of the recents row (capped, deduped). */
    suspend fun addSymbolRecent(symbol: String) =
        context.dataStore.edit { prefs ->
            val current = prefs[SYMBOL_RECENTS]?.split('\t')?.filter { it.isNotEmpty() }
                ?: emptyList()
            prefs[SYMBOL_RECENTS] =
                (listOf(symbol) + current.filter { it != symbol }).take(24).joinToString("\t")
        }

    suspend fun clearSymbolRecents() =
        context.dataStore.edit { it.remove(SYMBOL_RECENTS) }

    suspend fun setSymbolRowEnabled(value: Boolean) =
        context.dataStore.edit { it[SYMBOL_ROW_ENABLED] = value }

    /** The sets the row's picker offers; an empty pick falls back to defaults. */
    suspend fun setSymbolRowSetIds(ids: List<String>) =
        context.dataStore.edit { it[SYMBOL_ROW_SETS] = ids.distinct().joinToString("\t") }

    suspend fun setSymbolRowActiveSet(id: String) =
        context.dataStore.edit { it[SYMBOL_ROW_ACTIVE_SET] = id }

    /** Adds the set or replaces the stored set with the same id. */
    suspend fun upsertSymbolSet(set: SymbolSet) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_SYMBOL_SETS]?.let { SymbolSetCodec.decodeList(it) }
                ?: emptyList()
            val next = current.filter { it.id != set.id } + set
            prefs[CUSTOM_SYMBOL_SETS] = SymbolSetCodec.encodeList(next)
        }

    /** Deletes a custom set and drops every reference to it. */
    suspend fun deleteSymbolSet(id: String) =
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_SYMBOL_SETS]?.let { SymbolSetCodec.decodeList(it) }
                ?: emptyList()
            prefs[CUSTOM_SYMBOL_SETS] = SymbolSetCodec.encodeList(current.filter { it.id != id })
            // Deleting an edited built-in only drops the override — the
            // shipped set comes back, so every reference to it stays valid.
            if (BuiltInSymbolSets.byId(id) != null) return@edit
            prefs[SYMBOL_ROW_SETS]?.let { stored ->
                prefs[SYMBOL_ROW_SETS] = stored.split('\t').filter { it.isNotEmpty() && it != id }
                    .joinToString("\t")
            }
            if (prefs[SYMBOL_ROW_ACTIVE_SET] == id) prefs.remove(SYMBOL_ROW_ACTIVE_SET)
            // Modes referencing the set inherit the global sets again.
            prefs[KEYBOARD_MODES]?.let { stored ->
                val modes = KeyboardModeCodec.decodeList(stored).map { mode ->
                    val kept = mode.symbolSetIds?.filter { it != id }
                    mode.copy(symbolSetIds = kept?.ifEmpty { null })
                }
                prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(modes)
            }
        }

    suspend fun setBarOrder(rows: List<BarRow>) =
        context.dataStore.edit {
            it[BAR_ORDER] = sanitizeBarOrder(rows).joinToString(",") { row -> row.name }
        }

    suspend fun setEmojiFullBleed(value: Boolean) =
        context.dataStore.edit { it[EMOJI_FULL_BLEED] = value }

    suspend fun setMediaFullBleed(value: Boolean) =
        context.dataStore.edit { it[MEDIA_FULL_BLEED] = value }

    suspend fun setModeToolOrderEdits(value: Boolean) =
        context.dataStore.edit { it[MODE_TOOL_ORDER_EDITS] = value }

    suspend fun setModeToolOrderHintSeen(value: Boolean) =
        context.dataStore.edit { it[MODE_TOOL_ORDER_HINT] = value }

    /**
     * Rewrites one mode's pinned toolbar, so a drag made while that mode is
     * active lands where it will actually be read back from. Pinning into a
     * mode that was appending its tools switches it to replacing them: the
     * dragged arrangement is the whole bar the user just laid out, and
     * re-appending would shuffle it behind the global pins.
     */
    suspend fun setModeToolbarTools(modeId: String, tools: List<ToolbarTool>) =
        context.dataStore.edit { prefs ->
            val modes = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: DefaultKeyboardModes
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(
                modes.map { mode ->
                    if (mode.id == modeId) {
                        mode.copy(toolbarTools = tools.distinct(), toolbarToolsAppend = false)
                    } else {
                        mode
                    }
                }
            )
        }

    /**
     * Rewrites one mode's toolbox order. A mode stores only the tools it
     * floats to the front, so the full dragged order is stored as-is and the
     * global order stays the tiebreaker for anything the mode never names.
     */
    suspend fun setModeToolboxOrder(modeId: String, order: List<ToolbarTool>) =
        context.dataStore.edit { prefs ->
            val modes = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: DefaultKeyboardModes
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(
                modes.map { mode ->
                    if (mode.id == modeId) mode.copy(toolboxOrder = order.distinct()) else mode
                }
            )
        }

    /**
     * Adds default modes introduced after this install first ran. Fresh
     * installs get the whole list from [DefaultKeyboardModes]; an upgrade has
     * a stored list frozen at whatever shipped back then, so the new modes
     * would never appear. [MODE_SEED_VERSION] records how far the stored list
     * has been topped up — bump it whenever [DefaultKeyboardModes] grows, and
     * a mode the user deleted stays deleted because its version is already
     * covered. Idempotent; safe to call on every start.
     */
    suspend fun seedNewDefaultModes() =
        context.dataStore.edit { prefs ->
            val seeded = prefs[MODE_SEED_VERSION] ?: 0
            if (seeded >= CurrentModeSeedVersion) return@edit
            val stored = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
            prefs[MODE_SEED_VERSION] = CurrentModeSeedVersion
            // No stored list at all: the read path already falls back to the
            // full defaults, so there is nothing to top up.
            if (stored == null) return@edit
            val have = stored.map { it.id }.toSet()
            val missing = DefaultKeyboardModes.filter {
                it.id !in have && it.id in ModesAddedInSeedVersion2
            }
            if (missing.isEmpty()) return@edit
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(stored + missing)
        }

    /** Adds the mode or replaces the stored mode with the same id. */
    suspend fun upsertKeyboardMode(mode: KeyboardMode) =
        context.dataStore.edit { prefs ->
            val current = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: DefaultKeyboardModes
            val next =
                if (current.any { it.id == mode.id }) current.map { if (it.id == mode.id) mode else it }
                else current + mode
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(next)
        }

    suspend fun deleteKeyboardMode(id: String) =
        context.dataStore.edit { prefs ->
            val current = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: DefaultKeyboardModes
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(current.filter { it.id != id })
        }

    suspend fun setSmartSuggestions(value: Boolean) =
        context.dataStore.edit { it[SMART_SUGGESTIONS] = value }

    suspend fun setSmartCalc(value: Boolean) =
        context.dataStore.edit { it[SMART_CALC] = value }

    suspend fun setSmartCurrency(value: Boolean) =
        context.dataStore.edit { it[SMART_CURRENCY] = value }

    suspend fun setSmartUnits(value: Boolean) =
        context.dataStore.edit { it[SMART_UNITS] = value }

    suspend fun setSmartToolKeywords(value: Boolean) =
        context.dataStore.edit { it[SMART_TOOL_KEYWORDS] = value }

    /** Replaces one tool's trigger words; an empty list silences that tool. */
    suspend fun setToolKeywords(tool: ToolbarTool, words: List<String>) =
        context.dataStore.edit {
            it[TOOL_KEYWORDS] = SmartSuggest.withKeywords(it[TOOL_KEYWORDS].orEmpty(), tool, words)
        }

    suspend fun setCalcDegrees(value: Boolean) =
        context.dataStore.edit { it[CALC_DEGREES] = value }

    suspend fun setCalcPrecision(value: Int) =
        context.dataStore.edit { it[CALC_PRECISION] = value.coerceIn(0, 12) }

    suspend fun setCurrencyPair(from: String, to: String) =
        context.dataStore.edit {
            it[CURRENCY_FROM] = from.trim().uppercase()
            it[CURRENCY_TO] = to.trim().uppercase()
        }

    suspend fun setPwLength(value: Int) =
        context.dataStore.edit { it[PW_LENGTH] = value.coerceIn(4, 64) }

    suspend fun setPwUppercase(value: Boolean) =
        context.dataStore.edit { it[PW_UPPERCASE] = value }

    suspend fun setPwDigits(value: Boolean) =
        context.dataStore.edit { it[PW_DIGITS] = value }

    suspend fun setPwSymbols(value: Boolean) =
        context.dataStore.edit { it[PW_SYMBOLS] = value }

    suspend fun setPwExcludeAmbiguous(value: Boolean) =
        context.dataStore.edit { it[PW_EXCLUDE_AMBIGUOUS] = value }

    suspend fun setPwPassphraseMode(value: Boolean) =
        context.dataStore.edit { it[PW_PASSPHRASE_MODE] = value }

    suspend fun setPpWordCount(value: Int) =
        context.dataStore.edit { it[PP_WORD_COUNT] = value.coerceIn(2, 10) }

    suspend fun setPpSeparator(value: String) =
        context.dataStore.edit { it[PP_SEPARATOR] = value.take(3) }

    suspend fun setPpCapitalize(value: Boolean) =
        context.dataStore.edit { it[PP_CAPITALIZE] = value }

    suspend fun setPpIncludeDigit(value: Boolean) =
        context.dataStore.edit { it[PP_INCLUDE_DIGIT] = value }

    suspend fun setTypingTestMode(value: TypingTestMode) =
        context.dataStore.edit { it[TT_MODE] = value.name }

    suspend fun setTypingTestDuration(value: Int) =
        context.dataStore.edit { it[TT_DURATION] = value.coerceIn(5, 600) }

    suspend fun setTypingTestWordCount(value: Int) =
        context.dataStore.edit { it[TT_WORD_COUNT] = value.coerceIn(5, 500) }

    suspend fun setTypingTestPunctuation(value: Boolean) =
        context.dataStore.edit { it[TT_PUNCTUATION] = value }

    suspend fun setTypingTestNumbers(value: Boolean) =
        context.dataStore.edit { it[TT_NUMBERS] = value }

    /**
     * Files a finished run: appends it to the history, bumps the counter,
     * and stores a new personal best when [bests] is non-null (the caller
     * has already checked whether the record fell).
     */
    suspend fun recordTypingResult(history: String, bests: String?) =
        context.dataStore.edit { p ->
            p[TT_HISTORY] = history
            if (bests != null) p[TT_BESTS] = bests
            p[TT_COMPLETED] = (p[TT_COMPLETED] ?: 0) + 1
        }

    /** Wipes the personal bests and the score history. */
    suspend fun clearTypingStats() =
        context.dataStore.edit {
            it[TT_BESTS] = ""
            it[TT_HISTORY] = ""
            it[TT_COMPLETED] = 0
        }

    suspend fun setQrSizePx(value: Int) =
        context.dataStore.edit { it[QR_SIZE_PX] = value.coerceIn(256, 2048) }

    suspend fun setQrEcc(value: QrEccLevel) =
        context.dataStore.edit { it[QR_ECC] = value.name }

    suspend fun setAiProvider(value: AiProvider) =
        context.dataStore.edit { it[AI_PROVIDER] = value.name }

    suspend fun setAiAnthropicKey(value: String) =
        context.dataStore.edit { it[AI_ANTHROPIC_KEY] = value.trim() }

    suspend fun setAiOpenAiKey(value: String) =
        context.dataStore.edit { it[AI_OPENAI_KEY] = value.trim() }

    suspend fun setAiGeminiKey(value: String) =
        context.dataStore.edit { it[AI_GEMINI_KEY] = value.trim() }

    suspend fun setAiAnthropicModel(value: String) =
        context.dataStore.edit { it[AI_ANTHROPIC_MODEL] = value.trim() }

    suspend fun setAiOpenAiModel(value: String) =
        context.dataStore.edit { it[AI_OPENAI_MODEL] = value.trim() }

    suspend fun setAiGeminiModel(value: String) =
        context.dataStore.edit { it[AI_GEMINI_MODEL] = value.trim() }

    suspend fun setAiOllamaUrl(value: String) =
        context.dataStore.edit { it[AI_OLLAMA_URL] = value.trim().trimEnd('/') }

    suspend fun setAiOllamaModel(value: String) =
        context.dataStore.edit { it[AI_OLLAMA_MODEL] = value.trim() }

    suspend fun setAiLmStudioUrl(value: String) =
        context.dataStore.edit { it[AI_LM_STUDIO_URL] = value.trim().trimEnd('/') }

    suspend fun setAiLmStudioModel(value: String) =
        context.dataStore.edit { it[AI_LM_STUDIO_MODEL] = value.trim() }

    suspend fun setAiMaxTokens(value: Int) =
        context.dataStore.edit { it[AI_MAX_TOKENS] = value.coerceIn(64, 8192) }

    suspend fun setAiTranslateTo(value: String) =
        context.dataStore.edit { it[AI_TRANSLATE_TO] = value.trim() }

    suspend fun setAiPrompt(action: AiAction, value: String) =
        context.dataStore.edit {
            val key = when (action) {
                AiAction.REWRITE -> AI_PROMPT_REWRITE
                AiAction.SUMMARIZE -> AI_PROMPT_SUMMARIZE
                AiAction.TRANSLATE -> AI_PROMPT_TRANSLATE
                AiAction.IMPROVE -> AI_PROMPT_IMPROVE
                AiAction.FIX_GRAMMAR -> AI_PROMPT_FIX_GRAMMAR
                AiAction.EXPLAIN -> AI_PROMPT_EXPLAIN
                AiAction.CONTINUE -> AI_PROMPT_CONTINUE
            }
            it[key] = value
        }

    suspend fun setAiLocalModelId(value: String) =
        context.dataStore.edit { it[AI_LOCAL_MODEL_ID] = value }

    suspend fun setAiLocalBackend(value: LocalLlmBackend) =
        context.dataStore.edit { it[AI_LOCAL_BACKEND] = value.name }

    suspend fun setHfToken(value: String) =
        context.dataStore.edit { it[HF_TOKEN] = value.trim() }

    suspend fun setAiShowThinking(value: Boolean) =
        context.dataStore.edit { it[AI_SHOW_THINKING] = value }

    suspend fun setAiPanelModelPicker(value: Boolean) =
        context.dataStore.edit { it[AI_PANEL_MODEL_PICKER] = value }
}
