package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.plugins.InstalledPlugin
import com.wasimaster.wmkeyboard.core.plugins.PluginEvent
import com.wasimaster.wmkeyboard.core.plugins.PluginLabelStyle
import com.wasimaster.wmkeyboard.core.plugins.PluginWidget
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.PluginPanelUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * The Plugins panel: the installed list, and whichever plugin is running.
 *
 * Everything a plugin draws is drawn *here*, by the keyboard, from a plain data
 * description the script handed back. The script never gets a Compose handle, a
 * colour, or a pixel — which is why a plugin cannot make itself look like the
 * keyboard's own UI, cannot draw outside its panel, and cannot cover a system
 * prompt. The header above it always says which plugin is running.
 *
 * Text inputs are the delicate part. There are no `TextField`s anywhere in the
 * IME (they fight the `InputConnection`), so a plugin's input is a tap target
 * that, once focused, makes the keyboard route keystrokes into
 * [KeyboardUiState.pluginInputs] instead of the user's app. The service owns
 * that switch and clears it whenever the panel closes.
 */
@Composable
internal fun PluginPanel(
    state: KeyboardUiState,
    onOpenPlugin: (String) -> Unit,
    onEvent: (PluginEvent) -> Unit,
    onInputFocus: (String?) -> Unit,
    onToolInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: (String) -> Unit,
    onManage: () -> Unit,
) {
    when (val panel = state.plugins) {
        is PluginPanelUi.List -> PluginList(panel, onOpenPlugin, onManage)
        is PluginPanelUi.Running -> RunningPlugin(
            state = state,
            panel = panel,
            onEvent = onEvent,
            onInputFocus = onInputFocus,
            onToolInsert = onToolInsert,
            onCopy = onCopy,
            onPaste = onPaste,
        )
    }
}

@Composable
private fun PluginList(
    panel: PluginPanelUi.List,
    onOpenPlugin: (String) -> Unit,
    onManage: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        panel.notice?.let { notice ->
            Text(
                notice,
                color = kb.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        if (panel.plugins.isEmpty()) {
            // Only when nothing else has already explained the empty list. With
            // the subsystem off, or every plugin switched off, the notice above
            // says why — and "No plugins yet" beside it is simply untrue.
            if (panel.notice == null) {
                Text(
                    "No plugins yet",
                    color = kb.keyText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Plugins are small sandboxed tools you can add from an addon repository " +
                        "or a .wmplugin file. They can't see what you type.",
                    color = kb.secondaryText,
                    fontSize = 12.sp,
                )
            }
            ToolPanelChip(label = "Manage plugins", onClick = onManage)
        } else {
            for (plugin in panel.plugins) {
                PluginRow(plugin) { onOpenPlugin(plugin.id) }
            }
            ToolPanelChip(label = "Manage plugins", onClick = onManage)
        }
    }
}

@Composable
private fun PluginRow(plugin: InstalledPlugin, onClick: () -> Unit) {
    val kb = LocalKbTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(kb.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            plugin.name,
            color = kb.keyText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = plugin.description.ifBlank { "Version ${plugin.version}" }
        Text(
            subtitle,
            color = kb.secondaryText,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RunningPlugin(
    state: KeyboardUiState,
    panel: PluginPanelUi.Running,
    onEvent: (PluginEvent) -> Unit,
    onInputFocus: (String?) -> Unit,
    onToolInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (panel.busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = kb.accent,
                trackColor = kb.chip,
            )
        }
        panel.error?.let { message ->
            Text(
                message,
                color = kb.secondaryText,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        for (line in panel.ui.repairs) {
            Text(
                line,
                color = kb.secondaryText,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WidgetList(
                widgets = panel.ui.root,
                state = state,
                onEvent = onEvent,
                onInputFocus = onInputFocus,
                onToolInsert = onToolInsert,
                onCopy = onCopy,
                onPaste = onPaste,
            )
        }
    }
}

@Composable
private fun WidgetList(
    widgets: List<PluginWidget>,
    state: KeyboardUiState,
    onEvent: (PluginEvent) -> Unit,
    onInputFocus: (String?) -> Unit,
    onToolInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: (String) -> Unit,
) {
    for (widget in widgets) {
        PluginWidgetView(widget, state, onEvent, onInputFocus, onToolInsert, onCopy, onPaste)
    }
}

@Composable
private fun PluginWidgetView(
    widget: PluginWidget,
    state: KeyboardUiState,
    onEvent: (PluginEvent) -> Unit,
    onInputFocus: (String?) -> Unit,
    onToolInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    when (widget) {
        is PluginWidget.Column -> Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            WidgetList(widget.children, state, onEvent, onInputFocus, onToolInsert, onCopy, onPaste)
        }

        is PluginWidget.Row -> Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (child in widget.children) {
                Box(modifier = Modifier.weight(1f)) {
                    PluginWidgetView(
                        child, state, onEvent, onInputFocus, onToolInsert, onCopy, onPaste,
                    )
                }
            }
        }

        is PluginWidget.Label -> Text(
            widget.text,
            color = if (widget.style == PluginLabelStyle.CAPTION) kb.secondaryText else kb.keyText,
            fontSize = when (widget.style) {
                PluginLabelStyle.TITLE -> 15.sp
                PluginLabelStyle.BODY -> 13.sp
                PluginLabelStyle.CAPTION -> 11.sp
            },
            fontWeight = if (widget.style == PluginLabelStyle.TITLE) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
        )

        is PluginWidget.Output -> OutputWidget(widget, onToolInsert, onCopy)

        is PluginWidget.Button -> ToolPanelChip(
            label = widget.text,
            selected = widget.primary,
            enabled = widget.enabled,
            onClick = { onEvent(PluginEvent.Click(widget.id)) },
        )

        is PluginWidget.Toggle -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                widget.label,
                color = kb.keyText,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = widget.checked,
                onCheckedChange = { onEvent(PluginEvent.ToggleChanged(widget.id, it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = kb.accent),
            )
        }

        is PluginWidget.Input -> InputWidget(
            widget = widget,
            value = state.pluginInputs[widget.id].orEmpty(),
            focused = state.pluginFocusedInput == widget.id,
            onFocus = { onInputFocus(widget.id) },
            onPaste = { onPaste(widget.id) },
        )

        is PluginWidget.Spacer -> Spacer(modifier = Modifier.height(widget.height.dp))

        PluginWidget.Divider -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(kb.chip),
        )

        PluginWidget.Progress -> LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = kb.accent,
            trackColor = kb.chip,
        )

        is PluginWidget.Tabs -> TabsWidget(
            widget, state, onEvent, onInputFocus, onToolInsert, onCopy, onPaste,
        )
    }
}

/**
 * A block of result text, with the host's own Insert and Copy buttons.
 *
 * That Insert button is the *only* way a plugin's output reaches the text the
 * user is writing. There is no API a script can call to type for them, so every
 * character a plugin contributes is one the user tapped to accept.
 */
@Composable
private fun OutputWidget(
    widget: PluginWidget.Output,
    onToolInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(kb.chip)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            widget.text,
            color = kb.keyText,
            fontSize = 13.sp,
            fontFamily = if (widget.mono) FontFamily.Monospace else FontFamily.Default,
        )
        if (widget.text.isNotEmpty() && (widget.insertable || widget.copyable)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (widget.insertable) {
                    ToolPanelChip(label = "Insert", onClick = { onToolInsert(widget.text) })
                }
                if (widget.copyable) {
                    ToolPanelChip(label = "Copy", onClick = { onCopy(widget.text) })
                }
            }
        }
    }
}

