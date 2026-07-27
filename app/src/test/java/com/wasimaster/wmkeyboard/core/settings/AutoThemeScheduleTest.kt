package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.tools.SolarTimes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which half of the auto-theme pair each trigger picks, walked through a day. */
class AutoThemeScheduleTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test fun systemTriggerIgnoresTheClock() {
        val auto = AutoThemeSettings(trigger = AutoThemeTrigger.SYSTEM)
        assertTrue(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(12)))
        assertFalse(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(23)))
    }

    @Test fun scheduleSwitchesAtTheSetTimes() {
        val auto = AutoThemeSettings(
            trigger = AutoThemeTrigger.SCHEDULE,
            dayStartMinutes = at(7),
            nightStartMinutes = at(19),
        )
        // Before day, in day, at the exact boundaries, after night.
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(6, 59)))
        assertFalse(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(7)))
        assertFalse(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(18, 59)))
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(19)))
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(3)))
    }

    /** A night-shift schedule: the light window is the one that crosses midnight. */
    @Test fun scheduleWrapsOverMidnight() {
        val auto = AutoThemeSettings(
            trigger = AutoThemeTrigger.SCHEDULE,
            dayStartMinutes = at(21),
            nightStartMinutes = at(6),
        )
        assertFalse(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(22)))
        assertFalse(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(2)))
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(6)))
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(15)))
    }

    @Test fun sunTriggerFollowsSunriseAndSunset() {
        val auto = AutoThemeSettings(trigger = AutoThemeTrigger.SUN)
        val sun = SolarTimes(sunriseMinutes = at(5, 40), sunsetMinutes = at(18, 20))
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(5), sun = sun))
        assertFalse(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(12), sun = sun))
        assertTrue(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(19), sun = sun))
    }

    /** No saved location, or a polar day: the system decides rather than a guess. */
    @Test fun sunTriggerFallsBackToTheSystemWithoutTimes() {
        val auto = AutoThemeSettings(trigger = AutoThemeTrigger.SUN)
        assertTrue(auto.usesDarkSlot(systemDark = true, minutesOfDay = at(12), sun = null))
        assertFalse(auto.usesDarkSlot(systemDark = false, minutesOfDay = at(23), sun = null))
    }
}
