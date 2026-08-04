package com.wasimaster.wmkeyboard.core.prediction

import kotlin.math.ln

/**
 * Trie-guided fuzzy search: corrections and completions in one best-first
 * walk. Instead of generating every edit of the typed word and probing the
 * dictionary (Norvig generate-and-test), the walk descends the trie itself,
 * so only dictionary-reachable strings are ever considered — which makes
 * two-edit corrections affordable, corrects mid-word prefixes while
 * completing them, and draws substitution candidates from the trie's actual
 * edge labels (any script, not just ASCII).
 *
 * Scoring is log-space: `score = sourceLogWeight + ln(1 + frequency) - editCost`.
 * Edit penalties are the negative logs of the legacy multiplicative weights,
 * so relative ranking matches the edits-1 engine it replaces. A word reachable
 * several ways keeps its best score (max-merge, matching the engine's
 * historical maxOf semantics).
 *
 * The expansion order is admissible best-first: a state's bound is
 * `sourceLogWeight + ln(1 + maxSubtree(node)) - cost`, which only ever
 * overestimates, so pruning against the current K-th best emitted score never
 * loses a top-K word.
 */
class FuzzyBeamSearch {

    /** One weighted trie participating in a search. */
    class WalkSource(
        val walker: TrieWalker,
        val logWeight: Double,
        val tier: Tier,
    )

    enum class Tier { DICTIONARY, USER }

    class ScoredCandidate(
        val word: String,
        val score: Double,
        val editCost: Double,
        val edits: Int,
        val tier: Tier,
    )

    fun search(
        sources: List<WalkSource>,
        typed: CharSequence,
        proximity: KeyProximity,
        limit: Int,
        workspace: BeamWorkspace,
        maxEdits: Int = defaultMaxEdits(typed.length),
    ): List<ScoredCandidate> {
        if (typed.isEmpty() || limit <= 0) return emptyList()
        val k = maxOf(limit * 2, AUTOCORRECT_K)
        val results = HashMap<String, ScoredCandidate>()
        var floor = Double.NEGATIVE_INFINITY

        // Heaviest source first: its emissions raise the floor early, letting
        // lighter sources terminate after a handful of expansions.
        val ordered = sources.sortedByDescending {
            it.logWeight + ln1p(it.walker.maxSubtree(it.walker.root))
        }
        for (src in ordered) {
            val rootBound = src.logWeight + ln1p(src.walker.maxSubtree(src.walker.root))
            if (rootBound < floor - EPS) continue
            floor = searchOne(src, typed, proximity, maxEdits, k, results, floor, workspace)
        }

        return results.values.sortedWith(
            compareByDescending<ScoredCandidate> { it.score }.thenBy { it.word }
        ).take(k)
    }

