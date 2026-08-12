package com.wasimaster.wmkeyboard.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which mark the key beside the spacebar types.
 *
 * A script that ends its sentences with something other than "." says so once,
 * here, and `KeyboardScreen` swaps the period key over and pushes ASCII "." to
 * that key's long-press. The alternative — putting the mark in each layout's
 * long-press list — is what shipped first, and it had the default backwards on
 * every affected layout at once: it made the native mark the deliberate choice
 * and the foreign one what you get by not choosing. Bengali was the only script
 * set up correctly; Devanagari's fifteen layouts, and the built-in Hindi grid,
 * all typed a full stop.
 *
 * These are pinned per script rather than asserted in bulk because the value is
 * a user-visible default in a script none of us reads: getting Khmer's ។ and
 * Myanmar's ။ confused costs nothing at compile time and is invisible in review.
 */
class SentenceTerminatorTest {

    private fun fullStopOf(id: ScriptId): String = ScriptRegistry[id].fullStop

    @Test
    fun `the scripts with their own sentence mark declare it`() {
        assertEquals("Bengali dari", "।", fullStopOf(ScriptId.BENGALI))
        assertEquals("Devanagari danda", "।", fullStopOf(ScriptId.DEVANAGARI))
        assertEquals("Armenian vertsaket", "։", fullStopOf(ScriptId.ARMENIAN))
        assertEquals("Khmer khan", "។", fullStopOf(ScriptId.KHMER))
        assertEquals("Myanmar section", "။", fullStopOf(ScriptId.MYANMAR))
        assertEquals("Ethiopic arat netib", "።", fullStopOf(ScriptId.ETHIOPIC))
        assertEquals("Tibetan shad", "།", fullStopOf(ScriptId.TIBETAN))
        assertEquals("Ol Chiki mucaad", "᱾", fullStopOf(ScriptId.OL_CHIKI))
        assertEquals("Meetei Mayek cheikhei", "꯫", fullStopOf(ScriptId.MEETEI_MAYEK))
        assertEquals("Japanese ideographic full stop", "。", fullStopOf(ScriptId.JAPANESE))
    }

    /**
     * The scripts that genuinely end sentences with a full stop must keep it.
     * Arabic is the one worth stating: Urdu and Sindhi write ۔, but Arabic and
     * Persian write ".", and they share one [ScriptId] — so the mark belongs to
     * those layouts' own period keys, never to the script.
     */
    @Test
    fun `the scripts that use a full stop keep it`() {
        for (id in listOf(
            ScriptId.LATIN, ScriptId.CYRILLIC, ScriptId.GREEK, ScriptId.ARABIC,
            ScriptId.HEBREW, ScriptId.GEORGIAN, ScriptId.THAI, ScriptId.LAO,
            ScriptId.TAMIL, ScriptId.HANGUL, ScriptId.TIFINAGH,
        )) {
            assertEquals("$id should end sentences with a full stop", ".", fullStopOf(id))
        }
    }

    /**
     * The swap only fires on a period key that still types ".", so a script
     * declaring a mark must not also be one whose layouts already type it — that
     * combination silently does nothing. Stated as the rule rather than checked
     * against the layouts, which live in another module.
     */
    @Test
    fun `a declared mark is a single character`() {
        for (id in ScriptId.entries) {
            val mark = fullStopOf(id)
            assertTrue("$id has an empty sentence mark", mark.isNotEmpty())
            assertEquals("$id's sentence mark must be one character: $mark", 1, mark.length)
        }
    }
}
