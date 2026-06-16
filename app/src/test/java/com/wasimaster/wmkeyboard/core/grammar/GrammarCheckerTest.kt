package com.wasimaster.wmkeyboard.core.grammar

import org.junit.Assert.assertEquals
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
}
