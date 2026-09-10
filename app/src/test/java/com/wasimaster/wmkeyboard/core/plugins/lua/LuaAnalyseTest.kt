package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LuaAnalysis]: what names mean, and that every place it gives holds the name it claims. */
class LuaAnalyseTest {

    private fun analyse(source: String): LuaAnalysis {
        val document = LuaDocument.of(source)
        assertEquals("the test script must parse: $source", LuaSyntax.Valid, document.syntax)
        return requireNotNull(document.analysis) { "no analysis for: $source" }
    }

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    /** The offset of the [nth] occurrence of [fragment], counting from 0. */
    private fun String.at(fragment: String, nth: Int = 0): Int {
        var from = 0
        repeat(nth) { from = indexOf(fragment, from) + 1 }
        val found = indexOf(fragment, from)
        require(found >= 0) { "no $fragment" }
        return found
    }

    private fun LuaAnalysis.local(name: String): LuaSymbol = symbols.single { it.name == name }

    private fun String.text(span: LuaSpan?): String? = span?.let { substring(it.start, it.end) }

    @Test
    fun `a local and its uses are one symbol`() {
        val source = "local count = 0\nfunction add()\n  count = count + 1\nend\nreturn count"
        val analysis = analyse(source)
        val count = analysis.local("count")
        assertEquals(LuaSymbolKind.LOCAL, count.kind)
        assertEquals(LuaSpan(6, 11), count.declaration)
        assertEquals(listOf(source.at("count", 1)), count.writes.map { it.start })
        assertEquals(listOf(source.at("count", 2), source.at("count", 3)), count.reads.map { it.start })
        assertEquals(listOf("add" to true), analysis.globals.map { it.name to it.write })
    }

    @Test
    fun `local x = x reads the x outside`() {
        val source = "x = 1\nlocal x = x\nprint(x)"
        val analysis = analyse(source)
        assertEquals(listOf("x" to true, "x" to false, "print" to false), analysis.globals.map { it.name to it.write })
        assertEquals(listOf(source.lastIndexOf("x")), analysis.local("x").reads.map { it.start })
    }

    @Test
    fun `an inner local hides the outer one only inside its block`() {
        val source = "local v = 1\ndo\n  local v = 2\n  print(v)\nend\nprint(v)"
        val analysis = analyse(source)
        val (outer, inner) = analysis.symbols.filter { it.name == "v" }
        assertEquals(listOf(source.at("print(v)") + 6), inner.reads.map { it.start })
        assertEquals(listOf(source.lastIndexOf("v")), outer.reads.map { it.start })
        assertEquals(source.at("end"), inner.scope.end)
        assertEquals(source.length, outer.scope.end)
    }

    @Test
    fun `each branch of an if ends where the next begins`() {
        val source = "if a then\n  local x = 1\nelseif b then\n  local x = 2\nelse\n  local x = 3\nend"
        val (first, second, third) = analyse(source).symbols
        assertEquals(source.at("elseif"), first.scope.end)
        assertEquals(source.at("else\n"), second.scope.end)
        assertEquals(source.at("end"), third.scope.end)
    }

    @Test
    fun `until sees the locals of its repeat`() {
        val analysis = analyse("repeat\n  local done = true\nuntil done")
        assertEquals(emptyList<LuaGlobalUse>(), analysis.globals)
        assertEquals(1, analysis.local("done").reads.size)
    }

    @Test
    fun `a method has self and is named with its table`() {
        val source = "local M = {}\nfunction M.sub:greet(name)\n  return self, name\nend"
        val analysis = analyse(source)
        val self = analysis.symbols.single { it.kind == LuaSymbolKind.SELF }
        assertNull(self.declaration)
        assertEquals(1, self.reads.size)
        assertEquals(1, analysis.local("name").reads.size)
        assertEquals(1, analysis.local("M").reads.size)
        assertEquals(emptyList<LuaGlobalUse>(), analysis.globals)
        val greet = analysis.functions.single()
        assertEquals("M.sub:greet", greet.name)
        assertEquals(LuaFunctionKind.METHOD, greet.kind)
        assertEquals("M.sub:greet", source.text(greet.nameSpan))
        assertEquals(source.at("function"), greet.span.start)
        assertEquals(source.length, greet.span.end)
    }

    @Test
    fun `a loop variable lives inside its loop`() {
        val source = "for i = 1, 3 do print(i) end\nfor k, v in pairs(t) do t[k] = v end\nprint(i)"
        val analysis = analyse(source)
        assertEquals(LuaSymbolKind.FOR_VARIABLE, analysis.local("i").kind)
        assertEquals(1, analysis.local("i").reads.size)
        assertEquals(listOf("i", "k", "v"), analysis.symbols.map { it.name })
        assertEquals(
            listOf("print", "pairs", "t", "t", "print", "i"),
            analysis.globals.filter { !it.write }.map { it.name },
        )
    }

