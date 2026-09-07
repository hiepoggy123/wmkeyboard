package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker's clock. The one property a device cannot demonstrate is the
 * progress guarantee: whatever a fired timer decides, the next timeout must
 * be either "none" or in the future, or the pointer loop would spin on a
 * timeout that returns at once.
 */
class GlideDwellTest {

    private companion object {
        const val DWELL = 350L
        const val RADIUS = 12f
    }

    private fun stoppedAt(t: Long) = GlideDwell().apply { reset(100f, 200f, t) }

    @Test
    fun `a close call asks one dwell after the finger stopped`() {
        assertEquals(1_350L, stoppedAt(1_000).deadline(closeCall = true, holdToAsk = true, dwellMs = DWELL))
        assertEquals(1_350L, stoppedAt(1_000).deadline(closeCall = true, holdToAsk = false, dwellMs = DWELL))
    }

    @Test
    fun `a confident stroke asks after two dwells only with hold-to-ask on`() {
        assertEquals(1_700L, stoppedAt(1_000).deadline(closeCall = false, holdToAsk = true, dwellMs = DWELL))
        assertNull(stoppedAt(1_000).deadline(closeCall = false, holdToAsk = false, dwellMs = DWELL))
    }

    @Test
    fun `the timeout is the time left until the deadline`() {
        val dwell = stoppedAt(1_000)
        assertEquals(250L, dwell.timeout(1_100, closeCall = true, holdToAsk = false, dwellMs = DWELL))
        assertNull(dwell.timeout(1_100, closeCall = false, holdToAsk = false, dwellMs = DWELL))
        // Already past: the caller fires at once rather than sleeping a negative time.
        assertEquals(-50L, dwell.timeout(1_400, closeCall = true, holdToAsk = false, dwellMs = DWELL))
    }

    @Test
    fun `a jitter inside the radius keeps the clock, a move past it restarts it`() {
        val dwell = stoppedAt(1_000)
        dwell.sample(105f, 204f, 1_050, RADIUS)
        assertEquals(1_000L, dwell.stillSince)
        assertEquals(100f, dwell.stillX, 0f)
        dwell.sample(130f, 200f, 1_080, RADIUS)
        assertEquals(1_080L, dwell.stillSince)
        assertEquals(130f, dwell.stillX, 0f)
        assertEquals(200f, dwell.stillY, 0f)
    }

    @Test
    fun `a slow drift never adds up to a move`() {
        // Each step is inside the radius of where the finger *stopped*, not of
        // the previous sample, so the clock only restarts once the drift as a
        // whole crosses the radius.
        val dwell = stoppedAt(1_000)
        dwell.sample(104f, 200f, 1_010, RADIUS)
        dwell.sample(108f, 200f, 1_020, RADIUS)
        dwell.sample(111f, 200f, 1_030, RADIUS)
        assertEquals(1_000L, dwell.stillSince)
        dwell.sample(113f, 200f, 1_040, RADIUS)
        assertEquals(1_040L, dwell.stillSince)
    }

    @Test
    fun `firing at the deadline with two words opens`() {
        val dwell = stoppedAt(1_000)
        assertEquals(
            GlideDwell.Action.OPEN,
            dwell.onTimeout(1_350, closeCall = true, holdToAsk = false, dwellMs = DWELL, choices = 2),
        )
    }

    @Test
    fun `firing with fewer than two words re-arms instead of opening`() {
        val dwell = stoppedAt(1_000)
        assertEquals(
            GlideDwell.Action.REARM,
            dwell.onTimeout(1_350, closeCall = true, holdToAsk = false, dwellMs = DWELL, choices = 1),
        )
        assertEquals(
            GlideDwell.Action.REARM,
            dwell.onTimeout(1_350, closeCall = true, holdToAsk = false, dwellMs = DWELL, choices = 0),
        )
    }

    @Test
    fun `firing early waits`() {
        val dwell = stoppedAt(1_000)
        assertEquals(
            GlideDwell.Action.WAIT,
            dwell.onTimeout(1_300, closeCall = true, holdToAsk = false, dwellMs = DWELL, choices = 3),
        )
    }

    @Test
    fun `firing with nothing to ask re-arms`() {
        // The stroke stopped being a close call between arming and firing and
        // hold-to-ask is off: there is no deadline any more.
        val dwell = stoppedAt(1_000)
        assertEquals(
            GlideDwell.Action.REARM,
            dwell.onTimeout(1_350, closeCall = false, holdToAsk = false, dwellMs = DWELL, choices = 3),
        )
    }

    @Test
    fun `whatever a fired timer decides, the next timeout is none or in the future`() {
        for (closeCall in listOf(true, false)) {
            for (holdToAsk in listOf(true, false)) {
                for (choices in 0..3) {
                    for (late in listOf(0L, 1L, 900L)) {
                        val dwell = stoppedAt(1_000)
                        val due = dwell.deadline(closeCall, holdToAsk, DWELL) ?: continue
                        val now = due + late
                        val action = dwell.onTimeout(now, closeCall, holdToAsk, DWELL, choices)
                        // What the pointer loop does with each answer.
                        val armsAgain = when (action) {
                            GlideDwell.Action.OPEN -> false
                            GlideDwell.Action.REARM -> {
                                dwell.reset(dwell.stillX, dwell.stillY, now)
                                true
                            }
                            GlideDwell.Action.WAIT -> true
                        }
                        if (!armsAgain) continue
                        val next = dwell.timeout(now, closeCall, holdToAsk, DWELL)
                        val label = "closeCall=$closeCall holdToAsk=$holdToAsk choices=$choices late=$late → $action"
                        assertTrue(label, next == null || next > 0L)
                    }
                }
            }
        }
    }
}
