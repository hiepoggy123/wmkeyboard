package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker's hit testing, which runs in the pointer loop and decides what a
 * lift commits. Worth pinning directly: on a device the only way to notice it is
 * wrong is that a swipe types the word next to the one under your finger.
 */
class GlidePickerStateTest {

    private fun opened(words: List<String>): GlidePickerState = GlidePickerState().apply {
        open(words, x = 100f, y = 200f)
        // Three 60x40 targets in a row, as the layout would report them.
        words.indices.forEach { i ->
            place(i, Rect(left = i * 70f, top = 300f, right = i * 70f + 60f, bottom = 340f))
        }
    }

    @Test
    fun `a lift outside every target picks nothing`() {
        val picker = opened(listOf("good", "god", "food"))
        picker.hover = picker.targetAt(500f, 500f)
        assertEquals(-1, picker.hover)
        assertNull("lifting away from the picker must commit the decoder's own choice", picker.picked())
    }

    @Test
    fun `the finger picks the target it is over`() {
        val picker = opened(listOf("good", "god", "food"))
        picker.hover = picker.targetAt(80f, 320f)
        assertEquals("god", picker.picked())
        picker.hover = picker.targetAt(150f, 320f)
        assertEquals("food", picker.picked())
    }

    @Test
    fun `the gap between targets picks nothing`() {
        // The targets are spaced, and the space between them is not a near-miss
        // to be rounded into a choice — a finger there has not chosen.
        val picker = opened(listOf("good", "god", "food"))
        assertEquals(-1, picker.targetAt(65f, 320f))
    }

    @Test
    fun `only the words on offer can be picked`() {
        // More candidates than targets: the picker takes the leading few and the
        // rest stay on the suggestion strip, so a stale rect cannot resolve to a
        // word that was never shown.
        val picker = opened(listOf("a", "b", "c", "d", "e"))
        assertEquals(GlidePickerState.MAX_TARGETS, picker.words.size)
        assertEquals(-1, picker.targetAt(3 * 70f + 10f, 320f))
    }

    @Test
    fun `closing forgets the stroke`() {
        val picker = opened(listOf("good", "god"))
        picker.hover = picker.targetAt(20f, 320f)
        assertTrue(picker.offered)
        picker.close()
        assertTrue(picker.words.isEmpty())
        assertNull(picker.picked())
        // `offered` is what stops one stroke opening the picker twice; it has to
        // clear, or the next stroke could never open it at all.
        assertFalse(picker.offered)
        assertEquals(-1, picker.targetAt(20f, 320f))
    }

    @Test
    fun `reopening drops the previous stroke's rectangles`() {
        // Rects outlive the layout pass that wrote them, so a picker reopened
        // before the new targets land must not hit-test against the old ones.
        val picker = opened(listOf("good", "god"))
        picker.open(listOf("food", "fool"), x = 10f, y = 20f)
        assertEquals(-1, picker.targetAt(20f, 320f))
    }
}
