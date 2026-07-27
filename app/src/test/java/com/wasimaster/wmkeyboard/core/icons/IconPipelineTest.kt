package com.wasimaster.wmkeyboard.core.icons

import com.wasimaster.wmkeyboard.core.settings.IconSettings
import com.wasimaster.wmkeyboard.ime.ui.buildIconSet
import com.wasimaster.wmkeyboard.ime.ui.toImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The whole chain the keyboard actually walks: an SVG on disk → a parsed
 * document → a Compose vector → the resolved set a slot is looked up in.
 *
 * Worth its own test because each piece passing in isolation says nothing
 * about the precedence rules between them, which is where the user-visible
 * behaviour lives.
 */
class IconPipelineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: IconPackStore

    private val monochrome = """<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4Z"/></svg>"""
    private val coloured = """<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4Z" fill="#ff0000"/></svg>"""

    @Before
    fun setUp() {
        store = IconPackStore(temp.newFolder("iconpacks"))
    }

    @Test
    fun `a parsed svg becomes a sized vector`() {
        val doc = SvgParser.parse("""<svg viewBox="0 0 48 32"><path d="M0 0h48v32H0Z"/></svg>""")!!
        val vector = doc.toImageVector("test")
        assertEquals("test", vector.name)
        // The coordinate space is the file's; the intrinsic size is ours, and
        // never exceeds the 24dp icon box — see SvgVectorsTest.
        assertEquals(48f, vector.viewportWidth, 0f)
        assertEquals(32f, vector.viewportHeight, 0f)
        assertEquals(24f, vector.defaultWidth.value, 0.01f)
        assertEquals(16f, vector.defaultHeight.value, 0.01f)
    }

    @Test
    fun `no pack and no overrides is the stock keyboard`() {
        val set = buildIconSet(IconSettings(), store)
        assertEquals(0, set.size)
        assertNull(set.resolve(IconSlots.KEY_SHIFT))
    }

    @Test
    fun `an active pack supplies its slots and nothing else`() {
        val pack = store.createPack("Rounded")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, monochrome)

        val set = buildIconSet(IconSettings(activePackId = pack.id), store)
        assertNotNull(set.resolve(IconSlots.KEY_SHIFT))
        assertTrue(set.resolve(IconSlots.KEY_SHIFT)!!.monochrome)
        assertNull(set.resolve(IconSlots.KEY_BACKSPACE))
    }

    @Test
    fun `a coloured svg is not tintable`() {
        val pack = store.createPack("Colourful")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, coloured)

        val set = buildIconSet(IconSettings(activePackId = pack.id), store)
        assertFalse(set.resolve(IconSlots.KEY_SHIFT)!!.monochrome)
    }

    @Test
    fun `a per-slot override beats the active pack`() {
        val pack = store.createPack("Rounded")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, coloured)
        val mine = store.mine()!!
        store.setIcon(mine.id, IconSlots.KEY_SHIFT, monochrome)

        val set = buildIconSet(
            IconSettings(
                activePackId = pack.id,
                overrides = mapOf(IconSlots.KEY_SHIFT to IconOverrides.packSource(mine.id)),
            ),
            store,
        )
        // The override's icon is monochrome; the pack's is not.
        assertTrue(set.resolve(IconSlots.KEY_SHIFT)!!.monochrome)
    }

    @Test
    fun `a builtin override resolves and stays tintable`() {
        val set = buildIconSet(
            IconSettings(overrides = mapOf(IconSlots.KEY_SHIFT to IconOverrides.builtinSource("Star"))),
            store,
        )
        assertTrue(set.resolve(IconSlots.KEY_SHIFT)!!.monochrome)
    }

    /**
     * The failure mode that matters: a pack deleted while a slot still points
     * at it must fall back to the default, not draw nothing.
     */
    @Test
    fun `an override naming a missing pack falls back`() {
        val set = buildIconSet(
            IconSettings(overrides = mapOf(IconSlots.KEY_SHIFT to IconOverrides.packSource("gone"))),
            store,
        )
        assertNull(set.resolve(IconSlots.KEY_SHIFT))
    }

    @Test
    fun `an override naming a missing builtin falls back`() {
        val set = buildIconSet(
            IconSettings(overrides = mapOf(IconSlots.KEY_SHIFT to IconOverrides.builtinSource("NoSuchIcon"))),
            store,
        )
        assertNull(set.resolve(IconSlots.KEY_SHIFT))
    }

    /**
     * A dangling override must not *unset* what the active pack provides for
     * some other slot — only its own.
     */
    @Test
    fun `a dangling override only affects its own slot`() {
        val pack = store.createPack("Rounded")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, monochrome)
        store.setIcon(pack.id, IconSlots.KEY_BACKSPACE, monochrome)

        val set = buildIconSet(
            IconSettings(
                activePackId = pack.id,
                overrides = mapOf(IconSlots.KEY_SHIFT to IconOverrides.packSource("gone")),
            ),
            store,
        )
        assertNull(set.resolve(IconSlots.KEY_SHIFT))
        assertNotNull(set.resolve(IconSlots.KEY_BACKSPACE))
    }

    // ---- store behaviour ----

    @Test
    fun `setIcon refuses an unknown slot and an unparseable svg`() {
        val pack = store.createPack("Rounded")!!
        assertFalse(store.setIcon(pack.id, "tool.timetravel", monochrome))
        assertFalse(store.setIcon(pack.id, IconSlots.KEY_SHIFT, "not xml"))
        assertTrue(store.pack(pack.id)!!.slots.isEmpty())
    }

    @Test
    fun `removing an icon drops its file and its slot`() {
        val pack = store.createPack("Rounded")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, monochrome)
        assertTrue(store.fileFor(pack.id, IconSlots.KEY_SHIFT)!!.isFile)

        store.removeIcon(pack.id, IconSlots.KEY_SHIFT)
        assertTrue(store.pack(pack.id)!!.slots.isEmpty())
        assertFalse(store.fileFor(pack.id, IconSlots.KEY_SHIFT)!!.exists())
    }

    @Test
    fun `the parse cache refreshes when an icon changes`() {
        val pack = store.createPack("Rounded")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, monochrome)
        assertTrue(store.docs(pack.id).getValue(IconSlots.KEY_SHIFT).monochrome)

        store.setIcon(pack.id, IconSlots.KEY_SHIFT, coloured)
        assertFalse(store.docs(pack.id).getValue(IconSlots.KEY_SHIFT).monochrome)
    }

    @Test
    fun `a slot whose file vanished is dropped on reload`() {
        val pack = store.createPack("Rounded")!!
        store.setIcon(pack.id, IconSlots.KEY_SHIFT, monochrome)
        store.fileFor(pack.id, IconSlots.KEY_SHIFT)!!.delete()

        store.reload()
        assertTrue(store.pack(pack.id)!!.slots.isEmpty())
    }

    @Test
    fun `mine is stable and created once`() {
        val first = store.mine()!!
        assertEquals(IconPackStore.MINE_ID, first.id)
        assertEquals(first.id, store.mine()!!.id)
        assertEquals(1, store.packs().count { it.id == IconPackStore.MINE_ID })
        assertTrue(first.isMine)
    }
}
