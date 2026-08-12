package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.settings.CursorTools
import com.wasimaster.wmkeyboard.core.settings.HoldRepeatCursorTools
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isDirectBootSafeTool
import com.wasimaster.wmkeyboard.core.settings.toolOpensScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Selection mode tool: the tap ladder that tells one press from two and
 * three, and the answers the tool gives the surfaces around it.
 *
 * The ladder is the part worth a test. It reads three different gestures off
 * one button with nothing to go on but the gap between them, and every rung
 * changes what the next press means.
 */
class SelectionModeTest {

    private val window = 350L

    @Test
    fun `a lone press toggles`() {
        val taps = SelectionTapCounter(window)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000, multiTap = true))
        // Well past the window: a first press again, not a second.
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000 + window, multiTap = true))
    }

    @Test
    fun `two presses inside the window select the word, three the line`() {
        val taps = SelectionTapCounter(window)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000, multiTap = true))
        assertEquals(SelectionTap.WORD, taps.tap(1_100, multiTap = true))
        assertEquals(SelectionTap.LINE, taps.tap(1_200, multiTap = true))
    }

    /** A drummed-on button keeps working rather than going dead after three. */
    @Test
    fun `a fourth quick press starts a new ladder`() {
        val taps = SelectionTapCounter(window)
        taps.tap(1_000, multiTap = true)
        taps.tap(1_100, multiTap = true)
        taps.tap(1_200, multiTap = true)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_300, multiTap = true))
        assertEquals(SelectionTap.WORD, taps.tap(1_400, multiTap = true))
    }

    /** The gap is measured against the previous press, not the first of the run. */
    @Test
    fun `a slow second press is a first press again`() {
        val taps = SelectionTapCounter(window)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000, multiTap = true))
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000 + window + 1, multiTap = true))
        assertEquals(SelectionTap.WORD, taps.tap(1_000 + window + 50, multiTap = true))
    }

    @Test
    fun `with the shortcuts off every press toggles`() {
        val taps = SelectionTapCounter(window)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000, multiTap = false))
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_010, multiTap = false))
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_020, multiTap = false))
    }

    /**
     * The load-bearing one for the hold: a press right after a hold must read as
     * a first press. Without the reset it would be the second of a double and
     * select a word nobody asked for.
     */
    @Test
    fun `a hold in the middle of a run starts the ladder over`() {
        val taps = SelectionTapCounter(window)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_000, multiTap = true))
        taps.reset()
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_050, multiTap = true))
    }

    /** Turning the shortcuts off mid-run also drops it, rather than half-keeping it. */
    @Test
    fun `a press with the shortcuts off drops the run so far`() {
        val taps = SelectionTapCounter(window)
        taps.tap(1_000, multiTap = true)
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_050, multiTap = false))
        assertEquals(SelectionTap.TOGGLE, taps.tap(1_100, multiTap = true))
    }

    /**
     * The tool is filed with the caret tools, which is what gives it their
     * grouping on the Tools screen, their place in the toolbox order, and the
     * two answers below for free.
     */
    @Test
    fun `the tool is a cursor tool that acts in place`() {
        assertTrue(ToolbarTool.SELECT_MODE in CursorTools)
        // Nothing to open: it flips a mode and leaves the keys where they are.
        assertFalse(toolOpensScreen(ToolbarTool.SELECT_MODE))
        // It only touches the input connection, so it works before first unlock.
        assertTrue(isDirectBootSafeTool(ToolbarTool.SELECT_MODE))
        // Its hold arms the mode instead, so it must not also repeat.
        assertFalse(ToolbarTool.SELECT_MODE in HoldRepeatCursorTools)
    }
}
