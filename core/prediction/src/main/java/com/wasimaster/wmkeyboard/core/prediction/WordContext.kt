package com.wasimaster.wmkeyboard.core.prediction

/**
 * Derives bigram context from the text before the caret — extracted from the
 * IME so it is pure and JVM-testable, and so sentence boundaries become part
 * of the contract instead of an accident: "Hello. " used to hand "hello" to
 * the bigram store, polluting it with pairs that cross a full stop.
 */
object WordContext {

    /** U+0001, built rather than written literally so no editor or tool can
     * corrupt an invisible control character in this file. */
    private val SENTINEL_CHAR: Char = 1.toChar()

    /**
     * Pseudo-word marking "the caret is at a sentence start". Learned as a
     * bigram *previous* (so sentence openers become predictable) but never as
     * a word or a follower; U+0001 can never be typed, so it collides with
     * nothing and any store it leaks into simply never matches.
     */
    val SENTENCE_START: String = SENTINEL_CHAR + "s"

    /**
     * True for a character that belongs to the word around it.
     *
     * `Char.isLetter()` on its own answers this only for the scripts that
     * spell a word out of letters alone. Bengali হয়েছে ends in U+09C7 VOWEL
     * SIGN E, a combining mark, so a letters-only boundary reads that word as
     * having ended three characters earlier: `trim { !it.isLetter() }` hands
     * back হয়েছ and `takeLastWhile { it.isLetter() }` hands back a bare ছ. The
     * same holds for Devanagari matras, Tamil and Thai vowel signs, Arabic
     * harakat and Hebrew niqqud. A combining mark never separates two words —
     * it is part of what the letter before it spells.
     */
    fun isWordChar(c: Char): Boolean = c.isLetter() || when (c.category) {
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK,
        -> true
        else -> false
    }

    /**
     * The completed word ending [text], for next-word context:
     *  - null while still inside a word (or for empty text);
     *  - [SENTENCE_START] when a sentence ender lies between the last word
     *    and the caret ("Hello. " — the next word starts a sentence and
     *    "hello" is not its context). Known limitation, accepted: "Dr. "
     *    reads as a sentence start too;
     *  - otherwise the last word, in the one spelling every store keys on
     *    ("Hello, " -> "hello"). See [WordKey]: the text here is read back out
     *    of the field, so it can hold anything any keyboard or paste put there.
     */
    fun completedWordBefore(text: CharSequence?, enders: CharArray): String? {
        if (text.isNullOrEmpty()) return null
        if (isWordChar(text.last()) || text.last().isDigit()) return null
        // The run of separators between the last word and the caret.
        var i = text.length - 1
        while (i >= 0 && !isWordChar(text[i])) {
            if (text[i] in enders) return SENTENCE_START
            i--
        }
        val word = text.toString()
            .trim { !isWordChar(it) }
            .takeLastWhile { isWordChar(it) }
        return WordKey.of(word).ifEmpty { null }
    }

    /** True for the sentinel (or anything in its reserved control plane). */
    fun isSentinel(word: String?): Boolean = word != null && word.startsWith(SENTINEL_CHAR)

    /**
     * The last two completed words before the caret, most recent first:
     * `(prev1, prev2)`. prev2 is null when unknown or when any boundary
     * (sentence ender, or prev1 itself being the sentinel) intervenes —
     * trigram context silently degrades to bigram rather than guessing.
     */
    fun lastTwoWords(text: CharSequence?, enders: CharArray): Pair<String?, String?> {
        val prev1 = completedWordBefore(text, enders)
        if (prev1 == null || isSentinel(prev1)) return prev1 to null
        // Strip the trailing separators and prev1's own letters, then ask the
        // same question of what remains.
        val s = text.toString()
        var end = s.length
        while (end > 0 && !isWordChar(s[end - 1])) end--
        while (end > 0 && isWordChar(s[end - 1])) end--
        val prev2 = completedWordBefore(s.substring(0, end), enders)
        return prev1 to prev2?.takeUnless { isSentinel(it) }
    }
}
