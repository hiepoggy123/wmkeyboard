package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSystemsTest {

    @Test
    fun `jdn round trip and known values`() {
        assertEquals(2451545L, CalendarSystems.gregorianToJdn(2000, 1, 1))
        assertEquals(2440588L, CalendarSystems.gregorianToJdn(1970, 1, 1))
        for (jdn in listOf(2451545L, 2440588L, 2460000L, 2461000L)) {
            val (y, m, d) = CalendarSystems.jdnToGregorian(jdn)
            assertEquals(jdn, CalendarSystems.gregorianToJdn(y, m, d))
        }
    }

    @Test
    fun `day of week`() {
        // 1970-01-01 was a Thursday, 2000-01-01 a Saturday.
        assertEquals(4, CalendarSystems.dayOfWeek(CalendarSystems.gregorianToJdn(1970, 1, 1)))
        assertEquals(6, CalendarSystems.dayOfWeek(CalendarSystems.gregorianToJdn(2000, 1, 1)))
    }

    @Test
    fun `bengali new year is 1 boishakh`() {
        val date = CalendarSystems.toBengali(2026, 4, 14)
        assertEquals(1433, date.year)
        assertEquals(1, date.month)
        assertEquals(1, date.day)
        // The day before is the last of Choitro of the previous year.
        val before = CalendarSystems.toBengali(2026, 4, 13)
        assertEquals(1432, before.year)
        assertEquals(12, before.month)
        assertEquals(30, before.day)
    }

    @Test
    fun `bengali revised month boundaries`() {
        // Pohela Falgun is fixed to 14 February under the revised calendar.
        val falgun = CalendarSystems.toBengali(2026, 2, 14)
        assertEquals(11, falgun.month)
        assertEquals(1, falgun.day)
        // Srabon starts 16 July.
        val srabon = CalendarSystems.toBengali(2026, 7, 16)
        assertEquals(4, srabon.month)
        assertEquals(1, srabon.day)
        assertEquals(4, CalendarSystems.toBengali(2026, 7, 19).day)
    }

    @Test
    fun `bengali leap day lands in falgun`() {
        // 2024 is a Gregorian leap year; Falgun 1430 (starting Feb 2024) has 30 days.
        val last = CalendarSystems.toBengali(2024, 3, 14)
        assertEquals(11, last.month)
        assertEquals(30, last.day)
        val choitro1 = CalendarSystems.toBengali(2024, 3, 15)
        assertEquals(12, choitro1.month)
        assertEquals(1, choitro1.day)
    }

    @Test
    fun `hijri epoch`() {
        // Civil tabular epoch: 16 July 622 CE (Julian) = 1 Muharram 1 AH.
        val epoch = CalendarSystems.toHijri(622, 7, 19) // Gregorian-proleptic shift of the Julian date
        assertEquals(1, epoch.year)
        assertEquals(1, epoch.month)
        assertEquals(1, epoch.day)
    }

    @Test
    fun `hijri known date and adjustment`() {
        // 2026-07-19 falls in Safar 1448 in the tabular calendar.
        val date = CalendarSystems.toHijri(2026, 7, 19)
        assertEquals(1448, date.year)
        val adjusted = CalendarSystems.toHijri(2026, 7, 19, adjustDays = 1)
        val plainNext = CalendarSystems.toHijri(2026, 7, 20)
        assertEquals(plainNext, adjusted)
    }

    @Test
    fun `bengali digits`() {
        assertEquals("১৪৩৩", CalendarSystems.bengaliDigits(1433))
        assertEquals("৪", CalendarSystems.bengaliDigits(4))
    }
}
