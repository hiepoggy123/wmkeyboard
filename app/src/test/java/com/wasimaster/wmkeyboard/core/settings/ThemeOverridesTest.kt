package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The theme layout overlay: which spec is active, how its overrides land on
 * [KeyboardSettings], and the ordering contract with [resolvedFor] — the theme
 * reshapes the base look, an explicit per-screen sizing override still wins.
 */
class ThemeOverridesTest {

    private val wide = ThemeSpec(
        id = "wide",
        name = "Wide",
        toolWidthDp = 56,
        toolbarHeightDp = 52,
        keyHeightDp = 60,
        fontScale = 1.2f,
        boldKeyLabels = true,
        sidePadScale = 0.1f,
        gestureTrailWidthDp = 4f,
    )

    // ---- spec selection ----------------------------------------------

    @Test
    fun `the selected custom theme is the active spec`() {
        val settings = KeyboardSettings(keyboardThemeId = "wide", customThemes = listOf(wide))
        assertEquals("wide", settings.effectiveThemeId(darkSlot = false))
        assertSame(wide, settings.activeThemeSpec(darkSlot = false))
    }

    @Test
    fun `the default theme has no spec to override with`() {
        val settings = KeyboardSettings(keyboardThemeId = DEFAULT_THEME_ID)
        assertNull(settings.activeThemeSpec(darkSlot = false))
    }

    @Test
    fun `auto-theme picks the slot's id, not the selected one`() {
        val settings = KeyboardSettings(
            keyboardThemeId = "wide",
            customThemes = listOf(wide),
            autoTheme = AutoThemeSettings(
                enabled = true,
                lightThemeId = DEFAULT_THEME_ID,
                darkThemeId = "wide",
            ),
        )
        assertEquals(DEFAULT_THEME_ID, settings.effectiveThemeId(darkSlot = false))
        assertNull(settings.activeThemeSpec(darkSlot = false))
        assertSame(wide, settings.activeThemeSpec(darkSlot = true))
    }

    // ---- the overlay --------------------------------------------------

    @Test
    fun `a set override beats the global, an unset one falls through`() {
        val base = KeyboardSettings(keyHeightDp = 48, keyGapScale = 1.5f, fontScale = 1f)
        val overlaid = base.applyThemeOverrides(wide)
        assertEquals(60, overlaid.keyHeightDp)
        assertEquals(1.2f, overlaid.fontScale, 0f)
        assertEquals(56, overlaid.toolbarBehavior.toolWidthDp)
        assertEquals(0.1f, overlaid.layoutBehavior.sidePadScale, 0f)
        assertEquals(4f, overlaid.gesture.trailWidthDp, 0f)
        // keyGapScale is null on the spec: the user's global stands.
        assertEquals(1.5f, overlaid.keyGapScale, 0f)
        // Trail opacity unset: untouched.
        assertEquals(base.gesture.trailOpacity, overlaid.gesture.trailOpacity, 0f)
    }

    @Test
    fun `a theme with no overrides returns the same settings instance`() {
        // The caller remembers on the result; a fresh-but-equal copy per frame
        // would defeat the per-keystroke skip.
        val base = KeyboardSettings()
        assertSame(base, base.applyThemeOverrides(ThemeSpec(id = "plain", name = "Plain")))
        assertSame(base, base.applyThemeOverrides(null))
    }

    @Test
    fun `theme selection fields are never touched`() {
        val base = KeyboardSettings(keyboardThemeId = "wide", customThemes = listOf(wide))
        val overlaid = base.applyThemeOverrides(wide)
        assertEquals("wide", overlaid.keyboardThemeId)
        assertSame(base.customThemes, overlaid.customThemes)
        assertSame(base.autoTheme, overlaid.autoTheme)
    }

    // ---- ordering with resolvedFor ------------------------------------

    @Test
    fun `a per-screen sizing override beats the theme`() {
        val base = KeyboardSettings(
            keyHeightDp = 48,
            sizingOverrides = mapOf(
                ScreenVariant.LANDSCAPE to SizingOverride(keyHeightDp = 40, fontScale = 0.9f),
            ),
        )
        val resolved = base.applyThemeOverrides(wide).resolvedFor(ScreenVariant.LANDSCAPE)
        assertEquals(40, resolved.keyHeightDp)
        assertEquals(0.9f, resolved.fontScale, 0f)
        // Fields the variant does not size still carry the theme's values.
        assertEquals(52, resolved.toolbarHeightDp)
        assertEquals(56, resolved.toolbarBehavior.toolWidthDp)
    }

    @Test
    fun `on a screen with no sizing override the theme's sizes stand`() {
        val base = KeyboardSettings(keyHeightDp = 48)
        val resolved = base.applyThemeOverrides(wide).resolvedFor(ScreenVariant.PORTRAIT)
        assertEquals(60, resolved.keyHeightDp)
        assertEquals(1.2f, resolved.fontScale, 0f)
    }

    @Test
    fun `the variant's keyboard scale multiplies the theme's key height`() {
        val base = KeyboardSettings(
            keyHeightDp = 48,
            sizingOverrides = mapOf(
                ScreenVariant.LANDSCAPE to SizingOverride(keyboardScale = 0.5f),
            ),
        )
        val resolved = base.applyThemeOverrides(wide).resolvedFor(ScreenVariant.LANDSCAPE)
        assertEquals(30, resolved.keyHeightDp)
    }
}
