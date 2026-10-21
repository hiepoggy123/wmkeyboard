package com.wasimaster.wmkeyboard.core.prediction.telex

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Laban Key style Vietnamese Trajectory Glide Decoder.
 *
 * Implements trajectory pivot extraction, curvature analysis, and monotonic point-to-path
 * template matching against the curated dictionary of Vietnamese unaccented stems and compounds.
 *
 * Designed specifically for the characteristics of Vietnamese typing:
 * - Extremely fast inflection point (pivot) and endpoint detection.
 * - Monotonic key alignment along the drawn stroke with corner-cutting tolerance.
 * - Seamless integration with [VietnameseGlide]'s N-gram language model and UserLexicon.
 */
object VietnameseTrajectoryGlide {

    private const val SAMPLE_COUNT = 36
    private const val MAX_ENDPOINT_DISTANCE = 1.35f
    private const val MAX_LENGTH_DISCREPANCY = 0.85f

    data class StemEntry(
        val stem: String,
        val freq: Int,
        val isCompound: Boolean,
        val isUser: Boolean = false,
    )

    // Stems indexed by (startChar shl 8) or endChar
    private val stemsByStartEnd = ConcurrentHashMap<Int, MutableList<StemEntry>>()
    private val allStemEntries = ConcurrentHashMap<String, StemEntry>()

    @Volatile
    private var isIndexed = false

    fun isReady(): Boolean = isIndexed && stemsByStartEnd.isNotEmpty()

    /**
     * Indexes the curated unaccented stems and frequencies from [VietnameseGlide].
     */
    fun indexStems(stems: Map<String, Int>, compoundStems: Set<String> = emptySet()) {
        stemsByStartEnd.clear()
        allStemEntries.clear()
        for ((stem, freq) in stems) {
            val clean = stem.lowercase().trim()
            if (clean.isEmpty()) continue
            val startCh = clean.first()
            val endCh = clean.last()
            if (startCh !in 'a'..'z' || endCh !in 'a'..'z') continue
            val isCompound = compoundStems.contains(clean) || clean.contains(' ') || clean.length > 8
            if (!isCompound && !VietnameseOrthography.isValidVietnamesePrefix(clean)) continue
            val entry = StemEntry(clean, freq.coerceIn(1, 255), isCompound, isUser = false)
            val key = (startCh.code shl 8) or endCh.code
            stemsByStartEnd.getOrPut(key) { ArrayList() }.add(entry)
            allStemEntries[clean] = entry
        }
        isIndexed = true
    }

    /**
     * Clears all dynamic user compound stems.
     */
    fun clearUserCompounds() {
        for ((_, list) in stemsByStartEnd) {
            synchronized(list) {
                list.removeAll { it.isUser }
            }
        }
        val userStemKeys = allStemEntries.entries.filter { it.value.isUser }.map { it.key }
        for (k in userStemKeys) {
            allStemEntries.remove(k)
        }
    }

    /**
     * Dynamically adds or updates user compound stems from UserLexicon.
     */
    fun addUserCompound(stem: String, freq: Int) {
        val clean = stem.lowercase().trim()
        if (clean.length < 2) return
        val startCh = clean.first()
        val endCh = clean.last()
        if (startCh !in 'a'..'z' || endCh !in 'a'..'z') return
        val key = (startCh.code shl 8) or endCh.code
        // User compounds are multi-word phrases already vetted by isKnownSyllable.
        val entry = StemEntry(clean, freq.coerceIn(50, 255), isCompound = true, isUser = true)
        val list = stemsByStartEnd.getOrPut(key) { ArrayList() }
        synchronized(list) {
            list.removeAll { it.stem == clean }
            list.add(0, entry)
        }
        allStemEntries[clean] = entry
    }

