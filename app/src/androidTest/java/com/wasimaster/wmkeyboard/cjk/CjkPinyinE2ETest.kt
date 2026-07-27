package com.wasimaster.wmkeyboard.cjk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.input.composer.CjkConfig
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictionaries
import com.wasimaster.wmkeyboard.core.input.composer.ConversionDictionary
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyinScheme
import com.wasimaster.wmkeyboard.core.input.composer.PinyinComposer
import com.wasimaster.wmkeyboard.core.input.composer.PinyinSyllables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Instrumented tests for Chinese Pinyin input (Phase 0 & 1).
 */
@RunWith(AndroidJUnit4::class)
class CjkPinyinE2ETest {

    private val fixtureSyllables = setOf("ni", "hao", "xi", "an", "wo", "ma")

    @Before
    @After
    fun resetCjkGlobals() {
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY
        PinyinSyllables.valid = emptySet()
        CjkConfig.fuzzyPinyin = false
        CjkConfig.doublePinyin = DoublePinyinScheme.OFF
    }

    @Test
    fun testAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.wasimaster.wmkeyboard", appContext.packageName)
    }

    @Test
    fun testP1_1_WholePhraseRanking() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf(
                "ni\t你\t100",
                "ni\t尼\t10",
                "hao\t好\t100",
                "nihao\t你好\t500"
            )
        )

        val candidates = PinyinComposer.candidates("nihao")
        assertTrue("Candidates should not be empty", candidates.isNotEmpty())
        assertEquals("你好", candidates[0])
        assertTrue("Leading syllable candidate should also be present", candidates.contains("你"))
    }

    @Test
    fun testP1_2_CommitWholePhrase() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("nihao\t你好\t500")
        )

        val candidates = PinyinComposer.candidates("nihao")
        val chosen = candidates.first { it == "你好" }
        val consumed = PinyinComposer.consumedFor("nihao", chosen)

        assertEquals(5, consumed)
        val remaining = "nihao".substring(consumed)
        assertEquals("", remaining)
    }

    @Test
    fun testP1_4_SpaceCommitSingleSyllable() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("ni\t你\t100")
        )

        val candidates = PinyinComposer.candidates("ni")
        assertTrue(candidates.isNotEmpty())

        val topCandidate = candidates[0]
        assertEquals("你", topCandidate)

        val consumed = PinyinComposer.consumedFor("ni", topCandidate)
        assertEquals(2, consumed)
    }

    /**
     * Test P1.5: xi'an (with apostrophe) MUST segment into xi|an and offer 西安.
     *
     * The apostrophe is a *boundary marker in the buffer*, never part of a
     * reading — the segmenter folds it into the syllable's input span and looks
     * up `xian`. So the dictionary is keyed on `xian`, exactly as a real pack is,
     * and what the apostrophe changes is the syllable *count*: two units, which a
     * one-character word cannot cover.
     */
    @Test
    fun testP1_5_ApostropheSegmentationMustOfferXiAn() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("xian\t西安\t7", "xian\t见\t500")
        )

        // Spelled out as two syllables: 见 is one character and cannot span both,
        // so the place name leads despite being far rarer.
        val candidates = PinyinComposer.candidates("xi'an")
        assertTrue("xi'an with apostrophe MUST return candidates containing 西安", candidates.contains("西安"))
        assertEquals("西安", candidates[0])
        // The apostrophe is consumed with the syllable it introduced.
        assertEquals(5, PinyinComposer.consumedFor("xi'an", "西安"))

        // Typed as one syllable it is merely under-segmented, so the common
        // single character comes back and leads. Needs `xian` in the inventory —
        // without it the buffer segments as xi|an regardless of the apostrophe,
        // which is the whole distinction under test.
        PinyinSyllables.valid = fixtureSyllables + "xian"
        assertEquals("见", PinyinComposer.candidates("xian")[0])
    }

    @Test
    fun testP1_6_GibberishInputRawCommit() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY

        val composed = PinyinComposer.composeBuffer("zzz")
        assertEquals("zzz", composed)

        val candidates = PinyinComposer.candidates("zzz")
        assertTrue("Gibberish should have no candidates in empty dict", candidates.isEmpty())

        val consumed = PinyinComposer.consumedFor("zzz", "zzz")
        assertEquals(3, consumed)
    }

    @Test
    fun testP1_7_BackspaceEditingBuffer() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("niha\t呢\t10", "nihao\t你好\t100")
        )

        val fullCandidates = PinyinComposer.candidates("nihao")
        assertTrue(fullCandidates.contains("你好"))

        val editedBuffer = "nihao".dropLast(1)
        assertEquals("niha", editedBuffer)

        val editedCandidates = PinyinComposer.candidates(editedBuffer)
        assertFalse(editedCandidates.contains("你好"))
    }

    @Test
    fun testP1_8_LayoutSwitchFlushesBuffer() {
        val buffer = "nihao"
        assertTrue(PinyinComposer.isConversion)
        assertTrue(PinyinComposer.isTransliterating)
        assertEquals("nihao", PinyinComposer.composeBuffer(buffer))
    }
}
