package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "The answer stopped in the middle" is the one failure a model reports without
 * failing: the request succeeds, the text just ends early. Every provider spells
 * the signal differently, so each spelling is pinned here.
 */
class AiClientTruncationTest {

    private fun buffer() = AiClient.StreamBuffer()

    // ---- streamed ----

    @Test
    fun `Anthropic reports it on the closing message_delta`() {
        val buffer = buffer()
        AiClient.applyAnthropicEvent(
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hi"}}""",
            buffer,
        )
        assertFalse(buffer.truncated)
        AiClient.applyAnthropicEvent(
            """{"type":"message_delta","delta":{"stop_reason":"max_tokens"}}""",
            buffer,
        )
        assertTrue(buffer.truncated)
        assertEquals(AiClient.Completion("Hi", truncated = true), buffer.finish())
    }

    @Test
    fun `Anthropic's ordinary stop is not a truncation`() {
        val buffer = buffer()
        AiClient.applyAnthropicEvent(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
            buffer,
        )
        assertFalse(buffer.truncated)
    }

    @Test
    fun `an OpenAI-shaped service reports it as finish_reason length`() {
        val buffer = buffer()
        AiClient.applyOpenAiEvent(
            """{"choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}""",
            buffer,
        )
        assertFalse(buffer.truncated)
        AiClient.applyOpenAiEvent(
            """{"choices":[{"delta":{},"finish_reason":"length"}]}""",
            buffer,
        )
        assertTrue(buffer.truncated)
    }

    @Test
    fun `an OpenAI-shaped service that stops normally is not truncated`() {
        val buffer = buffer()
        AiClient.applyOpenAiEvent(
            """{"choices":[{"delta":{"content":"Hi"},"finish_reason":"stop"}]}""",
            buffer,
        )
        assertFalse(buffer.truncated)
    }

    @Test
    fun `Gemini reports it as MAX_TOKENS on the candidate`() {
        val buffer = buffer()
        AiClient.applyGeminiEvent(
            """{"candidates":[{"content":{"parts":[{"text":"Hi"}]},"finishReason":"MAX_TOKENS"}]}""",
            buffer,
        )
        assertTrue(buffer.truncated)
        // The text of the cut-off chunk is still kept: a partial answer is
        // better than none, it just has to be labelled.
        assertEquals("Hi", buffer.partial)
    }

    @Test
    fun `Gemini's ordinary stop is not a truncation`() {
        val buffer = buffer()
        AiClient.applyGeminiEvent(
            """{"candidates":[{"content":{"parts":[{"text":"Hi"}]},"finishReason":"STOP"}]}""",
            buffer,
        )
        assertFalse(buffer.truncated)
    }

    @Test
    fun `Ollama reports it as done_reason length`() {
        val buffer = buffer()
        AiClient.applyOllamaEvent(
            """{"message":{"content":"Hi"},"done":false}""",
            buffer,
        )
        assertFalse(buffer.truncated)
        AiClient.applyOllamaEvent(
            """{"message":{"content":""},"done":true,"done_reason":"length"}""",
            buffer,
        )
        assertTrue(buffer.truncated)
    }

    @Test
    fun `Ollama's ordinary stop is not a truncation`() {
        val buffer = buffer()
        AiClient.applyOllamaEvent(
            """{"message":{"content":"Hi"},"done":true,"done_reason":"stop"}""",
            buffer,
        )
        assertFalse(buffer.truncated)
    }

    // ---- the fallback path, where a proxy ignored `stream: true` ----

    @Test
    fun `a whole response body carries the same signal`() {
        assertTrue(AiClient.anthropicTruncated("""{"stop_reason":"max_tokens"}"""))
        assertFalse(AiClient.anthropicTruncated("""{"stop_reason":"end_turn"}"""))

        assertTrue(AiClient.openAiTruncated("""{"choices":[{"finish_reason":"length"}]}"""))
        assertFalse(AiClient.openAiTruncated("""{"choices":[{"finish_reason":"stop"}]}"""))

        assertTrue(AiClient.geminiTruncated("""{"candidates":[{"finishReason":"MAX_TOKENS"}]}"""))
        assertTrue(
            AiClient.geminiTruncated("""[{"candidates":[{"finishReason":"MAX_TOKENS"}]}]"""),
        )
        assertFalse(AiClient.geminiTruncated("""{"candidates":[{"finishReason":"STOP"}]}"""))

        assertTrue(AiClient.ollamaTruncated("""{"done":true,"done_reason":"length"}"""))
        assertFalse(AiClient.ollamaTruncated("""{"done":true,"done_reason":"stop"}"""))
    }

    @Test
    fun `a body that cannot be parsed never claims a truncation`() {
        // Better to say nothing than to warn about a cut that did not happen.
        for (body in listOf("", "not json", "[]", "{}")) {
            assertFalse(body, AiClient.anthropicTruncated(body))
            assertFalse(body, AiClient.openAiTruncated(body))
            assertFalse(body, AiClient.geminiTruncated(body))
            assertFalse(body, AiClient.ollamaTruncated(body))
        }
    }
}
