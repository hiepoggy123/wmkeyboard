package com.wasimaster.wmkeyboard.core.text

import org.junit.Assert.assertEquals
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
}
