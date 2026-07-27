package com.wasimaster.wmkeyboard.core.fonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The platform's own "can this be loaded" check is injected, so everything here
 * runs off device. What is actually being tested is the header sniff and the
 * store bookkeeping around it.
 */
class FontFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(dir: File? = File(temp.root, "fonts")) = FontStore(dir?.apply { mkdirs() })

    /** A plausible TrueType file: the sfnt magic, then filler. */
    private fun ttfBytes(size: Int = 2048): ByteArray =
        byteArrayOf(0x00, 0x01, 0x00, 0x00) + ByteArray(size) { it.toByte() }

    private fun import(
        store: FontStore,
        bytes: ByteArray,
        name: String = "Test Font",
        loads: Boolean = true,
    ) = FontFile.import(
        input = ByteArrayInputStream(bytes),
        store = store,
        name = name,
        loader = { loads },
    )

    // ---- accepting -----------------------------------------------------

    @Test
    fun `a TrueType file is imported and registered`() {
        val s = store()
        val result = import(s, ttfBytes())
        assertTrue("$result", result is FontImportResult.Imported)
        val font = (result as FontImportResult.Imported).font
        assertEquals("Test Font", font.name)
        assertEquals(1, s.fonts().size)
        assertNotNull(s.existingFileFor(font.id))
    }

    @Test
    fun `every sfnt flavour is accepted`() {
        for (magic in listOf("OTTO", "true", "ttcf")) {
            val s = store(File(temp.root, "fonts-$magic"))
            val bytes = magic.toByteArray(Charsets.ISO_8859_1) + ByteArray(512)
            assertTrue(magic, import(s, bytes) is FontImportResult.Imported)
        }
    }

    @Test
    fun `importing the same file twice keeps both, with distinct names`() {
        // Two files can legitimately be the same face at different weights;
        // silently overwriting is what the old single-slot import did wrong.
        val s = store()
        import(s, ttfBytes(), name = "Inter")
        import(s, ttfBytes(), name = "Inter")
        assertEquals(2, s.fonts().size)
        assertEquals(2, s.fonts().map { it.name }.distinct().size)
    }

    @Test
    fun `the stored file name comes from the id, never the source`() {
        // Nothing from the imported file may reach the filesystem as a path.
        val s = store()
        val font = (import(s, ttfBytes(), name = "../../etc/passwd") as FontImportResult.Imported).font
        assertEquals(FontStore.fileNameFor(font.id), font.fileName)
        assertTrue(s.existingFileFor(font.id)!!.canonicalPath.startsWith(temp.root.canonicalPath))
    }

    // ---- refusing ------------------------------------------------------

    @Test
    fun `a web font is refused with a message that says why`() {
        // The likely mistake: this is what a browser's network tab hands you.
        val s = store()
        val result = import(s, "wOFF".toByteArray() + ByteArray(512))
        assertTrue("$result", result is FontImportResult.NotAFont)
        assertTrue((result as FontImportResult.NotAFont).message.contains("WOFF"))
        assertTrue(s.fonts().isEmpty())
    }

    @Test
    fun `a zip and an HTML page are refused as what they are`() {
        assertTrue(
            (import(store(), "PK".toByteArray(Charsets.ISO_8859_1) + ByteArray(64))
                as FontImportResult.NotAFont).message.contains("zip"),
        )
        assertTrue(
            (import(store(File(temp.root, "f2")), "<!DOCTYPE html>".toByteArray())
                as FontImportResult.NotAFont).message.contains("HTML"),
        )
    }

    @Test
    fun `an empty or truncated file is refused`() {
        assertTrue(import(store(), ByteArray(0)) is FontImportResult.NotAFont)
        assertTrue(import(store(File(temp.root, "f2")), byteArrayOf(0, 1)) is FontImportResult.NotAFont)
    }

    @Test
    fun `a font the platform cannot load is refused and leaves nothing behind`() {
        // Valid header, unusable file — finding out now means the picker can
        // say so, rather than the keyboard silently falling back later.
        val s = store()
        val result = import(s, ttfBytes(), loads = false)
        assertTrue("$result", result is FontImportResult.NotAFont)
        assertTrue(s.fonts().isEmpty())
        assertTrue(File(temp.root, "fonts").listFiles().orEmpty().none { it.name.endsWith(".ttf") })
    }

    @Test
    fun `a file over the size cap is refused`() {
        val s = store()
        val huge = ttfBytes((FontStore.MAX_BYTES + 1024).toInt())
        assertTrue(import(s, huge) is FontImportResult.Failed)
        assertTrue(s.fonts().isEmpty())
    }

    @Test
    fun `the font cap is enforced`() {
        val s = store()
        repeat(FontStore.MAX_FONTS) { import(s, ttfBytes(), name = "F$it") }
        assertTrue(import(s, ttfBytes(), name = "one too many") is FontImportResult.TooManyFonts)
        assertEquals(FontStore.MAX_FONTS, s.fonts().size)
    }

    // ---- store ---------------------------------------------------------

    @Test
    fun `fonts survive a reload`() {
        val dir = File(temp.root, "fonts")
        val id = (import(store(dir), ttfBytes()) as FontImportResult.Imported).font.id
        assertNotNull(FontStore(dir).font(id))
    }

    @Test
    fun `reconcile drops an entry whose file was deleted by hand`() {
        val dir = File(temp.root, "fonts")
        val id = (import(store(dir), ttfBytes()) as FontImportResult.Imported).font.id
        File(dir, FontStore.fileNameFor(id)).delete()
        assertTrue(FontStore(dir).fonts().isEmpty())
    }

    @Test
    fun `reconcile deletes a file nothing references`() {
        val dir = File(temp.root, "fonts")
        store(dir).save()
        File(dir, "orphan.ttf").writeBytes(ttfBytes())
        FontStore(dir)
        assertTrue(File(dir, "orphan.ttf").exists().not())
    }

    @Test
    fun `deleting a font removes its file`() {
        val s = store()
        val font = (import(s, ttfBytes()) as FontImportResult.Imported).font
        val file = s.existingFileFor(font.id)!!
        s.delete(font.id)
        assertTrue(s.fonts().isEmpty())
        assertTrue(file.exists().not())
    }

    @Test
    fun `with no directory the store reads as empty and imports fail cleanly`() {
        val s = store(dir = null)
        assertTrue(s.fonts().isEmpty())
        assertNull(s.fileFor("anything"))
        assertTrue(import(s, ttfBytes()) is FontImportResult.Failed)
    }

    @Test
    fun `a font records the languages it claims to cover`() {
        val s = store()
        val result = FontFile.import(
            input = ByteArrayInputStream(ttfBytes()),
            store = s,
            name = "Inter",
            langIds = listOf("en", " ru ", "el", "en", ""),
            loader = { true },
        )
        val font = (result as FontImportResult.Imported).font
        // Trimmed, de-duplicated, blanks dropped — the picker filters on these.
        assertEquals(listOf("en", "ru", "el"), font.langIds)
        assertTrue("a text face by default", !font.emoji)
    }

    @Test
    fun `emoji faces are kept apart from the text faces`() {
        val s = store()
        FontFile.import(
            input = ByteArrayInputStream(ttfBytes()),
            store = s,
            name = "Inter",
            loader = { true },
        )
        FontFile.import(
            input = ByteArrayInputStream(ttfBytes()),
            store = s,
            name = "Twemoji",
            emoji = true,
            loader = { true },
        )
        assertEquals(2, s.fonts().size)
        // The key-label pickers must never offer a colour emoji font, and the
        // emoji picker must never offer a text one.
        assertEquals(listOf("Inter"), s.textFonts().map { it.name })
        assertEquals(listOf("Twemoji"), s.emojiFonts().map { it.name })
    }

    @Test
    fun `the emoji flag and languages survive a reload`() {
        val dir = File(temp.root, "fonts")
        val first = store(dir)
        FontFile.import(
            input = ByteArrayInputStream(ttfBytes()),
            store = first,
            name = "Twemoji",
            langIds = listOf("en"),
            emoji = true,
            loader = { true },
        )
        val reopened = FontStore(dir)
        val font = reopened.fonts().single()
        assertTrue(font.emoji)
        assertEquals(listOf("en"), font.langIds)
    }

    @Test
    fun `the settings font id round-trips through the store id`() {
        val fontId = FontStore.fontIdFor("font_123")
        assertTrue(fontId.startsWith(FontStore.ID_PREFIX))
        assertEquals("font_123", FontStore.storeIdOf(fontId))
        // Ids belonging to the older fixed slots and to Google Fonts must not
        // be mistaken for library ids.
        assertNull(FontStore.storeIdOf("custom"))
        assertNull(FontStore.storeIdOf("google:Inter"))
        assertNull(FontStore.storeIdOf("default"))
    }
}
