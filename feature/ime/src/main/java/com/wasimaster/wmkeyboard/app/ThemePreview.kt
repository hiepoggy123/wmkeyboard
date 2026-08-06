package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.wasimaster.wmkeyboard.core.theme.BackgroundBitmapCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.alpha
import coil3.compose.AsyncImage
import com.wasimaster.wmkeyboard.ime.ui.rememberMediaImageLoader
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.core.theme.KeyShapeKind
import com.wasimaster.wmkeyboard.core.theme.ThemeAnimation
import com.wasimaster.wmkeyboard.core.theme.ThemeBackgroundImage
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.theme.keyShapeFor
import com.wasimaster.wmkeyboard.core.theme.keyShapeKindOrNull

/**
 * Miniature keyboard drawn from the spec: toolbar, two key rows, bottom row.
 *
 * [imageOverride] shows a photo the theme has not been given yet, which is what
 * makes the picker's "how would this look" preview honest — the same
 * composable, the same key shapes and colours, a different image.
 * [landscape] previews the theme's separate landscape image where it has one.
 *
 * A theme that moves gets a badge in the corner. The preview itself is drawn at
 * phase 0 and never animates — a gallery of twenty live gradients is a gallery
 * that never stops recomposing — so without the badge there is nothing at all
 * to tell an animated theme from a still one until it is applied.
 */
@Composable
fun ThemePreview(
    theme: ThemeSpec,
    modifier: Modifier = Modifier,
    imageOverride: Any? = null,
    landscape: Boolean = false,
    animatedBadge: Boolean = true,
) {
    // Half the 3 dp the rows space their keys by, so a leaning shape spills
    // into the gap here exactly as it does on the keyboard itself.
    val keyShape = keyShapeFor(
        theme.keyShape,
        ((theme.keyCornerRadiusDp ?: 8) / 3f + 1).toInt(),
        bleedDp = 1.5f,
    )
    // The theme's own tool shape where it has one; a theme that follows the
    // global setting is drawn with the shipped default, since the preview is
    // handed a spec and never sees the settings. The mock-up's tools are a
    // tenth the size of the real ones, so the radius rounds them fully either
    // way and the row still reads as circles unless the theme says otherwise.
    val toolShape = keyShapeFor(
        keyShapeKindOrNull(theme.toolShape) ?: KeyShapeKind.ROUNDED,
        theme.toolCircleRadiusDp ?: 20,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Shaped like a keyboard rather than like a letterbox. The old
            // fixed height made every preview a wide, flat strip that told the
            // user very little about how the real thing would look.
            .aspectRatio(if (landscape) PREVIEW_ASPECT_LANDSCAPE else PREVIEW_ASPECT_PORTRAIT)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        val themeImage = if (landscape) {
            theme.backgroundImageLandscape ?: theme.backgroundImage
        } else {
            theme.backgroundImage
        }
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        // Decoding a photo at full size to fill a strip this small, once per
        // theme in a scrolling list, was the worst offender in the settings app.
        val targetW = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val targetH = (targetW / PREVIEW_ASPECT_PORTRAIT).toInt()
        val remote = imageOverride as? String
        if (remote != null && remote.startsWith("http")) {
            // A photo being considered has not been downloaded yet, so it is
            // shown straight from the service. Without this the preview drew
            // whatever the theme already had, which answered the wrong question.
            AsyncImage(
                model = remote,
                contentDescription = null,
                imageLoader = rememberMediaImageLoader(),
                modifier = Modifier
                    .matchParentSize()
                    .alpha(theme.backgroundImageOpacity),
                contentScale = ContentScale.Crop,
            )
        } else {
            ThemeBackgroundImage(
                path = remote ?: themeImage,
                blur = theme.backgroundImageBlur,
                opacity = theme.backgroundImageOpacity,
                targetW = targetW,
                targetH = targetH,
            )
        }
        // Static (phase 0) gradient — the preview doesn't animate.
        val boardGradient = theme.boardGradient
        if (boardGradient != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(boardGradient.brush()),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colorOf(theme.boardBackground)),
            )
        }
        // Weighted rather than fixed heights, so the mock-up keeps its
        // proportions at whatever size it is drawn.
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(TOOLBAR_WEIGHT)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(TOOLBAR_CIRCLES) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.6f)
                            .aspectRatio(1f)
                            .background(colorOf(theme.effectiveToolCircle()), toolShape),
                    )
                }
            }
            // A real QWERTY narrows as it goes down: ten letters, then nine
            // inset by half a key, then seven between shift and backspace.
            // Drawing three identical rows of ten made the preview read as a
            // grid rather than as a keyboard.
            PreviewKeyRow(theme, keyShape) {
                repeat(10) { LetterKey(theme, keyShape) }
            }
            PreviewKeyRow(theme, keyShape) {
                Spacer(Modifier.weight(0.5f))
                repeat(9) { LetterKey(theme, keyShape) }
                Spacer(Modifier.weight(0.5f))
            }
            PreviewKeyRow(theme, keyShape) {
                ModifierKey(theme, keyShape, weight = 1.5f)
                repeat(7) { LetterKey(theme, keyShape) }
                ModifierKey(theme, keyShape, weight = 1.5f)
            }
            PreviewKeyRow(theme, keyShape) {
                ModifierKey(theme, keyShape, weight = 1.5f)
                LetterKey(theme, keyShape, weight = 1f, dot = false)
                Box(
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxHeight()
                        .background(colorOf(theme.keyBackground), keyShape),
                )
                LetterKey(theme, keyShape, weight = 1f, dot = false)
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .background(colorOf(theme.enterKeyBackground), keyShape),
                )
            }
        }
        if (animatedBadge && theme.animation != ThemeAnimation.NONE) {
            AnimatedThemeBadge(
                theme = theme,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
    }
}

