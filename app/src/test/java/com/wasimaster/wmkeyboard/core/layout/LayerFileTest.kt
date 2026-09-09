package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.language.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copy and paste of one layer, the clipboard document behind issue #105. */
class LayerFileTest {

    private val symbols = LayerSpec(
        rows = listOf(
            listOf(Key("1"), Key("2")),
            listOf(
                Key("ABC", action = KeyAction.Letters),
                Key(" ", action = KeyAction.Space),
                Key("⌫", action = KeyAction.Delete),
            ),
        ),
        rowHeights = listOf(1f, 1.25f),
        fontScale = 0.8f,
        persistent = true,
        themeId = "midnight",
    )

    private fun encode(spec: LayerSpec, key: String = LayoutLayer.SYMBOLS.key) =
        LayerFile.encode(key, spec, appVersion = 12, appVersionName = "1.2")

    @Test
    fun `a layer round trips through the clipboard document`() {
        val copied = LayerFile.decode(encode(symbols))
        assertNotNull(copied)
        assertEquals(symbols, copied!!.spec)
        assertEquals(LayoutLayer.SYMBOLS.key, copied.layerKey)
        assertEquals(12, copied.fromAppVersion)
    }

    /**
     * The reason the document has an envelope at all. The clipboard is the one
     * input where an unrelated document is the normal case, and every
     * [LayerSpec] field but the rows has a default.
     */
    @Test
    fun `an unrelated clipping is rejected rather than half-read`() {
        assertNull(LayerFile.decode("""{"shopping":["milk","eggs"]}"""))
        assertNull(LayerFile.decode("{}"))
        assertNull(LayerFile.decode("a sentence someone copied"))
        assertNull(LayerFile.decode(""))
    }

    @Test
    fun `a whole exported layout is not mistaken for one layer`() {
        val layout = LayoutSpec(
            id = "custom_1",
            name = "Mine",
            layers = mapOf(LayoutLayer.SYMBOLS.key to symbols),
        )
        assertNull(LayerFile.decode(LayoutFile.encode(layout, 12, "1.2")))
    }

    @Test
    fun `a copied layer with no keys left is dropped rather than pasted`() {
        val empty = LayerSpec(rows = listOf(listOf(Key(""))))
        val repaired = empty.repairAsLayer(LayoutLayer.SYMBOLS.key)
        assertNull(repaired.spec)
        assertTrue(repaired.repairNotes.isNotEmpty())
    }

    /**
     * A grid copied off another device can name an action this build does not
     * have. The key goes, the rest of the layer arrives, and the paste says so.
     */
    @Test
    fun `an unknown action from a newer build is dropped and reported`() {
        val text = """
            {
              "format": "wmkeyboard-layer",
              "version": 1,
              "layer": "symbols",
              "spec": {
                "rows": [
                  [
                    {"label": "1"},
                    {"label": "2", "action": {"type": "teleport", "to": "mars"}}
                  ]
                ]
              }
            }
        """.trimIndent()
        val copied = LayerFile.decode(text)
        assertNotNull("one strange key must not cost the whole layer", copied)
        val repaired = copied!!.spec.repairAsLayer(LayoutLayer.SYMBOLS.key)
        assertNotNull(repaired.spec)
        assertTrue(
            "repair notes were ${repaired.repairNotes}",
            LayoutMessage(
                R.string.core_lang_repair_unknown_key_deleted,
                args = listOf(LayoutLayer.SYMBOLS.key, "teleport"),
            ) in repaired.repairNotes,
        )
        assertEquals(listOf(listOf(Key("1"))), repaired.spec!!.rows)
    }

    /**
     * Repairing one layer must not mint the keys the whole-layout pass adds.
     * A layer pasted into the letters is edited in front of its author, who
     * gets the missing keys reported under Problems instead.
     */
    @Test
    fun `pasting into the letters does not add a space or enter key`() {
        val bare = LayerSpec(rows = listOf(listOf(Key("a"), Key("b"))))
        val repaired = bare.repairAsLayer(LayoutLayer.LETTERS.key)
        assertEquals(bare.rows, repaired.spec?.rows)
        assertEquals(emptyList<LayoutMessage>(), repaired.repairNotes)
    }

    /** The layer a paste lands in decides the rules, not the one it came from. */
    @Test
    fun `a cycled layer keeps its own way back rather than gaining one`() {
        val copied = LayerFile.decode(encode(symbols, LayoutLayer.DATE.key))
        assertNotNull(copied)
        val repaired = copied!!.spec.repairAsLayer(LayoutLayer.TIME.key)
        assertEquals(symbols.rows, repaired.spec?.rows)
        assertEquals(LayoutLayer.DATE.key, copied.layerKey)
    }
}
