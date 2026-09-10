package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Format for Lua: indentation put back, nothing else touched. */
class LuaFormatTest {

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    private fun trimmedLines(text: String) = text.split('\n').map { it.trimStart(' ', '\t') }

    @Test
    fun `branches indent and their keywords line up with the if`() {
        val source = "function f(x)\nif x then\nreturn 1\nelseif y then\nreturn 2\nelse\nreturn 3\nend\nend"
        assertEquals(
            "function f(x)\n  if x then\n    return 1\n  elseif y then\n    return 2\n  else\n    return 3\n  end\nend",
            LuaFormat.reindent(source),
        )
    }

    @Test
    fun `loops indent their bodies`() {
        assertEquals(
            "for i = 1, 3 do\n  while x do\n    x = f()\n  end\nend\nrepeat\n  y()\nuntil done",
            LuaFormat.reindent("for i = 1, 3 do\nwhile x do\nx = f()\nend\nend\nrepeat\ny()\nuntil done"),
        )
    }

    @Test
    fun `a line opening a call and a function indents its body once`() {
        assertEquals("foo(function()\n  x()\nend)\ny()", LuaFormat.reindent("foo(function()\n        x()\n    end)\n  y()"))
        assertEquals("f(function()\n  x()\nend, other)", LuaFormat.reindent("f(function()\nx()\nend, other)"))
    }

    @Test
    fun `nested tables close in line with the line that opened them`() {
        assertEquals(
            "local t = {\n  a = 1,\n  b = f({\n    c = 2,\n  }),\n}",
            LuaFormat.reindent("local t = {\na = 1,\nb = f({\nc = 2,\n}),\n}"),
        )
    }

    @Test
    fun `a line that continues an expression steps in once`() {
        assertEquals(
            "if a\n  and b then\n  c()\nend\nlocal s = \"x\" ..\n  \"y\"\nlocal t = {\n  -1,\n  -2,\n}\n" +
                "local f = (\"%d\")\n  :format(3)",
            LuaFormat.reindent(
                "if a\nand b then\nc()\nend\nlocal s = \"x\" ..\n\"y\"\nlocal t = {\n-1,\n-2,\n}\n" +
                    "local f = (\"%d\")\n:format(3)",
            ),
        )
    }

    @Test
    fun `text inside a long string is copied exactly`() {
        assertEquals(
            "local s = [[\n    keep\n  this\n]]\nif x then\n  y()\nend",
            LuaFormat.reindent("local s = [[\n    keep\n  this\n]]\nif x then\ny()\nend"),
        )
    }

    @Test
    fun `a long comment is copied exactly`() {
        val source = "--[[\n   note\n]]\nx = 1"
        assertEquals(source, LuaFormat.reindent(source))
    }

    @Test
    fun `blank lines keep no indentation`() {
        assertEquals("if x then\n\n  y()\nend", LuaFormat.reindent("if x then\n     \ny()\nend"))
    }

    @Test
    fun `windows line endings survive`() {
        assertEquals("if x then\r\n  y()\r\nend\r\n", LuaFormat.reindent("if x then\r\ny()\r\nend\r\n"))
    }

    @Test
    fun `a broken file still formats and never indents below zero`() {
        assertEquals("end\nend\nx = 1", LuaFormat.reindent("  end\n end\nx = 1"))
        assertEquals("if x then\n  foo(\n", LuaFormat.reindent("if x then\nfoo(\n"))
    }

    @Test
    fun `only leading whitespace ever changes`() {
        for (name in DEMOS) {
            val source = demo(name)
            assertEquals(name, trimmedLines(source), trimmedLines(LuaFormat.reindent(source)))
        }
    }

    @Test
    fun `formatting twice changes nothing more`() {
        val sources = DEMOS.map(::demo) + "foo(function()\nx()\nend)\nif a then\nb()\nelse\nc()\nend"
        for (source in sources) {
            val once = LuaFormat.reindent(source)
            assertEquals(once, LuaFormat.reindent(once))
        }
    }

    @Test
    fun `the demo plugins are already in house style`() {
        val differences = DEMOS.flatMap { name ->
            val before = demo(name).split('\n')
            val after = LuaFormat.reindent(demo(name)).split('\n')
            before.indices
                .filter { before[it] != after.getOrNull(it) }
                .map { "$name:${it + 1}\n  was: '${before[it]}'\n  now: '${after[it]}'" }
        }
        if (differences.isNotEmpty()) fail("${differences.size} lines differ:\n" + differences.joinToString("\n"))
    }

    private companion object {
        val DEMOS = listOf("cipher-tool", "ui-kitchen-sink", "todo-list", "text-tools", "math-mode")
    }
}
