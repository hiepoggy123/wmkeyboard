package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.prediction.Suggestion
import kotlin.math.ln
import kotlin.math.sqrt

/** A sampled touch point of a gesture, in keyboard-view pixel coordinates. */
data class GesturePoint(val x: Float, val y: Float)

/** Centre of one letter key, in the same coordinate space as [GesturePoint]. */
data class KeyCenter(val char: Char, val x: Float, val y: Float)

/**
 * SHARK²-style gesture (swipe) typing decoder.
 *
 * Candidates are pruned by three cheap filters — the gesture must start
 * near the word's first key and end near its last, and the ideal path
 * length must be comparable to the drawn one — then ranked by a weighted
 * sum of shape distance (mean point-to-point distance between the drawn
 * path and the word's ideal key-to-key polyline, both resampled to
 * [SAMPLE_POINTS] equidistant points), start/end anchoring error, and a
 * log-frequency language prior.
 *
 * All distances are normalised by the key width so scores are independent
 * of screen density and keyboard size.
 */
class GestureDecoder(
    keys: List<KeyCenter>,
    private val keyWidth: Float,
) {

    companion object {
        private const val SAMPLE_POINTS = 32

        /** Max distance (in key widths) from gesture start/end to the first/last key. */
        private const val ANCHOR_RADIUS = 1.6f

        /** Ideal-vs-drawn path length ratio allowed through pruning. */
        private const val LENGTH_RATIO = 2.6f

        private const val SHAPE_WEIGHT = 1.0f
        private const val ANCHOR_WEIGHT = 0.55f
        private const val FREQUENCY_WEIGHT = 0.18f

        /** Words scoring worse than this (before the prior) are dropped. */
        private const val MAX_COST = 1.35f
    }

    private val centers: Map<Char, KeyCenter> = keys.associateBy { it.char.lowercaseChar() }

    /**
     * Decodes [path] against [lexicon] (word to frequency), returning up to
     * [limit] candidate words, best first. Empty when the gesture is too
     * short or nothing plausible matches.
     */
    fun decode(
        path: List<GesturePoint>,
        lexicon: List<Pair<String, Int>>,
        limit: Int = 4,
    ): List<Suggestion> {
        if (path.size < 3 || keyWidth <= 0f) return emptyList()
        val drawn = resample(path)
        val drawnLength = pathLength(path) / keyWidth
        if (drawnLength < 0.5f) return emptyList()

        val start = path.first()
        val end = path.last()
        val anchorLimit = ANCHOR_RADIUS * keyWidth

        // Keys close enough to the gesture's endpoints to anchor a word.
        // Checking a word's first/last letter against these sets is a much
        // cheaper reject than building its ideal path, and equivalent to
        // the anchor-distance pruning below (key centres are per-letter).
        val startChars = HashSet<Char>()
        val endChars = HashSet<Char>()
        for ((char, key) in centers) {
            if (distance(start.x, start.y, key.x, key.y) <= anchorLimit) startChars.add(char)
            if (distance(end.x, end.y, key.x, key.y) <= anchorLimit) endChars.add(char)
        }
        if (startChars.isEmpty() || endChars.isEmpty()) return emptyList()

        val scored = ArrayList<Suggestion>()
        for ((word, frequency) in lexicon) {
            if (word.length < 2) continue
            if (word.first().lowercaseChar() !in startChars ||
                word.last().lowercaseChar() !in endChars
            ) {
                continue
            }
            val ideal = idealPath(word) ?: continue

            val first = ideal.first()
            val last = ideal.last()
            val startError = distance(start.x, start.y, first.x, first.y)
            val endError = distance(end.x, end.y, last.x, last.y)
            if (startError > anchorLimit || endError > anchorLimit) continue

            val idealLength = pathLength(ideal) / keyWidth
            val ratio = (idealLength + 0.4f) / (drawnLength + 0.4f)
            if (ratio > LENGTH_RATIO || ratio < 1f / LENGTH_RATIO) continue

            val shapeCost = shapeDistance(drawn, resample(ideal)) / keyWidth
            val anchorCost = (startError + endError) / (2f * keyWidth)
            val cost = SHAPE_WEIGHT * shapeCost + ANCHOR_WEIGHT * anchorCost
            if (cost > MAX_COST) continue

            val prior = FREQUENCY_WEIGHT * ln((frequency + 1).toFloat())
            // Reuse Suggestion; frequency carries a sortable score (scaled to int).
            scored.add(Suggestion(word, ((prior - cost) * 1000).toInt()))
        }
        scored.sortByDescending { it.frequency }
        return dedupe(scored).take(limit)
    }

    /** The polyline through the word's key centres, consecutive duplicates collapsed. */
    private fun idealPath(word: String): List<GesturePoint>? {
        val points = ArrayList<GesturePoint>(word.length)
        var previous: Char? = null
        for (ch in word.lowercase()) {
            if (ch == previous) continue // double letters share one key
            val key = centers[ch] ?: return null
            points.add(GesturePoint(key.x, key.y))
            previous = ch
        }
        return if (points.isEmpty()) null else points
    }

    /** Resamples a polyline to [SAMPLE_POINTS] equidistant points. */
    private fun resample(path: List<GesturePoint>): List<GesturePoint> {
        if (path.size == 1) return List(SAMPLE_POINTS) { path[0] }
        val total = pathLength(path)
        if (total == 0f) return List(SAMPLE_POINTS) { path[0] }
        val step = total / (SAMPLE_POINTS - 1)
        val out = ArrayList<GesturePoint>(SAMPLE_POINTS)
        out.add(path.first())
        var accumulated = 0f
        var index = 0
        var current = path[0]
        while (out.size < SAMPLE_POINTS - 1 && index < path.size - 1) {
            val next = path[index + 1]
            val segment = distance(current.x, current.y, next.x, next.y)
            if (accumulated + segment >= step && segment > 0f) {
                val t = (step - accumulated) / segment
                val point = GesturePoint(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y),
                )
                out.add(point)
                current = point
                accumulated = 0f
            } else {
                accumulated += segment
                current = next
                index++
            }
        }
        while (out.size < SAMPLE_POINTS) out.add(path.last())
        return out
    }

    /** Mean pointwise distance between two equally-sampled paths. */
    private fun shapeDistance(a: List<GesturePoint>, b: List<GesturePoint>): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += distance(a[i].x, a[i].y, b[i].x, b[i].y)
        }
        return sum / a.size
    }

    private fun pathLength(path: List<GesturePoint>): Float {
        var length = 0f
        for (i in 1 until path.size) {
            length += distance(path[i - 1].x, path[i - 1].y, path[i].x, path[i].y)
        }
        return length
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun dedupe(list: List<Suggestion>): List<Suggestion> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it.word) }
    }
}
