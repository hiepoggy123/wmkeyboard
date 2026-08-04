package com.wasimaster.wmkeyboard.core.snippets

import com.wasimaster.wmkeyboard.content.R
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
    fun `the sample repository's pattern pack expands the way it is advertised`() {
        // The published pack, decoded and run end to end. The two examples are
        // the ones the documentation promises, so a change that quietly breaks
        // them fails here rather than on somebody's phone.
        val imported = SnippetFile.decode(fixture("pattern-replies.wmsnippets.json"))
        assertNotNull(imported)
        assertTrue(imported!!.repairs.isEmpty())
        assertTrue(imported.snippets.all { !it.triggerPattern.isNullOrBlank() })

        val index = SnippetIndex.of(imported.snippets)
        assertEquals(
            "Hello, John! Nice to meet you.",
            index.matchPattern("hello John", atFieldStart = true)?.text,
        )
        val letter = index.matchPattern("thanks Sarah", atFieldStart = true)
        assertTrue(letter!!.text.startsWith("Dear Sarah,"))
        assertEquals("thanks Sarah", letter.consumedText)
        // The transform is what turns a typed name into a written one.
        assertEquals(
            "Happy birthday, Sarah! Have a wonderful one.",
            index.matchPattern("bday sarah", atFieldStart = true)?.text,
        )
        // Every pattern starts with a plain word, so none of them costs a
        // check after every word the user types.
        assertTrue(imported.snippets.all { SnippetMatcher.headOf(it.triggerPattern.orEmpty()) != null })
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
        // The note names the snippet it dropped: the name is the argument
        // that fills %1$s, so that is what the assertion checks.
        assertTrue(
            imported.repairs.any {
                it.stringRes == R.string.core_content_snippet_repair_no_text &&
                    it.args == listOf<Any>("Empty")
            },
        )
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
        assertTrue(
            imported.repairs.any {
                it.pluralsRes == R.plurals.core_content_snippet_repair_shortened &&
                    it.args.first() == "Huge"
            },
        )
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
        assertTrue(
            imported.repairs.any {
                it.pluralsRes == R.plurals.core_content_snippet_repair_kept_first &&
                    it.args == listOf<Any>(500)
            },
        )
    }

    @Test
    fun `a pattern that will not compile is removed and the snippet kept`() {
        // A row is dropped only when there is nothing left to insert. A snippet
        // whose trigger stopped working still inserts from the panel.
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"Greet","text":"Hello","triggerPattern":"^hi (.+$"}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!
        assertEquals("Greet", imported.snippets.single().label)
        assertNull(imported.snippets.single().triggerPattern)
        assertTrue(
            imported.repairs.any {
                it.stringRes == R.string.core_content_snippet_repair_bad_pattern &&
                    it.args == listOf<Any>("Greet")
            },
        )
    }

    @Test
    fun `an over-long pattern is removed and reported`() {
        val huge = "a".repeat(SnippetMatcher.MAX_PATTERN_LENGTH + 1)
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"Long","text":"t","triggerPattern":"$huge"}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!
        assertNull(imported.snippets.single().triggerPattern)
        assertTrue(
            imported.repairs.any {
                it.pluralsRes == R.plurals.core_content_snippet_repair_pattern_too_long &&
                    it.args.first() == "Long"
            },
        )
    }

    @Test
    fun `an out of range word budget is clamped without a note`() {
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"A","text":"a","triggerPattern":"^a (.+)$","triggerWords":99},
              {"id":2,"label":"B","text":"b","triggerPattern":"^b (.+)$","triggerWords":-1}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!
        assertEquals(SnippetMatcher.MAX_WORDS, imported.snippets[0].triggerWords)
        assertEquals(0, imported.snippets[1].triggerWords)
        // A number nudged into range is not lost content.
        assertTrue(imported.repairs.isEmpty())
    }

    @Test
    fun `a plain snippet exports no pattern keys`() {
        // The published packs are hand-maintained files, so a plain snippet
        // must not grow two empty keys it never uses.
        val encoded = SnippetFile.encode(listOf(snippet(1, "Shrug", "x", "shrug")), 41, "1.4.0")
        assertTrue(!encoded.contains("triggerPattern"))
        assertTrue(!encoded.contains("triggerWords"))
    }

    @Test
    fun `a pattern snippet round-trips`() {
        val original = listOf(
            Snippet(
                id = 1,
                label = "Greet",
                text = "Hello, \$1!",
                triggerPattern = "^hi (.+)$",
                triggerWords = 2,
            ),
        )
        assertEquals(original, SnippetFile.decode(SnippetFile.encode(original, 41, "1.4.0"))!!.snippets)
    }

    @Test
    fun `a file written before patterns existed decodes without one`() {
        val text = """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"A","text":"a","trigger":"a"}
            ]}
        """.trimIndent()
        val imported = SnippetFile.decode(text)!!.snippets.single()
        assertNull(imported.triggerPattern)
        assertEquals(0, imported.triggerWords)
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
