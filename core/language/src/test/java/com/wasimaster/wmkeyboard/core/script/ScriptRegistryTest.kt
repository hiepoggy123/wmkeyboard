package com.wasimaster.wmkeyboard.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [ScriptRegistry] against [ScriptId].
 *
 * [ScriptRegistry.get] falls back to Latin rather than throwing, which is right
 * at runtime — a layout naming a script this build lacks still draws — and is
 * exactly why the gap needs a test. A [ScriptId] with no [ScriptDef] compiles,
 * passes every other test, and shows up only as a keyboard drawing Cherokee
 * with the user's Latin key font. `LanguageRegistryTest` catches this for the
 * scripts a shipped language names; nothing caught it for the rest until here.
 */
class ScriptRegistryTest {

    @Test
    fun `every ScriptId has a ScriptDef`() {
        val missing = ScriptId.entries.filterNot { ScriptRegistry.isRegistered(it) }
        assertTrue(
            "these ScriptIds silently fall back to Latin: ${missing.joinToString()}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every ScriptDef is filed under its own id`() {
        for (id in ScriptId.entries) {
            assertEquals(
                "ScriptRegistry[$id] returns a def for a different script",
                id,
                ScriptRegistry[id].id,
            )
        }
    }

    /**
     * A zero-width or reversed range would silently break the "is this
     * character in this script" test that cluster deletion and the layout
     * converter's script guess both rely on.
     */
    @Test
    fun `every script declares a non-empty unicode range`() {
        for (id in ScriptId.entries) {
            val range = ScriptRegistry[id].unicodeRange
            assertFalse("script $id declares an empty unicode range", range.isEmpty())
            assertTrue(
                "script $id declares a reversed range ${range.first}..${range.last}",
                range.last >= range.first,
            )
            assertTrue(
                "script $id declares a range outside Unicode",
                range.first >= 0 && range.last <= 0x10FFFF,
            )
        }
    }

    /**
     * The ranges are the main block of each script, so two scripts sharing one
     * would mean a character resolving to whichever happened to be checked
     * first. Latin, Greek and Cyrillic legitimately interleave with the
     * IPA/phonetic blocks, so only exact duplicates are an error.
     */
    @Test
    fun `no two scripts declare the same range`() {
        val byRange = ScriptId.entries.groupBy { ScriptRegistry[it].unicodeRange }
        val clashes = byRange.filterValues { it.size > 1 }
        assertTrue(
            "scripts share an identical unicode range: " +
                clashes.entries.joinToString { (r, ids) -> "$r -> ${ids.joinToString("/")}" },
            clashes.isEmpty(),
        )
    }

    /** A cased script must not also be one the shift key is inert on. */
    @Test
    fun `cased scripts are the ones with distinct capital and small letters`() {
        // Spot-check the three the Keyman work turned up, since they are the
        // ones most likely to be got wrong by hand: Osage and Adlam are cased
        // and Adlam is also RTL, which no other script here combines.
        assertTrue("Osage is a cased script", ScriptRegistry[ScriptId.OSAGE].hasLetterCase)
        assertTrue("Adlam is a cased script", ScriptRegistry[ScriptId.ADLAM].hasLetterCase)
        assertEquals(
            "Adlam runs right to left",
            TextDirection.RTL,
            ScriptRegistry[ScriptId.ADLAM].direction,
        )
        assertFalse("Vai is a syllabary and uncased", ScriptRegistry[ScriptId.VAI].hasLetterCase)
        assertTrue("Warang Citi is a cased script", ScriptRegistry[ScriptId.WARANG_CITI].hasLetterCase)
    }

    /**
     * Hangul is written from three blocks — the syllables the composer commits,
     * and the two jamo blocks the keys emit — and a single contiguous range can
     * hold only one of them. [ScriptDef.contains] is what a "which script is this
     * character" question has to ask, or a grid of conjoining jamo reads as no
     * script at all.
     */
    @Test
    fun `contains answers for every block a script is written from`() {
        val hangul = ScriptRegistry[ScriptId.HANGUL]
        assertTrue("a syllable is Hangul", hangul.contains('한'.code))
        assertTrue("a compatibility jamo is Hangul", hangul.contains('ㄱ'.code))
        assertTrue("a conjoining initial is Hangul", hangul.contains(0x1100))
        assertTrue("a conjoining final is Hangul", hangul.contains(0x11C2))
        assertFalse("Latin is not Hangul", hangul.contains('a'.code))
        // The main block alone stays the cluster-deletion bound.
        assertFalse("jamo are outside the main block", 0x1100 in hangul.unicodeRange)

        val warangCiti = ScriptRegistry[ScriptId.WARANG_CITI]
        assertTrue(warangCiti.contains(0x118A0))
        assertTrue(warangCiti.contains(0x118FF))
        assertFalse(warangCiti.contains(0x11900))
    }

    /** A script written from more than one block must not name its main block twice. */
    @Test
    fun `no script lists its main block among its other blocks`() {
        for (id in ScriptId.entries) {
            val def = ScriptRegistry[id]
            for (range in def.moreRanges) {
                assertFalse("script $id declares an empty extra range", range.isEmpty())
                assertTrue(
                    "script $id repeats its main block ${def.unicodeRange} in moreRanges",
                    range != def.unicodeRange,
                )
            }
        }
    }
}
