package com.wasimaster.wmkeyboard.core.plugins.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.plugins.PluginEvent
import com.wasimaster.wmkeyboard.core.plugins.PluginLabelStyle
import com.wasimaster.wmkeyboard.core.plugins.PluginWidget
import com.wasimaster.wmkeyboard.core.util.runCancellable
import com.wasimaster.wmkeyboard.plugins.R
import kotlinx.coroutines.delay

/**
 * How a plugin's widgets look, supplied by whichever screen draws them.
 *
 * The keyboard panel and the plugin editor's preview draw one plugin with one
 * renderer, so what an author sees in the preview is what a user will see in
 * the keyboard. What differs between the two is only this: the keyboard takes
 * its colours, shapes, chips and focus ring from the keyboard theme, and the
 * editor from Material.
 *
 * The chip is a slot rather than a pair of colours on purpose. The keyboard's
 * chip carries its own shape, outline and disabled dimming rule, and rebuilding
 * that from colours here is exactly how the two would start to look different.
 */
@Stable
class PluginPanelStyle(
    val text: Color,
    val secondaryText: Color,
    /** The fill behind cards, inputs and dividers. */
    val surface: Color,
    /** Text drawn on [surface]. */
    val onSurface: Color,
    val accent: Color,
    val cardShape: Shape,
    /** The outline an output card or an unfocused input wears. Returns the modifier unchanged for none. */
    val cardBorder: (Modifier, Shape) -> Modifier,
    /** The hardware focus ring. Returns the modifier unchanged where the host has no ring. */
    val ring: @Composable (Modifier, Boolean) -> Modifier,
    /** A pill button: its label, whether it is selected, whether it is enabled, its modifier, and its click. */
    val chip: @Composable (String, Boolean, Boolean, Modifier, () -> Unit) -> Unit,
)

/** The style [PluginWidgetList] draws with. A host must provide one. */
val LocalPluginPanelStyle = staticCompositionLocalOf<PluginPanelStyle> {
    error("No PluginPanelStyle: wrap PluginWidgetList in CompositionLocalProvider(LocalPluginPanelStyle provides ...)")
}

/**
 * Where a plugin's input widgets get their text, and where their taps go.
 *
 * [onValueChange] is the one real difference between the hosts, and it is in the
 * type rather than behind a flag. The keyboard's is null: there is no text field
 * anywhere in the IME, because one fights the input connection, so a plugin's
 * box is a tap target and the keyboard routes keystrokes into it. The editor's
 * preview has no keyboard to route, so it passes one and gets a real field.
 */
@Stable
interface PluginInputHost {
    fun value(id: String): String

    fun isFocused(id: String): Boolean

    fun onFocus(id: String)

    fun onPaste(id: String)

    val onValueChange: ((id: String, text: String) -> Unit)?
}

/**
 * Draws [widgets] in order. [ringed] is the widget the hardware focus ring is on,
 * compared by identity. [onInsert] is the only route from a plugin's output into
 * the text the user is writing, and it is a button the host draws.
 */
@Composable
fun PluginWidgetList(
    widgets: List<PluginWidget>,
    host: PluginInputHost,
    onEvent: (PluginEvent) -> Unit,
    onInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    ringed: PluginWidget? = null,
) {
    for (widget in widgets) {
        PluginWidgetView(widget, host, onEvent, onInsert, onCopy, ringed)
    }
}

@Composable
private fun PluginWidgetView(
    widget: PluginWidget,
    host: PluginInputHost,
    onEvent: (PluginEvent) -> Unit,
    onInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    ringed: PluginWidget?,
) {
    val style = LocalPluginPanelStyle.current
    when (widget) {
        is PluginWidget.Column -> Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PluginWidgetList(widget.children, host, onEvent, onInsert, onCopy, ringed)
        }

        is PluginWidget.Row -> Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (child in widget.children) {
                Box(modifier = Modifier.weight(1f)) {
                    PluginWidgetView(child, host, onEvent, onInsert, onCopy, ringed)
                }
            }
        }

        is PluginWidget.Label -> Text(
            widget.text,
            color = if (widget.style == PluginLabelStyle.CAPTION) style.secondaryText else style.text,
            fontSize = when (widget.style) {
                PluginLabelStyle.TITLE -> 15.sp
                PluginLabelStyle.BODY -> 13.sp
                PluginLabelStyle.CAPTION -> 11.sp
            },
            fontWeight = if (widget.style == PluginLabelStyle.TITLE) FontWeight.Medium else FontWeight.Normal,
        )

        is PluginWidget.Output -> OutputWidget(widget, onInsert, onCopy)

        is PluginWidget.Button -> style.chip(
            widget.text,
            widget.primary,
            widget.enabled,
            style.ring(Modifier, widget === ringed),
        ) { onEvent(PluginEvent.Click(widget.id)) }

        is PluginWidget.Toggle -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = style.ring(Modifier.fillMaxWidth(), widget === ringed),
        ) {
            Text(
                widget.label,
                color = style.text,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = widget.checked,
                onCheckedChange = { onEvent(PluginEvent.ToggleChanged(widget.id, it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = style.accent),
            )
        }

        is PluginWidget.Input -> Box(style.ring(Modifier, widget === ringed)) {
            InputWidget(widget, host)
        }

        is PluginWidget.Spacer -> Spacer(modifier = Modifier.height(widget.height.dp))

        PluginWidget.Divider -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(style.surface),
        )

        PluginWidget.Progress -> LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = style.accent,
            trackColor = style.surface,
        )

        is PluginWidget.Tabs -> TabsWidget(widget, host, onEvent, onInsert, onCopy)
    }
}

