package com.wasimaster.wmkeyboard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [revealScroll]: how far the editor scrolls to keep the caret in sight. */
class CodeRevealTest {

    @Test
    fun `a caret in sight with room to spare does not scroll`() {
        assertNull(revealScroll(scroll = 100, viewport = 400, start = 200f, end = 220f, margin = 20f))
    }

    @Test
    fun `a caret below the frame scrolls it into sight with a line to spare`() {
        assertEquals(160, revealScroll(scroll = 100, viewport = 400, start = 520f, end = 540f, margin = 20f))
    }

    @Test
    fun `a caret above the frame scrolls up to it with a line to spare`() {
        assertEquals(40, revealScroll(scroll = 100, viewport = 400, start = 60f, end = 80f, margin = 20f))
    }

    @Test
    fun `a caret on the last line in sight scrolls by the margin`() {
        assertEquals(120, revealScroll(scroll = 100, viewport = 400, start = 480f, end = 500f, margin = 20f))
    }

    @Test
    fun `the first line never scrolls above the top`() {
        assertNull(revealScroll(scroll = 0, viewport = 400, start = 10f, end = 30f, margin = 20f))
        assertEquals(0, revealScroll(scroll = 50, viewport = 400, start = 10f, end = 30f, margin = 20f))
    }

    @Test
    fun `a frame with no room for the margin keeps what it has`() {
        assertEquals(285, revealScroll(scroll = 0, viewport = 50, start = 300f, end = 320f, margin = 20f))
    }

    @Test
    fun `a line taller than the frame is shown from its top`() {
        assertEquals(300, revealScroll(scroll = 0, viewport = 10, start = 300f, end = 320f, margin = 20f))
        assertNull(revealScroll(scroll = 300, viewport = 10, start = 300f, end = 320f, margin = 20f))
    }

    @Test
    fun `no frame yet means no scroll`() {
        assertNull(revealScroll(scroll = 0, viewport = 0, start = 300f, end = 320f, margin = 20f))
    }
}
