package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.wasimaster.wmkeyboard.core.theme.ThemeBackgroundImage
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.theme.keyShapeFor

/**
 * Miniature keyboard drawn from the spec: toolbar, two key rows, bottom row.
 *
 * [imageOverride] shows a photo the theme has not been given yet, which is what
 * makes the picker's "how would this look" preview honest — the same
 * composable, the same key shapes and colours, a different image.
 * [landscape] previews the theme's separate landscape image where it has one.
 */
@Composable
fun ThemePreview(
    theme: ThemeSpec,
    modifier: Modifier = Modifier,
    imageOverride: String? = null,
    landscape: Boolean = false,
) {
    val keyShape = keyShapeFor(theme.keyShape, ((theme.keyCornerRadiusDp ?: 8) / 3f + 1).toInt())
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        val themeImage = if (landscape) {
            theme.backgroundImageLandscape ?: theme.backgroundImage
        } else {
            theme.backgroundImage
        }
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        // This preview is a strip a couple of hundred pixels tall. Decoding a
        // photo at full size to fill it, once per theme in a scrolling list,
        // was the single worst offender in the settings app.
        val targetW = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val targetH = with(density) { PREVIEW_HEIGHT_DP.dp.roundToPx() }
        ThemeBackgroundImage(
            path = imageOverride ?: themeImage,
            blur = theme.backgroundImageBlur,
            opacity = theme.backgroundImageOpacity,
            targetW = targetW,
            targetH = targetH,
        )
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
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(colorOf(theme.effectiveToolCircle()), CircleShape),
                    )
                }
            }
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(8) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .background(colorOf(theme.keyBackground), keyShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(colorOf(theme.keyText), CircleShape),
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(14.dp)
                        .background(colorOf(theme.modifierKeyBackground), keyShape),
                )
                Box(
                    modifier = Modifier
                        .weight(3.6f)
                        .height(14.dp)
                        .background(colorOf(theme.keyBackground), keyShape),
                )
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(14.dp)
                        .background(colorOf(theme.enterKeyBackground), keyShape),
                )
            }
        }
    }
}

private fun colorOf(argb: Long): Color = Color(argb.toInt())

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

// Mirrors the editor's fallback (ThemeScreens.effectiveToolCircle); kept
// private on both sides because it is presentation policy, not theme data.
private fun ThemeSpec.effectiveToolCircle(): Long =
    toolCircleBackground ?: colorOf(keyText).copy(alpha = 0.14f)
        .compositeOver(colorOf(boardBackground)).argb()

/** Height of the preview strip, and the height its photo is decoded for. */
private const val PREVIEW_HEIGHT_DP = 92
