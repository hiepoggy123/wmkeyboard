package com.wasimaster.wmkeyboard.core.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-Kotlin fix application; the native lint path is covered by cargo tests. */
class GrammarCheckerTest {

    private fun lint(start: Int, end: Int, vararg fixes: GrammarFix) =
        GrammarLint(start = start, end = end, suggestions = fixes.toList())

    @Test
    fun `replace fix swaps the span`() {
        val text = "He go to the store."
        val fixed = GrammarChecker.apply(text, lint(3, 5), GrammarFix("replace", "goes"))
        assertEquals("He goes to the store.", fixed)
    }

    @Test
    fun `remove fix deletes the span`() {
        val text = "the the store"
        assertEquals("the store", GrammarChecker.apply(text, lint(0, 4), GrammarFix("remove")))
    }

    @Test
    fun `insertAfter fix appends after the span`() {
        val text = "However the store"
        val fixed = GrammarChecker.apply(text, lint(0, 7), GrammarFix("insertAfter", ","))
        assertEquals("However, the store", fixed)
    }

    @Test
    fun `trailing space in replacement does not double the existing space`() {
        val text = "their not liking things"
        val fixed = GrammarChecker.apply(text, lint(0, 5), GrammarFix("replace", "they're "))
        assertEquals("they're not liking things", fixed)
    }

    @Test
    fun `leading space in replacement does not double the existing space`() {
        val text = "liking their things"
        val fixed = GrammarChecker.apply(text, lint(7, 12), GrammarFix("replace", " they're"))
        assertEquals("liking they're things", fixed)
    }

    @Test
    fun `replacement whitespace is kept when nothing adjacent duplicates it`() {
        val text = "their"
        val fixed = GrammarChecker.apply(text, lint(0, 5), GrammarFix("replace", "they're "))
        assertEquals("they're ", fixed)
    }

    @Test
    fun `out of bounds lint is a no-op`() {
        val text = "short"
        assertEquals(text, GrammarChecker.apply(text, lint(2, 99), GrammarFix("replace", "x")))
    }

    @Test
    fun `applyAll fixes back to front so spans stay valid`() {
        val text = "He go to the store and she go home."
        val lints = listOf(
            lint(3, 5, GrammarFix("replace", "goes")),
            lint(27, 29, GrammarFix("replace", "goes")),
        )
        assertEquals("He goes to the store and she goes home.", GrammarChecker.applyAll(text, lints))
    }

    @Test
    fun `applyAll skips overlapping lints instead of corrupting text`() {
        val text = "abcdef"
        val lints = listOf(
            lint(0, 4, GrammarFix("replace", "X")),
            lint(2, 6, GrammarFix("replace", "Y")),
        )
        // Later span applies first; the earlier, overlapping one is dropped.
        assertEquals("abY", GrammarChecker.applyAll(text, lints))
    }

    // The edits are what the keyboard splices into the field, and the whole
    // point of them is that they name the smallest span: anything wider gets
    // re-committed, which is what flattens a styled note.

    @Test
    fun `replace edit names only the misspelt span`() {
        val text = "He go to the store."
        val edit = GrammarChecker.edit(text, lint(3, 5), GrammarFix("replace", "goes"))
        assertEquals(GrammarEdit(3, 5, "goes"), edit)
    }

    @Test
    fun `remove edit is an empty replacement of the span`() {
        val edit = GrammarChecker.edit("the the store", lint(0, 4), GrammarFix("remove"))
        assertEquals(GrammarEdit(0, 4, ""), edit)
    }

    @Test
    fun `insertAfter edit is a zero width splice at the end of the span`() {
        val edit = GrammarChecker.edit("However the store", lint(0, 7), GrammarFix("insertAfter", ","))
        assertEquals(GrammarEdit(7, 7, ","), edit)
    }

    @Test
    fun `out of bounds lint yields no edit`() {
        assertNull(GrammarChecker.edit("short", lint(2, 99), GrammarFix("replace", "x")))
    }

    @Test
    fun `unknown fix kind yields no edit`() {
        assertNull(GrammarChecker.edit("short", lint(0, 5), GrammarFix("shrug", "x")))
    }

    @Test
    fun `editsAll runs back to front so every offset stays valid`() {
        val text = "He go to the store and she go home."
        val lints = listOf(
            lint(3, 5, GrammarFix("replace", "goes")),
            lint(27, 29, GrammarFix("replace", "goes")),
        )
        assertEquals(
            listOf(GrammarEdit(27, 29, "goes"), GrammarEdit(3, 5, "goes")),
            GrammarChecker.editsAll(text, lints),
        )
    }

    @Test
    fun `editsAll leaves the text between the issues alone`() {
        val text = "He go to the store and she go home."
        val edits = GrammarChecker.editsAll(
            text,
            listOf(
                lint(3, 5, GrammarFix("replace", "goes")),
                lint(27, 29, GrammarFix("replace", "goes")),
            ),
        )
        // Nothing outside the two two-character spans is named at all, so an
        // editor asked to apply these never rewrites the rest of the line.
        assertEquals(4, edits.sumOf { it.end - it.start })
    }
}
