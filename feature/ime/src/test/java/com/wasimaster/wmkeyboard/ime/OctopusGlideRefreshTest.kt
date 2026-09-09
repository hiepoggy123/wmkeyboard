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
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

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
 * (`VietnameseTypingE2ETest`): a subclass that attaches a context and hands back
 * a recording [InputConnection]. `onCreate` is deliberately never called —
 * Robolectric has no shadow for `InputMethodService`, and none of the state this
 * test needs comes from there.
 */
@RunWith(RobolectricTestRunner::class)
class OctopusGlideRefreshTest {

    /** The word the picker's verdict commits, so no decoder is needed. */
    private val word = "hello"

    /** Grid units, and the width a stroke is measured against. */
    private val keyWidth = 60f

    /**
     * Records what reached the field. It is also the test's tripwire: the commit
     * happens inside the same coroutine as the publish and about twenty lines
     * ahead of it, so a word in the field proves the coroutine got there. Without
     * that check a throw anywhere in the glide job — the scope has no exception
     * handler — reads exactly like a reverted fix.
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
        (0..5).map { GesturePoint(x = it * keyWidth, y = 0f, t = it * 20L) }

    /** The word planted on the board before the stroke, and looked for afterwards. */
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
            octopus = octopus,
            settings = KeyboardSettings(learnFromTyping = false),
        )
    }

    /** Runs the main looper until the glide's own thread hop has come back. */
    private fun settle(service: WMKeyboardService, before: Map<Int, OctopusWord>) {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (service.uiState.value.octopus != before) return
            Thread.sleep(SETTLE_STEP_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a glided word refreshes the words on the keys`() {
        val ic = RecordingInputConnection(View(RuntimeEnvironment.getApplication()))
        val service = Keyboard(ic)
        val stale = sentinel()
        seed(service, stale)

        service.onGesture(stroke(), letterKeys(), keyWidth, GlideVerdict.Word(word))
        settle(service, stale)

        // First, that the glide actually ran. The publish is downstream of this,
        // so an unchanged board only means something once the word has landed.
        assertEquals(word, ic.committed.toString().trim())
        // Then the fix itself: the board was written, so the stale word is gone.
        assertNotEquals(
            "the words on the keys were left behind by the glide (#118)",
            stale,
            service.uiState.value.octopus,
        )
    }

    private companion object {
        const val SETTLE_ROUNDS = 200
        const val SETTLE_STEP_MS = 10L
    }
}
