package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.composerType
import com.wasimaster.wmkeyboard.core.layout.script
import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptDef
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerTest {

    private val bengali = ScriptRegistry[ScriptId.BENGALI]
    private val latin = ScriptRegistry[ScriptId.LATIN]
    private val devanagari = ScriptDef(
        id = ScriptId.DEVANAGARI,
        composer = ComposerType.INDIC_CLUSTER,
        unicodeRange = 0x0900..0x097F,
    )

    @Test
    fun `factory maps each composer type to the right implementation`() {
        assertSame(NoComposer, composerFor(latin, ComposerType.NONE))
        assertSame(NoComposer, composerFor(latin, ComposerType.DEAD_KEY))
        assertSame(BengaliTransliterateComposer, composerFor(bengali, ComposerType.TRANSLITERATE))
        assertTrue(composerFor(bengali, ComposerType.INDIC_CLUSTER) is IndicClusterComposer)
        assertTrue(composerFor(devanagari, ComposerType.INDIC_CLUSTER) is IndicClusterComposer)
        // A transliterator with no engine for its script degrades, never crashes.
        assertSame(NoComposer, composerFor(devanagari, ComposerType.TRANSLITERATE))
    }

    @Test
    fun `NoComposer is a plain 1-to-1 append`() {
        assertFalse(NoComposer.isTransliterating)
        assertFalse(NoComposer.isClusterShaping)
        assertEquals("abc", NoComposer.composeBuffer("abc"))
        assertEquals("x", NoComposer.contextualForm("x", 'a'))
        assertEquals(1, NoComposer.deleteLength("ab"))
        assertEquals(2, NoComposer.deleteLength("a😀")) // surrogate pair
        assertEquals(0, NoComposer.deleteLength(""))
    }

    @Test
    fun `Avro composer defers to AvroPhonetic and stays a live input method`() {
        val composer = BengaliTransliterateComposer
        assertTrue(composer.isTransliterating)
        for (word in listOf("ami", "bhalo", "achi", "kemon")) {
            assertEquals(AvroPhonetic.transliterate(word), composer.composeBuffer(word))
        }
    }

    @Test
    fun `Bengali cluster composer matches BengaliGraphemes for deletion and vowel form`() {
        val composer = IndicClusterComposer(bengali)
        assertTrue(composer.isClusterShaping)
        for (text in listOf("জ্ঞ", "কজ্ঞ", "ক্ষ", "বাংলা", "")) {
            assertEquals(
                "deletion must match the tested Bengali path for '$text'",
                BengaliGraphemes.clusterDeleteLength(text),
                composer.deleteLength(text),
            )
        }
        // Vowel key া: kar after a consonant, independent at a word start.
        assertEquals("া", composer.contextualForm("া", 'ক'))
        assertEquals("আ", composer.contextualForm("া", null))
    }

    @Test
    fun `the Korean built-in resolves end to end to the Hangul composer`() {
        val spec = BuiltInLayouts.KOREAN
        assertEquals(ScriptId.HANGUL, spec.script().id)
        assertSame(HangulComposer, composerFor(spec.script(), spec.composerType()))
    }

    @Test
    fun `generic Indic deletion removes a conjunct cluster as one unit`() {
        val composer = IndicClusterComposer(devanagari)
        // क् ष  (ka + virama + ssa) is one cluster क्ष.
        assertEquals(3, composer.deleteLength("क्ष"))
        // Consonant + spacing vowel sign कि deletes together.
        assertEquals(2, composer.deleteLength("कि"))
        // Preceded by another syllable: only the last cluster goes.
        assertEquals(3, composer.deleteLength("न" + "क्ष"))
        // A non-Devanagari ending falls back to one char.
        assertEquals(1, composer.deleteLength("ab"))
    }
}
