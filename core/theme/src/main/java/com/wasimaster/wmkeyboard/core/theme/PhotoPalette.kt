package com.wasimaster.wmkeyboard.core.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A small grid of ARGB pixels sampled from a photo.
 *
 * Deliberately not a `Bitmap`: everything below is arithmetic, and taking
 * pixels rather than an Android object is what lets the colour picking and the
 * contrast rules be tested on the JVM, where there is no `Context` and no
 * Robolectric.
 */
data class PixelGrid(val pixels: IntArray, val width: Int, val height: Int) {

    init {
        require(width > 0 && height > 0) { "empty grid" }
        require(pixels.size >= width * height) { "grid smaller than its own size" }
    }

    // IntArray uses identity equality, so a data class holding one needs these
    // spelled out or two grids with the same pixels compare unequal.
    override fun equals(other: Any?): Boolean =
        other is PixelGrid && width == other.width && height == other.height &&
            pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * pixels.contentHashCode() + width) + height
}

/** How readable the keys are over a background, and why. */
data class Readability(
    /** WCAG contrast between the key text and what is behind it, 1..21. */
    val contrast: Float,
    /** How much the brightness varies where the keys sit, 0..1. */
    val variation: Float,
) {
    /** Below this the keys are genuinely hard to read, so the app acts. */
    val poor: Boolean get() = contrast < POOR_CONTRAST

    /** Readable, but close enough to be worth offering a fix. */
    val marginal: Boolean get() = !poor && contrast < GOOD_CONTRAST

    /** Enough contrast, but a detailed photo behind small labels. */
    val busy: Boolean get() = !poor && !marginal && variation > BUSY_VARIATION

    val good: Boolean get() = !poor && !marginal && !busy

    companion object {
        const val POOR_CONTRAST = 2.0f
        const val GOOD_CONTRAST = 3.0f
        const val BUSY_VARIATION = 0.22f

        /** What a fix aims for: WCAG AA for body text, since key labels are small. */
        const val TARGET_CONTRAST = 4.5f

        /** A scrim never goes past this, or the photo is simply gone. */
        const val MAX_SCRIM_ALPHA = 0.65f
    }
}

/**
 * A colour to rebuild a theme from, drawn from the photo.
 *
 * A coarse histogram over 5 bits per channel, with near-grey buckets set aside
 * unless the photo has nothing else — a picture of fog should not return six
 * indistinguishable greys and call them a palette. Weighted by saturation as
 * well as by count, so a small vivid subject beats a large flat sky.
 *
 * Fully deterministic: the same photo always gives the same colours.
 */
fun seedsFrom(grid: PixelGrid, max: Int = 6): List<Long> {
    val buckets = HashMap<Int, BucketAccumulator>()
    var counted = 0
    for (index in 0 until grid.width * grid.height) {
        val pixel = grid.pixels[index]
        if (((pixel ushr 24) and 0xFF) < MIN_ALPHA) continue
        counted++
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
        buckets.getOrPut(key) { BucketAccumulator() }.add(r, g, b)
    }
    if (counted == 0) return emptyList()

    val scored = buckets.values.map { it.summarise() }
    val colourful = scored.filter { it.saturation >= MIN_SATURATION }
    // Nothing colourful at all is a legitimate photo (fog, snow, a night sky),
    // so fall back to the plain histogram rather than returning nothing.
    val pool = colourful.ifEmpty { scored }
    val ranked = pool.sortedByDescending { it.weight(counted) }.take(max * OVERSAMPLE)
    return spreadOut(ranked).take(max).map { it.argb }
}

/**
 * Mean WCAG luminance of the band the keys sit in, 0..1.
 *
 * [top] skips the strip above the keys, which on most layouts is the
 * suggestion row: it is often the brightest part of a photo and the least
 * relevant to whether letters can be read.
 */
fun regionLuminance(grid: PixelGrid, top: Float = KEY_BAND_TOP): Float =
    bandStats(grid, top).mean

/** How much the brightness varies in that band, 0..1. */
fun regionVariation(grid: PixelGrid, top: Float = KEY_BAND_TOP): Float =
    bandStats(grid, top).deviation

/** WCAG relative luminance of one ARGB colour, 0..1. Alpha is ignored. */
fun luminanceOf(argb: Long): Float {
    val r = channel(((argb ushr 16) and 0xFF).toInt())
    val g = channel(((argb ushr 8) and 0xFF).toInt())
    val b = channel((argb and 0xFF).toInt())
    return (RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b).toFloat()
}

/** WCAG contrast ratio between two luminances, 1..21. */
fun contrastRatio(a: Float, b: Float): Float =
    (max(a, b) + CONTRAST_OFFSET) / (min(a, b) + CONTRAST_OFFSET)

/**
 * How readable [keyTextArgb] is over a photo of luminance [photoLuminance],
 * once the image opacity and the board scrim are composited over it.
 *
 * Blur damps the variation because blur is exactly the tool that fixes a busy
 * photo — a heavily blurred picture of a crowd is a smooth wash by the time it
 * is drawn.
 */
fun readabilityOver(
    photoLuminance: Float,
    photoVariation: Float,
    imageOpacity: Float,
    boardArgb: Long,
    keyBackgroundArgb: Long,
    keyTextArgb: Long,
    blur: Float,
): Readability {
    val behindKeys = effectiveLuminance(
        photoLuminance = photoLuminance,
        imageOpacity = imageOpacity,
        boardArgb = boardArgb,
        keyBackgroundArgb = keyBackgroundArgb,
    )
    val damped = photoVariation *
        (1f - boardArgb.alphaFraction()) *
        imageOpacity.coerceIn(0f, 1f) /
        (1f + blur / BLUR_DAMPING)
    return Readability(
        contrast = contrastRatio(behindKeys, luminanceOf(keyTextArgb)),
        variation = damped.coerceIn(0f, 1f),
    )
}

