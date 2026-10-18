package com.wasimaster.wmkeyboard.core.prediction.telex

import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class VietnameseGlideTest {

    @Test
    fun testVietnameseGlideResolution() {
        val sampleTsv = """
            tieng	tiếng,tiêng,tiềng
            nguoi	người,ngươi,nguội
            viet	viết,việt
            quehuong	quê hương
        """.trimIndent()

        val index = VietnameseGlide.loadFromStream(ByteArrayInputStream(sampleTsv.toByteArray(Charsets.UTF_8)))
        assertNotNull(index)
        assertTrue(index.alphabet.contains('t'.code))
        assertTrue(index.alphabet.contains('n'.code))

        // Test resolving single syllable
        val candidate = GlideBeam.Candidate("tieng", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertTrue(resolved.isNotEmpty())
        assertEquals("tiếng", resolved[0].word)

        // Test resolving compound word
        val compoundCandidate = GlideBeam.Candidate("quehuong", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolvedCompound = index.resolve(listOf(compoundCandidate))
        assertTrue(resolvedCompound.isNotEmpty())
        assertEquals("quê hương", resolvedCompound[0].word)

        // Test lookup direct
        val forms = VietnameseGlide.lookup("nguoi")
        assertEquals(listOf("người", "ngươi", "nguội"), forms)
    }

    @Test
    fun testThreeColumnChineseFormatAndFrequencyRanking() {
        // 3-column Chinese Pinyin format: reading \t word \t freq
        val threeColTsv = """
            may	mày	50
            may	mấy	189
            may	máy	181
            thumay	thứ mấy	200
        """.trimIndent()

        val index = VietnameseGlide.loadFromStream(ByteArrayInputStream(threeColTsv.toByteArray(Charsets.UTF_8)))
        assertNotNull(index)

        // Since mấy (189) > máy (181) > mày (50), mấy should rank first!
        val candidate = GlideBeam.Candidate("may", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertTrue(resolved.size >= 3)
        assertEquals("mấy", resolved[0].word)
        assertEquals("máy", resolved[1].word)
        assertEquals("mày", resolved[2].word)

        // Test compound word
        val compound = GlideBeam.Candidate("thumay", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolvedCompound = index.resolve(listOf(compound))
        assertTrue(resolvedCompound.isNotEmpty())
        assertEquals("thứ mấy", resolvedCompound[0].word)
    }

    @Test
    fun testUnigramFrequencyIntegration() {
        // 2-column TSV where "mày" is listed first, but unigrams map gives "máy" and "mấy" higher frequency
        val twoColTsv = """
            may	mày,mấy,máy,mây
        """.trimIndent()

        val unigramMap = mapOf(
            "máy" to 220,
            "mấy" to 210,
            "mây" to 140,
            "mày" to 100
        )

        val index = VietnameseGlide.loadFromStream(
            ByteArrayInputStream(twoColTsv.toByteArray(Charsets.UTF_8)),
            unigramMap
        )

        val candidate = GlideBeam.Candidate("may", 1.0, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        val resolved = index.resolve(listOf(candidate))
        assertEquals("máy", resolved[0].word)
        assertEquals("mấy", resolved[1].word)
        assertEquals("mây", resolved[2].word)
        assertEquals("mày", resolved[3].word)
    }
}
