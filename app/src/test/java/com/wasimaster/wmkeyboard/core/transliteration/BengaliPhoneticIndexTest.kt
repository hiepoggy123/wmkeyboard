package com.wasimaster.wmkeyboard.core.transliteration

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sibling ranking, which decides what a loose romanized spelling comes
 * back as. Frequencies here are the real ones from the bundled Bengali list,
 * since the whole point of these cases is that the commoner word is the wrong
 * answer and something else has to outweigh it.
 */
class BengaliPhoneticIndexTest {

    private val index = BengaliPhoneticIndex(
        listOf(
            // Aspiration pairs — the plain member is the commoner word.
            "তাকে" to 4850, "থাকে" to 1750,
            "তাকা" to 568, "থাকা" to 1582, "টাকা" to 1900,
            "গুম" to 422, "ঘুম" to 80,
            "কবর" to 900, "খবর" to 640,
            // Aspiration where the h was left out: ঠিক must still win on being
            // the far commoner word.
            "টিক" to 229, "ঠিক" to 565,
            // A bare word against itself plus the emphatic ও.
            "কথা" to 286, "কথাও" to 993,
            "আগে" to 530, "আগেও" to 1216,
            // ভাল/ভালো are one word spelled two ways, not two words.
            "ভাল" to 1900, "ভালো" to 6500,
            // The confusable folds the index was built for.
            "আছি" to 6900, "আসি" to 2300,
            "চলে" to 2100, "ছলে" to 40,
            // খণ্ড-ত: nobody reaches for its key mid-word, so it has to be
            // findable from the plain t — without dragging ordinary ত-final
            // words along with it.
            "হঠাৎ" to 1252, "হঠাত" to 40, "ভারত" to 3900,
        )
    )

    private fun top(input: String) = index.lookup(input).firstOrNull()

    @Test fun typedAspirationIsNotOverriddenByACommonerPlainWord() {
        // থাকে is 2.8x rarer than তাকে; the h is what asks for it.
        assertEquals("থাকে", top("thake"))
        assertEquals("তাকে", top("take"))
        assertEquals("ঘুম", top("ghum"))
        assertEquals("গুম", top("gum"))
        assertEquals("খবর", top("khobor"))
        assertEquals("কবর", top("kobor"))
    }

    @Test fun omittedAspirationStillYieldsToFrequency() {
        // Leaving the h out is ordinary casual spelling, so it only handicaps
        // the aspirated word rather than ruling it out.
        assertEquals("ঠিক", top("tik"))
        assertEquals("ঠিক", top("thik"))
        // টাকা is commoner than both তাকা and থাকা, and nothing was typed to
        // argue against it.
        assertEquals("টাকা", top("taka"))
    }

    @Test fun aFinalOIsPartOfTheWord() {
        // কথাও is "also"; it is not what "kotha" asked for.
        assertEquals("কথা", top("kotha"))
        assertEquals("কথাও", top("kothao"))
        assertEquals("আগে", top("age"))
        assertEquals("আগেও", top("ageo"))
    }

    @Test fun spellingVariantsStillFollowFrequency() {
        // ভাল/ভালো differ only by the o-kar, so the commoner spelling wins
        // whichever way it was typed.
        assertEquals("ভালো", top("valo"))
        assertEquals("ভাল", top("val"))
    }

    @Test fun chAndPhAreNotAspirationClaims() {
        // "ch" is how চ gets written by anyone used to English, so it must not
        // be read as asking for the aspirated ছ.
        assertEquals("চলে", top("chole"))
        assertEquals("আছি", top("achi"))
        assertEquals("আছি", top("asi"))
    }

    @Test fun khandaTaIsReachableFromAPlainT() {
        assertEquals("হঠাৎ", top("hotat"))
        assertEquals("হঠাৎ", top("hoThat"))
        // ...and a word that really does end in ত keeps its ত.
        assertEquals("ভারত", top("bharot"))
    }

    @Test fun unknownInputHasNoSiblings() {
        assertEquals(null, top("zzzq"))
    }
}
