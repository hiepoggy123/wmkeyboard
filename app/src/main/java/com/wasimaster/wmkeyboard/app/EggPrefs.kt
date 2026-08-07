package com.wasimaster.wmkeyboard.app

import android.content.Context
import androidx.core.content.edit

/**
 * The little state the easter eggs keep: which year's install anniversary has
 * already been celebrated, and the best score in the hidden mini game.
 *
 * Its own `SharedPreferences` file rather than fields on `KeyboardSettings`,
 * for the same two reasons as `UpdatePrefs`. Nothing here is a keyboard
 * setting — the IME never reads it, and a high score has no business in a
 * settings export or the config backup. And `KeyboardSettings` is a data
 * class within a handful of fields of the JVM's 255-slot ceiling on
 * `copy$default`, so its remaining room belongs to things that actually
 * shape typing.
 *
 * The celebration marker is the year itself, not a boolean: it re-arms on
 * its own every January without anyone having to clear it.
 */
internal class EggPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The year whose anniversary confetti has already fired, or 0. */
    var celebratedYear: Int
        get() = prefs.getInt(KEY_CELEBRATED_YEAR, 0)
        set(value) = prefs.edit { putInt(KEY_CELEBRATED_YEAR, value) }

    /** Best score in the keycap catcher, 0 until the game is found. */
    var gameHighScore: Int
        get() = prefs.getInt(KEY_GAME_HIGH_SCORE, 0)
        set(value) = prefs.edit { putInt(KEY_GAME_HIGH_SCORE, value) }

    private companion object {
        const val FILE_NAME = "egg_prefs"
        const val KEY_CELEBRATED_YEAR = "anniversary_celebrated_year"
        const val KEY_GAME_HIGH_SCORE = "game_high_score"
    }
}
