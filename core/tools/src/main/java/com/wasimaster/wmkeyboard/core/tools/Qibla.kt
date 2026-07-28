package com.wasimaster.wmkeyboard.core.tools

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Great-circle bearing from a location to the Kaaba, for the compass tool. */
object Qibla {

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    /** Initial bearing in degrees clockwise from true north, 0..360. */
    fun bearing(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LAT)
        val deltaLambda = Math.toRadians(KAABA_LON - longitude)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
