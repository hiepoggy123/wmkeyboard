package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.LastPage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.spanSlots
import com.wasimaster.wmkeyboard.core.settings.DefaultTextEditLayout
import com.wasimaster.wmkeyboard.core.settings.TextEditAction
import com.wasimaster.wmkeyboard.core.settings.TextEditKey
import com.wasimaster.wmkeyboard.core.settings.TextEditLayout
import com.wasimaster.wmkeyboard.core.settings.repeats
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The text-editing panel: a grid of cursor, selection and clipboard keys, drawn
 * from [TextEditLayout] so it can be rearranged the way a key layout can.
 *
 * It used to be a hand-built cluster of nested rows and columns. The arrangement
 * is unchanged — [DefaultTextEditLayout] is that cluster written down — but it is
 * data now, which is what makes it editable and what lets a key carry a second
 * action on press and hold.
 *
 * Keys whose action repeats (the moves, and backspace) auto-repeat on hold at the
 * text-edit interval, exactly as before. The rest have no use for a hold, so
 * theirs runs [TextEditKey.longPress] when the layout gives them one.
 */
@Composable
internal fun TextEditPanel(
    state: KeyboardUiState,
    onAction: (TextEditAction) -> Unit,
) {
    val height = keyRowsHeight(state)
    val repeatMs = state.settings.textEditing.repeatMs.toLong()
    val layout = state.settings.textEditing.layout ?: DefaultTextEditLayout
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // The whole answer, not the panel's own flag: with the toolbar's
        // Selection mode on, this panel's arrows extend the selection too, so
        // its Select key has to read as on.
        TextEditGrid(layout, repeatMs, state.selectingText, onAction)
    }
}

/** The gap between two panel keys; half of it pads each side of a cell. */
private val PanelKeyGap = 4.dp

/**
 * Places every key of [layout] against the grid, spans included.
 *
 * A hand-written [Layout] rather than nested rows, for the reason the key rows use
 * one: a key covering more than one row cannot be a child of a single `Row` — it
 * would draw outside its parent's bounds and the overhanging half would not be
 * hit-tested. The arithmetic itself is [spanSlots], the same geometry the keyboard
 * and the layout editor's preview share, reached by describing each key as the
 * width/span pair it is.
 */
