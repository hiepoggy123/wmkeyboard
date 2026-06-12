package com.wasimaster.wmkeyboard.core.prediction

/**
 * Name words harvested from the phone's contacts, so people's names
 * complete like dictionary words ("was" → Wasi) and chain like bigrams
 * ("Wasi" → Mollik). Words keep the contact's own capitalization and are
 * counted across contacts, so shared surnames rank higher.
 *
 * Built in memory from a one-off contacts query; nothing is persisted.
 */
class ContactNames private constructor(
    private val words: Map<String, Word>,
    private val nextMap: Map<String, List<String>>,
) {

    private data class Word(val display: String, val count: Int)

    val isEmpty: Boolean get() = words.isEmpty()

    /** True when [word] (lowercase) is part of some contact's name. */
    fun contains(word: String): Boolean = words.containsKey(word)

    /** Name words starting with [prefix] (lowercase), most common first. */
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

    /** Words that follow [previous] (lowercase) in contact names. */
    fun nextWords(previous: String): List<String> = nextMap[previous].orEmpty()

    companion object {
        val EMPTY = ContactNames(emptyMap(), emptyMap())

        private val SEPARATORS = Regex("[\\s,.()\\[\\]/_-]+")

        fun fromNames(names: Iterable<String>): ContactNames {
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
            return ContactNames(
                words,
                next.mapValues { (_, followers) ->
                    followers.values.sortedByDescending { it.count }.map { it.display }
                },
            )
        }
    }
}
