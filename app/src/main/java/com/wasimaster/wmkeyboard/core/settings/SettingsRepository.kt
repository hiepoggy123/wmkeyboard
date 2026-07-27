package com.wasimaster.wmkeyboard.core.settings

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.addons.AddonStore
import com.wasimaster.wmkeyboard.core.directboot.DirectBoot
import com.wasimaster.wmkeyboard.core.icons.IconOverrides
import com.wasimaster.wmkeyboard.core.icons.IconPackStore
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyinScheme
import com.wasimaster.wmkeyboard.core.input.composer.HanVariant
import com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LayoutCodec
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.repair
import com.wasimaster.wmkeyboard.core.layout.resolveLayoutSelection
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.layout.script
import com.wasimaster.wmkeyboard.core.tools.AltCalendar
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.NumeralCommitScope
import com.wasimaster.wmkeyboard.core.script.NumeralSystem
import com.wasimaster.wmkeyboard.core.script.ScriptDef
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import android.util.Base64
import com.wasimaster.wmkeyboard.core.stickers.StickerPackStore
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.withEmbeddedImages
import com.wasimaster.wmkeyboard.core.theme.withExtractedImages
import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import com.wasimaster.wmkeyboard.core.tools.DefaultToolLetters
import com.wasimaster.wmkeyboard.core.tools.decodeToolLetters
import com.wasimaster.wmkeyboard.core.tools.encodeToolLetters
import com.wasimaster.wmkeyboard.core.tools.formatLeader
import com.wasimaster.wmkeyboard.core.tools.parseLeader
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.SymbolSet
import com.wasimaster.wmkeyboard.core.tools.SymbolSetCodec
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import com.wasimaster.wmkeyboard.core.util.runCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Visual theme for the keyboard and settings app. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * A pair of themes that follow the system light/dark setting: [lightThemeId]
 * while the system is light, [darkThemeId] while it is dark. When [enabled],
 * this takes over from [KeyboardSettings.keyboardThemeId] entirely — the theme
 * tool shows the active one but can't change it, since the system does.
 *
 * The ids are the same namespace as [KeyboardSettings.keyboardThemeId]:
 * [DEFAULT_THEME_ID], a built-in id, or a custom id. A nested class rather than
 * three flat fields because the top-level [KeyboardSettings] count is near the
 * JVM `copy` ceiling; the DataStore keys stay flat all the same.
 */
data class AutoThemeSettings(
    val enabled: Boolean = false,
    val lightThemeId: String = DEFAULT_THEME_ID,
    val darkThemeId: String = DEFAULT_THEME_ID,
)

/**
 * The key-preview bubble (the character that pops above a pressed key). Grouped
 * into a nested class to keep [KeyboardSettings]'s top-level field count under
 * the JVM `copy$default` ceiling; the DataStore keys stay flat. Read as
 * `settings.popup.enabled` etc.
 */
data class KeyPopupSettings(
    val enabled: Boolean = true,
    /**
     * How long the bubble lingers *after* release, so a fast tap still leaves
     * a readable bubble instead of a single-frame flash. This is a comfort
     * floor: raise it for a slower, more deliberate feel.
     */
    val minDurationMs: Int = 150,
    /**
     * Hard ceiling on the bubble's on-screen life, measured from the press —
     * a stuck-bubble backstop, not a comfort knob. Normally the bubble clears
     * on release; if the release is ever dropped under UI-thread lag (e.g. the
     * InputConnection work on a new line), this cap hides it anyway so it can't
     * strand. Kept above the long-press timeout so genuine holds still preview
     * until the alternates popup takes over.
     */
    val maxDurationMs: Int = 750,
    val onKey: Boolean = true,
    val fontScale: Float = 1.0f,
    val heightDp: Int = 110,
    /**
     * Whether the bubble also shows on the numeric keypads (number, phone,
     * date and time fields). Off by default: on a PIN-style pad the floating
     * character is noise at best and shoulder-surfable at worst.
     */
    val inNumericFields: Boolean = false,
)

/** Shrinks the keyboard toward one edge for thumb reach. */
enum class OneHandedMode { OFF, LEFT, RIGHT }

/** Which edge a one-handed keyboard docks to. */
enum class OneHandedSide {
    LEFT, RIGHT;

    /** The live [OneHandedMode] that renders on this side. */
    fun toMode(): OneHandedMode = if (this == LEFT) OneHandedMode.LEFT else OneHandedMode.RIGHT

    companion object {
        /** The side a live [OneHandedMode] renders on, or null when OFF. */
        fun of(mode: OneHandedMode): OneHandedSide? = when (mode) {
            OneHandedMode.LEFT -> LEFT
            OneHandedMode.RIGHT -> RIGHT
            OneHandedMode.OFF -> null
        }
    }
}

/**
 * One-handed geometry for a single screen orientation.
 *
 * [widthPercent] is the keyboard's share of the screen width while
 * one-handed is active (the rail and any leftover fill the rest).
 * [heightScale] shrinks the keys vertically as a percent of their normal
 * height, bringing the top rows into thumb reach. [side] is the edge the
 * keyboard docks to when one-handed is enabled in this orientation; the
 * in-keyboard rail's flip button updates it live.
 */
data class OneHandedProfile(
    val widthPercent: Int = 78,
    val heightScale: Int = 100,
    val side: OneHandedSide = OneHandedSide.RIGHT,
)

/**
 * Per-orientation one-handed tuning. Landscape defaults narrower because a
 * landscape keyboard is very wide, so 78% would barely help thumb reach.
 */
data class OneHandedSettings(
    val portrait: OneHandedProfile = OneHandedProfile(),
    val landscape: OneHandedProfile = OneHandedProfile(widthPercent = 55),
) {
    /** The profile that applies for the current orientation. */
    fun forLandscape(landscape: Boolean): OneHandedProfile =
        if (landscape) this.landscape else portrait
}

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
    MODES, TYPING_TEST, MEDIA_CONTROL,
    // One-tap cursor moves. The text-edit panel already offers these, but on
    // the toolbar they cost a single tap instead of opening a panel first.
    CURSOR_LEFT, CURSOR_RIGHT, CURSOR_UP, CURSOR_DOWN,
    CURSOR_HOME, CURSOR_END, PAGE_UP, PAGE_DOWN,
    // Move by whole words (Ctrl+Arrow), and select the word or line at the cursor.
    CURSOR_WORD_LEFT, CURSOR_WORD_RIGHT, SELECT_WORD, SELECT_LINE,
    // Dismiss the keyboard in one tap. Grouped with the cursor moves in the
    // toolbox (it belongs beside the caret controls, not a panel it opens).
    HIDE_KEYBOARD,
}

/** The cursor tools, in the order they read on the toolbar. */
val CursorTools: List<ToolbarTool> = listOf(
    ToolbarTool.CURSOR_LEFT, ToolbarTool.CURSOR_RIGHT,
    ToolbarTool.CURSOR_WORD_LEFT, ToolbarTool.CURSOR_WORD_RIGHT,
    ToolbarTool.CURSOR_UP, ToolbarTool.CURSOR_DOWN,
    ToolbarTool.CURSOR_HOME, ToolbarTool.CURSOR_END,
    ToolbarTool.PAGE_UP, ToolbarTool.PAGE_DOWN,
    ToolbarTool.SELECT_WORD, ToolbarTool.SELECT_LINE,
)

/**
 * Tools that still work during direct boot — before the user has ever unlocked
 * the device, when the keyboard has no credential-encrypted storage, no
 * personal data, and (usually) no network.
 *
 * The rule is what a tool *reads*, not what it looks like: anything backed by a
 * file under `filesDir` (clipboard, snippets, stickers, camera shots, the
 * downloaded speech and LLM models, ML Kit's models), anything that needs a
 * credential the mirror deliberately does not carry (translate, the searches,
 * AI), anything that queries a content provider behind the lock (calendar), and
 * anything that has to start an activity (the settings app, the document
 * scanner) is out. What is left is arithmetic, sensors, and the keyboard's own
 * controls — which is roughly what anyone wants from a lock screen anyway.
 */
fun isDirectBootSafeTool(tool: ToolbarTool): Boolean = when (tool) {
    ToolbarTool.EMOJI, ToolbarTool.TEXT_EDIT, ToolbarTool.NUMPAD, ToolbarTool.SYMBOLS,
    ToolbarTool.ONE_HANDED, ToolbarTool.SPLIT, ToolbarTool.FLOATING, ToolbarTool.HIDE_KEYBOARD,
    ToolbarTool.THEMES, ToolbarTool.AUTOCORRECT, ToolbarTool.SOUND_HAPTICS, ToolbarTool.INCOGNITO,
    ToolbarTool.MODES, ToolbarTool.UNDO, ToolbarTool.REDO,
    ToolbarTool.CALCULATOR, ToolbarTool.UNIT_CONVERT, ToolbarTool.PASSWORD_GEN, ToolbarTool.QR_GEN,
    ToolbarTool.FLASHLIGHT, ToolbarTool.COMPASS, ToolbarTool.LEVEL, ToolbarTool.MOON_PHASE,
    -> true
    // The cursor moves only touch the input connection.
    else -> tool in CursorTools
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
 * Whether the offline Whisper dictation engine can run in this build. False in
 * the lite flavor (no LiteRT runtime) — settings hide the engine option and the
 * IME never routes dictation through it.
 */
fun isWhisperEnabled(): Boolean =
    BuildConfig.ENABLE_WHISPER && com.wasimaster.wmkeyboard.core.voice.whisper.WhisperEngine.AVAILABLE

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
    ToolbarTool.MEDIA_CONTROL,
    ToolbarTool.AI, ToolbarTool.GRAMMAR, ToolbarTool.TYPING_TEST, ToolbarTool.NUMPAD, ToolbarTool.ONE_HANDED,
    ToolbarTool.SPLIT, ToolbarTool.FLOATING, ToolbarTool.INCOGNITO, ToolbarTool.AUTOCORRECT,
    ToolbarTool.SOUND_HAPTICS, ToolbarTool.MODES, ToolbarTool.OCR, ToolbarTool.QR_SCAN,
    ToolbarTool.QR_GEN, ToolbarTool.DOC_SCAN, ToolbarTool.CAMERA, ToolbarTool.HANDWRITING,
    ToolbarTool.SYMBOLS, ToolbarTool.UNIT_CONVERT, ToolbarTool.CURRENCY, ToolbarTool.PASSWORD_GEN,
    ToolbarTool.WIKIPEDIA, ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.FLASHLIGHT,
    ToolbarTool.COMPASS, ToolbarTool.LEVEL, ToolbarTool.MOON_PHASE,
    // The one-tap cursor moves last: useful, but they would otherwise push
    // every other tool a full row down in the toolbox.
    ToolbarTool.CURSOR_LEFT, ToolbarTool.CURSOR_RIGHT,
    ToolbarTool.CURSOR_WORD_LEFT, ToolbarTool.CURSOR_WORD_RIGHT,
    ToolbarTool.CURSOR_UP, ToolbarTool.CURSOR_DOWN,
    ToolbarTool.CURSOR_HOME, ToolbarTool.CURSOR_END, ToolbarTool.PAGE_UP, ToolbarTool.PAGE_DOWN,
    ToolbarTool.SELECT_WORD, ToolbarTool.SELECT_LINE,
    ToolbarTool.HIDE_KEYBOARD,
)

val DefaultToolOrder: List<ToolbarTool> =
    RankedToolOrder + (ToolbarTool.entries - RankedToolOrder.toSet())

/**
 * The tools pinned to the toolbar out of the box, and what "Reset pinned
 * tools" restores — global or, when a mode owns the tool order, for that mode.
 */
val DefaultToolbarTools: List<ToolbarTool> =
    listOf(ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SETTINGS)

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
    ToolbarTool.MEDIA_CONTROL,
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

/**
 * One-tap writing actions on the AI tool's panel. [CUSTOM] is the odd one
 * out: its prompt is the instruction the user types on the keyboard each
 * run, so it has no stored per-action override and no built-in prompt body.
 */
enum class AiAction(val label: String) {
    REWRITE("Rewrite"), SUMMARIZE("Summarize"), TRANSLATE("Translate"),
    IMPROVE("Improve"), FIX_GRAMMAR("Fix grammar"), EXPLAIN("Explain"),
    CONTINUE("Continue"), CUSTOM("Custom"),
    ;

    /**
     * Whether the action still means something with an empty field. Only
     * [CUSTOM] does: its instruction can ask for text to be *written*, whereas
     * every preset describes something to do to text that has to already
     * exist. The AI panel keeps this one chip live when the others grey out.
     */
    val worksWithoutText: Boolean get() = this == CUSTOM
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
 * Key-press sound. [CLICK] and [STANDARD] come from the device's own sound
 * pack, so they match the stock keyboard's palette; [POP], [THOCK] and [CHIME]
 * are synthesised in-app. [CUSTOM] plays a file from
 * [com.wasimaster.wmkeyboard.core.feedback.SoundStore], named by
 * [KeySoundSettings.customId].
 */
enum class KeySoundStyle { CLICK, STANDARD, POP, THOCK, CHIME, CUSTOM }

/**
 * The installed key sound in use.
 *
 * A nested class holding one field looks like overkill, and would be, except
 * that `KeyboardSettings` is at the JVM's 255-argument ceiling for the
 * `copy$default` Kotlin generates for it — see the note on [CameraSettings].
 * New settings go in a group; the DataStore key stays flat.
 */
data class KeySoundSettings(
    /** [com.wasimaster.wmkeyboard.core.feedback.SoundStore] id, blank if none. */
    val customId: String = "",
)

/**
 * Key-press haptic waveform.
 *
 * [SYSTEM_KEY] and [SYSTEM_TAP] delegate to the platform's own key haptic via
 * `View.performHapticFeedback` — the exact path stock keyboards use, so on
 * tuned OEMs (Samsung, Pixel) they inherit the vendor's crafted click and
 * follow the system haptic-intensity setting. [SYSTEM_KEY] asks for
 * `VIRTUAL_KEY` (what Gboard/SwiftKey/Ridmik use); [SYSTEM_TAP] asks for
 * `KEYBOARD_TAP` (softer — Samsung's own keyboard). They fall back to a
 * hardware click when no attached view is available.
 *
 * The rest drive the vibrator directly: [CUSTOM] with the duration/amplitude
 * sliders; [CLICK]/[HEAVY_CLICK] with the device's predefined effects
 * (Android 10+); [SHARP] with the click primitive (Android 11+).
 */
// Declared best-to-worst: the two recommended platform styles first, then the
// hardware-tuned effects, then the manual Custom fallback last. UIs iterate
// `entries`, so this order drives their display. Persistence keys off `.name`,
// so reordering is storage-safe. [label] is the short chip caption shared by
// every picker.
enum class HapticStyle(val label: String) {
    SYSTEM_KEY("Key"),
    SYSTEM_TAP("Tap"),
    CLICK("Click"),
    HEAVY_CLICK("Heavy"),
    SHARP("Sharp"),
    CUSTOM("Custom"),
}

/**
 * What a horizontal swipe on the spacebar does. "Short" swipes start
 * moving right away; "long" swipes hold the spacebar past the long-press
 * delay first, then drag — distance is deliberately not the discriminator,
 * a fast flick travels further than a careful drag.
 */
enum class SpaceSwipeAction { NONE, LANGUAGE, CURSOR }

/**
 * What the resting spacebar label shows. [LANGUAGE] the current language name,
 * [LAYOUT] the current layout name, [BOTH] "Language (Layout)". Regardless of
 * mode, when the active language has more than one enabled layout the layout
 * name is appended anyway, so those layouts stay distinguishable.
 */
enum class SpacebarDisplay { LANGUAGE, LAYOUT, BOTH }

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
enum class EmojiFontChoice { SYSTEM, NOTO, CUSTOM, INSTALLED }

/**
 * Which emoji face from the font library [EmojiFontChoice.INSTALLED] draws with.
 *
 * Its own class for the reason [KeySoundSettings] is: `KeyboardSettings` sits at
 * the JVM's 255-argument ceiling for the `copy$default` Kotlin generates, so a
 * new setting joins a group rather than the flat list. The DataStore key is flat
 * either way.
 */
data class EmojiFontSettings(
    /** [com.wasimaster.wmkeyboard.core.fonts.FontStore] id, blank if none. */
    val installedId: String = "",
)

/**
 * What tapping an emoji suggestion does to the word being typed:
 * [REPLACE] swaps the word for the emoji (Gboard style), [APPEND] keeps
 * the word and adds the emoji after it ("birthday 🎂").
 */
enum class EmojiInsertMode { REPLACE, APPEND }

/**
 * Default Fitzpatrick skin tone applied to toned emoji in the suggestion
 * strip and emoji search. [NONE] leaves the neutral yellow base; the five
 * others map to 🏻..🏿, i.e. tone indices 1..5 in [EmojiVariantIndex].
 */
enum class EmojiSkinTone(val toneIndex: Int) {
    NONE(0), LIGHT(1), MEDIUM_LIGHT(2), MEDIUM(3), MEDIUM_DARK(4), DARK(5),
}

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
 *
 * [PASSTHROUGH] keeps the keyboard's own touch handling under a screen reader
 * — the spacebar cursor slide, the backspace word swipe, glide typing and
 * handwriting all keep working, and a key still announces on press and types
 * on release. It needs the app's pass-through accessibility service enabled
 * (see `core.accessibility.TouchPassthroughService`), because carving the
 * keyboard out of touch exploration is something only an accessibility service
 * may ask for; without it the mode falls back to [EXPLORE].
 */
enum class ScreenReaderMode { OFF, LABELS, EXPLORE, PASSTHROUGH }

/**
 * How the top toolbar behaves and lays out. Grouped into their own class
 * rather than sitting flat on [KeyboardSettings] because that class's primary
 * constructor is at the JVM's 255-argument ceiling — new toolbar settings land
 * here, and existing flat ones are migrated in as room is needed. Each field
 * still persists under its own DataStore key via the matching setter.
 */
data class ToolbarBehavior(
    /**
     * Master switch for the whole top strip (suggestions + toolbar). Off
     * removes it entirely, reclaiming its height for the keys. Guarded by a
     * warning in Settings because it hides suggestions and every pinned tool.
     */
    val enabled: Boolean = true,
    /**
     * Swipe down anywhere on the top strip to dismiss the keyboard, the way a
     * downward flick on the keys does on some keyboards. Off by default so the
     * gesture never surprises anyone reordering or scrolling the bar.
     */
    val swipeDownHide: Boolean = false,
    /**
     * With a physical keyboard attached, drop the on-screen keys and keep only
     * the toolbar strip, so the tools stay one tap away while typing on the
     * hardware keyboard. Off by default (the platform's usual behaviour stands).
     */
    val onlyWithHardwareKeyboard: Boolean = false,
    /**
     * Mirror the pinned tool order left-to-right when the active layout's
     * script runs right-to-left (Arabic, Hebrew …), so the bar reads with the
     * text. On by default; the toolbox grid is unaffected.
     */
    val reverseForRtl: Boolean = true,
    /** Pinned tools split the bar width evenly instead of packing to the left. */
    val greedy: Boolean = true,
    /**
     * Let the pinned tools scroll horizontally instead of packing into the
     * bar width — for people who pin more tools than fit at a tappable size.
     * Forces the packed (non-greedy) layout while on.
     */
    val scrollable: Boolean = false,
    /**
     * On the device lock screen, hide the whole top strip (suggestions +
     * toolbar) and block the clipboard panel, so copied text — one-time codes,
     * passwords — and every pinned tool stay off a screen anyone can wake. Off
     * by default; locked or not, the keyboard looks the same.
     */
    val hideWhenLocked: Boolean = false,
)

/**
 * How the suggestion strip is reachable from a physical keyboard, where nothing
 * is tappable.
 */
enum class SuggestionHotkeyMode(val label: String) {
    OFF("Off"),

    /** The leader, then a digit. Collides with nothing, at the cost of one extra key. */
    LEADER_DIGIT("After the shortcut key"),

