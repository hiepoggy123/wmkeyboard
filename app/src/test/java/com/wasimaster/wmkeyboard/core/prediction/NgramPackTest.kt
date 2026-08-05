package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NgramPackTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Compile entries the way NgramPackDownloadManager does and mmap them
     * back — the round trip the real pipeline takes. */
    private fun compile(name: String, entries: List<Pair<String, Int>>): WordSource {
        val file = File(temp.root, name)
        ByteArrayOutputStream().use { bytes ->
            PackedTrieCodec.write(PackedTrie.of(entries), bytes)
            file.writeBytes(bytes.toByteArray())
        }
        return MappedTrie.open(file) ?: error("failed to map $name")
    }

    private fun pack(): NgramPack {
        val bigrams = compile(
            "bigrams.wmdict",
            listOf(
                NgramPack.key("of", "the") to 204351,
                NgramPack.key("of", "a") to 50000,
                NgramPack.key("in", "the") to 179968,
            ),
        )
        val trigrams = compile(
            "trigrams.wmdict",
            listOf(
                NgramPack.key("one", "of", "the") to 16821,
                NgramPack.key("one", "of", "us") to 900,
            ),
        )
        return NgramPack.of(bigrams, trigrams)
    }

    @Test
    fun countsAndFollowersRoundTrip() {
        val p = pack()
        assertEquals(204351, p.bigramCount("of", "the"))
        assertEquals(204351, p.bigramCount("OF", "The")) // case-folded keys
        assertEquals(0, p.bigramCount("of", "zzz"))
        assertEquals(16821, p.trigramCount("one", "of", "the"))
        // Followers come best-first via the trie's branch-and-bound.
        assertEquals(listOf("the", "a"), p.nextWords("of", 5))
        assertEquals(listOf("the", "us"), p.nextWordsAfter("one", "of", 5))
        assertTrue(p.nextWords("zzz", 5).isEmpty())
        assertTrue(NgramPack.EMPTY.nextWords("of", 5).isEmpty())
    }

    @Test
    fun engineServesPackContextBelowPersonalStores() {
        val lexicon = UserLexicon(null)
        val engine = SuggestionEngine(Trie(), BengaliPhoneticIndex(emptyList()), lexicon)
        engine.ngramPack = pack()
        // Cold: corpus followers fill the empty-composing strip.
        assertEquals(listOf("the", "a"), engine.suggest("", previousWord = "of"))
        // Trigram context is more specific and leads.
        assertEquals(
            "the",
            engine.suggest("", previousWord = "of", previousWord2 = "one").first(),
        )
        // Personal habit beats the corpus outright.
        repeat(3) { lexicon.learnBigram("of", "course") }
        assertEquals("course", engine.suggest("", previousWord = "of").first())
    }

    @Test
    fun packBoostsMidWordCompletionsWithDampedCounts() {
        val dictionary = Trie().apply {
            insert("the", 90)
            insert("then", 100)
        }
        val engine = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        // Raw frequency says "then"; the corpus knows "of the" overwhelmingly.
        assertEquals("then", engine.suggest("th", previousWord = "of").first())
        engine.ngramPack = pack()
        assertEquals("the", engine.suggest("th", previousWord = "of").first())
    }

    @Test
    fun rerankerReadsPackEvidence() {
        val lexicon = UserLexicon(null)
        val dictionary = Trie().apply {
            insert("the", 90)
            insert("then", 100)
        }
        val p = pack()
        val reranker = NgramReranker(
            lexicon, SeedBigrams.EMPTY, { dictionary.frequencyOf(it) }, { p },
        )
        val out = reranker.rerank(
            RerankContext("th", "of", emptyList(), null),
            listOf("then", "the"),
        )
        assertEquals("the", out!!.first())
    }
}
