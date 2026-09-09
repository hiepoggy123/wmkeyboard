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
import com.wasimaster.wmkeyboard.core.settings.GestureSettings
import com.wasimaster.wmkeyboard.core.settings.GlideApostropheKey
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.ui.GlideVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The two glide commits that are not one stroke, one word: [WMKeyboardService.onGestureWords],
 * which lands a stroke that crossed the spacebar, and the possessive flick, which
 * extends the word a stroke has already landed.
 *
 * Both are wired to rules that are tested as pure functions elsewhere —
 * `PossessiveFlickTest` owns the flick's geometry, `GlideSpaceTest` the spacing —
 * so nothing here re-asks a shape question. What is asked here is whether the
 * service is wired to those answers: which segment a held shift and a picked word
 * reach, whether the flick edits the field before the decoder is ever asked, and
 * whether either path leaves the words on the keys behind (#118).
 *
 * Every test asserts what reached the [InputConnection] before it asserts state.
 * The glide scope carries no exception handler, so a throw inside a commit is
 * silent and reads exactly like a reverted fix; the recording connection is the
 * tripwire that tells the two apart.
 *
 * Deliberately out of reach here, and left untested rather than faked: this
 * module has no `SuggestionEngine` and no word list — they are `:app` assets, and
 * `:app` depends on this module rather than the other way round — so `glideDecode`
 * answers nothing on every stroke. Only a segment the picker explicitly answered
 * for can commit, and the picker's answer belongs to the last segment alone. So a
 * chained stroke commits exactly one word here, and the *sequence* a chain is for
 * — several words in order, spaced between them, the trailing space landing once
 * after the last — cannot be observed. The board assertions are about the publish
 * and not its content for the same reason: with no engine the map written is empty.
 *
 * The service is driven the way [OctopusGlideRefreshTest] drives it: a subclass
 * that attaches a context and hands back a recording connection, with `onCreate`
 * deliberately never called, because Robolectric has no shadow for
 * `InputMethodService` and none of this needs one.
 */
@RunWith(RobolectricTestRunner::class)
class GlideMultiWordAndPossessiveTest {

    /** The word every verdict here commits, so no decoder is needed. */
    private val word = "hello"

    /** What the field holds when a glide has landed [word] and spaced it. */
    private val glidedField = "$word "

    /** The default QWERTY set. Declared first: the centres below are read off it. */
    private val layouts = KeyboardUiState().layouts

    /**
     * Every text key's centre, punctuation included — the same map the keyboard
     * builds. The comma is in it because the possessive flick starts from
     * whichever punctuation key the apostrophe setting names.
     */
    private val centers: Map<Int, Pair<Float, Float>> = buildMap {
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

    /**
     * Records what reached the field, and honours a delete: the possessive flick
     * is the one glide path that takes text back, and left to `BaseInputConnection`
     * that delete would edit an editable nobody reads, leaving the take-back
     * invisible to the assertion that exists to see it.
     */
    private class RecordingInputConnection(
        target: View,
        initial: String = "",
    ) : BaseInputConnection(target, true) {
        val committed = StringBuilder(initial)
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed.append(text ?: "")
            return true
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val taken = beforeLength.coerceAtMost(committed.length)
            committed.setLength(committed.length - taken)
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

    /**
     * The glide grid, straight off the default QWERTY — no Compose, no measure
     * pass. [apostropheAt] puts `'` on one key, which is the only way punctuation
     * reaches the grid: `keySpelling` admits letters and marks alone, so without
     * it the flick has no key to start from.
     */
    private fun glideGrid(apostropheAt: Pair<Float, Float>? = null): List<KeyCenter> =
        layouts.glideKeys(apostropheCenter = apostropheAt) { centers[it] }

    /**
     * A plain left-to-right stroke along [row]. Its shape is irrelevant — nothing
     * in this module decodes one — but two segments are drawn along different rows
     * so a chained stroke is not two copies of the same gesture.
     */
    private fun stroke(row: Int): List<GesturePoint> = (0..STROKE_STEPS).map {
        GesturePoint(x = it * KEY_WIDTH, y = row * KEY_WIDTH, t = it * STROKE_STEP_MS)
    }

    /**
     * The `'s` flick: a straight line between the two key centres. Straight is
     * what makes it one — the travelled length has to agree with the direct
     * distance, which is the test that keeps a real word drawn between the same
     * two keys from being eaten (see `PossessiveFlickTest`).
     */
    private fun possessiveStroke(from: Pair<Float, Float>, to: Pair<Float, Float>): List<GesturePoint> =
        (0..STROKE_STEPS).map { step ->
            val fraction = step.toFloat() / STROKE_STEPS
            GesturePoint(
                x = from.first + (to.first - from.first) * fraction,
                y = from.second + (to.second - from.second) * fraction,
                t = step * STROKE_STEP_MS,
            )
        }

    /** The word planted on the board before a stroke, and looked for afterwards. */
    private fun sentinel(): Map<Int, OctopusWord> = mapOf(
        'q'.code to OctopusWord(
            keyCodePoint = 'q'.code,
            word = "STALE",
            typedChars = 0,
            kind = OctopusKind.NEXT_WORD,
            rank = 0,
        ),
    )

    /**
     * Writes the state a glide needs, without the half of the service that builds
     * it. Two arguments are load-bearing rather than tidy: `glideReady` is
     * otherwise only set by a watcher that wants a loaded dictionary, and every
     * gesture entry point returns at `glideAllowed` without it; and
     * `learnFromTyping` has to be off, or the commit dereferences the `lateinit`
     * lexicon that only `onCreate` assigns and the coroutine dies in silence.
     */
    private fun seed(
        service: WMKeyboardService,
        octopus: Map<Int, OctopusWord> = emptyMap(),
        shiftState: ShiftState = ShiftState.OFF,
        gesture: GestureSettings = GestureSettings(),
    ) {
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
            octopus = octopus,
            shiftState = shiftState,
            settings = KeyboardSettings(learnFromTyping = false, gesture = gesture),
        )
    }

    /**
     * Arms what a possessive flick reads behind it: the word it extends, and
     * whether the space in front of the caret is one the keyboard typed.
     *
     * Written straight into the fields rather than glided into place, because a
     * real glide first would put two strokes in one test: the second cancels the
     * first's job on entry while the first's publish sits in `NonCancellable`, so
     * which of the two lands last is not the test's to decide.
     */
    private fun armGlideTail(service: WMKeyboardService, glided: String, keyboardTypedSpace: Boolean) {
        val wordField = WMKeyboardService::class.java.getDeclaredField("lastGestureWord")
        wordField.isAccessible = true
        wordField.set(service, glided)
        val spaceField = WMKeyboardService::class.java.getDeclaredField("pendingWordSpace")
        spaceField.isAccessible = true
        spaceField.setBoolean(service, keyboardTypedSpace)
    }

    /**
     * Runs the main looper until the glide's own thread hop has come back.
     *
     * The board is what is waited on rather than the field, and on purpose: the
     * publish is the last statement of the commit, so a changed board means the
     * whole job is done. Waiting on the text instead would let a two-word commit
     * stop at its first trailing space and read as a one-word one.
     */
    private fun settle(service: WMKeyboardService, before: Map<Int, OctopusWord>) {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (service.uiState.value.octopus != before) return
            Thread.sleep(SETTLE_STEP_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * The shift the board is holding reaches a chained stroke at all.
     *
     * On its own this is half a rule; it is here so the two tests below cannot
     * pass by the capitalisation having quietly gone away altogether.
     *
     * Reddens if `onGestureWords` stops asking about the shift — `val word = leader`
     * in place of the `when (shiftAtGesture)` block.
     */
    @Test
    fun `a one-segment chained stroke takes the shift the board was holding`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, octopus = stale, shiftState = ShiftState.ON)

        service.onGestureWords(listOf(stroke(0)), glideGrid(), KEY_WIDTH, GlideVerdict.Word(word))
        settle(service, stale)

        assertEquals("Hello ", ic.committed.toString())
    }

    /**
     * The picker answered about the segment the finger stopped on, which is the
     * last one. A stroke can only pause to ask on the word it is drawing, so the
     * pick must not be spent again on the segments behind it.
     *
     * With no decoder the earlier segment has nothing else to commit, so the field
     * holding one word — and one trailing space — is the whole assertion.
     *
     * Reddens if the pick stops being scoped to the last segment:
     * `val picked = chosen` in place of `chosen?.takeIf { index == segments.lastIndex }`
     * commits it twice, and the leading-space rule puts a gap between them, so the
     * field reads "hello hello ".
     */
    @Test
    fun `the picker's word reaches the last segment of a chained stroke and no other`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, octopus = stale)

        service.onGestureWords(
            listOf(stroke(0), stroke(1)),
            glideGrid(),
            KEY_WIDTH,
            GlideVerdict.Word(word),
        )
        settle(service, stale)

        assertEquals(glidedField, ic.committed.toString())
    }

    /**
     * Only the first word of a chained stroke is the one the shift was put up for.
     * The words after it were never in front of that decision — the finger had
     * already crossed the spacebar — so they land in whatever case the word list
     * gave them.
     *
     * Reddens if the guard on the capitalisation goes: `if (index == 0)` dropped
     * from `onGestureWords` shifts the last segment too and the field reads
     * "Hello ". The test above is what stops this one passing by the
     * capitalisation having disappeared instead.
     */
    @Test
    fun `a held shift does not reach a later segment of a chained stroke`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, octopus = stale, shiftState = ShiftState.ON)

        service.onGestureWords(
            listOf(stroke(0), stroke(1)),
            glideGrid(),
            KEY_WIDTH,
            GlideVerdict.Word(word),
        )
        settle(service, stale)

        assertEquals(glidedField, ic.committed.toString())
    }

    /**
     * Issue #118 on the chained path: a glide never calls `refreshSuggestions`,
     * which was the only writer of the words floating over the keys, so the keys
     * carried whatever they had been carrying before the stroke.
     *
     * The publish is asserted, not its content: the fix writes the board
     * unconditionally, so the sentinel is gone even though the map written here is
     * empty. Reddens if `octopus = floating` is dropped from the final
     * `_uiState.update` of `onGestureWords`.
     */
    @Test
    fun `a chained stroke refreshes the words on the keys`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, octopus = stale)

        service.onGestureWords(listOf(stroke(0)), glideGrid(), KEY_WIDTH, GlideVerdict.Word(word))
        settle(service, stale)

        // First, that the commit ran at all. The publish is the line after it, so
        // an unchanged board only means something once the word has landed.
        assertEquals(glidedField, ic.committed.toString())
        assertNotEquals(
            "a chained stroke left the words on the keys behind (#118)",
            stale,
            service.uiState.value.octopus,
        )
    }

    /**
     * The flick appends `'s` to the word behind it, and the glide's own trailing
     * space is taken back first so the possessive goes inside it rather than after
     * it: "hello " becomes "hello's ", never "hello 's".
     *
     * Asserted with no settle, and that is itself part of the claim: the flick is
     * answered on the caller's thread, ahead of the decode, because there is
     * nothing to decode and no candidate to override.
     *
     * Reddens if the take-back goes — `if (before == " ") ic.deleteSurroundingText(1, 0)`
     * deleted from `appendPossessive` leaves the field reading "hello 's ".
     */
    @Test
    fun `the possessive flick takes back the space the glide typed`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()), glidedField)
        val service = Keyboard(ic)
        seed(service, gesture = GestureSettings(apostropheKey = GlideApostropheKey.COMMA))
        armGlideTail(service, word, keyboardTypedSpace = true)
        val apostrophe = centers.getValue(','.code)

        service.onGesture(
            possessiveStroke(apostrophe, centers.getValue('s'.code)),
            glideGrid(apostropheAt = apostrophe),
            KEY_WIDTH,
        )

        assertEquals("${word}'s ", ic.committed.toString())
    }

    /**
     * Only the keyboard's own space is the keyboard's to take: a space that is
     * already there when the glide did not type one belongs to the user, and the
     * possessive lands after it.
     *
     * The flag and the word behind it are armed directly, because what is under
     * test is which of the two signals the code believes — the flag it set when it
     * typed a space, or the space it can see in the field. Reaching the same pair
     * through the keyboard would take a longer sequence of presses than the
     * assertion is worth.
     *
     * Reddens if the flag stops guarding the take-back: dropping the
     * `if (pendingWordSpace)` wrapper in `appendPossessive` eats the user's space
     * and the field reads "hello's ".
     */
    @Test
    fun `the possessive flick leaves a space it did not type alone`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()), glidedField)
        val service = Keyboard(ic)
        seed(service, gesture = GestureSettings(apostropheKey = GlideApostropheKey.COMMA))
        armGlideTail(service, word, keyboardTypedSpace = false)
        val apostrophe = centers.getValue(','.code)

        service.onGesture(
            possessiveStroke(apostrophe, centers.getValue('s'.code)),
            glideGrid(apostropheAt = apostrophe),
            KEY_WIDTH,
        )

        assertEquals("${word} 's ", ic.committed.toString())
    }

    /**
     * Issue #118 on the possessive path. The flick rewrote the word behind the
     * caret, so the keys are answering about a word that is no longer there — the
     * one case where the board is stale without any decode having happened at all.
     *
     * Reddens if the `nextWordOctopus` publish is dropped from the possessive
     * branch of `onGesture`.
     */
    @Test
    fun `the possessive flick refreshes the words on the keys`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()), glidedField)
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(
            service,
            octopus = stale,
            gesture = GestureSettings(apostropheKey = GlideApostropheKey.COMMA),
        )
        armGlideTail(service, word, keyboardTypedSpace = true)
        val apostrophe = centers.getValue(','.code)

        service.onGesture(
            possessiveStroke(apostrophe, centers.getValue('s'.code)),
            glideGrid(apostropheAt = apostrophe),
            KEY_WIDTH,
        )
        settle(service, stale)

        // The flick's own edit first: without it the stroke was never taken as a
        // possessive at all, and the board would be untouched for that reason.
        assertEquals("${word}'s ", ic.committed.toString())
        assertNotEquals(
            "the possessive flick left the words on the keys behind (#118)",
            stale,
            service.uiState.value.octopus,
        )
    }

    private companion object {
        /** Grid units, and the width a stroke is measured against. */
        const val KEY_WIDTH = 60f

        /** Sample count of every synthetic stroke, and the gap between samples. */
        const val STROKE_STEPS = 5
        const val STROKE_STEP_MS = 20L

        const val SETTLE_ROUNDS = 200
        const val SETTLE_STEP_MS = 10L
    }
}
