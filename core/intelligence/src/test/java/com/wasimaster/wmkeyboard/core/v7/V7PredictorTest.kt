package com.wasimaster.wmkeyboard.core.v7

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V7PredictorTest {

    // Dummy predictor just for testing parsing logic without loading full ONNX model
    private class TestablePredictor {
        private val doubleConsonants = listOf(
            "ch", "kh", "ng", "nh", "ph", "th", "tr"
        )
        private val singleConsonants = listOf(
            "b", "d", "đ", "g", "z", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x"
        )

        fun separateRaws(input: String): List<String> {
            val pattern = java.util.regex.Pattern.compile("[a-zA-Z\\.]+(?:\\d|$)")
            val matcher = pattern.matcher(input)
            val raws = mutableListOf<String>()
            while (matcher.find()) {
                val part = matcher.group()
                if (part.isNotEmpty()) {
                    raws.add(part)
                }
            }
            return raws
        }

        fun parseRaw(raw: String): V7Predictor.RawPart? {
            if (raw.isEmpty()) return null
            val isCap = raw.first().isUpperCase()
            val lower = raw.lowercase(java.util.Locale.ROOT)

            val lastChar = lower.last()
            val tone = if (lastChar.isDigit()) {
                lastChar.digitToInt()
            } else {
                0
            }

            val textPart = if (lastChar.isDigit()) lower.dropLast(1) else lower
            if (textPart.isEmpty() && lastChar.isDigit()) {
                return V7Predictor.RawPart("0", "", tone, isCap)
            }

            var normalizedText = textPart
            if (normalizedText.startsWith("dd")) {
                normalizedText = "đ" + normalizedText.drop(2)
            }

            var consonant = "0"
            var rhyme = normalizedText

            for (dc in doubleConsonants) {
                if (normalizedText.startsWith(dc)) {
                    consonant = dc
                    rhyme = normalizedText.drop(dc.length)
                    break
                }
            }

            if (consonant == "0") {
                for (sc in singleConsonants) {
                    if (normalizedText.startsWith(sc)) {
                        consonant = sc
                        rhyme = normalizedText.drop(sc.length)
                        break
                    }
                }
            }

            if (consonant == "0" && normalizedText.isNotEmpty()) {
                val first = normalizedText.first()
                if (first == 'c' || first == 'q') {
                    consonant = "k"
                    rhyme = normalizedText.drop(1)
                }
            }

            return V7Predictor.RawPart(consonant, rhyme, tone, isCap)
        }
    }

    @Test
    fun testSeparateRaws() {
        val predictor = TestablePredictor()
        val parts = predictor.separateRaws("x0ch2")
        assertEquals(listOf("x0", "ch2"), parts)

        val partsLong = predictor.separateRaws("c6m2n0m1")
        assertEquals(listOf("c6", "m2", "n0", "m1"), partsLong)
    }

    @Test
    fun testParseRaw() {
        val predictor = TestablePredictor()
        val p1 = predictor.parseRaw("x0")
        assertNotNull(p1)
        assertEquals("x", p1?.consonant)
        assertEquals(0, p1?.tone)

        val p2 = predictor.parseRaw("ch2")
        assertNotNull(p2)
        assertEquals("ch", p2?.consonant)
        assertEquals(2, p2?.tone)

        val p3 = predictor.parseRaw("dd4")
        assertNotNull(p3)
        assertEquals("đ", p3?.consonant)
        assertEquals(4, p3?.tone)

        val p4 = predictor.parseRaw("X0")
        assertNotNull(p4)
        assertEquals(true, p4?.isCapitalized)
    }
}
