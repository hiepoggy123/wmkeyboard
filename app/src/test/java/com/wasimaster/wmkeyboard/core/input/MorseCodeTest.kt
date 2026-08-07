package com.wasimaster.wmkeyboard.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodeTest {

    @Test
    fun `letters digits and punctuation decode`() {
        assertEquals("s", MorseCode.decode("..."))
        assertEquals("o", MorseCode.decode("---"))
        assertEquals("e", MorseCode.decode("."))
        assertEquals("t", MorseCode.decode("-"))
        assertEquals("q", MorseCode.decode("--.-"))
        assertEquals("5", MorseCode.decode("....."))
        assertEquals("0", MorseCode.decode("-----"))
        assertEquals(".", MorseCode.decode(".-.-.-"))
        assertEquals("@", MorseCode.decode(".--.-."))
    }

    @Test
    fun `unknown sequence decodes to null`() {
        assertNull(MorseCode.decode("........"))
        assertNull(MorseCode.decode(""))
    }

    @Test
    fun `every letter and digit is reachable`() {
        val sequences = listOf(
            ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..",
            ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.",
            "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..",
            "-----", ".----", "..---", "...--", "....-", ".....", "-....",
            "--...", "---..", "----.",
        )
        val decoded = sequences.mapNotNull { MorseCode.decode(it) }.joinToString("")
        assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", decoded)
    }

    @Test
    fun `signals accumulate and take decodes and clears`() {
        val input = MorseInput()
        assertFalse(input.isPending)
        input.signal(dash = false)
        input.signal(dash = false)
        input.signal(dash = false)
        assertTrue(input.isPending)
        assertEquals("···", input.display)
        assertEquals("s", input.take())
        assertFalse(input.isPending)
        assertNull(input.take())
    }

    @Test
    fun `take on a nonsense sequence clears without spelling anything`() {
        val input = MorseInput()
        repeat(8) { input.signal(dash = false) }
        assertNull(input.take())
        assertFalse(input.isPending)
    }

    @Test
    fun `backspace edits the pending sequence`() {
        val input = MorseInput()
        input.signal(dash = true)
        input.signal(dash = false)
        assertTrue(input.backspace())
        assertEquals("−", input.display)
        assertEquals("t", input.take())
        assertFalse(input.backspace())
    }

    @Test
    fun `display maps to typographic glyphs`() {
        val input = MorseInput()
        input.signal(dash = false)
        input.signal(dash = true)
        assertEquals("·−", input.display)
    }

    @Test
    fun `three decoded letters spelling sos trip the watch`() {
        val input = MorseInput()
        assertFalse(input.recordDecoded("s"))
        assertFalse(input.recordDecoded("o"))
        assertTrue(input.recordDecoded("s"))
    }

    @Test
    fun `the watch is a rolling window not a state machine`() {
        val input = MorseInput()
        // "ssos" still ends in an SOS.
        assertFalse(input.recordDecoded("s"))
        assertFalse(input.recordDecoded("s"))
        assertFalse(input.recordDecoded("o"))
        assertTrue(input.recordDecoded("s"))
    }

    @Test
    fun `letters in between break the sequence`() {
        val input = MorseInput()
        input.recordDecoded("s")
        input.recordDecoded("o")
        input.recordDecoded("5")
        assertFalse(input.recordDecoded("s"))
    }

    @Test
    fun `reset clears the watch window`() {
        val input = MorseInput()
        input.recordDecoded("s")
        input.recordDecoded("o")
        input.reset()
        assertFalse(input.recordDecoded("s"))
    }

    @Test
    fun `a long run of letters keeps only the tail`() {
        val input = MorseInput()
        "abcdefghij".forEach { input.recordDecoded(it.toString()) }
        input.recordDecoded("s")
        input.recordDecoded("o")
        assertTrue(input.recordDecoded("s"))
    }
}
