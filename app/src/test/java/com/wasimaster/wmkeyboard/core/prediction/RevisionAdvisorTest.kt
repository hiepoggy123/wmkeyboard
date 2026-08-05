package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RevisionAdvisorTest {

    private fun advisor(
        seeds: SeedBigrams = SeedBigrams.EMPTY,
        lexicon: UserLexicon = UserLexicon(null),
    ) = RevisionAdvisor(lexicon, seeds)

    @Test
    fun seedEvidenceFlagsTheClassicSlip() {
        val seeds = SeedBigrams.load(
            "they're going 400\ntheir car 300\n".byteInputStream()
        )
        val a = advisor(seeds)
        // "their going": the sibling dominates, the typed word has nothing.
        assertEquals("they're", a.advise("their", "going"))
        // "their car" has its own support — no chip, ever.
        assertNull(a.advise("their", "car"))
    }

    @Test
    fun userHabitAloneCanTestify() {
        val lexicon = UserLexicon(null)
        repeat(8) { lexicon.learnBigram("you're", "right") }
        assertEquals("you're", advisor(lexicon = lexicon).advise("your", "right"))
    }

    @Test
    fun weakEvidenceStaysSilent() {
        // Below the corpus minimum: 20 sightings is noise at that scale.
        val faint = SeedBigrams.load("they're going 20\n".byteInputStream())
        assertNull(advisor(faint).advise("their", "going"))
        // A real habit for the typed word keeps the ratio under dominance.
        val contested = SeedBigrams.load(
            "they're going 400\ntheir going 100\n".byteInputStream()
        )
        assertNull(advisor(contested).advise("their", "going"))
        // Two personal uses is not yet a habit.
        val lexicon = UserLexicon(null)
        repeat(2) { lexicon.learnBigram("you're", "right") }
        assertNull(advisor(lexicon = lexicon).advise("your", "right"))
    }

    @Test
    fun nonConfusablesAndSentinelsNeverAdvise() {
        val seeds = SeedBigrams.load("hello world 9999\n".byteInputStream())
        val a = advisor(seeds)
        assertNull(a.advise("hello", "world"))
        assertNull(a.advise(null, "world"))
        assertNull(a.advise("their", null))
        assertNull(a.advise(WordContext.SENTENCE_START, "going"))
        assertNull(a.advise("their", "going!"))
    }
}
