package com.wasimaster.wmkeyboard.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSpacingTest {

    @Test
    fun testCaretRightAfterWordBeforeSpace() {
        // "This| is" -> before = 's', after = ' '
        val before = 's'
        val after = ' '
        val leading = VoiceSpacing.needsLeadingSpace(before, after)
        val trailing = VoiceSpacing.needsTrailingSpace(before, after)

        assertFalse(leading)
        assertFalse(trailing)
        assertEquals("really", VoiceSpacing.format("really", leading, trailing))
    }

    @Test
    fun testCaretAfterSpaceBeforeWord() {
        // "This |is" -> before = ' ', after = 'i'
        val before = ' '
        val after = 'i'
        val leading = VoiceSpacing.needsLeadingSpace(before, after)
        val trailing = VoiceSpacing.needsTrailingSpace(before, after)

        assertFalse(leading)
        assertTrue(trailing)
        assertEquals("really ", VoiceSpacing.format("really", leading, trailing))
    }

    @Test
    fun testWordSelectionReplacement() {
        // "This [is] a sample" -> before = ' ', after = ' '
        val before = ' '
        val after = ' '
        val leading = VoiceSpacing.needsLeadingSpace(before, after)
        val trailing = VoiceSpacing.needsTrailingSpace(before, after)

        assertFalse(leading)
        assertFalse(trailing)
        assertEquals("really", VoiceSpacing.format("really", leading, trailing))
    }

    @Test
    fun testEndOfLineDictation() {
        // "Hello|" -> before = 'o', after = null
        val before = 'o'
        val after: Char? = null
        val leading = VoiceSpacing.needsLeadingSpace(before, after)
        val trailing = VoiceSpacing.needsTrailingSpace(before, after)

        assertTrue(leading)
        assertFalse(trailing)
        assertEquals(" world", VoiceSpacing.format("world", leading, trailing))
    }

    @Test
    fun testEmptyFieldDictation() {
        // "|" -> before = null, after = null
        val before: Char? = null
        val after: Char? = null
        val leading = VoiceSpacing.needsLeadingSpace(before, after)
        val trailing = VoiceSpacing.needsTrailingSpace(before, after)

        assertFalse(leading)
        assertFalse(trailing)
        assertEquals("hello", VoiceSpacing.format("hello", leading, trailing))
    }

    @Test
    fun testPunctuationNotSpaced() {
        val before = 'o'
        val after = null
        val leading = VoiceSpacing.needsLeadingSpace(before, after)
        val trailing = VoiceSpacing.needsTrailingSpace(before, after)

        assertEquals(".", VoiceSpacing.format(".", leading, trailing))
    }
}
