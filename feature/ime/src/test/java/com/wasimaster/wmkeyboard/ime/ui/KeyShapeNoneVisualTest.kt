package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.ModifierKey
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.settings.AccessibilitySettings
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.theme.KeyOverride
import com.wasimaster.wmkeyboard.core.theme.KeyShapeKind
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import com.wasimaster.wmkeyboard.ime.ModifierState
import com.wasimaster.wmkeyboard.ime.Modifiers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A theme whose key shape is none (issue #149): a key at rest draws no face and
 * its label sits on the board, while everything that carries state or a colour
 * of its own still lights up.
 */
class KeyShapeNoneVisualTest {

    private val red = 0xFFFF0000

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
        keysHaveFaces = false,
    )

    private fun state() = KeyboardUiState(
        settings = KeyboardSettings(),
        layouts = LayoutSet(
            BuiltInLayouts.QWERTY.compile(LayoutLayer.LETTERS),
            BuiltInLayouts.QWERTY.compile(LayoutLayer.SYMBOLS),
            BuiltInLayouts.QWERTY.compile(LayoutLayer.SYMBOLS_SHIFTED),
        ),
    )

    private val enter = Key("", action = KeyAction.Enter)

    @Test
    fun `keys at rest draw no face, and a press still lights them`() {
        for (key in listOf(Key("a"), Key("⇧", action = KeyAction.Shift), enter)) {
            val visual = keyVisual(key, state(), palette())
            assertEquals(Color.Transparent, visual.background)
            assertEquals(Color.LightGray, visual.pressedBackground)
        }
    }

    /**
     * The enter label colour is picked to read on the enter key's accent face.
     * On a dark board with no face under it, a dark icon picked for a light
     * blue key would vanish.
     */
    @Test
    fun `enter takes the modifier label colour once it has no face`() {
        assertEquals(Color.DarkGray, keyVisual(enter, state(), palette()).contentColor)
        assertEquals(Color.Black, keyVisual(Key("a"), state(), palette()).contentColor)
    }

    @Test
    fun `a latched modifier still lights its face`() {
        val ctrl = Key("ctrl", action = KeyAction.Mod(ModifierKey.CTRL))
        val locked = state().copy(modifiers = Modifiers(ctrl = ModifierState.LOCKED))
        val armed = state().copy(modifiers = Modifiers(ctrl = ModifierState.ARMED))
        assertEquals(Color.Magenta, keyVisual(ctrl, locked, palette()).background)
        assertEquals(Color.LightGray, keyVisual(ctrl, armed, palette()).background)
        assertEquals(Color.Transparent, keyVisual(ctrl, state(), palette()).background)
    }

    @Test
    fun `a key the theme coloured on its own keeps its face`() {
        val palette = palette(mapOf("ENTER" to KeyOverride(background = red)))
        val visual = keyVisual(enter, state(), palette)
        assertEquals(Color(red.toInt()), visual.background)
        // Back on a face, so back to the colour picked for the enter key.
        assertEquals(Color.Yellow, visual.contentColor)
    }

    @Test
    fun `high contrast and key outlines bring rounded keys back`() {
        val bare = defaultKbTheme(
            scheme = darkColorScheme(),
            dark = true,
            amoled = false,
            settings = KeyboardSettings(),
        ).copy(keyShapeKind = KeyShapeKind.NONE)
        fun shapeUnder(accessibility: AccessibilitySettings) =
            bare.accessibilityAdjusted(KeyboardSettings(accessibility = accessibility)).keyShapeKind
        assertEquals(KeyShapeKind.NONE, shapeUnder(AccessibilitySettings()))
        assertEquals(KeyShapeKind.ROUNDED, shapeUnder(AccessibilitySettings(highContrast = true)))
        assertEquals(KeyShapeKind.ROUNDED, shapeUnder(AccessibilitySettings(keyOutlines = true)))
    }
}
