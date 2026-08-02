package com.wasimaster.wmkeyboard.core.tools

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streaming side of [AiClient]: one test per provider's event shape, plus
 * the cross-provider rules — reasoning is re-wrapped as `<think>` so
 * [AiThinking] can treat cloud and on-device output identically, stream noise
 * never aborts a response, and a mid-stream error event is raised rather than
 * silently truncating the answer.
 */
class AiClientStreamTest {

    private fun buffer() = AiClient.StreamBuffer()

    // ---- SSE framing ----

    @Test
    fun `only data lines carry a payload`() {
        assertEquals("""{"a":1}""", AiClient.sseData("""data: {"a":1}"""))
        // No space after the colon is equally valid SSE.
        assertEquals("""{"a":1}""", AiClient.sseData("""data:{"a":1}"""))
        assertNull(AiClient.sseData("event: content_block_delta"))
        assertNull(AiClient.sseData(""))
        assertNull(AiClient.sseData(": keep-alive comment"))
        // OpenAI's end sentinel is not JSON and must not be parsed as any.
        assertNull(AiClient.sseData("data: [DONE]"))
    }

    // ---- Anthropic ----

    @Test
    fun `anthropic text deltas concatenate and lifecycle events are ignored`() {
        val buffer = buffer()
        val stream = listOf(
            """event: message_start""",
            """data: {"type":"message_start","message":{"id":"msg_1","content":[]}}""",
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """data: {"type":"ping"}""",
            """data: {"type":"content_block_delta","index":0,""" +
                """"delta":{"type":"text_delta","text":"Hello"}}""",
            """data: {"type":"content_block_delta","index":0,""" +
                """"delta":{"type":"text_delta","text":", world"}}""",
            """data: {"type":"content_block_stop","index":0}""",
            """data: {"type":"message_stop"}""",
        )
        for (line in stream) {
            AiClient.sseData(line)?.let { AiClient.applyAnthropicEvent(it, buffer) }
        }
        assertEquals("Hello, world", buffer.finish().text)
    }

    @Test
    fun `anthropic thinking deltas are wrapped so AiThinking can strip them`() {
        val buffer = buffer()
        AiClient.applyAnthropicEvent(
            """{"type":"content_block_delta","index":0,""" +
                """"delta":{"type":"thinking_delta","thinking":"Let me count."}}""",
            buffer,
        )
        // Mid-reasoning the block is still open, which is exactly how
        // AiThinking reports "still thinking, nothing to show yet".
        val split = AiThinking.split(buffer.partial)
        assertTrue(split.thinking)
        assertEquals("", split.output)

        AiClient.applyAnthropicEvent(
            """{"type":"content_block_delta","index":0,""" +
                """"delta":{"type":"text_delta","text":"Four."}}""",
            buffer,
        )
        assertEquals("<think>Let me count.</think>Four.", buffer.finish().text)
        assertEquals("Four.", AiThinking.stripped(buffer.finish().text))
    }

    @Test
    fun `an all-reasoning anthropic response closes its think block`() {
        val buffer = buffer()
        AiClient.applyAnthropicEvent(
            """{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"…"}}""",
            buffer,
        )
        // Unterminated on the wire; closed on finish so the caller can tell
        // "spent the whole budget reasoning" from "returned nothing at all".
        assertEquals("<think>…</think>", buffer.finish().text)
        assertEquals("", AiThinking.stripped(buffer.finish().text))
    }

    @Test
    fun `an anthropic error event is raised, not swallowed`() {
        val error = assertThrows(IOException::class.java) {
            AiClient.applyAnthropicEvent(
                """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""",
                buffer(),
            )
        }
        assertEquals("Overloaded", error.message)
    }

    // ---- OpenAI-compatible ----

    @Test
    fun `openai content deltas concatenate`() {
        val buffer = buffer()
        AiClient.applyOpenAiEvent("""{"choices":[{"delta":{"role":"assistant"}}]}""", buffer)
        AiClient.applyOpenAiEvent("""{"choices":[{"delta":{"content":"Hi"}}]}""", buffer)
        AiClient.applyOpenAiEvent("""{"choices":[{"delta":{"content":" there"}}]}""", buffer)
        // A finish chunk carries a null content, which must not append "null".
        AiClient.applyOpenAiEvent(
            """{"choices":[{"delta":{"content":null},"finish_reason":"stop"}]}""",
            buffer,
        )
        assertEquals("Hi there", buffer.finish().text)
    }

