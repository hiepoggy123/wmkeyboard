package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Glide decoder: a best-first lattice over the dictionary trie itself.
 *
 * The decoder it replaces scored a flat word list — build each candidate's ideal
 * polyline, resample it, compare. That is linear in the dictionary on every
 * preview event, which caps how large a word list can ship and forces the whole
 * lexicon to be materialized as strings in the IME process. Walking the trie
 * instead shares work across every word with a common prefix, never materializes
 * a word that is not emitted, and takes its candidates from whatever tries the
 * caller hands over — bundled, imported, personal, any script.
 *
 * **The alignment.** The drawn stroke is resampled to
 * [GlideWorkspace.SAMPLE_POINTS] samples spaced equally *by arc length*, which
 * is what makes the whole model work: the drawn distance between sample `i` and
 * sample `j` is exactly `(j - i)` steps, no measuring required. A word explains
 * the stroke by placing each of its letters on a sample, in increasing order,
 * with the first letter on the first sample and the last on the last. Placing
 * letter `m` on sample `j` costs two things:
 *
 *  - **where** — `d²/2σ²` for the distance from sample `j` to letter `m`'s key;
 *  - **how far** — how badly the stroke's own travel since the previous letter,
 *    `(j - i)` steps, disagrees with the distance between the two keys.
 *
 * The second term is what stops a decoder from reading `ho` off a stroke drawn
 * for `hello`: both letters sit exactly where they should, but the finger
 * travelled three times as far as `h`-to-`o` asks for. It is also what makes a
 * detour expensive without any notion of curvature — a loop between two letters
 * shows up as arc length that the key distance cannot account for.
 *
 * Because a prefix's column depends only on the letters spelled so far, every
 * word sharing that prefix shares the work. A beam state is `(trie node,
 * column)` and advancing one letter is one bounded pass over the column.
 *
 * **Why the bound is admissible.** Both terms are non-negative and the DP takes
 * a minimum over predecessors, so no extension of a prefix can finish below that
 * prefix column's lowest value. Expanding in order of
 * `logWeight + ln(1 + maxSubtree(node)) - shapeWeight * minCol` therefore never
 * discards a word that would have finished in the top K — the same argument
 * `FuzzyBeamSearch` makes for typing, with the alignment floor standing in for
 * the edit cost.
 *
 * Scores are in the same log space as the typing beam
 * (`logWeight + ln(1 + frequency) - cost`), so `FuzzyBeamSearch.WalkSource`
 * weights carry over unchanged and a caller can build one source list for both.
 */
class GlideBeam(private val tuning: Tuning = Tuning()) {

    /**
     * The decoder's weights, injectable so the harness can sweep them rather
     * than the author guessing. The defaults are what the sweep settled on; see
     * `GlideTuningSweepTest` for the surface they were picked off.
     */
    data class Tuning(
        /**
         * Sample scatter around the key the finger meant, in key widths. Wider
         * than the tap model's 0.5: a stroke passes *through* a key rather than
         * stopping on it.
         */
        val sigma: Float = 0.42f,
        /**
         * Ceiling on one sample's location cost. Without it a single sample
         * flung off by a dropped touch report outweighs the whole alignment.
         */
        val maxPointCost: Float = 9.0f,
        /**
         * Nats per key width of disagreement between how far the finger
         * travelled between two letters and how far apart their keys are. This
         * is the term that reads `hello` off a `hello` stroke rather than `ho`.
         */
        val gapWeight: Float = 2.0f,
        /** How far, in samples, a placement may sit from where the arc length
         * says it should before the search stops considering it. */
        val gapWindow: Float = 8f,
        /** Nats per unit of alignment cost — the dial that trades shape against
         * the language model. */
        val shapeWeight: Double = 2.5,
        /** Flat charge for a doubled letter, which the stroke cannot show. */
        val repeatCost: Float = 0.1f,
        /** How far the stroke's first/last sample may sit from the word's
         * first/last key, in key widths. */
        val anchorRadius: Float = 1.3f,
        /** How close the stroke must pass to a key for that key's subtree to be
         * worth walking at all, in key widths. */
        val nearRadius: Float = 1.5f,
    ) {
        val invTwoSigmaSq: Float get() = 1f / (2f * sigma * sigma)
        val anchorRadiusSq: Float get() = anchorRadius * anchorRadius
        val nearCost: Float get() = nearRadius * nearRadius * invTwoSigmaSq
    }

