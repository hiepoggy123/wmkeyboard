package com.wasimaster.wmkeyboard.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrailleChordTest {

    private fun mask(vararg dots: Int) = dots.fold(0) { acc, d -> acc or (1 shl (d - 1)) }

    @Test
    fun `single dot tap commits on release`() {
        val chord = BrailleChord()
        chord.down(1)
        assertEquals(mask(1), chord.up())
    }

    @Test
    fun `simultaneous chord commits once when the last finger lifts`() {
        val chord = BrailleChord()
        chord.down(1)
        chord.down(4)
        chord.down(5)
        assertNull(chord.up())
        assertNull(chord.up())
        assertEquals(mask(1, 4, 5), chord.up())
    }

    @Test
    fun `rolled chord gathers dots pressed while others are held`() {
        val chord = BrailleChord()
        chord.down(1)
        chord.down(2)
        assertNull(chord.up()) // lift dot 1, dot 2 still held
        chord.down(3)
        assertNull(chord.up())
        assertEquals(mask(1, 2, 3), chord.up())
    }

    @Test
    fun `reset drops the half-typed chord`() {
        val chord = BrailleChord()
        chord.down(1)
        chord.reset()
        assertNull(chord.up())
        // And the counter did not go negative: a fresh chord still works.
        chord.down(2)
        assertEquals(mask(2), chord.up())
    }

    @Test
    fun `out of range dots are ignored`() {
        val chord = BrailleChord()
        chord.down(0)
        chord.down(7)
        assertNull(chord.up())
    }

    @Test
    fun `letters decode from standard cells`() {
        val g1 = BrailleGrade1()
        assertEquals("a", g1.decode(mask(1)))
        assertEquals("d", g1.decode(mask(1, 4, 5)))
        assertEquals("w", g1.decode(mask(2, 4, 5, 6)))
        assertEquals("z", g1.decode(mask(1, 3, 5, 6)))
    }

    @Test
    fun `capital indicator uppercases exactly one letter`() {
        val g1 = BrailleGrade1()
        assertEquals("", g1.decode(BrailleGrade1.CAPITAL_INDICATOR))
        assertEquals("H", g1.decode(mask(1, 2, 5)))
        assertEquals("i", g1.decode(mask(2, 4)))
    }

    @Test
    fun `number mode maps a-j cells to digits until a non-digit cell`() {
        val g1 = BrailleGrade1()
        assertEquals("", g1.decode(BrailleGrade1.NUMBER_INDICATOR))
        assertEquals("1", g1.decode(mask(1)))
        assertEquals("0", g1.decode(mask(2, 4, 5)))
        // k is not a digit cell: number mode ends and the letter decodes.
        assertEquals("k", g1.decode(mask(1, 3)))
        // And stays ended.
        assertEquals("a", g1.decode(mask(1)))
    }

    @Test
    fun `punctuation decodes`() {
        val g1 = BrailleGrade1()
        assertEquals(",", g1.decode(mask(2)))
        assertEquals(".", g1.decode(mask(2, 5, 6)))
        assertEquals("?", g1.decode(mask(2, 3, 6)))
    }

    @Test
    fun `unknown cell falls back to the unicode braille pattern`() {
        val g1 = BrailleGrade1()
        // Dots 1+2+4+5+6 is not a Grade-1 cell in our tables ("th" contraction
        // in Grade 2): it commits the raw pattern.
        val cell = mask(1, 2, 4, 5, 6)
        assertEquals((0x2800 + cell).toChar().toString(), g1.decode(cell))
    }

    @Test
    fun `reset clears the indicator latches`() {
        val g1 = BrailleGrade1()
        g1.decode(BrailleGrade1.CAPITAL_INDICATOR)
        g1.decode(BrailleGrade1.NUMBER_INDICATOR)
        g1.reset()
        assertEquals("a", g1.decode(mask(1)))
    }
}
