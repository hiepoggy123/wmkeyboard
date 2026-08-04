package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordContextTest {

    private val enders = charArrayOf('.', '!', '?', '।') // incl. Bengali danda

    private fun before(text: String?) = WordContext.completedWordBefore(text, enders)

    @Test fun fullStopYieldsSentenceStart() {
        assertEquals(WordContext.SENTENCE_START, before("Hello. "))
        assertEquals(WordContext.SENTENCE_START, before("Hello! "))
        assertEquals(WordContext.SENTENCE_START, before("Hello?  "))
        assertEquals(WordContext.SENTENCE_START, before("ভালো। "))
    }

    @Test fun commaKeepsTheWord() {
        assertEquals("hello", before("Hello, "))
        assertEquals("hello", before("Hello "))
        assertEquals("hello", before("Hello; "))
    }

    @Test fun midWordAndEmptyAreNull() {
        assertNull(before("Hel"))
        assertNull(before(""))
        assertNull(before(null))
        assertNull(before("Hello5")) // digits count as still-inside-a-token
    }

    @Test fun enderTailIsASentenceStartEvenWithoutAPrecedingWord() {
        // "... " with nothing before it: whatever comes next opens a
        // sentence, so the sentinel applies here too.
        assertEquals(WordContext.SENTENCE_START, before("... "))
        assertEquals(WordContext.SENTENCE_START, before("Done... "))
        // A tail of non-ender punctuation with no word stays null.
        assertNull(before(", "))
    }

    @Test fun abbreviationLimitationIsDocumentedBehavior() {
        // "Dr. " reads as a sentence start — accepted limitation.
        assertEquals(WordContext.SENTENCE_START, before("Dr. "))
    }

    @Test fun lastTwoWordsRecoversBothOrDegrades() {
        fun two(text: String?) = WordContext.lastTwoWords(text, enders)
        assertEquals("was" to "i", two("I was "))
        assertEquals("was" to "i", two("I was, "))
        // A sentence ender between the two words kills prev2.
        assertEquals("was" to null, two("Stop. Was "))
        // Sentence start: prev1 is the sentinel, prev2 always null.
        assertEquals(WordContext.SENTENCE_START to null, two("I was. "))
        // Single word: no prev2.
        assertEquals("hello" to null, two("Hello "))
        // Mid-word: nothing.
        assertEquals(null to null, two("Hel"))
    }

    @Test fun sentinelIsRecognizableAndUntypeable() {
        assertTrue(WordContext.isSentinel(WordContext.SENTENCE_START))
        assertFalse(WordContext.isSentinel("hello"))
        assertFalse(WordContext.isSentinel(null))
        assertTrue(WordContext.SENTENCE_START.first().code == 1)
    }

    @Test fun sentinelWorksAsBigramContextButIsNeverOffered() {
        val lexicon = UserLexicon(null)
        lexicon.learnBigram(WordContext.SENTENCE_START, "the")
        lexicon.learnBigram(WordContext.SENTENCE_START, "the")
        lexicon.learnBigram(WordContext.SENTENCE_START, "when")
        assertEquals(listOf("the", "when"), lexicon.nextWords(WordContext.SENTENCE_START, 5))
        // learnWord with the sentinel never lands (non-letter guard is the
        // trim in the service, but the lexicon itself must also be safe).
        lexicon.learnWord(WordContext.SENTENCE_START)
        val engine = SuggestionEngine(
            Trie(),
            com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex(emptyList()),
            lexicon,
        )
        val offers = engine.suggest("", previousWord = WordContext.SENTENCE_START)
        assertEquals(listOf("the", "when"), offers)
        assertFalse(offers.any { WordContext.isSentinel(it) })
    }
}
