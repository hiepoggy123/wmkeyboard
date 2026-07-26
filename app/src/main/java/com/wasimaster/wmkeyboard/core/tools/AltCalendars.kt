package com.wasimaster.wmkeyboard.core.tools

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A calendar the tool can show next to the Gregorian one. Two of these are
 * picked in settings; [NONE] is how a slot is left empty.
 *
 * [id] is the stored key and must not change once shipped.
 */
enum class AltCalendar(
    val id: String,
    val label: String,
    /**
     * False where the calendar's days line up with the Gregorian ones, so a
     * second number inside a day cell would only repeat the first.
     */
    val hasDayLabel: Boolean = true,
) {
    NONE("none", "None", hasDayLabel = false),
    BENGALI("bengali", "Bengali · বঙ্গাব্দ"),
    HIJRI("hijri", "Hijri · Islamic"),
    CHINESE("chinese", "Chinese · 农历"),
    HEBREW("hebrew", "Hebrew · לוח עברי"),
    HINDU("hindu", "Hindu · Saka"),
    PERSIAN("persian", "Persian · Solar Hijri"),
    BUDDHIST("buddhist", "Buddhist · Thai solar", hasDayLabel = false),
    JAPANESE("japanese", "Japanese · era years", hasDayLabel = false);

    companion object {
        fun fromId(id: String?): AltCalendar = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Conversions from Gregorian dates to every calendar the tool offers, plus the
 * three strings the panel draws: the tiny in-cell label, the full date under
 * the grid, and the month span in the header.
 *
 * Everything routes through the Julian Day Number so the calendars agree with
 * each other and with [CalendarSystems], which owns the JDN helpers and the
 * Bengali/Hijri arithmetic this file delegates to.
 *
 * Accuracy notes, worst case first:
 *  - **Chinese** is genuinely astronomical (Meeus new moons and solar
 *    longitudes evaluated in UTC+8). Month boundaries are exact for the modern
 *    era except where a new moon or a solar term falls within a minute or two
 *    of local midnight, which is rare and self-corrects the following month.
 *    Dates before 1929 ignore Beijing's older UTC+7:46 zone.
 *  - **Hijri** stays tabular, with the same user-set day offset as before.
 *  - **Hebrew**, **Persian**, **Hindu (Saka)**, **Buddhist** and **Japanese**
 *    are exact arithmetic over the range anyone will scroll to.
 */
object AltCalendars {

    // ---- public API ----

    /**
     * The small second number drawn inside a day cell, or "" for calendars
     * whose days line up with the Gregorian ones (Buddhist, Japanese) where a
     * second copy of the same number would be noise.
     */
    fun dayLabel(
        calendar: AltCalendar,
        year: Int,
        month: Int,
        day: Int,
        hijriAdjustDays: Int = 0,
    ): String = when (calendar) {
        AltCalendar.NONE, AltCalendar.BUDDHIST, AltCalendar.JAPANESE -> ""
        AltCalendar.BENGALI ->
            CalendarSystems.bengaliDigits(CalendarSystems.toBengali(year, month, day).day)
        AltCalendar.HIJRI ->
            CalendarSystems.toHijri(year, month, day, hijriAdjustDays).day.toString()
        AltCalendar.CHINESE -> chinese(year, month, day).let {
            // Real Chinese calendars label the first of a month with the month
            // itself rather than a "1", which is also the only cue in the grid
            // that a lunar month turned over.
            if (it.day == 1) chineseMonthName(it) else CHINESE_DAYS[it.day - 1]
        }
        AltCalendar.HEBREW -> hebrew(year, month, day).day.toString()
        AltCalendar.HINDU -> saka(year, month, day).day.toString()
        AltCalendar.PERSIAN -> persian(year, month, day).day.toString()
    }

    /** The full date, as shown under the grid for the selected day. */
    fun fullDate(
        calendar: AltCalendar,
        year: Int,
        month: Int,
        day: Int,
        hijriAdjustDays: Int = 0,
    ): String = when (calendar) {
        AltCalendar.NONE -> ""
        AltCalendar.BENGALI -> CalendarSystems.toBengali(year, month, day).let {
            "${CalendarSystems.bengaliDigits(it.day)} " +
                "${CalendarSystems.bengaliMonths[it.month - 1]} " +
                CalendarSystems.bengaliDigits(it.year)
        }
        AltCalendar.HIJRI -> CalendarSystems.toHijri(year, month, day, hijriAdjustDays).let {
            "${it.day} ${CalendarSystems.hijriMonths[it.month - 1]} ${it.year} AH"
        }
        AltCalendar.CHINESE -> chinese(year, month, day).let {
            "${chineseMonthName(it)}${CHINESE_DAYS[it.day - 1]} · " +
                "${sexagenary(it.year)}年 ${zodiac(it.year)}"
        }
        AltCalendar.HEBREW -> hebrew(year, month, day).let {
            "${it.day} ${hebrewMonthName(it.year, it.month)} ${it.year}"
        }
        AltCalendar.HINDU -> saka(year, month, day).let {
            "${it.day} ${SAKA_MONTHS[it.month - 1]} ${it.year} Saka"
        }
        AltCalendar.PERSIAN -> persian(year, month, day).let {
            "${it.day} ${PERSIAN_MONTHS[it.month - 1]} ${it.year}"
        }
        AltCalendar.BUDDHIST -> "${day} ${GREGORIAN_MONTHS[month - 1]} ${year + 543} BE"
        AltCalendar.JAPANESE -> japaneseEra(year, month, day).let { era ->
            "${era.kanji}${era.year}年${month}月${day}日 · ${era.romaji} ${era.year}"
        }
    }

    /**
     * The months this Gregorian month runs across, for the header line —
     * "Ramadan–Shawwal 1447". Collapses to one name when the month sits
     * inside a single month of the other calendar.
     */
    fun monthSpan(
        calendar: AltCalendar,
        year: Int,
        month: Int,
        hijriAdjustDays: Int = 0,
    ): String {
        if (calendar == AltCalendar.NONE) return ""
        val last = CalendarSystems.gregorianMonthLength(year, month)
        return when (calendar) {
            AltCalendar.NONE -> ""
            AltCalendar.BENGALI -> {
                val a = CalendarSystems.toBengali(year, month, 1)
                val b = CalendarSystems.toBengali(year, month, last)
                span(
                    CalendarSystems.bengaliMonths[a.month - 1],
                    CalendarSystems.bengaliMonths[b.month - 1],
                    CalendarSystems.bengaliDigits(b.year),
                )
            }
            AltCalendar.HIJRI -> {
                val a = CalendarSystems.toHijri(year, month, 1, hijriAdjustDays)
                val b = CalendarSystems.toHijri(year, month, last, hijriAdjustDays)
                span(
                    CalendarSystems.hijriMonths[a.month - 1].substringBefore(' '),
                    CalendarSystems.hijriMonths[b.month - 1].substringBefore(' '),
                    b.year.toString(),
                )
            }
            AltCalendar.CHINESE -> {
                val a = chinese(year, month, 1)
                val b = chinese(year, month, last)
                span(chineseMonthName(a), chineseMonthName(b), "${sexagenary(b.year)}年")
            }
            AltCalendar.HEBREW -> {
                val a = hebrew(year, month, 1)
                val b = hebrew(year, month, last)
                span(
                    hebrewMonthName(a.year, a.month),
                    hebrewMonthName(b.year, b.month),
                    b.year.toString(),
                )
            }
            AltCalendar.HINDU -> {
                val a = saka(year, month, 1)
                val b = saka(year, month, last)
                span(SAKA_MONTHS[a.month - 1], SAKA_MONTHS[b.month - 1], "${b.year} Saka")
            }
            AltCalendar.PERSIAN -> {
                val a = persian(year, month, 1)
                val b = persian(year, month, last)
                span(PERSIAN_MONTHS[a.month - 1], PERSIAN_MONTHS[b.month - 1], b.year.toString())
            }
            AltCalendar.BUDDHIST -> "${year + 543} BE"
            AltCalendar.JAPANESE -> japaneseEra(year, month, last).let {
                "${it.kanji}${it.year}年 · ${it.romaji} ${it.year}"
            }
        }
    }

    private fun span(first: String, last: String, year: String): String =
        if (first == last) "$first $year" else "$first–$last $year"

    // ---- Hebrew ----

    /**
     * 1 Tishrei AM 1, in the proleptic Gregorian calendar 7 September 3761 BCE.
     * Anchored against the modern Rosh Hashanahs the tests pin down.
     */
    private const val HEBREW_EPOCH_JDN = 347998L

    private fun hebrewElapsedDays(year: Int): Long {
        val months = (235L * year - 234L) / 19L
        val parts = 12084L + 13753L * months
        val day = months * 29L + parts / 25920L
        return if ((3L * (day + 1)) % 7 < 3) day + 1 else day
    }

    /** Postponement rule for a year that would otherwise be too long or short. */
    private fun hebrewCorrection(year: Int): Long {
        val last = hebrewElapsedDays(year - 1)
        val present = hebrewElapsedDays(year)
        val next = hebrewElapsedDays(year + 1)
        return when {
            next - present == 356L -> 2L
            present - last == 382L -> 1L
            else -> 0L
        }
    }

    /** JDN of 1 Tishrei of the given Anno Mundi year. */
    fun hebrewNewYear(year: Int): Long =
        HEBREW_EPOCH_JDN + hebrewElapsedDays(year) + hebrewCorrection(year)

    fun isHebrewLeap(year: Int): Boolean = (7 * year + 1).mod(19) < 7

    private fun hebrewMonthLengths(year: Int): IntArray {
        val length = (hebrewNewYear(year + 1) - hebrewNewYear(year)).toInt()
        val cheshvan = if (length == 355 || length == 385) 30 else 29
        val kislev = if (length == 353 || length == 383) 29 else 30
        return if (isHebrewLeap(year)) {
            intArrayOf(30, cheshvan, kislev, 29, 30, 30, 29, 30, 29, 30, 29, 30, 29)
        } else {
            intArrayOf(30, cheshvan, kislev, 29, 30, 29, 30, 29, 30, 29, 30, 29)
        }
    }

    /** Months are numbered from Tishrei, the way a Hebrew calendar is printed. */
    fun hebrew(year: Int, month: Int, day: Int): CalendarSystems.SimpleDate {
        val jdn = CalendarSystems.gregorianToJdn(year, month, day)
        var hy = ((jdn - HEBREW_EPOCH_JDN) * 400 / 146100).toInt() + 3761
        while (hebrewNewYear(hy) > jdn) hy--
        while (hebrewNewYear(hy + 1) <= jdn) hy++
        var offset = (jdn - hebrewNewYear(hy)).toInt()
        val lengths = hebrewMonthLengths(hy)
        var m = 0
        while (offset >= lengths[m]) {
            offset -= lengths[m]
            m++
        }
        return CalendarSystems.SimpleDate(hy, m + 1, offset + 1)
    }

    fun hebrewMonthName(year: Int, month: Int): String =
        if (isHebrewLeap(year)) HEBREW_MONTHS_LEAP[month - 1] else HEBREW_MONTHS[month - 1]

    private val HEBREW_MONTHS = listOf(
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul",
    )
    private val HEBREW_MONTHS_LEAP = listOf(
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar I", "Adar II",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul",
    )

    // ---- Persian (Solar Hijri) ----

    /**
     * Borkowski's leap-year breakpoints: the years at which the 33-year cycle
     * pattern shifts. Exact for 1178–3177 AP, far past anything the tool shows.
     */
    private val PERSIAN_BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private class PersianYear(val leap: Int, val gregorianYear: Int, val march: Int)

    private fun persianYear(jy: Int): PersianYear {
        val gy = jy + 621
        var leapJ = -14
        var jp = PERSIAN_BREAKS[0]
        var jump = 0
        for (i in 1 until PERSIAN_BREAKS.size) {
            val jm = PERSIAN_BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ++
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = ((n + 1).mod(33) - 1).mod(4)
        if (leap == -1) leap = 4
        return PersianYear(leap, gy, march)
    }

    fun persianToJdn(jy: Int, jm: Int, jd: Int): Long {
        val year = persianYear(jy)
        return CalendarSystems.gregorianToJdn(year.gregorianYear, 3, year.march) +
            (jm - 1) * 31L - (jm / 7) * (jm - 7).toLong() + jd - 1
    }

    fun persian(year: Int, month: Int, day: Int): CalendarSystems.SimpleDate {
        val jdn = CalendarSystems.gregorianToJdn(year, month, day)
        val gy = CalendarSystems.jdnToGregorian(jdn).year
        var jy = gy - 621
        val info = persianYear(jy)
        var k = (jdn - CalendarSystems.gregorianToJdn(gy, 3, info.march)).toInt()
        if (k >= 0) {
            if (k <= 185) return CalendarSystems.SimpleDate(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy--
            k += 179
            // The leap flag belongs to the year we started from, not the one
            // we just stepped back into.
            if (info.leap == 1) k++
        }
        return CalendarSystems.SimpleDate(jy, 7 + k / 30, k % 30 + 1)
    }

    private val PERSIAN_MONTHS = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
    )

    // ---- Hindu (Indian national / Saka) ----

    /**
     * The Indian national calendar: a solar year starting on 22 March (21 in a
     * Gregorian leap year), Saka era = Gregorian − 78. Chaitra takes the leap
     * day.
     */
    fun saka(year: Int, month: Int, day: Int): CalendarSystems.SimpleDate {
        val jdn = CalendarSystems.gregorianToJdn(year, month, day)
        var gy = year
        var leap = CalendarSystems.isGregorianLeap(gy)
        var start = CalendarSystems.gregorianToJdn(gy, 3, if (leap) 21 else 22)
        if (jdn < start) {
            gy--
            leap = CalendarSystems.isGregorianLeap(gy)
            start = CalendarSystems.gregorianToJdn(gy, 3, if (leap) 21 else 22)
        }
        var offset = (jdn - start).toInt()
        val lengths = intArrayOf(
            if (leap) 31 else 30, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 30,
        )
        var m = 0
        while (offset >= lengths[m]) {
            offset -= lengths[m]
            m++
        }
        return CalendarSystems.SimpleDate(gy - 78, m + 1, offset + 1)
    }

    private val SAKA_MONTHS = listOf(
        "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
        "Ashwin", "Kartika", "Agrahayana", "Pausha", "Magha", "Phalguna",
    )

    // ---- Japanese eras ----

    class JapaneseEra(val kanji: String, val romaji: String, val year: Int)

    fun japaneseEra(year: Int, month: Int, day: Int): JapaneseEra {
        val jdn = CalendarSystems.gregorianToJdn(year, month, day)
        for (era in JAPANESE_ERAS) {
            if (jdn >= era.startJdn) {
                return JapaneseEra(era.kanji, era.romaji, year - era.startYear + 1)
            }
        }
        // Before Meiji the lunisolar calendar was in force; showing a Gregorian
        // year with no era is honest, and nobody scrolls there anyway.
        return JapaneseEra("", "CE", year)
    }

    private class EraDef(
        val kanji: String,
        val romaji: String,
        val startYear: Int,
        val startMonth: Int,
        val startDay: Int,
    ) {
        val startJdn: Long = CalendarSystems.gregorianToJdn(startYear, startMonth, startDay)
    }

    /** Newest first — [japaneseEra] takes the first era that has already begun. */
    private val JAPANESE_ERAS = listOf(
        EraDef("令和", "Reiwa", 2019, 5, 1),
        EraDef("平成", "Heisei", 1989, 1, 8),
        EraDef("昭和", "Shōwa", 1926, 12, 25),
        EraDef("大正", "Taishō", 1912, 7, 30),
        EraDef("明治", "Meiji", 1868, 9, 8),
    )

    // ---- Chinese (lunisolar, astronomical) ----

    class ChineseDate(
        /** The sexagenary year, as the Gregorian year its first month starts in. */
        val year: Int,
        val month: Int,
        val leapMonth: Boolean,
        val day: Int,
    )

    private const val SYNODIC_MONTH = 29.530588861
    private const val CHINA_OFFSET = 8.0 / 24.0

    fun chinese(year: Int, month: Int, day: Int): ChineseDate {
        val jdn = CalendarSystems.gregorianToJdn(year, month, day)
        val monthStart = newMoonOnOrBefore(jdn)
        val sui = suiFor(monthStart)
        val index = ((monthStart - sui.month11) / SYNODIC_MONTH).roundToInt()
        // A leap month repeats the number of the month before it, so every
        // month from the leap one on is pushed back by one.
        val offset = if (sui.leapIndex in 1..index) index - 1 else index
        return ChineseDate(
            year = if (monthStart < sui.month1) sui.startYear - 1 else sui.startYear,
            month = (11 + offset - 1).mod(12) + 1,
            leapMonth = sui.leapIndex == index,
            day = (jdn - monthStart).toInt() + 1,
        )
    }

    fun chineseMonthName(date: ChineseDate): String =
        (if (date.leapMonth) "闰" else "") + CHINESE_MONTHS[date.month - 1]

    /** Heavenly stem + earthly branch, e.g. 丙午 for 2026. */
    fun sexagenary(year: Int): String =
        "${CHINESE_STEMS[(year - 4).mod(10)]}${CHINESE_BRANCHES[(year - 4).mod(12)]}"

    fun zodiac(year: Int): String = CHINESE_ZODIAC[(year - 4).mod(12)]

    /**
     * One "sui" — the span between two winter solstices — which is what the
     * leap-month rule is defined over. Computing it costs a couple of dozen
     * solar-term solves, so the most recent one is kept; a month of grid cells
     * all land in the same sui.
     */
    private class Sui(
        val month11: Long,
        val leapIndex: Int,
        val month1: Long,
        val startYear: Int,
        val nextMonth11: Long,
    )

    // Two slots, because a December grid straddles two sui: one for the cells
    // before that month's new moon and one for the rest. A single slot would
    // thrash and recompute the solar terms for every cell.
    private var suiCacheA: Sui? = null
    private var suiCacheB: Sui? = null

    @Synchronized
    private fun suiFor(monthStart: Long): Sui {
        suiCacheA?.let { if (monthStart >= it.month11 && monthStart < it.nextMonth11) return it }
        suiCacheB?.let {
            if (monthStart >= it.month11 && monthStart < it.nextMonth11) {
                // Promote, so the pair holds the two most recently used.
                suiCacheB = suiCacheA
                suiCacheA = it
                return it
            }
        }
        val solsticeYear = CalendarSystems.jdnToGregorian(monthStart).year
        var solstice = winterSolstice(solsticeYear)
        if (solstice > monthStart) solstice = winterSolstice(solsticeYear - 1)
        val month11 = newMoonOnOrBefore(solstice)
        val nextSolstice = winterSolstice(CalendarSystems.jdnToGregorian(month11 + 370).year)
        val nextMonth11 = newMoonOnOrBefore(nextSolstice)
        val monthCount = ((nextMonth11 - month11) / SYNODIC_MONTH).roundToInt()
        val firstMoon = newMoonIndexOnOrBefore(month11)
        var leapIndex = -1
        if (monthCount == 13) {
            val starts = LongArray(14) { localDay(newMoonJde(firstMoon + it)) }
            val terms = majorSolarTermDays(month11, nextMonth11)
            // Month 11 always holds the solstice, so the search starts at 1:
            // the leap month is the first one with no major solar term in it.
            for (i in 1 until 13) {
                if (terms.none { it >= starts[i] && it < starts[i + 1] }) {
                    leapIndex = i
                    break
                }
            }
        }
        // Month 1 is two new moons after month 11 unless a leap month landed in
        // between (a leap 11th or 12th month), which pushes it out by one.
        val month1Index = if (leapIndex == 1 || leapIndex == 2) 3 else 2
        val month1 = localDay(newMoonJde(firstMoon + month1Index))
        val sui = Sui(
            month11 = month11,
            leapIndex = leapIndex,
            month1 = month1,
            startYear = CalendarSystems.jdnToGregorian(month1).year,
            nextMonth11 = nextMonth11,
        )
        suiCacheB = suiCacheA
        suiCacheA = sui
        return sui
    }

    /** The JDN, in China standard time, of the new moon starting [jdn]'s month. */
    private fun newMoonOnOrBefore(jdn: Long): Long =
        localDay(newMoonJde(newMoonIndexOnOrBefore(jdn)))

    private fun newMoonIndexOnOrBefore(jdn: Long): Long {
        var k = floor((jdn - 2451550.0) / SYNODIC_MONTH).toLong() + 2
        while (localDay(newMoonJde(k)) > jdn) k--
        return k
    }

    /** The calendar day in China standard time that contains a TT instant. */
    private fun localDay(jde: Double): Long =
        floor(jde - deltaTSeconds(jde) / 86400.0 + 0.5 + CHINA_OFFSET).toLong()

    private fun winterSolstice(year: Int): Long = localDay(solarTerm(year, 270.0))

    /**
     * The days in [start, end) holding a zhongqi — a solar longitude that is a
     * multiple of 30°. Solved once for the whole sui rather than per candidate
     * month: the leap-month rule asks the same question twelve times over.
     */
    private fun majorSolarTermDays(start: Long, end: Long): LongArray {
        val year = CalendarSystems.jdnToGregorian(start).year
        val days = ArrayList<Long>(14)
        for (y in year - 1..year + 1) {
            for (i in 0 until 12) {
                val term = localDay(solarTerm(y, i * 30.0))
                if (term in start until end) days.add(term)
            }
        }
        return days.toLongArray()
    }

    /** The instant (TT) the sun's apparent longitude reaches [degrees], near [year]. */
    private fun solarTerm(year: Int, degrees: Double): Double {
        var jde = CalendarSystems.gregorianToJdn(year, 1, 1).toDouble() +
            (degrees - 280.0).mod(360.0) * 365.2422 / 360.0
        repeat(60) {
            val diff = ((solarLongitude(jde) - degrees + 180.0).mod(360.0)) - 180.0
            if (abs(diff) < 1e-7) return jde
            jde -= diff * 365.2422 / 360.0
        }
        return jde
    }

    /** Apparent geometric longitude of the sun (Meeus ch. 25, low accuracy). */
    private fun solarLongitude(jde: Double): Double {
        val t = (jde - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sinDeg(m) +
            (0.019993 - 0.000101 * t) * sinDeg(2 * m) +
            0.000289 * sinDeg(3 * m)
        val omega = 125.04 - 1934.136 * t
        return (l0 + c - 0.00569 - 0.00478 * sinDeg(omega)).mod(360.0)
    }

    /** Time (TT) of the k-th new moon since 2000 Jan 6 (Meeus ch. 49). */
    private fun newMoonJde(k: Long): Double {
        val kd = k.toDouble()
        val t = kd / 1236.85
        val base = 2451550.09766 + 29.530588861 * kd + 0.00015437 * t * t -
            0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = 2.5534 + 29.10535670 * kd - 0.0000014 * t * t - 0.00000011 * t * t * t
        val mp = 201.5643 + 385.81693528 * kd + 0.0107582 * t * t +
            0.00001238 * t * t * t - 0.000000058 * t * t * t * t
        val f = 160.7108 + 390.67050284 * kd - 0.0016118 * t * t -
            0.00000227 * t * t * t + 0.000000011 * t * t * t * t
        val omega = 124.7746 - 1.56375588 * kd + 0.0020672 * t * t + 0.00000215 * t * t * t
        val correction = -0.40720 * sinDeg(mp) +
            0.17241 * e * sinDeg(m) +
            0.01608 * sinDeg(2 * mp) +
            0.01039 * sinDeg(2 * f) +
            0.00739 * e * sinDeg(mp - m) -
            0.00514 * e * sinDeg(mp + m) +
            0.00208 * e * e * sinDeg(2 * m) -
            0.00111 * sinDeg(mp - 2 * f) -
            0.00057 * sinDeg(mp + 2 * f) +
            0.00056 * e * sinDeg(2 * mp + m) -
            0.00042 * sinDeg(3 * mp) +
            0.00042 * e * sinDeg(m + 2 * f) +
            0.00038 * e * sinDeg(m - 2 * f) -
            0.00024 * e * sinDeg(2 * mp - m) -
            0.00017 * sinDeg(omega) -
            0.00007 * sinDeg(mp + 2 * m) +
            0.00004 * sinDeg(2 * mp - 2 * f) +
            0.00004 * sinDeg(3 * m) +
            0.00003 * sinDeg(mp + m - 2 * f) +
            0.00003 * sinDeg(2 * mp + 2 * f) -
            0.00003 * sinDeg(mp + m + 2 * f) +
            0.00003 * sinDeg(mp - m + 2 * f) -
            0.00002 * sinDeg(mp - m - 2 * f) -
            0.00002 * sinDeg(3 * mp + m) +
            0.00002 * sinDeg(4 * mp)
        val planetary = 0.000325 * sinDeg(299.77 + 0.107408 * kd - 0.009173 * t * t) +
            0.000165 * sinDeg(251.88 + 0.016321 * kd) +
            0.000164 * sinDeg(251.83 + 26.651886 * kd) +
            0.000126 * sinDeg(349.42 + 36.412478 * kd) +
            0.000110 * sinDeg(84.66 + 18.206239 * kd) +
            0.000062 * sinDeg(141.74 + 53.303771 * kd) +
            0.000060 * sinDeg(207.14 + 2.453732 * kd) +
            0.000056 * sinDeg(154.84 + 7.306860 * kd) +
            0.000047 * sinDeg(34.52 + 27.261239 * kd) +
            0.000042 * sinDeg(207.19 + 0.121824 * kd) +
            0.000040 * sinDeg(291.34 + 1.844379 * kd) +
            0.000037 * sinDeg(161.72 + 24.198154 * kd) +
            0.000035 * sinDeg(239.56 + 25.513099 * kd) +
            0.000023 * sinDeg(331.55 + 3.592518 * kd)
        return base + correction + planetary
    }

    /** TT − UT in seconds (Espenak/Meeus fits); a minute either way at worst. */
    private fun deltaTSeconds(jde: Double): Double {
        val y = 2000 + (jde - 2451545.0) / 365.25
        return when {
            y < 1900 -> { val t = (y - 1820) / 100.0; -20 + 32 * t * t }
            y < 1920 -> {
                val t = y - 1900
                -2.79 + 1.494119 * t - 0.0598939 * t * t + 0.0061966 * t * t * t -
                    0.000197 * t * t * t * t
            }
            y < 1941 -> {
                val t = y - 1920
                21.20 + 0.84493 * t - 0.076100 * t * t + 0.0020936 * t * t * t
            }
            y < 1961 -> {
                val t = y - 1950
                29.07 + 0.407 * t - t * t / 233 + t * t * t / 2547
            }
            y < 1986 -> {
                val t = y - 1975
                45.45 + 1.067 * t - t * t / 260 - t * t * t / 718
            }
            y < 2005 -> {
                val t = y - 2000
                63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t +
                    0.000651814 * t * t * t * t + 0.00002373599 * t * t * t * t * t
            }
            y < 2050 -> { val t = y - 2000; 62.92 + 0.32217 * t + 0.005589 * t * t }
            y < 2150 -> -20 + 32 * ((y - 1820) / 100.0) * ((y - 1820) / 100.0) - 0.5628 * (2150 - y)
            else -> { val t = (y - 1820) / 100.0; -20 + 32 * t * t }
        }
    }

    private fun sinDeg(degrees: Double): Double = sin(degrees * Math.PI / 180.0)

    private val CHINESE_MONTHS = listOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月",
    )
    private val CHINESE_DAYS = listOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )
    private val CHINESE_STEMS = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val CHINESE_BRANCHES =
        listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val CHINESE_ZODIAC = listOf(
        "🐀 Rat", "🐂 Ox", "🐅 Tiger", "🐇 Rabbit", "🐉 Dragon", "🐍 Snake",
        "🐎 Horse", "🐐 Goat", "🐒 Monkey", "🐓 Rooster", "🐕 Dog", "🐖 Pig",
    )

    private val GREGORIAN_MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
