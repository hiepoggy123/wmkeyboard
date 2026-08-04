package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserLexiconTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, "learning/user_lexicon.json")

    @Test
    fun legacySnapshotWithoutNewFieldsLoads() {
        val f = file()
        f.parentFile?.mkdirs()
        f.writeText("""{"words":{"hello":5},"bigrams":{"hello":{"world":2}}}""")
        val lexicon = UserLexicon(f)
        assertEquals(5, lexicon.frequencyOf("hello"))
        assertEquals(listOf("world"), lexicon.nextWords("hello", 3))
        assertEquals(2, lexicon.bigramCount("hello", "world"))
    }

    @Test
    fun roundTripPreservesEverything() {
        val f = file()
        UserLexicon(f).apply {
            learnWord("hello", 3)
            learnBigram("hello", "world")
            learnBigram("hello", "world")
            learnBigram("hello", "there")
            save()
        }
        val back = UserLexicon(f)
        assertEquals(3, back.frequencyOf("hello"))
        assertEquals(2, back.bigramCount("hello", "world"))
        assertEquals(listOf("world", "there"), back.nextWords("hello", 5))
    }

    @Test
    fun nextWordsOrderSurvivesMutation() {
        val lexicon = UserLexicon(null)
        lexicon.learnBigram("a", "x")
        lexicon.learnBigram("a", "y")
        lexicon.learnBigram("a", "y")
        assertEquals(listOf("y", "x"), lexicon.nextWords("a", 5))
        // The cached order must invalidate when counts change.
        lexicon.learnBigram("a", "x")
        lexicon.learnBigram("a", "x")
        assertEquals(listOf("x", "y"), lexicon.nextWords("a", 5))
    }

    @Test
    fun followerListIsCapped() {
        val lexicon = UserLexicon(null)
        // "keep" gets weight so it survives; then flood with singles.
        repeat(5) { lexicon.learnBigram("prev", "keep") }
        for (i in 0 until 40) lexicon.learnBigram("prev", "w$i")
        val followers = lexicon.followerCounts("prev")
        assertTrue("cap exceeded: ${followers.size}", followers.size <= 32)
        assertTrue("keep" in followers)
    }

    @Test
    fun wordLengthAndCountGuards() {
        val lexicon = UserLexicon(null)
        lexicon.learnWord("x".repeat(33))
        assertFalse(lexicon.contains("x".repeat(33)))
        lexicon.learnWord("ok", count = Int.MAX_VALUE)
        lexicon.learnWord("ok", count = Int.MAX_VALUE)
        assertTrue(lexicon.frequencyOf("ok") in 1..1_000_000)
    }

    @Test
    fun capEvictsStaleWordsButKeepsRecentAndSticky() {
        val f = file()
        val lexicon = UserLexicon(f)
        // Old cohort learned at generation 0, then aged far past the cap's
        // half-life by saving repeatedly (each dirty save ticks a generation).
        for (i in 0 until 3000) lexicon.learnWord("old$i")
        repeat(200) {
            lexicon.learnWord("clock")
            lexicon.save()
        }
        lexicon.addWord("cherished", boost = 200) // sticky
        // Fresh higher-count cohort pushes past MAX_WORDS = 10_000; the
        // eviction quota (size - 9000) is smaller than the old cohort, so
        // every evicted word must come from it and no fresh word may die.
        for (i in 0 until 8000) lexicon.learnWord("new$i", count = 2)
        lexicon.save() // triggers compaction
        val kept = UserLexicon(f)
        assertTrue("sticky word evicted", kept.contains("cherished"))
        for (i in 0 until 8000 step 997) {
            assertTrue("fresh word new$i evicted", kept.contains("new$i"))
        }
        val oldSurvivors = kept.allWords().count { it.first.startsWith("old") }
        assertTrue("no stale words were evicted", oldSurvivors < 3000)
        assertTrue(kept.allWords().size <= 10_000)
    }

    @Test
    fun nullFileModeLearnsInMemoryOnly() {
        val lexicon = UserLexicon(null)
        lexicon.learnWord("ghost", 3)
        assertEquals(3, lexicon.frequencyOf("ghost"))
        lexicon.save() // must be a no-op, not a crash
        lexicon.clear()
        assertFalse(lexicon.contains("ghost"))
    }

    @Test
    fun forgetCleansEveryIndex() {
        val lexicon = UserLexicon(null)
        lexicon.learnWord("target", 5)
        lexicon.learnBigram("target", "next")
        lexicon.learnBigram("other", "target")
        lexicon.forget("target")
        assertFalse(lexicon.contains("target"))
        assertTrue(lexicon.nextWords("target", 5).isEmpty())
        assertFalse("target" in lexicon.followerCounts("other"))
    }

    @Test
    fun settingsAppRewriteWithoutWordGenIsTreatedAsFresh() {
        val f = file()
        UserLexicon(f).apply {
            learnWord("mine", 3)
            save()
        }
        // The settings app rewrites words only (no wordGen for the new entry).
        f.writeText("""{"words":{"edited":7},"bigrams":{}}""")
        val back = UserLexicon(f)
        assertEquals(7, back.frequencyOf("edited"))
        assertFalse(back.contains("mine"))
    }
}
