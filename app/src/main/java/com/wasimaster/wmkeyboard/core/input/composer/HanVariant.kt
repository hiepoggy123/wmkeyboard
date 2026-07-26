package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Simplified → Traditional Han conversion for candidate output.
 *
 * The conversion tables are all keyed to Simplified characters, but three of the
 * input methods here are used mostly by Traditional writers — Zhuyin in Taiwan,
 * Cangjie and Jyutping in Hong Kong. Rather than ship a second copy of every
 * dictionary, candidates pass through this character map on the way out when
 * [CjkConfig.traditionalOutput] is on.
 *
 * **Where this is applied matters.** It runs *inside* each composer, at the point
 * a dictionary word becomes a candidate — never on the list a composer has
 * already returned. `Composer.consumedFor` finds the chosen candidate by string
 * equality against what the composer produced, so converting afterwards would
 * leave it unable to match, silently falling back to consuming the whole buffer
 * and breaking prefix commit.
 *
 * The map is loaded from the bundled `s2t.txt` asset; until then it is empty and
 * [toTraditional] is the identity, so the toggle is inert rather than destructive.
 *
 * Known limitation: character-level mapping is not truly 1:1 — one simplified
 * character can correspond to several traditional ones depending on the word it
 * sits in (发 → 發 in 發展 but 髮 in 頭髮). This takes the more common form;
 * phrase-aware accuracy would need OpenCC-style logic and a much larger table.
 */
object HanVariant {

    /** Simplified → Traditional, one character to one. Empty until the asset loads. */
    @Volatile
    var s2t: Map<Char, Char> = emptyMap()

    /**
     * [text] in Traditional characters when the toggle is on and the map is
     * loaded; [text] unchanged otherwise. Characters absent from the map — which
     * is most of them, since the two scripts share the majority of their
     * characters — pass through as they are.
     */
    fun toTraditional(text: String): String {
        val map = s2t
        if (!CjkConfig.traditionalOutput || map.isEmpty() || text.isEmpty()) return text
        var changed = false
        val out = StringBuilder(text.length)
        for (c in text) {
            val t = map[c]
            if (t != null && t != c) { changed = true; out.append(t) } else out.append(c)
        }
        // Returning the original instance when nothing mapped keeps the common
        // case allocation-free — this runs per candidate, per keystroke.
        return if (changed) out.toString() else text
    }

    /** Parses `simplified<TAB>traditional` lines; malformed or identity rows are skipped. */
    fun parse(lines: Sequence<String>): Map<Char, Char> {
        val acc = HashMap<Char, Char>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split('\t')
            if (parts.size < 2) continue
            val simplified = parts[0].trim()
            val traditional = parts[1].trim()
            // One character per side: a multi-character mapping is a phrase rule,
            // which this table deliberately does not model.
            if (simplified.length != 1 || traditional.length != 1) continue
            if (simplified[0] == traditional[0]) continue
            acc.putIfAbsent(simplified[0], traditional[0])
        }
        return acc
    }
}
