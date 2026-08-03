package com.wasimaster.wmkeyboard.core.stickers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/** A sticker's border: how wide, and in which of the two colours. */
data class OutlineSpec(val widthPx: Float = 0f, val color: Int = android.graphics.Color.WHITE) {
    val visible: Boolean get() = widthPx > MIN_VISIBLE_PX

    companion object {
        /** Below half a pixel there is nothing to draw. */
        const val MIN_VISIBLE_PX = 0.5f

        /** As wide as the slider goes, at the 512 canvas. */
        const val MAX_WIDTH_PX = 24f
    }
}

/**
 * Draws the white-or-black border that hugs a cut-out subject.
 *
 * The border is the subject's own alpha, stamped in a ring of offsets around
 * where the subject will sit, with the subject drawn on top. Two alternatives
 * were rejected: blurring the alpha and thresholding it dilates by an amount
 * that depends on how soft the subject's edge already is, so the same slider
 * value would give different borders on a hard cutout and a feathered one;
 * and tracing a contour to stroke it loses the antialiasing that makes a
 * cutout look like a cutout.
 *
 * One consequence worth knowing: the border layer is solid and sits *behind*
 * the subject, so a part-transparent region inside the subject takes the
 * border colour. That is right for a sticker outline and is exactly what a
 * stroked contour would not do.
 */
object StickerOutline {

    /** Farthest apart two stamps may sit along the ring, in pixels. */
    private const val STEP_PX = 1.5f

    private const val MIN_STAMPS = 12

    /** Enough for [OutlineSpec.MAX_WIDTH_PX] to still meet [STEP_PX]. */
    private const val MAX_STAMPS = 104

    /**
     * How many stamps a border of [radiusPx] needs. Public for its test: it
     * is the whole cost model, since each stamp is one full-canvas blit.
     */
    fun stampCount(radiusPx: Float): Int {
        val needed = Math.ceil((2.0 * Math.PI * radiusPx / STEP_PX)).toInt()
        return needed.coerceIn(MIN_STAMPS, MAX_STAMPS)
    }

    /** Draws [subject] into [canvas] with its border under it. */
    fun draw(canvas: Canvas, subject: Bitmap, spec: OutlineSpec) {
        stamp(canvas, subject, spec)
        canvas.drawBitmap(subject, 0f, 0f, null)
    }

    /**
     * Just the border, as its own bitmap, or null when there is none.
     *
     * The editor keeps this separate from the subject so a brush stroke can
     * redraw the cheap layer without paying for the stamps every frame.
     */
    fun renderLayer(subject: Bitmap, spec: OutlineSpec): Bitmap? {
        if (!spec.visible) return null
        val layer = Bitmap.createBitmap(subject.width, subject.height, Bitmap.Config.ARGB_8888)
        stamp(Canvas(layer), subject, spec)
        return layer
    }

    private fun stamp(canvas: Canvas, subject: Bitmap, spec: OutlineSpec) {
        if (!spec.visible) return
        val alpha = subject.extractAlpha()
        val paint = Paint().apply {
            color = spec.color
            isFilterBitmap = false
        }
        val stamps = stampCount(spec.widthPx)
        for (index in 0 until stamps) {
            val angle = 2.0 * Math.PI * index / stamps
            canvas.drawBitmap(
                alpha,
                (cos(angle) * spec.widthPx).toFloat(),
                (sin(angle) * spec.widthPx).toFloat(),
                paint,
            )
        }
        alpha.recycle()
    }

    /** [subject] with its border, as a new bitmap of the same size. */
    fun render(subject: Bitmap, spec: OutlineSpec): Bitmap {
        val out = Bitmap.createBitmap(subject.width, subject.height, Bitmap.Config.ARGB_8888)
        draw(Canvas(out), subject, spec)
        return out
    }
}
