package com.wasimaster.wmkeyboard.core.tools

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R

/**
 * Which days the calendar tool tints as the weekend.
 *
 * A preset rather than seven checkboxes: the world's working weeks come in a
 * handful of shapes, and a list of named ones is quicker to pick from than a
 * grid to tick. [days] is 0 = Sunday through 6 = Saturday, matching the tool's
 * own column order.
 *
 * [id] is the stored key and must not change once shipped. [labelRes] is the
 * name a screen draws; resolve it where you show it, not before.
 */
enum class Weekend(val id: String, @StringRes val labelRes: Int, val days: Set<Int>) {
    SAT_SUN("sat_sun", R.string.core_tools_weekend_sat_sun_label, setOf(6, 0)),
    FRI_SAT("fri_sat", R.string.core_tools_weekend_fri_sat_label, setOf(5, 6)),
    THU_FRI("thu_fri", R.string.core_tools_weekend_thu_fri_label, setOf(4, 5)),
    FRI("fri", R.string.core_tools_weekend_fri_label, setOf(5)),
    SAT("sat", R.string.core_tools_weekend_sat_label, setOf(6)),
    SUN("sun", R.string.core_tools_weekend_sun_label, setOf(0)),
    NONE("none", R.string.core_tools_weekend_none_label, emptySet());

    companion object {
        fun fromId(id: String?): Weekend = entries.firstOrNull { it.id == id } ?: SAT_SUN

        /**
         * The working week where the phone is, as a starting default.
         *
         * A best guess from the region and nothing more — plenty of countries
         * here have industries that keep a different week, and several have
         * changed theirs recently (the UAE moved to a Saturday–Sunday weekend in
         * 2022). It is a setting for exactly that reason; this only decides what
         * it starts as. An unlisted or unknown region gets Saturday–Sunday,
         * which is the most common arrangement worldwide.
         */
        fun forRegion(regionCode: String?): Weekend = when (regionCode?.uppercase()) {
            "BH", "DZ", "EG", "IL", "IQ", "JO", "KW", "LY", "OM", "PS", "QA", "SA", "SY", "YE" ->
                FRI_SAT
            "AF", "IR" -> THU_FRI
            "BD", "BN", "DJ", "MV", "SO" -> FRI
            "NP" -> SAT
            "IN", "MX", "PH" -> SUN
            else -> SAT_SUN
        }
    }
}

/**
 * The two alternate calendars the tool starts out showing, worked out from the
 * device's region the same way suggested languages are.
 *
 * Both slots are settings, so this only picks the opening pair. The first slot
 * is the region's own civil or cultural calendar, since that is the one whose
 * day number is worth carrying inside every cell; the second is a
 * widely-observed calendar alongside it, most often Hijri where it is used for
 * religious dates even though the civil calendar is Gregorian.
 *
 * A region with nothing particular to show gets no alternate calendars at all.
 * Two extra numbers in every cell are noise to someone who reads neither.
 */
fun defaultAltCalendars(regionCode: String?): Pair<AltCalendar, AltCalendar> =
    when (regionCode?.uppercase()) {
        "BD" -> AltCalendar.BENGALI to AltCalendar.HIJRI
        "IN", "NP" -> AltCalendar.HINDU to AltCalendar.NONE
        "IR", "AF" -> AltCalendar.PERSIAN to AltCalendar.HIJRI
        "IL" -> AltCalendar.HEBREW to AltCalendar.NONE
        "CN", "TW", "HK", "MO", "SG", "MY", "VN" -> AltCalendar.CHINESE to AltCalendar.NONE
        "JP" -> AltCalendar.JAPANESE to AltCalendar.CHINESE
        "TH", "LA", "KH", "MM", "LK" -> AltCalendar.BUDDHIST to AltCalendar.NONE
        "AE", "BH", "DZ", "EG", "IQ", "JO", "KW", "LY", "MA", "OM", "PK", "PS",
        "QA", "SA", "SD", "SY", "TN", "YE", "ID", "BN", "MV", "SO", "DJ",
        -> AltCalendar.HIJRI to AltCalendar.NONE
        else -> AltCalendar.NONE to AltCalendar.NONE
    }
