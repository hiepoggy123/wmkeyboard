package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ceilings on the work the plugin editor does for the largest demo plugin,
 * math-mode.lua at about 1 600 lines. The ceilings are several times what a
 * laptop takes, so a slow CI machine passes and a change that makes the work
 * grow with the square of the file does not.
 *
 * Lexing runs on the main thread on every keystroke, so its ceiling is the
 * tight one. Parsing, analysis, diagnostics and completion run off the main
 * thread after a pause in typing.
 */
class LuaBenchmarkTest {

    private val source: String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/math-mode.lua")) { "missing math-mode.lua" }
            .bufferedReader()
            .use { it.readText() }

    /** The best of several runs after a warm-up, in milliseconds: the least noisy number a shared machine gives. */
    private fun bestOf(runs: Int = 5, work: () -> Unit): Double {
        repeat(3) { work() }
        return (1..runs).minOf {
            val start = System.nanoTime()
            work()
            (System.nanoTime() - start) / 1_000_000.0
        }
    }

    @Test
    fun `lexing the largest demo stays well inside a frame`() {
        val millis = bestOf { LuaLexer.lex(source) }
        assertTrue("lexing took $millis ms", millis < LEX_CEILING_MS)
    }

    @Test
    fun `parsing, analysis and diagnostics stay quick enough to follow typing`() {
        val millis = bestOf { LuaDiagnostics.of(LuaDocument.of(source), LuaHostShape(storage = true)) }
        assertTrue("a full pass took $millis ms", millis < CHECK_CEILING_MS)
    }

    @Test
    fun `completion from tokens alone stays quick at the end of the largest demo`() {
        val tokens = LuaLexer.lex(source)
        val analysis = LuaDocument.of(source).analysis
        val caret = source.indexOf("function render")
        val millis = bestOf { LuaCompletion.at(tokens, caret, explicit = true, analysis = analysis) }
        assertTrue("completion took $millis ms", millis < COMPLETION_CEILING_MS)
    }

    private companion object {
        const val LEX_CEILING_MS = 60.0
        const val CHECK_CEILING_MS = 600.0
        const val COMPLETION_CEILING_MS = 120.0
    }
}