    @Test
    fun `openai reasoning arrives under either field name`() {
        val viaContent = buffer()
        AiClient.applyOpenAiEvent(
            """{"choices":[{"delta":{"reasoning_content":"hmm"}}]}""",
            viaContent,
        )
        AiClient.applyOpenAiEvent("""{"choices":[{"delta":{"content":"42"}}]}""", viaContent)
        assertEquals("<think>hmm</think>42", viaContent.finish().text)

        val viaReasoning = buffer()
        AiClient.applyOpenAiEvent("""{"choices":[{"delta":{"reasoning":"hmm"}}]}""", viaReasoning)
        AiClient.applyOpenAiEvent("""{"choices":[{"delta":{"content":"42"}}]}""", viaReasoning)
        assertEquals("<think>hmm</think>42", viaReasoning.finish().text)
    }

    @Test
    fun `an openai error object is raised`() {
        val error = assertThrows(IOException::class.java) {
            AiClient.applyOpenAiEvent(
                """{"error":{"message":"Model not found","type":"invalid_request_error"}}""",
                buffer(),
            )
        }
        assertEquals("Model not found", error.message)
    }

    // ---- Gemini ----

    @Test
    fun `gemini parts concatenate and thought parts become reasoning`() {
        val buffer = buffer()
        AiClient.applyGeminiEvent(
            """{"candidates":[{"content":{"parts":[{"text":"weighing it","thought":true}]}}]}""",
            buffer,
        )
        AiClient.applyGeminiEvent(
            """{"candidates":[{"content":{"parts":[{"text":"Yes"}]}}]}""",
            buffer,
        )
        AiClient.applyGeminiEvent(
            """{"candidates":[{"content":{"parts":[{"text":", really"}]}}]}""",
            buffer,
        )
        assertEquals("<think>weighing it</think>Yes, really", buffer.finish().text)
    }

    @Test
    fun `a gemini chunk with no candidates is ignored`() {
        val buffer = buffer()
        AiClient.applyGeminiEvent("""{"usageMetadata":{"totalTokenCount":7}}""", buffer)
        assertTrue(buffer.isEmpty)
    }

    @Test
    fun `a non-sse gemini body falls back to concatenating the chunk array`() {
        // What a proxy that drops `alt=sse` returns: the chunks as one array.
        val body = """
            [
              {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]},
              {"candidates":[{"content":{"parts":[{"text":", world"}]}}]}
            ]
        """.trimIndent()
        assertEquals("Hello, world", AiClient.parseGeminiChunks(body))
    }

    @Test
    fun `the gemini fallback still reads a plain generateContent body`() {
        assertEquals(
            "Hi",
            AiClient.parseGeminiChunks("""{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}"""),
        )
    }

    // ---- Ollama ----

    @Test
    fun `ollama ndjson lines concatenate and thinking is wrapped`() {
        val buffer = buffer()
        val lines = listOf(
            """{"message":{"role":"assistant","thinking":"pondering"},"done":false}""",
            """{"message":{"role":"assistant","content":"Yes"},"done":false}""",
            """{"message":{"role":"assistant","content":"."},"done":false}""",
            """{"message":{"role":"assistant","content":""},"done":true}""",
        )
        for (line in lines) AiClient.applyOllamaEvent(line, buffer)
        assertEquals("<think>pondering</think>Yes.", buffer.finish().text)
    }

    @Test
    fun `an ollama error line is raised`() {
        val error = assertThrows(IOException::class.java) {
            AiClient.applyOllamaEvent("""{"error":"model 'nope' not found"}""", buffer())
        }
        assertEquals("model 'nope' not found", error.message)
    }

    // ---- stream noise ----

    @Test
    fun `unparseable lines never abort a response`() {
        val buffer = buffer()
        // A half-flushed line, a bare array, and a plain string are all things
        // a stream can hand over; none is a reason to fail the request.
        for (junk in listOf("""{"choices":[{"delta":{"cont""", """[1,2]""", """"text"""", "")) {
            AiClient.applyOpenAiEvent(junk, buffer)
            AiClient.applyAnthropicEvent(junk, buffer)
            AiClient.applyGeminiEvent(junk, buffer)
            AiClient.applyOllamaEvent(junk, buffer)
        }
        assertTrue(buffer.isEmpty)
    }

    @Test
    fun `partial exposes the stream so far without closing the think block`() {
        val buffer = buffer()
        buffer.reasoning("why")
        assertEquals("<think>why", buffer.partial)
        buffer.answer("because")
        assertEquals("<think>why</think>because", buffer.partial)
        // finish() on already-closed text changes nothing.
        assertEquals(buffer.partial, buffer.finish().text)
    }
}
