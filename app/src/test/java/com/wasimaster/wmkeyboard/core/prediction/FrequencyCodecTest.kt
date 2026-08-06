package com.wasimaster.wmkeyboard.core.prediction

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the three properties the rest of the prediction stack relies on when it
 * treats a stored frequency as a magnitude: exactness in the range where the
 * engine tests absolute thresholds, a bounded relative error above it, and
 * monotonicity — which is what makes quantising unable to reverse a ranking or
 * loosen a [PackedTrie.maxSubtree] bound.
 */
class FrequencyCodecTest {

    /** Below this the codec stores values verbatim. */
    private val exactCeiling = 2048

    @Test
    fun everyValueBelowTheMantissaWidthIsStoredExactly() {
        for (v in 0 until exactCeiling) {
            assertEquals("round($v)", v, FrequencyCodec.round(v))
        }
    }

    @Test
    fun negativeAndZeroCollapseToZero() {
        for (v in listOf(Int.MIN_VALUE, -9_999, -1, 0)) {
            assertEquals("round($v)", 0, FrequencyCodec.round(v))
        }
    }

    @Test
    fun decodeIsMonotoneAcrossEveryReachableCode() {
        // The property the branch-and-bound pruning leans on: raw codes can be
        // compared without decoding, and no rounding can swap two frequencies.
        var previous = -1
        for (code in 0..FrequencyCodec.MAX_CODE) {
            val value = FrequencyCodec.decode(code)
            assertTrue("decode($code) = $value went backwards from $previous", value >= previous)
            previous = value
        }
    }

    @Test
    fun relativeErrorStaysUnderOneIn2048() {
        var value = exactCeiling
        while (value in 1..Int.MAX_VALUE / 3) {
            val error = abs(FrequencyCodec.round(value) - value).toDouble() / value
            assertTrue("round($value) drifted $error", error <= 1.0 / 2048)
            value = value * 3 / 2 + 1
        }
    }

    @Test
    fun codesRoundTripAndTheTopOfTheIntRangeIsRepresentable() {
        for (code in 0..FrequencyCodec.MAX_CODE) {
            assertEquals("encode(decode($code))", code, FrequencyCodec.encode(FrequencyCodec.decode(code)))
        }
        assertEquals(FrequencyCodec.MAX_CODE, FrequencyCodec.encode(Int.MAX_VALUE))
        assertTrue(FrequencyCodec.decode(FrequencyCodec.MAX_CODE) > 2_000_000_000)
    }

    @Test
    fun corruptCodesAboveTheWritableRangeStayPositive() {
        // A torn or hostile file can hold any 16-bit pattern; an exponent of 31
        // would shift the implicit bit off the top of an Int.
        for (code in FrequencyCodec.MAX_CODE + 1..0xFFFF) {
            assertTrue("decode($code) went negative", FrequencyCodec.decode(code) >= 0)
        }
    }

    @Test
    fun roundingIsIdempotentSoWriteAfterBuildLosesNothing() {
        // PackedTrie.of snaps on the way in and the codec encodes on the way
        // out; the second step must be lossless or the mapped trie would drift
        // from the in-memory one it was written from.
        var value = 1
        while (value in 1..Int.MAX_VALUE / 3) {
            val once = FrequencyCodec.round(value)
            assertEquals("round(round($value))", once, FrequencyCodec.round(once))
            value = value * 3 / 2 + 1
        }
    }
}
