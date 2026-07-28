package com.wasimaster.wmkeyboard.core.prediction

import android.content.Context
import android.provider.UserDictionary

/**
 * Mirrors words this keyboard learns into Android's system personal
 * dictionary ([UserDictionary]), so other keyboards and the platform spell
 * checker recognize them too. Opt-in — the on-device [UserLexicon] already
 * covers this keyboard on its own.
 *
 * The provider only lets the *current* IME (or a spell checker / system app)
 * write, which is exactly the case while the user is typing on this keyboard,
 * so writes normally succeed. Every call is still wrapped defensively: a
 * SecurityException on some OEM build must never take the keyboard down.
 *
 * [UserDictionary.Words.addWord] does not deduplicate, so a session-lived set
 * of already-added words — seeded once from what the dictionary already holds —
 * keeps repeated typing from piling up duplicate rows.
 */
object SystemUserDictionary {

    /** Middle-of-the-road frequency; user-typed words aren't ranking-critical here. */
    private const val FREQUENCY = 250

    private val added = HashSet<String>()
    private var seeded = false

    /**
     * Adds [word] to the system dictionary if it isn't already there. Safe to
     * call from any thread, but does content-provider I/O, so callers should
     * run it off the main thread. No-ops on blank or single-character words.
     */
    fun add(context: Context, word: String) {
        val cleaned = word.trim()
        if (cleaned.length < 2) return
        val key = cleaned.lowercase()
        synchronized(this) {
            seed(context)
            if (!added.add(key)) return
        }
        runCatching {
            // locale = null makes the word valid for every locale, which suits a
            // multi-language keyboard where the same field mixes scripts.
            UserDictionary.Words.addWord(
                context,
                cleaned,
                FREQUENCY,
                null,
                null,
            )
        }
    }

    /**
     * The shortcut → expansion entries in Android's personal dictionary (the
     * SHORTCUT column the platform "Personal dictionary" UI fills in, e.g.
     * "omw" → "on my way"). Lowercased shortcuts. Reads the whole table each
     * call — cheap, small, and the caller caches it — and never throws: an OEM
     * that hides the provider just yields an empty map. Powers
     * [com.wasimaster.wmkeyboard.core.settings.SuggestionStripSettings.expandUserDictShortcuts].
     */
    fun shortcuts(context: Context): Map<String, String> {
        val out = HashMap<String, String>()
        runCatching {
            context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT),
                null,
                null,
                null,
            )?.use { cursor ->
                val wordCol = cursor.getColumnIndex(UserDictionary.Words.WORD)
                val shortcutCol = cursor.getColumnIndex(UserDictionary.Words.SHORTCUT)
                if (wordCol >= 0 && shortcutCol >= 0) {
                    while (cursor.moveToNext()) {
                        val shortcut = cursor.getString(shortcutCol)?.trim()
                        val word = cursor.getString(wordCol)?.trim()
                        if (!shortcut.isNullOrEmpty() && !word.isNullOrEmpty()) {
                            out[shortcut.lowercase()] = word
                        }
                    }
                }
            }
        }
        return out
    }

    /** Loads the words already in the dictionary once, so we don't re-add them. */
    private fun seed(context: Context) {
        if (seeded) return
        seeded = true
        runCatching {
            context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD),
                null,
                null,
                null,
            )?.use { cursor ->
                val col = cursor.getColumnIndex(UserDictionary.Words.WORD)
                if (col >= 0) {
                    while (cursor.moveToNext()) {
                        cursor.getString(col)?.let { added.add(it.lowercase()) }
                    }
                }
            }
        }
    }
}
