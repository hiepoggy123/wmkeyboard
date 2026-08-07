package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.layout.expandForTablet
import com.wasimaster.wmkeyboard.core.layout.tabletGridWidth
import com.wasimaster.wmkeyboard.core.settings.DeviceForm
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard window must not change height while the user is typing in one
 * field. Panels size themselves from [keyRowsHeight], so if it disagrees with
 * the grid the window resizes on every panel open — which is what these pin.
 */
class KeyRowsHeightTest {

    private fun grid(rows: Int) = KeyboardLayout(
        name = "test",
        rows = List(rows) { listOf(Key("a"), Key("b")) },
    )

    private fun state(set: LayoutSet, keyHeight: Int = 48, numberRow: Boolean = false) =
        KeyboardUiState(
            settings = KeyboardSettings(keyHeightDp = keyHeight, numberRow = numberRow),
            layouts = set,
        )

    /** What the function shipped as, for four rows: (keyHeight + 2*4) * rows + 2*2. */
    private fun expected(rows: Int, keyHeight: Int = 48) =
        (keyHeight.dp + 4.dp * 2) * rows + 2.dp * 2

    @Test
    fun `the shipped layouts still measure four rows`() {
        val set = LayoutSet.Default
        assertEquals(4, set.rowSpan)
        assertEquals(expected(4), keyRowsHeight(state(set)))
    }

    /**
     * The direct pin on the constraint the tablet expansion rests on.
     *
     * [keyRowsHeight] and [LayoutSet.rowSpan] size the IME window from layer row
     * *counts*, and neither sees the expansion — which is fine only because the
     * expansion never changes a row count. A transform that added a row would
     * draw a keyboard taller than the window measured for it, and the host app
     * would reflow behind it mid-sentence.
     */
    @Test
    fun `the tablet grid measures exactly as tall as the phone grid`() {
        val phone = LayoutSet.Default
        val expanded = BuiltInLayouts.QWERTY.compile(LayoutLayer.LETTERS)
            .expandForTablet(DeviceForm.LARGE_TABLET, numberRowShown = true)
        val tablet = LayoutSet(
            letters = expanded,
            symbols = phone.symbols,
            symbolsShifted = phone.symbolsShifted,
            gridWidth = tabletGridWidth(phone.letters, DeviceForm.LARGE_TABLET),
        )

        // The grid really did widen — otherwise this test proves nothing.
        assertTrue(expanded.rows.first().size > phone.letters.rows.first().size)
        assertEquals(phone.rowSpan, tablet.rowSpan)
        assertEquals(keyRowsHeight(state(phone)), keyRowsHeight(state(tablet)))
        assertEquals(
            keyRowsHeight(state(phone, numberRow = true)),
            keyRowsHeight(state(tablet, numberRow = true)),
        )
    }

    @Test
    fun `a three row layout reserves three rows`() {
        val set = LayoutSet(grid(3), grid(3), grid(3))
        assertEquals(3, set.rowSpan)
        assertEquals(expected(3), keyRowsHeight(state(set)))
    }

    @Test
    fun `a six row layout reserves six rows`() {
        val set = LayoutSet(grid(6), grid(6), grid(6))
        assertEquals(6, set.rowSpan)
        assertEquals(expected(6), keyRowsHeight(state(set)))
    }

    /**
     * The one that matters. Pressing ?123 happens mid-word; if the span
     * followed the active layer the window would resize under the user and
     * reflow the host app's whole screen.
     */
    @Test
    fun `the span is the tallest reachable layer not the active one`() {
        val set = LayoutSet(letters = grid(3), symbols = grid(5), symbolsShifted = grid(4))
        assertEquals("a 3-row letters layer must still reserve the symbols layer's 5", 5, set.rowSpan)
        assertEquals(expected(5), keyRowsHeight(state(set)))
    }

    @Test
    fun `a numeric pad counts toward the span`() {
        val set = LayoutSet(grid(3), grid(3), grid(3), numeric = grid(7))
        assertEquals(7, set.rowSpan)
    }

    @Test
    fun `the number row adds its own height on top`() {
        val set = LayoutSet(grid(4), grid(4), grid(4))
        val without = keyRowsHeight(state(set))
        val with = keyRowsHeight(state(set, numberRow = true))
        assertEquals(
            with,
            without + KeyboardSettings().numberRowHeightDp.dp + 4.dp * 2,
        )
    }

    @Test
    fun `an empty layout still reserves one row`() {
        val empty = KeyboardLayout("empty", emptyList())
        assertEquals(
            "a zero-row grid would otherwise collapse the keyboard to nothing",
            1,
            LayoutSet(empty, empty, empty).rowSpan,
        )
    }

    @Test
    fun `the built-in latin layouts report a full alphabet`() {
        for (id in listOf(BuiltInLayouts.QWERTY, BuiltInLayouts.AZERTY, BuiltInLayouts.DVORAK)) {
            val set = LayoutSet(
                id.compile(LayoutLayer.LETTERS),
                id.compile(LayoutLayer.SYMBOLS),
                id.compile(LayoutLayer.SYMBOLS_SHIFTED),
            )
            assertTrue("${id.id} should have a-z", set.lettersHaveFullAlphabet)
        }
    }

    @Test
    fun `a partial grid does not claim a full alphabet`() {
        val partial = KeyboardLayout(
            name = "partial",
            rows = listOf(listOf(Key("a"), Key("b"), Key(" ", action = KeyAction.Space))),
        )
        assertFalse(
            "gesture typing has to switch itself off rather than guess",
            LayoutSet(partial, partial, partial).lettersHaveFullAlphabet,
        )
    }

    @Test
    fun `the bengali layouts do not claim a latin alphabet`() {
        val set = LayoutSet(
            BuiltInLayouts.PROBHAT.compile(LayoutLayer.LETTERS),
            BuiltInLayouts.PROBHAT.compile(LayoutLayer.SYMBOLS),
            BuiltInLayouts.PROBHAT.compile(LayoutLayer.SYMBOLS_SHIFTED),
        )
        assertFalse(set.lettersHaveFullAlphabet)
    }
}
