package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldLanguageMixTest {

    @Test fun `no words means no opinion`() {
        assertNull(FieldLanguageMix().shares())
    }

    @Test fun `one word is half-strength evidence`() {
        val mix = FieldLanguageMix()
        mix.record(setOf("bn_rom"))
        val shares = mix.shares()
        assertNotNull(shares)
        assertEquals(1.0, shares!!.shareOf("bn_rom"), 1e-9)
        assertEquals(0.5, shares.ramp, 1e-9)
    }

    @Test fun `three words are full-strength evidence`() {
        val mix = FieldLanguageMix()
        repeat(3) { mix.record(setOf("bn_rom")) }
        assertEquals(1.0, mix.shares()!!.ramp, 1e-9)
    }

    @Test fun `a word both languages own moves the mix nowhere`() {
        val mix = FieldLanguageMix()
        repeat(3) { mix.record(setOf("en", "bn_rom")) }
        val shares = mix.shares()!!
        assertEquals(shares.shareOf("en"), shares.shareOf("bn_rom"), 1e-9)
    }

    @Test fun `recent words outweigh older ones`() {
        val mix = FieldLanguageMix()
        repeat(3) { mix.record(setOf("en")) }
        repeat(3) { mix.record(setOf("bn_rom")) }
        val shares = mix.shares()!!
        assertTrue(shares.shareOf("bn_rom") > shares.shareOf("en"))
    }

    @Test fun `a run of unknown words drains the evidence`() {
        val mix = FieldLanguageMix()
        mix.record(setOf("bn_rom"))
        repeat(4) { mix.record(emptySet()) }
        assertNull("stale evidence must fall back to neutral", mix.shares())
    }

    @Test fun `reset forgets the field`() {
        val mix = FieldLanguageMix()
        repeat(3) { mix.record(setOf("bn_rom")) }
        mix.reset()
        assertNull(mix.shares())
    }

    @Test fun `a prior is worth part of a word and leans the field`() {
        val mix = FieldLanguageMix()
        mix.seedPrior(FieldLanguageMix.Prior(mapOf("bn_rom" to 0.8, "en" to 0.2), 1.0))
        val shares = mix.shares()!!
        assertEquals(0.8, shares.shareOf("bn_rom"), 1e-9)
        assertEquals(0.5, shares.ramp, 1e-9)
    }

    @Test fun `one real word outweighs the prior`() {
        val mix = FieldLanguageMix()
        mix.seedPrior(FieldLanguageMix.Prior(mapOf("bn_rom" to 0.9, "en" to 0.1), FieldLanguageMix.MAX_PRIOR_WEIGHT))
        mix.record(setOf("en"))
        val shares = mix.shares()!!
        assertTrue("en ${shares.shareOf("en")} should lead", shares.shareOf("en") > shares.shareOf("bn_rom"))
    }

    @Test fun `a prior never exceeds its cap`() {
        val mix = FieldLanguageMix()
        mix.seedPrior(FieldLanguageMix.Prior(mapOf("bn_rom" to 1.0), 50.0))
        // Capped under full evidence: the ramp stays short of 1.
        assertTrue(mix.shares()!!.ramp < 1.0)
    }

    @Test fun `an even prior moves nothing`() {
        val mix = FieldLanguageMix()
        mix.seedPrior(FieldLanguageMix.Prior(mapOf("bn_rom" to 0.5, "en" to 0.5), 1.0))
        val shares = mix.shares()!!
        assertEquals(shares.shareOf("en"), shares.shareOf("bn_rom"), 1e-9)
    }
}
