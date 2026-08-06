package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.ModifierKey
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.theme.KeyOverride
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Single-key style overrides through [keyVisual]: the override recolours
 * exactly its own key, derives its pressed shade, and — the invariant the key
 * grid rests on — still varies with nothing a keystroke changes, since the
 * map rides the palette and the palette rides the theme.
 */
class KeyOverrideVisualTest {

    private val red = 0xFFFF0000
    private val white = 0xFFFFFFFF

    private fun palette(overrides: Map<String, KeyOverride> = emptyMap()) = KeyPalette(
        key = Color.White,
        keyText = Color.Black,
        modifierKey = Color.Gray,
        modifierKeyText = Color.DarkGray,
        enterKey = Color.Blue,
        enterKeyText = Color.Yellow,
        pressedKey = Color.LightGray,
        accent = Color.Magenta,
        overrides = overrides,
    )

    private fun state() = KeyboardUiState(
        settings = KeyboardSettings(),
        layouts = LayoutSet(
            BuiltInLayouts.QWERTY.compile(LayoutLayer.LETTERS),
            BuiltInLayouts.QWERTY.compile(LayoutLayer.SYMBOLS),
            BuiltInLayouts.QWERTY.compile(LayoutLayer.SYMBOLS_SHIFTED),
        ),
    )

    @Test
    fun `override recolours its own key and no other`() {
        val palette = palette(mapOf("a" to KeyOverride(background = red, text = white)))
        val a = keyVisual(Key("a"), state(), palette)
        val b = keyVisual(Key("b"), state(), palette)
        assertEquals(Color(red.toInt()), a.background)
        assertEquals(Color(white.toInt()), a.contentColor)
        assertEquals(Color.White, b.background)
        assertEquals(Color.Black, b.contentColor)
    }

    @Test
    fun `an overridden face derives its pressed shade`() {
        val palette = palette(mapOf("a" to KeyOverride(background = red)))
        val a = keyVisual(Key("a"), state(), palette)
        assertEquals(lerp(Color(red.toInt()), a.contentColor, 0.25f), a.pressedBackground)
    }

    @Test
    fun `popup and border colours ride the visual`() {
        val palette = palette(
            mapOf("a" to KeyOverride(border = red, popupBackground = red, popupText = white)),
        )
        val a = keyVisual(Key("a"), state(), palette)
        assertEquals(Color(red.toInt()), a.borderColor)
        assertEquals(Color(red.toInt()), a.popupBackground)
        assertEquals(Color(white.toInt()), a.popupText)
        val plain = keyVisual(Key("b"), state(), palette)
        assertNull(plain.borderColor)
        assertNull(plain.popupBackground)
    }

    @Test
    fun `special keys resolve by action name`() {
        assertEquals("ENTER", keyOverrideId(Key("", action = KeyAction.Enter)))
        assertEquals("SPACE", keyOverrideId(Key("", action = KeyAction.Space)))
        assertEquals("SHIFT", keyOverrideId(Key("", action = KeyAction.Shift)))
        assertEquals(
            "MOD_CTRL",
            keyOverrideId(Key("", action = KeyAction.Mod(ModifierKey.CTRL))),
        )
        assertEquals("a", keyOverrideId(Key("A")))
        assertNull(keyOverrideId(Key("")))
    }

    @Test
    fun `an override keyed by letter is case-insensitive to the label`() {
        val palette = palette(mapOf("a" to KeyOverride(background = red)))
        val upper = keyVisual(Key("A"), state(), palette)
        assertEquals(Color(red.toInt()), upper.background)
    }
}
