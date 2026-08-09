package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.ime.VoiceBarAction
import com.wasimaster.wmkeyboard.ime.VoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The collapsed voice bar (Gboard's voice-typing toolbar): the keyboard slides
 * off the screen and this pill of dictation controls is what remains, floating
 * over the app. Entered with the minimize button on the voice panel or the
 * strip, left with the pill's keyboard button — one session at a time, never a
 * standing mode. The dictation session runs on across both transitions.
 *
 * The window spans the screen while the bar owns it; the service keeps the
 * touchable region down to the pill (or, while the menu is open, the whole
 * window so a tap anywhere dismisses it — the same modal contract as any
 * context menu). The pill lies along the bottom, draggable between
 * left/centre/right rests, or stands against a side edge; switching
 * orientation glides it to its new place.
 */
@Composable
internal fun VoiceBarLayer(
    state: KeyboardUiState,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onLayoutSelect: (String) -> Unit,
    onPanelChange: (PanelMode) -> Unit,
    progress: () -> Float,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    val hasPermission = rememberMicPermissionState()
    val vertical = state.settings.voiceBar.vertical

    val drag = remember { VoiceBarDrag() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(VoiceBarMargin)
            // The whole reachable area, for the menu's modal region.
            .onGloballyPositioned { coords ->
                drag.rootRect = windowRect(coords.positionInWindow(), coords.size)
            },
    ) {
        val boxWidth = constraints.maxWidth
        val boxHeight = constraints.maxHeight
        val pillSize = remember { mutableStateOf(IntSize.Zero) }
        val posX = remember { Animatable(0f) }
        val posY = remember { Animatable(0f) }
        // The pill draws only after its first real placement, so it never
        // flashes at 0,0 (same trick as the floating keyboard frame).
        var placed by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        // Composition of the menu outlives [menuOpen] by one fade-out.
        var menuShown by remember { mutableStateOf(false) }

        fun slackX() = (boxWidth - pillSize.value.width).coerceAtLeast(0).toFloat()
        fun slackY() = (boxHeight - pillSize.value.height).coerceAtLeast(0).toFloat()
        fun anchor(): Offset {
            val bar = state.settings.voiceBar
            return if (vertical) {
                Offset(
                    if (bar.rightEdge) slackX() else 0f,
                    bar.yBias.coerceIn(0f, 1f) * slackY(),
                )
            } else {
                val x = when (bar.snap) {
                    VoiceBarSettings.SNAP_LEFT -> 0f
                    VoiceBarSettings.SNAP_RIGHT -> slackX()
                    else -> slackX() / 2f
                }
                Offset(x, slackY())
            }
        }

        fun publishRegion() {
            if (menuShown) drag.publishRect(drag.rootRect, onAction)
            else drag.publishRect(drag.pillRect, onAction)
        }

        // The resting place follows the persisted geometry: a drag writes it
        // and the pill is already there, but an orientation switch (or a
        // rotation) writes a different anchor and this glides the pill over.
        LaunchedEffect(
            vertical, state.settings.voiceBar.snap, state.settings.voiceBar.rightEdge,
            state.settings.voiceBar.yBias, boxWidth, boxHeight, pillSize.value,
        ) {
            if (pillSize.value == IntSize.Zero || drag.active) return@LaunchedEffect
            val target = anchor()
            if (!placed || kb.reduceMotion) {
                posX.snapTo(target.x)
                posY.snapTo(target.y)
                placed = true
            } else if (abs(posX.value - target.x) > 0.5f || abs(posY.value - target.y) > 0.5f) {
                launch { posX.animateTo(target.x, VoiceBarSettleSpring) }
                posY.animateTo(target.y, VoiceBarSettleSpring)
            }
            publishRegion()
        }

        // While the menu is up the whole window takes touches, so tapping
        // outside it dismisses — the modal scrim every context menu has.
        LaunchedEffect(menuShown) { publishRegion() }
        if (menuShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { menuOpen = false } },
            )
        }

        Surface(
            modifier = Modifier
                .then(
                    if (vertical) Modifier else Modifier.widthIn(max = VoiceBarMaxWidth)
                )
                .offset { IntOffset(posX.value.roundToInt(), posY.value.roundToInt()) }
                .onGloballyPositioned { coords ->
                    pillSize.value = coords.size
                    drag.pillRect = windowRect(coords.positionInWindow(), coords.size)
                    if (!drag.active && !posX.isRunning && !posY.isRunning && !menuShown) {
                        publishRegion()
                    }
                }
                .graphicsLayer {
                    val seen = if (placed) progress() else 0f
                    alpha = seen
                    scaleX = 0.96f + 0.04f * seen
                    scaleY = 0.96f + 0.04f * seen
                }
                .voiceBarDrag(
                    gesture = VoiceBarGesture(
                        vertical = vertical,
                        drag = drag,
                        posX = posX,
                        posY = posY,
                        scope = scope,
                        reduceMotion = kb.reduceMotion,
                        slackX = ::slackX,
                        slackY = ::slackY,
                        pillWidth = { pillSize.value.width },
                        boxWidth = boxWidth,
                        onAction = onAction,
                        publish = ::publishRegion,
                    ),
                ),
            shape = kb.cardShape(),
            // The theme paints the pill (colour + optional image); Surface
            // just supplies the shape, clip and shadow — same split as
            // floating mode.
            color = Color.Transparent,
            shadowElevation = 8.dp,
        ) {
            Box(modifier = Modifier.clip(kb.cardShape()).popupBorder(kb, kb.cardShape())) {
                BoardBackground(kb)
                VoiceBarPillContent(
                    state = state,
                    vertical = vertical,
                    hasPermission = hasPermission,
                    onToggle = onToggle,
                    onUndo = onUndo,
                    onRequestPermission = onRequestPermission,
                    onOpenVoiceSettings = onOpenVoiceSettings,
                    onRestore = { onAction(VoiceBarAction.Restore) },
                    onAction = onAction,
                    onMenu = { menuOpen = true },
                )
            }
        }

        if (menuOpen || menuShown) {
            VoiceBarMenu(
                state = state,
                open = menuOpen,
                vertical = vertical,
                anchor = VoiceBarMenuAnchor(
                    pillPosition = { Offset(posX.value, posY.value) },
                    pillSize = { pillSize.value },
                    boxWidth = boxWidth,
                    boxHeight = boxHeight,
                ),
                onShownChange = { menuShown = it },
                onDismiss = { menuOpen = false },
                actions = VoiceBarMenuActions(
                    onUndo = onUndo,
                    onOpenVoiceSettings = onOpenVoiceSettings,
                    onLayoutSelect = onLayoutSelect,
                    onPanelChange = onPanelChange,
                    onAction = onAction,
                ),
            )
        }
    }
}

