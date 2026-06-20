package com.wasimaster.wmkeyboard.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class WordDeleteTest {

    private fun len(text: String) = WordDelete.lengthBefore(text)

    @Test
    fun `word directly before the cursor`() {
        assertEquals("world".length, len("hello world"))
    }

    @Test
    fun `trailing space goes with the word before it`() {
        assertEquals("world ".length, len("hello world "))
        assertEquals("world   ".length, len("hello world   "))
        // And the step after that takes the rest.
        assertEquals("hello".length, len("hello"))
    }

    @Test
    fun `punctuation is its own step`() {
        assertEquals("...".length, len("wait..."))
        assertEquals("wait".length, len("wait"))
        // Contractions split rather than swallowing the line.
        assertEquals("t".length, len("don't"))
    }

    @Test
    fun `newlines count as whitespace`() {
        assertEquals("line\n".length, len("first line\n"))
    }

    @Test
    fun `nothing to delete`() {
        assertEquals(0, len(""))
        // Leading whitespace only: take it and stop.
        assertEquals(3, len("   "))
    }
}
