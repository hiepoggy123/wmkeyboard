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
}
