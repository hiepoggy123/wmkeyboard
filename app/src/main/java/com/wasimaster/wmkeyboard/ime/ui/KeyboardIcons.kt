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
}
