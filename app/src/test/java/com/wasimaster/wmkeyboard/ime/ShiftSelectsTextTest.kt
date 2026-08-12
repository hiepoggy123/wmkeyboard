package com.wasimaster.wmkeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shift as a selection modifier for the toolbar's caret-moving tools. The
 * decision is on the ui state, so it can be checked without an InputConnection
 * — the service does nothing more than pass this to `onTextEdit`, which already
 * knows how to send a shifted arrow.
 */
class ShiftSelectsTextTest {

    private fun state(shift: ShiftState, byUser: Boolean) =
        KeyboardUiState(shiftState = shift, shiftPressedByUser = byUser)

    @Test
    fun `a one-shot shift the user armed selects`() {
        assertTrue(state(ShiftState.ON, byUser = true).shiftSelectsText)
    }

    @Test
    fun `caps lock selects too`() {
        // Unlike Shift+Enter: dragging a long selection out is exactly what a
        // lock is for, so this one reads the lock as well as the one-shot.
        assertTrue(state(ShiftState.CAPS_LOCK, byUser = true).shiftSelectsText)
    }

    @Test
    fun `auto-capitalize's shift does not`() {
        // The case that would otherwise turn an ordinary cursor tap at a
        // sentence start into a silent selection the next keystroke overwrites.
        assertFalse(state(ShiftState.ON, byUser = false).shiftSelectsText)
    }

    @Test
    fun `an all-caps field's lock does not`() {
        // TYPE_TEXT_FLAG_CAP_CHARACTERS maps to CAPS_LOCK with nobody pressing
        // anything; reading it would leave the caret unmovable in that field.
        assertFalse(state(ShiftState.CAPS_LOCK, byUser = false).shiftSelectsText)
    }

    @Test
    fun `no shift does not`() {
        assertFalse(state(ShiftState.OFF, byUser = false).shiftSelectsText)
    }
}
