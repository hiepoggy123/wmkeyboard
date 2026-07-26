package com.wasimaster.wmkeyboard.core.prediction

/**
 * Read-only union of several [WordSource]s — used where a language has both a
 * downloaded dictionary and imported custom lists. A word present in more than
 * one source keeps its highest frequency, matching how [PackedTrie.of] folds
 * duplicate entries.
 */
class CompositeWordSource private constructor(
    private val sources: List<WordSource>,
) : WordSource {

    override fun complete(prefix: String, limit: Int): List<Suggestion> {
        val best = HashMap<String, Int>()
        for (source in sources) {
            for (suggestion in source.complete(prefix, limit)) {
                best.merge(suggestion.word, suggestion.frequency, ::maxOf)
            }
        }
        return best.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Suggestion(it.key, it.value) }
    }

    override fun frequencyOf(word: String): Int =
        sources.maxOfOrNull { it.frequencyOf(word) } ?: 0

    override fun contains(word: String): Boolean = sources.any { it.contains(word) }

    companion object {
        /** Collapses to the single source (or an empty one) when possible. */
        fun of(sources: List<WordSource>): WordSource = when (sources.size) {
            0 -> PackedTrie.EMPTY
            1 -> sources[0]
            else -> CompositeWordSource(sources)
        }
    }
}
