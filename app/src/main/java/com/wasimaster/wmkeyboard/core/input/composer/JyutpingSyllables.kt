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
     * Splits [buffer] into leading syllables using [inventory], greedily taking
     * the longest valid syllable at each position and folding a trailing tone
     * digit into that syllable's [Seg.inputLen] — counted for the deletion math,
     * but never part of [Seg.syllable], which stays toneless for lookup. Stops at
     * the first position no syllable covers, returning what matched so far.
     */
    fun segment(buffer: String, inventory: Set<String>): List<Seg> {
        if (buffer.isEmpty() || inventory.isEmpty()) return emptyList()
        val s = buffer.lowercase()
        val out = ArrayList<Seg>()
        var i = 0
        while (i < s.length) {
            var matchLen = 0
            val maxLen = minOf(MAX_SYLLABLE, s.length - i)
            for (len in maxLen downTo 1) {
                if (s.substring(i, i + len) in inventory) { matchLen = len; break }
            }
            if (matchLen == 0) break
            val syllable = s.substring(i, i + matchLen)
            i += matchLen
            val toned = i < s.length && s[i] in TONES
            if (toned) i++
            out.add(Seg(syllable, matchLen + if (toned) 1 else 0))
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
