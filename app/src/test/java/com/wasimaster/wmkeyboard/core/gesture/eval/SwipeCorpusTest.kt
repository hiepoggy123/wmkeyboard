package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.prediction.eval.QwertyGeometry
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the swipe corpus's own contract.
 *
 * Every accuracy number in [GestureEvalTest] is only as trustworthy as the
 * paths it measures, and the decoder currently ignores most of what those paths
 * carry — nothing in the gate would notice if timing went missing or the
 * geometry drifted. These tests notice.
 */
class SwipeCorpusTest {

    private val typical = SwipeCorpus.Noise.TYPICAL.profile

    private fun swipe(word: String, profile: SwipeCorpus.Profile = typical): List<GesturePoint> =
        SwipeCorpus(42L).swipe(word, profile) ?: error("corpus could not draw '$word'")

    @Test
    fun `timestamps advance with the finger`() {
        val path = swipe("keyboard")
        for (i in 1 until path.size) {
            assertTrue(
                "sample $i went back in time (${path[i - 1].t} -> ${path[i].t})",
                path[i].t >= path[i - 1].t,
            )
        }
        assertTrue("stroke took no time at all", path.last().t > path.first().t)
    }

    @Test
    fun `the finger slows at the letters`() {
        // The whole point of the speed profile: samples bunch up where a letter
        // is and spread out between letters. A decoder that reads pivots depends
        // on this, so it is worth asserting rather than assuming.
        val word = "gesture"
        val path = swipe(word)
        val centres = word.toSet().mapNotNull { QwertyGeometry.keyFor(it) }

        var nearSum = 0.0
        var nearCount = 0
        var farSum = 0.0
        var farCount = 0
        for (i in 1 until path.size) {
            val dt = path[i].t - path[i - 1].t
            if (dt <= 0L) continue
            val step = distance(path[i - 1], path[i]) / SwipeCorpus.KEY_WIDTH
            val speed = step / dt
            val toKey = centres.minOf { key ->
                val dx = path[i].x / SwipeCorpus.KEY_WIDTH - key.x
                val dy = path[i].y / SwipeCorpus.KEY_WIDTH - key.y
                sqrt(dx * dx + dy * dy)
            }
            when {
                toKey < NEAR_KEY -> { nearSum += speed; nearCount++ }
                toKey > FAR_FROM_KEY -> { farSum += speed; farCount++ }
            }
        }

        assertTrue("no samples near a letter", nearCount > 0)
        assertTrue("no samples between letters", farCount > 0)
        assertTrue(
            "speed near a letter (${nearSum / nearCount}) was not below speed between them " +
                "(${farSum / farCount})",
            nearSum / nearCount < farSum / farCount,
        )
    }

    @Test
    fun `a dwelt doubled letter holds the finger still`() {
        // Not every case dwells (DOUBLE_DWELL_SHARE), so this looks for the
        // longest standstill across several draws of a doubled word and asserts
        // that at least one of them hesitated.
        val longest = (0 until 12).maxOf { seed ->
            val path = SwipeCorpus(seed.toLong()).swipe("letter", typical).orEmpty()
            longestStandstill(path)
        }
        assertTrue("no draw of 'letter' ever hesitated on the double ($longest)", longest >= 6)
    }

    @Test
    fun `a clean swipe passes over the word's letters in order`() {
        // Catches a coordinate-space or key-table drift: if this ever fails, the
        // corpus and the decoder disagree about where keys are, and every
        // accuracy number is measuring the disagreement.
        val word = "planet"
        val path = swipe(word, SwipeCorpus.Noise.CLEAN.profile)
        val visited = path.map {
            QwertyGeometry.nearestKey(it.x / SwipeCorpus.KEY_WIDTH, it.y / SwipeCorpus.KEY_WIDTH)
        }
        var at = 0
        for (key in visited) {
            if (at < word.length && key == word[at]) at++
        }
        assertEquals("only matched ${word.take(at)} of $word in $visited", word.length, at)
    }

    @Test
    fun `the corpus is a pure function of its seed`() {
        val a = SwipeCorpus(42L).swipe("reproducible", typical)
        val b = SwipeCorpus(42L).swipe("reproducible", typical)
        assertEquals(a, b)
    }

    @Test
    fun `noise makes the path wander further from the ideal`() {
        val word = "wander"
        val clean = deviationFromIdeal(word, swipe(word, SwipeCorpus.Noise.CLEAN.profile))
        val sloppy = deviationFromIdeal(word, swipe(word, SwipeCorpus.Noise.SLOPPY.profile))
        assertTrue("sloppy ($sloppy) drew no further off the ideal than clean ($clean)", sloppy > clean)
    }

    /**
     * Longest run of samples that never leave [STILL_PX] of where the run began.
     *
     * Comparing each sample to the one before it would not do: at a pivot the
     * finger is already crawling, so step-to-step it looks stationary for a
     * while even with no dwell at all. Measuring against the run's own anchor
     * separates a hesitation from a slow corner — creeping motion walks out of
     * the radius, a held finger does not.
     */
    private fun longestStandstill(path: List<GesturePoint>): Int {
        var best = 0
        var anchor = 0
        for (i in path.indices) {
            if (distance(path[anchor], path[i]) > STILL_PX) anchor = i
            val run = i - anchor + 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * Mean distance from the samples to the word's ideal key-centre polyline,
     * in key widths — how far off the "perfect" stroke this one was drawn.
     *
     * Distance to the path's own centre will not do: shrink pulls a sloppy
     * stroke *tighter* around its centre while pushing it further from the
     * ideal, so that measure moves the wrong way.
     */
    private fun deviationFromIdeal(word: String, path: List<GesturePoint>): Float {
        val ideal = word.toCharArray().distinctConsecutive()
            .mapNotNull { QwertyGeometry.keyFor(it) }
        return path.map { p ->
            val x = p.x / SwipeCorpus.KEY_WIDTH
            val y = p.y / SwipeCorpus.KEY_WIDTH
            (1 until ideal.size).minOf { i ->
                pointToSegment(x, y, ideal[i - 1].x, ideal[i - 1].y, ideal[i].x, ideal[i].y)
            }
        }.average().toFloat()
    }

    private fun CharArray.distinctConsecutive(): List<Char> {
        val out = ArrayList<Char>(size)
        for (c in this) if (out.lastOrNull() != c) out.add(c)
        return out
    }

    @Suppress("LongParameterList")
    private fun pointToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared <= 0f) {
            0f
        } else {
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        }
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    private fun distance(a: GesturePoint, b: GesturePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val NEAR_KEY = 0.3f
        const val FAR_FROM_KEY = 0.6f

        /**
         * A held finger still trembles. At TYPICAL the tremor's stationary σ is
         * 0.08 key widths (~5 px), so this sits just above the wander a dwell
         * produces and well below the ~6 px per sample of ordinary travel.
         */
        const val STILL_PX = 8f
    }
}
