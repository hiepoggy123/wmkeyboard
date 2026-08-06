package com.wasimaster.wmkeyboard.ime.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.withFrameMillis
import com.wasimaster.wmkeyboard.core.theme.KeyEffectKind
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The key-press particle burst, built on the glide trail's architecture: all
 * per-particle data lives in preallocated plain arrays invisible to Compose,
 * with one `revision` counter subscribed inside the draw lambda and one
 * `active` flag that composes the canvas and runs the frame loop. A spawn is
 * array writes plus those two state bumps — no composition work rides the
 * press path — and when the last particle dies the loop exits, so an idle
 * keyboard spends nothing.
 *
 * Physics is stateless: a particle stores its birth position, velocity and
 * time, and the draw computes where it is from its age. The frame loop only
 * invalidates the draw and retires the dead.
 */
@Stable
internal class ParticleField {

    val x = FloatArray(CAPACITY)
    val y = FloatArray(CAPACITY)
    val vx = FloatArray(CAPACITY)
    val vy = FloatArray(CAPACITY)
    val sizePx = FloatArray(CAPACITY)
    val spinDegPerS = FloatArray(CAPACITY)
    val bornAt = LongArray(CAPACITY)
    val glyphIndex = IntArray(CAPACITY)

    private var head = 0

    var revision by mutableIntStateOf(0)
        private set
    var active by mutableStateOf(false)
        private set

    /** The frame clock's last stamp; what the draw measures ages against. */
    var nowMs by mutableLongStateOf(0L)
        private set

    fun spawn(cx: Float, cy: Float, count: Int, glyphCount: Int, now: Long) {
        if (glyphCount <= 0) return
        repeat(count) {
            val i = head
            head = (head + 1) % CAPACITY
            x[i] = cx
            y[i] = cy
            // Upward fan: bursts read as celebration, not as rain.
            vx[i] = Random.nextFloat() * 360f - 180f
            vy[i] = -(Random.nextFloat() * 380f + 180f)
            sizePx[i] = Random.nextFloat() * 0.5f + 0.75f
            spinDegPerS[i] = Random.nextFloat() * 360f - 180f
            bornAt[i] = now
            glyphIndex[i] = Random.nextInt(glyphCount)
        }
        nowMs = now
        revision++
        if (!active) active = true
    }

    /** One frame: repaints, and puts the field to sleep once everything died. */
    fun frame(now: Long) {
        nowMs = now
        if (bornAt.none { it > 0 && now - it < LIFETIME_MS }) {
            active = false
        }
    }

    fun clear() {
        bornAt.fill(0L)
        active = false
    }

    companion object {
        const val CAPACITY = 48
        const val LIFETIME_MS = 650L
        const val GRAVITY_PX_S2 = 1400f
    }
}

/** The grid's particle field, or null when no effect can ever spawn. */
internal val LocalParticleField = staticCompositionLocalOf<ParticleField?> { null }

/** How many particles one press throws, before the theme's intensity. */
private const val BASE_BURST = 5

/** Burst size for the active theme; 0 disables spawning entirely. */
internal fun burstCount(kb: KbTheme): Int =
    if (kb.keyEffect == null || kb.reduceMotion) {
        0
    } else {
        (BASE_BURST * kb.keyEffectIntensity).roundToInt().coerceIn(1, 12)
    }

/** The glyphs an effect kind throws; one particle picks one at random. */
internal fun effectGlyphs(kind: KeyEffectKind, param: String): List<String> = when (kind) {
    KeyEffectKind.STARS -> listOf("⭐", "🌟", "✨")
    KeyEffectKind.HEARTS -> listOf("❤️", "💖", "💜")
    KeyEffectKind.SPARKLE -> listOf("✨", "❇️", "💫")
    KeyEffectKind.CONFETTI -> listOf("🎊", "🎉", "🟡", "🔴", "🔵")
    KeyEffectKind.EMOJI -> {
        // Each grapheme-ish chunk is one particle kind. A BreakIterator would
        // be exact; splitting on code points pairs surrogates well enough for
        // the emoji people actually type, and a broken chunk just draws tofu
        // in a 600 ms particle.
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < param.length && chunks.size < 8) {
            val end = param.offsetByCodePoints(i, 1)
            chunks.add(param.substring(i, end))
            i = end
        }
        chunks.filter { it.isNotBlank() }.ifEmpty { listOf("🎉") }
    }
}

/**
 * Pre-rasterized particle glyphs. Text layout per frame would be the whole
 * frame budget; each glyph becomes one small bitmap, drawn per particle with
 * a transform and a fade.
 */
@Composable
internal fun rememberEffectGlyphs(kb: KbTheme): List<ImageBitmap> {
    val kind = kb.keyEffect ?: return emptyList()
    return remember(kind, kb.keyEffectParam) {
        effectGlyphs(kind, kb.keyEffectParam).map { rasterizeGlyph(it) }
    }
}

private const val GLYPH_PX = 56

private fun rasterizeGlyph(glyph: String): ImageBitmap {
    val bitmap = Bitmap.createBitmap(GLYPH_PX, GLYPH_PX, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = GLYPH_PX * 0.8f
        textAlign = Paint.Align.CENTER
    }
    val baseline = GLYPH_PX / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(glyph, GLYPH_PX / 2f, baseline, paint)
    return bitmap.asImageBitmap()
}

/**
 * The burst layer over the key grid: composed only while particles live, its
 * frame loop exiting with them. Sits with the glide-trail canvas — above the
 * keys and the decals, below the popup windows.
 */
@Composable
internal fun BoxScope.KeyPressEffectsOverlay(field: ParticleField, glyphs: List<ImageBitmap>) {
    if (!field.active || glyphs.isEmpty()) return
    LaunchedEffect(field) {
        while (field.active) {
            withFrameMillis { field.frame(it) }
        }
    }
    Canvas(modifier = Modifier.matchParentSize()) {
        field.revision
        val now = field.nowMs
        for (i in 0 until ParticleField.CAPACITY) {
            val born = field.bornAt[i]
            if (born == 0L) continue
            val age = now - born
            if (age !in 0 until ParticleField.LIFETIME_MS) continue
            val t = age / 1000f
            val px = field.x[i] + field.vx[i] * t
            val py = field.y[i] + field.vy[i] * t + 0.5f * ParticleField.GRAVITY_PX_S2 * t * t
            val life = 1f - age / ParticleField.LIFETIME_MS.toFloat()
            val bitmap = glyphs[field.glyphIndex[i] % glyphs.size]
            val edge = (GLYPH_PX * field.sizePx[i] * density / 2.5f).roundToInt()
            rotate(
                degrees = field.spinDegPerS[i] * t,
                pivot = androidx.compose.ui.geometry.Offset(px, py),
            ) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(px.roundToInt() - edge / 2, py.roundToInt() - edge / 2),
                    dstSize = IntSize(edge, edge),
                    alpha = life.coerceIn(0f, 1f),
                )
            }
        }
    }
}
