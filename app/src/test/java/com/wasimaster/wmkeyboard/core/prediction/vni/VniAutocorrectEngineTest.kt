package com.wasimaster.wmkeyboard.core.prediction.vni

import com.wasimaster.wmkeyboard.core.prediction.telex.TelexAutocorrectEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VniAutocorrectEngineTest {

    private lateinit var telexEngine: TelexAutocorrectEngine
    private lateinit var vniEngine: VniAutocorrectEngine

    // Mock VNI composer simulating VietnameseComposer VNI mode
    private val vniComposer: (String) -> String = { raw ->
        when (raw.lowercase()) {
            "vie6t5", "viet65" -> "việt"
            "vi6et5" -> "vi6et5"
            "vi6e5t" -> "vi6e5t"
            "viet64" -> "viễt"
            "duong72" -> "đường"
            "duong71" -> "đướng"
            "toa1n", "toan1" -> "toán"
            "to1an" -> "to1an"
            "te2" -> "tè"
            "te3" -> "tẻ"
            "te4" -> "tẽ"
            "te6" -> "tê"
            "tha1" -> "thá"
            "tha2" -> "thà"
            "thanh2" -> "thành"
            "thabh2" -> "thàbh"
            "tue65" -> "tuệ"
            "tue64" -> "tuễ"
            else -> raw
        }
    }

    @Before
    fun setUp() {
        telexEngine = TelexAutocorrectEngine.getInstance()
        telexEngine.resetForTesting()
        vniEngine = VniAutocorrectEngine.getInstance()

        val syllablesJson = """
            {
                "vieetj": {"word": "việt", "freq": 800},
                "dduowngf": {"word": "đường", "freq": 700},
                "toans": {"word": "toán", "freq": 600},
                "thaf": {"word": "thà", "freq": 300},
                "ter": {"word": "tẻ", "freq": 150},
                "tef": {"word": "tè", "freq": 180},
                "tex": {"word": "tẽ", "freq": 50},
                "tee": {"word": "tê", "freq": 200},
                "thanhf": {"word": "thành", "freq": 500},
                "tueej": {"word": "tuệ", "freq": 400},
                "trid": {"word": "trí", "freq": 450}
            }
        """.trimIndent()
        telexEngine.loadSyllables(syllablesJson)

        val uniJson = """
            {
                "việt": 800,
                "đường": 700,
                "toán": 600,
                "thà": 300,
                "tẻ": 150,
                "tè": 180,
                "tẽ": 50,
                "tê": 200,
                "thành": 500,
                "tuệ": 400,
                "trí": 450
            }
        """.trimIndent()
        telexEngine.languageModel.loadUnigrams(uniJson)

        val biJson = """
            {
                "trí": {"tuệ": 350}
            }
        """.trimIndent()
        telexEngine.languageModel.loadBigrams(biJson)

        val proxJson = """
            {
                "b": {
                    "neighbors": [
                        {"key": "b", "distance": 0.0, "penalty": 0.0},
                        {"key": "n", "distance": 1.0, "penalty": 1.2}
                    ]
                }
            }
        """.trimIndent()
        telexEngine.proximityManager.loadFromJson(proxJson)

        telexEngine.isReady = true
    }

    @Test
    fun testBimanualDesync_EarlyNumber() {
        // User typed "vi6et5" or "vi6e5t" due to right hand (6) striking before left hand (e)
        val results1 = vniEngine.correct("vi6et5", "vi6et5", composer = vniComposer)
        assertTrue("vi6et5 should correct to việt", results1.any { it.word == "việt" })
        assertEquals("Top candidate for vi6et5 should be việt", "việt", results1.first().word)

        val results2 = vniEngine.correct("vi6e5t", "vi6e5t", composer = vniComposer)
        assertTrue("vi6e5t should correct to việt", results2.any { it.word == "việt" })
        assertEquals("Top candidate for vi6e5t should be việt", "việt", results2.first().word)

        // "to1an" -> "toán"
        val results3 = vniEngine.correct("to1an", "to1an", composer = vniComposer)
        assertTrue("to1an should correct to toán", results3.any { it.word == "toán" })
    }

    @Test
    fun testHorizontalNumberRowSlips() {
        // "viet64" (4 adjacent to 5) -> "việt"
        val results1 = vniEngine.correct("viet64", "viễt", composer = vniComposer)
        assertTrue("viet64 should correct to việt", results1.any { it.word == "việt" })
        assertEquals("Top candidate for viet64 should be việt", "việt", results1.first().word)

        // "duong71" (1 adjacent to 2) -> "đường"
        val results2 = vniEngine.correct("duong71", "đướng", composer = vniComposer)
        assertTrue("duong71 should correct to đường", results2.any { it.word == "đường" })
        assertEquals("Top candidate for duong71 should be đường", "đường", results2.first().word)
    }

    @Test
    fun testVerticalSlips() {
        // "thaw" (w is vertically under 2) -> "thà"
        val results1 = vniEngine.correct("thaw", "thaw", composer = vniComposer)
        assertTrue("thaw should suggest thà", results1.any { it.word == "thà" })

        // "tee" (e is vertically under 3) -> "tẻ"
        val results2 = vniEngine.correct("tee", "tee", composer = vniComposer)
        assertTrue("tee should suggest tẻ", results2.any { it.word == "tẻ" })
    }

    @Test
    fun testDoubleTapCancelRecovery() {
        // User typed "viet655" (repeated 5 accidentally cancelling accent in VNI) -> recover "việt"
        val results = vniEngine.correct("viet655", "viet6", composer = vniComposer)
        assertTrue("viet655 should recover to việt", results.any { it.word == "việt" })
        assertEquals("Top candidate for viet655 should be việt", "việt", results.first().word)
    }

    @Test
    fun testQwertyLetterProximity() {
        // User typed "thabh2" with b adjacent to n -> "thành"
        val results = vniEngine.correct("thabh2", "thàbh", composer = vniComposer)
        assertTrue("thabh2 should correct to thành", results.any { it.word == "thành" })
        assertEquals("Top candidate for thabh2 should be thành", "thành", results.first().word)
    }

    @Test
    fun testNgramContextBoost() {
        // "tue64" with preceding word "trí" -> boosted bigram for "tuệ"
        val results = vniEngine.correct("tue64", "tuễ", previousWord = "trí", composer = vniComposer)
        assertTrue("tue64 should suggest tuệ", results.any { it.word == "tuệ" })
        assertEquals("tuệ should be top candidate boosted by bigram 'trí'", "tuệ", results.first().word)
    }
}
