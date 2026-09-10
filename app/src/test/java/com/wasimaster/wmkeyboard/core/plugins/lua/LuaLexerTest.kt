package com.wasimaster.wmkeyboard.core.plugins.lua

import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.COMMENT
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.KEYWORD
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.LONG_COMMENT
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.LONG_STRING
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.NAME
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.NUMBER
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.OPERATOR
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.SHEBANG
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.STRING
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.UNKNOWN
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaTokenKind.WHITESPACE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The lexer's rules, and above all its invariant: every character belongs to
 * exactly one token, whatever the input. The highlighter's length-preservation
 * rests on that, and a plugin being typed is broken input most of the time.
 */
class LuaLexerTest {

    /** Every non-whitespace token as (kind, text). */
    private fun code(source: String): List<Pair<LuaTokenKind, String>> {
        val tokens = LuaLexer.lex(source)
        return (0 until tokens.size)
            .filter { tokens.kind(it) != WHITESPACE }
            .map { tokens.kind(it) to tokens.text(it) }
    }

    private fun assertCovers(source: String) {
        val tokens = LuaLexer.lex(source)
        if (source.isEmpty()) {
            assertEquals(0, tokens.size)
            return
        }
        assertEquals("first token starts late", 0, tokens.start(0))
        for (i in 0 until tokens.size) assertTrue("token $i is empty", tokens.end(i) > tokens.start(i))
        for (i in 0 until tokens.size - 1) assertEquals("gap or overlap after token $i", tokens.end(i), tokens.start(i + 1))
        assertEquals("last token ends early", source.length, tokens.end(tokens.size - 1))
    }

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    // ---- words and operators ----------------------------------------------

    @Test
    fun `keywords are told apart from names`() {
        assertEquals(
            listOf(KEYWORD to "local", NAME to "endx", OPERATOR to "=", KEYWORD to "goto", NAME to "_G", KEYWORD to "elseif"),
            code("local endx = goto _G elseif"),
        )
    }

    @Test
    fun `every reserved word is a keyword`() {
        for (word in LuaLexer.KEYWORDS) assertEquals(word, listOf(KEYWORD to word), code(word))
    }

    @Test
    fun `operators take the longest match`() {
        val tokens = code("a...b..c::d==e~=f<=g>=h.i")
        assertEquals(
            listOf("a", "...", "b", "..", "c", "::", "d", "==", "e", "~=", "f", "<=", "g", ">=", "h", ".", "i"),
            tokens.map { it.second },
        )
        assertTrue(tokens.filterIndexed { i, _ -> i % 2 == 1 }.all { it.first == OPERATOR })
    }

    @Test
    fun `whitespace includes vertical tab and form feed`() {
        assertEquals(listOf(NAME to "a", NAME to "b", NAME to "c"), code("a\u000Bb\u000Cc"))
    }

    // ---- numbers ------------------------------------------------------------

    @Test
    fun `every numeric form is one number`() {
        for (number in listOf("123", "1.5", ".5", "5.", "1e10", "1E+10", "3e-2", "0x1F", "0X1f", "0x1p4", "0x.8p-2", "0xA.8P+1")) {
            assertEquals(number, listOf(NUMBER to number), code(number))
        }
    }

    @Test
    fun `a malformed number is still one number`() {
        assertEquals(listOf(NUMBER to "1e"), code("1e"))
        assertEquals(listOf(NUMBER to "0x"), code("0x"))
        assertEquals(listOf(NUMBER to "3abc"), code("3abc"))
    }

    @Test
    fun `a sign after a hex digit e is not an exponent`() {
        assertEquals(listOf(NUMBER to "0x1e", OPERATOR to "-", NUMBER to "5"), code("0x1e-5"))
    }

    // ---- strings --------------------------------------------------------------

    @Test
    fun `quoted strings honour their escapes`() {
        assertEquals(listOf(STRING to "\"a\\\"b\""), code("\"a\\\"b\""))
        assertEquals(listOf(STRING to "'it'", STRING to "'s'"), code("'it''s'"))
        assertEquals(listOf(STRING to "'a\"b'"), code("'a\"b'"))
    }

