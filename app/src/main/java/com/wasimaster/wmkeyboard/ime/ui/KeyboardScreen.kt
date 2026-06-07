package com.wasimaster.wmkeyboard.ime.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.delay
import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.clipboard.ClipKind
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariants
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.isFixedBengali
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.ime.EnterAction
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutMode
import com.wasimaster.wmkeyboard.ime.PanelMode
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

/** Root composable for the IME. Renders [KeyboardUiState] and forwards input. */
@Composable
fun KeyboardScreen(
    stateFlow: StateFlow<KeyboardUiState>,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit = {},
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onCursorMove: (Int) -> Unit = {},
    onSuggestion: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onEmojiRecentsClear: () -> Unit = {},
    onPanelChange: (PanelMode) -> Unit,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onSnippet: (Snippet) -> Unit = {},
    onOneHanded: (OneHandedMode) -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    val state by stateFlow.collectAsState()

    KeyboardTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
                Column(
                    modifier = Modifier.weight(if (oneHanded == OneHandedMode.OFF) 1f else 0.78f),
                ) {
                    TopBar(state, onSuggestion, onPanelChange, onOpenSettings)
                    when (state.panel) {
                        PanelMode.EMOJI -> EmojiPanel(state, onEmoji, onEmojiQueryTap, onEmojiRecentsClear)
                        PanelMode.CLIPBOARD -> ClipboardPanel(state, onClipboardItem, onClipboardPin, onClipboardDelete)
                        PanelMode.SNIPPETS -> SnippetsPanel(state, onSnippet)
                        PanelMode.NONE -> KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove)
                    }
                    // In emoji search mode the letters stay visible for typing the query.
                    if (state.panel == PanelMode.EMOJI && state.emojiSearchActive) {
                        KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove)
                    }
                }
                if (oneHanded == OneHandedMode.LEFT) {
                    OneHandedRail(current = oneHanded, onOneHanded = onOneHanded, modifier = Modifier.weight(0.22f))
                }
            }
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

