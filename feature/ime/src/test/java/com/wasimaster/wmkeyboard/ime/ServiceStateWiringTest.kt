package com.wasimaster.wmkeyboard.ime

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.settings.HapticSettings
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.ui.GlideVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * One branch of [WMKeyboardService], and the three other entry points that have
 * to agree with it about when it fires.
 *
 * The branch is in `onSuggestionTapped`: with nothing composing and a word
 * behind the caret that the keyboard put there itself, a tap on the strip
 * *deletes* that word — space included — before committing the pick, because
 * the strip is showing the alternates of the swipe that wrote it. That is
 * correct there and wrong nearly everywhere else, and the service carries three
 * separate answers to it:
 *
 *  - `onOctopusPick` exists as its own path rather than a detour through
 *    `onSuggestionTapped` precisely so a word taken off the *keys* is not
 *    caught by it (discussion #102). Glide "hello", flick `w` for "world", and
 *    the detour would leave "world" standing where "hello " was.
 *  - the same method then arms `lastGestureWord` with its own word, so the
 *    branch is right about *it* — one backspace takes the pick back whole, and
 *    a strip tap straight afterwards replaces it.
 *  - `onCursorMove` clears the flag, because once the caret has moved the strip
 *    is no longer talking about the word behind it.
 *
 * All four tests turn on the same handful of characters reaching the field, so
 * each is written to fail loudly on the one-line change that would break it —
 * named in the KDoc above each. The middle one is the anchor: it is the test
 * that would go red if this harness could not make the branch fire at all,
 * which is what would otherwise let the other three pass for the wrong reason.
 *
 * What this file deliberately leaves alone: the spacing and casing rules
 * themselves (`GlideSpaceTest`, `CaretSpacingTest` own them as pure functions —
 * the interest here is only in which text survives a commit, so every
 * assertion is trimmed), the decode (there is no suggestion engine and no word
 * list in this module, so every stroke reads as nothing and the words below all
 * arrive as explicit picks), and anything that needs a caret the field actually
 * moves — see [RecordingInputConnection].
 *
 * The service is driven the way `OctopusGlideRefreshTest` drives it: a subclass
 * that attaches a context and hands back a recording connection, with `onCreate`
 * deliberately never called — Robolectric has no shadow for `InputMethodService`.
 */
@RunWith(RobolectricTestRunner::class)
class ServiceStateWiringTest {

    /** The word every glide below commits, since no decoder is available to find one. */
    private val glided = "hello"

    /** The word every pick below takes, off the keys or off the strip. */
    private val picked = "world"

    /**
     * Records what reached the field, and is also the tripwire every test
     * springs before it asserts anything else: the glide commit runs in a scope
     * with no exception handler, so a throw anywhere in that job is silent and
     * presents exactly as "the word was not replaced" — which is what half of
     * these tests are trying to prove.
     *
     * `deleteSurroundingText` is implemented rather than inherited, and that is
     * load-bearing: [BaseInputConnection] with no editable behind it accepts the
     * call and does nothing, which would make a test that watches for a deleted
     * word pass whether or not the deletion was attempted.
     *
     * The caret does not move. Nothing here models a selection, so an arrow key
     * is recorded and not applied — see the caret test for what that costs.
     */
    private class RecordingInputConnection(target: View) : BaseInputConnection(target, true) {
        private val committed = StringBuilder()

        /** Key codes the service sent to the editor, in order, downs and ups alike. */
        val keys = mutableListOf<Int>()

        fun text(): String = committed.toString()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed.append(text ?: "")
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            // Nothing is ever after the caret in this harness, so only the
            // lookbehind is honoured.
            committed.setLength(committed.length - beforeLength.coerceAtMost(committed.length))
            return true
        }

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            event?.let { keys += it.keyCode }
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence =
            committed.takeLast(n).toString()
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
    }

    private class Keyboard(private val ic: InputConnection) : WMKeyboardService() {
        init { attachBaseContext(RuntimeEnvironment.getApplication()) }
        override fun getCurrentInputConnection(): InputConnection = ic
    }

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
                                (column * KEY_WIDTH) to (rowIndex * KEY_WIDTH),
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
        (0 until STROKE_POINTS).map {
            GesturePoint(x = it * KEY_WIDTH, y = 0f, t = it * STROKE_STEP_MS)
        }

    /**
     * Writes the state a glide needs, without the half of the service that
     * builds it.
     *
     * `glideReady` is otherwise only set by a watcher that wants a loaded
     * dictionary, and `learnFromTyping` has to be off or the commit dereferences
     * the `lateinit` lexicon that only `onCreate` assigns. `fieldNoSuggestions`
     * is the same kind of necessity one step further out: every pick here ends
     * by rebuilding the strip, and that pass reaches the snippet store, another
     * `lateinit` of `onCreate`'s. A field whose app suppresses the strip is the
     * honest way to keep the rebuild out, and it gates none of the paths below —
     * a glide and a pick both still commit into such a field.
     *
     * Haptics go off for a plainer reason: a pick buzzes, the buzz resolves the
     * platform vibrator through the application context, and none of that is
     * what these tests are asking about.
     */
    private fun seed(service: WMKeyboardService) {
        val field = WMKeyboardService::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(service) as MutableStateFlow<KeyboardUiState>
        flow.value = KeyboardUiState(
            glideReady = true,
            fieldNoSuggestions = true,
            settings = KeyboardSettings(
                learnFromTyping = false,
                haptics = HapticSettings(enabled = false),
            ),
        )
    }

    /**
     * Runs the main looper until the glide job's own thread hops have come back.
     *
     * The strip is what it publishes last — after the decode, after the commit,
     * after the hop that samples the stroke's shape — so a non-empty one means
     * the whole job is done and not merely under way.
     */
    private fun settleGlide(service: WMKeyboardService) {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (service.uiState.value.suggestions.isNotEmpty()) return
            Thread.sleep(SETTLE_STEP_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** A service holding a glided word, with the connection that recorded it. */
    private fun glideOnce(): Pair<WMKeyboardService, RecordingInputConnection> {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        seed(service)
        service.onGesture(stroke(), letterKeys(), KEY_WIDTH, GlideVerdict.Word(glided))
        settleGlide(service)
        // Every test below reads what happened *to* this word, so a job that
        // died on its way here has to be told apart from one that ran and left
        // the word alone.
        assertEquals("the glide never reached the field", glided, ic.text().trim())
        return service to ic
    }

    /**
     * Reddens if `onOctopusPick`'s body is replaced by a call to
     * `onSuggestionTapped`, which is the shape it deliberately is not.
     */
    @Test
    fun `a word taken off the keys is added to the glided word, not put in its place`() {
        val (service, ic) = glideOnce()

        service.onOctopusPick(picked, OctopusSource.FLICK)

        assertEquals("$glided $picked", ic.text().trim())
    }

    /**
     * The anchor: it is what says the deletion branch can fire in this harness
     * at all, so the three tests that assert it did *not* fire mean something.
     *
     * Reddens if the `lastGestureWord` branch is dropped from
     * `onSuggestionTapped`, or if the glide stops arming `lastGestureWord`.
     */
    @Test
    fun `a word taken off the strip is put in the glided word's place`() {
        val (service, ic) = glideOnce()

        service.onSuggestionTapped(picked)

        assertEquals(picked, ic.text().trim())
    }

    /**
     * Reddens if `lastGestureWord = null` is dropped from `onCursorMove`, or if
     * the direction of the arrow it sends is flipped.
     *
     * The caret does not really move here — the recording connection has no
     * caret to move — so what is under test is the flag and not the arithmetic
     * behind it. That is the right half to pin: on a real field the text before
     * a caret nudged one place left still ends in the glided word, so the flag
     * is the only thing standing between a scrubbed caret and a strip tap that
     * eats a word the user has moved away from.
     */
    @Test
    fun `moving the caret after a glide leaves the next strip pick standing on its own`() {
        val (service, ic) = glideOnce()

        service.onCursorMove(CARET_LEFT)

        // First that the move ran at all: everything it is meant to have
        // cleared is invisible until something asks for it.
        assertTrue("no arrow reached the editor", KeyEvent.KEYCODE_DPAD_LEFT in ic.keys)
        assertFalse("the caret went the wrong way", KeyEvent.KEYCODE_DPAD_RIGHT in ic.keys)

        service.onSuggestionTapped(picked)

        assertEquals("$glided $picked", ic.text().trim())
    }

    /**
     * The other half of the octopus pick: it takes no word from the field, but
     * it does hand its own over, so the word it just wrote is the one a strip
     * tap replaces.
     *
     * Reddens if `lastGestureWord = word` is dropped from `onOctopusPick`.
     */
    @Test
    fun `a word taken off the keys is itself replaceable from the strip`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        seed(service)

        service.onOctopusPick(glided, OctopusSource.TAP)
        assertEquals("the pick never reached the field", glided, ic.text().trim())

        service.onSuggestionTapped(picked)

        assertEquals(picked, ic.text().trim())
    }

    private companion object {
        /** Grid units, and the width a stroke is measured against. */
        const val KEY_WIDTH = 60f
        const val STROKE_POINTS = 6
        const val STROKE_STEP_MS = 20L
        /** [WMKeyboardService.onCursorMove]'s argument for one step left. */
        const val CARET_LEFT = -1
        const val SETTLE_ROUNDS = 200
        const val SETTLE_STEP_MS = 10L
    }
}
