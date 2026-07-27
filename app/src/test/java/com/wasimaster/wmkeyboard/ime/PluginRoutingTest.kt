package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.plugins.InstalledPlugin
import com.wasimaster.wmkeyboard.core.plugins.PluginWidget
import com.wasimaster.wmkeyboard.core.plugins.RenderedUi
import com.wasimaster.wmkeyboard.core.plugins.inputIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that decides whether a keystroke goes to a plugin or to the user's
 * app.
 *
 * A plugin is not allowed to see what the user types. The single place that
 * could break that promise is this routing flag: while it is on, keys go to a
 * script instead of to the field. So the states in which it may be on are
 * pinned down here, and the ways it must switch off — panel closed, field
 * changed, keyboard hidden, widget gone — each get a test.
 *
 * The service methods that perform those transitions need an Android runtime,
 * so what is asserted here is the state contract they are written against:
 * every one of them ends at a [KeyboardUiState] where routing is off.
 */
class PluginRoutingTest {

    private val plugin = InstalledPlugin(id = "com.example.demo", name = "Demo", version = "1.0.0")

    private fun typing(): KeyboardUiState = KeyboardUiState(
        panel = PanelMode.PLUGINS,
        plugins = PluginPanelUi.Running(plugin),
        pluginInputs = mapOf("msg" to "half a sentence"),
        pluginFocusedInput = "msg",
    )

    // ---- when routing is on ----------------------------------------------

    @Test
    fun `routing is on only with the panel open and a widget focused`() {
        assertTrue(typing().pluginTypingActive)
    }

    @Test
    fun `a focused widget without the panel does not route`() {
        // Belt and braces: the flag alone must never be enough.
        val stale = typing().copy(panel = PanelMode.NONE)
        assertFalse(stale.pluginTypingActive)
    }

    @Test
    fun `the panel alone does not route`() {
        // Browsing the plugin list, or a plugin with nothing focused, types
        // into the user's app exactly as normal.
        assertFalse(typing().copy(pluginFocusedInput = null).pluginTypingActive)
        assertFalse(
            KeyboardUiState(panel = PanelMode.PLUGINS, plugins = PluginPanelUi.List(emptyList()))
                .pluginTypingActive,
        )
    }

    @Test
    fun `no other panel routes to a plugin`() {
        for (panel in PanelMode.entries - PanelMode.PLUGINS) {
            assertFalse(
                "$panel must not route keys to a plugin",
                typing().copy(panel = panel).pluginTypingActive,
            )
        }
    }

    // ---- how routing switches off ----------------------------------------

    @Test
    fun `closing the panel gives the keys back`() {
        // What onPanelChange leaves behind.
        val closed = typing().copy(
            panel = PanelMode.NONE,
            pluginFocusedInput = null,
            pluginInputs = emptyMap(),
            plugins = PluginPanelUi.List(emptyList()),
        )
        assertFalse(closed.pluginTypingActive)
        assertTrue(closed.pluginInputs.isEmpty())
    }

    @Test
    fun `hiding the keyboard or changing field gives the keys back`() {
        // What stopPlugins leaves behind. The panel may still be the last one
        // shown, so clearing the focus is what has to do the work here.
        val stopped = typing().copy(pluginFocusedInput = null, pluginInputs = emptyMap())
        assertFalse(stopped.pluginTypingActive)
    }

    @Test
    fun `text typed into a plugin is never left lying around`() {
        val stopped = typing().copy(pluginFocusedInput = null, pluginInputs = emptyMap())
        assertTrue(stopped.pluginInputs.isEmpty())
    }

    // ---- buffers follow the widgets --------------------------------------

    @Test
    fun `a buffer whose widget is gone is dropped on the next render`() {
        // What the runtime listener does with each new tree: a plugin that
        // stops drawing a box must not keep what was typed into it, and must
        // not keep the keys pointed at something no longer on screen.
        val ui = RenderedUi(listOf(PluginWidget.Input(id = "other", label = "", placeholder = "")))
        val live = ui.inputIds()
        val next = typing().copy(
            pluginInputs = typing().pluginInputs.filterKeys { it in live },
            pluginFocusedInput = typing().pluginFocusedInput?.takeIf { it in live },
        )
        assertFalse(next.pluginTypingActive)
        assertTrue(next.pluginInputs.isEmpty())
    }

    @Test
    fun `a buffer whose widget is still drawn survives`() {
        val ui = RenderedUi(listOf(PluginWidget.Input(id = "msg", label = "", placeholder = "")))
        val live = ui.inputIds()
        val next = typing().copy(
            pluginInputs = typing().pluginInputs.filterKeys { it in live },
            pluginFocusedInput = typing().pluginFocusedInput?.takeIf { it in live },
        )
        assertTrue(next.pluginTypingActive)
        assertEquals("half a sentence", next.pluginInputs["msg"])
    }
}
