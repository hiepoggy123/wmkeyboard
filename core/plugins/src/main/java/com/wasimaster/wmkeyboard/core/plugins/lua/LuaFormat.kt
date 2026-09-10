package com.wasimaster.wmkeyboard.core.plugins.lua

/**
 * Re-indents Lua from its tokens. Only the leading whitespace of each line ever
 * changes; every other character is copied as it was.
 *
 * Deliberately not a pretty-printer built on the parse tree. luaj's tree keeps
 * no comments and holds a number as its value rather than as it was written, so
 * printing from it would delete every comment and turn `0xFF` into `255`. It also
 * cannot print a file that does not parse, and a file stops parsing exactly when
 * its author is mid-edit and reaches for Format. What a phone author needs from
 * Format is their indentation put back after typing on glass, and that is all
 * this does.
 *
 * Indentation comes from what is still open, and from where each open thing is
 * anchored:
 *
 *  * A bracket is anchored at the level its line was printed at, so what is
 *    inside it sits one step in from that line, however that line was indented.
 *  * A block keyword (`then`, `do`, `function`, `repeat`) is anchored at the line
 *    its statement started on. A condition wrapped over three lines still has its
 *    body one step in from the `if`.
 *  * Several openers on one line share one anchor, so `foo(function()` indents
 *    its body once, and a line starting with a closer lines up with the line that
 *    opened it: `end)`, `},`, `else`.
 *  * A line that carries on the expression before it, leading with `and`, `or`,
 *    `..` or a `:method`, or following a line that ended on one, steps one further
 *    in. Not when a bracket opened within that same expression is still open: its
 *    own step already says the line belongs to it.
 *
 * That is the style every demo plugin is written in, and the tests hold the
 * formatter to it by requiring each demo to come out unchanged.
 *
 * A line that starts inside a long string or a long comment is copied exactly:
 * its leading spaces are part of the string's value.
 */
object LuaFormat {

    fun reindent(source: String, indent: String = "  "): String {
        val tokens = LuaLexer.lex(source)
        val out = StringBuilder(source.length + source.length / 8)
        val open = OpenItems()
        var chainLine = 0
        var chainLevel = 0
        var line = 0
        var lineStart = 0
        var first = 0
        while (true) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) source.length else newline
            while (first < tokens.size && tokens.start(first) < lineStart) first++
            var past = first
            while (past < tokens.size && tokens.start(past) < lineEnd) past++

            val code = firstCode(tokens, first, past)
            val closing = code >= 0 && startsByClosing(tokens, code)
            val continuing = code >= 0 && !closing && continues(tokens, code)
            var level = when {
                open.size == 0 -> 0
                closing -> open.topAnchor
                else -> open.topAnchor + 1
            }
            if (continuing && !(open.size > 0 && open.topLine >= chainLine)) level++
            if (code >= 0 && !continuing) {
                chainLine = line
                chainLevel = level
            }

            if (startsInsideLongForm(tokens, lineStart)) {
                out.append(source, lineStart, lineEnd)
            } else {
                val lead = leadingEnd(source, lineStart, lineEnd)
                // A carriage return ends a Windows line; it is not content.
                val content = if (lineEnd > lead && source[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
                if (lead < content) {
                    repeat(level) { out.append(indent) }
                    out.append(source, lead, lineEnd)
                } else {
                    // A blank line keeps no indentation of its own.
                    out.append(source, content, lineEnd)
                }
            }
            for (index in first until past) track(tokens, index, line, level, chainLevel, open)
            if (newline < 0) break
            out.append('\n')
            lineStart = newline + 1
            line++
        }
        return out.toString()
    }

    @Suppress("LongParameterList")
    private fun track(tokens: LuaTokens, index: Int, line: Int, level: Int, chainLevel: Int, open: OpenItems) {
        if (!tokens.kind(index).isCode) return
        when {
            isBracketOpener(tokens, index) -> open.push(level, line)
            isBlockOpener(tokens, index) -> open.push(chainLevel, line)
            isCloser(tokens, index) -> open.pop()
            // else closes the branch before it and opens its own, level with itself.
            tokens.matches(index, "else") -> {
                open.pop()
                open.push(level, line)
            }
            // elseif closes the branch before it; its own `then` opens the next.
            tokens.matches(index, "elseif") -> open.pop()
        }
    }

