package com.wasimaster.wmkeyboard.core.settings

/**
 * The settings app's optional fingerprint lock: which of its screens and
 * destructive actions ask for a fingerprint first, and how long one check
 * lasts.
 *
 * **This is a lock on the user interface, not encryption.** Nothing it hides
 * is stored differently; a settings export, Android's own backup, `adb` with
 * USB debugging on, or a rooted phone still reach all of it, and the keyboard
 * service reads the same DataStore without passing any of this. What it stops
 * is the realistic threat it was asked for: somebody holding the unlocked
 * phone for thirty seconds. The strings on the screen say exactly that.
 *
 * Its own flow off [SettingsRepository] rather than a field on
 * [KeyboardSettings], for the reason spelled out on that class: the data class
 * sits one field under the JVM's `copy$default` ceiling, and its remaining
 * room belongs to things that shape typing. Nothing in the IME reads any of
 * this — it is settings-app state end to end. It is still kept in DataStore
 * rather than a private [android.content.SharedPreferences] the way
 * `UpdatePrefs` and `EggPrefs` are, because [SettingsBackup] walks the raw
 * preference map, and that is how this config travels to a new phone for free.
 *
 * **[enabled] is a wish; `BiometricManager.canAuthenticate()` is the truth.**
 * A phone with no enrolled fingerprint and no screen lock cannot answer a
 * prompt, so on one the gates open and this flag does nothing. That is
 * deliberate: the alternative is a backup restored onto such a phone shutting
 * its owner out of the very screen that holds the off switch.
 *
 * Note that [LockedSettings] mirrors these keys into device-protected storage
 * along with every other non-secret preference. That is not a leak — it is a
 * list of screen names, not the content of any of them, and the mirror is
 * still app-private — but it is worth knowing before adding anything here.
 */
data class AppLockSettings(
    /** Whether the lock is on at all. Turning it either way asks for a check. */
    val enabled: Boolean = false,
    /**
     * Ids from the settings app's own registry of lockable things.
     *
     * Opaque strings here: the curated list lives in `:app`, which this module
     * cannot see. Unrecognised ids are kept rather than dropped, so restoring
     * a backup written by a newer build and then upgrading does not silently
     * lose the user's picks.
     */
    val lockedTargets: Set<String> = emptySet(),
    /** How long one successful check keeps the locked screens open. */
    val relock: AppLockRelock = AppLockRelock.ON_LEAVE,
    /**
     * Whether the device's PIN, pattern or password is accepted instead of a
     * fingerprint. On by default: it is the only way back in after the sensor
     * locks itself out, and the only way this works at all on a phone whose
     * reader has broken or that never had one.
     */
    val allowDeviceCredential: Boolean = true,
)

/** When a successful check stops counting. */
enum class AppLockRelock {
    /** Every locked screen asks again, every time. */
    IMMEDIATE,
    AFTER_1_MIN,
    AFTER_5_MIN,
    /** Ends when the settings app goes to the background. The default. */
    ON_LEAVE,
    /** Ends only when the process does — a swipe out of recents, or a kill. */
    UNTIL_APP_CLOSES,
}

/**
 * The shipped values, for the `default =` argument every settings row passes
 * so it can draw its own restore control. The twin of [SettingsDefaults].
 */
val AppLockDefaults = AppLockSettings()
