package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The gate on every diagnostic: the demo plugins are the code the documentation
 * teaches from, so a check that calls anything in them an error or a warning is
 * the check that is wrong.
 */
class LuaDemoCorpusTest {

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `no demo plugin has an error or a warning`() {
        val problems = DEMOS.flatMap { (name, storage) ->
            val source = demo(name)
            val document = LuaDocument.of(source)
            assertNotNull("$name must be analysed", document.analysis)
            LuaDiagnostics.of(document, LuaHostShape(storage))
                .filter { it.severity != LuaSeverity.INFO }
                .map { "$name:${lineOf(source, it.span.start)} ${it.code} ${it.arg1.orEmpty()} ${it.arg2.orEmpty()}".trim() }
        }
        assertEquals(emptyList<String>(), problems)
    }

    private fun lineOf(source: String, offset: Int): Int = source.substring(0, offset).count { it == '\n' } + 1

    private companion object {
        /** Each demo and whether its manifest declares storage. */
        val DEMOS = listOf(
            "cipher-tool" to false,
            "ui-kitchen-sink" to false,
            "todo-list" to true,
            "text-tools" to false,
            "math-mode" to true,
        )
    }
}
