package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertArrayEquals
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

        /** A key's height, the unit the arc is spaced in, and a target's own. */
        const val KEY = 55f
        const val ROW = 44f

        /** The middle slot of a leader row of three, and a row without one. */
        const val MIDDLE = 1
        const val NO_MIDDLE = -1
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

    @Test
    fun `a row of three lifts its middle slot a quarter key clear`() {
        assertArrayEquals(floatArrayOf(1f, 1.25f, 1f), pickerRowLifts(3), 0f)
    }

    @Test
    fun `a row with no single middle is all near slots`() {
        assertArrayEquals(floatArrayOf(1f, 1f), pickerRowLifts(2), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f), pickerRowLifts(4), 0f)
    }

    @Test
    fun `a lone slot is the middle one`() {
        assertArrayEquals(floatArrayOf(1.25f), pickerRowLifts(1), 0f)
    }

    @Test
    fun `the likeliest word takes the slot nearest the finger`() {
        // A flat row with no middle to favour: nearest first, either way round.
        val x = floatArrayOf(50f, 150f, 250f)
        val y = floatArrayOf(-KEY, -KEY, -KEY)
        assertArrayEquals(intArrayOf(0, 1, 2), pickerWordAtSlot(x, y, 50f, 0f, NO_MIDDLE))
        assertArrayEquals(intArrayOf(2, 1, 0), pickerWordAtSlot(x, y, 250f, 0f, NO_MIDDLE))
    }

    @Test
    fun `the middle slot keeps the leader under a finger anywhere near it`() {
        // The arc's real shape, finger under the middle of the row.
        val x = floatArrayOf(70f, 180f, 290f)
        val y = floatArrayOf(-KEY, -1.25f * KEY, -KEY)
        // Word 0 goes straight up: the side slots are nowhere near twice as
        // close (123 against 69), so the easiest move stays the likeliest word.
        assertArrayEquals(intArrayOf(1, 0, 2), pickerWordAtSlot(x, y, 180f, 0f, MIDDLE))
    }

    @Test
    fun `a side slot takes the leader only when it is more than twice as close`() {
        // A stroke that ended at the left edge: the row is clamped there and the
        // middle is most of a row away, so the slot under the finger wins.
        val x = floatArrayOf(70f, 180f, 290f)
        val y = floatArrayOf(-KEY, -1.25f * KEY, -KEY)
        assertArrayEquals(intArrayOf(0, 1, 2), pickerWordAtSlot(x, y, 70f, 0f, MIDDLE))
    }

    @Test
    fun `a second row is further than every slot in the leader row`() {
        val x = floatArrayOf(60f, 180f, 300f, 120f, 240f)
        val upper = -1.25f * KEY - ROW - GAP
        val y = floatArrayOf(-KEY, -1.25f * KEY, -KEY, upper, upper)
        assertArrayEquals(intArrayOf(1, 0, 2, 3, 4), pickerWordAtSlot(x, y, 180f, 0f, MIDDLE))
    }

    @Test
    fun `ties go to the earlier slot`() {
        val x = floatArrayOf(50f, 150f)
        val y = floatArrayOf(-KEY, -KEY)
        assertArrayEquals(intArrayOf(0, 1), pickerWordAtSlot(x, y, 100f, 0f, NO_MIDDLE))
    }
}
