package com.wasimaster.wmkeyboard.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.accessibility.KeyboardPassthrough
import com.wasimaster.wmkeyboard.core.settings.ColorVisionFilter
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ScreenReaderMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import kotlinx.coroutines.launch

/**
 * The dyslexia-friendly face. Atkinson Hyperlegible was designed by the
 * Braille Institute specifically to disambiguate letterforms that collapse
 * into each other at low vision or with reading difficulty (I/l/1, O/0), and
 * unlike OpenDyslexic it is on Google Fonts, so it rides the existing
 * downloadable-fonts path with no bundled asset.
 */
private const val READABLE_FONT = "Atkinson Hyperlegible"

@Composable
internal fun AccessibilitySettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenFonts: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenKeyPress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val readableFontId = KeyboardFonts.googleId(READABLE_FONT)

    SettingsGroup(stringResource(R.string.accessibility_vision_title)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.accessibility_color_vision_title),
                subtitle = stringResource(R.string.accessibility_color_vision_subtitle),
                info = stringResource(R.string.accessibility_color_vision_info),
                options = listOf(
                    ColorVisionFilter.NONE to stringResource(CommonR.string.common_off),
                    ColorVisionFilter.DEUTERANOPIA to
                        stringResource(R.string.accessibility_color_vision_deutan),
                    ColorVisionFilter.PROTANOPIA to
                        stringResource(R.string.accessibility_color_vision_protan),
                    ColorVisionFilter.TRITANOPIA to
                        stringResource(R.string.accessibility_color_vision_tritan),
                    ColorVisionFilter.GRAYSCALE to
                        stringResource(R.string.accessibility_color_vision_grey),
                ),
                selected = settings.colorVisionFilter,
            ) { scope.launch { repository.setColorVisionFilter(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.accessibility_high_contrast_title),
                stringResource(R.string.accessibility_high_contrast_subtitle),
                settings.highContrastKeys,
                info = stringResource(R.string.accessibility_high_contrast_info),
            ) { scope.launch { repository.setHighContrastKeys(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.accessibility_key_outlines_title),
                stringResource(R.string.accessibility_key_outlines_subtitle),
                settings.keyOutlines,
                info = stringResource(R.string.accessibility_key_outlines_info),
            ) { scope.launch { repository.setKeyOutlines(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.accessibility_bold_labels_title),
                stringResource(R.string.accessibility_bold_labels_subtitle),
                settings.boldKeyLabels,
                info = stringResource(R.string.accessibility_bold_labels_info),
            ) { scope.launch { repository.setBoldKeyLabels(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.accessibility_readable_font_title),
                stringResource(R.string.accessibility_readable_font_subtitle, READABLE_FONT),
                settings.keyFontId == readableFontId,
                info = stringResource(R.string.accessibility_readable_font_info, READABLE_FONT),
            ) { on ->
                scope.launch {
                    repository.setKeyFontId(if (on) readableFontId else KeyboardFonts.DEFAULT_ID)
                }
            }
        }
        item {
            SliderSetting(
                title = stringResource(R.string.accessibility_text_size_title),
                subtitle = stringResource(R.string.accessibility_text_size_subtitle),
                value = settings.fontScale,
                range = 0.7f..1.5f,
                display = { "${(it * 100).toInt()}%" },
                info = stringResource(R.string.accessibility_text_size_info),
            ) { scope.launch { repository.setFontScale(it) } }
        }
        item {
            NavRow(
                stringResource(R.string.accessibility_keyboard_font_title),
                stringResource(R.string.accessibility_keyboard_font_subtitle),
                KeyboardFonts.displayName(context, settings.keyFontId, settings.customFontName),
                onClick = onOpenFonts,
            )
        }
    }

    SettingsGroup(stringResource(R.string.accessibility_motion_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.accessibility_reduce_motion_title),
                stringResource(R.string.accessibility_reduce_motion_subtitle),
                settings.reduceMotion,
                info = stringResource(R.string.accessibility_reduce_motion_info),
            ) { scope.launch { repository.setReduceMotion(it) } }
        }
    }

    SettingsGroup(stringResource(R.string.accessibility_screen_reader_title)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.accessibility_talkback_title),
                subtitle = stringResource(R.string.accessibility_talkback_subtitle),
                info = stringResource(R.string.accessibility_talkback_info),
                options = listOf(
                    ScreenReaderMode.OFF to stringResource(CommonR.string.common_off),
                    ScreenReaderMode.LABELS to
                        stringResource(R.string.accessibility_talkback_mode_labels),
                    ScreenReaderMode.EXPLORE to
                        stringResource(R.string.accessibility_talkback_mode_explore),
                    ScreenReaderMode.PASSTHROUGH to
                        stringResource(R.string.accessibility_talkback_mode_gestures),
                ),
                selected = settings.screenReaderMode,
            ) { scope.launch { repository.setScreenReaderMode(it) } }
        }
        if (settings.screenReaderMode == ScreenReaderMode.PASSTHROUGH) {
            item {
                val granted = rememberGrantState(KeyboardPassthrough::isServiceEnabled)
                // Disclosure before the accessibility screen: Play scrutinises
                // this API harder than any permission, and the system's own
                // warning there describes powers this service never asks for.
                val accessibility = rememberDisclosedSpecialAccess(SpecialAccess.ACCESSIBILITY)
                NavRow(
                    if (granted) {
                        stringResource(R.string.accessibility_passthrough_service_title)
                    } else {
                        stringResource(R.string.accessibility_passthrough_service_title_required)
                    },
                    if (granted) {
                        stringResource(R.string.accessibility_passthrough_service_on)
                    } else {
                        stringResource(
                            R.string.accessibility_passthrough_service_off,
                            stringResource(R.string.passthrough_service_label),
                        )
                    },
                ) { accessibility() }
            }
        }
    }

    when (settings.screenReaderMode) {
        ScreenReaderMode.EXPLORE ->
            CaptionText(stringResource(R.string.accessibility_explore_caption))
        ScreenReaderMode.PASSTHROUGH ->
            CaptionText(stringResource(R.string.accessibility_passthrough_caption))
        else -> Unit
    }

    SettingsGroup(stringResource(R.string.accessibility_touch_title)) {
        item {
            val offLabel = stringResource(CommonR.string.common_off)
            SliderSetting(
                title = stringResource(R.string.accessibility_debounce_title),
                subtitle = if (settings.keyDebounceMs == 0) {
                    stringResource(R.string.accessibility_debounce_subtitle_off)
                } else {
                    stringResource(R.string.accessibility_debounce_subtitle_on)
                },
                value = settings.keyDebounceMs.toFloat(),
                range = 0f..500f,
                display = { if (it.toInt() == 0) offLabel else "${it.toInt()} ms" },
                info = stringResource(R.string.accessibility_debounce_info),
            ) { scope.launch { repository.setKeyDebounceMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                title = stringResource(R.string.accessibility_long_press_title),
                subtitle = stringResource(R.string.accessibility_long_press_subtitle),
                value = settings.longPressDelayMs.toFloat(),
                range = 150f..800f,
                display = { "${it.toInt()} ms" },
                info = stringResource(R.string.accessibility_long_press_info),
            ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
        }
        item {
            NavRow(
                stringResource(R.string.accessibility_key_size_title),
                stringResource(R.string.accessibility_key_size_subtitle),
                null,
                onClick = onOpenLayout,
            )
        }
        item {
            NavRow(
                stringResource(R.string.accessibility_haptics_title),
                stringResource(R.string.accessibility_haptics_subtitle),
                null,
                onClick = onOpenKeyPress,
            )
        }
    }
}
