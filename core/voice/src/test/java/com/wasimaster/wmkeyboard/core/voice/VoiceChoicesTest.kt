package com.wasimaster.wmkeyboard.core.voice

import com.wasimaster.wmkeyboard.core.voice.executorch.EtCatalog
import com.wasimaster.wmkeyboard.core.voice.executorch.EtEngine
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperCatalog
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperModel
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperSize
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceChoicesTest {

    private val multilingual = WhisperCatalog.models.firstOrNull { it.langCodes.isEmpty() }
        ?: WhisperModel(
            id = "cpp-small",
            displayName = "Whisper Small",
            size = WhisperSize.SMALL,
            repo = "ggerganov/whisper.cpp",
            modelFile = "ggml-small.bin",
            vocabFile = "",
            modelBytes = 0L,
            vocabBytes = 0L,
            langCodes = emptySet(),
            tier = WhisperTier.STANDARD,
            descriptionRes = 0,
        )
    private val englishOnly = WhisperCatalog.models.firstOrNull { it.fixedLang == "en" }
        ?: WhisperModel(
            id = "base-en",
            displayName = "Whisper Base (English)",
            size = WhisperSize.BASE,
            repo = "DocWolle/whisper_tflite_models",
            modelFile = "whisper-base.en.tflite",
            vocabFile = "",
            modelBytes = 0L,
            vocabBytes = 0L,
            langCodes = setOf("en"),
            tier = WhisperTier.STANDARD,
            descriptionRes = 0,
        )
    private val parakeet = EtCatalog.forEngine(EtEngine.PARAKEET).first()
    private val moonshine = EtCatalog.forEngine(EtEngine.MOONSHINE).first()

    private fun choices(languageId: String, whistle: Boolean = true, server: Boolean = true) =
        VoiceChoices.forLanguage(
            languageId = languageId,
            whistleReady = whistle,
            whisperModels = listOf(multilingual, englishOnly),
            etModels = listOf(parakeet, moonshine),
            serverConfigured = server,
        )

    @Test fun englishGetsEveryEngineInOrder() {
        val engines = choices("en").map { it.engine }.distinct()
        assertEquals(listOf("system", "whistle", "whisper", "parakeet", "moonshine", "server"), engines)
        assertTrue(choices("en").any { it.modelId == englishOnly.id })
    }

    @Test fun onlyWhatServesTheLanguageIsListed() {
        val bangla = choices("bn")
        assertEquals(listOf("system", "whisper:${multilingual.id}", "server"), bangla.map { it.key })
        assertFalse(bangla.any { it.modelId == englishOnly.id })
    }

    @Test fun noServerAddressNoServer() {
        assertFalse(choices("de", server = false).any { it.engine == "server" })
        assertFalse(choices("de", whistle = false).any { it.engine == "whistle" })
        assertTrue(choices("de").any { it.engine == "parakeet" })
    }
}
