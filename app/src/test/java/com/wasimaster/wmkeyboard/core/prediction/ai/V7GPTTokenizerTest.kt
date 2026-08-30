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
        assertEquals("◌́", V7GPTTokenizer.toneCodeToMark(0))
        assertEquals("◌̀", V7GPTTokenizer.toneCodeToMark(1))
        assertEquals("◌̉", V7GPTTokenizer.toneCodeToMark(2))
        assertEquals("◌̃", V7GPTTokenizer.toneCodeToMark(3))
        assertEquals("◌̣", V7GPTTokenizer.toneCodeToMark(4))
        assertEquals("◌̣", V7GPTTokenizer.toneCodeToMark(6))
        assertEquals("◌", V7GPTTokenizer.toneCodeToMark(5))
        assertEquals("◌", V7GPTTokenizer.toneCodeToMark(7))
    }

    @Test
    fun testIsMatchPrefixAndTone() {
        val tokenizer = V7GPTTokenizer()
        tokenizer.renumList.add(null) // index 0
        tokenizer.renumToneList.add(null)
        tokenizer.renumToneMark.add(null)

        // index 1: "xin", tone code 5 (◌)
        tokenizer.renumList.add("xin")
        tokenizer.renumToneList.add(5)
        tokenizer.renumToneMark.add("◌")

        // index 2: "chào", tone code 1 (◌̀)
        tokenizer.renumList.add("chào")
        tokenizer.renumToneList.add(1)
        tokenizer.renumToneMark.add("◌̀")

        // index 3: "tiểu", tone code 2 (◌̉)
        tokenizer.renumList.add("tiểu")
        tokenizer.renumToneList.add(2)
        tokenizer.renumToneMark.add("◌̉")

        // index 4: "tiện", tone code 4 (◌̣)
        tokenizer.renumList.add("tiện")
        tokenizer.renumToneList.add(4)
        tokenizer.renumToneMark.add("◌̣")

        // 1. Prefix "x" matches "xin"
        assertTrue(tokenizer.isMatch(pattern = "x", word = "xin", idx = 1, toneMark = ""))
        assertFalse(tokenizer.isMatch(pattern = "x", word = "chào", idx = 2, toneMark = ""))

        // 2. Prefix "ch" matches "chào"
        assertTrue(tokenizer.isMatch(pattern = "ch", word = "chào", idx = 2, toneMark = ""))

        // 3. Embedded tone and vowel "tiể" matches "tiểu"
        assertTrue(tokenizer.isMatch(pattern = "tiể", word = "tiểu", idx = 3, toneMark = ""))
        assertFalse(tokenizer.isMatch(pattern = "tiể", word = "tiện", idx = 4, toneMark = ""))

        // 4. Embedded tone and vowel "tiệ" matches "tiện"
        assertTrue(tokenizer.isMatch(pattern = "tiệ", word = "tiện", idx = 4, toneMark = ""))
        assertFalse(tokenizer.isMatch(pattern = "tiệ", word = "tiểu", idx = 3, toneMark = ""))
    }

    @Test
    fun testFilterPredictions() {
        val tokenizer = V7GPTTokenizer()
        tokenizer.renumList.add(null)
        tokenizer.renumToneList.add(null)
        tokenizer.renumToneMark.add(null)

        tokenizer.renumList.add("xin")
        tokenizer.renumToneList.add(5)
        tokenizer.renumToneMark.add("◌")

        tokenizer.renumList.add("chào")
        tokenizer.renumToneList.add(1)
        tokenizer.renumToneMark.add("◌̀")

        tokenizer.renumList.add("xinh")
        tokenizer.renumToneList.add(5)
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
}
