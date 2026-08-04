package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.prediction.CorrectionStats.Penalty
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CorrectionStatsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, "learning/correction_stats.json")

    @Test
    fun sessionRevertBlocksImmediately() {
        val stats = CorrectionStats(null)
        assertEquals(Penalty.NONE, stats.penalty("teh", "the"))
        stats.recordRevert("teh", "the")
        assertEquals(Penalty.BLOCKED, stats.penalty("teh", "the"))
        // A different correction of the same typo is untouched.
        assertEquals(Penalty.NONE, stats.penalty("teh", "ten"))
    }

    @Test
    fun persistedSingleRevertPenalizesAndSecondBlocks() {
        val f = file()
        CorrectionStats(f).apply {
            recordRevert("teh", "the")
            save()
        }
        val nextSession = CorrectionStats(f)
        assertEquals(Penalty.PENALIZED, nextSession.penalty("teh", "the"))
        nextSession.recordRevert("teh", "the")
        nextSession.save()
        assertEquals(Penalty.BLOCKED, CorrectionStats(f).penalty("teh", "the"))
    }

    @Test
    fun penaltiesExpireAcrossManyQuietSessions() {
        val f = file()
        CorrectionStats(f).apply {
            recordRevert("teh", "the")
            save()
        }
        // 181 dirty save-generations with unrelated activity age the pair out.
        repeat(181) {
            CorrectionStats(f).apply {
                recordFired()
                save()
            }
        }
        assertEquals(Penalty.NONE, CorrectionStats(f).penalty("teh", "the"))
    }

    @Test
    fun multiplierColdStartAndBounds() {
        val stats = CorrectionStats(null)
        assertEquals(1.0, stats.confidenceMultiplier(), 1e-9)
        // Below the sample floor nothing moves.
        repeat(10) { stats.recordFired() }
        assertEquals(1.0, stats.confidenceMultiplier(), 1e-9)
        // A very bad run rails at the ceiling.
        repeat(30) {
            stats.recordFired()
            stats.recordRevert("w$it", "f$it")
        }
        assertEquals(2.5, stats.confidenceMultiplier(), 1e-9)
        // A long clean run drifts down and rails at the floor.
        repeat(400) { stats.recordFired() }
        assertTrue(stats.confidenceMultiplier() in 0.85..1.0)
    }

    @Test
    fun halvingWindowForgetsAncientHistory() {
        val stats = CorrectionStats(null)
        repeat(30) {
            stats.recordFired()
            stats.recordRevert("w$it", "f$it")
        }
        assertEquals(2.5, stats.confidenceMultiplier(), 1e-9)
        // Hundreds of clean corrections later the old reverts stop dominating.
        repeat(600) { stats.recordFired() }
        assertTrue(stats.confidenceMultiplier() < 1.5)
    }

    @Test
    fun pairTableIsCapped() {
        val stats = CorrectionStats(null)
        repeat(600) { stats.recordRevert("typed$it", "fix$it") }
        // No direct size accessor; the observable contract is that the most
        // recent pairs are still known.
        assertEquals(Penalty.BLOCKED, stats.penalty("typed599", "fix599"))
    }

    @Test
    fun clearWipesEverything() {
        val f = file()
        val stats = CorrectionStats(f)
        stats.recordRevert("teh", "the")
        stats.save()
        stats.clear()
        assertEquals(Penalty.NONE, CorrectionStats(f).penalty("teh", "the"))
    }

    @Test
    fun nullFileModeIsSessionOnly() {
        val stats = CorrectionStats(null)
        stats.recordRevert("teh", "the")
        stats.save() // no-op, no crash
        assertEquals(Penalty.BLOCKED, stats.penalty("teh", "the"))
    }
}
