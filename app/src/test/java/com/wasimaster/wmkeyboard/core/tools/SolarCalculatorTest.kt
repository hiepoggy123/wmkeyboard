package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Sanity bounds rather than exact minutes: the point of this calculator is that
 * a theme switches at roughly the right time everywhere, and pinning it to the
 * minute would only test the algorithm against itself.
 */
class SolarCalculatorTest {

    private fun millisOf(year: Int, month: Int, day: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis

    @Test fun londonMidsummerHasAnEarlySunriseAndLateSunset() {
        val zone = TimeZone.getTimeZone("Europe/London")
        val times = SolarCalculator.forDate(51.5, -0.13, millisOf(2026, Calendar.JUNE, 21, zone), zone)
        assertNotNull(times)
        // Sunrise a bit before 05:00 BST, sunset a bit after 21:00.
        assertTrue("sunrise=${times!!.sunriseMinutes}", times.sunriseMinutes in 4 * 60..5 * 60)
        assertTrue("sunset=${times.sunsetMinutes}", times.sunsetMinutes in 20 * 60..22 * 60)
    }

    @Test fun dhakaEquinoxIsRoughlyTwelveHoursOfDaylight() {
        val zone = TimeZone.getTimeZone("Asia/Dhaka")
        val times = SolarCalculator.forDate(23.81, 90.41, millisOf(2026, Calendar.MARCH, 20, zone), zone)
        assertNotNull(times)
        val daylight = times!!.sunsetMinutes - times.sunriseMinutes
        assertTrue("daylight=$daylight", daylight in 11 * 60..13 * 60)
    }

    @Test fun polarNightHasNoSunriseToScheduleAgainst() {
        val zone = TimeZone.getTimeZone("Europe/Oslo")
        // Longyearbyen in December: the sun does not come up at all.
        assertNull(SolarCalculator.forDate(78.22, 15.63, millisOf(2026, Calendar.DECEMBER, 21, zone), zone))
    }

    @Test fun nonsenseCoordinatesAreRejected() {
        assertNull(SolarCalculator.forDate(120.0, 0.0, millisOf(2026, Calendar.MAY, 1, TimeZone.getDefault())))
        assertNull(SolarCalculator.forDate(0.0, 999.0, millisOf(2026, Calendar.MAY, 1, TimeZone.getDefault())))
    }
}
