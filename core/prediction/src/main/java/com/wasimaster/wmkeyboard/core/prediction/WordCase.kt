package com.wasimaster.wmkeyboard.core.prediction

/**
 * Case tests that count letters by code point.
 *
 * `Char.isLetter()` and `Char.isUpperCase()` are false for either half of a
 * surrogate pair, so a word in Osage, Adlam or Warang Citi — cased scripts, all
 * of them outside the BMP — reads as "no letters at all" to a per-`Char` test.
 * The all-caps test then passes vacuously and every suggestion for such a word
 * came back in capitals.
 */

/** Whether [word] holds at least one letter. */
internal fun hasLetter(word: String): Boolean {
    var at = 0
    while (at < word.length) {
        val cp = word.codePointAt(at)
        if (Character.isLetter(cp)) return true
        at += Character.charCount(cp)
    }
    return false
}

/** Whether every letter of [word] is upper case; true when it has no letters. */
internal fun lettersAllUpper(word: String): Boolean {
    var at = 0
    while (at < word.length) {
        val cp = word.codePointAt(at)
        if (Character.isLetter(cp) && !Character.isUpperCase(cp)) return false
        at += Character.charCount(cp)
    }
    return true
}

/** Whether [word] is more than one letter long and written entirely in capitals. */
internal fun isAllCapsWord(word: String): Boolean =
    word.codePointCount(0, word.length) > 1 && hasLetter(word) && lettersAllUpper(word)

/** Whether [word] starts with an upper-case letter. */
internal fun startsUpperCase(word: String): Boolean =
    word.isNotEmpty() && Character.isUpperCase(word.codePointAt(0))

/** [word] with its first character — the whole first code point — upper-cased. */
internal fun capitalizeFirst(word: String): String {
    if (word.isEmpty()) return word
    val first = word.codePointAt(0)
    val upper = Character.toUpperCase(first)
    if (upper == first) return word
    return String(Character.toChars(upper)) + word.substring(Character.charCount(first))
}
