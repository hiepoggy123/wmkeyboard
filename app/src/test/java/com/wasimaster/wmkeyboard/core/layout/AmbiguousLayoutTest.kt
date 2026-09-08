package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.prediction.KeyProximity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two shipped boards that put more than one letter on a key: T9 and
 * Compact QWERTY (discussion #103).
 *
 * What they need that an ordinary layout does not is [Key.letters], and what
 * everything downstream needs from *them* is that the field and the key agree:
 * the anchor a key commits has to be the first letter of its set, or the buffer
 * and the decode would be describing different keystrokes.
 */
class AmbiguousLayoutTest {

    private val ambiguous = listOf(BuiltInLayouts.T9, BuiltInLayouts.COMPACT)

    private fun letterRows(spec: LayoutSpec) = spec.layer(LayoutLayer.LETTERS)!!.rows

    private fun textKeys(spec: LayoutSpec) =
        letterRows(spec).flatten().filter { it.action == KeyAction.Text }

    @Test
    fun bothBoardsAreAmbiguousAndNothingElseIs() {
        for (spec in ambiguous) {
            assertTrue("${spec.name} should be ambiguous", textKeys(spec).any { it.isAmbiguous() })
        }
        for (spec in BuiltInLayouts.all - ambiguous.toSet()) {
            val guessy = textKeys(spec).filter { it.isAmbiguous() }
            assertEquals("${spec.name} has ambiguous keys", emptyList<Key>(), guessy)
        }
    }

    @Test
    fun theCommittedCharacterIsTheFirstLetterOfTheKeysSet() {
        // The load-bearing invariant. The buffer holds what the key commits and
        // the key set describes position by position what it might have been;
        // an anchor that was not in its own set would make every reading of
        // that keystroke an edit away from what was typed.
        for (spec in ambiguous) {
            for (key in textKeys(spec)) {
                val set = key.letterSet()
                if (set.isEmpty()) continue
                assertEquals(
                    "${spec.name}: ${key.label} commits the wrong anchor",
                    set.first().toString(),
                    key.output ?: key.label,
                )
            }
        }
    }

    @Test
    fun betweenThemTheKeysSpellTheAlphabet() {
        for (spec in ambiguous) {
            val letters = textKeys(spec).flatMap { it.letterSet().toList() }
            assertEquals(
                "${spec.name} covers a-z exactly once",
                ('a'..'z').toList(),
                letters.sorted(),
            )
        }
    }

    @Test
    fun theKeypadFollowsTheStandardKeyGrouping() {
        // ITU E.161, which every phone since the mid-nineties has drawn, and
        // which is what a T9 user's fingers already know.
        val groups = textKeys(BuiltInLayouts.T9).map { it.letterSet() }.filter { it.length > 1 }
        assertEquals(
            listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"),
            groups,
        )
    }

    @Test
    fun theKeypadDrawsItsDigitsAsHints() {
        // The first long-press alternate is a key's corner hint, so putting the
        // digit there is what makes the grid read as a keypad — and holding a
        // key types it, the way it always did.
        val digits = textKeys(BuiltInLayouts.T9)
            .filter { it.isAmbiguous() }
            .map { it.longPress.first() }
        assertEquals(listOf("2", "3", "4", "5", "6", "7", "8", "9"), digits)
    }

    @Test
    fun everyLetterIsReachableOnItsOwnFromALongPress() {
        // The escape hatch, and the thing that makes an unknown word typable at
        // all on a board that otherwise guesses: every letter of every key is
        // in that key's popup, so it can be spelled outright.
        for (spec in ambiguous) {
            for (key in textKeys(spec).filter { it.isAmbiguous() }) {
                for (letter in key.letterSet()) {
                    assertTrue(
                        "${spec.name}: ${key.label} cannot spell '$letter'",
                        letter.toString() in key.longPress,
                    )
                }
            }
        }
    }

    @Test
    fun everyRowIsTheSameWidth() {
        // A row narrower than the grid is centred with side gaps and a wider
        // one is not, so a mismatched row on a five-column board is far more
        // visible than the same mistake on a ten-column one.
        for (spec in ambiguous) {
            val widths = letterRows(spec).map { row -> row.sumOf { it.width.toDouble() } }
            assertEquals("${spec.name} row widths", 1, widths.distinct().size)
        }
    }

    @Test
    fun proximityIsBetweenKeysNotBetweenLetters() {
        // Derived, not authored: the anchors are single characters, so the
        // existing derivation reads the grid correctly and the neighbours it
        // finds are the physical ones. On the keypad, `a`'s key really does sit
        // beside `d`'s and above `g`'s.
        val pad = KeyProximity.forLayout(BuiltInLayouts.T9)
        assertTrue(pad.areAdjacent('a', 'd'))
        assertTrue(pad.areAdjacent('a', 'g'))
        assertFalse(pad.areAdjacent('a', 'w'))
        // A letter that shares a key with the anchor is not "adjacent" to it —
        // it is the same key, which the decode handles as a free match rather
        // than as a slip.
        assertFalse(pad.areAdjacent('a', 'b'))
    }

    @Test
    fun neitherBoardAsksForTheTabletExpansion() {
        // Widening a keypad with Tab, caps lock and a mirrored shift would
        // make it neither a keypad nor a keyboard.
        for (spec in ambiguous) assertFalse(spec.name, spec.tabletExpand)
    }

    @Test
    fun bothAreEnglishLanguageLayoutsAndNeitherIsSecondary() {
        for (spec in ambiguous) {
            assertEquals("en", spec.langId)
            assertFalse("${spec.name} is secondary", spec.secondary)
            assertFalse("${spec.name} starts enabled", spec.id in BuiltInLayouts.defaultEnabledIds)
        }
    }
}
