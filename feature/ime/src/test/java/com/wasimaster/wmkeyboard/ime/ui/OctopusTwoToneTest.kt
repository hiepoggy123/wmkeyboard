package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.prediction.OctopusKind
import com.wasimaster.wmkeyboard.core.prediction.OctopusWord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two tones are how a floating word says what it is at a glance: how much
 * of it you have already written, and whether it is carrying on from you or
 * correcting you. Getting the split wrong would misreport what a flick is about
 * to do, which is worse than getting the colour wrong.
 */
class OctopusTwoToneTest {

    private val head = Color.Gray
    private val accent = Color.Red

    private fun label(word: String, typed: Int, kind: OctopusKind = OctopusKind.COMPLETION) =
        octopusLabel(OctopusWord('x'.code, word, typed, kind, 0), head, accent)

    /** The (text, colour) runs, in order. */
    private fun runs(word: String, typed: Int, kind: OctopusKind = OctopusKind.COMPLETION) =
        label(word, typed, kind).let { annotated ->
            annotated.spanStyles.map { annotated.text.substring(it.start, it.end) to it.item.color }
        }

    @Test
    fun `a completion splits where what you typed ends`() {
        assertEquals(
            listOf("hey" to head, "wood" to accent),
            runs("heywood", 3),
        )
    }

    @Test
    fun `the whole word is still the whole word`() {
        assertEquals("heywood", label("heywood", 3).text)
    }

    @Test
    fun `a next-word prediction has no typed head`() {
        assertEquals(listOf("playing" to accent), runs("playing", 0))
    }

    @Test
    fun `a correction is all accent, because what you typed is the part it disagrees with`() {
        assertEquals(
            listOf("hello" to accent),
            runs("hello", 4, OctopusKind.CORRECTION),
        )
    }

    @Test
    fun `a split past the end of the word does not fall off it`() {
        // Defence against a candidate and a buffer that have drifted apart:
        // an out-of-range split must not throw in the middle of a draw.
        assertEquals(listOf("hi" to head), runs("hi", 9))
    }
}
