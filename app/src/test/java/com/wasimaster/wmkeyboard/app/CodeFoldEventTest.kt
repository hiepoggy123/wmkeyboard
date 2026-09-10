package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeFoldEventTest {

    @Test
    fun `folding and unfolding say which block and which way, and typing says nothing`() {
        val state = CodeEditorState(TextFieldValue("function f()\n  a()\n  b()\nend"))
        assertNull(state.foldEvent)
        state.toggleFold(0)
        assertEquals(CodeFoldEvent(0, folded = true, serial = 1), state.foldEvent)
        state.toggleFold(0)
        assertEquals(CodeFoldEvent(0, folded = false, serial = 2), state.foldEvent)
        state.toggleFold(0)
        state.edit(TextFieldValue("-- x\nfunction f()\n  a()\n  b()\nend"))
        assertEquals(3, state.foldEvent?.serial)
        state.unfold(5)
        assertEquals(CodeFoldEvent(5, folded = false, serial = 4), state.foldEvent)
        state.unfold(5)
        assertEquals(4, state.foldEvent?.serial)
    }
}
