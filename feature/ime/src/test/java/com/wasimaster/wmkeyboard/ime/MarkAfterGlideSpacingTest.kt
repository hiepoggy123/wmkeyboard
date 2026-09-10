package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.emoji.EmojiShortcodes
import com.wasimaster.wmkeyboard.core.emoji.EmojiUsage
import com.wasimaster.wmkeyboard.core.settings.GestureSettings
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SuggestionSourceSettings
import com.wasimaster.wmkeyboard.ime.ui.GlideVerdict
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the field holds after a swiped word and one typed mark.
 *
 * `GlideSpaceTest` asks the rule which marks hug a word, and answers off a
 * string. This asks the service the question the user actually asks it, which
 * is a different question: a mark reaches the field down one of three branches
 * of `processTypedText`, and for two releases only one of the three consulted
 * the rule at all. The colon left through the inline emoji search and the
 * apostrophe left through the composing buffer, so both floated a space away
 * from the word while the full stop beside them hugged it (issue #123).
 *
 * So every case here glides a word and types one character, and the character
 * is chosen for the branch it takes rather than for the mark it is. A test that
 * only covered the marks would have stayed green through the whole of #123.
 *
 * Driven by [GlideServiceHarness] the way [GlideCommitSpacingTest] is, and the
 * field is read whole — the point is where the space ended up, not what was
 * committed to put it there.
 */
@RunWith(RobolectricTestRunner::class)
class MarkAfterGlideSpacingTest {

    /**
     * The reported case. The colon is a mark on its way to becoming a `:tada:`
     * query, and it hugs the word either way.
     *
     * Reddens if the inline-emoji branch is put back in front of the space rule
     * instead of behind it.
     */
    @Test
    fun `a colon hugs the word even though it opens an emoji query`() {
        assertEquals("$WORD:", markAfterGlide(":"))
    }

    /**
     * The control that says the test above is about the branch and not about
     * the character: with the search off, the same colon goes down the ordinary
     * path, and the answer must not change.
     */
    @Test
    fun `a colon hugs the word with the emoji search off`() {
        assertEquals("$WORD:", markAfterGlide(":", inlineEmojiSearch = false))
    }

    /**
     * The apostrophe is a word character, so it opens a composing buffer rather
     * than committing — `hello's` has to stay one word to the corrector. That
     * is a reason to buffer it, not a reason to leave a space in front of it.
     *
     * Reddens if the space rule goes back inside the branch that commits.
     */
    @Test
    fun `an apostrophe hugs the word even though it composes`() {
        assertEquals("$WORD'", markAfterGlide("'"))
    }

    /** The marks that build one longer word out of two (issue #123). */
    @Test
    fun `the joining marks hug the word`() {
        for (mark in listOf("-", "–", "—", "\\", "_", "/")) {
            assertEquals(mark, "$WORD$mark", markAfterGlide(mark))
        }
    }

    /**
     * The other half of the same rule, and the reason the list of marks is not
     * simply every mark: a hashtag and a mention attach to the word *after*
     * them, so the space in front of them is the one that belongs.
     */
    @Test
    fun `a hashtag and a mention keep the space in front of them`() {
        assertEquals("$WORD #", markAfterGlide("#"))
        assertEquals("$WORD @", markAfterGlide("@"))
    }

    /**
     * The control the whole file rests on. The space rule now runs in front of
     * every branch, including the one a letter takes, so this is what says it
     * still asks whether the character is a mark.
     *
     * Reddens if the rule is ever widened to "anything that is not a letter" or
     * hoisted past its own test, either of which eats the space between two
     * ordinary words.
     */
    @Test
    fun `a letter keeps the space and starts a new word`() {
        assertEquals("$WORD a", markAfterGlide("a"))
    }

    /**
     * A space the user typed is theirs, and no mark takes it back. Nothing is
     * glided here, so nothing ever armed the one-shot the rule reads.
     *
     * Reddens if the swallow starts reading the field instead of the flag,
     * which would eat a space the user put there on purpose — and it is the
     * only test here that would, since every other one arms the flag first.
     */
    @Test
    fun `a mark after a space the user typed leaves it alone`() {
        val editor = RecordingEditor(initial = "hello ")
        val (service, _, _) = glideKeyboard(editor = editor)
        service.onText(":")
        assertEquals("hello :", editor.text.toString())
    }

    /**
     * The colon took a space back on the way in; a completed shortcode gives it
     * back, because an emoji does stand off the word in front of it.
     *
     * Reddens if `emojiQueryAteSpace` is dropped, which commits `world🎉`.
     */
    @Test
    fun `a completed shortcode gets the swallowed space back`() {
        val run = glideThen(shortcodes = mapOf("tada" to EMOJI)) { service ->
            for (ch in ":tada:") service.onText(ch.toString())
        }
        assertEquals("$WORD $EMOJI", run)
    }

    /**
     * …and a query that never resolves keeps the colon where the space rule put
     * it, so the literal text reads as the punctuation it turned out to be.
     */
    @Test
    fun `an unfinished query leaves the colon hugging the word`() {
        val run = glideThen(shortcodes = mapOf("tada" to EMOJI)) { service ->
            for (ch in ":no") service.onText(ch.toString())
        }
        assertEquals("$WORD:no", run)
    }

    // ---- the harness ----

    /** Glides [WORD], then types [mark], and hands back the whole field. */
    private fun markAfterGlide(mark: String, inlineEmojiSearch: Boolean = true): String =
        glideThen(inlineEmojiSearch = inlineEmojiSearch) { it.onText(mark) }

    /**
     * Glides [WORD] into an empty field, waits the commit out, then runs [type].
     *
     * The field starts empty so the glide's own leading space is out of the way
     * and the expectation is the word, the mark, and whatever space is between
     * them. `learnFromTyping` is off for [glideReadyState]'s reason.
     */
    private fun glideThen(
        inlineEmojiSearch: Boolean = true,
        shortcodes: Map<String, String> = emptyMap(),
        type: (WMKeyboardService) -> Unit,
    ): String {
        val editor = RecordingEditor()
        val (service, _, _) = glideKeyboard(
            state = glideReadyState(
                settings = KeyboardSettings(
                    learnFromTyping = false,
                    gesture = GestureSettings(autoSpaceAfterGlide = true),
                    suggestionSources = SuggestionSourceSettings(
                        inlineEmojiSearch = inlineEmojiSearch,
                    ),
                ),
            ),
            editor = editor,
        )
        if (shortcodes.isNotEmpty()) seedShortcodes(service, shortcodes)

        service.onGesture(straightStroke(), glideKeyGrid(), GLIDE_KEY_WIDTH, GlideVerdict.Word(WORD))
        settle { service.uiState.value.suggestions.isNotEmpty() }
        type(service)
        return editor.text.toString()
    }

    /**
     * Plants the two pieces of the emoji path on the service.
     *
     * Reflection for the same reason `seedState` uses it: the real table is
     * read from an `:app` asset by `onCreate`, and these tests run neither.
     * `EmojiShortcodes.load` is the only way in, so the map is written back out
     * in the file format it parses.
     */
    private fun seedShortcodes(service: WMKeyboardService, codes: Map<String, String>) {
        val table = EmojiShortcodes.load(
            codes.entries.joinToString("\n") { "${it.key}\t${it.value}" }.byteInputStream(),
        )
        plant(service, "emojiShortcodes", table)
        // Committing an emoji records it, and that store is one more thing only
        // `onCreate` builds. A null file keeps it in memory, which is all a
        // test that never reads it back needs.
        plant(service, "emojiUsage", EmojiUsage(null))
    }

    private fun plant(service: WMKeyboardService, name: String, value: Any) {
        val field = WMKeyboardService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(service, value)
    }

    private companion object {
        /** The word the picker's verdict commits, so no decoder is needed. */
        const val WORD = "world"

        /** What `:tada:` resolves to in the planted table. */
        const val EMOJI = "🎉"
    }
}
