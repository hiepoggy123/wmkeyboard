package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The key that fires the button this is drawn on: `Ctrl+2`, `Shift+F`, `Alt+1`.
 *
 * Accent text on a translucent accent pill. Deliberately *not* the look of
 * [focusRing], which is a 2 dp accent outline with a fill: the ring means "the
 * arrow keys are pointing here", and a badge means "this key gets you here". A
 * grid can show both at once, so they have to read differently.
 *
 * Drawn *under* its icon rather than over it. Over the glyph it was covering the
 * thing it was labelling, and the icon is what the user is scanning for.
 *
 * One rule for callers: it never changes a measured footprint. Arming the picker
 * must not resize the keyboard, because a bar that grows a row the moment you
 * double-tap Ctrl shoves the app's own text under your hands.
 *
 * Fitting a spelled-out label is handled two ways, because the parents differ.
 * A narrow but unclipped parent — a symbol chip, an emoji cell — gets an
 * unbounded measure, so the badge simply overhangs its cell. A parent that clips
 * its own contents, which every [ToolCircle] does, cannot be overhung, so
 * [hintBadgeFontSize] steps the text down instead.
 */
@Composable
internal fun HintBadge(label: String, modifier: Modifier = Modifier) {
    val kb = LocalKbTheme.current
    val size = hintBadgeFontSize(label)
    Text(
        label,
        modifier = modifier
            .wrapContentWidth(unbounded = true)
            .clip(RoundedCornerShape(4.dp))
            .background(kb.accent.copy(alpha = 0.22f))
            .padding(horizontal = 2.dp),
        color = kb.accent,
        fontSize = size,
        lineHeight = size * 1.1f,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/**
 * Shrinks the longer spellings so they still fit.
 *
 * A toolbar button is 38 dp wide and clips its own contents, so `Shift+Q` at the
 * size `⇧Q` wants would lose its tail — and a truncated key name is worse than a
 * small one, because it names a key that does not exist. Stepped by length
 * rather than measured: the labels are drawn from a fixed vocabulary of at most
 * eight characters, so three tiers cover all of it without a text measure per
 * badge per frame.
 */
private fun hintBadgeFontSize(label: String) = when {
    label.length <= 3 -> 10.sp
    label.length <= 5 -> 9.sp
    else -> 8.sp
}

/**
 * Room a badge needs under an icon: the text plus its own vertical padding.
 * Callers that centre an icon in a fixed box lift it by half of this so the
 * badge has somewhere to sit, which moves the glyph without resizing the box.
 */
internal val HintBadgeHeight = 12.dp

