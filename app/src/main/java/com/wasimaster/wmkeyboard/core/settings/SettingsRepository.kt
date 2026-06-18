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
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import com.wasimaster.wmkeyboard.core.tools.SymbolSet
import com.wasimaster.wmkeyboard.core.tools.SymbolSetCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Which script/input method the keyboard is currently in. Each mode is one
 * language+layout pair; [ENGLISH] is the original English QWERTY mode and
 * keeps its stored name for settings compatibility.
 */
enum class InputMode { ENGLISH, AZERTY, DVORAK, AVRO, PROBHAT, JATIYA, FRENCH, GERMAN, SPANISH }

/**
 * The language a mode types. Modes group under a language in the settings
 * screens and share language-level behavior (dictionaries, autocorrect,
 * handwriting model, fonts); adding a language means adding a value here
 * and tagging its modes below.
 */
enum class KeyboardLanguage { ENGLISH, BANGLA, FRENCH, GERMAN, SPANISH }

val InputMode.language: KeyboardLanguage
    get() = when (this) {
        InputMode.ENGLISH, InputMode.AZERTY, InputMode.DVORAK -> KeyboardLanguage.ENGLISH
        InputMode.AVRO, InputMode.PROBHAT, InputMode.JATIYA -> KeyboardLanguage.BANGLA
        InputMode.FRENCH -> KeyboardLanguage.FRENCH
        InputMode.GERMAN -> KeyboardLanguage.GERMAN
        InputMode.SPANISH -> KeyboardLanguage.SPANISH
    }

/** English-language modes: Latin layouts with English suggestions/autocorrect. */
val InputMode.isEnglish: Boolean
    get() = language == KeyboardLanguage.ENGLISH

/**
 * Modes typing a Latin-script language (English, French, German, Spanish):
 * these share the Latin font choice, auto-capitalization rules and roman
 * composing, regardless of which dictionary (if any) backs them.
 */
val InputMode.isLatinScript: Boolean
    get() = language != KeyboardLanguage.BANGLA

/**
 * Fixed Bengali layouts type Bengali characters directly (no roman
 * composing, no transliteration): Probhat and the National (Jatiya) layout.
 */
val InputMode.isFixedBengali: Boolean
    get() = this == InputMode.PROBHAT || this == InputMode.JATIYA

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
    MODES,
}

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
    ToolbarTool.UNDO, ToolbarTool.REDO, ToolbarTool.SETTINGS, ToolbarTool.THEMES,
    ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH, ToolbarTool.DICTIONARY, ToolbarTool.CALCULATOR,
    ToolbarTool.AI, ToolbarTool.GRAMMAR, ToolbarTool.NUMPAD, ToolbarTool.ONE_HANDED,
    ToolbarTool.SPLIT, ToolbarTool.FLOATING, ToolbarTool.INCOGNITO, ToolbarTool.AUTOCORRECT,
    ToolbarTool.SOUND_HAPTICS, ToolbarTool.MODES, ToolbarTool.OCR, ToolbarTool.QR_SCAN,
    ToolbarTool.QR_GEN, ToolbarTool.DOC_SCAN, ToolbarTool.CAMERA, ToolbarTool.HANDWRITING,
    ToolbarTool.SYMBOLS, ToolbarTool.UNIT_CONVERT, ToolbarTool.CURRENCY, ToolbarTool.PASSWORD_GEN,
    ToolbarTool.WIKIPEDIA, ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.FLASHLIGHT,
    ToolbarTool.COMPASS, ToolbarTool.LEVEL, ToolbarTool.MOON_PHASE,
)

val DefaultToolOrder: List<ToolbarTool> =
    RankedToolOrder + (ToolbarTool.entries - RankedToolOrder.toSet())

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