/** Scratch state for one bar drag plus the published rectangles. */
private class VoiceBarDrag {
    /** A finger is moving the pill right now. */
    var active = false
    var pillRect: IntArray? = null
    var rootRect: IntArray? = null
}

/** Everything the pill's drag gesture needs, so the handler is one bundle. */
private class VoiceBarGesture(
    val vertical: Boolean,
    val drag: VoiceBarDrag,
    val posX: Animatable<Float, AnimationVector1D>,
    val posY: Animatable<Float, AnimationVector1D>,
    val scope: CoroutineScope,
    val reduceMotion: Boolean,
    val slackX: () -> Float,
    val slackY: () -> Float,
    val pillWidth: () -> Int,
    val boxWidth: Int,
    val onAction: (VoiceBarAction) -> Unit,
    val publish: () -> Unit,
)

/**
 * Move the pill with the finger; on release, settle it on its nearest rest —
 * the three snap points along the bottom, or (upright) whichever edge holds
 * the pill's centre — and persist where it ended up.
 */
private fun Modifier.voiceBarDrag(gesture: VoiceBarGesture): Modifier =
    pointerInput(gesture.vertical) {
        detectDragGestures(
            onDragStart = { gesture.drag.active = true },
            onDrag = { change, delta ->
                change.consume()
                gesture.scope.launch {
                    gesture.posX.snapTo(
                        (gesture.posX.value + delta.x).coerceIn(0f, gesture.slackX()),
                    )
                    if (gesture.vertical) {
                        gesture.posY.snapTo(
                            (gesture.posY.value + delta.y).coerceIn(0f, gesture.slackY()),
                        )
                    }
                }
            },
            onDragCancel = { gesture.drag.active = false },
            onDragEnd = {
                gesture.drag.active = false
                if (gesture.vertical) gesture.settleToEdge() else gesture.settleToSnap()
            },
        )
    }