    @Suppress("LongParameterList")
    private fun searchOne(
        src: WalkSource,
        typed: CharSequence,
        proximity: KeyProximity,
        maxEdits: Int,
        k: Int,
        results: HashMap<String, ScoredCandidate>,
        floorIn: Double,
        ws: BeamWorkspace,
    ): Double {
        var floor = floorIn
        val walker = src.walker
        val n = typed.length
        ws.reset()
        ws.pushState(
            node = walker.root, pos = 0, cost = 0.0, edits = 0,
            parent = -1, viaLabel = BeamWorkspace.NO_LABEL,
            bound = src.logWeight + ln1p(walker.maxSubtree(walker.root)),
        )
        var pops = 0
        while (ws.heapSize > 0 && pops < MAX_POPS) {
            val s = ws.popBest()
            if (ws.bound[s] < floor - EPS) break
            pops++
            val node = ws.node[s]
            val pos = ws.pos[s].toInt()
            val cost = ws.cost[s]
            val edits = ws.edits[s].toInt()

            if (pos == n) {
                if (walker.isWord(node)) {
                    val score = src.logWeight + ln1p(walker.frequency(node)) - cost
                    if (score > floor - EPS || results.size < k) {
                        emit(ws.materialize(s), score, cost, edits, src.tier, results)
                        if (results.size >= k) floor = kthBest(results, k)
                    }
                }
                // Completion: descend freely at no cost; the bound (maxSubtree)
                // steers toward the best words, exactly like classic complete().
                val count = walker.childrenInto(node, ws.children)
                for (i in 0 until count) {
                    val child = ws.children.nodes[i]
                    pushIfViable(
                        ws, src, walker, floor,
                        node = child, pos = n, cost = cost + COMPLETION_STEP,
                        edits = edits, parent = s, viaLabel = ws.children.labels[i],
                    )
                }
                continue
            }

            val expected = typed[pos]
            // Exact match of the next typed char.
            val matched = walker.child(node, expected)
            if (matched >= 0) {
                pushIfViable(
                    ws, src, walker, floor,
                    node = matched, pos = pos + 1, cost = cost,
                    edits = edits, parent = s, viaLabel = expected,
                )
            }

            if (edits < maxEdits && cost < MAX_EDIT_COST) {
                // Deletion: the typed char was an extra keypress — skip it.
                if (cost + COST_DELETION <= MAX_EDIT_COST) {
                    pushIfViable(
                        ws, src, walker, floor,
                        node = node, pos = pos + 1, cost = cost + COST_DELETION,
                        edits = edits + 1, parent = s, viaLabel = BeamWorkspace.NO_LABEL,
                    )
                }
                // Substitution and insertion candidates come from the node's
                // actual children — any script the dictionary holds.
                val count = walker.childrenInto(node, ws.children)
                for (i in 0 until count) {
                    val label = ws.children.labels[i]
                    val child = ws.children.nodes[i]
                    if (label != expected) {
                        val subCost = if (proximity.areAdjacent(expected, label)) {
                            COST_SUB_ADJACENT
                        } else {
                            COST_SUB_FAR
                        }
                        if (cost + subCost <= MAX_EDIT_COST) {
                            pushIfViable(
                                ws, src, walker, floor,
                                node = child, pos = pos + 1, cost = cost + subCost,
                                edits = edits + 1, parent = s, viaLabel = label,
                            )
                        }
                    }
                    // Insertion: the intended word has [label] here and the
                    // typed text missed it — consume the edge, hold position.
                    val adjacentToTyped = proximity.areAdjacent(expected, label) ||
                        (pos > 0 && proximity.areAdjacent(typed[pos - 1], label))
                    val insCost = if (adjacentToTyped) COST_INSERT_ADJACENT else COST_INSERT_FAR
                    if (cost + insCost <= MAX_EDIT_COST) {
                        pushIfViable(
                            ws, src, walker, floor,
                            node = child, pos = pos, cost = cost + insCost,
                            edits = edits + 1, parent = s, viaLabel = label,
                        )
                    }
                }
                // Transposition of the next two typed chars.
                if (pos + 1 < n && typed[pos] != typed[pos + 1] &&
                    cost + COST_TRANSPOSITION <= MAX_EDIT_COST
                ) {
                    val first = walker.child(node, typed[pos + 1])
                    if (first >= 0) {
                        val second = walker.child(first, typed[pos])
                        if (second >= 0) {
                            // Intermediate record links the first edge's label
                            // into the parent chain; it never enters the heap.
                            val link = ws.pushRecord(
                                node = first, pos = pos + 1, cost = cost,
                                edits = edits, parent = s, viaLabel = typed[pos + 1],
                            )
                            pushIfViable(
                                ws, src, walker, floor,
                                node = second, pos = pos + 2, cost = cost + COST_TRANSPOSITION,
                                edits = edits + 1, parent = link, viaLabel = typed[pos],
                            )
                        }
                    }
                }
            }
        }
        return floor
    }

    @Suppress("LongParameterList")
    private fun pushIfViable(
        ws: BeamWorkspace,
        src: WalkSource,
        walker: TrieWalker,
        floor: Double,
        node: Int,
        pos: Int,
        cost: Double,
        edits: Int,
        parent: Int,
        viaLabel: Char,
    ) {
        val bound = src.logWeight + ln1p(walker.maxSubtree(node)) - cost
        if (bound < floor - EPS) return
        ws.pushState(node, pos, cost, edits, parent, viaLabel, bound)
    }

    private fun emit(
        word: String,
        score: Double,
        editCost: Double,
        edits: Int,
        tier: Tier,
        results: HashMap<String, ScoredCandidate>,
    ) {
        val existing = results[word]
        if (existing == null || score > existing.score) {
            results[word] = ScoredCandidate(word, score, editCost, edits, tier)
        }
    }

    private fun kthBest(results: HashMap<String, ScoredCandidate>, k: Int): Double {
        if (results.size < k) return Double.NEGATIVE_INFINITY
        val scores = DoubleArray(results.size)
        var i = 0
        for (c in results.values) scores[i++] = c.score
        scores.sort()
        return scores[scores.size - k]
    }

