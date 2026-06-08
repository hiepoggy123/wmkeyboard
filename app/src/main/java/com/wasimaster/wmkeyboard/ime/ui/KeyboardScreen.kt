package com.wasimaster.wmkeyboard.ime.ui

import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.delay
import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.clipboard.ClipKind
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariants
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.isFixedBengali
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.ime.EnterAction
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutMode
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.ime.TextEditAction
import com.wasimaster.wmkeyboard.ime.ShiftState
import com.wasimaster.wmkeyboard.ime.layout.Key
import com.wasimaster.wmkeyboard.ime.layout.KeyAction
import com.wasimaster.wmkeyboard.ime.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.ime.layout.Layouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fired at pointer-down on any key so feedback (haptics) lands on press,
 * not on release when the key's action commits.
 */
private val LocalKeyPressFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/** Root composable for the IME. Renders [KeyboardUiState] and forwards input. */
@Composable
fun KeyboardScreen(
    stateFlow: StateFlow<KeyboardUiState>,
    onKey: (Key) -> Unit,
    onKeyPressed: () -> Unit = {},
    onText: (String) -> Unit = {},
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onCursorMove: (Int) -> Unit = {},
    onLanguageSelect: (InputMode) -> Unit = {},
    onSuggestion: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onEmojiRecentsClear: () -> Unit = {},
    onTextEdit: (TextEditAction) -> Unit = {},
    onPanelChange: (PanelMode) -> Unit,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onSnippet: (Snippet) -> Unit = {},
    onOneHanded: (OneHandedMode) -> Unit = {},
    onFloatingChange: (Boolean) -> Unit = {},
    onFloatingMoved: (Float, Float) -> Unit = { _, _ -> },
    onFloatingResized: (Int, Float) -> Unit = { _, _ -> },
    onFloatingBounds: (IntRect) -> Unit = {},
    onToggleSplit: () -> Unit = {},
    onToolbarToolsChange: (List<ToolbarTool>) -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    val state by stateFlow.collectAsState()

    // One entry point for every toolbar/toolbox tool.
    val onToolTap: (ToolbarTool) -> Unit = { tool ->
        when (tool) {
            ToolbarTool.EMOJI -> onPanelChange(PanelMode.EMOJI)
            ToolbarTool.CLIPBOARD -> onPanelChange(PanelMode.CLIPBOARD)
            ToolbarTool.SNIPPETS -> onPanelChange(PanelMode.SNIPPETS)
            ToolbarTool.TEXT_EDIT -> onPanelChange(PanelMode.TEXT_EDIT)
            ToolbarTool.SETTINGS -> onOpenSettings()
            ToolbarTool.ONE_HANDED -> onOneHanded(
                if (state.settings.oneHandedMode == OneHandedMode.OFF) OneHandedMode.RIGHT
                else OneHandedMode.OFF
            )
            ToolbarTool.SPLIT -> onToggleSplit()
            ToolbarTool.FLOATING -> onFloatingChange(!state.settings.floatingKeyboard)
        }
    }

    val body: @Composable ColumnScope.(KeyboardUiState) -> Unit = { bodyState ->
        CompositionLocalProvider(LocalKeyPressFeedback provides onKeyPressed) {
            KeyboardBody(
                state = bodyState,
                onKey = onKey,
                onText = onText,
                onGesture = onGesture,
                onGesturePreview = onGesturePreview,
                onCursorMove = onCursorMove,
                onLanguageSelect = onLanguageSelect,
                onSuggestion = onSuggestion,
                onEmoji = onEmoji,
                onEmojiQueryTap = onEmojiQueryTap,
                onEmojiRecentsClear = onEmojiRecentsClear,
                onTextEdit = onTextEdit,
                onPanelChange = onPanelChange,
                onClipboardItem = onClipboardItem,
                onClipboardPin = onClipboardPin,
                onClipboardDelete = onClipboardDelete,
                onSnippet = onSnippet,
                onToolTap = onToolTap,
                onToolbarToolsChange = onToolbarToolsChange,
            )
        }
    }

    KeyboardThemeProvider(settings = state.settings) {
        if (state.settings.floatingKeyboard) {
            // Floating mode: the compose root spans the whole IME window with
            // no background; the service restricts the touchable region to
            // the panel so everything else falls through to the app behind.
            FloatingKeyboardFrame(
                state = state,
                onDock = { onFloatingChange(false) },
                onMoved = onFloatingMoved,
                onResized = onFloatingResized,
                onBounds = onFloatingBounds,
                content = { heightScale ->
                    // Key height carries the whole layout (panels included),
                    // so scaling it scales the keyboard's height.
                    val scaled = if (heightScale == 1f) state else state.copy(
                        settings = state.settings.copy(
                            keyHeightDp = (state.settings.keyHeightDp * heightScale).roundToInt(),
                        ),
                    )
                    body(scaled)
                },
            )
            return@KeyboardThemeProvider
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            BoardBackground(LocalKbTheme.current)
            // navigationBarsPadding keeps the bottom key row clear of the
            // gesture-navigation bar on edge-to-edge (SDK 35+) IME windows.
            val oneHanded = state.settings.oneHandedMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // Extra breathing room above the gesture bar, adjustable
                    // in Settings → Appearance.
                    .padding(bottom = state.settings.bottomPaddingDp.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (oneHanded == OneHandedMode.RIGHT) {
                    OneHandedRail(current = oneHanded, onOneHanded = onOneHanded, modifier = Modifier.weight(0.22f))
                }
                // Resizable width: below 100% the keyboard shrinks and sits at
                // the chosen edge (or centered). One-handed mode has its own
                // fixed 78% width, so the two never compose.
                val widthFraction = if (oneHanded == OneHandedMode.OFF) {
                    state.settings.keyboardWidthPercent / 100f
                } else {
                    0.78f
                }
                val slack = if (oneHanded == OneHandedMode.OFF) 1f - widthFraction else 0f
                val leftSlack = when (state.settings.keyboardAlignment) {
                    KeyboardAlignment.LEFT -> 0f
                    KeyboardAlignment.CENTER -> slack / 2f
                    KeyboardAlignment.RIGHT -> slack
                }
                if (leftSlack > 0.001f) Spacer(modifier = Modifier.weight(leftSlack))
                Column(modifier = Modifier.weight(widthFraction)) { body(state) }
                val rightSlack = slack - leftSlack
                if (rightSlack > 0.001f) Spacer(modifier = Modifier.weight(rightSlack))
                if (oneHanded == OneHandedMode.LEFT) {
                    OneHandedRail(current = oneHanded, onOneHanded = onOneHanded, modifier = Modifier.weight(0.22f))
                }
            }
        }
    }
}

/**
 * Floating mode chrome: a detached, elevated panel holding the regular
 * keyboard body, movable by its drag handle and resizable from the corner
 * handle. Position is kept as fractions of the free space so it survives
 * rotation; width in dp. Both persist via the callbacks on gesture end.
 */
