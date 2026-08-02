package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.AiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiClientMaxTokensTest {

    private fun openAi(model: String, max: Int = 2048) = AiSettings(
        provider = AiProvider.OPENAI,
        openAiKey = "k",
        openAiModel = model,
        maxTokens = max,
    )

    @Test
    fun `plain models get the user's setting unchanged`() {
        assertEquals(2048, AiClient.effectiveMaxTokens(openAi("gpt-4o-mini")))
        assertEquals(2048, AiClient.effectiveMaxTokens(openAi("gemini-3.5-flash")))
        assertEquals(2048, AiClient.effectiveMaxTokens(openAi("llama3.2")))
    }

    @Test
    fun `reasoning models get headroom for the think block`() {
        assertEquals(8192, AiClient.effectiveMaxTokens(openAi("o3-mini")))
        assertEquals(8192, AiClient.effectiveMaxTokens(openAi("gpt-5.6-luna")))
        assertEquals(8192, AiClient.effectiveMaxTokens(openAi("deepseek-r1")))
        assertEquals(8192, AiClient.effectiveMaxTokens(openAi("qwen3:8b")))
    }

    @Test
    fun `headroom stops at the ceiling`() {
        assertEquals(131_072, AiClient.effectiveMaxTokens(openAi("o3-mini", max = 65_536)))
    }

    @Test
    fun `the provider maximum sends no ceiling at all`() {
        // This is the setting that stops a long "Improve" coming back cut in
        // half: no number in the request, so the service applies its own.
        assertNull(
            AiClient.effectiveMaxTokens(openAi("gpt-4o-mini", max = AiClient.PROVIDER_MAXIMUM)),
        )
        // Even for a reasoning model, where there is no number to multiply.
        assertNull(AiClient.effectiveMaxTokens(openAi("o3-mini", max = AiClient.PROVIDER_MAXIMUM)))
    }

    @Test
    fun `the blank-model default is what the hint advertises`() {
        // The settings screen renders DefaultModels directly, so a drift here
        // would show the user a model the client never actually requests.
        assertEquals(
            AiClient.DefaultModels.OPENAI,
            AiClient.config(openAi("")).model,
        )
    }
}
