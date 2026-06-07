package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishBengaliMapTest {

    private fun map(text: String) =
        EnglishBengaliMap.load(text.byteInputStream(Charsets.UTF_8))

    @Test fun mapsLoanword() {
        val m = map("keyboard\tকিবোর্ড\nchair\tচেয়ার\n")
        assertEquals(listOf("কিবোর্ড"), m.lookup("keyboard"))
        assertEquals(listOf("চেয়ার"), m.lookup("chair"))
        assertTrue(m.contains("keyboard"))
    }

    @Test fun lookupIsCaseInsensitiveAndTrimmed() {
        val m = map("chair\tচেয়ার\n")
        assertEquals(listOf("চেয়ার"), m.lookup("Chair"))
        assertEquals(listOf("চেয়ার"), m.lookup("  CHAIR "))
    }

    @Test fun duplicateKeysAccumulateInOrder() {
        val m = map("ticket\tটিকেট\nticket\tটিকিট\nticket\tটিকেট\n")
        assertEquals(listOf("টিকেট", "টিকিট"), m.lookup("ticket"))
    }

    @Test fun commentsBlanksAndMalformedSkipped() {
        val m = map(
            """
            # comment

            keyboard	কিবোর্ড
            noTabHere
            	leadingTabEmptyKey
            table
            """.trimIndent(),
        )
        assertEquals(1, m.size)
        assertEquals(listOf("কিবোর্ড"), m.lookup("keyboard"))
        assertFalse(m.contains("table"))
        assertFalse(m.contains("notabhere"))
    }

    @Test fun unmappedReturnsEmpty() {
        assertTrue(EnglishBengaliMap.EMPTY.lookup("anything").isEmpty())
        assertTrue(map("chair\tচেয়ার").lookup("mouse").isEmpty())
    }
}
