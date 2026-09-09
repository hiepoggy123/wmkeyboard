package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.ime.ui.GlideVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Issue #118: a glide left the words on the keys behind.
 *
 * A glide is the one commit that never calls `refreshSuggestions`, because the
 * strip has to keep the stroke's own alternates so a misread word can be tapped
 * away — and `octopus`, the map of words floating over the keys, was only ever
 * written by that method. So `onGesture` published the strip and nothing else,
 * and the keys carried whatever they had been carrying before the stroke, glide
 * after glide, until a space typed by hand finally ran a refresh.
 *
 * What this asserts is **the publish, not the content**. A sentinel is planted
 * on the board before the stroke; the fix writes `octopus` unconditionally, so
 * the sentinel is gone afterwards even when the map it writes is empty. That is
 * what makes the test work with no dictionary, no assets and no settings store —
 * and it is why it still reddens if the three publish lines are reverted, which
 * has been checked by reverting them.
 *
 * The service is driven the way the on-device harness drives it
 * (`VietnameseTypingE2ETest`), through [GlideServiceHarness]: a subclass that
 * attaches a context and hands back a recording field, and no `onCreate`.
 */
@RunWith(RobolectricTestRunner::class)
class OctopusGlideRefreshTest {

    /** The word the picker's verdict commits, so no decoder is needed. */
    private val word = "hello"

    @Test
    fun `a glided word refreshes the words on the keys`() {
        val stale = octopusSentinel()
        val (service, editor, _) = glideKeyboard(glideReadyState(octopus = stale))

        service.onGesture(straightStroke(), glideKeyGrid(), GLIDE_KEY_WIDTH, GlideVerdict.Word(word))
        settle { service.uiState.value.octopus != stale }

        // First, that the glide actually ran. The publish is downstream of this,
        // so an unchanged board only means something once the word has landed.
        assertEquals(word, editor.typed.trim())
        // Then the fix itself: the board was written, so the stale word is gone.
        assertNotEquals(
            "the words on the keys were left behind by the glide (#118)",
            stale,
            service.uiState.value.octopus,
        )
    }
}
