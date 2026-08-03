package com.wasimaster.wmkeyboard.app.updates

import android.content.Context
import androidx.core.content.edit

/**
 * The two pieces of state the update prompt keeps: which version the user has
 * already waved away, and whether they want to be prompted at all.
 *
 * Its own `SharedPreferences` file rather than a field on `KeyboardSettings`,
 * for two reasons. It is not a keyboard setting — nothing in the IME reads it,
 * and it has no business in a settings export, a theme share or the config
 * backup. And `KeyboardSettings` is a data class within a handful of fields of
 * the JVM's 255-slot ceiling on `copy$default`, so its remaining room belongs
 * to things that actually shape typing.
 *
 * The snooze is keyed by version code on purpose: it expires by itself when
 * the next release comes out, and a stale value carried onto a new device by
 * Android's backup names a version that device is not being offered.
 */
internal class UpdatePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the app may open Play's update dialog by itself.
     *
     * On by default: an update the user never hears about is the failure mode
     * this whole feature exists to fix. Turning it off leaves the update card
     * and the About row, so the information is still there — it just stops
     * arriving uninvited.
     */
    var autoPrompt: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PROMPT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_PROMPT, value) }

    /** True while [versionCode] is inside the window opened by [snooze]. */
    fun isSnoozed(versionCode: Int, now: Long): Boolean {
        if (prefs.getInt(KEY_SNOOZED_VERSION, 0) != versionCode) return false
        val since = prefs.getLong(KEY_SNOOZED_AT, 0L)
        // A clock that moved backwards (a manual change, a time-zone database
        // update) would otherwise hold the snooze open indefinitely.
        if (now < since) return false
        return now - since < UpdatePolicy.SNOOZE_MILLIS
    }

    /** Records "Not now" for one version. */
    fun snooze(versionCode: Int, now: Long) {
        prefs.edit {
            putInt(KEY_SNOOZED_VERSION, versionCode)
            putLong(KEY_SNOOZED_AT, now)
        }
    }

    private companion object {
        const val FILE_NAME = "app_updates"
        const val KEY_AUTO_PROMPT = "auto_prompt"
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_AT = "snoozed_at"
    }
}
