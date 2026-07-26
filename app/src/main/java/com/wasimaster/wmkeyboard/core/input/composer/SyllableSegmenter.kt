package com.wasimaster.wmkeyboard.core.input.composer

/** One segmented unit and how many input chars it spanned, separators included. */
data class Seg(val syllable: String, val inputLen: Int)

/**
 * Splits a conversion buffer into leading units against a fixed inventory. Every
 * conversion-based IME here needs the same walk over a different alphabet:
 * pinyin letters, Jyutping letters, bopomofo, and T9 digit codes.
 *
 * **Backtracks; a plain greedy longest-match is not sufficient.** When a unit can
 * borrow the first character of the one that follows, taking the longest match at
 * each position and never reconsidering both mis-splits and, worse, *silently
 * drops the tail*: greedy `sanguo` (三国) matches `sang`, then dead-ends on `uo`
 * and returns only `[sang]`, so two of the six typed letters vanish from the
 * candidate list. Pinyin has ~1800 such pairs; Jyutping has the whole -p/-t/-k
 * final series (`aakek` 啞劇 is `aa`+`kek`, but greedy takes `aak` and dies).
 *
 * So the split is searched, not guessed, in three passes:
 *  1. forward — which positions a complete segmentation can reach at all;
 *  2. the furthest of those is where the segmentable prefix ends;
 *  3. reverse — which positions still reach that end, so step 4 only ever takes
 *     a step whose remainder still works out.
 *
 * The walk then prefers the longest unit at each position *among the steps that
 * still reach the end*, which keeps the old greedy preference wherever greedy was
 * already right. A half-typed trailing unit is left unconsumed for the caller to
 * treat as raw input, exactly as before.
 *
 * Separators are the other thing every caller needs and each spells differently:
 * pinyin writes `'` *before* a unit as a manual boundary, while Jyutping tone
 * digits and bopomofo tone marks trail *after* one. Both are counted into
 * [Seg.inputLen] — a prefix commit deletes what the user typed, not what the
 * lookup used — and neither ever appears in [Seg.syllable].
 */
object SyllableSegmenter {

    /**
     * Splits [buffer] into leading units drawn from [inventory], where no unit is
     * longer than [maxUnit] chars. [skipBefore] marks characters that separate
     * units from the left (folded into the following unit's span), [skipAfter]
     * characters that trail a unit (folded into its own span).
     *
     * Returns the units of the longest prefix of [buffer] that segments
     * completely — empty when nothing does.
     */
    fun segment(
        buffer: String,
        inventory: Set<String>,
        maxUnit: Int,
        skipBefore: (Char) -> Boolean = { false },
        skipAfter: (Char) -> Boolean = { false },
        lowercase: Boolean = true,
    ): List<Seg> {
        if (buffer.isEmpty() || inventory.isEmpty()) return emptyList()
        val s = if (lowercase) buffer.lowercase() else buffer
        val n = s.length

        // The two passes below must agree on where a step starts and lands, or
        // the reverse pass marks positions the forward pass can never stand on
        // and the walk dead-ends. Hence one definition of each, used by both.

        /** First index at or after [from] that is not a leading separator. */
        fun advance(from: Int, limit: Int): Int {
            var i = from
            while (i < limit && skipBefore(s[i])) i++
            return i
        }

        /** Where a unit of [len] starting at [start] lands, swallowing a trailer. */
        fun landing(start: Int, len: Int, limit: Int): Int {
            val after = start + len
            return if (after < limit && skipAfter(s[after])) after + 1 else after
        }

        // Which positions a complete segmentation can reach from the start.
        val reach = BooleanArray(n + 1)
        reach[0] = true
        for (i in 0 until n) {
            if (!reach[i]) continue
            val start = advance(i, n)
            if (start >= n) continue
            for (len in 1..minOf(maxUnit, n - start)) {
                if (s.substring(start, start + len) in inventory) {
                    reach[landing(start, len, n)] = true
                }
            }
        }
        // The furthest one is where the segmentable prefix ends.
        var end = n
        while (end > 0 && !reach[end]) end--
        if (end == 0) return emptyList()

        // Which positions can still reach that end — the backtracking the greedy
        // walk lacked, so a step is only taken when the rest still works out.
        val toEnd = BooleanArray(end + 1)
        toEnd[end] = true
        for (i in end - 1 downTo 0) {
            val start = advance(i, end)
            if (start >= end) continue
            for (len in 1..minOf(maxUnit, end - start)) {
                if (s.substring(start, start + len) !in inventory) continue
                val next = landing(start, len, end)
                if (next <= end && toEnd[next]) { toEnd[i] = true; break }
            }
        }

        val out = ArrayList<Seg>()
        var i = 0
        while (i < end) {
            val start = advance(i, end)
            if (start >= end) break
            var chosen = 0
            var next = -1
            // Longest first, so the old greedy preference is kept wherever the
            // remainder still segments.
            for (len in minOf(maxUnit, end - start) downTo 1) {
                if (s.substring(start, start + len) !in inventory) continue
                val landed = landing(start, len, end)
                if (landed <= end && toEnd[landed]) { chosen = len; next = landed; break }
            }
            if (chosen == 0) break
            // The span runs from where the caller left off, so skipped leading
            // separators are deleted along with the unit they introduced.
            out.add(Seg(s.substring(start, start + chosen), next - i))
            i = next
        }
        return out
    }
}
