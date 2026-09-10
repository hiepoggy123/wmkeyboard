package com.wasimaster.wmkeyboard.core.plugins.lua

/**
 * The API name the plugin editor explains under the code: the one the caret is
 * on, or the function whose brackets or table the caret is inside. From tokens
 * alone, so it follows the caret through a file that does not parse.
 */
object LuaApiLookup {

    private const val MAX_CALL_TOKENS = 2000

    /**
     * `string.format` for a caret in `format`, `wm.storage` for one in `storage`,
     * `string.upper` for one in `s:upper`, and `ui.button` for a caret anywhere in
     * `ui.button { ... }`. Null for anything the API does not list.
     */
    fun at(tokens: LuaTokens, caret: Int): LuaApiEntry? {
        val name = nameAt(tokens, caret)
        if (name >= 0) entryEndingAt(tokens, name)?.let { return it }
        return enclosingCall(tokens, caret)
    }

    /** The function whose call is still open at [caret]: `print(` or `ui.label {` with no closer yet. */
    private fun enclosingCall(tokens: LuaTokens, caret: Int): LuaApiEntry? {
        if (caret <= 0) return null
        var at = tokens.indexAt(caret - 1)
        if (at >= 0 && !tokens.kind(at).isCode) at = tokens.prevCode(at)
        var depth = 0
        var steps = 0
        while (at >= 0 && steps++ < MAX_CALL_TOKENS) {
            if (tokens.kind(at) == LuaTokenKind.OPERATOR) {
                when {
                    tokens.matches(at, ")") || tokens.matches(at, "}") || tokens.matches(at, "]") -> depth++
                    tokens.matches(at, "(") || tokens.matches(at, "{") || tokens.matches(at, "[") -> {
                        if (depth > 0) {
                            depth--
                        } else {
                            if (tokens.matches(at, "[")) return null
                            val callee = tokens.prevCode(at)
                            if (callee < 0 || tokens.kind(callee) != LuaTokenKind.NAME) return null
                            return entryEndingAt(tokens, callee)?.takeIf { it.kind == LuaApiKind.FUNCTION }
                        }
                    }
                }
            }
            at = tokens.prevCode(at)
        }
        return null
    }

    /** The API entry a name chain ends in at [last]: `wm.storage.get`, or a string method after a colon. */
    private fun entryEndingAt(tokens: LuaTokens, last: Int): LuaApiEntry? {
        val before = tokens.prevCode(last)
        if (before >= 0 && tokens.matches(before, ":")) return LuaApi.find("string." + tokens.text(last))
        val parts = ArrayList<String>()
        var at = last
        while (at >= 0 && tokens.kind(at) == LuaTokenKind.NAME) {
            parts += tokens.text(at)
            val dot = tokens.prevCode(at)
            if (dot < 0 || !tokens.matches(dot, ".")) break
            at = tokens.prevCode(dot)
        }
        return LuaApi.find(parts.asReversed().joinToString("."))
    }

    private fun nameAt(tokens: LuaTokens, caret: Int): Int {
        if (caret < tokens.source.length) {
            val here = tokens.indexAt(caret)
            if (here >= 0 && tokens.kind(here) == LuaTokenKind.NAME) return here
        }
        val before = if (caret > 0) tokens.indexAt(caret - 1) else -1
        return if (before >= 0 && tokens.kind(before) == LuaTokenKind.NAME) before else -1
    }
}
