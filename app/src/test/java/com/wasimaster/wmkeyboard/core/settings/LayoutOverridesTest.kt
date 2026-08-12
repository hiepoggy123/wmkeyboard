package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.layout.LayoutAppearance
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The active layout's own label size, laid over the settings the rest of the
 * board uses.
 *
 * The one thing worth a test of its own is that it *multiplies*. Every other
 * overlay in the chain replaces, and a layout that replaced would throw away an
 * accessibility font size the moment the user imported it.
 */
class LayoutOverridesTest {

    @Test
    fun `a layout with no appearance changes nothing at all`() {
        val settings = KeyboardSettings(fontScale = 1.4f)
        // Same instance, not merely equal: the caller remembers on the result,
        // and a fresh copy per frame would stop every key skipping.
        assertSame(settings, settings.applyLayoutAppearance(null))
        assertSame(settings, settings.applyLayoutAppearance(LayoutAppearance(fontId = "custom")))
    }

    @Test
    fun `the layout's size multiplies the user's rather than replacing it`() {
        val settings = KeyboardSettings(fontScale = 1.5f)
        val shrunk = settings.applyLayoutAppearance(LayoutAppearance(fontScale = 0.8f))
        assertEquals(1.2f, shrunk.fontScale, 0.0001f)
    }

    @Test
    fun `an out-of-range multiplier is clamped`() {
        val settings = KeyboardSettings(fontScale = 1f)
        assertEquals(2f, settings.applyLayoutAppearance(LayoutAppearance(fontScale = 8f)).fontScale, 0f)
        assertEquals(0.5f, settings.applyLayoutAppearance(LayoutAppearance(fontScale = -3f)).fontScale, 0f)
    }

    /**
     * Ordering: the layout speaks last, after the theme and after the
     * screen-variant resolve, and it still leaves both of their answers in
     * place — because it scales what they decided instead of overwriting it.
     */
    @Test
    fun `the layout scales what the theme and the screen variant settled on`() {
        val theme = ThemeSpec(id = "big", name = "Big", fontScale = 1.5f, keyHeightDp = 60)
        val resolved = KeyboardSettings()
            .applyThemeOverrides(theme)
            .resolvedFor(ScreenVariant.PORTRAIT)
            .applyLayoutAppearance(LayoutAppearance(fontScale = 0.5f))
        assertEquals(0.75f, resolved.fontScale, 0.0001f)
        assertEquals(60, resolved.keyHeightDp)
    }
}
