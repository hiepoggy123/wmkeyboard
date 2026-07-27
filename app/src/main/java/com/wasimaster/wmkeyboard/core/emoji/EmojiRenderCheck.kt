package com.wasimaster.wmkeyboard.core.emoji

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Detects which emoji a given font can actually draw.
 *
 * An emoji the active font has no glyph for renders as a blank "tofu" box (or,
 * for a newer ZWJ sequence, as its unjoined component parts). [Paint.hasGlyph]
 * asks the font-and-fallback chain whether a string collapses to a single
 * positioned glyph, which is exactly the "can this be drawn?" question — so a
 * device whose emoji font predates the bundled Unicode catalog is caught here.
 *
 * The check is a few thousand cheap calls over the catalog; run it off the main
 * thread and cache the result (see the IME's hidden-emoji set).
 */
object EmojiRenderCheck {

    /**
     * The subset of [emojis] that [typeface] (or the system emoji font, when
     * null) cannot render as a single glyph.
     *
     * An emoji only counts as missing when *no* spelling of it draws. A
     * third-party font whose ligatures are keyed on the unqualified sequence
     * would otherwise fail every ZWJ emoji here and hide most of the catalog,
     * when in fact [EmojiFontShaping] is about to draw them correctly — the two
     * have to agree on what "this font can't draw it" means.
     */
    fun unrenderable(emojis: Collection<String>, typeface: Typeface?): Set<String> {
        val paint = Paint().apply {
            this.typeface = typeface ?: Typeface.DEFAULT
        }
        return emojis.filterNotTo(HashSet()) { emoji ->
            emoji.isEmpty() || paint.hasGlyph(emoji) ||
                EmojiFontShaping.candidates(emoji).any { paint.hasGlyph(it) }
        }
    }
}