    @Test
    fun `backslash z and an escaped line break keep a string open`() {
        val skipped = LuaLexer.lex("\"a\\z\n   b\" x")
        assertEquals(STRING, skipped.kind(0))
        assertFalse(skipped.unterminated(0))
        assertEquals("\"a\\z\n   b\"", skipped.text(0))

        val continued = LuaLexer.lex("'a\\\nb'")
        assertEquals(1, continued.size)
        assertFalse(continued.unterminated(0))
    }

    @Test
    fun `an unterminated quoted string stops at its line end`() {
        val source = "x = \"abc\ny = 1"
        val tokens = LuaLexer.lex(source)
        val string = (0 until tokens.size).first { tokens.kind(it) == STRING }
        assertTrue(tokens.unterminated(string))
        assertEquals("\"abc", tokens.text(string))
        assertEquals(WHITESPACE, tokens.kind(string + 1))
        assertEquals(NAME to "y", code(source)[3])
    }

    @Test
    fun `a string cut off by the end of the file is unterminated`() {
        val plain = LuaLexer.lex("s = 'abc")
        assertTrue(plain.unterminated(plain.size - 1))
        val trailingEscape = LuaLexer.lex("s = 'abc\\")
        assertTrue(trailingEscape.unterminated(trailingEscape.size - 1))
    }

    // ---- long brackets --------------------------------------------------------

    @Test
    fun `long strings close only at their own level`() {
        val cases = listOf("[[x]]" to 0, "[=[x]]]=]" to 1, "[==[ ]=] ]==]" to 2, "[===[]===]" to 3, "[====[ a ]====]" to 4)
        for ((source, level) in cases) {
            val tokens = LuaLexer.lex(source)
            assertEquals(source, 1, tokens.size)
            assertEquals(source, LONG_STRING, tokens.kind(0))
            assertEquals(source, level, tokens.level(0))
            assertFalse(source, tokens.unterminated(0))
        }
    }

    @Test
    fun `a bracket that opens no long string is an operator`() {
        assertEquals(listOf(OPERATOR to "[", OPERATOR to "=", NAME to "x"), code("[=x"))
        assertEquals(listOf(NAME to "t", OPERATOR to "[", NUMBER to "1", OPERATOR to "]"), code("t[1]"))
    }

    @Test
    fun `an unterminated long string runs to the end of the file`() {
        val tokens = LuaLexer.lex("s = [==[ never ]] closed")
        val last = tokens.size - 1
        assertEquals(LONG_STRING, tokens.kind(last))
        assertTrue(tokens.unterminated(last))
        assertEquals(2, tokens.level(last))
    }

    // ---- comments -------------------------------------------------------------

    @Test
    fun `line and long comments`() {
        assertEquals(listOf(COMMENT to "-- hi", NAME to "x"), code("-- hi\nx"))
        assertEquals(listOf(LONG_COMMENT to "--[[ a\n b ]]", NAME to "x"), code("--[[ a\n b ]] x"))
        assertEquals(listOf(LONG_COMMENT to "--[==[ ]] ]==]"), code("--[==[ ]] ]==]"))
        assertEquals(listOf(COMMENT to "--[x[ not long", NAME to "y"), code("--[x[ not long\ny"))
    }

    @Test
    fun `an unterminated long comment runs to the end of the file`() {
        val open = LuaLexer.lex("--[[ never")
        assertEquals(1, open.size)
        assertEquals(LONG_COMMENT, open.kind(0))
        assertTrue(open.unterminated(0))
    }

    @Test
    fun `a hash line is skipped only at the very start of a file`() {
        assertEquals(listOf(SHEBANG to "#!/usr/bin/lua", NAME to "x"), code("#!/usr/bin/lua\nx"))
        assertEquals(listOf(SHEBANG to "#t"), code("#t"))
        assertEquals(listOf(NAME to "x", OPERATOR to "=", OPERATOR to "#", NAME to "t"), code("x = #t"))
    }

