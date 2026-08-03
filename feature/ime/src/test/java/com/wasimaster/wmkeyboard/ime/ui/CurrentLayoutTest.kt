package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyRole
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.LongPressLetterActions
import com.wasimaster.wmkeyboard.ime.FieldKind
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field adaptation used to find the comma and period slots by matching their
 * labels on the last row, which silently skipped any layout arranged
 * differently — an email box in such a layout kept its comma and never got its
 * @ key, with nothing to tell the user why.
 */
class CurrentLayoutTest {

    private fun setOf(spec: com.wasimaster.wmkeyboard.core.layout.LayoutSpec) = LayoutSet(
        spec.compile(LayoutLayer.LETTERS),
        spec.compile(LayoutLayer.SYMBOLS),
        spec.compile(LayoutLayer.SYMBOLS_SHIFTED),
    )

    private fun state(
        spec: com.wasimaster.wmkeyboard.core.layout.LayoutSpec = BuiltInLayouts.QWERTY,
        fieldKind: FieldKind = FieldKind.TEXT,
        settings: KeyboardSettings = KeyboardSettings(),
    ) = KeyboardUiState(
        settings = settings,
        layouts = setOf(spec),
        fieldKind = fieldKind,
    )

    private fun KeyboardLayout.keys() = rows.flatten()

    /**
     * With every adaptation off the grid is handed back as-is rather than
     * rebuilt. The clipboard shortcuts ship off by default; globe-as-emoji and
     * the comma/globe swap ship *on*, so both are switched off to get here.
     */
    @Test
    fun `a plain text field with no adaptations returns the layout untouched`() {
        val s = state(settings = plain())
        assertEquals(s.layouts.letters, currentLayout(s))
    }

    /** Settings with every default-on bottom-row rewrite turned off. */
    private fun plain(): KeyboardSettings =
        KeyboardSettings(globeAsEmoji = false, swapCommaAndGlobe = false)

    /**
     * The swap trades the two keys either side of the spacebar, which is what
     * puts the emoji key (whichever of the two it is) in the outer slot.
     */
    @Test
    fun `the comma and globe keys trade places`() {
        val plain = currentLayout(state(settings = plain())).rows.last()
        val swapped = currentLayout(
            state(settings = KeyboardSettings(globeAsEmoji = false, swapCommaAndGlobe = true)),
        ).rows.last()
        val comma = plain.indexOfFirst { it.role == KeyRole.Comma }
        val globe = plain.indexOfFirst { it.action == KeyAction.LanguageSwitch }
        assertTrue(comma >= 0 && globe >= 0)
        assertEquals(plain[globe], swapped[comma])
        assertEquals(plain[comma], swapped[globe])
        // Everything else stays where it was.
        assertEquals(plain.size, swapped.size)
        for (i in plain.indices) {
            if (i != comma && i != globe) assertEquals(plain[i], swapped[i])
        }
    }

    /** The emoji key follows its slot: swapped, it sits where the globe was. */
    @Test
    fun `the swap carries the emoji key with it`() {
        val row = currentLayout(
            state(settings = KeyboardSettings(globeAsEmoji = true, swapCommaAndGlobe = true)),
        ).rows.last()
        val emoji = row.indexOfFirst { it.action == KeyAction.Emoji }
        val comma = row.indexOfFirst { it.role == KeyRole.Comma }
        assertTrue("no emoji key in $row", emoji >= 0)
        assertTrue("emoji key should lead the comma", emoji < comma)
    }

    /** The clipboard/undo/redo shortcuts ship off, so a plain field doesn't get them. */
    @Test
    fun `the default clipboard shortcuts stay off`() {
        val layout = currentLayout(state())
        assertNull(layout.keys().first { it.label == "a" }.clipboardAction)
        assertNull(layout.keys().first { it.label == "v" }.clipboardAction)
        assertNull(layout.keys().first { it.label == "z" }.clipboardAction)
        assertNull(layout.keys().first { it.label == "y" }.clipboardAction)
    }

    /** Enabling each toggle lands its shortcut on the matching key, including Z/Y. */
    @Test
    fun `enabled clipboard shortcuts land on a c v x z y`() {
        val layout = currentLayout(
            state(
                settings = KeyboardSettings(
                    longPressLetterActions = LongPressLetterActions(
                        selectAll = true,
                        copy = true,
                        paste = true,
                        cut = true,
                        undo = true,
                        redo = true,
                    ),
                ),
            ),
        )
        assertNotNull(layout.keys().first { it.label == "a" }.clipboardAction)
        assertNotNull(layout.keys().first { it.label == "c" }.clipboardAction)
        assertNotNull(layout.keys().first { it.label == "v" }.clipboardAction)
        assertNotNull(layout.keys().first { it.label == "x" }.clipboardAction)
        assertNotNull(layout.keys().first { it.label == "z" }.clipboardAction)
        assertNotNull(layout.keys().first { it.label == "y" }.clipboardAction)
        assertNull(layout.keys().first { it.label == "q" }.clipboardAction)
    }

