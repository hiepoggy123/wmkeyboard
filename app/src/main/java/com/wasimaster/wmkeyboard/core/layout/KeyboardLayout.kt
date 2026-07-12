package com.wasimaster.wmkeyboard.core.layout

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
 * The width every row is laid out against: the first row's total.
 *
 * Rows narrower than this are centred with equal padding on both sides, which
 * is how QWERTY's nine-key home row sits under its ten-key top row. Rows *wider*
 * than it deliberately overflow into narrower keys rather than being scaled —
 * Dvorak's third row is twelve wide against a grid weight of ten and has shipped
 * that way, so taking the maximum over all rows instead would put side padding
 * on its top row and visibly move a layout users already know.
 */
fun gridWeightOf(rows: List<List<Key>>): Float =
    rows.firstOrNull()?.sumOf { it.width.toDouble() }?.toFloat() ?: 0f

/**
 * Half the slack between [gridWeight] and this row's own width, as a layout
 * weight. Negative for an over-wide row; callers drop the spacer below a small
 * epsilon rather than testing for zero, because these are accumulated floats.
 */
fun sidePadFor(row: List<Key>, gridWeight: Float): Float =
    (gridWeight - row.sumOf { it.width.toDouble() }.toFloat()) / 2f
