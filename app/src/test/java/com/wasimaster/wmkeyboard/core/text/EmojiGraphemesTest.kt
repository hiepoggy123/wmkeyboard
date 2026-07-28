package com.wasimaster.wmkeyboard.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiGraphemesTest {

    private fun len(text: String) = EmojiGraphemes.deleteLength(text)

    @Test
    fun `variation selector sequence deletes whole`() {
        assertEquals(2, len("☠️"))
        assertEquals(2, len("hi ☠️"))
        assertEquals(2, len("❤️"))
    }

    @Test
    fun `skin tone modifier deletes with its base`() {
        assertEquals(4, len("👍🏽"))
        assertEquals(4, len("👋🏿"))
    }

    @Test
    fun `zwj sequence deletes whole`() {
        assertEquals("👨‍👩‍👧".length, len("👨‍👩‍👧"))
        assertEquals("🏋️‍♀️".length, len("🏋️‍♀️"))
        assertEquals("👨‍👩‍👧".length, len("family: 👨‍👩‍👧"))
    }

    @Test
    fun `flag deletes both regional indicators`() {
        assertEquals(4, len("🇧🇩"))
        // Half-typed pair: only the stray indicator goes.
        assertEquals(2, len("🇧"))
        // Two flags in a row still delete one flag.
        assertEquals(4, len("🇧🇩🇺🇸"))
    }

    @Test
    fun `keycap and tag sequences delete whole`() {
        assertEquals("1️⃣".length, len("1️⃣"))
        assertEquals("🏴󠁧󠁢󠁥󠁮󠁧󠁿".length, len("🏴󠁧󠁢󠁥󠁮󠁧󠁿"))
    }

    @Test
    fun `plain text and single-code-point emoji defer to the caller`() {
        assertEquals(0, len("hello"))
        assertEquals(0, len(""))
        assertEquals(0, len("😀"))
        // Bengali conjuncts (including their ZWJ) are not ours to claim.
        assertEquals(0, len("ক্ষ"))
        assertEquals(0, len("র‍্য"))
    }

    private fun forward(text: String) = EmojiGraphemes.forwardDeleteLength(text)

    @Test
    fun `forward delete takes a whole cluster`() {
        assertEquals("👨‍👩‍👧".length, forward("👨‍👩‍👧 rest"))
        assertEquals("👍🏽".length, forward("👍🏽!"))
        assertEquals("☠️".length, forward("☠️x"))
        assertEquals("🇧🇩".length, forward("🇧🇩🇺🇸"))
    }

    @Test
    fun `forward delete over ordinary text takes one character`() {
        assertEquals(1, forward("hello"))
        assertEquals(0, forward(""))
        // A surrogate pair is one character, not two halves.
        assertEquals(2, forward("😀a"))
    }

    @Test
    fun `emoji-only recognises what the strip should draw in the emoji font`() {
        assertTrue(EmojiGraphemes.isEmojiOnly("😂"))
        assertTrue(EmojiGraphemes.isEmojiOnly("❤️"))
        assertTrue(EmojiGraphemes.isEmojiOnly("👍🏽"))
        assertTrue(EmojiGraphemes.isEmojiOnly("👨‍👩‍👧"))
        assertTrue(EmojiGraphemes.isEmojiOnly("🇧🇩"))
        assertTrue(EmojiGraphemes.isEmojiOnly("🏴󠁧󠁢󠁥󠁮󠁧󠁿"))
        // Keycaps are the emoji with ASCII inside them.
        assertTrue(EmojiGraphemes.isEmojiOnly("1️⃣"))
        assertTrue(EmojiGraphemes.isEmojiOnly("#️⃣"))
        assertTrue(EmojiGraphemes.isEmojiOnly("▪️"))
    }

    @Test
    fun `emoji-only rejects words`() {
        assertFalse(EmojiGraphemes.isEmojiOnly("hello"))
        assertFalse(EmojiGraphemes.isEmojiOnly(""))
        assertFalse(EmojiGraphemes.isEmojiOnly(" "))
        assertFalse(EmojiGraphemes.isEmojiOnly("ভালো"))
        assertFalse(EmojiGraphemes.isEmojiOnly("nice 😀"))
        assertFalse(EmojiGraphemes.isEmojiOnly(":tada"))
    }

    @Test
    fun `forward delete keeps combining marks with their base`() {
        // Bengali কি — consonant plus a dependent vowel sign.
        assertEquals("কি".length, forward("কি"))
        // Decomposed "é": the base and its combining acute go in one press.
        assertEquals(2, forward("e\u0301x"))
    }
}
