package com.wasimaster.wmkeyboard.core.input.composer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Downloadable-pack catalog, on-disk resolution, and the reload state token. */
class CjkDictPackTest {

    private fun tempFilesDir(): File =
        File(System.getProperty("java.io.tmpdir"), "cjkpack-${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun `catalog maps packs to their language and is unavailable until hosted`() {
        val pinyin = CjkDictCatalog.byId("pinyin")!!
        assertEquals("zh", pinyin.langId)
        assertEquals(
            listOf("pinyin", "stroke", "cangjie"),
            CjkDictCatalog.forLang("zh").map { it.id },
        )
        assertEquals(listOf("ja_kana"), CjkDictCatalog.forLang("ja").map { it.id })
        assertEquals(emptyList<CjkDictPack>(), CjkDictCatalog.forLang("en"))
        // The shipped packs are hosted (url + checksum present).
        assertTrue(pinyin.available)
        // A pack is unavailable if either the URL or the checksum is missing.
        assertFalse(pinyin.copy(url = "").available)
        assertFalse(pinyin.copy(sha256 = "").available)
        // Cangjie is registered but not yet hosted, so its row reads as unavailable
        // rather than offering a download that cannot succeed.
        assertFalse(CjkDictCatalog.byId("cangjie")!!.available)
    }

    @Test
    fun `store resolves a downloaded pack file and reflects it in the token`() {
        val filesDir = tempFilesDir()
        val pinyin = CjkDictCatalog.byId("pinyin")!!

        assertFalse(CjkDictStore.isDownloaded(filesDir, pinyin))
        assertNull(CjkDictStore.downloadedFileFor(filesDir, "pinyin"))
        val before = CjkDictStore.stateToken(filesDir)

        // Simulate a completed download landing in place.
        val file = CjkDictStore.packFile(filesDir, pinyin)
        file.parentFile!!.mkdirs()
        file.writeText("ni\tN1\t100\n")

        assertTrue(CjkDictStore.isDownloaded(filesDir, pinyin))
        assertEquals(file, CjkDictStore.downloadedFileFor(filesDir, "pinyin"))
        assertNotEquals(before, CjkDictStore.stateToken(filesDir))

        filesDir.deleteRecursively()
    }
}
