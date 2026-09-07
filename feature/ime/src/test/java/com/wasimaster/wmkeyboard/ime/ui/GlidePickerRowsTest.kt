package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the picker's targets split across rows. The layout caps each target at a
 * third of the window less the gaps, so the split only ever has to be one row
 * or two; this pins that the leader's row is the fuller one and that a capped
 * row of three always fits.
 */
class GlidePickerRowsTest {

    private companion object {
        const val GAP = 10
        const val WIDTH = 360
    }

    @Test
    fun `targets that fit share one row`() {
        assertEquals(3, pickerLeaderRowCount(intArrayOf(100, 100, 100), GAP, WIDTH))
        assertEquals(4, pickerLeaderRowCount(intArrayOf(80, 80, 80, 80), GAP, WIDTH))
        assertEquals(1, pickerLeaderRowCount(intArrayOf(300), GAP, WIDTH))
    }

    @Test
    fun `a row exactly as wide as the window still fits`() {
        assertEquals(3, pickerLeaderRowCount(intArrayOf(110, 110, 120), GAP, WIDTH))
    }

    @Test
    fun `targets that do not fit split with the larger half nearest the finger`() {
        assertEquals(3, pickerLeaderRowCount(intArrayOf(100, 100, 100, 100, 100), GAP, WIDTH))
        assertEquals(2, pickerLeaderRowCount(intArrayOf(110, 110, 110, 110), GAP, WIDTH))
    }

    @Test
    fun `a capped row of three always fits`() {
        // The layout's cap: (width - 2 * gap) / 3 per target.
        val cap = (WIDTH - 2 * GAP) / 3
        for (n in 2..5) {
            val widths = IntArray(n) { cap }
            val leaderRow = pickerLeaderRowCount(widths, GAP, WIDTH)
            val rowWidth = leaderRow * cap + GAP * (leaderRow - 1)
            assertTrue("$n capped targets: leader row of $leaderRow is $rowWidth wide", rowWidth <= WIDTH)
            assertTrue("$n capped targets never need a third row", n - leaderRow <= leaderRow)
        }
    }

    @Test
    fun `an empty picker has an empty row`() {
        assertEquals(0, pickerLeaderRowCount(intArrayOf(), GAP, WIDTH))
    }
}