    class Candidate(
        val word: String,
        val score: Double,
        /** Total alignment cost: how badly the stroke had to be explained to
         * read this word off it. A confidence signal, low is good. */
        val shapeCost: Double,
        val tier: FuzzyBeamSearch.Tier,
    )

    /**
     * Decodes [path] (pixel coordinates, keys [keyWidth] wide) against [sources],
     * returning up to [limit] words best first. Empty when the stroke is too
     * short to be a gesture, or when nothing in the tries explains it.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun decode(
        path: List<GesturePoint>,
        keys: GlideKeyMap,
        keyWidth: Float,
        sources: List<FuzzyBeamSearch.WalkSource>,
        ws: GlideWorkspace,
        limit: Int = 4,
    ): List<Candidate> {
        if (path.size < MIN_SAMPLES || keyWidth <= 0f) return emptyList()
        if (keys.keyCount == 0 || sources.isEmpty() || limit <= 0) return emptyList()
        if (!resample(path, keyWidth, ws)) return emptyList()
        ws.prepareKeys(keys.keyCount)
        buildCosts(keys, ws)
        if (ws.arcStep <= 0f) return emptyList()

        val k = maxOf(limit * 2, RESULT_K)
        val results = HashMap<String, Candidate>()
        var floor = Double.NEGATIVE_INFINITY

        // Heaviest source first, so its emissions raise the floor before the
        // lighter ones start — the same ordering trick the typing beam uses.
        val ordered = sources.sortedByDescending {
            it.logWeight + ln1p(it.walker.maxSubtree(it.walker.root))
        }
        for (src in ordered) {
            val rootBound = src.logWeight + ln1p(src.walker.maxSubtree(src.walker.root))
            if (rootBound < floor - EPS) continue
            floor = searchOne(src, keys, ws, k, results, floor)
        }

        return results.values
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.word })
            .take(limit)
    }

    // ---- the walk ----

    @Suppress("LongParameterList", "CyclomaticComplexMethod", "NestedBlockDepth")
    private fun searchOne(
        src: FuzzyBeamSearch.WalkSource,
        keys: GlideKeyMap,
        ws: GlideWorkspace,
        k: Int,
        results: HashMap<String, Candidate>,
        floorIn: Double,
    ): Double {
        var floor = floorIn
        val walker = src.walker
        val n = GlideWorkspace.SAMPLE_POINTS
        val keyCount = keys.keyCount

        ws.reset()
        ws.push(
            node = walker.root, parent = -1, viaLabel = GlideWorkspace.NO_LABEL,
            lastKey = -1, length = 0, extra = 0f, floorCost = 0f,
            bound = src.logWeight + ln1p(walker.maxSubtree(walker.root)),
        )

        var pops = 0
        while (ws.heapSize > 0 && pops < MAX_POPS) {
            val s = ws.popBest()
            if (ws.bound[s] < floor - EPS) break
            pops++

            val node = ws.node[s]
            val length = ws.length[s].toInt()
            val extra = ws.extra[s]
            val lastKey = ws.lastKey[s]

            if (length >= MIN_WORD_LENGTH && walker.isWord(node) && ws.endKey[lastKey]) {
                // The last letter must land on the last sample: that is where
                // the finger lifted, and it is the end anchor the old decoder
                // had to apply as a separate filter.
                val total = ws.cols[ws.columnOf(s) + n - 1]
                if (total < GlideWorkspace.UNREACHABLE) {
                    val shape = total + extra
                    val score = src.logWeight + ln1p(walker.frequency(node)) -
                        tuning.shapeWeight * shape
                    if (score > floor - EPS || results.size < k) {
                        emit(ws.materialize(s), score, shape.toDouble(), src.tier, results)
                        if (results.size >= k) floor = kthBest(results, k)
                    }
                }
            }
            if (length >= MAX_WORD_LENGTH || length >= n) continue

            val count = walker.childrenInto(node, ws.children)
            for (i in 0 until count) {
                val label = ws.children.labels[i]
                val key = keys.keyIndex(label)
                // A character the grid cannot produce makes its whole subtree
                // unreachable; so does one the stroke never went near.
                if (key < 0 || key >= keyCount || !ws.nearKey[key]) continue
                if (length == 0 && !ws.startKey[key]) continue

                val child = ws.children.nodes[i]
                val subtree = walker.maxSubtree(child)
                val repeat = length > 0 && label == ws.viaLabel[s]

                val minCol: Float
                var childExtra = extra
                if (repeat) {
                    // A doubled letter is one key visited once, so the stroke
                    // holds no evidence of it at all: the alignment carries
                    // over untouched and the repeat pays a flat charge instead.
                    // Without the charge every doubled spelling would shadow
                    // its single one for free.
                    minCol = ws.floorCost[s]
                    childExtra += tuning.repeatCost
                } else {
                    minCol = advance(
                        ws,
                        prevColumn = if (length == 0) -1 else ws.columnOf(s),
                        key = key,
                        prevKey = lastKey,
                        keys = keys,
                    )
                    if (minCol >= GlideWorkspace.UNREACHABLE) continue
                }

                val bound = src.logWeight + ln1p(subtree) -
                    tuning.shapeWeight * (minCol + childExtra)
                if (bound < floor - EPS) continue

                val id = ws.push(
                    node = child, parent = s, viaLabel = label, lastKey = key,
                    length = length + 1, extra = childExtra, floorCost = minCol, bound = bound,
                )
                if (id < 0) break // pool saturated; best-first says the rest are worse
                if (repeat) ws.copyColumn(s, id) else ws.storeScratch(id)
            }
        }
        return floor
    }

    /**
     * Extends the alignment by one letter sitting on [key], writing the new
     * column into [GlideWorkspace.scratch] and returning its lowest value.
     *
     * [prevColumn] is the parent's column offset, or -1 for the word's first
     * letter, which is anchored to the stroke's first sample: that is where the
     * finger went down, so there is nothing to search over.
     *
     * For every later letter the DP asks, for each sample `j`, which earlier
     * sample `i` the previous letter sat on. Only a band of `i` is worth
     * checking. The stroke's travel between the two samples is `(j - i)` steps
     * of known length, the two keys are a known distance apart, and the two
     * should agree; [GAP_WINDOW] bounds how far they may disagree before the
     * placement is not worth scoring at all. That band is what keeps this a
     * bounded pass rather than the quadratic scan the exact formulation asks
     * for, and it costs nothing real — a placement outside it carries a gap
     * penalty no word recovers from.
     */
    private fun advance(
        ws: GlideWorkspace,
        prevColumn: Int,
        key: Int,
        prevKey: Int,
        keys: GlideKeyMap,
    ): Float {
        val n = GlideWorkspace.SAMPLE_POINTS
        val keyCount = keys.keyCount
        val cost = ws.pointCost
        val out = ws.scratch

        if (prevColumn < 0) {
            out[0] = cost[key]
            for (j in 1 until n) out[j] = GlideWorkspace.UNREACHABLE
            return out[0]
        }

        val prev = ws.cols
        val step = ws.arcStep
        val span = keys.distance(prevKey, key)
        // Samples the stroke should have travelled between the two letters.
        val expected = span / step
        val lo = maxOf(1, (expected - tuning.gapWindow).toInt())
        val hi = maxOf(lo, (expected + tuning.gapWindow).toInt() + 1)

        out[0] = GlideWorkspace.UNREACHABLE
        var best = GlideWorkspace.UNREACHABLE
        for (j in 1 until n) {
            val from = maxOf(0, j - hi)
            val to = j - lo
            var bestPrev = GlideWorkspace.UNREACHABLE
            var i = from
            while (i <= to) {
                val at = prev[prevColumn + i]
                if (at < GlideWorkspace.UNREACHABLE) {
                    val travelled = (j - i) * step
                    val gap = travelled - span
                    val v = at + tuning.gapWeight * (if (gap < 0f) -gap else gap)
                    if (v < bestPrev) bestPrev = v
                }
                i++
            }
            val value = if (bestPrev >= GlideWorkspace.UNREACHABLE) {
                GlideWorkspace.UNREACHABLE
            } else {
                bestPrev + cost[j * keyCount + key]
            }
            out[j] = value
            if (value < best) best = value
        }
        return best
    }

