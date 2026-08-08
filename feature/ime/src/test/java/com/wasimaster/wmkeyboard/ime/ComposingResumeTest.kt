package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.input.composer.composerFor
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.composerType
import com.wasimaster.wmkeyboard.core.layout.script
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which layouts may have the word a caret landed on re-armed as the composing
 * region. Built from the shipped layout specs the way the service builds the
 * live composer, so a layout that changes script or composer is checked here as
 * it is actually configured rather than against a hand-made stand-in.
 */
class ComposingResumeTest {

    private fun composerOf(spec: LayoutSpec) = composerFor(spec.script(), spec.composerType())

    private fun resumable(spec: LayoutSpec, hasWordSources: Boolean = true) =
        composingResumable(composerOf(spec), hasWordSources)

    @Test
    fun `english resumes`() {
        assertTrue(resumable(BuiltInLayouts.QWERTY))
    }

    @Test
    fun `other latin languages resume too`() {
        // The gate used to read `language.isEnglish`, inherited from a glide
        // flag; these complete from the same sources English does.
        for (spec in listOf(BuiltInLayouts.FRENCH, BuiltInLayouts.GERMAN, BuiltInLayouts.SPANISH)) {
            assertTrue(spec.id, resumable(spec))
        }
    }

    @Test
    fun `non-latin scripts that type themselves resume`() {
        // Cyrillic, Greek, Arabic and Hebrew all compose their own script, so
        // the word read back out of the field is exactly what the buffer holds.
        for (spec in listOf(
            BuiltInLayouts.RUSSIAN,
            BuiltInLayouts.GREEK,
            BuiltInLayouts.ARABIC,
            BuiltInLayouts.HEBREW,
        )) {
            assertTrue(spec.id, resumable(spec))
        }
    }

    @Test
    fun `a language with nothing to complete from does not resume`() {
        // No bundled list, no imported one, nothing learned: the underline
        // would sit there offering nothing.
        assertFalse(resumable(BuiltInLayouts.FRENCH, hasWordSources = false))
        assertFalse(resumable(BuiltInLayouts.QWERTY, hasWordSources = false))
    }

    @Test
    fun `avro does not resume`() {
        // Its buffer is the roman source of the Bengali in the field, and the
        // Bengali cannot be reversed back into it.
        val avro = composerOf(BuiltInLayouts.AVRO)
        assertTrue(avro.isTransliterating)
        assertFalse(composingResumable(avro, hasWordSources = true))
    }

    @Test
    fun `fixed bengali does not resume`() {
        // Probhat and Jatiya type Bengali straight into the field and never
        // compose, so a region armed behind them is extended by nothing — and
        // backspace would take the composing path and shed one code unit where
        // a whole conjunct cluster should go.
        for (spec in listOf(BuiltInLayouts.PROBHAT, BuiltInLayouts.JATIYA)) {
            val composer = composerOf(spec)
            assertTrue(spec.id, composer.isClusterShaping)
            assertFalse(spec.id, composingResumable(composer, hasWordSources = true))
        }
    }

    @Test
    fun `other fixed indic layouts do not resume either`() {
        assertFalse(resumable(BuiltInLayouts.HINDI))
    }

    @Test
    fun `hangul does not resume`() {
        assertFalse(resumable(BuiltInLayouts.KOREAN))
    }
}
