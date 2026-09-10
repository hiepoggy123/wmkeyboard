package com.wasimaster.wmkeyboard.core.plugins.lua

import com.wasimaster.wmkeyboard.core.plugins.PluginFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LuaParse] on real plugins and on the ways a script breaks while it is typed. */
class LuaParseTest {

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    private fun invalid(source: String): LuaSyntax.Invalid {
        val syntax = LuaParse.check(source)
        assertTrue("expected a parse failure for: $source\ngot $syntax", syntax is LuaSyntax.Invalid)
        return syntax as LuaSyntax.Invalid
    }

    @Test
    fun `every demo plugin parses`() {
        for (name in DEMOS) assertEquals(name, LuaSyntax.Valid, LuaParse.check(demo(name)))
    }

    @Test
    fun `an empty script and the blank plugin parse`() {
        assertEquals(LuaSyntax.Valid, LuaParse.check(""))
        assertEquals(LuaSyntax.Valid, LuaParse.check("-- nothing but a comment\n"))
    }

    @Test
    fun `goto and labels are Lua 5 point 2 and parse`() {
        assertEquals(LuaSyntax.Valid, LuaParse.check("for i = 1, 3 do\n  if i == 2 then goto skip end\n  ::skip::\nend"))
    }

    @Test
    fun `a missing then is found at the token after the condition`() {
        val failure = invalid("if x\n  y()\nend")
        assertEquals(7, failure.at.start)
        assertEquals("y", failure.found)
        assertTrue(failure.expected.toString(), "then" in failure.expected)
    }

    @Test
    fun `a missing end is found at the end of the file`() {
        val source = "if x then\n  y()\n"
        val failure = invalid(source)
        assertEquals(source.length, failure.at.start)
        assertEquals("<EOF>", failure.found)
        assertTrue(failure.expected.toString(), "end" in failure.expected)
    }

    @Test
    fun `each breakage lands on the character where it happened`() {
        val cases = listOf(
            "local x = = 2" to 10,
            "x = )" to 4,
            // luaj looks two tokens ahead to tell a call from an assignment, so a
            // call cut off at the end of the file fails on its bracket.
            "f(" to 1,
            "local 1 = 2" to 6,
            "function f(\nend" to 12,
            "return 1 x" to 9,
        )
        for ((source, offset) in cases) assertEquals(source, offset, invalid(source).at.start)
    }

    @Test
    fun `a tab is one character in the offset, as luaj counts it`() {
        val source = "local t = {\n\t\t= 1 }"
        assertEquals(14, invalid(source).at.start)
    }

    @Test
    fun `a Windows line ending does not move the offset`() {
        assertEquals(8, invalid("if x\r\n  y()\r\nend").at.start)
    }

    @Test
    fun `a character Lua cannot read lands on that character`() {
        assertEquals(4, invalid("x = \$").at.start)
    }

    @Test
    fun `nesting too deep is skipped, never a crash`() {
        val source = "x = " + "(".repeat(LuaParse.MAX_NESTING + 50) + "1" + ")".repeat(LuaParse.MAX_NESTING + 50)
        assertEquals(LuaSyntax.Skipped(LuaSyntax.Skipped.Reason.TOO_DEEP), LuaParse.check(source))
    }

    @Test
    fun `a script larger than an import takes is skipped`() {
        val source = "--" + "x".repeat(PluginFile.MAX_SCRIPT_BYTES)
        assertEquals(LuaSyntax.Skipped(LuaSyntax.Skipped.Reason.TOO_LARGE), LuaParse.check(source))
    }

    @Test
    fun `prefixes of real plugins never throw`() {
        val small = demo("cipher-tool")
        var end = 0
        while (end <= small.length) {
            LuaParse.check(small.substring(0, end))
            end += 37
        }
        val large = demo("math-mode")
        end = 0
        while (end <= large.length) {
            LuaParse.check(large.substring(0, end))
            end += 997
        }
    }

    private companion object {
        val DEMOS = listOf("cipher-tool", "ui-kitchen-sink", "todo-list", "text-tools", "math-mode")
    }
}