    @Test
    fun `functions know their name, kind, parameters and whether they return`() {
        val source = """
            function render()
              return ui.label { text = "hi" }
            end
            function on_event(e, extra) end
            local function helper(a, ...) end
            local t = { go = function() end }
            local f = function(x) return x end
            M = {}
            M.run = function() end
            call(function() end)
        """.trimIndent()
        val analysis = analyse(source)
        assertEquals(
            listOf<Triple<String?, LuaFunctionKind, List<String>>>(
                Triple("render", LuaFunctionKind.GLOBAL, emptyList()),
                Triple("on_event", LuaFunctionKind.GLOBAL, listOf("e", "extra")),
                Triple("helper", LuaFunctionKind.LOCAL, listOf("a")),
                Triple("go", LuaFunctionKind.FIELD, emptyList()),
                Triple("f", LuaFunctionKind.LOCAL, listOf("x")),
                Triple("M.run", LuaFunctionKind.FIELD, emptyList()),
                Triple(null, LuaFunctionKind.ANONYMOUS, emptyList()),
            ),
            analysis.functions.map { Triple(it.name, it.kind, it.parameters) },
        )
        assertEquals(listOf(true, false, false, false, true, false, false), analysis.functions.map { it.returnsValue })
        assertTrue(analysis.functions[2].vararg)
        for (function in analysis.functions.filter { it.name != null }) {
            assertEquals(function.name, source.text(function.nameSpan))
        }
    }

    @Test
    fun `the names visible at a point are the innermost of each`() {
        val source = "local a = 1\nlocal function f(b)\n  local a = 2\n  -- here\nend\n-- after"
        val analysis = analyse(source)
        val here = analysis.visibleAt(source.at("-- here"))
        assertEquals(setOf("a", "f", "b"), here.map { it.name }.toSet())
        assertEquals(source.at("a = 2"), here.single { it.name == "a" }.declaration?.start)
        val after = analysis.visibleAt(source.at("-- after"))
        assertEquals(setOf("a", "f"), after.map { it.name }.toSet())
        assertEquals(6, after.single { it.name == "a" }.declaration?.start)
        assertEquals("f", analysis.functionAt(source.at("-- here"))?.name)
        assertNull(analysis.functionAt(source.at("-- after")))
    }

    @Test
    fun `tabs, windows line ends and wide characters leave every span on its name`() {
        val source = "local s = \"\u00e9\uD835\uDC31\"\r\n\tlocal after = s\r\n\treturn after"
        val analysis = analyse(source)
        assertEquals("after", source.text(analysis.local("after").declaration))
        assertEquals(listOf(source.lastIndexOf("after")), analysis.local("after").reads.map { it.start })
        assertEquals(listOf(source.at("= s") + 2), analysis.local("s").reads.map { it.start })
    }

    @Test
    fun `every place found in the demo plugins holds the name it claims`() {
        for (name in DEMOS) {
            val source = demo(name)
            val analysis = analyse(source)
            for (symbol in analysis.symbols) {
                if (symbol.kind != LuaSymbolKind.SELF) assertNotNull("$name: ${symbol.name} was not placed", symbol.declaration)
                symbol.declaration?.let { assertEquals("$name at ${it.start}", symbol.name, source.text(it)) }
                (symbol.reads + symbol.writes).forEach { assertEquals("$name at ${it.start}", symbol.name, source.text(it)) }
            }
            analysis.globals.forEach { assertEquals("$name at ${it.span.start}", it.name, source.text(it.span)) }
            val named = analysis.functions.filter { it.kind == LuaFunctionKind.GLOBAL }.mapNotNull { it.name }
            assertTrue("$name: $named", "render" in named)
            assertTrue(name, analysis.functions.all { it.span.end > it.span.start })
        }
    }

    @Test
    fun `a script that does not parse keeps its tokens and has no analysis`() {
        val document = LuaDocument.of("if x then")
        assertTrue(document.syntax is LuaSyntax.Invalid)
        assertNull(document.analysis)
        assertTrue(document.tokens.size > 0)
    }

    @Test
    fun `a very long expression costs the analysis, never the app`() {
        LuaDocument.of("x = " + "1 + ".repeat(20_000) + "1")
    }

    @Test
    fun `the same text twice is one document`() {
        val source = "local a = 1"
        assertSame(LuaDocuments.of(source), LuaDocuments.of(String(source.toCharArray())))
    }

    private companion object {
        val DEMOS = listOf("cipher-tool", "ui-kitchen-sink", "todo-list", "text-tools", "math-mode")
    }
}
