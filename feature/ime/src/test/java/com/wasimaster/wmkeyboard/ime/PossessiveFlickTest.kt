package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `'s` flick: a short swipe from the apostrophe key to `s`, drawn right after a
 * glided word, that appends the possessive to it.
 *
 * The shape test is all this file can check, and it is the part that has to be
 * right: the flick and an ordinary swipe start from the same place and can end on
 * the same key, so the only thing separating them is that the flick goes straight
 * there. Too loose and it eats real words; too tight and a thumb's natural arc
 * misses.
 */
class PossessiveFlickTest {

    private val keyWidth = 60f

    /** Roughly a QWERTY bottom-row comma and a home-row s, in pixels. */
    private val comma = KeyCenter('\'', x = 570f, y = 180f)
    private val letterS = KeyCenter('s', x = 150f, y = 120f)

    private fun stroke(vararg points: Pair<Float, Float>): List<GesturePoint> =
        points.mapIndexed { i, (x, y) -> GesturePoint(x, y, i * 16L) }

    @Test
    fun `a straight swipe between the two keys is the flick`() {
        val points = stroke(
            comma.x to comma.y,
            360f to 150f,
            letterS.x to letterS.y,
        )
        assertTrue(possessiveFlick(points, comma, letterS, keyWidth))
    }

    /** A thumb draws an arc, not a line. Within the detour budget it still counts. */
    @Test
    fun `a gently curved swipe is still the flick`() {
        val points = stroke(
            comma.x to comma.y,
            430f to 100f,
            300f to 60f,
            letterS.x to letterS.y,
        )
        assertTrue(possessiveFlick(points, comma, letterS, keyWidth))
    }

    /**
     * The load-bearing case: a real word drawn from the same key to the same key.
     * Its path wanders far enough that the travelled length gives it away, so it
     * falls through and decodes as a word.
     */
    @Test
    fun `a word drawn between the same two keys is not the flick`() {
        val points = stroke(
            comma.x to comma.y,
            520f to 60f,
            300f to 180f,
            480f to 120f,
            240f to 60f,
            letterS.x to letterS.y,
        )
        assertFalse(possessiveFlick(points, comma, letterS, keyWidth))
    }

    @Test
    fun `a swipe that starts somewhere else is not the flick`() {
        val points = stroke(300f to 120f, 220f to 120f, letterS.x to letterS.y)
        assertFalse(possessiveFlick(points, comma, letterS, keyWidth))
    }

    @Test
    fun `a swipe that ends somewhere else is not the flick`() {
        val points = stroke(comma.x to comma.y, 400f to 150f, 330f to 120f)
        assertFalse(possessiveFlick(points, comma, letterS, keyWidth))
    }

    /** Degenerate input from a synthetic path or a zero-width grid. */
    @Test
    fun `nonsense input is not the flick`() {
        val points = stroke(comma.x to comma.y, letterS.x to letterS.y)
        assertFalse(possessiveFlick(points, comma, letterS, keyWidthPx = 0f))
        assertFalse(possessiveFlick(emptyList(), comma, letterS, keyWidth))
        assertFalse(possessiveFlick(points, comma, comma, keyWidth))
    }

    /**
     * Both ends are measured in key widths, so the same stroke on a smaller
     * keyboard is judged the same way. Here the start sits one key width out,
     * which is outside the reach at any size.
     */
    @Test
    fun `the reach scales with the key width`() {
        val offStart = stroke(
            (comma.x + keyWidth) to comma.y,
            360f to 150f,
            letterS.x to letterS.y,
        )
        assertFalse(possessiveFlick(offStart, comma, letterS, keyWidth))
        val nearStart = stroke(
            (comma.x + keyWidth * 0.4f) to comma.y,
            360f to 150f,
            letterS.x to letterS.y,
        )
        assertTrue(possessiveFlick(nearStart, comma, letterS, keyWidth))
    }
}
