package com.wasimaster.wmkeyboard.core.tools

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR-code rendering for the generator tool (ZXing core, fully offline).
 * The bitmap is drawn module-per-pixel then scaled by the consumer, so
 * generation stays cheap enough to run per keystroke.
 */
object QrCodeGen {

    /**
     * ZXing's payload cap for QR at the middle error-correction level.
     *
     * The shipped default. A longer payload does encode at a lower level, which
     * is the trade the `qrMaxChars` setting exists to let the user make, so
     * [bitmap] takes the live cap rather than reading this.
     */
    const val MAX_CHARS = 2000

    /**
     * [content] → QR bitmap of [sizePx]², or null when the content is blank
     * or too long to encode. [ecc] is L/M/Q/H (higher survives more damage
     * but packs less data).
     */
    fun bitmap(
        content: String,
        sizePx: Int,
        ecc: String = "M",
        foreground: Int = 0xFF000000.toInt(),
        background: Int = 0xFFFFFFFF.toInt(),
        maxChars: Int = MAX_CHARS,
    ): Bitmap? {
        if (content.isBlank() || content.length > maxChars) return null
        val level = runCatching { ErrorCorrectionLevel.valueOf(ecc) }.getOrDefault(ErrorCorrectionLevel.M)
        val matrix = runCatching {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                0, 0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to level,
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        }.getOrNull() ?: return null
        val moduleBitmap = createBitmap(matrix.width, matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                moduleBitmap[x, y] = if (matrix.get(x, y)) foreground else background
            }
        }
        // Nearest-neighbour scale keeps the modules crisp.
        return Bitmap.createScaledBitmap(moduleBitmap, sizePx, sizePx, false)
    }
}
