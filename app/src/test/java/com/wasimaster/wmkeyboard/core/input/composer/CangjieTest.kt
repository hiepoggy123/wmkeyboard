package com.wasimaster.wmkeyboard.core.input.composer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cangjie 倉頡 and Quick 速成 against one shared table. "Words" are ASCII stand-ins;
 * the radical-code lookup is script-agnostic.
 */
class CangjieTest {

    // Codes are Cangjie radical letters a-y. Quick types only first + last, so
    // a-*-b entries are the interesting ones: several codes collapse to one query.
    private val dict = CodeTableDictionary.parse(
        sequenceOf(
            "a\tA\t50",
            "ab\tAB\t80",
            "adb\tADB\t30",
            "adeb\tADEB\t10",
            "ac\tAC\t60",
            "bd\tBD\t40",
        ),
        CodeTableDictionary.CANGJIE_CODE,
    )

    @After
    fun reset() {
        CjkDictionaries.cangjie = CodeTableDictionary.EMPTY
    }

    @Test
    fun `prefix query puts an exact code first, then frequency`() {
        // "a" spells A outright, so it leads the characters that merely start
        // with that radical; those follow most-frequent first.
        assertEquals(listOf("A", "AB", "AC", "ADB", "ADEB"), dict.candidates("a"))
        // No character is spelled "ad" exactly, so frequency alone orders it.
        assertEquals(listOf("ADB", "ADEB"), dict.candidates("ad"))
        assertEquals(listOf("BD"), dict.candidates("b"))
    }

    @Test
    fun `quick matches first and last radical from the same table`() {
        // Every code starting a- and ending -b, whatever sits between: this is
        // the whole point of Quick, and it needs no second dictionary.
        assertEquals(listOf("AB", "ADB", "ADEB"), dict.quickCandidates('a', 'b'))
        assertEquals(listOf("AC"), dict.quickCandidates('a', 'c'))
        // A length-1 code matches when its single char is both first and last.
        assertEquals(listOf("A"), dict.quickCandidates('a', 'a'))
        // Nothing starts with c.
        assertEquals(emptyList<String>(), dict.quickCandidates('c', 'a'))
    }

    @Test
    fun `parse drops codes outside the cangjie alphabet`() {
        // z carries no radical, and a code longer than five is not Cangjie.
        val d = CodeTableDictionary.parse(
            sequenceOf("az\tX\t1", "abcdef\tY\t1", "ab\tZ\t1"),
            CodeTableDictionary.CANGJIE_CODE,
        )
        assertEquals(listOf("Z"), d.candidates("a"))
    }

    @Test
    fun `composer normalizes radical glyphs and letters alike`() {
        CjkDictionaries.cangjie = dict
        // The on-screen keys type letters; a hardware keyboard or a pasted glyph
        // reaches the same code.
        assertEquals(listOf("A", "AB", "AC", "ADB", "ADEB"), CangjieComposer.candidates("a"))
        assertEquals(listOf("A", "AB", "AC", "ADB", "ADEB"), CangjieComposer.candidates("日"))
        assertEquals(listOf("ADB", "ADEB"), CangjieComposer.candidates("日木"))
        // Composing region shows radicals, not the raw letters.
        assertEquals("日木", CangjieComposer.composeBuffer("ad"))
        assertTrue(CangjieComposer.isConversion)
        // One code run spells one character, so a commit takes the whole buffer.
        assertEquals(2, CangjieComposer.consumedFor("ad", "ADB"))
    }

    @Test
    fun `quick composer falls back to a prefix query on the first key`() {
        CjkDictionaries.cangjie = dict
        // With one key down the last radical is unknown, so this is still a
        // plain prefix match — identical to full Cangjie at that point.
        assertEquals(CangjieComposer.candidates("a"), CangjieQuickComposer.candidates("a"))
        // The second key turns it into a first-and-last match, which is where
        // the two methods diverge: ADB/ADEB are unreachable by prefix "ab".
        assertEquals(listOf("AB", "ADB", "ADEB"), CangjieQuickComposer.candidates("ab"))
        assertEquals(listOf("AB"), CangjieComposer.candidates("ab"))
        assertEquals("日月", CangjieQuickComposer.composeBuffer("ab"))
        assertTrue(CangjieQuickComposer.isConversion)
    }

    @Test
    fun `no table loaded yields no candidates`() {
        assertEquals(emptyList<String>(), CangjieComposer.candidates("a"))
        assertEquals(emptyList<String>(), CangjieQuickComposer.candidates("ab"))
    }
}