@Composable
private fun FloatingKeyboardFrame(
    state: KeyboardUiState,
    onDock: () -> Unit,
    onMoved: (Float, Float) -> Unit,
    onResized: (Int, Float) -> Unit,
    onBounds: (IntRect) -> Unit,
    content: @Composable ColumnScope.(Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxWidthPx = constraints.maxWidth
        val boxHeightPx = constraints.maxHeight
        val maxWidthDp = with(density) { boxWidthPx.toDp().value } - 16f
        var liveWidthDp by remember(state.settings.floatingWidthDp) {
            mutableFloatStateOf(state.settings.floatingWidthDp.toFloat())
        }
        var liveHeightScale by remember(state.settings.floatingHeightScale) {
            mutableFloatStateOf(state.settings.floatingHeightScale)
        }
        val panelWidthDp = liveWidthDp.coerceIn(FLOATING_MIN_WIDTH_DP, maxWidthDp.coerceAtLeast(FLOATING_MIN_WIDTH_DP))

        var panelSize by remember { mutableStateOf(IntSize.Zero) }
        // Live drag position in px; null = follow the persisted fractions.
        // Reset when the window size changes (rotation) so the fractions
        // re-anchor the panel.
        var dragOffset by remember(boxWidthPx, boxHeightPx) { mutableStateOf<Offset?>(null) }
        fun slackX() = (boxWidthPx - panelSize.width).coerceAtLeast(0).toFloat()
        fun slackY() = (boxHeightPx - panelSize.height).coerceAtLeast(0).toFloat()
        val offset = dragOffset ?: Offset(
            state.settings.floatingXFraction * slackX(),
            state.settings.floatingYFraction * slackY(),
        )

        Surface(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .width(panelWidthDp.dp)
                // Invisible for the first frame, before the panel has been
                // measured and placed from real sizes — avoids a flash at a
                // wrong position.
                .alpha(if (panelSize == IntSize.Zero) 0f else 1f)
                .onGloballyPositioned { coords ->
                    panelSize = coords.size
                    val position = coords.positionInWindow()
                    onBounds(
                        IntRect(
                            offset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
                            size = coords.size,
                        )
                    )
                },
            shape = RoundedCornerShape(18.dp),
            // The theme paints the panel (color + optional image); Surface
            // just supplies the shape, clip and shadow.
            color = Color.Transparent,
            shadowElevation = 10.dp,
        ) {
            Box {
                BoardBackground(LocalKbTheme.current)
                Column {
                    FloatingHandleBar(
                        onDock = onDock,
                        onDragBy = { delta ->
                            val current = dragOffset ?: offset
                            dragOffset = Offset(
                                (current.x + delta.x).coerceIn(0f, slackX()),
                                (current.y + delta.y).coerceIn(0f, slackY()),
                            )
                        },
                        onDragEnd = {
                            val end = dragOffset ?: return@FloatingHandleBar
                            onMoved(
                                if (slackX() > 0f) end.x / slackX() else 0.5f,
                                if (slackY() > 0f) end.y / slackY() else 0.5f,
                            )
                        },
                        onResizeBy = { delta ->
                            liveWidthDp = (liveWidthDp + with(density) { delta.x.toDp().value })
                                .coerceIn(FLOATING_MIN_WIDTH_DP, maxWidthDp.coerceAtLeast(FLOATING_MIN_WIDTH_DP))
                            // Height resizes too: the drag is normalized by the
                            // panel's unscaled height, so the grip tracks the
                            // finger no matter how tall the content already is.
                            val baseHeightPx = if (liveHeightScale > 0f) panelSize.height / liveHeightScale else 0f
                            if (baseHeightPx > 0f) {
                                liveHeightScale = (liveHeightScale + delta.y / baseHeightPx)
                                    .coerceIn(FLOATING_MIN_HEIGHT_SCALE, FLOATING_MAX_HEIGHT_SCALE)
                            }
                        },
                        onResizeEnd = { onResized(panelWidthDp.roundToInt(), liveHeightScale) },
                    )
                    content(liveHeightScale)
                }
            }
        }
    }
}

private const val FLOATING_MIN_WIDTH_DP = 240f
private const val FLOATING_MIN_HEIGHT_SCALE = 0.6f
private const val FLOATING_MAX_HEIGHT_SCALE = 1.6f

