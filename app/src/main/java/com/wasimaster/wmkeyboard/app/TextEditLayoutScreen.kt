package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.settings.DefaultTextEditLayout
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditKeySpan
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditKeyWidth
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditKeysPerRow
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditRowHeight
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditRows
import com.wasimaster.wmkeyboard.core.settings.MinTextEditRowHeight
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.TextEditAction
import com.wasimaster.wmkeyboard.core.settings.TextEditKey
import com.wasimaster.wmkeyboard.core.settings.TextEditLayout
import com.wasimaster.wmkeyboard.core.settings.repeats
import kotlinx.coroutines.launch

/** Route of the text-editing panel's layout editor. */
internal const val ROUTE_TEXT_EDIT_LAYOUT = "textedit_layout"

/**
 * Which key of the grid is open in the sheet: its row and its index in that row.
 * A position rather than the key itself, because every edit rewrites the key.
 */
private data class KeyAt(val row: Int, val col: Int)

/**
 * The text-editing panel's layout, edited the way a key layout is: a live preview
 * of the grid, a sheet per key, and rows you can add, delete and resize.
 *
 * Its own screen rather than a mode of the key-layout editor. The panel is one
 * global grid of actions with no language, no layers and no characters, so sharing
 * that editor would have meant explaining which of its controls do nothing here —
 * and the actions themselves are not `Key`s. What is shared is the *geometry*:
 * widths and row spans mean exactly what they mean in a key layout, and the
 * preview places them with the same `spanSlots` the keyboard draws with.
 *
 * Edits are written straight through, which is what makes the keyboard's own panel
 * update as you go. There is no undo stack: the grid is a dozen keys, and Reset
 * puts the shipped one back.
 */
