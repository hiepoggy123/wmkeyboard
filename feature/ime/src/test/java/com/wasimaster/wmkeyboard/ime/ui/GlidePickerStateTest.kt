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
 * wrong is that a swipe types the word next to the one under your finger — or,
 * since the cancel zone, types a word when it should have typed nothing.
 */
class GlidePickerStateTest {

    private companion object {
        const val ANCHOR_Y = 400f
        const val CANCEL_BELOW = 50f
    }

    private fun opened(words: List<String>, limit: Int = words.size): GlidePickerState =
        GlidePickerState().apply {
            open(words, limit, x = 100f, y = ANCHOR_Y)
            // 60x40 targets in a row above the anchor, as the layout would report them.
            this.words.indices.forEach { i ->
                place(i, Rect(left = i * 70f, top = 300f, right = i * 70f + 60f, bottom = 340f))
            }
        }

    @Test
    fun `a lift outside every target picks nothing`() {
        val picker = opened(listOf("good", "god", "food"))
        picker.track(500f, 320f, CANCEL_BELOW)
        assertEquals(-1, picker.hover)
        assertNull("lifting away from the picker must commit the decoder's own choice", picker.picked())
        assertEquals(GlideVerdict.Leader, picker.verdict())
    }

    @Test
    fun `the finger picks the target it is over`() {
        val picker = opened(listOf("good", "god", "food"))
        picker.track(80f, 320f, CANCEL_BELOW)
        assertEquals("god", picker.picked())
        assertEquals(GlideVerdict.Word("god"), picker.verdict())
        picker.track(150f, 320f, CANCEL_BELOW)
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
        // More candidates than the user asked for: the picker takes the leading
        // few and the rest stay on the suggestion strip, so a stale rect cannot
        // resolve to a word that was never shown.
        val picker = opened(listOf("a", "b", "c", "d", "e"), limit = 3)
        assertEquals(3, picker.words.size)
        assertEquals(-1, picker.targetAt(3 * 70f + 10f, 320f))
    }

    @Test
    fun `the count on offer is clamped to the setting's range`() {
        assertEquals(
            GlidePickerState.MAX_TARGETS,
            opened(listOf("a", "b", "c", "d", "e", "f", "g"), limit = 9).words.size,
        )
        assertEquals(GlidePickerState.MIN_TARGETS, opened(listOf("a", "b", "c"), limit = 1).words.size)
        assertEquals(5, GlidePickerState.MAX_TARGETS)
    }

    @Test
    fun `arriving on a target reports once`() {
        val picker = opened(listOf("good", "god", "food"))
        assertTrue("entering a target is the haptic tick", picker.track(80f, 320f, CANCEL_BELOW))
        assertFalse("moving within it is not", picker.track(90f, 325f, CANCEL_BELOW))
        assertTrue("crossing to the next one is", picker.track(150f, 320f, CANCEL_BELOW))
        assertFalse("leaving is not", picker.track(500f, 320f, CANCEL_BELOW))
        assertTrue("and coming back is again", picker.track(150f, 320f, CANCEL_BELOW))
    }

    @Test
    fun `dropping below the anchor cancels and clears the hover`() {
        val picker = opened(listOf("good", "god", "food"))
        picker.track(80f, 320f, CANCEL_BELOW)
        assertEquals(1, picker.hover)
        assertFalse(picker.track(80f, ANCHOR_Y + CANCEL_BELOW + 1f, CANCEL_BELOW))
        assertTrue(picker.cancelling)
        assertEquals(-1, picker.hover)
        assertEquals(GlideVerdict.Cancel, picker.verdict())
    }

    @Test
    fun `the cancel line is strict`() {
        val picker = opened(listOf("good", "god"))
        picker.track(80f, ANCHOR_Y + CANCEL_BELOW, CANCEL_BELOW)
        assertFalse("exactly on the line is still choosing", picker.cancelling)
    }

    @Test
    fun `sliding back up clears the cancel`() {
        val picker = opened(listOf("good", "god", "food"))
        picker.track(80f, ANCHOR_Y + CANCEL_BELOW + 20f, CANCEL_BELOW)
        assertTrue(picker.cancelling)
        assertTrue("arriving on a target from the cancel zone ticks", picker.track(80f, 320f, CANCEL_BELOW))
        assertFalse(picker.cancelling)
        assertEquals(GlideVerdict.Word("god"), picker.verdict())
    }

    @Test
    fun `below the cancel line no rectangle counts`() {
        // A target laid out low enough to straddle the line must not be picked
        // from underneath it: the zone wins, whatever the geometry.
        val picker = opened(listOf("good", "god"))
        picker.place(0, Rect(0f, ANCHOR_Y, 60f, ANCHOR_Y + 200f))
        picker.track(30f, ANCHOR_Y + CANCEL_BELOW + 10f, CANCEL_BELOW)
        assertEquals(-1, picker.hover)
        assertEquals(GlideVerdict.Cancel, picker.verdict())
    }

    @Test
    fun `a picker that never opened answers leader`() {
        val picker = GlidePickerState()
        assertFalse(picker.isOpen)
        assertEquals(GlideVerdict.Leader, picker.verdict())
    }

    @Test
    fun `closing forgets the stroke`() {
        val picker = opened(listOf("good", "god"))
        picker.track(20f, 320f, CANCEL_BELOW)
        assertTrue(picker.isOpen)
        picker.close()
        assertFalse(picker.isOpen)
        assertTrue(picker.words.isEmpty())
        assertNull(picker.picked())
        assertFalse(picker.cancelling)
        assertEquals(GlideVerdict.Leader, picker.verdict())
        assertEquals(-1, picker.targetAt(20f, 320f))
    }

    @Test
    fun `reopening drops the previous stroke's rectangles and its cancel`() {
        // Rects outlive the layout pass that wrote them, so a picker reopened
        // before the new targets land must not hit-test against the old ones.
        val picker = opened(listOf("good", "god"))
        picker.track(20f, ANCHOR_Y + 100f, CANCEL_BELOW)
        picker.open(listOf("food", "fool"), limit = 2, x = 10f, y = 20f)
        assertFalse(picker.cancelling)
        assertEquals(-1, picker.targetAt(20f, 320f))
    }
}
