package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The region-derived starting points for the calendar tool. */
class CalendarDefaultsTest {

    @Test
    fun `the weekend follows the region`() {
        assertEquals(Weekend.FRI_SAT, Weekend.forRegion("SA"))
        assertEquals(Weekend.FRI, Weekend.forRegion("BD"))
        assertEquals(Weekend.THU_FRI, Weekend.forRegion("IR"))
        assertEquals(Weekend.SAT, Weekend.forRegion("NP"))
        assertEquals(Weekend.SUN, Weekend.forRegion("IN"))
        assertEquals(Weekend.SAT_SUN, Weekend.forRegion("DE"))
    }

    @Test
    fun `an unknown or absent region falls back to Saturday and Sunday`() {
        assertEquals(Weekend.SAT_SUN, Weekend.forRegion(null))
        assertEquals(Weekend.SAT_SUN, Weekend.forRegion("ZZ"))
        assertEquals(Weekend.SAT_SUN, Weekend.forRegion(""))
    }

    @Test
    fun `region codes match regardless of case`() {
        assertEquals(Weekend.FRI, Weekend.forRegion("bd"))
        assertEquals(defaultAltCalendars("BD"), defaultAltCalendars("bd"))
    }

    @Test
    fun `weekend ids round-trip and days are valid columns`() {
        for (weekend in Weekend.entries) {
            assertEquals(weekend, Weekend.fromId(weekend.id))
            assertTrue("${weekend.id} has an out-of-range day", weekend.days.all { it in 0..6 })
        }
        assertEquals(Weekend.SAT_SUN, Weekend.fromId(null))
        assertEquals(Weekend.SAT_SUN, Weekend.fromId("nonsense"))
    }

    @Test
    fun `alternate calendars follow the region`() {
        assertEquals(AltCalendar.BENGALI to AltCalendar.HIJRI, defaultAltCalendars("BD"))
        assertEquals(AltCalendar.JAPANESE to AltCalendar.CHINESE, defaultAltCalendars("JP"))
        assertEquals(AltCalendar.HEBREW to AltCalendar.NONE, defaultAltCalendars("IL"))
        assertEquals(AltCalendar.BUDDHIST to AltCalendar.NONE, defaultAltCalendars("TH"))
        assertEquals(AltCalendar.HIJRI to AltCalendar.NONE, defaultAltCalendars("SA"))
    }

    @Test
    fun `a region with nothing to show gets no alternate calendars`() {
        assertEquals(AltCalendar.NONE to AltCalendar.NONE, defaultAltCalendars("DE"))
        assertEquals(AltCalendar.NONE to AltCalendar.NONE, defaultAltCalendars(null))
    }

    @Test
    fun `no region ever repeats a calendar across both slots`() {
        val regions = listOf(
            "BD", "IN", "NP", "IR", "AF", "IL", "CN", "TW", "HK", "MO", "SG",
            "MY", "VN", "JP", "TH", "LA", "KH", "MM", "LK", "AE", "SA", "EG",
            "PK", "ID", "DE", "US", null,
        )
        for (region in regions) {
            val (one, two) = defaultAltCalendars(region)
            if (two != AltCalendar.NONE) {
                assertTrue("$region shows the same calendar twice", one != two)
            }
        }
    }

    @Test
    fun `a second calendar never appears without a first`() {
        val regions = listOf("BD", "JP", "IL", "SA", "DE", "IN", null)
        for (region in regions) {
            val (one, two) = defaultAltCalendars(region)
            if (one == AltCalendar.NONE) {
                assertEquals("$region fills slot two but not slot one", AltCalendar.NONE, two)
            }
        }
    }
}
