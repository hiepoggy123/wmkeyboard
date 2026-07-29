package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The width-fitting maths behind the suggestion strip's shrinking words. */
class SuggestionTextFitTest {

    @Test
    fun `a word that already fits is left alone`() {
        val fit = fitSuggestionText(measuredWidthPx = 80f, availableWidthPx = 120f)
        assertEquals(1f, fit.fontScale, 0f)
        assertEquals(1f, fit.scaleX, 0f)
        assertFalse(fit.condensed)
    }

    @Test
    fun `a word exactly as wide as its slot is left alone`() {
        val fit = fitSuggestionText(measuredWidthPx = 120f, availableWidthPx = 120f)
        assertEquals(1f, fit.fontScale, 0f)
        assertEquals(1f, fit.scaleX, 0f)
    }

    @Test
    fun `mild overflow shrinks the font only`() {
        // 20% too wide: 0.8 is still above the 0.68 font floor, so no condensing.
        val fit = fitSuggestionText(measuredWidthPx = 150f, availableWidthPx = 120f)
        assertEquals(0.8f, fit.fontScale, 0.001f)
        assertEquals(1f, fit.scaleX, 0f)
        assertFalse(fit.condensed)
    }

    @Test
    fun `heavy overflow pins the font floor and condenses the rest`() {
        // Needs 0.6; the font stops at 0.68, so the glyphs take 0.6 / 0.68.
        val fit = fitSuggestionText(measuredWidthPx = 200f, availableWidthPx = 120f)
        assertEquals(0.68f, fit.fontScale, 0.001f)
        assertEquals(0.6f / 0.68f, fit.scaleX, 0.001f)
        assertTrue(fit.condensed)
    }

    @Test
    fun `an unfittable word bottoms out at both floors`() {
        val fit = fitSuggestionText(measuredWidthPx = 1000f, availableWidthPx = 120f)
        assertEquals(0.68f, fit.fontScale, 0.001f)
        assertEquals(0.8f, fit.scaleX, 0.001f)
    }

    @Test
    fun `the two floors together fit a word about 1_8x the slot`() {
        val fit = fitSuggestionText(measuredWidthPx = 1000f, availableWidthPx = 120f)
        val widest = 1f / (fit.fontScale * fit.scaleX)
        assertEquals(1.84f, widest, 0.02f)
    }

    @Test
    fun `longer words never scale up more than shorter ones`() {
        // Monotonic: growing the word can only ever shrink the drawn result.
        var previous = 1f
        for (width in 100..400 step 10) {
            val fit = fitSuggestionText(width.toFloat(), availableWidthPx = 120f)
            val effective = fit.fontScale * fit.scaleX
            assertTrue("width=$width went back up", effective <= previous + 1e-6f)
            previous = effective
        }
    }

    @Test
    fun `a zero-width slot or empty measurement is a no-op`() {
        assertEquals(1f, fitSuggestionText(200f, 0f).fontScale, 0f)
        assertEquals(1f, fitSuggestionText(200f, -5f).fontScale, 0f)
        assertEquals(1f, fitSuggestionText(0f, 120f).fontScale, 0f)
    }
}
