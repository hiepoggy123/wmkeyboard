// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
         * Loose ceilings — regressions, not noise, should trip them.
         *
         * Baseline 2026-08-06 on a desktop JVM, `.wmdict` v2 and the v1
         * NUL-joined n-gram packs:
         * ```
         * suggest                        p50 0.33ms   p99 0.56-0.62ms
         * suggest with pack              p50 0.39-0.55ms  p99 0.87-1.26ms
         * pack.bigramCount               p50 <0.001ms
         * pack.nextWords(5)              p50 0.008-0.010ms
         * shouldAutocorrect              p50 0.32ms   p99 0.55ms
         * complete(prefix2, 10)          p50 0.037ms  p99 0.07-0.11ms
         * keystroke(suggest+autocorrect) p50 0.33ms   p99 0.60-0.65ms
         * ```
         * Ranges, not points: back-to-back runs move p50 by up to 40%, so this
         * catches a change of scale and nothing finer. Treat a single run as
         * evidence only when the shift is well outside those bands.
         *
         * The figure recorded here before was 0.066ms, from 2026-08-05 and
         * several signal passes ago; it had drifted far enough to mislead.
         */
        const val SUGGEST_P50_CEILING_MS = 5.0
        const val SUGGEST_P99_CEILING_MS = 25.0
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("dictionaries-src/en.txt"),
            File("app/dictionaries-src/en.txt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    /**
     * The same hot path with a corpus pack installed, which is what a user who
     * has downloaded one actually runs. [suggestionHotPathStaysFast] leaves
     * `ngramPack` empty, so `bigramCount` returns on its first branch and none
     * of its numbers describe pack work at all.
     */
    @Test
    fun suggestionHotPathStaysFastWithACorpusPack() {
        val entries = realEntries()
        val packDir = tmp.newFolder("pack")
        val pack = SyntheticNgramPack.of(entries, packDir)
        // The other half of the story: what a pack of this shape costs on disk.
        for (file in packDir.listFiles().orEmpty().sortedBy { it.name }) {
            println("pack file ${file.name}: ${file.length()} bytes")
        }
        val engine = SuggestionEngine(
            PackedTrie.of(entries),
            BengaliPhoneticIndex(emptyList()),
            UserLexicon(null),
        ).apply { ngramPack = pack }
        val corpus = TypoCorpus(7L)
        val cases = corpus.generate(entries, 200)

        // Context matters here in a way it does not for the packless bench:
        // with no previous word the engine never asks the pack anything.
        val suggestNs = measure("suggest+pack") { i ->
            val case = cases[i % cases.size]
            engine.suggest(case.typed, previousWord = case.previous ?: "of")
        }
        val bigramNs = measure("bigramCount") { i ->
            pack.bigramCount("of", cases[i % cases.size].intended)
        }
        val followersNs = measure("nextWords") { i ->
            pack.nextWords(if (i and 1 == 0) "of" else cases[i % cases.size].intended, 5)
        }

        report("suggest with pack", suggestNs)
        report("pack.bigramCount", bigramNs)
        report("pack.nextWords(5)", followersNs)

        if (System.getProperty("wmkeyboard.benchAssert") != "false") {
            val p50 = percentile(suggestNs, 50.0) / 1e6
            val p99 = percentile(suggestNs, 99.0) / 1e6
            assertTrue("suggest P50 ${p50}ms above ceiling $SUGGEST_P50_CEILING_MS", p50 < SUGGEST_P50_CEILING_MS)
            assertTrue("suggest P99 ${p99}ms above ceiling $SUGGEST_P99_CEILING_MS", p99 < SUGGEST_P99_CEILING_MS)
        }
    }

    @Test
    fun suggestionHotPathStaysFast() {
        val entries = realEntries()
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
        // The production shape: one keystroke runs suggest and then the
        // commit precompute asks shouldAutocorrect for the same word — the
        // engine's shared-walk cache should make the second ask nearly free.
        val keystrokeNs = measure("keystroke") { i ->
            val typed = cases[i % cases.size].typed
            engine.suggest(typed, previousWord = null)
            engine.shouldAutocorrect(typed)
        }

        report("suggest", suggestNs)
        report("shouldAutocorrect", autocorrectNs)
        report("complete(prefix2, 10)", completeNs)
        report("keystroke(suggest+autocorrect)", keystrokeNs)

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
