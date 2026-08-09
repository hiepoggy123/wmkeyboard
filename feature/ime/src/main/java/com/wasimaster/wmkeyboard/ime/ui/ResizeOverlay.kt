package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.settings.ScreenVariant
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.ime.SizingAction
import kotlin.math.roundToInt

/**
 * The three stored-space values the inline resize tool drives. "Stored space"
 * means the numbers as the settings hold them, before the screen variant's
 * `keyboardScale` is folded in — what a drag produces here is exactly what
 * Done hands the repository, with no lossy round trip through rendered sizes.
 */
@Immutable
internal data class ResizeValues(
    val keyHeightDp: Int,
    val numberRowHeightDp: Int,
    val bottomPaddingDp: Int,
)

/**
 * One open resize session, built by [KeyboardScreen] where the screen variant
 * and the raw settings are known. A bundle rather than parameters for the
 * usual reason: everything reaching the keyboard body has to squeeze past the
 * 64K method ceiling of KeyboardScreen's caller.
 *
 * [entry] is what the values were when the tool opened; Reset returns to it by
 * clearing [preview] back to null. [onCommit] gets the final values on Done
 * (null when nothing was dragged).
 */
@Immutable
internal class ResizeSession(
    val entry: ResizeValues,
    val keyboardScale: Float,
    val maxBottomPaddingDp: Int,
    val preview: MutableState<ResizeValues?>,
    val onCommit: (ResizeValues?) -> Unit,
)

/**
 * Heights after a top-handle drag: [frac] is "how tall the scalable region is
 * now" over "how tall it was at drag start", applied to the drag-start values
 * so both heights keep their ratio and re-derive from one clean baseline every
 * frame (accumulating per-frame deltas drifts).
 */
internal fun resizeScaledHeights(start: ResizeValues, frac: Float): ResizeValues = start.copy(
    keyHeightDp = (start.keyHeightDp * frac).roundToInt()
        .coerceIn(SettingsRepository.KEY_HEIGHT_MIN_DP, SettingsRepository.KEY_HEIGHT_MAX_DP),
    numberRowHeightDp = (start.numberRowHeightDp * frac).roundToInt()
        .coerceIn(SettingsRepository.KEY_HEIGHT_MIN_DP, SettingsRepository.KEY_HEIGHT_MAX_DP),
)

/**
 * Padding after a bottom-handle or move-button drag of [dyDp] (screen
 * coordinates: positive = finger moved down = keyboard sinks).
 */
internal fun resizePaddedBy(start: ResizeValues, dyDp: Int, maxPad: Int): ResizeValues =
    start.copy(bottomPaddingDp = (start.bottomPaddingDp - dyDp).coerceIn(0, maxPad))

/** The top handle is pinned: the key height can go no further either way. */
internal fun resizeHeightAtLimit(values: ResizeValues): Boolean =
    values.keyHeightDp <= SettingsRepository.KEY_HEIGHT_MIN_DP ||
        values.keyHeightDp >= SettingsRepository.KEY_HEIGHT_MAX_DP

/** The bottom handle / move button is pinned against a padding bound. */
internal fun resizePadAtLimit(values: ResizeValues, maxPad: Int): Boolean =
    values.bottomPaddingDp <= 0 || values.bottomPaddingDp >= maxPad

/**
 * What Done sends the service. Fields that match [entry] go as null — "was
 * not changed, write nothing" — which is what keeps a padding-only session
 * from freezing the key height and losing the tablet defaults (see
 * `keyHeightUntouched` in the settings repository).
 */
internal fun resizeCommitAction(
    variant: ScreenVariant,
    entry: ResizeValues,
    result: ResizeValues?,
): SizingAction = if (result == null) {
    SizingAction.ResizeCancel
} else {
    SizingAction.ResizeCommit(
        variant = variant,
        keyHeightDp = result.keyHeightDp.takeIf { it != entry.keyHeightDp },
        numberRowHeightDp = result.numberRowHeightDp.takeIf { it != entry.numberRowHeightDp },
        bottomPaddingDp = result.bottomPaddingDp.takeIf { it != entry.bottomPaddingDp },
    )
}

