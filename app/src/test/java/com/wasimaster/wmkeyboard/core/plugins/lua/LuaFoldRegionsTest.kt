package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaFoldRegionsTest {

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `regions from tokens alone are the regions of the parsed document`() {
        for (name in listOf("cipher-tool", "ui-kitchen-sink", "todo-list", "text-tools", "math-mode")) {
            val source = demo(name)
            assertEquals(name, LuaNavigation.foldRegions(LuaDocument.of(source)), LuaNavigation.foldRegions(LuaLexer.lex(source)))
        }
    }

    @Test
    fun `a file that does not parse still folds`() {
        val source = "function render()\n  return ui.column {\n    ui.label { text = \n  }\nend"
        assertTrue(LuaNavigation.foldRegions(LuaLexer.lex(source)).isNotEmpty())
    }
}
