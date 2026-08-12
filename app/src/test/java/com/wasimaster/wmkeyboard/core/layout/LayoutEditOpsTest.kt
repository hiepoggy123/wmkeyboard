package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.app.KeyActionCatalog
import com.wasimaster.wmkeyboard.language.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * The editor's "what does an edit to this layer start from", mirrored from
     * KeyLayoutEditorScreen exactly as [withLayerRows] is.
     */
    private fun LayoutSpec.baseLayerOf(which: LayoutLayer): LayerSpec {
        layer(which)?.let { return it }
        if (BuiltInLayouts.default.layer(which) == null) return LayerSpec(rows = emptyList())
        val compiled = compile(which)
        return LayerSpec(rows = compiled.rows, rowHeights = compiled.rowHeights)
    }

    @Test
    fun `compile falls through to the letters grid for a layer nothing ships`() {
        // The fact the editor has to work around: Fn is the one layer no built-in
        // defines, so compile runs off the end of its fallback chain. Anything
        // that treats compile's answer as "the grid to edit" authors Fn as a
        // second copy of the alphabet.
        assertNull(BuiltInLayouts.default.layer(LayoutLayer.FN))
        assertEquals(
            BuiltInLayouts.default.compile(LayoutLayer.LETTERS).rows,
            mine().compile(LayoutLayer.FN).rows,
        )
    }

    @Test
    fun `an edit to the Fn layer starts from nothing, not from the letters`() {
        val base = mine().baseLayerOf(LayoutLayer.FN)
        assertEquals("Fn has no shipped grid to inherit", emptyList<List<Key>>(), base.rows)
    }

    @Test
    fun `an edit to an inherited shipped layer still starts from that layer`() {
        val base = mine().baseLayerOf(LayoutLayer.PHONE)
        assertEquals(
            BuiltInLayouts.default.compile(LayoutLayer.PHONE).rows,
            base.rows,
        )
    }

    @Test
    fun `an authored Fn layer is edited from itself`() {
        val withFn = mine().copy(
            layers = mapOf(LayoutLayer.FN.key to BuiltInLayouts.FN_DEFAULT),
        )
        assertEquals(BuiltInLayouts.FN_DEFAULT.rows, withFn.baseLayerOf(LayoutLayer.FN).rows)
    }

    @Test
    fun `the editing encoding drops defaults and still round-trips`() {
        val full = LayoutCodec.encode(BuiltInLayouts.QWERTY)
        val lean = LayoutCodec.encodeForEditing(BuiltInLayouts.QWERTY)

        assertTrue(
            "the raw-JSON screen is read by a person; ${lean.length} vs ${full.length}",
            lean.length < full.length / 2,
        )
        assertTrue("no null-valued field survives", !lean.contains(":null"))
        assertEquals(
            "and what it prints still decodes to the same layout",
            BuiltInLayouts.QWERTY,
            LayoutCodec.decode(lean),
        )
    }

    @Test
    fun `every action a key can carry draws something when its label is blank`() {
        // A key with no label and no glyph is a button the user cannot see. The
        // action picker never asks for a label, so every action it offers has to
        // answer this — including the payload-carrying ones.
        val actions = listOf(
            KeyAction.Symbols, KeyAction.Letters, KeyAction.Fn, KeyAction.Numpad,
            KeyAction.KanaVariant, KeyAction.MorseDot, KeyAction.MorseDash,
            KeyAction.Mod(ModifierKey.CTRL), KeyAction.Mod(ModifierKey.ALT),
            KeyAction.Mod(ModifierKey.META),
            KeyAction.SendKey(61), KeyAction.SendKey(111), KeyAction.SendKey(19),
            KeyAction.SendKey(20), KeyAction.SendKey(21), KeyAction.SendKey(22),
            KeyAction.SendKey(999), KeyAction.BrailleDot(3), KeyAction.Broadcast("x"),
        )
        for (action in actions) {
            assertTrue("$action draws nothing", action.fallbackLabel().isNotEmpty())
        }
    }

    @Test
    fun `the actions drawn from an icon slot claim no text fallback`() {
        // These never reach the text branch — the keyboard answers them with an
        // icon first — and handing them a glyph too would be a second spelling of
        // the same key that only the editor would ever show.
        val drawnAsIcons = listOf(
            KeyAction.Shift, KeyAction.Delete, KeyAction.ForwardDelete,
            KeyAction.Enter, KeyAction.LanguageSwitch, KeyAction.InputMethodPicker,
            KeyAction.Emoji, KeyAction.Text, KeyAction.None,
        )
        for (action in drawnAsIcons) {
            assertEquals("$action", "", action.fallbackLabel())
        }
    }

    @Test
    fun `the action picker never shows one group twice`() {
        // The picker prints a header whenever the group changes as it walks the
        // catalogue, so a catalogue that leaves a group and comes back to it
        // splits that group across two headings. Fn used to sit after the
        // modifiers, giving two "Layers" sections.
        val order = KeyActionCatalog.map { it.groupRes }
        assertEquals(
            "each group must be one contiguous run",
            order.distinct().size,
            order.zipWithNext().count { (a, b) -> a != b } + 1,
        )
    }

    @Test
    fun `a layout with a blocking finding is refused by the enable gate`() {
        // Mirrors rememberLayoutEnableGate: the editor has always said "you must
        // fix this before you turn this layout on", and nothing enforced it.
        fun blockers(spec: LayoutSpec) =
            validateLayout(spec).filter { it.severity == LayoutSeverity.BLOCKING }

        val broken = mine().withLayerRows(listOf(listOf(Key("a"))))
        assertTrue("no delete, enter or space", blockers(broken).isNotEmpty())
        assertFalse(broken.canBeEnabled())

        assertTrue("a shipped layout still passes", blockers(BuiltInLayouts.QWERTY).isEmpty())
        // A warning is not a refusal: a layout with no shift key still enables.
        val noShift = BuiltInLayouts.QWERTY.withLayerRows(
            BuiltInLayouts.QWERTY.compile(layer).rows
                .map { row -> row.filter { it.action != KeyAction.Shift } },
        )
        assertTrue(validateLayout(noShift).any { it.severity == LayoutSeverity.WARNING })
        assertTrue("but it is not blocked", blockers(noShift).isEmpty())
    }

    @Test
    fun `a layout edited after it was turned on is repaired where it is used`() {
        // What the keyboard does to a spec before drawing it (WMKeyboardService's
        // resolveLayoutSet). The editor saves whatever you are mid-way through,
        // and an enabled layout is live while you build it — so deleting the row
        // that carries ⌫ must not cost the running keyboard its backspace.
        val enabledAndBroken = BuiltInLayouts.QWERTY.withLayerRows(
            BuiltInLayouts.QWERTY.compile(layer).rows
                .map { row -> row.filter { it.action != KeyAction.Delete } },
        )
        assertTrue(
            "the stored layout is allowed to be broken",
            validateLayout(enabledAndBroken).any { it.severity == LayoutSeverity.BLOCKING },
        )

        val drawn = enabledAndBroken.repair().spec
        assertTrue(
            "but the grid the keyboard draws always has one",
            drawn.compile(layer).rows.flatten().any { it.action == KeyAction.Delete },
        )
        assertEquals(
            "and repairing an already-good layout changes nothing",
            drawn.repair().spec,
            drawn,
        )
    }
}
