package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Room the docked frame holds back for a bar row that is still growing in.
 *
 * The frame is the IME window's height. A row expanding into the stack grew
 * the frame with it, so every frame of the expand was a window relayout and a
 * new content inset, and every new inset re-laid out the host app — which, at
 * the moment a selection appears, is busy drawing a selection toolbar of its
 * own. That was the stutter in the selection macro bar's entrance.
 *
 * So while the row is shorter than its content, the frame adds the difference
 * back above the board. The window takes its final height on the first frame,
 * the host app is re-laid out once, and the row grows into space the frame
 * already holds: the resize tool's trick ([resizeHeadroom]), sized frame by
 * frame instead of latched. Nothing is drawn in the held space, so the top of
 * the board still travels up with the row.
 *
 * One row's worth: two rows growing at once would write over each other.
 *
 * Both heights are written from the row's measure pass and read by the frame
 * later in that same pass, because a row that changed height re-measures every
 * ancestor up to the frame. They are state rather than plain fields for the
 * one case that pass does not cover — a row taken out of the stack mid-growth,
 * whose reset has to reach a frame that nothing else re-measures.
 */
internal class RowRevealHeadroom {
    /** The row's content at full height, measured inside the reveal. */
    var fullPx by mutableIntStateOf(0)

    /** How much of that the reveal gives the row right now. */
    var shownPx by mutableIntStateOf(0)

    /** What the frame holds back: the part of the row not grown into yet. */
    val pendingPx: Int
        get() = (fullPx - shownPx).coerceAtLeast(0)
}

/**
 * Provided by the docked frame. Null in floating mode, where the window already
 * spans the screen and a row growing inside the panel resizes nothing.
 */
internal val LocalRowRevealHeadroom = staticCompositionLocalOf<RowRevealHeadroom?> { null }

/**
 * The frame's half: as tall as what it holds plus whatever a growing row has
 * not claimed yet, with the keyboard at the bottom and the held space empty.
 *
 * A measure-scope read, like [resizeHeadroom]'s: a change re-measures this
 * node and recomposes nothing.
 */
internal fun Modifier.rowRevealHeadroom(headroom: RowRevealHeadroom): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height + headroom.pendingPx).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
}

/**
 * The row's half: [AnimatedVisibility], reporting its two heights to the
 * frame's [RowRevealHeadroom], or a plain one when no frame is listening.
 *
 * The full height is read inside the reveal and the shown height outside it.
 * Only an entrance is held. A row on its way out reports no full height, so
 * the frame never keeps space for something leaving, nor past the last exit
 * frame, after which the reveal drops its content and nothing measures it
 * again. The macro bar's exit is over content that has already emptied anyway,
 * and still collapses on its first frame as it always has.
 */
@Composable
internal fun ColumnScope.RevealingBarRow(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    val headroom = LocalRowRevealHeadroom.current
    if (headroom == null) {
        AnimatedVisibility(visible = visible, enter = enter, exit = exit) { content() }
        return
    }
    // A row cut mid-growth (a full-bleed panel, the placement switched) must
    // not leave its shortfall held above the board.
    DisposableEffect(headroom) {
        onDispose {
            headroom.fullPx = 0
            headroom.shownPx = 0
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            headroom.shownPx = placeable.height
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
        enter = enter,
        exit = exit,
    ) {
        Box(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                headroom.fullPx = if (visible) placeable.height else 0
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        ) {
            content()
        }
    }
}
