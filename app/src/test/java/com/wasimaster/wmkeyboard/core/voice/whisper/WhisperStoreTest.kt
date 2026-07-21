package com.wasimaster.wmkeyboard.core.voice.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WhisperStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val model get() = WhisperCatalog.byId("base-multi")!!

    private fun materialize(file: File, content: String = "x") {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `paths follow whisper-id-file layout`() {
        val d = tmp.root
        assertEquals(File(d, "whisper/${model.id}/${model.modelFile}"), WhisperStore.modelFile(d, model))
        assertEquals(File(d, "whisper/${model.id}/${model.vocabFile}"), WhisperStore.vocabFile(d, model))
    }

    @Test
    fun `downloaded needs both model and vocab files`() {
        val d = tmp.root
        assertFalse(WhisperStore.isDownloaded(d, model))
        materialize(WhisperStore.modelFile(d, model))
        // Model present, vocab missing → not yet usable, but a partial exists.
        assertFalse(WhisperStore.isDownloaded(d, model))
        assertTrue(WhisperStore.hasPartial(d, model))
        materialize(WhisperStore.vocabFile(d, model))
        assertTrue(WhisperStore.isDownloaded(d, model))
    }

    @Test
    fun `soleDownloadedId returns the only complete model`() {
        val d = tmp.root
        assertNull(WhisperStore.soleDownloadedId(d))
        materialize(WhisperStore.modelFile(d, model))
        materialize(WhisperStore.vocabFile(d, model))
        assertEquals(model.id, WhisperStore.soleDownloadedId(d))
    }

    @Test
    fun `effectiveModel falls back to the sole downloaded model when unselected`() {
        val d = tmp.root
        materialize(WhisperStore.modelFile(d, model))
        materialize(WhisperStore.vocabFile(d, model))
        assertEquals(model.id, WhisperStore.effectiveModel(d, "")?.id)
        assertEquals(model.id, WhisperStore.effectiveModel(d, model.id)?.id)
    }

    @Test
    fun `orphan dir is detected and cleanable`() {
        val d = tmp.root
        materialize(File(WhisperStore.rootDir(d), "ghost-model/some.tflite"))
        assertEquals(1, WhisperStore.orphanDirs(d).size)
        WhisperStore.deleteOrphans(d)
        assertTrue(WhisperStore.orphanDirs(d).isEmpty())
    }
}
