package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.TextEditAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gboard-style text-editing panel: a d-pad cluster for cursor movement with
 * a Select toggle in the middle, clipboard actions on the right, and
 * home / end / backspace along the bottom. Arrows and backspace auto-repeat
 * on hold. While Select is on (or after Select all), arrow moves extend the
 * selection instead of moving the cursor.
 */
@Composable
internal fun TextEditPanel(
    state: KeyboardUiState,
    onAction: (TextEditAction) -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    val rowHeight = state.settings.keyHeightDp.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Left arrow spans the full cluster height, like Gboard.
            EditKey(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                description = "Move left",
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight(),
                repeatable = true,
            ) { onAction(TextEditAction.LEFT) }
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EditKey(
                    icon = Icons.Outlined.KeyboardArrowUp,
                    description = "Move up",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    repeatable = true,
                ) { onAction(TextEditAction.UP) }
                EditKey(
                    label = "Select",
                    description = "Toggle selection mode",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    active = state.textEditSelecting,
                ) { onAction(TextEditAction.SELECT) }
                EditKey(
                    icon = Icons.Outlined.KeyboardArrowDown,
                    description = "Move down",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    repeatable = true,
                ) { onAction(TextEditAction.DOWN) }
            }
            EditKey(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                description = "Move right",
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight(),
                repeatable = true,
            ) { onAction(TextEditAction.RIGHT) }
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EditKey(
                    icon = Icons.Outlined.SelectAll,
                    label = "Select all",
                    description = "Select all",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { onAction(TextEditAction.SELECT_ALL) }
                EditKey(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy",
                    description = "Copy",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { onAction(TextEditAction.COPY) }
                EditKey(
                    icon = Icons.Outlined.ContentPaste,
                    label = "Paste",
                    description = "Paste",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { onAction(TextEditAction.PASTE) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EditKey(
                icon = Icons.Outlined.FirstPage,
                description = "Move to line start",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) { onAction(TextEditAction.HOME) }
            EditKey(
                icon = Icons.Outlined.LastPage,
                description = "Move to line end",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) { onAction(TextEditAction.END) }
            EditKey(
                icon = Icons.AutoMirrored.Outlined.Backspace,
                description = "Delete",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                repeatable = true,
            ) { onAction(TextEditAction.BACKSPACE) }
        }
    }
}

private const val REPEAT_START_MS = 400L
private const val REPEAT_INTERVAL_MS = 60L

/**
 * One panel key, drawn with the keyboard theme's key colors. [repeatable]
 * keys fire once on press, then auto-repeat while held.
 */
@Composable
private fun EditKey(
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    active: Boolean = false,
    repeatable: Boolean = false,
    onAction: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(kb.keyRadiusDp.dp)
    val background = if (active) kb.toolCircleActive else kb.modifierKey
    val content = if (active) kb.toolCircleActiveIcon else kb.modifierKeyText
    Box(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .pointerInput(repeatable) {
                detectTapGestures(
                    onPress = {
                        onAction()
                        var repeat: Job? = null
                        if (repeatable) {
                            repeat = scope.launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    onAction()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        }
                        tryAwaitRelease()
                        repeat?.cancel()
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