    // ---- what Lua cannot read ------------------------------------------------

    @Test
    fun `characters Lua cannot use are unknown, one code point each`() {
        assertEquals(listOf(UNKNOWN to "$", UNKNOWN to "@", UNKNOWN to "~", UNKNOWN to "\\"), code("$ @ ~ \\"))
        val emoji = LuaLexer.lex("\uD83D\uDE00")
        assertEquals(1, emoji.size)
        assertEquals(UNKNOWN, emoji.kind(0))
        assertEquals(2, emoji.end(0))
        assertEquals(listOf(UNKNOWN to "\u00E9"), code("\u00E9"))
    }

    // ---- the invariant ----------------------------------------------------------

    @Test
    fun `tokens cover every demo script exactly`() {
        for (name in DEMOS) assertCovers(demo(name))
    }

    @Test
    fun `every prefix of a demo lexes and covers`() {
        for (name in DEMOS) {
            val source = demo(name)
            var end = 0
            while (end <= source.length) {
                assertCovers(source.substring(0, end))
                end += 37
            }
        }
    }

    @Test
    fun `mutated scripts still lex and cover`() {
        val random = Random(20_260_910)
        val alphabet = "\"'[]=-\n\\z{}()#.0x1e+ \t"
        for (name in DEMOS) {
            val source = demo(name)
            repeat(40) {
                val chars = StringBuilder(source)
                repeat(12) {
                    val at = random.nextInt(chars.length + 1)
                    if (random.nextBoolean() && at < chars.length) {
                        chars.deleteCharAt(at)
                    } else {
                        chars.insert(at, alphabet[random.nextInt(alphabet.length)])
                    }
                }
                assertCovers(chars.toString())
            }
        }
    }

    @Test
    fun `the demos lex to real Lua with nothing unknown`() {
        val kinds = DEMOS.flatMap { name ->
            val tokens = LuaLexer.lex(demo(name))
            (0 until tokens.size).map { tokens.kind(it) }
        }.toSet()
        for (kind in listOf(WHITESPACE, COMMENT, STRING, NUMBER, NAME, KEYWORD, OPERATOR)) {
            assertTrue("no $kind in any demo", kind in kinds)
        }
        assertFalse("a demo lexed to a character Lua cannot read", UNKNOWN in kinds)
    }

    // ---- navigation -------------------------------------------------------------

    @Test
    fun `indexAt finds the token under an offset`() {
        val source = "local x = 1"
        val tokens = LuaLexer.lex(source)
        assertEquals("local", tokens.text(tokens.indexAt(0)))
        assertEquals("local", tokens.text(tokens.indexAt(4)))
        assertEquals(" ", tokens.text(tokens.indexAt(5)))
        assertEquals("x", tokens.text(tokens.indexAt(6)))
        assertEquals("1", tokens.text(tokens.indexAt(source.length)))
        assertEquals(-1, tokens.indexAt(source.length + 1))
        assertEquals(-1, tokens.indexAt(-1))
        assertEquals(-1, LuaLexer.lex("").indexAt(0))
    }

    @Test
    fun `nextCode and prevCode step over layout and comments`() {
        val tokens = LuaLexer.lex("a --c\n --[[d]] b")
        val a = tokens.indexAt(0)
        val b = tokens.nextCode(a)
        assertTrue(tokens.matches(b, "b"))
        assertEquals(a, tokens.prevCode(b))
        assertEquals(-1, tokens.nextCode(b))
        assertEquals(-1, tokens.prevCode(a))
    }

    @Test
    fun `matches compares a whole token`() {
        val tokens = LuaLexer.lex("render renderer")
        assertTrue(tokens.matches(0, "render"))
        assertFalse(tokens.matches(2, "render"))
        assertFalse(tokens.matches(0, "rend"))
    }

    private companion object {
        val DEMOS = listOf("cipher-tool", "ui-kitchen-sink", "todo-list", "text-tools", "math-mode")
    }
}
