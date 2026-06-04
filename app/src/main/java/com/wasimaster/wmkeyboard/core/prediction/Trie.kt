package com.wasimaster.wmkeyboard.core.prediction

/**
 * Frequency-weighted prefix trie used for word completion.
 *
 * Lookup walks the prefix, then collects the highest-frequency words in the
 * subtree. The trie is built once from the bundled word list and mutated at
 * runtime as the user's personal words are learned.
 */
class Trie {

    private class Node {
        val children = HashMap<Char, Node>()
        var frequency = 0
        var isWord = false
    }

    private val root = Node()

    fun insert(word: String, frequency: Int) {
        if (word.isEmpty()) return
        var node = root
        for (ch in word) {
            node = node.children.getOrPut(ch) { Node() }
        }
        node.isWord = true
        node.frequency = maxOf(node.frequency, frequency)
    }

    /** Bumps a word's frequency, inserting it if absent. */
    fun reinforce(word: String, boost: Int = 1) {
        if (word.isEmpty()) return
        var node = root
        for (ch in word) {
            node = node.children.getOrPut(ch) { Node() }
        }
        node.isWord = true
        node.frequency += boost
    }

    fun frequencyOf(word: String): Int {
        var node = root
        for (ch in word) {
            node = node.children[ch] ?: return 0
        }
        return if (node.isWord) node.frequency else 0
    }

    fun contains(word: String): Boolean = frequencyOf(word) > 0

    /** Returns up to [limit] completions of [prefix], best frequency first. */
    fun complete(prefix: String, limit: Int = 8): List<Suggestion> {
        if (prefix.isEmpty()) return emptyList()
        var node = root
        for (ch in prefix) {
            node = node.children[ch] ?: return emptyList()
        }
        val results = ArrayList<Suggestion>()
        collect(node, StringBuilder(prefix), results)
        results.sortByDescending { it.frequency }
        return results.take(limit)
    }

    private fun collect(node: Node, prefix: StringBuilder, out: MutableList<Suggestion>) {
        if (node.isWord) out.add(Suggestion(prefix.toString(), node.frequency))
        for ((ch, child) in node.children) {
            prefix.append(ch)
            collect(child, prefix, out)
            prefix.deleteCharAt(prefix.length - 1)
        }
    }
}

data class Suggestion(val word: String, val frequency: Int)
