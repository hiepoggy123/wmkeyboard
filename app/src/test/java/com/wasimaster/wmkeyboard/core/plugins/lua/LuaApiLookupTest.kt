package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [LuaApiLookup] at a caret marked `|`. */
class LuaApiLookupTest {

    private fun path(marked: String): String? {
        val caret = marked.indexOf('|')
        return LuaApiLookup.at(LuaLexer.lex(marked.removeRange(caret, caret + 1)), caret)?.path
    }

    @Test
    fun `a caret on a name gives the name it ends a chain of`() {
        assertEquals("wm.storage.get", path("wm.storage.g|et(\"k\")"))
        assertEquals("wm.storage", path("wm.stor|age.get(\"k\")"))
        assertEquals("print", path("print|(1)"))
    }

    @Test
    fun `a method after a colon is a string method`() {
        assertEquals("string.upper", path("local s = \"x\"\nprint(s:up|per())"))
    }

    @Test
    fun `a caret inside a call or a widget table gives the function`() {
        assertEquals("string.format", path("string.format(\"%d\", |)"))
        assertEquals("print", path("print(foo(1)|)"))
        assertEquals("ui.button", path("return ui.button { id = \"x\", |}"))
    }

    @Test
    fun `nothing for a name the API lacks, a comment, or a caret outside any call`() {
        assertNull(path("string.fo|rm"))
        assertNull(path("-- string.format|"))
        assertNull(path("x = 1|"))
        assertNull(path("t[|1]"))
    }
}
