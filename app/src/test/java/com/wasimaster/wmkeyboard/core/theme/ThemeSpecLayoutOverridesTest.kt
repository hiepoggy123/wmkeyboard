package com.wasimaster.wmkeyboard.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The nullable layout/type overrides on [ThemeSpec]: they must survive a round
 * trip, decode as null from themes written before the feature, and come
 * through a reseed untouched — a reseed replaces colours, not geometry.
 */
class ThemeSpecLayoutOverridesTest {

    private fun overridden() = ThemeSpec(
        id = "custom_1",
        name = "Wide",
        toolWidthDp = 56,
        toolbarHeightDp = 52,
        keyHeightDp = 54,
        keyGapScale = 0.6f,
        sidePadScale = 0.1f,
        fontScale = 1.1f,
        boldKeyLabels = true,
        hintFontScale = 0.8f,
        gestureTrailWidthDp = 6f,
        gestureTrailOpacity = 0.4f,
    )

    @Test
    fun `layout overrides survive a round trip`() {
        val decoded = ThemeCodec.decode(ThemeCodec.encode(overridden()))
        assertNotNull(decoded)
        assertEquals(56, decoded?.toolWidthDp)
        assertEquals(52, decoded?.toolbarHeightDp)
        assertEquals(54, decoded?.keyHeightDp)
        assertEquals(0.6f, decoded!!.keyGapScale!!, 0f)
        assertEquals(0.1f, decoded.sidePadScale!!, 0f)
        assertEquals(1.1f, decoded.fontScale!!, 0f)
        assertEquals(true, decoded.boldKeyLabels)
        assertEquals(0.8f, decoded.hintFontScale!!, 0f)
        assertEquals(6f, decoded.gestureTrailWidthDp!!, 0f)
        assertEquals(0.4f, decoded.gestureTrailOpacity!!, 0f)
    }

    @Test
    fun `a theme written before this feature decodes with no overrides`() {
        val old = """
            {"id":"custom_9","name":"Old","dark":true,"boardBackground":-15329508}
        """.trimIndent()
        val decoded = ThemeCodec.decode(old)
        assertNotNull(decoded)
        assertNull(decoded?.toolWidthDp)
        assertNull(decoded?.toolbarHeightDp)
        assertNull(decoded?.keyHeightDp)
        assertNull(decoded?.keyGapScale)
        assertNull(decoded?.sidePadScale)
        assertNull(decoded?.fontScale)
        assertNull(decoded?.boldKeyLabels)
        assertNull(decoded?.hintFontScale)
        assertNull(decoded?.gestureTrailWidthDp)
        assertNull(decoded?.gestureTrailOpacity)
    }

    @Test
    fun `a theme from a future build with an unknown key still decodes`() {
        val future = """
            {"id":"custom_2","name":"Future","dark":true,"someFutureOverride":3}
        """.trimIndent()
        val decoded = ThemeCodec.decode(future)
        assertNotNull(decoded)
        assertEquals("Future", decoded?.name)
    }

    @Test
    fun `reseeding keeps the layout overrides`() {
        // reseeded() is written copy-style precisely so new fields survive; if
        // this fails, someone rewrote it as a field list.
        val reseeded = overridden().reseeded(0xFF4C8DF6, dark = true)
        assertEquals(56, reseeded.toolWidthDp)
        assertEquals(52, reseeded.toolbarHeightDp)
        assertEquals(54, reseeded.keyHeightDp)
        assertEquals(0.6f, reseeded.keyGapScale!!, 0f)
        assertEquals(0.1f, reseeded.sidePadScale!!, 0f)
        assertEquals(1.1f, reseeded.fontScale!!, 0f)
        assertEquals(true, reseeded.boldKeyLabels)
        assertEquals(0.8f, reseeded.hintFontScale!!, 0f)
        assertEquals(6f, reseeded.gestureTrailWidthDp!!, 0f)
        assertEquals(0.4f, reseeded.gestureTrailOpacity!!, 0f)
    }
}
