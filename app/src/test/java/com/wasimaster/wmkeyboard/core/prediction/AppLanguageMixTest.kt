package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLanguageMixTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val chat = "com.facebook.orca"
    private val mail = "com.google.android.gm"

    @Test
    fun `too few words is no opinion`() {
        val mix = AppLanguageMix()
        repeat(2) { mix.record(chat, setOf("bn_rom")) }
        assertNull(mix.prior(chat))
        mix.record(chat, setOf("bn_rom"))
        assertNotNull(mix.prior(chat))
    }

    @Test
    fun `an app leans the way its words go`() {
        val mix = AppLanguageMix()
        repeat(9) { mix.record(chat, setOf("bn_rom")) }
        mix.record(chat, setOf("en"))
        val prior = mix.prior(chat)!!
        assertEquals(0.9, prior.shares.getValue("bn_rom"), 0.05)
        assertEquals(0.1, prior.shares.getValue("en"), 0.05)
        assertNull(mix.prior(mail))
    }

    @Test
    fun `the prior is worth less than a typed word for a young app and never a full swing`() {
        val mix = AppLanguageMix()
        repeat(3) { mix.record(chat, setOf("bn_rom")) }
        val young = mix.prior(chat)!!.weight
        assertTrue("young weight $young", young < 0.5)
        repeat(400) { mix.record(chat, setOf("bn_rom")) }
        val old = mix.prior(chat)!!.weight
        assertTrue("old weight $old", old > young)
        assertTrue(old < AppLanguageMix.MAX_PRIOR_WEIGHT)
        assertTrue(old <= FieldLanguageMix.MAX_PRIOR_WEIGHT)
    }

    @Test
    fun `a word both languages know moves the app nowhere`() {
        val mix = AppLanguageMix()
        repeat(5) { mix.record(chat, setOf("en", "bn_rom")) }
        val prior = mix.prior(chat)!!
        assertEquals(prior.shares.getValue("en"), prior.shares.getValue("bn_rom"), 1e-9)
    }

    @Test
    fun `unknown words drain but never start a tally`() {
        val mix = AppLanguageMix()
        mix.record(chat, emptySet())
        assertFalse(mix.knows(chat))
        repeat(5) { mix.record(chat, setOf("bn_rom")) }
        repeat(300) { mix.record(chat, emptySet()) }
        assertNull("stale evidence must fall back to no opinion", mix.prior(chat))
    }

    @Test
    fun `a changed habit is followed`() {
        val mix = AppLanguageMix()
        repeat(50) { mix.record(chat, setOf("bn_rom")) }
        repeat(200) { mix.record(chat, setOf("en")) }
        val prior = mix.prior(chat)!!
        assertTrue(prior.shares.getValue("en") > prior.shares.getValue("bn_rom"))
    }

    @Test
    fun `the tally survives a restart`() {
        val file = File(temp.root, "app_language_mix.json")
        val first = AppLanguageMix(file)
        repeat(10) { first.record(chat, setOf("bn_rom")) }
        repeat(10) { first.record(mail, setOf("en")) }
        first.save()
        val second = AppLanguageMix(file)
        assertEquals(1.0, second.prior(chat)!!.shares.getValue("bn_rom"), 1e-9)
        assertEquals(1.0, second.prior(mail)!!.shares.getValue("en"), 1e-9)
        second.clear()
        assertFalse(file.exists())
        assertNull(second.prior(chat))
    }

    @Test
    fun `the least recently used app falls off`() {
        val mix = AppLanguageMix()
        for (i in 0 until AppLanguageMix.MAX_APPS + 1) {
            repeat(3) { mix.record("app$i", setOf("en")) }
        }
        assertFalse(mix.knows("app0"))
        assertTrue(mix.knows("app${AppLanguageMix.MAX_APPS}"))
    }
}
