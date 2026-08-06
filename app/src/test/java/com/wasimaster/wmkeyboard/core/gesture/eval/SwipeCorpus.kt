package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.prediction.eval.QwertyGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Reproducible synthetic swipe corpus: the gesture-side counterpart of
 * `TypoCorpus`. A pure function of (seed, word list, noise level), so a metric
 * moving between two runs means the decoder changed and nothing else.
 *
 * A real swipe is not the word's key-centre polyline. Five things happen to it,
 * and all five are modelled here because each breaks a different part of a
 * shape decoder:
 *
 *  - **Corner cutting.** Fingers round corners instead of reaching the centre
 *    of every key, so the drawn path bulges inside the ideal one. This is the
 *    dominant error and the reason a plain point-to-point distance is weak.
 *  - **Endpoint slop.** The stroke starts a little after the first key and
 *    stops a little before (or past) the last, which is exactly what the
 *    decoder's start/end anchoring keys on.
 *  - **Offset bias.** A thumb consistently lands low and to one side, shifting
 *    the whole path by a constant.
 *  - **Scale shrink.** Lazy swipes cover less ground than the layout asks for,
 *    shrinking the path toward its own centre.
 *  - **Speed profile.** The finger slows into a turn and accelerates out of it,
 *    so samples bunch up at the letters and spread out between them.
 *
 * Sample timing comes from that speed profile, so [GesturePoint.t] carries the
 * signal a decoder would use to find pivots.
 *
 * **One assumption to keep honest:** at a doubled letter this corpus adds a
 * dwell for [DOUBLE_DWELL_SHARE] of cases, on the theory that people hesitate
 * where a letter repeats. That is a claim about human behaviour, not a measured
 * fact. Any gain a dwell-aware decoder shows against this corpus is therefore
 * an upper bound on the real one, and should be treated as such until it is
 * checked on a device.
 */
class SwipeCorpus(seed: Long = 42L, private val keyWidth: Float = KEY_WIDTH) {

    /** How badly the swipe is drawn. Each axis is independent, so a sweep can
     * vary one and hold the rest — see `SwipeNoiseSweepTest`. */
    data class Profile(
        /** How far inside the corner the finger cuts, in key widths. */
        val cornerRadius: Float,
        /** Hand tremor, stationary σ in key widths. Correlated across samples — see [Tremor]. */
        val jitter: Float,
        /** Per-case constant offset, σ in key widths. */
        val bias: Float,
        /** Per-case shrink toward the path's centre, σ as a fraction. */
        val shrink: Float,
        /** Per-case start/end under- or overshoot, σ in key widths. */
        val endSlop: Float,
        /** Fraction of base speed lost at a pivot; higher bunches samples harder. */
        val pivotSlowdown: Float,
    )

    /** The four graded levels the gate measures. Reported separately, never averaged away. */
    enum class Noise(val profile: Profile) {
        CLEAN(Profile(0.05f, 0.010f, 0.00f, 0.00f, 0.00f, 0.50f)),
        LIGHT(Profile(0.25f, 0.040f, 0.06f, 0.03f, 0.10f, 0.55f)),
        TYPICAL(Profile(0.40f, 0.080f, 0.12f, 0.07f, 0.20f, 0.60f)),
        SLOPPY(Profile(0.55f, 0.120f, 0.17f, 0.10f, 0.30f, 0.62f)),
    }

    data class Case(
        val intended: String,
        val path: List<GesturePoint>,
        val previous: String?,
        val noise: Noise,
    )

    private val random = Random(seed)

    /**
     * [count] cases at [noise], each a swipe of a frequency-sampled word.
     * Words the geometry cannot draw are skipped, so the result is always
     * exactly [count] long.
     */
    fun generate(
        entries: List<Pair<String, Int>>,
        noise: Noise,
        count: Int = 500,
    ): List<Case> = generate(entries, noise.profile, count, noise)

    /** As [generate], but at an arbitrary [profile] — for sweeps off the graded levels. */
    fun generate(
        entries: List<Pair<String, Int>>,
        profile: Profile,
        count: Int = 500,
        noise: Noise = Noise.TYPICAL,
    ): List<Case> {
        val sampler = FrequencySampler(entries.filter(::usable))
        val cases = ArrayList<Case>(count)
        while (cases.size < count) {
            val intended = sampler.sample(random)
            val path = swipe(intended, profile) ?: continue
            val previous = if (random.nextFloat() < 0.5f) sampler.sample(random) else null
            cases.add(Case(intended, path, previous, noise))
        }
        return cases
    }

