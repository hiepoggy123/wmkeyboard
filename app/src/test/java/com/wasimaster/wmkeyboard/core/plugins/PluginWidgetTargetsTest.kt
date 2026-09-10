package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RenderedUi.targets] feeds the plugin editor's event injector. */
class PluginWidgetTargetsTest {

    private val go = PluginWidget.Button("go", "Go", primary = true, enabled = true)
    private val stop = PluginWidget.Button("stop", "Stop", primary = false, enabled = false)
    private val wrap = PluginWidget.Toggle("wrap", "Wrap", checked = false)
    private val text = PluginWidget.Input("text", "Text", "")
    private val deep = PluginWidget.Input("deep", "Deep", "")

    private val tabs = PluginWidget.Tabs(
        "main",
        listOf(
            PluginWidget.Tabs.Page(
                "One",
                listOf(PluginWidget.Column(listOf(go, PluginWidget.Row(listOf(wrap, text))))),
            ),
            PluginWidget.Tabs.Page("Two", listOf(stop, PluginWidget.Column(listOf(deep)))),
        ),
    )

    private val tree = RenderedUi(
        listOf(PluginWidget.Label("Hi", PluginLabelStyle.TITLE), tabs, PluginWidget.Divider),
    )

    @Test
    fun `targets descend columns, rows and tab pages in document order`() {
        val targets = tree.targets()
        assertEquals(listOf(go, stop), targets.buttons)
        assertEquals(listOf(wrap), targets.toggles)
        assertEquals(listOf(text, deep), targets.inputs)
        assertEquals(listOf(tabs), targets.tabs)
    }

    @Test
    fun `a disabled button is still offered`() {
        assertTrue(stop in tree.targets().buttons)
    }

    @Test
    fun `targets and inputIds agree about inputs`() {
        assertEquals(tree.inputIds(), tree.targets().inputs.map { it.id }.toSet())
    }

    @Test
    fun `a tree with nothing to act on has no targets`() {
        val inert = RenderedUi(
            listOf(
                PluginWidget.Label("Hi", PluginLabelStyle.BODY),
                PluginWidget.Output("out", "text", mono = false, insertable = true, copyable = true),
                PluginWidget.Spacer(8),
                PluginWidget.Divider,
                PluginWidget.Progress,
            ),
        )
        assertTrue(inert.targets().isEmpty)
        assertEquals(PluginTargets.EMPTY, RenderedUi.EMPTY.targets())
    }
}
