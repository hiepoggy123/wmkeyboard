package com.wasimaster.wmkeyboard.core.layout

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two conversions that change what a key means rather than where it sits:
 * the spelling of its letters, and the unit its width is written in.
 *
 * **Every character here is written as a code point.** A composed letter and its
 * decomposed pair render identically, so an assertion written in glyphs would
 * hold whichever spelling the file happened to be saved with — which is the one
 * thing these tests exist to tell apart. [cp] is the only way a character enters
 * this file.
 */
class ForeignNormalizationTest {

    /** A string from its code points, so no glyph has to be trusted. */
    private fun cp(vararg points: Int): String = String(points, 0, points.size)

    // ---- spelling ----

    @Test
    fun `a decomposed nukta letter is composed`() {
        // Base plus U+09BC is the spelling foreign files carry. The single code
        // point is the one the cluster composer and the backspace rules name.
        assertEquals(cp(0x09DF), normalizeForScript(cp(0x09AF, 0x09BC)))
        assertEquals(cp(0x09DC), normalizeForScript(cp(0x09A1, 0x09BC)))
        assertEquals(cp(0x09DD), normalizeForScript(cp(0x09A2, 0x09BC)))
    }

    @Test
    fun `NFC leaves these alone, which is the reason the table exists`() {
        // A composition exclusion: NFC will not join it, so something has to.
        // If this ever starts failing, the table can go.
        val decomposed = cp(0x09AF, 0x09BC)
        assertEquals(decomposed, Normalizer.normalize(decomposed, Normalizer.Form.NFC))
    }

    @Test
    fun `the other scripts with composition exclusions are composed too`() {
        assertEquals(cp(0x0958), normalizeForScript(cp(0x0915, 0x093C))) // Devanagari
        assertEquals(cp(0x0929), normalizeForScript(cp(0x0928, 0x093C)))
        assertEquals(cp(0x0A59), normalizeForScript(cp(0x0A16, 0x0A3C))) // Gurmukhi
        assertEquals(cp(0x0A33), normalizeForScript(cp(0x0A32, 0x0A3C)))
        assertEquals(cp(0x0B5C), normalizeForScript(cp(0x0B21, 0x0B3C))) // Oriya
    }

    @Test
    fun `every letter the table can produce really is that decomposition`() {
        // Guards the table against an entry that maps a letter to itself, which
        // is what a decomposed value in it would silently be.
        for (point in COMPOSED) {
            val composed = cp(point)
            val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
            val name = "U+%04X".format(point)
            assertEquals("$name is not two characters decomposed", 2, decomposed.length)
            assertEquals(name, composed, normalizeForScript(decomposed))
        }
    }

    @Test
    fun `an already composed letter is left alone`() {
        assertEquals(cp(0x09DF), normalizeForScript(cp(0x09DF)))
    }

    @Test
    fun `Latin text comes back byte for byte`() {
        val latin = "qwertyuiop QWERTY 0123456789 .,!?-_/@#\$%^&*()[]{}"
        // The same instance, not merely an equal one: a normalizer that rewrites
        // ASCII is one nobody can reason about, so the fast path is asserted
        // rather than assumed.
        assertSame(latin, normalizeForScript(latin))
        assertSame("", normalizeForScript(""))
    }

    @Test
    fun `a nukta with nothing before it stays where it is`() {
        assertEquals(cp(0x09BC), normalizeForScript(cp(0x09BC)))
        assertEquals(cp(0x61, 0x09BC), normalizeForScript(cp(0x61, 0x09BC)))
    }

    @Test
    fun `composition happens in the middle of a string`() {
        assertEquals(
            cp(0x0995, 0x09DF, 0x0995),
            normalizeForScript(cp(0x0995, 0x09AF, 0x09BC, 0x0995)),
        )
    }

    // ---- widths ----

    @Test
    fun `a row of tenths becomes a row of ones`() {
        val row = List(10) { Key(label = "a", width = 0.1f) }
        assertTrue(renormalizeWidths(listOf(row))[0].all { it.width == 1f })
    }

