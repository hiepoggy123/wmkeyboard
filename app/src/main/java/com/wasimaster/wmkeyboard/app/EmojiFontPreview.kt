package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.emoji.EmojiFontShaping
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The faces the preview draws — a spread of the shapes people notice first. */
private val PREVIEW_EMOJI = listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "❤️", "🎉")

/**
 * A line of emoji drawn in the chosen font, for the settings screen and the
 * onboarding step that both let someone pick one.
 *
 * Shaped exactly as the keyboard shapes them, so the preview shows what the
 * panel will show — ❤️ in particular silently comes from the system font in a
 * font with no variation-selector table (see [EmojiFontShaping]). The tables
 * are read off the main thread, so the row draws unshaped for a frame rather
 * than stalling the screen on a megabyte of font.
 *
 * [refresh] is a counter a caller bumps after importing a font file, since the
 * path stays the same while its contents change.
 */
@Composable
internal fun EmojiFontPreviewRow(
    choice: EmojiFontChoice,
    installedId: String,
    modifier: Modifier = Modifier,
    refresh: Int = 0,
) {
    val context = LocalContext.current
    val family = remember(choice, installedId, refresh) {
        KeyboardFonts.emojiFamily(context, choice, installedId)
    }
    val shaper by produceState(EmojiFontShaping.Identity, choice, installedId, refresh) {
        val file = KeyboardFonts.emojiFontFile(context, choice, installedId)
        withContext(Dispatchers.Default) { EmojiFontShaping.warm(file) }
        value = EmojiFontShaping.forFontFile(file)
    }
    // One Text per emoji: emoji fonts often have no space glyph, so drawing
    // them as a single spaced string makes the glyphs overlap.
    Row(modifier = modifier) {
        for (emoji in PREVIEW_EMOJI) {
            val spelling = shaper.spelling(emoji)
            Text(
                spelling.text,
                fontSize = 24.sp,
                fontFamily = if (spelling.systemFont) null else family,
                maxLines = 1,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/** How many of the missing faces the comparison shows. */
private const val MISSING_SAMPLE = 7

/**
 * The same emoji drawn twice: in the phone's own font, where they are empty
 * boxes, and in Google's, where they are not.
 *
 * A count of missing emoji is a claim; two rows of the same faces is the
 * evidence for it, and it makes the choice under it obvious without a
 * paragraph explaining what an emoji font is. The Google row comes from the
 * downloadable-font provider, so the caller only draws this where that
 * provider exists.
 */
@Composable
internal fun MissingEmojiComparison(missing: List<String>, modifier: Modifier = Modifier) {
    val sample = remember(missing) { missing.take(MISSING_SAMPLE) }
    if (sample.isEmpty()) return
    val context = LocalContext.current
    val noto = remember { KeyboardFonts.emojiFamily(context, EmojiFontChoice.NOTO) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        MissingEmojiRow(
            label = stringResource(R.string.onboarding_emoji_compare_device),
            sample = sample,
            // Explicitly the system font: this row exists to show the boxes.
            family = null,
        )
        Spacer(Modifier.height(10.dp))
        MissingEmojiRow(
            label = stringResource(R.string.onboarding_emoji_compare_google),
            sample = sample,
            family = noto,
        )
    }
}

@Composable
private fun MissingEmojiRow(label: String, sample: List<String>, family: FontFamily?) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row {
            for (emoji in sample) {
                Text(
                    emoji,
                    fontSize = 22.sp,
                    fontFamily = family,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}
