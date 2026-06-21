package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest

/**
 * The chip the suggestion strip shows when the text before the cursor is
 * something a tool can answer.
 *
 * Two shapes, one look. An answer chip fills the strip —
 * `[icon] 150 USD → 18,300.00 BDT   [⚙]` — because when you have just typed
 * an amount the conversion beats any word the dictionary could offer; the
 * trailing button hands the same numbers to the full tool. A keyword chip
 * ("wiki" → open Wikipedia) stays narrow and lets the word candidates keep
 * the rest of the strip, since the word being typed may well be a word.
 */
@Composable
internal fun SmartSuggestionChip(
    hit: SmartSuggest.SmartHit,
    reduceMotion: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onOpen: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    // Scale-in on appearance only. The key is the chip's kind, so the
    // animation plays when a chip arrives or changes character and not on
    // every keystroke that merely moves the number.
    val appear = remember(hit.kind) { Animatable(if (reduceMotion) 1f else 0.92f) }
    LaunchedEffect(hit.kind) {
        if (!reduceMotion) {
            appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val tint = kb.accent
    val fill = tint.copy(alpha = if (kb.dark) 0.20f else 0.11f)
    val keyword = hit.kind == SmartSuggest.Kind.TOOL

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = appear.value
            }
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .then(if (keyword) Modifier else Modifier.weight(1f))
                .fillMaxHeight()
                .clickable {
                    feedback()
                    onAccept()
                }
                .padding(start = 6.dp, end = if (keyword) 10.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            }
            if (keyword) {
                Text(
                    text = "Open ${toolLabel(hit.tool)}",
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = tint.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp),
                )
            } else {
                Text(
                    text = hit.query,
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text("→", color = kb.secondaryText, fontSize = 12.sp)
                if (hit.result != null) {
                    Text(
                        text = hit.result,
                        color = tint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    // Rates still in flight. The chip is already up so the
                    // strip does not jump when the number lands.
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                        color = tint,
                    )
                }
            }
        }
        if (!keyword) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 7.dp)
                    .background(tint.copy(alpha = 0.28f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable {
                        feedback()
                        onOpen()
                    }
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "Open ${toolLabel(hit.tool)}",
                    tint = tint.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
