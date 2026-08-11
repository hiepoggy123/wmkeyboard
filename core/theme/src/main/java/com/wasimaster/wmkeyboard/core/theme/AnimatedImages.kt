package com.wasimaster.wmkeyboard.core.theme

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import java.io.File

/**
 * Whether a background image file plays as an animation.
 *
 * The theme editor asks so it can turn [ThemeSpec.backgroundAnimated] on for the
 * user: the switch is off on a new theme, and a GIF picked from the gallery
 * would otherwise sit there as a still frame with nothing on screen to say why.
 *
 * Only the two formats the keyboard can actually play count, which is what Coil
 * and [ImageDecoder] between them decode: GIF, and WebP with the animation flag
 * set in its VP8X chunk. Animated PNG is deliberately not here — Android has no
 * decoder for it, so an APNG background is a still whatever this said.
 *
 * The file is opened, not read into memory: a wallpaper straight off a camera
 * runs to tens of megabytes and this is called on the picker's result.
 */
fun isAnimatedImageFile(file: File): Boolean = when {
    !file.isFile -> false
    isGif(file) -> gifIsAnimated(file)
    else -> isAnimatedWebp(file)
}

/**
 * A GIF with more than one frame.
 *
 * [ImageDecoder] is the only thing on the platform that knows, and it arrived in
 * API 28; below that every GIF counts as animated, which costs a rare
 * single-frame GIF a switch the user can turn straight back off. A file that
 * fails to decode counts as animated too, for the same reason: the switch is
 * recoverable and a silently still background is not.
 */
private fun gifIsAnimated(file: File): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
    return runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, _, _ ->
            // Nothing here is drawn — the frame count is the whole question — so
            // the decode is asked for the smallest bitmap it will produce.
            decoder.setTargetSampleSize(MAX_SAMPLE)
        } is AnimatedImageDrawable
    }.getOrDefault(true)
}

/** The largest sample size [ImageDecoder] accepts, i.e. the cheapest decode. */
private const val MAX_SAMPLE = 32

/** How many bytes of header the two magic-number checks below need. */
private const val HEADER_BYTES = 21

private fun header(file: File): ByteArray? = runCatching {
    file.inputStream().use { input ->
        val buffer = ByteArray(HEADER_BYTES)
        var read = 0
        while (read < HEADER_BYTES) {
            val n = input.read(buffer, read, HEADER_BYTES - read)
            if (n < 0) break
            read += n
        }
        if (read < HEADER_BYTES) null else buffer
    }
}.getOrNull()

private fun isGif(file: File): Boolean {
    val b = header(file) ?: return false
    return b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()
}

/** RIFF/WEBP with a VP8X chunk whose animation flag (0x02) is set. */
private fun isAnimatedWebp(file: File): Boolean {
    val b = header(file) ?: return false
    val riff = b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
        b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte()
    val webp = b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
        b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()
    if (!riff || !webp) return false
    val vp8x = b[12] == 'V'.code.toByte() && b[13] == 'P'.code.toByte() &&
        b[14] == '8'.code.toByte() && b[15] == 'X'.code.toByte()
    return vp8x && (b[20].toInt() and 0x02) != 0
}
