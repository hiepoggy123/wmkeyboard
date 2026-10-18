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
}
