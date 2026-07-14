package com.wasimaster.wmkeyboard.core.layout

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * One key of a layout grid.
 *
 * A key either outputs text ([output], falling back to [label]) or triggers an
 * [action]. [longPress] holds the alternate characters shown in the long-press
 * popup, whose first entry doubles as the corner hint. [width] is relative: 1.0
 * is a standard key, the spacebar is wider, shift/delete slightly wider.
 *
 * This is both the stored and the rendered type. A parallel KeySpec DTO was the
 * alternative and was rejected: it would have been field-identical, so the only
 * thing it bought was a mapping function to forget to update. The stored/runtime
 * split that does exist is one level up, at [LayoutSpec], which is where layers
 * inherit and identity lives.
 *
 * Because this is a Compose parameter, everything reachable from it has to stay
 * stable — see the `@Immutable` annotation on [KeyAction].
 */
@Serializable
data class Key(
    val label: String,
    val output: String? = null,
    val shiftLabel: String? = null,
    val action: KeyAction = KeyAction.Text,
    val width: Float = 1f,
    val longPress: List<String> = emptyList(),
    /** Clipboard shortcut fired on long press instead of the alternates popup. */
    val clipboardAction: ClipboardKeyAction? = null,
    /** What this key means to field adaptation; null infers it from position. */
    val role: KeyRole? = null,
)

/**
 * A compiled grid, ready to render. Produced by [LayoutSpec.compile] and never
 * stored — deliberately *not* `@Serializable`, so the type system says which of
 * the two is the wire format.
 */
data class KeyboardLayout(
    val name: String,
    val rows: List<List<Key>>,
    /**
     * Per-row height multipliers, positionally aligned with [rows]; null (the
     * usual case) means every row uses the standard key height. A missing or
     * short entry defaults to 1.0. Carried over verbatim from [LayerSpec].
     */
    val rowHeights: List<Float>? = null,
)

/**
 * The width every row is laid out against: the width the most rows share.
 *
 * Rows narrower than this are centred with equal padding on both sides, which
 * is how QWERTY's nine-key home row sits under its ten-key top row. Rows *wider*
 * than it deliberately overflow into narrower keys rather than being scaled —
 * Dvorak's third row is eleven wide against a grid weight of ten and has shipped
 * that way.
 *
 * Keying off the *most common* width rather than the first row's makes a lone
 * outlier row an outlier and nothing more. The first row was tempting but
 * breaks the moment a narrow row is inserted at the top: a single width-1 key
 * would set the grid weight to 1 and then fill the whole width. The maximum
 * breaks the other way, padding Dvorak's ten-key top rows to match its eleven-
 * key third row. The mode leaves every shipped layout on its historical grid
 * (each has a clear majority of ten-wide rows, Dvorak included) while treating
 * both a narrow top insert and a wide overflow row as the outliers they are.
 *
 * Widths are bucketed on a rounded key so accumulated-float jitter can't split
 * one width into two buckets; ties between equally common widths resolve to the
 * wider one.
 */
fun gridWeightOf(rows: List<List<Key>>): Float {
    if (rows.isEmpty()) return 0f
    val widths = rows.map { row -> row.sumOf { it.width.toDouble() }.toFloat() }
    return widths
        .groupBy { (it * 100f).roundToInt() }
        .entries
        .maxWith(compareBy({ it.value.size }, { it.key }))
        .value.first()
}

/**
 * Half the slack between [gridWeight] and this row's own width, as a layout
 * weight. Negative for an over-wide row; callers drop the spacer below a small
 * epsilon rather than testing for zero, because these are accumulated floats.
 */
fun sidePadFor(row: List<Key>, gridWeight: Float): Float =
    (gridWeight - row.sumOf { it.width.toDouble() }.toFloat()) / 2f
