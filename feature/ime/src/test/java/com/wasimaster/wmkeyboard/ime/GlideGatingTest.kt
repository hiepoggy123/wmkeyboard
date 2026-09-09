package com.wasimaster.wmkeyboard.ime

import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.prediction.OctopusKind
import com.wasimaster.wmkeyboard.core.prediction.OctopusWord
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.ui.GlideVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Every reason [WMKeyboardService] refuses a glide, asserted from the field's
 * side: a refused stroke types nothing and leaves the keyboard's state exactly
 * as it found it.
 *
 * The rules themselves are pure and tested as such (`GlideSpaceTest`,
 * `PossessiveFlickTest`, `OctopusFlickTest`). What is only true of the service
 * is that the gate is actually *in front of* the commit — `glideAllowed` and the
 * two early returns at the top of `onGesture`/`onGestureWords` are the whole of
 * it, and each one is a single line that a refactor can drop without any pure
 * test noticing. So each test here deletes nothing and asserts the negative
 * space: with the guard gone the stroke below commits `hello` into the field,
 * because an explicit [GlideVerdict.Word] survives a decode that finds nothing —
 * which is what makes a refusal provable in a harness with no dictionary in it.
 *
 * Two tripwires, in this order, because the glide scope carries no exception
 * handler and a throw inside the job is silent and looks exactly like a
 * refusal:
 *  - the recording [InputConnection], which is the one that would catch a
 *    committed word;
 *  - a live glide preview planted in the state before the stroke. Every path
 *    past the gate — commit, cancel, possessive — goes through
 *    `clearGlidePreview`, so the sentinel surviving is proof that nothing
 *    downstream of the gate ran at all. That is what catches the two guards
 *    whose fall-through commits nothing even when deleted.
 *
 * Deliberately not covered: the typing-test branch of `glideAllowed`, where the
 * test's own switch overrules the field and the word never reaches it (there is
 * no field to assert on); the readiness watcher that sets
 * [KeyboardUiState.glideReady], which wants a loaded suggestion engine this
 * module cannot build; and `onGesturePreview`, which shares the same gate but
 * publishes nothing a refusal could be told apart by.
 *
 * `onCreate` is deliberately never called, for the reason `ServiceBootProbeTest`
 * gives: Robolectric has no shadow for `InputMethodService`.
 */
@RunWith(RobolectricTestRunner::class)
class GlideGatingTest {

    /** The word the verdict commits, so no decoder is needed. */
    private val word = "hello"

    /** Grid units, and the width a stroke is measured against. */
    private val keyWidth = 60f

