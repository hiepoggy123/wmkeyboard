package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Where downloaded emoji dictionaries live, and how they are read back. */
class EmojiDictStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun write(langId: String, text: String) {
        val file = EmojiDictStore.packFile(temp.root, langId)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    @Test fun aWrittenPackIsFoundAndLoaded() {
        write("hi", "🎂\tकेक\tजन्मदिन का केक\n")
        assertTrue(EmojiDictStore.isDownloaded(temp.root, "hi"))
        assertEquals(listOf("hi"), EmojiDictStore.downloadedLanguageIds(temp.root))
        val pack = EmojiDictStore.load(temp.root, "hi")!!
        assertEquals(listOf("केक"), pack.keywords["🎂"])
    }

    /** Presence of the final file means the whole pipeline finished. */
    @Test fun aHalfWrittenPackDoesNotCount() {
        val part = EmojiDictStore.partFile(temp.root, "hi")
        part.parentFile?.mkdirs()
        part.writeText("🎂\tकेक\n")
        assertFalse(EmojiDictStore.isDownloaded(temp.root, "hi"))
        assertEquals(emptyList<String>(), EmojiDictStore.downloadedLanguageIds(temp.root))
        assertNull(EmojiDictStore.load(temp.root, "hi"))
    }

    @Test fun deleteTakesTheLanguageOut() {
        write("hi", "🎂\tकेक\n")
        EmojiDictStore.delete(temp.root, "hi")
        assertFalse(EmojiDictStore.isDownloaded(temp.root, "hi"))
        assertTrue(EmojiDictStore.loadAll(temp.root).isEmpty())
        // The emptied folder must not read as a downloaded language.
        assertEquals(emptyList<String>(), EmojiDictStore.downloadedLanguageIds(temp.root))
    }

    /** Delete has to stick, or the automatic pass fetches it straight back. */
    @Test fun deleteIsRememberedUntilAskedForAgain() {
        assertFalse(EmojiDictStore.isDeclined(temp.root, "hi"))
        write("hi", "🎂\tकेक\n")
        EmojiDictStore.delete(temp.root, "hi")
        assertTrue(EmojiDictStore.isDeclined(temp.root, "hi"))
        EmojiDictStore.clearDeclined(temp.root, "hi")
        assertFalse(EmojiDictStore.isDeclined(temp.root, "hi"))
    }

    @Test fun decliningOneLanguageLeavesAnotherAlone() {
        write("hi", "🎂\tकेक\n")
        write("ta", "🎂\tகேக்\n")
        EmojiDictStore.delete(temp.root, "hi")
        assertTrue(EmojiDictStore.isDeclined(temp.root, "hi"))
        assertFalse(EmojiDictStore.isDeclined(temp.root, "ta"))
        assertEquals(listOf("ta"), EmojiDictStore.downloadedLanguageIds(temp.root))
    }

    @Test fun anUnreadablePackIsSkippedRatherThanThrown() {
        write("hi", "%PDF-1.4 nonsense\n")
        assertNull(EmojiDictStore.load(temp.root, "hi"))
        assertTrue(EmojiDictStore.loadAll(temp.root).isEmpty())
    }

    @Test fun loadAllReturnsEveryLanguage() {
        write("hi", "🎂\tकेक\n")
        write("ta", "🎂\tகேக்\n")
        assertEquals(listOf("hi", "ta"), EmojiDictStore.downloadedLanguageIds(temp.root))
        assertEquals(2, EmojiDictStore.loadAll(temp.root).size)
    }

    @Test fun missingFoldersReadAsEmpty() {
        assertFalse(EmojiDictStore.isDownloaded(temp.root, "hi"))
        assertEquals(emptyList<String>(), EmojiDictStore.downloadedLanguageIds(temp.root))
        assertTrue(EmojiDictStore.loadAll(temp.root).isEmpty())
    }

    /** Downloads and imports are separate trees; neither may shadow the other. */
    @Test fun downloadsDoNotLiveWhereImportsDo() {
        write("hi", "🎂\tकेक\n")
        EmojiKeywordPacks.import(temp.root, "hi", "mine", "💰\tपैसा\n".byteInputStream())
        assertEquals(1, EmojiDictStore.loadAll(temp.root).size)
        assertEquals(1, EmojiKeywordPacks.packs(temp.root, "hi").size)
        assertNull(EmojiDictStore.load(temp.root, "hi")!!.keywords["💰"])
    }
}