    /**
     * Alt+1 … Alt+9 directly. One keystroke, but browsers, editors and chat apps
     * all claim modifier+digit for tab and workspace switching, so it is opt-in.
     */
    ALT_DIGIT("Alt + number"),
}

/**
 * Physical-keyboard shortcuts and panel navigation: opening a tool and driving
 * it without touching the screen. Grouped into their own class rather than
 * sitting flat on [KeyboardSettings] because that class's primary constructor is
 * at the JVM's 255-argument ceiling — each field still persists under its own
 * DataStore key via the matching setter.
 *
 * The older flat [KeyboardSettings.hardwareKeyboardInput] stays where it is: it
 * governs *typing*, which these do not touch.
 */
data class HardwareKeyboardSettings(
    /**
     * Master switch for the leader key and its tool letters. On by default: the
     * default leader is a double-tapped modifier, which produces no character
     * and is passed through to the app either way.
     */
    val shortcutsEnabled: Boolean = true,
    /**
     * Arrow keys move a highlight through an open panel, Enter picks it. Without
     * this, a shortcut can open a tool but not use one.
     */
    val panelNavigation: Boolean = true,
    /**
     * Escape closes an open panel. Only ever consumed when the keyboard actually
     * has something open — a bare Escape belongs to the app, which may be a
     * browser loading a page or an editor leaving insert mode.
     */
    val escClosesPanel: Boolean = true,
    val suggestionHotkeys: SuggestionHotkeyMode = SuggestionHotkeyMode.LEADER_DIGIT,
    /**
     * A shortcut that opens a tool also shows the keyboard, which a physical
     * keyboard usually hides. Restored to however it was as soon as the tool closes.
     */
    val autoShowUi: Boolean = true,
    /**
     * What arms the tool picker, in the canonical text form parsed by
     * `HardwareShortcuts.parseLeader` — `"doubletap:ctrl"` or `"ctrl+shift+K"`.
     * Kept as a string so this class needs no `KeyEvent` and the DataStore
     * round-trip is the identity.
     */
    val leader: String = "doubletap:ctrl",
    /** How long the armed picker waits for its letter. */
    val pickerTimeoutMs: Int = 3000,
    /**
     * Letter → the tool it opens, the complete map rather than a delta: the
     * default is non-empty, so "absent means default" could never express the
     * user unbinding a letter.
     */
    val toolByLetter: Map<Char, ToolbarTool> = DefaultToolLetters,
)

/**
 * Fine-grained feedback gates that don't fit the master haptic/sound toggles:
 * which key events buzz, whether a copy shows a toast, and whether Do Not
 * Disturb mutes haptics. Grouped into their own class rather than sitting flat
 * on [KeyboardSettings] because that class's primary constructor is at the
 * JVM's 255-argument ceiling (see the class doc). Each field still persists
 * under its own DataStore key via the matching setter. Read as
 * `settings.feedback.vibrateOnSpace`, etc.
 */
data class FeedbackSettings(
    /**
     * Buzz on space-bar presses. Off lets heavy space users silence just that
     * key while every other key still vibrates. The key sound (if on) still
     * plays. On by default.
     */
    val vibrateOnSpace: Boolean = true,
    /**
     * Buzz on each word removed by a swipe-to-delete on the backspace key. Off
     * makes clearing a sentence one smooth pull with no per-word buzz-saw. The
     * plain backspace tap and its hold-to-repeat are unaffected. On by default.
     */
    val vibrateOnDeleteSwipe: Boolean = true,
    /**
     * Buzz on every auto-repeat while a key is held (backspace/space repeat).
     * Off keeps only the first press buzzing; the repeats stay silent (their
     * key sound, if on, still plays). On by default.
     */
    val vibrateOnRepeat: Boolean = true,
    /**
     * Show a short toast confirming text was copied to the clipboard, for
     * fields that give no visual copy feedback of their own. Off by default.
     */
    val toastOnCopy: Boolean = false,
    /**
     * Suppress all keyboard haptics while the system is in Do Not Disturb, so a
     * silenced phone stays fully quiet in the pocket. Off by default (DND
     * targets notifications, not touch feedback, so haptics keep firing).
     */
    val hapticsRespectDnd: Boolean = false,
)

/**
 * Clipboard/undo/redo shortcuts a letter key can perform on long press
 * (A/C/V/X/Z/Y). Grouped into their own class rather than sitting flat on
 * [KeyboardSettings] because that class's primary constructor is at the
 * JVM's 255-argument ceiling (see the class doc). Each field still persists
 * under its own DataStore key via the matching setter. All off by default —
 * each one replaces that key's accent popup, so turning any on is an
 * explicit trade a user opts into. Read as `settings.longPressLetterActions.selectAll`, etc.
 */
data class LongPressLetterActions(
    val selectAll: Boolean = false,
    val copy: Boolean = false,
    val paste: Boolean = false,
    val cut: Boolean = false,
    val undo: Boolean = false,
    val redo: Boolean = false,
)

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
    /** Light+dark theme pair that follows the system setting; see [AutoThemeSettings]. */
    val autoTheme: AutoThemeSettings = AutoThemeSettings(),
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
    /**
     * Spacing between keys as a multiple of the built-in gap (1 = default).
     * Higher spreads the keys apart (and raises the keyboard, since the gap is
     * part of each row); lower packs them tighter.
     */
    val keyGapScale: Float = 1f,
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
     * Per-script font choice for the other non-Latin scripts, keyed by
     * [com.wasimaster.wmkeyboard.core.script.ScriptId] name. Value is "default"
     * (the script's automatic Noto face) or "google:<Name>" from that script's
     * curated list. Absent scripts use their automatic face. Latin/Cyrillic/Greek
     * follow [keyFontId] and Bengali has [bengaliFontId], so neither appears here.
     */
    val scriptFontIds: Map<String, String> = emptyMap(),
    /**
     * Bumped by the settings app whenever it edits the learned-words file
     * directly, so the IME (which keeps the lexicon in memory) reloads it.
     */
    val lexiconVersion: Int = 0,
    /** Bumped when the settings app imports or removes a custom word list. */
    val customDictVersion: Int = 0,
    /** Emoji look on the keyboard: system pack, Noto (stock Android), or custom. */
    val emojiFont: EmojiFontChoice = EmojiFontChoice.SYSTEM,
    /** Which library face [EmojiFontChoice.INSTALLED] uses; see [EmojiFontSettings]. */
    val emojiFontInstalled: EmojiFontSettings = EmojiFontSettings(),
    val hapticFeedback: Boolean = true,
    val hapticStrengthMs: Int = 15,
    val hapticAmplitude: Int = 255,
    val hapticStyle: HapticStyle = HapticStyle.SYSTEM_KEY,
    val hapticOnLongPress: Boolean = true,
    val hapticOnLongPressRelease: Boolean = false,
    /** Per-event haptic gates + copy toast (see [FeedbackSettings]); nested to
     *  stay under the primary-constructor field ceiling. */
    val feedback: FeedbackSettings = FeedbackSettings(),
    val keySound: Boolean = false,
    val keySoundStyle: KeySoundStyle = KeySoundStyle.CLICK,
    /** Sound-effect volume, 0..1 of the system media volume. */
    val keySoundVolume: Float = 0.5f,
    /** Which installed sound [KeySoundStyle.CUSTOM] plays; see [KeySoundSettings]. */
    val keySoundCustom: KeySoundSettings = KeySoundSettings(),
    /** Key-preview bubble settings; see [KeyPopupSettings]. */
    val popup: KeyPopupSettings = KeyPopupSettings(),
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
    /** Never autocorrect a word typed all in capitals (acronyms, shouting). */
    val autocorrectSkipAllCaps: Boolean = true,
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
    // `suggestionsFirst` and `suggestionPrimaryCenter` moved into
    // [SuggestionStripSettings] to keep this constructor under the JVM slot
    // ceiling; their DataStore keys are unchanged.
    /** Suggest names from the phone's contacts (needs the Contacts permission). */
    val contactSuggestions: Boolean = false,
    /**
     * Complete a contact's email address as you type the start of it — "john"
     * offers john.doe@gmail.com. Needs the Contacts permission.
     */
    val contactEmailSuggestions: Boolean = false,
    /**
     * Show those email completions inside email fields too, even when the app
     * has asked for no suggestion strip (which email fields normally do). Only
     * matters while [contactEmailSuggestions] is on.
     */
    val contactEmailSuggestionsInEmailFields: Boolean = true,
    /** Suggest the names of installed apps ("sign" → Signal). No permission needed. */
    val appNameSuggestions: Boolean = false,
    /**
     * Words the user never wants suggested or autocorrected to. Matched
     * case-insensitively; the word can still be typed and committed, it is
     * only kept out of the suggestion strip. Empty by default.
     */
    val suggestionBlacklist: Set<String> = emptySet(),
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
    /**
     * Glide-typing behaviour and trail appearance, grouped (see [GestureSettings]).
     * Nested rather than flattened onto [KeyboardSettings] because the top-level
     * data class is near the JVM's copy() slot ceiling.
     */
    val gesture: GestureSettings = GestureSettings(),
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
    /**
     * Route physical-keyboard keystrokes through the keyboard's own engine —
     * transliteration, the composing buffer, suggestions and autocorrect — so a
     * hardware keyboard types Bengali (or gets corrections) exactly like the
     * on-screen keys. Off types the raw characters straight into the field,
     * letting the system and the physical layout own input. On either way,
     * shortcuts (Ctrl+C), cursor keys and function keys stay with the system.
     */
    val hardwareKeyboardInput: Boolean = true,
    /**
     * Opening and driving the tools from a physical keyboard: the leader key,
     * its tool letters, the focus ring. Separate from [hardwareKeyboardInput],
     * which is only about how typed characters are processed.
     */
    val hardwareKeyboard: HardwareKeyboardSettings = HardwareKeyboardSettings(),
    /** Volume up/down move the text cursor while the keyboard is showing. */
    val volumeCursor: Boolean = false,
    /**
     * Hand the volume keys back to the system while audio is playing, so
     * cursor control never costs you the ability to turn a song down.
     */
    val volumeCursorMediaAware: Boolean = true,
    /** Replace the 🌐 key with an emoji key (language switching moves to spacebar swipes). */
    val globeAsEmoji: Boolean = true,
    /**
     * List each enabled layout as an Android input-method subtype, so the
     * system language switcher (the "Choose input method" sheet) lists them and
     * can switch between them. Off = the keyboard registers no subtypes and
     * ignores OS subtype switches; language switching then lives entirely
     * in-keyboard (globe / spacebar / picker).
     */
    val osLanguageSwitcher: Boolean = true,
    /**
     * Lead the switcher's subtype label with the app name ("WM Keyboard ·
     * English") rather than the bare language. The system decides how it styles
     * the label versus the app name — this only changes what the label itself
     * reads, so it cannot truly swap which is bold. No effect while
     * [osLanguageSwitcher] is off.
     */
    val subtypeAppNameFirst: Boolean = false,
    /** Per-app language/subtype memory (see [PerAppLanguageSettings]). */
    val perAppLanguage: PerAppLanguageSettings = PerAppLanguageSettings(),
    val onboardingDone: Boolean = false,
    val conjunctBackspace: Boolean = false,
    /** Chinese/Cantonese conversion-IME options (see [CjkSettings] for why nested). */
    val cjk: CjkSettings = CjkSettings(),
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    /** Per-orientation one-handed width, height scale and dock side. */
    val oneHanded: OneHandedSettings = OneHandedSettings(),
    val learnFromTyping: Boolean = true,
    /**
     * Also add words the keyboard learns to Android's system personal
     * dictionary, so other keyboards and spell checkers know them too. Off by
     * default — the on-device lexicon already covers this keyboard.
     */
    val addWordsToSystemDictionary: Boolean = false,
    /** Clipboard-tool history, panel and suggestion-strip settings (see [ClipboardSettings]). */
    val clipboard: ClipboardSettings = ClipboardSettings(),
    /** Suggestion-strip content options — quick-punctuation chips (see [SuggestionStripSettings]). */
    val suggestionStrip: SuggestionStripSettings = SuggestionStripSettings(),
    val longPressDelayMs: Int = 300,
    val keyRepeatIntervalMs: Int = 50,
    /** Small corner label on each key showing its first long-press character. */
    val longPressHints: Boolean = true,
    /** Assorted layout & gesture behaviours (see [LayoutBehaviorSettings]). */
    val layoutBehavior: LayoutBehaviorSettings = LayoutBehaviorSettings(),
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
    /** Long-press shortcuts on the A/C/V/X/Z/Y keys (see [LongPressLetterActions]). */
    val longPressLetterActions: LongPressLetterActions = LongPressLetterActions(),
    val emojiToolbar: Boolean = true,
    /** Tint each tool icon its own accent colour in Settings and the toolbox. */
    val coloredToolIcons: Boolean = true,
    /**
     * Per-tool accent-colour overrides (ARGB longs), applied when
     * [coloredToolIcons] is on. A tool absent from the map keeps its built-in
     * default (see [com.wasimaster.wmkeyboard.core.ui.toolAccentColor]).
     */
    val toolColorOverrides: Map<ToolbarTool, Long> = emptyMap(),
    /** Which glyph each customisable icon draws (see [IconSettings]). */
    val icons: IconSettings = IconSettings(),
    val incognito: Boolean = false,
    val toolbarTools: List<ToolbarTool> = DefaultToolbarTools,
    /** Toolbar enable/behaviour/layout switches (see [ToolbarBehavior]). */
    val toolbarBehavior: ToolbarBehavior = ToolbarBehavior(),
    /** Height of the top toolbar/suggestion strip, in dp. */
    val toolbarHeightDp: Int = 44,
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
    val emojiInsertMode: EmojiInsertMode = EmojiInsertMode.REPLACE,
    /** Emoji options that didn't fit the flat field list (see [EmojiSettings]). */
    val emoji: EmojiSettings = EmojiSettings(),
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
    /**
     * The two calendars the tool shows alongside the Gregorian one, in the
     * order they are drawn. The first is also what the day cells get their
     * small second number from. Either may be [AltCalendar.NONE].
     */
    val calendarAltOne: AltCalendar = AltCalendar.BENGALI,
    val calendarAltTwo: AltCalendar = AltCalendar.HIJRI,
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
    /** Offline Whisper dictation settings, grouped (see [CameraSettings] for why). */
    val whisper: WhisperSettings = WhisperSettings(),
    /** Camera tool settings, grouped (see [CameraSettings]). */
    val camera: CameraSettings = CameraSettings(),
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
    /** Text-editing tool and selection-editing settings (see [TextEditingSettings]). */
    val textEditing: TextEditingSettings = TextEditingSettings(),
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
     * Squiggle spelling errors but offer no fix popup when Harper acts as the
     * system spell checker. Only has an effect on Android 12+, where the
     * framework honours the "mark but don't show suggestions UI" flag.
     */
    val spellCheckerNoSuggestions: Boolean = false,
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
    /** Password/passphrase generator defaults (the panel tweaks these live). */
    val passwordGenerator: PasswordGeneratorSettings = PasswordGeneratorSettings(),
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
 * Camera-tool settings, grouped into their own object.
 *
 * Kotlin generates a `copy$default` for a data class that takes every property
 * as an argument plus bookkeeping slots, and a JVM method descriptor is capped
 * at 255 argument slots. [KeyboardSettings] had grown to that ceiling, so
 * cohesive families like this one are split off to keep it loadable — the
 * DataStore keys stay flat, so this is purely an in-memory grouping.
 */
/**
 * Chinese and Cantonese conversion-IME options, grouped rather than flat.
 *
 * [KeyboardSettings] sits at the JVM's `copy$default` argument ceiling — the same
 * reason [CameraSettings] and [LongPressLetterActions] were split out — so a
 * cohesive family like this one lives in its own class. Folding the two existing
 * pinyin options in here alongside the new one leaves the parent with fewer
 * fields than before, not more.
 *
 * The DataStore keys are unchanged by the nesting (`pinyin_fuzzy`,
 * `pinyin_double_pinyin`), so no existing preference is lost — only the Kotlin
 * path moved.
 */
/**
 * Password-generator defaults, grouped rather than flat because [KeyboardSettings]
 * sits against the JVM's 255-slot method-argument limit: Kotlin's generated
 * `copy$default` takes every field plus its mask ints, so a flat class stops
 * loading once the count creeps past ~245. Grouping a tool's own settings is the
 * pattern the other sub-classes here already follow.
 */
data class PasswordGeneratorSettings(
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
)

data class CjkSettings(
    /** Chinese: treat confusable pinyin initials/finals as equivalent (zh↔z, an↔ang…). */
    val pinyinFuzzy: Boolean = false,
    /** Chinese: the Double Pinyin scheme, or OFF for full pinyin. */
    val pinyinDoublePinyin: DoublePinyinScheme = DoublePinyinScheme.OFF,
    /** Convert candidate output to Traditional characters (Taiwan, Hong Kong). */
    val traditionalOutput: Boolean = false,
    /** Cantonese: match lazy-pronunciation mergers (n↔l, ng↔∅, -ng↔-n, -k↔-t). */
    val jyutpingLazy: Boolean = false,
    /** Which region's vocabulary Traditional output should prefer. */
    val hanRegion: HanVariant.HanRegion = HanVariant.HanRegion.GENERIC,
)

data class CameraSettings(
    /** Camera tool opens on the selfie camera. */
    val preferFront: Boolean = false,
    /** Mirror selfie captures so the photo matches the preview. */
    val mirrorFront: Boolean = true,
    /** Play a shutter click when the camera tool takes a photo. */
    val shutterSound: Boolean = true,
    /** Vibrate on camera controls, countdown ticks and the shutter. */
    val haptics: Boolean = true,
    /** Copy camera captures into Pictures/WM Keyboard as well as sending them. */
    val saveToGallery: Boolean = false,
)

/**
 * Offline Whisper dictation settings, grouped into their own object (see
 * [CameraSettings] for why the top-level class can't take more flat fields).
 * DataStore keys stay flat.
 */
data class WhisperSettings(
    /** Dictation backend: "system" = OS SpeechRecognizer, "whisper" = offline LiteRT. */
    val engine: String = "system",
    /**
     * The fallback Whisper catalog id — the model used for any language without
     * an entry in [modelByLang]. Blank falls back to the best downloaded model
     * for the language being typed in.
     */
    val modelId: String = "",
    /**
     * Language id → Whisper catalog id, for languages the user has pinned to a
     * specific model. Dictation resolves the model from the language of the
     * active layout, so a German-only graph can be the German choice while
     * everything else stays on a multilingual one.
     */
    val modelByLang: Map<String, String> = emptyMap(),
    /** Force Whisper to translate speech to English instead of transcribing verbatim. */
    val translate: Boolean = false,
)

/**
 * Text-editing tool and selection-editing settings, grouped into their own
 * object (see [CameraSettings] for why). DataStore keys stay flat.
 */
data class TextEditingSettings(
    /** Auto-repeat interval while holding an arrow/backspace in the text-editing tool. */
    val repeatMs: Int = 60,
    /**
     * Typing a bracket, brace or quote with text selected wraps the selection
     * in the pair (foo → (foo)) instead of replacing it.
     */
    val wrapSelectionWithPair: Boolean = true,
    /**
     * Pressing shift with text selected cycles its case (lower → Title → UPPER)
     * instead of arming shift for the next character.
     */
    val recapitalizeSelectionWithShift: Boolean = true,
)

/**
 * Per-app language memory, grouped into its own object (see [CameraSettings] for
 * why). DataStore keys stay flat.
 *
 * When [enabled], an explicit language switch while typing in an app is
 * remembered against that app's package name, and restored the next time a field
 * in the same app is focused. Apps with no stored pick follow the global
 * last-used layout ([KeyboardSettings.activeLayoutId]).
 */
data class PerAppLanguageSettings(
    /** Remember and restore the last explicitly-picked layout per app. */
    val enabled: Boolean = false,
    /** Package name → last explicitly-selected layout id. */
    val layoutByPackage: Map<String, String> = emptyMap(),
)

/**
 * Clipboard-tool settings — history capture, the panel, and the paste chip on
 * the suggestion strip — grouped into their own object (see [CameraSettings]
 * for why). DataStore keys stay flat.
 */
data class ClipboardSettings(
    /** Save copied text/images/files for quick paste from the clipboard tool. */
    val history: Boolean = true,
    /** Remove unpinned items after this many hours (0 = never). */
    val expiryHours: Int = 24,
    /** Fetch page titles for copied links and show them in the clipboard panel. */
    val linkPreviews: Boolean = false,
    /**
     * Record which app a clip was copied from (shown in the press-and-hold info
     * popup). Off by default: needs the Usage Access special permission and is a
     * best-effort guess of the foreground app at copy time.
     */
    val trackSource: Boolean = false,
    /**
     * Offer the most recently copied text as a paste chip on the suggestion
     * strip (Gboard style), so a fresh copy is one tap from being pasted.
     */
    val suggestRecent: Boolean = true,
    /**
     * Show an abc / space / backspace control row at the bottom of the clipboard
     * panel, like the emoji panel's, so a quick paste needs no detour to the keys.
     */
    val bottomRow: Boolean = false,
    /** List pinned entries at the end instead of the top of the clipboard panel. */
    val pinnedLast: Boolean = false,
    /** Show a search bar at the top of the clipboard panel to filter history. */
    val search: Boolean = false,
    /** Show user screenshots in the clipboard alongside copied text and images. */
    val userScreenshots: Boolean = false,
    /**
     * Delete a clip from history *and* from the system clipboard the moment it
     * is pasted into a password field. A password pasted out of a manager is
     * the single most sensitive thing the clipboard ever holds, and it would
     * otherwise sit there — readable by every app — until it expired. On by
     * default; turning it off keeps the clip like any other paste.
     */
    val clearAfterPasswordPaste: Boolean = true,
    /**
     * Pull one-time codes, phone numbers and links out of clips and offer them
     * as their own chips above the history, so the six digits inside a
     * verification SMS are one tap away instead of a copy-edit-paste.
     */
    val detectEntities: Boolean = true,
)

/**
 * Emoji behaviour split off into its own object because [KeyboardSettings]
 * sits at the JVM copy() slot ceiling (see [CameraSettings]). DataStore keys
 * stay flat.
 */
data class EmojiSettings(
    /**
     * Default skin tone shown for toned emoji in the suggestion strip and
     * emoji search. [EmojiSkinTone.NONE] keeps the neutral yellow base.
     */
    val defaultSkinTone: EmojiSkinTone = EmojiSkinTone.NONE,
    /**
     * Let the tone last picked for an emoji (from the panel's long-press
     * popup) override [defaultSkinTone] in suggestions and search. Off (the
     * default) means the global default always wins in those two places.
     */
    val toneOverrideByLastUsed: Boolean = false,
    /**
     * Close the emoji panel and return to the keys immediately after a single
     * emoji is inserted, instead of staying open for a run of emoji.
     */
    val closeAfterInsert: Boolean = false,
    /**
     * Hide emoji the device's emoji font can't draw (they render as a blank
     * "tofu" box) from the panel, search and suggestions. Detected per-glyph
     * against the active emoji font; importing a complete emoji font under
     * Emoji → Emoji font makes everything renderable again.
     */
    val hideUnrenderable: Boolean = false,
    /**
     * Let the emoji row scroll sideways to reach the emoji past its visible
     * slots. Off (the default) shows exactly [barCount] emoji and drops the
     * rest, so a sideways swipe can't slide the row out from under a tap.
     */
    val barScrollable: Boolean = false,
    /**
     * How many emoji the row fits across its width — equally, how tightly it
     * packs them, since each glyph shrinks to its slot. Beyond this the emoji
     * are only reachable with [barScrollable] on. See [EmojiBarCountRange].
     */
    val barCount: Int = 8,
    /**
     * Add Kaomoji ( ͡° ͜ʖ ͡°) and Emoticons :-) tabs to the end of the emoji
     * panel's tab strip. Off by default — they push the tab strip narrower,
     * and most users never reach for them.
     */
    val kaomojiTabs: Boolean = false,
)

/** Bounds for [EmojiSettings.barCount]; the settings slider shares them. */
val EmojiBarCountRange = 3..16

/** Glide-typing behaviour and swipe-trail appearance. See [KeyboardSettings.gesture]. */
data class GestureSettings(
    /**
     * Swiping over the spacebar mid-glide commits the current word and starts a
     * new one, so several words can be glided in one unbroken stroke. On by
     * default; off makes a swipe that crosses the spacebar decode as one word.
     */
    val spaceGlideMultiWord: Boolean = true,
    /**
     * How far the finger must travel before a press turns into a glide, as a
     * multiple of the system touch slop. Lower is more sensitive (a glide
     * starts sooner); higher needs a more deliberate swipe before it takes over
     * from a tap. Default 2×.
     */
    val startThresholdSlop: Float = 2f,
    /**
     * How long after the last keypress a glide is held back, in ms. During this
     * window right after tapping, a stray slide off a key needs to travel much
     * further before it is read as a swipe-word, so fast tap-typing does not
     * spill into accidental gestures. The extra distance fades to nothing across
     * the window. 0 disables the guard entirely; default 160 ms. Higher makes
     * gliding immediately after typing harder (fewer accidents, but a deliberate
     * swipe right after a tap is slower to start).
     */
    val postTypeCooldownMs: Int = 160,
    /**
     * Handwrite-with-swipes only. For this long after a drawn stroke lifts, a
     * quick tap over the letters is captured as another ink stroke of the same
     * character rather than typing its key — so the dot on an i or j, or the
     * cross on a t, can be added as a separate mark instead of committing a
     * letter. A press that lands after the window types as normal. 0 disables
     * it; default 700 ms.
     */
    val handwriteDotCooldownMs: Int = 700,
    /** Head width of the comet trail, in dp. The tail thins to ~30% of this. */
    val trailWidthDp: Float = 10f,
    /** How long each trail point stays on screen, in ms. Longer = a longer tail. */
    val trailDurationMs: Int = 350,
    /** Peak opacity of the trail, 0..1. */
    val trailOpacity: Float = 0.55f,
)

/**
 * Which glyph each customisable icon draws, grouped into its own class rather
 * than sitting flat on [KeyboardSettings] because that class's primary
 * constructor is at the JVM's 255-argument ceiling (see [ToolbarBehavior]).
 * Both fields still persist under their own DataStore key.
 *
 * Resolution order is [overrides], then [activePackId], then the built-in
 * glyph — a single icon the user picked by hand outranks the pack they
 * installed, which outranks the app's own default. See
 * `com.wasimaster.wmkeyboard.core.icons.IconSlots` for the slot ids and
 * `ime/ui/IconResolver.kt` for the lookup itself.
 */
data class IconSettings(
    /**
     * The installed icon pack supplying icons for every slot the user hasn't
     * overridden individually. Blank means the built-in icons.
     */
    val activePackId: String = "",
    /**
     * Slot id → icon source, for slots the user changed one at a time.
     *
     * A source is `b:<name>` for one of the bundled Material icons (see
     * `BuiltinIcons`) or `p:<packId>` to take that slot from a specific
     * installed pack. An entry naming a pack or an icon that no longer exists
     * falls back to the default rather than drawing nothing.
     */
    val overrides: Map<String, String> = emptyMap(),
)

/**
 * Assorted layout & gesture behaviours layered on top of the base keyboard,
 * grouped into their own class rather than sitting flat on [KeyboardSettings]
 * because that class's primary constructor is at the JVM's 255-argument
 * ceiling (see [ToolbarBehavior]). Each field still persists under its own
 * DataStore key via the matching setter.
 */
data class LayoutBehaviorSettings(
    /**
     * Long-pressing the ?123 / symbols key opens the numeric keypad panel on
     * any field, instead of the long-press behaving like a plain tap. Off by
     * default.
     */
    val symbolsLongPressNumpad: Boolean = false,
    /**
     * Swiping straight down on the spacebar dismisses the keyboard, the way a
     * downward flick on the toolbar can. Off by default so a stray vertical
     * drag never closes the keyboard mid-type.
     */
    val spaceSwipeDownHide: Boolean = false,
    /**
     * Turn the spacebar cursor slide into a 2-D touchpad: a vertical drag moves
     * the caret up and down as well as left and right. Only applies while a
     * spacebar swipe slot is set to cursor control; when on it also claims the
     * downward direction, so it takes precedence over [spaceSwipeDownHide].
     * Off by default.
     */
    val spaceCursor2d: Boolean = false,
    /** What the resting spacebar label shows: language, layout, or both. */
    val spacebarDisplay: SpacebarDisplay = SpacebarDisplay.LANGUAGE,
    /**
     * Size multiplier for the small corner hint character on each key (the
     * first long-press alternate, shown when [KeyboardSettings.longPressHints]
     * is on). 1.0 keeps the default 10sp base.
     */
    val hintFontScale: Float = 1.0f,
    /**
     * When on, holding shift on the letters layer swaps the extra number row's
     * digits for the symbol layer's bracket/math fill row (`=\<>[]{}|~`), so
     * those symbols are reachable without leaving the letters. Only has an
     * effect while [KeyboardSettings.numberRow] is on. Off by default.
     */
    val numberRowShiftSymbols: Boolean = false,
    /**
     * Smart key-hit detection: while a word is being typed, the touch target of
     * each letter is nudged toward the letters most likely to come next (from
     * the dictionary), so a tap that lands just inside a neighbour's cell still
     * commits the intended letter. Only biases boundary taps and only on the
     * letters layer; deliberate presses well inside a key are untouched. Off by
     * default.
     */
    val smartHitDetection: Boolean = false,
    /**
     * Which digit glyphs the number row and numpad draw, and (per
     * [numeralCommitScope]) type — chosen per language, keyed by
     * [com.wasimaster.wmkeyboard.core.script.LanguageDef.id]. An absent language
     * is [NumeralSystem.AUTO]: it follows its own default (Arabic → ٠-٩,
     * Persian/Urdu → ۰-۹, Bengali → ০-৯, the Devanagari languages → ०-९,
     * everything else Latin). Read it through [numeralSystemFor].
     */
    val numeralSystemByLang: Map<String, NumeralSystem> = emptyMap(),
    /**
     * Where a non-Latin numeral system rewrites committed digits. Default
     * [NumeralCommitScope.TEXT_ONLY] keeps ASCII in numeric/phone/date/time
     * fields so those stay machine-parseable while typing native digits
     * elsewhere. Drawing is unaffected — the glyphs always show on the keys.
     * Global on purpose: it is about what fields tolerate, not about a script.
     */
    val numeralCommitScope: NumeralCommitScope = NumeralCommitScope.TEXT_ONLY,
) {
    /** [langId]'s numeral system, [NumeralSystem.AUTO] when it has no entry. */
    fun numeralSystemFor(langId: String): NumeralSystem =
        numeralSystemByLang[langId] ?: NumeralSystem.AUTO
}

/**
 * Suggestion-strip content options, grouped into their own object (see
 * [CameraSettings] for why the top-level class can't take more flat fields).
 * DataStore keys stay flat.
 */
data class SuggestionStripSettings(
    /**
     * Offer a row of common punctuation ( . , ? ! ' ) beside the word
     * candidates, so a full stop or comma is one tap away without a detour to
     * the symbols layout. Shown only while candidates are up; an emoji
     * prediction takes the tail instead when one is present.
     */
    val punctuation: Boolean = false,
    /** Keep the suggestion strip as the default top bar even with nothing typed. */
    val suggestionsFirst: Boolean = false,
    /** Show the primary candidate in the middle slot (Gboard style) instead of the left. */
    val suggestionPrimaryCenter: Boolean = true,
    /**
     * Keep potentially-offensive words out of the suggestion strip and never
     * autocorrect a neutral typo into one. On by default (as AOSP ships it); the
     * words can always still be typed and committed verbatim. Lives here rather
     * than beside the other autocorrect flags only to stay under the settings
     * class's JVM field ceiling.
     */
    val blockOffensiveWords: Boolean = true,
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

/** Serializes the per-app layout map to a compact `pkg=layoutId;...` string. */
private fun encodePerAppLayouts(map: Map<String, String>): String =
    map.entries
        .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
        .joinToString(";") { (pkg, layoutId) -> "$pkg=$layoutId" }

private fun decodePerAppLayouts(raw: String): Map<String, String> =
    raw.split(';')
        .filter { it.isNotEmpty() }
        .mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0 || eq == entry.length - 1) return@mapNotNull null
            entry.substring(0, eq) to entry.substring(eq + 1)
        }
        .toMap()

/** Serializes the per-language Whisper model map to a compact `lang=modelId;...` string. */
private fun encodeWhisperModelByLang(map: Map<String, String>): String =
    map.entries
        .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
        .joinToString(";") { (language, modelId) -> "$language=$modelId" }

private fun decodeWhisperModelByLang(raw: String): Map<String, String> =
    raw.split(';')
        .filter { it.isNotEmpty() }
        .mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0 || eq == entry.length - 1) return@mapNotNull null
            entry.substring(0, eq) to entry.substring(eq + 1)
        }
        .toMap()

