package com.wasimaster.wmkeyboard.core.input.composer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stroke-order dictionary prefix/wildcard lookup and the stroke composer wiring. */
class StrokeTest {

    // "Words" are ASCII stand-ins; the stroke code + prefix logic is script-agnostic.
    private val dict = CodeTableDictionary.parse(
        sequenceOf(
            "1\tA\t10",
            "12\tB\t30",
            "121\tC\t20",
            "1234\tD\t5",
            "2\tE\t100",
        ),
        CodeTableDictionary.STROKE_CODE,
    )

    @After
    fun reset() {
        CjkDictionaries.stroke = CodeTableDictionary.EMPTY
    }

    @Test
    fun `prefix query returns matches frequency-ranked`() {
        assertEquals(listOf("B", "C", "A", "D"), dict.candidates("1"))
        assertEquals(listOf("B", "C", "D"), dict.candidates("12"))
        assertEquals(listOf("E"), dict.candidates("2"))
    }

    @Test
    fun `wildcard matches any single stroke`() {
        // "1." = 1 then any stroke: excludes the length-1 "1" (A).
        assertEquals(listOf("B", "C", "D"), dict.candidates("1."))
    }

    @Test
    fun `unknown prefix and malformed codes yield nothing`() {
        assertEquals(emptyList<String>(), dict.candidates("5"))
        // A code with a non-1..5 digit is dropped at parse time.
        val d = CodeTableDictionary.parse(
            sequenceOf("19\tX\t1", "13\tY\t1"),
            CodeTableDictionary.STROKE_CODE,
        )
        assertEquals(listOf("Y"), d.candidates("1"))
    }

    @Test
    fun `composer normalizes letter and digit keys and shows glyphs`() {
        // h/s/p/n/z and 1..5 both normalise to the same stroke digits.
        CjkDictionaries.stroke = dict
        assertEquals(listOf("B", "C", "A", "D"), StrokeComposer.candidates("h"))   // h → 1
        assertEquals(listOf("B", "C", "D"), StrokeComposer.candidates("hs"))        // hs → 12
        assertEquals(listOf("B", "C", "D"), StrokeComposer.candidates("12"))
        // Composing region renders stroke glyphs, not the typed letters.
        assertEquals("一丨", StrokeComposer.composeBuffer("hs"))
        assertTrue(StrokeComposer.isConversion)
    }
}
