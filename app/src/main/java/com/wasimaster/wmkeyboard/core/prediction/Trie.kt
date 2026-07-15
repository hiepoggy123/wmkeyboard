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
        /** Highest word frequency anywhere in this node's subtree (incl. self). */
        var maxSubtreeFreq = 0
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
        bumpMaxSubtree(word, node.frequency)
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
        bumpMaxSubtree(word, node.frequency)
    }

    /** Raise maxSubtreeFreq to [freq] along [word]'s path so it stays a valid upper bound. */
    private fun bumpMaxSubtree(word: String, freq: Int) {
        var node = root
        root.maxSubtreeFreq = maxOf(root.maxSubtreeFreq, freq)
        for (ch in word) {
            node = node.children[ch] ?: return
            node.maxSubtreeFreq = maxOf(node.maxSubtreeFreq, freq)
        }
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
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        var node = root
        for (ch in prefix) {
            node = node.children[ch] ?: return emptyList()
        }
        // Best-first expansion: only descend branches that can still hold a
        // top-[limit] word, so cost is bounded near [limit] rather than the
        // whole prefix subtree (which is huge for a short common prefix).
        val results = ArrayList<Suggestion>(limit)
        val heap = java.util.PriorityQueue<Frontier>(compareByDescending { it.priority })
        heap.add(Frontier(node, prefix, node.maxSubtreeFreq))
        while (heap.isNotEmpty() && results.size < limit) {
            val f = heap.poll()
            if (f.node == null) {
                // A completed word: its priority is an exact frequency, and no
                // unopened branch outranks it, so it is safe to emit now.
                results.add(Suggestion(f.text, f.priority))
            } else {
                val n = f.node
                if (n.isWord) heap.add(Frontier(null, f.text, n.frequency))
                for ((ch, child) in n.children) {
                    heap.add(Frontier(child, f.text + ch, child.maxSubtreeFreq))
                }
            }
        }
        return results
    }

    /** A heap entry: a subtree to expand ([node] != null) or a word to emit. */
    private class Frontier(
        val node: Node?,
        val text: String,
        val priority: Int,
    )
}

data class Suggestion(val word: String, val frequency: Int)
