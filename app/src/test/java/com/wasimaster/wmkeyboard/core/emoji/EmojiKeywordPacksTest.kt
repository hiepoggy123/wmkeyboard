package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The on-disk side: importing, listing and removing packs. */
class EmojiKeywordPacksTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun importPack(langId: String, name: String, text: String) =
        EmojiKeywordPacks.import(temp.root, langId, name, text.byteInputStream())

    @Test fun importsAndCountsAPack() {
        assertEquals(2, importPack("hi", "hindi", "🎂\tकेक\n💰\tपैसा\n"))
        assertEquals(1, EmojiKeywordPacks.packs(temp.root, "hi").size)
        assertEquals(listOf("hi"), EmojiKeywordPacks.languages(temp.root))
    }

    @Test fun packsStackWithinOneLanguage() {
        importPack("hi", "one", "🎂\tकेक\n")
        importPack("hi", "two", "💰\tपैसा\n")
        val loaded = EmojiKeywordPacks.load(temp.root, "hi")
        assertEquals(2, loaded.size)
        val merged = EmojiKeywordPack.merge(
            listOf(EmojiEntry("🎂", "food", listOf("cake"))),
            loaded,
        )
        assertTrue("केक" in merged.single().keywords)
    }

    /** A file that parses to nothing is deleted again rather than kept. */
    @Test fun junkIsRejectedAndLeavesNothingBehind() {
        assertEquals(0, importPack("hi", "junk", "%PDF-1.4 nonsense\n"))
        assertTrue(EmojiKeywordPacks.packs(temp.root, "hi").isEmpty())
        assertTrue(EmojiKeywordPacks.languages(temp.root).isEmpty())
    }

    @Test fun sameNameTwiceDoesNotClobber() {
        importPack("hi", "hindi", "🎂\tकेक\n")
        importPack("hi", "hindi", "💰\tपैसा\n")
        assertEquals(2, EmojiKeywordPacks.packs(temp.root, "hi").size)
    }

    /**
     * Names arrive from a document provider or a URL's last path segment, so a
     * pack must not be able to write outside its own language folder.
     */
    @Test fun traversalInAPackNameIsStripped() {
        importPack("hi", "../../evil", "🎂\tकेक\n")
        val written = EmojiKeywordPacks.packs(temp.root, "hi").single()
        assertEquals(
            EmojiKeywordPacks.languageDir(temp.root, "hi").canonicalPath,
            written.parentFile?.canonicalPath,
        )
        assertFalse('/' in written.name)
    }

    /**
     * The cap is enforced on the bytes themselves. A document provider may
     * report an unknown size and a URL may lie about one, so a caller checking
     * the advertised length is not enough.
     */
    @Test fun anOversizedStreamIsRefusedAndNothingIsKept() {
        // Synthesised rather than materialised: the point is the byte count,
        // and holding 8 MiB of String to prove it would be the slow way.
        var left = EmojiKeywordPack.MAX_BYTES + 1
        val endless = object : java.io.InputStream() {
            override fun read(): Int = if (left-- > 0) 'a'.code else -1
        }
        assertEquals(
            EmojiKeywordPacks.TOO_LARGE,
            EmojiKeywordPacks.import(temp.root, "hi", "huge", endless),
        )
        assertTrue(EmojiKeywordPacks.packs(temp.root, "hi").isEmpty())
    }

    @Test fun removeTakesThePackOut() {
        importPack("hi", "hindi", "🎂\tकेक\n")
        val file = EmojiKeywordPacks.packs(temp.root, "hi").single()
        assertTrue(EmojiKeywordPacks.remove(file))
        assertTrue(EmojiKeywordPacks.packs(temp.root, "hi").isEmpty())
        assertTrue(EmojiKeywordPacks.load(temp.root, "hi").isEmpty())
    }

    @Test fun missingFoldersReadAsEmpty() {
        assertTrue(EmojiKeywordPacks.packs(temp.root, "nope").isEmpty())
        assertTrue(EmojiKeywordPacks.load(temp.root, "nope").isEmpty())
        assertTrue(EmojiKeywordPacks.languages(temp.root).isEmpty())
    }
}
