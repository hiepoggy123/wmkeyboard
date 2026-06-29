package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.settings.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout registry replaced a stored [InputMode] with a stored layout id.
 * Nothing is rewritten on disk — the old preference is translated on read — so
 * these pin that an existing install lands exactly where it left off.
 */
class LayoutSelectionTest {

    private fun select(
        layoutId: String? = null,
        inputMode: String? = null,
        enabledIds: String? = null,
        enabledModes: String? = null,
        custom: List<LayoutSpec> = emptyList(),
    ) = resolveLayoutSelection(layoutId, inputMode, enabledIds, enabledModes, custom)

    @Test
    fun `a fresh install gets the shipped defaults`() {
        val s = select()
        assertEquals(BuiltInLayouts.QWERTY_ID, s.active.id)
        assertEquals(InputMode.ENGLISH, s.active.baseMode)
        assertEquals(BuiltInLayouts.defaultEnabledIds, s.enabledLayoutIds)
    }

    /** The migration case: an install typing Probhat before the registry existed. */
    @Test
    fun `a stored input mode translates to its built-in layout`() {
        val s = select(inputMode = "PROBHAT")
        assertEquals(BuiltInLayouts.PROBHAT_ID, s.active.id)
        assertEquals(
            "the derived mode has to match what was stored, or the dictionary changes",
            InputMode.PROBHAT,
            s.active.baseMode,
        )
    }

    @Test
    fun `stored enabled modes translate to their built-in layouts`() {
        val s = select(enabledModes = "ENGLISH,PROBHAT,JATIYA")
        assertEquals(
            listOf(BuiltInLayouts.QWERTY_ID, BuiltInLayouts.PROBHAT_ID, BuiltInLayouts.JATIYA_ID),
            s.enabledLayoutIds,
        )
        assertEquals(
            listOf(InputMode.ENGLISH, InputMode.PROBHAT, InputMode.JATIYA),
            s.enabledModes,
        )
    }

    @Test
    fun `a stored layout id wins over the legacy input mode`() {
        val s = select(layoutId = BuiltInLayouts.DVORAK_ID, inputMode = "PROBHAT")
        assertEquals(BuiltInLayouts.DVORAK_ID, s.active.id)
        assertEquals(InputMode.DVORAK, s.active.baseMode)
    }

    @Test
    fun `an unparseable stored mode falls back to the default`() {
        assertEquals(BuiltInLayouts.QWERTY_ID, select(inputMode = "KLINGON").active.id)
    }

    @Test
    fun `a custom layout supplies its own base mode`() {
        val mine = LayoutSpec(id = "custom_1", name = "My Bengali", langId = "bn")
        val s = select(layoutId = "custom_1", custom = listOf(mine))
        assertEquals("custom_1", s.active.id)
        assertEquals(
            "a custom layout inherits everything language-shaped from its base mode",
            InputMode.PROBHAT,
            s.active.baseMode,
        )
    }

    @Test
    fun `an active id whose layout was deleted heals to the default`() {
        val s = select(layoutId = "custom_gone")
        assertEquals(BuiltInLayouts.DEFAULT_ID, s.active.id)
    }

    /**
     * The reason the cycler works on ids rather than modes: three layouts all
     * based on English are three distinct stops. Cycling modes would collapse
     * them to one and make two of them unreachable from the keyboard.
     */
    @Test
    fun `several custom layouts sharing a base mode stay distinct`() {
        val a = LayoutSpec(id = "custom_a", name = "A", langId = "en")
        val b = LayoutSpec(id = "custom_b", name = "B", langId = "en")
        val s = select(enabledIds = "custom_a,custom_b", custom = listOf(a, b))
        assertEquals(listOf("custom_a", "custom_b"), s.enabledLayoutIds)
        assertEquals("but they collapse to one language", listOf(InputMode.ENGLISH), s.enabledModes)
    }

    @Test
    fun `an empty enabled list never yields an empty mode list`() {
        val s = select(enabledIds = ",,,")
        assertTrue(
            "hintedMode and the FORCE_ASCII fallback have no ifEmpty guard of their own",
            s.enabledModes.isNotEmpty(),
        )
    }

    @Test
    fun `an edited built-in keeps its slot and its id`() {
        val edited = BuiltInLayouts.PROBHAT.copy(name = "My Probhat")
        val s = select(layoutId = BuiltInLayouts.PROBHAT_ID, custom = listOf(edited))
        assertEquals(BuiltInLayouts.PROBHAT_ID, s.active.id)
        assertEquals("My Probhat", s.active.name)
        assertEquals(InputMode.PROBHAT, s.active.baseMode)
    }
}
