package com.wasimaster.wmkeyboard.app

import android.app.Application
import com.wasimaster.wmkeyboard.core.debug.DebugLog

/**
 * Exists for one reason: to install the crash handler before anything else in
 * the process runs.
 *
 * [MainActivity] and `WMKeyboardService` both call [DebugLog.attach] on create,
 * which covers the keyboard and the settings app once they are up. What it
 * cannot cover is everything that runs *before* them — content-provider
 * initialisers (Compose, DataStore, the ML Kit init provider), static
 * initialisers, a library's own startup. A crash in that window is exactly the
 * kind that leaves an app that "does not open at all", and it was the one kind
 * the log never recorded. `Application.onCreate` is the first app code the
 * platform runs in any process, so the handler now covers all of it.
 *
 * Nothing else belongs here. Anything heavier would run on every process start
 * including the keyboard's, where startup latency is what the user feels as the
 * keyboard being slow to appear.
 */
class WMApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The crash screen runs in its own process and must not get a handler:
        // a failure while showing a crash report would launch another crash
        // report, forever. See CrashReportActivity.
        if (DebugLog.isCrashProcess()) return
        DebugLog.attach(this)
    }
}
