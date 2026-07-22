package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Toneless-pinyin syllable segmentation. A conversion buffer like `nihao` is one
 * opaque string until it is split into syllables (`ni` | `hao`); only then can a
 * commit consume the leading syllable(s) and leave the tail to re-convert.
 *
 * The valid-syllable inventory (~410 entries) ships as the `pinyin_syllables.txt`
 * asset and is swapped into [valid] off the main thread, mirroring how
 * [CjkDictionaries] loads its conversion tables. Until it loads, [valid] is empty
 * and [segment] returns nothing, so the composer falls back to its raw-buffer
 * behaviour — no crash, no regression.
 *
 * [segment] takes the inventory as a parameter so the pure logic is testable with
 * a tiny fixture set; the no-arg overload uses the loaded global.
 */
object PinyinSyllables {

    /** The loaded valid-syllable inventory; empty until the asset is parsed in. */
    @Volatile
    var valid: Set<String> = emptySet()

    /** Longest real toneless pinyin syllable is 6 chars (`zhuang`, `chuang`, `shuang`). */
    private const val MAX_SYLLABLE = 6

    /** One segmented syllable and how many input chars it spanned. */
    data class Seg(val syllable: String, val inputLen: Int)

    /**
     * Splits [buffer] into leading syllables using [inventory], greedily taking
     * the longest valid syllable at each position and honouring `'` as a forced
     * boundary. A leading apostrophe is folded into the next syllable's
     * [Seg.inputLen] so a prefix commit deletes it along with the syllable.
     * Stops at the first position that no valid syllable covers (an in-progress
     * trailing syllable), returning the syllables matched so far; the caller
     * treats the unconsumed remainder as raw input.
     */
    fun segment(buffer: String, inventory: Set<String>): List<Seg> {
        if (buffer.isEmpty() || inventory.isEmpty()) return emptyList()
        val s = buffer.lowercase()
        val out = ArrayList<Seg>()
        var i = 0
        while (i < s.length) {
            // Consume any apostrophe boundaries; count them toward this syllable's
            // input span so deletion math stays exact.
            var lead = 0
            while (i + lead < s.length && s[i + lead] == '\'') lead++
            val start = i + lead
            if (start >= s.length) break
            var matchLen = 0
            val maxLen = minOf(MAX_SYLLABLE, s.length - start)
            for (len in maxLen downTo 1) {
                if (s.substring(start, start + len) in inventory) { matchLen = len; break }
            }
            if (matchLen == 0) break
            out.add(Seg(s.substring(start, start + matchLen), lead + matchLen))
            i = start + matchLen
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
