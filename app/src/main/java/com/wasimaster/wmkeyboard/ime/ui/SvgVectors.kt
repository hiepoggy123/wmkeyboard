package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.icons.SvgDoc
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Turns a parsed [SvgDoc] into a Compose [ImageVector], sized to its own
 * viewport so a 24×24 icon lands on 24.dp.
 *
 * This is the Compose half of the SVG support; the parsing itself is in
 * `core/icons/SvgParser.kt` so it stays JVM-testable. Path data goes through
 * [PathParser], the same reader Compose uses for `<vector>` resources, so any
 * `d` attribute an Android vector drawable could hold works here.
 *
 * A monochrome document is built entirely in black — the trick
 * `KeyboardIcons.kt` already uses — so `Icon(tint = …)` recolours it and the
 * icon tracks the theme. A document that declared its own colours keeps them,
 * and is drawn without a tint by [SlotIcon].
 */
fun SvgDoc.toImageVector(name: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = viewportWidth.dp,
        defaultHeight = viewportHeight.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    for (path in paths) {
        val nodes = runCatching { PathParser().parsePathString(path.pathData).toNodes() }
            .getOrNull()
            ?: continue
        if (nodes.isEmpty()) continue

        // An untransformed path needs no group; wrapping every one of them
        // would double the node count of a typical icon for nothing.
        val transform = path.transform?.let(::decompose)
        if (transform != null) {
            builder.addGroup(
                name = "",
                rotate = transform.rotationDegrees,
                pivotX = 0f,
                pivotY = 0f,
                scaleX = transform.scaleX,
                scaleY = transform.scaleY,
                translationX = transform.translationX,
                translationY = transform.translationY,
            )
        }
        builder.addPath(
            pathData = nodes,
            pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            // A painted path with no colour of its own is drawn black so the
            // caller's tint takes it over; one with a colour keeps it.
            fill = if (path.filled) SolidColor(path.fillArgb.toColorOrBlack()) else null,
            fillAlpha = path.fillAlpha,
            stroke = if (path.stroked) SolidColor(path.strokeArgb.toColorOrBlack()) else null,
            strokeAlpha = path.strokeAlpha,
            strokeLineWidth = if (path.stroked) path.strokeWidth else 0f,
            strokeLineCap = path.strokeCap.toStrokeCap(),
            strokeLineJoin = path.strokeJoin.toStrokeJoin(),
            strokeLineMiter = path.strokeMiter,
        )
        if (transform != null) builder.clearGroup()
    }
    return builder.build()
}

private fun Long?.toColorOrBlack(): Color = if (this == null) Color.Black else Color(toInt())

private fun String.toStrokeCap(): StrokeCap = when (lowercase()) {
    "round" -> StrokeCap.Round
    "square" -> StrokeCap.Square
    else -> StrokeCap.Butt
}

private fun String.toStrokeJoin(): StrokeJoin = when (lowercase()) {
    "round" -> StrokeJoin.Round
    "bevel" -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

/**
 * What a Compose vector group can express: translate, then rotate, then scale.
 *
 * SVG allows a fully general affine, so a matrix carrying skew cannot be
 * reproduced exactly. Decomposing and dropping the skew term draws a slightly
 * wrong icon rather than none at all, which is the right trade for the one
 * transform icons essentially never use.
 */
private data class GroupTransform(
    val translationX: Float,
    val translationY: Float,
    val rotationDegrees: Float,
    val scaleX: Float,
    val scaleY: Float,
)

/** `[a, b, c, d, e, f]` → the closest translate/rotate/scale triple. */
private fun decompose(m: FloatArray): GroupTransform? {
    if (m.size != 6) return null
    val a = m[0]
    val b = m[1]
    val scaleX = hypot(a, b)
    // A zero x-scale collapses the path to a line; there is nothing sensible
    // to draw, and dividing by it below would produce NaN geometry.
    if (scaleX == 0f) return null
    val determinant = a * m[3] - b * m[2]
    return GroupTransform(
        translationX = m[4],
        translationY = m[5],
        rotationDegrees = Math.toDegrees(atan2(b, a).toDouble()).toFloat(),
        scaleX = scaleX,
        scaleY = determinant / scaleX,
    )
}
