package com.wasimaster.wmkeyboard.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRepairTest {

    private fun letters(vararg rows: List<Key>) = LayoutSpec(
        id = "custom_1",
        name = "Test",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(rows.toList())),
    )

    private fun LayoutSpec.lettersKeys() = layer(LayoutLayer.LETTERS)!!.rows.flatten()

    private val usableBottomRow = listOf(
        Key("⇧", action = KeyAction.Shift),
        Key(" ", action = KeyAction.Space),
        Key("⌫", action = KeyAction.Delete),
        Key("⏎", action = KeyAction.Enter),
    )

    /**
     * The test that proves the rules are right rather than merely strict. If a
     * shipped layout trips a blocking rule, the rule is wrong — a user cannot be
     * told that QWERTY is not good enough to turn on.
     */
    @Test
    fun `every built-in validates clean`() {
        for (spec in BuiltInLayouts.all) {
            val blocking = validateLayout(spec).filter { it.severity == LayoutSeverity.BLOCKING }
            assertTrue("${spec.id} should have no blocking findings but had $blocking", blocking.isEmpty())
            assertTrue("${spec.id} should be enableable", spec.canBeEnabled())
        }
    }

    @Test
    fun `every built-in survives repair unchanged`() {
        for (spec in BuiltInLayouts.all) {
            val repaired = spec.repair()
            assertEquals("${spec.id} should need no repairs", emptyList<String>(), repaired.repairs)
        }
    }

    @Test
    fun `a missing delete key blocks and is repaired`() {
        val spec = letters(listOf(Key("a"), Key(" ", action = KeyAction.Space), Key("⏎", action = KeyAction.Enter)))

        assertFalse(spec.canBeEnabled())
        assertTrue(validateLayout(spec).any { it.message.contains("delete", ignoreCase = true) })

        val repaired = spec.repair()
        assertTrue(repaired.spec.lettersKeys().any { it.action == KeyAction.Delete })
        assertTrue(repaired.repairs.any { it.contains("delete") })
        assertTrue(repaired.spec.canBeEnabled())
    }

    @Test
    fun `a missing shift key warns but does not block`() {
        val spec = letters(listOf(Key("a")) + usableBottomRow.filter { it.action != KeyAction.Shift })
        val findings = validateLayout(spec)
        assertTrue(findings.any { it.message.contains("shift", ignoreCase = true) })
        assertTrue("a shift-less layout is still usable", spec.canBeEnabled())
    }

    @Test
    fun `an unknown action blocks and the key is dropped by repair`() {
        val spec = letters(listOf(Key("a"), Key("z", action = KeyAction.Unknown("teleport"))) + usableBottomRow)

        assertFalse(spec.canBeEnabled())

        val repaired = spec.repair()
        assertTrue(repaired.spec.lettersKeys().none { it.action is KeyAction.Unknown })
        assertTrue(repaired.repairs.any { it.contains("teleport") })
        assertEquals("only the offending key goes", 1, repaired.spec.lettersKeys().count { it.label == "a" })
    }

    @Test
    fun `a non-finite or zero width is clamped to one`() {
        val spec = letters(listOf(Key("a", width = 0f), Key("b", width = Float.NaN)) + usableBottomRow)
        val repaired = spec.repair()
        assertEquals(1f, repaired.spec.lettersKeys().first { it.label == "a" }.width, 0.001f)
        assertEquals(1f, repaired.spec.lettersKeys().first { it.label == "b" }.width, 0.001f)
    }

    @Test
    fun `an absurdly wide row is scaled back into the grid`() {
        val fat = List(10) { Key("k$it", width = 20f) }
        val repaired = letters(fat, usableBottomRow).repair()
        val total = repaired.spec.layer(LayoutLayer.LETTERS)!!.rows[0].sumOf { it.width.toDouble() }
        assertTrue("row totalled $total, should be scaled to fit", total <= 40.001)
    }

    @Test
    fun `an empty letters layer is dropped so the built-in grid is inherited`() {
        val spec = letters(emptyList())
        val repaired = spec.repair()
        assertNull("dropping the layer restores the shipped grid", repaired.spec.layer(LayoutLayer.LETTERS))
        assertTrue(repaired.repairs.isNotEmpty())
    }

    @Test
    fun `a symbols layer with no way back gets one`() {
        val spec = LayoutSpec(
            id = "custom_1",
            name = "Test",
            layers = mapOf(
                LayoutLayer.LETTERS.key to LayerSpec(listOf(listOf(Key("a")) + usableBottomRow)),
                LayoutLayer.SYMBOLS.key to LayerSpec(listOf(listOf(Key("!"), Key("?")))),
            ),
        )

        assertFalse("being stranded on the symbols layer blocks", spec.canBeEnabled())

        val repaired = spec.repair()
        val symbols = repaired.spec.layer(LayoutLayer.SYMBOLS)!!.rows.flatten()
        assertTrue(symbols.any { it.action == KeyAction.Letters })
        assertTrue(repaired.spec.canBeEnabled())
    }

    @Test
    fun `a blank text key is dropped`() {
        val spec = letters(listOf(Key("a"), Key("")) + usableBottomRow)
        val repaired = spec.repair()
        assertTrue(repaired.spec.lettersKeys().none { it.label.isEmpty() && it.action == KeyAction.Text })
    }

    @Test
    fun `an empty long-press entry is dropped without losing the key`() {
        val spec = letters(listOf(Key("a", longPress = listOf("@", "", "à"))) + usableBottomRow)
        val key = spec.repair().spec.lettersKeys().first { it.label == "a" }
        assertEquals(listOf("@", "à"), key.longPress)
    }

    @Test
    fun `validate never mutates the layout`() {
        val spec = letters(listOf(Key("a", width = 0f), Key("z", action = KeyAction.Unknown("x"))))
        val before = spec.copy()
        validateLayout(spec)
        assertEquals("validate is pure; the editor calls it on every keystroke", before, spec)
    }
}
