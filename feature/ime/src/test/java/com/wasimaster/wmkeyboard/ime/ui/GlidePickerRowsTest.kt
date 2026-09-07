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
    fun `a row of three pushes its middle slot a second key clear`() {
        assertArrayEquals(floatArrayOf(1f, 2f, 1f), pickerRowLifts(3), 0f)
    }

    @Test
    fun `a row with no single middle is all near slots`() {
        assertArrayEquals(floatArrayOf(1f, 1f), pickerRowLifts(2), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f), pickerRowLifts(4), 0f)
    }

    @Test
    fun `a lone slot is the middle one`() {
        assertArrayEquals(floatArrayOf(2f), pickerRowLifts(1), 0f)
    }

    @Test
    fun `the likeliest word takes the slot nearest the finger`() {
        // Three slots a key above the finger, which stopped over the left one.
        val x = floatArrayOf(50f, 150f, 250f)
        val flat = floatArrayOf(1f, 1f, 1f)
        assertArrayEquals(intArrayOf(0, 1, 2), pickerWordAtSlot(x, flat, 50f))
        // The same row with the finger under the right-hand slot.
        assertArrayEquals(intArrayOf(2, 1, 0), pickerWordAtSlot(x, flat, 250f))
    }

    @Test
    fun `the arc keeps the middle slot last however wide the words`() {
        // Wide targets: the middle slot's centre is closer in a straight line
        // than either near slot, and still goes last because it rides higher.
        val x = floatArrayOf(70f, 180f, 290f)
        val lifts = pickerRowLifts(3)
        assertArrayEquals(intArrayOf(0, 2, 1), pickerWordAtSlot(x, lifts, 180f))
        // Finger against the left edge: nearest near slot first, middle last.
        assertArrayEquals(intArrayOf(0, 2, 1), pickerWordAtSlot(x, lifts, 70f))
    }

    @Test
    fun `a second row takes the words the leader row has no slot for`() {
        // Three near slots and two above them: 0..2 land in the leader row.
        val x = floatArrayOf(60f, 180f, 300f, 120f, 240f)
        val lifts = floatArrayOf(1f, 2f, 1f, 3.2f, 3.2f)
        assertArrayEquals(intArrayOf(0, 2, 1, 3, 4), pickerWordAtSlot(x, lifts, 60f))
    }

    @Test
    fun `ties go to the earlier slot`() {
        val x = floatArrayOf(50f, 150f)
        assertArrayEquals(intArrayOf(0, 1), pickerWordAtSlot(x, floatArrayOf(1f, 1f), 100f))
    }
}
