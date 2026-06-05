package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiVariantsTest {

    @Test
    fun `hand emoji supports tones`() {
        assertTrue(EmojiVariants.supportsSkinTones("👍"))
        assertTrue(EmojiVariants.supportsSkinTones("👋"))
    }

    @Test
    fun `non-human emoji does not support tones`() {
        assertFalse(EmojiVariants.supportsSkinTones("🔥"))
        assertFalse(EmojiVariants.supportsSkinTones("🎉"))
        assertFalse(EmojiVariants.supportsSkinTones("🐱"))
    }

    @Test
    fun `already toned emoji is not re-toned`() {
        assertFalse(EmojiVariants.supportsSkinTones("👍🏽"))
    }

    @Test
    fun `variants are neutral plus five tones`() {
        val variants = EmojiVariants.variants("👍")
        assertEquals(6, variants.size)
        assertEquals("👍", variants[0])
        assertEquals("👍🏻", variants[1])
        assertEquals("👍🏿", variants[5])
    }

    @Test
    fun `unsupported emoji returns itself only`() {
        assertEquals(listOf("🔥"), EmojiVariants.variants("🔥"))
    }

    @Test
    fun `zwj sequence tones the base only`() {
        // 👩‍💻 woman technologist: tone goes on 👩, tail kept.
        val variants = EmojiVariants.variants("👩‍💻")
        assertEquals(6, variants.size)
        assertEquals("👩🏽‍💻", variants[3])
    }

    @Test
    fun `multi-person sequence is left alone`() {
        // 🤝 handshake as part of two-person sequence 🧑‍🤝‍🧑 has two bases.
        assertEquals(1, EmojiVariants.variants("🧑‍🤝‍🧑").size)
    }

    @Test
    fun `presentation selector is replaced by tone`() {
        // ✌️ = 270C FE0F. Toned form must be 270C 1F3FD without FE0F.
        val variants = EmojiVariants.variants("✌️")
        assertEquals("✌️", variants[0])
        assertEquals("✌🏽", variants[3])
    }
}
