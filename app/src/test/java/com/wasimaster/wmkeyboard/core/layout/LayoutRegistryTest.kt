package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRegistryTest {

    @Test
    fun `every built-in defines a letters layer and a known language`() {
        for (spec in BuiltInLayouts.all) {
            assertNotNull("${spec.id} must define a letters layer", spec.layer(LayoutLayer.LETTERS))
            assertNotSame(
                "${spec.id} resolves to an unknown language",
                LanguageRegistry.GENERIC,
                spec.language(),
            )
        }
    }

    @Test
    fun `built-in ids are unique`() {
        val ids = BuiltInLayouts.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `with no customs the registry is exactly the built-ins`() {
        assertEquals(BuiltInLayouts.all, resolveLayouts(emptyList()))
    }

    @Test
    fun `a custom layout reusing a built-in id shadows it in place`() {
        val edited = BuiltInLayouts.QWERTY.copy(name = "My QWERTY")
        val resolved = resolveLayouts(listOf(edited))

        assertEquals("shadowing must not add an entry", BuiltInLayouts.all.size, resolved.size)
        assertEquals(
            "the edit takes the built-in's slot",
            BuiltInLayouts.all.indexOfFirst { it.id == BuiltInLayouts.QWERTY_ID },
            resolved.indexOfFirst { it.id == BuiltInLayouts.QWERTY_ID },
        )
        assertEquals("My QWERTY", resolved.first { it.id == BuiltInLayouts.QWERTY_ID }.name)
    }

    @Test
    fun `deleting the shadow restores the shipped grid`() {
        val edited = BuiltInLayouts.QWERTY.copy(name = "My QWERTY")
        assertEquals("My QWERTY", resolveLayout(listOf(edited), BuiltInLayouts.QWERTY_ID).name)
        assertEquals("QWERTY", resolveLayout(emptyList(), BuiltInLayouts.QWERTY_ID).name)
    }

    @Test
    fun `a genuinely new custom layout is appended after the built-ins`() {
        val mine = LayoutSpec(id = "custom_1", name = "Mine")
        val resolved = resolveLayouts(listOf(mine))
        assertEquals(BuiltInLayouts.all.size + 1, resolved.size)
        assertEquals(mine, resolved.last())
    }

    @Test
    fun `an id that no longer exists falls back to the default rather than crashing`() {
        assertEquals(BuiltInLayouts.default, resolveLayout(emptyList(), "custom_deleted"))
    }

    @Test
    fun `compiling an undefined layer inherits the default's grid`() {
        val lettersOnly = LayoutSpec(
            id = "custom_1",
            name = "Mine",
            layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(listOf(listOf(Key("a"))))),
        )
        assertEquals(
            "an undefined symbols layer inherits the shipped one",
            BuiltInLayouts.default.compile(LayoutLayer.SYMBOLS).rows,
            lettersOnly.compile(LayoutLayer.SYMBOLS).rows,
        )
    }

    @Test
    fun `compiling twice returns the cached instance`() {
        assertSame(
            BuiltInLayouts.PROBHAT.compile(LayoutLayer.LETTERS),
            BuiltInLayouts.PROBHAT.compile(LayoutLayer.LETTERS),
        )
    }

    @Test
    fun `the compile cache notices an edit under the same id`() {
        val id = "custom_cache"
        val first = LayoutSpec(id, "A", layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(listOf(listOf(Key("a"))))))
        val second = LayoutSpec(id, "B", layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(listOf(listOf(Key("b"))))))

        assertEquals("a", first.compile(LayoutLayer.LETTERS).rows[0][0].label)
        assertEquals(
            "an edit under the same id must not serve the stale grid",
            "b",
            second.compile(LayoutLayer.LETTERS).rows[0][0].label,
        )
    }

    @Test
    fun `the default enabled ids all exist`() {
        assertTrue(BuiltInLayouts.defaultEnabledIds.all { BuiltInLayouts.byId(it) != null })
    }
}
