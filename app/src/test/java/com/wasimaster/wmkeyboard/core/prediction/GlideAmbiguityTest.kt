package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.settings.GlidePickerSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The close-call test behind the glide ambiguity picker, across the four
 * sensitivity tiers a user can pick. The margins are the enum's own, so a
 * retuned tier is retested rather than silently drifting from these cases.
 */
class GlideAmbiguityTest {

    private fun cand(word: String, score: Double) =
        GlideBeam.Candidate(word, score, shapeCost = 0.0, tier = FuzzyBeamSearch.Tier.DICTIONARY)

    /** A leader at zero and a runner-up [gap] nats behind it. */
    private fun pair(gap: Double) = listOf(cand("a", 0.0), cand("b", -gap))

    private val nearTies = GlidePickerSensitivity.NEAR_TIES.margin
    private val closeCalls = GlidePickerSensitivity.CLOSE_CALLS.margin
    private val anyDoubt = GlidePickerSensitivity.ANY_DOUBT.margin
    private val everyPause = GlidePickerSensitivity.EVERY_PAUSE.margin

    @Test
    fun `the tiers widen in order and the default is the old constant`() {
        assertTrue(nearTies < closeCalls)
        assertTrue(closeCalls < anyDoubt)
        assertTrue(anyDoubt < everyPause)
        assertEquals(SuggestionEngine.AMBIGUOUS_MARGIN, closeCalls, 0.0)
        assertTrue(everyPause.isInfinite())
    }

    @Test
    fun `fewer than two readings is never a close call, even at every pause`() {
        for (margin in listOf(nearTies, closeCalls, anyDoubt, everyPause)) {
            assertFalse(SuggestionEngine.glideIsAmbiguous(emptyList(), margin))
            assertFalse(SuggestionEngine.glideIsAmbiguous(listOf(cand("a", 0.0)), margin))
        }
    }

    @Test
    fun `a near tie is a close call at every tier`() {
        val decoded = pair(0.2)
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, nearTies))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, closeCalls))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, anyDoubt))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, everyPause))
    }

    @Test
    fun `a gap under the old constant is a close call from the default tier up`() {
        val decoded = pair(0.8)
        assertFalse(SuggestionEngine.glideIsAmbiguous(decoded, nearTies))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, closeCalls))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, anyDoubt))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, everyPause))
    }

    @Test
    fun `a clear lead only counts as doubt at the eager tiers`() {
        val decoded = pair(2.0)
        assertFalse(SuggestionEngine.glideIsAmbiguous(decoded, nearTies))
        assertFalse(SuggestionEngine.glideIsAmbiguous(decoded, closeCalls))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, anyDoubt))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, everyPause))
    }

    @Test
    fun `a runaway leader is only questioned by every pause`() {
        val decoded = pair(10.0)
        assertFalse(SuggestionEngine.glideIsAmbiguous(decoded, nearTies))
        assertFalse(SuggestionEngine.glideIsAmbiguous(decoded, closeCalls))
        assertFalse(SuggestionEngine.glideIsAmbiguous(decoded, anyDoubt))
        assertTrue(SuggestionEngine.glideIsAmbiguous(decoded, everyPause))
    }

    @Test
    fun `a gap exactly on the margin is not under it`() {
        // Strictly less than: the boundary belongs to "confident", so a tier's
        // number reads as "asks below this", never "at or below".
        assertFalse(SuggestionEngine.glideIsAmbiguous(pair(closeCalls), closeCalls))
        assertFalse(SuggestionEngine.glideIsAmbiguous(pair(nearTies), nearTies))
    }

    @Test
    fun `the default margin is the old constant`() {
        assertTrue(SuggestionEngine.glideIsAmbiguous(pair(1.0)))
        assertFalse(SuggestionEngine.glideIsAmbiguous(pair(1.2)))
    }
}
