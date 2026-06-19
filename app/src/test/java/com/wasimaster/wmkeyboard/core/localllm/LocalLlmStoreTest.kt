package com.wasimaster.wmkeyboard.core.localllm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalLlmStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val model get() = LocalLlmCatalog.models.first()

    private fun materialize(file: File, content: String = "x") {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `paths follow models-id-file layout`() {
        val filesDir = tmp.root
        assertEquals(
            File(filesDir, "models/${model.id}/${model.fileName}"),
            LocalLlmStore.modelFile(filesDir, model),
        )
        assertEquals(
            File(filesDir, "models/${model.id}/${model.fileName}.part"),
            LocalLlmStore.partFile(filesDir, model),
        )
    }

    @Test
    fun `isDownloaded only sees the final file`() {
        val filesDir = tmp.root
        assertFalse(LocalLlmStore.isDownloaded(filesDir, model))
        materialize(LocalLlmStore.partFile(filesDir, model))
        assertFalse(LocalLlmStore.isDownloaded(filesDir, model))
        materialize(LocalLlmStore.modelFile(filesDir, model))
        assertTrue(LocalLlmStore.isDownloaded(filesDir, model))
    }

    @Test
    fun `selectedModelFile resolves catalog ids`() {
        val filesDir = tmp.root
        assertNull(LocalLlmStore.selectedModelFile(filesDir, model.id))
        materialize(LocalLlmStore.modelFile(filesDir, model))
        assertEquals(
            LocalLlmStore.modelFile(filesDir, model),
            LocalLlmStore.selectedModelFile(filesDir, model.id),
        )
    }

    @Test
    fun `selectedModelFile resolves custom ids and rejects traversal`() {
        val filesDir = tmp.root
        val custom = File(LocalLlmStore.customDir(filesDir), "mine.litertlm")
        materialize(custom)
        assertEquals(custom, LocalLlmStore.selectedModelFile(filesDir, "custom:mine.litertlm"))
        assertNull(LocalLlmStore.selectedModelFile(filesDir, "custom:../escape.litertlm"))
        assertNull(LocalLlmStore.selectedModelFile(filesDir, "custom:"))
        assertNull(LocalLlmStore.selectedModelFile(filesDir, ""))
        assertNull(LocalLlmStore.selectedModelFile(filesDir, "unknown-id"))
    }

    @Test
    fun `customModels lists only model files`() {
        val filesDir = tmp.root
        materialize(File(LocalLlmStore.customDir(filesDir), "b.litertlm"))
        materialize(File(LocalLlmStore.customDir(filesDir), "a.task"))
        materialize(File(LocalLlmStore.customDir(filesDir), "notes.txt"))
        materialize(File(LocalLlmStore.customDir(filesDir), "half.litertlm.part"))
        assertEquals(
            listOf("a.task", "b.litertlm"),
            LocalLlmStore.customModels(filesDir).map { it.name },
        )
    }

    @Test
    fun `delete removes the whole model directory`() {
        val filesDir = tmp.root
        materialize(LocalLlmStore.modelFile(filesDir, model))
        materialize(LocalLlmStore.partFile(filesDir, model))
        LocalLlmStore.delete(filesDir, model)
        assertFalse(LocalLlmStore.modelDir(filesDir, model).exists())
    }

    @Test
    fun `totalBytesUsed sums finished and partial files`() {
        val filesDir = tmp.root
        materialize(LocalLlmStore.modelFile(filesDir, model), "abc")
        materialize(File(LocalLlmStore.customDir(filesDir), "c.task"), "defgh")
        assertEquals(8L, LocalLlmStore.totalBytesUsed(filesDir))
        assertEquals(0L, LocalLlmStore.totalBytesUsed(tmp.newFolder("empty")))
    }
}
