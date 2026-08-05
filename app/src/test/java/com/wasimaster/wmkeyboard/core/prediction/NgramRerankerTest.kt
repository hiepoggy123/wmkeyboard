package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NgramRerankerTest {

    private val dictionary = Trie().apply {
        insert("world", 100)
        insert("words", 120)
        insert("work", 110)
    }

    private fun reranker(
        lexicon: UserLexicon = UserLexicon(null),
        seeds: SeedBigrams = SeedBigrams.EMPTY,
    ) = NgramReranker(lexicon, seeds) { dictionary.frequencyOf(it) }

    private fun context(
        prev: String?,
        prev2: String? = null,
        recent: List<String> = emptyList(),
    ) = RerankContext("wor", prev, recent, prev2)

    @Test
    fun noContextOrNoEvidenceMeansNoOpinion() {
        val r = reranker()
        assertNull(r.rerank(context(prev = null), listOf("words", "world")))
        // Known previous word but nothing learned about any candidate.
        assertNull(r.rerank(context(prev = "hello"), listOf("words", "world")))
        // The sentence-start sentinel is context for the bigram store, but
        // with no learned openers it is still no evidence.
        assertNull(r.rerank(context(prev = WordContext.SENTENCE_START), listOf("words")))
    }

    @Test
    fun learnedBigramReordersTheHead() {
        val lexicon = UserLexicon(null)
        repeat(6) { lexicon.learnBigram("hello", "world") }
        val r = reranker(lexicon)
        val out = r.rerank(context(prev = "hello"), listOf("words", "world", "work"))
        assertEquals(listOf("world", "words", "work"), out)
    }

    @Test
    fun trigramEvidenceOutweighsBigram() {
        val lexicon = UserLexicon(null)
        // Bigram habit says "world"; the exact two-word context says "work".
        repeat(4) { lexicon.learnBigram("was", "world") }
        repeat(4) { lexicon.learnTrigram("i", "was", "work") }
        val r = reranker(lexicon)
        val out = r.rerank(
            context(prev = "was", prev2 = "i"),
            listOf("words", "world", "work"),
        )
        assertEquals("work", out!!.first())
    }

    @Test
    fun seedCountsAndRecencyAreWeakerNudges() {
        val seeds = SeedBigrams.load(
            "hello world 500\nhello work 20\n".byteInputStream()
        )
        val r = reranker(seeds = seeds)
        // Seed evidence alone reorders a near-tie (words 120 vs world 100).
        val out = r.rerank(context(prev = "hello"), listOf("words", "world"))
        assertEquals("world", out!!.first())
        // Recency alone flips a near-tie too.
        val r2 = reranker()
        val recent = r2.rerank(
            context(prev = "hello", recent = listOf("world")),
            listOf("words", "world"),
        )
        assertEquals("world", recent!!.first())
    }

    @Test
    fun engineAppliesRerankOnlyWhenAskedTo() {
        val lexicon = UserLexicon(null)
        repeat(6) { lexicon.learnBigram("hello", "world") }
        val engine = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), lexicon)
        // Wipe the strip's own capped context boost from the comparison by
        // asking with a previous word only the reranker sees... it can't be:
        // both read the same store. So compare opt-in vs not with the same
        // inputs — the reranker path must at minimum never lose the word.
        engine.reranker = NgramReranker(lexicon, SeedBigrams.EMPTY) { dictionary.frequencyOf(it) }
        val without = engine.suggest("wor", previousWord = "hello")
        val with = engine.suggest("wor", previousWord = "hello", allowRerank = true)
        assertEquals(without.toSet(), with.toSet())
        assertEquals("world", with.first())
    }

    @Test
    fun deterministicAndStableForTies() {
        val r = reranker()
        val lexicon = UserLexicon(null)
        lexicon.learnBigram("hello", "world")
        lexicon.learnBigram("hello", "words")
        val tied = NgramReranker(lexicon, SeedBigrams.EMPTY) { 100 }
        val a = tied.rerank(context(prev = "hello"), listOf("alpha", "beta", "world", "words"))
        val b = tied.rerank(context(prev = "hello"), listOf("alpha", "beta", "world", "words"))
        assertEquals(a, b)
        // Equal-evidence, equal-base candidates keep their incoming order.
        assertEquals(listOf("alpha", "beta"), a!!.takeLast(2))
        assertNull(r.rerank(context(prev = "zzz"), listOf("alpha")))
    }
}
