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
 * sits in (发 → 發 in 發展 but 髮 in 頭髮). This takes the more common form.
 *
 * ### Regional vocabulary
 *
 * Converting characters is not the same as writing Traditional. Taiwan and Hong
 * Kong do not merely spell the mainland's words differently, they often use
 * different words: 出租車 is 計程車 in Taipei, 光盤 is 光碟, 軟件 is 軟體. A
 * character map cannot reach any of that, so a Taiwanese user with the toggle on
 * still got mainland vocabulary in Traditional dress.
 *
 * [region] adds that layer, from OpenCC's own tables: after the character pass,
 * the whole candidate is looked up as a phrase, then any remaining regional
 * character preferences are applied. Whole-string lookup is enough because a
 * candidate is always a complete dictionary word — there is nothing to segment.
 */
object HanVariant {

    /** Which Traditional-using region's vocabulary to prefer. */
    enum class HanRegion { GENERIC, TAIWAN, HONG_KONG }

    /** Simplified → Traditional, one character to one. Empty until the asset loads. */
    @Volatile
    var s2t: Map<Char, Char> = emptyMap()

    /** Standard Traditional phrase → its Taiwan form (出租車 → 計程車). */
    @Volatile
    var twPhrases: Map<String, String> = emptyMap()

    /** Standard Traditional → Taiwan character preference (僞 → 偽). */
    @Volatile
    var twVariants: Map<Char, Char> = emptyMap()

    /** Standard Traditional → Hong Kong character preference. */
    @Volatile
    var hkVariants: Map<Char, Char> = emptyMap()

    /** The user's region; [HanRegion.GENERIC] leaves vocabulary untouched. */
    @Volatile
    var region: HanRegion = HanRegion.GENERIC
        set(value) { field = value; CjkDictionaries.invalidate() }

    /**
     * [text] in Traditional characters when the toggle is on and the map is
     * loaded; [text] unchanged otherwise. Characters absent from the map — which
     * is most of them, since the two scripts share the majority of their
     * characters — pass through as they are.
     *
     * The order is the one OpenCC uses and cannot be rearranged: the regional
     * tables are keyed to *Traditional* text (出租車, not 出租车), so the
     * character pass has to run first or none of them would ever match.
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
        val traditional = if (changed) out.toString() else text
        return localize(traditional)
    }

    /** [traditional] in the active region's vocabulary and character preferences. */
    private fun localize(traditional: String): String {
        val chars = when (region) {
            HanRegion.GENERIC -> return traditional
            HanRegion.TAIWAN -> twVariants
            HanRegion.HONG_KONG -> hkVariants
        }
        // The phrase table is Taiwan's; Hong Kong shares the standard vocabulary
        // and differs only in character preferences.
        val phrased = if (region == HanRegion.TAIWAN) twPhrases[traditional] ?: traditional else traditional
        if (chars.isEmpty()) return phrased
        var changed = false
        val out = StringBuilder(phrased.length)
        for (c in phrased) {
            val t = chars[c]
            if (t != null && t != c) { changed = true; out.append(t) } else out.append(c)
        }
        return if (changed) out.toString() else phrased
    }

    /**
     * Parses `phrase<TAB>replacement` lines — the multi-character rows [parse]
     * deliberately drops, which is why this exists beside it rather than as a
     * flag on it.
     */
    fun parsePhrases(lines: Sequence<String>): Map<String, String> {
        val acc = HashMap<String, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split('\t')
            if (parts.size < 2) continue
            val from = parts[0].trim()
            val to = parts[1].trim()
            if (from.isEmpty() || to.isEmpty() || from == to) continue
            acc.putIfAbsent(from, to)
        }
        return acc
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
