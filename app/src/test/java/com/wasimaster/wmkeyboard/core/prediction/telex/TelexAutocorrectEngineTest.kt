package com.wasimaster.wmkeyboard.core.prediction.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelexAutocorrectEngineTest {

    @Test
    fun testTrieStructure() {
        val trie = TelexTrie()
        trie.insert("tueej", "tuệ", 100)
        trie.insert("trid", "trí", 200)

        assertEquals("tuệ", trie.root.children['t']?.children['u']?.children['e']?.children['e']?.children['j']?.word)
        assertEquals("trí", trie.root.children['t']?.children['r']?.children['i']?.children['d']?.word)
    }

    @Test
    fun testProximityNeighbors() {
        val prox = TelexProximityManager()
        val sampleJson = """
            {
                "y": {
                    "coords": [5.5, 0.5],
                    "neighbors": [
                        {"key": "y", "distance": 0.0, "penalty": 0.0},
                        {"key": "t", "distance": 1.0, "penalty": 1.2},
                        {"key": "u", "distance": 1.0, "penalty": 1.2}
                    ]
                }
            }
        """.trimIndent()
        prox.loadFromJson(sampleJson)

        val neighbors = prox.getNeighbors('y')
        assertEquals(3, neighbors.size)
        assertTrue(neighbors.any { it.key == 't' })
        assertTrue(neighbors.any { it.key == 'u' })
    }

    @Test
    fun testLanguageModelBigram() {
        val lm = TelexLanguageModel()
        val uniJson = """{"trí": 500, "tuệ": 300, "huệ": 100}"""
        val biJson = """{"trí": {"tuệ": 90, "huệ": 10}}"""

        lm.loadUnigrams(uniJson)
        lm.loadBigrams(biJson)

        assertEquals(500, lm.getUnigramScore("trí"))
        assertEquals(300, lm.getUnigramScore("tuệ"))
        assertEquals(90, lm.getBigramScore("trí", "tuệ"))
        assertEquals(10, lm.getBigramScore("trí", "huệ"))
        assertEquals(0, lm.getBigramScore("tâm", "tuệ"))
    }

    @Test
    fun testCandidateRanking() {
        val cand1 = TelexCorrectionCandidate("tuệ", "tueej", 1.2, 150.0)
        val cand2 = TelexCorrectionCandidate("huệ", "hueej", 1.2, 80.0)
        val list = mutableListOf(cand2, cand1)
        list.sort()

        assertEquals("tuệ", list[0].word)
        assertEquals("huệ", list[1].word)
    }

    @Test
    fun testExactMatchBeatsHigherFrequencyNeighbor() {
        val engine = TelexAutocorrectEngine.getInstance()
        // Setup engine with mock syllables & proximity
        val syllablesJson = """
            {
                "caanr": {"word": "cẩn", "freq": 82},
                "caanf": {"word": "cần", "freq": 213}
            }
        """.trimIndent()
        engine.loadSyllables(syllablesJson)

        val proxJson = """
            {
                "c": {"coords": [3.0, 2.0], "neighbors": [{"key": "c", "distance": 0.0, "penalty": 0.0}]},
                "a": {"coords": [1.0, 1.0], "neighbors": [{"key": "a", "distance": 0.0, "penalty": 0.0}]},
                "n": {"coords": [6.0, 2.0], "neighbors": [{"key": "n", "distance": 0.0, "penalty": 0.0}]},
                "r": {"coords": [4.0, 0.0], "neighbors": [
                    {"key": "r", "distance": 0.0, "penalty": 0.0},
                    {"key": "f", "distance": 1.0, "penalty": 1.2}
                ]}
            }
        """.trimIndent()
        engine.proximityManager.loadFromJson(proxJson)

        val uniJson = """{"cẩn": 82, "cần": 213}"""
        engine.languageModel.loadUnigrams(uniJson)

        val results = engine.correct("caanr", maxResults = 2)
        assertTrue(results.isNotEmpty())
        assertEquals("cẩn", results[0].word) // "cẩn" must win over "cần" because it is an exact match
    }

    @Test
    fun testToneBeforeCodaPermutations() {
        val engine = TelexAutocorrectEngine.getInstance()
        val syllablesJson = """
            {
                "thaatj": {"word": "thật", "freq": 205},
                "thaanr": {"word": "thẩn", "freq": 1}
            }
        """.trimIndent()
        engine.loadSyllables(syllablesJson)

        val uniJson = """{"thật": 205, "thẩn": 1}"""
        engine.languageModel.loadUnigrams(uniJson)

        // Typing canonical "thaatj"
        val res1 = engine.correct("thaatj", maxResults = 1)
        assertTrue(res1.isNotEmpty())
        assertEquals("thật", res1[0].word)

        // Typing tone before coda "thaajt"
        val res2 = engine.correct("thaajt", maxResults = 1)
        assertTrue(res2.isNotEmpty())
        assertEquals("thật", res2[0].word)
    }
}
