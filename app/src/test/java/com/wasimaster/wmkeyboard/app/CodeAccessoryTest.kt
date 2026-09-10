package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeAccessoryTest {

    @Test
    fun `a key types over the selection and leaves the caret after it`() {
        val typedOver = typed(TextFieldValue("local x = 1", TextRange(6, 7)), "count")
        assertEquals("local count = 1", typedOver.text)
        assertEquals(TextRange(11), typedOver.selection)
        assertEquals("ab==", typed(TextFieldValue("ab", TextRange(2)), "==").text)
    }

    @Test
    fun `the caret moves between lines at the same column, or the end of a shorter line`() {
        val text = "local long = 1\nx\nlocal other = 2"
        assertEquals(text.indexOf("x") + 1, caretLines(text, 8, 1))
        assertEquals(text.indexOf("other") + 2, caretLines(text, 8, 2))
        assertEquals(8, caretLines(text, text.indexOf("other") + 2, -2))
        assertEquals(3, caretLines(text, 3, -1))
        assertEquals(text.length, caretLines(text, text.length, 5))
    }

    @Test
    fun `every key has its own label`() {
        assertEquals(LuaAccessoryKeys.size, LuaAccessoryKeys.map { it.label }.toSet().size)
    }
}
