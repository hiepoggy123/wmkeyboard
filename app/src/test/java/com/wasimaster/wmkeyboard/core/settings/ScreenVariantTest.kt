package com.wasimaster.wmkeyboard.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScreenVariantTest {

    @Test fun variantIsChosenFromOrientationAndPosture() {
        assertEquals(ScreenVariant.PORTRAIT, ScreenVariant.of(landscape = false, unfolded = false))
        assertEquals(ScreenVariant.LANDSCAPE, ScreenVariant.of(landscape = true, unfolded = false))
        assertEquals(
            ScreenVariant.PORTRAIT_UNFOLDED,
            ScreenVariant.of(landscape = false, unfolded = true),
        )
        assertEquals(
            ScreenVariant.LANDSCAPE_UNFOLDED,
            ScreenVariant.of(landscape = true, unfolded = true),
        )
    }

    @Test fun portraitResolvesToTheBaseSettingsUntouched() {
        val settings = KeyboardSettings(keyHeightDp = 48)
        assertSame(settings, settings.resolvedFor(ScreenVariant.PORTRAIT))
    }

    /**
     * The service reads one-handed settings straight off its own raw state while
     * the keyboard composable reads them through [resolvedFor] — the two must
     * agree, or the toolbar's one-handed button and its hardware shortcut would
     * dock the keyboard to different sides. They agree because sizing overrides
     * carry nothing about one-handed mode; this pins that, so a new
     * [SizingOverride] field cannot quietly break it.
     */
    @Test fun sizingOverridesNeverTouchOneHandedSettings() {
        val settings = KeyboardSettings(
            oneHandedMode = OneHandedMode.LEFT,
            oneHanded = OneHandedSettings(
                landscape = OneHandedProfile(side = OneHandedSide.RIGHT),
            ),
            sizingOverrides = ScreenVariant.entries.associateWith {
                SizingOverride(
                    keyHeightDp = 30,
                    numberRowHeightDp = 20,
                    bottomPaddingDp = 4,
                    keyboardWidthPercent = 90,
                    fontScale = 1.2f,
                    keyboardAlignment = KeyboardAlignment.RIGHT,
                    keyboardScale = 1.5f,
                )
            },
        )
        for (variant in ScreenVariant.entries) {
            val resolved = settings.resolvedFor(variant)
            assertEquals(variant.name, settings.oneHandedMode, resolved.oneHandedMode)
            assertEquals(variant.name, settings.oneHanded, resolved.oneHanded)
        }
    }

    @Test fun unsetFieldsInheritPortrait() {
        val settings = KeyboardSettings(
            keyHeightDp = 48,
            keyboardWidthPercent = 100,
            sizingOverrides = mapOf(
                ScreenVariant.LANDSCAPE to SizingOverride(keyHeightDp = 36),
            ),
        )
        val landscape = settings.resolvedFor(ScreenVariant.LANDSCAPE)
        assertEquals(36, landscape.keyHeightDp)
        // Only key height was overridden; width still follows portrait.
        assertEquals(100, landscape.keyboardWidthPercent)
    }

    @Test fun variantsDoNotLeakIntoEachOther() {
        val settings = KeyboardSettings(
            keyHeightDp = 48,
            sizingOverrides = mapOf(
                ScreenVariant.LANDSCAPE to SizingOverride(keyHeightDp = 36),
            ),
        )
        assertEquals(48, settings.resolvedFor(ScreenVariant.PORTRAIT_UNFOLDED).keyHeightDp)
        assertEquals(48, settings.resolvedFor(ScreenVariant.LANDSCAPE_UNFOLDED).keyHeightDp)
    }

    @Test fun anEmptyOverrideChangesNothing() {
        val settings = KeyboardSettings(
            keyHeightDp = 48,
            sizingOverrides = mapOf(ScreenVariant.LANDSCAPE to SizingOverride()),
        )
        assertSame(settings, settings.resolvedFor(ScreenVariant.LANDSCAPE))
    }

    @Test fun everyFieldCanBeOverridden() {
        val settings = KeyboardSettings(sizingOverrides = mapOf(
            ScreenVariant.LANDSCAPE to SizingOverride(
                keyHeightDp = 36,
                numberRowHeightDp = 30,
                bottomPaddingDp = 2,
                keyboardWidthPercent = 80,
                fontScale = 0.9f,
                keyboardAlignment = KeyboardAlignment.RIGHT,
            ),
        ))
        val landscape = settings.resolvedFor(ScreenVariant.LANDSCAPE)
        assertEquals(36, landscape.keyHeightDp)
        assertEquals(30, landscape.numberRowHeightDp)
        assertEquals(2, landscape.bottomPaddingDp)
        assertEquals(80, landscape.keyboardWidthPercent)
        assertEquals(0.9f, landscape.fontScale, 0.001f)
        assertEquals(KeyboardAlignment.RIGHT, landscape.keyboardAlignment)
    }
}