/** Whichever half of the screen the pill's centre ends in is the edge it docks to. */
private fun VoiceBarGesture.settleToEdge() {
    val centre = posX.value + pillWidth() / 2f
    val rightEdge = centre > boxWidth / 2f
    val yBias = if (slackY() > 0f) posY.value / slackY() else 0.5f
    onAction(VoiceBarAction.SetEdge(rightEdge, yBias))
    scope.launch {
        val x = if (rightEdge) slackX() else 0f
        if (reduceMotion) posX.snapTo(x) else posX.animateTo(x, VoiceBarSettleSpring)
        publish()
    }
}

/** The nearest of the three bottom rests wins the pill. */
private fun VoiceBarGesture.settleToSnap() {
    val end = posX.value
    val snapTargets = listOf(
        VoiceBarSettings.SNAP_LEFT to 0f,
        VoiceBarSettings.SNAP_CENTER to slackX() / 2f,
        VoiceBarSettings.SNAP_RIGHT to slackX(),
    )
    val (snap, x) = snapTargets.minByOrNull { abs(it.second - end) } ?: snapTargets[1]
    onAction(VoiceBarAction.SetSnap(snap))
    scope.launch {
        if (reduceMotion) posX.snapTo(x) else posX.animateTo(x, VoiceBarSettleSpring)
        publish()
    }
}

private fun windowRect(position: Offset, size: IntSize): IntArray {
    val left = position.x.roundToInt()
    val top = position.y.roundToInt()
    return intArrayOf(left, top, left + size.width, top + size.height)
}

private fun VoiceBarDrag.publishRect(rect: IntArray?, onAction: (VoiceBarAction) -> Unit) {
    val r = rect ?: return
    onAction(VoiceBarAction.Bounds(r[0], r[1], r[2], r[3]))
}

/**
 * The non-interactive pill drawn while the keyboard slides back up over it on
 * restore — the reverse of the collapse reveal. The real layer cannot render
 * here: it spans the window, and the window has already shrunk back to the
 * keyboard. Skipped for the vertical bar (it lives mid-edge, outside a
 * keyboard-height window).
 */
@Composable
internal fun VoiceBarEcho(state: KeyboardUiState, progress: () -> Float) {
    if (state.settings.voiceBar.vertical) return
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(VoiceBarMargin),
        contentAlignment = when (state.settings.voiceBar.snap) {
            VoiceBarSettings.SNAP_LEFT -> Alignment.BottomStart
            VoiceBarSettings.SNAP_RIGHT -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        },
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = VoiceBarMaxWidth)
                .graphicsLayer { alpha = progress() },
            shape = kb.cardShape(),
            color = Color.Transparent,
            shadowElevation = 8.dp,
        ) {
            Box(modifier = Modifier.clip(kb.cardShape()).popupBorder(kb, kb.cardShape())) {
                BoardBackground(kb)
                VoiceBarPillContent(
                    state = state,
                    vertical = false,
                    hasPermission = true,
                    onToggle = {},
                    onUndo = {},
                    onRequestPermission = {},
                    onOpenVoiceSettings = {},
                    onRestore = {},
                    onAction = {},
                    onMenu = {},
                )
            }
        }
    }
}

