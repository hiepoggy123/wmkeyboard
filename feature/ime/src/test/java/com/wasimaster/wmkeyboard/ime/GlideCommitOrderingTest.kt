package com.wasimaster.wmkeyboard.ime

import android.os.Handler
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Issue #113: a glided word landed one swipe late, because its own trailing
 * space overwrote it.
 *
 * `commitText` replaces the composing region whenever there is one, and the
 * keyboard arms a region over a word the caret has just been left at — that is
 * `restartSuggestionsAtCursor`, keeping the word revisable. So when the commit's
 * selection echo got in between the word and the space that finishes it, the
 * space landed *on* the word instead of after it: the field kept a lone space
 * and the word surfaced only when the next stroke flushed the stranded buffer.
 * The window was the learning hop — `withContext(Dispatchers.Default)` for the
 * hand model and the shape sample — which the word commit, the guard and the
 * space now all sit in front of.
 *
 * **The fake input connection's fidelity is the whole point of this file.** A
 * connection that records commits and does nothing else cannot reproduce this
 * bug at all: every ordering looks identical from a call log, because the calls
 * *are* identical either way — only the window between them differs. So
 * [RecordingInputConnection] models the two editor behaviours the bug is made
 * of, and nothing else:
 *
 *  - `commitText` replaces the current composing span rather than appending
 *    after it, exactly as a real editor does, and `setComposingText` /
 *    `setComposingRegion` maintain that span;
 *  - each commit posts a selection echo to the main looper, the way an editor
 *    sharing the keyboard's process does (the setup wizard's "try your
 *    keyboard" box), and that echo arms a region over the word the caret is now
 *    at the end of — the resume rule `restartSuggestionsAtCursor` applies.
 *
 * The echo carries the keyboard's own guard (`mayArmRegion`) rather than
 * calling `onUpdateSelection`, because the real resume path is gated on a
 * loaded dictionary (`composingResumable` wants `hasWordSources`) and this
 * module has no engine and no word lists — they are `:app` assets, and `:app`
 * depends on this module rather than the other way round. Reading
 * `lastGestureWord` for that gate is the model's business, never an assertion:
 * every test here asserts on what reached the field.
 *
 * Deliberately not covered:
 *  - a genuinely multi-word stroke. With no decoder only the segment carrying
 *    the picker's verdict can commit a word, so [onGestureWords] is driven with
 *    two segments and asserted on the one that lands.
 *  - two consecutive glides, which is how the bug presented on device. The
 *    second `onGesture` cancels the first job while its publish sits in
 *    `NonCancellable`, so the two can interleave; a field primed with text
 *    stands in for "a swipe onto a word that is already there".
 *  - the strip, the octopus and the revert guard's own behaviour, which
 *    `OctopusGlideRefreshTest` and the pure `GlideSpaceTest` already hold.
 *
 * Driven the way [OctopusGlideRefreshTest] drives the service: a subclass that
 * attaches a context and hands back the fake connection. `onCreate` is never
 * called — Robolectric has no shadow for `InputMethodService`.
 */
@RunWith(RobolectricTestRunner::class)
class GlideCommitOrderingTest {

    /** The word the picker's verdict commits, so no decoder is needed. */
    private val word = "hello"

    /** Grid units, and the width a stroke is measured against. */
    private val keyWidth = 60f

    /**
     * A field that behaves like the editor the bug needed: one composing span,
     * `commitText` replacing it, and a selection echo posted after every commit.
     *
     * The echo is *posted*, not run inline, because that is what a same-process
     * editor does and it is the entire mechanism of the fix: two commits with
     * no suspension between them never give the looper a turn to deliver it,
     * and two commits with a thread hop between them always do.
     */
    private class RecordingInputConnection(
        target: View,
        initial: String = "",
    ) : BaseInputConnection(target, true) {

        /** What the field holds, span replacements included. */
        val text = StringBuilder(initial)

        /** Every `commitText` argument in order — the tripwire, not the verdict. */
        val commits = mutableListOf<String>()

        /**
         * Whether the keyboard would let the echo arm a region at all. The
         * default is the strictest editor there is, one with no guard, which is
         * what makes the *ordering* of the two commits the only thing left to
         * test. A test about the guard itself supplies the keyboard's own.
         */
        var mayArmRegion: () -> Boolean = { true }

        /** Where the composing span starts, or -1. It always runs to the caret. */
        private var composingStart = -1

        private val echoes = Handler(Looper.getMainLooper())

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val committed = text?.toString().orEmpty()
            commits += committed
            replaceSpan(committed)
            composingStart = -1
            echoes.post { armRegionAtCaret() }
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val start = if (composingStart >= 0) composingStart else this.text.length
            replaceSpan(text?.toString().orEmpty())
            composingStart = start
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            composingStart = start.coerceIn(0, text.length)
            return true
        }

        override fun finishComposingText(): Boolean {
            composingStart = -1
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = text.takeLast(n)

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

        /** The caret is always at the end here, so the span reaches it. */
        private fun replaceSpan(committed: String) {
            if (composingStart >= 0) text.setLength(composingStart)
            text.append(committed)
        }

        /**
         * The keyboard's answer to the echo: the word the caret is sitting at
         * the end of becomes the composing region, so it stays revisable. A
         * caret after a space has no word to resume and arms nothing — the same
         * rule `resumableWordAt` applies, and the reason a space that has
         * already landed makes the echo harmless.
         */
        private fun armRegionAtCaret() {
            if (!mayArmRegion()) return
            var start = text.length
            while (start > 0 && text[start - 1].isLetter()) start--
            if (start == text.length) return
            composingStart = start
        }
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
        (0..5).map { GesturePoint(x = it * keyWidth, y = 0f, t = it * 20L) }

    /** Planted on the board before the stroke, so [settle] can see the glide finish. */
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
     * Writes the state a glide needs, without the half of the service that
     * builds it. `glideReady` is otherwise only set by a watcher that wants a
     * loaded dictionary, and `learnFromTyping` has to be off or the commit
     * dereferences the `lateinit` lexicon that only `onCreate` assigns.
     */
    private fun seed(service: WMKeyboardService, octopus: Map<Int, OctopusWord>) {
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
            settings = KeyboardSettings(learnFromTyping = false),
        )
    }

