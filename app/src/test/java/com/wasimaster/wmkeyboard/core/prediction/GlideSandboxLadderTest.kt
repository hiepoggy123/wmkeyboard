package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The sandbox ladder's promotion rules, which decide when the keyboard offers
 * to change what a swipe types — so the interesting cases are all the ones
 * where it must *not*.
 */
class GlideSandboxLadderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun ladder() = GlideSandboxLadder(File(temp.newFolder(), "glide_sandbox.json"))

    @Test
    fun firstRungWaitsForALexicon() {
        val ladder = ladder()
        assertNull(
            "offered the first rung to a user with almost no learned words",
            ladder.pending(GlideSandboxLadder.PREFER_AT_WORDS - 1),
        )
        assertEquals(
            GlideSandboxPolicy.PREFER_LEARNED,
            ladder.pending(GlideSandboxLadder.PREFER_AT_WORDS),
        )
    }

    @Test
    fun secondRungWaitsForAFullWindowOfEvidence() {
        val ladder = ladder()
        ladder.accept(GlideSandboxPolicy.PREFER_LEARNED)
        // A perfect but short run says nothing: the point of the window is that
        // a handful of lucky strokes cannot carry the decision.
        repeat(GlideSandboxLadder.WINDOW - 1) { ladder.observe(true) }
        assertNull(
            "offered LEARNED_ONLY on a partial window",
            ladder.pending(100_000),
        )
        ladder.observe(true)
        assertEquals(GlideSandboxPolicy.LEARNED_ONLY, ladder.pending(100_000))
    }

    @Test
    fun secondRungNeedsTheSandboxToBeAnsweringNearlyEverything() {
        val ladder = ladder()
        ladder.accept(GlideSandboxPolicy.PREFER_LEARNED)
        // One miss in ten: the policy would be losing accuracy overall at this
        // rate, so it must not be offered.
        repeat(GlideSandboxLadder.WINDOW) { ladder.observe(it % 10 != 0) }
        assertNull("offered LEARNED_ONLY at a 10% new-word rate", ladder.pending(100_000))
    }

    @Test
    fun onlyTheMeasuringRungCollectsEvidence() {
        val ladder = ladder()
        // OFF has no sandbox decode to have an opinion, and LEARNED_ONLY is
        // answered by the sandbox by definition — a window collected on either
        // would be meaningless, and on LEARNED_ONLY would read as perfect.
        repeat(GlideSandboxLadder.WINDOW) { ladder.observe(true) }
        assertEquals(
            "OFF collected evidence it cannot have",
            GlideSandboxPolicy.PREFER_LEARNED,
            ladder.pending(GlideSandboxLadder.PREFER_AT_WORDS),
        )
        ladder.accept(GlideSandboxPolicy.LEARNED_ONLY)
        repeat(GlideSandboxLadder.WINDOW) { ladder.observe(true) }
        assertNull("the top rung offered something", ladder.pending(100_000))
    }

    @Test
    fun acceptingARungDropsTheWindowItWasMeasuredOn() {
        val ladder = ladder()
        ladder.accept(GlideSandboxPolicy.PREFER_LEARNED)
        repeat(GlideSandboxLadder.WINDOW) { ladder.observe(true) }
        ladder.accept(GlideSandboxPolicy.PREFER_LEARNED) // no-op, same rung
        assertEquals(GlideSandboxPolicy.LEARNED_ONLY, ladder.pending(100_000))
    }

    @Test
    fun aDeclinedRungIsNeverOfferedAgain() {
        val ladder = ladder()
        ladder.decline(GlideSandboxPolicy.PREFER_LEARNED)
        assertNull(
            "re-offered a rung the user turned down",
            ladder.pending(GlideSandboxLadder.PREFER_AT_WORDS * 10),
        )
    }

    @Test
    fun theLadderSurvivesARestart() {
        val file = File(temp.newFolder(), "glide_sandbox.json")
        GlideSandboxLadder(file).apply {
            accept(GlideSandboxPolicy.PREFER_LEARNED)
            decline(GlideSandboxPolicy.LEARNED_ONLY)
            repeat(10) { observe(true) }
            save()
        }
        val reopened = GlideSandboxLadder(file)
        assertEquals(GlideSandboxPolicy.PREFER_LEARNED, reopened.accepted())
        // Declined before it was earned, and still declined after.
        repeat(GlideSandboxLadder.WINDOW) { reopened.observe(true) }
        assertNull(reopened.pending(100_000))
    }

    @Test
    fun clearPutsTheUserBackAtTheBottom() {
        val ladder = ladder()
        ladder.accept(GlideSandboxPolicy.LEARNED_ONLY)
        assertFalse(ladder.isEmpty())
        ladder.clear()
        assertTrue(ladder.isEmpty())
        assertEquals(GlideSandboxPolicy.OFF, ladder.accepted())
    }

    /** Direct boot: no file to write to, and nothing may crash. */
    @Test
    fun aStorelessLadderStillWorks() {
        val ladder = GlideSandboxLadder(null)
        ladder.accept(GlideSandboxPolicy.PREFER_LEARNED)
        repeat(GlideSandboxLadder.WINDOW) { ladder.observe(true) }
        assertEquals(GlideSandboxPolicy.LEARNED_ONLY, ladder.pending(100_000))
        ladder.save()
        ladder.reload()
        // Nothing was stored, so a reload is a reset rather than a restore.
        assertEquals(GlideSandboxPolicy.OFF, ladder.accepted())
    }
}
