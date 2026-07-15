package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests locking [PackedTrie] to [Trie]'s query semantics:
 * the packed rewrite is only safe if it is observationally identical to the
 * node trie it replaces. Cross-checked against the real shipped dictionary so
 * the guarantee holds on production data, not just toy inputs.
 */
class PackedTrieTest {

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("src/main/assets/dictionaries/en.txt"),
            File("app/src/main/assets/dictionaries/en.txt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    private fun buildBoth(entries: List<Pair<String, Int>>): Pair<Trie, PackedTrie> {
        val trie = Trie().apply { for ((w, f) in entries) insert(w, f) }
        return trie to PackedTrie.of(entries)
    }

    /** Order-independent view of a completion list: two results are equal iff
     * they contain the same (word, frequency) pairs. Tolerates the arbitrary
     * tie-order both tries inherit from their heap. */
    private fun canonical(list: List<Suggestion>): List<Pair<String, Int>> =
        list.map { it.word to it.frequency }.sortedWith(compareBy({ -it.second }, { it.first }))

    @Test
    fun completeMatchesNodeTrieAcrossPrefixes() {
        val entries = realEntries()
        val (trie, packed) = buildBoth(entries)
        // A limit above the total word count means no truncation, so the full
        // reachable (word, frequency) set is compared for every prefix — the
        // strongest equivalence check, free of tie-at-boundary ambiguity.
        val full = entries.size + 1
        val alphabet = ('a'..'z') + ('A'..'Z')
        val prefixes = buildList {
            for (a in alphabet) add(a.toString())
            for (a in 'a'..'z') for (b in 'a'..'z') add("$a$b")
        }
        for (p in prefixes) {
            assertEquals(
                "completions diverge for prefix \"$p\"",
                canonical(trie.complete(p, full)),
                canonical(packed.complete(p, full)),
            )
        }
    }

    @Test
    fun smallLimitPicksSameTopFrequencies() {
        val entries = realEntries()
        val (trie, packed) = buildBoth(entries)
        // At a truncating limit the two may pick different tied words, but the
        // frequencies of the chosen set — and their descending order — must
        // match, and every result must be a genuine completion.
        for (p in listOf("a", "th", "co", "re", "in", "wor", "sug", "key")) {
            val t = trie.complete(p, 5)
            val k = packed.complete(p, 5)
            assertEquals("freq ranking for \"$p\"", t.map { it.frequency }, k.map { it.frequency })
            assertTrue("not sorted for \"$p\"", k.zipWithNext().all { (a, b) -> a.frequency >= b.frequency })
            for (s in k) {
                assertTrue("\"${s.word}\" is not a completion of \"$p\"", s.word.startsWith(p))
                assertEquals("freq mismatch for \"${s.word}\"", s.frequency, packed.frequencyOf(s.word))
            }
        }
    }

    @Test
    fun frequencyAndContainsAreExactForEveryWord() {
        val entries = realEntries()
        val (trie, packed) = buildBoth(entries)
        for ((word, _) in entries) {
            assertEquals("frequencyOf(\"$word\")", trie.frequencyOf(word), packed.frequencyOf(word))
            assertEquals("contains(\"$word\")", trie.contains(word), packed.contains(word))
        }
        // Non-members and prefixes-that-aren't-words resolve identically.
        for (w in listOf("", "zzzzzz", "th", "qwertyuiop", "the")) {
            assertEquals("frequencyOf(\"$w\")", trie.frequencyOf(w), packed.frequencyOf(w))
            assertEquals("contains(\"$w\")", trie.contains(w), packed.contains(w))
        }
    }

    @Test
    fun controlledTieCaseRanksByFrequency() {
        // Words sharing a prefix with hand-picked frequencies: the top-2 must
        // be the two highest, in descending order, on both structures.
        val entries = listOf("cat" to 50, "car" to 90, "care" to 10, "cart" to 90, "dog" to 5)
        val (trie, packed) = buildBoth(entries)
        assertEquals(canonical(trie.complete("ca", 10)), canonical(packed.complete("ca", 10)))
        val top2 = packed.complete("ca", 2)
        assertEquals(listOf(90, 90), top2.map { it.frequency })
        assertEquals(0, packed.frequencyOf("ca")) // a prefix, not a word
        assertEquals(10, packed.frequencyOf("care"))
    }

    @Test
    fun emptyTrieIsWellBehaved() {
        val empty = PackedTrie.EMPTY
        assertEquals(emptyList<Suggestion>(), empty.complete("a", 5))
        assertEquals(0, empty.frequencyOf("a"))
        assertTrue(!empty.contains("a"))
    }
}
