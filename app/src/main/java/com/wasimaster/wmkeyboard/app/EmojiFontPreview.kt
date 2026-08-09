package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
 * paragraph explaining what an emoji font is.
 *
 * The second row is only honest if Google's font actually draws those faces,
 * and twice it might not: the provider can fail to hand the font over at all
 * (no Play services, no network, a device that has never fetched it), and the
 * version it hands over can be old enough to be missing the same new emoji the
 * phone is. Either way the row would have been a second set of boxes — the
 * exact screenshot this guard exists for. So the sample is the intersection:
 * emoji this phone cannot draw *and* the fetched Noto can, and with nothing in
 * that intersection the whole comparison stays away.
 */
@Composable
internal fun MissingEmojiComparison(missing: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sample by produceState(emptyList<String>(), missing) {
        value = withContext(Dispatchers.Default) { notoOnlyEmoji(context, missing) }
    }
    if (sample.isEmpty()) return
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

/**
 * The emoji worth putting in the comparison: the ones this phone really draws
 * as a box, and that Google's font really draws as something else.
 *
 * Both halves are decided by drawing them, not by asking. `Paint.hasGlyph` is
 * the only "does this font have it" API there is, and it is wrong often enough
 * to be useless here: it answers for the glyph, not for the sequence, so it
 * says yes to a flag whose regional-indicator pair the font renders as two
 * lettered squares, and yes to a ZWJ sequence a font renders as its separate
 * parts. Both of those produced comparison rows where the two halves were
 * identical — a picture whose caption said the opposite of what it showed.
 *
 * Rendering each candidate into a small bitmap answers the actual question.
 * The device's own drawing is compared against a codepoint nothing can draw,
 * which is what its "missing" box looks like; and Google's drawing is compared
 * against the device's, because a Google row that comes out pixel-identical is
 * not an upgrade to show anyone.
 *
 * Costs one small bitmap pair per candidate and runs off the main thread; the
 * candidate list is capped for the same reason.
 */
private suspend fun notoOnlyEmoji(context: Context, missing: List<String>): List<String> {
    val noto = KeyboardFonts.emojiTypeface(context, EmojiFontChoice.NOTO) ?: return emptyList()
    val tofu = renderGlyph(UNDRAWABLE, null)
    return missing.asSequence()
        .take(MISSING_CANDIDATES)
        .filter { emoji ->
            val system = renderGlyph(emoji, null)
            // Drawn as the device's own "no glyph" box, and drawn differently
            // by Google's font: only then is there something to show.
            system.sameAs(tofu) && !renderGlyph(emoji, noto).sameAs(system)
        }
        .take(MISSING_SAMPLE)
        .toList()
}

/** Renders one emoji at [GLYPH_PROBE_PX] square, in [typeface] or the system font. */
private fun renderGlyph(emoji: String, typeface: Typeface?): Bitmap {
    val bitmap = Bitmap.createBitmap(GLYPH_PROBE_PX, GLYPH_PROBE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = GLYPH_PROBE_PX * 0.8f
        textAlign = Paint.Align.CENTER
    }
    val baseline = GLYPH_PROBE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(emoji, GLYPH_PROBE_PX / 2f, baseline, paint)
    return bitmap
}

/**
 * A codepoint no font will ever have (a plane-16 noncharacter), so whatever it
 * draws as is this device's "I do not have this" box. Built from its number
 * rather than written out, because an astral literal does not survive every
 * editor it passes through.
 */
private val UNDRAWABLE = String(Character.toChars(0x10FFFD))

/** Side of the square each probe is rendered into. */
private const val GLYPH_PROBE_PX = 24

/** How many of the reported-missing emoji are probed before giving up. */
private const val MISSING_CANDIDATES = 40

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
