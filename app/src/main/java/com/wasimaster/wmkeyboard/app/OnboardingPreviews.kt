package com.wasimaster.wmkeyboard.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec

// Miniature keyboards and feature scenes for the wizard. Every one of them
// answers a question the words on the page cannot: which of three Bengali
// input systems this is, what "emoji key instead of 🌐" does to the bottom
// row, what a smart chip actually looks like when it appears. They are drawn
// rather than shipped as images, so they follow the theme and cost nothing in
// the APK.

/** Height of one key in every miniature here. */
private val MiniKeyHeight: Dp = 22.dp

/** Gap between miniature keys, horizontally and between rows. */
private val MiniKeyGap: Dp = 3.dp

/** Size of a key's letter. Set against a matching line height — see [MiniKeyLabel]. */
private val MiniKeyFontSize: TextUnit = 9.sp

/** How many rows of a real layout the layout-picker miniature draws. */
private const val MINI_LAYOUT_ROWS = 4

/**
 * A miniature of a layout's own letter grid, key for key.
 *
 * Reads the [LayoutLayer.LETTERS] rows straight off the spec, so a phonetic
 * layout and a native one look as different here as they do on the keyboard —
 * which is the entire point of showing it while someone chooses between them.
 */
@Composable
internal fun MiniLayoutPreview(spec: LayoutSpec, modifier: Modifier = Modifier) {
    val rows = spec.layers[LayoutLayer.LETTERS.key]?.rows.orEmpty().take(MINI_LAYOUT_ROWS)
    if (rows.isEmpty()) return
    MiniBoard(modifier) {
        for (row in rows) {
            MiniRow {
                for (key in row) {
                    MiniKey(miniKeyLabel(key), Modifier.weight(key.width.coerceIn(0.5f, 5f)))
                }
            }
        }
    }
}

/**
 * What a key shows in a miniature. Only text keys carry their label: an
 * icon-drawn key would show a name like "backspace" at 6sp, and the shapes of
 * the modifier keys read fine as blanks.
 */
private fun miniKeyLabel(key: Key): String = when (key.action) {
    KeyAction.Text -> key.label.take(2)
    else -> ""
}

/** Which part of [MiniKeyboardPreview] the setting under it is about. */
internal enum class MiniKeyHighlight { NONE, GLOBE, NUMBER_ROW }

private val MiniQwertyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/**
 * A miniature English board, with the two things the gestures page can change
 * — the digit row and the key left of the spacebar — drawn as they are set.
 *
 * Deliberately not the user's own layout: this preview is about the *shape* of
 * the board, and a phonetic Bengali grid would make the reader work out which
 * of the differences is the one the switch controls.
 */
@Composable
internal fun MiniKeyboardPreview(
    numberRow: Boolean,
    globeAsEmoji: Boolean,
    highlight: MiniKeyHighlight = MiniKeyHighlight.NONE,
    modifier: Modifier = Modifier,
) {
    MiniBoard(modifier) {
        if (numberRow) {
            MiniRow(highlighted = highlight == MiniKeyHighlight.NUMBER_ROW) {
                for (digit in "1234567890") {
                    MiniKey(digit.toString(), Modifier.weight(1f))
                }
            }
        }
        for ((index, row) in MiniQwertyRows.withIndex()) {
            MiniRow {
                // The bottom letter row keeps shift and backspace, so the
                // miniature is recognisable as a keyboard rather than as three
                // rows of letters.
                if (index == 2) MiniKey("", Modifier.weight(1.5f))
                for (letter in row) {
                    MiniKey(letter.toString(), Modifier.weight(1f))
                }
                if (index == 2) MiniKey("", Modifier.weight(1.5f))
            }
        }
        MiniRow {
            MiniKey("", Modifier.weight(1.5f))
            MiniKeyIcon(
                icon = if (globeAsEmoji) Icons.Outlined.Mood else Icons.Outlined.Language,
                highlighted = highlight == MiniKeyHighlight.GLOBE,
                modifier = Modifier.weight(1f),
            )
            MiniKey("", Modifier.weight(4f))
            MiniKey("", Modifier.weight(1f))
            MiniKey("", Modifier.weight(1.5f))
        }
    }
}

/** The board these miniatures sit on: one rounded, tinted panel. */
@Composable
private fun MiniBoard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MiniKeyGap),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(6.dp),
    ) { content() }
}

@Composable
private fun MiniRow(
    highlighted: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MiniKeyGap),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

@Composable
private fun MiniKey(label: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(MiniKeyHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (label.isNotEmpty()) MiniKeyLabel(label)
    }
}

/**
 * A key's letter, sitting in the middle of the key rather than near its floor.
 *
 * Centring the `Text` is not enough on its own: what gets centred is the line
 * box, and a line box carries the font's ascent and descent whether or not the
 * glyph uses them. At 9sp that leftover space is a visible drop. Trimming the
 * line box to the glyph and dropping the platform's extra font padding is what
 * actually puts an "a" in the centre of the key.
 */
