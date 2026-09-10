package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [LuaNavigation] on small scripts where the right answer is plain. */
class LuaNavigationTest {

    private fun String.at(fragment: String, nth: Int = 0): Int {
        var from = 0
        repeat(nth) { from = indexOf(fragment, from) + 1 }
        return indexOf(fragment, from).also { require(it >= 0) { "no $fragment" } }
    }

    @Test
    fun `a use goes to its declaration, and a global to its first assignment`() {
        val source = "local count = 0\ncount = count + 1\ntotal = 1\ntotal = 2\nprint(total)"
        val document = LuaDocument.of(source)
        assertEquals(LuaSpan(6, 11), LuaNavigation.definitionAt(document, source.at("count", 2) + 1))
        assertEquals(source.at("total"), LuaNavigation.definitionAt(document, source.lastIndexOf("total"))?.start)
        assertNull(LuaNavigation.definitionAt(document, source.at("print")))
    }

    @Test
    fun `references are every use of one symbol, and never a namesake`() {
        val source = "local v = 1\ndo\n  local v = 2\n  print(v)\nend\nprint(v)"
        val document = LuaDocument.of(source)
        val outer = LuaNavigation.referencesAt(document, 6)
        assertEquals(listOf(6, source.lastIndexOf("v")), outer.map { it.start })
        val inner = LuaNavigation.referencesAt(document, source.at("v = 2"))
        assertEquals(listOf(source.at("v = 2"), source.at("print(v)") + 6), inner.map { it.start })
    }

    @Test
    fun `rename is offered only where it is safe`() {
        val source = "function helper(self_like)\n  return self_like\nend\nfunction render()\n  return ui.label { text = helper(\"x\") }\nend\nlocal M = {}\nfunction M:go() return self end"
        val document = LuaDocument.of(source)
        assertEquals(2, LuaNavigation.renameSpans(document, source.at("self_like"))?.size)
        assertEquals(2, LuaNavigation.renameSpans(document, source.at("helper"))?.size)
        assertNull(LuaNavigation.renameSpans(document, source.at("render")))
        assertNull(LuaNavigation.renameSpans(document, source.at("ui")))
        assertNull(LuaNavigation.renameSpans(document, source.lastIndexOf("self")))
        assertNull(LuaNavigation.renameSpans(LuaDocument.of("local a = = 1\nprint(a)"), 6))
    }

    @Test
    fun `a new name is refused when it is not a name, is a keyword, or would change a use`() {
        val source = "local a = 1\nlocal b = 2\ndo\n  print(a)\nend\nprint(c)"
        val document = LuaDocument.of(source)
        assertEquals(LuaRenameProblem.NOT_A_NAME, LuaNavigation.renameProblem(document, 6, "1x"))
        assertEquals(LuaRenameProblem.KEYWORD, LuaNavigation.renameProblem(document, 6, "end"))
        assertEquals(LuaRenameProblem.TAKEN, LuaNavigation.renameProblem(document, 6, "b"))
        assertEquals(LuaRenameProblem.TAKEN, LuaNavigation.renameProblem(document, 6, "c"))
        assertNull(LuaNavigation.renameProblem(document, 6, "apples"))
    }

    @Test
    fun `the outline lists named functions with their depth`() {
        val source = "function render()\n  local function row() end\n  call(function()\n    local function deep() end\n  end)\nend\nfunction on_event(e) end"
        val outline = LuaNavigation.outline(requireNotNull(LuaDocument.of(source).analysis))
        assertEquals(listOf("render" to 0, "row" to 1, "deep" to 1, "on_event" to 0), outline.map { it.name to it.depth })
    }

    @Test
    fun `regions cover blocks, tables and long comments that span lines`() {
        val source = "function render()\n  return { a = 1,\n    b = 2 }\nend\nfunction one() end\n--[[ long\ncomment ]]\nif x then\n  y()\nelse\n  z()\nend"
        val regions = LuaNavigation.foldRegions(LuaDocument.of(source)).map { source.substring(it.start, it.end) }
        assertEquals(
            listOf(
                "function render()\n  return { a = 1,\n    b = 2 }\nend",
                "{ a = 1,\n    b = 2 }",
                "--[[ long\ncomment ]]",
                "if x then\n  y()\n",
                "else\n  z()\nend",
            ),
            regions,
        )
    }
}
