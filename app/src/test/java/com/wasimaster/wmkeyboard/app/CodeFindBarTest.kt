package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The find bar's and the rename dialog's arithmetic. */
class CodeFindBarTest {

    @Test
    fun `the first match shown is the first at or after the caret`() {
        val matches = listOf(TextRange(2, 4), TextRange(10, 12))
        assertEquals(1, firstMatchFrom(matches, 5))
        assertEquals(0, firstMatchFrom(matches, 20))
        assertEquals(0, firstMatchFrom(emptyList(), 3))
    }

    @Test
    fun `a rename is one edit that keeps the caret on its name`() {
        val text = "local a = a + a"
        val spans = listOf(TextRange(14, 15), TextRange(6, 7), TextRange(10, 11))
        val atEnd = requireNotNull(renameEdit(text, spans, "total", 11))
        assertEquals("local total = total + total", atEnd.applyTo(text))
        assertEquals(TextRange(19), atEnd.selection)
        assertEquals(TextRange(6), requireNotNull(renameEdit(text, spans, "total", 6)).selection)
        assertNull(renameEdit(text, emptyList(), "total", 0))
    }

    @Test
    fun `a line number is read in any script's digits and must name a line`() {
        assertEquals(12, parseLineNumber("12", 20))
        assertEquals(3, parseLineNumber(" 3 ", 20))
        assertEquals(3, parseLineNumber("\u09e9", 20))
        assertNull(parseLineNumber("0", 20))
        assertNull(parseLineNumber("21", 20))
        assertNull(parseLineNumber("x", 20))
        assertNull(parseLineNumber("", 20))
    }
}