@Composable
private fun KeyboardTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = when {
        supportsDynamic && dark -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (themeMode == ThemeMode.AMOLED) {
        scheme = scheme.copy(
            surface = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF111111),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---- top bar: suggestions or toolbar ----

@Composable
private fun TopBar(
    state: KeyboardUiState,
    onSuggestion: (String) -> Unit,
    onPanelChange: (PanelMode) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.settings.emojiToolbar || state.suggestions.isEmpty()) {
            IconButton(onClick = { onPanelChange(PanelMode.EMOJI) }) {
                Icon(
                    Icons.Outlined.EmojiEmotions,
                    contentDescription = "Emoji",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.suggestions.isEmpty()) {
            IconButton(onClick = { onPanelChange(PanelMode.CLIPBOARD) }) {
                Icon(
                    Icons.Outlined.ContentPaste,
                    contentDescription = "Clipboard",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onPanelChange(PanelMode.SNIPPETS) }) {
                Icon(
                    Icons.Outlined.TextSnippet,
                    contentDescription = "Snippets",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.settings.incognito) {
                Text("🕶 incognito", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
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

// ---- key grid ----

@Composable
private fun KeyRows(
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onCursorMove: (Int) -> Unit = {},
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
    val trailColor = MaterialTheme.colorScheme.primary

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
                .padding(horizontal = 1.5.dp, vertical = 2.dp),
        ) {
            if (state.settings.numberRow && state.layoutMode == LayoutMode.LETTERS) {
                Row {
                    "1234567890".forEach { digit ->
                        KeyButton(
                            key = Key(digit.toString()),
                            state = state,
                            modifier = Modifier.weight(1f),
                            onKey = onKey,
                            onText = onText,
                        )
                    }
                }
            }
            for (row in layout.rows) {
                Row {
                    for (key in row) {
                        val letter = key.label.singleOrNull()?.takeIf {
                            key.action == KeyAction.Text && it.isLetter()
                        }
                        KeyButton(
                            key = key,
                            state = state,
                            modifier = if (letter != null) {
                                Modifier
                                    .weight(key.width)
                                    .onGloballyPositioned { coords ->
                                        val topLeft = coords.positionInRoot() - boxOrigin
                                        keyCenters[letter.lowercaseChar()] = Offset(
                                            topLeft.x + coords.size.width / 2f,
                                            topLeft.y + coords.size.height / 2f,
                                        )
                                        keyWidthPx = coords.size.width.toFloat()
                                    }
                            } else {
                                Modifier.weight(key.width)
                            },
                            onKey = onKey,
                            onText = onText,
                            onCursorMove = onCursorMove,
                        )
                    }
                }
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

/** True when [position] falls within roughly one key of a tracked letter key. */
private fun nearLetterKey(position: Offset, centers: Map<Char, Offset>, keyWidth: Float): Boolean =
    centers.values.any { (it - position).getDistance() < keyWidth }

private fun currentLayout(state: KeyboardUiState): KeyboardLayout = when (state.layoutMode) {
    LayoutMode.SYMBOLS -> Layouts.SYMBOLS
    LayoutMode.SYMBOLS_SHIFTED -> Layouts.SYMBOLS_SHIFTED
    LayoutMode.LETTERS -> when (state.inputMode) {
        InputMode.PROBHAT -> Layouts.PROBHAT
        InputMode.JATIYA -> Layouts.JATIYA
        else -> Layouts.QWERTY
    }
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

@Composable
private fun KeyButton(
    key: Key,
    state: KeyboardUiState,
    modifier: Modifier,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    var showAlternates by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settings = state.settings

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
    val background = when {
        pressed -> MaterialTheme.colorScheme.primaryContainer
        key.action == KeyAction.Enter -> MaterialTheme.colorScheme.primary
        key.action != KeyAction.Text -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        key.action == KeyAction.Enter && !pressed -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Outer box = full grid cell and the touch target; inner box = the
    // visible key, inset by the gap. Presses in the gap between keys land
    // on whichever cell they fall in, so there are no dead zones.
    val density = LocalDensity.current
    var keyWidthPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .height(settings.keyHeightDp.dp + KeyGapVertical * 2)
            .pointerInputKey(key, settings.longPressDelayMs, settings.keyRepeatIntervalMs,
                spacebarCursor = settings.spacebarCursor,
                setPressed = { pressed = it },
                openAlternates = { showAlternates = true },
                onKey = onKey,
                onCursorMove = onCursorMove,
                scope = scope)
            .padding(horizontal = KeyGapHorizontal, vertical = KeyGapVertical)
            .background(background, RoundedCornerShape(settings.keyCornerRadiusDp.dp))
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
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                                color = MaterialTheme.colorScheme.onSurface,
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
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
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
        KeyAction.Space -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
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
                fontSize = ((if (isModeLabel) 13 else 19) * fontScale).sp,
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
 * Press handling: tap commits, long-press opens alternates (or begins
 * repeating for delete), release cancels. The spacebar instead supports a
 * horizontal drag that moves the text cursor. Implemented with raw press
 * detection so repeat, popup and drag can share the gesture.
 */
private fun Modifier.pointerInputKey(
    key: Key,
    longPressDelayMs: Int,
    repeatIntervalMs: Int,
    spacebarCursor: Boolean,
    setPressed: (Boolean) -> Unit,
    openAlternates: () -> Unit,
    onKey: (Key) -> Unit,
    onCursorMove: (Int) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = this.then(
    if (key.action == KeyAction.Space && spacebarCursor) {
        Modifier.pointerInput(key) {
            val stepPx = 16.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                setPressed(true)
                var moved = false
                var accumulated = 0f
                var lastX = down.position.x
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    accumulated += change.position.x - lastX
                    lastX = change.position.x
                    while (accumulated > stepPx) {
                        onCursorMove(1)
                        accumulated -= stepPx
                        moved = true
                    }
                    while (accumulated < -stepPx) {
                        onCursorMove(-1)
                        accumulated += stepPx
                        moved = true
                    }
                    if (moved) change.consume()
                }
                setPressed(false)
                if (!moved) onKey(key)
            }
        }
    } else {
        Modifier.pointerInput(key) {
            detectTapGestures(
                onPress = {
                    setPressed(true)
                    var longPressFired = false
                    val longPressJob: Job = scope.launch {
                        delay(longPressDelayMs.toLong())
                        longPressFired = true
                        if (key.action == KeyAction.Delete || key.action == KeyAction.Space) {
                            while (true) {
                                onKey(key)
                                delay(repeatIntervalMs.toLong())
                            }
                        } else if (key.longPress.isNotEmpty()) {
                            openAlternates()
                        } else {
                            // No alternates: long press behaves like a tap.
                            onKey(key)
                        }
                    }
                    // A cancelled press (finger became a swipe gesture) must not commit.
                    val released = tryAwaitRelease()
                    longPressJob.cancel()
                    setPressed(false)
                    if (released && !longPressFired) onKey(key)
                },
            )
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

@Composable
private fun EmojiPanel(
    state: KeyboardUiState,
    onEmoji: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onClearRecents: () -> Unit,
) {
    val height = if (state.emojiSearchActive) 120.dp else (state.settings.keyHeightDp * 4 + 40).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
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
                    text = state.emojiQuery.ifEmpty {
                        if (state.emojiSearchActive) "Type to search…" else "Search emoji (happy, বিড়াল, fire…)"
                    },
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

        if (tabs.isNotEmpty()) {
            val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 4.dp,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    if (selectedIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            ) {
                for (tab in tabs) {
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        icon = {
                            Icon(
                                emojiTabIcon(tab),
                                contentDescription = if (tab == RECENT_TAB) "Recent"
                                else tab.replaceFirstChar { it.uppercase() },
                                modifier = Modifier.size(20.dp),
                            )
                        },
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
            Popup(
                popupPositionProvider = rememberAboveAnchorPopup(),
                onDismissRequest = { showVariants = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
    val height = (state.settings.keyHeightDp * 4 + 40).dp
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
    val height = (state.settings.keyHeightDp * 4 + 40).dp
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
