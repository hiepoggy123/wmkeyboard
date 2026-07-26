package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AltCalendarsTest {

    private fun jdn(y: Int, m: Int, d: Int) = CalendarSystems.gregorianToJdn(y, m, d)

    // ---- Hebrew ----

    @Test
    fun `rosh hashanah lands on the published dates`() {
        val expected = mapOf(
            5782 to Triple(2021, 9, 7),
            5783 to Triple(2022, 9, 26),
            5784 to Triple(2023, 9, 16),
            5785 to Triple(2024, 10, 3),
            5786 to Triple(2025, 9, 23),
            5787 to Triple(2026, 9, 12),
        )
        for ((year, date) in expected) {
            val (gy, gm, gd) = date
            assertEquals("Rosh Hashanah $year", jdn(gy, gm, gd), AltCalendars.hebrewNewYear(year))
            val hebrew = AltCalendars.hebrew(gy, gm, gd)
            assertEquals(year, hebrew.year)
            assertEquals(1, hebrew.month)
            assertEquals(1, hebrew.day)
        }
    }

    @Test
    fun `hebrew years are only ever one of the six legal lengths`() {
        for (year in 5700..5900) {
            val length = AltCalendars.hebrewNewYear(year + 1) - AltCalendars.hebrewNewYear(year)
            assertTrue(
                "AM $year has $length days",
                length in listOf(353L, 354L, 355L, 383L, 384L, 385L),
            )
            // Leap years take the long lengths and only those.
            assertEquals(AltCalendars.isHebrewLeap(year), length > 360)
        }
    }

    @Test
    fun `hebrew dates advance one day at a time`() {
        var previous: CalendarSystems.SimpleDate? = null
        var day = jdn(2024, 1, 1)
        while (day < jdn(2030, 1, 1)) {
            val g = CalendarSystems.jdnToGregorian(day)
            val hebrew = AltCalendars.hebrew(g.year, g.month, g.day)
            val before = previous
            if (before != null) {
                val sameMonth = hebrew.year == before.year && hebrew.month == before.month
                assertTrue(
                    "jump at ${g.year}-${g.month}-${g.day}",
                    if (sameMonth) hebrew.day == before.day + 1 else hebrew.day == 1,
                )
            }
            previous = hebrew
            day++
        }
    }

    @Test
    fun `adar splits in two only in a leap year`() {
        // 5784 is a leap year (Adar I and Adar II), 5785 is not.
        assertTrue(AltCalendars.isHebrewLeap(5784))
        assertFalse(AltCalendars.isHebrewLeap(5785))
        assertEquals("Adar I", AltCalendars.hebrewMonthName(5784, 6))
        assertEquals("Adar II", AltCalendars.hebrewMonthName(5784, 7))
        assertEquals("Adar", AltCalendars.hebrewMonthName(5785, 6))
        assertEquals("Nisan", AltCalendars.hebrewMonthName(5785, 7))
    }

    // ---- Persian ----

    @Test
    fun `nowruz falls on the observed day of march`() {
        // The Iranian year starts on the day whose Tehran noon follows the
        // vernal equinox, which lands on 20 or 21 March; these are the
        // published dates.
        val expected = mapOf(
            2020 to 20, 2021 to 21, 2022 to 21, 2023 to 21, 2024 to 20,
            2025 to 21, 2026 to 21, 2027 to 21, 2028 to 20, 2029 to 20,
        )
        for ((gregorian, marchDay) in expected) {
            val date = AltCalendars.persian(gregorian, 3, marchDay)
            assertEquals("Nowruz $gregorian month", 1, date.month)
            assertEquals("Nowruz $gregorian day", 1, date.day)
            assertEquals("Nowruz $gregorian year", gregorian - 621, date.year)
        }
    }

    @Test
    fun `persian round trips and keeps its month lengths`() {
        var day = jdn(1950, 1, 1)
        while (day < jdn(2100, 1, 1)) {
            val g = CalendarSystems.jdnToGregorian(day)
            val p = AltCalendars.persian(g.year, g.month, g.day)
            assertEquals(day, AltCalendars.persianToJdn(p.year, p.month, p.day))
            val maxDay = when {
                p.month <= 6 -> 31
                p.month <= 11 -> 30
                else -> 30 // Esfand: 29, or 30 in a leap year
            }
            assertTrue("$p", p.day in 1..maxDay)
            day++
        }
    }

    // ---- Hindu (Saka) ----

    @Test
    fun `saka year starts on chaitra 1`() {
        // 22 March normally, 21 March when the Gregorian year is a leap year.
        assertEquals(CalendarSystems.SimpleDate(1948, 1, 1), AltCalendars.saka(2026, 3, 22))
        assertEquals(CalendarSystems.SimpleDate(1946, 1, 1), AltCalendars.saka(2024, 3, 21))
        // The day before belongs to the last month of the previous Saka year.
        val before = AltCalendars.saka(2026, 3, 21)
        assertEquals(1947, before.year)
        assertEquals(12, before.month)
        assertEquals(30, before.day)
    }

    @Test
    fun `saka months have the lengths the national calendar prescribes`() {
        var day = jdn(1950, 1, 1)
        while (day < jdn(2100, 1, 1)) {
            val g = CalendarSystems.jdnToGregorian(day)
            val s = AltCalendars.saka(g.year, g.month, g.day)
            val maxDay = when {
                s.month == 1 -> 31 // 30 in a common year; the leap day rides here
                s.month <= 6 -> 31
                else -> 30
            }
            assertTrue("$s", s.month in 1..12 && s.day in 1..maxDay)
            day++
        }
    }

    // ---- Chinese ----

    @Test
    fun `chinese new year matches the published dates`() {
        val expected = mapOf(
            2016 to Pair(2, 8), 2017 to Pair(1, 28), 2018 to Pair(2, 16),
            2019 to Pair(2, 5), 2020 to Pair(1, 25), 2021 to Pair(2, 12),
            2022 to Pair(2, 1), 2023 to Pair(1, 22), 2024 to Pair(2, 10),
            2025 to Pair(1, 29), 2026 to Pair(2, 17), 2027 to Pair(2, 6),
            2028 to Pair(1, 26), 2029 to Pair(2, 13), 2030 to Pair(2, 3),
        )
        for ((year, date) in expected) {
            val (month, day) = date
            val newYear = AltCalendars.chinese(year, month, day)
            assertEquals("CNY $year month", 1, newYear.month)
            assertEquals("CNY $year day", 1, newYear.day)
            assertFalse("CNY $year leap", newYear.leapMonth)
            assertEquals("CNY $year year", year, newYear.year)
            // The day before is the last of the twelfth month of the year before.
            val before = CalendarSystems.jdnToGregorian(
                CalendarSystems.gregorianToJdn(year, month, day) - 1,
            )
            val eve = AltCalendars.chinese(before.year, before.month, before.day)
            assertEquals("CNY eve $year", 12, eve.month)
            assertEquals("CNY eve $year", year - 1, eve.year)
        }
    }

    @Test
    fun `chinese leap months land where the almanacs put them`() {
        // year -> the month number the leap repeats, for the years that have one.
        val leaps = mapOf(
            2017 to 6, 2020 to 4, 2023 to 2, 2025 to 6, 2028 to 5, 2031 to 3,
        )
        for ((year, month) in leaps) {
            var day = jdn(year, 1, 1)
            var found: Int? = null
            while (day < jdn(year + 1, 1, 1)) {
                val g = CalendarSystems.jdnToGregorian(day)
                val date = AltCalendars.chinese(g.year, g.month, g.day)
                if (date.leapMonth && date.year == year) {
                    found = date.month
                    break
                }
                day++
            }
            assertEquals("leap month of $year", month, found)
        }
    }

    @Test
    fun `chinese days run 1 to 29 or 30 without gaps`() {
        var previous: AltCalendars.ChineseDate? = null
        var day = jdn(2024, 1, 1)
        while (day < jdn(2029, 1, 1)) {
            val g = CalendarSystems.jdnToGregorian(day)
            val date = AltCalendars.chinese(g.year, g.month, g.day)
            assertTrue("${g.year}-${g.month}-${g.day} day ${date.day}", date.day in 1..30)
            assertTrue("month ${date.month}", date.month in 1..12)
            val before = previous
            if (before != null) {
                val sameMonth = before.month == date.month && before.leapMonth == date.leapMonth
                assertTrue(
                    "jump at ${g.year}-${g.month}-${g.day}",
                    if (sameMonth) date.day == before.day + 1 else date.day == 1,
                )
            }
            previous = date
            day++
        }
    }

    @Test
    fun `sexagenary cycle and zodiac line up with the year`() {
        assertEquals("丙午", AltCalendars.sexagenary(2026))
        assertEquals("乙巳", AltCalendars.sexagenary(2025))
        assertEquals("甲辰", AltCalendars.sexagenary(2024))
        assertTrue(AltCalendars.zodiac(2026).endsWith("Horse"))
        assertTrue(AltCalendars.zodiac(2025).endsWith("Snake"))
    }

    // ---- Japanese and Buddhist ----

    @Test
    fun `japanese eras start on their proclamation day`() {
        assertEquals("Reiwa", AltCalendars.japaneseEra(2019, 5, 1).romaji)
        assertEquals(1, AltCalendars.japaneseEra(2019, 5, 1).year)
        assertEquals("Heisei", AltCalendars.japaneseEra(2019, 4, 30).romaji)
        assertEquals(31, AltCalendars.japaneseEra(2019, 4, 30).year)
        assertEquals(8, AltCalendars.japaneseEra(2026, 7, 26).year)
        assertEquals("令和", AltCalendars.japaneseEra(2026, 7, 26).kanji)
        assertEquals("Shōwa", AltCalendars.japaneseEra(1926, 12, 25).romaji)
        assertEquals("Taishō", AltCalendars.japaneseEra(1926, 12, 24).romaji)
    }

    @Test
    fun `buddhist years run 543 ahead`() {
        assertTrue(
            AltCalendars.fullDate(AltCalendar.BUDDHIST, 2026, 7, 26).contains("2569 BE"),
        )
    }

    // ---- formatting ----

    @Test
    fun `none contributes nothing to any of the three labels`() {
        assertEquals("", AltCalendars.dayLabel(AltCalendar.NONE, 2026, 7, 26))
        assertEquals("", AltCalendars.fullDate(AltCalendar.NONE, 2026, 7, 26))
        assertEquals("", AltCalendars.monthSpan(AltCalendar.NONE, 2026, 7))
    }

    @Test
    fun `calendars whose days match the gregorian ones draw no cell label`() {
        assertEquals("", AltCalendars.dayLabel(AltCalendar.BUDDHIST, 2026, 7, 26))
        assertEquals("", AltCalendars.dayLabel(AltCalendar.JAPANESE, 2026, 7, 26))
        // The flag the panel filters on has to agree with what dayLabel does.
        for (calendar in AltCalendar.entries) {
            assertEquals(
                calendar.name,
                calendar.hasDayLabel,
                AltCalendars.dayLabel(calendar, 2026, 7, 26).isNotEmpty(),
            )
        }
    }

    @Test
    fun `every calendar produces a non-empty date and span`() {
        for (calendar in AltCalendar.entries - AltCalendar.NONE) {
            assertTrue(calendar.name, AltCalendars.fullDate(calendar, 2026, 7, 26).isNotEmpty())
            assertTrue(calendar.name, AltCalendars.monthSpan(calendar, 2026, 7).isNotEmpty())
        }
    }

    @Test
    fun `month spans collapse to one name when the month does not straddle`() {
        // July 2026 sits inside a single Buddhist year and a single era year.
        assertEquals("2569 BE", AltCalendars.monthSpan(AltCalendar.BUDDHIST, 2026, 7))
        // Bengali months always straddle a Gregorian one, so that span has both.
        assertTrue(AltCalendars.monthSpan(AltCalendar.BENGALI, 2026, 7).contains("–"))
    }

    @Test
    fun `stored ids survive a round trip and unknown ids fall back to none`() {
        for (calendar in AltCalendar.entries) {
            assertEquals(calendar, AltCalendar.fromId(calendar.id))
        }
        assertEquals(AltCalendar.NONE, AltCalendar.fromId("gregorian"))
        assertEquals(AltCalendar.NONE, AltCalendar.fromId(null))
    }
}