    @Test
    fun `an email field trades the comma for an at sign`() {
        val layout = currentLayout(state(fieldKind = FieldKind.EMAIL))
        assertTrue("@ should be on the grid", layout.keys().any { it.label == "@" })
        assertTrue(
            "the bottom-row comma should be gone",
            layout.rows.last().none { it.label == "," && it.action == KeyAction.Text },
        )
    }

    @Test
    fun `a uri field puts domain endings on the period key`() {
        val layout = currentLayout(state(fieldKind = FieldKind.URI))
        val period = layout.rows.last().first { it.role == KeyRole.Period }
        assertTrue(period.longPress.contains("https://"))
    }

    /**
     * The regression the roles exist to prevent. This layout's bottom row has no
     * key labelled "," at all — the slot is spelled differently — so the old
     * label match would have found nothing and the email box would have gone
     * without its @ key.
     */
    @Test
    fun `a custom bottom row gets field adaptation through its role tag`() {
        val custom = com.wasimaster.wmkeyboard.core.layout.LayoutSpec(
            id = "custom_1",
            name = "Mine",
            layers = mapOf(
                LayoutLayer.LETTERS.key to com.wasimaster.wmkeyboard.core.layout.LayerSpec(
                    listOf(
                        listOf(Key("a"), Key("b")),
                        listOf(
                            Key("؛", role = KeyRole.Comma),
                            Key(" ", action = KeyAction.Space),
                            Key("۔", role = KeyRole.Period),
                            Key("⏎", action = KeyAction.Enter),
                        ),
                    ),
                ),
            ),
        )
        val layout = currentLayout(state(custom, fieldKind = FieldKind.EMAIL))
        assertTrue(
            "the tagged comma slot should have become the @ key",
            layout.keys().any { it.label == "@" },
        )
    }

    @Test
    fun `an untagged bottom row still adapts through the label fallback`() {
        // A layout written before roles existed: no tags anywhere.
        val legacy = com.wasimaster.wmkeyboard.core.layout.LayoutSpec(
            id = "custom_legacy",
            name = "Legacy",
            layers = mapOf(
                LayoutLayer.LETTERS.key to com.wasimaster.wmkeyboard.core.layout.LayerSpec(
                    listOf(
                        listOf(Key("a"), Key("b")),
                        listOf(Key(","), Key(" ", action = KeyAction.Space), Key(".")),
                    ),
                ),
            ),
        )
        val layout = currentLayout(state(legacy, fieldKind = FieldKind.EMAIL))
        assertTrue(layout.keys().any { it.label == "@" })
    }

    @Test
    fun `dvorak's top-row punctuation is never mistaken for the bottom-row slots`() {
        val layout = currentLayout(state(BuiltInLayouts.DVORAK, fieldKind = FieldKind.EMAIL))
        val topRow = layout.rows.first()
        assertEquals("Dvorak types ' , . from its top row", "'", topRow[0].label)
        assertEquals(",", topRow[1].label)
        assertEquals(".", topRow[2].label)
        assertNull("a top-row comma has no role", topRow[1].roleIn(0, layout.rows.lastIndex))
    }

    @Test
    fun `the clipboard shortcut follows what a key types not what it shows`() {
        val shouting = com.wasimaster.wmkeyboard.core.layout.LayoutSpec(
            id = "custom_shout",
            name = "Shouty",
            layers = mapOf(
                LayoutLayer.LETTERS.key to com.wasimaster.wmkeyboard.core.layout.LayerSpec(
                    listOf(listOf(Key("A", output = "a"), Key("C", output = "c"))),
                ),
            ),
        )
        val layout = currentLayout(
            state(
                shouting,
                settings = KeyboardSettings(
                    longPressLetterActions = LongPressLetterActions(selectAll = true),
                ),
            ),
        )
        assertNotNull(
            "a key labelled A that outputs a should still get select-all",
            layout.keys().first { it.label == "A" }.clipboardAction,
        )
    }

    @Test
    fun `comma-as-emoji keeps the key's own width`() {
        val wide = com.wasimaster.wmkeyboard.core.layout.LayoutSpec(
            id = "custom_wide",
            name = "Wide",
            layers = mapOf(
                LayoutLayer.LETTERS.key to com.wasimaster.wmkeyboard.core.layout.LayerSpec(
                    listOf(
                        listOf(Key("a")),
                        listOf(Key(",", role = KeyRole.Comma, width = 2.5f)),
                    ),
                ),
            ),
        )
        val layout = currentLayout(state(wide, settings = KeyboardSettings(commaAsEmoji = true)))
        val emoji = layout.keys().first { it.action == KeyAction.Emoji }
        assertEquals("rebuilding the key from scratch used to reset its width", 2.5f, emoji.width, 0.001f)
        assertTrue("comma moves to the long-press alternates", emoji.longPress.first() == ",")
    }
}
