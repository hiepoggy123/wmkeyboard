package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.settings.RowSettings
import com.wasimaster.wmkeyboard.core.settings.SymbolRowLinesRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stacked symbol row (issue #83): how a set is dealt across the lines
 * that scroll on their own, and how tall the stack is.
 */
class SymbolRowLinesTest {

    @Test
    fun `one line holds every entry in order`() {
        assertEquals(listOf(listOf(0, 1, 2, 3)), symbolRowLineEntries(4, 1))
    }

    @Test
    fun `entries are dealt down the lines in turn`() {
        assertEquals(
            listOf(listOf(0, 3, 6), listOf(1, 4), listOf(2, 5)),
            symbolRowLineEntries(7, 3),
        )
    }

    @Test
    fun `a set shorter than the stack leaves the bottom lines empty`() {
        assertEquals(listOf(listOf(0), listOf(1), listOf()), symbolRowLineEntries(2, 3))
    }

    @Test
    fun `an empty set gives every line nothing`() {
        assertEquals(listOf(listOf<Int>(), listOf()), symbolRowLineEntries(0, 2))
    }

    @Test
    fun `every entry lands on exactly one line`() {
        for (count in 0..20) for (lines in 1..4) {
            val dealt = symbolRowLineEntries(count, lines).flatten().sorted()
            assertEquals("$count over $lines", (0 until count).toList(), dealt)
        }
    }

    @Test
    fun `a line count below one still deals onto one line`() {
        assertEquals(listOf(listOf(0, 1)), symbolRowLineEntries(2, 0))
    }

    @Test
    fun `the row is one height per line`() {
        assertEquals(40.dp, symbolRowHeight(RowSettings(symbolRowHeightDp = 40, symbolRowLines = 1)))
        assertEquals(96.dp, symbolRowHeight(RowSettings(symbolRowHeightDp = 32, symbolRowLines = 3)))
    }

    @Test
    fun `a line count off the range is held to it`() {
        assertEquals(SymbolRowLinesRange.last, symbolRowLines(RowSettings(symbolRowLines = 99)))
        assertEquals(SymbolRowLinesRange.first, symbolRowLines(RowSettings(symbolRowLines = 0)))
        assertEquals(
            (40 * SymbolRowLinesRange.last).dp,
            symbolRowHeight(RowSettings(symbolRowHeightDp = 40, symbolRowLines = 99)),
        )
    }
}
