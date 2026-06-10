package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariantIndex
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.tools.WeatherInfo

enum class ShiftState { OFF, ON, CAPS_LOCK }

enum class LayoutMode { LETTERS, SYMBOLS, SYMBOLS_SHIFTED }

enum class PanelMode {
    NONE, EMOJI, CLIPBOARD, SNIPPETS, TOOLBOX, TEXT_EDIT,
    COMPASS, LEVEL, MOON_PHASE, WEATHER, CALENDAR,
    THEMES, SOUND_HAPTICS, NUMPAD, HANDWRITING, CAMERA, DICTIONARY,
}

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

/** One change made from the sound & haptics quick panel. */
sealed interface SoundHapticAction {
    data class Haptics(val on: Boolean) : SoundHapticAction
    data class HapticStyleChange(val style: com.wasimaster.wmkeyboard.core.settings.HapticStyle) : SoundHapticAction
    data class HapticAmplitude(val amplitude: Int) : SoundHapticAction
    data class Sound(val on: Boolean) : SoundHapticAction
    data class SoundStyleChange(val style: com.wasimaster.wmkeyboard.core.settings.KeySoundStyle) : SoundHapticAction
    data class SoundVolume(val volume: Float) : SoundHapticAction
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
    UP, DOWN, LEFT, RIGHT, HOME, END,
    SELECT, SELECT_ALL, COPY, PASTE, BACKSPACE,
}

/** What the enter key does in the focused field, from EditorInfo.imeOptions. */
enum class EnterAction { DEFAULT, SEARCH, SEND, GO, NEXT, PREVIOUS, DONE }

/**
 * Immutable UI state rendered by the Compose keyboard. The service owns a
 * MutableStateFlow of this and mutates it via copy().
 */
data class KeyboardUiState(
    val settings: KeyboardSettings = KeyboardSettings(),
    val inputMode: InputMode = InputMode.ENGLISH,
    val layoutMode: LayoutMode = LayoutMode.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val panel: PanelMode = PanelMode.NONE,
    val suggestions: List<String> = emptyList(),
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
    val emojiCatalog: List<EmojiEntry> = emptyList(),
    /** RGI toned-sequence lookup; loaded once with the catalog. */
    val emojiVariants: EmojiVariantIndex = EmojiVariantIndex.empty(),
    val clipboardItems: List<ClipItem> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val secureField: Boolean = false,
    val enterAction: EnterAction = EnterAction.DEFAULT,
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
)