@Composable
private fun MiniKeyLabel(label: String) {
    Text(
        label,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        color = MaterialTheme.colorScheme.onSurface,
        style = LocalTextStyle.current.copy(
            fontSize = MiniKeyFontSize,
            lineHeight = MiniKeyFontSize,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}

@Composable
private fun MiniKeyIcon(icon: ImageVector, highlighted: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(MiniKeyHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (highlighted) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ---- discover-page illustrations ----

/** Height every discover card's illustration is drawn at. */
internal val DiscoverPreviewHeight: Dp = 62.dp

/**
 * The picture on a discover card: a tiny staged scene of the feature doing its
 * thing — an answer chip appearing over the keys, a code arriving, a waveform
 * moving under a dictation.
 *
 * Drawn from the same primitives as the keyboard miniatures rather than
 * shipped as GIFs. A dozen animated images would be a megabyte of APK that
 * cannot follow the theme, and a still screenshot of a keyboard at 62 dp is
 * unreadable — what carries at this size is one moving element and one word.
 */
@Composable
internal fun DiscoverPreview(
    id: String,
    accent: Color,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(DiscoverPreviewHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f)),
    ) {
        when (id) {
            "chips" -> ChipScene("12*8=", "96", accent, animate)
            "hotwords" -> ChipScene("translate", "⇱", accent, animate)
            "otp" -> ChipScene("code", "482 913", accent, animate)
            "toolbox" -> GridScene(accent)
            "clipboard", "snippets" -> StackScene(accent, animate)
            "photos", "modes" -> WallpaperScene(accent)
            "whisper" -> WaveScene(accent, animate)
            "fancy" -> FancyScene(accent, animate)
            "ai" -> AiScene(accent, animate)
            else -> ToggleScene(accent, animate)
        }
    }
}

/** Typed text on the left, the chip the keyboard offers for it on the right. */
@Composable
private fun ChipScene(typed: String, chip: String, accent: Color, animate: Boolean) {
    val pulse = pulseAlpha(animate)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(typed, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .alpha(pulse)
                .clip(RoundedCornerShape(8.dp))
                .background(accent)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(chip, fontSize = 11.sp, color = Color.White, maxLines = 1)
        }
    }
}

/** The toolbox: a grid of tool circles. */
@Composable
private fun GridScene(accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(5) { column ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.35f + column * 0.1f)),
                    )
                }
            }
        }
    }
}

/** A stack of saved things: clipboard entries, snippet cards. */
@Composable
private fun StackScene(accent: Color, animate: Boolean) {
    val pulse = pulseAlpha(animate)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { row ->
            Box(
                modifier = Modifier
                    .alpha(if (row == 0) pulse else 1f)
                    .width((72 - row * 12).dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.55f - row * 0.12f)),
            )
        }
    }
}

/** Keys over a picture: the photo backgrounds, and the per-app boards. */
@Composable
private fun WallpaperScene(accent: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(96.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.45f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
        }
    }
}

/** Dictation: bars that rise and fall like a level meter. */
@Composable
private fun WaveScene(accent: Color, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "wavePhase",
    )
    val moving = if (animate) phase else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(34.dp),
    ) {
        // Each bar is a fixed fraction of one animated phase, so they breathe
        // together rather than marching — one timer, seven heights.
        listOf(0.35f, 0.7f, 1f, 0.55f, 0.9f, 0.45f, 0.25f).forEach { scale ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight((0.25f + moving * scale * 0.75f).coerceAtMost(1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
        }
    }
}

/** Styled text: the fancy-text strip, and the AI rewrite. */
@Composable
private fun FancyScene(accent: Color, animate: Boolean) {
    val pulse = pulseAlpha(animate)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Aa", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("→", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .alpha(pulse)
                .clip(RoundedCornerShape(8.dp))
                .background(accent)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("𝓐𝓪", fontSize = 15.sp, color = Color.White)
        }
    }
}

/**
 * The AI tools: a rough line of text being rewritten into a clean one, with
 * the wand doing it.
 *
 * Its own scene rather than the fancy-text one in another colour — the two
 * cards sit in the same grid, and two identical pictures say the two features
 * are the same feature.
 */
@Composable
private fun AiScene(accent: Color, animate: Boolean) {
    val pulse = pulseAlpha(animate)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Before: a short, ragged draft.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextLine(width = 46.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, alpha = 0.35f)
            TextLine(width = 30.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, alpha = 0.35f)
        }
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(18.dp)
                .alpha(pulse),
        )
        // After: longer, even, and in the tool's own colour.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextLine(width = 52.dp, color = accent, alpha = pulse)
            TextLine(width = 44.dp, color = accent, alpha = pulse)
        }
    }
}

/** One bar standing in for a line of text. */
@Composable
private fun TextLine(width: Dp, color: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .alpha(alpha)
            .width(width)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/** Two switches on the bar: incognito and autocorrect. */
@Composable
private fun ToggleScene(accent: Color, animate: Boolean) {
    val pulse = pulseAlpha(animate)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) { index ->
            val on = index == 0
            Box(
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
                modifier = Modifier
                    .alpha(if (on) pulse else 1f)
                    .size(width = 34.dp, height = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (on) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

/**
 * The one animation every scene shares: a slow breath on whatever the feature
 * actually produces. Held at full opacity when the user has asked for less
 * motion, so the scene still reads as a picture rather than disappearing.
 */
@Composable
private fun pulseAlpha(animate: Boolean): Float {
    if (!animate) return 1f
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return alpha
}
