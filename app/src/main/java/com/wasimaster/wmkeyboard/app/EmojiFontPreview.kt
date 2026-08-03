package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
