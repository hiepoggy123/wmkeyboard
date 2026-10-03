package com.wasimaster.wmkeyboard.core.prediction.vni

import com.wasimaster.wmkeyboard.core.prediction.telex.TelexAutocorrectEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "toa1n", "toan1" -> "toán"
            "to1an" -> "to1an"
            "te2" -> "tè"
            "te3" -> "tẻ"
            "tha1" -> "thá"
            "tha2" -> "thà"
            "thanh2" -> "thành"
            "thabh2" -> "thàbh"
            "tue65" -> "tuệ"
            "hello" -> "hello"
            "helko" -> "helko"
            "thanh" -> "thanh"
            "thsmh" -> "thsmh"
            "thaw" -> "thaw"
            "tee" -> "tee"
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
                "thành": 500,
                "tuệ": 400,
                "trí": 450,
                "hello": 600,
                "thanh": 450
            }
        """.trimIndent()
        telexEngine.languageModel.loadUnigrams(uniJson)

        val biJson = """
            {
                "trí": {"tuệ": 350}
            }
        """.trimIndent()
        telexEngine.languageModel.loadBigrams(biJson)

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
    fun testDoubleTapCancelRecovery() {
        // User typed "viet655" (repeated 5 accidentally cancelling accent in VNI) -> recover "việt"
        val results = vniEngine.correct("viet655", "viet6", composer = vniComposer)
        assertTrue("viet655 should recover to việt", results.any { it.word == "việt" })
        assertEquals("Top candidate for viet655 should be việt", "việt", results.first().word)
    }

    @Test
    fun testQwertyLetterProximity_Accented() {
        // User typed "thabh2" with b adjacent to n on QWERTY Row 3 -> "thành"
        val results = vniEngine.correct("thabh2", "thàbh", composer = vniComposer)
        assertTrue("thabh2 should correct to thành", results.any { it.word == "thành" })
        assertEquals("Top candidate for thabh2 should be thành", "thành", results.first().word)
    }

    @Test
    fun testNoNumbersTyped_FiltersOutAccentsAndCorrectsUnaccented() {
        // 1. "thaw" without numbers must NEVER produce "thà" or any accented word
        val results1 = vniEngine.correct("thaw", "thaw", composer = vniComposer)
        assertFalse("thaw must not suggest accented word thà", results1.any { it.word == "thà" })

        // 2. "tee" without numbers must NEVER produce "tẻ" or any accented word
        val results2 = vniEngine.correct("tee", "tee", composer = vniComposer)
        assertFalse("tee must not suggest accented word tẻ", results2.any { it.word == "tẻ" })

        // 3. User typed "helko" ('k' is horizontal left neighbor of 'l' on QWERTY) -> English "hello"
        val results3 = vniEngine.correct("helko", "helko", composer = vniComposer)
        assertTrue("helko should suggest hello", results3.any { it.word == "hello" })

        // 4. User typed "thsmh" ('s' is horizontal left neighbor of 'a' on QWERTY) -> unaccented "thanh"
        val results4 = vniEngine.correct("thsmh", "thsmh", composer = vniComposer)
        assertTrue("thsmh should suggest thanh", results4.any { it.word == "thanh" })
    }

    @Test
    fun testNumbersTyped_RequiresAccentedCandidate() {
        // When digits are present, any suggestion must be accented Vietnamese
        val results = vniEngine.correct("vi6et5", "vi6et5", composer = vniComposer)
        assertTrue("All candidates must be accented Vietnamese", results.all { VniAutocorrectEngine.hasVietnameseDiacritics(it.word) })
    }

    @Test
    fun testNgramContextBoost() {
        // "tue65" with preceding word "trí" -> boosted bigram for "tuệ"
        val results = vniEngine.correct("tue65", "tue65", previousWord = "trí", composer = vniComposer)
        assertTrue("tue65 should suggest tuệ", results.any { it.word == "tuệ" })
        assertEquals("tuệ should be top candidate boosted by bigram 'trí'", "tuệ", results.first().word)
    }
}