/**
 * The corner badge on an animated theme's preview.
 *
 * Drawn in the theme's own key colours rather than the app's, because it sits
 * on top of the mock-up: an accent from the settings palette would read as part
 * of the surrounding card and disappear over a light theme's board.
 */
@Composable
private fun AnimatedThemeBadge(theme: ThemeSpec, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colorOf(theme.keyBackground).copy(alpha = 0.92f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Animation,
            contentDescription = stringResource(R.string.ime_theme_animated_desc),
            modifier = Modifier.size(11.dp),
            tint = colorOf(theme.keyText),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.ime_theme_animated_badge),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            color = colorOf(theme.keyText),
        )
    }
}

/** One row of the mock-up keyboard. Every row is the same height. */
@Composable
private fun ColumnScope.PreviewKeyRow(
    theme: ThemeSpec,
    keyShape: Shape,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .weight(ROW_WEIGHT)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

/** A letter key, with a dot standing in for its label. */
@Composable
private fun RowScope.LetterKey(
    theme: ThemeSpec,
    keyShape: Shape,
    weight: Float = 1f,
    dot: Boolean = true,
) {
    // The letter-key texture, drawn the way the real board draws it: over the
    // key colour, clipped to the key shape. One shared 128 px decode serves
    // every key of the mock-up through the bitmap cache.
    val texture by produceState<ImageBitmap?>(null, theme.keyTexture) {
        value = theme.keyTexture?.let {
            BackgroundBitmapCache.load(it, 0f, PREVIEW_TEXTURE_PX, PREVIEW_TEXTURE_PX)
                ?.asImageBitmap()
        }
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(colorOf(theme.keyBackground), keyShape),
        contentAlignment = Alignment.Center,
    ) {
        texture?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(keyShape)
                    .alpha(theme.keyTextureOpacity),
            )
        }
        if (dot) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.2f)
                    .aspectRatio(1f)
                    .background(colorOf(theme.keyText), CircleShape),
            )
        }
    }
}

/** Decode edge for the mock-up's key texture; its keys are a few dp across. */
private const val PREVIEW_TEXTURE_PX = 128

/** Shift, backspace and the symbol key, which carry the modifier colour. */
@Composable
private fun RowScope.ModifierKey(theme: ThemeSpec, keyShape: Shape, weight: Float) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(colorOf(theme.modifierKeyBackground), keyShape),
    )
}

private fun colorOf(argb: Long): Color = Color(argb.toInt())

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

// Mirrors the editor's fallback (ThemeScreens.effectiveToolCircle); kept
// private on both sides because it is presentation policy, not theme data.
private fun ThemeSpec.effectiveToolCircle(): Long =
    toolCircleBackground ?: colorOf(keyText).copy(alpha = 0.14f)
        .compositeOver(colorOf(boardBackground)).argb()

/**
 * Width to height of the preview. A real keyboard is about 360 dp across and
 * 220 dp tall with its toolbar, which is roughly 1.65:1; sideways it is much
 * wider for the same number of rows.
 */
private const val PREVIEW_ASPECT_PORTRAIT = 1.7f
private const val PREVIEW_ASPECT_LANDSCAPE = 3.2f

/** How the mock-up divides its height between the toolbar and the key rows. */
private const val TOOLBAR_WEIGHT = 0.75f
private const val ROW_WEIGHT = 1f

/** Tools shown in the mock-up's toolbar row. */
private const val TOOLBAR_CIRCLES = 5
