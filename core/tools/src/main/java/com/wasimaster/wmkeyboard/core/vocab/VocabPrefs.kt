package com.wasimaster.wmkeyboard.core.vocab

import android.content.Context
import androidx.core.content.edit

/**
 * The little state the Vocabulary tool keeps outside the learning record:
 * how often the word-of-the-day chip has been up in the current slot, and
 * which words have already nudged today when the cooldown is "once a day".
 *
 * Its own `SharedPreferences` file rather than fields on `KeyboardSettings`
 * (the same reasoning as `EggPrefs`): none of this is a setting, none of it
 * belongs in a backup, and the settings class is near its argument ceiling.
 * The keyboard and the settings app share a process, so both read the same
 * file. The markers are slot and day numbers, not booleans, so they re-arm
 * when the slot turns without anyone clearing them.
 */
class VocabPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The slot ([WordOfDay.slot]) whose chip the user dismissed, or 0. */
    var chipDismissedSlot: Int
        get() = prefs.getInt(KEY_CHIP_DISMISSED, 0)
        set(value) = prefs.edit { putInt(KEY_CHIP_DISMISSED, value) }

    /**
     * Whether the chip may come up now: not dismissed for [slot], fewer than
     * [maxShows] times in the slot so far, and at least [minGapMillis] since
     * the last time. A chip that was shown and typed past comes back later in
     * the slot, so a word is not lost to the one field it happened to land in.
     */
    fun mayOfferChip(slot: Int, nowMillis: Long, maxShows: Int, minGapMillis: Long): Boolean {
        if (chipDismissedSlot == slot) return false
        if (prefs.getInt(KEY_CHIP_SLOT, 0) != slot) return true
        if (prefs.getInt(KEY_CHIP_SHOWS, 0) >= maxShows) return false
        return nowMillis - prefs.getLong(KEY_CHIP_LAST_MILLIS, 0L) >= minGapMillis
    }

    fun recordChipShown(slot: Int, nowMillis: Long) {
        val shows = if (prefs.getInt(KEY_CHIP_SLOT, 0) == slot) prefs.getInt(KEY_CHIP_SHOWS, 0) else 0
        prefs.edit {
            putInt(KEY_CHIP_SLOT, slot)
            putInt(KEY_CHIP_SHOWS, shows + 1)
            putLong(KEY_CHIP_LAST_MILLIS, nowMillis)
        }
    }

    /** The typed words that have already nudged on [day]. */
    fun nudgedOn(day: Int): Set<String> {
        if (prefs.getInt(KEY_NUDGED_DAY, 0) != day) return emptySet()
        return prefs.getStringSet(KEY_NUDGED_WORDS, null).orEmpty()
    }

    fun markNudged(day: Int, word: String) {
        val current = if (prefs.getInt(KEY_NUDGED_DAY, 0) == day) {
            prefs.getStringSet(KEY_NUDGED_WORDS, null).orEmpty()
        } else {
            emptySet()
        }
        prefs.edit {
            putInt(KEY_NUDGED_DAY, day)
            putStringSet(KEY_NUDGED_WORDS, current + word)
        }
    }

    private companion object {
        const val FILE_NAME = "vocab_prefs"
        const val KEY_CHIP_SLOT = "chip_slot"
        const val KEY_CHIP_SHOWS = "chip_shows"
        const val KEY_CHIP_LAST_MILLIS = "chip_last_millis"
        const val KEY_CHIP_DISMISSED = "chip_dismissed_slot"
        const val KEY_NUDGED_DAY = "nudged_day"
        const val KEY_NUDGED_WORDS = "nudged_words"
    }
}
