package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM latency probe for the suggestion hot path. Not a rigorous
 * benchmark — its value is the relative before/after number when the engine
 * changes, with intentionally loose absolute ceilings for CI noise. Disable
 * the assertions entirely with `-Dwmkeyboard.benchAssert=false`.
 */
class PredictionLatencyBench {

    private companion object {
        const val WARMUP = 200
        const val MEASURE = 1000
        /**
         * Loose ceilings — regressions, not noise, should trip them. Baseline
         * 2026-08-05 on a desktop JVM: suggest p50 0.066ms / p99 0.172ms, so
         * these leave ~75× headroom for slower CI machines.
         */
        const val SUGGEST_P50_CEILING_MS = 5.0
        const val SUGGEST_P99_CEILING_MS = 25.0
    }

    @Test
    fun suggestionHotPathStaysFast() {
        val entries = run {
            val candidates = listOf(
                File("dictionaries-src/en.txt"),
                File("app/dictionaries-src/en.txt"),
            )
            val file = candidates.firstOrNull { it.exists() }
                ?: error("en.txt not found (cwd=${File(".").absolutePath})")
            file.inputStream().use { DictionaryLoader.loadEntries(it) }
        }
        val engine = SuggestionEngine(
            PackedTrie.of(entries),
            BengaliPhoneticIndex(emptyList()),
            UserLexicon(null),
        )
        val trie = PackedTrie.of(entries)
        val corpus = TypoCorpus(7L)
        val cases = corpus.generate(entries, 200)

        val suggestNs = measure("suggest") { i ->
            val case = cases[i % cases.size]
            engine.suggest(case.typed, previousWord = case.previous)
        }
        val autocorrectNs = measure("shouldAutocorrect") { i ->
            engine.shouldAutocorrect(cases[i % cases.size].typed)
        }
        val completeNs = measure("complete") { i ->
            trie.complete(cases[i % cases.size].intended.take(2), 10)
        }

        report("suggest", suggestNs)
        report("shouldAutocorrect", autocorrectNs)
        report("complete(prefix2, 10)", completeNs)

        if (System.getProperty("wmkeyboard.benchAssert") != "false") {
            val p50 = percentile(suggestNs, 50.0) / 1e6
            val p99 = percentile(suggestNs, 99.0) / 1e6
            assertTrue("suggest P50 ${p50}ms above ceiling $SUGGEST_P50_CEILING_MS", p50 < SUGGEST_P50_CEILING_MS)
            assertTrue("suggest P99 ${p99}ms above ceiling $SUGGEST_P99_CEILING_MS", p99 < SUGGEST_P99_CEILING_MS)
        }
    }

    private inline fun measure(name: String, op: (Int) -> Any?): LongArray {
        repeat(WARMUP) { op(it) }
        val samples = LongArray(MEASURE)
        for (i in 0 until MEASURE) {
            val start = System.nanoTime()
            op(i)
            samples[i] = System.nanoTime() - start
        }
        return samples
    }

    private fun percentile(samples: LongArray, p: Double): Long {
        val sorted = samples.sorted()
        val index = ((p / 100.0) * (sorted.size - 1)).toInt()
        return sorted[index]
    }

    private fun report(name: String, samples: LongArray) {
        fun ms(v: Long) = String.format(Locale.ROOT, "%.3f", v / 1e6)
        println(
            "bench $name: p50=${ms(percentile(samples, 50.0))}ms " +
                "p95=${ms(percentile(samples, 95.0))}ms p99=${ms(percentile(samples, 99.0))}ms"
        )
    }
}
