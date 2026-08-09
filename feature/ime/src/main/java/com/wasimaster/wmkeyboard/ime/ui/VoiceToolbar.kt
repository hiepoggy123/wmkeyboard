package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.settings.VoiceBarSettings
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.ime.VoiceBarAction
import com.wasimaster.wmkeyboard.ime.VoiceStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The collapsed voice bar (Gboard's voice-typing toolbar): the keyboard gives
 * its whole window to a floating pill of dictation controls, so the app behind
 * gets the screen back while the user talks. The service keeps the touchable
 * region down to the pill itself — everything around it falls through.
 *
 * Two orientations share the state machine: the horizontal pill lies along the
 * bottom edge (draggable between left/centre/right rests), the vertical one
 * stands against a side edge (draggable along it, crossing the middle of the
 * screen flips the edge). Both persist where they settle through
 * [VoiceBarAction], the multiplexed command slot.
 */
@Composable
internal fun VoiceBarLayer(
    state: KeyboardUiState,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onRestoreKeyboard: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onLayoutSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(hasVoiceBarMicPermission(context)) }
    // The permission dialog lives in a trampoline activity; re-check when the
    // keyboard comes back to the foreground afterwards (same as the panel).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasVoiceBarMicPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.settings.voiceBar.vertical) {
        VerticalVoiceBar(
            state = state,
            hasPermission = hasPermission,
            onToggle = onToggle,
            onRequestPermission = onRequestPermission,
            onOpenVoiceSettings = onOpenVoiceSettings,
            onRestoreKeyboard = onRestoreKeyboard,
            onAction = onAction,
            onUndo = onUndo,
            onLayoutSelect = onLayoutSelect,
        )
    } else {
        HorizontalVoiceBar(
            state = state,
            hasPermission = hasPermission,
            onToggle = onToggle,
            onUndo = onUndo,
            onRequestPermission = onRequestPermission,
            onOpenVoiceSettings = onOpenVoiceSettings,
            onRestoreKeyboard = onRestoreKeyboard,
            onAction = onAction,
            onLayoutSelect = onLayoutSelect,
        )
    }
}

/** Scratch state for one bar drag. Deliberately not snapshot state. */
private class VoiceBarDrag {
    /** A finger is moving the pill right now. */
    var active = false
    /** Latest measured pill rectangle, published when the pill is at rest. */
    var left = 0
    var top = 0
    var right = 0
    var bottom = 0
    var measured = false
}

private fun VoiceBarDrag.publish(onAction: (VoiceBarAction) -> Unit) {
    if (measured) onAction(VoiceBarAction.Bounds(left, top, right, bottom))
}

