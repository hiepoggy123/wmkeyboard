package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionWatchTest {

    /** Fires a correction, then reports the caret the editor would echo back. */
    private fun CorrectionWatch.fire(typed: String, corrected: String, caret: Int) {
        push(typed, corrected)
        onCaret(caret)
    }

    @Test
    fun typingStraightPastACorrectionLeavesItUndisturbed() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        watch.fire("acn", "can", 8)
        val settled = watch.drain()
        assertEquals(listOf("the", "can"), settled.map { it.corrected })
        assertTrue(settled.none { it.disturbed })
    }

    @Test
    fun backspacingIntoACorrectionDisturbsIt() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        watch.fire("acn", "can", 8)
        watch.onCaret(7)
        val settled = watch.drain().associateBy { it.corrected }
        assertFalse(settled.getValue("the").disturbed)
        assertTrue(settled.getValue("can").disturbed)
    }

    @Test
    fun goingBackDisturbsEverythingAfterThatPoint() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        watch.fire("acn", "can", 8)
        watch.onCaret(2)
        assertTrue(watch.drain().all { it.disturbed })
    }

    @Test
    fun anEntryIsNeverDisturbedByItsOwnEcho() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        // The next correction's echo arrives before anything anchors it: it
        // must anchor rather than read as the caret jumping backwards.
        watch.push("acn", "can")
        watch.onCaret(8)
        assertTrue(watch.drain().none { it.disturbed })
    }

    @Test
    fun aCaretAtTheEndChangesNothing() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        watch.onCaret(4)
        watch.onCaret(4)
        assertTrue(watch.drain().none { it.disturbed })
    }

    @Test
    fun dropRemovesTheEntryTheUndoAlreadyJudged() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        watch.fire("acn", "can", 8)
        watch.drop("Teh", "The")
        assertEquals(listOf("can"), watch.drain().map { it.corrected })
    }

    @Test
    fun dropOnlyMatchesTheWholePair() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        // Same typed word, a different fix: a rejection of one says nothing
        // about the other, and this store keeps them apart.
        watch.drop("teh", "ten")
        assertEquals(1, watch.size)
    }

    @Test
    fun overflowJudgesTheOldestByDistance() {
        val watch = CorrectionWatch(capacity = 2)
        assertTrue(watch.push("a", "A").isEmpty())
        assertTrue(watch.push("b", "B").isEmpty())
        val settled = watch.push("c", "C")
        assertEquals(listOf("A"), settled.map { it.corrected })
        assertEquals(listOf("B", "C"), watch.drain().map { it.corrected })
    }

    @Test
    fun drainEmptiesTheWatch() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        assertEquals(1, watch.drain().size)
        assertTrue(watch.isEmpty())
        assertTrue(watch.drain().isEmpty())
    }

    @Test
    fun clearThrowsTheQueueAway() {
        val watch = CorrectionWatch()
        watch.fire("teh", "the", 4)
        watch.clear()
        assertTrue(watch.isEmpty())
    }

    // --- a fix rewriting the correction's output ---

    @Test
    fun findNamesTheCorrectionWhoseOutputStandsThere() {
        val watch = CorrectionWatch()
        watch.fire("teh", "ten", 4)
        watch.fire("acn", "can", 8)
        val found = watch.find("Ten", nearAnchor = 4)
        assertEquals("teh", found?.typed)
        // Off by the trailing space either way is the same place.
        assertEquals("teh", watch.find("ten", nearAnchor = 5)?.typed)
        assertEquals("teh", watch.find("ten", nearAnchor = 3)?.typed)
    }

    @Test
    fun findIgnoresTheSameWordSomewhereElse() {
        val watch = CorrectionWatch()
        watch.fire("teh", "ten", 4)
        assertNull(watch.find("ten", nearAnchor = 20))
        assertNull(watch.find("the", nearAnchor = 4))
    }

    @Test
    fun aCorrectionStillWaitingForItsEchoMatchesAnywhere() {
        val watch = CorrectionWatch()
        watch.push("teh", "ten")
        assertEquals("teh", watch.find("ten", nearAnchor = 99)?.typed)
    }

    @Test
    fun removeTakesOneEntryOut() {
        val watch = CorrectionWatch()
        watch.fire("teh", "ten", 4)
        watch.fire("teh", "ten", 12)
        val newest = watch.find("ten", nearAnchor = 12)!!
        watch.remove(newest)
        assertEquals(1, watch.size)
        assertEquals(4, watch.drain().single().anchor)
    }

    @Test
    fun theTapsRideAlong() {
        val watch = CorrectionWatch()
        val taps = listOf(TouchPoint(0f, 0f), null, null)
        val keys = KeyTouchModel(mapOf('t' to TouchPoint(0f, 0f)))
        watch.push("teh", "the", taps, keys)
        val entry = watch.drain().single()
        assertEquals(taps, entry.taps)
        assertTrue(keys === entry.keys)
    }
}
