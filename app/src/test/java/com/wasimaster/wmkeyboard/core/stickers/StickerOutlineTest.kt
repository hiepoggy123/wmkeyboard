package com.wasimaster.wmkeyboard.core.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StickerOutline.stampCount] is the whole cost model of the border: each
 * stamp is one full-canvas blit, so this is what keeps the slider honest.
 */
class StickerOutlineTest {

    @Test
    fun `stamp count grows with the border width`() {
        val counts = listOf(1f, 4f, 12f, 24f).map { StickerOutline.stampCount(it) }
        assertEquals(counts.sorted(), counts)
    }

    @Test
    fun `stamp count stays inside its bounds`() {
        assertEquals(12, StickerOutline.stampCount(0f))
        assertEquals(12, StickerOutline.stampCount(1f))
        assertEquals(104, StickerOutline.stampCount(1000f))
    }

    @Test
    fun `stamps sit no more than one and a half pixels apart`() {
        for (width in listOf(2f, 6f, 12f, 20f, 24f)) {
            val stamps = StickerOutline.stampCount(width)
            val gap = 2.0 * Math.PI * width / stamps
            assertTrue("gap $gap at width $width", gap <= 1.5)
        }
    }

    @Test
    fun `a border under half a pixel is not drawn`() {
        assertTrue(OutlineSpec(widthPx = 1f).visible)
        assertTrue(!OutlineSpec(widthPx = 0f).visible)
        assertTrue(!OutlineSpec(widthPx = 0.4f).visible)
    }
}