/** Serializes the per-language numeral map to a compact `lang=SYSTEM;...` string. */
private fun encodeNumeralSystems(map: Map<String, NumeralSystem>): String =
    map.entries
        .filter { it.key.isNotEmpty() && it.value != NumeralSystem.AUTO }
        .joinToString(";") { (language, system) -> "$language=${system.name}" }

private fun decodeNumeralSystems(raw: String): Map<String, NumeralSystem> =
    raw.split(';')
        .filter { it.isNotEmpty() }
        .mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0 || eq == entry.length - 1) return@mapNotNull null
            val system = runCatching {
                NumeralSystem.valueOf(entry.substring(eq + 1))
            }.getOrNull() ?: return@mapNotNull null
            entry.substring(0, eq) to system
        }
        .toMap()

/** Serializes the per-script font map to a compact `SCRIPT=fontId;...` string. */
private fun encodeScriptFontIds(map: Map<String, String>): String =
    map.entries
        .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
        .joinToString(";") { (script, fontId) -> "$script=$fontId" }

private fun decodeScriptFontIds(raw: String): Map<String, String> =
    raw.split(';')
        .filter { it.isNotEmpty() }
        .mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0 || eq == entry.length - 1) return@mapNotNull null
            entry.substring(0, eq) to entry.substring(eq + 1)
        }
        .toMap()

class SettingsRepository(private val context: Context) {

    /**
     * The device-protected copy of these settings, and the only one readable
     * during direct boot. See [LockedSettings] for what it does and does not
     * carry.
     */
    private val locked = LockedSettings(context)

    /**
     * Whether credential-encrypted storage is readable. Starts as whatever the
     * platform says at construction and only ever goes true — via
     * [onUserUnlocked], which the IME calls when the platform broadcasts the
     * unlock. Every read and write below routes on it, so a single flip moves
     * the whole repository from the mirror back to the real store.
     */
    private val unlocked = MutableStateFlow(DirectBoot.isUserUnlocked(context))

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
        private val AUTO_THEME_ENABLED = booleanPreferencesKey("auto_theme_enabled")
        private val AUTO_THEME_LIGHT_ID = stringPreferencesKey("auto_theme_light_id")
        private val AUTO_THEME_DARK_ID = stringPreferencesKey("auto_theme_dark_id")
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
        private fun keyboardScaleKey(v: ScreenVariant) =
            floatPreferencesKey(variantKey("keyboard_scale", v))
        private val KEY_GAP_SCALE = floatPreferencesKey("key_gap_scale")
        private val KEY_FONT_ID = stringPreferencesKey("key_font_id")
        private val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        private val BENGALI_FONT_ID = stringPreferencesKey("bengali_font_id")
        private val CUSTOM_BENGALI_FONT_NAME = stringPreferencesKey("custom_bengali_font_name")
        private val SCRIPT_FONT_IDS = stringPreferencesKey("script_font_ids")
        private val LEXICON_VERSION = intPreferencesKey("lexicon_version")
        private val CUSTOM_DICT_VERSION = intPreferencesKey("custom_dict_version")
        private val EMOJI_FONT = stringPreferencesKey("emoji_font")
        private val EMOJI_FONT_INSTALLED_ID = stringPreferencesKey("emoji_font_installed_id")
        private val AUTO_APOSTROPHE = booleanPreferencesKey("auto_apostrophe")
        private val HAPTIC = booleanPreferencesKey("haptic")
        private val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength")
        private val HAPTIC_AMPLITUDE = intPreferencesKey("haptic_amplitude")
        private val HAPTIC_STYLE = stringPreferencesKey("haptic_style")
        private val HAPTIC_ON_LONG_PRESS = booleanPreferencesKey("haptic_on_long_press")
        private val HAPTIC_ON_LONG_PRESS_RELEASE = booleanPreferencesKey("haptic_on_long_press_release")
        private val FEEDBACK_VIBRATE_SPACE = booleanPreferencesKey("feedback_vibrate_space")
        private val FEEDBACK_VIBRATE_DELETE_SWIPE = booleanPreferencesKey("feedback_vibrate_delete_swipe")
        private val FEEDBACK_VIBRATE_REPEAT = booleanPreferencesKey("feedback_vibrate_repeat")
        private val FEEDBACK_TOAST_ON_COPY = booleanPreferencesKey("feedback_toast_on_copy")
        private val FEEDBACK_HAPTICS_RESPECT_DND = booleanPreferencesKey("feedback_haptics_respect_dnd")
        private val KEY_SOUND = booleanPreferencesKey("key_sound")
        private val KEY_POPUP = booleanPreferencesKey("key_popup")
        private val KEY_POPUP_MIN_DURATION = intPreferencesKey("key_popup_min_duration")
        private val KEY_POPUP_MAX_DURATION = intPreferencesKey("key_popup_max_duration")
        private val KEY_POPUP_ON_KEY = booleanPreferencesKey("key_popup_on_key")
        private val KEY_POPUP_IN_NUMERIC = booleanPreferencesKey("key_popup_in_numeric_fields")
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
        private val AUTOCORRECT_SKIP_ALL_CAPS =
            booleanPreferencesKey("autocorrect_skip_all_caps")
        private val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        private val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        private val DOUBLE_SPACE_TAB = booleanPreferencesKey("double_space_tab")
        private val WRAP_SELECTION_WITH_PAIR = booleanPreferencesKey("wrap_selection_with_pair")
        private val RECAPITALIZE_SELECTION_WITH_SHIFT =
            booleanPreferencesKey("recapitalize_selection_with_shift")
        private val SUGGESTIONS = booleanPreferencesKey("suggestions")
        private val SHOW_SUGGESTIONS_ALL_FIELDS =
            booleanPreferencesKey("show_suggestions_all_fields")
        private val SUGGESTIONS_FIRST = booleanPreferencesKey("suggestions_first")
        private val SUGGESTION_PRIMARY_CENTER = booleanPreferencesKey("suggestion_primary_center")
        private val BLOCK_OFFENSIVE_WORDS = booleanPreferencesKey("block_offensive_words")
        private val CONTACT_SUGGESTIONS = booleanPreferencesKey("contact_suggestions")
        private val CONTACT_EMAIL_SUGGESTIONS =
            booleanPreferencesKey("contact_email_suggestions")
        private val CONTACT_EMAIL_SUGGESTIONS_IN_EMAIL_FIELDS =
            booleanPreferencesKey("contact_email_suggestions_in_email_fields")
        private val APP_NAME_SUGGESTIONS = booleanPreferencesKey("app_name_suggestions")
        private val SUGGESTION_BLACKLIST = stringSetPreferencesKey("suggestion_blacklist")
        private val INLINE_EMOJI_SEARCH = booleanPreferencesKey("inline_emoji_search")
        private val INLINE_AUTOFILL = booleanPreferencesKey("inline_autofill")
        private val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
        private val LETTER_SWIPE_ACTION = stringPreferencesKey("letter_swipe_action")
        private val GESTURE_SPACE_MULTI_WORD = booleanPreferencesKey("gesture_space_multi_word")
        private val GESTURE_START_THRESHOLD_SLOP = floatPreferencesKey("gesture_start_threshold_slop")
        private val GESTURE_POST_TYPE_COOLDOWN_MS = intPreferencesKey("gesture_post_type_cooldown_ms")
        private val GESTURE_HANDWRITE_DOT_COOLDOWN_MS = intPreferencesKey("gesture_handwrite_dot_cooldown_ms")
        private val GESTURE_TRAIL_WIDTH_DP = floatPreferencesKey("gesture_trail_width_dp")
        private val GESTURE_TRAIL_DURATION_MS = intPreferencesKey("gesture_trail_duration_ms")
        private val GESTURE_TRAIL_OPACITY = floatPreferencesKey("gesture_trail_opacity")
        // Legacy boolean, read only to migrate into SPACE_LONG_SWIPE.
        private val SPACEBAR_CURSOR = booleanPreferencesKey("spacebar_cursor")
        private val SPACE_SHORT_SWIPE = stringPreferencesKey("space_short_swipe")
        private val SPACE_LONG_SWIPE = stringPreferencesKey("space_long_swipe")
        private val SPACEBAR_LANGUAGE_ARROWS = booleanPreferencesKey("spacebar_language_arrows")
        private val SPACEBAR_LABEL = stringPreferencesKey("spacebar_label")
        private val SYMBOLS_LONGPRESS_NUMPAD = booleanPreferencesKey("symbols_longpress_numpad")
        private val SPACE_SWIPE_DOWN_HIDE = booleanPreferencesKey("space_swipe_down_hide")
        private val SPACE_CURSOR_2D = booleanPreferencesKey("space_cursor_2d")
        private val HINT_FONT_SCALE = floatPreferencesKey("hint_font_scale")
        private val NUMBER_ROW_SHIFT_SYMBOLS = booleanPreferencesKey("number_row_shift_symbols")
        private val SMART_HIT_DETECTION = booleanPreferencesKey("smart_hit_detection")
        private val SPACEBAR_DISPLAY = stringPreferencesKey("spacebar_display")
        private val NUMERAL_SYSTEM_BY_LANG = stringPreferencesKey("numeral_system_by_lang")
        private val NUMERAL_COMMIT_SCOPE = stringPreferencesKey("numeral_commit_scope")
        private val BACKSPACE_SWIPE_DELETE = booleanPreferencesKey("backspace_swipe_delete")
        private val HARDWARE_KEYBOARD_INPUT = booleanPreferencesKey("hardware_keyboard_input")
        private val HW_SHORTCUTS_ENABLED = booleanPreferencesKey("hw_shortcuts_enabled")
        private val HW_PANEL_NAVIGATION = booleanPreferencesKey("hw_panel_navigation")
        private val HW_ESC_CLOSES_PANEL = booleanPreferencesKey("hw_esc_closes_panel")
        private val HW_SUGGESTION_HOTKEYS = stringPreferencesKey("hw_suggestion_hotkeys")
        private val HW_AUTO_SHOW_UI = booleanPreferencesKey("hw_auto_show_ui")
        private val HW_LEADER = stringPreferencesKey("hw_leader")
        private val HW_PICKER_TIMEOUT_MS = intPreferencesKey("hw_picker_timeout_ms")
        private val HW_TOOL_LETTERS = stringPreferencesKey("hw_tool_letters")
        private val VOLUME_CURSOR = booleanPreferencesKey("volume_cursor")
        private val VOLUME_CURSOR_MEDIA_AWARE = booleanPreferencesKey("volume_cursor_media_aware")
        private val GLOBE_AS_EMOJI = booleanPreferencesKey("globe_as_emoji")
        private val OS_LANGUAGE_SWITCHER = booleanPreferencesKey("os_language_switcher")
        private val SUBTYPE_APP_NAME_FIRST = booleanPreferencesKey("subtype_app_name_first")
        private val PER_APP_LANGUAGE_ENABLED = booleanPreferencesKey("per_app_language_enabled")
        private val PER_APP_LAYOUT_MAP = stringPreferencesKey("per_app_layout_map")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val CONJUNCT_BACKSPACE = booleanPreferencesKey("conjunct_backspace")
        private val PINYIN_FUZZY = booleanPreferencesKey("pinyin_fuzzy")
        private val PINYIN_DOUBLE_PINYIN = stringPreferencesKey("pinyin_double_pinyin")
        private val CJK_TRADITIONAL_OUTPUT = booleanPreferencesKey("cjk_traditional_output")
        private val JYUTPING_LAZY = booleanPreferencesKey("jyutping_lazy")
        private val CJK_HAN_REGION = stringPreferencesKey("cjk_han_region")
        private val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        // One-handed width leaves room for the rail on the inner edge, so it is
        // capped below 100%. Height scale never grows the keys, only shrinks.
        const val ONE_HANDED_WIDTH_MIN = 40
        const val ONE_HANDED_WIDTH_MAX = 85
        const val ONE_HANDED_HEIGHT_SCALE_MIN = 60
        const val ONE_HANDED_HEIGHT_SCALE_MAX = 100
        // Per-orientation one-handed geometry. `portrait` = false suffix keeps
        // the two orientations in step by construction.
        private fun oneHandedWidthKey(landscape: Boolean) =
            intPreferencesKey("one_handed_width_${if (landscape) "landscape" else "portrait"}")
        private fun oneHandedHeightScaleKey(landscape: Boolean) =
            intPreferencesKey("one_handed_height_scale_${if (landscape) "landscape" else "portrait"}")
        private fun oneHandedSideKey(landscape: Boolean) =
            stringPreferencesKey("one_handed_side_${if (landscape) "landscape" else "portrait"}")
        private val LEARN_FROM_TYPING = booleanPreferencesKey("learn_from_typing")
        private val ADD_WORDS_TO_SYSTEM_DICTIONARY =
            booleanPreferencesKey("add_words_to_system_dictionary")
        private val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        private val CLIPBOARD_EXPIRY_HOURS = intPreferencesKey("clipboard_expiry_hours")
        private val CLIPBOARD_LINK_PREVIEWS = booleanPreferencesKey("clipboard_link_previews")
        private val CLIPBOARD_TRACK_SOURCE = booleanPreferencesKey("clipboard_track_source")
        private val CLIPBOARD_SUGGEST_RECENT = booleanPreferencesKey("clipboard_suggest_recent")
        private val PUNCTUATION_SUGGESTIONS = booleanPreferencesKey("punctuation_suggestions")
        private val CLIPBOARD_BOTTOM_ROW = booleanPreferencesKey("clipboard_bottom_row")
        private val CLIPBOARD_PINNED_LAST = booleanPreferencesKey("clipboard_pinned_last")
        private val CLIPBOARD_SEARCH = booleanPreferencesKey("clipboard_search")
        private val CLIPBOARD_USER_SCREENSHOTS = booleanPreferencesKey("clipboard_user_screenshots")
        private val CLIPBOARD_CLEAR_AFTER_PASSWORD_PASTE =
            booleanPreferencesKey("clipboard_clear_after_password_paste")
        private val CLIPBOARD_DETECT_ENTITIES = booleanPreferencesKey("clipboard_detect_entities")
        private val LONG_PRESS_DELAY = intPreferencesKey("long_press_delay")
        private val KEY_REPEAT_INTERVAL = intPreferencesKey("key_repeat_interval")
        private val LONG_PRESS_HINTS = booleanPreferencesKey("long_press_hints")
        private val LONG_PRESS_A_SELECT_ALL = booleanPreferencesKey("long_press_a_select_all")
        private val LONG_PRESS_C_COPY = booleanPreferencesKey("long_press_c_copy")
        private val LONG_PRESS_V_PASTE = booleanPreferencesKey("long_press_v_paste")
        private val LONG_PRESS_X_CUT = booleanPreferencesKey("long_press_x_cut")
        private val LONG_PRESS_Z_UNDO = booleanPreferencesKey("long_press_z_undo")
        private val LONG_PRESS_Y_REDO = booleanPreferencesKey("long_press_y_redo")
        private val EMOJI_TOOLBAR = booleanPreferencesKey("emoji_toolbar")
        private val COLORED_TOOL_ICONS = booleanPreferencesKey("colored_tool_icons")
        private val TOOL_COLOR_OVERRIDES = stringPreferencesKey("tool_color_overrides")
        private val ICON_PACK_ID = stringPreferencesKey("icon_pack_id")
        private val ICON_OVERRIDES = stringPreferencesKey("icon_overrides")
        private val INCOGNITO = booleanPreferencesKey("incognito")
        private val TOOLBAR_TOOLS = stringPreferencesKey("toolbar_tools")
        private val TOOLBAR_GREEDY = booleanPreferencesKey("toolbar_greedy")
        private val TOOLBAR_ENABLED = booleanPreferencesKey("toolbar_enabled")
        private val TOOLBAR_SWIPE_DOWN_HIDE = booleanPreferencesKey("toolbar_swipe_down_hide")
        private val TOOLBAR_ONLY_HW_KEYBOARD = booleanPreferencesKey("toolbar_only_hw_keyboard")
        private val REVERSE_TOOLBAR_RTL = booleanPreferencesKey("reverse_toolbar_rtl")
        private val TOOLBAR_HEIGHT = intPreferencesKey("toolbar_height")
        private val TOOLBAR_SCROLLABLE = booleanPreferencesKey("toolbar_scrollable")
        private val TOOLBAR_HIDE_WHEN_LOCKED = booleanPreferencesKey("toolbar_hide_when_locked")
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
        private val EMOJI_DEFAULT_SKIN_TONE = stringPreferencesKey("emoji_default_skin_tone")
        private val EMOJI_TONE_OVERRIDE_LAST_USED =
            booleanPreferencesKey("emoji_tone_override_last_used")
        private val EMOJI_CLOSE_AFTER_INSERT = booleanPreferencesKey("emoji_close_after_insert")
        private val EMOJI_HIDE_UNRENDERABLE = booleanPreferencesKey("emoji_hide_unrenderable")
        private val EMOJI_BAR_SCROLLABLE = booleanPreferencesKey("emoji_bar_scrollable")
        private val EMOJI_BAR_COUNT = intPreferencesKey("emoji_bar_count")
        private val EMOJI_KAOMOJI_TABS = booleanPreferencesKey("emoji_kaomoji_tabs")
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
        private val KEY_SOUND_CUSTOM_ID = stringPreferencesKey("key_sound_custom_id")
        private val LEVEL_SHOW_ANGLES = booleanPreferencesKey("level_show_angles")
        private val REDO_USES_CTRL_Y = booleanPreferencesKey("redo_uses_ctrl_y")
        private val MOON_SOUTHERN = booleanPreferencesKey("moon_southern_hemisphere")
        private val WEATHER_FAHRENHEIT = booleanPreferencesKey("weather_fahrenheit")
        private val WEATHER_LAT = floatPreferencesKey("weather_lat")
        private val WEATHER_LON = floatPreferencesKey("weather_lon")
        private val WEATHER_PLACE = stringPreferencesKey("weather_place")
        // Superseded by CALENDAR_ALT_ONE/TWO, still read once to carry the old
        // Bengali/Hijri switches over to the new pair of picks.
        private val CALENDAR_SHOW_BENGALI = booleanPreferencesKey("calendar_show_bengali")
        private val CALENDAR_SHOW_HIJRI = booleanPreferencesKey("calendar_show_hijri")
        private val CALENDAR_ALT_ONE = stringPreferencesKey("calendar_alt_one")
        private val CALENDAR_ALT_TWO = stringPreferencesKey("calendar_alt_two")
        private val HIJRI_ADJUST_DAYS = intPreferencesKey("hijri_adjust_days")
        private val HANDWRITING_STYLUS_ONLY = booleanPreferencesKey("handwriting_stylus_only")
        private val HANDWRITING_COMMIT_DELAY = intPreferencesKey("handwriting_commit_delay")
        private val HANDWRITING_AUTO_SPACE = booleanPreferencesKey("handwriting_auto_space")
        private val VOICE_STRIP_MODE = booleanPreferencesKey("voice_strip_mode")
        private val VOICE_CONTINUOUS = booleanPreferencesKey("voice_continuous")
        private val VOICE_SPOKEN_PUNCTUATION = booleanPreferencesKey("voice_spoken_punctuation")
        private val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        private val WHISPER_MODEL_ID = stringPreferencesKey("whisper_model_id")
        private val WHISPER_MODEL_BY_LANG = stringPreferencesKey("whisper_model_by_lang")
        private val WHISPER_TRANSLATE = booleanPreferencesKey("whisper_translate")
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
        private val SPELL_CHECKER_NO_SUGGESTIONS =
            booleanPreferencesKey("spell_checker_no_suggestions")
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

