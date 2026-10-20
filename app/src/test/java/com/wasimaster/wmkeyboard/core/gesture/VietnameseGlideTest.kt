package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VietnameseGlideTest {

    @Test
    fun stripDiacriticsRemovesVietnameseAccentsAndD() {
        assertEquals("tieng viet", VietnameseDiacriticsModel.stripDiacritics("tiếng việt"))
        assertEquals("duong", VietnameseDiacriticsModel.stripDiacritics("đường"))
        assertEquals("Dak Lak", VietnameseDiacriticsModel.stripDiacritics("Đắk Lắk"))
        assertEquals("nguoi viet nam", VietnameseDiacriticsModel.stripDiacritics("người việt nam"))
        assertEquals("truong hoc", VietnameseDiacriticsModel.stripDiacritics("trường học"))
    }

    @Test
    fun uninitializedModelFallsBackGracefully() {
        // When model is not initialized (e.g. standard JVM test runner), restore returns candidate
        assertFalse(VietnameseDiacriticsModel.isReady)
        assertEquals("tieng", VietnameseDiacriticsModel.restore("tieng", "hoc"))
        assertEquals("viet", VietnameseDiacriticsModel.restore("viet", "tieng"))
    }

    @Test
    fun vietnameseGlideSpellingsContainCoreSyllables() {
        val spellings = VietnameseGlide.spellings(null).toMap()
        assertTrue("tieng should be in spellings", spellings.containsKey("tieng"))
        assertTrue("viet should be in spellings", spellings.containsKey("viet"))
        assertTrue("hoc should be in spellings", spellings.containsKey("hoc"))
        assertTrue("sinh should be in spellings", spellings.containsKey("sinh"))
        assertTrue("truong should be in spellings", spellings.containsKey("truong"))
        assertTrue("nguoi should be in spellings", spellings.containsKey("nguoi"))
        assertTrue("khong should be in spellings", spellings.containsKey("khong"))
        assertEquals("abcdefghijklmnopqrstuvwxyz", VietnameseGlide.GLIDE_KEYS)
    }

    @Test
    fun contextualRomanizedIndexPassesContext() {
        val spellings = PackedTrie.of(listOf("toan" to 100))
        var receivedContext: String? = null
        val index = RomanizedIndex.contextual(
            spellings = spellings,
            personal = PackedTrie.EMPTY,
            keys = VietnameseGlide.GLIDE_KEYS,
        ) { candidate, context ->
            receivedContext = context
            if (context == "bai") listOf("toán") else listOf("toàn")
        }

        val decoded = listOf(GlideBeam.Candidate("toan", 1.0, 0.5, 0))
        val resolvedWithContext = index.resolve(decoded, "bai")
        assertEquals("bai", receivedContext)
        assertEquals("toán", resolvedWithContext.first().word)

        val resolvedOtherContext = index.resolve(decoded, "hoan")
        assertEquals("hoan", receivedContext)
        assertEquals("toàn", resolvedOtherContext.first().word)
    }
}
