package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feet and inches: the three spellings, the plurals, and the roundings that
 * must not produce "1 ft 12 in".
 */
class CompoundUnitsTest {

    private fun symbols(value: Double, decimals: Int = 2) =
        CompoundUnits.format(value, "ft", CompoundUnits.Style.SYMBOL, decimals)

    @Test
    fun onlyFeetTakeACompoundReading() {
        assertTrue(CompoundUnits.applies("ft"))
        assertFalse(CompoundUnits.applies("m"))
        // The square and per-second feet are their own units, not this one.
        assertFalse(CompoundUnits.applies("ft²"))
        assertFalse(CompoundUnits.applies("ft/s"))
        assertNull(CompoundUnits.format(5.0, "m", CompoundUnits.Style.SYMBOL, 2))
    }

    @Test
    fun theThreeSpellings() {
        val metre = 3.280839895
        assertEquals("3 ft 3.37 in", symbols(metre))
        assertEquals(
            "3 feet 3.37 inches",
            CompoundUnits.format(metre, "ft", CompoundUnits.Style.WORD, 2),
        )
        // The narrowest form spends nothing on a space: 3'3" is how it is
        // written anyway.
        assertEquals("3'3\"", CompoundUnits.format(metre, "ft", CompoundUnits.Style.PRIME, 0))
    }

    @Test
    fun oneOfEitherHalfIsSingular() {
        assertEquals(
            "1 foot 1 inch",
            CompoundUnits.format(13.0 / 12.0, "ft", CompoundUnits.Style.WORD, 2),
        )
        assertEquals(
            "2 feet 3 inches",
            CompoundUnits.format(2.25, "ft", CompoundUnits.Style.WORD, 2),
        )
    }

    @Test
    fun aHalfThatIsNotThereIsNotWritten() {
        assertEquals("3 ft", symbols(3.0))
        assertEquals("6 in", symbols(0.5))
        // Zero is still a length, so it keeps the unit rather than showing "0".
        assertEquals("0 ft", symbols(0.0))
    }

    @Test
    fun aRoundedMinorFillsAWholeMajorInsteadOfOverflowing() {
        // 1.99999 ft is 2 ft, never "1 ft 12 in".
        assertEquals("2 ft", symbols(1.99999))
        assertEquals("2 ft", symbols(1.999, decimals = 0))
    }

    @Test
    fun negativeLengthsKeepOneSign() {
        assertEquals("-1 ft 6 in", symbols(-1.5))
        assertEquals(-1.5, CompoundUnits.snap(-1.5, "ft", 2), 1e-9)
    }

    @Test
    fun snapReportsWhatTheTextActuallySays() {
        // Whole inches move the number, and the chip ladder marks that with
        // a "~" — this is the value the marker is decided against.
        assertEquals(3.25, CompoundUnits.snap(3.280839895, "ft", 0), 1e-9)
        assertEquals(3.0 + 3.37 / 12.0, CompoundUnits.snap(3.280839895, "ft", 2), 1e-9)
        // A unit with no compound reading is its own answer.
        assertEquals(5.0, CompoundUnits.snap(5.0, "m", 2), 1e-9)
    }

    @Test
    fun absurdlyLargeLengthsKeepTheirDecimal() {
        // A light year is 3.1e16 ft; nobody wants the inches.
        assertNull(symbols(3.1e16))
        assertNull(symbols(Double.NaN))
        assertNull(symbols(Double.POSITIVE_INFINITY))
    }
}
