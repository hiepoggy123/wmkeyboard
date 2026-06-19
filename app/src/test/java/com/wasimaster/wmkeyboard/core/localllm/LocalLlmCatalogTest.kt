package com.wasimaster.wmkeyboard.core.localllm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmCatalogTest {

    @Test
    fun `catalog is sorted by size ascending`() {
        assertEquals(
            LocalLlmCatalog.models.sortedBy { it.sizeBytes },
            LocalLlmCatalog.models,
        )
    }

    @Test
    fun `ids are unique and directory-safe`() {
        val ids = LocalLlmCatalog.models.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
        ids.forEach { id ->
            assertTrue("id '$id' has unsafe chars", id.matches(Regex("[a-z0-9.\\-]+")))
        }
    }

    @Test
    fun `file names match their declared format`() {
        LocalLlmCatalog.models.forEach { model ->
            assertTrue(
                "${model.id}: ${model.fileName} vs ${model.format}",
                model.fileName.endsWith(".${model.format.extension}"),
            )
        }
    }

    @Test
    fun `download url resolves repo main`() {
        val model = LocalLlmCatalog.models.first()
        assertEquals(
            "https://huggingface.co/${model.repo}/resolve/main/${model.fileName}",
            LocalLlmCatalog.downloadUrl(model),
        )
    }

    @Test
    fun `byId finds every model and misses unknowns`() {
        LocalLlmCatalog.models.forEach { assertEquals(it, LocalLlmCatalog.byId(it.id)) }
        assertNull(LocalLlmCatalog.byId("nope"))
        assertNull(LocalLlmCatalog.byId(""))
    }
}
