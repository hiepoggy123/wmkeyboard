// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.MappedTrie
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.PackedTrieCodec
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Latency probe for the *mapped* trie — the one the keyboard actually reads.
 *
 * [PredictionLatencyBench] builds an in-memory [PackedTrie] and measures that,
 * so until this existed nothing in the repo timed a single `ByteBuffer` get.
 * Any change to the `.wmdict` layout — a narrower field, a symbol-table
 * indirection, a computed `childStart` — would have been invisible.
 *
 * Baseline 2026-08-06, v2 format, bundled English (17k words, 40,541 nodes),
 * in the shared Gradle test JVM: `frequencyOf` p50 1.1-1.8us, `complete(3, 10)`
 * p50 8.8us, `contains` p50 0.4us. In an isolated JVM the same build measures
 * 0.38us and 3.5us, and on a 1.64M-word list (4.98M nodes) 0.29us and 21us —
 * completion costs more per call at that size but the per-node reads do not,
 * which is the shape a format change should be judged against.
 *
 * Two things about those numbers. `contains` and `frequencyOf` do identical
 * work and differ fivefold, purely because whichever runs first absorbs the
 * JIT warmup the others then skip; compare like-for-like across runs, never
 * one line against another. And p99 here is dominated by GC pauses from
 * whatever else shares the JVM, which is why the assertions below are on p50.
 *
 * Ceilings are deliberately loose: this is here to catch a change of scale.
 */
class MappedTrieLatencyBench {

    @get:Rule
    val tmp = TemporaryFolder()

    private companion object {
        const val WARMUP = 2_000
        const val MEASURE = 20_000
        /** ~25x over the observed p50, so only a change of scale trips them. */
        const val FREQUENCY_OF_CEILING_US = 50.0
        const val COMPLETE_CEILING_US = 200.0
    }

    @Test
    fun mappedTrieReadsStayFast() {
        val candidates = listOf(File("dictionaries-src/en.txt"), File("app/dictionaries-src/en.txt"))
        val source = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        val entries = source.inputStream().use { DictionaryLoader.loadEntries(it) }

        val file = tmp.newFile("bench.wmdict")
        file.outputStream().use { PackedTrieCodec.write(PackedTrie.of(entries), it) }
        val trie = MappedTrie.open(file) ?: error("MappedTrie rejected its own codec's output")
        println("bench mapped file: ${file.length()} bytes for ${entries.size} words")

        val words = entries.asSequence().map { it.first }.take(4000).toList()
        val prefixes = words.filter { it.length >= 3 }.map { it.take(3) }.distinct()

        val frequencyNs = measure { i -> trie.frequencyOf(words[i % words.size]) }
        val completeNs = measure { i -> trie.complete(prefixes[i % prefixes.size], 10) }
        val walkNs = measure { i -> trie.contains(words[i % words.size]) }

        report("mapped frequencyOf", frequencyNs)
        report("mapped complete(3, 10)", completeNs)
        report("mapped contains", walkNs)

        if (System.getProperty("wmkeyboard.benchAssert") != "false") {
            val frequencyP50 = percentile(frequencyNs, 50.0) / 1000.0
            val completeP50 = percentile(completeNs, 50.0) / 1000.0
            assertTrue(
                "frequencyOf P50 ${frequencyP50}us above ceiling $FREQUENCY_OF_CEILING_US",
                frequencyP50 < FREQUENCY_OF_CEILING_US,
            )
            assertTrue(
                "complete P50 ${completeP50}us above ceiling $COMPLETE_CEILING_US",
                completeP50 < COMPLETE_CEILING_US,
            )
        }
    }

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
        return sorted[((p / 100.0) * (sorted.size - 1)).toInt()]
    }

    private fun report(name: String, samples: LongArray) {
        fun us(v: Long) = String.format(Locale.ROOT, "%.3f", v / 1000.0)
        println(
            "bench $name: p50=${us(percentile(samples, 50.0))}us " +
                "p95=${us(percentile(samples, 95.0))}us p99=${us(percentile(samples, 99.0))}us",
        )
    }
}
