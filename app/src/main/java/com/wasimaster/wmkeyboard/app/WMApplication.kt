package com.wasimaster.wmkeyboard.app

import android.app.Application
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.app.drive.installDriveAuth
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.core.settings.sink.BackupClients

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
        // Publishes the Drive token provider, if this build has one at all, so
        // the backup job can find it. Here rather than in the activity or the
        // keyboard because either of those may be what started the process, and
        // the job runs in whichever one is alive. A no-op on a build without
        // Play services compiled in.
        installDriveAuth(this)
        // The Dropbox and OneDrive client ids, which live in BuildConfig and
        // so cannot be read from the library module that needs them.
        BackupClients.install(
            dropbox = BuildConfig.DROPBOX_APP_KEY,
            oneDrive = BuildConfig.ONEDRIVE_CLIENT_ID,
        )
    }
}
