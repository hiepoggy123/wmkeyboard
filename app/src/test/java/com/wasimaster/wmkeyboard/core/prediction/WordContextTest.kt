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

    @Test fun aCombiningMarkIsPartOfTheWordNotABoundary() {
        // হয়েছে is হ য ় ে ছ ে — it *ends* in a vowel sign, and two of its six
        // characters are combining marks. Asking Char.isLetter() where a word
        // ends therefore truncates it: this used to hand back a bare ছ, so
        // every Bengali bigram was keyed on a single consonant and the corpus
        // pack could never match anything. Devanagari, Tamil, Thai, Arabic and
        // Hebrew all spell words this way too.
        assertEquals("হয়েছে", before("হয়েছে "))
        assertEquals("করা", before("করা "))
        assertEquals("কিয়া", before("কিয়া, "))
        assertEquals("किया", before("किया "))       // Devanagari matra
        assertEquals("ก่อน", before("ก่อน "))          // Thai tone mark

        // Still inside the word while the mark is the last thing typed.
        assertNull(before("হয়েছে"))
        assertNull(before("किया"))
    }

    @Test fun contextIsReadInTheStoresOwnSpelling() {
        // Field text is whatever some keyboard or paste left there, so both
        // spellings of য় turn up. They must key the same word — see WordKey.
        // These two lines look identical and differ only in their bytes, and
        // tools fold the precomposed one into the decomposed one given half a
        // chance — which is exactly what happened while this test was being
        // written, and what the assertFalse caught. Keep the assertion: without
        // it a fold leaves every check below passing against itself.
        val decomposed = "হয়েছে"
        val precomposed = "হয়েছে"
        assertFalse("the two spellings must differ as strings", decomposed == precomposed)
        assertEquals(decomposed, before("$precomposed "))
        assertEquals(before("$decomposed "), before("$precomposed "))
    }

    @Test fun bothContextWordsSurviveCombiningMarks() {
        val (prev1, prev2) = WordContext.lastTwoWords("করা হয়েছে ", enders)
        assertEquals("হয়েছে", prev1)
        assertEquals("করা", prev2)
    }
}