@Composable
internal fun TextEditLayoutScreen(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val stored = settings?.textEditing?.layout
    val layout = stored ?: DefaultTextEditLayout
    var editing by remember { mutableStateOf<KeyAt?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun write(next: TextEditLayout) {
        scope.launch { repository.setTextEditLayout(next) }
    }

    // No scroller of its own: SettingsScreen already wraps this content in a
    // Column(verticalScroll), and a scrollable inside a scrollable is measured
    // with an infinite maximum height, which Compose refuses outright.
    Column {
        CaptionText(stringResource(R.string.textedit_layout_intro_body))
        TextEditPreview(
            layout = layout,
            selected = editing,
            onKeyClick = { row, col -> editing = KeyAt(row, col) },
        )
        for (rowIndex in layout.rows.indices) {
            SettingsGroup(stringResource(R.string.textedit_layout_row_title, rowIndex + 1)) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            enabled = layout.rows[rowIndex].size < MaxTextEditKeysPerRow,
                            onClick = { write(layout.addKey(rowIndex)) },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.textedit_layout_add_key_action))
                        }
                        // The last row cannot go: a panel with no keys has nothing
                        // to draw and nothing to edit back from.
                        if (layout.rows.size > 1) {
                            TextButton(onClick = {
                                if (editing?.row == rowIndex) editing = null
                                write(layout.deleteRow(rowIndex))
                            }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(stringResource(R.string.textedit_layout_delete_row_action))
                            }
                        }
                    }
                }
                item {
                    RowHeightControl(
                        height = layout.rowHeight(rowIndex),
                        onChange = { write(layout.withRowHeight(rowIndex, it)) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = layout.rows.size < MaxTextEditRows,
                onClick = { write(layout.addRow()) },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.textedit_layout_add_row_action))
            }
            if (stored != null) {
                TextButton(onClick = { confirmReset = true }) {
                    Text(stringResource(CommonR.string.common_reset_defaults))
                }
            }
        }
    }

    editing?.let { at ->
        val key = layout.rows.getOrNull(at.row)?.getOrNull(at.col)
        if (key == null) {
            editing = null
            return@let
        }
        TextEditKeySheet(
            key = key,
            at = at,
            rowSize = layout.rows[at.row].size,
            rowsBelow = layout.rows.size - at.row - 1,
            onChange = { write(layout.replaceKey(at, it)) },
            onMove = { delta -> write(layout.moveKey(at, delta)); editing = KeyAt(at.row, at.col + delta) },
            onDelete = {
                editing = null
                write(layout.deleteKey(at))
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.textedit_layout_reset_title)) },
            text = { Text(stringResource(R.string.textedit_layout_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    editing = null
                    scope.launch { repository.setTextEditLayout(null) }
                }) { Text(stringResource(CommonR.string.common_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

/**
 * The grid as the keyboard will draw it, one tappable cell per key.
 *
 * Rows are drawn as plain [Row]s of weighted cells with a spacer standing in for
 * every column a key above is holding. That is not the keyboard's own placement
 * ([spanSlots] is), but it is the same *result* for the arrangements this editor
 * can produce, and it keeps the preview a few lines instead of a second layout
 * implementation that could disagree with the first.
 */
@Composable
private fun TextEditPreview(
    layout: TextEditLayout,
    selected: KeyAt?,
    onKeyClick: (Int, Int) -> Unit,
) {
    val grid = layout.gridWeight.takeIf { it > 0f } ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((rowIndex, row) in layout.rows.withIndex()) {
            // What keys above are holding over this row, as leading and trailing
            // slack. Only the two edges matter for the arrangements a span can
            // make here, which is where the shipped tall arrows sit.
            val held = layout.heldWidth(rowIndex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((46 * layout.rowHeight(rowIndex)).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (held.first > 0f) Box(modifier = Modifier.weight(held.first))
                for ((col, key) in row.withIndex()) {
                    val isSelected = selected?.row == rowIndex && selected.col == col
                    Box(
                        modifier = Modifier
                            .weight(key.width)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onKeyClick(rowIndex, col) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(actionTitle(key.action)),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            key.longPress?.takeUnless { key.action.repeats }?.let { hold ->
                                Text(
                                    stringResource(R.string.textedit_layout_hold_badge, stringResource(actionTitle(hold))),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                if (held.second > 0f) Box(modifier = Modifier.weight(held.second))
                // Keeps a short row at its real proportions instead of stretching
                // it across the whole board.
                val slack = grid - layout.rowWidth(rowIndex)
                if (slack > 0.01f) Box(modifier = Modifier.weight(slack))
            }
        }
    }
}

/** Per-row height multiplier, mirroring the key layout editor's control. */
@Composable
private fun RowHeightControl(height: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            stringResource(R.string.textedit_layout_row_height_label, height),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = height,
            onValueChange = { onChange((it * 100f).toInt() / 100f) },
            valueRange = MinTextEditRowHeight..MaxTextEditRowHeight,
        )
    }
}

/** One key's own controls: what it does, what a hold does, how big it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditKeySheet(
    key: TextEditKey,
    at: KeyAt,
    rowSize: Int,
    rowsBelow: Int,
    onChange: (TextEditKey) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingAction by remember(at) { mutableStateOf(false) }
    var pickingHold by remember(at) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeaderPublic(
                stringResource(R.string.textedit_layout_key_position_title, at.row + 1, at.col + 1),
            )
            NavRow(
                title = R.string.textedit_layout_key_action_title,
                value = stringResource(actionTitle(key.action)),
            ) { pickingAction = true }
            // A key whose action repeats has no hold to give away, so the row
            // says that instead of offering a choice that would never fire.
            if (key.action.repeats) {
                CaptionText(stringResource(R.string.textedit_layout_hold_repeats_body))
            } else {
                NavRow(
                    title = R.string.textedit_layout_key_hold_title,
                    subtitle = stringResource(R.string.textedit_layout_key_hold_subtitle),
                    value = key.longPress?.let { stringResource(actionTitle(it)) }
                        ?: stringResource(CommonR.string.common_none),
                ) { pickingHold = true }
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.textedit_layout_key_width_label, key.width),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = key.width.coerceIn(0.2f, MaxTextEditKeyWidth),
                    onValueChange = { onChange(key.copy(width = (it * 100f).toInt() / 100f)) },
                    valueRange = 0.2f..MaxTextEditKeyWidth,
                )
            }
            // Rows below is what a span can reach into, so a key on the last row
            // has no control at all — the same rule the key editor follows.
            if (rowsBelow > 0 || key.rowSpan > 1) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.textedit_layout_key_span_label, key.rowSpan),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (span in 1..maxOf(rowsBelow + 1, key.rowSpan).coerceAtMost(MaxTextEditKeySpan)) {
                            FilterChip(
                                selected = span == key.rowSpan,
                                onClick = { onChange(key.copy(rowSpan = span)) },
                                label = { Text(span.toString()) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.textedit_layout_delete_key_action))
                }
                Box(modifier = Modifier.weight(1f))
                IconButton(enabled = at.col > 0, onClick = { onMove(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.textedit_layout_move_left_desc),
                    )
                }
                IconButton(enabled = at.col < rowSize - 1, onClick = { onMove(+1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.textedit_layout_move_right_desc),
                    )
                }
            }
        }
    }

    if (pickingAction) {
        ActionPickerDialog(
            title = stringResource(R.string.textedit_layout_key_action_title),
            current = key.action,
            allowNone = false,
            onDismiss = { pickingAction = false },
            onPick = { picked ->
                pickingAction = false
                picked?.let {
                    // Turning a key into a repeating one drops the hold it can no
                    // longer run, so the stored layout says what the panel does.
                    onChange(key.copy(action = it, longPress = key.longPress?.takeUnless { _ -> it.repeats }))
                }
            },
        )
    }
    if (pickingHold) {
        ActionPickerDialog(
            title = stringResource(R.string.textedit_layout_key_hold_title),
            current = key.longPress,
            allowNone = true,
            // Holding a key to do what tapping it does is a slow tap.
            exclude = key.action,
            onDismiss = { pickingHold = false },
            onPick = { picked ->
                pickingHold = false
                onChange(key.copy(longPress = picked))
            },
        )
    }
}

/** Picks one of the seventeen panel actions, or none. */
@Composable
private fun ActionPickerDialog(
    title: String,
    current: TextEditAction?,
    allowNone: Boolean,
    exclude: TextEditAction? = null,
    onDismiss: () -> Unit,
    onPick: (TextEditAction?) -> Unit,
) {
    val options = remember(exclude) { TextEditAction.entries.filter { it != exclude } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (allowNone) {
                    item {
                        WmRow(
                            title = stringResource(CommonR.string.common_none),
                            trailing = { RadioButton(selected = current == null, onClick = { onPick(null) }) },
                            onClick = { onPick(null) },
                        )
                    }
                }
                items(options, key = { it.name }) { action ->
                    WmRow(
                        title = stringResource(actionTitle(action)),
                        trailing = { RadioButton(selected = current == action, onClick = { onPick(action) }) },
                        onClick = { onPick(action) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/**
 * The name each action is listed under — the keyboard's own spoken labels, so the
 * editor and TalkBack call the same key the same thing.
 */
private fun actionTitle(action: TextEditAction): Int = when (action) {
    TextEditAction.LEFT -> R.string.textedit_action_left
    TextEditAction.RIGHT -> R.string.textedit_action_right
    TextEditAction.UP -> R.string.textedit_action_up
    TextEditAction.DOWN -> R.string.textedit_action_down
    TextEditAction.WORD_LEFT -> R.string.textedit_action_word_left
    TextEditAction.WORD_RIGHT -> R.string.textedit_action_word_right
    TextEditAction.PAGE_UP -> R.string.textedit_action_page_up
    TextEditAction.PAGE_DOWN -> R.string.textedit_action_page_down
    TextEditAction.HOME -> R.string.textedit_action_home
    TextEditAction.END -> R.string.textedit_action_end
    TextEditAction.SELECT -> R.string.textedit_action_select
    TextEditAction.SELECT_ALL -> R.string.textedit_action_select_all
    TextEditAction.SELECT_WORD -> R.string.textedit_action_select_word
    TextEditAction.SELECT_LINE -> R.string.textedit_action_select_line
    TextEditAction.COPY -> R.string.textedit_action_copy
    TextEditAction.PASTE -> R.string.textedit_action_paste
    TextEditAction.BACKSPACE -> R.string.textedit_action_backspace
}

// ---- the edits ----

/** Leading and trailing slack a row inherits from spans above it. */
private fun TextEditLayout.heldWidth(index: Int): Pair<Float, Float> {
    var leading = 0f
    var trailing = 0f
    for (r in 0 until index) {
        var x = 0f
        for (key in rows[r]) {
            val holds = r + key.rowSpan > index
            if (holds) {
                // A span at the left edge pushes this row right; anything further
                // along holds space after it.
                if (x <= 0.01f) leading += key.width else trailing += key.width
            }
            x += key.width
        }
    }
    return leading to trailing
}

private fun TextEditLayout.mapRows(block: (MutableList<MutableList<TextEditKey>>) -> Unit): TextEditLayout {
    val next = rows.map { it.toMutableList() }.toMutableList()
    block(next)
    return copy(rows = next.filter { it.isNotEmpty() }.map { it.toList() })
}

private fun TextEditLayout.replaceKey(at: KeyAt, key: TextEditKey): TextEditLayout =
    mapRows { rows -> rows[at.row][at.col] = key }

private fun TextEditLayout.deleteKey(at: KeyAt): TextEditLayout =
    mapRows { rows -> rows[at.row].removeAt(at.col) }

private fun TextEditLayout.moveKey(at: KeyAt, delta: Int): TextEditLayout =
    mapRows { rows ->
        val target = (at.col + delta).coerceIn(0, rows[at.row].size - 1)
        val key = rows[at.row].removeAt(at.col)
        rows[at.row].add(target, key)
    }

private fun TextEditLayout.addKey(row: Int): TextEditLayout =
    mapRows { rows -> rows[row].add(TextEditKey(TextEditAction.LEFT)) }

private fun TextEditLayout.deleteRow(row: Int): TextEditLayout {
    val next = rows.toMutableList().apply { removeAt(row) }
    // Heights are index-aligned with the rows, so one has to go with the other.
    val heights = rowHeights?.toMutableList()?.apply { if (row < size) removeAt(row) }
    return copy(rows = next, rowHeights = heights?.takeIf { hs -> hs.any { it != 1f } })
}

private fun TextEditLayout.addRow(): TextEditLayout =
    copy(rows = rows + listOf(listOf(TextEditKey(TextEditAction.LEFT))))

private fun TextEditLayout.withRowHeight(row: Int, height: Float): TextEditLayout {
    val heights = MutableList(rows.size) { rowHeights?.getOrNull(it) ?: 1f }
    heights[row] = height
    return copy(rowHeights = heights.takeIf { hs -> hs.any { it != 1f } })
}
