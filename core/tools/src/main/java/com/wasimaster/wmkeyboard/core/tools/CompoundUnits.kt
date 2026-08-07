package com.wasimaster.wmkeyboard.core.tools

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

/**
 * Feet and inches — the one place a fractional unit is normally read as a
 * whole major plus a minor. "1 m" is 3 ft 3.37 in to anybody who measures in
 * feet; "3.2808399 ft" is a number, not a height.
 *
 * Pure and resource-free, like the rest of [SmartSuggest]'s neighbourhood.
 * The word forms are the English spellings [SmartSuggest] already accepts as
 * aliases, and are only ever used to echo back a unit the user spelled out in
 * English — a symbol-typed trigger gets symbols back.
 */
object CompoundUnits {

    /** How the two halves are named. */
    enum class Style {
        /** "3 ft 3.37 in" — the default, and what the converter panel shows. */
        SYMBOL,

        /** "3 feet 3.37 inches", for a trigger that spelled its unit out. */
        WORD,

        /** "3'3.37\"" — the narrowest form, for a chip that has to fit. */
        PRIME,
    }

    /** One unit's three spellings. */
    private data class Spelling(
        val symbol: String,
        val singular: String,
        val plural: String,
        val prime: String,
    )

    private data class Compound(val major: Spelling, val minor: Spelling, val per: Double)

    private val compounds = listOf(
        Compound(
            major = Spelling("ft", "foot", "feet", "'"),
            minor = Spelling("in", "inch", "inches", "\""),
            per = 12.0,
        ),
    )

    /**
     * Above this the minor half is noise — a light year is 3.1e16 ft, and no
     * one wants the inches. The plain decimal reading takes over instead.
     */
    private const val MAX_MAJOR = 1e9

    private fun compoundFor(majorSymbol: String): Compound? =
        compounds.firstOrNull { it.major.symbol == majorSymbol }

    /** Whether [majorSymbol] has a compound reading at all. */
    fun applies(majorSymbol: String): Boolean = compoundFor(majorSymbol) != null

    /**
     * [value] of [majorSymbol] written as a whole major plus a minor, or null
     * when the unit has no compound reading (or the number is too big for one
     * to mean anything). [minorDecimals] is how precisely the minor half is
     * written; 0 gives whole inches.
     */
    fun format(
        value: Double,
        majorSymbol: String,
        style: Style,
        minorDecimals: Int,
    ): String? {
        val compound = compoundFor(majorSymbol) ?: return null
        val parts = split(value, compound, minorDecimals) ?: return null
        val (sign, major, minor) = parts
        val majorText = word(major, compound.major, style)
        val minorText = word(minor, compound.minor, style)
        return when {
            // "0 ft" rather than a bare "0": the unit is the whole answer.
            major == 0.0 && minor == 0.0 -> sign + majorText
            major == 0.0 -> sign + minorText
            minor == 0.0 -> sign + majorText
            // The prime form is the last thing tried when width is short, so
            // it spends nothing on a space: 5'11" is how it is written anyway.
            style == Style.PRIME -> "$sign$majorText$minorText"
            else -> "$sign$majorText $minorText"
        }
    }

    /**
     * The value the text from [format] actually stands for. The chip ladder
     * marks a tier "~" only when its rounding moved the number, and whole
     * inches move it — this is what that comparison is against.
     */
    fun snap(value: Double, majorSymbol: String, minorDecimals: Int): Double {
        val compound = compoundFor(majorSymbol) ?: return value
        val parts = split(value, compound, minorDecimals) ?: return value
        val (sign, major, minor) = parts
        val magnitude = major + minor / compound.per
        return if (sign.isEmpty()) magnitude else -magnitude
    }

    /** The sign, the whole majors and the minors left over, after rounding. */
    private data class Parts(val sign: String, val major: Double, val minor: Double)

    private fun split(value: Double, compound: Compound, minorDecimals: Int): Parts? {
        if (!value.isFinite()) return null
        val magnitude = abs(value)
        if (magnitude >= MAX_MAJOR) return null
        var major = floor(magnitude)
        val step = 10.0.pow(minorDecimals.coerceIn(0, 6))
        var minor = round((magnitude - major) * compound.per * step) / step
        // Rounding the minor can fill a whole major: 1.99999 ft is 2 ft, never
        // "1 ft 12 in".
        if (minor >= compound.per) {
            major += 1.0
            minor = 0.0
        }
        return Parts(if (value < 0) "-" else "", major, minor)
    }

    private fun word(value: Double, spelling: Spelling, style: Style): String {
        val text = CalcEngine.format(value, 6)
        return when (style) {
            Style.SYMBOL -> "$text ${spelling.symbol}"
            Style.PRIME -> "$text${spelling.prime}"
            Style.WORD -> "$text ${if (value == 1.0) spelling.singular else spelling.plural}"
        }
    }
}
