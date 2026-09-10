package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The suggestion list's rules, and the two languages behind it. */
class CodeCompletionTest {

    private val open = CodeCompletions(TextRange(2, 4), listOf(CodeCompletion("abcd", CodeCompletionKind.VARIABLE, "abcd")))

    @Test
    fun `typing asks for suggestions`() {
        assertEquals(CompletionStep.ASK, completionStep("wm", "wm.", 3, null))
    }

    @Test
    fun `deleting asks again only while a list is open`() {
        assertEquals(CompletionStep.ASK, completionStep("abcd", "abc", 3, open))
        assertEquals(CompletionStep.CLOSE, completionStep("abcd", "abc", 3, null))
    }

    @Test
    fun `moving the caret keeps the list inside its word and closes it outside`() {
        assertEquals(CompletionStep.KEEP, completionStep("abcd", "abcd", 3, open))
        assertEquals(CompletionStep.CLOSE, completionStep("abcd", "abcd", 0, open))
        assertEquals(CompletionStep.CLOSE, completionStep("abcd", "abcd", 3, null))
    }

    @Test
    fun `choosing replaces the word and puts the caret where the item says`() {
        val shown = CodeCompletions(TextRange(7, 11), listOf(CodeCompletion("format", CodeCompletionKind.FUNCTION, "format()", 7)))
        val edit = shown.editFor(shown.items.single())
        assertEquals("string.format()", edit.applyTo("string.form"))
        assertEquals(TextRange(14), edit.selection)
    }

    @Test
    fun `Lua suggests members after a dot from tokens alone`() {
        val found = requireNotNull(LuaCode.completions("wm.", 3, explicit = false))
        assertEquals(TextRange(3, 3), found.replace)
        assertEquals(CodeCompletionKind.MODULE, found.items.single { it.label == "storage" }.kind)
        assertEquals(CodeCompletionKind.FUNCTION, found.items.single { it.label == "log" }.kind)
    }

    @Test
    fun `the JSON editor suggests nothing`() {
        assertNull(JsonCode.completions("{\"a\": ", 6, explicit = true))
    }
}
