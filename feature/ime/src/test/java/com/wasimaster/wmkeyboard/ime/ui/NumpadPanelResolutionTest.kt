package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.layout.BuiltInPanelLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.LayerSpec
import com.wasimaster.wmkeyboard.core.layout.Layouts
import com.wasimaster.wmkeyboard.core.layout.PanelKind
import com.wasimaster.wmkeyboard.core.layout.PanelLayoutSpec
import com.wasimaster.wmkeyboard.core.layout.resolvePanelLayouts
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Numpad tool's pad has four sources, in a fixed order: the typing
 * layout's own Numpad tab, the shared Numpad layout when edited, the layout's
 * authored Number layer (issue #55), and the shipped pad in the digit order
 * the tool's setting asks for.
 */
class NumpadPanelResolutionTest {

    private val base = LayoutSet(Layouts.QWERTY, Layouts.SYMBOLS, Layouts.SYMBOLS_SHIFTED)
    private fun firstLabel(spec: PanelLayoutSpec) = spec.grid.rows.first().first().label

    @Test
    fun `the shipped pad follows the calculator setting`() {
        val dialer = KeyboardUiState(layouts = base, panelLayouts = resolvePanelLayouts(emptyList()))
        assertEquals("1", firstLabel(dialer.panelLayout(PanelKind.NUMPAD)))
        val calculator = dialer.copy(settings = KeyboardSettings(numpadCalculatorLayout = true))
        assertEquals("7", firstLabel(calculator.panelLayout(PanelKind.NUMPAD)))
    }

    @Test
    fun `an authored Number layer beats the shipped pad and the setting`() {
        val number = KeyboardLayout(name = "n", rows = listOf(listOf(Key("9"))))
        val state = KeyboardUiState(
            layouts = base.copy(number = number),
            settings = KeyboardSettings(numpadCalculatorLayout = true),
            panelLayouts = resolvePanelLayouts(emptyList()),
        )
        assertEquals("9", firstLabel(state.panelLayout(PanelKind.NUMPAD)))
    }

    @Test
    fun `a shared pad the user edited beats the Number layer, and the layout's own tab beats both`() {
        val number = KeyboardLayout(name = "n", rows = listOf(listOf(Key("9"))))
        val shared = PanelLayoutSpec(PanelKind.NUMPAD, LayerSpec(listOf(listOf(Key("S")))))
        val state = KeyboardUiState(
            layouts = base.copy(number = number),
            panelLayouts = resolvePanelLayouts(listOf(shared)),
        )
        assertEquals("S", firstLabel(state.panelLayout(PanelKind.NUMPAD)))
        val own = PanelLayoutSpec(PanelKind.NUMPAD, LayerSpec(listOf(listOf(Key("L")))))
        val withOwn = state.copy(layouts = state.layouts.copy(panels = mapOf(PanelKind.NUMPAD to own)))
        assertEquals("L", firstLabel(withOwn.panelLayout(PanelKind.NUMPAD)))
        // An unedited shared map carries the shipped pad, which must not
        // outrank the Number layer.
        assertEquals("shipped", BuiltInPanelLayouts.NUMPAD, resolvePanelLayouts(emptyList()).getValue(PanelKind.NUMPAD))
    }
}
