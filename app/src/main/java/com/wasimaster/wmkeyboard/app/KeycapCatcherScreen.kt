package com.wasimaster.wmkeyboard.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.isActive

/**
 * The keycap catcher: the mini game behind seven taps on the version row.
 *
 * Keycaps fall from the top of the play area; a spacebar-shaped bar at the
 * bottom follows a horizontal drag; three missed keycaps end the round. The
 * fall speeds up as the score grows, which is the whole difficulty curve.
 *
 * Uses [WmLazyScreen] rather than the ordinary screen scaffold on purpose:
 * the play area needs the body's real bounds, and a game cannot live inside
 * the scaffold's scrolling column.
 */
@Composable
internal fun KeycapCatcherScreen(
    anim: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
) {
    WmLazyScreen(
        title = stringResource(R.string.egg_game_title),
        onBack = onBack,
        route = "egg_game",
        anim = anim,
    ) { padding ->
        KeycapCatcherGame(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

private enum class GamePhase { PLAYING, OVER }

/** One falling keycap. Plain vars: the frame clock, not the snapshot system, drives it. */
private class FallingCap(
    val xFrac: Float,
    var y: Float,
    val speed: Float,
    val letter: String,
)

@Composable
private fun KeycapCatcherGame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val eggPrefs = remember { EggPrefs(context) }
    var best by remember { mutableIntStateOf(eggPrefs.gameHighScore) }
    var phase by remember { mutableStateOf(GamePhase.PLAYING) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(START_LIVES) }
    // Bumping this restarts the game loop below — it is the Play again button.
    var round by remember { mutableIntStateOf(0) }
    var catcherX by remember { mutableFloatStateOf(0.5f) }
    val caps = remember { mutableStateListOf<FallingCap>() }
    // The canvas reads this so every physics tick invalidates the draw pass —
    // the caps themselves are plain objects the snapshot system cannot see.
    var frame by remember { mutableLongStateOf(0L) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val capPx = with(density) { CapSize.toPx() }
    val catcherW = with(density) { CatcherWidth.toPx() }
    val catcherH = with(density) { CatcherHeight.toPx() }
    val bottomMargin = with(density) { 24.dp.toPx() }

    // The game loop. Deliberately triggered motion — seven taps asked for it —
    // so unlike the ambient anniversary confetti it does not check reduceMotion.
    LaunchedEffect(round) {
        caps.clear()
        score = 0
        lives = START_LIVES
        phase = GamePhase.PLAYING
        var last = withFrameNanos { it }
        var untilSpawn = FIRST_SPAWN_S
        while (isActive && lives > 0) {
            val now = withFrameNanos { it }
            // Clamped so a background pause does not land every cap at once.
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val w = areaSize.width.toFloat()
            val h = areaSize.height.toFloat()
            if (w <= 0f || h <= 0f) continue

            untilSpawn -= dt
            if (untilSpawn <= 0f) {
                caps += FallingCap(
                    xFrac = 0.08f + Random.nextFloat() * 0.84f,
                    y = -capPx,
                    speed = h * (0.22f + Random.nextFloat() * 0.10f),
                    letter = ('A' + Random.nextInt(26)).toString(),
                )
                untilSpawn = (SPAWN_EVERY_S - score * SPAWN_RAMP_S).coerceAtLeast(SPAWN_FLOOR_S)
            }

            val speedMult = (1f + score * 0.05f).coerceAtMost(2.5f)
            val catchTop = h - bottomMargin - catcherH
            val landed = mutableListOf<FallingCap>()
            for (cap in caps) {
                cap.y += cap.speed * speedMult * dt
                val capX = cap.xFrac * w
                val bottom = cap.y + capPx / 2
                when {
                    bottom >= catchTop && cap.y - capPx / 2 <= catchTop + catcherH &&
                        abs(capX - catcherX * w) <= (catcherW + capPx) / 2 -> {
                        score++
                        landed += cap
                    }
                    cap.y - capPx / 2 > h -> {
                        lives--
                        landed += cap
                    }
                }
            }
            if (landed.isNotEmpty()) caps.removeAll(landed)
            frame = now
        }
        if (lives <= 0) {
            phase = GamePhase.OVER
            if (score > best) {
                best = score
                eggPrefs.gameHighScore = score
            }
        }
    }

    val capFill = MaterialTheme.colorScheme.surfaceVariant
    val capOutline = MaterialTheme.colorScheme.outline
    val catcherColor = MaterialTheme.colorScheme.primary
    val letterStyle = MaterialTheme.typography.titleMedium
        .copy(color = MaterialTheme.colorScheme.onSurface)
    val textMeasurer = rememberTextMeasurer()
    val letterLayouts = remember(textMeasurer, letterStyle) {
        HashMap<String, TextLayoutResult>()
    }

    Box(
        modifier
            .onSizeChanged { areaSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (areaSize.width > 0) {
                        catcherX = (catcherX + drag.x / areaSize.width).coerceIn(0.05f, 0.95f)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Read on purpose: this is what ties the draw pass to the loop.
            frame
            val corner = CornerRadius(capPx * 0.2f)
            for (cap in caps) {
                val topLeft = Offset(cap.xFrac * size.width - capPx / 2, cap.y - capPx / 2)
                drawRoundRect(capFill, topLeft = topLeft, size = Size(capPx, capPx), cornerRadius = corner)
                drawRoundRect(
                    capOutline, topLeft = topLeft, size = Size(capPx, capPx),
                    cornerRadius = corner, style = Stroke(width = 1.dp.toPx()),
                )
                val layout = letterLayouts.getOrPut(cap.letter) {
                    textMeasurer.measure(AnnotatedString(cap.letter), letterStyle)
                }
                drawText(
                    layout,
                    topLeft = Offset(
                        cap.xFrac * size.width - layout.size.width / 2f,
                        cap.y - layout.size.height / 2f,
                    ),
                )
            }
            drawRoundRect(
                catcherColor,
                topLeft = Offset(
                    catcherX * size.width - catcherW / 2,
                    size.height - bottomMargin - catcherH,
                ),
                size = Size(catcherW, catcherH),
                cornerRadius = CornerRadius(catcherH / 2),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.egg_game_score_label, score),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.egg_game_best_label, best),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            val livesDesc = stringResource(R.string.egg_game_lives_desc, lives)
            Text(
                "♥".repeat(lives),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = livesDesc },
            )
        }

        if (phase == GamePhase.PLAYING && score == 0) {
            Text(
                stringResource(R.string.egg_game_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }

        if (phase == GamePhase.OVER) {
            Card(modifier = Modifier.align(Alignment.Center)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.egg_game_over_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.egg_game_score_label, score),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.egg_game_best_label, best),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { round++ }) {
                        Text(stringResource(R.string.egg_game_play_again_action))
                    }
                }
            }
        }
    }
}

private val CapSize = 44.dp
private val CatcherWidth = 140.dp
private val CatcherHeight = 22.dp
private const val START_LIVES = 3
private const val FIRST_SPAWN_S = 0.6f
private const val SPAWN_EVERY_S = 1.2f
private const val SPAWN_RAMP_S = 0.02f
private const val SPAWN_FLOOR_S = 0.45f
