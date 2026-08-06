// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GestureDecoder
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.eval.EvalMetrics
import com.wasimaster.wmkeyboard.core.prediction.eval.SuggestMetrics
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline glide-decoder quality harness: runs the real decoder over the real
 * shipped English dictionary against a reproducible synthetic swipe corpus,
 * and asserts the headline metrics never regress past [GestureEvalBaseline].
 *
 * Prints a metric table to stdout and writes a diffable JSON artifact to
 * `build/reports/gestureeval/metrics.json`.
 */
class GestureEvalTest {

    private companion object {
        const val SEED = 42L
        const val CASES_PER_LEVEL = 500

        /**
         * Deeper than the four candidates production shows, so MRR keeps
         * resolving after rank 4 instead of flattening into "missed". top1 and
         * top3 are unaffected by the depth.
         */
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
    fun qualityDoesNotRegress() {
        val entries = realEntries()
        val decoder = GestureDecoder(SwipeCorpus.keyCenters(), SwipeCorpus.KEY_WIDTH)
        val corpus = SwipeCorpus(SEED)

        val byNoise = LinkedHashMap<SwipeCorpus.Noise, SuggestMetrics>()
        val emptyRate = LinkedHashMap<SwipeCorpus.Noise, Double>()
        val allRanks = ArrayList<Int?>(CASES_PER_LEVEL * SwipeCorpus.Noise.entries.size)
        for (noise in SwipeCorpus.Noise.entries) {
            var empty = 0
            val ranks = corpus.generate(entries, noise, CASES_PER_LEVEL).map { case ->
                val decoded = decoder.decode(case.path, entries, limit = RANK_DEPTH)
                if (decoded.isEmpty()) empty++
                val at = decoded.indexOfFirst { it.word.equals(case.intended, ignoreCase = true) }
                if (at >= 0) at + 1 else null
            }
            byNoise[noise] = EvalMetrics.suggest(ranks)
            emptyRate[noise] = empty / CASES_PER_LEVEL.toDouble()
            allRanks.addAll(ranks)
        }
        val overall = EvalMetrics.suggest(allRanks)

        report(byNoise, emptyRate, overall)

        floor("top1", overall.top1, GestureEvalBaseline.TOP1)
        floor("top3", overall.top3, GestureEvalBaseline.TOP3)
        floor("mrr", overall.mrr, GestureEvalBaseline.MRR)
        floor("clean top1", top1(byNoise, SwipeCorpus.Noise.CLEAN), GestureEvalBaseline.CLEAN_TOP1)
        floor("light top1", top1(byNoise, SwipeCorpus.Noise.LIGHT), GestureEvalBaseline.LIGHT_TOP1)
        floor("typical top1", top1(byNoise, SwipeCorpus.Noise.TYPICAL), GestureEvalBaseline.TYPICAL_TOP1)
        floor("sloppy top1", top1(byNoise, SwipeCorpus.Noise.SLOPPY), GestureEvalBaseline.SLOPPY_TOP1)
    }

    private fun top1(byNoise: Map<SwipeCorpus.Noise, SuggestMetrics>, noise: SwipeCorpus.Noise): Double =
        byNoise.getValue(noise).top1

    private fun floor(name: String, value: Double, baseline: Double) {
        assertTrue(
            "$name $value regressed below ${baseline - GestureEvalBaseline.TOLERANCE}",
            value >= baseline - GestureEvalBaseline.TOLERANCE,
        )
    }

    private fun report(
        byNoise: Map<SwipeCorpus.Noise, SuggestMetrics>,
        emptyRate: Map<SwipeCorpus.Noise, Double>,
        overall: SuggestMetrics,
    ) {
        fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        println("=== glide eval (seed=$SEED, ${CASES_PER_LEVEL}/level, n=${overall.n}) ===")
        for ((noise, m) in byNoise) {
            // `empty` is the share the decoder refused outright. It separates a
            // pruning failure from a ranking failure, which the rank metrics
            // alone cannot tell apart.
            println(
                String.format(Locale.ROOT, "%-8s", noise.name) +
                    " top1=${pct(m.top1)}  top3=${pct(m.top3)}  mrr=${pct(m.mrr)}" +
                    "  empty=${pct(emptyRate.getValue(noise))}"
            )
        }
        println("OVERALL  top1=${pct(overall.top1)}  top3=${pct(overall.top3)}  mrr=${pct(overall.mrr)}")

        val json = buildString {
            append("{\n")
            append("  \"seed\": $SEED,\n")
            append("  \"casesPerLevel\": $CASES_PER_LEVEL,\n")
            append("  \"n\": ${overall.n},\n")
            for ((noise, m) in byNoise) {
                val key = noise.name.lowercase(Locale.ROOT)
                append("  \"$key\": { ")
                append("\"top1\": ${pct(m.top1)}, ")
                append("\"top3\": ${pct(m.top3)}, ")
                append("\"mrr\": ${pct(m.mrr)}, ")
                append("\"empty\": ${pct(emptyRate.getValue(noise))} },\n")
            }
            append("  \"top1\": ${pct(overall.top1)},\n")
            append("  \"top3\": ${pct(overall.top3)},\n")
            append("  \"mrr\": ${pct(overall.mrr)}\n")
            append("}\n")
        }
        val out = File("build/reports/gestureeval/metrics.json")
        out.parentFile?.mkdirs()
        out.writeText(json)
        println("metrics written to ${out.path}")
    }
}