/** Backend for the AI tool — cloud APIs (bring your own key) or a self-hosted server. */
enum class AiProvider(val label: String) {
    ANTHROPIC("Claude"), OPENAI("OpenAI"), GEMINI("Gemini"),
    OLLAMA("Ollama"), LM_STUDIO("LM Studio"),
}

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
    val inputMode: InputMode = InputMode.ENGLISH,
    val enabledModes: List<InputMode> =
        listOf(InputMode.ENGLISH, InputMode.AVRO, InputMode.PROBHAT, InputMode.JATIYA),
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
    val keyPopupMinDurationMs: Int = 150,
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
    val autocorrect: Boolean = true,
    /** Fix missing apostrophes on commit: arent → aren't, im → I'm. */
    val autoApostrophe: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    /** Double-tapping space inserts a tab character (wins over the period). */
    val doubleSpaceTab: Boolean = false,
    val suggestions: Boolean = true,
    /** Keep the suggestion strip as the default top bar even with nothing typed. */
    val suggestionsFirst: Boolean = false,
    /** Show the primary candidate in the middle slot (Gboard style) instead of the left. */
    val suggestionPrimaryCenter: Boolean = true,
    /** Suggest names from the phone's contacts (needs the Contacts permission). */
    val contactSuggestions: Boolean = false,
    val gestureTyping: Boolean = true,
    /** Swipe that starts moving before the long-press delay elapses. */
    val spaceShortSwipe: SpaceSwipeAction = SpaceSwipeAction.LANGUAGE,
    /** Swipe that begins after holding the spacebar past the long-press delay. */
    val spaceLongSwipe: SpaceSwipeAction = SpaceSwipeAction.CURSOR,
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
    val longPressDelayMs: Int = 300,
    val keyRepeatIntervalMs: Int = 50,
    /** Small corner label on each key showing its first long-press character. */
    val longPressHints: Boolean = true,
    /** Long-pressing A selects all text in the field. */
    val longPressASelectAll: Boolean = true,
    /** Long-pressing C copies the selection (selects all first when nothing is selected). */
    val longPressCCopy: Boolean = true,
    /** Long-pressing V pastes the clipboard. */
    val longPressVPaste: Boolean = true,
    /** Long-pressing X cuts the selection (selects all first when nothing is selected). */
    val longPressXCut: Boolean = true,
    val emojiToolbar: Boolean = true,
    val incognito: Boolean = false,
    val toolbarTools: List<ToolbarTool> =
        listOf(ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS, ToolbarTool.SETTINGS),
    val toolbarGreedy: Boolean = true,
    val toolCircleRadiusDp: Int = 20,
    val commaAsEmoji: Boolean = false,
    /** History tab of the emoji panel: recently used vs most used. */
    val emojiTabMode: EmojiTabMode = EmojiTabMode.RECENTS,
    /** "Clear recents" button on the emoji panel's history tab. Off by default. */
    val emojiClearRecentsButton: Boolean = false,
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
    /** Top-to-bottom order of the rows above the keys. */
    val barOrder: List<BarRow> = listOf(BarRow.TOPBAR, BarRow.EMOJI, BarRow.SYMBOL),
    /** Keyboard modes (per-app / per-field bundles of overrides). */
    val keyboardModes: List<KeyboardMode> = DefaultKeyboardModes,
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
    val aiMaxTokens: Int = 1024,
    /** Target language of the AI translate action. */
    val aiTranslateTo: String = "English",
    val aiPromptRewrite: String = "",
    val aiPromptSummarize: String = "",
    val aiPromptTranslate: String = "",
    val aiPromptImprove: String = "",
    val aiPromptFixGrammar: String = "",
    val aiPromptExplain: String = "",
    val aiPromptContinue: String = "",
)

