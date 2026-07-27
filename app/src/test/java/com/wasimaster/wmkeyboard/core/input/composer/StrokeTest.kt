package com.wasimaster.wmkeyboard.core.input.composer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `prefix query puts an exact code first, then frequency`() {
        // "1" is A's whole code, so A leads even though B/C are more frequent;
        // the rest of the prefix follows most-frequent first.
        assertEquals(listOf("A", "B", "C", "D"), dict.candidates("1"))
        assertEquals(listOf("B", "C", "D"), dict.candidates("12"))
        assertEquals(listOf("E"), dict.candidates("2"))
    }

    /**
     * The shipped pack ranks 二 (code `11`) 271st inside prefix `11`, behind
     * common characters that merely start with two horizontals — so typing its
     * exact code used to leave it off a three-slot strip entirely.
     */
    @Test
    fun `a rare character still leads its own exact code`() {
        val d = CodeTableDictionary.parse(
            sequenceOf("11\t二\t79", "1134\t天\t954", "112\t于\t947", "1132\t开\t922"),
            CodeTableDictionary.STROKE_CODE,
        )
        assertEquals("二", d.candidates("11").first())
        // A prefix nobody spells exactly is still ordered by frequency alone.
        assertEquals(listOf("天", "于", "开", "二"), d.candidates("1"))
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
        assertEquals(listOf("A", "B", "C", "D"), StrokeComposer.candidates("h"))   // h → 1
        assertEquals(listOf("B", "C", "D"), StrokeComposer.candidates("hs"))        // hs → 12
        assertEquals(listOf("B", "C", "D"), StrokeComposer.candidates("12"))
        // Composing region renders stroke glyphs, not the typed letters.
        assertEquals("一丨", StrokeComposer.composeBuffer("hs"))
        assertTrue(StrokeComposer.isConversion)
    }

    /**
     * The wildcard is punctuation, so unless the composer claims it the service
     * treats it as an ordinary key: it commits whatever was composing and types a
     * literal star. On device that turned 一丨 + `*` into "地*" with an empty
     * strip instead of widening the search.
     */
    @Test
    fun `the wildcard key belongs in the composing buffer`() {
        assertTrue(StrokeComposer.buffersChar('*'))
        assertTrue(StrokeComposer.buffersChar('?'))
        assertTrue(StrokeComposer.buffersChar('＊'))
        // Ordinary punctuation still commits and types through.
        assertFalse(StrokeComposer.buffersChar(','))
        assertFalse(StrokeComposer.buffersChar('.'))

        CjkDictionaries.stroke = dict
        assertEquals(listOf("B", "C", "D"), StrokeComposer.candidates("1*"))
        assertEquals("一＊", StrokeComposer.composeBuffer("1*"))
    }
}
