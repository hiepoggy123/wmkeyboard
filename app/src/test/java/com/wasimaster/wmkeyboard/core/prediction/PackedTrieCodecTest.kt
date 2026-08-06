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

    private var written = 0

    /** The last file [roundTrip] wrote, so a test can inspect its header. */
    private lateinit var lastFile: File

    private fun roundTrip(trie: PackedTrie): MappedTrie {
        // Numbered: a test that round-trips several tries would otherwise ask
        // TemporaryFolder for the same name twice, which it refuses.
        val file = tmp.newFile("dict${written++}.wmdict")
        file.outputStream().use { PackedTrieCodec.write(trie, it) }
        lastFile = file
        return MappedTrie.open(file) ?: error("MappedTrie rejected its own codec's output")
    }

    /** The flags word of the last file written, read straight off the header. */
    private fun lastFlags(): Int {
        val head = lastFile.readBytes()
        return (head[6].toInt() and 0xFF shl 8) or (head[7].toInt() and 0xFF)
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
        // against the folded input, not the raw line list. Frequencies are
        // snapped to the format's minifloat grid on the way in; rounding is
        // monotone, so folding before or after would give the same answer.
        val folded = HashMap<String, Int>()
        for ((w, f) in entries) if (w.isNotEmpty()) folded.merge(w, FrequencyCodec.round(f), ::maxOf)
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

    /**
     * The counted `childStart` keeps one byte per node, so a node with more
     * children than a byte can count forces the codec back to a plain `i32`
     * array. No shipped Latin or Bengali list comes near it — the root of the
     * English list has 26 children — but a Hangul word list has thousands, and
     * that fallback is the branch nothing else here exercises.
     */
    @Test
    fun aNodeWithMoreChildrenThanAByteCanCountStillRoundTrips() {
        // 300 distinct second characters hang off "a", from a block with no
        // surrogates so one code unit is one child.
        val entries = (0 until 300).map { "a" + (0x4E00 + it).toChar() to it + 1 }
        val packed = PackedTrie.of(entries)
        val mapped = roundTrip(packed)

        // Pins the branch under test: if a future change made this fit in a
        // byte after all, the assertions below would stop covering the
        // fallback and nothing else would notice.
        assertEquals(
            "expected the i32 childStart fallback",
            0,
            lastFlags() and PackedTrieCodec.FLAG_DEGREE_U8,
        )
        assertEquals(300, mapped.wordCount)
        for ((word, frequency) in entries) {
            assertEquals("frequencyOf(\"$word\")", frequency, mapped.frequencyOf(word))
            assertTrue("contains(\"$word\")", mapped.contains(word))
        }
        assertEquals(canonical(packed.complete("a", 300)), canonical(mapped.complete("a", 300)))
        assertEquals(entries.toMap(), mapped.entries().toMap())
    }

    /**
     * The counted path only pays off past the first checkpoint, and the sum it
     * does is over the nodes since that checkpoint — so the interesting sizes
     * are the ones straddling a stride boundary, where an off-by-one in either
     * the writer's checkpoints or the reader's loop shows up.
     */
    @Test
    fun triesStraddlingACheckpointBoundaryRoundTrip() {
        val stride = PackedTrieCodec.CHECKPOINT_STRIDE
        for (count in listOf(stride - 1, stride, stride + 1, stride * 2, stride * 3 + 7)) {
            val entries = (0 until count).map { "w%04d".format(it) to it + 1 }
            val packed = PackedTrie.of(entries)
            val mapped = roundTrip(packed)
            assertEquals(
                "$count words: expected the counted childStart",
                PackedTrieCodec.FLAG_DEGREE_U8,
                lastFlags() and PackedTrieCodec.FLAG_DEGREE_U8,
            )
            for ((word, frequency) in entries) {
                assertEquals("$count words: frequencyOf(\"$word\")", frequency, mapped.frequencyOf(word))
            }
            assertEquals("$count words: full walk", entries.toMap(), mapped.entries().toMap())
            assertEquals(
                "$count words: completion",
                canonical(packed.complete("w", count)),
                canonical(mapped.complete("w", count)),
            )
        }
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
