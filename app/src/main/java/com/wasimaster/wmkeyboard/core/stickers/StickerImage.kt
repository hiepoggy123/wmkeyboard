package com.wasimaster.wmkeyboard.core.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import com.wasimaster.wmkeyboard.core.media.MediaMime
import java.io.ByteArrayOutputStream

/**
 * Turns an arbitrary picked image into sticker bytes.
 *
 * Still images become 512×512 WebP under 100 KB — WhatsApp's static sticker
 * spec, and the only shape that reliably gets accepted as an
 * `image/webp.wasticker` rather than a plain image (see
 * [com.wasimaster.wmkeyboard.core.media.MediaMime]).
 *
 * Animated sources are stored byte for byte instead: Android ships no
 * animated-WebP encoder, so re-encoding one would flatten it to a single
 * frame. An animated WebP that already meets the spec therefore still goes out
 * as a sticker; an animated GIF can only ever be sent as an image.
 */
object StickerImage {

    /** Largest source we'll even read; anything bigger is refused outright. */
    const val MAX_SOURCE_BYTES = 12L * 1024 * 1024

    /** Cap for animated files, which are stored as-is. */
    const val MAX_ANIMATED_BYTES = 2 * 1024 * 1024

    /** WhatsApp's sticker canvas. */
    const val TARGET_SIZE = 512

    /** WhatsApp's static sticker budget. */
    const val TARGET_BYTES = 100 * 1024

    private val QUALITY_LADDER = intArrayOf(90, 80, 70, 60, 50)

    sealed interface Result {
        data class Ok(val sticker: ProcessedSticker) : Result

        /** Animated and over [MAX_ANIMATED_BYTES], or source over [MAX_SOURCE_BYTES]. */
        data object TooLarge : Result

        /** Not an image, or an image nothing on this device can decode. */
        data object NotAnImage : Result
    }

    fun process(bytes: ByteArray): Result {
        if (bytes.size > MAX_SOURCE_BYTES) return Result.TooLarge
        if (bytes.isEmpty()) return Result.NotAnImage

        // Already a conforming sticker (a re-imported pack, or a provider
        // sticker that was built to the same spec): re-encoding it would only
        // cost a generation of lossy quality.
        conforming(bytes)?.let { return Result.Ok(it) }

        val animation = detectAnimation(bytes)
        if (animation != null) {
            if (bytes.size > MAX_ANIMATED_BYTES) return Result.TooLarge
            val size = boundsOf(bytes)
            return Result.Ok(
                ProcessedSticker(
                    bytes = bytes,
                    mime = animation,
                    animated = true,
                    aspectRatio = size?.let { it.first.toFloat() / it.second } ?: 1f,
                )
            )
        }

        val source = decodeStill(bytes) ?: return Result.NotAnImage
        val canvas = square(source)
        if (canvas !== source) source.recycle()
        val encoded = encodeWebp(canvas)
        canvas.recycle()
        return if (encoded == null) Result.NotAnImage else Result.Ok(
            ProcessedSticker(bytes = encoded, mime = MediaMime.WEBP, animated = false, aspectRatio = 1f)
        )
    }

    /**
     * [bytes] as a sticker if they already meet the spec — a still 512×512
     * WebP inside the size budget — or null when they need re-encoding.
     */
    private fun conforming(bytes: ByteArray): ProcessedSticker? {
        if (bytes.size > TARGET_BYTES) return null
        if (!isWebp(bytes) || isAnimatedWebp(bytes)) return null
        val (width, height) = boundsOf(bytes) ?: return null
        if (width != TARGET_SIZE || height != TARGET_SIZE) return null
        return ProcessedSticker(bytes, MediaMime.WEBP, animated = false, aspectRatio = 1f)
    }

    /**
     * The MIME of an animated source, or null when the bytes are a still.
     *
     * WebP animation is a header flag, so it's exact. GIF frame counting isn't
     * worth the parser: below API 28 (no [ImageDecoder]) every GIF is assumed
     * animated, which only costs a rare single-frame GIF its sticker MIME.
     */
    private fun detectAnimation(bytes: ByteArray): String? {
        if (isGif(bytes)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return MediaMime.GIF
            val animated = runCatching {
                val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                ImageDecoder.decodeDrawable(source) is AnimatedImageDrawable
            }.getOrDefault(true)
            return if (animated) MediaMime.GIF else null
        }
        if (isAnimatedWebp(bytes)) return MediaMime.WEBP
        return null
    }

    private fun isGif(b: ByteArray): Boolean =
        b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    private fun isWebp(b: ByteArray): Boolean {
        if (b.size < 12) return false
        val riff = b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte()
        val webp = b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
            b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()
        return riff && webp
    }

    /** RIFF/WEBP with a VP8X chunk whose animation flag (0x02) is set. */
    private fun isAnimatedWebp(b: ByteArray): Boolean {
        if (b.size < 21 || !isWebp(b)) return false
        val vp8x = b[12] == 'V'.code.toByte() && b[13] == 'P'.code.toByte() &&
            b[14] == '8'.code.toByte() && b[15] == 'X'.code.toByte()
        return vp8x && (b[20].toInt() and 0x02) != 0
    }

    private fun boundsOf(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    /**
     * Decodes a still, downsampling huge sources so a 50-megapixel photo can't
     * blow the heap. [ImageDecoder] is preferred on API 28+ because it applies
     * EXIF rotation; [BitmapFactory] below it does not.
     */
    private fun decodeStill(bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > TARGET_SIZE) {
                        val scale = TARGET_SIZE.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }
        }
        val bounds = boundsOf(bytes) ?: return null
        var sample = 1
        while (maxOf(bounds.first, bounds.second) / sample > TARGET_SIZE * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * Fits [source] inside a transparent 512×512 canvas, preserving aspect
     * ratio. Small sources are scaled up so the sticker doesn't arrive as a
     * postage stamp floating in an empty frame.
     */
    private fun square(source: Bitmap): Bitmap {
        if (source.width == TARGET_SIZE && source.height == TARGET_SIZE) return source
        val scale = TARGET_SIZE.toFloat() / maxOf(source.width, source.height)
        val width = (source.width * scale).toInt().coerceIn(1, TARGET_SIZE)
        val height = (source.height * scale).toInt().coerceIn(1, TARGET_SIZE)
        val out = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
        val left = (TARGET_SIZE - width) / 2
        val top = (TARGET_SIZE - height) / 2
        Canvas(out).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(left, top, left + width, top + height),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    private fun encodeWebp(bitmap: Bitmap): ByteArray? {
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        var last: ByteArray? = null
        for (quality in QUALITY_LADDER) {
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(format, quality, out)) return last
            last = out.toByteArray()
            if (last.size <= TARGET_BYTES) return last
        }
        return last
    }
}
