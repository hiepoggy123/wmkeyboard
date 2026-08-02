package com.wasimaster.wmkeyboard.core.tools

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R
import kotlin.math.PI
import kotlin.math.cos

/**
 * Mean-cycle moon phase arithmetic. Accurate to a few hours — plenty for a
 * keyboard tool — and fully offline: everything derives from a reference
 * new moon and the mean synodic month.
 */
object MoonPhase {

    /** Mean length of a lunation in days. */
    const val SYNODIC_DAYS = 29.530588853

    /** Reference new moon: 2000-01-06 18:14 UTC. */
    private const val EPOCH_MILLIS = 947_182_440_000L

    private const val DAY_MILLIS = 86_400_000.0

    data class Info(
        /** Days since the last new moon, 0 until [SYNODIC_DAYS]. */
        val ageDays: Double,
        /** Position in the cycle, 0 = new, 0.5 = full, exclusive of 1. */
        val cycleFraction: Double,
        /** Lit fraction of the disc, 0..1. */
        val illumination: Double,
        /** One of the eight named phases, 0 = new moon .. 7 = waning crescent. */
        val phaseIndex: Int,
        val nextNewMoonMillis: Long,
        val nextFullMoonMillis: Long,
    )

    fun at(timeMillis: Long): Info {
        val days = (timeMillis - EPOCH_MILLIS) / DAY_MILLIS
        val fraction = ((days / SYNODIC_DAYS) % 1.0 + 1.0) % 1.0
        val age = fraction * SYNODIC_DAYS
        val illumination = (1.0 - cos(2.0 * PI * fraction)) / 2.0
        val phaseIndex = (Math.round(fraction * 8.0).toInt()) % 8
        val nextNew = timeMillis + ((1.0 - fraction) * SYNODIC_DAYS * DAY_MILLIS).toLong()
        val toFull = if (fraction < 0.5) 0.5 - fraction else 1.5 - fraction
        val nextFull = timeMillis + (toFull * SYNODIC_DAYS * DAY_MILLIS).toLong()
        return Info(age, fraction, illumination, phaseIndex, nextNew, nextFull)
    }

    /** The name of a phase, for the screen that draws it to resolve. */
    @StringRes
    fun phaseNameRes(index: Int): Int = when (index) {
        0 -> R.string.core_tools_moon_new_label
        1 -> R.string.core_tools_moon_waxing_crescent_label
        2 -> R.string.core_tools_moon_first_quarter_label
        3 -> R.string.core_tools_moon_waxing_gibbous_label
        4 -> R.string.core_tools_moon_full_label
        5 -> R.string.core_tools_moon_waning_gibbous_label
        6 -> R.string.core_tools_moon_last_quarter_label
        else -> R.string.core_tools_moon_waning_crescent_label
    }
}
