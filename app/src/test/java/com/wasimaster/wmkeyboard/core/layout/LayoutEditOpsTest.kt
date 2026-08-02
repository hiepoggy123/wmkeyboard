package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.language.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid edits the editor performs, exercised against the model rather than
 * through Compose.
 *
 * These pin the copy-on-write rule that makes "a layout replaces everything"
 * survivable: a layout inherits every layer it has not authored, and touching an
 * inherited layer writes this layout's own copy of it — so moving one letter
 * never costs the user a phone pad.
 */
class LayoutEditOpsTest {

    private val layer = LayoutLayer.LETTERS

    /** The editor's copy-on-first-edit, mirrored from KeyLayoutEditorScreen. */
    private fun LayoutSpec.withLayerRows(rows: List<List<Key>>): LayoutSpec {
        val existing = layer(layer) ?: LayerSpec(rows)
        return copy(layers = layers + (layer.key to existing.copy(rows = rows)))
    }

    private fun mine() = LayoutSpec(id = "custom_1", name = "Mine")

    @Test
    fun `a layout with no layers inherits every one of them`() {
        val bare = mine()
        assertNull("nothing authored", bare.layer(LayoutLayer.SYMBOLS))
        assertEquals(
            "but it still compiles to the shipped grid",
            BuiltInLayouts.default.compile(LayoutLayer.SYMBOLS).rows,
            bare.compile(LayoutLayer.SYMBOLS).rows,
        )
    }

    @Test
    fun `editing an inherited layer authors only that layer`() {
        val edited = mine().let { it.withLayerRows(it.compile(layer).rows + listOf(listOf(Key("z")))) }

        assertNotNull("the edited layer is now this layout's own", edited.layer(layer))
        assertNull("the others are untouched", edited.layer(LayoutLayer.PHONE))
        assertEquals(
            "and the phone pad still comes from the built-in",
            BuiltInLayouts.default.compile(LayoutLayer.PHONE).rows,
            edited.compile(LayoutLayer.PHONE).rows,
        )
    }

    @Test
    fun `resetting a layer drops back to the built-in grid`() {
        val edited = mine().withLayerRows(listOf(listOf(Key("only"))))
        val reset = edited.copy(layers = edited.layers - layer.key)

        assertNull(reset.layer(layer))
        assertEquals(
            BuiltInLayouts.default.compile(layer).rows,
            reset.compile(layer).rows,
        )
    }

    @Test
    fun `authoring a layer preserves its number row`() {
        val withNumberRow = mine().copy(
            layers = mapOf(
                layer.key to LayerSpec(
                    rows = listOf(listOf(Key("a"))),
                    numberRow = listOf(Key("1"), Key("2")),
                ),
            ),
        )
        val edited = withNumberRow.withLayerRows(listOf(listOf(Key("b"))))
        assertEquals(
            "editing rows must not silently drop the layer's other fields",
            listOf(Key("1"), Key("2")),
            edited.layer(layer)?.numberRow,
        )
    }

    @Test
    fun `moving a key within its row keeps every key`() {
        val row = listOf(Key("a"), Key("b"), Key("c"))
        val moved = row.toMutableList().apply { add(2, removeAt(0)) }
        assertEquals(listOf(Key("b"), Key("c"), Key("a")), moved)
    }

    @Test
    fun `duplicating a key inserts the copy next to the original`() {
        val row = listOf(Key("a"), Key("b"), Key("c"))
        val col = 1
        val out = row.subList(0, col + 1) + row[col] + row.drop(col + 1)
        assertEquals(listOf(Key("a"), Key("b"), Key("b"), Key("c")), out)
    }

    @Test
    fun `deleting the last key of a row leaves an empty row the renderer tolerates`() {
        val edited = mine().withLayerRows(listOf(emptyList()))
        // Phase 2 taught splitKeys and gridWeightOf to survive this; the editor
        // is allowed to produce it, and validate reports it.
        assertEquals(0f, gridWeightOf(edited.compile(layer).rows), 0.001f)
        // Matched by the resource the finding names, plus the row number and
        // layer it carries, so a reworded warning does not fail this test.
        val findings = validateLayout(edited)
        assertTrue(
            "findings were $findings",
            LayoutMessage(
                R.string.core_lang_layout_empty_row_warning,
                args = listOf(1, layer.key),
            ) in findings.map { it.text },
        )
    }

    @Test
    fun `a half-built layout can be saved but not enabled`() {
        val broken = mine().withLayerRows(listOf(listOf(Key("a"))))
        assertTrue(
            "no delete, enter or space — the editor still has to let it exist",
            validateLayout(broken).any { it.severity == LayoutSeverity.BLOCKING },
        )
        assertTrue("but repair makes it usable at activation", broken.repair().spec.canBeEnabled())
    }

    @Test
    fun `an edited built-in keeps the built-in id so the shadow resolves`() {
        val edited = BuiltInLayouts.QWERTY.withLayerRows(listOf(listOf(Key("z"))))
        assertEquals(BuiltInLayouts.QWERTY_ID, edited.id)
        assertEquals(edited, resolveLayout(listOf(edited), BuiltInLayouts.QWERTY_ID))
    }
}
