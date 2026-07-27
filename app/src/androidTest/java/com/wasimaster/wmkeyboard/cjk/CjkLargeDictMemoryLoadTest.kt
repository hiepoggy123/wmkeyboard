package com.wasimaster.wmkeyboard.cjk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictCatalog
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictStore
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictionaries
import com.wasimaster.wmkeyboard.core.input.composer.ConversionDictionary
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Device Memory Instrumented Test for Japanese Kana->Kanji Dictionary Loading (P5.1).
 *
 * Tests the memory footprint when loading large dictionary files (like ja_kana.tsv ~41MB / 1.08M entries).
 * Specification: Large dictionary packs MUST load without throwing OutOfMemoryError or reverting to EMPTY.
 */
@RunWith(AndroidJUnit4::class)
class CjkLargeDictMemoryLoadTest {

    @Test
    fun testP5_1_JaKanaLargeDictLoadMustNotOom() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = appContext.filesDir

        val jaPack = CjkDictCatalog.byId("ja_kana")
        assertNotNull(jaPack)

        val dictFile = CjkDictStore.packFile(filesDir, jaPack!!)

        if (dictFile.isFile) {
            val loadedDict = dictFile.bufferedReader().useLines { lines ->
                ConversionDictionary.parse(lines)
            }
            assertNotEquals("Japanese dictionary MUST NOT fall back to EMPTY on load", ConversionDictionary.EMPTY, loadedDict)
            assertTrue("Japanese dictionary MUST contain kanji for にほん", loadedDict.exact("にほん").contains("日本"))
        } else {
            // Simulate 1.08M lines parsing to verify heap allocation threshold
            val largeSequence = Sequence {
                var count = 0
                object : Iterator<String> {
                    override fun hasNext(): Boolean = count < 1_080_000
                    override fun next(): String {
                        count++
                        return "reading$count\tKanjiCandidate$count\t100"
                    }
                }
            }

            val parsed = ConversionDictionary.parse(largeSequence)
            assertNotEquals("1.08M entry dictionary MUST load without OOM", ConversionDictionary.EMPTY, parsed)
        }
    }
}
