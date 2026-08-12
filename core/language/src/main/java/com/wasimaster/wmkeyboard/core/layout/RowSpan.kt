package com.wasimaster.wmkeyboard.core.layout

/**
 * Where the keys of a grid land once [Key.rowSpan] is allowed to make one of
 * them taller than its row.
 *
 * ## Why the geometry lives here
 *
 * A row without spans is laid out by the renderer's own `Row`: every key takes
 * `Modifier.weight(key.width)` and Compose does the arithmetic. A spanning key
 * cannot be expressed that way — it belongs to two rows at once, and a child
 * drawn outside its `Row`'s bounds is not hit-tested, so the bottom half of a
 * two-row Enter would look right and be untappable.
 *
 * So a run of rows joined by a span is measured as one block instead, and this
 * file is the measurement: pure arithmetic over grid units, no Compose, unit
 * testable, and shared by the keyboard and the layout editor's preview so the
 * two can never draw the same grid differently.
 *
 * ## The model
 *
 * A key with `rowSpan = n` holds its horizontal interval in the n rows starting
 * at its own. Keys in the rows below flow left to right and **skip over** the
 * held intervals, the way cells flow around a `rowspan` in an HTML table. That
 * makes the common arrangement — a tall Enter or Shift at one edge — come out
 * as written, and it gives a predictable answer for the rare span in the middle
 * of a row rather than overlapping keys.
 *
 * Rows are still centred against the grid weight, counting the columns held over
 * them ([spanRowWidths]), so the row under a two-row Enter is not treated as a
 * short row and re-centred out from under it.
 */

/** Nothing here compares floats for equality; a hundredth of a column is noise. */
private const val Epsilon = 0.001f

/** One key of a grid, and the rectangle it holds, measured in grid units. */
data class KeySlot(
    /** Row the key is written in — the first of the [span] rows it covers. */
    val row: Int,
    /** Index within that row, so a caller can map a slot back to the key it edits. */
    val col: Int,
    val key: Key,
    /** Left edge, in grid units from the left of the board. */
    val x: Float,
    /** How many rows this key covers, already clamped to the rows that exist. */
    val span: Int,
) {
    /** Right edge, in grid units. */
    val end: Float get() = x + key.width
}

/** Whether any key in [rows] covers more than its own row. */
fun hasRowSpans(rows: List<List<Key>>): Boolean =
    rows.any { row -> row.any { it.rowSpan > 1 } }

/**
 * How many rows the key at [row] really covers, given a grid of [rowCount] rows.
 *
 * Clamped rather than validated: a span reaching past the last row is what
 * deleting a row in the editor leaves behind, and the grid still has to draw.
 */
fun Key.spanFrom(row: Int, rowCount: Int): Int =
    rowSpan.coerceIn(1, (rowCount - row).coerceAtLeast(1))

/**
 * The width of every row for layout purposes: its own keys, plus the columns
 * held over it by spanning keys above.
 *
 * This is the number a row is centred and elected on, not `row.sumOf { width }`
 * — the row under a two-row Enter draws one column narrower than the grid on
 * purpose, and measuring it without the Enter would centre its keys across the
 * gap the Enter is standing in.
 */
fun spanRowWidths(rows: List<List<Key>>): FloatArray {
    val widths = FloatArray(rows.size)
    for (r in rows.indices) {
        for (key in rows[r]) widths[r] += key.width
    }
    for (r in rows.indices) {
        for (key in rows[r]) {
            val span = key.spanFrom(r, rows.size)
            for (below in r + 1 until r + span) widths[below] += key.width
        }
    }
    return widths
}

/**
 * The runs of rows that have to be laid out together: a row and every row a key
 * of it reaches into, transitively. Returned in order and covering every row, so
 * a grid with no spans comes back as one single-row range per row and the caller
 * takes its ordinary path for each.
 */
fun spanBands(rows: List<List<Key>>): List<IntRange> {
    if (rows.isEmpty()) return emptyList()
    val reach = IntArray(rows.size) { it }
    for (r in rows.indices) {
        for (key in rows[r]) {
            val last = r + key.spanFrom(r, rows.size) - 1
            if (last > reach[r]) reach[r] = last
        }
    }
    val bands = ArrayList<IntRange>()
    var start = 0
    while (start < rows.size) {
        var end = reach[start]
        var i = start
        while (i <= end) {
            if (reach[i] > end) end = reach[i]
            i++
        }
        bands += start..end
        start = end + 1
    }
    return bands
}

/**
 * Every key of [rows] placed against a [gridWeight]-wide board.
 *
 * Rows are laid out top to bottom so that a span is always known before the rows
 * it covers are placed. Within a row the cursor starts at the centring pad and
 * jumps past any interval held from above; a key that finds no room simply
 * overflows the right edge, which is the same thing an over-wide row has always
 * done rather than a case to reject.
 */
fun spanSlots(rows: List<List<Key>>, gridWeight: Float): List<KeySlot> {
    val widths = spanRowWidths(rows)
    // Held intervals per row, as flat start/end pairs — one small list per row,
    // built once per layout rather than per frame.
    val held = Array(rows.size) { ArrayList<Float>(4) }
    val slots = ArrayList<KeySlot>()
    for (r in rows.indices) {
        // The same centring rule ordinary rows use, and negative for an over-wide
        // row: it starts hard against the left edge instead of off-screen.
        var x = ((gridWeight - widths[r]) / 2f).coerceAtLeast(0f)
        for ((c, key) in rows[r].withIndex()) {
            x = firstFree(held[r], x, key.width)
            val span = key.spanFrom(r, rows.size)
            slots += KeySlot(row = r, col = c, key = key, x = x, span = span)
            for (below in r + 1 until r + span) {
                held[below] += x
                held[below] += x + key.width
            }
            x += key.width
        }
    }
    return slots
}

/**
 * The leftmost position at or after [from] where a key [width] wide clears every
 * interval in [held] (flat start/end pairs).
 *
 * Re-scans after each shift because the intervals are in no particular order and
 * clearing one can push the key into the next. The cursor only ever moves right,
 * so it terminates in at most one pass per interval.
 */
private fun firstFree(held: List<Float>, from: Float, width: Float): Float {
    if (held.isEmpty()) return from
    var x = from
    var moved = true
    while (moved) {
        moved = false
        var i = 0
        while (i < held.size) {
            val start = held[i]
            val end = held[i + 1]
            if (x < end - Epsilon && x + width > start + Epsilon) {
                x = end
                moved = true
            }
            i += 2
        }
    }
    return x
}