@Composable
private fun TextEditGrid(
    layout: TextEditLayout,
    repeatMs: Long,
    selecting: Boolean,
    onAction: (TextEditAction) -> Unit,
) {
    val rows = layout.rows
    if (rows.isEmpty()) return
    val gridWeight = layout.gridWeight
    if (gridWeight <= 0f) return
    // Geometry in grid units, recomputed only when the layout itself changes.
    val slots = remember(layout) {
        spanSlots(
            rows.map { row -> row.map { Key(label = "", width = it.width, rowSpan = it.rowSpan) } },
            gridWeight,
        )
    }
    val heights = remember(layout) { List(rows.size) { layout.rowHeight(it) } }
    val totalHeight = heights.sum()

    Layout(
        content = {
            for (slot in slots) {
                val key = rows[slot.row][slot.col]
                val holdAction = key.longPress?.takeUnless { key.action.repeats }
                EditKey(
                    icon = iconFor(key.action),
                    label = labelFor(key.action),
                    description = stringResource(descriptionFor(key.action)),
                    active = key.action == TextEditAction.SELECT && selecting,
                    repeatable = key.action.repeats,
                    repeatIntervalMs = repeatMs,
                    onHold = holdAction?.let { hold -> { onAction(hold) } },
                ) { onAction(key.action) }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val gapPx = PanelKeyGap.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val unit = width / gridWeight
        // Row tops from the per-row multipliers, so a row given 1.5 takes half
        // again the share of an ordinary one. Accumulated rather than multiplied
        // out, so rounding cannot leave a seam between two rows.
        val rowTops = IntArray(rows.size + 1)
        var covered = 0f
        for (r in rows.indices) {
            covered += heights[r]
            rowTops[r + 1] = (height * (covered / totalHeight)).toInt()
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val slot = slots[index]
            val last = (slot.row + slot.span - 1).coerceAtMost(rows.size - 1)
            measurable.measure(
                Constraints.fixed(
                    width = ((slot.key.width * unit).toInt() - gapPx).coerceAtLeast(0),
                    height = (rowTops[last + 1] - rowTops[slot.row] - gapPx).coerceAtLeast(0),
                ),
            )
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val slot = slots[index]
                placeable.place(
                    x = (slot.x * unit).toInt() + gapPx / 2,
                    y = rowTops[slot.row] + gapPx / 2,
                )
            }
        }
    }
}

/** The glyph each action draws, or null for the one that reads better as a word. */
internal fun iconFor(action: TextEditAction): ImageVector? = when (action) {
    TextEditAction.LEFT -> Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    TextEditAction.RIGHT -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
    TextEditAction.UP -> Icons.Outlined.KeyboardArrowUp
    TextEditAction.DOWN -> Icons.Outlined.KeyboardArrowDown
    TextEditAction.WORD_LEFT -> Icons.Outlined.KeyboardDoubleArrowLeft
    TextEditAction.WORD_RIGHT -> Icons.Outlined.KeyboardDoubleArrowRight
    TextEditAction.PAGE_UP -> Icons.Outlined.KeyboardDoubleArrowUp
    TextEditAction.PAGE_DOWN -> Icons.Outlined.KeyboardDoubleArrowDown
    TextEditAction.HOME -> Icons.Outlined.FirstPage
    TextEditAction.END -> Icons.AutoMirrored.Outlined.LastPage
    TextEditAction.SELECT_ALL -> Icons.Outlined.SelectAll
    TextEditAction.SELECT_WORD -> Icons.Outlined.ShortText
    TextEditAction.SELECT_LINE -> Icons.Outlined.Subject
    TextEditAction.COPY -> Icons.Outlined.ContentCopy
    TextEditAction.PASTE -> Icons.Outlined.ContentPaste
    TextEditAction.BACKSPACE -> Icons.AutoMirrored.Outlined.Backspace
    // Select is a toggle rather than a move: it reads as a word, and being lit is
    // what says it is on.
    TextEditAction.SELECT -> null
}

/** The words on the key, for the actions an icon alone does not explain. */
@Composable
private fun labelFor(action: TextEditAction): String? = when (action) {
    TextEditAction.SELECT -> stringResource(R.string.ime_textedit_select_label)
    TextEditAction.SELECT_ALL -> stringResource(CommonR.string.common_select_all)
    TextEditAction.COPY -> stringResource(CommonR.string.common_copy)
    TextEditAction.PASTE -> stringResource(CommonR.string.common_paste)
    else -> null
}

/** The spoken name of each action, also the name the editor lists it under. */
@StringRes
internal fun descriptionFor(action: TextEditAction): Int = when (action) {
    TextEditAction.LEFT -> R.string.ime_textedit_left_desc
    TextEditAction.RIGHT -> R.string.ime_textedit_right_desc
    TextEditAction.UP -> R.string.ime_textedit_up_desc
    TextEditAction.DOWN -> R.string.ime_textedit_down_desc
    TextEditAction.WORD_LEFT -> R.string.ime_textedit_word_left_desc
    TextEditAction.WORD_RIGHT -> R.string.ime_textedit_word_right_desc
    TextEditAction.PAGE_UP -> R.string.ime_textedit_page_up_desc
    TextEditAction.PAGE_DOWN -> R.string.ime_textedit_page_down_desc
    TextEditAction.HOME -> R.string.ime_textedit_home_desc
    TextEditAction.END -> R.string.ime_textedit_end_desc
    TextEditAction.SELECT -> R.string.ime_textedit_select_desc
    TextEditAction.SELECT_ALL -> CommonR.string.common_select_all
    TextEditAction.SELECT_WORD -> R.string.ime_textedit_select_word_desc
    TextEditAction.SELECT_LINE -> R.string.ime_textedit_select_line_desc
    TextEditAction.COPY -> CommonR.string.common_copy
    TextEditAction.PASTE -> CommonR.string.common_paste
    TextEditAction.BACKSPACE -> CommonR.string.common_delete
}

private const val REPEAT_START_MS = 400L
private const val REPEAT_INTERVAL_MS = 60L

/**
 * One panel key, drawn with the keyboard theme's key colors. [repeatable] keys
 * fire once on press, then auto-repeat every [repeatIntervalMs] while held (the
 * interval is the text-edit tool's repeat-speed setting).
 *
 * [onHold] is the alternative to that repeat, and only one of the two is ever
 * live: a key that repeats has no hold to spare, and a key that does not repeat
 * has nothing else to do with one. A hold key defers its own action to the release
 * instead of firing on press, or one gesture would do both things.
 */
@Composable
private fun EditKey(
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    active: Boolean = false,
    repeatable: Boolean = false,
    repeatIntervalMs: Long = REPEAT_INTERVAL_MS,
    onHold: (() -> Unit)? = null,
    onAction: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    val shape = kb.keyShape()
    val background = if (active) kb.toolCircleActive else kb.modifierKey
    val content = if (active) kb.toolCircleActiveIcon else kb.modifierKeyText
    Box(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .panelKeyBorder(kb, shape)
            .pointerInput(repeatable, repeatIntervalMs, onHold) {
                detectTapGestures(
                    onPress = {
                        if (onHold == null) onAction()
                        var timer: Job? = null
                        var held = false
                        if (repeatable) {
                            timer = scope.launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    onAction()
                                    delay(repeatIntervalMs)
                                }
                            }
                        } else if (onHold != null) {
                            timer = scope.launch {
                                delay(REPEAT_START_MS)
                                held = true
                                onHold()
                            }
                        }
                        tryAwaitRelease()
                        timer?.cancel()
                        // The tap the press deferred, unless the hold took it.
                        if (onHold != null && !held) onAction()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = if (label == null) description else null,
                    modifier = Modifier.height(20.dp),
                    tint = content,
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    color = content,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
