package com.wasimaster.wmkeyboard.ime

import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.settings.GestureSettings
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.ui.GlideVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The two spaces and the capital a single glided word arrives with, read off
 * the field the service actually wrote into.
 *
 * `GlideSpaceTest` already asks the rules themselves what a given piece of text
 * in front of the caret means, and nothing here re-asks that. What no pure test
 * can answer is whether the service is *wired* to them: that one space is
 * committed in front of the word and another behind it, in that order; that
 * each consults its own gate and only its own; that the shift the stroke
 * committed under is what decides the case; and that the lookbehind the leading
 * space reads is wide enough for the rule it feeds — a rule that gives opposite
 * answers to the same last character.
 *
 * The stroke's word comes from an explicit [GlideVerdict.Word] rather than a
 * decode, because there is no `SuggestionEngine` in this module and never will
 * be — the word lists are `:app` assets, and `:app` depends on this module
 * rather than the other way round. A pick survives an empty decode, which is
 * what makes a commit assertable here at all.
 *
 * That same emptiness is why [GlideVerdict.Cancel] is deliberately **not**
 * covered. With no engine the decode is always empty, so every verdict that is
 * not a [GlideVerdict.Word] already commits nothing: an assertion that a cancel
 * types nothing would stay green with the cancel branch deleted, which makes it
 * worth less than no test at all. It needs a real decoder, so it belongs in an
 * `:app` or on-device test.
 *
 * Driven the way [OctopusGlideRefreshTest] drives it: a subclass that attaches a
 * context and hands back a recording [InputConnection], and no `onCreate` —
 * Robolectric has no shadow for `InputMethodService`, and nothing asserted here
 * comes from there.
 */
@RunWith(RobolectricTestRunner::class)
class GlideCommitSpacingTest {

    // ---- the spaces ----

