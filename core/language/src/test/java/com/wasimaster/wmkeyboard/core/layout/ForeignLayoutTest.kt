package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.language.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a layout written for FlorisBoard or HeliBoard.
 *
 * The fixtures are hand-written minimal files, not packs taken from either
 * project: FlorisBoard is Apache-2.0 and HeliBoard is GPL-3.0, and this repo is
 * MIT, so the formats are exercised without any of their content entering the
 * tree.
 */
class ForeignLayoutTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("foreign/$name")) {
            "missing test fixture foreign/$name"
        }.use { it.readBytes().decodeToString() }

    private fun ConvertedLayout.letters(): List<List<Key>> =
        checkNotNull(layout.layer(LayoutLayer.LETTERS)).rows

    private fun ConvertedLayout.keys(): List<Key> = letters().flatten()

    // ---- the text format ----

    @Test
    fun `a blank line starts a new row`() {
        val converted = checkNotNull(ForeignLayouts.fromSimpleText(fixture("heliboard_qwerty.txt"), "q.txt"))
        // Three rows in the file. Repair appends the missing delete, space and
        // enter keys, which land on the last row rather than making a fourth.
        assertEquals(3, converted.letters().size)
        assertEquals(listOf("q", "w", "e"), converted.letters()[0].map { it.label })
    }

    @Test
    fun `the tokens after the label become the press-and-hold letters`() {
        val converted = checkNotNull(ForeignLayouts.fromSimpleText(fixture("heliboard_qwerty.txt"), "q.txt"))
        val e = converted.keys().first { it.label == "e" }
        assertEquals(listOf("3", "é", "è", "ê"), e.longPress)
    }

    @Test
    fun `a comment line is not a key`() {
        val converted = checkNotNull(ForeignLayouts.fromSimpleText(fixture("heliboard_qwerty.txt"), "q.txt"))
        assertTrue(converted.keys().none { it.label.startsWith("//") })
    }

    @Test
    fun `a file with no keys converts to nothing`() {
        assertNull(ForeignLayouts.fromSimpleText("", "empty.txt"))
        assertNull(ForeignLayouts.fromSimpleText("// only a comment\n\n\n", "empty.txt"))
    }

    // ---- the JSON format ----

    @Test
    fun `every key shape is read`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        val keys = converted.keys()
        // auto_text_key and text_key both commit their own label.
        assertEquals("q", keys.first { it.label == "q" }.label)
        // multi_text_key joins its code points into one output.
        val multi = keys.first { it.label == "th" }
        assertEquals("th", multi.output ?: multi.label)
    }

    @Test
    fun `main comes before relevant so it becomes the hint`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        assertEquals(listOf("é", "è"), converted.keys().first { it.label == "e" }.longPress)
    }

    @Test
    fun `an auto_text_key gets no shift label`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        // A null shiftLabel is what makes the keyboard uppercase the key itself.
        // Writing "Q" in would freeze the case for every script that has none.
        assertTrue(converted.keys().filter { it.action == KeyAction.Text }.all { it.shiftLabel == null })
    }

    @Test
    fun `negative codes become the matching actions`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        val actions = converted.keys().map { it.action }
        assertTrue(KeyAction.Shift in actions)
        assertTrue(KeyAction.Delete in actions)
        assertTrue(KeyAction.Symbols in actions)
        assertTrue(actions.any { it is KeyAction.SendKey && it.keyCode == KEYCODE_TAB })
    }

    @Test
    fun `space and enter are actions even though their codes are positive`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        val actions = converted.keys().map { it.action }
        assertTrue(KeyAction.Space in actions)
        assertTrue(KeyAction.Enter in actions)
        // Exactly one of each: if code 32 had stayed a text key, repair would
        // have found no space key and added a second spacebar.
        assertEquals(1, actions.count { it == KeyAction.Space })
        assertEquals(1, actions.count { it == KeyAction.Enter })
    }

    @Test
    fun `a key this keyboard has no action for is dropped and reported`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        // -233 is voice input, which is a tool here rather than a key.
        assertEquals(listOf(-233), converted.unmapped)
        assertTrue(converted.keys().none { it.label == "voice" })
        val note = converted.notes.first { it.pluralsRes == R.plurals.core_lang_foreign_keys_dropped }
        assertEquals(1, note.quantity)
        assertTrue(note.args.contains("-233"))
    }

    @Test
    fun `a key that points at a popup group it did not ship is reported`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        assertTrue(converted.keys().first { it.label == "r" }.longPress.isEmpty())
        assertTrue(converted.notes.any { it.pluralsRes == R.plurals.core_lang_foreign_popups_missing })
    }

    @Test
    fun `an icon key ignores the word the file drew on it`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        // The keyboard draws shift and delete from icon slots, so a file that
        // labels them "shift" and "delete" must not put those words on screen.
        val shift = converted.keys().first { it.action == KeyAction.Shift }
        assertEquals("", shift.label)
        assertEquals("", converted.keys().first { it.action == KeyAction.Delete }.label)
        // A text-drawn action keeps the file's label.
        assertEquals("?123", converted.keys().first { it.action == KeyAction.Symbols }.label)
    }

    @Test
    fun `comments and a trailing comma do not fail the file`() {
        // Both fixtures carry them, because HeliBoard's own reader allows both
        // and these files are hand-written.
        assertNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        assertNotNull(ForeignLayouts.fromFlorisJson(fixture("heliboard_bengali.json"), "b.json"))
    }

    @Test
    fun `text that is not JSON converts as the text format`() {
        val converted = checkNotNull(ForeignLayouts.convert(fixture("heliboard_qwerty.txt"), "q.txt"))
        assertEquals(ForeignSource.HELIBOARD_TEXT, converted.source)
        assertEquals(
            ForeignSource.FLORIS_JSON,
            checkNotNull(ForeignLayouts.convert(fixture("floris_qwerty.json"), "f.json")).source,
        )
    }

    @Test
    fun `a file that is neither converts to nothing`() {
        assertNull(ForeignLayouts.fromFlorisJson("{not json", "x.json"))
        assertNull(ForeignLayouts.fromFlorisJson("""{"shopping":["milk"]}""", "x.json"))
        assertNull(ForeignLayouts.fromFlorisJson("[[],[]]", "x.json"))
    }

    // ---- the language guess ----

    @Test
    fun `the language is guessed from the letters and never written in`() {
        val bengali = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("heliboard_bengali.json"), "b.json"))
        assertEquals("bn", bengali.guessedLangId)
        val latin = checkNotNull(ForeignLayouts.fromFlorisJson(fixture("floris_qwerty.json"), "f.json"))
        assertEquals("en", latin.guessedLangId)
        // Left blank in the layout itself: the guess has to pass in front of
        // someone before it can reach storage and pick a dictionary.
        assertEquals("", bengali.layout.langId)
    }

    @Test
    fun `a layout with no letters at all guesses English`() {
        assertEquals("en", guessLangId(listOf(listOf(Key(label = "1"), Key(label = "!")))))
    }

    // ---- the name ----

    @Test
    fun `the layout is named after the file`() {
        val converted = checkNotNull(ForeignLayouts.convert("a\n", "/tmp/my_grid.wmlayout.json"))
        assertEquals("my grid", converted.layout.name)
        assertEquals("Imported layout", checkNotNull(ForeignLayouts.convert("a\n", ".json")).layout.name)
    }

    // ---- what must never come out ----

    @Test
    fun `no converted key ever carries the unknown action`() {
        // KeyAction.Unknown cannot be serialized — its serializer throws — so a
        // converted layout carrying one would take the whole custom-layouts
        // write down with it. The converter drops those keys instead.
        for (name in FIXTURES) {
            val converted = checkNotNull(ForeignLayouts.convert(fixture(name), name)) { name }
            assertTrue(name, converted.keys().none { it.action is KeyAction.Unknown })
        }
    }

    @Test
    fun `every conversion survives being written out`() {
        for (name in FIXTURES) {
            val converted = checkNotNull(ForeignLayouts.convert(fixture(name), name)) { name }
            // The assertion that matters most in this file: encode() is what
            // upsertCustomLayout calls, inside a DataStore edit that catches
            // nothing, and an unwritable action would take the whole
            // custom-layouts store down with it.
            val stored = converted.withLanguage(converted.guessedLangId)
            assertEquals(name, stored, LayoutCodec.decode(LayoutCodec.encode(stored)))
        }
    }

    @Test
    fun `a layout stored with no language silently becomes English`() {
        // Not a wish, a fact about LayoutCodec.migrateLayout — and the reason
        // withLanguage is the only way out of a conversion. If this ever starts
        // failing, that guard can be reconsidered.
        val converted = checkNotNull(ForeignLayouts.convert(fixture("heliboard_bengali.json"), "b.json"))
        assertEquals("", converted.layout.langId)
        val reread = checkNotNull(LayoutCodec.decode(LayoutCodec.encode(converted.layout)))
        assertEquals("en", reread.langId)
        // Going through withLanguage is what keeps the Bengali grid Bengali.
        assertEquals("bn", converted.withLanguage("bn").langId)
        assertEquals("bn", converted.withLanguage("").langId)
    }

    @Test
    fun `every conversion is already repaired`() {
        for (name in FIXTURES) {
            val converted = checkNotNull(ForeignLayouts.convert(fixture(name), name)) { name }
            // The keyboard repairs again on every layout compile and throws the
            // notes away. Anything left to fix here would be a silent
            // difference between the grid the user approved and the one they
            // type on.
            assertEquals(name, converted.layout, converted.layout.repair().spec)
            assertTrue(name, converted.layout.repair().repairNotes.isEmpty())
        }
    }

    @Test
    fun `every conversion can be turned on`() {
        for (name in FIXTURES) {
            val converted = checkNotNull(ForeignLayouts.convert(fixture(name), name)) { name }
            assertTrue(name, converted.layout.canBeEnabled())
        }
    }

    @Test
    fun `a conversion round-trips through the layout file format`() {
        for (name in FIXTURES) {
            val converted = checkNotNull(ForeignLayouts.convert(fixture(name), name)) { name }
            val stored = converted.withLanguage(converted.guessedLangId)
            val reread = checkNotNull(LayoutFile.decode(LayoutFile.encode(stored, 0, ""))) { name }
            assertEquals(name, stored.layers, reread.layout.layers)
            // Nothing left for the file's own repair pass either.
            assertTrue(name, reread.repairNotes.isEmpty())
        }
    }

    @Test
    fun `the missing bottom row is added back, once`() {
        val converted = checkNotNull(ForeignLayouts.fromSimpleText(fixture("heliboard_qwerty.txt"), "q.txt"))
        // The text format cannot spell these three, so every file is missing
        // all of them and repair is expected to supply them.
        val actions = converted.keys().map { it.action }
        assertEquals(1, actions.count { it == KeyAction.Delete })
        assertEquals(1, actions.count { it == KeyAction.Space })
        assertEquals(1, actions.count { it == KeyAction.Enter })
        assertTrue(converted.notes.any { it.stringRes == R.string.core_lang_repair_delete_key_added })
    }

    private companion object {
        val FIXTURES = listOf("heliboard_qwerty.txt", "floris_qwerty.json", "heliboard_bengali.json")

        const val KEYCODE_TAB = 61
    }

    @Test
    fun `a bare string is a key`() {
        val converted = checkNotNull(ForeignLayouts.fromFlorisJson("""[["a","b"]]""", "x.json"))
        assertEquals(listOf("a", "b"), converted.letters()[0].take(2).map { it.label })
    }

    @Test
    fun `rows wrapped in an object are found`() {
        assertNotNull(ForeignLayouts.fromFlorisJson("""{"arrangement":[["a"]]}""", "x.json"))
        assertFalse(ForeignLayouts.fromFlorisJson("""{"arrangement":[["a"]]}""", "x.json")!!.keys().isEmpty())
    }
}
