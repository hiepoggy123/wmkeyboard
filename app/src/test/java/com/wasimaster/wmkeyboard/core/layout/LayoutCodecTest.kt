package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.settings.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCodecTest {

    private fun spec(vararg rows: List<Key>) = LayoutSpec(
        id = "custom_1",
        name = "Test",
        baseMode = InputMode.ENGLISH,
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(rows.toList())),
    )

    @Test
    fun `round trips a layout`() {
        val original = spec(
            listOf(
                Key("a", longPress = listOf("@", "à")),
                Key("⇧", action = KeyAction.Shift, width = 1.5f),
                Key(".", role = KeyRole.Period),
                Key("x", clipboardAction = ClipboardKeyAction.CUT),
            ),
        )
        assertEquals(original, LayoutCodec.decode(LayoutCodec.encode(original)))
    }

    @Test
    fun `round trips a list`() {
        val list = listOf(spec(listOf(Key("a"))), spec(listOf(Key("b"))).copy(id = "custom_2"))
        assertEquals(list, LayoutCodec.decodeList(LayoutCodec.encodeList(list)))
    }

    /**
     * The load-bearing one. An action tag from a newer build has to cost the
     * user that one key, not the whole file — so this asserts the other two
     * keys survive, not merely that decoding did not throw.
     */
    @Test
    fun `an unknown action tag decodes to Unknown without losing the other keys`() {
        val json = """
            {
              "id": "custom_1",
              "name": "Test",
              "baseMode": "ENGLISH",
              "layers": {
                "letters": {
                  "rows": [[
                    {"label": "a"},
                    {"label": "z", "action": {"type": "teleport", "destination": "mars"}},
                    {"label": "e"}
                  ]]
                }
              }
            }
        """.trimIndent()

        val decoded = LayoutCodec.decode(json)
        assertNotNull("an unknown action must not fail the whole file", decoded)

        val row = decoded!!.layer(LayoutLayer.LETTERS)!!.rows.single()
        assertEquals(3, row.size)
        assertEquals(KeyAction.Text, row[0].action)
        assertEquals(KeyAction.Unknown("teleport"), row[1].action)
        assertEquals(KeyAction.Text, row[2].action)
    }

    @Test
    fun `an unknown layer key is ignored rather than fatal`() {
        val json = """
            {"id":"custom_1","name":"T","layers":{
              "letters":{"rows":[[{"label":"a"}]]},
              "hyperspace":{"rows":[[{"label":"b"}]]}
            }}
        """.trimIndent()
        val decoded = LayoutCodec.decode(json)
        assertNotNull(decoded)
        assertNotNull(decoded!!.layer(LayoutLayer.LETTERS))
        assertTrue("the foreign layer is kept verbatim, not resolvable", "hyperspace" in decoded.layers)
    }

    @Test
    fun `an unknown base mode coerces to the default instead of failing`() {
        val json = """
            {"id":"custom_1","name":"T","baseMode":"KLINGON",
             "layers":{"letters":{"rows":[[{"label":"a"}]]}}}
        """.trimIndent()
        assertEquals(InputMode.ENGLISH, LayoutCodec.decode(json)?.baseMode)
    }

    @Test
    fun `malformed json decodes to null rather than throwing`() {
        assertNull(LayoutCodec.decode("{not json"))
        assertEquals(emptyList<LayoutSpec>(), LayoutCodec.decodeList("{not json"))
    }
}
