package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The shift crossings a stroke made ride home on the picker's verdict (#115),
 * because `ServiceKeyboardContent` has no room for another callback. That makes
 * this the one place a glide's "capitalize this" can be lost, so it is checked.
 */
class GlideVerdictTest {

    @Test
    fun anOrdinaryGlideCarriesNoCapitalsAndAllocatesNothing() {
        val leader = GlideVerdict.Leader()
        assertEquals(0, leader.capitals)
        assertSame(leader, leader.withCapitals(0))
        val word = GlideVerdict.Word("mark")
        assertSame(word, word.withCapitals(0))
    }

    @Test
    fun crossingTheShiftKeyRidesHomeOnEitherAnswer() {
        assertEquals(GlideVerdict.Leader(1), GlideVerdict.Leader().withCapitals(1))
        assertEquals(
            GlideVerdict.Word("mark", 2),
            GlideVerdict.Word("mark").withCapitals(2),
        )
        // The picker's word survives the crossing: they are two answers about
        // the same stroke, not competing ones.
        assertEquals("mark", (GlideVerdict.Word("mark").withCapitals(1) as GlideVerdict.Word).word)
    }

    @Test
    fun aCancelledStrokeTypesNothingSoItCapitalizesNothing() {
        assertSame(GlideVerdict.Cancel, GlideVerdict.Cancel.withCapitals(3))
        assertEquals(0, GlideVerdict.Cancel.capitals)
    }
}
