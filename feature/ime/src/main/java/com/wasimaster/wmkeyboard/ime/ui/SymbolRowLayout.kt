package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.settings.RowSettings
import com.wasimaster.wmkeyboard.core.settings.SymbolRowLinesRange

/**
 * The symbol row's whole height: one [RowSettings.symbolRowHeightDp] per line
 * (issue #83). The strip and the panels that stand in for it both size from
 * this, so a stack of three lines hides under the symbols panel exactly as one
 * line did.
 */
internal fun symbolRowHeight(rows: RowSettings): Dp =
    (rows.symbolRowHeightDp * symbolRowLines(rows)).dp

/** The line count the row draws: the setting, held to its range. */
internal fun symbolRowLines(rows: RowSettings): Int =
    rows.symbolRowLines.coerceIn(SymbolRowLinesRange.first, SymbolRowLinesRange.last)

/**
 * Which entries each line of a symbol row that scrolls line by line shows,
 * as indices into the set, top line first.
 *
 * Dealt out in turn rather than cut into runs: entry 0 goes to the top line,
 * entry 1 to the one under it, and so on down and round again. That is the
 * order a grid of the same line count fills its columns in, so switching a
 * row between scrolling together and scrolling apart moves nothing until a
 * line is actually scrolled, and a hardware hint's badge names the same entry
 * in both. A set shorter than the stack leaves the bottom lines empty rather
 * than stretching; an empty set gives every line nothing.
 */
internal fun symbolRowLineEntries(count: Int, lines: Int): List<List<Int>> {
    val stack = lines.coerceAtLeast(1)
    val entries = List(stack) { ArrayList<Int>() }
    for (index in 0 until count.coerceAtLeast(0)) entries[index % stack] += index
    return entries
}
