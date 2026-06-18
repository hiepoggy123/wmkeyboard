package com.wasimaster.wmkeyboard.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
    val readableFontId = KeyboardFonts.googleId(READABLE_FONT)

    SettingsGroup("Vision") {
        item {
            ChoiceSetting(
                title = "Colour vision",
                subtitle = "Adjust the palette for colour blindness",
                info = "Shifts the keyboard's colours so hues that would otherwise look " +
                    "identical stay apart. Pick the type that matches your vision: " +
                    "deuteranopia and protanopia are the two red-green types (deuteranopia " +
                    "is by far the most common), tritanopia is the blue-yellow type. " +
                    "Grayscale removes colour entirely.\n\n" +
                    "This corrects rather than simulates — it does not show you what a " +
                    "colour-blind person sees, it makes the keyboard easier to read if you " +
                    "are one.",
                options = listOf(
                    ColorVisionFilter.NONE to "Off",
                    ColorVisionFilter.DEUTERANOPIA to "Deutan",
                    ColorVisionFilter.PROTANOPIA to "Protan",
                    ColorVisionFilter.TRITANOPIA to "Tritan",
                    ColorVisionFilter.GRAYSCALE to "Grey",
                ),
                selected = settings.colorVisionFilter,
            ) { scope.launch { repository.setColorVisionFilter(it) } }
        }
        item {
            ToggleSetting(
                "High contrast keys",
                "Maximum-contrast labels on a plain black or white board",
                settings.highContrastKeys,
                info = "Replaces the theme's key and board colours with a flat high-contrast " +
                    "set and forces every label to pure black or white. Your theme's accent " +
                    "colour is kept, so shift and active tools still stand out.\n\n" +
                    "This overrides the selected theme's colours while it is on.",
            ) { scope.launch { repository.setHighContrastKeys(it) } }
        }
        item {
            ToggleSetting(
                "Key outlines",
                "Draw a border around every key",
                settings.keyOutlines,
                info = "Outlines make key edges readable even when the key fill and the board " +
                    "are close in brightness — useful with translucent themes or a background " +
                    "image. Themes that already define their own key border keep it.",
            ) { scope.launch { repository.setKeyOutlines(it) } }
        }
        item {
            ToggleSetting(
                "Bold key labels",
                "Heavier letters on the keys",
                settings.boldKeyLabels,
                info = "Thickens the strokes of key labels. Pairs well with high contrast on " +
                    "small or dim screens.",
            ) { scope.launch { repository.setBoldKeyLabels(it) } }
        }
        item {
            ToggleSetting(
                "Readable font",
                "Use $READABLE_FONT on the keyboard",
                settings.keyFontId == readableFontId,
                info = "$READABLE_FONT was designed by the Braille Institute for low vision " +
                    "and reading difficulty: characters that normally look alike — capital I, " +
                    "lowercase l and the digit 1; capital O and zero — are drawn distinctly.\n\n" +
                    "This is a shortcut for the keyboard font setting; turning it off restores " +
                    "the system font. Pick any other face under Appearance › Keyboard font.",
            ) { on ->
                scope.launch {
                    repository.setKeyFontId(if (on) readableFontId else KeyboardFonts.DEFAULT_ID)
                }
            }
        }
        item {
            SliderSetting(
                title = "Text size",
                subtitle = "Size of the labels on the keys",
                value = settings.fontScale,
                range = 0.7f..1.5f,
                display = "${(settings.fontScale * 100).toInt()}%",
                info = "Scales key labels only. To make the keys themselves bigger, use " +
                    "Layout & size.",
            ) { scope.launch { repository.setFontScale(it) } }
        }
        item {
            NavRow(
                "Keyboard font",
                "Browse all fonts, or import your own",
                KeyboardFonts.displayName(settings.keyFontId, settings.customFontName),
                onOpenFonts,
            )
        }
    }

    SettingsGroup("Motion") {
        item {
            ToggleSetting(
                "Reduce motion",
                "Remove non-essential animation",
                settings.reduceMotion,
                info = "Turns off the sliding and scaling transitions in the keyboard toolbar " +
                    "and this settings app, for motion sensitivity.\n\n" +
                    "Feedback that tells you something — the key preview bubble, the pressed-key " +
                    "colour, the gesture trail — is deliberately kept, since removing it would " +
                    "make the keyboard harder to use, not easier.",
            ) { scope.launch { repository.setReduceMotion(it) } }
        }
    }

    SettingsGroup("Screen reader") {
        item {
            ChoiceSetting(
                title = "TalkBack support",
                subtitle = "How keys are announced",
                info = "Off: keys carry no spoken label — the keyboard is effectively invisible " +
                    "to a screen reader.\n\n" +
                    "Labels: every key gets a spoken name, including punctuation (\"period\", " +
                    "\"at sign\"), while typing still works by direct touch. Best if you use " +
                    "a screen reader occasionally, or use switch access.\n\n" +
                    "Explore by touch: the standard screen-reader keyboard behaviour — slide " +
                    "your finger to hear each key, then activate to type it. Only changes how " +
                    "keys behave while TalkBack is actually running; with it off the keyboard " +
                    "types normally either way.",
                options = listOf(
                    ScreenReaderMode.OFF to "Off",
                    ScreenReaderMode.LABELS to "Labels",
                    ScreenReaderMode.EXPLORE to "Explore",
                ),
                selected = settings.screenReaderMode,
            ) { scope.launch { repository.setScreenReaderMode(it) } }
        }
    }

    if (settings.screenReaderMode == ScreenReaderMode.EXPLORE) {
        CaptionText(
            "While TalkBack is running, spacebar swipes and long-press alternates are " +
                "handled by TalkBack's own gestures instead of the keyboard's.",
        )
    }

    SettingsGroup("Touch") {
        item {
            SliderSetting(
                title = "Ignore repeated presses",
                subtitle = if (settings.keyDebounceMs == 0) {
                    "Off — every press registers"
                } else {
                    "A second press of the same key is ignored for this long"
                },
                value = settings.keyDebounceMs.toFloat(),
                range = 0f..500f,
                display = if (settings.keyDebounceMs == 0) "Off" else "${settings.keyDebounceMs} ms",
                info = "For hand tremor or spasticity: after a key registers, a second press " +
                    "landing within this window is discarded, so one intended tap does not " +
                    "become two characters.\n\n" +
                    "Only repeats of the same key are filtered — typing two different keys " +
                    "quickly is never blocked. Raise it until doubled letters stop; too high " +
                    "and deliberate double letters (\"ll\", \"ee\") get eaten.",
            ) { scope.launch { repository.setKeyDebounceMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                title = "Long-press delay",
                subtitle = "How long to hold a key before its alternates open",
                value = settings.longPressDelayMs.toFloat(),
                range = 150f..800f,
                display = "${settings.longPressDelayMs} ms",
                info = "Raise this if you trigger long-press popups by accident; lower it if " +
                    "holding a key feels sluggish.",
            ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
        }
        item {
            NavRow(
                "Key size & spacing",
                "Taller keys and wider gaps are easier to hit accurately",
                null,
                onOpenLayout,
            )
        }
        item {
            NavRow(
                "Haptics & sound",
                "Confirm each key press by feel or by ear",
                null,
                onOpenKeyPress,
            )
        }
    }
}
