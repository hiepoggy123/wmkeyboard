package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordRevisionTest {

    @Test
    fun aFieldEditIsMirroredAndConfirmedByItsEchoes() {
        // "recie|ve" at offset 10: two backspaces, then "ei" typed.
        val r = WordRevision("recieve", start = 10, cursor = 5, mode = WordRevision.Mode.FIELD)
        r.expectDelete(2, 0)
        assertEquals("recve", r.current())
        assertEquals(WordRevision.Outcome.MATCH, r.onCaret(13))
        r.expectInsert("ei")
        assertEquals("receive", r.current())
        assertEquals(WordRevision.Outcome.MATCH, r.onCaret(15))
        assertEquals(WordRevision.Outcome.LEFT, r.onCaret(30))
        val revision = r.finish()!!
        assertEquals("recieve", revision.original)
        assertEquals("receive", revision.revised)
        assertEquals(17, revision.anchor)
    }

    @Test
    fun aComposingFragmentIsMirroredAsAReplacement() {
        // "rec|ve": the user types e, then i; each keystroke rewrites the fragment.
        val r = WordRevision("recve", start = 0, cursor = 3, mode = WordRevision.Mode.FIELD)
        r.expectReplaceBefore(0, "e")
        assertEquals(WordRevision.Outcome.MATCH, r.onCaret(4))
        r.expectReplaceBefore(1, "ei")
        assertEquals(WordRevision.Outcome.MATCH, r.onCaret(5))
        assertEquals("receive", r.current())
        assertEquals("receive", r.finish()?.revised)
    }

    @Test
    fun aSpacePutBackSplitsTheWord() {
        val r = WordRevision("thisbis", start = 4, cursor = 5, mode = WordRevision.Mode.FIELD)
        r.expectDelete(1, 0)
        r.onCaret(8)
        r.expectInsert(" ")
        r.onCaret(9)
        assertEquals("this is", r.finish()?.revised)
    }

    @Test
    fun aCaretMoveInsideTheWordFollowsTheCursor() {
        val r = WordRevision("wibble", start = 10, cursor = 6, mode = WordRevision.Mode.FIELD)
        assertEquals(WordRevision.Outcome.MOVED, r.onCaret(12))
        assertEquals(2, r.cursor)
        r.expectInsert("x")
        assertEquals("wixbble", r.current())
        assertEquals(WordRevision.Outcome.MATCH, r.onCaret(13))
    }

    @Test
    fun anEchoTheMirrorDidNotPredictResyncsInsideTheWordAndLeavesOutsideIt() {
        val r = WordRevision("wibble", start = 10, cursor = 6, mode = WordRevision.Mode.FIELD)
        r.expectDelete(1, 0)
        assertTrue(r.pending)
        // A hardware arrow key moved the caret instead: follow it, forget the expectation.
        assertEquals(WordRevision.Outcome.MOVED, r.onCaret(12))
        assertFalse(r.pending)
        assertEquals(2, r.cursor)
        r.expectDelete(1, 0)
        assertEquals(WordRevision.Outcome.LEFT, r.onCaret(30))
    }

    @Test
    fun theCaretMaySitOneCharacterPastTheEnd() {
        val r = WordRevision("wibble", start = 10, cursor = 6, mode = WordRevision.Mode.FIELD)
        assertEquals(WordRevision.Outcome.MOVED, r.onCaret(17))
        assertEquals(WordRevision.Outcome.LEFT, r.onCaret(18))
    }

    @Test
    fun leavingTheWordIsOnlyEverJudgedWithNothingPending() {
        val r = WordRevision("wibble", start = 10, cursor = 6, mode = WordRevision.Mode.FIELD)
        assertEquals(WordRevision.Outcome.LEFT, r.onCaret(9))
        assertEquals(WordRevision.Outcome.LEFT, r.onCaret(18))
        assertEquals(WordRevision.Outcome.MOVED, r.onCaret(16))
        assertEquals(WordRevision.Outcome.MOVED, r.onCaret(10))
    }

    @Test
    fun composingModeIgnoresCaretsAndTakesTheBuffer() {
        val r = WordRevision("teh", start = 4, cursor = 3, mode = WordRevision.Mode.COMPOSING)
        assertEquals(WordRevision.Outcome.MATCH, r.onCaret(0))
        r.expectInsert("zzz")
        assertEquals("teh", r.current())
        val revision = r.finish("the")!!
        assertEquals("the", revision.revised)
        assertEquals(7, revision.anchor)
    }

    @Test
    fun nothingChangedIsNoRevision() {
        val r = WordRevision("teh", start = 4, cursor = 3, mode = WordRevision.Mode.COMPOSING)
        assertNull(r.finish("teh"))
        assertNull(r.finish("Teh"))
        assertNull(r.finish(""))
        assertNull(r.finish("   "))
    }

    @Test
    fun aWordDeletedOutrightIsNoRevision() {
        val r = WordRevision("teh", start = 4, cursor = 3, mode = WordRevision.Mode.FIELD)
        r.expectDelete(3, 0)
        assertNull(r.finish())
    }

    @Test
    fun aFieldTrackerCanBeAdoptedByTheComposingBuffer() {
        val r = WordRevision("recieve", start = 10, cursor = 5, mode = WordRevision.Mode.FIELD)
        r.expectDelete(2, 0)
        r.onCaret(13)
        r.expectInsert("ei")
        r.onCaret(15)
        assertTrue(r.covers(10, "receive"))
        assertFalse(r.covers(11, "receive"))
        assertFalse(r.covers(10, "recieve"))
        r.toComposing()
        assertEquals(WordRevision.Mode.COMPOSING, r.mode)
        assertFalse(r.pending)
        // The original is what the user first wrote, not the half-fixed word.
        assertEquals("recieve", r.finish("received")?.original)
    }
}
