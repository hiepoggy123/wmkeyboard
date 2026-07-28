package com.wasimaster.wmkeyboard.core.voice

/**
 * Spoken-punctuation post-processing for dictated text: "comma" becomes
 * ",", "দাঁড়ি" becomes "।", "new line" becomes a newline. Applied to
 * final results only (partials stay raw — they are transient anyway).
 *
 * The trade-off is deliberate and Gboard-shaped: a sentence that really
 * contains the word "period" gets a "." instead. The whole pass sits
 * behind a per-tool setting for exactly that reason.
 */
object VoicePunctuation {

    /** Longest phrases first, so "question mark" wins over any one-word rule. */
    private val ENGLISH = listOf(
        "new paragraph" to "\n\n",
        "new line" to "\n",
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "full stop" to ".",
        "period" to ".",
        "comma" to ",",
        "colon" to ":",
        "semicolon" to ";",
    ).map { (phrase, symbol) -> phrase.split(' ') to symbol }

    private val BENGALI = listOf(
        "নতুন প্যারা" to "\n\n",
        "নতুন লাইন" to "\n",
        "প্রশ্নবোধক চিহ্ন" to "?",
        "প্রশ্নবোধক" to "?",
        "বিস্ময়সূচক চিহ্ন" to "!",
        "বিস্ময়বোধক" to "!",
        "দাঁড়ি" to "।",
        "কমা" to ",",
        "কোলন" to ":",
        "সেমিকোলন" to ";",
    ).map { (phrase, symbol) -> phrase.split(' ') to symbol }

    fun apply(text: String, languageTag: String): String {
        val table = if (languageTag.startsWith("en")) ENGLISH else BENGALI
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return text
        val sb = StringBuilder()
        var i = 0
        outer@ while (i < words.size) {
            for ((phrase, symbol) in table) {
                if (i + phrase.size <= words.size &&
                    phrase.indices.all { words[i + it].equals(phrase[it], ignoreCase = true) }
                ) {
                    // Punctuation attaches to what came before it, no space.
                    sb.append(symbol)
                    i += phrase.size
                    continue@outer
                }
            }
            // Words are space-joined, except right after a newline (and at
            // the very start — the caller handles the leading space).
            if (sb.isNotEmpty() && sb.last() != '\n') sb.append(' ')
            sb.append(words[i])
            i++
        }
        return sb.toString()
    }
}