/**
 * A plugin's text box.
 *
 * Not a `TextField`: tapping it tells the service to route the keys here, and
 * the text drawn is whatever the service has collected. The Paste button is how
 * the user hands a plugin something they already had — the replacement for the
 * clipboard and text-reading APIs the sandbox deliberately does not have.
 */
@Composable
private fun InputWidget(
    widget: PluginWidget.Input,
    value: String,
    focused: Boolean,
    onFocus: () -> Unit,
    onPaste: () -> Unit,
) {
    val kb = LocalKbTheme.current
    // Focusing a box collapses the panel to make room for the key rows, so the
    // box the user just tapped can end up under them. Scroll it back into view
    // rather than leaving them typing into something they cannot see.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        // A frame's grace: the panel is still resizing when the focus lands.
        delay(80)
        try {
            requester.bringIntoView()
        } catch (e: CancellationException) {
            // The panel closed mid-scroll; cancellation belongs to the caller.
            throw e
        } catch (_: Exception) {
            // The node went away — nothing left to bring into view.
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (widget.label.isNotEmpty()) {
            Text(widget.label, color = kb.secondaryText, fontSize = 11.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(kb.chip)
                    .then(
                        if (focused) {
                            Modifier.border(1.dp, kb.accent, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClick = onFocus)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    value.ifEmpty { widget.placeholder },
                    color = if (value.isEmpty()) kb.secondaryText else kb.keyText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ToolPanelChip(label = "Paste", onClick = onPaste)
        }
    }
}

@Composable
private fun TabsWidget(
    widget: PluginWidget.Tabs,
    state: KeyboardUiState,
    onEvent: (PluginEvent) -> Unit,
    onInputFocus: (String?) -> Unit,
    onToolInsert: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: (String) -> Unit,
) {
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
                ToolPanelChip(
                    label = page.title,
                    selected = i == index,
                    onClick = {
                        selected = i
                        onEvent(PluginEvent.TabSelected(widget.id, i))
                    },
                )
            }
        }
        WidgetList(
            widget.pages[index].children,
            state, onEvent, onInputFocus, onToolInsert, onCopy, onPaste,
        )
    }
}
