package com.wasimaster.wmkeyboard.core.input.composer

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Traditional output and the Taiwan/Hong Kong vocabulary layer on top of it. */
class HanVariantTest {

    private fun asset(name: String): Sequence<String> =
        File("src/main/assets/dictionaries/$name").readText().lineSequence()

    @Before
    fun reset() {
        CjkConfig.traditionalOutput = false
        HanVariant.region = HanVariant.HanRegion.GENERIC
        HanVariant.s2t = emptyMap()
        HanVariant.twPhrases = emptyMap()
        HanVariant.twVariants = emptyMap()
        HanVariant.hkVariants = emptyMap()
        PinyinSyllables.valid = emptySet()
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY
        CjkDictionaries.ngrams = CjkNgrams.EMPTY
        CjkLearning.store = null
    }

    @After
    fun tearDown() = reset()

    private fun loadShipped() {
        HanVariant.s2t = HanVariant.parse(asset("s2t.txt"))
        HanVariant.twPhrases = HanVariant.parsePhrases(asset("tw_phrases.txt"))
        HanVariant.twVariants = HanVariant.parse(asset("tw_variants.txt"))
        HanVariant.hkVariants = HanVariant.parse(asset("hk_variants.txt"))
        assertTrue("s2t missing", HanVariant.s2t.size > 2000)
        assertTrue("tw phrases missing", HanVariant.twPhrases.size > 500)
        assertTrue("tw variants missing", HanVariant.twVariants.isNotEmpty())
        assertTrue("hk variants missing", HanVariant.hkVariants.isNotEmpty())
    }

    @Test
    fun `parsePhrases keeps the multi-character rows parse drops`() {
        val lines = sequenceOf("# comment", "", "出租車\t計程車", "僞\t偽", "同\t同", "junk")
        val phrases = HanVariant.parsePhrases(lines)
        assertEquals("計程車", phrases["出租車"])
        // Single-character rows are phrases too as far as this parser cares.
        assertEquals("偽", phrases["僞"])
        // Identity and malformed rows are skipped by both.
        assertEquals(null, phrases["同"])
        assertEquals(null, phrases["junk"])
        // The character parser is the one that refuses multi-character rows.
        assertEquals(null, HanVariant.parse(lines)['出'])
    }

    @Test
    fun `the toggle off is the identity whatever the region`() {
        loadShipped()
        HanVariant.region = HanVariant.HanRegion.TAIWAN
        assertEquals("出租车", HanVariant.toTraditional("出租车"))
    }

    @Test
    fun `generic converts characters but not vocabulary`() {
        loadShipped()
        CjkConfig.traditionalOutput = true
        assertEquals("出租車", HanVariant.toTraditional("出租车"))
    }

    @Test
    fun `taiwan swaps the word, not just the characters`() {
        // The whole point of the layer: 出租车 in Traditional characters is
        // 出租車, which no one in Taipei says.
        loadShipped()
        CjkConfig.traditionalOutput = true
        HanVariant.region = HanVariant.HanRegion.TAIWAN
        assertEquals("計程車", HanVariant.toTraditional("出租车"))
        assertEquals("光碟", HanVariant.toTraditional("光盘"))
    }

    @Test
    fun `hong kong keeps standard vocabulary`() {
        loadShipped()
        CjkConfig.traditionalOutput = true
        HanVariant.region = HanVariant.HanRegion.HONG_KONG
        // The Taiwan phrase table is Taiwan's; Hong Kong shares the standard
        // wording and differs in character preferences.
        assertEquals("出租車", HanVariant.toTraditional("出租车"))
    }

    @Test
    fun `a word in no table passes through unchanged`() {
        loadShipped()
        CjkConfig.traditionalOutput = true
        HanVariant.region = HanVariant.HanRegion.TAIWAN
        assertEquals("你好", HanVariant.toTraditional("你好"))
    }

    @Test
    fun `regional conversion does not disturb prefix commit`() {
        // The layer changes a candidate's length (出租車 → 計程車 is the same
        // length, but 中間件 → 中介軟體 is not). consumedFor must keep reporting
        // the *input* it covers, which comes from the reading, not the output.
        loadShipped()
        CjkConfig.traditionalOutput = true
        HanVariant.region = HanVariant.HanRegion.TAIWAN
        PinyinSyllables.valid = setOf("chu", "zu", "che")
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("chuzuche\t出租车\t500", "chu\t出\t900"),
        )
        val candidates = PinyinComposer.candidates("chuzuche")
        assertTrue("got $candidates", "計程車" in candidates)
        assertEquals(8, PinyinComposer.consumedFor("chuzuche", "計程車"))
        candidates.forEachIndexed { index, text ->
            assertEquals(text, PinyinComposer.consumedFor("chuzuche", text), PinyinComposer.consumedForIndex("chuzuche", index))
        }
    }

    @Test
    fun `a length-changing phrase still commits its own reading`() {
        loadShipped()
        CjkConfig.traditionalOutput = true
        HanVariant.region = HanVariant.HanRegion.TAIWAN
        // 中間件 (3 chars) becomes 中介軟體 (4) — output length and input length
        // have nothing to do with each other, which is exactly the invariant.
        assertEquals("中介軟體", HanVariant.toTraditional("中间件"))
        PinyinSyllables.valid = setOf("zhong", "jian", "jian2")
        CjkDictionaries.pinyin = ConversionDictionary.parse(sequenceOf("zhongjian\t中间件\t500"))
        assertEquals(9, PinyinComposer.consumedFor("zhongjian", "中介軟體"))
    }
}
