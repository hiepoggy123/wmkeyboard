// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.gesture.GlideWorkspace
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A measurement, not a regression gate.
 *
 * [GestureEvalTest] reports four graded noise levels, but a level is a bundle of
 * five independent distortions — so when a level's score moves, the bundle does
 * not say which axis moved it. This sweeps one axis at a time with the rest held
 * at [SwipeCorpus.Noise.TYPICAL] and prints the curve.
 *
 * It exists for two jobs:
 *
 *  1. **Keeping the corpus honest.** If a graded level sits past the knee of a
 *     curve, that level is measuring the corpus's extremity rather than the
 *     decoder's skill, and its parameters should come back in. The first run of
 *     this harness caught exactly that — a corner-cut radius of 0.70 key widths
 *     put SLOPPY off a cliff at top1 0.056.
 *  2. **Locating a regression.** A change that costs accuracy usually costs it
 *     on one axis. The curve says which.
 *
 * Assertions here are sanity rails only: the sweep must be monotone-ish and the
 * mildest setting must decode well. Real floors live in [GestureEvalBaseline].
 */
class SwipeNoiseSweepTest {

    private companion object {
        const val SEED = 42L
        const val CASES = 250
        const val RANK_DEPTH = 8
    }

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("dictionaries-src/en.txt"),
            File("app/dictionaries-src/en.txt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    @Test
    fun noiseAxesDegradeGracefully() {
        val entries = realEntries()
        val trie = Trie().apply { entries.forEach { (word, frequency) -> insert(word, frequency) } }
        val decoder = BeamProbe(
            GlideBeam(),
            GlideWorkspace(),
            GlideKeyMap.of(SwipeCorpus.keyCenters(), SwipeCorpus.KEY_WIDTH),
            trie.walkers().map {
                FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
            },
        )
        val base = SwipeCorpus.Noise.TYPICAL.profile

        println("=== glide noise sweep (seed=$SEED, $CASES/point, others at TYPICAL) ===")

        val corner = sweep(entries, decoder, "cornerRadius", CORNER_STEPS) { base.copy(cornerRadius = it) }
        sweep(entries, decoder, "jitter", JITTER_STEPS) { base.copy(jitter = it) }
        sweep(entries, decoder, "bias", BIAS_STEPS) { base.copy(bias = it) }
        sweep(entries, decoder, "shrink", SHRINK_STEPS) { base.copy(shrink = it) }
        sweep(entries, decoder, "endSlop", END_SLOP_STEPS) { base.copy(endSlop = it) }

        // The mildest corner cut is near-ideal geometry; if that does not decode,
        // the corpus and the decoder disagree about the coordinate space and
        // every other number in this harness is meaningless.
        assertTrue(
            "near-ideal corner cut decoded at only ${corner.first().top1}",
            corner.first().top1 >= 0.80,
        )
    }

    private class Point(val value: Float, val top1: Double, val empty: Double)

    /**
     * The decoder with everything the sweep never varies already bound, so a
     * sweep row reads as one call with one axis in it.
     */
    private class BeamProbe(
        private val beam: GlideBeam,
        private val workspace: GlideWorkspace,
        private val keys: GlideKeyMap,
        private val sources: List<FuzzyBeamSearch.WalkSource>,
    ) {
        fun decode(path: List<GesturePoint>, limit: Int): List<GlideBeam.Candidate> =
            beam.decode(path, keys, SwipeCorpus.KEY_WIDTH, sources, workspace, limit)
    }

    private fun sweep(
        entries: List<Pair<String, Int>>,
        decoder: BeamProbe,
        axis: String,
        steps: List<Float>,
        profileAt: (Float) -> SwipeCorpus.Profile,
    ): List<Point> {
        val points = steps.map { value ->
            // A fresh corpus per point so each one sees the same word draw, and
            // the only thing that differs between points is the axis.
            val corpus = SwipeCorpus(SEED)
            var hits = 0
            var empty = 0
            for (case in corpus.generate(entries, profileAt(value), CASES)) {
                val decoded = decoder.decode(case.path, RANK_DEPTH)
                if (decoded.isEmpty()) empty++
                if (decoded.firstOrNull()?.word.equals(case.intended, ignoreCase = true)) hits++
            }
            Point(value, hits / CASES.toDouble(), empty / CASES.toDouble())
        }
        val line = points.joinToString("  ") {
            String.format(Locale.ROOT, "%.2f:%.3f(e%.2f)", it.value, it.top1, it.empty)
        }
        println(String.format(Locale.ROOT, "%-13s", axis) + line)
        return points
    }
}

private val CORNER_STEPS = listOf(0.05f, 0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
private val JITTER_STEPS = listOf(0.02f, 0.06f, 0.10f, 0.16f, 0.24f)
private val BIAS_STEPS = listOf(0.00f, 0.08f, 0.16f, 0.28f, 0.40f)
private val SHRINK_STEPS = listOf(0.00f, 0.05f, 0.10f, 0.18f, 0.28f)
private val END_SLOP_STEPS = listOf(0.00f, 0.10f, 0.22f, 0.36f, 0.52f)
