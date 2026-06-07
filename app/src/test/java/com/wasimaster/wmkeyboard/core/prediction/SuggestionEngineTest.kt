package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private fun engine(): SuggestionEngine {
        val dictionary = Trie().apply {
            insert("the", 100)
            insert("they", 90)
            insert("them", 80)
            insert("hello", 70)
            insert("help", 60)
            insert("world", 50)
        }
        val bengali = BengaliPhoneticIndex(
            listOf(
                "আছি" to 6900,
                "আসি" to 2300,
                "ভালো" to 6500,
                "আমি" to 9000,
            )
        )
        return SuggestionEngine(dictionary, bengali, UserLexicon(null))
    }

    @Test fun prefixCompletion() {
        val suggestions = engine().suggest("th", previousWord = null)
        assertEquals("the", suggestions.first())
        assertTrue("they" in suggestions)
    }

    @Test fun capitalizationPreserved() {
        val suggestions = engine().suggest("Th", previousWord = null)
        assertEquals("The", suggestions.first())
    }

    @Test fun typoCorrection() {
        val suggestions = engine().suggest("helo", previousWord = null)
        assertTrue("hello" in suggestions || "help" in suggestions)
    }

    @Test fun autocorrectSuggestsDictionaryWord() {
        assertEquals("hello", engine().shouldAutocorrect("helo"))
        assertNull(engine().shouldAutocorrect("hello")) // already correct
        assertNull(engine().shouldAutocorrect("xy")) // too short
    }

    @Test fun avroPhoneticSiblingWins() {
        val suggestions = engine().suggest("asi", previousWord = null, avroMode = true)
        assertEquals("আছি", suggestions.first())
        assertTrue("আসি" in suggestions)
    }

    @Test fun avroLoanwordWinsOverPhonetics() {
        val dictionary = Trie()
        val loanwords = EnglishBengaliMap.load(
            "keyboard\tকিবোর্ড\nchair\tচেয়ার\n".byteInputStream(Charsets.UTF_8)
        )
        val e = SuggestionEngine(
            dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null), loanwords,
        )
        assertEquals("কিবোর্ড", e.suggest("keyboard", null, avroMode = true).first())
        assertEquals("চেয়ার", e.suggest("chair", null, avroMode = true).first())
    }

    @Test fun avroSentenceWords() {
        val e = engine()
        assertEquals("আমি", e.suggest("ami", null, avroMode = true).first())
        assertEquals("ভালো", e.suggest("valo", null, avroMode = true).first())
        assertEquals("আছি", e.suggest("asi", null, avroMode = true).first())
    }

    @Test fun userLearningPersonalizes() {
        val lexicon = UserLexicon(null)
        val dictionary = Trie().apply { insert("test", 10) }
        val e = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), lexicon)
        lexicon.learnWord("tezos")
        lexicon.learnWord("tezos")
        val suggestions = e.suggest("te", previousWord = null)
        assertEquals("tezos", suggestions.first()) // learned word outranks dictionary
    }

    @Test fun nextWordPrediction() {
        val lexicon = UserLexicon(null)
        lexicon.learnBigram("good", "morning")
        lexicon.learnBigram("good", "morning")
        lexicon.learnBigram("good", "night")
        val e = SuggestionEngine(Trie(), BengaliPhoneticIndex(emptyList()), lexicon)
        val suggestions = e.suggest("", previousWord = "good")
        assertEquals(listOf("morning", "night"), suggestions.take(2))
    }
}
