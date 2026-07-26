package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The edges are the whole point: a ragged last row and a one-column list are
 * where a naive `index + columns` either lands on nothing or silently wraps to
 * the wrong item.
 */
class PanelFocusMathTest {

    @Test
    fun `horizontal moves within a row`() {
        val grid = FocusGrid(count = 9, columns = 3)
        assertEquals(1, grid.step(0, dx = 1, dy = 0))
        assertEquals(0, grid.step(1, dx = -1, dy = 0))
    }

    @Test
    fun `horizontal never wraps to another row`() {
        val grid = FocusGrid(count = 9, columns = 3)
        assertNull(grid.step(2, dx = 1, dy = 0))
        assertNull(grid.step(3, dx = -1, dy = 0))
        assertNull(grid.step(0, dx = -1, dy = 0))
        assertNull(grid.step(8, dx = 1, dy = 0))
    }

    @Test
    fun `vertical moves a whole row`() {
        val grid = FocusGrid(count = 9, columns = 3)
        assertEquals(4, grid.step(1, dx = 0, dy = 1))
        assertEquals(1, grid.step(4, dx = 0, dy = -1))
    }

    @Test
    fun `down into a ragged last row lands on the last item`() {
        // 7 items over 3 columns: the last row holds only index 6.
        val grid = FocusGrid(count = 7, columns = 3)
        assertEquals(6, grid.step(4, dx = 0, dy = 1))
        assertEquals(6, grid.step(5, dx = 0, dy = 1))
    }

    @Test
    fun `down from the last row goes nowhere`() {
        val grid = FocusGrid(count = 7, columns = 3)
        assertNull(grid.step(6, dx = 0, dy = 1))
    }

    @Test
    fun `up from the first row goes nowhere`() {
        val grid = FocusGrid(count = 9, columns = 3)
        assertNull(grid.step(1, dx = 0, dy = -1))
    }

    @Test
    fun `a single column is a list`() {
        val grid = FocusGrid(count = 4, columns = 1)
        assertEquals(1, grid.step(0, dx = 0, dy = 1))
        assertNull(grid.step(0, dx = 1, dy = 0))
        assertNull(grid.step(3, dx = 0, dy = 1))
    }

    @Test
    fun `an empty grid never moves`() {
        val grid = FocusGrid(count = 0, columns = 3)
        assertNull(grid.step(0, dx = 1, dy = 0))
        assertNull(grid.step(0, dx = 0, dy = 1))
    }

    @Test
    fun `more columns than items is a single row`() {
        val grid = FocusGrid(count = 2, columns = 8)
        assertEquals(1, grid.step(0, dx = 1, dy = 0))
        assertNull(grid.step(1, dx = 1, dy = 0))
        assertNull(grid.step(0, dx = 0, dy = 1))
    }

    @Test
    fun `an out of range index is clamped rather than crashing`() {
        val grid = FocusGrid(count = 4, columns = 2)
        assertEquals(2, grid.step(99, dx = -1, dy = 0))
        assertEquals(1, grid.step(-5, dx = 1, dy = 0))
    }

    @Test
    fun `a zero step is a no-op`() {
        val grid = FocusGrid(count = 4, columns = 2)
        assertNull(grid.step(1, dx = 0, dy = 0))
    }
}
