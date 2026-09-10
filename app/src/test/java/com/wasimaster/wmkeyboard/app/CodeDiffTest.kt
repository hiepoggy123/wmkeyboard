package com.wasimaster.wmkeyboard.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeDiffTest {

    private fun kinds(old: String, new: String) = lineDiff(old, new).map { "${it.kind.name[0]}${it.text}" }

    @Test
    fun `the same text is all the same`() {
        assertEquals(listOf("Sa", "Sb"), kinds("a\nb", "a\nb"))
    }

    @Test
    fun `a changed line is removed then added in its place`() {
        assertEquals(listOf("Sa", "Rb", "Ax", "Sc"), kinds("a\nb\nc", "a\nx\nc"))
    }

    @Test
    fun `lines added at the end and removed at the start`() {
        assertEquals(listOf("Sa", "Sb", "Ac"), kinds("a\nb", "a\nb\nc"))
        assertEquals(listOf("Ra", "Sb", "Sc"), kinds("a\nb\nc", "b\nc"))
    }

    @Test
    fun `lines moved are matched where they share the most`() {
        assertEquals(listOf("Ax", "Sa", "Sb", "Rc"), kinds("a\nb\nc", "x\na\nb"))
    }

    @Test
    fun `a middle too large to match is shown removed then added`() {
        val old = (1..2100).joinToString("\n") { "old $it" }
        val new = (1..2100).joinToString("\n") { "new $it" }
        val diff = lineDiff(old, new)
        assertEquals(4200, diff.size)
        assertEquals(DiffKind.REMOVED, diff[2099].kind)
        assertEquals(DiffKind.ADDED, diff[2100].kind)
    }

    @Test
    fun `unchanged runs away from a change fold into a count`() {
        val old = (1..20).joinToString("\n") { "line $it" }
        val new = old.replace("line 10", "changed")
        val rows = collapseDiff(lineDiff(old, new), context = 2)
        assertEquals(DiffRow.Unchanged(7), rows.first())
        assertEquals(DiffRow.Unchanged(8), rows.last())
        assertEquals(1 + 2 + 2 + 2 + 1, rows.size)
        assertEquals(listOf(DiffRow.Unchanged(3)), collapseDiff(lineDiff("a\nb\nc", "a\nb\nc")))
    }
}
