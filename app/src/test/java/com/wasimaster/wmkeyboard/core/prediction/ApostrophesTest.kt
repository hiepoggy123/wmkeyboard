package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApostrophesTest {

    @Test
    fun fixesCommonContractions() {
        assertEquals("aren't", Apostrophes.fix("arent"))
        assertEquals("isn't", Apostrophes.fix("isnt"))
        assertEquals("don't", Apostrophes.fix("dont"))
        assertEquals("won't", Apostrophes.fix("wont"))
        assertEquals("can't", Apostrophes.fix("cant"))
        assertEquals("y'all", Apostrophes.fix("yall"))
        assertEquals("o'clock", Apostrophes.fix("oclock"))
        assertEquals("ma'am", Apostrophes.fix("maam"))
    }

    @Test
    fun capitalizesI() {
        assertEquals("I'm", Apostrophes.fix("im"))
        assertEquals("I've", Apostrophes.fix("ive"))
        assertEquals("I", Apostrophes.fix("i"))
        // Already correct: nothing to do.
        assertNull(Apostrophes.fix("I"))
    }

    @Test
    fun preservesTypedCase() {
        assertEquals("Don't", Apostrophes.fix("Dont"))
        assertEquals("AREN'T", Apostrophes.fix("ARENT"))
        assertEquals("I'M", Apostrophes.fix("IM"))
        assertEquals("They're", Apostrophes.fix("Theyre"))
    }

    @Test
    fun leavesRealWordsAlone() {
        // Apostrophe-less forms that are real words must never be touched.
        for (word in listOf("its", "were", "well", "ill", "id", "hell",
                "shell", "wed", "shed", "lets", "sons", "whore", "hers")) {
            assertNull(word, Apostrophes.fix(word))
        }
        assertNull(Apostrophes.fix("hello"))
        assertNull(Apostrophes.fix(""))
    }

    @Test
    fun fixedWordsRoundTripToNull() {
        // Words already carrying their apostrophe are not in the table.
        assertNull(Apostrophes.fix("aren't"))
        assertNull(Apostrophes.fix("I'm"))
    }
}