    /**
     * The drawn path for [word] at [noise], in the pixel space of a keyboard
     * whose keys are [keyWidth] wide, or null when the word cannot be drawn
     * (a character off the grid, or a path too short to be a gesture at all).
     */
    fun swipe(word: String, profile: Profile): List<GesturePoint>? {
        val anchors = anchorsOf(word) ?: return null
        if (anchors.size < 2) return null
        val slopped = applyEndSlop(anchors, profile)
        val dense = densePath(slopped, profile.cornerRadius)
        if (dense.size < 2) return null
        val arc = cumulativeArc(dense)
        val total = arc.last()
        if (total <= 0f) return null

        val pivots = pivotArcs(dense, arc, slopped)
        val dwells = dwellsFor(word, pivots)
        val centre = centroid(slopped)
        val scale = 1f - kotlin.math.abs(gaussian()) * profile.shrink
        val biasX = gaussian() * profile.bias
        val biasY = gaussian() * profile.bias

        val tremor = Tremor(profile.jitter)
        val out = ArrayList<GesturePoint>(64)
        var travelled = 0f
        var clock = START_TIME_MS
        var nextDwell = 0
        while (travelled <= total) {
            val here = pointAt(dense, arc, travelled)
            out.add(emit(here, centre, scale, biasX, biasY, tremor.next(), clock))
            // A dwell is extra samples at a standstill, which is what the
            // digitizer reports while a finger hesitates on a key. The tremor
            // keeps running through it — a held finger still wanders.
            while (nextDwell < dwells.size && dwells[nextDwell].first <= travelled) {
                var held = 0L
                while (held < dwells[nextDwell].second) {
                    clock += SAMPLE_MS
                    held += SAMPLE_MS
                    out.add(emit(here, centre, scale, biasX, biasY, tremor.next(), clock))
                }
                nextDwell++
            }
            travelled += stepAt(travelled, pivots, profile.pivotSlowdown)
            clock += SAMPLE_MS
        }
        // The finger lifts where the stroke ends, not at whatever arc the last
        // fixed-size step happened to land on.
        val last = pointAt(dense, arc, total)
        out.add(emit(last, centre, scale, biasX, biasY, tremor.next(), clock))
        return if (out.size >= MIN_SAMPLES) out else null
    }

    // ---- geometry ----

    private class Pt(val x: Float, val y: Float)

    /** Key centres of the word's letters in key widths, consecutive repeats collapsed. */
    private fun anchorsOf(word: String): List<Pt>? {
        val out = ArrayList<Pt>(word.length)
        var previous: Char? = null
        for (ch in word) {
            if (ch == previous) continue
            val key = QwertyGeometry.keyFor(ch) ?: return null
            out.add(Pt(key.x, key.y))
            previous = ch
        }
        return out
    }

    /** Moves the first and last anchor along their own segment: start late, stop early or past. */
    private fun applyEndSlop(anchors: List<Pt>, profile: Profile): List<Pt> {
        if (profile.endSlop <= 0f) return anchors
        val out = ArrayList<Pt>(anchors)
        out[0] = shifted(anchors[0], anchors[1], gaussian() * profile.endSlop)
        val n = anchors.size
        out[n - 1] = shifted(anchors[n - 1], anchors[n - 2], gaussian() * profile.endSlop)
        return out
    }

    /** [from] moved [by] key widths toward [toward]. */
    private fun shifted(from: Pt, toward: Pt, by: Float): Pt {
        val dx = toward.x - from.x
        val dy = toward.y - from.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return from
        return Pt(from.x + dx / len * by, from.y + dy / len * by)
    }

    /**
     * The anchor polyline with every interior corner replaced by a quadratic
     * curve that cuts [radius] key widths off it, sampled finely enough that
     * walking it by arc length is accurate.
     */
    private fun densePath(anchors: List<Pt>, radius: Float): List<Pt> {
        val out = ArrayList<Pt>(anchors.size * DENSE_PER_CORNER)
        out.add(anchors[0])
        for (i in 1 until anchors.size - 1) {
            val r = cornerRadius(anchors, i, radius)
            val enter = shifted(anchors[i], anchors[i - 1], r)
            val exit = shifted(anchors[i], anchors[i + 1], r)
            addLine(out, out.last(), enter)
            addQuad(out, enter, anchors[i], exit)
        }
        addLine(out, out.last(), anchors.last())
        return out
    }

