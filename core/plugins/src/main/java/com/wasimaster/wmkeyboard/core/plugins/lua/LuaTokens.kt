package com.wasimaster.wmkeyboard.core.plugins.lua

/** A half-open range of UTF-16 offsets into a source string. */
data class LuaSpan(val start: Int, val end: Int) {
    val length: Int
        get() = end - start

    operator fun contains(offset: Int): Boolean = offset in start until end
}

/** What a run of source text is, as far as a lexer can tell without parsing. */
enum class LuaTokenKind {
    WHITESPACE,
    COMMENT,
    LONG_COMMENT,
    STRING,
    LONG_STRING,
    NUMBER,
    NAME,
    KEYWORD,
    OPERATOR,

    /** A `#` line at the very start of a file, which Lua skips. */
    SHEBANG,

    /** A character that starts no Lua token: `$`, `@`, a lone `~`, a non-ASCII letter. */
    UNKNOWN,
    ;

    /** True for a token the parser reads, false for layout and comments. */
    val isCode: Boolean
        get() = this != WHITESPACE && this != COMMENT && this != LONG_COMMENT && this != SHEBANG
}

/**
 * Every token in one source string, kept as parallel primitive arrays.
 *
 * Arrays rather than a list of token objects because the plugin editor lexes on
 * every keystroke. A 256 KB script is around 60 000 tokens, and 60 000
 * allocations a keystroke is typing latency paid to the garbage collector. The
 * arrays cost ten bytes a token and one allocation each.
 *
 * The tokens cover the source exactly: the first starts at 0, each one ends
 * where the next begins, and the last ends at the end of the source. Anything
 * coloured token by token, the highlighter above all, is therefore the same
 * length as the text by construction rather than by care.
 */
class LuaTokens internal constructor(
    val source: String,
    private val kinds: ByteArray,
    private val starts: IntArray,
    private val ends: IntArray,
    private val flags: ByteArray,
    val size: Int,
) {

    fun kind(index: Int): LuaTokenKind = KINDS[kinds[index].toInt()]

    fun start(index: Int): Int = starts[index]

    fun end(index: Int): Int = ends[index]

    fun span(index: Int): LuaSpan = LuaSpan(starts[index], ends[index])

    /** True when a string or long bracket reached a line end or the end of the file without closing. */
    fun unterminated(index: Int): Boolean = (flags[index].toInt() and UNTERMINATED) != 0

    /** How many `=` a long bracket opens with: 0 for `[[`, 2 for `[==[`. */
    fun level(index: Int): Int = (flags[index].toInt() and 0xFF) ushr 1

    /** The token's text. Allocates, so hot paths use [matches] instead. */
    fun text(index: Int): String = source.substring(starts[index], ends[index])

    /** Whether the token is exactly [word], compared in place. */
    fun matches(index: Int, word: String): Boolean {
        val start = starts[index]
        return ends[index] - start == word.length && source.regionMatches(start, word, 0, word.length)
    }

    /**
     * The token containing [offset], or the last token when [offset] is the end of
     * the source. -1 for an empty source or an offset outside it.
     */
    fun indexAt(offset: Int): Int {
        if (size == 0 || offset < 0 || offset > source.length) return -1
        if (offset == source.length) return size - 1
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                ends[mid] <= offset -> low = mid + 1
                starts[mid] > offset -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** The next token after [index] that the parser reads, or -1. */
    fun nextCode(index: Int): Int {
        var i = index + 1
        while (i < size) {
            if (kind(i).isCode) return i
            i++
        }
        return -1
    }

    /** The nearest token before [index] that the parser reads, or -1. */
    fun prevCode(index: Int): Int {
        var i = index - 1
        while (i >= 0) {
            if (kind(i).isCode) return i
            i--
        }
        return -1
    }

    internal companion object {
        const val UNTERMINATED = 1

        /** Seven bits of the flag byte hold the level; nothing real comes close. */
        const val MAX_LEVEL = 127

        private val KINDS = LuaTokenKind.entries.toTypedArray()
    }
}
