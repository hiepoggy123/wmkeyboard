package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguageMixConfidenceTest {

    @Test fun `untrained keyboard is neutral`() {
        val mix = LanguageMixConfidence()
        // With no data every language reports the neutral multiplier, so the
        // engine reproduces the old flat weighting exactly.
        assertEquals(LanguageMixConfidence.NEUTRAL, mix.confidenceFor("es"), 0.0)
        assertEquals(LanguageMixConfidence.NEUTRAL, mix.confidenceFor("de"), 0.0)
    }

    @Test fun `the most-used language is boosted, an unused one damped`() {
        val mix = LanguageMixConfidence()
        repeat(50) { mix.record("es") }
        // Spanish is the sole (busiest) language → top of the band.
        assertEquals(LanguageMixConfidence.MAX_CONFIDENCE, mix.confidenceFor("es"), 1e-6)
        // German never used → bottom of the band, below neutral.
        assertEquals(LanguageMixConfidence.MIN_CONFIDENCE, mix.confidenceFor("de"), 1e-6)
    }

    @Test fun `relative use orders two secondaries`() {
        val mix = LanguageMixConfidence()
        repeat(40) { mix.record("es") }
        repeat(5) { mix.record("de") }
        assertTrue(mix.confidenceFor("es") > mix.confidenceFor("de"))
        assertTrue(mix.confidenceFor("de") >= LanguageMixConfidence.MIN_CONFIDENCE)
        assertTrue(mix.confidenceFor("es") <= LanguageMixConfidence.MAX_CONFIDENCE)
    }

    @Test fun `confidence forgets a language the user moved on from`() {
        val mix = LanguageMixConfidence()
        repeat(30) { mix.record("es") }
        val before = mix.confidenceFor("es")
        // The user switches to typing German heavily; Spanish should fade.
        repeat(600) { mix.record("de") }
        assertTrue(mix.confidenceFor("de") > mix.confidenceFor("es"))
        assertTrue(mix.confidenceFor("es") < before)
    }

    @Test fun `usage survives a save and reload`() {
        val file = File.createTempFile("language_mix", ".json").apply { deleteOnExit() }
        LanguageMixConfidence(file).apply {
            repeat(20) { record("es") }
            save()
        }
        val reloaded = LanguageMixConfidence(file)
        assertTrue(reloaded.confidenceFor("es") > reloaded.confidenceFor("de"))
    }

    @Test fun `clear wipes learned habit`() {
        val file = File.createTempFile("language_mix", ".json").apply { deleteOnExit() }
        val mix = LanguageMixConfidence(file)
        repeat(20) { mix.record("es") }
        mix.clear()
        assertEquals(LanguageMixConfidence.NEUTRAL, mix.confidenceFor("es"), 0.0)
        assertTrue(!file.exists())
    }
}
