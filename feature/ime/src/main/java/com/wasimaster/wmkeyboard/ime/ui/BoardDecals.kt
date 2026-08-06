package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wasimaster.wmkeyboard.core.theme.BackgroundBitmapCache
import com.wasimaster.wmkeyboard.core.theme.DecalSpec
import kotlin.math.max
import kotlin.math.roundToInt

/** One decal ready to draw: its bitmap beside its placement. */
@Immutable
class BoardDecal(val spec: DecalSpec, val bitmap: ImageBitmap)

/**
 * The theme's stickers over the key grid.
 *
 * A plain [Canvas] — no pointer input, so it is touch-transparent by
 * construction, and static, so it draws only when the board draws. Decodes go
 * through [BackgroundBitmapCache] like every other theme image; the composable
 * is a no-op while they land or when the theme has no decals (high contrast
 * empties the list before it gets here).
 */
@Composable
internal fun BoxScope.BoardDecalsOverlay(kb: KbTheme) {
    DecalsCanvas(kb.decals)
}

/**
 * The sharable half of [BoardDecalsOverlay]: draws [specs] over whatever box
 * it is composed in. The theme editor's preview draws the same list over its
 * miniature keyboard, so what the user drags is what the keyboard shows.
 */
@Composable
internal fun BoxScope.DecalsCanvas(specs: List<DecalSpec>) {
    if (specs.isEmpty()) return
    val decals by produceState(emptyList<BoardDecal>(), specs) {
        value = specs.mapNotNull { spec ->
            val path = spec.image ?: return@mapNotNull null
            BackgroundBitmapCache.load(path, 0f, DECAL_DECODE_PX, DECAL_DECODE_PX)
                ?.asImageBitmap()
                ?.let { BoardDecal(spec, it) }
        }
    }
    if (decals.isEmpty()) return
    Canvas(modifier = Modifier.matchParentSize()) {
        for (decal in decals) {
            val spec = decal.spec
            val bitmap = decal.bitmap
            val dstW = max(1f, size.width * spec.scale)
            val dstH = max(1f, dstW * bitmap.height / bitmap.width.toFloat())
            val center = Offset(size.width * spec.x, size.height * spec.y)
            rotate(degrees = spec.rotationDeg, pivot = center) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(
                        (center.x - dstW / 2f).roundToInt(),
                        (center.y - dstH / 2f).roundToInt(),
                    ),
                    dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                    alpha = spec.opacity.coerceIn(0f, 1f),
                )
            }
        }
    }
}

/** Decode edge for a sticker; they are imported downscaled to this anyway. */
internal const val DECAL_DECODE_PX = 512
