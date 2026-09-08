package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.prediction.OctopusKind
import com.wasimaster.wmkeyboard.core.prediction.OctopusWord
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.OctopusPlacement
import kotlin.math.roundToInt

/**
 * One floating word and the rectangle it occupies, both in the key grid's own
 * space.
 *
 * [area] is where it draws, which on the top row reaches above the grid; [hit]
 * is the part of that a finger can actually reach, which is [area] clipped to
 * the board. The two differ only on the top row, and that is the whole of the
 * compromise there: the word is drawn where the eye expects it and tapped where
 * the grid still receives touches.
 */
@Immutable
internal data class OctopusSlot(val word: OctopusWord, val area: Rect, val hit: Rect)

/**
 * Where each word draws, given the keys' measured cells.
 *
 * Pure geometry, so the whole of the layout's behaviour — the placements, the
 * overhang, the overlap rule, the top row — is checkable without a keyboard.
 * [widthOf] measures a word at the size it will be drawn; it is a parameter for
 * the same reason, and because it is the only thing here that needs a font.
 *
 * A key that has not reported its bounds yet yields no slot rather than a slot
 * at the origin.
 */
internal fun octopusSlots(
    words: Collection<OctopusWord>,
    bounds: Map<Int, Rect>,
    placement: OctopusPlacement,
    bandHeightPx: Float,
    /**
     * How much of the band sits above the key's top edge, 0..1. Not 1: the word
     * straddles its key rather than clearing it, which is what both the Z10 and
     * the Octopus tweak drew, and what leaves the top row something a finger can
     * still reach — a band wholly above the grid is outside every touch it gets.
     */
    straddle: Float,
    gapVPx: Float,
    maxOverhangPx: Float,
    boardSize: Size,
    widthOf: (String) -> Float,
): List<OctopusSlot> {
    if (bounds.isEmpty() || bandHeightPx <= 0f) return emptyList()
    val kept = ArrayList<OctopusSlot>(words.size)
    for (word in words.sortedBy { it.rank }) {
        val cell = bounds[word.keyCodePoint] ?: continue
        val text = widthOf(word.word)
        if (text <= 0f) continue
        // A word may lean into the gaps on either side, but no further: past
        // that it stops reading as belonging to its own key. One that cannot
        // fit even then is dropped rather than cut down to an ellipsis, which
        // is what keeps a dense board from turning into a wall of stubs.
        val allowed = cell.width + maxOverhangPx * 2
        if (text > allowed) continue
        val top = when (placement) {
            // STRIP shares FLOAT's arithmetic exactly: the reserved lane moved
            // the cell down, so "the band above the cell" is already the lane.
            OctopusPlacement.FLOAT, OctopusPlacement.STRIP ->
                cell.top - bandHeightPx * straddle.coerceIn(0f, 1f)
            OctopusPlacement.IN_KEY -> cell.top + gapVPx
        }
        val centre = cell.center.x
        var left = centre - text / 2
        var right = centre + text / 2
        // Pulled inside the board rather than allowed to hang off it: nothing
        // out there can be read, let alone pressed.
        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (right > boardSize.width) {
            left -= right - boardSize.width
            right = boardSize.width
        }
        val area = Rect(left.coerceAtLeast(0f), top, right, top + bandHeightPx)
        // Walked in rank order, so the better word keeps its place and the
        // worse one goes. Shrinking or shifting the loser would leave a word
        // that no longer points at its own key, which is the one thing the
        // whole feature must not do.
        if (kept.any { it.area.overlaps(area) }) continue
        val hitTop = area.top.coerceIn(0f, boardSize.height)
        val hitBottom = area.bottom.coerceIn(0f, boardSize.height)
        kept.add(
            OctopusSlot(
                word = word,
                area = area,
                hit = if (hitBottom > hitTop) {
                    Rect(area.left, hitTop, area.right, hitBottom)
                } else {
                    Rect.Zero
                },
            )
        )
    }
    return kept
}

/**
 * The floating words, drawn over the keys (discussion #102).
 *
 * A sibling of the key grid rather than something the keys draw, for the reason
 * [AutopilotOverlay] states and [KeyVisual] enforces: a key that read the live
 * prediction would cost the whole board its per-keystroke recomposition skip.
 * The live maps are read here, in this leaf's own scope, so a key reporting its
 * position invalidates the overlay and nothing else.
 *
 * Not a [androidx.compose.ui.window.Popup], deliberately, unlike the glide
 * overlay and the key preview. Those exist only while a finger is down; this
 * one would stand for the whole session, and a window over the host app is
 * exactly what the untrusted-touch rules punish. Drawn in the grid instead, it
 * costs no window, it is hit-tested in the grid's own coordinates, and the
 * accessibility carve-out — which is the grid, not the headroom — covers all of
 * it rather than half.
 *
 * Nothing here takes a touch: plain boxes and text, so every press falls
 * through to the key underneath. The taps are resolved from [rects] by the
 * grid's own pointer loop.
 */
