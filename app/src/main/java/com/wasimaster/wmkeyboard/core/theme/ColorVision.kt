package com.wasimaster.wmkeyboard.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.wasimaster.wmkeyboard.core.settings.ColorVisionFilter

/**
 * Colour-blindness correction ("daltonization") for keyboard themes.
 *
 * This deliberately *corrects* rather than *simulates*: simulation shows a
 * trichromat what a dichromat sees, which is the wrong thing to render on a
 * real keyboard. Daltonization instead measures the colour information a
 * given dichromacy discards and pushes that error into channels the user can
 * still separate, so hues that would collapse into one another (a red enter
 * key against a green accent, for deuteranopia) stay distinguishable.
 *
 * The pipeline is the standard one: sRGB → LMS cone response, collapse the
 * missing cone, back to RGB, then redistribute the difference. Matrices are
 * the widely used Viénot/Brettel-derived set; they operate on gamma-encoded
 * sRGB directly, matching the reference implementations.
 */
object ColorVision {

    private val RGB_TO_LMS = floatArrayOf(
        17.8824f, 43.5161f, 4.11935f,
        3.45565f, 27.1554f, 3.86714f,
        0.0299566f, 0.184309f, 1.46709f,
    )

    private val LMS_TO_RGB = floatArrayOf(
        0.0809444479f, -0.130504409f, 0.116721066f,
        -0.0102485335f, 0.0540193266f, -0.113614708f,
        -0.000365296938f, -0.00412161469f, 0.693511405f,
    )

    /** Collapses the long-wave (L) cone onto the M/S response. */
    private val PROTAN = floatArrayOf(
        0f, 2.02344f, -2.52581f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    )

    /** Collapses the medium-wave (M) cone. */
    private val DEUTAN = floatArrayOf(
        1f, 0f, 0f,
        0.494207f, 0f, 1.24827f,
        0f, 0f, 1f,
    )

    /** Collapses the short-wave (S) cone. */
    private val TRITAN = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        -0.395913f, 0.801109f, 0f,
    )

    /**
     * Error redistribution. Red-green error is folded into green and blue,
     * which every dichromacy retains at least one of, so the lost separation
     * reappears as a brightness/blueness difference.
     */
    private val SHIFT = floatArrayOf(
        0f, 0f, 0f,
        0.7f, 1f, 0f,
        0.7f, 0f, 1f,
    )

    private fun apply(m: FloatArray, r: Float, g: Float, b: Float, out: FloatArray) {
        out[0] = m[0] * r + m[1] * g + m[2] * b
        out[1] = m[3] * r + m[4] * g + m[5] * b
        out[2] = m[6] * r + m[7] * g + m[8] * b
    }

    /**
     * [color] adjusted for [filter]. Alpha is preserved untouched — the theme
     * system leans on translucency for scrims and ghosted keys, and shifting
     * it would change layout-visible weight rather than hue.
     */
    fun correct(color: Color, filter: ColorVisionFilter): Color {
        if (filter == ColorVisionFilter.NONE) return color
        if (filter == ColorVisionFilter.GRAYSCALE) {
            // luminance() is the perceptual (linear, Rec.709-weighted) value,
            // so equal-luminance hues collapse to the same grey — which is
            // the point: it makes any remaining contrast failure obvious.
            val y = color.luminance().coerceIn(0f, 1f)
            return Color(red = y, green = y, blue = y, alpha = color.alpha)
        }
        val sim = when (filter) {
            ColorVisionFilter.PROTANOPIA -> PROTAN
            ColorVisionFilter.TRITANOPIA -> TRITAN
            else -> DEUTAN
        }
        val lms = FloatArray(3)
        val simLms = FloatArray(3)
        val simRgb = FloatArray(3)
        val shifted = FloatArray(3)

        val r = color.red * 255f
        val g = color.green * 255f
        val b = color.blue * 255f

        apply(RGB_TO_LMS, r, g, b, lms)
        apply(sim, lms[0], lms[1], lms[2], simLms)
        apply(LMS_TO_RGB, simLms[0], simLms[1], simLms[2], simRgb)
        // What this eye cannot see: the gap between the original and the
        // dichromat rendering.
        apply(SHIFT, r - simRgb[0], g - simRgb[1], b - simRgb[2], shifted)

        return Color(
            red = ((r + shifted[0]) / 255f).coerceIn(0f, 1f),
            green = ((g + shifted[1]) / 255f).coerceIn(0f, 1f),
            blue = ((b + shifted[2]) / 255f).coerceIn(0f, 1f),
            alpha = color.alpha,
        )
    }
}
