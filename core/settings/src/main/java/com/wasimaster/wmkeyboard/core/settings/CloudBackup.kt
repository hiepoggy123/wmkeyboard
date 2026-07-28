package com.wasimaster.wmkeyboard.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * The user's answer to "may Android back this app up?".
 *
 * `android:allowBackup` is a manifest flag the platform reads before the app
 * runs, so it cannot itself be a setting. The switch is instead honoured by
 * the app's [android.app.backup.BackupAgent], which declines to write anything
 * when this is off — the manifest says backup is *possible*, this says whether
 * it *happens*, and the default is that it does not.
 *
 * Read from device-protected storage, because backup runs on the platform's
 * schedule: the device is idle and charging, which after a reboot means it may
 * still be locked and the real DataStore unreadable. [LockedSettings] already
 * mirrors every non-secret setting there for direct boot, so the flag is
 * available without a second store. A mirror that has never been written
 * (a fresh install) reads as absent, which is the same as off.
 */
object CloudBackup {

    internal val KEY = booleanPreferencesKey("cloud_backup")

    /** Whether the user has opted this app's data into Android backup. */
    fun isEnabled(context: Context): Boolean =
        runCatching { LockedSettings(context).read()[KEY] }.getOrNull() == true
}
