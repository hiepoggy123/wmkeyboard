package com.wasimaster.wmkeyboard.core.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetFileTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("addons/$name")) {
            "missing test fixture addons/$name"
        }.use { it.readBytes().decodeToString() }

    private fun snippet(id: Long, label: String, text: String, trigger: String? = null) =
        Snippet(id = id, label = label, text = text, trigger = trigger)

    @Test
    fun `reads the sample repository's snippet pack`() {
        val imported = SnippetFile.decode(fixture("dev-shortcuts.wmsnippets.json"))
        assertNotNull(imported)
        assertTrue(imported!!.snippets.isNotEmpty())
        // The sample deliberately includes one entry with no trigger.
        assertTrue(imported.snippets.any { it.trigger == null })
        assertTrue(imported.snippets.all { it.text.isNotBlank() })
    }

    @Test
    fun `round-trips`() {
        val original = listOf(
            snippet(1, "Shrug", "¯\\_(ツ)_/¯", "shrug"),
            snippet(2, "Sign-off", "Best,\nWasi"),
        )
        val decoded = SnippetFile.decode(SnippetFile.encode(original, 41, "1.4.0"))
        assertNotNull(decoded)
        assertEquals(original, decoded!!.snippets)
        assertEquals(41, decoded.fromAppVersion)
    }

    @Test
    fun `rejects a file that is not a snippet pack`() {
        assertNull(SnippetFile.decode("""{"format":"wmkeyboard-layout","version":1}"""))
        assertNull(SnippetFile.decode("""{"snippets":[]}"""))
        assertNull(SnippetFile.decode("not json"))
    }

    @Test
    fun `a snippet with no text is dropped and reported`() {
        // There is nothing for it to insert.
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"Empty","text":""},
              {"id":2,"label":"Real","text":"hello"}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!
        assertEquals(listOf("Real"), imported.snippets.map { it.label })
        assertTrue(imported.repairs.any { it.contains("Empty") })
    }

    @Test
    fun `a snippet with no label is named after its text`() {
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"","text":"first line\nsecond line"}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!
        assertEquals("first line", imported.snippets.single().label)
        assertTrue(imported.repairs.isNotEmpty())
    }

    @Test
    fun `a blank trigger becomes no trigger`() {
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"A","text":"a","trigger":"   "}
            ]}
        """.trimIndent()
        assertNull(SnippetFile.decode(text)!!.snippets.single().trigger)
    }

    @Test
    fun `an over-long snippet is truncated rather than refused`() {
        val huge = "x".repeat(50_000)
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"Huge","text":"$huge"}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!
        assertTrue(imported.snippets.single().text.length < huge.length)
        assertTrue(imported.repairs.any { it.contains("Huge") })
    }

    @Test
    fun `an absurd number of snippets is capped`() {
        val many = (1..600).joinToString(",") {
            """{"id":$it,"label":"S$it","text":"t$it"}"""
        }
        val imported = SnippetFile.decode(
            """{"format":"wmkeyboard-snippets","version":1,"snippets":[$many]}""",
        )!!
        assertEquals(500, imported.snippets.size)
        assertTrue(imported.repairs.any { it.contains("500") })
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"futureThing":7,"snippets":[
              {"id":1,"label":"A","text":"a","colour":"red"}
            ]}
        """.trimIndent()
        assertEquals(1, SnippetFile.decode(text)!!.snippets.size)
    }

    @Test
    fun `the file extension stays compound so plain json is unclaimed`() {
        // A bare .json association would offer this app for every JSON file on
        // the device.
        assertTrue(SnippetFile.FILE_EXTENSION.endsWith(".json"))
        assertTrue(SnippetFile.FILE_EXTENSION.startsWith("wm"))
    }
}
