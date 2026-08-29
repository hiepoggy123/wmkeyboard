package com.wasimaster.wmkeyboard.core.prediction.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V7GPTTokenizerTest {

    @Test
    fun testNormalizeChar() {
        assertEquals('a', V7GPTTokenizer.normalizeChar('á'))
        assertEquals('a', V7GPTTokenizer.normalizeChar('ầ'))
        assertEquals('a', V7GPTTokenizer.normalizeChar('ặ'))
        assertEquals('e', V7GPTTokenizer.normalizeChar('ế'))
        assertEquals('e', V7GPTTokenizer.normalizeChar('ẹ'))
        assertEquals('o', V7GPTTokenizer.normalizeChar('ở'))
        assertEquals('o', V7GPTTokenizer.normalizeChar('ộ'))
        assertEquals('u', V7GPTTokenizer.normalizeChar('ừ'))
        assertEquals('u', V7GPTTokenizer.normalizeChar('ụ'))
        assertEquals('đ', V7GPTTokenizer.normalizeChar('đ'))
        assertEquals('x', V7GPTTokenizer.normalizeChar('x'))
    }

    @Test
    fun testToneCodeMapping() {
        assertEquals("◌", V7GPTTokenizer.toneCodeToMark(0))
        assertEquals("◌́", V7GPTTokenizer.toneCodeToMark(1))
        assertEquals("◌́", V7GPTTokenizer.toneCodeToMark(6))
        assertEquals("◌̀", V7GPTTokenizer.toneCodeToMark(2))
        assertEquals("◌̉", V7GPTTokenizer.toneCodeToMark(3))
        assertEquals("◌̃", V7GPTTokenizer.toneCodeToMark(4))
        assertEquals("◌̣", V7GPTTokenizer.toneCodeToMark(5))
        assertEquals("◌̣", V7GPTTokenizer.toneCodeToMark(7))
    }

    @Test
    fun testIsMatchPrefixAndTone() {
        val tokenizer = V7GPTTokenizer()
        tokenizer.renumList.add(null) // index 0
        tokenizer.renumToneMark.add(null) // index 0

        // index 1: "xin", tone 0 (◌)
        tokenizer.renumList.add("xin")
        tokenizer.renumToneMark.add("◌")

        // index 2: "chào", tone 2 (◌̀)
        tokenizer.renumList.add("chào")
        tokenizer.renumToneMark.add("◌̀")

        // 1. Shorthand "x" with tone "◌" matches "xin"
        assertTrue(tokenizer.isMatch(pattern = "x", word = "xin", idx = 1, toneMark = "◌"))
        // 2. Shorthand "x" with tone "◌̀" does NOT match "xin"
        assertFalse(tokenizer.isMatch(pattern = "x", word = "xin", idx = 1, toneMark = "◌̀"))

        // 3. Shorthand "ch" with tone "◌̀" matches "chào"
        assertTrue(tokenizer.isMatch(pattern = "ch", word = "chào", idx = 2, toneMark = "◌̀"))
        // 4. Shorthand "ch" with tone "◌́" does NOT match "chào"
        assertFalse(tokenizer.isMatch(pattern = "ch", word = "chào", idx = 2, toneMark = "◌́"))
    }

    @Test
    fun testFilterPredictions() {
        val tokenizer = V7GPTTokenizer()
        tokenizer.renumList.add(null)
        tokenizer.renumToneMark.add(null)

        tokenizer.renumList.add("xin")
        tokenizer.renumToneMark.add("◌")

        tokenizer.renumList.add("chào")
        tokenizer.renumToneMark.add("◌̀")

        tokenizer.renumList.add("xinh")
        tokenizer.renumToneMark.add("◌")

        val predictionIds = intArrayOf(1, 2, 3)
        val filteredX = tokenizer.filter(pattern = "x", predictionIds = predictionIds, maxResults = 5)
        assertEquals(2, filteredX.size)
        assertEquals("xin", filteredX[0])
        assertEquals("xinh", filteredX[1])

        val filteredCh = tokenizer.filter(pattern = "ch", predictionIds = predictionIds, maxResults = 5)
        assertEquals(1, filteredCh.size)
        assertEquals("chào", filteredCh[0])
    }

    @Test
    fun testBiasVectorManager() {
        val tokenizer = V7GPTTokenizer()
        tokenizer.enumDict["xin"] = 1
        tokenizer.enumDict["chào"] = 2

        val biasMgr = V7BiasVectorManager(size = 10)
        assertEquals(0.0f, biasMgr.biasVector[1], 0.0001f)

        biasMgr.updateBias("xin", tokenizer)
        assertTrue(biasMgr.biasVector[1] > 0.0f)
        assertEquals(0.0f, biasMgr.biasVector[2], 0.0001f)
    }

    @Test
    fun testExtractShorthand() {
        val (p1, t1) = V7GPTTokenizer.extractShorthand("x◌")
        assertEquals("x", p1)
        assertEquals("◌", t1)

        val (p2, t2) = V7GPTTokenizer.extractShorthand("x◌́")
        assertEquals("x", p2)
        assertEquals("◌́", t2)

        val (p3, t3) = V7GPTTokenizer.extractShorthand("ch◌̀")
        assertEquals("ch", p3)
        assertEquals("◌̀", t3)

        val (p4, t4) = V7GPTTokenizer.extractShorthand("t◌̣")
        assertEquals("t", p4)
        assertEquals("◌̣", t4)
    }
}
