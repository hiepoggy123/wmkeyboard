// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.gesture.GlideWorkspace
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.gesture.KeyOffsets
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.Trie
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the hand model (issue #52) is worth, measured the way it is used: one
 * user, one stroke at a time, each decoded against the grid as the model has
 * it *so far* and then learned from.
 *
 * The corpus draws every stroke for the key centres as laid out, with its
 * offset and shrink noise drawn fresh per stroke, so it has no notion of a
 * hand. One is put on top here: a constant miss plus a reach that pulls every
 * point toward the bottom-right corner, which is a right thumb that lands low
 * and cuts the far keys short. The same strokes are also decoded with the
 * model frozen empty, so the gain is the model's alone, and a hand with no
 * bias at all is run as the control — adapting to nothing must cost nothing.
 *
 * Gated on the second half of each run, once the model has had strokes to
 * learn from. Measured 2026-09-08 at seed 42 over 1000 typical strokes, top-1
 * on the second half: biased hand .2640 frozen, .7260 adapting; no bias
 * .9580 frozen, .9680 adapting. The floors sit well inside those.
 */
class GlideKeyOffsetEvalTest {

    private companion object {
        const val SEED = 42L
        const val CASES = 1000
        const val WARMUP = CASES / 2
        const val RANK_DEPTH = 4

        /** The biased hand: a constant miss in key widths, and a pull toward the corner. */
        const val BIAS_X = 0.22f
        const val BIAS_Y = 0.30f
        const val REACH = -0.10f

        /** What adapting must be worth on the biased hand, in top-1 points. */
        const val MIN_GAIN = 0.30

        /** What adapting may cost on an unbiased hand: ~1.5 binomial σ at 500 strokes. */
        const val MAX_LOSS = 0.015
    }

    private class Hand(val biasX: Float, val biasY: Float, val reach: Float) {
        fun apply(points: List<GesturePoint>, anchorX: Float, anchorY: Float): List<GesturePoint> =
            points.map { p ->
                val x = p.x / SwipeCorpus.KEY_WIDTH
                val y = p.y / SwipeCorpus.KEY_WIDTH
                GesturePoint(
                    (x + biasX + reach * (x - anchorX)) * SwipeCorpus.KEY_WIDTH,
                    (y + biasY + reach * (y - anchorY)) * SwipeCorpus.KEY_WIDTH,
                    p.t,
                )
            }
    }

    private class Run(val early: Double, val late: Double)

    private fun entries(): List<Pair<String, Int>> {
        val file = listOf(File("dictionaries-src/en.txt"), File("app/dictionaries-src/en.txt"))
            .firstOrNull { it.exists() } ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    @Test
    fun adaptingToAHandPaysAndCostsNothingWithout() {
        val entries = entries()
        val trie = Trie().apply { entries.forEach { (word, frequency) -> insert(word, frequency) } }
        val sources = trie.walkers().map {
            FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        }
        val grid = GlideGrid.of(BuiltInLayouts.QWERTY)
        val centers = grid.keyCenters(SwipeCorpus.KEY_WIDTH)
        val anchorX = centers.maxOf { it.x } / SwipeCorpus.KEY_WIDTH
        val anchorY = centers.maxOf { it.y } / SwipeCorpus.KEY_WIDTH
        val cases = SwipeCorpus(SEED, grid = grid).generate(entries, SwipeCorpus.Noise.TYPICAL, CASES)

        val biased = Hand(BIAS_X, BIAS_Y, REACH)
        val straight = Hand(0f, 0f, 0f)
        val biasedFrozen = run(cases, biased, adapt = false, centers, sources, anchorX, anchorY)
        val biasedAdapted = run(cases, biased, adapt = true, centers, sources, anchorX, anchorY)
        val straightFrozen = run(cases, straight, adapt = false, centers, sources, anchorX, anchorY)
        val straightAdapted = run(cases, straight, adapt = true, centers, sources, anchorX, anchorY)

        println("=== glide hand adaptation (seed=$SEED, $CASES typical strokes, top1 first/second half) ===")
        report("biased hand, frozen  ", biasedFrozen)
        report("biased hand, adapting", biasedAdapted)
        report("no bias, frozen      ", straightFrozen)
        report("no bias, adapting    ", straightAdapted)

        val gain = biasedAdapted.late - biasedFrozen.late
        assertTrue("adapting to a biased hand gained only $gain top-1", gain >= MIN_GAIN)
        val loss = straightFrozen.late - straightAdapted.late
        assertTrue("adapting to an unbiased hand cost $loss top-1", loss <= MAX_LOSS)
    }

    @Suppress("LongParameterList")
    private fun run(
        cases: List<SwipeCorpus.Case>,
        hand: Hand,
        adapt: Boolean,
        centers: List<KeyCenter>,
        sources: List<FuzzyBeamSearch.WalkSource>,
        anchorX: Float,
        anchorY: Float,
    ): Run {
        val beam = GlideBeam()
        val workspace = GlideWorkspace()
        val model = KeyOffsets(null)
        val raw = GlideKeyMap.of(centers, SwipeCorpus.KEY_WIDTH)
        var decodeGrid = raw
        var gridVersion = -1
        var earlyHits = 0
        var lateHits = 0
        cases.forEachIndexed { index, case ->
            val path = hand.apply(case.path, anchorX, anchorY)
            if (adapt && model.version != gridVersion) {
                decodeGrid = GlideKeyMap.of(model.shifted(centers, SwipeCorpus.KEY_WIDTH), SwipeCorpus.KEY_WIDTH)
                gridVersion = model.version
            }
            val decoded = beam.decode(path, decodeGrid, SwipeCorpus.KEY_WIDTH, sources, workspace, RANK_DEPTH)
            val hit = decoded.firstOrNull()?.word.equals(case.intended, ignoreCase = true)
            if (index < WARMUP) { if (hit) earlyHits++ } else if (hit) lateHits++
            // The user keeps the word they meant — off the strip if not off
            // the first slot — so every stroke teaches its intended word.
            if (adapt) {
                val aligned = beam.align(case.intended, path, raw, SwipeCorpus.KEY_WIDTH, workspace)
                if (aligned != null) {
                    model.observe(
                        List(aligned.size) { i ->
                            val k = aligned.keys[i]
                            KeyOffsets.Observation(raw.keyX[k], raw.keyY[k], aligned.x[i], aligned.y[i])
                        },
                    )
                }
            }
        }
        return Run(earlyHits / WARMUP.toDouble(), lateHits / (CASES - WARMUP).toDouble())
    }

    private fun report(label: String, run: Run) {
        println(String.format(Locale.ROOT, "%s  early=%.4f  late=%.4f", label, run.early, run.late))
    }
}
