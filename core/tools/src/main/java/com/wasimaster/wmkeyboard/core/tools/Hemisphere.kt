package com.wasimaster.wmkeyboard.core.tools

/**
 * Which hemisphere a region sits in, for the settings whose right answer is a
 * fact about where the phone is rather than a taste.
 *
 * Only the moon tool asks so far: a waxing crescent is lit on the right from
 * London and on the left from Sydney, and a viewer who has to flip that by hand
 * has been shown the wrong moon at least once first.
 *
 * The list is the regions whose *population* is mostly south of the equator,
 * not whose land is — Indonesia and Ecuador straddle it, and both are counted
 * by where the people are. Regions that straddle it with no clear majority
 * (Kenya, Uganda, Gabon, Kiribati, São Tomé) are left northern, which is what
 * an unlisted region gets anyway.
 *
 * A default and nothing more: the setting exists because someone in Nairobi or
 * on a ship knows better than a table does.
 */
private val SOUTHERN_REGIONS = setOf(
    // South America
    "AR", "BO", "BR", "CL", "EC", "FK", "GS", "PE", "PY", "UY",
    // Africa
    "AO", "BI", "BW", "CD", "CG", "KM", "LS", "MG", "MU", "MW", "MZ",
    "NA", "RE", "RW", "SC", "SH", "SZ", "TZ", "YT", "ZA", "ZM", "ZW",
    // Oceania and the south Pacific
    "AS", "AU", "CK", "FJ", "NC", "NF", "NR", "NU", "NZ", "PF", "PG",
    "PN", "SB", "TK", "TO", "TV", "VU", "WF", "WS",
    // Asia
    "CC", "CX", "ID", "IO", "TL",
    // The far south
    "AQ", "BV", "HM", "TF",
)

/**
 * True when [regionCode] — an ISO 3166-1 alpha-2 code, as
 * `DeviceLocales.read(context).regionCodes` gives it — is a southern-hemisphere
 * region. An unknown, unlisted or absent region reads as northern, which is
 * where most of the world's phones are.
 */
fun isSouthernHemisphere(regionCode: String?): Boolean =
    regionCode?.uppercase() in SOUTHERN_REGIONS
