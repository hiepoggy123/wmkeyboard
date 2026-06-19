package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiThinkingTest {

    @Test
    fun `plain output passes through`() {
        val split = AiThinking.split("Hello world")
        assertEquals("Hello world", split.output)
        assertFalse(split.thinking)
    }

    @Test
    fun `open think block means still reasoning`() {
        val split = AiThinking.split("<think>step 1, step 2")
        assertEquals("", split.output)
        assertTrue(split.thinking)
    }

    @Test
    fun `closed think block is removed from output`() {
        val split = AiThinking.split("<think>reasoning</think>The answer.")
        assertEquals("The answer.", split.output)
        assertFalse(split.thinking)
    }

    @Test
    fun `text before think block is kept`() {
        assertEquals("Before After", AiThinking.stripped("Before <think>x</think>After"))
    }

    @Test
    fun `stripped trims and handles think-only output`() {
        assertEquals("", AiThinking.stripped("<think>never closed"))
        assertEquals("Answer", AiThinking.stripped("<think>r</think>\n Answer \n"))
    }

    @Test
    fun `close without open treats the head as reasoning`() {
        // Qwen3-style: the template already opened the think block.
        val split = AiThinking.split("step 1, step 2</think>The answer.")
        assertEquals("The answer.", split.output)
        assertFalse(split.thinking)
        assertEquals("The answer.", AiThinking.stripped("reasoning</think>\nThe answer."))
    }

    @Test
    fun `implicit thinking hides bare reasoning until the close tag`() {
        val streaming = AiThinking.split("step 1, step 2", implicitThink = true)
        assertEquals("", streaming.output)
        assertTrue(streaming.thinking)
        // A finished response with no markers at all is a normal answer.
        assertEquals("Plain answer", AiThinking.stripped("Plain answer"))
    }
}
