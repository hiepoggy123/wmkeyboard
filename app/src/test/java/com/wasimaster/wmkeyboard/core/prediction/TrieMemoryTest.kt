package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import org.junit.Test

/**
 * Not a behavioural test — a one-shot heap measurement to decide whether the
 * bundled English [Trie] is worth repacking (Phase 2 of the perf roadmap).
 * Builds the real shipped dictionary, measures its retained size on the JVM
 * (a reasonable proxy for ART), and writes a report to a file so the result
 * survives Gradle's stdout capture. Always passes.
 */
class TrieMemoryTest {

    private fun assetFile(name: String): File {
        // Gradle runs unit tests with the module dir (app/) as cwd; fall back
        // to the repo root in case it is invoked from there.
        val candidates = listOf(
            File("src/main/assets/dictionaries/$name"),
            File("app/src/main/assets/dictionaries/$name"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("dictionary asset not found: $name (cwd=${File(".").absolutePath})")
    }

    private fun usedMemory(): Long {
        val rt = Runtime.getRuntime()
        repeat(6) {
            System.gc()
            System.runFinalization()
            Thread.sleep(30)
        }
        return rt.totalMemory() - rt.freeMemory()
    }

    @Test
    fun measureBundledEnglishTrie() {
        val entries = assetFile("en.txt").inputStream().use { DictionaryLoader.loadEntries(it) }

        // Structural stats, independent of Trie internals: a trie node exists
        // for every distinct prefix (plus the root).
        val prefixes = HashSet<String>()
        var totalChars = 0L
        for ((word, _) in entries) {
            totalChars += word.length
            for (i in 1..word.length) prefixes.add(word.substring(0, i))
        }
        val nodeCount = prefixes.size + 1 // + root

        // Retained heap of the node-based Trie: delta across building it, with
        // the parsed entries already resident so only the Trie's own footprint
        // is counted. The reference is kept live past the measurement.
        val beforeTrie = usedMemory()
        val trie = Trie()
        for ((word, frequency) in entries) trie.insert(word, frequency)
        val afterTrie = usedMemory()
        val retainedTrie = afterTrie - beforeTrie
        val sanityTrie = trie.complete("th", 3).size

        // Retained heap of the PackedTrie (Phase 2), measured the same way. The
        // node Trie stays referenced, so this delta is PackedTrie's own cost.
        val beforePacked = usedMemory()
        val packed = PackedTrie.of(entries)
        val afterPacked = usedMemory()
        val retainedPacked = afterPacked - beforePacked
        val sanityPacked = packed.complete("th", 3).size

        val saved = retainedTrie - retainedPacked
        val pct = if (retainedTrie > 0) 100 - retainedPacked * 100 / retainedTrie else 0

        val report = buildString {
            appendLine("=== Bundled English lexicon heap: Trie vs PackedTrie ===")
            appendLine("words           : ${entries.size}")
            appendLine("trie nodes      : $nodeCount")
            appendLine("total word chars: $totalChars")
            appendLine("avg nodes/word  : ${"%.2f".format(nodeCount.toDouble() / entries.size)}")
            appendLine("---")
            appendLine("Trie  retained  : ${retainedTrie / 1024} KiB (${"%.2f".format(retainedTrie / 1048576.0)} MiB), ${if (nodeCount > 0) retainedTrie / nodeCount else 0} B/node")
            appendLine("Packed retained : ${retainedPacked / 1024} KiB (${"%.2f".format(retainedPacked / 1048576.0)} MiB), ${if (nodeCount > 0) retainedPacked / nodeCount else 0} B/node")
            appendLine("---")
            appendLine("saved           : ${saved / 1024} KiB (${"%.2f".format(saved / 1048576.0)} MiB, $pct% smaller)")
            appendLine("(sanity: th-completions trie=$sanityTrie packed=$sanityPacked; JVM proxy for ART, ±noise)")
        }
        println(report)
        File(System.getProperty("trie.report.out") ?: "trie-memory-report.txt").writeText(report)
    }
}
