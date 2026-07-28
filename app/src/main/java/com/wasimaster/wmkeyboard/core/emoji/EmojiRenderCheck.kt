package com.wasimaster.wmkeyboard.core.emoji

import android.graphics.Paint
import android.graphics.Typeface
import java.io.File

/**
 * Detects which emoji a given font can actually draw.
 *
 * An emoji the active font has no glyph for renders as a blank "tofu" box (or,
 * for a newer ZWJ sequence, as its unjoined component parts), which is what the
 * "hide unrenderable emoji" setting exists to sweep out of the panel.
 *
 * Two different questions, two different tools:
 *
 *  - **The system emoji font.** [Paint.hasGlyph] is exactly right here: the
 *    paint's font collection *is* the system chain, and the call reports
 *    whether the string collapses to a single positioned glyph. A phone whose
 *    emoji font predates the bundled catalog is caught this way.
 *  - **A chosen font.** [Paint.hasGlyph] is exactly wrong here, because the
 *    collection behind a `Typeface.createFromFile` is that font *plus the
 *    system fallback chain* — so it answers for the phone, not for the font,
 *    and reports near-perfect coverage for a font with almost none. The font's
 *    own tables are read instead; see [EmojiFontCoverage].
 *
 * The check is a few thousand cheap lookups over the catalog; run it off the
 * main thread and cache the result (see the IME's hidden-emoji set).
 */
object EmojiRenderCheck {

    /**
     * The subset of [emojis] that will not draw at all — neither in the font at
     * [fontFile] nor, once that font has been given up on, in the system emoji
     * font. Null means the system font was the choice all along.
     *
     * Both halves are needed, and only together. An emoji the chosen font
     * lacks is not missing from the *panel*: [EmojiShaper] hands that one to
     * the system font, which draws it (see `emojiFamilyFor`). Hiding those
     * would empty the panel of perfectly visible emoji every time someone
     * picked a font with an older catalog. What genuinely cannot be shown is
     * what neither font has — a Unicode 16 face on a phone whose emoji font
     * stopped at 15 — and that is what this setting is for.
     */
    fun unrenderable(emojis: Collection<String>, fontFile: File?): Set<String> {
        val shaper = EmojiFontShaping.forFontFile(fontFile)
        // With no chosen font, the system font draws everything, so its own
        // coverage is the only question.
        val systemOnly = shaper === EmojiFontShaping.Identity
        val paint = Paint().apply { typeface = Typeface.DEFAULT }
        return emojis.filterTo(HashSet()) { emoji ->
            emoji.isNotEmpty() &&
                (systemOnly || shaper.drawsWithSystemFont(emoji)) &&
                !paint.hasGlyph(emoji)
        }
    }
}