/**
 * Per-gesture scratch, deliberately not snapshot state (the same trick as the
 * floating frame's FloatingGesture): the pointer handlers write it every
 * frame and nothing composes from it, so touching it invalidates nothing.
 * [baseScalablePx] is measured once at drag start — re-reading it per frame
 * feeds the drag's own effect back into the mapping and oscillates.
 */
private class ResizeGesture {
    var start: ResizeValues? = null
    var baseScalablePx = 0f
    var accY = 0f
}

/**
 * The inline resize mode, drawn over the whole docked frame: the keyboard
 * dims behind a scrim that eats every touch, an accent outline hugs the
 * keyboard's rectangle, and three drags drive the geometry — the top handle
 * scales the key heights, the bottom handle and the centre move button both
 * ride the bottom padding. Reset returns to the values the tool opened with;
 * Done persists and leaves. Handles turn the mistake red when pinned at a
 * limit.
 *
 * Everything previews through [ResizeSession.preview] and nothing touches the
 * DataStore until Done — see the session doc for why that ordering is the
 * whole design.
 */
@Composable
internal fun BoxScope.ResizeOverlay(
    session: ResizeSession,
    state: KeyboardUiState,
) {
    val kb = LocalKbTheme.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val current = session.preview.value ?: session.entry
    val arrangement = dockedWidthArrangement(state.settings)
    // What the height drag's frac is measured against: the key grid as it
    // stands right now. Captured into the gesture at drag start (not per
    // frame) via rememberUpdatedState so the pointerInput lambda never goes
    // stale across recompositions.
    val scalablePx = with(density) { keyRowsHeight(state).toPx() }
    val latestScalablePx = rememberUpdatedState(scalablePx)
    val latestValues = rememberUpdatedState(current)

    val heightAtLimit = resizeHeightAtLimit(current)
    val padAtLimit = resizePadAtLimit(current, session.maxBottomPaddingDp)
    val limitColor = errorLimitColor(kb)

    // Scrim first: it spans toolbar, keys and padding alike, and it consumes
    // every gesture that no handle claimed — the resize mode must never type.
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInputSwallowAll(),
    )
    // The keyboard-hugging frame: after the same insets the keyboard Row
    // takes, what is left of the docked Box is exactly the rectangle the
    // keys are laid out in — so the outline tracks both drags for free.
    Row(
        modifier = Modifier
            .matchParentSize()
            .navigationBarsPadding()
            .padding(bottom = state.settings.bottomPaddingDp.dp),
    ) {
        if (arrangement.leftSlack > 0.001f) {
            Spacer(modifier = Modifier.weight(arrangement.leftSlack))
        }
        Box(
            modifier = Modifier
                .weight(arrangement.widthFraction)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, kb.accent, RoundedCornerShape(16.dp)),
            )
            val heightGesture = remember { ResizeGesture() }
            ResizeHandleBar(
                color = if (heightAtLimit) limitColor else kb.accent,
                description = stringResource(R.string.ime_resize_height_handle_desc),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .pointerInput(session) {
                        detectDragGestures(
                            onDragStart = {
                                heightGesture.start = latestValues.value
                                heightGesture.baseScalablePx = latestScalablePx.value
                                heightGesture.accY = 0f
                            },
                            onDragEnd = { heightGesture.start = null },
                            onDragCancel = { heightGesture.start = null },
                        ) { change, dragAmount ->
                            change.consume()
                            val start = heightGesture.start ?: return@detectDragGestures
                            val base = heightGesture.baseScalablePx
                            if (base <= 0f) return@detectDragGestures
                            heightGesture.accY += dragAmount.y
                            val frac = (base - heightGesture.accY) / base
                            val next = resizeScaledHeights(start, frac)
                            publishPreview(session, next, haptic) {
                                resizeHeightAtLimit(it)
                            }
                        }
                    },
            )
            val padGesture = remember { ResizeGesture() }
            val padDrag = { gesture: ResizeGesture, dragY: Float ->
                val start = gesture.start
                if (start != null) {
                    gesture.accY += dragY
                    val dyDp = with(density) { gesture.accY.toDp() }.value.roundToInt()
                    val next = resizePaddedBy(start, dyDp, session.maxBottomPaddingDp)
                    publishPreview(session, next, haptic) {
                        resizePadAtLimit(it, session.maxBottomPaddingDp)
                    }
                }
            }
            ResizeHandleBar(
                color = if (padAtLimit) limitColor else kb.accent,
                description = stringResource(R.string.ime_resize_padding_handle_desc),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .pointerInput(session) {
                        detectDragGestures(
                            onDragStart = {
                                padGesture.start = latestValues.value
                                padGesture.accY = 0f
                            },
                            onDragEnd = { padGesture.start = null },
                            onDragCancel = { padGesture.start = null },
                        ) { change, dragAmount ->
                            change.consume()
                            padDrag(padGesture, dragAmount.y)
                        }
                    },
            )
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ResizePill(
                    icon = Icons.Outlined.RestartAlt,
                    label = stringResource(R.string.ime_resize_reset),
                    onClick = {
                        haptic()
                        session.preview.value = null
                    },
                )
                val moveGesture = remember { ResizeGesture() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(kb.popup)
                        .border(1.dp, if (padAtLimit) limitColor else kb.accent, CircleShape)
                        .pointerInput(session) {
                            detectDragGestures(
                                onDragStart = {
                                    moveGesture.start = latestValues.value
                                    moveGesture.accY = 0f
                                },
                                onDragEnd = { moveGesture.start = null },
                                onDragCancel = { moveGesture.start = null },
                            ) { change, dragAmount ->
                                change.consume()
                                padDrag(moveGesture, dragAmount.y)
                            }
                        },
                ) {
                    Icon(
                        Icons.Outlined.OpenWith,
                        contentDescription = stringResource(R.string.ime_resize_move_desc),
                        tint = if (padAtLimit) limitColor else kb.popupText,
                        modifier = Modifier.size(24.dp),
                    )
                }
                ResizePill(
                    icon = Icons.Outlined.Check,
                    label = stringResource(R.string.ime_resize_done),
                    onClick = {
                        haptic()
                        session.onCommit(session.preview.value)
                    },
                )
            }
        }
        if (arrangement.rightSlack > 0.001f) {
            Spacer(modifier = Modifier.weight(arrangement.rightSlack))
        }
    }
}