    @Test
    fun `a wide key keeps its ratio`() {
        val row = listOf(Key(label = "a", width = 0.1f), Key(label = "b", width = 0.15f))
        val scaled = renormalizeWidths(listOf(row))[0]
        assertEquals(1f, scaled[0].width, 0.001f)
        assertEquals(1.5f, scaled[1].width, 0.001f)
    }

    @Test
    fun `a short row stays short so the keyboard centres it`() {
        // Nine tenths is QWERTY's home row. It has to come out as 9 against a
        // grid weight of 10, not be stretched to fill the width.
        val rows = listOf(
            List(10) { Key(label = "a", width = 0.1f) },
            List(9) { Key(label = "b", width = 0.1f) },
        )
        val scaled = renormalizeWidths(rows)
        assertEquals(10f, scaled[0].sumOf { it.width.toDouble() }.toFloat(), 0.01f)
        assertEquals(9f, scaled[1].sumOf { it.width.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun `a row already in grid units passes through`() {
        val rows = listOf(List(10) { Key(label = "a", width = 1f) })
        // The same list back, not a rescaled copy: multiplying these by ten
        // would put one key at 10 units and the row at 100.
        assertSame(rows, renormalizeWidths(rows))
    }

    @Test
    fun `no scaled width is zero or wider than a key may be`() {
        val row = listOf(Key(label = "a", width = 0.0001f), Key(label = "b", width = 1.9f))
        assertTrue(renormalizeWidths(listOf(row))[0].all { it.width > 0f && it.width <= MaxKeyWidth })
    }

    // ---- the key-code table ----

    @Test
    fun `the table never answers with the unknown action`() {
        // Unknown cannot be written back out, so a converter that produced one
        // would crash the save rather than lose a key.
        for (code in -20100..-1) {
            assertTrue(code.toString(), keyActionForCode(code) !is KeyAction.Unknown)
        }
    }

    @Test
    fun `the codes both keyboards share map to the same things`() {
        assertEquals(KeyAction.Shift, keyActionForCode(-11))
        assertEquals(KeyAction.CapsLock, keyActionForCode(-13))
        assertEquals(KeyAction.Delete, keyActionForCode(-7))
        assertEquals(KeyAction.ForwardDelete, keyActionForCode(-9))
        assertEquals(KeyAction.Letters, keyActionForCode(-201))
        assertEquals(KeyAction.Symbols, keyActionForCode(-202))
        assertEquals(KeyAction.Emoji, keyActionForCode(-212))
        assertEquals(KeyAction.LanguageSwitch, keyActionForCode(-227))
        assertEquals(KeyAction.Mod(ModifierKey.CTRL), keyActionForCode(-1))
        assertEquals(KeyAction.None, keyActionForCode(-999))
    }

    @Test
    fun `HeliBoard's own function keys land on F1 through F12`() {
        assertEquals(KeyAction.SendKey(KEYCODE_F1), keyActionForCode(-10028))
        assertEquals(KeyAction.SendKey(KEYCODE_F1 + 11), keyActionForCode(-10039))
    }

    @Test
    fun `a host-app action is not a key here`() {
        // Voice input, the settings screen, the clipboard panel and the smartbar
        // toggles are all reached another way in this keyboard. A key that
        // silently does nothing is worse than one that is honestly missing.
        for (code in listOf(-233, -301, -213, -241, -244, -10052, -20000)) {
            assertNull(code.toString(), keyActionForCode(code))
        }
    }

    @Test
    fun `an approximated code is one the table still answers`() {
        for (code in ApproximateCodes) {
            assertNotNull(code.toString(), keyActionForCode(code))
        }
    }

    private companion object {
        const val KEYCODE_F1 = 131

        /** Every letter the composition table can produce, by code point. */
        val COMPOSED = listOf(
            0x0958, 0x0959, 0x095A, 0x095B, 0x095C, 0x095D,
            0x0929, 0x095E, 0x095F, 0x0931, 0x0934,
            0x09DC, 0x09DD, 0x09DF,
            0x0A59, 0x0A5A, 0x0A5B, 0x0A5E, 0x0A33, 0x0A36,
            0x0B5C, 0x0B5D,
        )
    }
}
