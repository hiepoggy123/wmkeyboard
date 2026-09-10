package com.wasimaster.wmkeyboard.core.plugins.lua

import com.wasimaster.wmkeyboard.core.plugins.PluginFile
import org.luaj.vm2.ast.Chunk
import org.luaj.vm2.parser.LuaParser
import org.luaj.vm2.parser.ParseException
import org.luaj.vm2.parser.TokenMgrError
import java.io.StringReader

/** What the parser made of a script. The tree itself stays inside this module. */
sealed interface LuaSyntax {
    data object Valid : LuaSyntax

    /**
     * The parser stopped at [at]. [found] is the text it met there, or `<EOF>` at
     * the end of the file. [expected] names what would have been accepted there:
     * a keyword or operator as written, or one of `<NAME>`, `<NUMBER>`, `<STRING>`
     * and `<EOF>`. JavaCC's own sentence is never kept; the app puts these into
     * words the user reads.
     *
     * Where the grammar looks ahead to choose, [at] is the token the choice began
     * on: `f(` at the end of a file stops at its bracket, not after it.
     */
    data class Invalid(val at: LuaSpan, val found: String, val expected: List<String>) : LuaSyntax

    /** Not parsed at all, for [reason]. */
    data class Skipped(val reason: Reason) : LuaSyntax {
        enum class Reason { TOO_LARGE, TOO_DEEP }
    }
}

/** A parse as the language service keeps it: the verdict, and the tree when there is one. */
internal class LuaParsed(val syntax: LuaSyntax, val chunk: Chunk?)

/**
 * luaj's Lua 5.2 parser, run for the plugin editor.
 *
 * The only file in the service that names `org.luaj.vm2.parser`. The parser is
 * a pure function from text to a tree; it compiles nothing and runs nothing, and
 * no plugin can reach it, which is why it may ship at all (see
 * `proguard-rules.pro` and `LuaLanguageShrinkerTest`).
 *
 * Built for broken input, because a script being typed is broken most of the
 * time. It never throws, whatever the script.
 */
object LuaParse {

    /** Openers deeper than this are not parsed: the recursive-descent parser would be one bracket from a stack overflow. */
    const val MAX_NESTING = 200

    fun check(source: String): LuaSyntax = parse(source, LuaLexer.lex(source)).syntax

    @Suppress("TooGenericExceptionCaught")
    internal fun parse(source: String, tokens: LuaTokens): LuaParsed {
        if (source.length > PluginFile.MAX_SCRIPT_BYTES) {
            return LuaParsed(LuaSyntax.Skipped(LuaSyntax.Skipped.Reason.TOO_LARGE), null)
        }
        if (deepestNesting(tokens) > MAX_NESTING) {
            return LuaParsed(LuaSyntax.Skipped(LuaSyntax.Skipped.Reason.TOO_DEEP), null)
        }
        return try {
            LuaParsed(LuaSyntax.Valid, LuaParser(StringReader(source)).Chunk())
        } catch (failure: ParseException) {
            LuaParsed(invalid(source, tokens, failure), null)
        } catch (failure: TokenMgrError) {
            LuaParsed(lexicalFailure(source, tokens), null)
        } catch (failure: StackOverflowError) {
            LuaParsed(LuaSyntax.Skipped(LuaSyntax.Skipped.Reason.TOO_DEEP), null)
        } catch (failure: Throwable) {
            // A parser bug is not the author's error. Say nothing rather than point
            // at a place that is not wrong.
            LuaParsed(LuaSyntax.Valid, null)
        }
    }

    private fun invalid(source: String, tokens: LuaTokens, failure: ParseException): LuaSyntax {
        // JavaCC's currentToken is the last one it accepted; the failure is the next.
        val token = failure.currentToken?.let { it.next ?: it }
            ?: return lexicalFailure(source, tokens)
        val atEnd = token.kind == 0
        // JavaCC puts the end of the file on the last character it read, which is
        // not where anything is missing. The end of the text is.
        val lines = LuaLines(source)
        val start = if (atEnd) source.length else lines.offset(token.beginLine, token.beginColumn)
        val end = if (atEnd) source.length else lines.offset(token.endLine, token.endColumn) + 1
        val images = failure.tokenImage
        val expected = failure.expectedTokenSequences
            ?.mapNotNull { sequence -> sequence.firstOrNull()?.let { images?.getOrNull(it) } }
            ?.map(::plainImage)
            ?.distinct()
            .orEmpty()
        return LuaSyntax.Invalid(
            at = LuaSpan(start.coerceIn(0, source.length), end.coerceIn(start.coerceIn(0, source.length), source.length)),
            found = if (atEnd) "<EOF>" else token.image.orEmpty(),
            expected = expected,
        )
    }

    /**
     * A lexical error carries its position only inside an English sentence, so it
     * is placed from this module's own lexer: the first character Lua cannot read,
     * or the first string or comment that never closes.
     */
    private fun lexicalFailure(source: String, tokens: LuaTokens): LuaSyntax {
        for (index in 0 until tokens.size) {
            if (tokens.kind(index) == LuaTokenKind.UNKNOWN || tokens.unterminated(index)) {
                return LuaSyntax.Invalid(tokens.span(index), tokens.text(index).take(MAX_FOUND), emptyList())
            }
        }
        val last = (tokens.size - 1 downTo 0).firstOrNull { tokens.kind(it).isCode }
        val at = last?.let { LuaSpan(tokens.start(it), tokens.end(it)) } ?: LuaSpan(source.length, source.length)
        return LuaSyntax.Invalid(at, "", emptyList())
    }

    /** A token image as JavaCC wrote it, `"end"` or `<NAME>`, with the quotes taken off a literal. */
    private fun plainImage(image: String): String =
        if (image.length >= 2 && image.startsWith('"') && image.endsWith('"')) image.substring(1, image.length - 1) else image

    /** The deepest the brackets and blocks go, from tokens alone. A closer too many never goes below zero. */
    private fun deepestNesting(tokens: LuaTokens): Int {
        var depth = 0
        var deepest = 0
        for (index in 0 until tokens.size) {
            when (tokens.kind(index)) {
                LuaTokenKind.OPERATOR -> when {
                    tokens.matches(index, "(") || tokens.matches(index, "{") || tokens.matches(index, "[") -> depth++
                    tokens.matches(index, ")") || tokens.matches(index, "}") || tokens.matches(index, "]") -> depth--
                }
                LuaTokenKind.KEYWORD -> when {
                    tokens.matches(index, "function") || tokens.matches(index, "do") ||
                        tokens.matches(index, "then") || tokens.matches(index, "repeat") -> depth++
                    tokens.matches(index, "end") || tokens.matches(index, "until") -> depth--
                }
                else -> Unit
            }
            if (depth < 0) depth = 0
            if (depth > deepest) deepest = depth
        }
        return deepest
    }

    private const val MAX_FOUND = 40
}