    /**
     * Reddens if `commitGestureLeadingSpace(ic, state)` is dropped from the
     * commit, which lands the swiped word hard against the typed one —
     * `helloworld` — and equally if `commitGestureSpace` is. The expectation is
     * the whole committed string rather than two `contains` checks, so it pins
     * the order as well: space, word, space.
     */
    @Test
    fun `a glided word is spaced off the text in front of it and spaced for the word after it`() {
        val run = glide(before = "hello")

        assertEquals(" $WORD ", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    /**
     * Issue #34: `he said "` followed by a swiped word came out as
     * `he said " hello`, with the quotation opening on a space.
     *
     * Reddens if `commitGestureLeadingSpace` commits its space unconditionally
     * instead of putting the text behind the caret to `spacesBeforeGlidedWord`
     * first.
     */
    @Test
    fun `a glided word after an opening quote goes straight up against it`() {
        val run = glide(before = "he said \"")

        assertEquals("$WORD ", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    /**
     * The same last character as the test above and the opposite answer, because
     * a straight double quote is an opener or a closer only by what stands
     * behind it on the line.
     *
     * Reddens if the leading space's lookbehind is narrowed to the single
     * character every other branch of the rule needs: with only `"` in hand the
     * quote count on the line comes out odd, the mark reads as an opener, and
     * the word is shoved up against the quotation it is supposed to follow.
     */
    @Test
    fun `a glided word after a closed quotation is spaced from it`() {
        val run = glide(before = "he said \"hi\"")

        assertEquals(" $WORD ", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    /**
     * A word swiped back into the middle of a sentence already has a space after
     * it, and a second would leave a double gap.
     *
     * Reddens if the `spacedAfterCaret` early return leaves `commitGestureSpace`.
     */
    @Test
    fun `the trailing space is skipped when the text after the caret already has one`() {
        val run = glide(before = "hello", after = " there")

        assertEquals(" $WORD", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    /**
     * The setting names the space that follows a glide, and only that one. The
     * space in front of the word is not the same space and not under the same
     * switch: it is what keeps two swiped words from running together, and a
     * user who turned off the trailing one did not ask for `helloworld`.
     *
     * Reddens twice over: dropping the `autoSpaceAfterGlide` gate puts the
     * trailing space back, and hoisting that gate up to cover
     * `commitGestureLeadingSpace` as well takes the leading one away.
     */
    @Test
    fun `turning the space after a glide off leaves the space in front of it alone`() {
        val run = glide(before = "hello", autoSpaceAfterGlide = false)

        assertEquals(" $WORD", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    // ---- the capitals ----
    //
    // All three glide into an empty field, so the leading space is out of the
    // way and the whole expectation is the word and its own trailing space.

    /**
     * The control the two below need: without a case that stays lower, a service
     * that capitalized every glided word would pass both of them.
     *
     * Reddens if the commit stops consulting the shift and title-cases
     * unconditionally — which is what letting auto-capitalize's own shift reach
     * a glide would look like.
     */
    @Test
    fun `a glide with shift off commits the word as it stands`() {
        val run = glide(shift = ShiftState.OFF)

        assertEquals("$WORD ", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    /**
     * Reddens if the `ShiftState.ON` arm of the commit's `when` is dropped, so a
     * held shift commits `world` where the user asked for a name.
     */
    @Test
    fun `a glide under a held shift commits the word title-cased`() {
        val run = glide(shift = ShiftState.ON)

        assertEquals("World ", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    /**
     * Reddens if `ShiftState.CAPS_LOCK` is folded into the `ON` arm, which
     * commits `World` for a stroke drawn under a locked shift.
     */
    @Test
    fun `a glide under caps lock commits the word shouted`() {
        val run = glide(shift = ShiftState.CAPS_LOCK)

        assertEquals("WORLD ", run.typed)
        assertTrue(RAN_SHORT, run.finished)
    }

    // ---- the harness ----

    /**
     * Records what reached the field, and answers the two lookbehind and
     * lookahead reads the spacing rules make, so a test's whole context is the
     * pair of strings it hands this.
     *
     * It is also the tripwire. The glide's scope carries no exception handler,
     * so a throw anywhere in the commit is silent and shows up as a field that
     * did not change — the same symptom as a rule that declined to type a space.
     * Every test reads this before it reads any state.
     */
    private class RecordingInputConnection(
        target: View,
        /** What the field already holds in front of the caret. */
        private val before: String,
        /** And behind it. A leading space here is what makes the trailing one unnecessary. */
        private val after: String,
    ) : BaseInputConnection(target, true) {
        val committed = StringBuilder()
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed.append(text ?: "")
            return true
        }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence =
            (before + committed).takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = after.take(n)
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
    }

    private class Keyboard(val ic: InputConnection) : WMKeyboardService() {
        init { attachBaseContext(RuntimeEnvironment.getApplication()) }
        override fun getCurrentInputConnection(): InputConnection = ic
    }

    /** One finished stroke: what the field was given, and whether the job got that far. */
    private class Stroke(
        private val service: WMKeyboardService,
        private val ic: RecordingInputConnection,
    ) {
        /** Every commit the service made, in the order it made them. */
        val typed: String get() = ic.committed.toString()

        /**
         * Whether the commit ran all the way to its last line, which publishes
         * the strip. The seeded state has no suggestions and nothing else on
         * this path writes any, so this standing means every write into the
         * field has happened — and that is the only thing that lets a test
         * assert a space is *absent*. Without it "the setting is off" and "the
         * coroutine died one line early" are the same observation.
         */
        val finished: Boolean get() = service.uiState.value.suggestions.isNotEmpty()
    }

    /**
     * Plants the state one stroke needs, draws it, and waits the job out.
     *
     * Two of the seeded values are load-bearing and read as hygiene.
     * `glideReady` is otherwise set only by a watcher that waits on a loaded
     * dictionary, and without it `onGesture` returns at the gate and the test
     * body never runs. `learnFromTyping` has to be off, or the commit
     * dereferences the `lateinit` lexicon that only `onCreate` assigns — and the
     * scope would swallow the throw. A secure field would shut the learning off
     * too, and must not be used for it: it also shuts the glide off.
     */
    private fun glide(
        before: String = "",
        after: String = "",
        shift: ShiftState = ShiftState.OFF,
        autoSpaceAfterGlide: Boolean = true,
    ): Stroke {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()), before, after)
        val service = Keyboard(ic)
        val field = WMKeyboardService::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(service) as kotlinx.coroutines.flow.MutableStateFlow<KeyboardUiState>
        flow.value = KeyboardUiState(
            glideReady = true,
            // Not a gate a glide reads — allowsGestureTyping deliberately
            // ignores it — but insurance against the day something on the
            // commit path calls refreshSuggestions, which reaches a snippet
            // store that is one more lateinit only onCreate assigns.
            fieldNoSuggestions = true,
            shiftState = shift,
            settings = KeyboardSettings(
                learnFromTyping = false,
                gesture = GestureSettings(autoSpaceAfterGlide = autoSpaceAfterGlide),
            ),
        )

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(WORD))
        settle(service)
        return Stroke(service, ic)
    }

    /** Grid units, and the width a stroke is measured against. */
    private val keyWidth = 60f

    /** The letter grid, straight off the default QWERTY — no Compose, no measure pass. */
    private fun letterKeys(): List<KeyCenter> {
        val layouts = KeyboardUiState().layouts
        val centers = buildMap {
            for ((rowIndex, row) in layouts.letters.rows.withIndex()) {
                var column = 0
                for (key in row) {
                    if (key.action == KeyAction.Text) {
                        (key.output ?: key.label).firstOrNull()?.let {
                            putIfAbsent(
                                it.lowercaseChar().code,
                                (column * keyWidth) to (rowIndex * keyWidth),
                            )
                        }
                    }
                    column++
                }
            }
        }
        return layouts.glideKeys { centers[it] }
    }

    /** A plain left-to-right stroke. Its shape is irrelevant: the verdict decides the word. */
    private fun stroke(): List<GesturePoint> =
        (0..5).map { GesturePoint(x = it * keyWidth, y = 0f, t = it * 20L) }

    /**
     * Runs the main looper until the glide's own thread hop has come back. Only
     * the resumption after the decode's `withContext` needs this: the test body
     * is already on the main looper, so everything up to it dispatched inline.
     */
    private fun settle(service: WMKeyboardService) {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (service.uiState.value.suggestions.isNotEmpty()) return
            Thread.sleep(SETTLE_STEP_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        /**
         * The word the picker's verdict commits, so no decoder is needed. Lower
         * case on purpose: the capitals tests assert the service's own
         * upper-casing, which says nothing if the word arrives capitalized.
         */
        const val WORD = "world"

        /** Said when the field looks right but the commit stopped short of its end. */
        const val RAN_SHORT = "the glide never reached its last line — the field is only half written"

        const val SETTLE_ROUNDS = 200
        const val SETTLE_STEP_MS = 10L
    }
}
