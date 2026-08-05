package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyBeamSearchTest {

    private val search = FuzzyBeamSearch()

    private fun source(trie: WordSource, logWeight: Double = 0.0) =
        FuzzyBeamSearch.WalkSource(trie.walkers().single(), logWeight, FuzzyBeamSearch.Tier.DICTIONARY)

    private fun run(
        sources: List<FuzzyBeamSearch.WalkSource>,
        typed: String,
        limit: Int = 5,
        maxEdits: Int = FuzzyBeamSearch.defaultMaxEdits(typed.length),
    ): List<FuzzyBeamSearch.ScoredCandidate> =
        search.search(sources, typed, KeyProximity.QWERTY, limit, BeamWorkspace(), maxEdits)

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
    fun doubledLetterDeletionCostsTheCheapTier() {
        // "helllo": the extra l doubles its neighbours — a double-strike or
        // hold-repeat slip, priced like a transposition rather than an
        // arbitrary stray character.
        val trie = PackedTrie.of(listOf("hello" to 100))
        val got = run(listOf(source(trie)), "helllo").first()
        assertEquals("hello", got.word)
        assertEquals(FuzzyBeamSearch.COST_DELETE_DOUBLED, got.editCost, 1e-9)
    }

    @Test
    fun missedDoublingInsertsAtAdjacentCost() {
        // "runing" -> "running": the inserted edge repeats the char just
        // typed. 'n' is nowhere near 'i' on QWERTY, so without the
        // same-char rule this would be priced as a far insertion.
        val trie = PackedTrie.of(listOf("running" to 100))
        val got = run(listOf(source(trie)), "runing").first()
        assertEquals("running", got.word)
        assertEquals(FuzzyBeamSearch.COST_INSERT_ADJACENT, got.editCost, 1e-9)
    }

    @Test
    fun zeroEditWalkMatchesClassicCompletion() {
        val trie = PackedTrie.of(realEntries())
        for (prefix in listOf("th", "he", "wo", "pre", "a")) {
            val classic = trie.complete(prefix, 10)
            val beam = run(listOf(source(trie)), prefix, limit = 10, maxEdits = 0)
                .filter { it.word.startsWith(prefix) }
            // Same words in the same frequency order (beam score is a
            // monotonic transform of frequency when edit cost is zero).
            assertEquals(
                "completion parity for '$prefix'",
                classic.map { it.word },
                beam.take(classic.size).map { it.word },
            )
        }
    }

    @Test
    fun norvigCandidatesAllSurfaceWithMatchingScores() {
        val entries = realEntries()
        val trie = PackedTrie.of(entries)
        val typos = listOf("helo", "wrold", "teh", "beleive", "recieve", "adress")
        for (typed in typos) {
            val beam = run(listOf(source(trie)), typed, limit = 400, maxEdits = 1)
                .associateBy { it.word }
            for ((candidate, weight) in norvigEdits1(typed, KeyProximity.QWERTY)) {
                val freq = trie.frequencyOf(candidate)
                if (freq <= 0 || candidate == typed) continue
                val expectedCost = if (candidate.startsWith(typed)) 0.0 else -ln(weight)
                val expected = ln(1.0 + freq) - expectedCost
                val got = beam[candidate]
                assertTrue("'$typed' -> '$candidate' missing from beam", got != null)
                assertTrue(
                    "'$typed' -> '$candidate' score ${got!!.score} below expected $expected",
                    got.score >= expected - 1e-6,
                )
            }
        }
    }

    @Test
    fun twoAdjacentSlipsAreReachable() {
        val trie = Trie().apply { insert("hello", 100) }
        // g->h and w->e are both QWERTY-adjacent slips: total cost 0.21,
        // within MAX_EDIT_COST, needs the 2-edit budget.
        val results = run(listOf(source(trie)), "gwllo")
        assertEquals("hello", results.single().word)
        assertEquals(2, results.single().edits)
    }

    @Test
    fun twoFarSubstitutionsAreNotReachable() {
        val trie = Trie().apply { insert("hello", 100) }
        // z->e and q->l are far substitutions mid-word (trailing errors would
        // be rescued by free completion): 2 x 1.609 exceeds MAX_EDIT_COST, and
        // every delete/insert detour needs a third edit.
        assertTrue(run(listOf(source(trie)), "hzlqo").isEmpty())
    }

    @Test
    fun correctionAndCompletionUnify() {
        val trie = Trie().apply {
            insert("the", 100)
            insert("thermometer", 40)
        }
        val words = run(listOf(source(trie)), "teh", limit = 5).map { it.word }
        // "teh" transposes to "the" and then completes past it.
        assertTrue("the" in words)
        assertTrue("thermometer" in words)
    }

    @Test
    fun nonAsciiCorrectionsComeFromTrieEdges() {
        val trie = Trie().apply { insert("привет", 50) }
        // Cyrillic word with one substituted letter: the legacy ASCII alphabet
        // could never generate this candidate; trie-edge substitution can.
        val typed = "правет"
        val results = run(listOf(source(trie)), typed)
        assertEquals("привет", results.single().word)
    }

    @Test
    fun sourceOrderDoesNotChangeResults() {
        val a = PackedTrie.of(listOf("hello" to 70, "help" to 60))
        val b = Trie().apply {
            insert("helm", 40)
            insert("hell", 30)
        }
        val forward = run(listOf(source(a), source(b, ln(2.0))), "helo", limit = 8)
        val reversed = run(listOf(source(b, ln(2.0)), source(a)), "helo", limit = 8)
        assertEquals(forward.map { it.word to it.score }, reversed.map { it.word to it.score })
    }

    @Test
    fun maxMergeAcrossSourcesKeepsBestScoreAndTier() {
        val dict = PackedTrie.of(listOf("hello" to 10))
        val user = Trie().apply { insert("hello", 10) }
        val userSource = FuzzyBeamSearch.WalkSource(
            user.walkers().single(), ln(500.0), FuzzyBeamSearch.Tier.USER,
        )
        val results = run(listOf(source(dict), userSource), "hello", limit = 4)
        val hello = results.single { it.word == "hello" }
        // The user-weighted path wins the merge and keeps its tier.
        assertEquals(FuzzyBeamSearch.Tier.USER, hello.tier)
        assertTrue(abs(hello.score - (ln(500.0) + ln(11.0))) < 1e-9)
    }

    /**
     * Test-local copy of the legacy Norvig edits-1 generator (deleted from the
     * engine): candidate -> best multiplicative weight.
     */
    private fun norvigEdits1(word: String, proximity: KeyProximity): Map<String, Double> {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val result = HashMap<String, Double>()
        fun emit(candidate: String, weight: Double) {
            if (candidate != word) result.merge(candidate, weight, ::maxOf)
        }
        for (i in word.indices) {
            emit(word.removeRange(i, i + 1), 0.7)
        }
        for (i in 0 until word.length - 1) {
            val sb = StringBuilder(word)
            val tmp = sb[i]
            sb[i] = sb[i + 1]
            sb[i + 1] = tmp
            emit(sb.toString(), 0.9)
        }
        for (i in word.indices) {
            for (c in alphabet) {
                if (c == word[i]) continue
                val weight = if (proximity.areAdjacent(word[i], c)) 0.9 else 0.2
                emit(word.substring(0, i) + c + word.substring(i + 1), weight)
            }
        }
        for (i in 0..word.length) {
            for (c in alphabet) {
                val nearPrev = i > 0 && proximity.areAdjacent(word[i - 1], c)
                val nearNext = i < word.length && proximity.areAdjacent(word[i], c)
                val weight = if (nearPrev || nearNext) 0.7 else 0.25
                emit(word.substring(0, i) + c + word.substring(i), weight)
            }
        }
        return result
    }
}
