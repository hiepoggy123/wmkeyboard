package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFoldingTest {

    private val block = "function f()\n  a()\n  b()\nend"
    private val whole = TextRange(0, block.length)

    @Test
    fun `a fold hides the lines between its first and its last`() {
        assertEquals(TextRange(12, 24), hiddenRangeOf(block, whole))
        assertNull(hiddenRangeOf("if x then\nend", TextRange(0, 13)))
        val folds = foldsFor(block, listOf(whole), setOf(0))
        assertEquals("function f()$FOLD_PLACEHOLDER\nend", foldedText(AnnotatedString(block), folds, SpanStyle()).text)
    }

    @Test
    fun `the fold map is a valid mapping both ways and returns every visible offset to itself`() {
        val text = "a\nfunction f()\n  a()\n  b()\nend\nlocal t = {\n  1,\n  2,\n}\nz"
        val regions = listOf(TextRange(2, 30), TextRange(41, 56))
        val folds = foldsFor(text, regions, setOf(2, 41))
        assertEquals(2, folds.size)
        val map = FoldMap(folds, text.length)
        val shown = foldedText(AnnotatedString(text), folds, SpanStyle()).text
        assertEquals(shown.length, map.transformedLength)
        var previous = -1
        for (offset in 0..text.length) {
            val out = map.originalToTransformed(offset)
            assertTrue(out in 0..shown.length && out >= previous)
            previous = out
            val hidden = folds.any { offset > it.hidden.min && offset < it.hidden.max }
            if (!hidden) assertEquals("offset $offset", offset, map.transformedToOriginal(out))
        }
        for (offset in 0..shown.length) assertTrue(map.transformedToOriginal(offset) in 0..text.length)
    }

    @Test
    fun `a fold inside a folded block is left to the outer one`() {
        val text = "function outer()\n  if x then\n    y()\n    z()\n  end\n  w()\nend"
        val outer = TextRange(0, text.length)
        val inner = TextRange(text.indexOf("if"), text.indexOf("end") + 3)
        assertEquals(listOf(0), foldsFor(text, listOf(outer, inner), setOf(0, inner.min)).map { it.start })
    }

    @Test
    fun `fold starts follow an edit before them and drop when the edit covers them`() {
        val old = "x = 1\nfunction f()\n  a()\n  b()\nend"
        val start = old.indexOf("function")
        assertEquals(setOf(start + 3), remapFoldStarts(setOf(start), old, "xyz" + old))
        assertEquals(setOf(start), remapFoldStarts(setOf(start), old, old + "\nprint(1)"))
        assertEquals(emptySet<Int>(), remapFoldStarts(setOf(start), old, old.replace("function f()", "local g")))
    }

    @Test
    fun `the block around the caret is the innermost one holding it`() {
        val regions = listOf(TextRange(0, 50), TextRange(10, 20))
        assertEquals(TextRange(10, 20), blockAround(regions, 12))
        assertEquals(TextRange(10, 20), blockAround(regions, 10))
        assertEquals(TextRange(0, 50), blockAround(regions, 20))
        assertNull(blockAround(regions, 50))
    }

    @Test
    fun `each line that can fold names its outermost region`() {
        val text = "local t = { f = function()\n  a()\n  b()\nend }\n"
        val regions = listOf(TextRange(text.indexOf("function"), text.indexOf("end") + 3), TextRange(10, text.indexOf('}') + 1))
        assertEquals(mapOf(0 to 10), foldableStarts(text, regions, lineStartOffsets(text)))
    }
}
