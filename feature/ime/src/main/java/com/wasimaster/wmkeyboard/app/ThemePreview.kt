package com.wasimaster.wmkeyboard.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.blurredBy
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.theme.keyShapeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniature keyboard drawn from the spec: toolbar, two key rows, bottom row. */
@Composable
fun ThemePreview(theme: ThemeSpec, modifier: Modifier = Modifier) {
    val keyShape = keyShapeFor(theme.keyShape, ((theme.keyCornerRadiusDp ?: 8) / 3f + 1).toInt())
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        theme.backgroundImage?.let { path ->
            val bitmap by produceState<ImageBitmap?>(initialValue = null, path, theme.backgroundImageBlur) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        BitmapFactory.decodeFile(path)
                            ?.blurredBy(theme.backgroundImageBlur)
                            ?.asImageBitmap()
                    }.getOrNull()
                }
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(theme.backgroundImageOpacity),
                    contentScale = ContentScale.Crop,
                )
            }
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
