package com.wasimaster.wmkeyboard.core.plugins

/** How a [PluginWidget.Label] is drawn. */
enum class PluginLabelStyle { TITLE, BODY, CAPTION }

/**
 * One node of the UI a plugin asked for.
 *
 * This is the entire vocabulary. A plugin describes what it wants using these
 * shapes and the host draws them with the keyboard's own theme, which is what
 * keeps a plugin from styling itself into something that looks like part of the
 * keyboard, or like a system prompt.
 *
 * Immutable and made only of strings, booleans and ints: this crosses from the
 * plugin thread to the UI thread on every render, and nothing that crosses is
 * allowed to be a Lua value or a mutable reference back into the script's world.
 */
sealed interface PluginWidget {

    /** Top-level only. The host owns which page is showing. */
    data class Tabs(val id: String, val pages: List<Page>) : PluginWidget {
        data class Page(val title: String, val children: List<PluginWidget>)
    }

    data class Column(val children: List<PluginWidget>) : PluginWidget

    data class Row(val children: List<PluginWidget>) : PluginWidget

    data class Label(val text: String, val style: PluginLabelStyle) : PluginWidget

    /**
     * A block of result text. [insertable] adds the host's own Insert button,
     * which is the only route from a plugin to the user's text field — there is
     * no API a script can call to type for them.
     */
    data class Output(
        val id: String,
        val text: String,
        val mono: Boolean,
        val insertable: Boolean,
        val copyable: Boolean,
    ) : PluginWidget

    data class Button(
        val id: String,
        val text: String,
        val primary: Boolean,
        val enabled: Boolean,
    ) : PluginWidget

    data class Toggle(val id: String, val label: String, val checked: Boolean) : PluginWidget

    /**
     * A text field the user types into.
     *
     * Carries no value: the buffer belongs to the host, which feeds it from the
     * keyboard's own keystroke routing and passes the current text back to the
     * script as an event. A plugin never sees a keystroke, only the contents of
     * its own box.
     */
    data class Input(val id: String, val label: String, val placeholder: String) : PluginWidget

    data class Spacer(val height: Int) : PluginWidget

    data object Divider : PluginWidget

    data object Progress : PluginWidget
}

/**
 * A whole screen as the plugin asked for it, plus anything that had to be
 * dropped on the way.
 *
 * [repairs] is shown to the user as a caption rather than swallowed — a widget
 * that silently vanishes is a plugin bug the author will never hear about.
 */
data class RenderedUi(
    val root: List<PluginWidget>,
    val repairs: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = RenderedUi(emptyList())
    }
}

/** Everything that can happen to a plugin's UI. All of it user-initiated. */
sealed interface PluginEvent {
    val id: String

    data class Click(override val id: String) : PluginEvent

    data class ToggleChanged(override val id: String, val value: Boolean) : PluginEvent

    data class InputChanged(override val id: String, val value: String) : PluginEvent

    data class TabSelected(override val id: String, val index: Int) : PluginEvent
}

/** The ids of every input widget in this tree, so the host knows which buffers to keep. */
fun RenderedUi.inputIds(): Set<String> {
    val ids = LinkedHashSet<String>()
    fun walk(widgets: List<PluginWidget>) {
        for (widget in widgets) {
            // Exhaustive on purpose: a widget type added later that can hold
            // children must not be able to hide an input from the host, so the
            // compiler is made to ask about it here.
            when (widget) {
                is PluginWidget.Input -> ids.add(widget.id)
                is PluginWidget.Column -> walk(widget.children)
                is PluginWidget.Row -> walk(widget.children)
                is PluginWidget.Tabs -> widget.pages.forEach { walk(it.children) }
                is PluginWidget.Label,
                is PluginWidget.Output,
                is PluginWidget.Button,
                is PluginWidget.Toggle,
                is PluginWidget.Spacer,
                PluginWidget.Divider,
                PluginWidget.Progress,
                -> Unit
            }
        }
    }
    walk(root)
    return ids
}
