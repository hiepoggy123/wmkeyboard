package com.wasimaster.wmkeyboard.core.plugins.lua

/**
 * One version of a script, read every way the plugin editor needs: its tokens
 * always, the parser's verdict, and what its names mean when it parses.
 *
 * Built off the main thread after a pause in typing. Whatever the editor draws
 * while a finger is on the glass reads [tokens] alone.
 */
class LuaDocument private constructor(
    val source: String,
    val tokens: LuaTokens,
    val syntax: LuaSyntax,
    /** Null unless [syntax] is [LuaSyntax.Valid] and the tree could be walked. */
    val analysis: LuaAnalysis?,
    internal val blocks: LuaBlocks,
) {
    companion object {
        fun of(source: String): LuaDocument {
            val tokens = LuaLexer.lex(source)
            val parsed = LuaParse.parse(source, tokens)
            val blocks = LuaBlocks.of(tokens)
            val analysis = parsed.chunk?.let { LuaAnalyse.analyse(source, tokens, blocks, it) }
            return LuaDocument(source, tokens, parsed.syntax, analysis, blocks)
        }
    }
}

/**
 * The last two documents built. Diagnostics, completion and the outline all ask
 * about the same text after one pause in typing, and the preview asks about the
 * text it last ran, so two covers both without keeping old scripts alive.
 */
object LuaDocuments {
    private const val CAPACITY = 2
    private val recent = ArrayDeque<LuaDocument>(CAPACITY)

    fun of(source: String): LuaDocument {
        synchronized(recent) {
            recent.firstOrNull { it.source === source || it.source == source }?.let { return it }
        }
        val built = LuaDocument.of(source)
        synchronized(recent) {
            recent.removeAll { it.source == source }
            recent.addFirst(built)
            while (recent.size > CAPACITY) recent.removeLast()
        }
        return built
    }
}
