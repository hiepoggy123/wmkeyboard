package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A one-shot confetti burst for the install-anniversary card: paper rectangles
 * fall from the top edge, sway, spin and fade, and the whole thing retires by
 * itself after a few seconds.
 *
 * Deliberately its own little `Canvas` rather than a lift of the keyboard's
 * `ParticleField`: that engine is `internal` to `:feature:ime` and shaped
 * around key-press bursts, and promoting it to a shared module is more public
 * surface than one celebration a year justifies.
 *
 * Callers must not draw this when `reduceMotion` is on — the celebration
 * degrades to the card and the toast, which hold still.
 */
@Composable
internal fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val pieces = remember { List(PIECE_COUNT) { ConfettiPiece(Random) } }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (elapsedMs < TOTAL_MS) {
            withFrameNanos { now -> elapsedMs = (now - startNanos) / 1_000_000 }
        }
        onFinished()
    }
    val pieceSize = with(LocalDensity.current) { 10.dp.toPx() }
    Canvas(modifier = modifier.fillMaxSize()) {
        for (piece in pieces) {
            // Each piece runs its own clock, offset by its delay, so the burst
            // arrives as a shower rather than a curtain.
            val t = (elapsedMs - piece.delayMs) / piece.fallMs
            if (t < 0f || t > 1f) continue
            val y = t * (size.height + pieceSize * 2) - pieceSize
            val x = piece.lane * size.width + sin(t * piece.swayTurns + piece.swayPhase) * piece.swayAmp
            val alpha = ((1f - t) * 4f).coerceIn(0f, 1f)
            rotate(degrees = t * piece.spinDegrees, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color,
                    topLeft = Offset(x - pieceSize * piece.scale / 2, y - pieceSize * piece.scale / 4),
                    size = Size(pieceSize * piece.scale, pieceSize * piece.scale / 2),
                    alpha = alpha,
                )
            }
        }
    }
}

/** One paper rectangle: where it falls, how it moves, what it looks like. */
private class ConfettiPiece(random: Random) {
    val lane = random.nextFloat()
    val delayMs = random.nextFloat() * 1200f
    val fallMs = 1600f + random.nextFloat() * 1200f
    val swayAmp = 20f + random.nextFloat() * 40f
    val swayTurns = 4f + random.nextFloat() * 4f
    val swayPhase = random.nextFloat() * 6.28f
    val spinDegrees = (random.nextFloat() - 0.5f) * 1080f
    val scale = 0.7f + random.nextFloat() * 0.6f
    val color = PALETTE[random.nextInt(PALETTE.size)]
}

/** Festive and theme-independent: confetti has no dark mode. */
private val PALETTE = listOf(
    Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFFFFEE58),
    Color(0xFF66BB6A), Color(0xFF42A5F5), Color(0xFFAB47BC),
)

private const val PIECE_COUNT = 80
private const val TOTAL_MS = 4000L
