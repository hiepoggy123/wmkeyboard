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
 * hand. Three are put on top here. A constant miss plus a reach that pulls
 * every point toward the bottom-right corner, which is a right thumb that
 * lands low and cuts the far keys short. A hand that draws every stroke at
 * 85% of the keyboard about its centre, which is a thumb that never quite
 * reaches the far keys — a scale, not a shift, and what the model's trend is
 * for. And a hand with no bias at all, run as the control: adapting to
 * nothing must cost nothing. The same strokes are also decoded with the
 * model frozen empty, so each gain is the model's alone.
 *
 * Gated on the second half of each run, once the model has had strokes to
 * learn from. Measured 2026-09-08 at seed 42 over 1000 typical strokes, top-1
 * on the second half: biased hand .2600 frozen, .9080 adapting (.7260 before
 * the trend, with the per-key shift capped at 0.45); small hand .5180 frozen,
 * .9460 adapting (.9080 before the trend); no bias .9520 frozen, .9580
 * adapting (.9640 before the trend: the corner cutting of an unbiased hand
 * reads as a slight inward slope, and following it costs half a point). The
 * floors sit well inside those.
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

        /** The small hand: how much of the keyboard it draws, about the centre. */
        const val SMALL = 0.85f

        /** What adapting must be worth on the biased hand, in top-1 points. */
        const val MIN_GAIN = 0.30

        /** What adapting must be worth on the small hand. */
        const val MIN_SMALL_GAIN = 0.30

        /** What adapting may cost on an unbiased hand: ~1.5 binomial σ at 500 strokes. */
        const val MAX_LOSS = 0.015
    }

    private class Hand(val biasX: Float, val biasY: Float, val reach: Float, val scale: Float = 1f) {
        @Suppress("LongParameterList")
        fun apply(
            points: List<GesturePoint>,
            anchorX: Float,
            anchorY: Float,
            centreX: Float,
            centreY: Float,
        ): List<GesturePoint> =
            points.map { p ->
                val x = p.x / SwipeCorpus.KEY_WIDTH
                val y = p.y / SwipeCorpus.KEY_WIDTH
                GesturePoint(
                    (centreX + (x - centreX) * scale + biasX + reach * (x - anchorX)) * SwipeCorpus.KEY_WIDTH,
                    (centreY + (y - centreY) * scale + biasY + reach * (y - anchorY)) * SwipeCorpus.KEY_WIDTH,
                    p.t,
                )
            }
    }

    private class Run(val early: Double, val late: Double)

    private class Board(
        val centers: List<KeyCenter>,
        val sources: List<FuzzyBeamSearch.WalkSource>,
        val anchorX: Float,
        val anchorY: Float,
        val centreX: Float,
        val centreY: Float,
    )

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
        val board = Board(
            centers, sources,
            anchorX = centers.maxOf { it.x } / SwipeCorpus.KEY_WIDTH,
            anchorY = centers.maxOf { it.y } / SwipeCorpus.KEY_WIDTH,
            centreX = centers.map { it.x }.average().toFloat() / SwipeCorpus.KEY_WIDTH,
            centreY = centers.map { it.y }.average().toFloat() / SwipeCorpus.KEY_WIDTH,
        )
        val cases = SwipeCorpus(SEED, grid = grid).generate(entries, SwipeCorpus.Noise.TYPICAL, CASES)

        val biased = Hand(BIAS_X, BIAS_Y, REACH)
        val small = Hand(0f, 0f, 0f, scale = SMALL)
        val straight = Hand(0f, 0f, 0f)
        val biasedFrozen = run(cases, biased, adapt = false, board)
        val biasedAdapted = run(cases, biased, adapt = true, board)
        val smallFrozen = run(cases, small, adapt = false, board)
        val smallAdapted = run(cases, small, adapt = true, board)
        val straightFrozen = run(cases, straight, adapt = false, board)
        val straightAdapted = run(cases, straight, adapt = true, board)

        println("=== glide hand adaptation (seed=$SEED, $CASES typical strokes, top1 first/second half) ===")
        report("biased hand, frozen  ", biasedFrozen)
        report("biased hand, adapting", biasedAdapted)
        report("small hand, frozen   ", smallFrozen)
        report("small hand, adapting ", smallAdapted)
        report("no bias, frozen      ", straightFrozen)
        report("no bias, adapting    ", straightAdapted)

        val gain = biasedAdapted.late - biasedFrozen.late
        assertTrue("adapting to a biased hand gained only $gain top-1", gain >= MIN_GAIN)
        val smallGain = smallAdapted.late - smallFrozen.late
        assertTrue("adapting to a small hand gained only $smallGain top-1", smallGain >= MIN_SMALL_GAIN)
        val loss = straightFrozen.late - straightAdapted.late
        assertTrue("adapting to an unbiased hand cost $loss top-1", loss <= MAX_LOSS)
    }

    private fun run(cases: List<SwipeCorpus.Case>, hand: Hand, adapt: Boolean, board: Board): Run {
        val beam = GlideBeam()
        val workspace = GlideWorkspace()
        val model = KeyOffsets(null)
        val raw = GlideKeyMap.of(board.centers, SwipeCorpus.KEY_WIDTH)
        var decodeGrid = raw
        var gridVersion = -1
        var earlyHits = 0
        var lateHits = 0
        cases.forEachIndexed { index, case ->
            val path = hand.apply(case.path, board.anchorX, board.anchorY, board.centreX, board.centreY)
            if (adapt && model.version != gridVersion) {
                decodeGrid = GlideKeyMap.of(model.shifted(board.centers, SwipeCorpus.KEY_WIDTH), SwipeCorpus.KEY_WIDTH)
                gridVersion = model.version
            }
            val decoded = beam.decode(path, decodeGrid, SwipeCorpus.KEY_WIDTH, board.sources, workspace, RANK_DEPTH)
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
