package com.wasimaster.wmkeyboard.ime

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The spike's first question, and the only one that decides whether any of the
 * rest is worth writing: can [WMKeyboardService] be built and read on the JVM?
 *
 * Deliberately does NOT call `onCreate()`. Robolectric ships a shadow for
 * `InputMethodManager` but none for `InputMethodService`, so `super.onCreate()`
 * would run real AOSP window code — and every field this service publishes
 * before that point is ordinary Kotlin. The state flow being readable here is
 * what makes an assertion about the keyboard's own state possible at all.
 */
@RunWith(RobolectricTestRunner::class)
class ServiceBootProbeTest {

    private class Probe(private val ic: InputConnection?) : WMKeyboardService() {
        init { attachBaseContext(RuntimeEnvironment.getApplication()) }
        override fun getCurrentInputConnection(): InputConnection? = ic
    }

    @Test
    fun `the service constructs and publishes a state on the JVM`() {
        val service = Probe(null)

        assertNotNull(service.uiState.value)
        assertNotNull(service.uiState.value.settings)
        assertNotNull(service.uiState.value.layouts)
    }
}
