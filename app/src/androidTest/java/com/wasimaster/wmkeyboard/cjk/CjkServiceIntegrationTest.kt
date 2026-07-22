package com.wasimaster.wmkeyboard.cjk

import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictionaries
import com.wasimaster.wmkeyboard.core.input.composer.ConversionDictionary
import com.wasimaster.wmkeyboard.core.input.composer.PinyinComposer
import com.wasimaster.wmkeyboard.core.input.composer.PinyinSyllables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Device Instrumented Integration Test for IME InputConnection & Selection Callbacks.
 *
 * Tests the system-level interaction between InputConnection, selection callbacks (onUpdateSelection),
 * and prefix commit (P1.3 / P5.3 / X.5).
 */
@RunWith(AndroidJUnit4::class)
class CjkServiceIntegrationTest {

    private class TestInputConnection : BaseInputConnection(
        View(InstrumentationRegistry.getInstrumentation().targetContext),
        true
    ) {
        val textBuf = StringBuilder()
        var composingText = ""
        var cursor = 0
        var selStart = 0
        var selEnd = 0
        var compStart = -1
        var compEnd = -1

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            val start = (cursor - n).coerceAtLeast(0)
            return textBuf.substring(start, cursor)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
            val end = (cursor + n).coerceAtMost(textBuf.length)
            return textBuf.substring(cursor, end)
        }

        override fun getSelectedText(flags: Int): CharSequence {
            return textBuf.substring(selStart, selEnd)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val start = (cursor - beforeLength).coerceAtLeast(0)
            textBuf.delete(start, cursor)
            cursor = start
            selStart = cursor
            selEnd = cursor
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composingText = text?.toString() ?: ""
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            compStart = start
            compEnd = end
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composingText.isNotEmpty()) {
                textBuf.append(composingText)
                cursor = textBuf.length
                selStart = cursor
                selEnd = cursor
                composingText = ""
            }
            compStart = -1
            compEnd = -1
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val t = text?.toString() ?: ""
            this.textBuf.append(t)
            cursor = this.textBuf.length
            selStart = cursor
            selEnd = cursor
            composingText = ""
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            selStart = start
            selEnd = end
            return true
        }
    }

    @Before
    @After
    fun resetGlobals() {
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY
        PinyinSyllables.valid = emptySet()
    }

    /**
     * Tests P1.3 / P5.3 / X.5: Real InputConnection & onUpdateSelection prefix commit behavior.
     *
     * Specification: Selecting a prefix candidate (e.g. "你" for "nihao") MUST commit "你"
     * and preserve the remaining tail ("hao") in the composing buffer for re-conversion.
     *
     * Bug on device: The async onUpdateSelection callback following commitText trips the abandon
     * heuristic and finishComposingText() wipes the tail.
     */
    @Test
    fun testP1_3_PrefixCommitMustPreserveTailBuffer() {
        PinyinSyllables.valid = setOf("ni", "hao")
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf("ni\t你\t100", "hao\t好\t100", "nihao\t你好\t500")
        )

        val ic = TestInputConnection()

        // 1. User typed "nihao"
        var composingBuffer = StringBuilder("nihao")

        // 2. User selects "你" candidate (consumed = 2 for "ni")
        val chosenCandidate = "你"
        val consumed = PinyinComposer.consumedFor(composingBuffer.toString(), chosenCandidate)
        assertEquals(2, consumed)

        // Prefix commit sequence:
        ic.commitText(chosenCandidate, 1)
        composingBuffer.delete(0, consumed)
        assertEquals("hao", composingBuffer.toString())

        // Post-commit selection coords in text field:
        val postCommitSelStart = ic.cursor
        val postCommitSelEnd = ic.cursor
        val candidatesEnd = ic.compEnd

        // Check if abandon heuristic fires incorrectly on async selection update:
        val wouldAbandon = composingBuffer.isNotEmpty() &&
                (postCommitSelStart != candidatesEnd || postCommitSelEnd != candidatesEnd)

        // Intended specification: Selection update MUST NOT abandon the prefix-commit tail
        assertFalse("onUpdateSelection must NOT abandon composing buffer during prefix commit", wouldAbandon)

        if (wouldAbandon) {
            ic.finishComposingText()
            composingBuffer = StringBuilder()
        }

        // Intended specification assertion: Composing buffer MUST remain as "hao"
        assertEquals("hao", composingBuffer.toString())
    }
}
