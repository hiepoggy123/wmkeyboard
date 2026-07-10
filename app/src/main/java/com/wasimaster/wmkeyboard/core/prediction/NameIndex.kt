package com.wasimaster.wmkeyboard.core.prediction

/**
 * An in-memory word index built from multi-word proper names, so they
 * complete like dictionary words ("was" → Wasi) and chain like bigrams
 * ("Wasi" → Mollik). Words keep their source capitalization and are counted
 * across entries, so a repeated surname — or a repeated vendor name across
 * app labels — ranks higher.
 *
 * Two things feed this: the phone's contacts ([ContactNames]) and the
 * labels of installed apps ([AppNames]). Both are lists of short
 * human-written names whose individual words people type, so they index
 * identically; only the source and the weight differ.
 *
 * Built from a one-off query; nothing is persisted.
 */
class NameIndex private constructor(
    private val words: Map<String, Word>,
    private val nextMap: Map<String, List<String>>,
) {

    private data class Word(val display: String, val count: Int)

    val isEmpty: Boolean get() = words.isEmpty()

    /** True when [word] (lowercase) is part of some indexed name. */
    fun contains(word: String): Boolean = words.containsKey(word)

    /** Every indexed word, lowercase — the searchable key set. */
    val wordKeys: Set<String> get() = words.keys

    /** The source capitalization of an indexed [word] (lowercase), or null. */
    fun displayOf(word: String): String? = words[word]?.display

    /** Indexed words starting with [prefix] (lowercase), most common first. */
    fun complete(prefix: String, limit: Int): List<Suggestion> {
        if (prefix.isEmpty()) return emptyList()
        return words.entries
            .asSequence()
            .filter { it.key.startsWith(prefix) }
            .map { Suggestion(it.value.display, it.value.count) }
            .sortedByDescending { it.frequency }
            .take(limit)
            .toList()
    }

    /** Words that follow [previous] (lowercase) within an indexed name. */
    fun nextWords(previous: String): List<String> = nextMap[previous].orEmpty()

    companion object {
        val EMPTY = NameIndex(emptyMap(), emptyMap())

        private val SEPARATORS = Regex("[\\s,.()\\[\\]/_-]+")

        fun fromNames(names: Iterable<String>): NameIndex {
            val words = HashMap<String, Word>()
            val next = HashMap<String, HashMap<String, Word>>()
            for (name in names) {
                val parts = name.split(SEPARATORS)
                    .map { it.trim { c -> !c.isLetter() } }
                    .filter { part -> part.length >= 2 && part.any { it.isLetter() } }
                var previous: String? = null
                for (part in parts) {
                    val key = part.lowercase()
                    words.merge(key, Word(part, 1)) { old, _ -> Word(old.display, old.count + 1) }
                    previous?.let { prev ->
                        next.getOrPut(prev) { HashMap() }
                            .merge(key, Word(part, 1)) { old, _ -> Word(old.display, old.count + 1) }
                    }
                    previous = key
                }
            }
            return NameIndex(
                words,
                next.mapValues { (_, followers) ->
                    followers.values.sortedByDescending { it.count }.map { it.display }
                },
            )
        }
    }
}

/** Name words harvested from the phone's contacts. */
typealias ContactNames = NameIndex

/** Name words harvested from the labels of installed apps. */
typealias AppNames = NameIndex