    // ---- per-decode preparation ----

    /**
     * Resamples [path] to [GlideWorkspace.SAMPLE_POINTS] equidistant samples in
     * key-width coordinates, interpolating the clock along with the position so
     * timing survives. False when the stroke is too short to be a swipe at all.
     */
    private fun resample(path: List<GesturePoint>, keyWidth: Float, ws: GlideWorkspace): Boolean {
        val n = GlideWorkspace.SAMPLE_POINTS
        var totalPx = 0f
        for (i in 1 until path.size) {
            totalPx += distance(path[i - 1], path[i])
        }
        if (totalPx / keyWidth < MIN_STROKE_LENGTH) return false

        val step = totalPx / (n - 1)
        ws.arcStep = step / keyWidth
        val first = path.first()
        ws.pathX[0] = first.x / keyWidth
        ws.pathY[0] = first.y / keyWidth
        ws.pathT[0] = first.t

        var out = 1
        var index = 0
        var cx = first.x
        var cy = first.y
        var ct = first.t
        var accumulated = 0f
        while (out < n - 1 && index < path.size - 1) {
            val next = path[index + 1]
            val segment = sqrt((next.x - cx) * (next.x - cx) + (next.y - cy) * (next.y - cy))
            if (accumulated + segment >= step && segment > 0f) {
                val fraction = (step - accumulated) / segment
                cx += fraction * (next.x - cx)
                cy += fraction * (next.y - cy)
                ct += ((next.t - ct) * fraction).toLong()
                ws.pathX[out] = cx / keyWidth
                ws.pathY[out] = cy / keyWidth
                ws.pathT[out] = ct
                out++
                accumulated = 0f
            } else {
                accumulated += segment
                cx = next.x
                cy = next.y
                ct = next.t
                index++
            }
        }
        val last = path.last()
        while (out < n) {
            ws.pathX[out] = last.x / keyWidth
            ws.pathY[out] = last.y / keyWidth
            ws.pathT[out] = last.t
            out++
        }
        return true
    }

