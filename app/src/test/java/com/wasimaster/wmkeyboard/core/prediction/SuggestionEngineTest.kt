package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
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

    @Test fun completesContactEmails() {
        val e = engine().apply {
            contactEmails = ContactEmails.fromAddresses(listOf("john.doe@gmail.com"))
        }
        assertTrue("john.doe@gmail.com" in e.suggest("john", previousWord = null))
        // A single letter must not list the whole address book.
        assertTrue("john.doe@gmail.com" !in e.suggest("j", previousWord = null))
    }

    @Test fun capitalizationPreserved() {
        val suggestions = engine().suggest("Th", previousWord = null)
        assertEquals("The", suggestions.first())
    }

    @Test fun typoCorrection() {
        val suggestions = engine().suggest("helo", previousWord = null)
        assertTrue("hello" in suggestions || "help" in suggestions)
    }

    @Test fun nextLetterWeightsRanksLikelyContinuations() {
        // Extensions of "the": "they" → 'y' (freq 90), "them" → 'm' (freq 80).
        // "the" itself is exactly the prefix, so it contributes no next letter.
        val bias = engine().nextLetterWeights("the")
        assertEquals(setOf('y', 'm'), bias.keys)
        assertEquals(1.0f, bias.getValue('y'), 1e-4f) // top letter normalised to 1
        assertTrue(bias.getValue('m') < bias.getValue('y'))
    }

    @Test fun nextLetterWeightsEmptyForBlankOrUnknownPrefix() {
        assertTrue(engine().nextLetterWeights("").isEmpty())
        assertTrue(engine().nextLetterWeights("zzz").isEmpty())
    }

    @Test fun autocorrectFiresOnlyWhenConfident() {
        // "wprld" has exactly one plausible fix.
        assertEquals("world", engine().shouldAutocorrect("wprld"))
        assertNull(engine().shouldAutocorrect("hello")) // already correct
        assertNull(engine().shouldAutocorrect("xy")) // too short
        // "helo" is ambiguous between "hello" and "help": suggest, don't force.
        assertNull(engine().shouldAutocorrect("helo"))
    }

    @Test fun blacklistedWordNeverSuggested() {
        val e = engine().apply { blacklist = setOf("they") }
        val suggestions = e.suggest("th", previousWord = null)
        assertTrue("they" !in suggestions)
        // The rest of the completions are untouched.
        assertTrue("the" in suggestions)
        assertTrue("them" in suggestions)
    }

    @Test fun blacklistMatchesCaseInsensitively() {
        val e = engine().apply { blacklist = setOf("they") }
        // Capitalized composing still filters the (capitalized) candidate.
        assertTrue("They" !in e.suggest("Th", previousWord = null))
    }

    @Test fun blacklistedWordIsNotAnAutocorrectTarget() {
        // "wprld" would normally autocorrect to "world"; blacklisting it must
        // stop the silent replacement.
        val e = engine().apply { blacklist = setOf("world") }
        assertNull(e.shouldAutocorrect("wprld"))
    }

    @Test fun allCapsWordsAreNotCorrectedWhenSkipEnabled() {
        // "WPRLD" would correct to "world" if it were lowercase, but an
        // all-caps word is a deliberate acronym/shout by default.
        assertNull(engine().apply { skipAllCapsAutocorrect = true }.shouldAutocorrect("WPRLD"))
        // A mixed-case slip is still corrected — only all-caps is spared. The
        // leading capital carries through to the correction.
        assertEquals("World", engine().apply { skipAllCapsAutocorrect = true }.shouldAutocorrect("Wprld"))
        // Off, an all-caps word corrects like any other (case carried through).
        assertEquals("WORLD", engine().apply { skipAllCapsAutocorrect = false }.shouldAutocorrect("WPRLD"))
    }

    @Test fun autocorrectPrefersAdjacentKeySlip() {
        // Equal frequencies: "cst" fixes to "cat" because s sits next to a,
        // while u is across the keyboard.
        val dictionary = Trie().apply {
            insert("cat", 100)
            insert("cut", 100)
        }
        val e = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        assertEquals("cat", e.shouldAutocorrect("cst"))
    }

    @Test fun autocorrectStaysQuietBetweenCloseCandidates() {
        // Both candidates are one adjacent-key slip away at similar
        // frequency — no winner is confident enough to force.
        val dictionary = Trie().apply {
            insert("test", 100)
            insert("tear", 95)
        }
        val e = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        assertNull(e.shouldAutocorrect("tesr"))
    }

    @Test fun autocorrectConsensusBetweenDictionaryAndLexicon() {
        val dictionary = Trie().apply {
            insert("cat", 100)
            insert("car", 90)
        }
        val lexicon = UserLexicon(null)
        lexicon.learnWord("cat")
        val e = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), lexicon)
        assertEquals("cat", e.shouldAutocorrect("caz"))
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

    @Test fun avroLiteralHoldsAgainstNearTieSibling() {
        // হলো is more frequent than হল, but the composing preview showed
        // হলো — when the top sibling is less than 2x more frequent, the
        // literal (what the user sees while typing) survives.
        val bengali = BengaliPhoneticIndex(
            listOf("হলো" to 1986, "হল" to 1900)
        )
        val e = SuggestionEngine(Trie(), bengali, UserLexicon(null))
        val suggestions = e.suggest("holO", null, avroMode = true)
        assertEquals("হলো", suggestions.first())
        assertTrue("হল" in suggestions)
    }

    @Test fun avroLiteralWinsWithoutSiblings() {
        // "wasi" has no phonetic sibling in the dictionary; the literal
        // transliteration (ওয়াসি) must survive the space commit untouched.
        val e = engine()
        val literal = AvroPhonetic.transliterate("wasi")
        assertEquals(literal, e.suggest("wasi", null, avroMode = true).first())
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

    @Test fun splitWordSuggestion() {
        val dictionary = Trie().apply {
            insert("of", 9000)
            insert("the", 10000)
            insert("a", 9500)
            insert("lot", 800)
        }
        val e = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        assertEquals("of the", e.suggest("ofthe", previousWord = null).first())
        assertTrue("a lot" in e.suggest("alot", previousWord = null))
        // Capitalization pattern carries over the whole phrase.
        assertEquals("Of the", e.suggest("Ofthe", previousWord = null).first())
    }

    @Test fun contactNamesCompleteAndChain() {
        val contacts = ContactNames.fromNames(
            listOf("Wasi Mollik", "Wasim Akram", "Wasi Uddin")
        )
        val e = SuggestionEngine(Trie(), BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        e.contacts = contacts
        val completions = e.suggest("was", previousWord = null)
        assertEquals("Wasi", completions.first()) // two contacts share it
        assertTrue("Wasim" in completions)
        // Next-word chaining through the name.
        val next = e.suggest("", previousWord = "Wasi")
        assertTrue("Mollik" in next && "Uddin" in next)
        // A known name is never autocorrected away.
        assertNull(e.shouldAutocorrect("wasim"))
    }

    @Test fun seedBigramsCoverColdStart() {
        val seeds = SeedBigrams.load(
            "good morning 100\ngood night 90\nthank you 100\n".byteInputStream()
        )
        val e = SuggestionEngine(
            Trie(), BengaliPhoneticIndex(emptyList()), UserLexicon(null), seedBigrams = seeds,
        )
        assertEquals(listOf("morning", "night"), e.suggest("", previousWord = "good"))
        assertEquals(listOf("you"), e.suggest("", previousWord = "Thank"))
    }

    @Test fun learnedBigramsOutrankSeeds() {
        val seeds = SeedBigrams.load("good morning 100\ngood night 90\n".byteInputStream())
        val lexicon = UserLexicon(null)
        lexicon.learnBigram("good", "game")
        val e = SuggestionEngine(
            Trie(), BengaliPhoneticIndex(emptyList()), lexicon, seedBigrams = seeds,
        )
        assertEquals(listOf("game", "morning", "night"), e.suggest("", previousWord = "good"))
    }

    @Test fun `secondary dictionary words are suggested alongside the primary`() {
        val spanish = Trie().apply { insert("gato", 1); insert("gata", 1) }
        val e = engine().apply {
            englishSources = false // primary is a non-English language
            secondaryDictionaries = listOf(SecondaryDictionary("es", spanish))
        }
        assertTrue("gato" in e.suggest("gat", previousWord = null))
    }

    @Test fun `a word in a secondary dictionary is never autocorrected away`() {
        val secondary = Trie().apply { insert("worl", 1) }
        val e = engine().apply { secondaryDictionaries = listOf(SecondaryDictionary("es", secondary)) }
        assertNull("worl is a valid secondary word", e.shouldAutocorrect("worl"))
        // Without the secondary the same string corrects to the bundled word.
        assertEquals("world", engine().shouldAutocorrect("worl"))
    }

    @Test fun `english as a secondary contributes the bundled list`() {
        val e = engine().apply {
            englishSources = false // primary isn't English
            englishAsSecondary = true
        }
        val s = e.suggest("hel", previousWord = null)
        assertTrue("hello" in s || "help" in s)
    }

    @Test fun `the more-used secondary language outranks the neglected one`() {
        // Two secondaries whose completions collide on the same prefix; the one
        // the user actually types should sort ahead once the mix has adapted.
        val spanish = Trie().apply { insert("plato", 1) }
        val german = Trie().apply { insert("platz", 1) }
        val mix = LanguageMixConfidence()
        val e = SuggestionEngine(
            Trie(), BengaliPhoneticIndex(emptyList()), UserLexicon(null),
            mixConfidence = mix,
        ).apply {
            englishSources = false
            primaryLanguageId = "fr"
            secondaryDictionaries = listOf(
                SecondaryDictionary("es", spanish),
                SecondaryDictionary("de", german),
            )
        }
        // Cold start: equal weight, so neither is forced ahead of the other.
        // Teach the keyboard the user leans on Spanish.
        repeat(30) { e.recordUsage("plato") }
        val ranked = e.suggest("plat", previousWord = null)
        assertTrue("plato" in ranked && "platz" in ranked)
        assertTrue(
            "the used language should sort first",
            ranked.indexOf("plato") < ranked.indexOf("platz"),
        )
    }

    @Test fun `words the primary covers do not inflate a secondary`() {
        // "world" is a primary (English) word: committing it must count toward
        // the primary, leaving the untouched secondary damped below neutral —
        // never boosted as though the user had typed Spanish.
        val dictionary = Trie().apply { insert("world", 50) }
        val spanish = Trie().apply { insert("hola", 1) }
        val mix = LanguageMixConfidence()
        val e = SuggestionEngine(
            dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null),
            mixConfidence = mix,
        ).apply {
            englishSources = true
            primaryLanguageId = "en"
            secondaryDictionaries = listOf(SecondaryDictionary("es", spanish))
        }
        repeat(20) { e.recordUsage("world") }
        // Primary got the credit, so Spanish sits below neutral, not above it.
        assertTrue(mix.confidenceFor("es") < LanguageMixConfidence.NEUTRAL)
        assertTrue(mix.confidenceFor("en") > mix.confidenceFor("es"))
    }
}