/**
 * A block of result text, with the host's own Insert and Copy buttons.
 *
 * That Insert button is the *only* way a plugin's output reaches the text the
 * user is writing. There is no API a script can call to type for them, so every
 * character a plugin contributes is one the user pressed a button to accept.
 */
@Composable
private fun OutputWidget(
    widget: PluginWidget.Output,
    onInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val style = LocalPluginPanelStyle.current
    val shape = style.cardShape
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.surface)
            .then(style.cardBorder(Modifier, shape))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            widget.text,
            color = style.onSurface,
            fontSize = 13.sp,
            fontFamily = if (widget.mono) FontFamily.Monospace else FontFamily.Default,
        )
        if (widget.text.isNotEmpty() && (widget.insertable || widget.copyable)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (widget.insertable) {
                    style.chip(stringResource(R.string.core_plugins_ui_insert_action), false, true, Modifier) {
                        onInsert(widget.text)
                    }
                }
                if (widget.copyable) {
                    style.chip(stringResource(CommonR.string.common_copy), false, true, Modifier) {
                        onCopy(widget.text)
                    }
                }
            }
        }
    }
}

/**
 * A plugin's text box.
 *
 * In the keyboard, not a text field: tapping it tells the service to route the
 * keys here, and the text drawn is whatever the service has collected. In the
 * editor's preview, a real field. The Paste button is in both, and is how the
 * user hands a plugin something they already had, in place of the clipboard and
 * text-reading APIs the sandbox deliberately does not have.
 */
@Composable
private fun InputWidget(widget: PluginWidget.Input, host: PluginInputHost) {
    val style = LocalPluginPanelStyle.current
    val value = host.value(widget.id)
    val focused = host.isFocused(widget.id)
    val edit = host.onValueChange
    // Focusing a box collapses the keyboard panel to make room for the key rows,
    // so the box the user just pressed can end up under them. Scroll it back
    // into view rather than leaving them typing into something they cannot see.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        // A frame's grace: the panel is still resizing when the focus lands.
        delay(80)
        // Not runCatching: the panel closing cancels this effect, and swallowing
        // that would leave the coroutine running. See runCancellable's KDoc.
        runCancellable { requester.bringIntoView() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (widget.label.isNotEmpty()) {
            Text(widget.label, color = style.secondaryText, fontSize = 11.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (edit != null) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { edit(widget.id, it) },
                    placeholder = { Text(widget.placeholder, fontSize = 13.sp) },
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val fieldShape = style.cardShape
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(fieldShape)
                        .background(style.surface)
                        .then(
                            if (focused) {
                                // Focus wins over the theme's outline: the accent
                                // ring is what says "keys go here now".
                                Modifier.border(1.dp, style.accent, fieldShape)
                            } else {
                                style.cardBorder(Modifier, fieldShape)
                            },
                        )
                        .clickable { host.onFocus(widget.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        value.ifEmpty { widget.placeholder },
                        color = if (value.isEmpty()) style.secondaryText else style.onSurface,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            style.chip(stringResource(CommonR.string.common_paste), false, true, Modifier) { host.onPaste(widget.id) }
        }
    }
}

@Composable
private fun TabsWidget(
    widget: PluginWidget.Tabs,
    host: PluginInputHost,
    onEvent: (PluginEvent) -> Unit,
    onInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val style = LocalPluginPanelStyle.current
    // Which tab is showing is the host's business, kept in the panel's own
    // saved state so it survives a recomposition without the script having to
    // track it or the service having to store it.
    var selected by rememberSaveable(widget.id) { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, widget.pages.lastIndex)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            widget.pages.forEachIndexed { i, page ->
                val title = page.title.ifEmpty { stringResource(R.string.core_plugins_ui_tab_default_title, i + 1) }
                style.chip(title, i == index, true, Modifier) {
                    selected = i
                    onEvent(PluginEvent.TabSelected(widget.id, i))
                }
            }
        }
        PluginWidgetList(widget.pages[index].children, host, onEvent, onInsert, onCopy)
    }
}
