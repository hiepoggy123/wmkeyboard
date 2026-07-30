package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The preview bubbles' timing, which used to live in two LaunchedEffects per key
 * and now lives in one place so a single coroutine can drive every bubble.
 *
 * Worth testing on its own: the first attempt at this shipped with the whole
 * board sharing one bubble, so a second finger stole the first one's, and the
 * arithmetic that decides when a bubble goes is invisible until it is wrong.
 */
class KeyPreviewStateTest {

    private companion object {
        const val MIN = 100
        const val MAX = 1000
    }

    private var clock = 0L
    private val state = KeyPreviewState { clock }

    private fun preview(token: Any, label: String) =
        KeyPreview(token, label, Offset.Zero, IntSize(10, 10))

    private fun labels() = state.shown.map { it.label }

    @Test
    fun `two keys held at once show two bubbles`() {
        state.press(preview("a", "a"))
        clock = 10
        state.press(preview("b", "b"))
        assertEquals(listOf("a", "b"), labels())
    }

    @Test
    fun `lifting one finger leaves the other's bubble alone`() {
        state.press(preview("a", "a"))
        state.press(preview("b", "b"))
        clock = 500
        state.release("a")
        // Past a's minimum already, so a goes and b — still held — stays.
        state.expire(clock, MIN, MAX)
        assertEquals(listOf("b"), labels())
    }

    /** A fast tap still has to show a readable bubble, not a single-frame flash. */
    @Test
    fun `a tap shorter than the minimum holds the bubble open`() {
        state.press(preview("a", "a"))
        clock = 20
        state.release("a")
        val due = state.expire(clock, MIN, MAX)
        assertEquals(listOf("a"), labels())
        assertEquals(100L, due)

        clock = 100
        state.expire(clock, MIN, MAX)
        assertEquals(emptyList<String>(), labels())
    }

    @Test
    fun `a release past the minimum drops the bubble at once`() {
        state.press(preview("a", "a"))
        clock = 300
        state.release("a")
        state.expire(clock, MIN, MAX)
        assertEquals(emptyList<String>(), labels())
    }

    /**
     * The cap exists for a release that arrives late or never — a key disposed
     * mid-press, or a commit stalling the release. Without it the bubble strands.
     */
    @Test
    fun `a bubble never released still goes at the maximum`() {
        state.press(preview("a", "a"))
        clock = 999
        assertEquals(1000L, state.expire(clock, MIN, MAX))
        assertEquals(listOf("a"), labels())

        clock = 1000
        assertNull(state.expire(clock, MIN, MAX))
        assertEquals(emptyList<String>(), labels())
    }

    /** A late release must not push the bubble past the cap it already had. */
    @Test
    fun `a release later than the cap does not extend the bubble`() {
        state.press(preview("a", "a"))
        clock = 5000
        state.release("a")
        state.expire(clock, MIN, MAX)
        assertEquals(emptyList<String>(), labels())
    }

    /** Only the first release counts, or a second would push the floor out. */
    @Test
    fun `a repeated release does not restart the minimum`() {
        state.press(preview("a", "a"))
        clock = 20
        state.release("a")
        clock = 60
        state.release("a")
        assertEquals(100L, state.expire(clock, MIN, MAX))
    }

    /** The long-press alternates replace the bubble outright. */
    @Test
    fun `cancel drops one key's bubble immediately and leaves the rest`() {
        state.press(preview("a", "a"))
        state.press(preview("b", "b"))
        state.cancel("a")
        assertEquals(listOf("b"), labels())
    }

    @Test
    fun `the nearest deadline is the one reported`() {
        state.press(preview("a", "a"))
        clock = 400
        state.press(preview("b", "b"))
        clock = 410
        state.release("b")
        // Two different clocks: a is held, so only its cap at 1000 applies; b was
        // let go 10ms after appearing, so it owes the rest of its minimum, to 500.
        // The nearer of the two is what the timer needs to be woken for.
        assertEquals(500L, state.expire(clock, MIN, MAX))
        assertEquals(listOf("a", "b"), labels())

        clock = 500
        assertEquals(1000L, state.expire(clock, MIN, MAX))
        assertEquals(listOf("a"), labels())
    }

    /** Pressing the same key again restarts its clock rather than stacking. */
    @Test
    fun `re-pressing a key replaces its own bubble`() {
        state.press(preview("a", "a"))
        clock = 900
        state.press(preview("a", "a2"))
        assertEquals(listOf("a2"), labels())
        clock = 1000
        assertEquals(1900L, state.expire(clock, MIN, MAX))
    }

    @Test
    fun `every press and release bumps the revision the timer waits on`() {
        val start = state.revision
        state.press(preview("a", "a"))
        assertEquals(start + 1, state.revision)
        state.release("a")
        assertEquals(start + 2, state.revision)
        state.cancel("a")
        assertEquals(start + 3, state.revision)
    }
}
