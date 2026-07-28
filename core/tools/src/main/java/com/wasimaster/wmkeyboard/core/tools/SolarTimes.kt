package com.wasimaster.wmkeyboard.core.tools

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset for one day, as minutes past local midnight. */
data class SolarTimes(val sunriseMinutes: Int, val sunsetMinutes: Int)

/**
 * Sunrise and sunset from a latitude, a longitude and a date — the NOAA
 * approximation, which is accurate to about a minute anywhere people live.
 *
 * Local rather than fetched. The weather tool already knows how to ask a server
 * for these, but the keyboard needs an answer *while it is drawing itself*, on
 * whatever network the phone has at that moment, for a theme that must not
 * flicker — so this trades a minute of precision for an answer that is always
 * there and costs nothing.
 *
 * Returns null inside the polar circles on the days the sun never rises or
 * never sets: there is no sunrise to schedule against, and callers fall back to
 * the system light/dark setting rather than pretending otherwise.
 */
object SolarCalculator {

    /** Sun centre 0.833° below the horizon — the standard refraction allowance. */
    private const val ZENITH = 90.833

    private const val MINUTES_PER_DAY = 24 * 60

    fun forDate(
        latitude: Double,
        longitude: Double,
        timeMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): SolarTimes? {
        if (abs(latitude) > 90 || abs(longitude) > 180) return null
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        // Offset for *this* instant, so a schedule stays right across a DST
        // change instead of drifting by an hour for half the year.
        val offsetMinutes = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) /
            60_000.0

        val sunrise = solarEvent(latitude, longitude, dayOfYear, offsetMinutes, rising = true)
        val sunset = solarEvent(latitude, longitude, dayOfYear, offsetMinutes, rising = false)
        if (sunrise == null || sunset == null) return null
        return SolarTimes(sunrise, sunset)
    }

    /**
     * One event as minutes past local midnight, or null when the sun neither
     * rises nor sets that day at that latitude.
     */
    @Suppress("detekt.LongParameterList")
    private fun solarEvent(
        latitude: Double,
        longitude: Double,
        dayOfYear: Int,
        offsetMinutes: Double,
        rising: Boolean,
    ): Int? {
        val longitudeHour = longitude / 15.0
        val approximate = dayOfYear + ((if (rising) 6.0 else 18.0) - longitudeHour) / 24.0

        // Sun's mean anomaly, true longitude, right ascension.
        val meanAnomaly = 0.9856 * approximate - 3.289
        var trueLongitude = meanAnomaly + 1.916 * sinDeg(meanAnomaly) +
            0.020 * sinDeg(2 * meanAnomaly) + 282.634
        trueLongitude = trueLongitude.wrapDegrees()

        var rightAscension = atanDeg(0.91764 * tanDeg(trueLongitude)).wrapDegrees()
        // Right ascension has to land in the same quadrant as the longitude.
        val longitudeQuadrant = floor(trueLongitude / 90.0) * 90.0
        val ascensionQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + (longitudeQuadrant - ascensionQuadrant)) / 15.0

        val sinDeclination = 0.39782 * sinDeg(trueLongitude)
        val cosDeclination = cosDeg(asinDeg(sinDeclination))

        val cosHourAngle = (cosDeg(ZENITH) - sinDeclination * sinDeg(latitude)) /
            (cosDeclination * cosDeg(latitude))
        // Out of range: midnight sun, or polar night.
        if (cosHourAngle > 1 || cosHourAngle < -1) return null

        val hourAngle = (if (rising) 360.0 - acosDeg(cosHourAngle) else acosDeg(cosHourAngle)) / 15.0
        val localMeanTime = hourAngle + rightAscension - 0.06571 * approximate - 6.622
        val utcHours = (localMeanTime - longitudeHour).wrapHours()
        val minutes = (utcHours * 60.0 + offsetMinutes).toInt()
        return ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    private fun Double.wrapDegrees(): Double = ((this % 360.0) + 360.0) % 360.0
    private fun Double.wrapHours(): Double = ((this % 24.0) + 24.0) % 24.0

    private fun sinDeg(degrees: Double) = sin(Math.toRadians(degrees))
    private fun cosDeg(degrees: Double) = cos(Math.toRadians(degrees))
    private fun tanDeg(degrees: Double) = tan(Math.toRadians(degrees))
    private fun asinDeg(value: Double) = Math.toDegrees(asin(value))
    private fun acosDeg(value: Double) = Math.toDegrees(acos(value))
    private fun atanDeg(value: Double) = Math.toDegrees(kotlin.math.atan(value))
}
