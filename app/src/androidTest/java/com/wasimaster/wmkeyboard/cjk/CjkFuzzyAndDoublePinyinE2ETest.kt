package com.wasimaster.wmkeyboard.cjk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasimaster.wmkeyboard.core.input.composer.CjkConfig
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictionaries
import com.wasimaster.wmkeyboard.core.input.composer.ConversionDictionary
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyin
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyinScheme
import com.wasimaster.wmkeyboard.core.input.composer.PinyinComposer
import com.wasimaster.wmkeyboard.core.input.composer.PinyinFuzzy
import com.wasimaster.wmkeyboard.core.input.composer.PinyinSyllables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Instrumented tests for Fuzzy & Double Pinyin (Phase 3).
 *
 * Exercises:
 * - P3.1: Fuzzy Pinyin OFF (si returns only si words, no shi words).
 * - P3.2: Fuzzy Pinyin ON (si matches shi words like 老师).
 * - P3.3: Double Pinyin Xiaohe scheme (nihc -> nihao -> 你好).
 * - P3.4: Double Pinyin Microsoft, Sogou, Ziranma, Pinyin++ scheme mappings.
 * - P3.5: Resetting Double Pinyin to OFF.
 */
@RunWith(AndroidJUnit4::class)
class CjkFuzzyAndDoublePinyinE2ETest {

    @Before
    @After
    fun resetGlobals() {
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY
        PinyinSyllables.valid = emptySet()
        CjkConfig.fuzzyPinyin = false
        CjkConfig.doublePinyin = DoublePinyinScheme.OFF
    }

    @Test
    fun testP3_1_FuzzyPinyinDisabled() {
        PinyinSyllables.valid = setOf("si", "shi")
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf(
                "si\t四\t100",
                "si\t死\t90",
                "shi\t是\t500"
            )
        )
        CjkConfig.fuzzyPinyin = false

        val candidates = PinyinComposer.candidates("si")
        assertTrue(candidates.contains("四"))
        assertTrue(candidates.contains("死"))
        assertFalse("When fuzzy is OFF, shi candidates should not appear for si", candidates.contains("是"))
    }

    @Test
    fun testP3_2_FuzzyPinyinEnabled() {
        val valid = setOf("shi", "si", "lao")
        PinyinSyllables.valid = valid
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf(
                "si\t四\t100",
                "shi\t是\t500",
                "laoshi\t老师\t800"
            )
        )

        // Fuzzy expansion test
        val expanded = PinyinFuzzy.expand("si", valid)
        assertTrue(expanded.contains("si"))
        assertTrue(expanded.contains("shi"))

        CjkConfig.fuzzyPinyin = true

        val candidates = PinyinComposer.candidates("si")
        assertTrue("When fuzzy is ON, shi candidates appear for si", candidates.contains("是"))

        val compoundCandidates = PinyinComposer.candidates("laosi")
        assertTrue("Fuzzy laosi should find 老师 (lǎoshī)", compoundCandidates.contains("老师"))
    }

    @Test
    fun testP3_3_DoublePinyinXiaohe() {
        PinyinSyllables.valid = setOf("ni", "hao")
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("nihao\t你好\t500")
        )

        CjkConfig.doublePinyin = DoublePinyinScheme.XIAOHE

        // In Xiaohe, 'nihc' -> 'ni' + 'hao'
        val composed = PinyinComposer.composeBuffer("nihc")
        assertEquals("nihao", composed)

        val candidates = PinyinComposer.candidates("nihc")
        assertTrue("Double pinyin nihc should resolve 你好", candidates.contains("你好"))
        assertEquals(4, PinyinComposer.consumedFor("nihc", "你好"))
    }

    @Test
    fun testP3_4_DoublePinyinAllSchemes() {
        val valid = setOf("ni", "hao", "hui", "hu", "zhong", "xiang", "an", "lve", "chu", "shi")

        // Microsoft / Sogou
        val ms = DoublePinyin.tableFor(DoublePinyinScheme.MICROSOFT)
        assertNotNull(ms)
        assertEquals("hui", DoublePinyin.translate("hv", ms!!, valid))
        assertEquals("zhong", DoublePinyin.translate("vs", ms, valid))

        // Ziranma
        val zr = DoublePinyin.tableFor(DoublePinyinScheme.ZIRANMA)
        assertNotNull(zr)
        assertEquals("xiang", DoublePinyin.translate("xd", zr!!, valid))

        // Pinyin++
        val pp = DoublePinyin.tableFor(DoublePinyinScheme.PINYINPP)
        assertNotNull(pp)
        assertEquals("zhong", DoublePinyin.translate("vy", pp!!, valid))
        assertEquals("chu", DoublePinyin.translate("uu", pp, valid))
        assertEquals("shi", DoublePinyin.translate("ii", pp, valid))
    }

    @Test
    fun testP3_5_DoublePinyinReset() {
        CjkConfig.doublePinyin = DoublePinyinScheme.XIAOHE
        assertEquals(DoublePinyinScheme.XIAOHE, CjkConfig.doublePinyin)

        CjkConfig.doublePinyin = DoublePinyinScheme.OFF
        assertEquals(DoublePinyinScheme.OFF, CjkConfig.doublePinyin)
        assertEquals("nihc", PinyinComposer.composeBuffer("nihc"))
    }
}