    companion object {
        /** -ln of the legacy multiplicative edit weights (SuggestionEngine),
         * kept exact so ranking is precisely isomorphic to the edits-1 engine. */
        val COST_TRANSPOSITION = -ln(0.9)
        val COST_SUB_ADJACENT = -ln(0.9)
        val COST_DELETION = -ln(0.7)
        val COST_INSERT_ADJACENT = -ln(0.7)
        val COST_INSERT_FAR = -ln(0.25)
        val COST_SUB_FAR = -ln(0.2)
        const val COMPLETION_STEP = 0.0

        /** Two far substitutions can never survive; two adjacent slips can. */
        const val MAX_EDIT_COST = 2.0

        /** Autocorrect wants a deeper ranked list than the strip shows. */
        const val AUTOCORRECT_K = 8

        /** Runaway-state backstop; floor pruning ends healthy walks long before. */
        const val MAX_POPS = 4096

        private const val EPS = 1e-9

        fun defaultMaxEdits(typedLength: Int): Int = if (typedLength >= 5) 2 else 1

        private fun ln1p(v: Int): Double = ln(1.0 + v)
    }
}

/**
 * Pooled parallel-array state store + index heap for [FuzzyBeamSearch].
 * One instance per thread (the engine keeps them in a ThreadLocal): the
 * search allocates nothing per keystroke beyond emitted words.
 */
class BeamWorkspace(initialCapacity: Int = 256) {

    var node = IntArray(initialCapacity); private set
    var pos = ShortArray(initialCapacity); private set
    var cost = DoubleArray(initialCapacity); private set
    var edits = ByteArray(initialCapacity); private set
    var parent = IntArray(initialCapacity); private set
    var viaLabel = CharArray(initialCapacity); private set
    var bound = DoubleArray(initialCapacity); private set
    private var size = 0

    private var heap = IntArray(initialCapacity)
    var heapSize = 0; private set

    val children = ChildBuffer()
    private val sb = StringBuilder(24)

    fun reset() {
        size = 0
        heapSize = 0
    }

    /** Appends a state record without scheduling it for expansion. */
    @Suppress("LongParameterList")
    fun pushRecord(node: Int, pos: Int, cost: Double, edits: Int, parent: Int, viaLabel: Char): Int {
        ensure(size + 1)
        val id = size++
        this.node[id] = node
        this.pos[id] = pos.toShort()
        this.cost[id] = cost
        this.edits[id] = edits.toByte()
        this.parent[id] = parent
        this.viaLabel[id] = viaLabel
        this.bound[id] = 0.0
        return id
    }

    @Suppress("LongParameterList")
    fun pushState(node: Int, pos: Int, cost: Double, edits: Int, parent: Int, viaLabel: Char, bound: Double): Int {
        val id = pushRecord(node, pos, cost, edits, parent, viaLabel)
        this.bound[id] = bound
        heapPush(id)
        return id
    }

    fun popBest(): Int {
        val top = heap[0]
        heapSize--
        if (heapSize > 0) {
            heap[0] = heap[heapSize]
            siftDown(0)
        }
        return top
    }

    /** Rebuilds the word for state [s] from its parent chain. */
    fun materialize(s: Int): String {
        sb.setLength(0)
        var cur = s
        while (cur >= 0) {
            val label = viaLabel[cur]
            if (label != NO_LABEL) sb.append(label)
            cur = parent[cur]
        }
        sb.reverse()
        return sb.toString()
    }

    private fun ensure(needed: Int) {
        if (node.size >= needed) return
        var capacity = node.size
        while (capacity < needed) capacity *= 2
        node = node.copyOf(capacity)
        pos = pos.copyOf(capacity)
        cost = cost.copyOf(capacity)
        edits = edits.copyOf(capacity)
        parent = parent.copyOf(capacity)
        viaLabel = viaLabel.copyOf(capacity)
        bound = bound.copyOf(capacity)
    }

    private fun heapPush(id: Int) {
        if (heapSize == heap.size) heap = heap.copyOf(heap.size * 2)
        heap[heapSize] = id
        var i = heapSize++
        while (i > 0) {
            val up = (i - 1) ushr 1
            if (bound[heap[up]] >= bound[heap[i]]) break
            val tmp = heap[up]
            heap[up] = heap[i]
            heap[i] = tmp
            i = up
        }
    }

    private fun siftDown(start: Int) {
        var i = start
        while (true) {
            val left = i * 2 + 1
            if (left >= heapSize) break
            val right = left + 1
            var best = left
            if (right < heapSize && bound[heap[right]] > bound[heap[left]]) best = right
            if (bound[heap[i]] >= bound[heap[best]]) break
            val tmp = heap[i]
            heap[i] = heap[best]
            heap[best] = tmp
            i = best
        }
    }

    companion object {
        /** Sentinel for "this state consumed no trie edge" (deletion links).
         * NUL can never be a dictionary edge label. */
        val NO_LABEL: Char = 0.toChar()
    }
}
