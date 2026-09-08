package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A key carrying a floating word drops its corner hint. The flags behind that
 * are read at draw time rather than in composition, and this is what keeps
 * them honest: the wrong key hiding its hint is cosmetic, but flipping flags
 * that did not change would redraw every hinted key on every keystroke, which
 * is the cost the whole arrangement exists to avoid.
 */
class OctopusOccupancyTest {

    @Test
    fun `a key holding a word is flagged`() {
        val occupancy = OctopusOccupancy()
        val l = occupancy.flag('l'.code)
        val p = occupancy.flag('p'.code)
        occupancy.set(setOf('l'.code))
        assertTrue(l.value)
        assertFalse(p.value)
    }

    @Test
    fun `a key asked about after the fact starts out already correct`() {
        // A key composing late must not draw a hint under a word that is
        // already floating over it.
        val occupancy = OctopusOccupancy()
        occupancy.set(setOf('l'.code))
        assertTrue(occupancy.flag('l'.code).value)
        assertFalse(occupancy.flag('p'.code).value)
    }

    @Test
    fun `the same flag is handed back every time`() {
        val occupancy = OctopusOccupancy()
        assertEquals(occupancy.flag('l'.code), occupancy.flag('l'.code))
    }

    @Test
    fun `losing the word clears the flag`() {
        val occupancy = OctopusOccupancy()
        val l = occupancy.flag('l'.code)
        occupancy.set(setOf('l'.code))
        occupancy.set(setOf('p'.code))
        assertFalse(l.value)
    }

    @Test
    fun `an empty board clears everything`() {
        val occupancy = OctopusOccupancy()
        val flags = "qwerty".map { occupancy.flag(it.code) }
        occupancy.set("qwerty".map { it.code }.toSet())
        occupancy.set(emptySet())
        assertTrue(flags.none { it.value })
    }

    @Test
    fun `a key that keeps its word is not written to again`() {
        // The steady state: one key gains a word, one loses it, and the other
        // thirty-eight are left alone.
        val occupancy = OctopusOccupancy()
        val l = occupancy.flag('l'.code)
        val p = occupancy.flag('p'.code)
        val d = occupancy.flag('d'.code)
        occupancy.set(setOf('l'.code, 'p'.code))
        val before = listOf(l.value, p.value, d.value)
        occupancy.set(setOf('l'.code, 'd'.code))
        assertEquals("l keeps its word throughout", before[0], l.value)
        assertFalse("p lost it", p.value)
        assertTrue("d gained it", d.value)
    }
}
