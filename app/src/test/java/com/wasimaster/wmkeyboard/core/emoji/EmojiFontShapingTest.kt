package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe is injected, so the whole decision is testable off-device — only
 * `Paint.hasGlyph` needs Android, and it is exactly what these fakes stand in
 * for.
 */
class EmojiFontShapingTest {

    private val heartOnFire = "❤️‍🔥"
    private val heartOnFireUnqualified = "❤‍🔥"

    /** A font that draws only the spellings it was given. */
    private fun font(vararg drawable: String) = EmojiShaper { it in drawable.toSet() }

    @Test
    fun `the system font shapes nothing`() {
        val shaper = EmojiFontShaping.forTypeface(null)
        assertSame(EmojiFontShaping.Identity, shaper)
        assertEquals(heartOnFire, shaper.shape(heartOnFire))
    }

    @Test
    fun `a font that handles the qualified form is left alone`() {
        assertEquals(heartOnFire, font(heartOnFire).shape(heartOnFire))
    }

    /** The Twemoji case: ligatures keyed on the sequence without U+FE0F. */
    @Test
    fun `the selector is dropped for a font whose ligature omits it`() {
        assertEquals(heartOnFireUnqualified, font(heartOnFireUnqualified).shape(heartOnFire))
    }

    /** And the other direction, for a source sequence that arrived unqualified. */
    @Test
    fun `the selector is added for a font whose ligature requires it`() {
        assertEquals(heartOnFire, font(heartOnFire).shape(heartOnFireUnqualified))
    }

    @Test
    fun `an emoji no spelling can draw keeps its standard form`() {
        // hideUnrenderable is what deals with this; mangling the text is not.
        assertEquals(heartOnFire, font("👍").shape(heartOnFire))
    }

    @Test
    fun `a single code point is never rewritten`() {
        val shaper = font()
        assertEquals("🔥", shaper.shape("🔥"))
    }

    @Test
    fun `the answer is cached, so the font is probed once per emoji`() {
        var probes = 0
        val shaper = EmojiShaper { probes++; it == heartOnFireUnqualified }
        repeat(5) { assertEquals(heartOnFireUnqualified, shaper.shape(heartOnFire)) }
        assertTrue("probed $probes times", probes <= 2)
    }

    // ---- candidates ----

    @Test
    fun `candidates offer the unqualified form first`() {
        val candidates = EmojiFontShaping.candidates(heartOnFire)
        assertEquals(heartOnFireUnqualified, candidates.first())
    }

    @Test
    fun `qualifying adds a selector only to text-default bases`() {
        // U+2764 is a text-default symbol and takes one; U+1F525 is
        // emoji-default and does not.
        val candidates = EmojiFontShaping.candidates(heartOnFireUnqualified)
        assertTrue(candidates.toString(), heartOnFire in candidates)
        assertTrue(candidates.none { it.endsWith('️') })
    }

    @Test
    fun `a skin tone is never separated from its base`() {
        // 👍🏽 — the tone already forces emoji presentation, so no selector
        // may be wedged between the two.
        val toned = "👍🏽"
        assertTrue(EmojiFontShaping.candidates(toned).none { it.contains('️') })
    }

    @Test
    fun `a flag is never split by a selector`() {
        // 🇧🇩 is two regional indicators; anything between them is two letters.
        val flag = "🇧🇩"
        assertTrue(EmojiFontShaping.candidates(flag).none { it.contains('️') })
    }

    @Test
    fun `a keycap keeps its selector position`() {
        // 1️⃣ = 0031 FE0F 20E3. Stripped it is 0031 20E3; re-qualified the
        // selector must land back between the digit and the enclosing mark.
        val keycap = "1️⃣"
        val candidates = EmojiFontShaping.candidates(keycap)
        assertEquals("1⃣", candidates.first())
        assertTrue(candidates.toString(), keycap in candidates)
    }
}
