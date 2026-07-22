package com.wasimaster.wmkeyboard

import android.os.Bundle
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.ime.WMKeyboardService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-Device Service-Level Instrumented E2E Test for [WMKeyboardService].
 *
 * Exercises the actual Android Service lifecycle, InputConnection binding,
 * layout switching, composing preview updates, and text committing for
 * Vietnamese Telex and VNI input methods.
 */
@RunWith(AndroidJUnit4::class)
class VietnameseTypingE2ETest {

    private class TestInputConnection(targetView: View) : BaseInputConnection(targetView, true) {
        var lastComposingText: String = ""
        val committedText = StringBuilder()

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            lastComposingText = text?.toString() ?: ""
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committedText.append(text ?: "")
            lastComposingText = ""
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            val total = committedText.toString()
            return if (n >= total.length) total
            else total.substring(total.length - n)
        }

        override fun finishComposingText(): Boolean {
            if (lastComposingText.isNotEmpty()) {
                committedText.append(lastComposingText)
                lastComposingText = ""
            }
            return true
        }

        fun fullOutput(): String {
            return committedText.toString() + lastComposingText
        }
    }

    private class TestableWMKeyboardService(
        baseContext: android.content.Context,
        private val testIc: TestInputConnection
    ) : WMKeyboardService() {
        init {
            attachBaseContext(baseContext)
        }

        override fun getCurrentInputConnection(): InputConnection {
            return testIc
        }
    }

    private lateinit var service: TestableWMKeyboardService
    private lateinit var testIc: TestInputConnection

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dummyView = View(context)

        testIc = TestInputConnection(dummyView)
        service = TestableWMKeyboardService(context, testIc)

        AssetLayouts.load(context.assets)

        instrumentation.runOnMainSync {
            val editorInfo = EditorInfo().apply {
                packageName = context.packageName
                inputType = EditorInfo.TYPE_CLASS_TEXT
                extras = Bundle()
            }
            service.onCreate()
            service.onCreateInputView()
            service.onStartInput(editorInfo, false)
            service.onStartInputView(editorInfo, false)
        }
    }

    private fun typeString(input: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (ch in input) {
                when (ch) {
                    ' ' -> service.onKey(Key(label = " ", action = KeyAction.Space))
                    else -> service.onKey(Key(label = ch.toString(), action = KeyAction.Text))
                }
            }
        }
    }

    @Test
    fun serviceLevelTelexComposingAndCommit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            service.onLayoutSelected(AssetLayouts.VI_TELEX_ID)
        }

        typeString("vieejt ")
        assertEquals("việt ", testIc.fullOutput())

        typeString("tieengs ")
        assertEquals("việt tiếng ", testIc.fullOutput())
    }

    @Test
    fun serviceLevelVniComposingAndCommit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            service.onLayoutSelected(AssetLayouts.VI_VNI_ID)
        }

        typeString("viet65 ")
        assertEquals("việt ", testIc.fullOutput())

        typeString("tieng61 ")
        assertEquals("việt tiếng ", testIc.fullOutput())
    }
}