/** Handle row on top of the floating panel: dock button, drag pill, resize grip. */
@Composable
private fun FloatingHandleBar(
    onDock: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeBy: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDock, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Outlined.CloseFullscreen,
                contentDescription = "Dock keyboard",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            onDragBy(amount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            onResizeBy(amount)
                        },
                        onDragEnd = onResizeEnd,
                        onDragCancel = onResizeEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.OpenInFull,
                contentDescription = "Resize keyboard",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Side rail shown in one-handed mode: swap sides or return to full width. */
@Composable
private fun OneHandedRail(
    current: OneHandedMode,
    onOneHanded: (OneHandedMode) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = {
            onOneHanded(if (current == OneHandedMode.LEFT) OneHandedMode.RIGHT else OneHandedMode.LEFT)
        }) {
            Icon(
                if (current == OneHandedMode.LEFT) {
                    Icons.AutoMirrored.Outlined.ArrowForward
                } else {
                    Icons.AutoMirrored.Outlined.ArrowBack
                },
                contentDescription = "Move keyboard to the other side",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onOneHanded(OneHandedMode.OFF) }) {
            Icon(
                Icons.Outlined.Fullscreen,
                contentDescription = "Exit one-handed mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- top bar: suggestions or toolbar ----

@Composable
private fun TopBar(
    state: KeyboardUiState,
    onSuggestion: (String) -> Unit,
    onPanelChange: (PanelMode) -> Unit,
    onToolTap: (ToolbarTool) -> Unit,
    drag: ToolDragController,
) {
    // "Show the toolbar instead" while suggestions are up; resets once the
    // suggestions go away so the bar returns to candidates next time.
    var toolbarOverride by remember { mutableStateOf(false) }
    val hasSuggestions = state.suggestions.isNotEmpty()
    LaunchedEffect(hasSuggestions) { if (!hasSuggestions) toolbarOverride = false }
    val showToolbar = !hasSuggestions || toolbarOverride || state.panel == PanelMode.TOOLBOX

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasSuggestions) {
            IconButton(onClick = { toolbarOverride = !toolbarOverride }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (showToolbar) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                    contentDescription = if (showToolbar) "Show suggestions" else "Show toolbar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showToolbar) {
            ToolbarRow(state, onPanelChange, onToolTap, drag)
        } else {
            if (state.settings.emojiToolbar) {
                ToolCircle(
                    icon = toolIcon(ToolbarTool.EMOJI),
                    description = "Emoji",
                    active = false,
                ) { onToolTap(ToolbarTool.EMOJI) }
            }
            // The top candidates split the whole bar evenly (Gboard style),
            // so each one gets the largest possible tap target.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = state.suggestions.take(3)
                shown.forEachIndexed { index, suggestion ->
                    if (index > 0) {
                        VerticalDivider(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSuggestion(suggestion) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---- customizable toolbar & toolbox ----

private fun toolIcon(tool: ToolbarTool): ImageVector = when (tool) {
    ToolbarTool.EMOJI -> Icons.Outlined.EmojiEmotions
    ToolbarTool.CLIPBOARD -> Icons.Outlined.ContentPaste
    ToolbarTool.SNIPPETS -> Icons.Outlined.TextSnippet
    ToolbarTool.TEXT_EDIT -> Icons.Outlined.EditNote
    ToolbarTool.ONE_HANDED -> Icons.Outlined.Smartphone
    ToolbarTool.SPLIT -> Icons.Outlined.VerticalSplit
    ToolbarTool.FLOATING -> Icons.Outlined.PictureInPictureAlt
    ToolbarTool.SETTINGS -> Icons.Outlined.Settings
}

private fun toolLabel(tool: ToolbarTool): String = when (tool) {
    ToolbarTool.EMOJI -> "Emoji"
    ToolbarTool.CLIPBOARD -> "Clipboard"
    ToolbarTool.SNIPPETS -> "Snippets"
    ToolbarTool.TEXT_EDIT -> "Text editing"
    ToolbarTool.ONE_HANDED -> "One-handed"
    ToolbarTool.SPLIT -> "Split"
    ToolbarTool.FLOATING -> "Floating"
    ToolbarTool.SETTINGS -> "Settings"
}

private fun toolActive(tool: ToolbarTool, state: KeyboardUiState): Boolean = when (tool) {
    ToolbarTool.EMOJI -> state.panel == PanelMode.EMOJI
    ToolbarTool.CLIPBOARD -> state.panel == PanelMode.CLIPBOARD
    ToolbarTool.SNIPPETS -> state.panel == PanelMode.SNIPPETS
    ToolbarTool.TEXT_EDIT -> state.panel == PanelMode.TEXT_EDIT
    ToolbarTool.ONE_HANDED -> state.settings.oneHandedMode != OneHandedMode.OFF
    ToolbarTool.SPLIT -> state.settings.splitKeyboard
    ToolbarTool.FLOATING -> state.settings.floatingKeyboard
    ToolbarTool.SETTINGS -> false
}

/**
 * Live state of a toolbar-customization drag. Bounds and positions are all
 * in window-root coordinates; the ghost is drawn relative to the keyboard
 * body's origin. Drops on the toolbar insert at the slot under the finger,
 * drops anywhere else send a toolbar tool back to the toolbox.
 */
private class ToolDragController {
    var dragging by mutableStateOf<ToolbarTool?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    private var fromToolbar = false
    var toolbarBounds: Rect? = null
    var currentTools: List<ToolbarTool> = emptyList()
    var onCommit: (List<ToolbarTool>) -> Unit = {}

    fun start(tool: ToolbarTool, fromBar: Boolean, at: Offset) {
        dragging = tool
        fromToolbar = fromBar
        position = at
    }

    fun move(to: Offset) {
        position = to
    }

    fun cancel() {
        dragging = null
    }

    fun end() {
        val tool = dragging ?: return
        dragging = null
        // Generous hit box: a drop just above/below the bar still counts.
        val bar = toolbarBounds?.inflate(30f)
        val tools = currentTools
        if (bar != null && bar.contains(position)) {
            val without = tools - tool
            val slot = if (without.isEmpty()) {
                0
            } else {
                (((position.x - bar.left) / bar.width) * (without.size + 1))
                    .toInt()
                    .coerceIn(0, without.size)
            }
            onCommit(without.toMutableList().apply { add(slot, tool) })
        } else if (fromToolbar) {
            onCommit(tools - tool)
        }
    }
}

/** Wires long-press-drag onto a tool while customization (toolbox) is open. */
@Composable
private fun DraggableTool(
    tool: ToolbarTool,
    fromToolbar: Boolean,
    enabled: Boolean,
    drag: ToolDragController,
    content: @Composable (Modifier) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    content(
        Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(enabled, tool) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> drag.start(tool, fromToolbar, origin + at) },
                    onDrag = { change, _ ->
                        change.consume()
                        drag.move(origin + change.position)
                    },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.cancel() },
                )
            }
    )
}

/** One round tool button; the circle radius comes from the theme (0 = bare icon). */
@Composable
private fun ToolCircle(
    icon: ImageVector,
    description: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val shape = RoundedCornerShape(kb.toolRadiusDp.dp)
    val background = when {
        active -> kb.toolCircleActive
        kb.toolRadiusDp > 0 -> kb.toolCircle
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(shape)
            .background(background, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = if (active) kb.toolCircleActiveIcon else kb.toolbarIcon,
        )
    }
}

/**
 * The toolbar itself: fixed toolbox launcher, then the user's tools —
 * spread across the free space when the greedy setting is on, packed to
 * the left otherwise.
 */
@Composable
private fun RowScope.ToolbarRow(
    state: KeyboardUiState,
    onPanelChange: (PanelMode) -> Unit,
    onToolTap: (ToolbarTool) -> Unit,
    drag: ToolDragController,
) {
    val customizing = state.panel == PanelMode.TOOLBOX
    ToolCircle(
        icon = Icons.Outlined.GridView,
        description = "Toolbox",
        active = customizing,
        modifier = Modifier.padding(horizontal = 3.dp),
    ) { onPanelChange(PanelMode.TOOLBOX) }
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .onGloballyPositioned { drag.toolbarBounds = it.boundsInRoot() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tool in state.settings.toolbarTools) {
            val cell = if (state.settings.toolbarGreedy) {
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            } else {
                Modifier.padding(horizontal = 3.dp)
            }
            Box(cell, contentAlignment = Alignment.Center) {
                DraggableTool(tool, fromToolbar = true, enabled = customizing, drag = drag) { dragModifier ->
                    ToolCircle(
                        icon = toolIcon(tool),
                        description = toolLabel(tool),
                        active = toolActive(tool, state),
                        modifier = dragModifier,
                    ) { onToolTap(tool) }
                }
            }
        }
        if (!state.settings.toolbarGreedy) Spacer(modifier = Modifier.weight(1f))
    }
    if (state.settings.incognito) {
        Text(
            "🕶",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}

/**
 * Gboard-style toolbox: every tool that is not on the toolbar, shown in a
 * labeled grid. Tap to use a tool in place; hold and drag it up onto the
 * toolbar to pin it. Toolbar tools drag down here to unpin.
 */
@Composable
private fun ToolboxPanel(
    state: KeyboardUiState,
    onToolTap: (ToolbarTool) -> Unit,
    drag: ToolDragController,
) {
    val height = keyRowsHeight(state.settings)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Text(
            "Hold and drag a tool onto the toolbar to pin it — or drag a toolbar tool down here to remove it.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
        val available = ToolbarTool.entries.filter { it !in state.settings.toolbarTools }
        if (available.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Every tool is on the toolbar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        for (rowTools in available.chunked(4)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (tool in rowTools) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DraggableTool(tool, fromToolbar = false, enabled = true, drag = drag) { dragModifier ->
                            Column(
                                modifier = dragModifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ToolCircle(
                                    icon = toolIcon(tool),
                                    description = toolLabel(tool),
                                    active = toolActive(tool, state),
                                ) { onToolTap(tool) }
                                Text(
                                    toolLabel(tool),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                repeat(4 - rowTools.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Toolbar + panels + key rows, wrapped in a Box so the tool-drag ghost can
 * float over everything while the toolbox is open.
 */
@Composable
private fun KeyboardBody(
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit,
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLanguageSelect: (InputMode) -> Unit,
    onSuggestion: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onEmojiRecentsClear: () -> Unit,
    onTextEdit: (TextEditAction) -> Unit,
    onPanelChange: (PanelMode) -> Unit,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onSnippet: (Snippet) -> Unit,
    onToolTap: (ToolbarTool) -> Unit,
    onToolbarToolsChange: (List<ToolbarTool>) -> Unit,
) {
    val drag = remember { ToolDragController() }
    drag.currentTools = state.settings.toolbarTools
    drag.onCommit = onToolbarToolsChange
    var bodyOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bodyOrigin = it.positionInRoot() },
    ) {
        Column {
            TopBar(state, onSuggestion, onPanelChange, onToolTap, drag)
            when (state.panel) {
                PanelMode.EMOJI -> EmojiPanel(state, onEmoji, onEmojiQueryTap, onEmojiRecentsClear)
                PanelMode.CLIPBOARD -> ClipboardPanel(state, onClipboardItem, onClipboardPin, onClipboardDelete)
                PanelMode.SNIPPETS -> SnippetsPanel(state, onSnippet)
                PanelMode.TEXT_EDIT -> TextEditPanel(state, onTextEdit)
                PanelMode.TOOLBOX -> ToolboxPanel(state, onToolTap, drag)
                PanelMode.NONE -> KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLanguageSelect)
            }
            // In emoji search mode the letters stay visible for typing the query.
            if (state.panel == PanelMode.EMOJI && state.emojiSearchActive) {
                KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLanguageSelect)
            }
        }
        drag.dragging?.let { tool ->
            val kb = LocalKbTheme.current
            val ghost = drag.position - bodyOrigin
            Box(
                modifier = Modifier
                    .offset { IntOffset((ghost.x - 22.dp.toPx()).roundToInt(), (ghost.y - 22.dp.toPx()).roundToInt()) }
                    .size(44.dp)
                    .background(kb.toolCircleActive, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    toolIcon(tool),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = kb.toolCircleActiveIcon,
                )
            }
        }
    }
}

// ---- key grid ----

@Composable
private fun KeyRows(
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onCursorMove: (Int) -> Unit = {},
    onLanguageSelect: (InputMode) -> Unit = {},
) {
    val layout = currentLayout(state)
    val gestureEnabled = state.settings.gestureTyping &&
        state.layoutMode == LayoutMode.LETTERS &&
        state.inputMode == InputMode.ENGLISH &&
        state.panel == PanelMode.NONE

    // Letter-key centres and width, captured from layout in this Box's space.
    val keyCenters = remember { mutableStateMapOf<Char, Offset>() }
    var keyWidthPx by remember { mutableStateOf(0f) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var trail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val trailColor = LocalKbTheme.current.accent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { boxOrigin = it.positionInRoot() }
            .pointerInput(gestureEnabled) {
                if (!gestureEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    var isGesture = false
                    val points = ArrayList<Offset>()
                    points.add(down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (isGesture) change.consume()
                            break
                        }
                        points.add(change.position)
                        if (!isGesture && keyWidthPx > 0f &&
                            (change.position - down.position).getDistance() > slop * 2 &&
                            nearLetterKey(down.position, keyCenters, keyWidthPx)
                        ) {
                            isGesture = true
                        }
                        if (isGesture) {
                            change.consume()
                            trail = points.toList()
                            // Live preview: decode every few samples.
                            if (points.size % 6 == 0) {
                                onGesturePreview(
                                    points.map { GesturePoint(it.x, it.y) },
                                    keyCenters.map { (char, center) -> KeyCenter(char, center.x, center.y) },
                                    keyWidthPx,
                                )
                            }
                        }
                    }
                    if (isGesture && points.size >= 4) {
                        onGesture(
                            points.map { GesturePoint(it.x, it.y) },
                            keyCenters.map { (char, center) -> KeyCenter(char, center.x, center.y) },
                            keyWidthPx,
                        )
                    }
                    trail = emptyList()
                }
            },
    ) {
        // No spacing between cells: each key's touch target fills its whole
        // grid cell (gaps included) so a press landing between two keys
        // still hits the nearest one. The visual gap comes from per-key
        // padding inside KeyButton.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.5.dp, vertical = KeyRowsPadVertical),
        ) {
            val onLetterPositioned: (Char, LayoutCoordinates) -> Unit = { letter, coords ->
                val topLeft = coords.positionInRoot() - boxOrigin
                keyCenters[letter] = Offset(
                    topLeft.x + coords.size.width / 2f,
                    topLeft.y + coords.size.height / 2f,
                )
                keyWidthPx = coords.size.width.toFloat()
            }
            // Rows narrower than the top row (e.g. the 9-key QWERTY home row)
            // keep the top row's key width and are centered with side gaps,
            // instead of stretching their keys to fill the full width.
            val gridWeight = layout.rows.first().map { it.width }.sum()
            val split = state.settings.splitKeyboard
            val splitGapPercent = state.settings.splitGapPercent
            // The number row stays on every layer (letters and symbols alike),
            // matching Gboard, so switching layers never changes the height.
            if (state.settings.numberRow) {
                val digits = remember { "1234567890".map { Key(it.toString()) } }
                KeyRow(
                    keys = digits,
                    gridWeight = digits.size.toFloat(),
                    split = split,
                    splitGapPercent = splitGapPercent,
                    keyHeightDp = state.settings.numberRowHeightDp,
                    state = state,
                    onKey = onKey,
                    onText = onText,
                    onCursorMove = onCursorMove,
                    onLanguageSelect = onLanguageSelect,
                    onLetterPositioned = onLetterPositioned,
                )
            }
            for (row in layout.rows) {
                KeyRow(
                    keys = row,
                    gridWeight = gridWeight,
                    split = split,
                    splitGapPercent = splitGapPercent,
                    keyHeightDp = state.settings.keyHeightDp,
                    state = state,
                    onKey = onKey,
                    onText = onText,
                    onCursorMove = onCursorMove,
                    onLanguageSelect = onLanguageSelect,
                    onLetterPositioned = onLetterPositioned,
                )
            }
        }

        if (trail.size > 1) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path()
                path.moveTo(trail.first().x, trail.first().y)
                for (point in trail.drop(1)) path.lineTo(point.x, point.y)
                drawPath(
                    path = path,
                    color = trailColor.copy(alpha = 0.55f),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/**
 * One row of keys. In split mode the row is cut near its midpoint and the
 * halves are pushed apart by a center gap sized as a percentage of the
 * keyboard width; a spacebar straddling the cut is divided between the halves.
 */
@Composable
private fun KeyRow(
    keys: List<Key>,
    gridWeight: Float,
    split: Boolean,
    splitGapPercent: Int,
    keyHeightDp: Int,
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLanguageSelect: (InputMode) -> Unit,
    onLetterPositioned: (Char, LayoutCoordinates) -> Unit,
) {
    val sidePad = (gridWeight - keys.map { it.width }.sum()) / 2f
    Row {
        if (sidePad > 0.01f) Spacer(modifier = Modifier.weight(sidePad))
        if (split) {
            val (left, right) = remember(keys) { splitKeys(keys) }
            for (key in left) {
                KeyCell(key, keyHeightDp, state, onKey, onText, onCursorMove, onLanguageSelect, onLetterPositioned)
            }
            Spacer(modifier = Modifier.weight(gridWeight * splitGapPercent / 100f))
            for (key in right) {
                KeyCell(key, keyHeightDp, state, onKey, onText, onCursorMove, onLanguageSelect, onLetterPositioned)
            }
        } else {
            for (key in keys) {
                KeyCell(key, keyHeightDp, state, onKey, onText, onCursorMove, onLanguageSelect, onLetterPositioned)
            }
        }
        if (sidePad > 0.01f) Spacer(modifier = Modifier.weight(sidePad))
    }
}

@Composable
private fun RowScope.KeyCell(
    key: Key,
    keyHeightDp: Int,
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLanguageSelect: (InputMode) -> Unit,
    onLetterPositioned: (Char, LayoutCoordinates) -> Unit,
) {
    val letter = key.label.singleOrNull()?.takeIf {
        key.action == KeyAction.Text && it.isLetter()
    }
    KeyButton(
        key = key,
        state = state,
        modifier = if (letter != null) {
            Modifier
                .weight(key.width)
                .onGloballyPositioned { onLetterPositioned(letter.lowercaseChar(), it) }
        } else {
            Modifier.weight(key.width)
        },
        heightDp = keyHeightDp,
        onKey = onKey,
        onText = onText,
        onCursorMove = onCursorMove,
        onLanguageSelect = onLanguageSelect,
    )
}

/**
 * Cuts a row for split mode. A spacebar spanning the midpoint is divided
 * into a half per side (the left half loses its label so the language name
 * is not shown twice); otherwise the cut lands on the key boundary nearest
 * the midpoint, with ties going right so QWERTY splits asdfg | hjkl.
 */
internal fun splitKeys(keys: List<Key>): Pair<List<Key>, List<Key>> {
    val boundaries = FloatArray(keys.size + 1)
    for (i in keys.indices) boundaries[i + 1] = boundaries[i] + keys[i].width
    val mid = boundaries[keys.size] / 2f
    for (i in keys.indices) {
        if (keys[i].action == KeyAction.Space &&
            boundaries[i] < mid - 0.01f && boundaries[i + 1] > mid + 0.01f
        ) {
            val left = keys.subList(0, i) + keys[i].copy(label = "", width = mid - boundaries[i])
            val right = listOf(keys[i].copy(width = boundaries[i + 1] - mid)) +
                keys.subList(i + 1, keys.size)
            return left to right
        }
    }
    var cut = 1
    for (b in 2 until keys.size) {
        if (abs(boundaries[b] - mid) <= abs(boundaries[cut] - mid) + 0.001f) cut = b
    }
    return keys.subList(0, cut) to keys.subList(cut, keys.size)
}

/** True when [position] falls within roughly one key of a tracked letter key. */
private fun nearLetterKey(position: Offset, centers: Map<Char, Offset>, keyWidth: Float): Boolean =
    centers.values.any { (it - position).getDistance() < keyWidth }

private fun currentLayout(state: KeyboardUiState): KeyboardLayout {
    val base = when (state.layoutMode) {
        LayoutMode.SYMBOLS -> Layouts.SYMBOLS
        LayoutMode.SYMBOLS_SHIFTED -> Layouts.SYMBOLS_SHIFTED
        LayoutMode.LETTERS -> when (state.inputMode) {
            InputMode.PROBHAT -> Layouts.PROBHAT
            InputMode.JATIYA -> Layouts.JATIYA
            else -> Layouts.QWERTY
        }
    }
    // Optional Gboard-style emoji key: the letter layouts' comma key becomes
    // an emoji-panel key, with comma demoted to its long-press alternates.
    val commaAsEmoji = state.settings.commaAsEmoji && state.layoutMode == LayoutMode.LETTERS
    // 🌐 → emoji key: language switching lives on spacebar swipes instead.
    val globeAsEmoji = state.settings.globeAsEmoji
    if (!commaAsEmoji && !globeAsEmoji) return base
    return KeyboardLayout(
        base.name,
        base.rows.map { row ->
            row.map { key ->
                when {
                    commaAsEmoji && key.action == KeyAction.Text && key.label == "," ->
                        Key(",", action = KeyAction.Emoji, longPress = listOf(",") + key.longPress)
                    globeAsEmoji && key.action == KeyAction.LanguageSwitch ->
                        key.copy(action = KeyAction.Emoji)
                    else -> key
                }
            }
        },
    )
}

/**
 * Places a popup centered above its anchor with a clear gap, so the
 * character bubble and long-press alternates are not hidden under the
 * pressing finger.
 */
private class AboveAnchorPopupPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun rememberAboveAnchorPopup(): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density) {
        AboveAnchorPopupPositionProvider(with(density) { 10.dp.roundToPx() })
    }
}

/**
 * Places the popup so its bottom edge lines up with the pressed key's
 * bottom, growing upward from the key itself — the tall stock-keyboard
 * style where the bubble visually replaces the key.
 */
private object OnKeyPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.bottom - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** Visual gap between keys, provided as padding inside each touch cell. */
private val KeyGapHorizontal = 2.5.dp
private val KeyGapVertical = 4.dp

/** Vertical padding of the [KeyRows] column, mirrored into [keyRowsHeight]. */
private val KeyRowsPadVertical = 2.dp

/**
 * Exact height of [KeyRows]: four key rows (each key height plus its
 * vertical gaps), the optional number row, and the column padding. Every
 * panel sizes itself with this so opening a tool or switching layers never
 * changes the keyboard's height under the user's fingers.
 */
internal fun keyRowsHeight(settings: KeyboardSettings): Dp {
    var height = (settings.keyHeightDp.dp + KeyGapVertical * 2) * 4 + KeyRowsPadVertical * 2
    if (settings.numberRow) {
        height += settings.numberRowHeightDp.dp + KeyGapVertical * 2
    }
    return height
}

@Composable
private fun KeyButton(
    key: Key,
    state: KeyboardUiState,
    modifier: Modifier,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit = {},
    onLanguageSelect: (InputMode) -> Unit = {},
    heightDp: Int? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    var showAlternates by remember { mutableStateOf(false) }
    // Language the spacebar swipe currently has selected, shown in a tooltip
    // popup above the spacebar while the finger is still down.
    var languagePreview by remember { mutableStateOf<InputMode?>(null) }
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val onKeyPress = LocalKeyPressFeedback.current

    // The preview bubble outlives the physical press by up to the minimum
    // popup duration, so a fast tap still shows a readable bubble instead
    // of a single-frame flash.
    var previewVisible by remember { mutableStateOf(false) }
    var previewShownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pressed) {
        if (pressed) {
            previewShownAt = SystemClock.uptimeMillis()
            previewVisible = true
        } else if (previewVisible) {
            val remaining = settings.keyPopupMinDurationMs -
                (SystemClock.uptimeMillis() - previewShownAt)
            if (remaining > 0) delay(remaining)
            previewVisible = false
        }
    }

    // Samsung-style contrast: letter keys clearly lighter than the board,
    // modifier keys a shade darker than the letters.
    val kb = LocalKbTheme.current
    val background = when {
        pressed -> kb.pressedKey
        key.action == KeyAction.Enter -> kb.enterKey
        key.action != KeyAction.Text -> kb.modifierKey
        else -> kb.key
    }
    val contentColor = when {
        key.action == KeyAction.Enter && !pressed -> kb.enterKeyText
        key.action != KeyAction.Text -> kb.modifierKeyText
        else -> kb.keyText
    }
    val keyShape = RoundedCornerShape(kb.keyRadiusDp.dp)

    // Outer box = full grid cell and the touch target; inner box = the
    // visible key, inset by the gap. Presses in the gap between keys land
    // on whichever cell they fall in, so there are no dead zones.
    val density = LocalDensity.current
    var keyWidthPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .height((heightDp ?: settings.keyHeightDp).dp + KeyGapVertical * 2)
            .pointerInputKey(key, settings.longPressDelayMs, settings.keyRepeatIntervalMs,
                spaceShortSwipe = settings.spaceShortSwipe,
                spaceLongSwipe = settings.spaceLongSwipe,
                enabledModes = settings.enabledModes.ifEmpty { listOf(InputMode.ENGLISH) },
                currentMode = state.inputMode,
                setPressed = { pressed = it },
                onKeyPress = onKeyPress,
                hapticOnLongPress = settings.hapticOnLongPress,
                hapticOnLongPressRelease = settings.hapticOnLongPressRelease,
                openAlternates = { showAlternates = true },
                onKey = onKey,
                onCursorMove = onCursorMove,
                onLanguageSelect = onLanguageSelect,
                setLanguagePreview = { languagePreview = it },
                scope = scope)
            .padding(horizontal = KeyGapHorizontal, vertical = KeyGapVertical)
            .background(background, keyShape)
            .then(
                if (kb.keyBorder != null && kb.keyBorderWidthDp > 0f) {
                    Modifier.border(kb.keyBorderWidthDp.dp, kb.keyBorder, keyShape)
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { keyWidthPx = it.size.width },
        contentAlignment = Alignment.Center,
    ) {
        KeyContent(key, state, contentColor)
        val popupPosition = rememberAboveAnchorPopup()

        if (showAlternates && key.longPress.isNotEmpty()) {
            Popup(
                popupPositionProvider = popupPosition,
                onDismissRequest = { showAlternates = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (alternate in key.longPress) {
                            Text(
                                text = alternate,
                                modifier = Modifier
                                    .clickable {
                                        showAlternates = false
                                        onText(alternate)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                fontSize = (18 * settings.popupFontScale).sp,
                                color = kb.popupText,
                            )
                        }
                    }
                }
            }
        }

        // Key preview bubble while pressed. In on-key mode the bubble
        // grows upward from the pressed key itself (stock-keyboard style,
        // key-wide with a large label near the top, clear of the finger);
        // otherwise it floats above the fingertip.
        if (previewVisible && settings.keyPopup && key.action == KeyAction.Text && !showAlternates) {
            val onKeyStyle = settings.keyPopupOnKey
            Popup(
                popupPositionProvider = if (onKeyStyle) OnKeyPopupPositionProvider else popupPosition,
                properties = PreviewPopupProperties,
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .height(settings.keyPopupHeightDp.dp)
                            .widthIn(min = if (onKeyStyle) with(density) { keyWidthPx.toDp() } + 8.dp else 0.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = if (onKeyStyle) Alignment.TopCenter else Alignment.Center,
                    ) {
                        Text(
                            text = displayLabel(key, state),
                            modifier = if (onKeyStyle) Modifier.padding(top = 8.dp) else Modifier,
                            fontSize = ((if (onKeyStyle) 34 else 22) * settings.popupFontScale).sp,
                            color = kb.popupText,
                        )
                    }
                }
            }
        }

        // Tooltip above the spacebar while a swipe is cycling languages:
        // every enabled mode in a row, the live selection highlighted.
        languagePreview?.let { previewMode ->
            val enabledModes = settings.enabledModes.ifEmpty { listOf(InputMode.ENGLISH) }
            Popup(
                popupPositionProvider = popupPosition,
                properties = PreviewPopupProperties,
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (mode in enabledModes) {
                            val selected = mode == previewMode
                            Text(
                                text = languageDisplayName(mode),
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .background(
                                        if (selected) kb.pressedKey else Color.Transparent,
                                        RoundedCornerShape(kb.popupRadiusDp.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = (14 * settings.popupFontScale).sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) kb.popupText else kb.popupText.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun languageDisplayName(mode: InputMode): String = when (mode) {
    InputMode.ENGLISH -> "English"
    InputMode.AVRO -> "বাংলা · Avro"
    InputMode.PROBHAT -> "প্রভাত"
    InputMode.JATIYA -> "জাতীয়"
}

@Composable
private fun KeyContent(key: Key, state: KeyboardUiState, contentColor: Color) {
    val fontScale = state.settings.fontScale
    when (key.action) {
        KeyAction.Shift -> Icon(
            when (state.shiftState) {
                ShiftState.CAPS_LOCK -> KeyboardIcons.ShiftLock
                ShiftState.ON -> KeyboardIcons.ShiftFilled
                ShiftState.OFF -> KeyboardIcons.Shift
            },
            contentDescription = when (state.shiftState) {
                ShiftState.CAPS_LOCK -> "Caps lock on"
                ShiftState.ON -> "Shift on"
                ShiftState.OFF -> "Shift"
            },
            tint = if (state.shiftState != ShiftState.OFF) MaterialTheme.colorScheme.primary else contentColor,
        )
        KeyAction.Delete -> Icon(
            Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = "Delete",
            tint = contentColor,
        )
        KeyAction.Enter -> Icon(
            when (state.enterAction) {
                EnterAction.SEARCH -> Icons.Outlined.Search
                EnterAction.SEND -> Icons.AutoMirrored.Outlined.Send
                EnterAction.GO -> Icons.AutoMirrored.Outlined.ArrowForward
                EnterAction.NEXT -> Icons.AutoMirrored.Outlined.KeyboardTab
                EnterAction.PREVIOUS -> Icons.AutoMirrored.Outlined.ArrowBack
                EnterAction.DONE -> Icons.Outlined.Check
                EnterAction.DEFAULT -> Icons.AutoMirrored.Outlined.KeyboardReturn
            },
            contentDescription = "Enter",
            tint = contentColor,
        )
        KeyAction.LanguageSwitch -> Icon(
            Icons.Outlined.Language,
            contentDescription = "Switch language",
            tint = contentColor,
        )
        KeyAction.Emoji -> Icon(
            Icons.Outlined.EmojiEmotions,
            contentDescription = "Emoji",
            tint = contentColor,
        )
        KeyAction.Space -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Split-spacebar left halves carry an empty label: no language name.
            if (key.label.isNotEmpty()) Text(
                text = when (state.inputMode) {
                    InputMode.ENGLISH -> "English"
                    InputMode.AVRO -> "বাংলা · Avro"
                    InputMode.PROBHAT -> "বাংলা · প্রভাত"
                    InputMode.JATIYA -> "বাংলা · জাতীয়"
                },
                fontSize = (11 * fontScale).sp,
                color = contentColor.copy(alpha = 0.5f),
            )
        }
        else -> {
            // Multi-character mode labels (?123, ABC, =\<) read as labels,
            // not characters — render them clearly smaller than letters.
            val isModeLabel = key.action != KeyAction.Text && key.label.length > 1
            Text(
                text = displayLabel(key, state),
                fontSize = ((if (isModeLabel) 15.6f else 23f) * fontScale).sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

private fun displayLabel(key: Key, state: KeyboardUiState): String {
    val raw = when {
        state.shiftState != ShiftState.OFF && key.shiftLabel != null -> key.shiftLabel
        // Latin letter labels track the live shift state: lowercase normally,
        // uppercase while shift or caps lock is active.
        state.shiftState != ShiftState.OFF && key.action == KeyAction.Text &&
            !state.inputMode.isFixedBengali &&
            key.label.singleOrNull()?.code?.let { it in 'a'.code..'z'.code } == true ->
            key.label.uppercase()
        else -> key.label
    }
    // Fixed Bengali layouts: vowel keys track the cursor context — the
    // independent letter (আ, ই …) at a word start, the kar (া, ি …) after a
    // consonant, the য়-glide (য়া) after a vowel — matching what the key
    // will actually commit.
    if (state.inputMode.isFixedBengali && key.action == KeyAction.Text &&
        state.vowelForm != BengaliGraphemes.VowelKeyForm.KAR
    ) {
        raw.singleOrNull()
            ?.let { BengaliGraphemes.vowelKeyText(it, state.vowelForm) }
            ?.let { return it }
    }
    return raw
}

/**
 * The preview bubble is a separate window that lingers briefly after release
 * and, in on-key mode, covers the key itself plus part of the row above. It
 * must never intercept touches, or rapid re-taps land on the bubble window
 * and get dropped before the keyboard sees them.
 */
private val PreviewPopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
)

/**
 * Press handling: tap commits, long-press opens alternates (or begins
 * repeating for delete), release cancels. The spacebar instead supports
 * horizontal swipes: a swipe that starts moving right away performs
 * [spaceShortSwipe], one that begins after holding the spacebar past the
 * long-press delay performs [spaceLongSwipe] — cursor movement steps the
 * text cursor, language switching cycles the enabled input modes with a
 * live tooltip preview and commits on release. Implemented with raw press
 * detection so repeat, popup and drag can share the gesture.
 */
private fun Modifier.pointerInputKey(
    key: Key,
    longPressDelayMs: Int,
    repeatIntervalMs: Int,
    spaceShortSwipe: SpaceSwipeAction,
    spaceLongSwipe: SpaceSwipeAction,
    enabledModes: List<InputMode>,
    currentMode: InputMode,
    setPressed: (Boolean) -> Unit,
    onKeyPress: () -> Unit,
    hapticOnLongPress: Boolean,
    hapticOnLongPressRelease: Boolean,
    openAlternates: () -> Unit,
    onKey: (Key) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLanguageSelect: (InputMode) -> Unit,
    setLanguagePreview: (InputMode?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = this.then(
    if (key.action == KeyAction.Space &&
        (spaceShortSwipe != SpaceSwipeAction.NONE || spaceLongSwipe != SpaceSwipeAction.NONE)
    ) {
        Modifier.pointerInput(
            key, spaceShortSwipe, spaceLongSwipe, enabledModes, currentMode, longPressDelayMs,
        ) {
            val slopPx = 12.dp.toPx()
            val cursorStepPx = 16.dp.toPx()
            val langStepPx = 44.dp.toPx()
            // Extra travel demanded before the language list wraps around at
            // either end — the boundary acts like a detent, not a wall.
            val langWrapPx = langStepPx * 2.5f
            awaitEachGesture {
                val down = awaitFirstDown()
                setPressed(true)
                onKeyPress()
                // Resolved on the first movement past the slop; null until
                // then (and forever for a plain tap).
                var action: SpaceSwipeAction? = null
                var accumulated = 0f
                var lastX = down.position.x
                var langIndex = enabledModes.indexOf(currentMode).coerceAtLeast(0)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (action == null) {
                        val totalDx = change.position.x - down.position.x
                        if (abs(totalDx) > slopPx) {
                            // Short vs long is decided by hold time, not travel
                            // distance — a fast flick covers more ground than a
                            // careful drag, so distance can't tell them apart.
                            val elapsed = change.uptimeMillis - down.uptimeMillis
                            action = if (elapsed < longPressDelayMs) spaceShortSwipe else spaceLongSwipe
                            lastX = change.position.x
                            accumulated = 0f
                            if (action == SpaceSwipeAction.LANGUAGE) {
                                // The movement that crossed the slop already
                                // counts: a quick flick switches one language.
                                // At a list end the flick parks on the boundary
                                // — wrapping needs a continued drag past the
                                // langWrapPx detent below.
                                val flicked = (langIndex + if (totalDx > 0) 1 else -1)
                                    .coerceIn(0, enabledModes.size - 1)
                                if (flicked != langIndex) {
                                    langIndex = flicked
                                    onKeyPress()
                                }
                                setLanguagePreview(enabledModes[langIndex])
                            }
                            change.consume()
                        }
                        continue
                    }
                    accumulated += change.position.x - lastX
                    lastX = change.position.x
                    when (action) {
                        SpaceSwipeAction.CURSOR -> {
                            var moved = false
                            while (accumulated > cursorStepPx) {
                                onCursorMove(1); accumulated -= cursorStepPx; moved = true
                            }
                            while (accumulated < -cursorStepPx) {
                                onCursorMove(-1); accumulated += cursorStepPx; moved = true
                            }
                            if (moved) change.consume()
                        }
                        SpaceSwipeAction.LANGUAGE -> {
                            // The list ends put up resistance instead of
                            // wrapping immediately: a wrap costs langWrapPx of
                            // travel (vs langStepPx per normal step), so the
                            // selection parks on the boundary language first
                            // and only cycles around on a deliberate pull.
                            val last = enabledModes.size - 1
                            var stepped = false
                            while (true) {
                                if (accumulated > langStepPx && langIndex < last) {
                                    langIndex++
                                    accumulated -= langStepPx
                                } else if (accumulated > langWrapPx && langIndex == last && last > 0) {
                                    langIndex = 0
                                    accumulated -= langWrapPx
                                } else if (accumulated < -langStepPx && langIndex > 0) {
                                    langIndex--
                                    accumulated += langStepPx
                                } else if (accumulated < -langWrapPx && langIndex == 0 && last > 0) {
                                    langIndex = last
                                    accumulated += langWrapPx
                                } else {
                                    break
                                }
                                stepped = true
                            }
                            if (stepped) {
                                setLanguagePreview(enabledModes[langIndex])
                                onKeyPress()
                            }
                            change.consume()
                        }
                        // NONE: the swipe is deliberately inert — swallow it
                        // so release does not type a space.
                        else -> change.consume()
                    }
                }
                setPressed(false)
                setLanguagePreview(null)
                when (action) {
                    null -> onKey(key)
                    SpaceSwipeAction.LANGUAGE -> {
                        val selected = enabledModes[langIndex]
                        if (selected != currentMode) onLanguageSelect(selected)
                    }
                    else -> {}
                }
            }
        }
    } else {
        // Settings are part of the pointerInput keys: pointerInput only
        // restarts when its keys change, so leaving them out would keep a
        // stale closure alive (e.g. release haptics still firing after the
        // toggle was turned off).
        Modifier.pointerInput(key, spaceShortSwipe, spaceLongSwipe, longPressDelayMs, repeatIntervalMs,
            hapticOnLongPress, hapticOnLongPressRelease) {
            // Raw per-pointer tracking rather than detectTapGestures, which
            // handles one gesture at a time per key: a second finger landing
            // on the same key before the first lifts (burst double-taps) was
            // swallowed. Here every pointer gets its own press lifecycle.
            class Press {
                var longPressFired = false
                var job: Job? = null
            }
            val presses = HashMap<PointerId, Press>()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        val press = presses[change.id]
                        when {
                            press == null && change.changedToDown() -> {
                                val p = Press()
                                presses[change.id] = p
                                setPressed(true)
                                onKeyPress()
                                p.job = scope.launch {
                                    delay(longPressDelayMs.toLong())
                                    p.longPressFired = true
                                    if (key.action == KeyAction.Delete || key.action == KeyAction.Space) {
                                        while (true) {
                                            onKeyPress()
                                            onKey(key)
                                            delay(repeatIntervalMs.toLong())
                                        }
                                    } else if (key.longPress.isNotEmpty()) {
                                        // Tactile cue that the long press registered and the
                                        // finger can be released (alternates are open / the
                                        // long-press action fired). Delete/space skip it:
                                        // their repeat loop already buzzes per repeat.
                                        if (hapticOnLongPress) onKeyPress()
                                        openAlternates()
                                    } else {
                                        // No alternates: long press behaves like a tap.
                                        if (hapticOnLongPress) onKeyPress()
                                        onKey(key)
                                    }
                                }
                            }
                            // Another handler claimed the pointer (glide typing
                            // consumed the move/up on the Initial pass): the
                            // press must not commit.
                            press != null && change.isConsumed -> {
                                press.job?.cancel()
                                presses.remove(change.id)
                                if (presses.isEmpty()) setPressed(false)
                            }
                            press != null && change.changedToUp() -> {
                                change.consume()
                                press.job?.cancel()
                                presses.remove(change.id)
                                if (presses.isEmpty()) setPressed(false)
                                // Forgiving bounds: a sloppy fast tap that drifts
                                // slightly off the cell still commits; a deliberate
                                // slide well away (≥ half a key beyond the edge)
                                // cancels, preserving slide-off-to-cancel.
                                val inBounds =
                                    change.position.x > -size.width * 0.5f &&
                                        change.position.x < size.width * 1.5f &&
                                        change.position.y > -size.height * 0.5f &&
                                        change.position.y < size.height * 1.5f
                                if (!press.longPressFired) {
                                    if (inBounds) onKey(key)
                                } else if (hapticOnLongPressRelease) {
                                    onKeyPress()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
)

// ---- emoji panel ----

/** Sentinel tab id for the recents tab; ★ avoids clashing with catalog categories. */
private const val RECENT_TAB = "★recent"

/** Category → tab icon; falls back to the smiley for unknown categories. */
private fun emojiTabIcon(tab: String): ImageVector = when (tab) {
    RECENT_TAB -> Icons.Outlined.Schedule
    "smileys" -> Icons.Outlined.EmojiEmotions
    "people" -> Icons.Outlined.EmojiPeople
    "animals" -> Icons.Outlined.Pets
    "nature" -> Icons.Outlined.EmojiNature
    "food" -> Icons.Outlined.Fastfood
    "travel" -> Icons.Outlined.DirectionsCar
    "activities" -> Icons.Outlined.SportsSoccer
    "objects" -> Icons.Outlined.EmojiObjects
    "symbols" -> Icons.Outlined.EmojiSymbols
    "flags" -> Icons.Outlined.EmojiFlags
    else -> Icons.Outlined.EmojiEmotions
}

/**
 * One compact emoji tab: a 20dp icon over a 2dp selection bar, in a plain
 * weighted cell so search + every category share the row evenly.
 */
@Composable
private fun RowScope.EmojiTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun EmojiPanel(
    state: KeyboardUiState,
    onEmoji: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onClearRecents: () -> Unit,
) {
    val height = if (state.emojiSearchActive) 120.dp else keyRowsHeight(state.settings)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // The search field only shows while a search is underway; idle, the
        // entry point is the first icon of the tab strip below, so the panel
        // doesn't spend a whole bar of vertical space on it.
        if (state.emojiSearchActive || state.emojiQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
                    .clickable { onEmojiQueryTap() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = state.emojiQuery.ifEmpty { "Type to search…" },
                    color = if (state.emojiQuery.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state.emojiQuery.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(state.emojiResults.map { it.emoji }) { emoji ->
                    EmojiCell(emoji, onEmoji)
                }
            }
            return@Column
        }

        // One category rendered at a time behind tabs: the full catalog in a
        // single grid was a composition/measure hog.
        val categories = remember(state.emojiCatalog) {
            state.emojiCatalog.map { it.category }.distinct()
        }
        val hasRecents = state.emojiRecents.isNotEmpty()
        val tabs = remember(categories, hasRecents) {
            buildList {
                if (hasRecents) add(RECENT_TAB)
                addAll(categories)
            }
        }
        var selectedTab by remember { mutableStateOf(tabs.firstOrNull().orEmpty()) }
        if (selectedTab !in tabs) selectedTab = tabs.firstOrNull().orEmpty()

        // Compact icon strip: search plus every category, split evenly across
        // the width so everything fits with no scrolling — Material's Tab has
        // a 90dp min width that forced a ScrollableTabRow here before.
        if (!state.emojiSearchActive && tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiTab(
                    icon = Icons.Outlined.Search,
                    description = "Search emoji",
                    selected = false,
                    onClick = onEmojiQueryTap,
                )
                for (tab in tabs) {
                    EmojiTab(
                        icon = emojiTabIcon(tab),
                        description = if (tab == RECENT_TAB) "Recent"
                        else tab.replaceFirstChar { it.uppercase() },
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                    )
                }
            }
        }

        if (selectedTab == RECENT_TAB) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))
                TextButton(onClick = onClearRecents) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(modifier = Modifier.width(4.dp))
                    Text("Clear recents", fontSize = 12.sp)
                }
            }
        }

        val emojis = if (selectedTab == RECENT_TAB) {
            state.emojiRecents
        } else {
            remember(state.emojiCatalog, selectedTab) {
                state.emojiCatalog.filter { it.category == selectedTab }.map { it.emoji }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(emojis) { emoji ->
                EmojiCell(emoji, onEmoji)
            }
        }
    }
}

/**
 * One emoji in the grid. Only emojis with skin-tone variants register a
 * long-press handler; everything else is a plain tap with no long-press
 * timeout involved.
 */
@Composable
private fun EmojiCell(emoji: String, onEmoji: (String) -> Unit) {
    var showVariants by remember { mutableStateOf(false) }
    val variants = remember(emoji) { EmojiVariants.variants(emoji) }
    val hasVariants = variants.size > 1
    Box {
        Text(
            text = emoji,
            modifier = Modifier
                .pointerInput(emoji, hasVariants) {
                    if (hasVariants) {
                        detectTapGestures(
                            onTap = { onEmoji(emoji) },
                            onLongPress = { showVariants = true },
                        )
                    } else {
                        detectTapGestures(onTap = { onEmoji(emoji) })
                    }
                }
                .padding(6.dp),
            fontSize = 26.sp,
        )
        if (showVariants) {
            val kb = LocalKbTheme.current
            Popup(
                popupPositionProvider = rememberAboveAnchorPopup(),
                onDismissRequest = { showVariants = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 8.dp,
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        for (variant in variants) {
                            Text(
                                text = variant,
                                modifier = Modifier
                                    .clickable {
                                        showVariants = false
                                        onEmoji(variant)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                fontSize = 24.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- snippets panel ----

@Composable
private fun SnippetsPanel(state: KeyboardUiState, onSnippet: (Snippet) -> Unit) {
    val height = keyRowsHeight(state.settings)
    if (state.snippets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No snippets yet.\nAdd them in Settings → Snippets.\nVariables: {date} {time} {datetime} {clip}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.snippets, key = { it.id }) { snippet ->
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .clickable { onSnippet(snippet) }
                    .padding(10.dp),
            ) {
                Text(
                    text = snippet.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = snippet.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ---- clipboard panel ----

@Composable
private fun ClipboardPanel(
    state: KeyboardUiState,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    if (state.clipboardItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Clipboard history is empty.\nCopied text will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.clipboardItems, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .clickable { onClipboardItem(item) }
                    .padding(10.dp),
            ) {
                if (item.kind == ClipKind.IMAGE) {
                    ClipThumbnail(item)
                } else {
                    Text(
                        text = item.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.kind == ClipKind.HTML) {
                        Text(
                            "Rich text",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = { onClipboardPin(item) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (item.pinned) "Unpin" else "Pin",
                            modifier = Modifier.size(16.dp),
                            tint = if (item.pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onClipboardDelete(item) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Decodes a downsampled preview of an image clip off the main thread. */
@Composable
private fun ClipThumbnail(item: ClipItem) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.imagePath) {
        value = withContext(Dispatchers.IO) {
            val path = item.imagePath ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= THUMBNAIL_TARGET_PX &&
                    bounds.outHeight / (sample * 2) >= THUMBNAIL_TARGET_PX
                ) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Copied image",
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    } ?: Box(modifier)
}

private const val THUMBNAIL_TARGET_PX = 256
