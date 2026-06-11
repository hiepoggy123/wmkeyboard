package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom key glyphs in the Material Symbols outlined style. The bundled
 * material-icons set has no proper shift glyph (KeyboardArrowUp reads as
 * a plain chevron), so the three shift states get purpose-drawn vectors:
 * outline when off, filled when armed, filled with an underbar for caps
 * lock — the same visual language Gboard uses.
 */
object KeyboardIcons {

    private const val VIEWPORT = 24f

    private fun shiftArrow(
        filled: Boolean,
        capsBar: Boolean,
        name: String,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply {
        val arrowBottom = if (capsBar) 14.4f else 16.6f
        path(
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3.6f)
            lineTo(4.6f, 11.2f)
            horizontalLineTo(8.7f)
            verticalLineTo(arrowBottom)
            horizontalLineTo(15.3f)
            verticalLineTo(11.2f)
            horizontalLineTo(19.4f)
            close()
        }
        if (capsBar) {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.7f, 18.2f)
                horizontalLineTo(15.3f)
                verticalLineTo(19.6f)
                horizontalLineTo(8.7f)
                close()
            }
        }
    }.build()

    val Shift: ImageVector by lazy { shiftArrow(filled = false, capsBar = false, name = "Shift") }
    val ShiftFilled: ImageVector by lazy { shiftArrow(filled = true, capsBar = false, name = "ShiftFilled") }
    val ShiftLock: ImageVector by lazy { shiftArrow(filled = true, capsBar = true, name = "ShiftLock") }

    /**
     * Incognito glyph (hat + glasses), for the toolbar indicator — the
     * material set has no incognito icon and the 🕶 emoji ignored theming.
     */
    val Incognito: ImageVector by lazy {
        ImageVector.Builder(
            name = "Incognito",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            // Hat crown.
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.1f, 9.4f)
                lineTo(9.1f, 4.6f)
                quadTo(9.3f, 3.8f, 10.1f, 3.8f)
                horizontalLineTo(13.9f)
                quadTo(14.7f, 3.8f, 14.9f, 4.6f)
                lineTo(15.9f, 9.4f)
                close()
            }
            // Brim.
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.6f, 10.4f)
                horizontalLineTo(20.4f)
                verticalLineTo(11.9f)
                horizontalLineTo(3.6f)
                close()
            }
            // Glasses: two lenses joined by a short bridge.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(10.6f, 16.9f)
                arcTo(2.55f, 2.55f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.59f, 16.85f)
                moveTo(18.7f, 16.9f)
                arcTo(2.55f, 2.55f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18.69f, 16.85f)
                moveTo(10.8f, 16.3f)
                quadTo(12f, 15.6f, 13.2f, 16.3f)
            }
        }.build()
    }
}
