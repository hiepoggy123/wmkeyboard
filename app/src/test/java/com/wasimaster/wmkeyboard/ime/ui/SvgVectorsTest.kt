package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.icons.SvgDoc
import com.wasimaster.wmkeyboard.core.icons.SvgPath
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The intrinsic size an icon reports is a layout guard, not a drawing detail:
 * a call site that passes no size modifier measures at exactly it, so a pack
 * from a stranger must not be able to name its own.
 */
class SvgVectorsTest {

    private fun doc(width: Float, height: Float) = SvgDoc(
        viewportWidth = width,
        viewportHeight = height,
        paths = listOf(SvgPath(pathData = "M0 0h1v1H0Z")),
        monochrome = true,
    )

    @Test
    fun `a 24 by 24 icon is unchanged`() {
        val vector = doc(24f, 24f).toImageVector("slot")
        assertEquals(24f, vector.defaultWidth.value, 0.01f)
        assertEquals(24f, vector.defaultHeight.value, 0.01f)
    }

    @Test
    fun `a 512 icon is clamped to the box`() {
        val vector = doc(512f, 512f).toImageVector("slot")
        assertEquals(24f, vector.defaultWidth.value, 0.01f)
        assertEquals(24f, vector.defaultHeight.value, 0.01f)
        // The coordinate space is untouched — the paths are written in it.
        assertEquals(512f, vector.viewportWidth, 0.01f)
    }

    @Test
    fun `a wide icon keeps its aspect ratio inside the box`() {
        val vector = doc(48f, 24f).toImageVector("slot")
        assertEquals(24f, vector.defaultWidth.value, 0.01f)
        assertEquals(12f, vector.defaultHeight.value, 0.01f)
    }

    @Test
    fun `a tiny icon is scaled up to the box rather than left microscopic`() {
        val vector = doc(8f, 8f).toImageVector("slot")
        assertEquals(24f, vector.defaultWidth.value, 0.01f)
    }

    @Test
    fun `a nonsense viewport falls back to the box`() {
        for (bad in listOf(0f to 24f, -5f to 24f, Float.NaN to 24f, Float.POSITIVE_INFINITY to 24f)) {
            val vector = doc(bad.first, bad.second).toImageVector("slot")
            assertEquals(bad.toString(), 24f, vector.defaultWidth.value, 0.01f)
            assertEquals(bad.toString(), 24f, vector.defaultHeight.value, 0.01f)
        }
    }
}
