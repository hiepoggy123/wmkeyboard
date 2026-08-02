package com.wasimaster.wmkeyboard.core.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decoded background images, shared by the keyboard and the settings preview.
 *
 * Both used to decode the file at full size, with no sampling and no cache. A
 * photo straight off a phone camera or a photo service is commonly 6000x4000,
 * which is about 96 MB of ARGB_8888 — inside an input method, which the system
 * is already quick to kill, and repeated for every theme in the settings list.
 *
 * Sizes are bucketed rather than exact so a font-scale nudge, a one-dp width
 * change or a floating-keyboard resize drag does not invalidate everything.
 */
object BackgroundBitmapCache {

    /**
     * [stamp] is the file's modified time, so a rewritten image at the same
     * path is never served from an entry made before it was rewritten — which
     * is what happens every time the user crops.
     */
    data class Key(
        val path: String,
        val stamp: Long,
        val blurStep: Int,
        val targetW: Int,
        val targetH: Int,
    )

    private const val BUCKET_PX = 128
    private const val MIN_BUDGET_BYTES = 4 * 1024 * 1024
    private const val MAX_BUDGET_BYTES = 24 * 1024 * 1024

    private val cache: LruCache<Key, Bitmap> = run {
        val budget = (Runtime.getRuntime().maxMemory() / 16)
            .coerceIn(MIN_BUDGET_BYTES.toLong(), MAX_BUDGET_BYTES.toLong())
            .toInt()
        object : LruCache<Key, Bitmap>(budget) {
            override fun sizeOf(key: Key, value: Bitmap): Int = value.byteCount

            // Deliberately no recycle() here. An evicted bitmap may still be
            // held by a live ImageBitmap in composition, or by the outgoing
            // half of a theme crossfade, and drawing a recycled bitmap is a
            // hard crash rather than a blank frame. Letting the collector take
            // it is correct; this override exists to say so, because removing
            // it is exactly the "cleanup" somebody will try.
        }
    }

    /** Rounds a pixel size up to the nearest bucket. */
    fun bucket(px: Int): Int = max(BUCKET_PX, ((px + BUCKET_PX - 1) / BUCKET_PX) * BUCKET_PX)

    /**
     * The image at [path], sampled down to cover [targetW] x [targetH] and
     * blurred by [blur] on the editor's 0..25 scale. Null when the file is
     * missing or cannot be read, which is what a theme whose image was deleted
     * behind its back looks like.
     */
    suspend fun load(path: String, blur: Float, targetW: Int, targetH: Int): Bitmap? {
        val file = File(path)
        val stamp = file.lastModified()
        if (stamp == 0L) return null
        val key = Key(
            path = path,
            stamp = stamp,
            // Quantised, so dragging the blur slider reuses a decode instead of
            // starting a new one on every frame.
            blurStep = blur.roundToInt(),
            targetW = bucket(targetW),
            targetH = bucket(targetH),
        )
        cache.get(key)?.let { return it }
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decode(file, key) }.getOrNull()
        } ?: return null
        cache.put(key, decoded)
        return decoded
    }

    /** Drops entries for one path, for when its file is replaced or deleted. */
    fun invalidate(path: String) {
        cache.snapshot().keys.filter { it.path == path }.forEach { cache.remove(it) }
    }

    /** Frees memory under pressure. Called from the keyboard's trim callbacks. */
    fun trim(level: Int) {
        if (level >= TRIM_LEVEL_EVICT_ALL) evictAll() else cache.trimToSize(cache.size() / 2)
    }

    fun evictAll() = cache.evictAll()

    private fun decode(file: File, key: Key): Bitmap? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder samples in one pass and applies the EXIF rotation,
            // which BitmapFactory.decodeFile does not — so a portrait photo
            // from some phones used to draw on its side.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val sample = sampleSizeFor(info.size.width, info.size.height, key.targetW, key.targetH)
                decoder.setTargetSize(
                    max(1, info.size.width / sample),
                    max(1, info.size.height / sample),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, key.targetW, key.targetH)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } ?: return null
        // Below API 31 the blur is baked in, so it belongs to the cache key.
        // From 31 the draw applies it on the GPU and the sharp bitmap is cached
        // once for every radius.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bitmap
        } else {
            bitmap.blurredBy(key.blurStep.toFloat())
        }
    }

    /**
     * The largest power-of-two sample that still leaves the image covering the
     * target on both axes. Never smaller than the target: the background is
     * drawn with [ContentScale.Crop], and upscaling a sub-target bitmap is
     * visibly soft.
     */
    fun sampleSizeFor(width: Int, height: Int, targetW: Int, targetH: Int): Int {
        if (width <= 0 || height <= 0 || targetW <= 0 || targetH <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetW && height / (sample * 2) >= targetH) {
            sample *= 2
        }
        return sample
    }

    /** `TRIM_MEMORY_RUNNING_LOW`; anything at or above this empties the cache. */
    private const val TRIM_LEVEL_EVICT_ALL = 10
}

/**
 * Draws a theme's background image, sampled to the size it is being shown at.
 *
 * The one implementation behind both the keyboard's board and the settings
 * app's theme preview, which used to carry their own copies of this and drift.
 */
@Composable
fun BoxScope.ThemeBackgroundImage(
    path: String?,
    blur: Float,
    opacity: Float,
    targetW: Int,
    targetH: Int,
) {
    val resolved = path?.takeIf { it.isNotBlank() } ?: return
    val blurStep = blur.roundToInt()
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        resolved,
        blurStep,
        targetW,
        targetH,
    ) {
        value = BackgroundBitmapCache
            .load(resolved, blurStep.toFloat(), targetW, targetH)
            ?.asImageBitmap()
    }
    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = Modifier
            .matchParentSize()
            .alpha(opacity)
            .then(
                // From API 31 the blur is a draw-time GPU effect, so the cache
                // holds one sharp bitmap however many radii are in play and the
                // editor's blur slider stops re-decoding as it is dragged.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurStep > 0) {
                    val radiusPx = blurStep * BLUR_RADIUS_PX_PER_STEP
                    Modifier.graphicsLayer { renderEffect = BlurEffect(radiusPx, radiusPx) }
                } else {
                    Modifier
                }
            ),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Maps the editor's 0..25 blur scale onto a pixel radius for [BlurEffect].
 *
 * Approximate rather than exact: below API 31 the same scale drives
 * [blurredBy], which blurs by downscaling and upscaling again, and the two
 * cannot be made to match pixel for pixel. Worth a look on both sides of that
 * version boundary before changing it.
 */
private const val BLUR_RADIUS_PX_PER_STEP = 2f
