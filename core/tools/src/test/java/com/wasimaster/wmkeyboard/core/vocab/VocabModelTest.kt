package com.wasimaster.wmkeyboard.core.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabModelTest {

    @Test
    fun `glosses pair each word with its own romanisation`() {
        val bn = VocabTranslation(w = listOf("ঘৃণা করা", "ঘৃণা"), r = listOf("ghrina kora", "ghrina"))
        assertEquals(listOf(VocabGloss("ঘৃণা করা", "ghrina kora"), VocabGloss("ঘৃণা", "ghrina")), bn.glosses())
        // Serbo-Croatian in both alphabets: the Latin repeat folds into the Cyrillic entry.
        val sh = VocabTranslation(w = listOf("снизити", "sniziti"), r = listOf("sniziti", ""))
        assertEquals(listOf(VocabGloss("снизити", "sniziti")), sh.glosses())
        // Plain Latin glosses carry no romanisation.
        assertEquals(listOf(VocabGloss("odiar", null)), VocabTranslation(w = listOf("odiar")).glosses())
        assertEquals(listOf(VocabGloss("odiar", null)), VocabTranslation(w = listOf("odiar"), r = listOf("Odiar")).glosses())
    }

    @Test
    fun `quotation references split into a year and a clean citation`() {
        val quote = VocabQuotation(
            text = "Many vegetarians abhor the thought of killing animals.",
            ref = "1975 March 21, Judy Klemesrud, “Vegetarianism: Growing Way of Life”, in The New York Times, →ISSN, archived from the original on 02 Nov 2025:",
        )
        assertEquals("1975", quote.year)
        assertEquals("Judy Klemesrud, “Vegetarianism: Growing Way of Life”, in The New York Times", quote.citation)
        val bible = VocabQuotation("x", "1611, The Holy Bible, […] (King James Version), London: […] Robert Barker, […], →OCLC, Romans 12:9:")
        assertEquals("1611", bible.year)
        assertEquals("The Holy Bible, (King James Version), London: Robert Barker, Romans 12:9", bible.citation)
        val circa = VocabQuotation("x", "c. 1350–1470, Geoffrey Chaucer, The Canterbury Tales")
        assertEquals("c. 1350–1470", circa.year)
        assertEquals("Geoffrey Chaucer, The Canterbury Tales", circa.citation)
        assertNull(VocabQuotation("x", "Shakespeare").year)
        assertEquals("Shakespeare", VocabQuotation("x", "Shakespeare").citation)
    }
}
