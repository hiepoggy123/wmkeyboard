package com.wasimaster.wmkeyboard.core.voice.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperCatalogTest {

    @Test
    fun `ids are unique`() {
        val ids = WhisperCatalog.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every model has positive sizes and a vocab companion`() {
        for (m in WhisperCatalog.models) {
            assertTrue("${m.id} model bytes", m.modelBytes > 0)
            assertTrue("${m.id} vocab bytes", m.vocabBytes > 0)
            assertEquals(m.modelBytes + m.vocabBytes, m.sizeBytes)
            assertTrue("${m.id} model file", m.modelFile.endsWith(".tflite"))
            assertTrue("${m.id} vocab file", m.vocabFile.endsWith(".bin"))
        }
    }

    @Test
    fun `download url is the HF resolve path`() {
        val m = WhisperCatalog.byId("base-multi")!!
        assertEquals(
            "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/${m.modelFile}",
            WhisperCatalog.downloadUrl(m.repo, m.modelFile),
        )
    }

    @Test
    fun `single-language models carry a fixed language, multilingual ones do not`() {
        assertTrue(WhisperCatalog.byId("base-multi")!!.multilingual)
        assertEquals(null, WhisperCatalog.byId("base-multi")!!.fixedLang)
        assertEquals("de", WhisperCatalog.byId("base-de")!!.fixedLang)
        assertEquals("en", WhisperCatalog.byId("tiny-en")!!.fixedLang)
    }
}
