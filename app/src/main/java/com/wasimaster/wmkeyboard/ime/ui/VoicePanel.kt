package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.isEnglish
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.VoiceModelState
import com.wasimaster.wmkeyboard.ime.VoiceStatus
import com.wasimaster.wmkeyboard.ime.layout.Key
import com.wasimaster.wmkeyboard.ime.layout.KeyAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Voice input panel: a large mic button with a level-driven pulse ring and
 * the utterance in progress underneath (the same text is live in the editor
 * as composing text), plus the handwriting panel's action rail. The service
 * owns the recognizer; the panel only renders [KeyboardUiState.voice] and
 * reports taps.
 */
@Composable
internal fun VoicePanel(
    state: KeyboardUiState,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onDownloadModel: () -> Unit,
    onKey: (Key) -> Unit,
    onLanguageSelect: (InputMode) -> Unit,
    onClose: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val height = keyRowsHeight(state.settings)
    val voice = state.voice
    val feedback = LocalKeyPressFeedback.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(hasMicPermission(context)) }
    // The permission dialog lives in a trampoline activity; re-check when
    // the keyboard comes back to the foreground afterwards.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasMicPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(2.dp),
        ) {
            when {
                state.secureField -> VoiceNotice(
                    "Voice input is unavailable in password fields.",
                )
                !hasPermission -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Voice typing needs permission to use the microphone. " +
                            "Audio is only captured while you dictate.",
                        color = kb.secondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(kb.keyRadiusDp.dp))
                            .background(kb.toolCircleActive)
                            .pointerInput(Unit) { detectTapGestures { onRequestPermission() } }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Allow microphone",
                            color = kb.toolCircleActiveIcon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                voice.status == VoiceStatus.UNAVAILABLE -> VoiceNotice(
                    "Speech recognition is not available on this device.",
                )
                else -> MicContent(state, onToggle, onDownloadModel)
            }

            // Language chip: shows the active recognition language, tap
            // switches between English and Bengali (the enabled input
            // modes decide what is available).
            val modes = state.settings.enabledModes.ifEmpty { listOf(InputMode.ENGLISH) }
            val english = voice.languageTag.startsWith("en")
            if (modes.any { it.isEnglish } && modes.any { !it.isEnglish }) {
                Text(
                    text = if (english) "EN" else "বাং",
                    color = kb.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(kb.toolRadiusDp.dp))
                        .background(kb.chip)
                        .clickable {
                            feedback()
                            val other = if (english) {
                                modes.first { !it.isEnglish }
                            } else {
                                modes.firstOrNull { it.isEnglish } ?: InputMode.ENGLISH
                            }
                            onLanguageSelect(other)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Undo the last dictated utterance (whole, in one tap).
            val undoVisible = voice.canUndo && hasPermission && !state.secureField &&
                voice.status != VoiceStatus.LISTENING && voice.status != VoiceStatus.FINISHING
            if (undoVisible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(kb.toolRadiusDp.dp))
                        .clickable {
                            feedback()
                            onUndo()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = kb.toolbarIcon,
                    )
                    Text(
                        "Undo",
                        color = kb.toolbarIcon,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // Action rail, sized like a key column (same as handwriting).
        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight(),
        ) {
            VoiceRailKey(
                description = "Delete",
                icon = Icons.AutoMirrored.Outlined.Backspace,
                repeatable = true,
                modifier = Modifier.weight(1f),
            ) {
                feedback()
                onKey(Key("⌫", action = KeyAction.Delete))
            }
            VoiceRailKey(
                description = "Space",
                icon = Icons.Outlined.SpaceBar,
                modifier = Modifier.weight(1f),
            ) {
                feedback()
                onKey(Key(" ", action = KeyAction.Space))
            }
            VoiceRailKey(
                description = "Enter",
                icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
                modifier = Modifier.weight(1f),
            ) {
                feedback()
                onKey(Key("⏎", action = KeyAction.Enter))
            }
            VoiceRailKey(
                description = "Back to keyboard",
                icon = Icons.Outlined.Keyboard,
                modifier = Modifier.weight(1f),
            ) {
                feedback()
                onClose()
            }
        }
    }
}

