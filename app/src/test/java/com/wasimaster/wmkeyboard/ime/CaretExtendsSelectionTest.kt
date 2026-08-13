package com.wasimaster.wmkeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the spacebar cursor swipe and the volume keys read to decide between
 * moving the caret and dragging a selection out (issue #23): selection mode from
 * any of its three surfaces, or a shift the user put up themselves.
 */
class CaretExtendsSelectionTest {

    @Test
    fun `a caps lock the user pressed extends`() {
        // The reported bug: the toolbar's arrow tools and a layout's own arrow
        // keys already selected under a shift lock, and only these gestures did
        // not, so the same lock behaved differently on the same caret.
        val state = KeyboardUiState(shiftState = ShiftState.CAPS_LOCK, shiftPressedByUser = true)
        assertTrue(state.caretExtendsSelection)
    }

    @Test
    fun `a one-shot shift the user pressed extends`() {
        val state = KeyboardUiState(shiftState = ShiftState.ON, shiftPressedByUser = true)
        assertTrue(state.caretExtendsSelection)
    }

    @Test
    fun `a shift the keyboard armed does not`() {
        // Auto-capitalize at a sentence start, or an all-caps field: a statement
        // about the next letter's case, not about the caret.
        assertFalse(KeyboardUiState(shiftState = ShiftState.ON).caretExtendsSelection)
        assertFalse(KeyboardUiState(shiftState = ShiftState.CAPS_LOCK).caretExtendsSelection)
    }

    @Test
    fun `selection mode extends with no shift at all`() {
        assertTrue(KeyboardUiState(selectionMode = true).caretExtendsSelection)
        assertTrue(KeyboardUiState(selectionHold = true).caretExtendsSelection)
        assertTrue(KeyboardUiState(textEditSelecting = true).caretExtendsSelection)
    }

    @Test
    fun `a plain scrub moves the caret`() {
        assertFalse(KeyboardUiState().caretExtendsSelection)
    }
}
