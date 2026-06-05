package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import android.os.Build
import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariants
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.settings.InputMode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
                    .navigationBarsPadding(),
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
                        PanelMode.EMOJI -> EmojiPanel(state, onEmoji, onEmojiQueryTap, onKey, onText)
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
                    Icons.AutoMirrored.Filled.ArrowForward
                } else {
                    Icons.AutoMirrored.Filled.ArrowBack
                },
                contentDescription = "Move keyboard to the other side",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onOneHanded(OneHandedMode.OFF) }) {
            Icon(
                Icons.Filled.Fullscreen,
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
                    Icons.Filled.EmojiEmotions,
                    contentDescription = "Emoji",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.suggestions.isEmpty()) {
            IconButton(onClick = { onPanelChange(PanelMode.CLIPBOARD) }) {
                Icon(
                    Icons.Filled.ContentPaste,
                    contentDescription = "Clipboard",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onPanelChange(PanelMode.SNIPPETS) }) {
                Icon(
                    Icons.Filled.TextSnippet,
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
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(state.suggestions) { suggestion ->
                    Text(
                        text = suggestion,
                        modifier = Modifier
                            .clickable { onSuggestion(suggestion) }
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.settings.numberRow && state.layoutMode == LayoutMode.LETTERS) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
        else -> Layouts.QWERTY
    }
}

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

    Box(
        modifier = modifier
            .height(settings.keyHeightDp.dp)
            .background(background, RoundedCornerShape(settings.keyCornerRadiusDp.dp))
            .pointerInputKey(key, settings.longPressDelayMs, settings.keyRepeatIntervalMs,
                spacebarCursor = settings.spacebarCursor,
                setPressed = { pressed = it },
                openAlternates = { showAlternates = true },
                onKey = onKey,
                onCursorMove = onCursorMove,
                scope = scope),
        contentAlignment = Alignment.Center,
    ) {
        KeyContent(key, state, contentColor)

        if (showAlternates && key.longPress.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showAlternates = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 8.dp,
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        for (alternate in key.longPress) {
                            Text(
                                text = alternate,
                                modifier = Modifier
                                    .clickable {
                                        showAlternates = false
                                        onText(alternate)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                fontSize = (18 * settings.fontScale).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Key preview bubble while pressed.
        if (pressed && settings.keyPopup && key.action == KeyAction.Text && !showAlternates) {
            Popup(alignment = Alignment.TopCenter) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = displayLabel(key, state),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = (22 * settings.fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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
            if (state.shiftState == ShiftState.CAPS_LOCK) {
                Icons.Filled.KeyboardCapslock
            } else {
                Icons.Filled.KeyboardArrowUp
            },
            contentDescription = when (state.shiftState) {
                ShiftState.CAPS_LOCK -> "Caps lock on"
                ShiftState.ON -> "Shift on"
                ShiftState.OFF -> "Shift"
            },
            tint = if (state.shiftState != ShiftState.OFF) MaterialTheme.colorScheme.primary else contentColor,
        )
        KeyAction.Delete -> Icon(
            Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Delete",
            tint = contentColor,
        )
        KeyAction.Enter -> Icon(
            when (state.enterAction) {
                EnterAction.SEARCH -> Icons.Filled.Search
                EnterAction.SEND -> Icons.AutoMirrored.Filled.Send
                EnterAction.GO -> Icons.AutoMirrored.Filled.ArrowForward
                EnterAction.NEXT -> Icons.AutoMirrored.Filled.KeyboardTab
                EnterAction.PREVIOUS -> Icons.AutoMirrored.Filled.ArrowBack
                EnterAction.DONE -> Icons.Filled.Check
                EnterAction.DEFAULT -> Icons.AutoMirrored.Filled.KeyboardReturn
            },
            contentDescription = "Enter",
            tint = contentColor,
        )
        KeyAction.LanguageSwitch -> Icon(
            Icons.Filled.Language,
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
                },
                fontSize = (11 * fontScale).sp,
                color = contentColor.copy(alpha = 0.5f),
            )
        }
        else -> Text(
            text = displayLabel(key, state),
            fontSize = (19 * fontScale).sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

private fun displayLabel(key: Key, state: KeyboardUiState): String = when {
    state.shiftState != ShiftState.OFF && key.shiftLabel != null -> key.shiftLabel
    // Latin letters display uppercase regardless of shift (Samsung style);
    // shift state shows on the shift key and in the committed text.
    key.action == KeyAction.Text && state.inputMode != InputMode.PROBHAT &&
        key.label.singleOrNull()?.code?.let { it in 'a'.code..'z'.code } == true ->
        key.label.uppercase()
    else -> key.label
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

@Composable
private fun EmojiPanel(
    state: KeyboardUiState,
    onEmoji: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
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
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = state.emojiQuery.ifEmpty {
                        if (state.emojiSearchActive) "Type to search…" else "Search emoji (happy, বিড়াল, fire…)"
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

        val emojisToShow: List<Pair<String?, List<String>>> = when {
            state.emojiQuery.isNotEmpty() -> listOf(null to state.emojiResults.map { it.emoji })
            else -> buildList {
                if (state.emojiRecents.isNotEmpty()) add("Recent" to state.emojiRecents)
                state.emojiCatalog.groupBy { it.category }.forEach { (category, entries) ->
                    add(category.replaceFirstChar { it.uppercase() } to entries.map { it.emoji })
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            for ((header, emojis) in emojisToShow) {
                if (header != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = header,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(emojis) { emoji ->
                    EmojiCell(emoji, onEmoji)
                }
            }
        }
    }
}

/** One emoji in the grid; long-press opens skin-tone variants. */
@Composable
private fun EmojiCell(emoji: String, onEmoji: (String) -> Unit) {
    var showVariants by remember { mutableStateOf(false) }
    val variants = remember(emoji) { EmojiVariants.variants(emoji) }
    Box {
        Text(
            text = emoji,
            modifier = Modifier
                .pointerInput(emoji) {
                    detectTapGestures(
                        onTap = { onEmoji(emoji) },
                        onLongPress = {
                            if (variants.size > 1) showVariants = true else onEmoji(emoji)
                        },
                    )
                }
                .padding(6.dp),
            fontSize = 26.sp,
        )
        if (showVariants) {
            Popup(
                alignment = Alignment.TopCenter,
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
                Text(
                    text = item.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Icons.Filled.Delete,
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
