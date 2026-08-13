package com.wasimaster.wmkeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the text after the caret already spaces what is about to be committed
 * there — the shared answer behind the glide's trailing space, the suggestion
 * strip's, and the one after auto-spaced punctuation.
 */
class CaretSpacingTest {

    @Test
    fun `a space is spaced`() {
        assertTrue(spacedAfterCaret(" world"))
    }

    @Test
    fun `a no-break space is spaced`() {
        assertTrue(spacedAfterCaret(" world"))
    }

    @Test
    fun `a tab is spaced`() {
        assertTrue(spacedAfterCaret("\tworld"))
    }

    @Test
    fun `a newline is not spaced`() {
        // Issue #27: a web-view editor (Obsidian) reports the rest of the
        // document, so the caret at the end of a line reads "\n" as what
        // follows. Treating that as an existing space cost the glided word its
        // separator and ran it into the next tapped word.
        assertFalse(spacedAfterCaret("\nnext line"))
        assertFalse(spacedAfterCaret("\r\nnext line"))
    }

    @Test
    fun `nothing after the caret is not spaced`() {
        // End of the text: the space still has to be typed.
        assertFalse(spacedAfterCaret(""))
        assertFalse(spacedAfterCaret(null))
    }

    @Test
    fun `a letter is not spaced`() {
        assertFalse(spacedAfterCaret("world"))
        assertFalse(spacedAfterCaret("."))
    }
}
