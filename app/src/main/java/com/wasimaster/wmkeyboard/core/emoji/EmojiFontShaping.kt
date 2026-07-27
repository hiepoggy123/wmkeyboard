package com.wasimaster.wmkeyboard.core.emoji

import android.graphics.Paint
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

/**
 * Spells an emoji the way the *chosen font* wants to see it.
 *
 * The catalog stores fully-qualified sequences, which is what the standard says
 * to send: `❤️‍🔥` is `2764 FE0F 200D 1F525`, with U+FE0F asking for the
 * emoji-coloured form of a character that would otherwise be plain text. The
 * system emoji font handles that through a cmap format-14 variation-selector
 * subtable, and every ZWJ ligature in it is built around the qualified form.
 *
 * Third-party emoji fonts frequently are not. A converted set often ships its
 * GSUB ligatures keyed on the *unqualified* sequence (`2764 200D 1F525`) and no
 * format-14 subtable at all — so handed the qualified string the font matches
 * no ligature, draws a bare heart, and then has nothing to draw for the U+FE0F
 * itself. The result is `❤ ▯ 🔥`: three glyphs where one was meant, which is
 * how a perfectly good emoji font ends up looking broken.
 *
 * So the sequence is a rendering detail, not the text. [EmojiShaper.shape]
 * returns the spelling this font can actually draw; what gets committed to the
 * field is always the catalog's own, standard form.
 *
 * Probing is lazy and cached: the first time an emoji is drawn in a given font
 * it costs a few [Paint.hasGlyph] calls, and nothing after that. With the
 * system font ([forSystemFont]) it costs nothing at all — that font wants the
 * qualified form, which is what we already have.
 */
class EmojiShaper internal constructor(private val canDraw: ((String) -> Boolean)?) {

    private val cache = ConcurrentHashMap<String, String>()

    /**
     * [emoji] respelled for this font, or unchanged when it is already right —
     * which is the answer for the system font, for a font that handles the
     * qualified form, and for anything with no variation selector in it.
     */
    fun shape(emoji: String): String {
        val probe = canDraw ?: return emoji
        // One code point can't be mis-spelled: there is no selector to move and
        // no ligature to miss.
        if (emoji.length < 2) return emoji
        return cache.getOrPut(emoji) { pick(probe, emoji) }
    }

    private fun pick(probe: (String) -> Boolean, emoji: String): String {
        // The authored form first. When the font is fine with it — the usual
        // case, including every font with a format-14 subtable — nothing else
        // is even tried.
        if (probe(emoji)) return emoji
        for (candidate in EmojiFontShaping.candidates(emoji)) {
            if (candidate != emoji && probe(candidate)) return candidate
        }
        // Nothing draws as one glyph. Keep the standard spelling: the emoji is
        // missing from this font either way, and `hideUnrenderable` is the
        // setting that deals with that.
        return emoji
    }
}

object EmojiFontShaping {

    private const val VS16 = '️'
    private const val ZWJ = '‍'

    /** U+20E3 COMBINING ENCLOSING KEYCAP — the box around `1️⃣`. */
    private const val KEYCAP = 0x20E3

    /** The identity shaper: every emoji is drawn exactly as the catalog spells it. */
    val Identity = EmojiShaper(null)

    /**
     * A shaper for [typeface], or [Identity] when it is null.
     *
     * Null means the system emoji font, which is the one font guaranteed to
     * want the standard spelling — so the whole mechanism switches off for the
     * default configuration rather than probing a font that never needs it.
     */
    fun forTypeface(typeface: Typeface?): EmojiShaper {
        if (typeface == null) return Identity
        val paint = Paint().apply { this.typeface = typeface }
        return EmojiShaper { paint.hasGlyph(it) }
    }

    /** Explicit form of "no shaping needed", for readability at call sites. */
    fun forSystemFont(): EmojiShaper = Identity

    /**
     * Alternative spellings of [emoji], in the order worth trying.
     *
     * Two directions, because fonts get it wrong both ways: drop every U+FE0F
     * (for a font whose ligatures were built unqualified, or that has no
     * format-14 subtable to absorb the selector), and add one after each
     * text-default base (for a font that keyed its ligatures on the qualified
     * form when the source sequence omitted it).
     *
     * Internal rather than private so it can be tested without a device —
     * everything here is pure string work; only the probe needs Android.
     */
    internal fun candidates(emoji: String): List<String> = buildList {
        val stripped = emoji.filter { it != VS16 }
        if (stripped.isNotEmpty()) add(stripped)
        val qualified = qualify(stripped)
        if (qualified != stripped) add(qualified)
    }

    /**
     * U+FE0F after every base that defaults to text presentation.
     *
     * Only those: adding one to a character that is already emoji-presentation
     * by default (👍, 🔥 — everything from U+1F000 up) is legal but pointless,
     * and doubles the number of spellings to probe. The text-default set is
     * essentially the BMP symbols, which is what [needsSelector] tests.
     */
    private fun qualify(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            appendRange(text, i, i + width)
            i += width
            if (!needsSelector(cp)) continue
            // Not before a skin tone or another selector: the selector belongs
            // directly after its own base, and a tone already forces emoji
            // presentation on its own.
            val next = if (i < text.length) text.codePointAt(i) else -1
            val followed = next == VS16.code || next in 0x1F3FB..0x1F3FF
            if (!followed) append(VS16)
        }
    }

    /**
     * True for the code points that render as plain text unless asked not to —
     * the legacy symbol blocks that predate emoji, plus the keycap bases.
     *
     * The exclusions matter more than the inclusions. A combining mark or a
     * format control is not a base, and putting a selector after one moves it
     * out of position: `1 FE0F 20E3` is a keycap, `1 20E3 FE0F` is a digit with
     * two marks stuck on it. Regional indicators are excluded for the same
     * reason — a selector between the two halves of 🇧🇩 is two letters.
     */
    private fun needsSelector(cp: Int): Boolean {
        if (cp == ZWJ.code || cp == VS16.code || cp == KEYCAP) return false
        // General Punctuation's format controls through the invisible operators.
        if (cp in 0x200B..0x206F) return false
        // The halves of a flag.
        if (cp in 0x1F1E6..0x1F1FF) return false
        return cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code ||
            cp in 0x00A9..0x00AE ||
            cp in 0x2000..0x2BFF ||
            cp in 0x3030..0x303D ||
            cp in 0x3297..0x3299 ||
            cp in 0x1F170..0x1F251
    }
}
