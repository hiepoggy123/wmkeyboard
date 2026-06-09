package com.wasimaster.wmkeyboard.core.tools

/**
 * Pure-arithmetic calendar conversions for the calendar tool, all routed
 * through the Julian Day Number so they agree with each other. No java.time
 * chronologies (minSdk 24, no desugaring).
 *
 * - Bengali: the revised Bangladeshi calendar (in force since 1426 BS /
 *   2019): Boishakh–Ashshin 31 days, Kartik–Choitro 30, except Falgun with
 *   29 (30 when the Gregorian year it falls in is a leap year). Pohela
 *   Boishakh is fixed to 14 April.
 * - Hijri: the tabular (arithmetic) Islamic calendar, with a user-visible
 *   day adjustment since actual months follow moon sightings.
 */
object CalendarSystems {

    data class SimpleDate(val year: Int, val month: Int, val day: Int)

    // ---- Julian Day Number ----

    /** Valid for all Gregorian dates; truncating division is intentional. */
    fun gregorianToJdn(year: Int, month: Int, day: Int): Long {
        val a = (month - 14) / 12
        return (1461L * (year + 4800 + a)) / 4 +
            (367L * (month - 2 - 12 * a)) / 12 -
            (3L * ((year + 4900 + a) / 100)) / 4 +
            day - 32075
    }

    fun jdnToGregorian(jdn: Long): SimpleDate {
        var l = jdn + 68569
        val n = (4 * l) / 146097
        l -= (146097 * n + 3) / 4
        val i = (4000 * (l + 1)) / 1461001
        l -= (1461 * i) / 4 - 31
        val j = (80 * l) / 2447
        val day = (l - (2447 * j) / 80).toInt()
        l = j / 11
        val month = (j + 2 - 12 * l).toInt()
        val year = (100 * (n - 49) + i + l).toInt()
        return SimpleDate(year, month, day)
    }

    /** 0 = Sunday .. 6 = Saturday. */
    fun dayOfWeek(jdn: Long): Int = ((jdn + 1) % 7).toInt()

    fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun gregorianMonthLength(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isGregorianLeap(year)) 29 else 28
    }

    // ---- Bengali (revised Bangladeshi) ----

    val bengaliMonths = listOf(
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
        "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র",
    )

    private fun bengaliMonthLengths(boishakhGregorianYear: Int): IntArray {
        // Falgun spans February of the following Gregorian year and absorbs
        // its leap day.
        val falgun = if (isGregorianLeap(boishakhGregorianYear + 1)) 30 else 29
        return intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, falgun, 30)
    }

    fun toBengali(year: Int, month: Int, day: Int): SimpleDate {
        val jdn = gregorianToJdn(year, month, day)
        val startYear = if (jdn >= gregorianToJdn(year, 4, 14)) year else year - 1
        var offset = (jdn - gregorianToJdn(startYear, 4, 14)).toInt()
        val lengths = bengaliMonthLengths(startYear)
        var m = 0
        while (offset >= lengths[m]) {
            offset -= lengths[m]
            m++
        }
        return SimpleDate(startYear - 593, m + 1, offset + 1)
    }

    fun bengaliDigits(value: Int): String =
        value.toString().map { c -> if (c in '0'..'9') ('০' + (c - '0')) else c }
            .joinToString("")

    // ---- Hijri (tabular) ----

    val hijriMonths = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    )

    /**
     * Classic integer algorithm for the civil tabular calendar (epoch
     * 16 July 622, leap years 2,5,7,10,13,16,18,21,24,26,29 of each
     * 30-year cycle). [adjustDays] shifts the input date to track local
     * moon sightings.
     */
    fun toHijri(year: Int, month: Int, day: Int, adjustDays: Int = 0): SimpleDate {
        val jdn = gregorianToJdn(year, month, day) + adjustDays
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) +
            (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) -
            (j / 16) * ((15238 * j) / 43) + 29
        val m = (24 * l) / 709
        val d = l - (709 * m) / 24
        val y = 30 * n + j - 30
        return SimpleDate(y.toInt(), m.toInt(), d.toInt())
    }
}
