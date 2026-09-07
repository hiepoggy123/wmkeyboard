package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [WordRanks], the per-word rank adjustments behind the word card (#99). */
class WordRanksTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, "learning/word_ranks.json")

    @Test
    fun stepsAreClampedAndKeyed() {
        val ranks = WordRanks(null)
        ranks.set("Boston", 25)
        assertEquals(WordRanks.MAX_STEPS, ranks.offsetOf("boston"))
        ranks.set("boston", -25)
        assertEquals(WordRanks.MIN_STEPS, ranks.offsetOf("BOSTON"))
        assertEquals(mapOf("boston" to WordRanks.MIN_STEPS), ranks.snapshot())
    }

    @Test
    fun zeroRemovesTheEntry() {
        val ranks = WordRanks(null)
        ranks.set("hello", 3)
        ranks.set("hello", 0)
        assertEquals(0, ranks.offsetOf("hello"))
        assertTrue(ranks.isEmpty())
        assertTrue(ranks.snapshot().isEmpty())
    }

    @Test
    fun blankWordsAreRefused() {
        val ranks = WordRanks(null)
        assertFalse(ranks.set("   ", 3))
        assertTrue(ranks.isEmpty())
    }

    @Test
    fun roundTripsThroughTheFile() {
        val f = file()
        WordRanks(f).apply {
            set("hello", 2)
            set("world", -4)
            save()
        }
        val back = WordRanks(f)
        assertEquals(2, back.offsetOf("hello"))
        assertEquals(-4, back.offsetOf("world"))
        // Strongest first, then by word.
        assertEquals(listOf("world" to -4, "hello" to 2), back.all())
    }

    @Test
    fun clearDeletesTheFile() {
        val f = file()
        WordRanks(f).apply {
            set("hello", 2)
            save()
        }
        assertTrue(f.exists())
        WordRanks(f).clear()
        assertFalse(f.exists())
        assertTrue(WordRanks(f).isEmpty())
    }

    @Test
    fun snapshotIsTheLatestState() {
        val ranks = WordRanks(null)
        val before = ranks.snapshot()
        ranks.set("hello", 1)
        // The published map is replaced, never mutated under a reader.
        assertTrue(before.isEmpty())
        assertEquals(1, ranks.snapshot()["hello"])
    }
}
