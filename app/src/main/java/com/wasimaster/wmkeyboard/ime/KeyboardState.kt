package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
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
    THEMES, SOUND_HAPTICS, NUMPAD,
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

/** Weather panel state, owned by the service (it does the fetching). */
sealed interface WeatherUi {
    /** No location configured in the weather tool's settings. */
    data object NoLocation : WeatherUi
    data object Loading : WeatherUi
    data object Error : WeatherUi
    data class Ready(val info: WeatherInfo) : WeatherUi
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
    val emojiCatalog: List<EmojiEntry> = emptyList(),
    val clipboardItems: List<ClipItem> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val secureField: Boolean = false,
    val enterAction: EnterAction = EnterAction.DEFAULT,
    /** Torch state, mirrored from CameraManager for the flashlight tool. */
    val torchOn: Boolean = false,
    val weather: WeatherUi = WeatherUi.NoLocation,
    /**
     * What the vowel keys should produce given the character before the
     * cursor: kar (া, ি …) after a consonant, the য়-glide (য়া, য়ে) after
     * a vowel, and the independent letter (আ, ই …) at a word start. Fixed
     * Bengali layouts use it for both the committed output and the
     * displayed key labels.
     */
    val vowelForm: BengaliGraphemes.VowelKeyForm = BengaliGraphemes.VowelKeyForm.INDEPENDENT,
)