    /** Never cut more than [CORNER_CAP] of either adjoining segment, or the corner inverts. */
    private fun cornerRadius(anchors: List<Pt>, i: Int, radius: Float): Float {
        val back = distance(anchors[i], anchors[i - 1]) * CORNER_CAP
        val forward = distance(anchors[i], anchors[i + 1]) * CORNER_CAP
        return minOf(radius, back, forward)
    }

    private fun addLine(out: MutableList<Pt>, from: Pt, to: Pt) {
        val steps = (distance(from, to) / DENSE_STEP).toInt().coerceAtLeast(1)
        for (s in 1..steps) {
            val f = s / steps.toFloat()
            out.add(Pt(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f))
        }
    }

    private fun addQuad(out: MutableList<Pt>, from: Pt, control: Pt, to: Pt) {
        for (s in 1..DENSE_PER_CORNER) {
            val f = s / DENSE_PER_CORNER.toFloat()
            val g = 1f - f
            out.add(
                Pt(
                    g * g * from.x + 2f * g * f * control.x + f * f * to.x,
                    g * g * from.y + 2f * g * f * control.y + f * f * to.y,
                ),
            )
        }
    }

    private fun cumulativeArc(path: List<Pt>): FloatArray {
        val arc = FloatArray(path.size)
        for (i in 1 until path.size) {
            arc[i] = arc[i - 1] + distance(path[i - 1], path[i])
        }
        return arc
    }

    /** Where along the drawn path each anchor ended up, for the speed profile. */
    private fun pivotArcs(dense: List<Pt>, arc: FloatArray, anchors: List<Pt>): FloatArray {
        val out = FloatArray(anchors.size)
        for (i in anchors.indices) {
            var best = 0
            var bestDistance = Float.MAX_VALUE
            for (j in dense.indices) {
                val d = distance(dense[j], anchors[i])
                if (d < bestDistance) {
                    bestDistance = d
                    best = j
                }
            }
            out[i] = arc[best]
        }
        return out
    }

