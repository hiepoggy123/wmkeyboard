package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.wasimaster.wmkeyboard.core.tools.TextDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a comparison is drawn. The rules worth pinning are the ones that are not
 * about colour: colour alone is unreadable over a photo background and for a
 * colour-blind reader, and the isolate characters must never reach the field.
 */
class AiDiffAnnotatedTest {

    private val colors = DiffColors(added = Color(0xFF43C593), deleted = Color(0xFFC62828))

    private fun render(source: String, result: String) =
        diffAnnotated(TextDiff.diff(source, result), colors)

    @Test
    fun `unchanged text carries no styling at all`() {
        val annotated = render("no change here", "no change here")
        assertEquals("no change here", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `what went is struck through and what arrived is underlined`() {
        val annotated = render("the quick brown fox", "the quick red fox")
        val decorations = annotated.spanStyles.map { it.item.textDecoration }
        assertTrue(decorations.contains(TextDecoration.LineThrough))
        assertTrue(decorations.contains(TextDecoration.Underline))
    }

    @Test
    fun `a changed run is never told apart by colour alone`() {
        // The rule that keeps this readable on a photo theme and for a
        // colour-blind reader.
        val annotated = render("alpha beta gamma", "alpha delta gamma")
        assertTrue(annotated.spanStyles.isNotEmpty())
        for (style in annotated.spanStyles) {
            assertNotNull(style.item.textDecoration)
            assertTrue(style.item.textDecoration != TextDecoration.None)
        }
    }

    @Test
    fun `every changed run is wrapped in the direction isolates`() {
        // Without these a struck-through Arabic deletion reorders the untouched
        // text around it.
        val annotated = render("hello world", "hello there")
        for (style in annotated.spanStyles) {
            val piece = annotated.text.substring(style.start, style.end)
            assertTrue(piece, piece.startsWith("⁨"))
            assertTrue(piece, piece.endsWith("⁩"))
        }
    }

    @Test
    fun `unchanged text never gets an isolate`() {
        val annotated = render("the quick brown fox", "the quick red fox")
        val changed = annotated.spanStyles
            .map { annotated.text.substring(it.start, it.end) }
            .joinToString("")
        val untouched = annotated.text.filterNot { it in changed }
        assertFalse(untouched.contains('⁨'))
        assertFalse(untouched.contains('⁩'))
    }

    @Test
    fun `a changed space is drawn with a mark that can be seen`() {
        // A deleted paragraph break is a real change, and an empty span says
        // nothing at all.
        val annotated = render("one\n\ntwo", "one\ntwo")
        val changed = annotated.spanStyles
            .map { annotated.text.substring(it.start, it.end) }
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.any { it.contains('¶') || it.contains('·') })
    }

    @Test
    fun `ordinary text keeps its own characters`() {
        val annotated = render("hello world", "hello there")
        assertTrue(annotated.text.contains("world"))
        assertTrue(annotated.text.contains("there"))
    }

    @Test
    fun `a comparison that was refused renders nothing`() {
        val refused = TextDiff.Result(
            spans = emptyList(),
            granularity = TextDiff.Granularity.NONE,
            added = 0,
            deleted = 0,
            tooLong = true,
        )
        assertEquals("", diffAnnotated(refused, colors).text)
    }
}