/** ON_RESUME-refreshed mic permission, shared by the layer and the echo. */
@Composable
private fun rememberMicPermissionState(): Boolean {
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
    return hasPermission
}

/**
 * What the pill holds, in Gboard's order. Horizontal: menu, keyboard, the
 * status centre, delete, mic. Vertical: the same from top to bottom with the
 * status dropped — an upright bar has no room for a line of text.
 */
@Composable
private fun VoiceBarPillContent(
    state: KeyboardUiState,
    vertical: Boolean,
    hasPermission: Boolean,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onRestore: () -> Unit,
    onAction: (VoiceBarAction) -> Unit,
    onMenu: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(VoiceBarHeight).padding(vertical = 6.dp),
        ) {
            VoiceBarMic(state, hasPermission, onToggle, onRequestPermission)
            VoiceBarDeleteButton(onAction)
            VoiceBarIconButton(
                icon = Icons.Outlined.Keyboard,
                description = stringResource(R.string.ime_voice_bar_restore_desc),
                onClick = onRestore,
            )
            VoiceBarMenuButton(onMenu = { feedback(); onMenu() })
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(VoiceBarHeight).padding(horizontal = 8.dp),
        ) {
            VoiceBarMenuButton(onMenu = { feedback(); onMenu() })
            Spacer(modifier = Modifier.width(2.dp))
            VoiceBarIconButton(
                icon = Icons.Outlined.Keyboard,
                description = stringResource(R.string.ime_voice_bar_restore_desc),
                onClick = onRestore,
            )
            VoiceBarCentre(
                state = state,
                hasPermission = hasPermission,
                onUndo = onUndo,
                onRequestPermission = onRequestPermission,
                onOpenVoiceSettings = onOpenVoiceSettings,
                modifier = Modifier.weight(1f),
            )
            VoiceBarDeleteButton(onAction)
            VoiceBarMic(state, hasPermission, onToggle, onRequestPermission)
        }
    }
}

/** The hamburger in its tinted circle, as in the Pixel toolbar. */
@Composable
private fun VoiceBarMenuButton(onMenu: () -> Unit) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .size(VoiceBarButton)
            .padding(3.dp)
            .clip(CircleShape)
            .background(kb.chip)
            .chipBorder(kb, CircleShape)
            .clickable(onClick = onMenu),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Menu,
            contentDescription = stringResource(R.string.ime_voice_bar_menu_desc),
            modifier = Modifier.size(20.dp),
            tint = kb.toolbarIcon,
        )
    }
}

/**
 * The pill's centre: the big status line, or — when the session pauses with
 * something to act on — its action chips, Gboard style.
 */
@Composable
private fun VoiceBarCentre(
    state: KeyboardUiState,
    hasPermission: Boolean,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val voice = state.voice
    val listening = voice.status == VoiceStatus.LISTENING
    val transcribing = voice.status == VoiceStatus.TRANSCRIBING
    val idle = !listening && !transcribing && voice.status != VoiceStatus.FINISHING

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
        idle && voice.canUndo -> 9
        else -> 10
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .graphicsLayer { alpha = statusAlpha.value },
    ) {
        if (statusKind == 9) {
            // Paused with an utterance to take back: the chip is the action.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                VoiceBarChip(
                    text = stringResource(R.string.ime_voice_undo_action),
                    icon = Icons.AutoMirrored.Outlined.Undo,
                ) {
                    feedback()
                    onUndo()
                }
            }
            return@Row
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
        Text(
            statusText,
            color = if (listening && voice.partial.isNotEmpty()) {
                kb.toolbarIcon
            } else {
                kb.secondaryText
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
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

/** One action chip in the pill's centre. */
@Composable
private fun VoiceBarChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    val kb = LocalKbTheme.current
    val shape = kb.chipShape()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(kb.chip)
            .chipBorder(kb, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = kb.chipText,
        )
        Text(
            text,
            color = kb.chipText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 5.dp),
        )
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

/** One round bar button. */
@Composable
private fun VoiceBarIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .size(VoiceBarButton)
            .clip(kb.toolShape())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = kb.toolbarIcon,
        )
    }
}

