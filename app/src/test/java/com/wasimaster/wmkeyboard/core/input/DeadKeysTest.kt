package com.wasimaster.wmkeyboard.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadKeysTest {

    private val acute = '́'
    private val grave = '̀'
    private val tilde = '̃'

    @Test fun combiningMarksAreDeadKeys() {
        assertTrue(DeadKeys.isDeadKey("́"))
        assertTrue(DeadKeys.isDeadKey("̧"))
    }

    @Test fun spacingAccentsAndLettersAreNotDeadKeys() {
        // These are characters people type literally — `a^2`, a shell
        // backtick — so they must never arm an accent.
        assertFalse(DeadKeys.isDeadKey("´"))
        assertFalse(DeadKeys.isDeadKey("^"))
        assertFalse(DeadKeys.isDeadKey("~"))
        assertFalse(DeadKeys.isDeadKey("`"))
        assertFalse(DeadKeys.isDeadKey("e"))
        assertFalse(DeadKeys.isDeadKey(""))
        assertFalse(DeadKeys.isDeadKey("ab"))
    }

    @Test fun combinesIntoPrecomposedCharacters() {
        assertEquals("é", DeadKeys.combine('e', acute))
        assertEquals("à", DeadKeys.combine('a', grave))
        assertEquals("ñ", DeadKeys.combine('n', tilde))
        assertEquals("Ü", DeadKeys.combine('U', '̈'))
        assertEquals("ç", DeadKeys.combine('c', '̧'))
    }

    @Test fun combineRefusesPairsUnicodeHasNoCharacterFor() {
        assertNull(DeadKeys.combine('q', tilde))
        assertNull(DeadKeys.combine('7', acute))
    }

    @Test fun applyFallsBackToSpacingAccentPlusText() {
        assertEquals("é", DeadKeys.apply(acute, "e"))
        // No precomposed "q with tilde": keep both, losing nothing.
        assertEquals("~q", DeadKeys.apply(tilde, "q"))
        // Multi-character input can never combine.
        assertEquals("´ab", DeadKeys.apply(acute, "ab"))
    }

    @Test fun standaloneUsesSpacingFormWhereOneExists() {
        assertEquals("´", DeadKeys.standalone(acute))
        assertEquals("`", DeadKeys.standalone(grave))
        // A mark with no spacing form stays visible on a dotted circle
        // rather than silently attaching to the previous character.
        assertEquals("◌̖", DeadKeys.standalone('̖'))
    }
}
