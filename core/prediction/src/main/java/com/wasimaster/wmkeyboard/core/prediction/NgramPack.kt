package com.wasimaster.wmkeyboard.core.prediction

/**
 * A downloaded corpus n-gram pack for one language: bigram and trigram counts,
 * read straight out of a memory-mapped [MappedNgramPack].
 *
 * The pack carries its own vocabulary and its own id space, so nothing outside
 * this class ever learns that followers are stored as numbers. Callers pass
 * words and get words back.
 *
 * Corpus counts complement, never replace, the personal stores: the user's own
 * n-grams are the stronger evidence everywhere both exist.
 */
class NgramPack private constructor(private val mapped: MappedNgramPack?) {

    /** No pack installed. Not "no trigrams" — a bigram-only pack is a real pack. */
    val isEmpty: Boolean get() = mapped == null

    fun bigramCount(previous: String, next: String): Int =
        mapped?.bigramCount(previous.lowercase(), next.lowercase()) ?: 0

    fun trigramCount(prev2: String, prev1: String, next: String): Int =
        mapped?.trigramCount(prev2.lowercase(), prev1.lowercase(), next.lowercase()) ?: 0

    /** Corpus followers of [previous], best first. */
    fun nextWords(previous: String, limit: Int): List<String> =
        mapped?.nextWords(previous.lowercase(), limit).orEmpty()

    /** Corpus followers of the two-word context, best first. */
    fun nextWordsAfter(prev2: String, prev1: String, limit: Int): List<String> =
        mapped?.nextWordsAfter(prev2.lowercase(), prev1.lowercase(), limit).orEmpty()

    companion object {
        val EMPTY = NgramPack(null)

        fun of(mapped: MappedNgramPack?): NgramPack =
            if (mapped == null) EMPTY else NgramPack(mapped)
    }
}
