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
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM latency probe for the glide hot path. Not a rigorous benchmark —
 * its value is the relative before/after number when the decoder changes, with
 * intentionally loose absolute ceilings for CI noise. Disable the assertions
 * with `-Dwmkeyboard.benchAssert=false`.
 *
 * Three shapes are measured, and the last two are the ones that matter:
 *
 *  - **`decode`** is one finished swipe, the cost paid once on finger-up.
 *  - **`stroke`** is a whole gesture's worth of live previews — the decoder is
 *    re-run against a growing prefix of the path several times per second while
 *    the finger is still down. That is where a slow decoder is actually felt,
 *    and it costs a multiple of a single decode.
 *  - **`decode(4x lexicon)`** is the claim the trie beam exists to make. The old
 *    flat-scan decoder was linear in the word list, so a user on a large
 *    downloaded dictionary paid proportionally; the beam should barely notice,
 *    and this row is what would catch that going wrong.
 */
class GestureLatencyBench {

    private companion object {
        const val WARMUP = 30
        const val MEASURE = 200

        /** Live previews issued across one stroke, matching the production cadence. */
        const val PREVIEWS_PER_STROKE = 6

        /**
         * Loose ceilings — regressions, not noise, should trip them. Baseline
         * 2026-08-07 on a desktop JVM with the trie beam over the bundled
         * 17k-word list: decode p50 well under a millisecond, so these leave
         * wide headroom for slower machines and larger dictionaries.
         */
        const val DECODE_P50_CEILING_MS = 25.0
        const val DECODE_P99_CEILING_MS = 90.0

        /** Three plausible English endings, to quadruple the word list without
         * leaving the alphabet the corpus draws on. */
        val LARGE_SUFFIXES = listOf("ing", "ness", "ly")
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

    private fun sourcesFor(entries: List<Pair<String, Int>>): List<FuzzyBeamSearch.WalkSource> {
        val trie = Trie().apply { entries.forEach { (word, frequency) -> insert(word, frequency) } }
        return trie.walkers().map {
            FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        }
    }

    @Test
    fun glideHotPathStaysFast() {
        val entries = realEntries()
        val beam = GlideBeam()
        val workspace = GlideWorkspace()
        val keys = GlideKeyMap.of(SwipeCorpus.keyCenters(), SwipeCorpus.KEY_WIDTH)
        val sources = sourcesFor(entries)
        val cases = SwipeCorpus(7L).generate(entries, SwipeCorpus.Noise.TYPICAL, 100)

        val decodeNs = measure { i ->
            beam.decode(
                cases[i % cases.size].path, keys, SwipeCorpus.KEY_WIDTH, sources, workspace,
            )
        }
        // The live-preview shape: the same stroke decoded repeatedly against a
        // lengthening prefix, which is what the finger-down path really costs.
        val strokeNs = measure { i ->
            val path = cases[i % cases.size].path
            for (step in 1..PREVIEWS_PER_STROKE) {
                val upTo = path.size * step / PREVIEWS_PER_STROKE
                if (upTo >= 3) {
                    beam.decode(
                        path.subList(0, upTo), keys, SwipeCorpus.KEY_WIDTH, sources, workspace,
                    )
                }
            }
        }
        // One trie holding four times the words, which is the shape a large
        // downloaded list actually takes. Suffixing rather than concatenating
        // four copies keeps them distinct without inventing a new alphabet: the
        // extra words share prefixes with the real ones, so they are genuinely
        // in the beam's way rather than rejected at the first edge.
        val wide = entries + LARGE_SUFFIXES.flatMap { suffix ->
            entries.map { (word, frequency) -> (word + suffix) to frequency / 2 }
        }
        val wideSources = sourcesFor(wide)
        val wideNs = measure { i ->
            beam.decode(
                cases[i % cases.size].path, keys, SwipeCorpus.KEY_WIDTH, wideSources, workspace,
            )
        }

        report("decode(full path)", decodeNs)
        report("stroke($PREVIEWS_PER_STROKE previews)", strokeNs)
        report("decode(4x lexicon)", wideNs)
        println("lexicon=${entries.size} words, wide=${wide.size} words, sampled path len=${averageLength(cases)}")

        if (System.getProperty("wmkeyboard.benchAssert") != "false") {
            val p50 = percentile(decodeNs, 50.0) / 1e6
            val p99 = percentile(decodeNs, 99.0) / 1e6
            assertTrue("decode P50 ${p50}ms above ceiling $DECODE_P50_CEILING_MS", p50 < DECODE_P50_CEILING_MS)
            assertTrue("decode P99 ${p99}ms above ceiling $DECODE_P99_CEILING_MS", p99 < DECODE_P99_CEILING_MS)
        }
    }

    private fun averageLength(cases: List<SwipeCorpus.Case>): Int =
        cases.sumOf { it.path.size } / cases.size

    private inline fun measure(op: (Int) -> Any?): LongArray {
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