    /**
     * Fills the per-sample, per-key cost table and the three key masks the walk
     * prunes with: keys the stroke passed near at all, keys it could have
     * started on, and keys it could have ended on.
     */
    private fun buildCosts(keys: GlideKeyMap, ws: GlideWorkspace) {
        val n = GlideWorkspace.SAMPLE_POINTS
        val keyCount = keys.keyCount
        val cost = ws.pointCost
        val invTwoSigmaSq = tuning.invTwoSigmaSq
        val maxPointCost = tuning.maxPointCost
        val nearCost = tuning.nearCost
        for (i in 0 until n) {
            val base = i * keyCount
            val px = ws.pathX[i]
            val py = ws.pathY[i]
            for (k in 0 until keyCount) {
                val dx = px - keys.keyX[k]
                val dy = py - keys.keyY[k]
                val scaled = (dx * dx + dy * dy) * invTwoSigmaSq
                cost[base + k] = if (scaled > maxPointCost) maxPointCost else scaled
                if (scaled <= nearCost) ws.nearKey[k] = true
            }
        }
        anchorMask(ws.pathX[0], ws.pathY[0], keys, ws.startKey)
        anchorMask(ws.pathX[n - 1], ws.pathY[n - 1], keys, ws.endKey)
    }

    private fun anchorMask(x: Float, y: Float, keys: GlideKeyMap, out: BooleanArray) {
        for (k in 0 until keys.keyCount) {
            val dx = x - keys.keyX[k]
            val dy = y - keys.keyY[k]
            out[k] = dx * dx + dy * dy <= tuning.anchorRadiusSq
        }
    }

    // ---- results ----

    private fun emit(
        word: String,
        score: Double,
        shapeCost: Double,
        tier: FuzzyBeamSearch.Tier,
        results: HashMap<String, Candidate>,
    ) {
        val existing = results[word]
        if (existing == null || score > existing.score) {
            results[word] = Candidate(word, score, shapeCost, tier)
        }
    }

    private fun kthBest(results: HashMap<String, Candidate>, k: Int): Double {
        if (results.size < k) return Double.NEGATIVE_INFINITY
        val scores = DoubleArray(results.size)
        var i = 0
        for (c in results.values) scores[i++] = c.score
        scores.sort()
        return scores[scores.size - k]
    }

    private fun distance(a: GesturePoint, b: GesturePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {

        /** Strokes shorter than this are taps, in key widths. */
        private const val MIN_STROKE_LENGTH = 0.5f

        private const val MIN_SAMPLES = 3
        private const val MIN_WORD_LENGTH = 2
        private const val MAX_WORD_LENGTH = 24

        /** Runaway backstop; floor pruning ends healthy walks far earlier. */
        const val MAX_POPS = 2000

        /** Rank depth kept internally, so reranking has something to reorder. */
        const val RESULT_K = 8

        private const val EPS = 1e-9

        private fun ln1p(v: Int): Double = ln(1.0 + v)
    }
}