    /**
     * The flag `restartSuggestionsAtCursor` tests before it arms a region at
     * all. Read for the editor model, never asserted on — see the class KDoc.
     */
    private fun gestureWordArmed(service: WMKeyboardService): Boolean {
        val field = WMKeyboardService::class.java.getDeclaredField("lastGestureWord")
        field.isAccessible = true
        return field.get(service) != null
    }

    /**
     * Runs the main looper until the glide's own thread hops have come back.
     * The board is written by the glide's last statement, so a changed board
     * means the whole commit — echoes included — is behind us.
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
     * The control, and the reason the three tests below can fail at all.
     *
     * Every one of them is an absence claim — no echo got in between the word
     * and its space — and an absence claim is worth nothing until the thing
     * being claimed absent has been shown to happen. So this drives the fake
     * editor alone, with no service in the loop, and lets the echo through on
     * purpose: the word is committed, the looper is pumped so the posted echo
     * arms a region over it, and the space that follows then *replaces* the
     * word instead of following it.
     *
     * That is issue #113 exactly, reproduced in four lines. If this test ever
     * goes green with `hello ` in the field, the fake has stopped modelling a
     * same-process editor — and the three tests below are green for a reason
     * that has nothing to do with the service.
     */
    @Test
    fun `the fake editor really can eat a word, which is what the tests below rule out`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))

        ic.commitText(word, 1)
        // The window the fix closed: on a real editor sharing this process, the
        // selection echo lands here.
        shadowOf(Looper.getMainLooper()).idle()
        ic.commitText(" ", 1)

        assertEquals("the echo never armed, so nothing below is being ruled out", " ", ic.text.toString())
    }

    /**
     * Reddens if `commitGestureSpace(ic, state)` moves back below the
     * `withContext(Dispatchers.Default)` learning hop in `onGesture`, which is
     * where it sat before 00ac2b50: the echo then arms a region over the word
     * during the hop and the space replaces it, leaving the lone space #113
     * was reported as.
     */
    @Test
    fun `a glided word and the space that finishes it land with no window between them`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, stale)

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle(service, stale)

        // The tripwire first: the glide scope has no exception handler, so a
        // throw anywhere in the commit is silent and reads exactly like the bug.
        // This list is the same before and after the fix — what differs is only
        // what the field made of it, which is the assertion below.
        assertEquals("the glide never reached the field", listOf(word, " "), ic.commits)
        assertEquals(
            "the glide's own trailing space overwrote the word (#113)",
            "$word ",
            ic.text.toString(),
        )
    }

    /**
     * The same commit, onto a field that already holds a word — which is what
     * every swipe after the first one is, and where the leading space enters
     * the picture too.
     *
     * `GlideCommitSpacingTest` asserts the same final text for the same
     * scenario, and the pair is not redundant: that file asks *which spaces get
     * typed*, against a fake that only records, and this one asks *whether the
     * word survives them*, against a fake that arms a composing region the way
     * a same-process editor does. Delete either and a real regression walks
     * through the gap the other leaves.
     *
     * Reddens if `commitGestureSpace` moves back below the learning hop, and
     * also if `commitGestureLeadingSpace(ic, state)` moves after
     * `ic.commitText(word, 1)`, which would run the two words together and put
     * both spaces behind them.
     */
    @Test
    fun `a glide onto existing text is spaced in front of the word and behind it`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()), EXISTING)
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, stale)

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle(service, stale)

        assertEquals("the glide never reached the field", listOf(" ", word, " "), ic.commits)
        assertEquals(
            "the swiped word was not left standing between its two spaces (#113)",
            "$EXISTING $word ",
            ic.text.toString(),
        )
    }

    /**
     * `onGestureWords` commits its trailing space after the loop, so the
     * learning hop is still between the last word and that space — there, the
     * guard is the whole fix rather than half of it. Hence the editor model is
     * given the keyboard's own guard: this is the test that the guard is armed
     * *early enough to be consulted*, asserted through the field rather than
     * through the flag.
     *
     * Reddens if `lastGestureWord = word` moves back below the learning hop in
     * `onGestureWords`, where it sat before 00ac2b50: the echo then finds the
     * guard unarmed, arms a region over the word, and the trailing space
     * replaces it.
     */
    @Test
    fun `a chained glide arms the composing guard before it stops to learn`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        ic.mayArmRegion = { !gestureWordArmed(service) }
        val stale = sentinel()
        seed(service, stale)

        // Two segments, one word: with no decoder only the last segment — the
        // one the picker's verdict belongs to — can commit anything.
        service.onGestureWords(
            listOf(stroke(), stroke()),
            letterKeys(),
            keyWidth,
            GlideVerdict.Word(word),
        )
        settle(service, stale)

        assertEquals("the chained glide never reached the field", listOf(word, " "), ic.commits)
        assertEquals(
            "the chained glide's trailing space overwrote its last word (#113)",
            "$word ",
            ic.text.toString(),
        )
    }

    private companion object {
        const val SETTLE_ROUNDS = 200
        const val SETTLE_STEP_MS = 10L

        /** A word already in the field, so the leading-space rule has something to see. */
        const val EXISTING = "hi"
    }
}
