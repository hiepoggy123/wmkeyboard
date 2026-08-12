package com.wasimaster.wmkeyboard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hex field of the colour picker.
 *
 * Issue #19 reported that editing a colour by hex "adds FF". The old field was
 * AARRGGBB on every row, alpha or not, and any six-digit entry was turned into
 * `0xFF000000 or entry`. Six digits now keep the opacity the picker is holding,
 * and the rows that carry no alpha show six digits in the first place.
 */
class ColorPickerHexTest {

    private val opaque = 0xFF

    @Test
    fun `six digits keep the opacity the picker holds`() {
        // Half transparent red, typed as RRGGBB on a row that supports alpha.
        assertEquals(0x80FF0000.toInt(), parseHex("FF0000", alpha = 0x80))
    }

    @Test
    fun `six digits on a row without alpha stay opaque`() {
        assertEquals(0xFF4C8DF6.toInt(), parseHex("4C8DF6", alpha = opaque))
    }

    @Test
    fun `eight digits carry their own alpha and ignore the picker`() {
        assertEquals(0x804C8DF6.toInt(), parseHex("804C8DF6", alpha = opaque))
        assertEquals(0x004C8DF6, parseHex("004C8DF6", alpha = opaque))
    }

    @Test
    fun `three digits expand the shorthand`() {
        assertEquals(0xFFFF0000.toInt(), parseHex("F00", alpha = opaque))
        assertEquals(0xFFAABBCC.toInt(), parseHex("ABC", alpha = opaque))
    }

    @Test
    fun `lower case parses, so the field does not have to re-case what is typed`() {
        assertEquals(parseHex("4C8DF6", alpha = opaque), parseHex("4c8df6", alpha = opaque))
    }

    @Test
    fun `partial entries parse to nothing, so deleting does not move the colour`() {
        // The repro deletes an eight digit value down to two. None of the
        // lengths it passes through may commit a colour.
        for (partial in listOf("", "F", "FF", "FF0", "FF00", "FF000", "FF0000F")) {
            if (partial.length == 3 || partial.length == 6) continue
            assertNull("$partial parsed", parseHex(partial, alpha = opaque))
        }
    }

    @Test
    fun `junk parses to nothing`() {
        assertNull(parseHex("GGGGGG", alpha = opaque))
        assertNull(parseHex("FF00 0", alpha = opaque))
    }
}
