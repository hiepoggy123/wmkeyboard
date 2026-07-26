package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Round-trip guarantee for the `.wmdict` binary format: a [MappedTrie] opened
 * on a file written by [PackedTrieCodec] must be observationally identical to
 * the [PackedTrie] it was written from — checked on the real shipped English
 * list, mirroring [PackedTrieTest]'s cross-check approach.
 */
class PackedTrieCodecTest {

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

    private fun roundTrip(trie: PackedTrie): MappedTrie {
        val file = tmp.newFile("dict.wmdict")
        file.outputStream().use { PackedTrieCodec.write(trie, it) }
        return MappedTrie.open(file) ?: error("MappedTrie rejected its own codec's output")
    }

    private fun canonical(list: List<Suggestion>): List<Pair<String, Int>> =
        list.map { it.word to it.frequency }.sortedWith(compareBy({ -it.second }, { it.first }))

    @Test
    fun roundTripPreservesEveryQueryOnRealDictionary() {
        val entries = realEntries()
        val packed = PackedTrie.of(entries)
        val mapped = roundTrip(packed)

        assertEquals(packed.wordCount, mapped.wordCount)
        for ((word, _) in entries) {
            assertEquals("frequencyOf(\"$word\")", packed.frequencyOf(word), mapped.frequencyOf(word))
            assertEquals("contains(\"$word\")", packed.contains(word), mapped.contains(word))
        }
        val full = entries.size + 1
        val prefixes = buildList {
            for (a in 'a'..'z') add(a.toString())
            for (a in 'a'..'z') for (b in 'a'..'z') add("$a$b")
        }
        for (p in prefixes) {
            assertEquals(
                "completions diverge for prefix \"$p\"",
                canonical(packed.complete(p, full)),
                canonical(mapped.complete(p, full)),
            )
        }
        for (p in listOf("a", "th", "co", "wor")) {
            assertEquals(
                "top-5 frequencies diverge for \"$p\"",
                packed.complete(p, 5).map { it.frequency },
                mapped.complete(p, 5).map { it.frequency },
            )
        }
    }

    @Test
    fun entriesEnumerationRecoversTheWholeList() {
        val entries = realEntries()
        val packed = PackedTrie.of(entries)
        val mapped = roundTrip(packed)
        // Duplicate words keep their max frequency in the trie, so compare
        // against the folded input, not the raw line list.
        val folded = HashMap<String, Int>()
        for ((w, f) in entries) if (w.isNotEmpty()) folded.merge(w, f, ::maxOf)
        val walked = mapped.entries()
        assertEquals(folded.size, walked.size)
        assertEquals(folded, walked.toMap())
    }

    @Test
    fun headerReportsCounts() {
        val trie = PackedTrie.of(listOf("cat" to 50, "car" to 90, "care" to 10))
        val file = tmp.newFile("small.wmdict")
        file.outputStream().use { PackedTrieCodec.write(trie, it) }
        val header = PackedTrieCodec.readHeader(file)
        assertEquals(3, header.wordCount)
        assertEquals(header.nodeCount - 1, header.edgeCount)
    }

    @Test
    fun surrogatePairsAndNonLatinRoundTrip() {
        val entries = listOf("বাংলা" to 7, "কলম" to 3, "😀word" to 2, "কল" to 9)
        val mapped = roundTrip(PackedTrie.of(entries))
        for ((w, f) in entries) assertEquals(f, mapped.frequencyOf(w))
        assertEquals(
            canonical(PackedTrie.of(entries).complete("ক", 10)),
            canonical(mapped.complete("ক", 10)),
        )
    }

    @Test
    fun corruptFilesAreRejectedNotCrashed() {
        val garbage = tmp.newFile("garbage.wmdict")
        garbage.writeBytes(ByteArray(64) { it.toByte() })
        assertNull(MappedTrie.open(garbage))
        assertTrue(runCatching { PackedTrieCodec.readHeader(garbage) }.exceptionOrNull() is IOException)

        val truncated = tmp.newFile("truncated.wmdict")
        val trie = PackedTrie.of(listOf("cat" to 50, "car" to 90))
        val bytes = java.io.ByteArrayOutputStream().also { PackedTrieCodec.write(trie, it) }.toByteArray()
        truncated.writeBytes(bytes.copyOf(bytes.size / 2))
        assertNull(MappedTrie.open(truncated))

        assertNull(MappedTrie.open(File(tmp.root, "missing.wmdict")))
    }
}
