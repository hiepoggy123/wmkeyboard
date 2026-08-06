// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GestureDecoder
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
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
 * Two shapes are measured, and the second is the one that matters:
 *
 *  - **`decode`** is one finished swipe, the cost paid once on finger-up.
 *  - **`stroke`** is a whole gesture's worth of live previews — the decoder is
 *    re-run against a growing prefix of the path several times per second while
 *    the finger is still down. That is where a slow decoder is actually felt,
 *    and it costs a multiple of a single decode.
 */
class GestureLatencyBench {

    private companion object {
        const val WARMUP = 30
        const val MEASURE = 200

        /** Live previews issued across one stroke, matching the production cadence. */
        const val PREVIEWS_PER_STROKE = 6

        /**
         * Loose ceilings — regressions, not noise, should trip them. Baseline
         * 2026-08-06 on a desktop JVM with the pre-rework decoder over the
         * bundled 17k-word list: decode p50 ~1.4 ms, so these leave wide
         * headroom for slower machines and for a larger downloaded dictionary.
         */
        const val DECODE_P50_CEILING_MS = 25.0
        const val DECODE_P99_CEILING_MS = 90.0
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
    fun glideHotPathStaysFast() {
        val entries = realEntries()
        val decoder = GestureDecoder(SwipeCorpus.keyCenters(), SwipeCorpus.KEY_WIDTH)
        val cases = SwipeCorpus(7L).generate(entries, SwipeCorpus.Noise.TYPICAL, 100)

        val decodeNs = measure { i ->
            decoder.decode(cases[i % cases.size].path, entries)
        }
        // The live-preview shape: the same stroke decoded repeatedly against a
        // lengthening prefix, which is what the finger-down path really costs.
        val strokeNs = measure { i ->
            val path = cases[i % cases.size].path
            for (step in 1..PREVIEWS_PER_STROKE) {
                val upTo = path.size * step / PREVIEWS_PER_STROKE
                if (upTo >= 3) decoder.decode(path.subList(0, upTo), entries)
            }
        }

        report("decode(full path)", decodeNs)
        report("stroke($PREVIEWS_PER_STROKE previews)", strokeNs)
        println("lexicon=${entries.size} words, sampled path len=${averageLength(cases)}")

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
