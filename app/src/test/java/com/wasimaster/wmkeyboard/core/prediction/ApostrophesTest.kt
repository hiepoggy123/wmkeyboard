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

    /**
     * The same words [leavesRealWordsAlone] protects, once the user has drawn the
     * apostrophe through the glide key: the stroke said which word was meant, so
     * the guess the automatic fix refuses to make is no longer a guess.
     */
    @Test
    fun declaredApostropheReachesTheAmbiguousForms() {
        assertEquals("it's", Apostrophes.fixExplicit("its"))
        assertEquals("we're", Apostrophes.fixExplicit("were"))
        assertEquals("we'll", Apostrophes.fixExplicit("well"))
        assertEquals("I'll", Apostrophes.fixExplicit("ill"))
        assertEquals("I'd", Apostrophes.fixExplicit("id"))
        assertEquals("let's", Apostrophes.fixExplicit("lets"))
        assertEquals("she'd", Apostrophes.fixExplicit("shed"))
        assertEquals("Let's", Apostrophes.fixExplicit("Lets"))
    }

    /**
     * A declared apostrophe also reaches the ordinary table, so the feature works
     * with "Fix missing apostrophes" switched off — that setting is about
     * guessing, and this stroke is not a guess.
     */
    @Test
    fun declaredApostropheAlsoFixesTheUnambiguousForms() {
        assertEquals("don't", Apostrophes.fixExplicit("dont"))
        assertEquals("I'm", Apostrophes.fixExplicit("im"))
    }

    /** No table entry, no rewrite: a drawn apostrophe never invents a spelling. */
    @Test
    fun declaredApostropheLeavesUnknownWordsAlone() {
        assertNull(Apostrophes.fixExplicit("hello"))
        assertNull(Apostrophes.fixExplicit("developers"))
        assertNull(Apostrophes.fixExplicit("it's"))
        assertNull(Apostrophes.fixExplicit(""))
    }

    @Test
    fun fixedWordsRoundTripToNull() {
        // Words already carrying their apostrophe are not in the table.
        assertNull(Apostrophes.fix("aren't"))
        assertNull(Apostrophes.fix("I'm"))
    }
}
