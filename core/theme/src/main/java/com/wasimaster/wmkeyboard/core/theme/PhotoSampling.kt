package com.wasimaster.wmkeyboard.core.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Android half of [PhotoPalette]: turns an image into the plain pixel grid
 * the colour and contrast rules work on.
 *
 * Kept in its own file so the arithmetic next door stays importable from a unit
 * test without dragging Android in.
 */
object PhotoSampling {

    /** Long edge of the sampled grid. Small on purpose; the maths is statistical. */
    private const val SAMPLE_MAX_SIDE = 96

    /** Reads [file] down to a grid, or null when it cannot be read. */
    fun gridOf(file: File, maxSide: Int = SAMPLE_MAX_SIDE): PixelGrid? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = BackgroundBitmapCache.sampleSizeFor(
                width = bounds.outWidth,
                height = bounds.outHeight,
                targetW = maxSide,
                targetH = maxSide,
            )
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return try {
            gridOf(bitmap, maxSide)
        } finally {
            bitmap.recycle()
        }
    }

    /** Same, for a bitmap already in hand. Does not recycle [bitmap]. */
    fun gridOf(bitmap: Bitmap, maxSide: Int = SAMPLE_MAX_SIDE): PixelGrid? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val scale = maxSide.toFloat() / max(bitmap.width, bitmap.height)
        val width = if (scale < 1f) max(1, (bitmap.width * scale).roundToInt()) else bitmap.width
        val height = if (scale < 1f) max(1, (bitmap.height * scale).roundToInt()) else bitmap.height
        val scaled = if (width == bitmap.width && height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        return try {
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            PixelGrid(pixels, width, height)
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}
