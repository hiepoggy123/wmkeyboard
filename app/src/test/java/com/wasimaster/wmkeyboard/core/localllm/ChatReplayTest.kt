package com.wasimaster.wmkeyboard.core.localllm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplayTest {

    private fun turn(fromUser: Boolean, text: String) = ChatReplay.Turn(fromUser, text)

    @Test
    fun `no history means the plain user message`() {
        assertEquals("hello", ChatReplay.replayMessage(emptyList(), "hello"))
    }

    @Test
    fun `the transcript replays oldest first and ends with the new message`() {
        val message = ChatReplay.replayMessage(
            listOf(
                turn(fromUser = true, "What is Everest?"),
                turn(fromUser = false, "The tallest mountain."),
            ),
            "How tall?",
        )
        val userAt = message.indexOf("User: What is Everest?")
        val assistantAt = message.indexOf("Assistant: The tallest mountain.")
        assertTrue(userAt in 0 until assistantAt)
        assertTrue(message.endsWith("How tall?"))
    }

    @Test
    fun `oldest turns fall off past the budget`() {
        val old = turn(fromUser = true, "ancient ".repeat(2_000))
        val recent = turn(fromUser = false, "recent answer")
        val message = ChatReplay.replayMessage(listOf(old, recent), "next")
        assertFalse("ancient" in message)
        assertTrue("recent answer" in message)
    }

    @Test
    fun `blank turns are skipped`() {
        val message = ChatReplay.replayMessage(
            listOf(turn(fromUser = false, "   "), turn(fromUser = true, "kept")),
            "next",
        )
        assertTrue("User: kept" in message)
        assertFalse("Assistant:   " in message)
    }
}
