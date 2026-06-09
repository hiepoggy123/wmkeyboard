package com.wasimaster.wmkeyboard.core.settings

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which script/input method the keyboard is currently in. */
enum class InputMode { ENGLISH, AVRO, PROBHAT, JATIYA }

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
 * A tool that can live on the top toolbar. Tools not in
 * [KeyboardSettings.toolbarTools] wait in the toolbox panel; the user drags
 * them between the two while the toolbox is open (Gboard style).
 */
enum class ToolbarTool { EMOJI, CLIPBOARD, SNIPPETS, TEXT_EDIT, ONE_HANDED, SPLIT, FLOATING, SETTINGS }

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
    val hapticFeedback: Boolean = true,
    val hapticStrengthMs: Int = 15,
    val hapticAmplitude: Int = 255,
    val hapticStyle: HapticStyle = HapticStyle.CUSTOM,
    val hapticOnLongPress: Boolean = true,
    val hapticOnLongPressRelease: Boolean = false,
    val keySound: Boolean = false,
    val keyPopup: Boolean = true,
    val keyPopupMinDurationMs: Int = 150,
    val keyPopupOnKey: Boolean = true,
    val popupFontScale: Float = 1.0f,
    val keyPopupHeightDp: Int = 110,
    val numberRow: Boolean = false,
    val autocorrect: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val suggestions: Boolean = true,
    val gestureTyping: Boolean = true,
    /** Swipe that starts moving before the long-press delay elapses. */
    val spaceShortSwipe: SpaceSwipeAction = SpaceSwipeAction.LANGUAGE,
    /** Swipe that begins after holding the spacebar past the long-press delay. */
    val spaceLongSwipe: SpaceSwipeAction = SpaceSwipeAction.CURSOR,
    /** Replace the 🌐 key with an emoji key (language switching moves to spacebar swipes). */
    val globeAsEmoji: Boolean = true,
    val onboardingDone: Boolean = false,
    val conjunctBackspace: Boolean = false,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val learnFromTyping: Boolean = true,
    val clipboardHistory: Boolean = true,
    val clipboardExpiryHours: Int = 24,
    val longPressDelayMs: Int = 300,
    val keyRepeatIntervalMs: Int = 50,
    val emojiToolbar: Boolean = true,
    val incognito: Boolean = false,
    val toolbarTools: List<ToolbarTool> =
        listOf(ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS, ToolbarTool.SETTINGS),
    val toolbarGreedy: Boolean = true,
    val toolCircleRadiusDp: Int = 20,
    val commaAsEmoji: Boolean = false,
    /** History tab of the emoji panel: recently used vs most used. */
    val emojiTabMode: EmojiTabMode = EmojiTabMode.RECENTS,
    /** Emoji candidates in the suggestion strip while typing. */
    val emojiPrediction: Boolean = true,
    val emojiBarMode: EmojiBarMode = EmojiBarMode.BUTTON,
    val emojiBarContent: EmojiBarContent = EmojiBarContent.MOST_USED,
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
        private val NUMBER_ROW = booleanPreferencesKey("number_row")
        private val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        private val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        private val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        private val SUGGESTIONS = booleanPreferencesKey("suggestions")
        private val GESTURE_TYPING = booleanPreferencesKey("gesture_typing")
        // Legacy boolean, read only to migrate into SPACE_LONG_SWIPE.
        private val SPACEBAR_CURSOR = booleanPreferencesKey("spacebar_cursor")
        private val SPACE_SHORT_SWIPE = stringPreferencesKey("space_short_swipe")
        private val SPACE_LONG_SWIPE = stringPreferencesKey("space_long_swipe")
        private val GLOBE_AS_EMOJI = booleanPreferencesKey("globe_as_emoji")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val CONJUNCT_BACKSPACE = booleanPreferencesKey("conjunct_backspace")
        private val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        private val LEARN_FROM_TYPING = booleanPreferencesKey("learn_from_typing")
        private val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        private val CLIPBOARD_EXPIRY_HOURS = intPreferencesKey("clipboard_expiry_hours")
        private val LONG_PRESS_DELAY = intPreferencesKey("long_press_delay")
        private val KEY_REPEAT_INTERVAL = intPreferencesKey("key_repeat_interval")
        private val EMOJI_TOOLBAR = booleanPreferencesKey("emoji_toolbar")
        private val INCOGNITO = booleanPreferencesKey("incognito")
        private val TOOLBAR_TOOLS = stringPreferencesKey("toolbar_tools")
        private val TOOLBAR_GREEDY = booleanPreferencesKey("toolbar_greedy")
        private val TOOL_CIRCLE_RADIUS = intPreferencesKey("tool_circle_radius")
        private val COMMA_AS_EMOJI = booleanPreferencesKey("comma_as_emoji")
        private val EMOJI_TAB_MODE = stringPreferencesKey("emoji_tab_mode")
        private val EMOJI_PREDICTION = booleanPreferencesKey("emoji_prediction")
        private val EMOJI_BAR_MODE = stringPreferencesKey("emoji_bar_mode")
        private val EMOJI_BAR_CONTENT = stringPreferencesKey("emoji_bar_content")
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
            hapticFeedback = p[HAPTIC] ?: defaults.hapticFeedback,
            hapticStrengthMs = p[HAPTIC_STRENGTH] ?: defaults.hapticStrengthMs,
            hapticAmplitude = p[HAPTIC_AMPLITUDE] ?: defaults.hapticAmplitude,
            hapticStyle = p[HAPTIC_STYLE]?.let { runCatching { HapticStyle.valueOf(it) }.getOrNull() }
                ?: defaults.hapticStyle,
            hapticOnLongPress = p[HAPTIC_ON_LONG_PRESS] ?: defaults.hapticOnLongPress,
            hapticOnLongPressRelease = p[HAPTIC_ON_LONG_PRESS_RELEASE]
                ?: defaults.hapticOnLongPressRelease,
            keySound = p[KEY_SOUND] ?: defaults.keySound,
            keyPopup = p[KEY_POPUP] ?: defaults.keyPopup,
            keyPopupMinDurationMs = p[KEY_POPUP_MIN_DURATION] ?: defaults.keyPopupMinDurationMs,
            keyPopupOnKey = p[KEY_POPUP_ON_KEY] ?: defaults.keyPopupOnKey,
            popupFontScale = p[POPUP_FONT_SCALE] ?: defaults.popupFontScale,
            keyPopupHeightDp = p[KEY_POPUP_HEIGHT] ?: defaults.keyPopupHeightDp,
            numberRow = p[NUMBER_ROW] ?: defaults.numberRow,
            autocorrect = p[AUTOCORRECT] ?: defaults.autocorrect,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: defaults.autoCapitalize,
            doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: defaults.doubleSpacePeriod,
            suggestions = p[SUGGESTIONS] ?: defaults.suggestions,
            gestureTyping = p[GESTURE_TYPING] ?: defaults.gestureTyping,
            spaceShortSwipe = p[SPACE_SHORT_SWIPE]
                ?.let { runCatching { SpaceSwipeAction.valueOf(it) }.getOrNull() }
                ?: defaults.spaceShortSwipe,
            // Users who had explicitly turned spacebar cursor control off
            // keep it off until they pick a new swipe action.
            spaceLongSwipe = p[SPACE_LONG_SWIPE]
                ?.let { runCatching { SpaceSwipeAction.valueOf(it) }.getOrNull() }
                ?: if (p[SPACEBAR_CURSOR] == false) SpaceSwipeAction.NONE else defaults.spaceLongSwipe,
            globeAsEmoji = p[GLOBE_AS_EMOJI] ?: defaults.globeAsEmoji,
            onboardingDone = p[ONBOARDING_DONE] ?: defaults.onboardingDone,
            conjunctBackspace = p[CONJUNCT_BACKSPACE] ?: defaults.conjunctBackspace,
            oneHandedMode = p[ONE_HANDED_MODE]
                ?.let { runCatching { OneHandedMode.valueOf(it) }.getOrNull() }
                ?: defaults.oneHandedMode,
            learnFromTyping = p[LEARN_FROM_TYPING] ?: defaults.learnFromTyping,
            clipboardHistory = p[CLIPBOARD_HISTORY] ?: defaults.clipboardHistory,
            clipboardExpiryHours = p[CLIPBOARD_EXPIRY_HOURS] ?: defaults.clipboardExpiryHours,
            longPressDelayMs = p[LONG_PRESS_DELAY] ?: defaults.longPressDelayMs,
            keyRepeatIntervalMs = p[KEY_REPEAT_INTERVAL] ?: defaults.keyRepeatIntervalMs,
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
            emojiPrediction = p[EMOJI_PREDICTION] ?: defaults.emojiPrediction,
            emojiBarMode = p[EMOJI_BAR_MODE]
                ?.let { runCatching { EmojiBarMode.valueOf(it) }.getOrNull() }
                ?: defaults.emojiBarMode,
            emojiBarContent = p[EMOJI_BAR_CONTENT]
                ?.let { runCatching { EmojiBarContent.valueOf(it) }.getOrNull() }
                ?: defaults.emojiBarContent,
        )
    }

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

    suspend fun setSpaceShortSwipe(value: SpaceSwipeAction) =
        context.dataStore.edit { it[SPACE_SHORT_SWIPE] = value.name }

    suspend fun setSpaceLongSwipe(value: SpaceSwipeAction) =
        context.dataStore.edit { it[SPACE_LONG_SWIPE] = value.name }

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

    suspend fun setLongPressDelayMs(value: Int) =
        context.dataStore.edit { it[LONG_PRESS_DELAY] = value.coerceIn(150, 700) }

    suspend fun setKeyRepeatIntervalMs(value: Int) =
        context.dataStore.edit { it[KEY_REPEAT_INTERVAL] = value.coerceIn(20, 200) }

    suspend fun setEmojiToolbar(value: Boolean) =
        context.dataStore.edit { it[EMOJI_TOOLBAR] = value }

    suspend fun setEmojiTabMode(value: EmojiTabMode) =
        context.dataStore.edit { it[EMOJI_TAB_MODE] = value.name }

    suspend fun setEmojiPrediction(value: Boolean) =
        context.dataStore.edit { it[EMOJI_PREDICTION] = value }

    suspend fun setEmojiBarMode(value: EmojiBarMode) =
        context.dataStore.edit { it[EMOJI_BAR_MODE] = value.name }

    suspend fun setEmojiBarContent(value: EmojiBarContent) =
        context.dataStore.edit { it[EMOJI_BAR_CONTENT] = value.name }

    suspend fun setIncognito(value: Boolean) =
        context.dataStore.edit { it[INCOGNITO] = value }
}
