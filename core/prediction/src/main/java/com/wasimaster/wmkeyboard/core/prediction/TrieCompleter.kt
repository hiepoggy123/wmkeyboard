package com.wasimaster.wmkeyboard.core.prediction

import java.util.PriorityQueue

/**
 * The one shared implementation of best-first prefix completion, expressed
 * over [TrieWalker] so [Trie], [PackedTrie] and [MappedTrie] stop maintaining
 * three parallel copies of the same branch-and-bound loop.
 *
 * Semantics are pinned by the characterization tests against the real
 * dictionary: only branches whose maxSubtree can still hold a top-[limit]
 * word are descended, and a completed word is emitted once nothing unopened
 * outranks it.
 */
internal object TrieCompleter {

    fun complete(walker: TrieWalker, prefix: String, limit: Int): List<Suggestion> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        var node = walker.root
        for (ch in prefix) {
            node = walker.child(node, ch)
            if (node < 0) return emptyList()
        }
        return from(walker, node, prefix, limit)
    }

    /**
     * The [limit] highest-frequency words in the whole trie. Same branch-and-
     * bound walk as [complete] with the root as its starting node — [complete]
     * itself refuses an empty prefix, and changing that would quietly turn
     * every `complete("")` in the codebase into a whole-dictionary scan.
     */
    fun top(walker: TrieWalker, limit: Int): List<Suggestion> =
        if (limit <= 0) emptyList() else from(walker, walker.root, "", limit)

    private fun from(walker: TrieWalker, node: Int, prefix: String, limit: Int): List<Suggestion> {
        val results = ArrayList<Suggestion>(limit)
        val heap = PriorityQueue<Frontier>(compareByDescending { it.priority })
        val children = ChildBuffer()
        heap.add(Frontier(node, prefix, walker.maxSubtree(node)))
        while (heap.isNotEmpty() && results.size < limit) {
            val f = heap.poll() ?: break
            if (f.node < 0) {
                // A completed word: priority is its exact frequency, and no
                // unopened branch outranks it, so it is safe to emit now.
                results.add(Suggestion(f.text, f.priority))
            } else {
                if (walker.isWord(f.node)) {
                    heap.add(Frontier(-1, f.text, walker.frequency(f.node)))
                }
                val count = walker.childrenInto(f.node, children)
                for (i in 0 until count) {
                    val child = children.nodes[i]
                    heap.add(Frontier(child, f.text + children.labels[i], walker.maxSubtree(child)))
                }
            }
        }
        return results
    }

    /** A heap entry: a subtree to expand ([node] >= 0) or a word to emit ([node] < 0). */
    private class Frontier(val node: Int, val text: String, val priority: Int)
}