@Composable
private fun HorizontalVoiceBar(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onRestoreKeyboard: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onLayoutSelect: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = VoiceBarMargin, vertical = VoiceBarMargin),
    ) {
        val trackPx = constraints.maxWidth
        val pillWidth = remember { mutableStateOf(0) }
        // Live drag position; null = resting at the persisted snap anchor.
        val dragX = remember { mutableStateOf<Float?>(null) }
        val settleX = remember { Animatable(0f) }
        var settling by remember { mutableStateOf(false) }
        val drag = remember { VoiceBarDrag() }
        fun slack() = (trackPx - pillWidth.value).coerceAtLeast(0).toFloat()
        fun anchorFor(snap: Int) = when (snap) {
            VoiceBarSettings.SNAP_LEFT -> 0f
            VoiceBarSettings.SNAP_RIGHT -> slack()
            else -> slack() / 2f
        }
        fun restingX(): Float {
            val live = dragX.value
            return when {
                live != null -> live.coerceIn(0f, slack())
                settling -> settleX.value
                else -> anchorFor(state.settings.voiceBar.snap)
            }
        }

        VoiceBarPill(
            modifier = Modifier
                .widthIn(max = VoiceBarMaxWidth)
                .offset { IntOffset(restingX().roundToInt(), 0) }
                .onGloballyPositioned { coords ->
                    pillWidth.value = coords.size.width
                    drag.record(coords.positionInWindow(), coords.size)
                    if (!drag.active && !settling) drag.publish(onAction)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            drag.active = true
                            settling = false
                            dragX.value = restingX()
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragX.value = ((dragX.value ?: 0f) + delta.x).coerceIn(0f, slack())
                        },
                        onDragCancel = { drag.active = false },
                        onDragEnd = {
                            drag.active = false
                            val end = dragX.value ?: return@detectDragGestures
                            // Nearest of the three rests wins the pill.
                            val snap = listOf(
                                VoiceBarSettings.SNAP_LEFT,
                                VoiceBarSettings.SNAP_CENTER,
                                VoiceBarSettings.SNAP_RIGHT,
                            ).minByOrNull { abs(anchorFor(it) - end) }
                                ?: VoiceBarSettings.SNAP_CENTER
                            onAction(VoiceBarAction.SetSnap(snap))
                            scope.launch {
                                settling = true
                                settleX.snapTo(end)
                                dragX.value = null
                                if (kb.reduceMotion) {
                                    settleX.snapTo(anchorFor(snap))
                                } else {
                                    settleX.animateTo(anchorFor(snap), VoiceBarSettleSpring)
                                }
                                settling = false
                                drag.publish(onAction)
                            }
                        },
                    )
                },
        ) {
            HorizontalBarContent(
                state = state,
                hasPermission = hasPermission,
                onToggle = onToggle,
                onUndo = onUndo,
                onRequestPermission = onRequestPermission,
                onOpenVoiceSettings = onOpenVoiceSettings,
                onRestoreKeyboard = onRestoreKeyboard,
                onAction = onAction,
                onLayoutSelect = onLayoutSelect,
            )
        }
    }
}

@Composable
private fun VerticalVoiceBar(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onRestoreKeyboard: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onUndo: () -> Unit,
    onLayoutSelect: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(VoiceBarMargin),
    ) {
        val boxWidth = constraints.maxWidth
        val boxHeight = constraints.maxHeight
        val pillSize = remember { mutableStateOf(IntSize.Zero) }
        val dragPos = remember { mutableStateOf<Pair<Float, Float>?>(null) }
        val settleX = remember { Animatable(0f) }
        val settleY = remember { Animatable(0f) }
        var settling by remember { mutableStateOf(false) }
        val drag = remember { VoiceBarDrag() }
        fun slackX() = (boxWidth - pillSize.value.width).coerceAtLeast(0).toFloat()
        fun slackY() = (boxHeight - pillSize.value.height).coerceAtLeast(0).toFloat()
        fun edgeAnchor(rightEdge: Boolean) = if (rightEdge) slackX() else 0f
        fun resting(): Pair<Float, Float> {
            val live = dragPos.value
            return when {
                live != null -> live.first.coerceIn(0f, slackX()) to
                    live.second.coerceIn(0f, slackY())
                settling -> settleX.value to settleY.value
                else -> edgeAnchor(state.settings.voiceBar.rightEdge) to
                    state.settings.voiceBar.yBias.coerceIn(0f, 1f) * slackY()
            }
        }

        VoiceBarPill(
            modifier = Modifier
                .offset {
                    val (x, y) = resting()
                    IntOffset(x.roundToInt(), y.roundToInt())
                }
                .onGloballyPositioned { coords ->
                    pillSize.value = coords.size
                    drag.record(coords.positionInWindow(), coords.size)
                    if (!drag.active && !settling) drag.publish(onAction)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            drag.active = true
                            settling = false
                            dragPos.value = resting()
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val current = dragPos.value ?: return@detectDragGestures
                            dragPos.value =
                                (current.first + delta.x).coerceIn(0f, slackX()) to
                                    (current.second + delta.y).coerceIn(0f, slackY())
                        },
                        onDragCancel = { drag.active = false },
                        onDragEnd = {
                            drag.active = false
                            val end = dragPos.value ?: return@detectDragGestures
                            // Whichever half of the screen the pill's centre
                            // ends up in is the edge it docks to.
                            val centre = end.first + pillSize.value.width / 2f
                            val rightEdge = centre > boxWidth / 2f
                            val yBias = if (slackY() > 0f) end.second / slackY() else 0.5f
                            onAction(VoiceBarAction.SetEdge(rightEdge, yBias))
                            scope.launch {
                                settling = true
                                settleX.snapTo(end.first)
                                settleY.snapTo(end.second)
                                dragPos.value = null
                                if (kb.reduceMotion) {
                                    settleX.snapTo(edgeAnchor(rightEdge))
                                } else {
                                    settleX.animateTo(edgeAnchor(rightEdge), VoiceBarSettleSpring)
                                }
                                settling = false
                                drag.publish(onAction)
                            }
                        },
                    )
                },
        ) {
            VerticalBarContent(
                state = state,
                hasPermission = hasPermission,
                onToggle = onToggle,
                onRequestPermission = onRequestPermission,
                onOpenVoiceSettings = onOpenVoiceSettings,
                onRestoreKeyboard = onRestoreKeyboard,
                onAction = onAction,
                onUndo = onUndo,
                onLayoutSelect = onLayoutSelect,
            )
        }
    }
}

