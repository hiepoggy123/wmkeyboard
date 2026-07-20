package com.wasimaster.wmkeyboard.ime.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wasimaster.wmkeyboard.core.media.MediaSnapshot
import com.wasimaster.wmkeyboard.core.media.hasNotificationAccess
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import kotlinx.coroutines.delay

/**
 * Media-control tool: album art, track metadata, a seek bar and the play /
 * pause / skip transport for whatever app currently owns a media session —
 * the same thing the lock screen shows. The service owns the
 * [com.wasimaster.wmkeyboard.core.media.MediaControlManager]; this panel only
 * renders [KeyboardUiState.mediaControl] and reports taps.
 *
 * Reading another app's session needs a notification-listener grant, which
 * the panel checks itself (mirroring how [VoicePanel] checks the mic
 * permission) and prompts for when missing.
 */
@Composable
internal fun MediaControlPanel(
    state: KeyboardUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onRequestAccess: () -> Unit,
    onResume: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    // The grant is toggled in system Settings, in another task; re-check when
    // the keyboard comes back to the foreground, and re-bind the session then
    // (the service could not start listening while access was still off).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasNotificationAccess(context)
                if (hasAccess) onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val track = state.mediaControl
        when {
            !hasAccess -> AccessPrompt(kb, onRequestAccess)
            track == null -> Notice(
                kb,
                Icons.Outlined.MusicNote,
                "Nothing playing right now",
                "Start a song, video or podcast in any app and its controls appear here.",
            )
            else -> NowPlaying(kb, track, onPlayPause, onNext, onPrevious, onSeek)
        }
    }
}

@Composable
private fun NowPlaying(
    kb: KbTheme,
    track: MediaSnapshot,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art (or a placeholder note), a square as tall as the panel.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(kb.chip),
            contentAlignment = Alignment.Center,
        ) {
            val art = track.art
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = kb.secondaryText,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                track.title.ifBlank { "Unknown title" },
                color = kb.keyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = track.artist.ifBlank { track.appLabel }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Third line: album, or the source app when there's no album so the
            // user still sees where the audio is coming from.
            val third = track.album.ifBlank {
                if (track.artist.isNotBlank()) track.appLabel else ""
            }
            if (third.isNotBlank()) {
                Text(
                    third,
                    color = kb.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.8f),
                )
            }

            if (track.durationMs > 0L) {
                Spacer(Modifier.height(8.dp))
                SeekBar(kb, track, onSeek)
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaButton(
                    kb, Icons.Outlined.SkipPrevious, "Previous",
                    size = 44.dp, enabled = track.canPrev, onClick = onPrevious,
                )
                MediaButton(
                    kb,
                    if (track.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (track.playing) "Pause" else "Play",
                    size = 56.dp, accent = true, onClick = onPlayPause,
                )
                MediaButton(
                    kb, Icons.Outlined.SkipNext, "Next",
                    size = 44.dp, enabled = track.canNext, onClick = onNext,
                )
            }
        }
    }
}

@Composable
private fun SeekBar(kb: KbTheme, track: MediaSnapshot, onSeek: (Long) -> Unit) {
    // Live position: extrapolate from the last stamp while playing so the bar
    // creeps forward between the sparse state updates the session sends.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track.playing, track.positionMs, track.positionUpdateTimeMs) {
        now = SystemClock.elapsedRealtime()
        while (track.playing) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val livePosition = if (track.playing) {
        track.positionMs + ((now - track.positionUpdateTimeMs) * track.speed).toLong()
    } else {
        track.positionMs
    }.coerceIn(0L, track.durationMs)
    val playedFraction = (livePosition.toFloat() / track.durationMs).coerceIn(0f, 1f)
    val shown = if (scrubbing) scrubFraction else playedFraction

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { scrubbing = true; scrubFraction = it },
            onValueChangeFinished = {
                onSeek((scrubFraction * track.durationMs).toLong())
                scrubbing = false
            },
            enabled = track.canSeek,
            colors = SliderDefaults.colors(
                thumbColor = kb.accent,
                activeTrackColor = kb.accent,
                inactiveTrackColor = kb.divider,
                disabledThumbColor = kb.secondaryText,
                disabledActiveTrackColor = kb.secondaryText,
                disabledInactiveTrackColor = kb.divider,
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatTime((shown * track.durationMs).toLong()),
                color = kb.secondaryText, fontSize = 10.sp,
            )
            Text(formatTime(track.durationMs), color = kb.secondaryText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MediaButton(
    kb: KbTheme,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (accent) kb.accent else kb.toolCircle)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (accent) kb.toolCircleActiveIcon else kb.toolbarIcon,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun AccessPrompt(kb: KbTheme, onRequestAccess: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            tint = kb.secondaryText,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To show and control what's playing, WM Keyboard needs notification " +
                "access. It reads no notifications — only the media session.",
            color = kb.secondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(kb.keyRadiusDp.dp))
                .background(kb.accent)
                .clickable { onRequestAccess() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                "Grant access",
                color = kb.toolCircleActiveIcon,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Notice(
    kb: KbTheme,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = kb.secondaryText,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, color = kb.keyText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            color = kb.secondaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Milliseconds → m:ss (or h:mm:ss for long content). */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
