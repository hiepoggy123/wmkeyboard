package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LuaCode] against the [CodeLanguage] contract, and the colours a plugin author sees. */
class LuaCodeTest {

    private val colors = CodeColors(
        background = Color(0xFF000001), border = Color(0xFF000002), gutter = Color(0xFF000003),
        gutterText = Color(0xFF000004), gutterActiveText = Color(0xFF000005), activeLine = Color(0xFF000006),
        caret = Color(0xFF000007), selection = Color(0xFF000008), bracketMatch = Color(0xFF000009),
        text = Color(0xFF00000A), key = Color(0xFF00000B), string = Color(0xFF00000C), number = Color(0xFF00000D),
        keyword = Color(0xFF00000E), problem = Color(0xFF00000F), comment = Color(0xFF000010),
        function = Color(0xFF000011), operator = Color(0xFF000012), warning = Color(0xFF000013),
        handle = Color(0xFF000014), findMatch = Color(0xFF000015), findActive = Color(0xFF000016),
        foldMark = Color(0xFF000017),
    )

    private fun colourAt(coloured: AnnotatedString, offset: Int): Color? =
        coloured.spanStyles.firstOrNull { offset >= it.start && offset < it.end }?.item?.color

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `highlighting never changes the text`() {
        val sources = DEMOS.map(::demo) + listOf("", "x = \"open", "--[[ open", "@ ~ \\", "if then end end ((((")
        for (source in sources) assertEquals(source, LuaCode.highlight(source, colors).text)
    }

    @Test
    fun `each kind of token takes its colour`() {
        val source = "local n = 42 -- note\nprint(\"hi\")"
        val coloured = LuaCode.highlight(source, colors)
        assertEquals(colors.keyword, colourAt(coloured, source.indexOf("local")))
        assertEquals(colors.number, colourAt(coloured, source.indexOf("42")))
        assertEquals(colors.comment, colourAt(coloured, source.indexOf("note")))
        assertEquals(colors.function, colourAt(coloured, source.indexOf("print")))
        assertEquals(colors.string, colourAt(coloured, source.indexOf("hi")))
        assertNull(colourAt(coloured, source.indexOf("n =")))
        assertNull(colourAt(coloured, source.indexOf("=")))
    }

    @Test
    fun `a field after a dot is a key and a called name is a function`() {
        val source = "wm.storage.get(k)"
        val coloured = LuaCode.highlight(source, colors)
        assertNull(colourAt(coloured, 0))
        assertEquals(colors.key, colourAt(coloured, source.indexOf("storage")))
        assertEquals(colors.function, colourAt(coloured, source.indexOf("get")))
        assertNull(colourAt(coloured, source.indexOf("k)")))
    }

    @Test
    fun `a declared function name is a function`() {
        val source = "local function helper() end\nfunction M.render() end"
        val coloured = LuaCode.highlight(source, colors)
        assertEquals(colors.function, colourAt(coloured, source.indexOf("helper")))
        assertEquals(colors.function, colourAt(coloured, source.indexOf("render")))
    }

    @Test
    fun `an unclosed string is drawn as a problem`() {
        val source = "x = \"abc"
        assertEquals(colors.problem, colourAt(LuaCode.highlight(source, colors), source.indexOf("abc")))
    }

    @Test
    fun `neighbouring comment lines share one span`() {
        assertEquals(1, LuaCode.highlight("-- a\n-- b\n-- c", colors).spanStyles.size)
    }

    @Test
    fun `brackets in strings and comments are not brackets`() {
        assertEquals(listOf(1, 7, 9, 10), LuaCode.brackets("f(\"(\", {x}) -- ]"))
    }

    @Test
    fun `only brackets of one kind pair up`() {
        val source = "f(a[1])"
        val brackets = LuaCode.brackets(source)
        assertEquals(1 to 6, LuaCode.matchingBracket(source, brackets, 2))
        assertEquals(3 to 5, LuaCode.matchingBracket(source, brackets, 4))
        assertEquals(3 to 5, LuaCode.matchingBracket(source, brackets, 6))
        assertEquals(1 to 6, LuaCode.matchingBracket(source, brackets, 7))
        assertNull(LuaCode.matchingBracket(source, brackets, 0))
    }

    @Test
    fun `problem points at the first unclosed string`() {
        assertEquals(CodeProblem(10), LuaCode.problem("x = 1\ny = \"oops"))
    }

    @Test
    fun `the demos have no lexical problem`() {
        for (name in DEMOS) assertNull(name, LuaCode.problem(demo(name)))
    }

    @Test
    fun `format re-indents`() {
        assertEquals("if x then\n  y()\nend", LuaCode.format("if x then\ny()\nend"))
    }

    @Test
    fun `Lua comments and rules`() {
        assertEquals("--", LuaCode.lineComment)
        assertSame(LuaSmartRules, LuaCode.smartRules)
    }

    @Test
    fun `the largest demo stays inside its span budget`() {
        val spans = LuaCode.highlight(demo("math-mode"), colors).spanStyles.size
        assertTrue("$spans spans", spans < 12_000)
    }

    private companion object {
        val DEMOS = listOf("cipher-tool", "ui-kitchen-sink", "todo-list", "text-tools", "math-mode")
    }
}
