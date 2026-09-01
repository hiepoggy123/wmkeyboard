package com.wasimaster.wmkeyboard.core.prediction.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TelexAutocorrectEngineTest {

    @Before
    fun setUp() {
        TelexAutocorrectEngine.getInstance().resetForTesting()
    }

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

    @Test
    fun testVietnameseOrthographyValidator() {
        // Valid Vietnamese words
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("hôm"))
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("nay"))
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("trời"))
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("thật"))
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("đẹp"))
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("nghiêng"))
        assertTrue(VietnameseOrthography.isValidVietnameseSyllable("khuỷu"))

        // Invalid phonotactics
        org.junit.Assert.assertFalse(VietnameseOrthography.isValidVietnameseSyllable("rtời")) // Invalid onset "rt"
        org.junit.Assert.assertFalse(VietnameseOrthography.isValidVietnameseSyllable("htật")) // Invalid onset "ht"
        org.junit.Assert.assertFalse(VietnameseOrthography.isValidVietnameseSyllable("đpe"))  // Invalid cluster "đp"
    }

    @Test
    fun testBimanualDesyncEngineOppositeHand() {
        // c (Left) vs h (Right) -> Opposite hand
        assertTrue(BimanualDesyncEngine.isOppositeHand('c', 'h'))
        assertTrue(BimanualDesyncEngine.isOppositeHand('r', 't') == false) // r and t are both Left Hand
        assertTrue(BimanualDesyncEngine.isOppositeHand('t', 'h')) // t (Left) vs h (Right) -> Opposite hand
        assertTrue(BimanualDesyncEngine.isOppositeHand('n', 'g')) // n (Right) vs g (Left) -> Opposite hand
    }

    @Test
    fun testLanguageModelTrigram() {
        val lm = TelexLanguageModel()
        val uniJson = """{"đẹp": 900, "buồn": 300}"""
        val biJson = """{"thật": {"đẹp": 800, "buồn": 100}}"""
        val triJson = """{"trời thật": {"đẹp": 980, "buồn": 50}}"""

        lm.loadUnigrams(uniJson)
        lm.loadBigrams(biJson)
        lm.loadTrigrams(triJson)

        assertEquals(980, lm.getTrigramScore("trời", "thật", "đẹp"))
        assertEquals(50, lm.getTrigramScore("trời", "thật", "buồn"))
        assertEquals(0, lm.getTrigramScore("người", "thật", "đẹp"))
    }

    @Test
    fun testBimanualDesyncCandidates() {
        val engine = TelexAutocorrectEngine.getInstance()
        val syllablesJson = """
            {
                "troif": {"word": "trời", "freq": 170},
                "thaatj": {"word": "thật", "freq": 205},
                "ddepj": {"word": "đẹp", "freq": 185}
            }
        """.trimIndent()
        engine.loadSyllables(syllablesJson)

        val uniJson = """{"trời": 170, "thật": 205, "đẹp": 185}"""
        engine.languageModel.loadUnigrams(uniJson)

        // Typing swapped left-right hand "rtoif" -> desync candidates include "trời"
        val desync1 = BimanualDesyncEngine.generateCandidates("rtoif", engine)
        assertTrue(desync1.any { it.word == "trời" })

        // Typing swapped left-right hand "htaatj" -> desync candidates include "thật"
        val desync2 = BimanualDesyncEngine.generateCandidates("htaatj", engine)
        assertTrue(desync2.any { it.word == "thật" })
    }

    @Test
    fun testNewVowelCores() {
        // The 6 newly added core vowel combinations
        assertTrue("ngoài should be valid", VietnameseOrthography.isValidVietnameseSyllable("ngoài"))
        assertTrue("xoay should be valid", VietnameseOrthography.isValidVietnameseSyllable("xoay"))
        assertTrue("khuấy should be valid", VietnameseOrthography.isValidVietnameseSyllable("khuấy"))
        assertTrue("nhiều should be valid", VietnameseOrthography.isValidVietnameseSyllable("nhiều"))
        assertTrue("yêu should be valid", VietnameseOrthography.isValidVietnameseSyllable("yêu"))
        assertTrue("chuối should be valid", VietnameseOrthography.isValidVietnameseSyllable("chuối"))
    }

    @Test
    fun testNgramPackBinaryLookup() {
        val file = File("src/main/assets/telex/ngrams.wmng")
        if (file.exists()) {
            val engine = TelexAutocorrectEngine.getInstance()
            assertTrue("loadNgramPack should succeed", engine.loadNgramPack(file))
            assertEquals(222, engine.languageModel.getBigramScore("hôm", "nay"))
            assertEquals(216, engine.languageModel.getBigramScore("người", "việt"))
            assertEquals(100, engine.languageModel.getTrigramScore("trời", "quang", "mây"))
        }
    }

    @Test
    fun testDfsProximityCandidateSearch() {
        val engine = TelexAutocorrectEngine.getInstance()
        val syllablesJson = """{"tueej": {"word": "tuệ", "freq": 300}}"""
        engine.loadSyllables(syllablesJson)

        val proxJson = """
            {
                "r": {
                    "neighbors": [
                        {"key": "r", "distance": 0.0, "penalty": 0.0},
                        {"key": "t", "distance": 1.0, "penalty": 1.2}
                    ]
                }
            }
        """.trimIndent()
        engine.proximityManager.loadFromJson(proxJson)

        // Typing "rueej" with typo 'r' near 't' should be corrected to "tuệ" via DFS
        val results = engine.correct("rueej")
        assertTrue("DFS should find candidate tuệ", results.any { it.word == "tuệ" })
    }
}
