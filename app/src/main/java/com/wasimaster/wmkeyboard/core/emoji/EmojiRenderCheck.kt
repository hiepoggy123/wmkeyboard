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
     */
    fun unrenderable(emojis: Collection<String>, typeface: Typeface?): Set<String> {
        val paint = Paint().apply {
            this.typeface = typeface ?: Typeface.DEFAULT
        }
        return emojis.filterNotTo(HashSet()) { it.isEmpty() || paint.hasGlyph(it) }
    }
}
