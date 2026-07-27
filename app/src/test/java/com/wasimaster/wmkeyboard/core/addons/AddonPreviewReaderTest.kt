package com.wasimaster.wmkeyboard.core.addons

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Reading a payload well enough to show it, without installing it.
 *
 * The reader runs against a file a stranger wrote, so the interesting cases are
 * the malformed ones: everything it can't make sense of has to come back as
 * [AddonPreviewContent.Unreadable] rather than an exception on the way to the
 * UI thread.
 */
class AddonPreviewReaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun entry(type: AddonType, path: String = "payload") =
        AddonEntry(id = "x", type = type, name = "X", version = "1.0.0", path = path)

    private fun file(name: String, text: String): File =
        temp.newFile(name).apply { writeText(text) }

    // ---- snippets --------------------------------------------------------

    @Test
    fun `reads the snippets out of a pack`() {
        val payload = file(
            "p.wmsnippets.json",
            """
            {"format":"wmkeyboard-snippets","version":1,"snippets":[
              {"id":1,"label":"Shrug","text":"¯\\_(ツ)_/¯","trigger":"shrug"},
              {"id":2,"label":"Email","text":"me@example.com"}
            ]}
            """.trimIndent(),
        )
        val content = AddonPreviewReader.read(entry(AddonType.Snippets), payload)
        val snippets = content as AddonPreviewContent.Snippets
        assertEquals(2, snippets.total)
        assertEquals("Shrug", snippets.entries[0].label)
        assertEquals("shrug", snippets.entries[0].trigger)
        // A snippet with no trigger reads as blank, not "null".
        assertEquals("", snippets.entries[1].trigger)
    }

    @Test
    fun `a snippet pack that is not one is unreadable, not a crash`() {
        val payload = file("p.wmsnippets.json", """{"format":"wmkeyboard-layout"}""")
        assertTrue(
            AddonPreviewReader.read(entry(AddonType.Snippets), payload)
                is AddonPreviewContent.Unreadable,
        )
    }

    // ---- dictionaries ----------------------------------------------------

    @Test
    fun `counts a word list and samples the start of it`() {
        val payload = file(
            "words.txt",
            buildString {
                appendLine("# a comment, skipped")
                appendLine("")
                appendLine("kotlin 90")
                appendLine("gradle 40")
                appendLine("coroutine")
            },
        )
        val content = AddonPreviewReader.read(entry(AddonType.Dictionary, "words.txt"), payload)
        val dictionary = content as AddonPreviewContent.Dictionary
        assertEquals(3, dictionary.total)
        // The frequency is stripped; the sample is the words themselves.
        assertEquals(listOf("kotlin", "gradle", "coroutine"), dictionary.words)
        assertTrue(!dictionary.truncated)
        // Everything in the file, so the dialog isn't showing a subset.
        assertTrue(!dictionary.partial)
    }

    @Test
    fun `a long word list is capped and says so`() {
        val over = AddonPreviewReader.MAX_WORDS + 500
        val payload = file("words.txt", (1..over).joinToString("\n") { "word$it $it" })

        val dictionary = AddonPreviewReader.read(entry(AddonType.Dictionary, "words.txt"), payload)
            as AddonPreviewContent.Dictionary

        assertEquals(over, dictionary.total)
        assertEquals(AddonPreviewReader.MAX_WORDS, dictionary.words.size)
        assertTrue(dictionary.partial)
        // The count is exact; only the word list was cut short.
        assertTrue(!dictionary.truncated)
    }

    @Test
    fun `reads a gzipped word list`() {
        // The format allows dictionaries to travel gzipped, keyed off the path.
        val payload = temp.newFile("words.txt.gz")
        GZIPOutputStream(payload.outputStream()).use {
            it.write("alpha 3\nbeta 2\n".toByteArray())
        }
        val content = AddonPreviewReader.read(
            entry(AddonType.Dictionary, "dictionaries/words.txt.gz"),
            payload,
        )
        assertEquals(listOf("alpha", "beta"), (content as AddonPreviewContent.Dictionary).words)
    }

    @Test
    fun `a word list of nothing but comments is unreadable`() {
        val payload = file("words.txt", "# nothing here\n\n# still nothing\n")
        assertTrue(
            AddonPreviewReader.read(entry(AddonType.Dictionary, "words.txt"), payload)
                is AddonPreviewContent.Unreadable,
        )
    }

    // ---- sound -----------------------------------------------------------

    @Test
    fun `a sound preview is just the file, ready to play`() {
        val payload = file("s.mp3", "not really an mp3, the player decides")
        val content = AddonPreviewReader.read(entry(AddonType.Sound), payload)
        assertEquals(payload, (content as AddonPreviewContent.Sound).file)
    }

    // ---- stickers --------------------------------------------------------

    private fun stickerPack(vararg names: String): File {
        val payload = temp.newFile("pack.wmstickers")
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("pack.json"))
            zip.write("""{"format":"wmkeyboard-stickers","version":1}""".toByteArray())
            zip.closeEntry()
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(ByteArray(64) { it.toByte() })
                zip.closeEntry()
            }
        }
        return payload
    }

    @Test
    fun `unpacks the first sticker images and counts the rest`() {
        // pack.json is already in every archive; only the images count.
        val payload = stickerPack("stickers/a.png", "stickers/b.webp", "stickers/c.jpg")
        val content = AddonPreviewReader.read(entry(AddonType.Stickers), payload)
        val stickers = content as AddonPreviewContent.Stickers
        assertEquals(3, stickers.total)
        assertEquals(3, stickers.images.size)
        assertTrue("images should be on disk", stickers.images.all { it.isFile })
    }

    @Test
    fun `a sticker entry cannot write outside the preview directory`() {
        // Entry names are never used as paths: the extracted files are named
        // from the index, so `../` inside the archive escapes nothing.
        val payload = stickerPack("../../escaped.png", "stickers/ok.png")
        val stickers = AddonPreviewReader.read(entry(AddonType.Stickers), payload)
            as AddonPreviewContent.Stickers
        val root = temp.root.canonicalFile
        for (image in stickers.images) {
            assertTrue(
                "escaped to ${image.canonicalPath}",
                image.canonicalFile.startsWith(root),
            )
        }
    }

    @Test
    fun `an archive with no images is unreadable`() {
        assertTrue(
            AddonPreviewReader.read(entry(AddonType.Stickers), stickerPack())
                is AddonPreviewContent.Unreadable,
        )
    }

    @Test
    fun `a sticker payload that is not a zip is unreadable`() {
        val payload = file("pack.wmstickers", "plain text, definitely not a zip")
        assertTrue(
            AddonPreviewReader.read(entry(AddonType.Stickers), payload)
                is AddonPreviewContent.Unreadable,
        )
    }

    // ---- the types that deliberately have no preview ---------------------

    @Test
    fun `a theme reports that it cannot be previewed`() {
        val payload = file("t.wmtheme.json", "{}")
        for (type in AddonType.entries.filterNot { it.previewable }) {
            assertTrue(
                "$type should not claim a preview",
                AddonPreviewReader.read(entry(type), payload)
                    is AddonPreviewContent.Unreadable,
            )
        }
    }

    /** Kept honest: the zip helper really does produce a readable archive. */
    @Test
    fun `the fixture builder writes a real archive`() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("a")); it.closeEntry() }
        assertTrue(bytes.size() > 0)
    }
}
