package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Jyutping (粵拼) syllable segmentation — the Cantonese counterpart to
 * [PinyinSyllables]. A buffer like `neihou` is one opaque string until it is
 * split into `nei` | `hou`; only then can a commit consume the leading
 * syllable(s) and leave the tail to re-convert.
 *
 * Tones are written as the digits 1–6 *after* the syllable (`nei5hou2`). The
 * conversion table is toneless, as the pinyin one already is, so a tone digit is
 * folded into the span of the syllable it follows and dropped from the reading —
 * the same suffix fold [ZhuyinSyllables] does for its tone marks. That makes
 * tones optional: typing them narrows nothing today but costs nothing either,
 * and `neihou` behaves exactly like `nei5hou2`.
 *
 * The inventory ships as the `jyutping_syllables.txt` asset and is swapped into
 * [valid] off the main thread. Until it loads, [valid] is empty and [segment]
 * returns nothing, so the composer falls back to committing the raw roman
 * letters — no crash, no trap.
 */
object JyutpingSyllables {

    /** The loaded toneless-syllable inventory; empty until the asset is parsed in. */
    @Volatile
    var valid: Set<String> = emptySet()

    /** Longest toneless Jyutping syllable is 6 (`gwaang`, `kwaang`). */
    private const val MAX_SYLLABLE = 6

    private const val TONES = "123456"

    /** One segmented syllable and how many input chars it spanned, tone digit included. */
    data class Seg(val syllable: String, val inputLen: Int)

    /**
     * Splits [buffer] into leading syllables using [inventory], folding a trailing
     * tone digit into its syllable's [Seg.inputLen] — counted for the deletion
     * math, but never part of [Seg.syllable], which stays toneless for lookup.
     *
     * **Backtracks; a plain greedy longest-match is not sufficient here.** Jyutping
     * finals include -p/-t/-k/-m/-n/-ng, so a syllable can borrow the first letter
     * of a vowel-initial one that follows: `aakek` (啞劇) is `aa`+`kek`, but greedy
     * takes the valid `aak` first and then dead-ends on `ek`, losing a real word.
     * Pinyin sidesteps this with its apostrophe convention; Jyutping readings carry
     * no such separator, so the split has to be searched rather than guessed.
     *
     * Resolves to the longest prefix of [buffer] that segments completely, and
     * within that still prefers the longest syllable at each step — so a
     * half-typed trailing syllable is left for the caller as raw input, exactly as
     * before.
     */
    fun segment(buffer: String, inventory: Set<String>): List<Seg> {
        if (buffer.isEmpty() || inventory.isEmpty()) return emptyList()
        val s = buffer.lowercase()
        val n = s.length

        /** Where one syllable of [len] at [from] lands, swallowing a tone digit. */
        fun landing(from: Int, len: Int, limit: Int): Int {
            val after = from + len
            return if (after < limit && s[after] in TONES) after + 1 else after
        }

        // Which positions a complete segmentation can reach from the start.
        val reach = BooleanArray(n + 1)
        reach[0] = true
        for (i in 0 until n) {
            if (!reach[i]) continue
            for (len in 1..minOf(MAX_SYLLABLE, n - i)) {
                if (s.substring(i, i + len) in inventory) reach[landing(i, len, n)] = true
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
            for (len in 1..minOf(MAX_SYLLABLE, end - i)) {
                if (s.substring(i, i + len) !in inventory) continue
                val next = landing(i, len, end)
                if (next <= end && toEnd[next]) { toEnd[i] = true; break }
            }
        }

        val out = ArrayList<Seg>()
        var i = 0
        while (i < end) {
            var chosen = 0
            var next = -1
            for (len in minOf(MAX_SYLLABLE, end - i) downTo 1) {
                if (s.substring(i, i + len) !in inventory) continue
                val landed = landing(i, len, end)
                if (landed <= end && toEnd[landed]) { chosen = len; next = landed; break }
            }
            if (chosen == 0) break
            out.add(Seg(s.substring(i, i + chosen), next - i))
            i = next
        }
        return out
    }

    /** [segment] against the loaded global inventory. */
    fun segment(buffer: String): List<Seg> = segment(buffer, valid)

    /** Parses one-syllable-per-line asset text into an inventory set. */
    fun parse(lines: Sequence<String>): Set<String> =
        lines.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
}
