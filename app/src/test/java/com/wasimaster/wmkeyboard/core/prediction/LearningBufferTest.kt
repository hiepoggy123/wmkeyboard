package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningBufferTest {

    private fun LearningBuffer.words(): List<String> = drain().map { it.word }

    /** Commits [word], then reports the caret the editor would echo back. */
    private fun LearningBuffer.commit(word: String, caret: Int) {
        push(word, "en", 1)
        onCaret(caret)
    }

    @Test
    fun typingForwardKeepsEverything() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.commit("wobble", 14)
        assertEquals(listOf("wibble", "wobble"), buffer.words())
    }

    @Test
    fun backspacingIntoTheLastWordDropsIt() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.commit("wobble", 14)
        // One backspace lands the caret inside the word it just committed.
        buffer.onCaret(13)
        assertEquals(listOf("wibble"), buffer.words())
    }

    @Test
    fun goingBackToEditDropsEverythingFromThatPointOn() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.commit("wobble", 14)
        buffer.commit("wubble", 21)
        // A tap back into the first word.
        buffer.onCaret(3)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun aCaretAtTheEndChangesNothing() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.onCaret(7)
        buffer.onCaret(7)
        assertEquals(listOf("wibble"), buffer.words())
    }

    @Test
    fun anEntryIsNeverDroppedByItsOwnEcho() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        // The next word's commit echo arrives before anything anchors it; it
        // must anchor rather than be read as the caret jumping backwards.
        buffer.push("wobble", "en", 1)
        buffer.onCaret(14)
        assertEquals(listOf("wibble", "wobble"), buffer.words())
    }

    @Test
    fun dropRemovesOneWordByName() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.commit("wobble", 14)
        buffer.drop("Wibble")
        assertEquals(listOf("wobble"), buffer.words())
    }

    @Test
    fun overflowSettlesTheOldestByDistance() {
        val buffer = LearningBuffer(capacity = 2)
        assertTrue(buffer.push("one", "en", 1).isEmpty())
        assertTrue(buffer.push("two", "en", 1).isEmpty())
        val settled = buffer.push("three", "en", 1)
        assertEquals(listOf("one"), settled.map { it.word })
        assertEquals(listOf("two", "three"), buffer.words())
    }

    @Test
    fun drainEmptiesTheBuffer() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        assertEquals(listOf("wibble"), buffer.words())
        assertTrue(buffer.isEmpty())
        assertTrue(buffer.words().isEmpty())
    }

    @Test
    fun clearThrowsTheQueueAway() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.clear()
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun theWeightAndLanguageSurviveTheQueue() {
        val buffer = LearningBuffer()
        buffer.push("wibble", "bn", 2)
        val entry = buffer.drain().single()
        assertEquals("bn", entry.langId)
        assertEquals(2, entry.weight)
    }

    @Test
    fun whetherTheWordWasRecognisedSurvivesTheQueue() {
        val buffer = LearningBuffer()
        buffer.push("wibble", "en", 1, caseTrusted = true, known = true)
        buffer.push("wobble", "en", 1)
        val (recognised, unrecognised) = buffer.drain()
        assertTrue(recognised.known)
        assertTrue(recognised.caseTrusted)
        assertFalse(unrecognised.known)
    }

    /**
     * The whole point of #101: a recognised word the user glided, read back
     * and took out again must never reach the personal dictionary.
     */
    @Test
    fun aRecognisedWordBackspacedAwayNeverSettles() {
        val buffer = LearningBuffer()
        buffer.push("form", "en", 1, known = true)
        buffer.onCaret(4)
        // Backspacing the swipe away puts the caret in front of the word.
        buffer.onCaret(0)
        assertTrue(buffer.isEmpty())
    }

    // --- manual fixes: dropped words pairing with what replaced them ---

    @Test
    fun onCaretHandsBackWhatItDropped() {
        val buffer = LearningBuffer()
        buffer.commit("teh", 4)
        val dropped = buffer.onCaret(3)
        assertEquals(listOf("teh"), dropped.map { it.word })
        assertEquals(4, dropped.single().anchor)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun aDroppedWordPairsWithTheWordCommittedInItsPlace() {
        val buffer = LearningBuffer()
        buffer.commit("teh", 4)
        // Backspace into it, retype, space: the new word ends where the old did.
        buffer.onCaret(3)
        buffer.push("the", "en", 1, known = true)
        buffer.onCaret(4)
        val entry = buffer.drain().single()
        assertEquals("teh", entry.replaces)
        assertTrue(entry.suspect)
        assertNull(entry.revised)
    }

    @Test
    fun anExplicitReplacesIsNeverOverwrittenByAGuess() {
        val buffer = LearningBuffer()
        buffer.commit("teh", 4)
        buffer.onCaret(3)
        buffer.push("the", "en", 1, known = true, replaces = "tge")
        buffer.onCaret(4)
        val entry = buffer.drain().single()
        assertEquals("tge", entry.replaces)
        assertFalse(entry.suspect)
    }

    @Test
    fun positionAloneIsOnlyEverASuspicion() {
        // "the cat sat": the caret goes back before "sat" and "so" is typed in
        // front of it. By position and distance this looks like sat -> so; the
        // suspect flag is what sends the caller to the field to check.
        val buffer = LearningBuffer()
        buffer.commit("the", 4)
        buffer.commit("cat", 8)
        buffer.commit("sat", 12)
        buffer.onCaret(8)
        buffer.push("so", "en", 1, known = true)
        buffer.onCaret(11)
        val entry = buffer.drain().last()
        assertEquals("sat", entry.replaces)
        assertTrue(entry.suspect)
    }

    @Test
    fun aWordFarFromTheDroppedOneIsNotItsReplacement() {
        val buffer = LearningBuffer()
        buffer.commit("teh", 4)
        buffer.onCaret(3)
        buffer.push("the", "en", 1, known = true)
        // Anchored twenty characters away: the user went on writing elsewhere.
        buffer.onCaret(24)
        assertNull(buffer.drain().single().replaces)
    }

    @Test
    fun aRewriteIsNotAFix() {
        val buffer = LearningBuffer()
        buffer.commit("wibble", 7)
        buffer.onCaret(2)
        buffer.push("zzzzzz", "en", 1, known = true)
        buffer.onCaret(7)
        assertNull(buffer.drain().single().replaces)
    }

    @Test
    fun aFatFingeredSpacePutBackPairsBothHalves() {
        val buffer = LearningBuffer()
        buffer.commit("thisbis", 8)
        buffer.onCaret(5)
        buffer.push("this", "en", 1, known = true)
        buffer.onCaret(5)
        buffer.push("is", "en", 1, known = true)
        buffer.onCaret(8)
        val (first, second) = buffer.drain()
        assertNull(first.replaces)
        assertEquals("thisbis", second.replaces)
        assertEquals("this is", second.revised)
        assertTrue(second.suspect)
    }

    @Test
    fun theSameSpellingComingBackIsNotAReplacement() {
        val buffer = LearningBuffer()
        buffer.commit("teh", 4)
        buffer.onCaret(3)
        buffer.push("Teh", "en", 1)
        buffer.onCaret(4)
        assertNull(buffer.drain().single().replaces)
    }

    @Test
    fun drainForgetsTheDroppedWords() {
        val buffer = LearningBuffer()
        buffer.commit("teh", 4)
        buffer.onCaret(3)
        buffer.drain()
        buffer.push("the", "en", 1, known = true)
        buffer.onCaret(4)
        assertNull(buffer.drain().single().replaces)
    }

    @Test
    fun anExplicitAnchorIsKeptAndTheNextEchoDoesNotMoveIt() {
        val buffer = LearningBuffer()
        // A word fixed in place at 10..13, then the caret taps to the end of the text.
        buffer.push("the", "en", 1, known = true, replaces = "teh", anchor = 13)
        buffer.onCaret(40)
        val entry = buffer.drain().single()
        assertEquals(13, entry.anchor)
        assertEquals("teh", entry.replaces)
    }

    @Test
    fun theTapsAndTypedTextSurviveTheQueue() {
        val buffer = LearningBuffer()
        val taps = listOf(TouchPoint(1f, 1f), null, TouchPoint(3f, 1f))
        val keys = KeyTouchModel(mapOf('t' to TouchPoint(1f, 1f)))
        buffer.push("the", "en", 2, known = true, origin = WordOrigin.PICK, typed = "th", taps = taps, keys = keys)
        val entry = buffer.drain().single()
        assertEquals(WordOrigin.PICK, entry.origin)
        assertEquals("th", entry.typed)
        assertEquals(taps, entry.taps)
        assertTrue(keys === entry.keys)
    }

    @Test
    fun aDroppedWordCarriesItsOrigin() {
        val buffer = LearningBuffer()
        buffer.push("hello", "en", 1, known = true, origin = WordOrigin.GLIDE)
        buffer.onCaret(6)
        val dropped = buffer.onCaret(2).single()
        assertEquals(WordOrigin.GLIDE, dropped.origin)
    }
}
