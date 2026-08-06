package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The key that fires the button this is drawn on: `2`, `⌃2`, `⇧F`, `⌥1`.
 *
 * Accent text on a translucent accent pill. Deliberately *not* the look of
 * [focusRing], which is a 2 dp accent outline with a fill: the ring means "the
 * arrow keys are pointing here", and a badge means "this key gets you here". A
 * grid can show both at once, so they have to read differently.
 *
 * Callers overlay this inside a button's existing footprint rather than adding
 * it to a layout. Arming the picker must never change the keyboard's height —
 * a bar that grows a row the moment you double-tap Ctrl pushes the app's own
 * text under your hands.
 */
@Composable
internal fun HintBadge(label: String, modifier: Modifier = Modifier) {
    val kb = LocalKbTheme.current
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(kb.accent.copy(alpha = 0.22f))
            .padding(horizontal = 3.dp),
        color = kb.accent,
        fontSize = HintBadgeFontSize,
        lineHeight = HintBadgeLineHeight,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/**
 * Small enough to sit under a 20 dp icon inside a 38 dp button without touching
 * either edge, and still legible: these are one or two characters, which take
 * far less reading than a word would at the same size.
 */
private val HintBadgeFontSize = 8.sp

private val HintBadgeLineHeight = 9.sp
