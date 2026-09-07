package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.layout.BuiltInPanelLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.Layouts
import com.wasimaster.wmkeyboard.core.layout.PanelKind
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import com.wasimaster.wmkeyboard.ime.PanelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which theme the board asks for: the open panel's own, else the typing grid's (issues #61, #63). */
class ScreenThemeIdTest {

    private val themedLetters: KeyboardLayout = Layouts.QWERTY.copy(themeId = "custom_layout")
    private val set = LayoutSet(themedLetters, Layouts.SYMBOLS, Layouts.SYMBOLS_SHIFTED)

    @Test
    fun `no panel open reads the typing grid`() {
        assertEquals("custom_layout", screenThemeId(KeyboardUiState(layouts = set)))
        assertNull(screenThemeId(KeyboardUiState()))
    }

    @Test
    fun `an open panel with a theme of its own wins, one without falls back to the grid`() {
        val pad = BuiltInPanelLayouts.default(PanelKind.TEXT_EDIT)
        val themedPad = pad.copy(grid = pad.grid.copy(themeId = "custom_panel"))
        val withTheme = KeyboardUiState(
            layouts = set,
            panel = PanelMode.TEXT_EDIT,
            panelLayouts = mapOf(PanelKind.TEXT_EDIT to themedPad),
        )
        assertEquals("custom_panel", screenThemeId(withTheme))
        val withoutTheme = withTheme.copy(panelLayouts = mapOf(PanelKind.TEXT_EDIT to pad))
        assertEquals("custom_layout", screenThemeId(withoutTheme))
        // A panel that is not a layout has no theme to speak of.
        assertEquals("custom_layout", screenThemeId(withTheme.copy(panel = PanelMode.COMPASS)))
    }
}
