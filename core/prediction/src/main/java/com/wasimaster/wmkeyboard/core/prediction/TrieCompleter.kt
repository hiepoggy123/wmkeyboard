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

    /**
     * The best word under each edge leaving [prefix]'s node, keyed by the code
     * point that edge adds — "if the next key were X, the word would be Y".
     *
     * The octopus (discussion #102) needs a word per key, not a distribution
     * over letters, and one prefix descent followed by a top-1 walk per child
     * is far cheaper than a completion call per candidate letter: each of those
     * top-1 walks is a single descent down the highest-maxSubtree chain,
     * because branch-and-bound never opens a subtree that cannot hold the
     * winner.
     *
     * Surrogate pairs are followed a second level down and reported as whole
     * code points. [SuggestionEngine.nextLetterWeights] silently drops them —
     * a lone high surrogate is not `isLetter()` — which is exactly the bug this
     * must not inherit, since the key maps are code-point keyed.
     */
    fun bestPerNextCodePoint(walker: TrieWalker, prefix: String): Map<Int, Suggestion> {
        var node = walker.root
        for (ch in prefix) {
            node = walker.child(node, ch)
            if (node < 0) return emptyMap()
        }
        val out = LinkedHashMap<Int, Suggestion>()
        val edges = ChildBuffer()
        val count = walker.childrenInto(node, edges)
        // Copied out before the inner walks: `edges` is reused for the low
        // surrogates of a supplementary letter, and iterating a buffer another
        // call is refilling would silently walk the wrong subtrees.
        val labels = edges.labels.copyOf(count)
        val nodes = edges.nodes.copyOf(count)
        val low = ChildBuffer()
        for (i in 0 until count) {
            val label = labels[i]
            if (Character.isHighSurrogate(label)) {
                val lows = walker.childrenInto(nodes[i], low)
                val lowLabels = low.labels.copyOf(lows)
                val lowNodes = low.nodes.copyOf(lows)
                for (j in 0 until lows) {
                    if (!Character.isLowSurrogate(lowLabels[j])) continue
                    val text = prefix + label + lowLabels[j]
                    val best = from(walker, lowNodes[j], text, 1).firstOrNull() ?: continue
                    keepBetter(out, Character.toCodePoint(label, lowLabels[j]), best)
                }
            } else {
                val best = from(walker, nodes[i], prefix + label, 1).firstOrNull() ?: continue
                keepBetter(out, label.code, best)
            }
        }
        return out
    }

    private fun keepBetter(out: MutableMap<Int, Suggestion>, codePoint: Int, found: Suggestion) {
        val current = out[codePoint]
        if (current == null || found.frequency > current.frequency) out[codePoint] = found
    }

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