    /**
     * Decodes a gesture stroke directly against the indexed Vietnamese stems using
     * Laban Key style pivot trajectory template matching.
     */
    fun decode(
        path: List<GesturePoint>,
        keys: GlideKeyMap,
        keyWidth: Float,
        limit: Int = 16,
    ): List<GlideBeam.Candidate> {
        if (!isIndexed || path.size < 2 || keyWidth <= 0f) return emptyList()

        // 1. Resample path to equidistant points in key-width units
        val pts = resamplePath(path, keyWidth, SAMPLE_COUNT) ?: return emptyList()
        val pStart = pts.first()
        val pEnd = pts.last()

        // 2. Measure stroke travel length in key widths
        var strokeLength = 0f
        for (i in 0 until pts.size - 1) {
            strokeLength += dist(pts[i], pts[i + 1])
        }
        if (strokeLength < 0.45f) return emptyList()

        // Measure velocity and apply adaptive gesture scaling (AdaptGestureScale)
        val startTime = path.first().t
        val endTime = path.last().t
        val durationMs = if (endTime > startTime) (endTime - startTime).coerceIn(25L, 4000L) else 200L
        val velocity = strokeLength / (durationMs / 1000f) // key widths per second
        val velocityFactor = ((velocity - 5.0f) / 10.0f).coerceIn(0.0f, 0.40f)

        val maxEndpointDist = MAX_ENDPOINT_DISTANCE * (1.0f + velocityFactor * 0.25f)
        val maxLenDiscrepancy = MAX_LENGTH_DISCREPANCY * (1.0f + velocityFactor * 0.30f)

        // 3. Find candidate start and end keys (with adaptive search radius)
        val startCandidates = findNearbyKeys(pStart, keys, maxEndpointDist)
        val endCandidates = findNearbyKeys(pEnd, keys, maxEndpointDist)
        if (startCandidates.isEmpty() || endCandidates.isEmpty()) return emptyList()

        // 4. Detect stroke pivots (sharp directional changes and pauses)
        val pivots = detectPivots(pts)

        // 5. Evaluate candidate stems from matching (start, end) buckets
        val candidateScores = ArrayList<GlideBeam.Candidate>(limit * 2)
        val evaluatedStems = HashSet<String>()

        for ((startChar, startDist) in startCandidates) {
            for ((endChar, endDist) in endCandidates) {
                val bucketKey = (startChar.code shl 8) or endChar.code
                val stems = stemsByStartEnd[bucketKey] ?: continue

                // Fuzzy endpoint penalty: secondary nearby keys receive a distance penalty
                val startKeyPenalty = if (startDist > 0.5f) (startDist - 0.5f) * 0.4f else 0.0f
                val endKeyPenalty = if (endDist > 0.5f) (endDist - 0.5f) * 0.4f else 0.0f
                val secondaryPenalty = (startKeyPenalty + endKeyPenalty).toDouble()

                for (entry in stems) {
                    val stem = entry.stem
                    if (!evaluatedStems.add(stem)) continue

                    val baseScore = scoreTrajectory(
                        stem = stem,
                        pts = pts,
                        strokeLength = strokeLength,
                        pivots = pivots,
                        keys = keys,
                        entry = entry,
                        startDist = startDist,
                        endDist = endDist,
                        maxEndpointDist = maxEndpointDist,
                        maxLenDiscrepancy = maxLenDiscrepancy,
                        velocityFactor = velocityFactor,
                    ) ?: continue

                    val finalScore = baseScore - secondaryPenalty
                    val tier = if (entry.isUser) FuzzyBeamSearch.Tier.USER else FuzzyBeamSearch.Tier.DICTIONARY
                    candidateScores.add(GlideBeam.Candidate(stem, finalScore, 0.0, tier))
                }
            }
        }

        if (candidateScores.isEmpty()) return emptyList()

        return candidateScores
            .sortedWith(compareByDescending<GlideBeam.Candidate> { it.score }.thenBy { it.word })
            .distinctBy { it.word }
            .take(limit)
    }

    /**
     * Scores a candidate stem against the resampled stroke points.
     */
    private fun scoreTrajectory(
        stem: String,
        pts: List<Point2D>,
        strokeLength: Float,
        pivots: List<Point2D>,
        keys: GlideKeyMap,
        entry: StemEntry,
        startDist: Float,
        endDist: Float,
        maxEndpointDist: Float,
        maxLenDiscrepancy: Float,
        velocityFactor: Float,
    ): Double? {
        val len = stem.length
        if (len < 1) return null

        // Resolve all key coordinates for the stem
        val keyCoords = ArrayList<Point2D>(len)
        for (ch in stem) {
            if (ch == ' ' || ch == '-' || ch == '_') continue
            val idx = keys.keyIndex(ch)
            if (idx < 0) return null // layout cannot type this key
            keyCoords.add(Point2D(keys.keyX[idx], keys.keyY[idx]))
        }
        if (keyCoords.isEmpty()) return null

        val actualLen = keyCoords.size

        // 1. Endpoint match using adaptive distance
        val kStart = keyCoords.first()
        val kEnd = keyCoords.last()
        val dStart = dist(pts.first(), kStart)
        val dEnd = dist(pts.last(), kEnd)
        if (dStart > maxEndpointDist || dEnd > maxEndpointDist) return null

        // 2. Ideal polyline length
        var idealLength = 0f
        for (i in 0 until actualLen - 1) {
            idealLength += dist(keyCoords[i], keyCoords[i + 1])
        }

        val lenDiff = abs(strokeLength - idealLength) / maxOf(strokeLength, idealLength, 1.0f)
        if (actualLen >= 3 && lenDiff > maxLenDiscrepancy) return null

        // 3. Monotonic point-to-key matching
        var currentSampleIdx = 0
        var totalKeyCost = 2.0f * (dStart * dStart + dEnd * dEnd)
        val numSamples = pts.size

        for (r in 1 until actualLen - 1) {
            val targetKey = keyCoords[r]
            var bestDist = 999f
            var bestIdx = currentSampleIdx

            // Monotonic window
            val maxSearchIdx = minOf(numSamples - 1, numSamples - (actualLen - r - 1))
            for (j in currentSampleIdx..maxSearchIdx) {
                val d = dist(pts[j], targetKey)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = j
                }
            }

            // Key radius penalty: penalize missing the key heavily
            totalKeyCost += bestDist * bestDist
            currentSampleIdx = bestIdx
        }

