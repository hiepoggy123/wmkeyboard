package com.wasimaster.wmkeyboard.core.prediction

/**
 * A downloaded corpus n-gram pack for one language: bigram and trigram
 * counts, stored as ordinary `.wmdict` tries whose keys are the n-gram's
 * words joined by NUL ("of<NUL>the"). That reuses the whole existing stack —
 * mmap loading (no heap for a 600k-pair corpus), branch-and-bound
 * completion, the codec, the on-device compile pipeline — with zero new
 * formats: `frequencyOf(key)` is the pair count, and `complete("of<NUL>")`
 * enumerates followers best-first.
 *
 * Corpus counts complement, never replace, the personal stores: the user's
 * own n-grams are the stronger evidence everywhere both exist.
 */
class NgramPack private constructor(
    private val bigrams: WordSource?,
    private val trigrams: WordSource?,
) {

    val isEmpty: Boolean get() = bigrams == null && trigrams == null

    fun bigramCount(previous: String, next: String): Int =
        bigrams?.frequencyOf(key(previous, next)) ?: 0

    fun trigramCount(prev2: String, prev1: String, next: String): Int =
        trigrams?.frequencyOf(key(prev2, prev1, next)) ?: 0

    /** Corpus followers of [previous], best first. */
    fun nextWords(previous: String, limit: Int): List<String> =
        followers(bigrams, previous.lowercase() + SEPARATOR, limit)

    /** Corpus followers of the two-word context, best first. */
    fun nextWordsAfter(prev2: String, prev1: String, limit: Int): List<String> =
        followers(trigrams, prev2.lowercase() + SEPARATOR + prev1.lowercase() + SEPARATOR, limit)

    private fun followers(source: WordSource?, prefix: String, limit: Int): List<String> =
        source?.complete(prefix, limit)
            ?.map { it.word.substringAfterLast(SEPARATOR) }
            .orEmpty()

    companion object {
        val EMPTY = NgramPack(null, null)

        /** NUL, built rather than written literally: no editor or tool can
         * corrupt an invisible control character in this file. */
        val SEPARATOR: Char = 0.toChar()

        fun key(vararg words: String): String =
            words.joinToString(SEPARATOR.toString()) { it.lowercase() }

        /** Both sources optional: a language may ship only bigrams. */
        fun of(bigrams: WordSource?, trigrams: WordSource?): NgramPack =
            if (bigrams == null && trigrams == null) EMPTY else NgramPack(bigrams, trigrams)
    }
}
