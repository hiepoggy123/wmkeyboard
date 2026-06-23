package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.LayoutSeverity
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.layout.gridWeightOf
import com.wasimaster.wmkeyboard.core.layout.resolveLayouts
import com.wasimaster.wmkeyboard.core.layout.sidePadFor
import com.wasimaster.wmkeyboard.core.layout.validateLayout
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.ime.ui.KbTheme
import com.wasimaster.wmkeyboard.ime.ui.KeyboardThemeProvider
import com.wasimaster.wmkeyboard.ime.ui.LocalKbTheme
import com.wasimaster.wmkeyboard.ime.ui.keyShape
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Gallery
// ---------------------------------------------------------------------------

/**
 * Every layout the user has: the shipped ones, then their own.
 *
 * A list rather than the two-per-row card grid the themes gallery uses. A
 * theme's whole identity is a colour swatch and reads fine at 150dp; a layout's
 * identity is its key arrangement, and a ten-column grid in a half-width card
 * gives about 17dp per key. A full-width row with a one-line shape summary
 * carries more than a shrunken grid would.
 */
@Composable
internal fun KeyLayoutsScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<LayoutSpec?>(null) }

    val layouts = resolveLayouts(settings.customLayouts)
    val customIds = settings.customLayouts.map { it.id }.toSet()

    CaptionText(
        "A layout is a key grid. It types the language of the layout it is based on, " +
            "so a rearranged Bengali grid still uses the Bengali dictionary.",
    )

    /**
     * Copies a layout and opens the copy.
     *
     * Deliberately does not activate it, unlike the themes gallery, which
     * applies a duplicate before navigating. A layout owns delete, enter and
     * space, and a half-built copy becoming the live keyboard mid-edit is how
     * someone ends up unable to type well enough to undo it. Custom layouts go
     * live only from their toggle under Languages, and only once they validate.
     */
    fun duplicateAndEdit(base: LayoutSpec) {
        scope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            repository.upsertCustomLayout(base.copy(id = id, name = "${base.name} copy"))
            onNavigate("keymap_edit/$id")
        }
    }

    SettingsGroup("Your layouts") {
        val customs = layouts.filter { it.id in customIds && BuiltInLayouts.byId(it.id) == null }
        if (customs.isEmpty()) {
            item {
                ListItem(
                    colors = transparentListColors(),
                    headlineContent = { Text("No layouts of your own yet") },
                    supportingContent = {
                        Text("Copy one below to start from a grid that already works.")
                    },
                )
            }
        }
        for (layout in customs) {
            item {
                LayoutRow(
                    layout = layout,
                    enabled = layout.id in settings.enabledLayoutIds,
                    onEdit = { onNavigate("keymap_edit/${layout.id}") },
                    onDuplicate = { duplicateAndEdit(layout) },
                    onDelete = { confirmDelete = layout },
                    deleteIsReset = false,
                )
            }
        }
    }

    SettingsGroup("Built in") {
        for (layout in layouts.filter { BuiltInLayouts.byId(it.id) != null }) {
            item {
                LayoutRow(
                    layout = layout,
                    enabled = layout.id in settings.enabledLayoutIds,
                    onEdit = { onNavigate("keymap_edit/${layout.id}") },
                    onDuplicate = { duplicateAndEdit(layout) },
                    // An edited built-in is stored as an override under the same
                    // id, so removing it restores the shipped grid rather than
                    // deleting anything — hence Reset, not Delete.
                    onDelete = if (layout.id in customIds) {
                        { confirmDelete = layout }
                    } else {
                        null
                    },
                    deleteIsReset = true,
                )
            }
        }
    }

    confirmDelete?.let { layout ->
        val reset = BuiltInLayouts.byId(layout.id) != null
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (reset) "Reset ${layout.name}?" else "Delete ${layout.name}?") },
            text = {
                Text(
                    if (reset) {
                        "Your changes to this built-in layout are discarded and the " +
                            "original grid comes back. Nothing else changes."
                    } else {
                        "This layout is removed. If it is switched on under Languages " +
                            "it is switched off, and the keyboard falls back to QWERTY."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteCustomLayout(layout.id) }
                    confirmDelete = null
                }) { Text(if (reset) "Reset" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LayoutRow(
    layout: LayoutSpec,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteIsReset: Boolean,
) {
    ListItem(
        colors = transparentListColors(),
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = { Text(layout.name) },
        supportingContent = { Text(layoutSummary(layout, enabled)) },
        trailingContent = {
            Row {
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate ${layout.name}")
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            if (deleteIsReset) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                            contentDescription =
                                if (deleteIsReset) "Reset ${layout.name}" else "Delete ${layout.name}",
                        )
                    }
                }
            }
        },
    )
}