/** Where the menu hangs off the pill: its live position and the space around it. */
private class VoiceBarMenuAnchor(
    val pillPosition: () -> Offset,
    val pillSize: () -> IntSize,
    val boxWidth: Int,
    val boxHeight: Int,
)

/** What the menu's rows do, bundled to keep the composable's list short. */
private class VoiceBarMenuActions(
    val onUndo: () -> Unit,
    val onOpenVoiceSettings: () -> Unit,
    val onLayoutSelect: (String) -> Unit,
    val onPanelChange: (PanelMode) -> Unit,
    val onAction: (VoiceBarAction) -> Unit,
)

/** One row of the context menu card. */
private class VoiceMenuEntry(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The hamburger's context menu: a card floating off the pill with a caret
 * pointing back at the button, exactly Gboard's. Rows adapted to this
 * keyboard's featureset; the panel shortcuts restore the keyboard with that
 * panel open, which is also what Gboard's do.
 */
@Composable
private fun VoiceBarMenu(
    state: KeyboardUiState,
    open: Boolean,
    vertical: Boolean,
    anchor: VoiceBarMenuAnchor,
    onShownChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    actions: VoiceBarMenuActions,
) {
    val pillPosition = anchor.pillPosition
    val pillSize = anchor.pillSize
    val boxWidth = anchor.boxWidth
    val boxHeight = anchor.boxHeight
    val onUndo = actions.onUndo
    val onOpenVoiceSettings = actions.onOpenVoiceSettings
    val onLayoutSelect = actions.onLayoutSelect
    val onPanelChange = actions.onPanelChange
    val onAction = actions.onAction
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(open, kb.reduceMotion) {
        onShownChange(true)
        if (kb.reduceMotion) {
            appear.snapTo(if (open) 1f else 0f)
        } else if (open) {
            appear.animateTo(1f, VoiceBarMenuSpring)
        } else {
            appear.animateTo(0f, tween(VoiceBarMenuOutMs))
        }
        if (!open) onShownChange(false)
    }

    fun act(block: () -> Unit): () -> Unit = {
        feedback()
        onDismiss()
        block()
    }

    val languages = state.settings.enabledLanguages.ifEmpty {
        listOf(LanguageRegistry.byId("en"))
    }
    val languageVisible = languages.any { it.isEnglish } && languages.any { !it.isEnglish }
    val english = state.voice.languageTag.startsWith("en")
    val entries = buildList {
        add(
            VoiceMenuEntry(
                Icons.Outlined.Settings,
                stringResource(R.string.ime_voice_bar_menu_settings),
                onClick = act(onOpenVoiceSettings),
            )
        )
        add(
            VoiceMenuEntry(
                Icons.AutoMirrored.Outlined.Undo,
                stringResource(R.string.ime_voice_undo_action),
                enabled = state.voice.canUndo,
                onClick = act(onUndo),
            )
        )
        if (languageVisible) {
            add(
                VoiceMenuEntry(
                    Icons.Outlined.Language,
                    stringResource(R.string.ime_voice_bar_menu_language),
                    onClick = act {
                        val other = if (english) {
                            languages.first { !it.isEnglish }
                        } else {
                            languages.firstOrNull { it.isEnglish }
                                ?: LanguageRegistry.byId("en")
                        }
                        val layoutId = other.layoutIds.firstOrNull {
                            it in state.settings.enabledLayoutIds
                        } ?: other.layoutIds.firstOrNull()
                        if (layoutId != null) onLayoutSelect(layoutId)
                    },
                )
            )
        }
        add(
            VoiceMenuEntry(
                Icons.Outlined.ContentPaste,
                stringResource(R.string.ime_voice_bar_menu_clipboard),
                onClick = act { onPanelChange(PanelMode.CLIPBOARD) },
            )
        )
        add(
            VoiceMenuEntry(
                Icons.Outlined.Mood,
                stringResource(R.string.ime_voice_bar_menu_emoji),
                onClick = act { onPanelChange(PanelMode.EMOJI) },
            )
        )
        add(
            VoiceMenuEntry(
                Icons.Outlined.Translate,
                stringResource(R.string.ime_voice_bar_menu_translate),
                onClick = act { onPanelChange(PanelMode.TRANSLATE) },
            )
        )
        add(
            VoiceMenuEntry(
                if (vertical) Icons.Outlined.SwapHoriz else Icons.Outlined.SwapVert,
                stringResource(
                    if (vertical) R.string.ime_voice_bar_menu_horizontal
                    else R.string.ime_voice_bar_menu_vertical
                ),
                onClick = {
                    feedback()
                    onDismiss()
                    onAction(VoiceBarAction.SetVertical(!vertical))
                },
            )
        )
    }

    val menuSize = remember { mutableStateOf(IntSize.Zero) }
    val caretHeightPx = with(LocalDensity.current) { VoiceBarMenuCaret.toPx() }
    Column(
        modifier = Modifier
            .onGloballyPositioned { menuSize.value = it.size }
            .offset {
                val pill = pillPosition()
                val size = menuSize.value
                if (vertical) {
                    // Beside the pill, bottom-aligned with it, on its free side.
                    val pillW = pillSize().width
                    val gap = 8.dp.roundToPx()
                    val x = if (pill.x > boxWidth / 2f) {
                        (pill.x - size.width - gap).coerceAtLeast(0f)
                    } else {
                        (pill.x + pillW + gap).coerceAtMost((boxWidth - size.width).toFloat())
                    }
                    val y = (pill.y + pillSize().height - size.height)
                        .coerceIn(0f, (boxHeight - size.height).toFloat())
                    IntOffset(x.roundToInt(), y.roundToInt())
                } else {
                    // Above the pill, left edges aligned, caret at the hamburger.
                    val x = pill.x.coerceIn(0f, (boxWidth - size.width).toFloat())
                    val y = (pill.y - size.height - caretHeightPx - 4.dp.toPx())
                        .coerceAtLeast(0f)
                    IntOffset(x.roundToInt(), y.roundToInt())
                }
            }
            .graphicsLayer {
                val seen = appear.value
                alpha = seen
                scaleX = 0.9f + 0.1f * seen
                scaleY = 0.9f + 0.1f * seen
                transformOrigin = if (vertical) {
                    TransformOrigin(0.5f, 1f)
                } else {
                    TransformOrigin(0.12f, 1f)
                }
            },
    ) {
        Surface(shape = kb.menuShape(), color = Color.Transparent, shadowElevation = 10.dp) {
            Column(
                modifier = Modifier
                    .clip(kb.menuShape())
                    .background(kb.popup)
                    .popupBorder(kb, kb.menuShape())
                    .width(VoiceBarMenuWidth)
                    .padding(vertical = 6.dp),
            ) {
                for (entry in entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable(enabled = entry.enabled, onClick = entry.onClick)
                            .padding(horizontal = 16.dp)
                            .graphicsLayer { alpha = if (entry.enabled) 1f else 0.4f },
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = kb.toolbarIcon,
                        )
                        Text(
                            entry.label,
                            color = kb.popupText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
            }
        }
        if (!vertical) {
            // The caret tail pointing down at the hamburger.
            Canvas(
                modifier = Modifier
                    .padding(start = 18.dp)
                    .size(width = 16.dp, height = VoiceBarMenuCaret),
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, kb.popup)
            }
        }
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
private val VoiceBarMenuWidth = 232.dp
private val VoiceBarMenuCaret = 8.dp

private const val VoiceBarStatusFadeMs = 180
private const val VoiceBarMenuOutMs = 110

/** The settle after a drag or an orientation switch: firm, one soft overshoot at most. */
private val VoiceBarSettleSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

/** The menu's pop: springs in, fades out. */
private val VoiceBarMenuSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
