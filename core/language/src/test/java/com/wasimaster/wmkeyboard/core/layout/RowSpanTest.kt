package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.language.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry a key covering more than one row is laid out by.
 *
 * The shape under test throughout is the one the feature exists for: a tall key
 * at one edge — ClearFlow's Enter — with the rows beneath it flowing into the
 * width it leaves rather than under it.
 */
class RowSpanTest {

    private fun row(vararg widths: Float) = widths.map { Key("k", width = it) }

    /** A row of [count] plain keys, with the last one [span] rows tall. */
    private fun spanningRow(count: Int, span: Int) =
        List(count - 1) { Key("k") } + Key("⏎", action = KeyAction.Enter, rowSpan = span)

    // ---- widths ----

    @Test
    fun `a row with no spans measures its own keys`() {
        val rows = listOf(row(1f, 1f, 1f), row(1f, 2f))
        assertArrayEquals(floatArrayOf(3f, 3f), spanRowWidths(rows))
    }

    @Test
    fun `a spanning key adds its column to the rows it covers`() {
        // Ten wide, with a two-row Enter at the end of row 1: row 2 has nine keys
        // of its own and the Enter standing in the tenth column.
        val rows = listOf(spanningRow(10, 2), row(*FloatArray(9) { 1f }))
        assertArrayEquals(floatArrayOf(10f, 10f), spanRowWidths(rows))
    }

    @Test
    fun `the grid weight counts the columns a span holds`() {
        // Without the carry, row 2 measures 9 and the grid would be elected as
        // the modal width of {10, 9} — a coin toss that centres row 2 wrongly.
        val rows = listOf(spanningRow(10, 2), row(*FloatArray(9) { 1f }))
        assertEquals(10f, gridWeightOf(rows), 1e-4f)
    }

    @Test
    fun `a span past the last row is clamped rather than counted`() {
        val rows = listOf(spanningRow(3, 5), row(1f, 1f))
        assertEquals(2, rows[0].last().spanFrom(0, rows.size))
        assertArrayEquals(floatArrayOf(3f, 3f), spanRowWidths(rows))
    }

    // ---- bands ----

    @Test
    fun `a grid without spans is one band per row`() {
        val rows = listOf(row(1f, 1f), row(1f, 1f), row(1f, 1f))
        assertFalse(hasRowSpans(rows))
        assertEquals(listOf(0..0, 1..1, 2..2), spanBands(rows))
    }

    @Test
    fun `a two-row key joins its rows into one band`() {
        val rows = listOf(spanningRow(3, 2), row(1f, 1f), row(1f, 1f, 1f))
        assertTrue(hasRowSpans(rows))
        assertEquals(listOf(0..1, 2..2), spanBands(rows))
    }

    @Test
    fun `overlapping spans merge into a single band`() {
        // Row 0 reaches row 1, row 1 reaches row 2: all three are one block, even
        // though no single key covers all of them.
        val rows = listOf(spanningRow(3, 2), spanningRow(3, 2), row(1f, 1f, 1f))
        assertEquals(listOf(0..2), spanBands(rows))
    }

    // ---- placement ----

    @Test
    fun `keys under a span stop at its edge`() {
        val rows = listOf(spanningRow(10, 2), row(*FloatArray(9) { 1f }))
        val slots = spanSlots(rows, gridWeight = 10f)
        val enter = slots.single { it.key.action == KeyAction.Enter }
        assertEquals(9f, enter.x, 1e-4f)
        assertEquals(2, enter.span)
        // The nine keys below fill columns 0..9 and none of them reaches the
        // tenth, which the Enter is standing in.
        val below = slots.filter { it.row == 1 }
        assertEquals(0f, below.first().x, 1e-4f)
        assertEquals(9f, below.last().end, 1e-4f)
    }

