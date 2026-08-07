package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typing test's badge logic: the encoding round-trips, unknown ids are
 * dropped, and each badge unlocks exactly at its boundary.
 */
class TypingAchievementsTest {

    private fun result(
        wpm: Double = 60.0,
        correct: Int = 200,
        incorrect: Int = 3,
        extra: Int = 0,
        missed: Int = 0,
        mode: TypingTestMode = TypingTestMode.TIME,
    ) = TypingResult(
        wpm = wpm,
        raw = wpm,
        accuracy = 97.0,
        consistency = 80.0,
        correctChars = correct,
        incorrectChars = incorrect,
        extraChars = extra,
        missedChars = missed,
        seconds = 30.0,
        samples = emptyList(),
        mode = mode,
        configKey = "time30",
    )

    @Test
    fun `encode and decode round trip`() {
        val ids = setOf(TypingAchievements.WPM_100, TypingAchievements.PANGRAM)
        assertEquals(ids, TypingAchievements.decode(TypingAchievements.encode(ids)))
        assertEquals(emptySet<String>(), TypingAchievements.decode(""))
    }

    @Test
    fun `decode drops unknown ids`() {
        assertEquals(
            setOf(TypingAchievements.PERFECT),
            TypingAchievements.decode("perfect,unicorn,"),
        )
    }

    @Test
    fun `wpm badge unlocks at exactly one hundred`() {
        assertTrue(TypingAchievements.WPM_100 in TypingAchievements.evaluate(result(wpm = 100.0), 1, false))
        assertFalse(TypingAchievements.WPM_100 in TypingAchievements.evaluate(result(wpm = 99.9), 1, false))
    }

    @Test
    fun `perfect badge needs all three zero and enough characters`() {
        val clean = result(incorrect = 0, extra = 0, missed = 0)
        assertTrue(TypingAchievements.PERFECT in TypingAchievements.evaluate(clean, 1, false))
        assertFalse(TypingAchievements.PERFECT in TypingAchievements.evaluate(result(incorrect = 1, extra = 0, missed = 0), 1, false))
        assertFalse(TypingAchievements.PERFECT in TypingAchievements.evaluate(result(incorrect = 0, extra = 1, missed = 0), 1, false))
        assertFalse(TypingAchievements.PERFECT in TypingAchievements.evaluate(result(incorrect = 0, extra = 0, missed = 1), 1, false))
    }

    @Test
    fun `a short flawless run is luck not typing`() {
        val tiny = result(
            correct = TypingAchievements.PERFECT_MIN_CHARS - 1,
            incorrect = 0, extra = 0, missed = 0,
        )
        assertFalse(TypingAchievements.PERFECT in TypingAchievements.evaluate(tiny, 1, false))
    }

    @Test
    fun `pangram badge needs quote mode and a pangram prompt`() {
        val quote = result(mode = TypingTestMode.QUOTE)
        assertTrue(TypingAchievements.PANGRAM in TypingAchievements.evaluate(quote, 1, true))
        assertFalse(TypingAchievements.PANGRAM in TypingAchievements.evaluate(quote, 1, false))
        assertFalse(TypingAchievements.PANGRAM in TypingAchievements.evaluate(result(mode = TypingTestMode.TIME), 1, true))
    }

    @Test
    fun `tests badge unlocks at exactly fifty`() {
        assertTrue(TypingAchievements.TESTS_50 in TypingAchievements.evaluate(result(), TypingAchievements.TESTS_GOAL, false))
        assertFalse(TypingAchievements.TESTS_50 in TypingAchievements.evaluate(result(), TypingAchievements.TESTS_GOAL - 1, false))
    }

    @Test
    fun `pangram check covers the shipped fox quote`() {
        assertTrue(
            TypingAchievements.isPangram(
                "The quick brown fox jumps over the lazy dog while the whole town sleeps through the quiet afternoon.",
            ),
        )
        assertFalse(TypingAchievements.isPangram("The five boxing wizards"))
    }
}
