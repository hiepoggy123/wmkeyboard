package com.wasimaster.wmkeyboard.core.voice.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `every language code in the catalog is a language whisper knows`() {
        for (m in WhisperCatalog.models) {
            for (code in m.langCodes) {
                assertNotNull("${m.id} covers unknown code $code", WhisperLanguages.tokenFor(code))
            }
        }
    }

    @Test
    fun `english graphs use the english vocab and everything else the multilingual one`() {
        for (m in WhisperCatalog.models) {
            val expected =
                if (m.fixedLang == "en") "filters_vocab_en.bin" else "filters_vocab_multilingual.bin"
            assertEquals("${m.id} vocab", expected, m.vocabFile)
        }
    }

    @Test
    fun `only the grouped graphs take a language token`() {
        val world = WhisperCatalog.byId("base-world")!!
        assertTrue(world.selectableLang)
        assertEquals(40, world.langCodes.size)
        assertEquals(50261, world.langTokenFor("de"))
        // Bangla is not in the TOP_WORLD subset, so nothing is forced.
        assertNull(world.langTokenFor("bn"))
        // Our Norwegian id is "nb" while Whisper's token is "no".
        assertEquals(WhisperLanguages.tokenFor("no"), world.langTokenFor("nb"))

        assertNull(WhisperCatalog.byId("base-multi")!!.langTokenFor("de"))
        assertNull(WhisperCatalog.byId("base-de")!!.langTokenFor("de"))
    }

    @Test
    fun `european group is a subset of the world group`() {
        val eu = WhisperCatalog.byId("base-eu")!!.langCodes
        val world = WhisperCatalog.byId("base-world")!!.langCodes
        assertEquals(26, eu.size)
        assertTrue(world.containsAll(eu))
    }

    @Test
    fun `coverage counts only what a model can transcribe`() {
        val codes = setOf("en", "de", "bn")
        assertEquals(3, WhisperCatalog.byId("base-multi")!!.coverageOf(codes))
        assertEquals(2, WhisperCatalog.byId("base-world")!!.coverageOf(codes))
        assertEquals(1, WhisperCatalog.byId("base-de")!!.coverageOf(codes))
    }

    @Test
    fun `recommendations fit the enabled languages`() {
        // A language with a dedicated graph gets it, and the all-languages
        // fallback is always offered.
        val german = WhisperCatalog.recommendedFor(setOf("de"))
        assertTrue(german.any { it.fixedLang == "de" })
        assertTrue(german.any { it.id == "base-multi" })

        // Two languages both inside TOP_WORLD: the grouped graph leads.
        assertEquals("base-world", WhisperCatalog.recommendedFor(setOf("de", "fr")).first().id)

        // A language no grouped graph covers still gets the multilingual one.
        assertTrue(WhisperCatalog.recommendedFor(setOf("bn")).any { it.id == "base-multi" })

        // No enabled languages at all still yields a sane default.
        assertEquals(listOf("base-multi"), WhisperCatalog.recommendedFor(emptySet()).map { it.id })
    }

    @Test
    fun `recommendations never exceed the requested limit`() {
        val many = setOf("en", "de", "fr", "es", "it", "pt", "ru", "zh")
        assertTrue(WhisperCatalog.recommendedFor(many).size <= 4)
        assertTrue(WhisperCatalog.recommendedFor(many, limit = 2).size <= 2)
    }
}