    @Test
    fun `a key flows past a span sitting in the middle of the row`() {
        // A two-row key in the middle: the row below starts to its left, jumps
        // the held columns, and carries on to its right.
        val rows = listOf(
            listOf(Key("a"), Key("b", width = 2f, rowSpan = 2), Key("c")),
            row(1f, 1f),
        )
        val slots = spanSlots(rows, gridWeight = 4f)
        val below = slots.filter { it.row == 1 }
        assertEquals(0f, below[0].x, 1e-4f)
        assertEquals(3f, below[1].x, 1e-4f)
    }

    @Test
    fun `a narrow row under a span is still centred on the grid`() {
        // Row 1 has two keys of its own and the two-wide span above it, so it is
        // four wide against a six-wide grid and takes a one-column pad each side.
        val rows = listOf(
            listOf(Key("a"), Key("b"), Key("c"), Key("d"), Key("e", width = 2f, rowSpan = 2)),
            row(1f, 1f),
        )
        assertEquals(6f, gridWeightOf(rows), 1e-4f)
        val slots = spanSlots(rows, gridWeight = 6f)
        assertEquals(1f, slots.first { it.row == 1 }.x, 1e-4f)
    }

    @Test
    fun `an empty grid places nothing`() {
        assertEquals(emptyList<KeySlot>(), spanSlots(emptyList(), gridWeight = 10f))
        assertEquals(emptyList<IntRange>(), spanBands(emptyList()))
    }

    // ---- validation and repair ----

    private fun layerOf(rows: List<List<Key>>) = LayoutSpec(
        id = "test",
        name = "Test",
        langId = "en",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(rows = rows)),
    )

    /** The letters rows a valid layout needs, so a finding is about the span. */
    private fun typeableRows(last: List<Key>) = listOf(
        listOf(Key("a"), Key("⌫", action = KeyAction.Delete)),
        last,
    )

    @Test
    fun `a nonsense span blocks the layout`() {
        val spec = layerOf(
            typeableRows(
                listOf(
                    Key(" ", action = KeyAction.Space),
                    Key("⏎", action = KeyAction.Enter, rowSpan = 0),
                ),
            ),
        )
        assertFalse(spec.canBeEnabled())
    }

    @Test
    fun `repair clamps a nonsense span and says so`() {
        val spec = layerOf(
            typeableRows(
                listOf(
                    Key(" ", action = KeyAction.Space),
                    Key("⏎", action = KeyAction.Enter, rowSpan = 99),
                ),
            ),
        )
        val repaired = spec.repair()
        val rows = repaired.spec.layer(LayoutLayer.LETTERS)!!.rows
        assertEquals(MaxKeySpan, rows.last().last().rowSpan)
        assertTrue(repaired.repairNotes.isNotEmpty())
        assertTrue(repaired.spec.canBeEnabled())
    }

    @Test
    fun `a span reaching past the last row only warns`() {
        val spec = layerOf(
            typeableRows(
                listOf(
                    Key(" ", action = KeyAction.Space),
                    Key("⏎", action = KeyAction.Enter, rowSpan = 3),
                ),
            ),
        )
        assertTrue(spec.canBeEnabled())
        assertTrue(
            validateLayout(spec).any { it.severity == LayoutSeverity.WARNING },
        )
    }

    @Test
    fun `a row filled out by a span is not reported as wide`() {
        // The regression this guards runs the other way from the name: measured
        // without the carry, the row under the Enter is nine wide and the grid is
        // elected from {10, 9, 10} — right by luck here, wrong the moment two
        // rows sit under the span, and the row is then reported against it.
        val spec = layerOf(
            listOf(
                spanningRow(10, 2),
                List(8) { Key("k") } + Key("⌫", action = KeyAction.Delete),
                listOf(Key(" ", action = KeyAction.Space, width = 10f)),
            ),
        )
        assertEquals(10f, gridWeightOf(spec.layer(LayoutLayer.LETTERS)!!.rows), 1e-4f)
        assertTrue(validateLayout(spec).none { it.severity == LayoutSeverity.BLOCKING })
        assertEquals(
            listOf(R.string.core_lang_layout_no_shift_warning),
            validateLayout(spec).map { it.text.stringRes },
        )
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.toList().toString(), actual.toList().toString())
    }
}