        // Velocity-adaptive scale relaxation: corner-cutting naturally increases distance
        val effectiveKeyCost = if (velocityFactor > 0.0f) {
            totalKeyCost / (1.0f + velocityFactor * 0.35f)
        } else {
            totalKeyCost
        }

        // 4. Pivot alignment bonus (Laban Key characteristic with adaptive pivot radius)
        var pivotBonus = 0.0
        val pivotThreshold = 0.65f * (1.0f + velocityFactor * 0.25f)
        if (pivots.isNotEmpty()) {
            for (pivot in pivots) {
                var matched = false
                for (k in keyCoords) {
                    if (dist(pivot, k) < pivotThreshold) {
                        matched = true
                        break
                    }
                }
                if (matched) {
                    pivotBonus += 0.45
                }
            }
        }

        // 5. Total Shape Cost
        val shapeCost = effectiveKeyCost * 1.75f + lenDiff * 3.5f

        // 6. Language Model log-frequency term
        val freqBonus = ln(1.0 + entry.freq) * 0.85

        // Tier base bonus
        val tierBonus = if (entry.isUser) 2.8 else 1.4

        val finalScore = tierBonus + freqBonus + pivotBonus - shapeCost
        if (finalScore < -16.0) return null

        return finalScore
    }

    /**
     * Extracts inflection points / pivots along the trajectory where directional angle is sharp.
     */
    private fun detectPivots(pts: List<Point2D>): List<Point2D> {
        val pivots = ArrayList<Point2D>()
        if (pts.size < 5) return pivots

        val step = 2
        for (i in step until pts.size - step) {
            val pPrev = pts[i - step]
            val pCurr = pts[i]
            val pNext = pts[i + step]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)
            if (len1 < 0.05f || len2 < 0.05f) continue

            // Dot product to compute turning angle
            val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
            val clampedDot = dot.coerceIn(-1.0f, 1.0f)
            val angle = kotlin.math.acos(clampedDot)

            // Sharp turn >= 35 degrees (0.6 radians)
            if (angle >= 0.6f) {
                pivots.add(pCurr)
            }
        }
        return pivots
    }

    /**
     * Finds keys near a given point within maxDistance key widths.
     */
    private fun findNearbyKeys(
        point: Point2D,
        keys: GlideKeyMap,
        maxDistance: Float,
    ): List<Pair<Char, Float>> {
        val result = ArrayList<Pair<Char, Float>>()
        for (c in 'a'..'z') {
            val idx = keys.keyIndex(c)
            if (idx < 0) continue
            val kx = keys.keyX[idx]
            val ky = keys.keyY[idx]
            val d = dist(point.x, point.y, kx, ky)
            if (d <= maxDistance) {
                result.add(c to d)
            }
        }
        result.sortBy { it.second }
        return result.take(3)
    }

    /**
     * Resamples path evenly by arc-length to numPoints.
     */
    private fun resamplePath(
        path: List<GesturePoint>,
        keyWidth: Float,
        numPoints: Int,
    ): List<Point2D>? {
        if (path.size < 2 || keyWidth <= 0f) return null

        // Convert to key-width units and de-duplicate jitter
        val clean = ArrayList<Point2D>(path.size)
        var lastX = -999f
        var lastY = -999f
        for (p in path) {
            val kx = p.x / keyWidth
            val ky = p.y / keyWidth
            if (dist(kx, ky, lastX, lastY) > 0.04f) {
                clean.add(Point2D(kx, ky))
                lastX = kx
                lastY = ky
            }
        }
        if (clean.size < 2) return null

        var totalLen = 0f
        for (i in 0 until clean.size - 1) {
            totalLen += dist(clean[i], clean[i + 1])
        }
        if (totalLen <= 0f) return null

        val interval = totalLen / (numPoints - 1)
        val resampled = ArrayList<Point2D>(numPoints)
        resampled.add(clean.first())

        var accumulated = 0f
        var segStart = clean[0]
        var segIdx = 0

        while (resampled.size < numPoints - 1 && segIdx < clean.size - 1) {
            val segEnd = clean[segIdx + 1]
            val segLen = dist(segStart, segEnd)
            if (accumulated + segLen >= interval) {
                val remain = interval - accumulated
                val t = if (segLen > 0f) remain / segLen else 0f
                val nx = segStart.x + (segEnd.x - segStart.x) * t
                val ny = segStart.y + (segEnd.y - segStart.y) * t
                val np = Point2D(nx, ny)
                resampled.add(np)
                segStart = np
                accumulated = 0f
            } else {
                accumulated += segLen
                segIdx++
                if (segIdx < clean.size - 1) {
                    segStart = clean[segIdx]
                }
            }
        }

        while (resampled.size < numPoints) {
            resampled.add(clean.last())
        }

        return resampled
    }

    private fun dist(p1: Point2D, p2: Point2D): Float = dist(p1.x, p1.y, p2.x, p2.y)

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    fun resetForTesting() {
        stemsByStartEnd.clear()
        allStemEntries.clear()
        isIndexed = false
    }

    data class Point2D(val x: Float, val y: Float)
}
