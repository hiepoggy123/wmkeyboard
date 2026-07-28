package com.wasimaster.wmkeyboard.core.tools

/**
 * Where a focus ring lands after an arrow key. Pure arithmetic over a grid of
 * items, with no Compose and no Android, so the awkward cases (a ragged last
 * row, a one-column list, an empty result set) can be tested directly instead
 * of being chased around a panel.
 *
 * A list is just a grid one column wide.
 */
data class FocusGrid(val count: Int, val columns: Int)

/**
 * The index an arrow key moves to, or null when the step leaves the grid — the
 * caller turns that into a region change (Up out of the results into the search
 * box) or a no-op.
 *
 * Horizontal movement deliberately never wraps rows: Left at the first column
 * stays put rather than jumping to the end of the row above. Wrapping reads as
 * a glitch when the ring appears somewhere the eye wasn't, and leaving a row is
 * what Tab is for.
 */
fun FocusGrid.step(from: Int, dx: Int, dy: Int): Int? {
    if (count <= 0) return null
    val cols = columns.coerceAtLeast(1)
    val start = from.coerceIn(0, count - 1)
    if (dx != 0) {
        val target = start + dx
        // Same row only.
        if (target < 0 || target >= count) return null
        return if (target / cols == start / cols) target else null
    }
    if (dy == 0) return null
    val target = start + dy * cols
    if (target in 0 until count) return target
    // Down out of a full row into a ragged last row lands on the last item
    // rather than nowhere — the row below does exist, it is just short.
    val lastRow = (count - 1) / cols
    if (dy > 0 && start / cols < lastRow) return count - 1
    return null
}
