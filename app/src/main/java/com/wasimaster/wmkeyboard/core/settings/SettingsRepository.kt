package com.wasimaster.wmkeyboard.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which script/input method the keyboard is currently in. */
enum class InputMode { ENGLISH, AVRO, PROBHAT }

/** Visual theme for the keyboard and settings app. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

data class KeyboardSettings(
    val inputMode: InputMode = InputMode.ENGLISH,
    val enabledModes: List<InputMode> = listOf(InputMode.ENGLISH, InputMode.AVRO, InputMode.PROBHAT),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val keyHeightDp: Int = 54,
    val keyCornerRadiusDp: Int = 14,
    val fontScale: Float = 1.0f,
    val hapticFeedback: Boolean = true,
    val hapticStrengthMs: Int = 15,
    val keySound: Boolean = false,
    val keyPopup: Boolean = true,
    val numberRow: Boolean = false,
    val autocorrect: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val suggestions: Boolean = true,
    val gestureTyping: Boolean = true,
    val spacebarCursor: Boolean = true,
    val conjunctBackspace: Boolean = false,
    val learnFromTyping: Boolean = true,
    val clipboardHistory: Boolean = true,
    val clipboardExpiryHours: Int = 24,
    val longPressDelayMs: Int = 300,
    val keyRepeatIntervalMs: Int = 50,
    val emojiToolbar: Boolean = true,
    val incognito: Boolean = false,
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
        private val KEY_HEIGHT = intPreferencesKey("key_height")
        private val KEY_CORNER_RADIUS = intPreferencesKey("key_corner_radius")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val HAPTIC = booleanPreferencesKey("haptic")
        private val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength")
        private val KEY_SOUND = booleanPreferencesKey("key_sound")
        private val KEY_POPUP = booleanPreferencesKey("key_popup")
        private val NUMBER_ROW = booleanPreferencesKey("number_row")
        private val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        private val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        private val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        private val SUGGESTIONS = booleanPreferencesKey("suggestions")
        private val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
        private val SPACEBAR_CURSOR = booleanPreferencesKey("spacebar_cursor")
        private val CONJUNCT_BACKSPACE = booleanPreferencesKey("conjunct_backspace")
        private val LEARN_FROM_TYPING = booleanPreferencesKey("learn_from_typing")
        private val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        private val CLIPBOARD_EXPIRY_HOURS = intPreferencesKey("clipboard_expiry_hours")
        private val LONG_PRESS_DELAY = intPreferencesKey("long_press_delay")
        private val KEY_REPEAT_INTERVAL = intPreferencesKey("key_repeat_interval")
        private val EMOJI_TOOLBAR = booleanPreferencesKey("emoji_toolbar")
        private val INCOGNITO = booleanPreferencesKey("incognito")
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
            keyHeightDp = p[KEY_HEIGHT] ?: defaults.keyHeightDp,
            keyCornerRadiusDp = p[KEY_CORNER_RADIUS] ?: defaults.keyCornerRadiusDp,
            fontScale = p[FONT_SCALE] ?: defaults.fontScale,
            hapticFeedback = p[HAPTIC] ?: defaults.hapticFeedback,
            hapticStrengthMs = p[HAPTIC_STRENGTH] ?: defaults.hapticStrengthMs,
            keySound = p[KEY_SOUND] ?: defaults.keySound,
            keyPopup = p[KEY_POPUP] ?: defaults.keyPopup,
            numberRow = p[NUMBER_ROW] ?: defaults.numberRow,
            autocorrect = p[AUTOCORRECT] ?: defaults.autocorrect,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
            doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: defaults.doubleSpacePeriod,
            suggestions = p[SUGGESTIONS] ?: defaults.suggestions,
            gestureTyping = p[GESTURE_TYPING] ?: defaults.gestureTyping,
            spacebarCursor = p[SPACEBAR_CURSOR] ?: defaults.spacebarCursor,
            conjunctBackspace = p[CONJUNCT_BACKSPACE] ?: defaults.conjunctBackspace,
            learnFromTyping = p[LEARN_FROM_TYPING] ?: defaults.learnFromTyping,
            clipboardHistory = p[CLIPBOARD_HISTORY] ?: defaults.clipboardHistory,
            clipboardExpiryHours = p[CLIPBOARD_EXPIRY_HOURS] ?: defaults.clipboardExpiryHours,
            longPressDelayMs = p[LONG_PRESS_DELAY] ?: defaults.longPressDelayMs,
            keyRepeatIntervalMs = p[KEY_REPEAT_INTERVAL] ?: defaults.keyRepeatIntervalMs,
            emojiToolbar = p[EMOJI_TOOLBAR] ?: defaults.emojiToolbar,
            incognito = p[INCOGNITO] ?: defaults.incognito,
        )
    }

    suspend fun setInputMode(mode: InputMode) =
        context.dataStore.edit { it[INPUT_MODE] = mode.name }

    suspend fun setEnabledModes(modes: List<InputMode>) =
        context.dataStore.edit { it[ENABLED_MODES] = modes.joinToString(",") { m -> m.name } }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[THEME_MODE] = mode.name }

    suspend fun setDynamicColor(value: Boolean) =
        context.dataStore.edit { it[DYNAMIC_COLOR] = value }

    suspend fun setKeyHeightDp(value: Int) =
        context.dataStore.edit { it[KEY_HEIGHT] = value.coerceIn(40, 80) }

    suspend fun setKeyCornerRadiusDp(value: Int) =
        context.dataStore.edit { it[KEY_CORNER_RADIUS] = value.coerceIn(0, 28) }

    suspend fun setFontScale(value: Float) =
        context.dataStore.edit { it[FONT_SCALE] = value.coerceIn(0.7f, 1.5f) }

    suspend fun setHapticFeedback(value: Boolean) =
        context.dataStore.edit { it[HAPTIC] = value }

    suspend fun setHapticStrengthMs(value: Int) =
        context.dataStore.edit { it[HAPTIC_STRENGTH] = value.coerceIn(5, 60) }

    suspend fun setKeySound(value: Boolean) =
        context.dataStore.edit { it[KEY_SOUND] = value }

    suspend fun setKeyPopup(value: Boolean) =
        context.dataStore.edit { it[KEY_POPUP] = value }

    suspend fun setNumberRow(value: Boolean) =
        context.dataStore.edit { it[NUMBER_ROW] = value }

    suspend fun setAutocorrect(value: Boolean) =
        context.dataStore.edit { it[AUTOCORRECT] = value }

    suspend fun setAutoCapitalize(value: Boolean) =
        context.dataStore.edit { it[AUTO_CAPITALIZE] = value }

    suspend fun setDoubleSpacePeriod(value: Boolean) =
        context.dataStore.edit { it[DOUBLE_SPACE_PERIOD] = value }

    suspend fun setSuggestions(value: Boolean) =
        context.dataStore.edit { it[SUGGESTIONS] = value }

    suspend fun setGestureTyping(value: Boolean) =
        context.dataStore.edit { it[GESTURE_TYPING] = value }

    suspend fun setSpacebarCursor(value: Boolean) =
        context.dataStore.edit { it[SPACEBAR_CURSOR] = value }

    suspend fun setConjunctBackspace(value: Boolean) =
        context.dataStore.edit { it[CONJUNCT_BACKSPACE] = value }

    suspend fun setLearnFromTyping(value: Boolean) =
        context.dataStore.edit { it[LEARN_FROM_TYPING] = value }

    suspend fun setClipboardHistory(value: Boolean) =
        context.dataStore.edit { it[CLIPBOARD_HISTORY] = value }

    suspend fun setClipboardExpiryHours(value: Int) =
        context.dataStore.edit { it[CLIPBOARD_EXPIRY_HOURS] = value.coerceIn(0, 24 * 7) }

    suspend fun setLongPressDelayMs(value: Int) =
        context.dataStore.edit { it[LONG_PRESS_DELAY] = value.coerceIn(150, 700) }

    suspend fun setKeyRepeatIntervalMs(value: Int) =
        context.dataStore.edit { it[KEY_REPEAT_INTERVAL] = value.coerceIn(20, 200) }

    suspend fun setEmojiToolbar(value: Boolean) =
        context.dataStore.edit { it[EMOJI_TOOLBAR] = value }

    suspend fun setIncognito(value: Boolean) =
        context.dataStore.edit { it[INCOGNITO] = value }
}