    /**
     * The live settings.
     *
     * Unlocked, this is the DataStore, and each emission republishes the
     * device-protected mirror so the next direct boot draws the keyboard the
     * user actually configured. Locked, it is the mirror itself — the DataStore
     * is not merely empty then but unreadable, so it is never touched.
     *
     * The switch is a [flatMapLatest] on [unlocked]: an unlock while the
     * keyboard is on screen tears down the mirror flow and re-collects the real
     * one, and existing collectors just see one more emission.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val settings: Flow<KeyboardSettings> = unlocked
        .flatMapLatest { isUnlocked ->
            if (isUnlocked) {
                context.dataStore.data
                    .onEach { locked.write(it) }
                    // The mirror write is disk work; keep it off whichever
                    // dispatcher the collector (the IME's main-thread scope)
                    // happens to be on.
                    .flowOn(Dispatchers.IO)
            } else {
                locked.snapshots()
            }
        }
        .map { mapPreferences(it) }

    /**
     * Called when the platform broadcasts that credential-encrypted storage has
     * become readable ([android.content.Intent.ACTION_USER_UNLOCKED]). Flips
     * every read and write back to the real store and re-emits [settings] from
     * it, discarding whatever the locked session wrote to the mirror.
     */
    fun onUserUnlocked() {
        unlocked.value = true
    }

    /**
     * Every write goes through here so that exactly one place knows which store
     * is writable. Locked, edits land in the device-protected mirror: the
     * keyboard's own toggles keep working on the lock screen, and the first
     * emission after unlock overwrites them.
     */
    private suspend fun editPrefs(transform: suspend (MutablePreferences) -> Unit) {
        if (unlocked.value) context.dataStore.edit { transform(it) }
        else locked.edit { transform(it) }
    }

