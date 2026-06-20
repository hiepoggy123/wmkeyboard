package com.wasimaster.wmkeyboard.core.localllm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmCatalogTest {

    // The catalog order is a quality ranking, not a size ranking — the
    // settings list and the panel picker both render it as-is, so the models
    // worth using have to come first.
    @Test
    fun `recommended models lead the catalog`() {
        val tiers = LocalLlmCatalog.models.map { it.tier }
        assertEquals(
            listOf(ModelTier.RECOMMENDED, ModelTier.RECOMMENDED),
            tiers.take(2),
        )
        assertTrue(
            "a recommended model is buried below other tiers",
            tiers.indexOfLast { it == ModelTier.RECOMMENDED } == 1,
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