    /**
     * Records what reached the field. The commit sits inside the glide job, so
     * a word here proves the job ran; nothing here, once the looper has been
     * pumped, is the refusal this file is about.
     */
    private class RecordingInputConnection(target: View) : BaseInputConnection(target, true) {
        val committed = StringBuilder()
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed.append(text ?: "")
            return true
        }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence =
            committed.takeLast(n).toString()
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
    }

    private class Keyboard(val ic: InputConnection) : WMKeyboardService() {
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
        (0..STROKE_LAST).map { GesturePoint(x = it * keyWidth, y = 0f, t = it * STROKE_STEP_MS) }

    /** The floating word a stroke in flight would have put over the `q` key. */
    private fun previewBoard(): Map<Int, OctopusWord> = mapOf(
        'q'.code to OctopusWord(
            keyCodePoint = 'q'.code,
            word = SENTINEL,
            typedChars = 0,
            kind = OctopusKind.COMPLETION,
            rank = 0,
        ),
    )

    /**
     * The state a glide runs from, with one gate open to the caller.
     *
     * Two of these arguments are load-bearing rather than tidy. `glideReady` is
     * otherwise only set by a watcher that wants a loaded dictionary, so its
     * default of false would refuse every stroke and make each test below pass
     * for the wrong reason. `learnFromTyping` has to be off or the commit
     * dereferences the `lateinit` lexicon that only `onCreate` assigns — and a
     * throw in the glide job is silent, so that would read as a refusal too.
     *
     * The preview fields are the sentinel: a stroke that got past the gate
     * clears all three on its way through, whatever it goes on to type.
     */
    private fun readyToGlide(
        glideReady: Boolean = true,
        settings: KeyboardSettings = KeyboardSettings(learnFromTyping = false),
        secureField: Boolean = false,
        fieldKind: FieldKind = FieldKind.TEXT,
        fieldNoSuggestions: Boolean = false,
    ) = KeyboardUiState(
        glideReady = glideReady,
        settings = settings,
        secureField = secureField,
        fieldKind = fieldKind,
        fieldNoSuggestions = fieldNoSuggestions,
        glideWord = SENTINEL,
        glideChoices = listOf(SENTINEL),
        octopusGlide = previewBoard(),
    )

    /** Writes the state a glide needs, without the half of the service that builds it. */
    private fun seed(service: WMKeyboardService, state: KeyboardUiState): KeyboardUiState {
        val field = WMKeyboardService::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(service) as kotlinx.coroutines.flow.MutableStateFlow<KeyboardUiState>
        flow.value = state
        return state
    }

    /**
     * Runs the main looper until [until] holds, or until a commit that was
     * going to happen has had many times the one thread hop it needs.
     *
     * The wait is what makes a negative assertion mean anything: the commit is
     * on the far side of `withContext(Dispatchers.Default)`, so asserting an
     * empty field the instant `onGesture` returns would be green with every
     * guard in this file deleted.
     */
    private fun settle(until: () -> Boolean) {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (until()) return
            Thread.sleep(SETTLE_STEP_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Both tripwires, field first. [reason] names the gate under test. */
    private fun assertRefused(
        reason: String,
        ic: RecordingInputConnection,
        service: WMKeyboardService,
        seeded: KeyboardUiState,
    ) {
        assertEquals("$reason: the stroke reached the field", "", ic.committed.toString())
        assertSame(
            "$reason: the stroke got past the gate and republished the state",
            seeded,
            service.uiState.value,
        )
    }

    /** A service seeded with [state], and the connection watching its field. */
    private fun keyboard(
        state: KeyboardUiState,
    ): Triple<Keyboard, RecordingInputConnection, KeyboardUiState> {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        return Triple(service, ic, seed(service, state))
    }

    /**
     * Reddens if `!state.glideReady -> false` goes from `glideAllowed`.
     *
     * The gate that matters most to the tests around it: it is off by default,
     * so a service test that forgets to seed it never reaches the code it
     * believes it is exercising. Pinned here so that reading is at least
     * deliberate.
     */
    @Test
    fun `a keyboard whose word lists are not loaded refuses the stroke`() {
        val (service, ic, seeded) = keyboard(readyToGlide(glideReady = false))

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotEmpty() }

        assertRefused("not ready", ic, service, seeded)
    }

    /** Reddens if `state.settings.gestureTyping &&` goes from `glideAllowed`. */
    @Test
    fun `gesture typing switched off refuses the stroke`() {
        val settings = KeyboardSettings(gestureTyping = false, learnFromTyping = false)
        val (service, ic, seeded) = keyboard(readyToGlide(settings = settings))

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotEmpty() }

        assertRefused("gesture typing off", ic, service, seeded)
    }

    /** Reddens if `!secureField &&` goes from `KeyboardUiState.allowsGestureTyping`. */
    @Test
    fun `a password field refuses the stroke`() {
        val (service, ic, seeded) = keyboard(readyToGlide(secureField = true))

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotEmpty() }

        assertRefused("password field", ic, service, seeded)
    }

    /**
     * The whole of `allowsGestureTyping`'s field test, both sides of it.
     *
     * Reddens on the URL half if `|| fieldKind == FieldKind.URI` goes (#37 —
     * an omnibox is a search box as much as an address box), and on the other
     * six kinds if the test is widened past `TEXT`/`URI` — an email address has
     * no dictionary words in it to decode, and a keypad has no letters at all.
     * The two allowed kinds are asserted in the same test as the refused ones
     * on purpose: without them the six refusals could be green for any reason
     * at all, including a harness that never types.
     */
    @Test
    fun `only plain text and URL fields take a glide`() {
        for (kind in FieldKind.entries) {
            val (service, ic, seeded) = keyboard(readyToGlide(fieldKind = kind))

            service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))

            if (kind == FieldKind.TEXT || kind == FieldKind.URI) {
                // Blank rather than empty: the glide's own spacing commits
                // separately from the word, and only the word is the question.
                settle { ic.committed.isNotBlank() }
                assertEquals("$kind should glide", word, ic.committed.toString().trim())
            } else {
                settle { ic.committed.isNotEmpty() }
                assertRefused("$kind", ic, service, seeded)
            }
        }
    }

    /**
     * The control for the two `onGestureWords` refusals below.
     *
     * They are absence claims about a second entry point, and the field-kind
     * control above only ever drives `onGesture`. Without this one, nothing in
     * the file shows that a chained stroke can reach the field at all — so
     * both refusals would be green on a harness that simply never types
     * through that door, which is the failure they exist to rule out.
     */
    @Test
    fun `a chained stroke does reach the field, so the two refusals below mean something`() {
        val (service, ic, _) = keyboard(readyToGlide())

        service.onGestureWords(listOf(stroke()), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotBlank() }

        assertEquals(word, ic.committed.toString().trim())
    }

    /** Reddens if `if (keys.isEmpty()) return` goes from `onGesture`. */
    @Test
    fun `a stroke with no key grid under it is refused`() {
        val (service, ic, seeded) = keyboard(readyToGlide())

        service.onGesture(stroke(), emptyList(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotEmpty() }

        assertRefused("no keys", ic, service, seeded)
    }

    /**
     * Reddens if `clearGlidePreview()` goes from `cancelGlide`.
     *
     * The one refusal that is not a no-op: dragging out of the picker and
     * lifting has to take the stroke's own previews down, or the floating word
     * stays over the keys after the finger has gone. So the sentinel is
     * asserted the other way round here — cleared, not surviving.
     *
     * That a cancel types nothing is NOT what this proves, and the name no
     * longer says it does. With no engine every decode is empty and a `Cancel`
     * carries no word to rescue it, so deleting the guard leaves the stroke
     * committing nothing either way. The empty field below is a tripwire — it
     * says the job did not somehow commit — and not the claim. Whether the
     * guard stops a real decode wants a dictionary, and belongs where there is
     * one.
     *
     * `fieldNoSuggestions` is on because the cancel restores the strip, and
     * `refreshSuggestions` reaches the snippet store, which is one more
     * `lateinit` that only `onCreate` assigns. It is not a gate a glide reads:
     * `allowsGestureTyping` deliberately ignores it, so a field that asked for
     * a quiet strip still swipes.
     */
    @Test
    fun `a cancelled pick takes the stroke's preview down`() {
        val (service, ic, _) = keyboard(readyToGlide(fieldNoSuggestions = true))

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Cancel)
        settle { ic.committed.isNotEmpty() }

        // Tripwire, not the claim — see the note above.
        assertEquals("a cancelled pick reached the field", "", ic.committed.toString())
        val state = service.uiState.value
        assertNull("the floating word outlived the cancelled stroke", state.glideWord)
        assertEquals(emptyList<String>(), state.glideChoices)
        assertEquals(emptyMap<Int, OctopusWord>(), state.octopusGlide)
    }

    /**
     * Reddens if `segments.isEmpty() ||` goes from `onGestureWords`.
     *
     * The sentinel is the only tripwire that can catch this one, and it is why
     * there is a sentinel at all: with the guard deleted the job still commits
     * nothing, because there is no segment to commit — it just clears the
     * preview on its way to doing nothing, which the field cannot see.
     */
    @Test
    fun `a multi-word glide with no segments is refused`() {
        val (service, ic, seeded) = keyboard(readyToGlide())

        service.onGestureWords(emptyList(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotEmpty() }

        assertRefused("no segments", ic, service, seeded)
    }

    /**
     * Reddens if `keys.isEmpty() ||` goes from `onGestureWords`: the pick
     * belongs to the last segment and survives its empty decode, so the word
     * would land in the field with no grid under the stroke at all.
     */
    @Test
    fun `a multi-word glide with no key grid under it is refused`() {
        val (service, ic, seeded) = keyboard(readyToGlide())

        service.onGestureWords(listOf(stroke()), emptyList(), keyWidth, GlideVerdict.Word(word))
        settle { ic.committed.isNotEmpty() }

        assertRefused("no keys", ic, service, seeded)
    }

    private companion object {
        /** Planted in the preview fields, and looked for afterwards. */
        const val SENTINEL = "STALE"

        /** Enough samples for a stroke the decoder would accept as begun. */
        const val STROKE_LAST = 5
        const val STROKE_STEP_MS = 20L

        /**
         * Half a second, which is many times the single thread hop a commit
         * takes. Only the refusals wait it out in full; a stroke that types
         * leaves as soon as the word lands.
         */
        const val SETTLE_ROUNDS = 100
        const val SETTLE_STEP_MS = 5L
    }
}
