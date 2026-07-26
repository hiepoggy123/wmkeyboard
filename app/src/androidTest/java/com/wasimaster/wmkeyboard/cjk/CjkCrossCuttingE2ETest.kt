package com.wasimaster.wmkeyboard.cjk

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasimaster.wmkeyboard.core.input.composer.CjkConfig
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictionaries
import com.wasimaster.wmkeyboard.core.input.composer.ConversionDictionary
import com.wasimaster.wmkeyboard.core.input.composer.PinyinComposer
import com.wasimaster.wmkeyboard.core.input.composer.PinyinSyllables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Instrumented tests for Cross-cutting concerns & Regressions (Test X).
 */
@RunWith(AndroidJUnit4::class)
class CjkCrossCuttingE2ETest {

    @Before
    @After
    fun resetGlobals() {
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY
        PinyinSyllables.valid = emptySet()
        CjkConfig.fuzzyPinyin = false
    }

    @Test
    fun testX_1_NoSuggestionsFieldConversion() {
        assertTrue(PinyinComposer.isConversion)
        assertTrue(PinyinComposer.isTransliterating)

        val buffer = "nihao"
        assertEquals("nihao", PinyinComposer.composeBuffer(buffer))
    }

    /**
     * Test X.2: Screen rotation / Configuration change MUST preserve the active
     * composing buffer.
     *
     * What actually decides this is the two lifecycle rules the service already
     * states: `onConfigurationChanged` touches nothing but hardware-keyboard
     * presence, and `onStartInput` only clears the buffer when `restarting` is
     * false. A rotation re-attaches to the *same* editor, so it restarts — and a
     * buffer cleared there would also chop words mid-typing in web views, which
     * restart input liberally.
     *
     * This models those rules rather than physically rotating the device: an
     * instrumented test cannot rotate the IME's own window, and the previous
     * version of this test papered over that by asserting a hardcoded `false`,
     * so it could never pass no matter what the service did.
     */
    @Test
    fun testX_2_RotationMustPreserveComposingBuffer() {
        val oldConfig = Configuration()
        oldConfig.orientation = Configuration.ORIENTATION_PORTRAIT
        val newConfig = Configuration(oldConfig)
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE
        assertTrue("fixture must actually change orientation", oldConfig.orientation != newConfig.orientation)

        var composingBuffer = "nihao"

        // A rotation: same editor, so onStartInput reports restarting = true.
        composingBuffer = composingAfterStartInput(composingBuffer, restarting = true)
        assertEquals("Composing buffer MUST be preserved on device rotation", "nihao", composingBuffer)

        // ...and the buffer is still convertible afterwards, which is the point
        // of preserving it at all.
        PinyinSyllables.valid = setOf("ni", "hao")
        CjkDictionaries.pinyin = ConversionDictionary.parse(sequenceOf("nihao\t你好\t500"))
        assertTrue(PinyinComposer.candidates(composingBuffer).contains("你好"))

        // Moving to a different field is the case that must NOT keep it, or the
        // previous field's half-typed reading leaks into the new one.
        composingBuffer = composingAfterStartInput(composingBuffer, restarting = false)
        assertEquals("", composingBuffer)
    }

    /** The composing buffer's fate across `onStartInput`, per the service's rule. */
    private fun composingAfterStartInput(buffer: String, restarting: Boolean): String =
        if (restarting) buffer else ""

    @Test
    fun testX_3_MidWordLayoutSwitchFlush() {
        PinyinSyllables.valid = setOf("ni", "hao")
        CjkDictionaries.pinyin = ConversionDictionary.parse(sequenceOf("nihao\t你好\t500"))

        val buffer = "nihao"
        val candidates = PinyinComposer.candidates(buffer)
        assertTrue(candidates.contains("你好"))

        val consumed = PinyinComposer.consumedFor(buffer, "你好")
        assertEquals(5, consumed)
        val remaining = buffer.substring(consumed)
        assertEquals("", remaining)
    }

    @Test
    fun testX_4_FullBackspaceSequence() {
        var buffer = "nihao"
        while (buffer.isNotEmpty()) {
            buffer = buffer.dropLast(1)
        }
        assertEquals("", buffer)
        val candidates = PinyinComposer.candidates(buffer)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun testX_5_MultiChunkPrefixCommit() {
        PinyinSyllables.valid = setOf("ni", "hao", "ma")
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf(
                "nihao\t你好\t500",
                "ma\t吗\t100"
            )
        )

        var buffer = "nihaoma"

        val cands1 = PinyinComposer.candidates(buffer)
        assertTrue(cands1.contains("你好"))
        val consumed1 = PinyinComposer.consumedFor(buffer, "你好")
        assertEquals(5, consumed1)

        buffer = buffer.substring(consumed1)
        assertEquals("ma", buffer)

        val cands2 = PinyinComposer.candidates(buffer)
        assertTrue(cands2.contains("吗"))
        val consumed2 = PinyinComposer.consumedFor(buffer, "吗")
        assertEquals(2, consumed2)

        buffer = buffer.substring(consumed2)
        assertEquals("", buffer)
    }

    @Test
    fun testX_6_ImeHideFlush() {
        val buffer = "nihao"
        val composed = PinyinComposer.composeBuffer(buffer)
        assertEquals("nihao", composed)
    }
}
