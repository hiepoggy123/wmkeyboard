package com.wasimaster.wmkeyboard.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is what stands between a resume and a request, and the settings
 * activity resumes every time the user comes back from anywhere. Sixty
 * unauthenticated requests an hour are shared by every device behind one
 * address, so each rule here is a rule about not spending them.
 */
class UpdateCheckGateTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_000_000_000L

    private fun gate(
        now: Long = this.now,
        lastCheckAt: Long = 0L,
        rateLimitedUntil: Long = 0L,
        state: UpdateState = UpdateState.Idle,
    ) = UpdateCheckGate.shouldAutoCheck(now, lastCheckAt, rateLimitedUntil, state)

    @Test
    fun `a first check is always allowed`() {
        assertTrue(gate())
    }

    @Test
    fun `a second check inside the interval is not`() {
        assertFalse(gate(lastCheckAt = now - hour))
        assertFalse(gate(lastCheckAt = now - 5 * hour))
    }

    @Test
    fun `a check after the interval is`() {
        assertTrue(gate(lastCheckAt = now - 6 * hour))
        assertTrue(gate(lastCheckAt = now - 48 * hour))
    }

    @Test
    fun `a clock that went backwards reads as stale, not as a closed window`() {
        // These preferences are covered by Android's backup, so a restored
        // device inherits another device's clock readings.
        assertTrue(gate(lastCheckAt = now + 100 * hour))
    }

    @Test
    fun `a stored rate limit holds the check back`() {
        assertFalse(gate(rateLimitedUntil = now + hour))
    }

    @Test
    fun `an expired rate limit does not`() {
        assertTrue(gate(rateLimitedUntil = now - 1))
    }

    @Test
    fun `a rate limit further out than GitHub's own window is ignored`() {
        // An hour is the whole window, so a value days away came from a clock
        // that disagrees with this one and must not silence checks forever.
        assertTrue(gate(rateLimitedUntil = now + 72 * hour))
    }

    @Test
    fun `nothing is checked on top of work the user started`() {
        assertFalse(gate(state = UpdateState.Downloading(1, 2)))
        assertFalse(gate(state = UpdateState.Downloaded))
        assertFalse(gate(state = UpdateState.Installing))
        assertFalse(gate(state = UpdateState.Unsupported))
    }

    @Test
    fun `a quiet state is checkable`() {
        assertTrue(gate(state = UpdateState.Idle))
        assertTrue(gate(state = UpdateState.Checking))
        assertTrue(gate(state = UpdateState.UpToDate(userAsked = false)))
        assertTrue(gate(state = UpdateState.Failed(cancelled = false)))
        assertTrue(gate(state = UpdateState.Available(14, 1, immediate = false)))
    }

    @Test
    fun `the ETag is only sent when the body it describes is still there`() {
        assertEquals("W/x", UpdateCheckGate.etagToSend("W/x", cacheExists = true))
        assertNull(UpdateCheckGate.etagToSend("W/x", cacheExists = false))
        assertNull(UpdateCheckGate.etagToSend(null, cacheExists = true))
        assertNull(UpdateCheckGate.etagToSend("  ", cacheExists = true))
    }

    @Test
    fun `an install press is picked back up only while it is recent`() {
        val pressed = 5_000L
        assertTrue(UpdateCheckGate.installStillPending(pressed + 1_000, pressed))
        assertTrue(UpdateCheckGate.installStillPending(pressed + 119_000, pressed))
        assertFalse(UpdateCheckGate.installStillPending(pressed + 121_000, pressed))
        assertFalse(UpdateCheckGate.installStillPending(pressed, 0L))
        // A reboot restarts the monotonic clock, and a reboot mid-install is a
        // good enough reason to make the user press the button again.
        assertFalse(UpdateCheckGate.installStillPending(1_000, pressed))
    }
}
