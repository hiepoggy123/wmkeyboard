package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeKeysTest {

    private val keys = listOf(AccessoryKey("=", "="), AccessoryKey("(", "("), AccessoryKey("end", "end"), AccessoryKey("nil", "nil"))

    @Test
    fun `saved order comes first, unnamed keys keep their place, hidden keys go`() {
        assertEquals(listOf("end", "=", "(", "nil"), arrangeKeys(keys, listOf("end", "="), emptySet()).map { it.label })
        assertEquals(listOf("end", "nil"), arrangeKeys(keys, listOf("end"), setOf("=", "(")).map { it.label })
        assertEquals(keys, arrangeKeys(keys, listOf("gone", "gone"), emptySet()))
    }

    @Test
    fun `the suggestion bar hands a choice back to the field that published it`() {
        val bar = CodeSuggestionBar()
        val item = CodeCompletion("print", CodeCompletionKind.FUNCTION, "print()")
        var chosen: CodeCompletion? = null
        bar.publish(CodeCompletions(TextRange(0, 2), listOf(item))) { chosen = it }
        bar.choose(item)
        assertEquals(item, chosen)
        bar.publish(null) {}
        assertNull(bar.shown)
    }
}