/**
 * Pushes [next] into the live preview if it moved, with one haptic tick the
 * moment a drag first pins against its limit (and none while it stays there).
 */
private inline fun publishPreview(
    session: ResizeSession,
    next: ResizeValues,
    haptic: () -> Unit,
    atLimit: (ResizeValues) -> Boolean,
) {
    val previous = session.preview.value ?: session.entry
    if (next == previous) return
    if (atLimit(next) && !atLimit(previous)) haptic()
    session.preview.value = next
}

/**
 * A drag handle: a short pill drawn on the outline's edge, wrapped in a much
 * larger invisible hit target so a thumb finds it without looking.
 */
@Composable
private fun ResizeHandleBar(
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(96.dp)
            .height(32.dp)
            .semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(color),
        )
    }
}

/** Reset / Done: the tool's two buttons, in the keyboard's popup styling. */
@Composable
private fun ResizePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(kb.popup)
            .border(1.dp, kb.accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = kb.popupText, modifier = Modifier.size(16.dp))
        Text(
            label,
            color = kb.popupText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Mistake red for a handle pinned at its limit. Same pair as the typing
 * test's error colour: lighter on dark boards so it stays legible.
 */
private fun errorLimitColor(kb: KbTheme): Color =
    if (kb.dark) Color(0xFFEF7070) else Color(0xFFD64545)

/** Consumes every pointer event outright: the scrim types nothing, ever. */
private fun Modifier.pointerInputSwallowAll(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