    private fun mapPreferences(p: Preferences): KeyboardSettings {
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
        return KeyboardSettings(
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
            autoTheme = AutoThemeSettings(
                enabled = p[AUTO_THEME_ENABLED] ?: defaults.autoTheme.enabled,
                lightThemeId = p[AUTO_THEME_LIGHT_ID] ?: defaults.autoTheme.lightThemeId,
                darkThemeId = p[AUTO_THEME_DARK_ID] ?: defaults.autoTheme.darkThemeId,
            ),
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
            keyGapScale = p[KEY_GAP_SCALE] ?: defaults.keyGapScale,
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
                        keyboardScale = p[keyboardScaleKey(v)],
                    )
                }
                .filterValues { !it.isEmpty },
            keyFontId = p[KEY_FONT_ID] ?: defaults.keyFontId,
            customFontName = p[CUSTOM_FONT_NAME] ?: defaults.customFontName,
            bengaliFontId = p[BENGALI_FONT_ID] ?: defaults.bengaliFontId,
            scriptFontIds = p[SCRIPT_FONT_IDS]?.let { decodeScriptFontIds(it) } ?: defaults.scriptFontIds,
            customBengaliFontName = p[CUSTOM_BENGALI_FONT_NAME]
                ?: defaults.customBengaliFontName,
            lexiconVersion = p[LEXICON_VERSION] ?: defaults.lexiconVersion,
            customDictVersion = p[CUSTOM_DICT_VERSION] ?: defaults.customDictVersion,
            emojiFont = p[EMOJI_FONT]
                ?.let { runCatching { EmojiFontChoice.valueOf(it) }.getOrNull() }
                ?: defaults.emojiFont,
            emojiFontInstalled = EmojiFontSettings(
                installedId = p[EMOJI_FONT_INSTALLED_ID] ?: defaults.emojiFontInstalled.installedId,
            ),
            hapticFeedback = p[HAPTIC] ?: defaults.hapticFeedback,
            hapticStrengthMs = p[HAPTIC_STRENGTH] ?: defaults.hapticStrengthMs,
            hapticAmplitude = p[HAPTIC_AMPLITUDE] ?: defaults.hapticAmplitude,
            hapticStyle = p[HAPTIC_STYLE]?.let { runCatching { HapticStyle.valueOf(it) }.getOrNull() }
                ?: defaults.hapticStyle,
            hapticOnLongPress = p[HAPTIC_ON_LONG_PRESS] ?: defaults.hapticOnLongPress,
            hapticOnLongPressRelease = p[HAPTIC_ON_LONG_PRESS_RELEASE]
                ?: defaults.hapticOnLongPressRelease,
            feedback = FeedbackSettings(
                vibrateOnSpace = p[FEEDBACK_VIBRATE_SPACE] ?: defaults.feedback.vibrateOnSpace,
                vibrateOnDeleteSwipe = p[FEEDBACK_VIBRATE_DELETE_SWIPE]
                    ?: defaults.feedback.vibrateOnDeleteSwipe,
                vibrateOnRepeat = p[FEEDBACK_VIBRATE_REPEAT] ?: defaults.feedback.vibrateOnRepeat,
                toastOnCopy = p[FEEDBACK_TOAST_ON_COPY] ?: defaults.feedback.toastOnCopy,
                hapticsRespectDnd = p[FEEDBACK_HAPTICS_RESPECT_DND]
                    ?: defaults.feedback.hapticsRespectDnd,
            ),
            keySound = p[KEY_SOUND] ?: defaults.keySound,
            keySoundStyle = p[KEY_SOUND_STYLE]
                ?.let { runCatching { KeySoundStyle.valueOf(it) }.getOrNull() }
                ?: defaults.keySoundStyle,
            keySoundVolume = p[KEY_SOUND_VOLUME] ?: defaults.keySoundVolume,
            keySoundCustom = KeySoundSettings(
                customId = p[KEY_SOUND_CUSTOM_ID] ?: defaults.keySoundCustom.customId,
            ),
            popup = KeyPopupSettings(
                enabled = p[KEY_POPUP] ?: defaults.popup.enabled,
                minDurationMs = p[KEY_POPUP_MIN_DURATION] ?: defaults.popup.minDurationMs,
                maxDurationMs = p[KEY_POPUP_MAX_DURATION] ?: defaults.popup.maxDurationMs,
                onKey = p[KEY_POPUP_ON_KEY] ?: defaults.popup.onKey,
                inNumericFields = p[KEY_POPUP_IN_NUMERIC] ?: defaults.popup.inNumericFields,
                fontScale = p[POPUP_FONT_SCALE] ?: defaults.popup.fontScale,
                heightDp = p[KEY_POPUP_HEIGHT] ?: defaults.popup.heightDp,
            ),
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
            autocorrectSkipAllCaps =
                p[AUTOCORRECT_SKIP_ALL_CAPS] ?: defaults.autocorrectSkipAllCaps,
            autoApostrophe = p[AUTO_APOSTROPHE] ?: defaults.autoApostrophe,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
            doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: defaults.doubleSpacePeriod,
            doubleSpaceTab = p[DOUBLE_SPACE_TAB] ?: defaults.doubleSpaceTab,
            suggestions = p[SUGGESTIONS] ?: defaults.suggestions,
            showSuggestionsInAllFields = p[SHOW_SUGGESTIONS_ALL_FIELDS]
                ?: defaults.showSuggestionsInAllFields,
            contactSuggestions = p[CONTACT_SUGGESTIONS] ?: defaults.contactSuggestions,
            contactEmailSuggestions = p[CONTACT_EMAIL_SUGGESTIONS]
                ?: defaults.contactEmailSuggestions,
            contactEmailSuggestionsInEmailFields = p[CONTACT_EMAIL_SUGGESTIONS_IN_EMAIL_FIELDS]
                ?: defaults.contactEmailSuggestionsInEmailFields,
            appNameSuggestions = p[APP_NAME_SUGGESTIONS] ?: defaults.appNameSuggestions,
            suggestionBlacklist = p[SUGGESTION_BLACKLIST] ?: defaults.suggestionBlacklist,
            inlineEmojiSearch = p[INLINE_EMOJI_SEARCH] ?: defaults.inlineEmojiSearch,
            inlineAutofill = p[INLINE_AUTOFILL] ?: defaults.inlineAutofill,
            gestureTyping = p[GESTURE_TYPING] ?: defaults.gestureTyping,
            letterSwipeAction = p[LETTER_SWIPE_ACTION]
                ?.let { runCatching { LetterSwipeAction.valueOf(it) }.getOrNull() }
                ?: defaults.letterSwipeAction,
            gesture = GestureSettings(
                spaceGlideMultiWord = p[GESTURE_SPACE_MULTI_WORD] ?: defaults.gesture.spaceGlideMultiWord,
                startThresholdSlop = p[GESTURE_START_THRESHOLD_SLOP] ?: defaults.gesture.startThresholdSlop,
                postTypeCooldownMs = p[GESTURE_POST_TYPE_COOLDOWN_MS] ?: defaults.gesture.postTypeCooldownMs,
                handwriteDotCooldownMs = p[GESTURE_HANDWRITE_DOT_COOLDOWN_MS] ?: defaults.gesture.handwriteDotCooldownMs,
                trailWidthDp = p[GESTURE_TRAIL_WIDTH_DP] ?: defaults.gesture.trailWidthDp,
                trailDurationMs = p[GESTURE_TRAIL_DURATION_MS] ?: defaults.gesture.trailDurationMs,
                trailOpacity = p[GESTURE_TRAIL_OPACITY] ?: defaults.gesture.trailOpacity,
            ),
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
            hardwareKeyboardInput = p[HARDWARE_KEYBOARD_INPUT] ?: defaults.hardwareKeyboardInput,
            hardwareKeyboard = HardwareKeyboardSettings(
                shortcutsEnabled = p[HW_SHORTCUTS_ENABLED] ?: defaults.hardwareKeyboard.shortcutsEnabled,
                panelNavigation = p[HW_PANEL_NAVIGATION] ?: defaults.hardwareKeyboard.panelNavigation,
                escClosesPanel = p[HW_ESC_CLOSES_PANEL] ?: defaults.hardwareKeyboard.escClosesPanel,
                suggestionHotkeys = p[HW_SUGGESTION_HOTKEYS]
                    ?.let { raw -> runCatching { SuggestionHotkeyMode.valueOf(raw) }.getOrNull() }
                    ?: defaults.hardwareKeyboard.suggestionHotkeys,
                autoShowUi = p[HW_AUTO_SHOW_UI] ?: defaults.hardwareKeyboard.autoShowUi,
                leader = p[HW_LEADER] ?: defaults.hardwareKeyboard.leader,
                pickerTimeoutMs = p[HW_PICKER_TIMEOUT_MS] ?: defaults.hardwareKeyboard.pickerTimeoutMs,
                // Absent, not empty, means "never edited": an empty stored map is
                // a user who unbound every letter, and must stay empty.
                toolByLetter = p[HW_TOOL_LETTERS]?.let(::decodeToolLetters)
                    ?: defaults.hardwareKeyboard.toolByLetter,
            ),
            volumeCursor = p[VOLUME_CURSOR] ?: defaults.volumeCursor,
            volumeCursorMediaAware = p[VOLUME_CURSOR_MEDIA_AWARE] ?: defaults.volumeCursorMediaAware,
            globeAsEmoji = p[GLOBE_AS_EMOJI] ?: defaults.globeAsEmoji,
            osLanguageSwitcher = p[OS_LANGUAGE_SWITCHER] ?: defaults.osLanguageSwitcher,
            subtypeAppNameFirst = p[SUBTYPE_APP_NAME_FIRST] ?: defaults.subtypeAppNameFirst,
            perAppLanguage = PerAppLanguageSettings(
                enabled = p[PER_APP_LANGUAGE_ENABLED] ?: defaults.perAppLanguage.enabled,
                layoutByPackage = p[PER_APP_LAYOUT_MAP]?.let { decodePerAppLayouts(it) }
                    ?: defaults.perAppLanguage.layoutByPackage,
            ),
            onboardingDone = p[ONBOARDING_DONE] ?: defaults.onboardingDone,
            conjunctBackspace = p[CONJUNCT_BACKSPACE] ?: defaults.conjunctBackspace,
            cjk = CjkSettings(
                pinyinFuzzy = p[PINYIN_FUZZY] ?: defaults.cjk.pinyinFuzzy,
                pinyinDoublePinyin = p[PINYIN_DOUBLE_PINYIN]
                    ?.let { runCatching { DoublePinyinScheme.valueOf(it) }.getOrNull() }
                    ?: defaults.cjk.pinyinDoublePinyin,
                traditionalOutput = p[CJK_TRADITIONAL_OUTPUT] ?: defaults.cjk.traditionalOutput,
                jyutpingLazy = p[JYUTPING_LAZY] ?: defaults.cjk.jyutpingLazy,
                hanRegion = p[CJK_HAN_REGION]
                    ?.let { runCatching { HanVariant.HanRegion.valueOf(it) }.getOrNull() }
                    ?: defaults.cjk.hanRegion,
            ),
            oneHandedMode = p[ONE_HANDED_MODE]
                ?.let { runCatching { OneHandedMode.valueOf(it) }.getOrNull() }
                ?: defaults.oneHandedMode,
            oneHanded = OneHandedSettings(
                portrait = readOneHandedProfile(p, landscape = false, defaults.oneHanded.portrait),
                landscape = readOneHandedProfile(p, landscape = true, defaults.oneHanded.landscape),
            ),
            learnFromTyping = p[LEARN_FROM_TYPING] ?: defaults.learnFromTyping,
            addWordsToSystemDictionary =
                p[ADD_WORDS_TO_SYSTEM_DICTIONARY] ?: defaults.addWordsToSystemDictionary,
            clipboard = ClipboardSettings(
                history = p[CLIPBOARD_HISTORY] ?: defaults.clipboard.history,
                expiryHours = p[CLIPBOARD_EXPIRY_HOURS] ?: defaults.clipboard.expiryHours,
                linkPreviews = p[CLIPBOARD_LINK_PREVIEWS] ?: defaults.clipboard.linkPreviews,
                trackSource = p[CLIPBOARD_TRACK_SOURCE] ?: defaults.clipboard.trackSource,
                suggestRecent = p[CLIPBOARD_SUGGEST_RECENT] ?: defaults.clipboard.suggestRecent,
                bottomRow = p[CLIPBOARD_BOTTOM_ROW] ?: defaults.clipboard.bottomRow,
                pinnedLast = p[CLIPBOARD_PINNED_LAST] ?: defaults.clipboard.pinnedLast,
                search = p[CLIPBOARD_SEARCH] ?: defaults.clipboard.search,
                userScreenshots = p[CLIPBOARD_USER_SCREENSHOTS] ?: defaults.clipboard.userScreenshots,
                clearAfterPasswordPaste = p[CLIPBOARD_CLEAR_AFTER_PASSWORD_PASTE]
                    ?: defaults.clipboard.clearAfterPasswordPaste,
                detectEntities = p[CLIPBOARD_DETECT_ENTITIES] ?: defaults.clipboard.detectEntities,
            ),
            suggestionStrip = SuggestionStripSettings(
                punctuation = p[PUNCTUATION_SUGGESTIONS] ?: defaults.suggestionStrip.punctuation,
                suggestionsFirst = p[SUGGESTIONS_FIRST] ?: defaults.suggestionStrip.suggestionsFirst,
                suggestionPrimaryCenter = p[SUGGESTION_PRIMARY_CENTER]
                    ?: defaults.suggestionStrip.suggestionPrimaryCenter,
                blockOffensiveWords = p[BLOCK_OFFENSIVE_WORDS]
                    ?: defaults.suggestionStrip.blockOffensiveWords,
            ),
            longPressDelayMs = p[LONG_PRESS_DELAY] ?: defaults.longPressDelayMs,
            keyRepeatIntervalMs = p[KEY_REPEAT_INTERVAL] ?: defaults.keyRepeatIntervalMs,
            longPressHints = p[LONG_PRESS_HINTS] ?: defaults.longPressHints,
            layoutBehavior = LayoutBehaviorSettings(
                symbolsLongPressNumpad =
                    p[SYMBOLS_LONGPRESS_NUMPAD] ?: defaults.layoutBehavior.symbolsLongPressNumpad,
                spaceSwipeDownHide =
                    p[SPACE_SWIPE_DOWN_HIDE] ?: defaults.layoutBehavior.spaceSwipeDownHide,
                spaceCursor2d = p[SPACE_CURSOR_2D] ?: defaults.layoutBehavior.spaceCursor2d,
                hintFontScale = p[HINT_FONT_SCALE] ?: defaults.layoutBehavior.hintFontScale,
                numberRowShiftSymbols =
                    p[NUMBER_ROW_SHIFT_SYMBOLS] ?: defaults.layoutBehavior.numberRowShiftSymbols,
                smartHitDetection =
                    p[SMART_HIT_DETECTION] ?: defaults.layoutBehavior.smartHitDetection,
                spacebarDisplay = p[SPACEBAR_DISPLAY]
                    ?.let { runCatching { SpacebarDisplay.valueOf(it) }.getOrNull() }
                    ?: defaults.layoutBehavior.spacebarDisplay,
                numeralSystemByLang = p[NUMERAL_SYSTEM_BY_LANG]
                    ?.let { decodeNumeralSystems(it) }
                    ?: defaults.layoutBehavior.numeralSystemByLang,
                numeralCommitScope = p[NUMERAL_COMMIT_SCOPE]
                    ?.let { runCatching { NumeralCommitScope.valueOf(it) }.getOrNull() }
                    ?: defaults.layoutBehavior.numeralCommitScope,
            ),
            rawClipboardShortcuts = p[RAW_CLIPBOARD_SHORTCUTS] ?: defaults.rawClipboardShortcuts,
            longPressLetterActions = LongPressLetterActions(
                selectAll = p[LONG_PRESS_A_SELECT_ALL] ?: defaults.longPressLetterActions.selectAll,
                copy = p[LONG_PRESS_C_COPY] ?: defaults.longPressLetterActions.copy,
                paste = p[LONG_PRESS_V_PASTE] ?: defaults.longPressLetterActions.paste,
                cut = p[LONG_PRESS_X_CUT] ?: defaults.longPressLetterActions.cut,
                undo = p[LONG_PRESS_Z_UNDO] ?: defaults.longPressLetterActions.undo,
                redo = p[LONG_PRESS_Y_REDO] ?: defaults.longPressLetterActions.redo,
            ),
            emojiToolbar = p[EMOJI_TOOLBAR] ?: defaults.emojiToolbar,
            coloredToolIcons = p[COLORED_TOOL_ICONS] ?: defaults.coloredToolIcons,
            toolColorOverrides = decodeToolColors(p[TOOL_COLOR_OVERRIDES]),
            icons = IconSettings(
                activePackId = p[ICON_PACK_ID] ?: defaults.icons.activePackId,
                overrides = IconOverrides.decode(p[ICON_OVERRIDES]),
            ),
            incognito = p[INCOGNITO] ?: defaults.incognito,
            // Empty stored string is a valid state (everything in the toolbox),
            // distinct from never-set (defaults apply).
            toolbarTools = p[TOOLBAR_TOOLS]?.let { csv ->
                if (csv.isEmpty()) emptyList()
                else csv.split(',').mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            } ?: defaults.toolbarTools,
            toolbarBehavior = ToolbarBehavior(
                enabled = p[TOOLBAR_ENABLED] ?: defaults.toolbarBehavior.enabled,
                swipeDownHide = p[TOOLBAR_SWIPE_DOWN_HIDE] ?: defaults.toolbarBehavior.swipeDownHide,
                onlyWithHardwareKeyboard =
                    p[TOOLBAR_ONLY_HW_KEYBOARD] ?: defaults.toolbarBehavior.onlyWithHardwareKeyboard,
                reverseForRtl = p[REVERSE_TOOLBAR_RTL] ?: defaults.toolbarBehavior.reverseForRtl,
                greedy = p[TOOLBAR_GREEDY] ?: defaults.toolbarBehavior.greedy,
                scrollable = p[TOOLBAR_SCROLLABLE] ?: defaults.toolbarBehavior.scrollable,
                hideWhenLocked = p[TOOLBAR_HIDE_WHEN_LOCKED] ?: defaults.toolbarBehavior.hideWhenLocked,
            ),
            toolbarHeightDp = p[TOOLBAR_HEIGHT] ?: defaults.toolbarHeightDp,
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
            emoji = EmojiSettings(
                defaultSkinTone = p[EMOJI_DEFAULT_SKIN_TONE]
                    ?.let { runCatching { EmojiSkinTone.valueOf(it) }.getOrNull() }
                    ?: defaults.emoji.defaultSkinTone,
                toneOverrideByLastUsed = p[EMOJI_TONE_OVERRIDE_LAST_USED]
                    ?: defaults.emoji.toneOverrideByLastUsed,
                closeAfterInsert = p[EMOJI_CLOSE_AFTER_INSERT] ?: defaults.emoji.closeAfterInsert,
                hideUnrenderable = p[EMOJI_HIDE_UNRENDERABLE] ?: defaults.emoji.hideUnrenderable,
                barScrollable = p[EMOJI_BAR_SCROLLABLE] ?: defaults.emoji.barScrollable,
                barCount = p[EMOJI_BAR_COUNT]?.coerceIn(EmojiBarCountRange)
                    ?: defaults.emoji.barCount,
                kaomojiTabs = p[EMOJI_KAOMOJI_TABS] ?: defaults.emoji.kaomojiTabs,
            ),
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
            calendarAltOne = calendarAltFromPrefs(p, first = true, defaults = defaults),
            calendarAltTwo = calendarAltFromPrefs(p, first = false, defaults = defaults),
            hijriAdjustDays = p[HIJRI_ADJUST_DAYS] ?: defaults.hijriAdjustDays,
            handwritingStylusOnly = p[HANDWRITING_STYLUS_ONLY] ?: defaults.handwritingStylusOnly,
            handwritingCommitDelayMs = p[HANDWRITING_COMMIT_DELAY]
                ?: defaults.handwritingCommitDelayMs,
            handwritingAutoSpace = p[HANDWRITING_AUTO_SPACE] ?: defaults.handwritingAutoSpace,
            voiceStripMode = p[VOICE_STRIP_MODE] ?: defaults.voiceStripMode,
            voiceContinuous = p[VOICE_CONTINUOUS] ?: defaults.voiceContinuous,
            voiceSpokenPunctuation = p[VOICE_SPOKEN_PUNCTUATION]
                ?: defaults.voiceSpokenPunctuation,
            whisper = WhisperSettings(
                engine = p[VOICE_ENGINE] ?: defaults.whisper.engine,
                modelId = p[WHISPER_MODEL_ID] ?: defaults.whisper.modelId,
                modelByLang = p[WHISPER_MODEL_BY_LANG]?.let { decodeWhisperModelByLang(it) }
                    ?: defaults.whisper.modelByLang,
                translate = p[WHISPER_TRANSLATE] ?: defaults.whisper.translate,
            ),
            camera = CameraSettings(
                preferFront = p[CAMERA_PREFER_FRONT] ?: defaults.camera.preferFront,
                mirrorFront = p[CAMERA_MIRROR_FRONT] ?: defaults.camera.mirrorFront,
                shutterSound = p[CAMERA_SHUTTER_SOUND] ?: defaults.camera.shutterSound,
                haptics = p[CAMERA_HAPTICS] ?: defaults.camera.haptics,
                saveToGallery = p[CAMERA_SAVE_TO_GALLERY] ?: defaults.camera.saveToGallery,
            ),
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
            textEditing = TextEditingSettings(
                repeatMs = p[TEXT_EDIT_REPEAT_MS] ?: defaults.textEditing.repeatMs,
                wrapSelectionWithPair =
                    p[WRAP_SELECTION_WITH_PAIR] ?: defaults.textEditing.wrapSelectionWithPair,
                recapitalizeSelectionWithShift = p[RECAPITALIZE_SELECTION_WITH_SHIFT]
                    ?: defaults.textEditing.recapitalizeSelectionWithShift,
            ),
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
            spellCheckerNoSuggestions = p[SPELL_CHECKER_NO_SUGGESTIONS]
                ?: defaults.spellCheckerNoSuggestions,
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
                ?: if (p[EMOJI_ROW_ABOVE_TOOLBAR] == false) {
                    // Legacy toggle explicitly off = emoji row below the toolbar.
                    // true (emoji above) and unset both fall through to the
                    // default order, which already puts the emoji row first.
                    sanitizeBarOrder(listOf(BarRow.TOPBAR, BarRow.EMOJI, BarRow.SYMBOL))
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
            // The keys stay flat across the grouping, so a user's stored
            // generator settings survive the refactor untouched.
            passwordGenerator = PasswordGeneratorSettings(
                pwLength = p[PW_LENGTH] ?: defaults.passwordGenerator.pwLength,
                pwUppercase = p[PW_UPPERCASE] ?: defaults.passwordGenerator.pwUppercase,
                pwDigits = p[PW_DIGITS] ?: defaults.passwordGenerator.pwDigits,
                pwSymbols = p[PW_SYMBOLS] ?: defaults.passwordGenerator.pwSymbols,
                pwExcludeAmbiguous = p[PW_EXCLUDE_AMBIGUOUS]
                    ?: defaults.passwordGenerator.pwExcludeAmbiguous,
                pwPassphraseMode = p[PW_PASSPHRASE_MODE]
                    ?: defaults.passwordGenerator.pwPassphraseMode,
                ppWordCount = p[PP_WORD_COUNT] ?: defaults.passwordGenerator.ppWordCount,
                ppSeparator = p[PP_SEPARATOR] ?: defaults.passwordGenerator.ppSeparator,
                ppCapitalize = p[PP_CAPITALIZE] ?: defaults.passwordGenerator.ppCapitalize,
                ppIncludeDigit = p[PP_INCLUDE_DIGIT] ?: defaults.passwordGenerator.ppIncludeDigit,
            ),
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
        editPrefs { prefs ->
            val disabled = decodeDisabledTools(prefs[DISABLED_TOOLS])
            val next = if (enabled) disabled - tool else (disabled + tool).distinct()
            prefs[DISABLED_TOOLS] = next.joinToString(",") { it.name }
        }

    /** Replaces the whole enabled set at once (the onboarding tools page). */
    suspend fun setEnabledTools(enabled: Collection<ToolbarTool>) =
        editPrefs { prefs ->
            prefs[DISABLED_TOOLS] =
                (ToolbarTool.entries - enabled.toSet()).joinToString(",") { it.name }
        }

    suspend fun setToolboxOrder(order: List<ToolbarTool>) =
        editPrefs {
            it[TOOLBOX_ORDER] = order.distinct().joinToString(",") { tool -> tool.name }
        }

    suspend fun setToolboxHintDismissed(value: Boolean) =
        editPrefs { it[TOOLBOX_HINT_DISMISSED] = value }

    private fun decodeDisabledTools(csv: String?): List<ToolbarTool> =
        csv?.split(',')?.mapNotNull { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            .orEmpty()

    /** `NAME=AARRGGBB` pairs; entries for unknown tools or bad hex are dropped. */
    private fun decodeToolColors(csv: String?): Map<ToolbarTool, Long> =
        csv?.split(',')?.mapNotNull { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) return@mapNotNull null
            val tool = runCatching { ToolbarTool.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val color = parts[1].toULongOrNull(16)?.toLong() ?: return@mapNotNull null
            tool to color
        }?.toMap().orEmpty()

    private fun encodeToolColors(map: Map<ToolbarTool, Long>): String =
        map.entries.joinToString(",") { (tool, color) -> "${tool.name}=%08X".format(color) }


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
        editPrefs { it[FLASHLIGHT_AUTO_OFF] = value }

    suspend fun setCompassShowDegrees(value: Boolean) =
        editPrefs { it[COMPASS_SHOW_DEGREES] = value }

    suspend fun setCompassShowQibla(value: Boolean) =
        editPrefs { it[COMPASS_SHOW_QIBLA] = value }

    suspend fun setKeySoundStyle(value: KeySoundStyle) =
        editPrefs { it[KEY_SOUND_STYLE] = value.name }

    suspend fun setKeySoundVolume(value: Float) =
        editPrefs { it[KEY_SOUND_VOLUME] = value.coerceIn(0.05f, 1f) }

    /**
     * Picks an installed sound and switches the style to
     * [KeySoundStyle.CUSTOM] in one write — selecting a sound without also
     * selecting the style would look like nothing happened.
     */
    suspend fun setKeySoundCustomId(value: String) =
        editPrefs {
            it[KEY_SOUND_CUSTOM_ID] = value
            if (value.isNotBlank()) it[KEY_SOUND_STYLE] = KeySoundStyle.CUSTOM.name
        }

    suspend fun setLevelShowAngles(value: Boolean) =
        editPrefs { it[LEVEL_SHOW_ANGLES] = value }

    suspend fun setRedoUsesCtrlY(value: Boolean) =
        editPrefs { it[REDO_USES_CTRL_Y] = value }

    suspend fun setMoonSouthernHemisphere(value: Boolean) =
        editPrefs { it[MOON_SOUTHERN] = value }

    suspend fun setWeatherFahrenheit(value: Boolean) =
        editPrefs { it[WEATHER_FAHRENHEIT] = value }

    /** Passing nulls clears the stored location. */
    suspend fun setWeatherLocation(latitude: Float?, longitude: Float?, place: String) =
        editPrefs { prefs ->
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

    suspend fun setCalendarAltOne(value: AltCalendar) =
        editPrefs { it[CALENDAR_ALT_ONE] = value.id }

    suspend fun setCalendarAltTwo(value: AltCalendar) =
        editPrefs { it[CALENDAR_ALT_TWO] = value.id }

    /**
     * One of the two alternate-calendar slots. Installs from before the picker
     * existed have no stored pick, so they inherit whichever of the old
     * Bengali/Hijri switches were on, in that order.
     */
    private fun calendarAltFromPrefs(
        p: Preferences,
        first: Boolean,
        defaults: KeyboardSettings,
    ): AltCalendar {
        p[if (first) CALENDAR_ALT_ONE else CALENDAR_ALT_TWO]?.let { return AltCalendar.fromId(it) }
        val bengali = p[CALENDAR_SHOW_BENGALI]
        val hijri = p[CALENDAR_SHOW_HIJRI]
        if (bengali == null && hijri == null) {
            return if (first) defaults.calendarAltOne else defaults.calendarAltTwo
        }
        val legacy = buildList {
            if (bengali != false) add(AltCalendar.BENGALI)
            if (hijri != false) add(AltCalendar.HIJRI)
        }
        return legacy.getOrElse(if (first) 0 else 1) { AltCalendar.NONE }
    }

    suspend fun setHijriAdjustDays(value: Int) =
        editPrefs { it[HIJRI_ADJUST_DAYS] = value.coerceIn(-2, 2) }

    suspend fun setHandwritingStylusOnly(value: Boolean) =
        editPrefs { it[HANDWRITING_STYLUS_ONLY] = value }

    suspend fun setHandwritingCommitDelayMs(value: Int) =
        editPrefs { it[HANDWRITING_COMMIT_DELAY] = value.coerceIn(300, 2000) }

    suspend fun setHandwritingAutoSpace(value: Boolean) =
        editPrefs { it[HANDWRITING_AUTO_SPACE] = value }

    suspend fun setVoiceStripMode(value: Boolean) =
        editPrefs { it[VOICE_STRIP_MODE] = value }

    suspend fun setVoiceContinuous(value: Boolean) =
        editPrefs { it[VOICE_CONTINUOUS] = value }

    suspend fun setVoiceSpokenPunctuation(value: Boolean) =
        editPrefs { it[VOICE_SPOKEN_PUNCTUATION] = value }

    suspend fun setVoiceEngine(value: String) =
        editPrefs { it[VOICE_ENGINE] = value }

    suspend fun setWhisperModelId(value: String) =
        editPrefs { it[WHISPER_MODEL_ID] = value }

    /**
     * Pins [languageId] to a Whisper model, or drops the entry when [modelId] is
     * blank so that language goes back to being resolved automatically.
     */
    suspend fun setWhisperModelForLanguage(languageId: String, modelId: String) =
        editPrefs { prefs ->
            val current = prefs[WHISPER_MODEL_BY_LANG]?.let { decodeWhisperModelByLang(it) }.orEmpty()
            val next =
                if (modelId.isBlank()) current - languageId else current + (languageId to modelId)
            if (next == current) return@editPrefs
            prefs[WHISPER_MODEL_BY_LANG] = encodeWhisperModelByLang(next)
        }

    /** Drops every language pinned to [modelId] — used when that model is deleted. */
    suspend fun clearWhisperModelAssignments(modelId: String) =
        editPrefs { prefs ->
            val current = prefs[WHISPER_MODEL_BY_LANG]?.let { decodeWhisperModelByLang(it) }.orEmpty()
            val next = current.filterValues { it != modelId }
            if (next == current) return@editPrefs
            prefs[WHISPER_MODEL_BY_LANG] = encodeWhisperModelByLang(next)
        }

    suspend fun setWhisperTranslate(value: Boolean) =
        editPrefs { it[WHISPER_TRANSLATE] = value }

    suspend fun setCameraPreferFront(value: Boolean) =
        editPrefs { it[CAMERA_PREFER_FRONT] = value }

    suspend fun setCameraMirrorFront(value: Boolean) =
        editPrefs { it[CAMERA_MIRROR_FRONT] = value }

    suspend fun setCameraShutterSound(value: Boolean) =
        editPrefs { it[CAMERA_SHUTTER_SOUND] = value }

    suspend fun setCameraHaptics(value: Boolean) =
        editPrefs { it[CAMERA_HAPTICS] = value }

    suspend fun setCameraSaveToGallery(value: Boolean) =
        editPrefs { it[CAMERA_SAVE_TO_GALLERY] = value }

    suspend fun setDocScanSaveToGallery(value: Boolean) =
        editPrefs { it[DOC_SCAN_SAVE_TO_GALLERY] = value }

    suspend fun setQrSaveToGallery(value: Boolean) =
        editPrefs { it[QR_SAVE_TO_GALLERY] = value }

    suspend fun setStickerSendMode(value: MediaSendMode) =
        editPrefs { it[STICKER_SEND_MODE] = value.name }

    suspend fun setGifSendMode(value: MediaSendMode) =
        editPrefs { it[GIF_SEND_MODE] = value.name }

    suspend fun setQrSendMode(value: MediaSendMode) =
        editPrefs { it[QR_SEND_MODE] = value.name }

    suspend fun setTextEditRepeatMs(value: Int) =
        editPrefs { it[TEXT_EDIT_REPEAT_MS] = value.coerceIn(30, 200) }

    suspend fun setNumpadPhoneLayout(value: Boolean) =
        editPrefs { it[NUMPAD_PHONE_LAYOUT] = value }

    suspend fun setIncognitoPausesClipboard(value: Boolean) =
        editPrefs { it[INCOGNITO_PAUSES_CLIPBOARD] = value }

    suspend fun setIncognitoPausesLearning(value: Boolean) =
        editPrefs { it[INCOGNITO_PAUSES_LEARNING] = value }

    suspend fun setAutoIncognito(value: Boolean) =
        editPrefs { it[AUTO_INCOGNITO] = value }

    suspend fun setOcrAutoSelectWords(value: Boolean) =
        editPrefs { it[OCR_AUTO_SELECT_WORDS] = value }

    suspend fun setQrScanHaptics(value: Boolean) =
        editPrefs { it[QR_SCAN_HAPTICS] = value }

    suspend fun setQrScanAutoInsert(value: Boolean) =
        editPrefs { it[QR_SCAN_AUTO_INSERT] = value }

    suspend fun setQrScanLinkPreviews(value: Boolean) =
        editPrefs { it[QR_SCAN_LINK_PREVIEWS] = value }

    suspend fun setCurrencyDecimals(value: Int) =
        editPrefs { it[CURRENCY_DECIMALS] = value.coerceIn(0, 6) }

    suspend fun setCurrencyCacheHours(value: Int) =
        editPrefs { it[CURRENCY_CACHE_HOURS] = value.coerceIn(1, 48) }

    suspend fun setGrammarDebounceMs(value: Int) =
        editPrefs { it[GRAMMAR_DEBOUNCE_MS] = value.coerceIn(100, 1500) }

    suspend fun setUnitConvertLast(value: String) =
        editPrefs { it[UNIT_CONVERT_LAST] = value }

    suspend fun setDictionaryAutoLookup(value: Boolean) =
        editPrefs { it[DICTIONARY_AUTO_LOOKUP] = value }

    suspend fun setToolboxColumns(value: Int) =
        editPrefs { it[TOOLBOX_COLUMNS] = value.coerceIn(3, 6) }

    suspend fun setEmojiRowAboveToolbar(value: Boolean) =
        editPrefs { it[EMOJI_ROW_ABOVE_TOOLBAR] = value }

    suspend fun setToolbarTools(tools: List<ToolbarTool>) =
        editPrefs {
            it[TOOLBAR_TOOLS] = tools.distinct().joinToString(",") { tool -> tool.name }
        }

    suspend fun setToolbarGreedy(value: Boolean) =
        editPrefs { it[TOOLBAR_GREEDY] = value }

    suspend fun setToolbarEnabled(value: Boolean) =
        editPrefs { it[TOOLBAR_ENABLED] = value }

    suspend fun setToolbarSwipeDownHide(value: Boolean) =
        editPrefs { it[TOOLBAR_SWIPE_DOWN_HIDE] = value }

    suspend fun setToolbarOnlyWithHardwareKeyboard(value: Boolean) =
        editPrefs { it[TOOLBAR_ONLY_HW_KEYBOARD] = value }

    suspend fun setReverseToolbarForRtl(value: Boolean) =
        editPrefs { it[REVERSE_TOOLBAR_RTL] = value }

    suspend fun setToolbarHeightDp(value: Int) =
        editPrefs { it[TOOLBAR_HEIGHT] = value.coerceIn(32, 80) }

    suspend fun setToolbarScrollable(value: Boolean) =
        editPrefs { it[TOOLBAR_SCROLLABLE] = value }

    suspend fun setToolbarHideWhenLocked(value: Boolean) =
        editPrefs { it[TOOLBAR_HIDE_WHEN_LOCKED] = value }

    suspend fun setToolbarLabels(value: Boolean) =
        editPrefs { it[TOOLBAR_LABELS] = value }

    suspend fun setToolbarLabelSize(value: Int) =
        editPrefs { it[TOOLBAR_LABEL_SIZE] = value.coerceIn(7, 14) }

    suspend fun setToolCircleRadiusDp(value: Int) =
        editPrefs { it[TOOL_CIRCLE_RADIUS] = value.coerceIn(0, 20) }

    /**
     * Moving emoji onto the comma key also pulls the emoji tool off the
     * toolbar (it would be redundant); the user can drag it back from the
     * toolbox. Turning the setting off leaves the toolbar as-is.
     */
    suspend fun setCommaAsEmoji(value: Boolean) =
        editPrefs { prefs ->
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
        editPrefs { prefs ->
            val custom = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) }.orEmpty()
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
        editPrefs { it[RAW_CLIPBOARD_SHORTCUTS] = value }

    /** Replaces the secondary-language map (primary langId → secondary langIds). */
    suspend fun setSecondaryLanguages(map: Map<String, List<String>>) =
        editPrefs { it[SECONDARY_LANGUAGES] = encodeSecondaryLanguages(map) }

    /** The layouts the 🌐 key cycles; an empty pick falls back to the default. */
    suspend fun setEnabledLayoutIds(ids: List<String>) =
        editPrefs { prefs ->
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
        editPrefs { prefs ->
            val current = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) }.orEmpty()
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
        editPrefs { prefs ->
            val current = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) }.orEmpty()
            val next = transform(resolveLayout(current, id))
            prefs[CUSTOM_LAYOUTS] =
                LayoutCodec.encodeList(current.filter { it.id != next.id } + next)
        }

    /**
     * Deletes a custom layout and drops every reference to it.
     *
     * Deleting an *edited shipped layout* only removes the override — the
     * shipped grid comes back under the same id, so every reference to it stays
     * valid, which is why the reference cleanup below is skipped for those.
     * "Shipped" covers the JSON asset layouts as well as the compiled built-ins:
     * `resolveLayouts` splices both back in, so an edited BÉPO restores exactly
     * like an edited QWERTY does, and stripping its references would switch off
     * a layout that is still there.
     */
    suspend fun deleteCustomLayout(id: String) =
        editPrefs { prefs ->
            val current = prefs[CUSTOM_LAYOUTS]?.let { LayoutCodec.decodeList(it) }.orEmpty()
            prefs[CUSTOM_LAYOUTS] = LayoutCodec.encodeList(current.filter { it.id != id })
            if (BuiltInLayouts.byId(id) != null || AssetLayouts.byId(id) != null) return@editPrefs
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
        editPrefs { it[THEME_MODE] = mode.name }

    suspend fun setDynamicColor(value: Boolean) =
        editPrefs { it[DYNAMIC_COLOR] = value }

    suspend fun setKeyboardThemeId(id: String) =
        editPrefs { it[KEYBOARD_THEME_ID] = id }

    suspend fun setAutoThemeEnabled(value: Boolean) =
        editPrefs { it[AUTO_THEME_ENABLED] = value }

    suspend fun setAutoThemeLightId(id: String) =
        editPrefs { it[AUTO_THEME_LIGHT_ID] = id }

    suspend fun setAutoThemeDarkId(id: String) =
        editPrefs { it[AUTO_THEME_DARK_ID] = id }

    /** Adds the theme or replaces the stored theme with the same id. */
    suspend fun upsertCustomTheme(theme: ThemeSpec) =
        editPrefs { prefs ->
            val current = prefs[CUSTOM_THEMES]?.let { ThemeCodec.decodeList(it) }.orEmpty()
            val next = current.filter { it.id != theme.id } + theme
            prefs[CUSTOM_THEMES] = ThemeCodec.encodeList(next)
        }

    /** Deletes a custom theme; falls back to the default theme if it was selected. */
    suspend fun deleteCustomTheme(id: String) =
        editPrefs { prefs ->
            val current = prefs[CUSTOM_THEMES]?.let { ThemeCodec.decodeList(it) }.orEmpty()
            prefs[CUSTOM_THEMES] = ThemeCodec.encodeList(current.filter { it.id != id })
            if (prefs[KEYBOARD_THEME_ID] == id) prefs[KEYBOARD_THEME_ID] = DEFAULT_THEME_ID
        }

    suspend fun setKeyHeightDp(value: Int) =
        editPrefs { it[KEY_HEIGHT] = value.coerceIn(32, 100) }

    suspend fun setNumberRowHeightDp(value: Int) =
        editPrefs { it[NUMBER_ROW_HEIGHT] = value.coerceIn(32, 100) }

    suspend fun setSplitKeyboard(value: Boolean) =
        editPrefs { it[SPLIT_KEYBOARD] = value }

    suspend fun setSplitGapPercent(value: Int) =
        editPrefs { it[SPLIT_GAP_PERCENT] = value.coerceIn(5, 40) }

    suspend fun setFloatingKeyboard(value: Boolean) =
        editPrefs { it[FLOATING_KEYBOARD] = value }

    suspend fun setFloatingWidthDp(value: Int) =
        editPrefs { it[FLOATING_WIDTH] = value.coerceIn(240, 500) }

    /** Both axes from one resize-grip gesture, persisted in a single edit. */
    suspend fun setFloatingSize(widthDp: Int, heightScale: Float) =
        editPrefs {
            it[FLOATING_WIDTH] = widthDp.coerceIn(240, 500)
            it[FLOATING_HEIGHT_SCALE] = heightScale.coerceIn(0.6f, 1.6f)
        }

    suspend fun setFloatingPosition(x: Float, y: Float) =
        editPrefs {
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

    /**
     * Whole-keyboard size multiplier for [variant] (folded vs unfolded etc.).
     * There is no base/portrait key — portrait is sized by its plain key-height
     * slider — so this only ever writes the per-variant override key.
     */
    suspend fun setVariantKeyboardScale(variant: ScreenVariant, value: Float?) {
        if (!variant.isOverride) return
        editPrefs {
            val v = value?.coerceIn(0.5f, 1.5f)
            if (v == null) it.remove(keyboardScaleKey(variant)) else it[keyboardScaleKey(variant)] = v
        }
    }

    /** Clears every override on [variant], returning it to the portrait values. */
    suspend fun clearVariantSizing(variant: ScreenVariant) {
        if (!variant.isOverride) return
        editPrefs {
            it.remove(keyHeightKey(variant))
            it.remove(numberRowHeightKey(variant))
            it.remove(bottomPaddingKey(variant))
            it.remove(widthPercentKey(variant))
            it.remove(fontScaleKey(variant))
            it.remove(alignmentKey(variant))
            it.remove(keyboardScaleKey(variant))
        }
    }

    private suspend fun <T : Any> editVariant(
        variant: ScreenVariant,
        baseKey: Preferences.Key<T>,
        overrideKey: Preferences.Key<T>,
        value: T?,
    ) = editPrefs { prefs ->
        val key = if (variant.isOverride) overrideKey else baseKey
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    suspend fun setKeyboardWidthPercent(value: Int) =
        editPrefs { it[KEYBOARD_WIDTH_PERCENT] = value.coerceIn(50, 100) }

    suspend fun setKeyboardAlignment(value: KeyboardAlignment) =
        editPrefs { it[KEYBOARD_ALIGNMENT] = value.name }

    suspend fun setBottomPaddingDp(value: Int) =
        editPrefs { it[BOTTOM_PADDING] = value.coerceIn(0, 40) }

    suspend fun setKeyCornerRadiusDp(value: Int) =
        editPrefs { it[KEY_CORNER_RADIUS] = value.coerceIn(0, 28) }

    suspend fun setKeyGapScale(value: Float) =
        editPrefs { it[KEY_GAP_SCALE] = value.coerceIn(0f, 2f) }

    suspend fun setFontScale(value: Float) =
        editPrefs { it[FONT_SCALE] = value.coerceIn(0.7f, 1.5f) }

    suspend fun setKeyFontId(value: String) =
        editPrefs { it[KEY_FONT_ID] = value }

    /** Records both the imported file's display name and selects it. */
    suspend fun setCustomFont(name: String) =
        editPrefs {
            it[CUSTOM_FONT_NAME] = name
            it[KEY_FONT_ID] = "custom"
        }

    suspend fun setBengaliFontId(value: String) =
        editPrefs { it[BENGALI_FONT_ID] = value }

    /**
     * Selects [fontId] for [script] (a [com.wasimaster.wmkeyboard.core.script.ScriptId]
     * name). "default" drops the entry so the script falls back to its automatic
     * Noto face and the map stays compact.
     */
    suspend fun setScriptFontId(script: String, fontId: String) =
        editPrefs { prefs ->
            val current = prefs[SCRIPT_FONT_IDS]?.let { decodeScriptFontIds(it) }.orEmpty()
            val next = if (fontId == "default") current - script else current + (script to fontId)
            if (next == current) return@editPrefs
            prefs[SCRIPT_FONT_IDS] = encodeScriptFontIds(next)
        }

    /** Records the imported Bengali font's display name and selects it. */
    suspend fun setCustomBengaliFont(name: String) =
        editPrefs {
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
        editPrefs { prefs -> parsed.entries.forEach { prefs.put(it) } }
        val readable = runCancellable { settings.first() }.isSuccess
        if (!readable) {
            editPrefs { prefs ->
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

    // ---- full-config bundle ----
    //
    // One file that can carry several independent parts of the app — settings,
    // custom themes, the learned dictionary, clipboard history, snippets — each
    // an opt-in section. See [ConfigBackup] for the container format.
    //
    // The file-backed stores (dictionary/clipboard/snippets) live under
    // filesDir as JSON the store itself wrote; export embeds that JSON verbatim
    // and import writes it straight back, so this repository never has to model
    // their internals. Custom themes are the one DataStore string preference
    // that gets its own section, so the settings section always excludes it.

    private val bundleJson = Json { ignoreUnknownKeys = true }

    /** Clip kinds worth exporting: the ones whose bytes/URIs survive the move. */
    private val TEXTUAL_CLIP_KINDS = setOf("TEXT", "HTML", "LINK")

    private fun storeFile(relativePath: String) = File(context.filesDir, relativePath)

    /** A store's JSON file as an element, or null when it's missing or empty. */
    private fun readStore(relativePath: String): JsonElement? {
        val file = storeFile(relativePath)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { bundleJson.parseToJsonElement(text) }.getOrNull()
    }

    /** Overwrites a store's JSON file with [element]; false on any I/O error. */
    private fun writeStore(relativePath: String, element: JsonElement): Boolean = runCatching {
        val file = storeFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(element.toString())
        true
    }.getOrDefault(false)

    /**
     * Drops image/file/folder clips from a clipboard snapshot: those point at
     * files or content URIs that only exist on the source device, so carrying
     * them to another phone would just leave broken entries. Text clips travel.
     */
    private fun portableClipboard(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val items = obj["items"] as? JsonArray ?: return element
        val kept = items.filter { item ->
            val kind = (item as? JsonObject)?.get("kind") as? JsonPrimitive
            (kind?.contentOrNull ?: "TEXT") in TEXTUAL_CLIP_KINDS
        }
        return buildJsonObject { put("items", JsonArray(kept)) }
    }

    /** Relative path of the sticker manifest, the one file that isn't binary. */
    private val stickerManifestPath =
        "${StickerPackStore.DIR_NAME}/packs.json"

    /**
     * Pack and file names a restore will accept: exactly the shape this app
     * generates. No separators, and no leading dot, so "." and ".." can't
     * match at all.
     */
    private val SAFE_STICKER_NAME = Regex("""[A-Za-z0-9_-]+(\.[A-Za-z0-9]+)?""")

    /**
     * Sticker packs as `{manifest, files}`, with every image base64'd beside
     * the manifest the way theme backgrounds travel. A manifest alone would
     * restore a list of pack names with no pictures in them.
     */
    private fun stickerSection(): JsonElement? {
        val manifest = readStore(stickerManifestPath) ?: return null
        val root = File(context.filesDir, StickerPackStore.DIR_NAME)
        val files = buildJsonObject {
            for (pack in StickerPackStore.get(context).packs()) {
                for (sticker in pack.stickers) {
                    val file = File(File(root, pack.id), sticker.fileName)
                    if (!file.isFile) continue
                    val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
                    put(
                        "${pack.id}/${sticker.fileName}",
                        JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)),
                    )
                }
            }
        }
        if (files.isEmpty()) return null
        return buildJsonObject {
            put("manifest", manifest)
            put("files", files)
        }
    }

    /** Replaces the sticker directory with the bundle's packs and images. */
    private fun restoreStickers(section: JsonObject): Boolean {
        val manifest = section["manifest"] as? JsonObject ?: return false
        val files = section["files"] as? JsonObject ?: JsonObject(emptyMap())
        val root = File(context.filesDir, StickerPackStore.DIR_NAME)
        return runCatching {
            root.deleteRecursively()
            root.mkdirs()
            val rootPath = root.canonicalPath
            for ((path, value) in files) {
                // Keys come from a file someone else wrote. Split them
                // ourselves, and accept only names that could have been
                // generated here — then confirm against the canonical path,
                // so no combination of dots or separators can land outside.
                val parts = path.split('/')
                if (parts.size != 2) continue
                val (packId, name) = parts
                if (!SAFE_STICKER_NAME.matches(packId) || !SAFE_STICKER_NAME.matches(name)) continue
                val packDir = File(root, packId)
                val target = File(packDir, name)
                if (packDir.canonicalPath != "$rootPath${File.separator}$packId") continue
                if (target.canonicalPath != "${packDir.canonicalPath}${File.separator}$name") continue
                val bytes = (value as? JsonPrimitive)?.contentOrNull
                    ?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
                    ?: continue
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            File(root, "packs.json").writeText(manifest.toString())
            // Whatever the bundle claimed but couldn't deliver is dropped here.
            StickerPackStore.get(context).reload()
            true
        }.getOrDefault(false)
    }

    /** Relative path of the icon-pack manifest, the one file that isn't binary. */
    private val iconManifestPath = "${IconPackStore.DIR_NAME}/packs.json"

    /** Same shape as [SAFE_STICKER_NAME]: what a restore will accept for a pack id or file name. */
    private val SAFE_ICON_NAME = Regex("""[A-Za-z0-9_-]+(\.[A-Za-z0-9]+)?""")

    /**
     * Icon packs as `{manifest, files}`, with every SVG base64'd beside the
     * manifest the way sticker images travel. A manifest alone would restore a
     * list of pack names with no icons in them.
     */
    private fun iconSection(): JsonElement? {
        val manifest = readStore(iconManifestPath) ?: return null
        val store = IconPackStore.get(context)
        val root = File(context.filesDir, IconPackStore.DIR_NAME)
        val files = buildJsonObject {
            for (pack in store.packs()) {
                for (slot in pack.slots) {
                    val file = store.fileFor(pack.id, slot) ?: continue
                    if (!file.isFile) continue
                    val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
                    put(
                        "${pack.id}/${IconPackStore.fileNameFor(slot)}",
                        JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)),
                    )
                }
            }
        }
        if (files.isEmpty()) return null
        return buildJsonObject {
            put("manifest", manifest)
            put("files", files)
        }
    }

    /** Replaces the icon-pack directory with the bundle's packs and SVGs. */
    private fun restoreIcons(section: JsonObject): Boolean {
        val manifest = section["manifest"] as? JsonObject ?: return false
        val files = section["files"] as? JsonObject ?: JsonObject(emptyMap())
        val root = File(context.filesDir, IconPackStore.DIR_NAME)
        return runCatching {
            root.deleteRecursively()
            root.mkdirs()
            val rootPath = root.canonicalPath
            for ((path, value) in files) {
                // Keys come from a file someone else wrote; only accept names
                // this app could have generated, then confirm against the
                // canonical path, so no combination of dots or separators can
                // land outside the icon-pack directory.
                val parts = path.split('/')
                if (parts.size != 2) continue
                val (packId, name) = parts
                if (!SAFE_ICON_NAME.matches(packId) || !SAFE_ICON_NAME.matches(name)) continue
                val packDir = File(root, packId)
                val target = File(packDir, name)
                if (packDir.canonicalPath != "$rootPath${File.separator}$packId") continue
                if (target.canonicalPath != "${packDir.canonicalPath}${File.separator}$name") continue
                val bytes = (value as? JsonPrimitive)?.contentOrNull
                    ?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
                    ?: continue
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            File(root, "packs.json").writeText(manifest.toString())
            // Whatever the bundle claimed but couldn't deliver is dropped here.
            IconPackStore.get(context).reload()
            true
        }.getOrDefault(false)
    }

    /** A path segment safe to use as-is: no separator, no `.`/`..` climb. */
    private fun isSafeSegment(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name

    /**
     * Custom word lists as a flat `{ "langId/fileName": base64 }` map — there
     * is no manifest to carry separately, the files on disk under
     * [CustomDictionaries.root] are the whole of the state.
     */
    private fun wordlistsSection(): JsonElement? {
        val root = CustomDictionaries.root(context.filesDir)
        val files = buildJsonObject {
            for (langDir in root.listFiles().orEmpty()) {
                if (!langDir.isDirectory) continue
                for (file in CustomDictionaries.lists(context.filesDir, langDir.name)) {
                    val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
                    put("${langDir.name}/${file.name}", JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                }
            }
        }
        if (files.isEmpty()) return null
        return files
    }

    /** Replaces the custom-word-list directory with the bundle's lists. */
    private fun restoreWordlists(section: JsonObject): Boolean {
        val root = CustomDictionaries.root(context.filesDir)
        return runCatching {
            root.deleteRecursively()
            root.mkdirs()
            val rootPath = root.canonicalPath
            for ((path, value) in section) {
                // Same defence as stickers/icons: only names this app could
                // have generated, confirmed against the canonical path so no
                // combination of dots or separators lands outside the folder.
                val parts = path.split('/')
                if (parts.size != 2) continue
                val (langId, name) = parts
                if (!isSafeSegment(langId) || !isSafeSegment(name) || !name.endsWith(".txt")) continue
                val langDir = File(root, langId)
                val target = File(langDir, name)
                if (langDir.canonicalPath != "$rootPath${File.separator}$langId") continue
                if (target.canonicalPath != "${langDir.canonicalPath}${File.separator}$name") continue
                val bytes = (value as? JsonPrimitive)?.contentOrNull
                    ?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
                    ?: continue
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Restores the addon repository list, merging rather than replacing.
     *
     * Merging because the two sides are both just bookmarks: a repository the
     * user added on this device is no less wanted for not being in the backup,
     * and a duplicate is decided by manifest URL. Cached manifests are dropped
     * — they re-fetch on the next visit, and a stale one from another device
     * would show addons at versions this device never saw.
     */
    private fun restoreAddonRepos(section: JsonObject): Boolean = runCatching {
        val incoming = section["repos"]?.jsonArray ?: return false
        val file = File(context.filesDir, "addons/repos.json")
        val existing = runCatching {
            bundleJson.parseToJsonElement(file.readText()).jsonObject["repos"]?.jsonArray
        }.getOrNull().orEmpty()

        val merged = LinkedHashMap<String, JsonElement>()
        for (element in existing + incoming) {
            val obj = element as? JsonObject ?: continue
            val url = (obj["manifestUrl"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.startsWith("https://") } ?: continue
            merged.putIfAbsent(
                url,
                JsonObject(obj - "cachedManifest" - "fetchedAt"),
            )
        }
        if (merged.isEmpty()) return false

        file.parentFile?.mkdirs()
        file.writeText(
            bundleJson.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("version", JsonPrimitive(1))
                    put("repos", JsonArray(merged.values.toList()))
                },
            ),
        )
        AddonStore.attach(context)
        true
    }.getOrDefault(false)

    /**
     * Builds a full-config bundle from the chosen [sections]. A section whose
     * store is empty or absent is simply left out of the file.
     */
    suspend fun exportConfig(
        sections: Set<ConfigBackup.Section>,
        includeSecrets: Boolean,
        appVersion: Int,
        appVersionName: String,
    ): String {
        val prefs = context.dataStore.data.first()
        val out = LinkedHashMap<ConfigBackup.Section, JsonElement>()
        if (ConfigBackup.Section.SETTINGS in sections) {
            out[ConfigBackup.Section.SETTINGS] =
                SettingsBackup.encodeSettings(prefs, includeSecrets, exclude = SettingsBackup.THEME_KEYS)
        }
        if (ConfigBackup.Section.THEMES in sections) {
            prefs[CUSTOM_THEMES]?.takeIf { it.isNotBlank() }?.let { raw ->
                // Embed each theme's background image as base64 so it travels with
                // the bundle instead of a device-local path that won't resolve on
                // another phone.
                val themes = ThemeCodec.decodeList(raw).map { it.withEmbeddedImages() }
                if (themes.isNotEmpty()) {
                    runCatching { bundleJson.parseToJsonElement(ThemeCodec.encodeList(themes)) }
                        .getOrNull()
                        ?.let { out[ConfigBackup.Section.THEMES] = it }
                }
            }
        }
        if (ConfigBackup.Section.DICTIONARY in sections) {
            readStore("learning/user_lexicon.json")?.let { out[ConfigBackup.Section.DICTIONARY] = it }
        }
        if (ConfigBackup.Section.CLIPBOARD in sections) {
            readStore("clipboard/history.json")?.let { out[ConfigBackup.Section.CLIPBOARD] = portableClipboard(it) }
        }
        if (ConfigBackup.Section.SNIPPETS in sections) {
            readStore("snippets/snippets.json")?.let { out[ConfigBackup.Section.SNIPPETS] = it }
        }
        if (ConfigBackup.Section.STICKERS in sections) {
            stickerSection()?.let { out[ConfigBackup.Section.STICKERS] = it }
        }
        if (ConfigBackup.Section.ICONS in sections) {
            iconSection()?.let { out[ConfigBackup.Section.ICONS] = it }
        }
        if (ConfigBackup.Section.WORDLISTS in sections) {
            wordlistsSection()?.let { out[ConfigBackup.Section.WORDLISTS] = it }
        }
        if (ConfigBackup.Section.ADDONS in sections) {
            // The repository list only. Cached manifests are re-fetched, and
            // the installed-addon records point at local ids that mean nothing
            // on another device.
            readStore("addons/repos.json")?.let { out[ConfigBackup.Section.ADDONS] = it }
        }
        return ConfigBackup.encode(appVersion, appVersionName, out)
    }

    /** How many items each section of a decoded bundle holds, for the dialog. */
    fun describeConfig(parsed: ConfigBackup.Parsed): Map<ConfigBackup.Section, Int> {
        val counts = LinkedHashMap<ConfigBackup.Section, Int>()
        for ((section, element) in parsed.sections) {
            val count = runCatching {
                when (section) {
                    ConfigBackup.Section.SETTINGS -> element.jsonObject.size
                    ConfigBackup.Section.THEMES -> element.jsonArray.size
                    ConfigBackup.Section.DICTIONARY -> element.jsonObject["words"]?.jsonObject?.size ?: 0
                    ConfigBackup.Section.CLIPBOARD -> element.jsonObject["items"]?.jsonArray?.size ?: 0
                    ConfigBackup.Section.SNIPPETS -> element.jsonObject["snippets"]?.jsonArray?.size ?: 0
                    ConfigBackup.Section.STICKERS -> element.jsonObject["files"]?.jsonObject?.size ?: 0
                    ConfigBackup.Section.ICONS -> element.jsonObject["files"]?.jsonObject?.size ?: 0
                    ConfigBackup.Section.WORDLISTS -> element.jsonObject.size
                    ConfigBackup.Section.ADDONS -> element.jsonObject["repos"]?.jsonArray?.size ?: 0
                }
            }.getOrDefault(0)
            counts[section] = count
        }
        return counts
    }

    /** True when a decoded bundle's settings section carries any API key. */
    fun configContainsSecrets(parsed: ConfigBackup.Parsed): Boolean {
        val settings = parsed.sections[ConfigBackup.Section.SETTINGS]?.let { it as? JsonObject } ?: return false
        return settings.keys.any { it in SettingsBackup.SECRET_KEYS }
    }

    sealed interface ConfigImportResult {
        /**
         * [restored] lists the sections written. [settingsFailed] is true when
         * the settings section parsed but left the app unable to read its own
         * settings, so it was rolled back while the other sections still applied.
         */
        data class Applied(
            val restored: List<ConfigBackup.Section>,
            val settingsFailed: Boolean,
        ) : ConfigImportResult
        data object NotABackup : ConfigImportResult
    }

    /**
     * Restores every section present in a full-config bundle. The settings
     * section is verified and rolled back on its own the same way
     * [importSettings] is; the file-backed sections overwrite their store file.
     */
    suspend fun importConfig(text: String): ConfigImportResult {
        val parsed = ConfigBackup.decode(text) ?: return ConfigImportResult.NotABackup
        val restored = ArrayList<ConfigBackup.Section>()
        var settingsFailed = false

        (parsed.sections[ConfigBackup.Section.SETTINGS] as? JsonObject)?.let { obj ->
            val (entries, _) = SettingsBackup.decodeSettings(obj)
            val snapshot = context.dataStore.data.first()
            editPrefs { prefs -> entries.forEach { prefs.put(it) } }
            if (runCancellable { settings.first() }.isSuccess) {
                restored.add(ConfigBackup.Section.SETTINGS)
            } else {
                editPrefs { prefs ->
                    prefs.clear()
                    for ((key, value) in snapshot.asMap()) {
                        @Suppress("UNCHECKED_CAST")
                        prefs[key as Preferences.Key<Any>] = value
                    }
                }
                settingsFailed = true
            }
        }

        (parsed.sections[ConfigBackup.Section.THEMES] as? JsonArray)?.let { array ->
            val themes = runCatching { ThemeCodec.decodeList(array.toString()) }.getOrNull()
            if (themes != null) {
                // Rebuild any embedded background images onto local storage and
                // strip the base64 before persisting the themes.
                val dir = File(context.filesDir, "theme_images")
                val extracted = themes.map { it.withExtractedImages(dir) }
                editPrefs { it[CUSTOM_THEMES] = ThemeCodec.encodeList(extracted) }
                restored.add(ConfigBackup.Section.THEMES)
            }
        }

        (parsed.sections[ConfigBackup.Section.DICTIONARY] as? JsonObject)?.let { obj ->
            if (writeStore("learning/user_lexicon.json", obj)) {
                restored.add(ConfigBackup.Section.DICTIONARY)
                bumpLexiconVersion()
            }
        }
        (parsed.sections[ConfigBackup.Section.CLIPBOARD] as? JsonObject)?.let { obj ->
            if (writeStore("clipboard/history.json", obj)) restored.add(ConfigBackup.Section.CLIPBOARD)
        }
        (parsed.sections[ConfigBackup.Section.SNIPPETS] as? JsonObject)?.let { obj ->
            if (writeStore("snippets/snippets.json", obj)) restored.add(ConfigBackup.Section.SNIPPETS)
        }
        (parsed.sections[ConfigBackup.Section.STICKERS] as? JsonObject)?.let { obj ->
            if (restoreStickers(obj)) restored.add(ConfigBackup.Section.STICKERS)
        }
        (parsed.sections[ConfigBackup.Section.ICONS] as? JsonObject)?.let { obj ->
            if (restoreIcons(obj)) restored.add(ConfigBackup.Section.ICONS)
        }
        (parsed.sections[ConfigBackup.Section.WORDLISTS] as? JsonObject)?.let { obj ->
            if (restoreWordlists(obj)) {
                restored.add(ConfigBackup.Section.WORDLISTS)
                bumpCustomDictVersion()
            }
        }
        (parsed.sections[ConfigBackup.Section.ADDONS] as? JsonObject)?.let { obj ->
            if (restoreAddonRepos(obj)) restored.add(ConfigBackup.Section.ADDONS)
        }

        return ConfigImportResult.Applied(restored, settingsFailed)
    }

    suspend fun bumpLexiconVersion() =
        editPrefs { it[LEXICON_VERSION] = (it[LEXICON_VERSION] ?: 0) + 1 }

    suspend fun bumpCustomDictVersion() =
        editPrefs { it[CUSTOM_DICT_VERSION] = (it[CUSTOM_DICT_VERSION] ?: 0) + 1 }

    suspend fun setEmojiFont(value: EmojiFontChoice) =
        editPrefs { it[EMOJI_FONT] = value.name }

    /**
     * Picks an emoji face from the font library and switches to it in one
     * write, the same pairing as [setKeySoundCustomId].
     */
    suspend fun setInstalledEmojiFont(fontId: String) =
        editPrefs {
            it[EMOJI_FONT_INSTALLED_ID] = fontId
            if (fontId.isNotBlank()) it[EMOJI_FONT] = EmojiFontChoice.INSTALLED.name
        }

    /**
     * Drops [fontId] as the emoji face if it is the one selected, falling back
     * to the system emoji font. Called when the font is deleted.
     */
    suspend fun forgetInstalledEmojiFont(fontId: String) =
        editPrefs {
            if (it[EMOJI_FONT_INSTALLED_ID] != fontId) return@editPrefs
            it[EMOJI_FONT_INSTALLED_ID] = ""
            if (it[EMOJI_FONT] == EmojiFontChoice.INSTALLED.name) {
                it[EMOJI_FONT] = EmojiFontChoice.SYSTEM.name
            }
        }

    /**
     * Drops a deleted key sound, falling the style back to Click. Left pointing
     * at a missing file the keyboard would still make a sound — the player falls
     * back to the system click — but the settings screen would show Custom
     * selected with nothing under it.
     */
    suspend fun forgetKeySound(soundId: String) =
        editPrefs {
            if (it[KEY_SOUND_CUSTOM_ID] != soundId) return@editPrefs
            it[KEY_SOUND_CUSTOM_ID] = ""
            if (it[KEY_SOUND_STYLE] == KeySoundStyle.CUSTOM.name) {
                it[KEY_SOUND_STYLE] = KeySoundStyle.CLICK.name
            }
        }

    suspend fun setAutoApostrophe(value: Boolean) =
        editPrefs { it[AUTO_APOSTROPHE] = value }

    suspend fun setHapticFeedback(value: Boolean) =
        editPrefs { it[HAPTIC] = value }

    suspend fun setHapticStrengthMs(value: Int) =
        editPrefs { it[HAPTIC_STRENGTH] = value.coerceIn(5, 60) }

    suspend fun setHapticAmplitude(value: Int) =
        editPrefs { it[HAPTIC_AMPLITUDE] = value.coerceIn(1, 255) }

    suspend fun setHapticStyle(value: HapticStyle) =
        editPrefs { it[HAPTIC_STYLE] = value.name }

    suspend fun setHapticOnLongPress(value: Boolean) =
        editPrefs { it[HAPTIC_ON_LONG_PRESS] = value }

    suspend fun setHapticOnLongPressRelease(value: Boolean) =
        editPrefs { it[HAPTIC_ON_LONG_PRESS_RELEASE] = value }

    suspend fun setVibrateOnSpace(value: Boolean) =
        editPrefs { it[FEEDBACK_VIBRATE_SPACE] = value }

    suspend fun setVibrateOnDeleteSwipe(value: Boolean) =
        editPrefs { it[FEEDBACK_VIBRATE_DELETE_SWIPE] = value }

    suspend fun setVibrateOnRepeat(value: Boolean) =
        editPrefs { it[FEEDBACK_VIBRATE_REPEAT] = value }

    suspend fun setToastOnCopy(value: Boolean) =
        editPrefs { it[FEEDBACK_TOAST_ON_COPY] = value }

    suspend fun setHapticsRespectDnd(value: Boolean) =
        editPrefs { it[FEEDBACK_HAPTICS_RESPECT_DND] = value }

    suspend fun setKeySound(value: Boolean) =
        editPrefs { it[KEY_SOUND] = value }

    suspend fun setKeyPopup(value: Boolean) =
        editPrefs { it[KEY_POPUP] = value }

    suspend fun setKeyPopupMinDurationMs(value: Int) =
        editPrefs { it[KEY_POPUP_MIN_DURATION] = value.coerceIn(0, 300) }

    suspend fun setKeyPopupMaxDurationMs(value: Int) =
        editPrefs { it[KEY_POPUP_MAX_DURATION] = value.coerceIn(400, 2000) }

    suspend fun setKeyPopupOnKey(value: Boolean) =
        editPrefs { it[KEY_POPUP_ON_KEY] = value }

    suspend fun setKeyPopupInNumericFields(value: Boolean) =
        editPrefs { it[KEY_POPUP_IN_NUMERIC] = value }

    suspend fun setPopupFontScale(value: Float) =
        editPrefs { it[POPUP_FONT_SCALE] = value.coerceIn(0.7f, 1.6f) }

    suspend fun setKeyPopupHeightDp(value: Int) =
        editPrefs { it[KEY_POPUP_HEIGHT] = value.coerceIn(32, 160) }

    // ---- accessibility ----

    suspend fun setColorVisionFilter(value: ColorVisionFilter) =
        editPrefs { it[COLOR_VISION_FILTER] = value.name }

    suspend fun setHighContrastKeys(value: Boolean) =
        editPrefs { it[HIGH_CONTRAST_KEYS] = value }

    suspend fun setKeyOutlines(value: Boolean) =
        editPrefs { it[KEY_OUTLINES] = value }

    suspend fun setBoldKeyLabels(value: Boolean) =
        editPrefs { it[BOLD_KEY_LABELS] = value }

    suspend fun setReduceMotion(value: Boolean) =
        editPrefs { it[REDUCE_MOTION] = value }

    suspend fun setScreenReaderMode(value: ScreenReaderMode) =
        editPrefs { it[SCREEN_READER_MODE] = value.name }

    suspend fun setKeyDebounceMs(value: Int) =
        editPrefs { it[KEY_DEBOUNCE_MS] = value.coerceIn(0, 500) }

    suspend fun setNumberRow(value: Boolean) =
        editPrefs { it[NUMBER_ROW] = value }

    suspend fun setAutocorrect(value: Boolean) =
        editPrefs { it[AUTOCORRECT] = value }

    /** Bounds mirror `SuggestionEngine.MIN/MAX_AUTOCORRECT_CONFIDENCE`. */
    suspend fun setAutocorrectConfidence(value: Float) =
        editPrefs { it[AUTOCORRECT_CONFIDENCE] = value.coerceIn(1.5f, 10f) }

    suspend fun setRevertAutocorrectOnBackspace(value: Boolean) =
        editPrefs { it[REVERT_AUTOCORRECT_ON_BACKSPACE] = value }

    suspend fun setAutocorrectSkipAllCaps(value: Boolean) =
        editPrefs { it[AUTOCORRECT_SKIP_ALL_CAPS] = value }

    suspend fun setAutoCapitalize(value: Boolean) =
        editPrefs { it[AUTO_CAPITALIZE] = value }

    suspend fun setDoubleSpacePeriod(value: Boolean) =
        editPrefs { it[DOUBLE_SPACE_PERIOD] = value }

    suspend fun setDoubleSpaceTab(value: Boolean) =
        editPrefs { it[DOUBLE_SPACE_TAB] = value }

    suspend fun setWrapSelectionWithPair(value: Boolean) =
        editPrefs { it[WRAP_SELECTION_WITH_PAIR] = value }

    suspend fun setRecapitalizeSelectionWithShift(value: Boolean) =
        editPrefs { it[RECAPITALIZE_SELECTION_WITH_SHIFT] = value }

    suspend fun setSuggestions(value: Boolean) =
        editPrefs { it[SUGGESTIONS] = value }

    suspend fun setShowSuggestionsInAllFields(value: Boolean) =
        editPrefs { it[SHOW_SUGGESTIONS_ALL_FIELDS] = value }

    suspend fun setSuggestionsFirst(value: Boolean) =
        editPrefs { it[SUGGESTIONS_FIRST] = value }

    suspend fun setSuggestionPrimaryCenter(value: Boolean) =
        editPrefs { it[SUGGESTION_PRIMARY_CENTER] = value }

    suspend fun setBlockOffensiveWords(value: Boolean) =
        editPrefs { it[BLOCK_OFFENSIVE_WORDS] = value }

    suspend fun setContactSuggestions(value: Boolean) =
        editPrefs { it[CONTACT_SUGGESTIONS] = value }

    suspend fun setContactEmailSuggestions(value: Boolean) =
        editPrefs { it[CONTACT_EMAIL_SUGGESTIONS] = value }

    suspend fun setContactEmailSuggestionsInEmailFields(value: Boolean) =
        editPrefs { it[CONTACT_EMAIL_SUGGESTIONS_IN_EMAIL_FIELDS] = value }

    suspend fun setAppNameSuggestions(value: Boolean) =
        editPrefs { it[APP_NAME_SUGGESTIONS] = value }

    /** Adds a word to the never-suggest blacklist (lowercased, trimmed). */
    suspend fun addSuggestionBlacklistWord(word: String) {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return
        editPrefs {
            it[SUGGESTION_BLACKLIST] = (it[SUGGESTION_BLACKLIST].orEmpty() + normalized)
        }
    }

    /** Removes a word from the never-suggest blacklist. */
    suspend fun removeSuggestionBlacklistWord(word: String) {
        val normalized = word.trim().lowercase()
        editPrefs {
            it[SUGGESTION_BLACKLIST] = (it[SUGGESTION_BLACKLIST].orEmpty() - normalized)
        }
    }

    suspend fun setInlineEmojiSearch(value: Boolean) =
        editPrefs { it[INLINE_EMOJI_SEARCH] = value }

    suspend fun setInlineAutofill(value: Boolean) =
        editPrefs { it[INLINE_AUTOFILL] = value }

    suspend fun setGestureTyping(value: Boolean) =
        editPrefs { it[GESTURE_TYPING] = value }

    suspend fun setLetterSwipeAction(value: LetterSwipeAction) =
        editPrefs { it[LETTER_SWIPE_ACTION] = value.name }

    suspend fun setGestureSpaceMultiWord(value: Boolean) =
        editPrefs { it[GESTURE_SPACE_MULTI_WORD] = value }

    suspend fun setGestureStartThresholdSlop(value: Float) =
        editPrefs { it[GESTURE_START_THRESHOLD_SLOP] = value.coerceIn(0.5f, 4f) }

    suspend fun setGesturePostTypeCooldownMs(value: Int) =
        editPrefs { it[GESTURE_POST_TYPE_COOLDOWN_MS] = value.coerceIn(0, 500) }

    suspend fun setGestureHandwriteDotCooldownMs(value: Int) =
        editPrefs { it[GESTURE_HANDWRITE_DOT_COOLDOWN_MS] = value.coerceIn(0, 1500) }

    suspend fun setGestureTrailWidthDp(value: Float) =
        editPrefs { it[GESTURE_TRAIL_WIDTH_DP] = value.coerceIn(2f, 24f) }

    suspend fun setGestureTrailDurationMs(value: Int) =
        editPrefs { it[GESTURE_TRAIL_DURATION_MS] = value.coerceIn(100, 1200) }

    suspend fun setGestureTrailOpacity(value: Float) =
        editPrefs { it[GESTURE_TRAIL_OPACITY] = value.coerceIn(0.1f, 1f) }

    suspend fun setSpaceShortSwipe(value: SpaceSwipeAction) =
        editPrefs { it[SPACE_SHORT_SWIPE] = value.name }

    suspend fun setSpaceLongSwipe(value: SpaceSwipeAction) =
        editPrefs { it[SPACE_LONG_SWIPE] = value.name }

    suspend fun setSpacebarLanguageArrows(value: Boolean) =
        editPrefs { it[SPACEBAR_LANGUAGE_ARROWS] = value }

    suspend fun setSpacebarLabel(value: String) =
        editPrefs { it[SPACEBAR_LABEL] = value.trim() }

    suspend fun setSymbolsLongPressNumpad(value: Boolean) =
        editPrefs { it[SYMBOLS_LONGPRESS_NUMPAD] = value }

    suspend fun setSpaceSwipeDownHide(value: Boolean) =
        editPrefs { it[SPACE_SWIPE_DOWN_HIDE] = value }

    suspend fun setSpaceCursor2d(value: Boolean) =
        editPrefs { it[SPACE_CURSOR_2D] = value }

    suspend fun setHintFontScale(value: Float) =
        editPrefs { it[HINT_FONT_SCALE] = value.coerceIn(0.5f, 2.0f) }

    suspend fun setNumberRowShiftSymbols(value: Boolean) =
        editPrefs { it[NUMBER_ROW_SHIFT_SYMBOLS] = value }

    suspend fun setSmartHitDetection(value: Boolean) =
        editPrefs { it[SMART_HIT_DETECTION] = value }

    /**
     * Picks [value] as [langId]'s numeral system. [NumeralSystem.AUTO] drops the
     * entry, so the language falls back to its own default and the map stays
     * compact.
     */
    suspend fun setNumeralSystemForLanguage(langId: String, value: NumeralSystem) =
        editPrefs { prefs ->
            val current = prefs[NUMERAL_SYSTEM_BY_LANG]?.let { decodeNumeralSystems(it) }.orEmpty()
            val next =
                if (value == NumeralSystem.AUTO) current - langId else current + (langId to value)
            if (next == current) return@editPrefs
            prefs[NUMERAL_SYSTEM_BY_LANG] = encodeNumeralSystems(next)
        }

    suspend fun setSpacebarDisplay(value: SpacebarDisplay) =
        editPrefs { it[SPACEBAR_DISPLAY] = value.name }

    suspend fun setNumeralCommitScope(value: NumeralCommitScope) =
        editPrefs { it[NUMERAL_COMMIT_SCOPE] = value.name }

    suspend fun setBackspaceSwipeDelete(value: Boolean) =
        editPrefs { it[BACKSPACE_SWIPE_DELETE] = value }

    suspend fun setHardwareKeyboardInput(value: Boolean) =
        editPrefs { it[HARDWARE_KEYBOARD_INPUT] = value }

    suspend fun setHwShortcutsEnabled(value: Boolean) =
        editPrefs { it[HW_SHORTCUTS_ENABLED] = value }

    suspend fun setHwPanelNavigation(value: Boolean) =
        editPrefs { it[HW_PANEL_NAVIGATION] = value }

    suspend fun setHwEscClosesPanel(value: Boolean) =
        editPrefs { it[HW_ESC_CLOSES_PANEL] = value }

    suspend fun setHwSuggestionHotkeys(value: SuggestionHotkeyMode) =
        editPrefs { it[HW_SUGGESTION_HOTKEYS] = value.name }

    suspend fun setHwAutoShowUi(value: Boolean) =
        editPrefs { it[HW_AUTO_SHOW_UI] = value }

    /** Stores the leader in its canonical text form; junk is refused, not persisted. */
    suspend fun setHwLeader(value: String) {
        val canonical = parseLeader(value)?.let(::formatLeader) ?: return
        editPrefs { it[HW_LEADER] = canonical }
    }

    /** The whole table at once — the shortcut editor's save and its reset button. */
    suspend fun setHwToolLetters(map: Map<Char, ToolbarTool>) =
        editPrefs { it[HW_TOOL_LETTERS] = encodeToolLetters(map) }

    /**
     * Binds one letter, or unbinds it when [tool] is null. Read-modify-write in a
     * single edit so two rows saved at once cannot lose each other, and it keeps
     * the table unambiguous in both directions: one letter opens one tool, and
     * one tool answers to one letter.
     */
    suspend fun setHwToolLetter(letter: Char, tool: ToolbarTool?) =
        editPrefs { prefs ->
            val current = prefs[HW_TOOL_LETTERS]?.let(::decodeToolLetters) ?: DefaultToolLetters
            val next = current.toMutableMap()
            next.remove(letter.uppercaseChar())
            if (tool != null) {
                next.entries.removeAll { it.value == tool }
                next[letter.uppercaseChar()] = tool
            }
            prefs[HW_TOOL_LETTERS] = encodeToolLetters(next)
        }

    suspend fun setVolumeCursor(value: Boolean) =
        editPrefs { it[VOLUME_CURSOR] = value }

    suspend fun setVolumeCursorMediaAware(value: Boolean) =
        editPrefs { it[VOLUME_CURSOR_MEDIA_AWARE] = value }

    suspend fun setGlobeAsEmoji(value: Boolean) =
        editPrefs { it[GLOBE_AS_EMOJI] = value }

    suspend fun setOsLanguageSwitcher(value: Boolean) =
        editPrefs { it[OS_LANGUAGE_SWITCHER] = value }

    suspend fun setSubtypeAppNameFirst(value: Boolean) =
        editPrefs { it[SUBTYPE_APP_NAME_FIRST] = value }

    suspend fun setRememberLayoutPerApp(value: Boolean) =
        editPrefs { it[PER_APP_LANGUAGE_ENABLED] = value }

    /** Records [layoutId] as the last explicitly-picked layout for [packageName]. */
    suspend fun setAppLayout(packageName: String, layoutId: String) =
        editPrefs { prefs ->
            val current = prefs[PER_APP_LAYOUT_MAP]?.let { decodePerAppLayouts(it) }.orEmpty()
            if (current[packageName] == layoutId) return@editPrefs
            prefs[PER_APP_LAYOUT_MAP] = encodePerAppLayouts(current + (packageName to layoutId))
        }

    suspend fun setOnboardingDone(value: Boolean) =
        editPrefs { it[ONBOARDING_DONE] = value }

    suspend fun setConjunctBackspace(value: Boolean) =
        editPrefs { it[CONJUNCT_BACKSPACE] = value }

    suspend fun setPinyinFuzzy(value: Boolean) =
        context.dataStore.edit { it[PINYIN_FUZZY] = value }

    suspend fun setPinyinDoublePinyin(value: DoublePinyinScheme) =
        context.dataStore.edit { it[PINYIN_DOUBLE_PINYIN] = value.name }

    suspend fun setCjkTraditionalOutput(value: Boolean) =
        context.dataStore.edit { it[CJK_TRADITIONAL_OUTPUT] = value }

    suspend fun setJyutpingLazy(value: Boolean) =
        context.dataStore.edit { it[JYUTPING_LAZY] = value }

    suspend fun setCjkHanRegion(value: HanVariant.HanRegion) =
        context.dataStore.edit { it[CJK_HAN_REGION] = value.name }

    suspend fun setOneHandedMode(value: OneHandedMode) =
        editPrefs { it[ONE_HANDED_MODE] = value.name }

    suspend fun setOneHandedWidthPercent(landscape: Boolean, value: Int) =
        editPrefs {
            it[oneHandedWidthKey(landscape)] =
                value.coerceIn(ONE_HANDED_WIDTH_MIN, ONE_HANDED_WIDTH_MAX)
        }

    suspend fun setOneHandedHeightScale(landscape: Boolean, value: Int) =
        editPrefs {
            it[oneHandedHeightScaleKey(landscape)] =
                value.coerceIn(ONE_HANDED_HEIGHT_SCALE_MIN, ONE_HANDED_HEIGHT_SCALE_MAX)
        }

    suspend fun setOneHandedSide(landscape: Boolean, value: OneHandedSide) =
        editPrefs { it[oneHandedSideKey(landscape)] = value.name }

    /**
     * Reads one orientation's one-handed profile. A missing dock side falls
     * back to the legacy global [ONE_HANDED_MODE] so users who had picked
     * LEFT/RIGHT before this feature keep that side as their default.
     */
    private fun readOneHandedProfile(
        p: Preferences,
        landscape: Boolean,
        default: OneHandedProfile,
    ): OneHandedProfile {
        val legacySide = p[ONE_HANDED_MODE]
            ?.let { runCatching { OneHandedMode.valueOf(it) }.getOrNull() }
            ?.let { OneHandedSide.of(it) }
        val side = p[oneHandedSideKey(landscape)]
            ?.let { runCatching { OneHandedSide.valueOf(it) }.getOrNull() }
            ?: legacySide ?: default.side
        return OneHandedProfile(
            widthPercent = p[oneHandedWidthKey(landscape)] ?: default.widthPercent,
            heightScale = p[oneHandedHeightScaleKey(landscape)] ?: default.heightScale,
            side = side,
        )
    }

    suspend fun setLearnFromTyping(value: Boolean) =
        editPrefs { it[LEARN_FROM_TYPING] = value }

    suspend fun setAddWordsToSystemDictionary(value: Boolean) =
        editPrefs { it[ADD_WORDS_TO_SYSTEM_DICTIONARY] = value }

    suspend fun setClipboardHistory(value: Boolean) =
        editPrefs { it[CLIPBOARD_HISTORY] = value }

    suspend fun setClipboardExpiryHours(value: Int) =
        editPrefs { it[CLIPBOARD_EXPIRY_HOURS] = value.coerceIn(0, 24 * 7) }

    suspend fun setClipboardLinkPreviews(value: Boolean) =
        editPrefs { it[CLIPBOARD_LINK_PREVIEWS] = value }

    suspend fun setClipboardTrackSource(value: Boolean) =
        editPrefs { it[CLIPBOARD_TRACK_SOURCE] = value }

    suspend fun setClipboardSuggestRecent(value: Boolean) =
        editPrefs { it[CLIPBOARD_SUGGEST_RECENT] = value }

    suspend fun setPunctuationSuggestions(value: Boolean) =
        editPrefs { it[PUNCTUATION_SUGGESTIONS] = value }

    suspend fun setClipboardBottomRow(value: Boolean) =
        editPrefs { it[CLIPBOARD_BOTTOM_ROW] = value }

    suspend fun setClipboardPinnedLast(value: Boolean) =
        editPrefs { it[CLIPBOARD_PINNED_LAST] = value }

    suspend fun setClipboardSearch(value: Boolean) =
        editPrefs { it[CLIPBOARD_SEARCH] = value }

    suspend fun setClipboardUserScreenshots(value: Boolean) =
        editPrefs { it[CLIPBOARD_USER_SCREENSHOTS] = value }

    suspend fun setClipboardClearAfterPasswordPaste(value: Boolean) =
        editPrefs { it[CLIPBOARD_CLEAR_AFTER_PASSWORD_PASTE] = value }

    suspend fun setClipboardDetectEntities(value: Boolean) =
        editPrefs { it[CLIPBOARD_DETECT_ENTITIES] = value }

    suspend fun setLongPressDelayMs(value: Int) =
        editPrefs { it[LONG_PRESS_DELAY] = value.coerceIn(150, 700) }

    suspend fun setKeyRepeatIntervalMs(value: Int) =
        editPrefs { it[KEY_REPEAT_INTERVAL] = value.coerceIn(20, 200) }

    suspend fun setLongPressHints(value: Boolean) =
        editPrefs { it[LONG_PRESS_HINTS] = value }

    suspend fun setLongPressASelectAll(value: Boolean) =
        editPrefs { it[LONG_PRESS_A_SELECT_ALL] = value }

    suspend fun setLongPressCCopy(value: Boolean) =
        editPrefs { it[LONG_PRESS_C_COPY] = value }

    suspend fun setLongPressVPaste(value: Boolean) =
        editPrefs { it[LONG_PRESS_V_PASTE] = value }

    suspend fun setLongPressXCut(value: Boolean) =
        editPrefs { it[LONG_PRESS_X_CUT] = value }

    suspend fun setLongPressZUndo(value: Boolean) =
        editPrefs { it[LONG_PRESS_Z_UNDO] = value }

    suspend fun setLongPressYRedo(value: Boolean) =
        editPrefs { it[LONG_PRESS_Y_REDO] = value }

    suspend fun setEmojiToolbar(value: Boolean) =
        editPrefs { it[EMOJI_TOOLBAR] = value }

    suspend fun setColoredToolIcons(value: Boolean) =
        editPrefs { it[COLORED_TOOL_ICONS] = value }

    /** Override one tool's accent colour; a null [color] restores its default. */
    suspend fun setToolColor(tool: ToolbarTool, color: Long?) =
        editPrefs { prefs ->
            val current = decodeToolColors(prefs[TOOL_COLOR_OVERRIDES]).toMutableMap()
            if (color == null) current.remove(tool) else current[tool] = color
            prefs[TOOL_COLOR_OVERRIDES] = encodeToolColors(current)
        }

    /** Drop every per-tool colour override, restoring all built-in defaults. */
    suspend fun clearToolColors() =
        editPrefs { it.remove(TOOL_COLOR_OVERRIDES) }

    /** Switch icon packs; a blank [packId] goes back to the built-in icons. */
    suspend fun setIconPack(packId: String) =
        editPrefs { it[ICON_PACK_ID] = packId }

    /** Override one slot's icon; a null [source] restores its default. */
    suspend fun setIconOverride(slot: String, source: String?) =
        editPrefs { prefs ->
            val current = IconOverrides.decode(prefs[ICON_OVERRIDES]).toMutableMap()
            if (source == null) current.remove(slot) else current[slot] = source
            prefs[ICON_OVERRIDES] = IconOverrides.encode(current)
        }

    /**
     * Drop every per-slot override *and* the active pack, so every icon is the
     * built-in one again. Uninstalling the packs themselves is separate — this
     * is "stop using them", not "delete them".
     */
    suspend fun clearIconOverrides() =
        editPrefs {
            it.remove(ICON_OVERRIDES)
            it.remove(ICON_PACK_ID)
        }

    /**
     * Forgets [packId] everywhere it is referenced, for when a pack is deleted:
     * the active pack falls back to the built-ins and any slot pinned to it
     * loses its override, rather than both silently resolving to nothing.
     */
    suspend fun forgetIconPack(packId: String) =
        editPrefs { prefs ->
            if (prefs[ICON_PACK_ID] == packId) prefs.remove(ICON_PACK_ID)
            val kept = IconOverrides.decode(prefs[ICON_OVERRIDES])
                .filterValues { it != IconOverrides.packSource(packId) }
            if (kept.isEmpty()) prefs.remove(ICON_OVERRIDES)
            else prefs[ICON_OVERRIDES] = IconOverrides.encode(kept)
        }

    suspend fun setEmojiTabMode(value: EmojiTabMode) =
        editPrefs { it[EMOJI_TAB_MODE] = value.name }

    suspend fun setEmojiClearRecentsButton(value: Boolean) =
        editPrefs { it[EMOJI_CLEAR_RECENTS_BUTTON] = value }

    suspend fun setEmojiLongPressName(value: Boolean) =
        editPrefs { it[EMOJI_LONG_PRESS_NAME] = value }

    suspend fun setEmojiPrediction(value: Boolean) =
        editPrefs { it[EMOJI_PREDICTION] = value }

    suspend fun setEmojiBarMode(value: EmojiBarMode) =
        editPrefs { it[EMOJI_BAR_MODE] = value.name }

    suspend fun setEmojiBarContent(value: EmojiBarContent) =
        editPrefs { it[EMOJI_BAR_CONTENT] = value.name }

    suspend fun setEmojiBarScrollable(value: Boolean) =
        editPrefs { it[EMOJI_BAR_SCROLLABLE] = value }

    suspend fun setEmojiBarCount(value: Int) =
        editPrefs { it[EMOJI_BAR_COUNT] = value.coerceIn(EmojiBarCountRange) }

    suspend fun setEmojiInsertMode(value: EmojiInsertMode) =
        editPrefs { it[EMOJI_INSERT_MODE] = value.name }

    suspend fun setEmojiDefaultSkinTone(value: EmojiSkinTone) =
        editPrefs { it[EMOJI_DEFAULT_SKIN_TONE] = value.name }

    suspend fun setEmojiToneOverrideByLastUsed(value: Boolean) =
        editPrefs { it[EMOJI_TONE_OVERRIDE_LAST_USED] = value }

    suspend fun setEmojiCloseAfterInsert(value: Boolean) =
        editPrefs { it[EMOJI_CLOSE_AFTER_INSERT] = value }

    suspend fun setHideUnrenderableEmoji(value: Boolean) =
        editPrefs { it[EMOJI_HIDE_UNRENDERABLE] = value }

    suspend fun setEmojiKaomojiTabs(value: Boolean) =
        editPrefs { it[EMOJI_KAOMOJI_TABS] = value }

    suspend fun setIncognito(value: Boolean) =
        editPrefs { it[INCOGNITO] = value }

    suspend fun setTranslateTargetLang(value: String) =
        editPrefs { it[TRANSLATE_TARGET_LANG] = value }

    suspend fun setGrammarDialect(value: GrammarDialect) =
        editPrefs { it[GRAMMAR_DIALECT] = value.name }

    suspend fun setSpellCheckerNoSuggestions(value: Boolean) =
        editPrefs { it[SPELL_CHECKER_NO_SUGGESTIONS] = value }

    suspend fun setTranslateApiKey(value: String) =
        editPrefs { it[TRANSLATE_API_KEY] = value.trim() }

    suspend fun setKlipyApiKey(value: String) =
        editPrefs { it[KLIPY_API_KEY] = value.trim() }

    suspend fun setBraveApiKey(value: String) =
        editPrefs { it[BRAVE_API_KEY] = value.trim() }

    suspend fun setGiphyApiKey(value: String) =
        editPrefs { it[GIPHY_API_KEY] = value.trim() }

    suspend fun setGifSourceMode(value: GifSourceMode) =
        editPrefs { it[GIF_SOURCE_MODE] = value.name }

    suspend fun setGifContentFilter(value: GifContentFilter) =
        editPrefs { it[GIF_CONTENT_FILTER] = value.name }

    suspend fun setSearchSafe(value: Boolean) =
        editPrefs { it[SEARCH_SAFE] = value }

    suspend fun setSearchResultCount(value: Int) =
        editPrefs { it[SEARCH_RESULT_COUNT] = value.coerceIn(1, 10) }

    suspend fun setWikiLanguage(value: String) =
        editPrefs { it[WIKI_LANGUAGE] = value.trim().lowercase() }

    suspend fun setWikiLinksMarkdown(value: Boolean) =
        editPrefs { it[WIKI_LINKS_MARKDOWN] = value }

    /** Pushes one symbol to the front of the recents row (capped, deduped). */
    suspend fun addSymbolRecent(symbol: String) =
        editPrefs { prefs ->
            val current = prefs[SYMBOL_RECENTS]?.split('\t')?.filter { it.isNotEmpty() }
                .orEmpty()
            prefs[SYMBOL_RECENTS] =
                (listOf(symbol) + current.filter { it != symbol }).take(24).joinToString("\t")
        }

    suspend fun clearSymbolRecents() =
        editPrefs { it.remove(SYMBOL_RECENTS) }

    suspend fun setSymbolRowEnabled(value: Boolean) =
        editPrefs { it[SYMBOL_ROW_ENABLED] = value }

    /** The sets the row's picker offers; an empty pick falls back to defaults. */
    suspend fun setSymbolRowSetIds(ids: List<String>) =
        editPrefs { it[SYMBOL_ROW_SETS] = ids.distinct().joinToString("\t") }

    suspend fun setSymbolRowActiveSet(id: String) =
        editPrefs { it[SYMBOL_ROW_ACTIVE_SET] = id }

    /** Adds the set or replaces the stored set with the same id. */
    suspend fun upsertSymbolSet(set: SymbolSet) =
        editPrefs { prefs ->
            val current = prefs[CUSTOM_SYMBOL_SETS]?.let { SymbolSetCodec.decodeList(it) }
                .orEmpty()
            val next = current.filter { it.id != set.id } + set
            prefs[CUSTOM_SYMBOL_SETS] = SymbolSetCodec.encodeList(next)
        }

    /** Deletes a custom set and drops every reference to it. */
    suspend fun deleteSymbolSet(id: String) =
        editPrefs { prefs ->
            val current = prefs[CUSTOM_SYMBOL_SETS]?.let { SymbolSetCodec.decodeList(it) }
                .orEmpty()
            prefs[CUSTOM_SYMBOL_SETS] = SymbolSetCodec.encodeList(current.filter { it.id != id })
            // Deleting an edited built-in only drops the override — the
            // shipped set comes back, so every reference to it stays valid.
            if (BuiltInSymbolSets.byId(id) != null) return@editPrefs
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
        editPrefs {
            it[BAR_ORDER] = sanitizeBarOrder(rows).joinToString(",") { row -> row.name }
        }

    suspend fun setEmojiFullBleed(value: Boolean) =
        editPrefs { it[EMOJI_FULL_BLEED] = value }

    suspend fun setMediaFullBleed(value: Boolean) =
        editPrefs { it[MEDIA_FULL_BLEED] = value }

    suspend fun setModeToolOrderEdits(value: Boolean) =
        editPrefs { it[MODE_TOOL_ORDER_EDITS] = value }

    suspend fun setModeToolOrderHintSeen(value: Boolean) =
        editPrefs { it[MODE_TOOL_ORDER_HINT] = value }

    /**
     * Rewrites one mode's pinned toolbar, so a drag made while that mode is
     * active lands where it will actually be read back from. Pinning into a
     * mode that was appending its tools switches it to replacing them: the
     * dragged arrangement is the whole bar the user just laid out, and
     * re-appending would shuffle it behind the global pins.
     */
    suspend fun setModeToolbarTools(modeId: String, tools: List<ToolbarTool>) =
        editPrefs { prefs ->
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
        editPrefs { prefs ->
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
        editPrefs { prefs ->
            val seeded = prefs[MODE_SEED_VERSION] ?: 0
            if (seeded >= CurrentModeSeedVersion) return@editPrefs
            val stored = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
            prefs[MODE_SEED_VERSION] = CurrentModeSeedVersion
            // No stored list at all: the read path already falls back to the
            // full defaults, so there is nothing to top up.
            if (stored == null) return@editPrefs
            val have = stored.map { it.id }.toSet()
            val missing = DefaultKeyboardModes.filter {
                it.id !in have && it.id in ModesAddedInSeedVersion2
            }
            if (missing.isEmpty()) return@editPrefs
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(stored + missing)
        }

    /** Adds the mode or replaces the stored mode with the same id. */
    suspend fun upsertKeyboardMode(mode: KeyboardMode) =
        editPrefs { prefs ->
            val current = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: DefaultKeyboardModes
            val next =
                if (current.any { it.id == mode.id }) current.map { if (it.id == mode.id) mode else it }
                else current + mode
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(next)
        }

    suspend fun deleteKeyboardMode(id: String) =
        editPrefs { prefs ->
            val current = prefs[KEYBOARD_MODES]?.let { KeyboardModeCodec.decodeList(it) }
                ?: DefaultKeyboardModes
            prefs[KEYBOARD_MODES] = KeyboardModeCodec.encodeList(current.filter { it.id != id })
        }

    /**
     * Restores a built-in mode to the configuration it ships with (its entry
     * in [DefaultKeyboardModes]), discarding the user's edits to it. A no-op
     * for an id that was never a built-in — a user-created mode has no shipped
     * default to fall back to.
     */
    suspend fun resetKeyboardModeToDefault(id: String) {
        val default = DefaultKeyboardModes.firstOrNull { it.id == id } ?: return
        upsertKeyboardMode(default)
    }

    suspend fun setSmartSuggestions(value: Boolean) =
        editPrefs { it[SMART_SUGGESTIONS] = value }

    suspend fun setSmartCalc(value: Boolean) =
        editPrefs { it[SMART_CALC] = value }

    suspend fun setSmartCurrency(value: Boolean) =
        editPrefs { it[SMART_CURRENCY] = value }

    suspend fun setSmartUnits(value: Boolean) =
        editPrefs { it[SMART_UNITS] = value }

    suspend fun setSmartToolKeywords(value: Boolean) =
        editPrefs { it[SMART_TOOL_KEYWORDS] = value }

    /** Replaces one tool's trigger words; an empty list silences that tool. */
    suspend fun setToolKeywords(tool: ToolbarTool, words: List<String>) =
        editPrefs {
            it[TOOL_KEYWORDS] = SmartSuggest.withKeywords(it[TOOL_KEYWORDS].orEmpty(), tool, words)
        }

    suspend fun setCalcDegrees(value: Boolean) =
        editPrefs { it[CALC_DEGREES] = value }

    suspend fun setCalcPrecision(value: Int) =
        editPrefs { it[CALC_PRECISION] = value.coerceIn(0, 12) }

    suspend fun setCurrencyPair(from: String, to: String) =
        editPrefs {
            it[CURRENCY_FROM] = from.trim().uppercase()
            it[CURRENCY_TO] = to.trim().uppercase()
        }

    suspend fun setPwLength(value: Int) =
        editPrefs { it[PW_LENGTH] = value.coerceIn(4, 64) }

    suspend fun setPwUppercase(value: Boolean) =
        editPrefs { it[PW_UPPERCASE] = value }

    suspend fun setPwDigits(value: Boolean) =
        editPrefs { it[PW_DIGITS] = value }

    suspend fun setPwSymbols(value: Boolean) =
        editPrefs { it[PW_SYMBOLS] = value }

    suspend fun setPwExcludeAmbiguous(value: Boolean) =
        editPrefs { it[PW_EXCLUDE_AMBIGUOUS] = value }

    suspend fun setPwPassphraseMode(value: Boolean) =
        editPrefs { it[PW_PASSPHRASE_MODE] = value }

    suspend fun setPpWordCount(value: Int) =
        editPrefs { it[PP_WORD_COUNT] = value.coerceIn(2, 10) }

    suspend fun setPpSeparator(value: String) =
        editPrefs { it[PP_SEPARATOR] = value.take(3) }

    suspend fun setPpCapitalize(value: Boolean) =
        editPrefs { it[PP_CAPITALIZE] = value }

    suspend fun setPpIncludeDigit(value: Boolean) =
        editPrefs { it[PP_INCLUDE_DIGIT] = value }

    suspend fun setTypingTestMode(value: TypingTestMode) =
        editPrefs { it[TT_MODE] = value.name }

    suspend fun setTypingTestDuration(value: Int) =
        editPrefs { it[TT_DURATION] = value.coerceIn(5, 600) }

    suspend fun setTypingTestWordCount(value: Int) =
        editPrefs { it[TT_WORD_COUNT] = value.coerceIn(5, 500) }

    suspend fun setTypingTestPunctuation(value: Boolean) =
        editPrefs { it[TT_PUNCTUATION] = value }

    suspend fun setTypingTestNumbers(value: Boolean) =
        editPrefs { it[TT_NUMBERS] = value }

    /**
     * Files a finished run: appends it to the history, bumps the counter,
     * and stores a new personal best when [bests] is non-null (the caller
     * has already checked whether the record fell).
     */
    suspend fun recordTypingResult(history: String, bests: String?) =
        editPrefs { p ->
            p[TT_HISTORY] = history
            if (bests != null) p[TT_BESTS] = bests
            p[TT_COMPLETED] = (p[TT_COMPLETED] ?: 0) + 1
        }

    /** Wipes the personal bests and the score history. */
    suspend fun clearTypingStats() =
        editPrefs {
            it[TT_BESTS] = ""
            it[TT_HISTORY] = ""
            it[TT_COMPLETED] = 0
        }

    suspend fun setQrSizePx(value: Int) =
        editPrefs { it[QR_SIZE_PX] = value.coerceIn(256, 2048) }

    suspend fun setQrEcc(value: QrEccLevel) =
        editPrefs { it[QR_ECC] = value.name }

    suspend fun setAiProvider(value: AiProvider) =
        editPrefs { it[AI_PROVIDER] = value.name }

    suspend fun setAiAnthropicKey(value: String) =
        editPrefs { it[AI_ANTHROPIC_KEY] = value.trim() }

    suspend fun setAiOpenAiKey(value: String) =
        editPrefs { it[AI_OPENAI_KEY] = value.trim() }

    suspend fun setAiGeminiKey(value: String) =
        editPrefs { it[AI_GEMINI_KEY] = value.trim() }

    suspend fun setAiAnthropicModel(value: String) =
        editPrefs { it[AI_ANTHROPIC_MODEL] = value.trim() }

    suspend fun setAiOpenAiModel(value: String) =
        editPrefs { it[AI_OPENAI_MODEL] = value.trim() }

    suspend fun setAiGeminiModel(value: String) =
        editPrefs { it[AI_GEMINI_MODEL] = value.trim() }

    suspend fun setAiOllamaUrl(value: String) =
        editPrefs { it[AI_OLLAMA_URL] = value.trim().trimEnd('/') }

    suspend fun setAiOllamaModel(value: String) =
        editPrefs { it[AI_OLLAMA_MODEL] = value.trim() }

    suspend fun setAiLmStudioUrl(value: String) =
        editPrefs { it[AI_LM_STUDIO_URL] = value.trim().trimEnd('/') }

    suspend fun setAiLmStudioModel(value: String) =
        editPrefs { it[AI_LM_STUDIO_MODEL] = value.trim() }

    suspend fun setAiMaxTokens(value: Int) =
        editPrefs { it[AI_MAX_TOKENS] = value.coerceIn(64, 8192) }

    suspend fun setAiTranslateTo(value: String) =
        editPrefs { it[AI_TRANSLATE_TO] = value.trim() }

    suspend fun setAiPrompt(action: AiAction, value: String) =
        editPrefs {
            val key = when (action) {
                AiAction.REWRITE -> AI_PROMPT_REWRITE
                AiAction.SUMMARIZE -> AI_PROMPT_SUMMARIZE
                AiAction.TRANSLATE -> AI_PROMPT_TRANSLATE
                AiAction.IMPROVE -> AI_PROMPT_IMPROVE
                AiAction.FIX_GRAMMAR -> AI_PROMPT_FIX_GRAMMAR
                AiAction.EXPLAIN -> AI_PROMPT_EXPLAIN
                AiAction.CONTINUE -> AI_PROMPT_CONTINUE
                // Custom's prompt is typed at run time — nothing to persist.
                AiAction.CUSTOM -> return@editPrefs
            }
            it[key] = value
        }

    suspend fun setAiLocalModelId(value: String) =
        editPrefs { it[AI_LOCAL_MODEL_ID] = value }

    suspend fun setAiLocalBackend(value: LocalLlmBackend) =
        editPrefs { it[AI_LOCAL_BACKEND] = value.name }

    suspend fun setHfToken(value: String) =
        editPrefs { it[HF_TOKEN] = value.trim() }

    suspend fun setAiShowThinking(value: Boolean) =
        editPrefs { it[AI_SHOW_THINKING] = value }

    suspend fun setAiPanelModelPicker(value: Boolean) =
        editPrefs { it[AI_PANEL_MODEL_PICKER] = value }
}
