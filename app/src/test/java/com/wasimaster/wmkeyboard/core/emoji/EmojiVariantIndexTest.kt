package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EmojiVariantIndexTest {

    companion object {
        private lateinit var index: EmojiVariantIndex

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val asset = File("src/main/assets/emoji/variants.tsv")
            index = EmojiVariantIndex.load(FileInputStream(asset))
        }
    }

    @Test fun thumbsUpHasFiveUniformTones() {
        assertTrue(index.hasTones("👍"))
        val variants = index.uniformVariants("👍")
        assertEquals(listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), variants)
    }

    @Test fun nonHumanEmojiHasNoTones() {
        assertFalse(index.hasTones("🔥"))
        assertFalse(index.hasTones("🎉"))
        assertTrue(index.uniformVariants("🔥").isEmpty())
    }

    @Test fun handshakeSupportsDualTones() {
        assertTrue(index.hasDualTones("🤝"))
        assertFalse(index.hasDualTones("👍"))
    }

    @Test fun handshakeEqualTonesUseUniformForm() {
        // Same tone on both hands is the single-codepoint toned handshake.
        assertEquals("🤝🏽", index.tonedPair("🤝", 3, 3))
    }

    @Test fun handshakeMixedTonesUseHandSequence() {
        // Light + medium-dark = rightwards hand / leftwards hand ZWJ pair.
        val mixed = index.tonedPair("🤝", 1, 4)
        assertEquals("🫱🏻‍🫲🏾", mixed)
    }

    @Test fun neutralPairIsTheBaseItself() {
        assertEquals("🤝", index.tonedPair("🤝", 0, 0))
    }

    @Test fun mixedNeutralAndTonedHasNoRgiForm() {
        assertNull(index.tonedPair("🤝", 0, 3))
    }

    @Test fun womenHoldingHandsSupportsDualTones() {
        assertTrue(index.hasDualTones("👭"))
        // Uniform tone comes from the single-codepoint form.
        assertEquals("👭🏼", index.tonedPair("👭", 2, 2))
        // Mixed tones decompose into the woman ZWJ handshake woman sequence.
        val mixed = index.tonedPair("👭", 1, 5)
        assertTrue(mixed!!.contains("‍"))
        assertTrue(mixed.contains("🏻"))
        assertTrue(mixed.contains("🏿"))
    }

    @Test fun popupVariantsIncludeNeutralFirst() {
        val popup = index.popupVariants("✌️")
        assertEquals(6, popup.size)
        assertEquals("✌️", popup.first())
    }

    @Test fun zwjProfessionKeepsTones() {
        // Woman health worker: tones apply inside the ZWJ sequence.
        assertTrue(index.hasTones("👩‍⚕️"))
        assertEquals(5, index.uniformVariants("👩‍⚕️").size)
    }
}
