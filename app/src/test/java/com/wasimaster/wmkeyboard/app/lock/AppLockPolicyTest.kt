package com.wasimaster.wmkeyboard.app.lock

import com.wasimaster.wmkeyboard.core.settings.AppLockRelock
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long one successful check lasts.
 *
 * [AppLockSession] takes the clock as an argument precisely so this can be
 * tested with no Android and no waiting, and so that nothing in it can read
 * the wall clock by accident. Everything here is arithmetic on a `now` this
 * test chooses.
 */
class AppLockPolicyTest {

    private val start = 1_000_000L
    private val minute = 60_000L

    @After
    fun tearDown() {
        // The session is a process-scoped object, so a leftover grant would
        // leak into the next test in the same JVM.
        AppLockSession.clear()
        AppLockSession.clearConfig()
        AppLockSession.prompting = null
    }

    @Test
    fun `the configurator starts shut`() {
        assertFalse(AppLockSession.configGranted)
    }

    @Test
    fun `the configurator opens on its own check and not on a screen check`() {
        // The regression this pins: the gate in front of the off switch used to
        // answer "locked" unconditionally, so passing its prompt changed
        // nothing and the screen could never be opened again.
        AppLockSession.grant(start)
        assertFalse("a screen check opened the configurator", AppLockSession.configGranted)
        AppLockSession.grantConfig()
        assertTrue(AppLockSession.configGranted)
    }

    @Test
    fun `the configurator's check does not open the screens`() {
        AppLockSession.grantConfig()
        for (policy in AppLockRelock.entries) {
            assertFalse("$policy opened on a configurator check", AppLockSession.isOpen(policy, start))
        }
    }

    @Test
    fun `leaving the app shuts the configurator whatever the policy`() {
        for (policy in AppLockRelock.entries) {
            AppLockSession.grantConfig()
            AppLockSession.onStopped(policy)
            assertFalse("$policy kept the off switch open", AppLockSession.configGranted)
        }
    }

    @Test
    fun `nothing is open before the first check`() {
        for (policy in AppLockRelock.entries) {
            assertFalse("$policy started open", AppLockSession.isOpen(policy, start))
        }
    }

    @Test
    fun `a fresh check opens every policy`() {
        for (policy in AppLockRelock.entries) {
            AppLockSession.clear()
            AppLockSession.grant(start)
            assertTrue("$policy shut immediately after a check", AppLockSession.isOpen(policy, start))
        }
    }

    @Test
    fun `the one minute window closes on the minute`() {
        AppLockSession.grant(start)
        assertTrue(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + minute - 1))
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + minute))
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + 10 * minute))
    }

    @Test
    fun `the five minute window closes on the fifth minute`() {
        AppLockSession.grant(start)
        assertTrue(AppLockSession.isOpen(AppLockRelock.AFTER_5_MIN, start + 5 * minute - 1))
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_5_MIN, start + 5 * minute))
    }

    @Test
    fun `the timed windows count time spent outside the app`() {
        // elapsedRealtime keeps running while the app is in the background, so
        // a five minute window is five minutes of wall time and not five
        // minutes of looking at the screen. This is why onStopped leaves the
        // timed policies alone.
        AppLockSession.grant(start)
        AppLockSession.onStopped(AppLockRelock.AFTER_5_MIN)
        assertTrue(AppLockSession.isOpen(AppLockRelock.AFTER_5_MIN, start + minute))
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_5_MIN, start + 6 * minute))
    }

    @Test
    fun `leaving the app closes the two event policies`() {
        for (policy in listOf(AppLockRelock.ON_LEAVE, AppLockRelock.IMMEDIATE)) {
            AppLockSession.grant(start)
            AppLockSession.onStopped(policy)
            assertFalse("$policy survived a trip to the background", AppLockSession.isOpen(policy, start))
        }
    }

    @Test
    fun `until-app-closes survives the background`() {
        AppLockSession.grant(start)
        AppLockSession.onStopped(AppLockRelock.UNTIL_APP_CLOSES)
        assertTrue(AppLockSession.isOpen(AppLockRelock.UNTIL_APP_CLOSES, start + 60 * minute))
    }

    @Test
    fun `leaving the app does not shorten a timed window`() {
        AppLockSession.grant(start)
        AppLockSession.onStopped(AppLockRelock.AFTER_1_MIN)
        assertTrue(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + 1))
    }

    @Test
    fun `a clock that runs backwards does not extend a window`() {
        // elapsedRealtime cannot go backwards, which is the reason the session
        // uses it. If a future change ever swapped in a wall clock, a user who
        // wound the phone back would get an open grant forever, so pin the
        // behaviour: an earlier `now` reads as no time passed, never as more.
        AppLockSession.grant(start)
        assertTrue(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start - 10 * minute))
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + minute))
    }

    @Test
    fun `clearing shuts every policy`() {
        AppLockSession.grant(start)
        AppLockSession.clear()
        for (policy in AppLockRelock.entries) {
            assertFalse("$policy stayed open after clear", AppLockSession.isOpen(policy, start))
        }
    }

    @Test
    fun `a second check restarts a timed window`() {
        AppLockSession.grant(start)
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + minute))
        AppLockSession.grant(start + minute)
        assertTrue(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + minute))
        assertFalse(AppLockSession.isOpen(AppLockRelock.AFTER_1_MIN, start + 2 * minute))
    }
}
