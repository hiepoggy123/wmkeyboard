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

    /**
     * Per-keystroke touch evidence: the layout's key-center model plus the
     * tap position for each composing character (null where unknown —
     * hardware keys, pasted text, re-armed words).
     */
    class TouchScoring(val model: KeyTouchModel, val points: List<TouchPoint?>)

    class ScoredCandidate(
        val word: String,
        val score: Double,
        val editCost: Double,
        val edits: Int,
        /** Characters appended beyond the typed length by completion descent. */
        val completedChars: Int,
        val tier: Tier,
        /** Best score contributed by DICTIONARY-tier sources (NEGATIVE_INFINITY if none). */
        val dictScore: Double = Double.NEGATIVE_INFINITY,
        /** Best score contributed by USER-tier sources (NEGATIVE_INFINITY if none). */
        val userScore: Double = Double.NEGATIVE_INFINITY,
    )

    fun search(
        sources: List<WalkSource>,
        typed: CharSequence,
        proximity: KeyProximity,
        limit: Int,
        workspace: BeamWorkspace,
        maxEdits: Int = defaultMaxEdits(typed.length),
        touch: TouchScoring? = null,
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
            floor = searchOne(src, typed, proximity, maxEdits, k, results, floor, workspace, touch)
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
        touch: TouchScoring?,
    ): Double {
        var floor = floorIn
        val walker = src.walker
        val n = typed.length
        ws.reset()
        ws.pushState(
            node = walker.root, pos = 0, cost = 0.0, editSpend = 0.0, edits = 0, comp = 0,
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
            val editSpend = ws.editCost[s]
            val edits = ws.edits[s].toInt()
            val comp = ws.comp[s].toInt()

            if (pos == n) {
                if (walker.isWord(node)) {
                    val score = src.logWeight + ln1p(walker.frequency(node)) - cost
                    if (score > floor - EPS || results.size < k) {
                        emit(ws.materialize(s), score, editSpend, edits, comp, src.tier, results)
                        if (results.size >= k) floor = kthBest(results, k)
                    }
                }
                // Completion: descend at no cost on a clean prefix — the bound
                // (maxSubtree) steers toward the best words, exactly like
                // classic complete(). After an edit, each extra character is
                // charged like the old insert-at-end edit: an edited-then-
                // completed word is speculation stacked on speculation and
                // must not outrank the direct fix by raw frequency.
                val step = if (edits > 0) COST_COMPLETION_AFTER_EDIT else COMPLETION_STEP
                val count = walker.childrenInto(node, ws.children)
                for (i in 0 until count) {
                    val child = ws.children.nodes[i]
                    pushIfViable(
                        ws, src, walker, floor,
                        node = child, pos = n, cost = cost + step, editSpend = editSpend,
                        edits = edits, comp = comp + 1, parent = s,
                        viaLabel = ws.children.labels[i],
                    )
                }
                continue
            }

            val expected = typed[pos]
            // Exact match of the next typed char. With touch evidence, an
            // off-center tap makes even the "match" slightly expensive —
            // which is exactly what lets the neighbouring key's word win.
            val matched = walker.child(node, expected)
            if (matched >= 0) {
                pushIfViable(
                    ws, src, walker, floor,
                    node = matched, pos = pos + 1,
                    cost = cost + matchCost(touch, pos, expected),
                    editSpend = editSpend,
                    edits = edits, comp = comp, parent = s, viaLabel = expected,
                )
            }

            if (edits < maxEdits && editSpend < MAX_EDIT_COST) {
                // Budget gates compare base edit costs; the second-edit
                // surcharge and touch/match adjustments are ranking signal,
                // not budget spend.
                val surcharge = if (edits + 1 >= 2) SECOND_EDIT_SURCHARGE else 0.0
                // Deletion: the typed char was an extra keypress — skip it.
                if (editSpend + COST_DELETION <= MAX_EDIT_COST) {
                    pushIfViable(
                        ws, src, walker, floor,
                        node = node, pos = pos + 1, cost = cost + COST_DELETION + surcharge,
                        editSpend = editSpend + COST_DELETION,
                        edits = edits + 1, comp = comp, parent = s,
                        viaLabel = BeamWorkspace.NO_LABEL,
                    )
                }
                // Substitution and insertion candidates come from the node's
                // actual children — any script the dictionary holds.
                val count = walker.childrenInto(node, ws.children)
                for (i in 0 until count) {
                    val label = ws.children.labels[i]
                    val child = ws.children.nodes[i]
                    if (label != expected) {
                        val subCost = substitutionCost(touch, pos, expected, label, proximity)
                        if (editSpend + subCost <= MAX_EDIT_COST) {
                            pushIfViable(
                                ws, src, walker, floor,
                                node = child, pos = pos + 1, cost = cost + subCost + surcharge,
                                editSpend = editSpend + subCost,
                                edits = edits + 1, comp = comp, parent = s, viaLabel = label,
                            )
                        }
                    }
                    // Insertion: the intended word has [label] here and the
                    // typed text missed it — consume the edge, hold position.
                    val adjacentToTyped = proximity.areAdjacent(expected, label) ||
                        (pos > 0 && proximity.areAdjacent(typed[pos - 1], label))
                    val insCost = if (adjacentToTyped) COST_INSERT_ADJACENT else COST_INSERT_FAR
                    if (editSpend + insCost <= MAX_EDIT_COST) {
                        pushIfViable(
                            ws, src, walker, floor,
                            node = child, pos = pos, cost = cost + insCost + surcharge,
                            editSpend = editSpend + insCost,
                            edits = edits + 1, comp = comp, parent = s, viaLabel = label,
                        )
                    }
                }
                // Transposition of the next two typed chars.
                if (pos + 1 < n && typed[pos] != typed[pos + 1] &&
                    editSpend + COST_TRANSPOSITION <= MAX_EDIT_COST
                ) {
                    val first = walker.child(node, typed[pos + 1])
                    if (first >= 0) {
                        val second = walker.child(first, typed[pos])
                        if (second >= 0) {
                            // Intermediate record links the first edge's label
                            // into the parent chain; it never enters the heap.
                            val link = ws.pushRecord(
                                node = first, pos = pos + 1, cost = cost,
                                editSpend = editSpend, edits = edits, comp = comp,
                                parent = s, viaLabel = typed[pos + 1],
                            )
                            pushIfViable(
                                ws, src, walker, floor,
                                node = second, pos = pos + 2,
                                cost = cost + COST_TRANSPOSITION + surcharge,
                                editSpend = editSpend + COST_TRANSPOSITION,
                                edits = edits + 1, comp = comp, parent = link,
                                viaLabel = typed[pos],
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
        editSpend: Double,
        edits: Int,
        comp: Int,
        parent: Int,
        viaLabel: Char,
    ) {
        val bound = src.logWeight + ln1p(walker.maxSubtree(node)) - cost
        if (bound < floor - EPS) return
        ws.pushState(node, pos, cost, editSpend, edits, comp, parent, viaLabel, bound)
    }

    /**
     * Rank cost of consuming [expected] as itself. Zero without touch data;
     * with a tap position, the likelihood gap to the tap's best key (capped).
     */
    private fun matchCost(touch: TouchScoring?, pos: Int, expected: Char): Double {
        val p = touch?.points?.getOrNull(pos) ?: return 0.0
        if (!touch.model.knows(expected)) return 0.0
        val best = touch.model.bestKey(p) ?: return 0.0
        val gap = touch.model.logLikelihood(p, best) - touch.model.logLikelihood(p, expected)
        return gap.coerceIn(0.0, MATCH_CAP)
    }

    /**
     * Cost of assuming the user meant [label] where they typed [expected].
     * With a tap position, substituting toward the key the finger actually
     * landed near is nearly free; without one, the discrete adjacency weights.
     */
    private fun substitutionCost(
        touch: TouchScoring?,
        pos: Int,
        expected: Char,
        label: Char,
        proximity: KeyProximity,
    ): Double {
        val p = touch?.points?.getOrNull(pos)
        if (p == null || !touch.model.knows(expected) || !touch.model.knows(label)) {
            return if (proximity.areAdjacent(expected, label)) COST_SUB_ADJACENT else COST_SUB_FAR
        }
        val gap = touch.model.logLikelihood(p, expected) - touch.model.logLikelihood(p, label)
        return COST_SUB_ADJACENT + gap.coerceIn(0.0, COST_SUB_FAR - COST_SUB_ADJACENT)
    }

    private fun emit(
        word: String,
        score: Double,
        editCost: Double,
        edits: Int,
        completedChars: Int,
        tier: Tier,
        results: HashMap<String, ScoredCandidate>,
    ) {
        val existing = results[word]
        val dictScore = maxOf(
            existing?.dictScore ?: Double.NEGATIVE_INFINITY,
            if (tier == Tier.DICTIONARY) score else Double.NEGATIVE_INFINITY,
        )
        val userScore = maxOf(
            existing?.userScore ?: Double.NEGATIVE_INFINITY,
            if (tier == Tier.USER) score else Double.NEGATIVE_INFINITY,
        )
        results[word] = if (existing == null || score > existing.score) {
            ScoredCandidate(word, score, editCost, edits, completedChars, tier, dictScore, userScore)
        } else {
            ScoredCandidate(
                word, existing.score, existing.editCost, existing.edits,
                existing.completedChars, existing.tier, dictScore, userScore,
            )
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

        /** Per-character completion cost once the path holds an edit. As
         * expensive as a far insertion: an edited-then-completed word needs a
         * ~4x frequency advantage per extra character to outrank the direct
         * fix (otherwise "skills" buries "skill" for typed "skiml", and
         * deleted-prefix floods like "bwl" -> "bl" -> blue/black/blood push
         * the real fix out of the result set). */
        val COST_COMPLETION_AFTER_EDIT = -ln(0.25)

        /** Two far substitutions can never survive; two adjacent slips can.
         * Applies to the base edit costs, before [SECOND_EDIT_SURCHARGE]. */
        const val MAX_EDIT_COST = 2.0

        /**
         * Extra cost on the second edit: the legacy engine only ever reached
         * one edit, so a two-edit word must rank below any one-edit fix of
         * comparable frequency — it exists to catch typos nothing else
         * explains, not to outbid them. (Cheapest 2-edit total: ~2.21 nats,
         * above the most expensive single edit at 1.609.)
         */
        const val SECOND_EDIT_SURCHARGE = 2.0

        /** Autocorrect wants a deeper ranked list than the strip shows. */
        const val AUTOCORRECT_K = 8

        /** Cap on the rank cost an off-center tap adds to an exact match. */
        const val MATCH_CAP = 1.0

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
    var editCost = DoubleArray(initialCapacity); private set
    var edits = ByteArray(initialCapacity); private set
    var comp = ByteArray(initialCapacity); private set
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
    fun pushRecord(
        node: Int,
        pos: Int,
        cost: Double,
        editSpend: Double,
        edits: Int,
        comp: Int,
        parent: Int,
        viaLabel: Char,
    ): Int {
        ensure(size + 1)
        val id = size++
        this.node[id] = node
        this.pos[id] = pos.toShort()
        this.cost[id] = cost
        this.editCost[id] = editSpend
        this.edits[id] = edits.toByte()
        this.comp[id] = comp.toByte()
        this.parent[id] = parent
        this.viaLabel[id] = viaLabel
        this.bound[id] = 0.0
        return id
    }

    @Suppress("LongParameterList")
    fun pushState(
        node: Int,
        pos: Int,
        cost: Double,
        editSpend: Double,
        edits: Int,
        comp: Int,
        parent: Int,
        viaLabel: Char,
        bound: Double,
    ): Int {
        val id = pushRecord(node, pos, cost, editSpend, edits, comp, parent, viaLabel)
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
        editCost = editCost.copyOf(capacity)
        edits = edits.copyOf(capacity)
        comp = comp.copyOf(capacity)
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
