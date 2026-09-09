package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The code editor's rules, without a screen: what one typed character turns
 * into, where a line starts, and what the JSON language reports.
 */
class CodeEditorTest {

    /** Types [character] at [caret] the way a text field reports it. */
    private fun type(text: String, caret: Int, character: Char): TextFieldValue {
        val old = TextFieldValue(text, TextRange(caret))
        val typed = TextFieldValue(
            text.substring(0, caret) + character + text.substring(caret),
            TextRange(caret + 1),
        )
        return smartEdit(old, typed)
    }

    /** Presses backspace at [caret] the way a text field reports it. */
    private fun backspace(text: String, caret: Int): TextFieldValue {
        val old = TextFieldValue(text, TextRange(caret))
        val cut = TextFieldValue(text.removeRange(caret - 1, caret), TextRange(caret - 1))
        return smartEdit(old, cut)
    }

    @Test
    fun `an opening brace brings its closer`() {
        val result = type("", 0, '{')
        assertEquals("{}", result.text)
        assertEquals(1, result.selection.end)
    }

    @Test
    fun `an opener in front of a word does not close`() {
        val result = type("abc", 0, '[')
        assertEquals("[abc", result.text)
    }

    @Test
    fun `an opener inside a string does not close`() {
        val result = type("\"ab\"", 3, '{')
        assertEquals("\"ab{\"", result.text)
    }

    @Test
    fun `a typed closer steps over the one already there`() {
        val result = type("{}", 1, '}')
        assertEquals("{}", result.text)
        assertEquals(2, result.selection.end)
    }

    @Test
    fun `a quote closes itself and then steps over`() {
        val opened = type("", 0, '"')
        assertEquals("\"\"", opened.text)
        assertEquals(1, opened.selection.end)
        val closed = type("\"\"", 1, '"')
        assertEquals("\"\"", closed.text)
        assertEquals(2, closed.selection.end)
    }

    @Test
    fun `backspace over an empty pair takes both halves`() {
        val result = backspace("a{}b", 2)
        assertEquals("ab", result.text)
        assertEquals(1, result.selection.end)
    }

    @Test
    fun `backspace over a pair with content takes one half`() {
        val result = backspace("{a}", 1)
        assertEquals("a}", result.text)
    }

    @Test
    fun `a newline carries the indentation on`() {
        val result = type("  \"a\": 1", 8, '\n')
        assertEquals("  \"a\": 1\n  ", result.text)
        assertEquals(11, result.selection.end)
    }

    @Test
    fun `a newline after an opener indents one step deeper`() {
        val result = type("  \"a\": [", 8, '\n')
        assertEquals("  \"a\": [\n    ", result.text)
    }

    @Test
    fun `a newline between a pair puts the closer on its own line`() {
        val result = type("[]", 1, '\n')
        assertEquals("[\n  \n]", result.text)
        assertEquals(4, result.selection.end)
    }

    @Test
    fun `plain typing is left alone`() {
        val result = type("ab", 1, 'x')
        assertEquals("axb", result.text)
        assertEquals(2, result.selection.end)
    }

    @Test
    fun `line starts and the line an offset falls on`() {
        val starts = lineStartOffsets("ab\ncd\n\nef")
        assertEquals(listOf(0, 3, 6, 7), starts)
        assertEquals(0, lineOf(starts, 0))
        assertEquals(0, lineOf(starts, 2))
        assertEquals(1, lineOf(starts, 3))
        assertEquals(2, lineOf(starts, 6))
        assertEquals(3, lineOf(starts, 9))
    }

    @Test
    fun `format indents the document again`() {
        val formatted = JsonCode.format("{\"a\":[1,2]}")
        assertEquals("{\n  \"a\": [\n    1,\n    2\n  ]\n}", formatted)
    }

    @Test
    fun `format refuses text that does not parse`() {
        assertNull(JsonCode.format("{\"a\":"))
    }

    @Test
    fun `a problem is reported at the offset the parser stopped on`() {
        assertNull(JsonCode.problem("{\"a\": 1}"))
        assertNull(JsonCode.problem("   "))
        val problem = JsonCode.problem("{\"a\": }")
        assertTrue(problem != null)
        assertTrue((problem?.offset ?: 0) > 0)
    }

    @Test
    fun `brackets pair up outside strings`() {
        val source = "{\"a\": [1, 2]}"
        assertEquals(0 to 12, JsonCode.matchingBracket(source, 1))
        assertEquals(6 to 11, JsonCode.matchingBracket(source, 12))
        assertNull(JsonCode.matchingBracket(source, 4))
    }

    @Test
    fun `a bracket inside a string is not a bracket`() {
        assertNull(JsonCode.matchingBracket("{\"a[\": 1}", 4))
    }
}
