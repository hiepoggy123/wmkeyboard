package com.wasimaster.wmkeyboard.core.plugins.lua

/**
 * Splits Lua 5.2 source into [LuaTokens]. Hand-written, one pass, no regex.
 *
 * Built for text that is broken most of the time, because someone is typing it.
 * It never throws, it gives every character to exactly one token, and an
 * unterminated construct stops where Lua itself says the damage stops: a quoted
 * string at the end of its line, a long bracket at the end of the file.
 *
 * It draws the same token boundaries as luaj's own grammar, including long
 * brackets at any level and a `#` line at the very start of a file, so the
 * highlighter and the parser never disagree about where a string ends.
 */
object LuaLexer {

    /** The reserved words of Lua 5.2. `goto` is one; it was not in 5.1. */
    val KEYWORDS: Set<String> = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if",
        "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
    )

    private const val LONGEST_KEYWORD = 8

    /** [KEYWORDS] grouped by length, so a name is compared only with words it could be. */
    private val KEYWORDS_BY_LENGTH: Array<Array<String>> = Array(LONGEST_KEYWORD + 1) { length ->
        KEYWORDS.filter { it.length == length }.toTypedArray()
    }

    fun lex(source: String): LuaTokens {
        val out = Builder(source)
        val length = source.length
        var i = 0
        if (length > 0 && source[0] == '#') {
            i = lineEnd(source, 0)
            out.add(LuaTokenKind.SHEBANG, 0, i)
        }
        while (i < length) {
            val start = i
            val c = source[i]
            when {
                isSpace(c) -> {
                    i = spaceEnd(source, i)
                    out.add(LuaTokenKind.WHITESPACE, start, i)
                }

                c == '-' && i + 1 < length && source[i + 1] == '-' -> i = comment(source, i, out)
                c == '"' || c == '\'' -> i = shortString(source, i, out)
                c == '[' && openerLevel(source, i) >= 0 -> i = longBracket(source, i, i, LuaTokenKind.LONG_STRING, out)
                isDigit(c) || (c == '.' && i + 1 < length && isDigit(source[i + 1])) -> {
                    i = numberEnd(source, i)
                    out.add(LuaTokenKind.NUMBER, start, i)
                }

                isNameStart(c) -> {
                    i = nameEnd(source, i)
                    out.add(if (isKeyword(source, start, i)) LuaTokenKind.KEYWORD else LuaTokenKind.NAME, start, i)
                }

                else -> {
                    val operator = operatorLength(source, i)
                    if (operator > 0) {
                        i += operator
                        out.add(LuaTokenKind.OPERATOR, start, i)
                    } else {
                        // One whole code point, so an emoji is one token rather than two halves.
                        i += Character.charCount(source.codePointAt(i))
                        out.add(LuaTokenKind.UNKNOWN, start, i)
                    }
                }
            }
        }
        return out.build()
    }

    /** A `--` at [start]: a long comment if a long bracket follows at once, a line comment otherwise. */
    private fun comment(source: String, start: Int, out: Builder): Int {
        val bracket = start + 2
        if (bracket < source.length && source[bracket] == '[' && openerLevel(source, bracket) >= 0) {
            return longBracket(source, start, bracket, LuaTokenKind.LONG_COMMENT, out)
        }
        val end = lineEnd(source, start)
        out.add(LuaTokenKind.COMMENT, start, end)
        return end
    }

    private fun shortString(source: String, start: Int, out: Builder): Int {
        val quote = source[start]
        val length = source.length
        var i = start + 1
        while (i < length) {
            when (source[i]) {
                quote -> {
                    out.add(LuaTokenKind.STRING, start, i + 1)
                    return i + 1
                }

                '\n', '\r' -> {
                    out.add(LuaTokenKind.STRING, start, i, unterminated = true)
                    return i
                }

                '\\' -> i = escapeEnd(source, i)
                else -> i++
            }
        }
        out.add(LuaTokenKind.STRING, start, length, unterminated = true)
        return length
    }

    /** Past the escape whose backslash is at [at]. */
    private fun escapeEnd(source: String, at: Int): Int {
        val length = source.length
        val next = at + 1
        if (next >= length) return length
        val after = next + 1
        return when (source[next]) {
            // \z skips the whitespace after it, line breaks included.
            'z' -> spaceEnd(source, after)
            // A backslash before a line break carries the string on; either two-character break counts once.
            '\r' -> if (after < length && source[after] == '\n') after + 1 else after
            '\n' -> if (after < length && source[after] == '\r') after + 1 else after
            else -> after
        }
    }

    /**
     * The level of the long bracket opening at [at] (`[`, some `=`, `[`), or -1 when
     * the `[` there opens no long bracket.
     */
    private fun openerLevel(source: String, at: Int): Int {
        var i = at + 1
        while (i < source.length && source[i] == '=') i++
        return if (i < source.length && source[i] == '[') i - at - 1 else -1
    }

    /** A long string or long comment whose bracket opens at [openAt], as one token from [tokenStart]. */
    private fun longBracket(source: String, tokenStart: Int, openAt: Int, kind: LuaTokenKind, out: Builder): Int {
        val level = openerLevel(source, openAt)
        var i = source.indexOf(']', openAt + level + 2)
        while (i >= 0) {
            if (closesAt(source, i, level)) {
                val end = i + level + 2
                out.add(kind, tokenStart, end, level = level)
                return end
            }
            i = source.indexOf(']', i + 1)
        }
        out.add(kind, tokenStart, source.length, level = level, unterminated = true)
        return source.length
    }

    private fun closesAt(source: String, at: Int, level: Int): Boolean {
        val close = at + level + 1
        if (close >= source.length) return false
        for (i in at + 1 until close) if (source[i] != '=') return false
        return source[close] == ']'
    }

    /**
     * The end of a number starting at [start]. Takes the longest run that could be
     * one, the way Lua's own lexer does, so `1e` and `3abc` are one malformed number
     * for a diagnostic to name rather than a number followed by a stray name.
     */
    private fun numberEnd(source: String, start: Int): Int {
        val length = source.length
        val hex = start + 1 < length && source[start] == '0' && (source[start + 1] == 'x' || source[start + 1] == 'X')
        val lower = if (hex) 'p' else 'e'
        val upper = if (hex) 'P' else 'E'
        var i = if (hex) start + 2 else start
        while (i < length) {
            val c = source[i]
            if ((c == lower || c == upper) && i + 1 < length && (source[i + 1] == '+' || source[i + 1] == '-')) {
                i += 2
            } else if (isNameChar(c) || c == '.') {
                i++
            } else {
                break
            }
        }
        return i
    }

    private fun nameEnd(source: String, start: Int): Int {
        var i = start
        while (i < source.length && isNameChar(source[i])) i++
        return i
    }

    private fun isKeyword(source: String, start: Int, end: Int): Boolean {
        val length = end - start
        if (length > LONGEST_KEYWORD) return false
        for (word in KEYWORDS_BY_LENGTH[length]) {
            if (source.regionMatches(start, word, 0, length)) return true
        }
        return false
    }

    /** How long the operator at [at] is, longest match first, or 0 when none starts there. */
    private fun operatorLength(source: String, at: Int): Int {
        val next = if (at + 1 < source.length) source[at + 1] else ' '
        return when (source[at]) {
            '.' -> when {
                next != '.' -> 1
                at + 2 < source.length && source[at + 2] == '.' -> 3
                else -> 2
            }

            ':' -> if (next == ':') 2 else 1
            '=', '<', '>' -> if (next == '=') 2 else 1
            // Lua 5.2 has no unary ~; only ~= means anything.
            '~' -> if (next == '=') 2 else 0
            '+', '-', '*', '/', '%', '^', '#', '(', ')', '{', '}', '[', ']', ';', ',' -> 1
            else -> 0
        }
    }

    private fun lineEnd(source: String, from: Int): Int {
        var i = from
        while (i < source.length && source[i] != '\n' && source[i] != '\r') i++
        return i
    }

    private fun spaceEnd(source: String, from: Int): Int {
        var i = from
        while (i < source.length && isSpace(source[i])) i++
        return i
    }

    /** Lua's whitespace: space, tab, the two line breaks, vertical tab and form feed. */
    private fun isSpace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C'

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    /** Lua names are ASCII. Anything else outside a string or comment is not Lua. */
    private fun isNameStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isNameChar(c: Char): Boolean = isNameStart(c) || isDigit(c)

    private class Builder(private val source: String) {
        private var capacity = source.length / 3 + 16
        private var kinds = ByteArray(capacity)
        private var starts = IntArray(capacity)
        private var ends = IntArray(capacity)
        private var flags = ByteArray(capacity)
        private var size = 0

        fun add(kind: LuaTokenKind, start: Int, end: Int, level: Int = 0, unterminated: Boolean = false) {
            if (end <= start) return
            if (size == capacity) grow()
            kinds[size] = kind.ordinal.toByte()
            starts[size] = start
            ends[size] = end
            val levelBits = minOf(level, LuaTokens.MAX_LEVEL) shl 1
            flags[size] = (levelBits or if (unterminated) LuaTokens.UNTERMINATED else 0).toByte()
            size++
        }

        private fun grow() {
            capacity *= 2
            kinds = kinds.copyOf(capacity)
            starts = starts.copyOf(capacity)
            ends = ends.copyOf(capacity)
            flags = flags.copyOf(capacity)
        }

        fun build(): LuaTokens = LuaTokens(source, kinds, starts, ends, flags, size)
    }
}