/** One line describing a layout: its language, its shape, and whether it is on. */
internal fun layoutSummary(layout: LayoutSpec, enabled: Boolean): String {
    val letters = layout.compile(LayoutLayer.LETTERS).rows
    val shape = "${letters.size} rows · ${letters.sumOf { it.size }} keys"
    val extras = layout.layers.keys.count { it != LayoutLayer.LETTERS.key }
    val layers = if (extras > 0) " · $extras custom layer${if (extras == 1) "" else "s"}" else ""
    val state = if (enabled) "On" else "Off"
    return "$state · ${baseModeTitle(layout.baseMode)} · $shape$layers"
}

/**
 * The catalog's name for a mode, so the subtitle tracks a renamed catalog entry
 * rather than duplicating it.
 */
internal fun baseModeTitle(mode: InputMode): String {
    for (language in LanguageCatalog) {
        val option = language.layouts.firstOrNull {
            BuiltInLayouts.byId(it.layoutId)?.baseMode == mode
        } ?: continue
        return "${language.name} · ${option.title}"
    }
    return mode.name
}

// ---------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------

/** Row and column address of one key in the layer being edited. */
internal data class KeyRef(val row: Int, val col: Int)

@Composable
internal fun KeyLayoutEditorScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    layoutId: String,
    onNavigate: (String) -> Unit,
) {
    val layout = resolveLayouts(settings.customLayouts).firstOrNull { it.id == layoutId }
    if (layout == null) {
        Text(
            "This layout no longer exists.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var layer by rememberSaveable(layoutId) { mutableStateOf(LayoutLayer.LETTERS) }
    var selection by remember(layoutId, layer) { mutableStateOf<KeyRef?>(null) }
    var showShift by rememberSaveable(layoutId) { mutableStateOf(false) }

    SectionHeaderPublic(layout.name)

    LayerChips(layout, layer) { layer = it; selection = null }

    if (layout.layer(layer) == null) {
        CaptionText(
            "Showing the built-in ${layerTitle(layer).lowercase()} grid. This layout " +
                "does not change it yet.",
        )
    }

    EditorGrid(
        layout = layout.compile(layer),
        settings = settings,
        selection = selection,
        showShift = showShift,
        onSelect = { selection = if (selection == it) null else it },
    )

    SettingsGroup {
        item {
            ToggleSetting(
                "Show the shift plane",
                "Draw each key as it appears with shift held",
                showShift,
            ) { showShift = it }
        }
    }

    val findings = validateLayout(layout)
    if (findings.isNotEmpty()) {
        SettingsGroup("Problems") {
            for (finding in findings) {
                item {
                    ListItem(
                        colors = transparentListColors(),
                        headlineContent = { Text(finding.message) },
                        supportingContent = {
                            Text(
                                if (finding.severity == LayoutSeverity.BLOCKING) {
                                    "Has to be fixed before this layout can be switched on."
                                } else {
                                    "Worth a look, but the layout still works."
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    CaptionText(
        "Editing keys lands next. Nothing you change here affects typing until the " +
            "layout is switched on under Languages.",
    )
}

/** Layer tabs. The pencil marks a layer this layout has actually authored. */
@Composable
private fun LayerChips(
    layout: LayoutSpec,
    selected: LayoutLayer,
    onSelect: (LayoutLayer) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(LayoutLayer.entries) { layer ->
            FilterChip(
                selected = layer == selected,
                onClick = { onSelect(layer) },
                label = { Text(layerTitle(layer), maxLines = 1) },
                leadingIcon = if (layout.layer(layer) != null) {
                    {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Customised",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

internal fun layerTitle(layer: LayoutLayer): String = when (layer) {
    LayoutLayer.LETTERS -> "Letters"
    LayoutLayer.SYMBOLS -> "Symbols"
    LayoutLayer.SYMBOLS_SHIFTED -> "Symbols 2"
    LayoutLayer.NUMBER -> "Number"
    LayoutLayer.PHONE -> "Phone"
    LayoutLayer.DATE -> "Date"
    LayoutLayer.TIME -> "Time"
    LayoutLayer.DATETIME -> "Date & time"
    LayoutLayer.FN -> "Fn"
}

/**
 * The grid the user edits, drawn in the keyboard's own theme.
 *
 * [KeyboardThemeProvider] needs only [KeyboardSettings], so the editor gets the
 * real key colours, key shape, corner radius and font without touching the input
 * pipeline — `KeyboardScreen` itself wants a `StateFlow<KeyboardUiState>` and
 * some ninety callbacks, and a synthetic state that large is a maintenance
 * liability rather than a preview. Reusing the real `KeyCell`/`KeyButton` was the
 * other option and was rejected for the same reason, plus their whole job is the
 * press machinery — long-press popups, key repeat, the spacebar hold timer —
 * every one of which fights tap-to-select.
 *
 * The provider is scoped to this Box on purpose: it swaps MaterialTheme, and
 * hoisting it any higher would repaint every row and slider on the screen in the
 * keyboard's palette.
 */
@Composable
private fun EditorGrid(
    layout: KeyboardLayout,
    settings: KeyboardSettings,
    selection: KeyRef?,
    showShift: Boolean,
    onSelect: (KeyRef) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        KeyboardThemeProvider(settings) {
            val kb = LocalKbTheme.current
            // Forced LTR: a key's index in its row is its serialized order, so an
            // RTL locale mirroring the grid would make "move right" write
            // index - 1. Labels inside each cell still resolve their own bidi.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(kb.board)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // The first row sets the grid width and every other row is
                    // centred against it, or squeezed if it is wider — the same
                    // rule the real keyboard lays rows out by, taken from the
                    // same helpers so the two can never disagree.
                    val gridWeight = gridWeightOf(layout.rows).takeIf { it > 0f } ?: 10f
                    if (layout.rows.isEmpty()) {
                        Text(
                            "This layer has no rows.",
                            modifier = Modifier.padding(12.dp),
                            color = kb.keyText.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                    layout.rows.forEachIndexed { r, row ->
                        val sidePad = sidePadFor(row, gridWeight)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (sidePad > 0.01f) Spacer(Modifier.weight(sidePad))
                            row.forEachIndexed { c, key ->
                                EditorKeyCell(
                                    key = key,
                                    kb = kb,
                                    heightDp = settings.keyHeightDp,
                                    selected = selection == KeyRef(r, c),
                                    showShift = showShift,
                                    modifier = Modifier.weight(key.width),
                                ) { onSelect(KeyRef(r, c)) }
                            }
                            if (sidePad > 0.01f) Spacer(Modifier.weight(sidePad))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.EditorKeyCell(
    key: Key,
    kb: KbTheme,
    heightDp: Int,
    selected: Boolean,
    showShift: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        key.action == KeyAction.Enter -> kb.enterKey
        key.action != KeyAction.Text -> kb.modifierKey
        else -> kb.key
    }
    val foreground = when {
        key.action == KeyAction.Enter -> kb.enterKeyText
        key.action != KeyAction.Text -> kb.modifierKeyText
        else -> kb.keyText
    }
    // The user's own key height keeps the preview honest, but a 100dp setting
    // would put two rows on screen — clamping is cheaper than a zoom control.
    val height = heightDp.dp.coerceIn(38.dp, 56.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(kb.keyShape())
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(2.dp, kb.accent, kb.keyShape())
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Shown verbatim rather than uppercased the way the real keyboard does
        // under shift: the stored label is the thing being edited.
        val primary = if (showShift) key.shiftLabel ?: key.label.uppercase() else key.label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = primary.ifBlank { actionGlyph(key.action) },
                color = foreground,
                fontSize = labelSize(primary),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // Probhat and Jatiya put half the alphabet on shiftLabel, and many
            // fonts render a bare matra (া, ি) as an orphaned mark. Showing the
            // pair identifies the key even when the top glyph is ambiguous. The
            // real keyboard swaps on shift instead, so this is editor-only.
            if (!showShift && key.shiftLabel != null) {
                Text(
                    text = key.shiftLabel!!,
                    color = foreground.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * ".com" and "https://" are legal labels and would blow a cell open at full
 * size, so the label steps down as it grows.
 */
private fun labelSize(label: String) = when {
    label.length <= 1 -> 16.sp
    label.length <= 3 -> 13.sp
    else -> 11.sp
}

/** What to draw for an action key whose label is blank, like a keypad spacebar. */
private fun actionGlyph(action: KeyAction): String = when (action) {
    KeyAction.Space -> "space"
    KeyAction.Enter -> "⏎"
    KeyAction.Delete -> "⌫"
    KeyAction.Shift -> "⇧"
    KeyAction.None -> ""
    else -> "·"
}