@Composable
internal fun BoxScope.OctopusOverlay(
    words: Map<Int, OctopusWord>,
    bounds: Map<Int, Rect>,
    /** The key grid's own size; nothing is tapped past it. */
    boardSize: Size,
    /** A glide stroke owns the board, and the picker may be asking already. */
    hidden: Boolean,
    settings: KeyboardSettings,
    palette: KeyPalette,
    kb: KbTheme,
    rects: OctopusRects,
) {
    val octopus = settings.octopus
    if (hidden || words.isEmpty() || bounds.isEmpty() || boardSize.width <= 0f) {
        rects.publish(emptyList(), bounds)
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val fontSize = OctopusLabelSp * settings.fontScale * octopus.fontScale
    val style = hintTextStyle().copy(fontSize = fontSize.sp)
    val bandHeightPx = with(density) { (fontSize * OctopusBandLines).sp.toPx() }
    val gapVPx = with(density) { keyGapV(settings).toPx() }
    val maxOverhangPx = with(density) { OctopusOverhangDp.toPx() }
    val slots = octopusSlots(
        words = words.values,
        bounds = bounds,
        placement = octopus.placement,
        bandHeightPx = bandHeightPx,
        straddle = OctopusStraddle,
        gapVPx = gapVPx,
        maxOverhangPx = maxOverhangPx,
        boardSize = boardSize,
        widthOf = { measurer.measure(it, style, maxLines = 1).size.width.toFloat() },
    )
    // Stamped with the very table the rectangles were measured from, so a
    // layout change — which hands the grid a fresh one — reads as nothing until
    // this has run again against it.
    rects.publish(slots, bounds)
    val head = palette.keyText.copy(alpha = OctopusHeadAlpha)
    for (slot in slots) {
        val area = slot.area
        Box(
            modifier = Modifier
                // The key underneath is what a screen reader reads; this word
                // reaches TalkBack through that key's own custom action, so a
                // node here would only offer a target it could never activate.
                .clearAndSetSemantics { }
                .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                .size(
                    width = with(density) { area.width.toDp() },
                    height = with(density) { area.height.toDp() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = octopusLabel(slot.word, head, kb.accent),
                style = style,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The word in two tones, split where what the user has typed ends.
 *
 * The head is faded rather than absent: it says "you have written this much",
 * which is what makes the tail read as an offer instead of a whole new word.
 * A correction has no head worth claiming — the point of it is that what was
 * typed was wrong — so it is drawn in the accent throughout, and that is what
 * separates a fix from a continuation at a glance.
 */
internal fun octopusLabel(word: OctopusWord, head: Color, accent: Color): AnnotatedString {
    // Coerced rather than trusted: a candidate and a buffer that have drifted
    // apart must not throw in the middle of a draw.
    val split = if (word.kind == OctopusKind.CORRECTION) {
        0
    } else {
        word.typedChars.coerceIn(0, word.word.length)
    }
    return buildAnnotatedString {
        if (split > 0) {
            withStyle(SpanStyle(color = head)) { append(word.word.substring(0, split)) }
        }
        if (split < word.word.length) {
            withStyle(SpanStyle(color = accent)) { append(word.word.substring(split)) }
        }
    }
}

/**
 * Base size of a floating word, before the board's own font scale and the
 * user's octopus size. A step above the corner hint's 8.5sp: a hint is one
 * character read off a key already under the eye, and these are whole words
 * read across a board.
 */
private const val OctopusLabelSp = 10.5f

/** Band height as a multiple of the font size, leaving room for descenders. */
private const val OctopusBandLines = 1.35f

/**
 * How much of that band clears the key. The rest overlaps it, so the word reads
 * as belonging to the key rather than floating between two rows — and so the top
 * row, whose band would otherwise be entirely off the grid, keeps a strip of
 * itself inside the only area that receives touches.
 */
private const val OctopusStraddle = 0.72f

/** How far a word may lean into the gap on either side of its own key. */
private val OctopusOverhangDp = 14.dp

/** The typed head, faded against the completion it introduces. */
private const val OctopusHeadAlpha = 0.55f
