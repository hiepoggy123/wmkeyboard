package com.wasimaster.wmkeyboard.core.plugins.lua

/**
 * Line and column to character offset, counted the way luaj's JavaCC parser
 * counts them: lines break at `\n`, at `\r\n` and at a `\r` alone, and a column
 * is one UTF-16 character, a tab included, because luaj builds its char stream
 * with a tab size of 1.
 */
internal class LuaLines(private val source: String) {

    private val starts: IntArray = run {
        val found = ArrayList<Int>()
        found.add(0)
        for (index in source.indices) {
            val character = source[index]
            if (character == '\n' || (character == '\r' && source.getOrNull(index + 1) != '\n')) found.add(index + 1)
        }
        found.toIntArray()
    }

    /** The offset of a 1-based [line] and [column], kept inside that line. */
    fun offset(line: Int, column: Int): Int {
        if (line < 1) return 0
        if (line > starts.size) return source.length
        val start = starts[line - 1]
        val end = if (line < starts.size) starts[line] - 1 else source.length
        return (start + column - 1).coerceIn(start, maxOf(start, end))
    }
}

/**
 * The keywords that open and close blocks, paired the way Lua's grammar pairs
 * them: `function`, `do`, `then` and `repeat` open; `end` and `until` close;
 * `elseif` closes the `then` before it; `else` closes one block and opens the
 * next. `while` and `for` open nothing of their own, because their `do` does.
 *
 * Exact on a script that parses. On one that does not, pairs are made where they
 * can be, and what is left over is kept in [unclosed] and [strays].
 */
internal class LuaBlocks private constructor(
    private val closers: IntArray,
    private val openers: IntArray,
    private val enclosing: IntArray,
    /** Openers nothing closed, outermost first. */
    val unclosed: IntArray,
    /** Closers with nothing open to close. */
    val strays: IntArray,
) {
    /** The token that closes the block [opener] starts, or -1. */
    fun closerOf(opener: Int): Int = closers.getOrElse(opener) { -1 }

    /** The token that opened the block [closer] ends, or -1. For `else`, the `then` it closes. */
    fun openerOf(closer: Int): Int = openers.getOrElse(closer) { -1 }

    /** The innermost opener still open at [token], or -1 at the top of the file. */
    fun enclosing(token: Int): Int = enclosing.getOrElse(token) { -1 }

    companion object {
        fun of(tokens: LuaTokens): LuaBlocks {
            val size = tokens.size
            val closers = IntArray(size) { -1 }
            val openers = IntArray(size) { -1 }
            val enclosing = IntArray(size) { -1 }
            val stack = IntArray(size)
            var depth = 0
            val strays = ArrayList<Int>()
            for (index in 0 until size) {
                enclosing[index] = if (depth > 0) stack[depth - 1] else -1
                if (tokens.kind(index) != LuaTokenKind.KEYWORD) continue
                val isElse = tokens.matches(index, "else")
                val closes = isElse || tokens.matches(index, "end") || tokens.matches(index, "until") ||
                    tokens.matches(index, "elseif")
                val opens = isElse || tokens.matches(index, "function") || tokens.matches(index, "do") ||
                    tokens.matches(index, "then") || tokens.matches(index, "repeat")
                if (closes) {
                    if (depth > 0) {
                        val opener = stack[--depth]
                        closers[opener] = index
                        openers[index] = opener
                    } else {
                        strays += index
                    }
                }
                if (opens) stack[depth++] = index
            }
            return LuaBlocks(closers, openers, enclosing, stack.copyOf(depth), strays.toIntArray())
        }
    }
}