    private fun startsInsideLongForm(tokens: LuaTokens, offset: Int): Boolean {
        val index = tokens.indexAt(offset)
        if (index < 0) return false
        val kind = tokens.kind(index)
        val spanning = kind == LuaTokenKind.LONG_STRING || kind == LuaTokenKind.LONG_COMMENT || kind == LuaTokenKind.STRING
        return spanning && tokens.start(index) < offset && offset < tokens.end(index)
    }

    private fun leadingEnd(source: String, from: Int, to: Int): Int {
        var index = from
        while (index < to && (source[index] == ' ' || source[index] == '\t')) index++
        return index
    }

    /** The first token on the line that the parser reads, or -1 for a line of comments. */
    private fun firstCode(tokens: LuaTokens, from: Int, to: Int): Int {
        for (index in from until to) if (tokens.kind(index).isCode) return index
        return -1
    }

    /** Whether a line whose first code token is [index] starts by closing something. */
    private fun startsByClosing(tokens: LuaTokens, index: Int): Boolean =
        isCloser(tokens, index) || tokens.matches(index, "else") || tokens.matches(index, "elseif")

    /**
     * Whether a line whose first code token is [index] continues the expression
     * before it. A leading minus straight after a comma or an opening bracket is a
     * new item in a list, not a continuation.
     */
    private fun continues(tokens: LuaTokens, index: Int): Boolean {
        val previous = tokens.prevCode(index)
        if (previous < 0) return false
        val afterItemStart = tokens.kind(previous) == LuaTokenKind.OPERATOR && tokens.text(previous) in ITEM_STARTS
        if (leadsOn(tokens, index) && !afterItemStart) return true
        return trailsOff(tokens, previous)
    }

    private fun leadsOn(tokens: LuaTokens, index: Int): Boolean = when (tokens.kind(index)) {
        LuaTokenKind.KEYWORD -> tokens.matches(index, "and") || tokens.matches(index, "or")
        LuaTokenKind.OPERATOR -> tokens.text(index) in LEADING_OPERATORS
        else -> false
    }

    private fun trailsOff(tokens: LuaTokens, index: Int): Boolean = when (tokens.kind(index)) {
        LuaTokenKind.KEYWORD -> tokens.matches(index, "and") || tokens.matches(index, "or")
        LuaTokenKind.OPERATOR -> tokens.text(index) in TRAILING_OPERATORS
        else -> false
    }

    private fun isBracketOpener(tokens: LuaTokens, index: Int): Boolean =
        tokens.kind(index) == LuaTokenKind.OPERATOR &&
            (tokens.matches(index, "(") || tokens.matches(index, "{") || tokens.matches(index, "["))

    private fun isBlockOpener(tokens: LuaTokens, index: Int): Boolean =
        tokens.kind(index) == LuaTokenKind.KEYWORD &&
            (tokens.matches(index, "function") || tokens.matches(index, "do") ||
                tokens.matches(index, "then") || tokens.matches(index, "repeat"))

    private fun isCloser(tokens: LuaTokens, index: Int): Boolean = when (tokens.kind(index)) {
        LuaTokenKind.KEYWORD -> tokens.matches(index, "end") || tokens.matches(index, "until")
        LuaTokenKind.OPERATOR -> tokens.matches(index, ")") || tokens.matches(index, "}") || tokens.matches(index, "]")
        else -> false
    }

    private val BINARY_OPERATORS = setOf("..", "+", "-", "*", "/", "%", "^", "==", "~=", "<", "<=", ">", ">=")
    private val LEADING_OPERATORS = BINARY_OPERATORS + setOf(":", ".")
    private val TRAILING_OPERATORS = BINARY_OPERATORS + "="
    private val ITEM_STARTS = setOf(",", "{", "(", "[", ";")

    /** Everything still open, innermost last: the level each is anchored at, and the line it opened on. */
    private class OpenItems {
        private var anchors = IntArray(16)
        private var lines = IntArray(16)

        var size = 0
            private set

        val topAnchor: Int
            get() = anchors[size - 1]

        val topLine: Int
            get() = lines[size - 1]

        fun push(anchor: Int, line: Int) {
            if (size == anchors.size) {
                anchors = anchors.copyOf(size * 2)
                lines = lines.copyOf(size * 2)
            }
            anchors[size] = anchor
            lines[size] = line
            size++
        }

        /** A closer with nothing open, in a file being typed, is simply ignored. */
        fun pop() {
            if (size > 0) size--
        }
    }
}
