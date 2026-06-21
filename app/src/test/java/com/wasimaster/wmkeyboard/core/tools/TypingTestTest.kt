package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TypingTestTest {

    // ---- prompt generation ----

    @Test
    fun `word mode generates exactly the requested count`() {
        val words = buildTypingPrompt(
            TypingTestMode.WORDS, duration = 30, wordCount = 25,
            punctuation = false, numbers = false, random = Random(1),
        )
        assertEquals(25, words.size)
    }

    @Test
    fun `time mode generates more words than anyone can type`() {
        val words = buildTypingPrompt(
            TypingTestMode.TIME, duration = 15, wordCount = 25,
            punctuation = false, numbers = false, random = Random(1),
        )
        // 60 is the floor; a 15-second run must not be able to exhaust it.
        assertTrue(words.size >= 60)
    }

    @Test
    fun `punctuation capitalises after a full stop and ends the last word`() {
        val words = buildTypingPrompt(
            TypingTestMode.WORDS, duration = 30, wordCount = 60,
            punctuation = true, numbers = false, random = Random(7),
        )
        assertTrue(words.last().endsWith("."))
        for ((index, word) in words.withIndex()) {
            if (index == words.lastIndex) continue
            if (word.endsWith(".") || word.endsWith("!") || word.endsWith("?")) {
                val next = words[index + 1].trimStart('"')
                assertTrue(
                    "expected '$next' to start a sentence",
                    next.first().isUpperCase() || next.first().isDigit(),
                )
            }
        }
    }

    // ---- character comparison ----

    @Test
    fun `finished word marks the untyped tail as missing`() {
        assertEquals(
            listOf(CharState.CORRECT, CharState.CORRECT, CharState.MISSING),
            compareWord("the", "th", live = false),
        )
    }

    @Test
    fun `live word leaves the untyped tail pending`() {
        assertEquals(
            listOf(CharState.CORRECT, CharState.CORRECT, CharState.PENDING),
            compareWord("the", "th", live = true),
        )
    }

    @Test
    fun `overshooting a word is extra, not wrong`() {
        assertEquals(
            listOf(CharState.CORRECT, CharState.CORRECT, CharState.CORRECT, CharState.EXTRA),
            compareWord("the", "thee", live = false),
        )
    }

    // ---- scoring ----

    @Test
    fun `a perfect minute scores one wpm per five characters`() {
        // Ten four-letter words plus nine scored spaces = 49 characters.
        val words = List(10) { TypedWord("abcd", "abcd") }
        val result = scoreTypingTest(
            words = words,
            elapsedMs = 60_000,
            totalKeystrokes = 49,
            correctKeystrokes = 49,
            samples = emptyList(),
            mode = TypingTestMode.WORDS,
            configKey = "words10",
        )
        assertEquals(49, result.correctChars)
        assertEquals(49 / 5.0, result.wpm, 0.001)
        assertEquals(100.0, result.accuracy, 0.001)
        assertEquals(0, result.incorrectChars)
    }

    @Test
    fun `the space after a wrong word does not count as a correct character`() {
        val result = scoreTypingTest(
            words = listOf(TypedWord("the", "teh"), TypedWord("cat", "cat")),
            elapsedMs = 60_000,
            totalKeystrokes = 7,
            correctKeystrokes = 5,
            samples = emptyList(),
            mode = TypingTestMode.WORDS,
            configKey = "words2",
        )
        // "t" and "e"/"h" swapped: t correct, e wrong, h wrong. Then "cat".
        // The separating space is forfeited because "the" was mistyped.
        assertEquals(4, result.correctChars)
        assertEquals(2, result.incorrectChars)
    }

    @Test
    fun `raw counts mistakes that wpm does not`() {
        val result = scoreTypingTest(
            words = listOf(TypedWord("abcd", "abxd")),
            elapsedMs = 60_000,
            totalKeystrokes = 4,
            correctKeystrokes = 3,
            samples = emptyList(),
            mode = TypingTestMode.WORDS,
            configKey = "words1",
        )
        assertEquals(3 / 5.0, result.wpm, 0.001)
        assertEquals(4 / 5.0, result.raw, 0.001)
        assertEquals(75.0, result.accuracy, 0.001)
    }

    @Test
    fun `accuracy remembers corrected mistakes the final text does not show`() {
        // The user typed a wrong letter, backspaced, and got it right; the
        // stored word is perfect but two of three keystrokes were needed.
        val result = scoreTypingTest(
            words = listOf(TypedWord("go", "go")),
            elapsedMs = 60_000,
            totalKeystrokes = 3,
            correctKeystrokes = 2,
            samples = emptyList(),
            mode = TypingTestMode.WORDS,
            configKey = "words1",
        )
        assertEquals(0, result.incorrectChars)
        assertEquals(2 * 100.0 / 3, result.accuracy, 0.001)
    }

    @Test
    fun `an even pace scores full consistency and a spiky one does not`() {
        val steady = (1..5).map { WpmSample(it, 60.0, 60.0, 0) }
        val spiky = listOf(
            WpmSample(1, 20.0, 20.0, 0),
            WpmSample(2, 90.0, 90.0, 0),
            WpmSample(3, 15.0, 15.0, 0),
            WpmSample(4, 100.0, 100.0, 0),
        )
        fun consistency(samples: List<WpmSample>) = scoreTypingTest(
            words = listOf(TypedWord("a", "a")),
            elapsedMs = 60_000, totalKeystrokes = 1, correctKeystrokes = 1,
            samples = samples, mode = TypingTestMode.TIME, configKey = "time60",
        ).consistency

        assertEquals(100.0, consistency(steady), 0.001)
        assertTrue(consistency(spiky) < 60.0)
    }

    // ---- persistence codecs ----

    @Test
    fun `bests round-trip and only improve on a faster run`() {
        val stored = TypingBests.encode(mapOf("time30" to 80.0))
        assertEquals(80.0, TypingBests.decode(stored)["time30"])

        assertNull(TypingBests.improve(stored, "time30", 79.9))
        assertNull("a tie is not a new record", TypingBests.improve(stored, "time30", 80.0))
        assertEquals(80.5, TypingBests.improve(stored, "time30", 80.5)?.get("time30"))
        // A config with no record yet always counts as an improvement.
        assertEquals(10.0, TypingBests.improve(stored, "quote", 10.0)?.get("quote"))
    }

    @Test
    fun `bests decoding survives a corrupt preference`() {
        assertEquals(emptyMap<String, Double>(), TypingBests.decode("garbage;;=;x=y"))
    }

    @Test
    fun `history keeps only the most recent runs, newest last`() {
        var history = ""
        for (wpm in 1..(TypingHistory.LIMIT + 5)) {
            history = TypingHistory.append(history, wpm.toDouble())
        }
        val decoded = TypingHistory.decode(history)
        assertEquals(TypingHistory.LIMIT, decoded.size)
        assertEquals((TypingHistory.LIMIT + 5).toDouble(), decoded.last(), 0.001)
    }

    @Test
    fun `config keys separate the modes and their lengths`() {
        assertEquals("time30", typingConfigKey(TypingTestMode.TIME, 30, 25))
        assertEquals("words25", typingConfigKey(TypingTestMode.WORDS, 30, 25))
        assertEquals("quote", typingConfigKey(TypingTestMode.QUOTE, 30, 25))
    }
}
