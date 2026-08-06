package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.theme.KeyEffectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The particle field's lifecycle: spawning wakes it, the frame clock puts it
 * back to sleep once every particle is past its lifetime, and the ring buffer
 * takes any amount of hammering without growing.
 */
class ParticleFieldTest {

    @Test
    fun `spawn wakes the field and frame retires it`() {
        val field = ParticleField()
        assertFalse(field.active)
        field.spawn(10f, 10f, count = 5, glyphCount = 3, now = 1_000L)
        assertTrue(field.active)
        // Mid-life: still alive.
        field.frame(1_000L + ParticleField.LIFETIME_MS / 2)
        assertTrue(field.active)
        // Past every particle's lifetime: the loop's exit condition.
        field.frame(1_000L + ParticleField.LIFETIME_MS + 1)
        assertFalse(field.active)
    }

    @Test
    fun `the ring buffer never grows past capacity`() {
        val field = ParticleField()
        repeat(100) { field.spawn(0f, 0f, count = 12, glyphCount = 1, now = 5_000L) }
        // Every slot is at most CAPACITY entries; the arrays are the capacity.
        assertEquals(ParticleField.CAPACITY, field.bornAt.size)
        assertTrue(field.bornAt.all { it == 0L || it == 5_000L })
    }

    @Test
    fun `spawn with no glyphs is a no-op`() {
        val field = ParticleField()
        field.spawn(0f, 0f, count = 5, glyphCount = 0, now = 1_000L)
        assertFalse(field.active)
    }

    @Test
    fun `clear puts the field to sleep at once`() {
        val field = ParticleField()
        field.spawn(0f, 0f, count = 5, glyphCount = 1, now = 1_000L)
        field.clear()
        assertFalse(field.active)
        assertTrue(field.bornAt.all { it == 0L })
    }

    @Test
    fun `emoji param splits into per-code-point glyphs and never comes back empty`() {
        assertEquals(listOf("🎉", "🔥"), effectGlyphs(KeyEffectKind.EMOJI, "🎉🔥"))
        assertEquals(listOf("🎉"), effectGlyphs(KeyEffectKind.EMOJI, ""))
        assertTrue(effectGlyphs(KeyEffectKind.STARS, "").isNotEmpty())
    }

    @Test
    fun `custom image kind has no text glyphs — its bitmaps come from files`() {
        assertTrue(effectGlyphs(KeyEffectKind.CUSTOM_IMAGE, "ignored").isEmpty())
    }
}
