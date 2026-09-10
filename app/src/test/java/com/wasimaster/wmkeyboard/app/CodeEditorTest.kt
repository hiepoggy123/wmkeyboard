package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LayoutCodec
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
    fun `format keeps a short document on one line`() {
        assertEquals("{\"a\": [1, 2]}", JsonCode.format("{\"a\":[1,2]}"))
    }

    @Test
    fun `format breaks only the containers that do not fit`() {
        // The outer object is far past the budget, so it breaks. The two lists
        // are well inside it, so they stay whole, which is the whole point.
        val long = "x".repeat(70)
        val source = "{\"alpha\":[1,2,3],\"beta\":[4,5],\"gamma\":\"$long\"}"
        assertEquals(
            "{\n" +
                "  \"alpha\": [1, 2, 3],\n" +
                "  \"beta\": [4, 5],\n" +
                "  \"gamma\": \"$long\"\n" +
                "}",
            JsonCode.format(source),
        )
    }

    @Test
    fun `format breaks a list that is too long for one line`() {
        val item = "\"" + "y".repeat(30) + "\""
        val formatted = JsonCode.format("[$item,$item,$item]").orEmpty()
        assertEquals(5, formatted.lines().size)
        assertTrue(formatted.startsWith("[\n  $item,\n"))
    }

    @Test
    fun `format round-trips through the parser`() {
        val source = "{\"a\":[1,2,{\"b\":null,\"c\":true}],\"d\":\"e\"}"
        val formatted = JsonCode.format(source).orEmpty()
        assertEquals(
            kotlinx.serialization.json.Json.parseToJsonElement(source),
            kotlinx.serialization.json.Json.parseToJsonElement(formatted),
        )
    }

    @Test
    fun `the screen opens in the shape Format prints`() {
        // The complaint this test exists for: the editor opened in one shape
        // and Format printed another, because the text came from kotlinx's
        // prettyPrint and only the button went through ours. One printer now,
        // so opening the screen is already formatted and Format is a no-op on
        // a document nobody has touched.
        val text = LayoutCodec.encodeForEditing(BuiltInLayouts.QWERTY)
        assertEquals(text, JsonCode.format(text))
    }

    @Test
    fun `the screen opens with short lists whole`() {
        // One field per line put every long-press alternate on its own; a line
        // holding a whole list is what says that stopped.
        val text = LayoutCodec.encodeForEditing(BuiltInLayouts.QWERTY)
        assertTrue(text.lines().any { Regex("""\[[^\[\]]+]""").containsMatchIn(it) })
    }

    @Test
    fun `printing is the same the second time`() {
        val once = JsonCode.format("{\"a\":[1,2,3],\"b\":{\"c\":\"${"d".repeat(120)}\"}}").orEmpty()
        assertEquals(once, JsonCode.format(once))
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
        val brackets = JsonCode.brackets(source)
        assertEquals(listOf(0, 6, 11, 12), brackets)
        assertEquals(0 to 12, JsonCode.matchingBracket(source, brackets, 1))
        assertEquals(6 to 11, JsonCode.matchingBracket(source, brackets, 12))
        assertNull(JsonCode.matchingBracket(source, brackets, 4))
    }

    @Test
    fun `a bracket inside a string is not a bracket`() {
        val source = "{\"a[\": 1}"
        assertNull(JsonCode.matchingBracket(source, JsonCode.brackets(source), 4))
    }
}
