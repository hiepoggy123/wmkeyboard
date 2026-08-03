package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

class AnimatedEmojiTest {

    companion object {
        private lateinit var animated: AnimatedEmoji

        @BeforeClass
        @JvmStatic
        fun setUp() {
            animated = AnimatedEmoji.load(
                FileInputStream(File("src/main/assets/emoji/animated.txt")),
            )
        }
    }

    @Test fun plainEmojiSpellsItsCodePoint() {
        assertEquals("1f600", animated.keyFor("😀"))
    }

    @Test fun zwjSequenceKeepsEveryCodePoint() {
        assertEquals("1f642_200d_2195_fe0f", animated.keyFor("🙂‍↕️"))
    }

    /** The index spells ❤️ with its selector, so the bare heart has to find it. */
    @Test fun variationSelectorIsOptional() {
        assertEquals("2764_fe0f", animated.keyFor("❤️"))
        assertEquals("2764_fe0f", animated.keyFor("❤"))
    }

    /** Most toned emoji have an animation of their own, and it wins. */
    @Test fun skinToneKeepsItsOwnAnimation() {
        assertEquals("1f44d_1f3fd", animated.keyFor("👍🏽"))
    }

    /**
     * A tone with no animation of its own falls back to the neutral one. The
     * bundled index happens to carry all five tones wherever it carries any,
     * so the ladder is exercised against a hand-built index rather than a
     * real emoji that would stop being an example on the next regeneration.
     */
    @Test fun untonedAnimationStandsInForAMissingTone() {
        val index = AnimatedEmoji.load("1f44d\n".byteInputStream())
        assertEquals("1f44d", index.keyFor("👍🏽"))
    }

    /** Flags have no animation; offering one would 404. */
    @Test fun uncoveredEmojiHasNoKey() {
        assertNull(animated.keyFor("🇧🇩"))
        assertNull(animated.keyFor(""))
    }

    @Test fun urlIsTheGstaticAsset() {
        assertEquals(
            "https://fonts.gstatic.com/s/e/notoemoji/latest/1f600/512.gif",
            animated.gifUrl("1f600"),
        )
    }

    @Test fun emptyIndexOffersNothing() {
        assertNull(AnimatedEmoji.EMPTY.keyFor("😀"))
    }
}
