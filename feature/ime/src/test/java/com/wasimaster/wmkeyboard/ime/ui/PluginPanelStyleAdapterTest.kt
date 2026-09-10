package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.plugins.ui.PluginPanelStyle
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The plugin panel dressed as the keyboard. Every colour the shared renderer
 * draws with must come from the keyboard theme, so no Material default can
 * reach a panel shown inside someone's messaging app.
 */
class PluginPanelStyleAdapterTest {

    @Test
    fun `every colour of the panel style comes from the keyboard theme`() {
        val keyText = Color(0xFF010101)
        val secondaryText = Color(0xFF020202)
        val chip = Color(0xFF030303)
        val chipText = Color(0xFF040404)
        val accent = Color(0xFF050505)
        val kb = defaultKbTheme(lightColorScheme(), dark = false, amoled = false, settings = KeyboardSettings())
            .copy(keyText = keyText, secondaryText = secondaryText, chip = chip, chipText = chipText, accent = accent)
        val style = keyboardPluginStyle(kb)
        assertEquals(keyText, style.text)
        assertEquals(secondaryText, style.secondaryText)
        assertEquals(chip, style.surface)
        assertEquals(chipText, style.onSurface)
        assertEquals(accent, style.accent)
        // A colour added to the style later must be checked above, or this fails.
        val colours = PluginPanelStyle::class.java.declaredFields.filter { it.type == java.lang.Long.TYPE }.map { it.name }.toSet()
        assertEquals(setOf("text", "secondaryText", "surface", "onSurface", "accent"), colours)
    }
}
