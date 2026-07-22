package com.wasimaster.wmkeyboard.cjk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictCatalog
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictStore
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictionaries
import com.wasimaster.wmkeyboard.core.input.composer.StrokeComposer
import com.wasimaster.wmkeyboard.core.input.composer.StrokeDictionary
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Instrumented tests for Chinese Stroke (笔画) input (Phase 4).
 */
@RunWith(AndroidJUnit4::class)
class CjkStrokeE2ETest {

    @Before
    @After
    fun resetGlobals() {
        CjkDictionaries.stroke = StrokeDictionary.EMPTY
    }

    @Test
    fun testP4_1_SingleStroke() {
        CjkDictionaries.stroke = StrokeDictionary.parse(
            sequenceOf(
                "1\t一",
                "1\t不",
                "1\t在",
                "2\t十"
            )
        )

        val composed = StrokeComposer.composeBuffer("一")
        assertEquals("一", composed)

        val candidates = StrokeComposer.candidates("一")
        assertTrue(candidates.contains("一"))
        assertTrue(candidates.contains("不"))
        assertTrue(candidates.contains("在"))
    }

    @Test
    fun testP4_2_DoubleStroke() {
        CjkDictionaries.stroke = StrokeDictionary.parse(
            sequenceOf(
                "11\t二",
                "11\t于",
                "11\t天",
                "11\t开"
            )
        )

        val composed = StrokeComposer.composeBuffer("一一")
        assertEquals("一一", composed)

        val candidates = StrokeComposer.candidates("一一")
        assertTrue(candidates.contains("于"))
        assertTrue(candidates.contains("天"))
    }

    @Test
    fun testP4_3_StrokeCandidateCommit() {
        CjkDictionaries.stroke = StrokeDictionary.parse(
            sequenceOf("11\t天")
        )

        val candidates = StrokeComposer.candidates("一一")
        assertTrue(candidates.isNotEmpty())

        val chosen = candidates[0]
        assertEquals("天", chosen)

        val consumed = StrokeComposer.consumedFor("一一", chosen)
        assertEquals(2, consumed)
    }

    /**
     * Test P4.4: Stroke Wildcard matching (* / ?) MUST resolve candidates on device.
     * FAILS on device because wildcard * pattern lookup produces an empty candidate strip.
     */
    @Test
    fun testP4_4_WildcardStrokeMatchingMustResolveCandidates() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = appContext.filesDir
        val strokeFile = CjkDictStore.downloadedFileFor(filesDir, "stroke")

        if (strokeFile != null && strokeFile.isFile) {
            CjkDictionaries.stroke = StrokeDictionary.parse(strokeFile.bufferedReader().useLines { it })
        } else {
            CjkDictionaries.stroke = StrokeDictionary.parse(sequenceOf("12\t十", "13\t八"))
        }

        val composed = StrokeComposer.composeBuffer("一*")
        assertEquals("一＊", composed)

        val candidates = StrokeComposer.candidates("一*")

        // Specification assertion: Wildcards MUST return non-empty matching candidates on device
        assertTrue("Stroke wildcard * MUST return matching candidates", candidates.isNotEmpty())
    }

    @Test
    fun testP4_5_StrokeLayoutAsset() {
        assertNotNull(AssetLayouts.ZH_STROKE_ID)
        assertEquals("asset_zh_stroke", AssetLayouts.ZH_STROKE_ID)
        assertTrue(StrokeComposer.isConversion)
        assertTrue(StrokeComposer.isTransliterating)
    }
}
