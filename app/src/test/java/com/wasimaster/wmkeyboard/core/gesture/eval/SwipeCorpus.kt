package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.atan2
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
class SwipeCorpus(
    seed: Long = 42L,
    private val keyWidth: Float = KEY_WIDTH,
    /**
     * The layout being swiped on. Defaults to QWERTY so every caller that only
     * ever measured English keeps measuring exactly what it did.
     */
    val grid: GlideGrid = GlideGrid.of(BuiltInLayouts.QWERTY),
    /**
     * One synthetic user's motor habits, or null for none. Null changes
     * nothing at all — the main random stream is untouched — so every
     * measurement without one is what it always was.
     */
    private val signature: Signature? = null,
) {

    /**
     * How one user draws words: a fixed wobble of every interior anchor and
     * a fixed scaling of every corner cut, drawn per word from [seed] rather
     * than from the corpus's stream, so the same word is drawn the same way
     * twice while the per-case tremor, bias and shrink stay fresh. A
     * persistent *global* offset or shrink would vanish under the shape
     * channel's normalisation and belongs to the hand model, which is why
     * the habit here is per word. Like the dwell, a claim about hands rather
     * than a measurement: a gain against it is an upper bound.
     */
    class Signature(
        val seed: Long,
        /** σ of each interior anchor's displacement, in key widths. */
        val anchorWobble: Float = 0.2f,
        /** Range the profile's corner radius is scaled by, per word. */
        val cornerScale: ClosedFloatingPointRange<Float> = 0.6f..1.4f,
    )

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
        /**
         * Share of *single* letters the finger holds still on, the way it
         * holds on a doubled one. Zero on every graded level, and drawn only
         * when non-zero, so the gate's corpus is bit-for-bit what it was; a
         * sweep switches it on to see what a pause-reading decoder does with
         * strokes that actually pause.
         */
        val letterDwell: Float = 0f,
        /**
         * Share of *doubled* letters drawn as a small circle through the key,
         * Swype's mark for a letter written twice. Zero on every graded level
         * and drawn only when non-zero, so the gate's corpus stays bit-for-bit
         * what it was; a sweep switches it on to see what a loop-reading
         * decoder does with strokes that loop. The same caveat as the dwell:
         * that people loop is a claim, so any gain measured here is an upper
         * bound until a device says so.
         */
        val loopOnDoubles: Float = 0f,
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
        val ideal = anchorsOf(word) ?: return null
        if (ideal.size < 2) return null
        val habit = signature?.let { Random(it.seed xor word.hashCode().toLong()) }
        val anchors = if (habit != null) styled(ideal, habit) else ideal
        val cornerRadius = if (habit != null) {
            val range = signature.cornerScale
            profile.cornerRadius * (range.start + habit.nextFloat() * (range.endInclusive - range.start))
        } else {
            profile.cornerRadius
        }
        val slopped = applyEndSlop(anchors, profile)
        val loops = loopsFor(word, slopped.size, profile)
        val loopSpans = ArrayList<IntRange>(2)
        val dense = densePath(slopped, cornerRadius, loops, loopSpans)
        if (dense.size < 2) return null
        val arc = cumulativeArc(dense)
        val total = arc.last()
        if (total <= 0f) return null

        val pivots = pivotArcs(dense, arc, slopped)
        // A loop is drawn at pivot speed the whole way round.
        val slow = FloatArray(loopSpans.size * 2)
        loopSpans.forEachIndexed { i, span ->
            slow[2 * i] = arc[span.first]
            slow[2 * i + 1] = arc[span.last]
        }
        val dwells = dwellsFor(word, pivots, profile)
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
            travelled += stepAt(travelled, pivots, profile.pivotSlowdown, slow)
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

    /**
     * Key centres of the word's letters in key widths, consecutive repeats
     * collapsed — a doubled letter is one key, visited once.
     */
    private fun anchorsOf(word: String): List<Pt>? {
        val out = ArrayList<Pt>(word.length)
        var previous = -1
        var at = 0
        while (at < word.length) {
            val codePoint = word.codePointAt(at)
            at += Character.charCount(codePoint)
            if (codePoint == previous) continue
            val key = grid.centerOf(codePoint) ?: return null
            out.add(Pt(key.x, key.y))
            previous = codePoint
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

    /** [anchors] with every interior one displaced the way this user's habit for the word has it. */
    private fun styled(anchors: List<Pt>, habit: Random): List<Pt> {
        val wobble = signature?.anchorWobble ?: return anchors
        val out = ArrayList<Pt>(anchors)
        for (i in 1 until anchors.size - 1) {
            out[i] = Pt(anchors[i].x + gaussian(habit) * wobble, anchors[i].y + gaussian(habit) * wobble)
        }
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
    private fun densePath(
        anchors: List<Pt>,
        radius: Float,
        loops: BooleanArray?,
        loopSpans: MutableList<IntRange>,
    ): List<Pt> {
        val out = ArrayList<Pt>(anchors.size * DENSE_PER_CORNER)
        out.add(anchors[0])
        if (loops != null && loops[0]) addLoop(out, anchors[0], anchors[1], loopSpans)
        for (i in 1 until anchors.size - 1) {
            if (loops != null && loops[i]) {
                // The loop stands in for the corner: the finger reaches the
                // key, goes round through it and leaves from it.
                addLine(out, out.last(), anchors[i])
                addLoop(out, anchors[i], anchors[i + 1], loopSpans)
                continue
            }
            val r = cornerRadius(anchors, i, radius)
            val enter = shifted(anchors[i], anchors[i - 1], r)
            val exit = shifted(anchors[i], anchors[i + 1], r)
            addLine(out, out.last(), enter)
            addQuad(out, enter, anchors[i], exit)
        }
        addLine(out, out.last(), anchors.last())
        val last = anchors.size - 1
        if (loops != null && loops[last]) addLoop(out, anchors[last], anchors[last - 1], loopSpans)
        return out
    }

    /**
     * A circle of [LOOP_RADIUS] through [at], drawn once round and back to it,
     * standing off to one side of the line toward [toward]. Records the dense
     * indices it covers in [spans], for the speed profile.
     */
    private fun addLoop(out: MutableList<Pt>, at: Pt, toward: Pt, spans: MutableList<IntRange>) {
        var dx = toward.x - at.x
        var dy = toward.y - at.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) {
            dx = 1f
            dy = 0f
        } else {
            dx /= len
            dy /= len
        }
        val cx = at.x - dy * LOOP_RADIUS
        val cy = at.y + dx * LOOP_RADIUS
        val start = atan2(at.y - cy, at.x - cx)
        val from = out.size - 1
        for (s in 1..LOOP_DENSE_POINTS) {
            val angle = start + 2.0 * PI * s / LOOP_DENSE_POINTS
            out.add(Pt(cx + LOOP_RADIUS * cos(angle).toFloat(), cy + LOOP_RADIUS * sin(angle).toFloat()))
        }
        spans.add(from..out.size - 1)
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

    /**
     * Arc travelled in one sample interval: slowest at the pivots, fastest
     * between them, and pivot-slow the whole way round a loop ([slow] holds
     * each loop's arc range as a start/end pair).
     */
    private fun stepAt(at: Float, pivots: FloatArray, slowdown: Float, slow: FloatArray): Float {
        var nearest = Float.MAX_VALUE
        for (p in pivots) {
            val d = kotlin.math.abs(p - at)
            if (d < nearest) nearest = d
        }
        var closeness = exp(-(nearest / PIVOT_WIDTH) * (nearest / PIVOT_WIDTH))
        var i = 0
        while (i < slow.size) {
            if (at >= slow[i] && at <= slow[i + 1]) closeness = 1f
            i += 2
        }
        val speed = BASE_SPEED * (1f - slowdown * closeness)
        return (speed * SAMPLE_MS).coerceAtLeast(MIN_STEP)
    }

    /**
     * Which anchors this case draws a loop on: doubled letters, at
     * [Profile.loopOnDoubles]. Null — and no random draw at all — when the
     * profile never loops, so the graded corpus is bit-for-bit what it was.
     */
    private fun loopsFor(word: String, anchorCount: Int, profile: Profile): BooleanArray? {
        if (profile.loopOnDoubles <= 0f) return null
        val out = BooleanArray(anchorCount)
        var anchor = 0
        var i = 0
        while (i < word.length) {
            var run = 1
            while (i + run < word.length && word[i + run] == word[i]) run++
            if (run > 1 && anchor < anchorCount && random.nextFloat() < profile.loopOnDoubles) {
                out[anchor] = true
            }
            anchor++
            i += run
        }
        return out
    }

    /** (arc, hold in ms) for the letters this case hesitates on: doubled ones,
     * and single ones at [Profile.letterDwell]. */
    private fun dwellsFor(
        word: String,
        pivots: FloatArray,
        profile: Profile,
    ): List<Pair<Float, Long>> {
        val out = ArrayList<Pair<Float, Long>>(2)
        var anchor = 0
        var i = 0
        while (i < word.length) {
            var run = 1
            while (i + run < word.length && word[i + run] == word[i]) run++
            val hesitates = if (run > 1) {
                random.nextFloat() < DOUBLE_DWELL_SHARE
            } else {
                profile.letterDwell > 0f && random.nextFloat() < profile.letterDwell
            }
            if (hesitates && anchor < pivots.size) {
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
    private fun gaussian(): Float = gaussian(random)

    private fun gaussian(from: Random): Float {
        val u1 = from.nextDouble().coerceAtLeast(1e-12)
        val u2 = from.nextDouble()
        return (sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)).toFloat()
    }

    /**
     * A word this grid can be asked to draw. The alphabet test is the grid's
     * own rather than `'a'..'z'`: on Probhat a "letter" is as likely to be a
     * vowel sign or the hasanta as a consonant, and on ЙЦУКЕН none of them are
     * Latin at all.
     */
    private fun usable(entry: Pair<String, Int>): Boolean {
        val word = entry.first
        return word.length in MIN_WORD..MAX_WORD && grid.canSpell(word)
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

        /** A loop a third of a key wide, as a 32-gon. */
        private const val LOOP_RADIUS = 0.3f
        private const val LOOP_DENSE_POINTS = 32

        /** Tremor autocorrelation between consecutive samples (~80 ms memory). */
        private const val RHO = 0.9f

        /** sqrt(1 - RHO²) — keeps the AR(1) walk's stationary σ at the requested value. */
        private const val MIX = 0.4359f

        /** The decoder rejects anything shorter, so a case that short is not a case. */
        private const val MIN_SAMPLES = 4

        /** The letter grid the corpus draws on, in the same pixel space as the paths. */
        fun keyCenters(keyWidth: Float = KEY_WIDTH): List<com.wasimaster.wmkeyboard.core.gesture.KeyCenter> =
            GlideGrid.of(BuiltInLayouts.QWERTY).keyCenters(keyWidth)
    }
}
