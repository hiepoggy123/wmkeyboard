package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Find and replace, without the bar. */
class CodeFindTest {

    private val plain = CodeFindOptions()

    @Test
    fun `literal matches`() {
        assertTrue(findMatches("abc", "x", plain).isEmpty())
        assertEquals(listOf(TextRange(0, 1)), findMatches("abc", "a", plain))
        assertEquals(listOf(TextRange(0, 2), TextRange(2, 4)), findMatches("aaaa", "aa", plain))
        assertEquals(listOf(TextRange(2, 3)), findMatches("abc", "c", plain))
        assertTrue(findMatches("abc", "", plain).isEmpty())
    }

    @Test
    fun `a literal query is not a pattern`() {
        assertEquals(listOf(TextRange(1, 3)), findMatches("a.*b", ".*", plain))
    }

    @Test
    fun `case folds unless asked not to`() {
        assertEquals(2, findMatches("End end", "end", plain).size)
        assertEquals(1, findMatches("End end", "end", plain.copy(caseSensitive = true)).size)
        assertEquals(1, findMatches("I", "i", plain).size)
    }

    @Test
    fun `whole words`() {
        val text = "end bend ends _end end_ end."
        assertEquals(listOf(TextRange(0, 3), TextRange(24, 27)), findMatches(text, "end", plain.copy(wholeWord = true)))
    }

    @Test
    fun `pattern mode`() {
        val options = plain.copy(regex = true)
        assertEquals(listOf(TextRange(0, 3), TextRange(4, 7)), findMatches("foo\nfob", "^fo.", options))
        assertEquals(listOf(TextRange(1, 2), TextRange(3, 4)), findMatches("ab\nb", "b$", options))
        assertTrue(findMatches("a\nb", "a.b", options).isEmpty())
        assertTrue(findMatches("abc", "(", options).isEmpty())
        assertNull(findPattern("(", options))
    }

    @Test
    fun `replacing one match puts the caret after it`() {
        val edit = replaceMatch("x = old", TextRange(4, 7), "newer", "old", plain)
        assertEquals("x = newer", edit.applyTo("x = old"))
        assertEquals(TextRange(9), edit.selection)
    }

    @Test
    fun `replace all is one edit and never matches its own output`() {
        val text = "a-a-a"
        val edit = replaceAllMatches(text, findMatches(text, "a", plain), "aa", "a", plain)!!
        assertEquals("aa-aa-aa", edit.applyTo(text))
        assertEquals(TextRange(8), edit.selection)
    }

    @Test
    fun `a pattern replacement fills in its groups`() {
        val options = plain.copy(regex = true)
        val text = "local a = 1\nlocal b = 2"
        val query = "local (\\w+)"
        val edit = replaceAllMatches(text, findMatches(text, query, options), "var $1", query, options)!!
        assertEquals("var a = 1\nvar b = 2", edit.applyTo(text))
    }

    @Test
    fun `a replacement naming a missing group is used as typed`() {
        val options = plain.copy(regex = true)
        assertEquals("\$9bc", replaceMatch("abc", TextRange(0, 1), "\$9", "a", options).applyTo("abc"))
    }

    @Test
    fun `nothing to replace gives nothing`() {
        assertNull(replaceAllMatches("abc", emptyList(), "x", "q", plain))
    }
}
