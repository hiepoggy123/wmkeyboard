package com.wasimaster.wmkeyboard.core.input.composer

import kotlin.math.ln

/**
 * Word unigram and bigram counts for the conversion decoder — how likely 事情 is
 * at all, and how much likelier it becomes right after 这件.
 *
 * **No pack ships one yet.** [EMPTY] is the shipped state, and it is a working
 * state rather than a stub: [logProbability] then falls back to the conversion
 * pack's own frequency column, which is what makes the decoder a unigram Viterbi
 * today and a bigram one the day a pack lands, with no change to [Lattice].
 *
 * The format, when it does land, is the plain TSV every other pack here uses,
 * with the section decided by field count:
 * ```
 * # wmkeyboard cjk ngram v1
 * 你好\t1234567          <- 2 fields: unigram
 * 这件\t事情\t12345      <- 3 fields: bigram
 * ```
 * The unigram section is not redundant with the conversion pack's frequencies:
 * backoff needs the count of a *word* across all its readings, and that pack is
 * keyed by reading with no way to index the other way. Keeping both sections in
 * one file also keeps them from drifting to different corpora or tokenizers.
 *
 * Storage is two sorted `LongArray`s of 64-bit hashes with parallel counts, not
 * the `Map<String, Map<String, Int>>` this obviously wants to be — that shape is
 * exactly what blew a 256 MB heap in [ConversionDictionary] and forced its
 * structure-of-arrays layout. At ~300k bigrams a hashed table is ~3.6 MB; the map
 * would be an order of magnitude more. Hashing costs collisions, but at 64 bits
 * over 300k keys that is a ~1e-7 chance of one wrong score, which is not worth a
 * fallback path to fix.
 */
class CjkNgrams private constructor(
    private val uniKeys: LongArray,
    private val uniCounts: IntArray,
    private val biKeys: LongArray,
    private val biCounts: IntArray,
    /** Corpus size, the denominator for an unconditioned unigram. */
    private val totalTokens: Long,
) {

    val isEmpty: Boolean get() = uniKeys.isEmpty() && biKeys.isEmpty()

    /** Corpus count of [word], or 0 when the model has never seen it. */
    fun unigram(word: String): Int {
        val i = uniKeys.binarySearch(hash(word))
        return if (i >= 0) uniCounts[i] else 0
    }

    /** Corpus count of [previous] immediately followed by [word]. */
    fun bigram(previous: String, word: String): Int {
        val i = biKeys.binarySearch(pairHash(previous, word))
        return if (i >= 0) biCounts[i] else 0
    }

    /**
     * `log P(word | previous)` by stupid backoff (Brants et al. 2007): the
     * conditional when the pair was seen, otherwise [BACKOFF] times the word's
     * own probability.
     *
     * Stupid backoff rather than Kneser-Ney deliberately. KN wants continuation
     * counts, which is a third table on a heap that has already run out once, and
     * at corpus scale the two are hard to tell apart — this is exactly the
     * lots-of-data regime it was published for. It also needs no normalizing
     * constant, so an edge costs two array lookups and a subtraction.
     *
     * [packTotal] and [packFreq] are the conversion pack's own numbers, used when
     * this model is [EMPTY] so the decoder still has a real unigram to rank by.
     * Always finite: an unseen word floors at one notional occurrence rather than
     * `ln(0)`, so a path can never be scored at negative infinity and drop out.
     */
    fun logProbability(previous: String?, word: String, packFreq: Int, packTotal: Long): Double {
        if (isEmpty) return ln((packFreq + 1).toDouble()) - ln((packTotal + 1).toDouble())
        if (previous != null) {
            val pair = bigram(previous, word)
            if (pair > 0) {
                val prior = unigram(previous)
                if (prior > 0) return ln(pair.toDouble()) - ln(prior.toDouble())
            }
        }
        return LOG_BACKOFF + ln((unigram(word) + 1).toDouble()) - ln((totalTokens + 1).toDouble())
    }

    companion object {
        val EMPTY = CjkNgrams(LongArray(0), IntArray(0), LongArray(0), IntArray(0), 0L)

        /** The published stupid-backoff weight. */
        const val BACKOFF = 0.4
        private val LOG_BACKOFF = ln(BACKOFF)

        /**
         * Parses the TSV described on the class. Two fields is a unigram, three a
         * bigram; anything else — blank lines, comments, malformed rows — is
         * skipped, matching [ConversionDictionary.parse]'s tolerance of a pack
         * that picked up stray lines somewhere between the corpus and the device.
         */
        fun parse(lines: Sequence<String>): CjkNgrams {
            val uni = HashMap<Long, Int>()
            val bi = HashMap<Long, Int>()
            var total = 0L
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                when (parts.size) {
                    2 -> {
                        val count = parts[1].trim().toIntOrNull() ?: continue
                        if (parts[0].isEmpty() || count <= 0) continue
                        uni[hash(parts[0])] = count
                        total += count
                    }
                    3 -> {
                        val count = parts[2].trim().toIntOrNull() ?: continue
                        if (parts[0].isEmpty() || parts[1].isEmpty() || count <= 0) continue
                        bi[pairHash(parts[0], parts[1])] = count
                    }
                    else -> continue
                }
            }
            if (uni.isEmpty() && bi.isEmpty()) return EMPTY
            val (uk, uc) = sortedPairs(uni)
            val (bk, bc) = sortedPairs(bi)
            return CjkNgrams(uk, uc, bk, bc, total)
        }

        /** A hash map flattened into key-sorted parallel arrays for binary search. */
        private fun sortedPairs(src: Map<Long, Int>): Pair<LongArray, IntArray> {
            val keys = src.keys.toLongArray()
            keys.sort()
            val counts = IntArray(keys.size)
            for (i in keys.indices) counts[i] = src.getValue(keys[i])
            return keys to counts
        }

        /** FNV-1a 64 over the chars — cheap, and well spread for short strings. */
        private fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }

        /** [hash] of the pair, separated so `ab|c` and `a|bc` cannot collide. */
        private fun pairHash(a: String, b: String): Long {
            var h = hash(a)
            h = h xor 0L
            h *= 0x100000001b3L
            for (c in b) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }
    }
}