private fun VoiceBarDrag.record(
    position: androidx.compose.ui.geometry.Offset,
    size: IntSize,
) {
    left = position.x.roundToInt()
    top = position.y.roundToInt()
    right = left + size.width
    bottom = top + size.height
    measured = true
}

/**
 * The pill chrome both orientations share: entrance animation, theme
 * background (colour, gradient or photo — the same painter as the keyboard
 * board, clipped to the pill), border and shadow.
 */
@Composable
private fun VoiceBarPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val kb = LocalKbTheme.current
    val shape = kb.cardShape()
    // Enter as one gesture: fade, rise and unshrink together. Draw-phase
    // reads only, so the entrance never moves the published touch bounds.
    val appear = remember { Animatable(if (kb.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            appear.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    val riseDistance = with(LocalDensity.current) { VoiceBarRise.toPx() }
    Surface(
        modifier = modifier.graphicsLayer {
            val seen = appear.value
            alpha = seen
            translationY = (1f - seen) * riseDistance
            scaleX = 0.92f + 0.08f * seen
            scaleY = 0.92f + 0.08f * seen
        },
        shape = shape,
        // The theme paints the pill (colour + optional image); Surface just
        // supplies the shape, clip and shadow — same split as floating mode.
        color = Color.Transparent,
        shadowElevation = 8.dp,
    ) {
        Box(modifier = Modifier.clip(shape).popupBorder(LocalKbTheme.current, shape)) {
            BoardBackground(LocalKbTheme.current)
            content()
        }
    }
}

@Composable
private fun HorizontalBarContent(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onRestoreKeyboard: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onLayoutSelect: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    // Two bar pages, Gboard style: the hamburger swaps the whole row for the
    // menu row and back. Cross-dissolved by hand — the house style animates
    // paint, never measured size (see the KeyboardScreen notes).
    var shownMenu by remember { mutableStateOf(false) }
    val pageAlpha = remember { Animatable(1f) }
    LaunchedEffect(menuOpen, kb.reduceMotion) {
        if (shownMenu != menuOpen) {
            if (!kb.reduceMotion) {
                pageAlpha.animateTo(0f, tween(VoiceBarPageFadeOutMs))
            }
            shownMenu = menuOpen
            if (kb.reduceMotion) {
                pageAlpha.snapTo(1f)
            } else {
                pageAlpha.animateTo(1f, tween(VoiceBarPageFadeInMs))
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(VoiceBarHeight)
            .graphicsLayer { alpha = pageAlpha.value }
            .padding(horizontal = 6.dp),
    ) {
        if (shownMenu) {
            HorizontalMenuPage(
                state = state,
                onBack = { menuOpen = false },
                onUndo = onUndo,
                onVertical = { onAction(VoiceBarAction.SetVertical(true)) },
                onOpenVoiceSettings = onOpenVoiceSettings,
                onLayoutSelect = onLayoutSelect,
                closeMenu = { menuOpen = false },
            )
        } else {
            VoiceBarIconButton(
                icon = Icons.Outlined.Menu,
                description = stringResource(R.string.ime_voice_bar_menu_desc),
            ) {
                feedback()
                menuOpen = true
            }
            VoiceBarStatus(
                state = state,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onOpenVoiceSettings = onOpenVoiceSettings,
                modifier = Modifier.weight(1f),
            )
            val voice = state.voice
            val idle = voice.status != VoiceStatus.LISTENING &&
                voice.status != VoiceStatus.FINISHING &&
                voice.status != VoiceStatus.TRANSCRIBING
            if (voice.canUndo && idle) {
                VoiceBarIconButton(
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    description = stringResource(R.string.ime_voice_strip_undo_desc),
                ) {
                    feedback()
                    onUndo()
                }
            }
            VoiceBarIconButton(
                icon = Icons.Outlined.Keyboard,
                description = stringResource(R.string.ime_voice_bar_restore_desc),
                onClick = onRestoreKeyboard,
            )
            VoiceBarDeleteButton(onAction)
            VoiceBarMic(
                state = state,
                hasPermission = hasPermission,
                onToggle = onToggle,
                onRequestPermission = onRequestPermission,
            )
        }
    }
}

/** The hamburger's page: back, undo, language, upright toggle, settings. */
@Composable
private fun RowScope.HorizontalMenuPage(
    state: KeyboardUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onVertical: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onLayoutSelect: (String) -> Unit,
    closeMenu: () -> Unit,
) {
    val feedback = LocalKeyPressFeedback.current
    VoiceBarIconButton(
        icon = Icons.AutoMirrored.Outlined.ArrowBack,
        description = stringResource(R.string.ime_voice_bar_menu_back_desc),
    ) {
        feedback()
        onBack()
    }
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceBarIconButton(
            icon = Icons.AutoMirrored.Outlined.Undo,
            description = stringResource(R.string.ime_voice_strip_undo_desc),
            enabled = state.voice.canUndo,
        ) {
            feedback()
            onUndo()
            closeMenu()
        }
        VoiceBarLanguageChip(state, onLayoutSelect, closeMenu)
        VoiceBarIconButton(
            icon = Icons.Outlined.SwapVert,
            description = stringResource(R.string.ime_voice_bar_vertical_desc),
        ) {
            feedback()
            onVertical()
        }
        VoiceBarIconButton(
            icon = Icons.Outlined.Settings,
            description = stringResource(R.string.ime_voice_strip_settings_action),
        ) {
            feedback()
            onOpenVoiceSettings()
        }
    }
}

@Composable
private fun VerticalBarContent(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onRestoreKeyboard: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onUndo: () -> Unit,
    onLayoutSelect: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var shownMenu by remember { mutableStateOf(false) }
    val pageAlpha = remember { Animatable(1f) }
    LaunchedEffect(menuOpen, kb.reduceMotion) {
        if (shownMenu != menuOpen) {
            if (!kb.reduceMotion) {
                pageAlpha.animateTo(0f, tween(VoiceBarPageFadeOutMs))
            }
            shownMenu = menuOpen
            if (kb.reduceMotion) {
                pageAlpha.snapTo(1f)
            } else {
                pageAlpha.animateTo(1f, tween(VoiceBarPageFadeInMs))
            }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .width(VoiceBarHeight)
            .graphicsLayer { alpha = pageAlpha.value }
            .padding(vertical = 8.dp),
    ) {
        if (shownMenu) {
            VoiceBarIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                description = stringResource(R.string.ime_voice_bar_menu_back_desc),
            ) {
                feedback()
                menuOpen = false
            }
            VoiceBarIconButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                description = stringResource(R.string.ime_voice_strip_undo_desc),
                enabled = state.voice.canUndo,
            ) {
                feedback()
                onUndo()
                menuOpen = false
            }
            VoiceBarLanguageChip(state, onLayoutSelect) { menuOpen = false }
            VoiceBarIconButton(
                icon = Icons.Outlined.SwapHoriz,
                description = stringResource(R.string.ime_voice_bar_horizontal_desc),
            ) {
                feedback()
                onAction(VoiceBarAction.SetVertical(false))
            }
            VoiceBarIconButton(
                icon = Icons.Outlined.Settings,
                description = stringResource(R.string.ime_voice_strip_settings_action),
            ) {
                feedback()
                onOpenVoiceSettings()
            }
        } else {
            VoiceBarMic(
                state = state,
                hasPermission = hasPermission,
                onToggle = onToggle,
                onRequestPermission = onRequestPermission,
            )
            VoiceBarDeleteButton(onAction)
            VoiceBarIconButton(
                icon = Icons.Outlined.Keyboard,
                description = stringResource(R.string.ime_voice_bar_restore_desc),
                onClick = onRestoreKeyboard,
            )
            VoiceBarIconButton(
                icon = Icons.Outlined.Menu,
                description = stringResource(R.string.ime_voice_bar_menu_desc),
            ) {
                feedback()
                menuOpen = true
            }
        }
    }
}

/**
 * The status line and its one remedy chip, exactly the strip's cascade so the
 * two dictation bars never disagree about the same session.
 */
@Composable
private fun VoiceBarStatus(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val voice = state.voice
    val listening = voice.status == VoiceStatus.LISTENING
    val transcribing = voice.status == VoiceStatus.TRANSCRIBING

    // Which *kind* of line is up. Fading keyed on this rather than the text
    // keeps a growing partial from strobing the line on every word.
    val statusKind = when {
        voice.status == VoiceStatus.NEED_PERMISSION || !hasPermission -> 1
        voice.status == VoiceStatus.UNAVAILABLE -> 2
        voice.whisperNeedsModel -> 3
        listening && voice.whisper -> 4
        listening -> 5
        transcribing -> 6
        voice.status == VoiceStatus.FINISHING -> 7
        voice.status == VoiceStatus.ERROR -> 8
        else -> 9
    }
    val statusAlpha = remember { Animatable(1f) }
    LaunchedEffect(statusKind, kb.reduceMotion) {
        if (!kb.reduceMotion) {
            statusAlpha.snapTo(0f)
            statusAlpha.animateTo(1f, tween(VoiceBarStatusFadeMs))
        } else {
            statusAlpha.snapTo(1f)
        }
    }

    val speakNow = stringResource(R.string.ime_voice_bar_speak_now)
    val statusText = when (statusKind) {
        1 -> stringResource(R.string.ime_voice_strip_permission)
        2 -> stringResource(R.string.ime_voice_strip_unavailable)
        3 -> stringResource(R.string.ime_voice_strip_no_model)
        4 -> stringResource(R.string.ime_voice_strip_listening_hint)
        5 -> voice.partial.ifEmpty { speakNow }
        6 -> stringResource(R.string.ime_voice_status_transcribing)
        7 -> "…"
        8 -> voice.errorMessage ?: stringResource(R.string.ime_voice_status_error)
        else -> stringResource(R.string.ime_voice_bar_paused)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 6.dp),
    ) {
        Text(
            statusText,
            color = if (listening && voice.partial.isNotEmpty()) {
                kb.toolbarIcon
            } else {
                kb.secondaryText
            },
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .graphicsLayer { alpha = statusAlpha.value },
        )
        val action = when (statusKind) {
            1 -> stringResource(R.string.ime_voice_strip_allow_action) to onRequestPermission
            3 -> stringResource(R.string.ime_voice_strip_settings_action) to onOpenVoiceSettings
            else -> null
        }
        if (action != null) {
            Text(
                action.first,
                color = kb.toolCircleActiveIcon,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(kb.toolShape())
                    .background(kb.toolCircleActive)
                    .clickable {
                        feedback()
                        action.second()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** The mic with its level-driven double pulse ring — the bar's main control. */
@Composable
private fun VoiceBarMic(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val voice = state.voice
    val listening = voice.status == VoiceStatus.LISTENING
    val busy = voice.status == VoiceStatus.FINISHING ||
        voice.status == VoiceStatus.TRANSCRIBING
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(VoiceBarButton)) {
        // Two rings, the outer looser than the inner, so louder speech reads
        // as a swell rather than a single hard edge. Parked static under
        // reduce motion for the same reason as the panel's ring.
        val innerScale by animateFloatAsState(
            targetValue = when {
                !listening -> 0f
                kb.reduceMotion -> 1.1f
                else -> 1f + voice.level * 0.45f
            },
            animationSpec = if (kb.reduceMotion) snap() else spring(stiffness = 220f),
            label = "voiceBarPulseInner",
        )
        val outerScale by animateFloatAsState(
            targetValue = when {
                !listening -> 0f
                kb.reduceMotion -> 1.2f
                else -> 1f + voice.level * 0.85f
            },
            animationSpec = if (kb.reduceMotion) snap() else spring(stiffness = 140f),
            label = "voiceBarPulseOuter",
        )
        if (listening) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(outerScale)
                    .background(kb.accent.copy(alpha = 0.12f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(innerScale)
                    .background(kb.accent.copy(alpha = 0.25f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (listening) kb.toolCircleActive else kb.chip)
                .clickable(enabled = !busy) {
                    if (hasPermission) onToggle() else onRequestPermission()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = kb.accent,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = if (listening) {
                        stringResource(R.string.ime_voice_stop_desc)
                    } else {
                        stringResource(R.string.ime_voice_start_desc)
                    },
                    modifier = Modifier.size(20.dp),
                    tint = if (listening) kb.toolCircleActiveIcon else kb.secondaryText,
                )
            }
        }
    }
}

/** Backspace with the rail's press-and-hold repeat. */
@Composable
private fun VoiceBarDeleteButton(onAction: (VoiceBarAction) -> Unit) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val scope = rememberCoroutineScope()
    val description = stringResource(CommonR.string.common_delete)
    Box(
        modifier = Modifier
            .size(VoiceBarButton)
            .clip(kb.toolShape())
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        feedback()
                        onAction(VoiceBarAction.RailKey(Key("⌫", action = KeyAction.Delete)))
                        val repeat: Job = scope.launch {
                            delay(400)
                            while (true) {
                                onAction(
                                    VoiceBarAction.RailKey(Key("⌫", action = KeyAction.Delete)),
                                )
                                delay(120)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = kb.toolbarIcon,
        )
    }
}

/** EN ⇄ bn chip, only when both an English and a non-English language are on. */
@Composable
private fun VoiceBarLanguageChip(
    state: KeyboardUiState,
    onLayoutSelect: (String) -> Unit,
    closeMenu: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val languages = state.settings.enabledLanguages.ifEmpty {
        listOf(LanguageRegistry.byId("en"))
    }
    if (!(languages.any { it.isEnglish } && languages.any { !it.isEnglish })) return
    val english = state.voice.languageTag.startsWith("en")
    Text(
        text = if (english) "EN" else "বাং",
        color = kb.secondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(kb.chipShape())
            .background(kb.chip)
            .chipBorder(kb, kb.chipShape())
            .clickable {
                feedback()
                val other = if (english) {
                    languages.first { !it.isEnglish }
                } else {
                    languages.firstOrNull { it.isEnglish } ?: LanguageRegistry.byId("en")
                }
                val layoutId = other.layoutIds.firstOrNull {
                    it in state.settings.enabledLayoutIds
                } ?: other.layoutIds.firstOrNull()
                if (layoutId != null) onLayoutSelect(layoutId)
                closeMenu()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** One round bar button. Disabled draws at reduced alpha and swallows taps. */
@Composable
private fun VoiceBarIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .size(VoiceBarButton)
            .clip(kb.toolShape())
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
            tint = kb.toolbarIcon,
        )
    }
}

private fun hasVoiceBarMicPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private val VoiceBarHeight = 56.dp
private val VoiceBarButton = 44.dp
private val VoiceBarMargin = 8.dp
private val VoiceBarMaxWidth = 420.dp

/** How far below its rest the pill starts its entrance. */
private val VoiceBarRise = 24.dp

// Page swaps reuse the strip-content rhythm: quick out, calm in.
private const val VoiceBarPageFadeOutMs = 110
private const val VoiceBarPageFadeInMs = 200
private const val VoiceBarStatusFadeMs = 180

/** The settle after a drag: firm, one soft overshoot at most. */
private val VoiceBarSettleSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
