// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.gesture.GlideWorkspace
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.eval.EvalMetrics
import java.io.File
import java.util.Locale
import org.junit.Ignore
import org.junit.Test

/**
 * Measurement, not a gate: sweeps [GlideBeam.Tuning] one axis at a time and
 * prints the surface, so the shipped defaults are a number someone read off a
 * table rather than a number someone liked.
 *
 * `@Ignore` because it is minutes of work, not seconds, and nothing regresses
 * if it never runs. Drop the annotation (or run it by name) when a weight is
 * being re-picked:
 *
 * ```
 * ./gradlew :app:testFullDebugUnitTest --tests "*GlideTuningSweepTest"
 * ```
 *
 * One axis at a time is deliberate. A full grid over eight weights measures the
 * corpus's quirks as much as the decoder's, and the interaction that actually
 * matters — shape weight against the language model — is visible on its own
 * axis because everything else is a shape-internal detail.
 */
@Ignore("measurement, minutes long; run by name when re-picking a weight")
class GlideTuningSweepTest {

    private companion object {
        const val SEED = 42L
        const val CASES_PER_LEVEL = 300
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
    fun sweep() {
        val entries = realEntries()
        val trie = Trie().apply { entries.forEach { (word, frequency) -> insert(word, frequency) } }
        val sources = trie.walkers().map {
            FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        }
        val keys = GlideKeyMap.of(SwipeCorpus.keyCenters(), SwipeCorpus.KEY_WIDTH)
        // One corpus for every configuration: the whole point is that the only
        // thing changing between rows is the weight named in the row.
        val cases = SwipeCorpus.Noise.entries.associateWith { noise ->
            SwipeCorpus(SEED).generate(entries, noise, CASES_PER_LEVEL)
        }

        val base = GlideBeam.Tuning()
        axis("shapeChannel", listOf(20.0, 30.0, 45.0, 65.0, 90.0), cases, keys, sources) {
            base.copy(shapeChannel = it)
        }
        axis("dwellPenalty", listOf(0.0f, 0.2f, 0.4f, 0.7f, 1.2f, 2.0f), cases, keys, sources) {
            base.copy(dwellPenalty = it)
        }
        axis("gapWeight", listOf(0.5f, 1.0f, 2.0f, 3.0f, 4.0f, 6.0f), cases, keys, sources) {
            base.copy(gapWeight = it)
        }
        axis("sigma", listOf(0.40f, 0.45f, 0.55f, 0.65f, 0.80f), cases, keys, sources) {
            base.copy(sigma = it)
        }
        axis("maxPointCost", listOf(3.0f, 4.5f, 6.0f, 9.0f, 15.0f), cases, keys, sources) {
            base.copy(maxPointCost = it)
        }
        axis("gapWindow", listOf(4f, 6f, 8f, 10f, 14f, 20f), cases, keys, sources) {
            base.copy(gapWindow = it)
        }
        axis("repeatCost", listOf(0.0f, 0.2f, 0.35f, 0.6f, 1.0f, 2.0f), cases, keys, sources) {
            base.copy(repeatCost = it)
        }
        axis("anchorRadius", listOf(1.0f, 1.3f, 1.6f, 2.0f, 2.5f), cases, keys, sources) {
            base.copy(anchorRadius = it)
        }
        axis("nearRadius", listOf(0.9f, 1.1f, 1.25f, 1.5f, 2.0f), cases, keys, sources) {
            base.copy(nearRadius = it)
        }
    }

    private fun <T : Any> axis(
        name: String,
        values: List<T>,
        cases: Map<SwipeCorpus.Noise, List<SwipeCorpus.Case>>,
        keys: GlideKeyMap,
        sources: List<FuzzyBeamSearch.WalkSource>,
        tuning: (T) -> GlideBeam.Tuning,
    ) {
        println("--- $name ---")
        for (value in values) {
            val row = measure(tuning(value), cases, keys, sources)
            println(String.format(Locale.ROOT, "%-8s", value.toString()) + " $row")
        }
    }

    private fun measure(
        tuning: GlideBeam.Tuning,
        cases: Map<SwipeCorpus.Noise, List<SwipeCorpus.Case>>,
        keys: GlideKeyMap,
        sources: List<FuzzyBeamSearch.WalkSource>,
    ): String {
        val beam = GlideBeam(tuning)
        val workspace = GlideWorkspace()
        val all = ArrayList<Int?>()
        val perLevel = StringBuilder()
        for ((noise, level) in cases) {
            val ranks = level.map { case ->
                val decoded = beam.decode(
                    case.path, keys, SwipeCorpus.KEY_WIDTH, sources, workspace, RANK_DEPTH,
                )
                val at = decoded.indexOfFirst { it.word.equals(case.intended, ignoreCase = true) }
                if (at >= 0) at + 1 else null
            }
            all.addAll(ranks)
            perLevel.append(
                String.format(
                    Locale.ROOT, " %s=%.3f", noise.name.take(2), EvalMetrics.suggest(ranks).top1,
                )
            )
        }
        val overall = EvalMetrics.suggest(all)
        return String.format(
            Locale.ROOT, "top1=%.4f top3=%.4f mrr=%.4f |%s",
            overall.top1, overall.top3, overall.mrr, perLevel,
        )
    }
}