/**
 * How opaque the board has to be for the keys to clear
 * [Readability.TARGET_CONTRAST], or 0 when they already do.
 *
 * Solved by walking the alpha upward rather than in closed form: the key
 * background may itself be translucent, so the composite has two unknowns and
 * a search over 0..0.65 in small steps is both exact enough and obviously
 * correct. Capped, because the user asked for a photo.
 */
fun scrimAlphaFor(
    photoLuminance: Float,
    imageOpacity: Float,
    boardArgb: Long,
    keyBackgroundArgb: Long,
    keyTextArgb: Long,
    target: Float = Readability.TARGET_CONTRAST,
): Float {
    val textLuminance = luminanceOf(keyTextArgb)
    var alpha = boardArgb.alphaFraction()
    while (alpha <= Readability.MAX_SCRIM_ALPHA) {
        val luminance = effectiveLuminance(
            photoLuminance = photoLuminance,
            imageOpacity = imageOpacity,
            boardArgb = boardArgb.withAlphaFraction(alpha),
            keyBackgroundArgb = keyBackgroundArgb,
        )
        if (contrastRatio(luminance, textLuminance) >= target) {
            // Already fine as it stands: nothing to change.
            return if (alpha <= boardArgb.alphaFraction()) 0f else alpha
        }
        alpha += SCRIM_STEP
    }
    return Readability.MAX_SCRIM_ALPHA
}

// ---- internals --------------------------------------------------------

/** Photo, then the image opacity, then the board, then the key background. */
private fun effectiveLuminance(
    photoLuminance: Float,
    imageOpacity: Float,
    boardArgb: Long,
    keyBackgroundArgb: Long,
): Float {
    // The board colour shows through wherever the photo does not.
    val boardLuminance = luminanceOf(boardArgb)
    val opacity = imageOpacity.coerceIn(0f, 1f)
    val underBoard = photoLuminance * opacity + boardLuminance * (1f - opacity)
    val boardAlpha = boardArgb.alphaFraction()
    val behindKey = boardLuminance * boardAlpha + underBoard * (1f - boardAlpha)
    val keyAlpha = keyBackgroundArgb.alphaFraction()
    return luminanceOf(keyBackgroundArgb) * keyAlpha + behindKey * (1f - keyAlpha)
}

private class BandStats(val mean: Float, val deviation: Float)

private fun bandStats(grid: PixelGrid, top: Float): BandStats {
    val first = (grid.height * top.coerceIn(0f, 0.9f)).toInt()
    var sum = 0.0
    var squares = 0.0
    var count = 0
    for (y in first until grid.height) {
        for (x in 0 until grid.width) {
            val pixel = grid.pixels[y * grid.width + x]
            val luminance = luminanceOf(pixel.toLong() and 0xFFFFFFFFL).toDouble()
            sum += luminance
            squares += luminance * luminance
            count++
        }
    }
    if (count == 0) return BandStats(0f, 0f)
    val mean = sum / count
    val variance = (squares / count - mean * mean).coerceAtLeast(0.0)
    return BandStats(mean.toFloat(), sqrt(variance).toFloat())
}

private class BucketAccumulator {
    private var r = 0L
    private var g = 0L
    private var b = 0L
    private var n = 0

    fun add(red: Int, green: Int, blue: Int) {
        r += red
        g += green
        b += blue
        n++
    }

    fun summarise(): BucketSummary {
        val red = (r / n).toInt()
        val green = (g / n).toInt()
        val blue = (b / n).toInt()
        val high = maxOf(red, green, blue)
        val low = minOf(red, green, blue)
        val saturation = if (high == 0) 0f else (high - low).toFloat() / high
        return BucketSummary(
            argb = 0xFF000000L or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong(),
            red = red,
            green = green,
            blue = blue,
            count = n,
            saturation = saturation,
        )
    }
}

private class BucketSummary(
    val argb: Long,
    val red: Int,
    val green: Int,
    val blue: Int,
    val count: Int,
    val saturation: Float,
) {
    /** Share of the photo, lifted by how colourful the bucket is. */
    fun weight(total: Int): Float =
        (count.toFloat() / total) * (SATURATION_FLOOR + saturation)
}

/**
 * Keeps only buckets that look different from the ones already chosen, so six
 * swatches are six colours rather than six shades of the same one.
 */
private fun spreadOut(candidates: List<BucketSummary>): List<BucketSummary> {
    val chosen = ArrayList<BucketSummary>()
    for (candidate in candidates) {
        val tooClose = chosen.any { picked ->
            abs(picked.red - candidate.red) +
                abs(picked.green - candidate.green) +
                abs(picked.blue - candidate.blue) < MIN_SEPARATION
        }
        if (!tooClose) chosen += candidate
    }
    return chosen
}

private fun channel(value: Int): Double {
    val c = value / 255.0
    return if (c <= SRGB_KNEE) c / SRGB_LINEAR_DIVISOR else ((c + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
}

/** Keys occupy roughly the bottom four fifths of the board. */
private const val KEY_BAND_TOP = 0.2f

private const val MIN_ALPHA = 128
private const val MIN_SATURATION = 0.15f
private const val SATURATION_FLOOR = 0.2f
private const val MIN_SEPARATION = 90
private const val OVERSAMPLE = 6
private const val BLUR_DAMPING = 4f
private const val SCRIM_STEP = 0.02f
private const val CONTRAST_OFFSET = 0.05f
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val SRGB_KNEE = 0.03928
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
