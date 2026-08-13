package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
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
}