    private fun pointAt(path: List<Pt>, arc: FloatArray, at: Float): Pt {
        if (at <= 0f) return path.first()
        if (at >= arc.last()) return path.last()
        var lo = 0
        var hi = arc.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arc[mid] < at) lo = mid + 1 else hi = mid
        }
        val i = lo.coerceAtLeast(1)
        val span = arc[i] - arc[i - 1]
        val f = if (span <= 0f) 0f else (at - arc[i - 1]) / span
        val a = path[i - 1]
        val b = path[i]
        return Pt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    /** Arc travelled in one sample interval: slowest at the pivots, fastest between them. */
    private fun stepAt(at: Float, pivots: FloatArray, slowdown: Float): Float {
        var nearest = Float.MAX_VALUE
        for (p in pivots) {
            val d = kotlin.math.abs(p - at)
            if (d < nearest) nearest = d
        }
        val closeness = exp(-(nearest / PIVOT_WIDTH) * (nearest / PIVOT_WIDTH))
        val speed = BASE_SPEED * (1f - slowdown * closeness)
        return (speed * SAMPLE_MS).coerceAtLeast(MIN_STEP)
    }

    /** (arc, hold in ms) for the doubled letters this case hesitates on. */
    private fun dwellsFor(word: String, pivots: FloatArray): List<Pair<Float, Long>> {
        val out = ArrayList<Pair<Float, Long>>(2)
        var anchor = 0
        var i = 0
        while (i < word.length) {
            var run = 1
            while (i + run < word.length && word[i + run] == word[i]) run++
            if (run > 1 && anchor < pivots.size && random.nextFloat() < DOUBLE_DWELL_SHARE) {
                out.add(pivots[anchor] to DOUBLE_DWELL_MS)
            }
            anchor++
            i += run
        }
        return out
    }

    private fun centroid(anchors: List<Pt>): Pt {
        var x = 0f
        var y = 0f
        for (a in anchors) {
            x += a.x
            y += a.y
        }
        return Pt(x / anchors.size, y / anchors.size)
    }

    /** Applies shrink, bias and jitter, then converts key widths to pixels. */
    @Suppress("LongParameterList")
    private fun emit(
        p: Pt,
        centre: Pt,
        scale: Float,
        biasX: Float,
        biasY: Float,
        tremor: Pt,
        clock: Long,
    ): GesturePoint {
        val x = centre.x + (p.x - centre.x) * scale + biasX + tremor.x
        val y = centre.y + (p.y - centre.y) * scale + biasY + tremor.y
        return GesturePoint(x * keyWidth, y * keyWidth, clock)
    }

    /**
     * Hand tremor as an AR(1) walk rather than per-sample white noise.
     *
     * White noise is the obvious model and it is wrong: at 125 Hz it has the
     * finger jumping a fraction of a key back and forth every 8 ms, which no
     * hand does. It also breaks the corpus in a way that looks like a decoder
     * failure — all that reversing inflates the drawn path's arc length, the
     * decoder's ideal-vs-drawn length ratio rejects every candidate, and the
     * metric reads 0.000 with half the cases returning nothing at all.
     *
     * [RHO] gives a correlation length of ~10 samples (~80 ms), so the path
     * wanders instead of buzzing. [MIX] keeps the stationary deviation equal to
     * the requested σ.
     */
    private inner class Tremor(private val sigma: Float) {
        private var x = 0f
        private var y = 0f

        fun next(): Pt {
            x = RHO * x + MIX * sigma * gaussian()
            y = RHO * y + MIX * sigma * gaussian()
            return Pt(x, y)
        }
    }

    private fun distance(a: Pt, b: Pt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Box–Muller: kotlin.random.Random has no Gaussian sampler. */
    private fun gaussian(): Float {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return (sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)).toFloat()
    }

    private fun usable(entry: Pair<String, Int>): Boolean {
        val word = entry.first
        return word.length in MIN_WORD..MAX_WORD && word.all { it in 'a'..'z' }
    }

    /** Cumulative-sum frequency-weighted sampling with binary search. */
    private class FrequencySampler(entries: List<Pair<String, Int>>) {
        private val words = entries.map { it.first }
        private val cumulative = LongArray(entries.size)

        init {
            require(entries.isNotEmpty()) { "no usable words in the entry list" }
            var total = 0L
            for (i in entries.indices) {
                total += entries[i].second.coerceAtLeast(1)
                cumulative[i] = total
            }
        }

        fun sample(random: Random): String {
            val target = random.nextLong(cumulative.last())
            var lo = 0
            var hi = cumulative.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (cumulative[mid] <= target) lo = mid + 1 else hi = mid
            }
            return words[lo]
        }
    }

    companion object {
        /** Pixels per key, matching the synthetic grid in `GestureDecoderTest`. */
        const val KEY_WIDTH = 60f

        /**
         * Two-letter words are tapped, not swiped — their stroke is one short
         * line and decoding it is a coin flip that would swamp the metric.
         * Matches `TypoCorpus`'s own length floor.
         */
        private const val MIN_WORD = 3
        private const val MAX_WORD = 14

        /** 125 Hz, a common digitizer rate. */
        private const val SAMPLE_MS = 8L
        private const val START_TIME_MS = 10_000L

        /** Key widths per millisecond: ~5 key widths in ~400 ms. */
        private const val BASE_SPEED = 0.0125f
        private const val MIN_STEP = 0.005f

        /** How far from a pivot its slowdown still reaches, in key widths. */
        private const val PIVOT_WIDTH = 0.35f

        private const val DENSE_STEP = 0.02f
        private const val DENSE_PER_CORNER = 12
        private const val CORNER_CAP = 0.45f

        private const val DOUBLE_DWELL_SHARE = 0.6f
        private const val DOUBLE_DWELL_MS = 90L

        /** Tremor autocorrelation between consecutive samples (~80 ms memory). */
        private const val RHO = 0.9f

        /** sqrt(1 - RHO²) — keeps the AR(1) walk's stationary σ at the requested value. */
        private const val MIX = 0.4359f

        /** The decoder rejects anything shorter, so a case that short is not a case. */
        private const val MIN_SAMPLES = 4

        /** The letter grid the corpus draws on, in the same pixel space as the paths. */
        fun keyCenters(keyWidth: Float = KEY_WIDTH): List<KeyCenter> =
            QwertyGeometry.keys.map { KeyCenter(it.char, it.x * keyWidth, it.y * keyWidth) }
    }
}
