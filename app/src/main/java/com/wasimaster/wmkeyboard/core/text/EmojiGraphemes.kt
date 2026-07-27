package com.wasimaster.wmkeyboard.core.text

/**
 * Backspace support for emoji that are several code points long.
 *
 * Without this a single backspace peels one code point off a sequence and
 * leaves a fragment behind: ☠️ becomes a bare ☠, 👍🏽 loses its tone and
 * shows as 👍, 👨‍👩‍👧 sheds one family member per press. The recognised
 * shapes are the ones UTS #51 defines:
 *
 *  - emoji + variation selector (☠️ = U+2620 U+FE0F)
 *  - emoji + skin-tone modifier (👍🏽)
 *  - ZWJ sequences, any depth (👨‍👩‍👧, 🏋️‍♀️)
 *  - regional-indicator flag pairs (🇧🇩)
 *  - keycaps (1️⃣) and tag sequences (🏴󠁧󠁢󠁥󠁮󠁧󠁿)
 *
 * Deliberately narrow: [deleteLength] returns 0 for anything that is not an
 * emoji cluster, so ordinary text (and Bengali, whose conjuncts use the same
 * ZWJ this scans for) falls through to the caller's own rules untouched.
 */
object EmojiGraphemes {

    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3

    private fun isTone(cp: Int) = cp in 0x1F3FB..0x1F3FF
    private fun isTag(cp: Int) = cp in 0xE0020..0xE007F
    private fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF
    private fun isSelector(cp: Int) = cp == VS15 || cp == VS16

    /**
     * True for code points that can head an emoji. Used as the guard that
     * keeps this off non-emoji text: Bengali (U+0980..U+09FF) and every
     * other script sit below the ranges listed here, so a ZWJ-joined
     * conjunct is never mistaken for a ZWJ emoji sequence.
     */
    private fun isEmojiBase(cp: Int) = cp >= 0x1F000 ||
        cp in 0x2100..0x21FF || cp in 0x2300..0x23FF || cp in 0x2460..0x27BF ||
        cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF || cp in 0x3030..0x303D ||
        cp in 0x3200..0x32FF || cp == 0x00A9 || cp == 0x00AE ||
        cp == 0x203C || cp == 0x2049

    /**
     * UTF-16 units to delete so the whole emoji before the cursor goes in
     * one backspace, or 0 when [before] does not end in an emoji cluster.
     *
     * [before] should be the text immediately preceding the cursor; a few
     * dozen units is plenty, but a truncated lookback only ever shortens the
     * cluster, never corrupts it.
     */
    // The ZWJ walk below reads as a one-shot loop to detekt: it only sees the
    // unconditional `break` that ends the body, not the `continue` above it that
    // makes the loop iterate over joined segments.
    @Suppress("UnconditionalJumpStatementInLoop")
    fun deleteLength(before: CharSequence): Int {
        if (before.isEmpty()) return 0
        val cps = codePoints(before)
        val last = cps.lastIndex

        // 🏴󠁧󠁢󠁥󠁮󠁧󠁿 — tag chars trailing a base emoji.
        if (isTag(cps[last])) {
            var j = last
            while (j >= 0 && isTag(cps[j])) j--
            return if (j >= 0) unitsFrom(cps, j) else 0
        }
        // 1️⃣ — [char] FE0F 20E3.
        if (cps[last] == KEYCAP) {
            var j = last - 1
            if (j >= 0 && isSelector(cps[j])) j--
            return if (j >= 0) unitsFrom(cps, j) else 0
        }
        // 🇧🇩 — one flag is two regional indicators; an odd trailing count
        // means the pair is still half-typed, so drop a single indicator.
        if (isRegional(cps[last])) {
            var count = 0
            var j = last
            while (j >= 0 && isRegional(cps[j])) {
                count++
                j--
            }
            return if (count % 2 == 0) unitsFrom(cps, last - 1) else unitsFrom(cps, last)
        }

        // Walk back over `base [tone|selector]*` segments joined by ZWJ.
        var start = last
        var sawEmoji = false
        while (true) {
            while (start >= 0 && (isTone(cps[start]) || isSelector(cps[start]))) start--
            if (start < 0) break
            if (isEmojiBase(cps[start])) sawEmoji = true
            start--
            if (start >= 0 && cps[start] == ZWJ) {
                start--
                continue
            }
            break
        }
        val from = start + 1
        // A lone code point is the caller's ordinary backspace, not ours.
        if (!sawEmoji || from > last || (from == last && !isSelector(cps[last]))) return 0
        return unitsFrom(cps, from)
    }

    /**
     * UTF-16 units the ⌦ key should delete: the whole cluster *starting* at
     * [after], so one forward delete takes 👨‍👩‍👧 or a Bengali কি off in one
     * press rather than shedding a code point at a time.
     *
     * The mirror image of [deleteLength] with one deliberate difference: this
     * never returns 0 for a non-empty string. Backspace only asks about emoji
     * and has its own rules for everything else; forward delete has no such
     * fallback, so ordinary text answers 1 (or 2 across a surrogate pair) and
     * combining marks ride along with the base they attach to.
     */
    fun forwardDeleteLength(after: CharSequence): Int {
        if (after.isEmpty()) return 0
        val cps = codePoints(after)
        var i = 0

        fun consumeTrailers() {
            while (i <= cps.lastIndex && isTrailer(cps[i])) i++
        }

        // 🇧🇩 — a flag is a pair of regional indicators, taken together.
        if (isRegional(cps[0])) {
            i = if (cps.size >= 2 && isRegional(cps[1])) 2 else 1
            return unitsBefore(cps, i)
        }

        i = 1
        consumeTrailers()
        // ZWJ sequences: keep taking `ZWJ base [tone|selector]*` while they run.
        while (i < cps.lastIndex && cps[i] == ZWJ) {
            i += 2
            consumeTrailers()
        }
        return unitsBefore(cps, i)
    }

    /**
     * Code points that belong to the character before them and so travel with
     * it: emoji tones and variation selectors, keycap and tag marks, and the
     * ordinary combining marks of every script.
     */
    private fun isTrailer(cp: Int): Boolean =
        isTone(cp) || isSelector(cp) || isTag(cp) || cp == KEYCAP || isCombining(cp)

    /** Marks that belong to the character before them (accents, Indic kars). */
    private fun isCombining(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }

    /** UTF-16 length of the first [count] code points of [cps]. */
    private fun unitsBefore(cps: IntArray, count: Int): Int {
        var units = 0
        for (i in 0 until minOf(count, cps.size)) units += Character.charCount(cps[i])
        return units
    }

    /** UTF-16 length of the code points from [from] to the end of [cps]. */
    private fun unitsFrom(cps: IntArray, from: Int): Int {
        if (from < 0) return 0
        var units = 0
        for (i in from..cps.lastIndex) units += Character.charCount(cps[i])
        return units
    }

    private fun codePoints(text: CharSequence): IntArray {
        val out = IntArray(text.length)
        var n = 0
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            out[n++] = cp
            i += Character.charCount(cp)
        }
        return out.copyOf(n)
    }
}
