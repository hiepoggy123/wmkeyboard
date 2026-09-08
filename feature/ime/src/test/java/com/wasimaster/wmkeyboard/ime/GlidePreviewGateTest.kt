package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.settings.GlidePreviewSteadiness
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The steadiness gate's rules, and the one property that matters more than any
 * of them: what a lift types is what the screen was showing.
 */
class GlidePreviewGateTest {

    private val light = GlidePreviewSteadiness.LIGHT

    /** A reading: words best first, with scores that fall by [gap] a place. */
    private fun scores(vararg values: Double) = values.toList()

    @Test
    fun theFirstReadingIsPublishedAsItIs() {
        val gate = GlidePreviewGate()
        assertEquals(0, gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L))
        assertEquals("hello", gate.shown)
    }

    @Test
    fun aNarrowChallengerDoesNotTakeTheScreen() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        // "help" now leads, but only by less than the margin, and the hold has
        // long passed — so the screen keeps "hello".
        val at = gate.steady(listOf("help", "hello"), scores(0.0, -0.3), light, 1_000L)
        assertEquals(1, at)
        assertEquals("hello", gate.shown)
    }

    @Test
    fun aClearChallengerTakesTheScreen() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        val at = gate.steady(listOf("help", "hello"), scores(0.0, -4.0), light, 1_000L)
        assertEquals(0, at)
        assertEquals("help", gate.shown)
    }

    @Test
    fun aWordKeepsTheScreenForItsHoldEvenAgainstAClearChallenger() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        val at = gate.steady(listOf("help", "hello"), scores(0.0, -9.0), light, light.holdMs - 1L)
        assertEquals("held a beaten word past its hold", 1, at)
        assertEquals("hello", gate.shown)
    }

    @Test
    fun aWordThatLeavesTheReadingGoesAtOnce() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        // Inside the hold window, and it still goes: it is not a reading of
        // this stroke any more.
        val at = gate.steady(listOf("held", "helm"), scores(0.0, -1.0), light, 1L)
        assertEquals(0, at)
        assertEquals("held", gate.shown)
    }

    @Test
    fun offPublishesEveryReading() {
        val gate = GlidePreviewGate()
        val off = GlidePreviewSteadiness.OFF
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), off, 0L)
        assertEquals(0, gate.steady(listOf("help", "hello"), scores(0.0, -0.1), off, 1L))
        assertEquals("help", gate.shown)
    }

    /**
     * The property the whole feature turns on: a steadier preview must never
     * mean typing a word the user was not shown.
     */
    @Test
    fun aLiftTypesTheWordOnScreen() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        gate.steady(listOf("help", "hello"), scores(0.0, -0.3), light, 1_000L)
        assertEquals("hello", gate.shown)
        assertEquals(
            "the lift typed something other than the word on screen",
            "hello",
            gate.commit(listOf("help", "hello"), scores(0.0, -0.3), light),
        )
    }

    @Test
    fun aLiftTakesTheLeaderOnceTheShownWordIsClearlyBeaten() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        // Held on screen only because it was inside its hold window...
        gate.steady(listOf("help", "hello"), scores(0.0, -9.0), light, 1L)
        assertEquals("hello", gate.shown)
        // ...but the finished stroke is emphatic, so the lift takes its leader.
        assertEquals(
            "help",
            gate.commit(listOf("help", "hello"), scores(0.0, -9.0), light),
        )
    }

    @Test
    fun aLiftTakesTheLeaderWhenTheShownWordIsGone() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello"), scores(0.0), light, 0L)
        assertEquals("held", gate.commit(listOf("held", "helm"), scores(0.0, -1.0), light))
    }

    @Test
    fun aResetStopsTheNextStrokeInheritingThisOne() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello", "help"), scores(0.0, -2.0), light, 0L)
        gate.reset()
        assertEquals(null, gate.shown)
        // A fresh stroke's first reading is published as it is, not judged
        // against the last stroke's word.
        assertEquals(0, gate.steady(listOf("help", "hello"), scores(0.0, -0.1), light, 5L))
        assertEquals("help", gate.shown)
    }

    @Test
    fun anEmptyReadingChangesNothing() {
        val gate = GlidePreviewGate()
        gate.steady(listOf("hello"), scores(0.0), light, 0L)
        assertEquals(0, gate.steady(emptyList(), emptyList(), light, 1_000L))
        assertEquals("hello", gate.shown)
        assertEquals(null, gate.commit(emptyList(), emptyList(), light))
    }

    /** Every tier holds a near-tie and yields to a rout. */
    @Test
    fun everyTierHoldsANarrowChallengerAndYieldsToAClearOne() {
        for (tier in GlidePreviewSteadiness.entries) {
            if (tier == GlidePreviewSteadiness.OFF) continue
            val gate = GlidePreviewGate()
            gate.steady(listOf("hello", "help"), scores(0.0, -2.0), tier, 0L)
            val narrow = tier.margin / 2
            assertEquals(
                "$tier let a challenger $narrow nats ahead take the screen",
                1,
                gate.steady(listOf("help", "hello"), scores(0.0, -narrow), tier, 10_000L),
            )
            assertEquals(
                "$tier held against a challenger ${tier.margin * 4} nats ahead",
                0,
                gate.steady(
                    listOf("help", "hello"), scores(0.0, -tier.margin * 4), tier, 20_000L,
                ),
            )
        }
    }
}
