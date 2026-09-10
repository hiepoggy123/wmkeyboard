package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The editor's line commands, as plain functions of a string and a selection. */
class CodeEditsTest {

    // ---- comments -----------------------------------------------------------

    @Test
    fun `a plain line gains a comment marker`() {
        val text = "x = 1"
        val edit = toggleLineComment(text, TextRange(2), "--")
        assertEquals("-- x = 1", edit.applyTo(text))
        assertEquals(TextRange(5), edit.selection)
    }

    @Test
    fun `a commented line loses its marker and one space`() {
        val text = "  -- x = 1"
        val edit = toggleLineComment(text, TextRange(8), "--")
        assertEquals("  x = 1", edit.applyTo(text))
        assertEquals(TextRange(5), edit.selection)
    }

    @Test
    fun `a block with some lines commented is commented whole`() {
        val text = "a()\n-- b()\nc()"
        assertEquals("-- a()\n-- -- b()\n-- c()", toggleLineComment(text, TextRange(0, text.length), "--").applyTo(text))
    }

    @Test
    fun `a fully commented block is uncommented whole`() {
        val text = "-- a()\n--b()"
        assertEquals("a()\nb()", toggleLineComment(text, TextRange(0, text.length), "--").applyTo(text))
    }

    @Test
    fun `the marker goes in at the shallowest indent`() {
        val text = "  if x then\n    y()\n  end"
        assertEquals(
            "  -- if x then\n  --   y()\n  -- end",
            toggleLineComment(text, TextRange(0, text.length), "--").applyTo(text),
        )
    }

    @Test
    fun `blank lines inside a block are left alone`() {
        val text = "a()\n\nb()"
        assertEquals("-- a()\n\n-- b()", toggleLineComment(text, TextRange(0, text.length), "--").applyTo(text))
    }

    @Test
    fun `a selection over the block still reaches its end`() {
        val text = "a()\nb()"
        val edit = toggleLineComment(text, TextRange(0, text.length), "--")
        val result = edit.applyTo(text)
        assertEquals(TextRange(3, result.length), edit.selection)
    }

    @Test
    fun `a selection ending at the start of a line leaves that line out`() {
        val text = "a()\nb()"
        assertEquals("-- a()\nb()", toggleLineComment(text, TextRange(0, 4), "--").applyTo(text))
    }

    // ---- duplicate and move ---------------------------------------------------

    @Test
    fun `duplicating a line puts the copy below with the caret on it`() {
        val text = "a\nbc\nd"
        val edit = duplicateLines(text, TextRange(3))
        assertEquals("a\nbc\nbc\nd", edit.applyTo(text))
        assertEquals(TextRange(6), edit.selection)
    }

    @Test
    fun `duplicating a selection copies every line it touches`() {
        val text = "a\nb\nc"
        assertEquals("a\nb\na\nb\nc", duplicateLines(text, TextRange(0, 3)).applyTo(text))
    }

    @Test
    fun `duplicating the last line with no newline`() {
        val text = "a\nb"
        val edit = duplicateLines(text, TextRange(3))
        assertEquals("a\nb\nb", edit.applyTo(text))
        assertEquals(TextRange(5), edit.selection)
    }

    @Test
    fun `moving a line up swaps it with the one above`() {
        val text = "a\nb\nc"
        val edit = moveLines(text, TextRange(2), -1)!!
        assertEquals("b\na\nc", edit.applyTo(text))
        assertEquals(TextRange(0), edit.selection)
    }

    @Test
    fun `moving a line down swaps it with the one below`() {
        val text = "a\nb\nc"
        val edit = moveLines(text, TextRange(2), 1)!!
        assertEquals("a\nc\nb", edit.applyTo(text))
        assertEquals(TextRange(4), edit.selection)
    }

    @Test
    fun `a selected block moves as one`() {
        val text = "a\nb\nc\nd"
        val edit = moveLines(text, TextRange(2, 5), 1)!!
        assertEquals("a\nd\nb\nc", edit.applyTo(text))
        assertEquals(TextRange(4, 7), edit.selection)
    }

    @Test
    fun `moving past either end gives nothing`() {
        assertNull(moveLines("a\nb", TextRange(0), -1))
        assertNull(moveLines("a\nb", TextRange(2), 1))
    }

    // ---- words, lines, the caret ---------------------------------------------

    @Test
    fun `the word, the spaces or the character at an offset`() {
        val text = "local my_name = x.y"
        assertEquals(TextRange(6, 13), wordRangeAt(text, 8))
        assertEquals(TextRange(6, 13), wordRangeAt(text, 6))
        assertEquals(TextRange(6, 13), wordRangeAt(text, 13))
        assertEquals(TextRange(13, 14), wordRangeAt(text, 14))
        assertEquals(TextRange(16, 17), wordRangeAt(text, 17))
        assertEquals(TextRange(1, 2), wordRangeAt("(+)", 1))
        assertEquals(TextRange(0, 2), wordRangeAt("ab", 2))
        assertEquals(TextRange(0), wordRangeAt("", 0))
    }

    @Test
    fun `the line at an offset`() {
        val text = "ab\n\ncd"
        assertEquals(TextRange(0, 2), lineRangeAt(text, 1))
        assertEquals(TextRange(0, 2), lineRangeAt(text, 2))
        assertEquals(TextRange(3, 3), lineRangeAt(text, 3))
        assertEquals(TextRange(4, 6), lineRangeAt(text, 6))
        assertEquals(TextRange(0, 3), lineRangeAt("abc", 1))
    }

    @Test
    fun `the caret steps by character and by word`() {
        val text = "ab cd\nef"
        assertEquals(3, caretStep(text, 2, 1, byWord = false))
        assertEquals(0, caretStep(text, 0, -1, byWord = false))
        assertEquals(text.length, caretStep(text, text.length, 5, byWord = false))
        assertEquals(5, caretStep(text, 2, 1, byWord = true))
        assertEquals(8, caretStep(text, 5, 1, byWord = true))
        assertEquals(3, caretStep(text, 5, -1, byWord = true))
        assertEquals(0, caretStep(text, 3, -1, byWord = true))
    }

    @Test
    fun `the offset a line starts at`() {
        assertEquals(3, offsetOfLine("ab\ncd", 1))
        assertEquals(3, offsetOfLine("ab\ncd", 9))
        assertEquals(0, offsetOfLine("ab", -1))
    }

    @Test
    fun `merged edits apply together`() {
        val text = "a b a"
        val edit = mergeEdits(text, listOf(TextRange(4, 5) to "zz", TextRange(0, 1) to "zz"), TextRange(0))!!
        assertEquals("zz b zz", edit.applyTo(text))
        assertNull(mergeEdits(text, listOf(TextRange(0, 2) to "x", TextRange(1, 3) to "y"), TextRange(0)))
        assertNull(mergeEdits(text, emptyList(), TextRange(0)))
    }
}