/**
 * DataStore-backed settings. Every option on the settings screens flows
 * through here; the IME service collects [settings] and re-renders live.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val Context.dataStore by preferencesDataStore(name = "keyboard_settings")

        private val INPUT_MODE = stringPreferencesKey("input_mode")
        private val ENABLED_MODES = stringPreferencesKey("enabled_modes")
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
        private val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        private val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        private val DOUBLE_SPACE_TAB = booleanPreferencesKey("double_space_tab")
        private val SUGGESTIONS = booleanPreferencesKey("suggestions")
        private val SUGGESTIONS_FIRST = booleanPreferencesKey("suggestions_first")
        private val SUGGESTION_PRIMARY_CENTER = booleanPreferencesKey("suggestion_primary_center")
        private val CONTACT_SUGGESTIONS = booleanPreferencesKey("contact_suggestions")
        private val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
        // Legacy boolean, read only to migrate into SPACE_LONG_SWIPE.
        private val SPACEBAR_CURSOR = booleanPreferencesKey("spacebar_cursor")
        private val SPACE_SHORT_SWIPE = stringPreferencesKey("space_short_swipe")
        private val SPACE_LONG_SWIPE = stringPreferencesKey("space_long_swipe")
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
        private val LONG_PRESS_DELAY = intPreferencesKey("long_press_delay")
        private val KEY_REPEAT_INTERVAL = intPreferencesKey("key_repeat_interval")
        private val LONG_PRESS_HINTS = booleanPreferencesKey("long_press_hints")
        private val LONG_PRESS_A_SELECT_ALL = booleanPreferencesKey("long_press_a_select_all")
        private val LONG_PRESS_C_COPY = booleanPreferencesKey("long_press_c_copy")
        private val LONG_PRESS_V_PASTE = booleanPreferencesKey("long_press_v_paste")
        private val LONG_PRESS_X_CUT = booleanPreferencesKey("long_press_x_cut")
        private val EMOJI_TOOLBAR = booleanPreferencesKey("emoji_toolbar")
        private val INCOGNITO = booleanPreferencesKey("incognito")
        private val TOOLBAR_TOOLS = stringPreferencesKey("toolbar_tools")
        private val TOOLBAR_GREEDY = booleanPreferencesKey("toolbar_greedy")
        private val TOOL_CIRCLE_RADIUS = intPreferencesKey("tool_circle_radius")
        private val COMMA_AS_EMOJI = booleanPreferencesKey("comma_as_emoji")
        private val EMOJI_TAB_MODE = stringPreferencesKey("emoji_tab_mode")
        private val EMOJI_CLEAR_RECENTS_BUTTON = booleanPreferencesKey("emoji_clear_recents_button")
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
        private val KEYBOARD_MODES = stringPreferencesKey("keyboard_modes")
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
    }

    val settings: Flow<KeyboardSettings> = context.dataStore.data.map { p ->
        val defaults = KeyboardSettings()
        KeyboardSettings(
            inputMode = p[INPUT_MODE]?.let { runCatching { InputMode.valueOf(it) }.getOrNull() }
                ?: defaults.inputMode,
            enabledModes = p[ENABLED_MODES]
                ?.split(',')
                ?.mapNotNull { runCatching { InputMode.valueOf(it) }.getOrNull() }
                ?.ifEmpty { null }
                ?: defaults.enabledModes,
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
            autoApostrophe = p[AUTO_APOSTROPHE] ?: defaults.autoApostrophe,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
            doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: defaults.doubleSpacePeriod,
            doubleSpaceTab = p[DOUBLE_SPACE_TAB] ?: defaults.doubleSpaceTab,
            suggestions = p[SUGGESTIONS] ?: defaults.suggestions,
            suggestionsFirst = p[SUGGESTIONS_FIRST] ?: defaults.suggestionsFirst,
            suggestionPrimaryCenter = p[SUGGESTION_PRIMARY_CENTER]
                ?: defaults.suggestionPrimaryCenter,
            contactSuggestions = p[CONTACT_SUGGESTIONS] ?: defaults.contactSuggestions,
            gestureTyping = p[GESTURE_TYPING] ?: defaults.gestureTyping,
            spaceShortSwipe = p[SPACE_SHORT_SWIPE]
                ?.let { runCatching { SpaceSwipeAction.valueOf(it) }.getOrNull() }
                ?: defaults.spaceShortSwipe,
            // Users who had explicitly turned spacebar cursor control off
            // keep it off until they pick a new swipe action.
            spaceLongSwipe = p[SPACE_LONG_SWIPE]
                ?.let { runCatching { SpaceSwipeAction.valueOf(it) }.getOrNull() }
                ?: if (p[SPACEBAR_CURSOR] == false) SpaceSwipeAction.NONE else defaults.spaceLongSwipe,
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
            longPressDelayMs = p[LONG_PRESS_DELAY] ?: defaults.longPressDelayMs,
            keyRepeatIntervalMs = p[KEY_REPEAT_INTERVAL] ?: defaults.keyRepeatIntervalMs,
            longPressHints = p[LONG_PRESS_HINTS] ?: defaults.longPressHints,
            longPressASelectAll = p[LONG_PRESS_A_SELECT_ALL] ?: defaults.longPressASelectAll,
            longPressCCopy = p[LONG_PRESS_C_COPY] ?: defaults.longPressCCopy,
            longPressVPaste = p[LONG_PRESS_V_PASTE] ?: defaults.longPressVPaste,
            longPressXCut = p[LONG_PRESS_X_CUT] ?: defaults.longPressXCut,
            emojiToolbar = p[EMOJI_TOOLBAR] ?: defaults.emojiToolbar,
            incognito = p[INCOGNITO] ?: defaults.incognito,
            // Empty stored string is a valid state (everything in the toolbox),
            // distinct from never-set (defaults apply).
            toolbarTools = p[TOOLBAR_TOOLS]?.let { csv ->
                if (csv.isEmpty()) emptyList()
                else csv.split(',').mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            } ?: defaults.toolbarTools,
            toolbarGreedy = p[TOOLBAR_GREEDY] ?: defaults.toolbarGreedy,
            toolCircleRadiusDp = p[TOOL_CIRCLE_RADIUS] ?: defaults.toolCircleRadiusDp,
            commaAsEmoji = p[COMMA_AS_EMOJI] ?: defaults.commaAsEmoji,
            emojiTabMode = p[EMOJI_TAB_MODE]
                ?.let { runCatching { EmojiTabMode.valueOf(it) }.getOrNull() }
                ?: defaults.emojiTabMode,
            emojiClearRecentsButton = p[EMOJI_CLEAR_RECENTS_BUTTON] ?: defaults.emojiClearRecentsButton,
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
            keyboardModes = p[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: defaults.keyboardModes,
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
     * rank's relative position — appended in default order after the known
     * ones, so nothing ever vanishes from the toolbox.
     */
    private fun decodeToolOrder(csv: String?): List<ToolbarTool> {
        val stored = csv?.split(',')
            ?.mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        if (stored.isEmpty()) return DefaultToolOrder
        return stored + (DefaultToolOrder - stored.toSet())
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

    suspend fun setInputMode(mode: InputMode) =
        context.dataStore.edit { it[INPUT_MODE] = mode.name }

    suspend fun setEnabledModes(modes: List<InputMode>) =
        context.dataStore.edit { it[ENABLED_MODES] = modes.joinToString(",") { m -> m.name } }

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

    suspend fun setAutoCapitalize(value: Boolean) =
        context.dataStore.edit { it[AUTO_CAPITALIZE] = value }

    suspend fun setDoubleSpacePeriod(value: Boolean) =
        context.dataStore.edit { it[DOUBLE_SPACE_PERIOD] = value }

    suspend fun setDoubleSpaceTab(value: Boolean) =
        context.dataStore.edit { it[DOUBLE_SPACE_TAB] = value }

    suspend fun setSuggestions(value: Boolean) =
        context.dataStore.edit { it[SUGGESTIONS] = value }

    suspend fun setSuggestionsFirst(value: Boolean) =
        context.dataStore.edit { it[SUGGESTIONS_FIRST] = value }

    suspend fun setSuggestionPrimaryCenter(value: Boolean) =
        context.dataStore.edit { it[SUGGESTION_PRIMARY_CENTER] = value }

    suspend fun setContactSuggestions(value: Boolean) =
        context.dataStore.edit { it[CONTACT_SUGGESTIONS] = value }

    suspend fun setGestureTyping(value: Boolean) =
        context.dataStore.edit { it[GESTURE_TYPING] = value }

    suspend fun setSpaceShortSwipe(value: SpaceSwipeAction) =
        context.dataStore.edit { it[SPACE_SHORT_SWIPE] = value.name }

    suspend fun setSpaceLongSwipe(value: SpaceSwipeAction) =
        context.dataStore.edit { it[SPACE_LONG_SWIPE] = value.name }

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

    suspend fun setEmojiTabMode(value: EmojiTabMode) =
        context.dataStore.edit { it[EMOJI_TAB_MODE] = value.name }

    suspend fun setEmojiClearRecentsButton(value: Boolean) =
        context.dataStore.edit { it[EMOJI_CLEAR_RECENTS_BUTTON] = value }

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
}
