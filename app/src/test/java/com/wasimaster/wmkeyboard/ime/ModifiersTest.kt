package com.wasimaster.wmkeyboard.ime

import android.view.KeyEvent
import com.wasimaster.wmkeyboard.core.layout.ModifierKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The latch mirrors [ShiftState] on purpose: tap arms, a quick second tap locks,
 * anything after that clears. A timer-free OFF → ARMED → LOCKED cycle would have
 * left Ctrl *locked* when the user armed it and changed their mind — the one
 * state where every following letter silently becomes a shortcut.
 */
class ModifiersTest {

    private val ctrl = ModifierKey.CTRL
    private val alt = ModifierKey.ALT

    @Test
    fun `nothing is latched by default`() {
        assertTrue(Modifiers.None.isEmpty)
        assertEquals(ModifierState.OFF, Modifiers.None[ctrl])
        assertEquals(0, Modifiers.None.metaFlags())
    }

    @Test
    fun `get and with address each modifier independently`() {
        val armed = Modifiers.None.with(ctrl, ModifierState.ARMED)
        assertEquals(ModifierState.ARMED, armed[ctrl])
        assertEquals(ModifierState.OFF, armed[alt])
        assertEquals(ModifierState.OFF, armed[ModifierKey.META])
        assertFalse(armed.isEmpty)
    }

    @Test
    fun `an armed latch is spent by the key that uses it`() {
        val armed = Modifiers.None.with(ctrl, ModifierState.ARMED)
        assertTrue("one key, then gone", armed.consumed().isEmpty)
    }

    @Test
    fun `a locked latch survives the key that uses it`() {
        val locked = Modifiers.None.with(ctrl, ModifierState.LOCKED)
        assertEquals(
            "surviving is the entire point of locking it",
            ModifierState.LOCKED,
            locked.consumed()[ctrl],
        )
    }

    @Test
    fun `consuming drops the armed ones and keeps the locked ones together`() {
        val mixed = Modifiers.None
            .with(ctrl, ModifierState.LOCKED)
            .with(alt, ModifierState.ARMED)
        val after = mixed.consumed()
        assertEquals(ModifierState.LOCKED, after[ctrl])
        assertEquals(ModifierState.OFF, after[alt])
    }

    @Test
    fun `meta flags carry both the generic and the left-hand bit`() {
        val flags = Modifiers.None.with(ctrl, ModifierState.ARMED).metaFlags()
        assertTrue("editors test either one", flags and KeyEvent.META_CTRL_ON != 0)
        assertTrue("a real keyboard reports both", flags and KeyEvent.META_CTRL_LEFT_ON != 0)
        assertEquals("and nothing else", 0, flags and KeyEvent.META_ALT_ON)
    }

    @Test
    fun `combined modifiers or their flags together`() {
        val flags = Modifiers.None
            .with(ctrl, ModifierState.ARMED)
            .with(alt, ModifierState.LOCKED)
            .with(ModifierKey.META, ModifierState.ARMED)
            .metaFlags()
        assertTrue(flags and KeyEvent.META_CTRL_ON != 0)
        assertTrue(flags and KeyEvent.META_ALT_ON != 0)
        assertTrue(flags and KeyEvent.META_META_ON != 0)
    }

    @Test
    fun `a locked latch reports the same flags as an armed one`() {
        assertEquals(
            "locking changes how long it lasts, not what it means",
            Modifiers.None.with(ctrl, ModifierState.ARMED).metaFlags(),
            Modifiers.None.with(ctrl, ModifierState.LOCKED).metaFlags(),
        )
    }

    /** The gesture the service runs: tap, tap again, tap once more. */
    @Test
    fun `the tap cycle is off then armed then locked then off`() {
        fun next(current: ModifierState, doubleTap: Boolean) = when {
            doubleTap && current != ModifierState.LOCKED -> ModifierState.LOCKED
            current == ModifierState.OFF -> ModifierState.ARMED
            else -> ModifierState.OFF
        }

        assertEquals(ModifierState.ARMED, next(ModifierState.OFF, doubleTap = false))
        assertEquals(ModifierState.LOCKED, next(ModifierState.ARMED, doubleTap = true))
        assertEquals(ModifierState.OFF, next(ModifierState.LOCKED, doubleTap = true))
        assertEquals(
            "a slow second tap clears rather than locking",
            ModifierState.OFF,
            next(ModifierState.ARMED, doubleTap = false),
        )
    }
}
