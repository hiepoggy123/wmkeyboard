package com.wasimaster.wmkeyboard.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCodecTest {

    private fun spec(vararg rows: List<Key>) = LayoutSpec(
        id = "custom_1",
        name = "Test",
        langId = "en",
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

    /**
     * A file written before `tabletExpand` existed has to mean "yes". Every one
     * of the 1,256 shipped assets and every stored custom layout is such a file, so
     * the other default would silently opt the whole corpus out of the tablet
     * grid at once, with nothing to show for it but a keyboard that never widens.
     */
    @Test
    fun `a layout written before tabletExpand existed opts in`() {
        val old = """
            {"id":"custom_old","name":"Old","langId":"en","version":2,
             "layers":{"letters":{"rows":[[{"label":"a"}]]}}}
        """.trimIndent()
        val decoded = LayoutCodec.decode(old)
        assertNotNull(decoded)
        assertTrue("an old file must default to opted in", decoded!!.tabletExpand)
    }

    /**
     * …and adding the field must not have bumped the format version, which would
     * rewrite the effective version of every asset and custom layout on read for
     * no migration.
     */
    @Test
    fun `adding tabletExpand did not bump the format version`() {
        assertEquals(2, CurrentLayoutSpecVersion)
        val old = """
            {"id":"custom_old","name":"Old","langId":"en","version":2,
             "layers":{"letters":{"rows":[[{"label":"a"}]]}}}
        """.trimIndent()
        assertEquals(2, LayoutCodec.decode(old)!!.version)
    }

    @Test
    fun `round trips an opted-out layout`() {
        val original = spec(listOf(Key("a"))).copy(tabletExpand = false)
        assertEquals(original, LayoutCodec.decode(LayoutCodec.encode(original)))
    }

    @Test
    fun `round trips a layout's own font and label sizes`() {
        val original = spec(
            listOf(Key("a"), Key("Send", labelScale = 0.7f)),
        ).copy(appearance = LayoutAppearance(fontId = "google:Roboto Mono", fontScale = 0.85f))
        assertEquals(original, LayoutCodec.decode(LayoutCodec.encode(original)))
    }

    /**
     * A file written before the layout could carry type of its own — which is
     * every asset and every stored custom layout — has to read as "says
     * nothing", not as a layout that has asked for the defaults. Only the first
     * one keeps following the theme and the settings.
     */
    @Test
    fun `a layout written before appearance existed says nothing`() {
        val old = """
            {"id":"custom_old","name":"Old","langId":"en","version":2,
             "layers":{"letters":{"rows":[[{"label":"a"}]]}}}
        """.trimIndent()
        val decoded = checkNotNull(LayoutCodec.decode(old))
        assertNull(decoded.appearance)
        assertNull(decoded.layer(LayoutLayer.LETTERS)!!.rows[0][0].labelScale)
        // And it did not bump the format version, for the reason above.
        assertEquals(2, decoded.version)
    }

    @Test
    fun `an out-of-range size is clamped at draw time, not stored`() {
        // Both of these come off a file, so neither can be trusted; both are
        // pulled back to the range the renderer honours without the stored
        // layout being rewritten under the user.
        assertEquals(2f, LayoutAppearance(fontScale = 9f).drawnFontScale(), 0f)
        assertEquals(0.5f, LayoutAppearance(fontScale = 0f).drawnFontScale(), 0f)
        assertEquals(1f, (null as LayoutAppearance?).drawnFontScale(), 0f)
        assertEquals(1f, LayoutAppearance(fontScale = Float.NaN).drawnFontScale(), 0f)
        assertEquals(2f, checkNotNull(Key("a", labelScale = 40f).drawnLabelScale()), 0f)
        assertNull(Key("a").drawnLabelScale())
    }

    @Test
    fun `round trips flick keys and the kana-variant action`() {
        val original = spec(
            listOf(
                Key(
                    "あ",
                    flick = mapOf(
                        FlickDirection.LEFT to "い",
                        FlickDirection.UP to "う",
                        FlickDirection.RIGHT to "え",
                        FlickDirection.DOWN to "お",
                    ),
                ),
                Key("小゛゜", action = KeyAction.KanaVariant),
            ),
        )
        assertEquals(original, LayoutCodec.decode(LayoutCodec.encode(original)))
    }

    /**
     * A key that hides its corner hint keeps its alternates: the two are separate
     * fields on purpose, since clearing `longPress` is what an author does *not*
     * want here. Also pins the default, because a file written before the field
     * existed has to keep drawing its hints.
     */
    @Test
    fun `round trips a hint-hiding key without touching its alternates`() {
        val original = spec(listOf(Key("a", longPress = listOf("@", "à"), hideHint = true)))
        val decoded = LayoutCodec.decode(LayoutCodec.encode(original))
        assertEquals(original, decoded)
        val key = decoded!!.layers.getValue(LayoutLayer.LETTERS.key).rows[0][0]
        assertEquals(listOf("@", "à"), key.longPress)

        val old = """
            {"id":"custom_old","name":"Old","langId":"en","version":2,
             "layers":{"letters":{"rows":[[{"label":"a","longPress":["@"]}]]}}}
        """.trimIndent()
        val oldKey = LayoutCodec.decode(old)!!.layers.getValue("letters").rows[0][0]
        assertEquals(false, oldKey.hideHint)
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
    fun `an unknown legacy base mode migrates to the default language`() {
        val json = """
            {"id":"custom_1","name":"T","baseMode":"KLINGON",
             "layers":{"letters":{"rows":[[{"label":"a"}]]}}}
        """.trimIndent()
        assertEquals("en", LayoutCodec.decode(json)?.langId)
    }

    /**
     * The registry migration: a layout written before langId existed stored an
     * `InputMode` name in `baseMode`. Decoding must translate it to a langId and
     * carry Avro's transliteration onto the composer, or an upgrade silently
     * turns a Bengali phonetic layout into English.
     */
    @Test
    fun `a pre-registry baseMode migrates to langId and composer`() {
        val avro = """
            {"id":"custom_1","name":"Mine","baseMode":"AVRO","version":1,
             "layers":{"letters":{"rows":[[{"label":"a"}]]}}}
        """.trimIndent()
        val decoded = LayoutCodec.decode(avro)!!
        assertEquals("bn", decoded.langId)
        assertEquals(com.wasimaster.wmkeyboard.core.script.ComposerType.TRANSLITERATE, decoded.composer)
        assertNull("the legacy field is cleared once migrated", decoded.legacyBaseMode)
        assertEquals(CurrentLayoutSpecVersion, decoded.version)

        val probhat = avro.replace("AVRO", "PROBHAT")
        val fixed = LayoutCodec.decode(probhat)!!
        assertEquals("bn", fixed.langId)
        assertNull("fixed Bengali keeps the script-default composer", fixed.composer)
    }

    @Test
    fun `malformed json decodes to null rather than throwing`() {
        assertNull(LayoutCodec.decode("{not json"))
        assertEquals(emptyList<LayoutSpec>(), LayoutCodec.decodeList("{not json"))
    }
}
