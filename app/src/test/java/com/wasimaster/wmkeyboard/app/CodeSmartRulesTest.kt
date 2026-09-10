package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smart editing under Lua's rules. JSON's are pinned, unchanged, by [CodeEditorTest]. */
class CodeSmartRulesTest {

    private fun type(text: String, caret: Int, character: Char, rules: CodeSmartRules = LuaSmartRules): TextFieldValue {
        val old = TextFieldValue(text, TextRange(caret))
        val typed = TextFieldValue(text.substring(0, caret) + character + text.substring(caret), TextRange(caret + 1))
        return smartEdit(old, typed, rules)
    }

    private fun backspace(text: String, caret: Int): TextFieldValue {
        val old = TextFieldValue(text, TextRange(caret))
        return smartEdit(old, TextFieldValue(text.removeRange(caret - 1, caret), TextRange(caret - 1)), LuaSmartRules)
    }

    @Test
    fun `a round bracket closes`() {
        val result = type("f", 1, '(')
        assertEquals("f()", result.text)
        assertEquals(2, result.selection.end)
    }

    @Test
    fun `a single quote closes and then steps over`() {
        val opened = type("x = ", 4, '\'')
        assertEquals("x = ''", opened.text)
        assertEquals(5, opened.selection.end)
        val closed = type("x = ''", 5, '\'')
        assertEquals("x = ''", closed.text)
        assertEquals(6, closed.selection.end)
    }

    @Test
    fun `backspace over an empty round pair takes both halves`() {
        assertEquals("f", backspace("f()", 2).text)
    }

    @Test
    fun `nothing closes inside a line comment`() {
        assertEquals("-- note {", type("-- note ", 8, '{').text)
        assertEquals("--[", type("--", 2, '[').text)
    }

    @Test
    fun `nothing closes inside a long string`() {
        assertEquals("s = [[ (", type("s = [[ ", 7, '(').text)
    }

    @Test
    fun `nothing closes inside a quoted string`() {
        assertEquals("s = \"ab[", type("s = \"ab", 7, '[').text)
    }

    @Test
    fun `typing the quote that ends a string adds no second one`() {
        assertEquals("s = \"ab\"", type("s = \"ab", 7, '"').text)
    }

    @Test
    fun `a newline after then indents`() {
        val result = type("if x then", 9, '\n')
        assertEquals("if x then\n  ", result.text)
        assertEquals(12, result.selection.end)
    }

    @Test
    fun `a newline after a function header indents`() {
        assertEquals("function render()\n  ", type("function render()", 17, '\n').text)
        assertEquals("  local f = function(a)\n    ", type("  local f = function(a)", 23, '\n').text)
    }

    @Test
    fun `a newline after a call holding a whole function does not indent`() {
        assertEquals("  foo(function() return 1 end)\n  ", type("  foo(function() return 1 end)", 30, '\n').text)
    }

    @Test
    fun `a newline after a comment ending in then does not indent`() {
        assertEquals("x = 1 -- then\n", type("x = 1 -- then", 13, '\n').text)
    }

    @Test
    fun `a newline between round brackets opens them up`() {
        val result = type("f()", 2, '\n')
        assertEquals("f(\n  \n)", result.text)
        assertEquals(5, result.selection.end)
    }

    @Test
    fun `the default rules are still JSON's`() {
        assertEquals("(", type("", 0, '(', CodeSmartRules.Json).text)
        val old = TextFieldValue("", TextRange(0))
        assertEquals("{}", smartEdit(old, TextFieldValue("{", TextRange(1))).text)
    }

    @Test
    fun `inside a string or comment, judged from the text before the caret`() {
        assertFalse(luaInert("x = 1", 5))
        assertFalse(luaInert("x", 0))
        assertTrue(luaInert("-- c", 4))
        assertTrue(luaInert("s = 'ab", 7))
        assertFalse(luaInert("s = 'ab'", 8))
        assertTrue(luaInert("[[ x", 4))
        assertFalse(luaInert("--[[ x ]] y", 11))
    }

    @Test
    fun `which lines open a block`() {
        for (line in listOf("if x then", "for i = 1, 3 do", "else", "repeat", "function render()", "local f = function(a, b)")) {
            assertTrue(line, luaOpensBlock(line))
        }
        for (line in listOf("foo(function() return 1 end)", "x = f()", "if x then return end", "-- then", "")) {
            assertFalse(line, luaOpensBlock(line))
        }
    }
}
