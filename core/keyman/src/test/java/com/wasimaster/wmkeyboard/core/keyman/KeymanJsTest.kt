package com.wasimaster.wmkeyboard.core.keyman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pulling the touch layout out of a compiled keyboard.
 *
 * A `.kmp` package carries no `.keyman-touch-layout`; the compiler folds it into
 * the keyboard's `.js` as the `KVKL` property. Reading it back means finding one
 * assignment in a minified file and matching its braces, which is the kind of
 * thing that works on the file you tried and fails on the next one. So the
 * fixtures here are real compiled keyboards, and the test that matters is that
 * what comes out converts into a usable grid rather than merely parsing.
 */
class KeymanJsTest {

    private fun js(id: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("js/$id.js")) {
            "missing fixture js/$id.js"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `the touch layout comes out of every fixture`() {
        for (id in FIXTURES) {
            val extracted = KeymanJs.extractTouchLayout(js(id))
            assertTrue("$id: ${(extracted as? KeymanResult.Failure)?.fault}", extracted is KeymanResult.Success)
        }
    }

    /**
     * The real check. Extracting a balanced string proves the brace matching
     * stopped somewhere; converting it proves it stopped in the right place.
     */
    @Test
    fun `what comes out converts into a usable grid`() {
        for (id in FIXTURES) {
            val json = (KeymanJs.extractTouchLayout(js(id)) as KeymanResult.Success).value
            val doc = KeymanTouchLayoutReader.parse(json)
            assertTrue("$id did not parse as a touch layout", doc is KeymanResult.Success)

            val converted = TouchLayoutConverter.convert((doc as KeymanResult.Success).value, id, id)
            assertTrue("$id did not convert", converted is KeymanResult.Success)
            val spec = (converted as KeymanResult.Success).value.layout
            assertTrue("$id has no letters layer", LETTERS in spec.layers)
            assertTrue("$id converted to an empty grid", spec.layers.values.any { it.rows.isNotEmpty() })
        }
    }

    /**
     * The grid from the package must match the grid from the source file. They
     * are the same data by two routes, and if they ever disagree the bundled
     * layouts and an imported package would put different keys in front of the
     * user for the same keyboard.
     */
    @Test
    fun `the compiled grid matches the source touch layout`() {
        for (id in listOf("khmer_angkor", "lao_2008_basic", "basic_kbdus")) {
            val fromJs = grid(id, (KeymanJs.extractTouchLayout(js(id)) as KeymanResult.Success).value)
            val fromSource = grid(id, sourceTouchLayout(id))
            assertEquals("$id: layer names differ", fromSource.keys, fromJs.keys)
            for ((layer, rows) in fromSource) {
                assertEquals("$id: layer '$layer' row shape differs", rows, fromJs[layer])
            }
        }
    }

    private fun grid(id: String, json: String): Map<String, List<Int>> {
        val doc = (KeymanTouchLayoutReader.parse(json) as KeymanResult.Success).value
        val spec = (TouchLayoutConverter.convert(doc, id, id) as KeymanResult.Success).value.layout
        return spec.layers.mapValues { (_, layer) -> layer.rows.map { it.size } }
    }

    private fun sourceTouchLayout(id: String): String =
        checkNotNull(
            javaClass.classLoader?.getResourceAsStream("touch/$id.keyman-touch-layout"),
        ) { "missing fixture touch/$id.keyman-touch-layout" }.use { it.readBytes().decodeToString() }

    /** A keyboard with no touch layout is a plain answer, not an exception. */
    @Test
    fun `a file with no touch layout is reported, not thrown`() {
        val result = KeymanJs.extractTouchLayout("function Keyboard_x(){this.KI='x';}")
        assertTrue(result is KeymanResult.Failure)
        assertEquals(KeymanFault.JS_NO_TOUCH_LAYOUT, (result as KeymanResult.Failure).fault)
    }

    @Test
    fun `truncated input never throws`() {
        val full = js("basic_kbdus")
        for (cut in listOf(0, 1, 64, 512, full.length / 3, full.length / 2, full.length - 1)) {
            val text = full.take(cut)
            // Either answer is fine. Throwing is not: this runs on a file the
            // user chose, and a crash there loses the whole import.
            KeymanJs.extractTouchLayout(text)
            KeymanJs.extractDefaultKeys(text)
        }
    }

    /**
     * A brace inside a string must not end the object. Key caps in this corpus
     * include `{` and `}`, so this is a real shape, not a contrived one.
     */
    @Test
    fun `braces inside strings do not end the object`() {
        val text = """function K(){this.KVKL={"phone":{"layer":[{"id":"default","row":[{"id":1,"key":[""" +
            """{"id":"K_A","text":"}"},{"id":"K_B","text":"{"}]}]}]}};}"""
        val json = (KeymanJs.extractTouchLayout(text) as KeymanResult.Success).value
        val doc = (KeymanTouchLayoutReader.parse(json) as KeymanResult.Success).value
        val keys = doc.preferred()!!.layer.first().row.first().key
        assertEquals(2, keys.size)
        assertEquals("}", keys[0].text)
        assertEquals("{", keys[1].text)
    }

    private companion object {
        val FIXTURES = listOf("basic_kbdus", "khmer_angkor", "lao_2008_basic")
        const val LETTERS = "letters"
    }
}
