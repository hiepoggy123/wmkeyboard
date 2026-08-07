package com.wasimaster.wmkeyboard.core.tools

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageSwitchTest {

    @Test
    fun `ctrl-space steps forward and ctrl-shift-space back`() {
        assertEquals(1, languageSwitchDelta(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON))
        assertEquals(
            -1,
            languageSwitchDelta(
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            ),
        )
    }

    @Test
    fun `bare space and other chords are not a switch`() {
        assertNull(languageSwitchDelta(KeyEvent.KEYCODE_SPACE, 0))
        // AltGr arrives as Ctrl+Alt and produces a character.
        assertNull(
            languageSwitchDelta(
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
            ),
        )
        // Meta+Space is the system's own IME rotation.
        assertNull(
            languageSwitchDelta(
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON,
            ),
        )
        assertNull(languageSwitchDelta(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun `caps lock does not stop the chord matching`() {
        assertEquals(
            1,
            languageSwitchDelta(
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.META_CTRL_ON or KeyEvent.META_CAPS_LOCK_ON,
            ),
        )
    }

    @Test
    fun `cycle starts one step from the current layout and wraps`() {
        val ids = listOf("a", "b", "c")
        assertEquals(2, languageCycleStart(ids, "b", 1))
        assertEquals(0, languageCycleStart(ids, "b", -1))
        assertEquals(0, languageCycleStart(ids, "c", 1))
        assertEquals(2, languageCycleStart(ids, "a", -1))
    }

    @Test
    fun `a current layout outside the cycle starts from the pointed end`() {
        val ids = listOf("a", "b", "c")
        assertEquals(0, languageCycleStart(ids, "zzz", 1))
        assertEquals(2, languageCycleStart(ids, "zzz", -1))
    }

    @Test
    fun `nothing to cycle with fewer than two layouts`() {
        assertNull(languageCycleStart(emptyList(), "a", 1))
        assertNull(languageCycleStart(listOf("a"), "a", 1))
    }

    @Test
    fun `stepping wraps both directions`() {
        assertEquals(0, languageCycleStep(2, 1, 3))
        assertEquals(2, languageCycleStep(0, -1, 3))
        assertEquals(1, languageCycleStep(0, 1, 3))
    }
}