/** The mic button with pulse ring, and the session status underneath. */
@Composable
private fun MicContent(
    state: KeyboardUiState,
    onToggle: () -> Unit,
    onDownloadModel: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val voice = state.voice
    val listening = voice.status == VoiceStatus.LISTENING
    val finishing = voice.status == VoiceStatus.FINISHING

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring behind the button, breathing with the mic level.
            // Reduce motion parks it at a fixed size rather than snapping to
            // the level: the target is live mic amplitude, so a snap spec
            // would restate it several times a second and strobe the ring —
            // more motion than the spring it replaced, not less. Held static
            // it still marks that the mic is open, which is its real job.
            val ringScale by animateFloatAsState(
                targetValue = when {
                    !listening -> 0f
                    kb.reduceMotion -> 1.15f
                    else -> 1f + voice.level * 0.45f
                },
                animationSpec = if (kb.reduceMotion) snap() else spring(stiffness = 220f),
                label = "voicePulse",
            )
            if (listening) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(ringScale)
                        .background(kb.accent.copy(alpha = 0.25f), CircleShape),
                )
            }
            // Tap toggles; a long press is walkie-talkie — dictation runs
            // only while the finger stays down, releasing stops it.
            val currentStatus by rememberUpdatedState(voice.status)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (listening) kb.toolCircleActive else kb.modifierKey)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val pressed = currentStatus
                                if (pressed != VoiceStatus.FINISHING) {
                                    val startedIdle = pressed != VoiceStatus.LISTENING
                                    if (startedIdle) onToggle()
                                    val downAt = System.currentTimeMillis()
                                    tryAwaitRelease()
                                    val held = System.currentTimeMillis() - downAt
                                    if (startedIdle) {
                                        if (held >= HOLD_TO_TALK_MS) onToggle()
                                    } else {
                                        onToggle()
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (finishing) {
                    CircularProgressIndicator(
                        color = kb.accent,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = if (listening) "Stop listening" else "Start listening",
                        modifier = Modifier.size(30.dp),
                        tint = if (listening) kb.toolCircleActiveIcon else kb.modifierKeyText,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val statusText = when {
            listening -> voice.partial.ifEmpty { "Listening…" }
            finishing -> "…"
            voice.status == VoiceStatus.ERROR ->
                voice.errorMessage ?: "Speech recognition failed — try again."
            else -> "Tap to speak"
        }
        Text(
            statusText,
            color = if (listening && voice.partial.isNotEmpty()) kb.toolbarIcon else kb.secondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        // Offline-model chip (API 33+): downloading the language's on-device
        // model makes dictation offline, faster, and beep-free.
        val modelLanguage = if (voice.languageTag.startsWith("bn")) "Bengali" else "English"
        when (voice.modelState) {
            VoiceModelState.DOWNLOADABLE -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(kb.toolRadiusDp.dp))
                    .background(kb.chip)
                    .clickable { onDownloadModel() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = kb.secondaryText,
                )
                Text(
                    "Get $modelLanguage for offline dictation",
                    color = kb.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            VoiceModelState.DOWNLOADING -> Text(
                "Downloading $modelLanguage offline model" +
                    if (voice.modelProgress >= 0) " — ${voice.modelProgress}%" else "…",
                color = kb.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> {}
        }
    }
}

/** A long press on the mic switches to press-and-hold dictation. */
private const val HOLD_TO_TALK_MS = 600L

/**
 * Compact dictation bar (Gboard style): replaces the suggestion strip while
 * active, so the keys stay visible for fixing recognition errors mid-flow.
 * Shown by [KeyboardUiState.voice].strip; the voice tool toggles it when
 * the compact-bar setting is on.
 */
@Composable
internal fun VoiceStripBar(
    state: KeyboardUiState,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = LocalKbTheme.current
    val voice = state.voice
    val listening = voice.status == VoiceStatus.LISTENING
    val finishing = voice.status == VoiceStatus.FINISHING
    val feedback = LocalKeyPressFeedback.current

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(start = 6.dp),
        ) {
            // Static under reduce motion, for the same reason as the panel's
            // ring above.
            val ringScale by animateFloatAsState(
                targetValue = when {
                    !listening -> 0f
                    kb.reduceMotion -> 1.15f
                    else -> 1f + voice.level * 0.5f
                },
                animationSpec = if (kb.reduceMotion) snap() else spring(stiffness = 220f),
                label = "voiceStripPulse",
            )
            if (listening) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .scale(ringScale)
                        .background(kb.accent.copy(alpha = 0.25f), CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (listening) kb.toolCircleActive else kb.chip)
                    .clickable(enabled = !finishing) { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (finishing) {
                    CircularProgressIndicator(
                        color = kb.accent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = if (listening) "Stop listening" else "Start listening",
                        modifier = Modifier.size(18.dp),
                        tint = if (listening) kb.toolCircleActiveIcon else kb.secondaryText,
                    )
                }
            }
        }
        val statusText = when {
            state.secureField -> "Unavailable in password fields"
            voice.status == VoiceStatus.NEED_PERMISSION -> "Microphone permission needed"
            voice.status == VoiceStatus.UNAVAILABLE -> "Speech recognition unavailable"
            listening -> voice.partial.ifEmpty { "Listening…" }
            finishing -> "…"
            voice.status == VoiceStatus.ERROR ->
                voice.errorMessage ?: "Speech recognition failed — try again."
            else -> "Tap the mic to dictate"
        }
        Text(
            statusText,
            color = if (listening && voice.partial.isNotEmpty()) kb.toolbarIcon else kb.secondaryText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        if (voice.status == VoiceStatus.NEED_PERMISSION) {
            Text(
                "Allow",
                color = kb.toolCircleActiveIcon,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(kb.toolRadiusDp.dp))
                    .background(kb.toolCircleActive)
                    .clickable { onRequestPermission() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (voice.canUndo && !listening && !finishing) {
            Icon(
                Icons.AutoMirrored.Outlined.Undo,
                contentDescription = "Undo dictation",
                tint = kb.toolbarIcon,
                modifier = Modifier
                    .clip(RoundedCornerShape(kb.toolRadiusDp.dp))
                    .clickable {
                        feedback()
                        onUndo()
                    }
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Stop voice typing",
            tint = kb.toolbarIcon,
            modifier = Modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(kb.toolRadiusDp.dp))
                .clickable { onClose() }
                .padding(6.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun VoiceNotice(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = LocalKbTheme.current.secondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun hasMicPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** One key on the panel's right-hand action rail (same look as handwriting's). */
@Composable
private fun VoiceRailKey(
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    repeatable: Boolean = false,
    onAction: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(kb.keyRadiusDp.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
            .clip(shape)
            .background(kb.modifierKey, shape)
            .pointerInput(repeatable) {
                detectTapGestures(
                    onPress = {
                        onAction()
                        var repeat: Job? = null
                        if (repeatable) {
                            repeat = scope.launch {
                                delay(400)
                                while (true) {
                                    onAction()
                                    delay(120)
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
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(22.dp),
            tint = kb.modifierKeyText,
        )
    }
}
