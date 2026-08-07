package com.wasimaster.wmkeyboard.core.input

/**
 * The pending dot/dash sequence of the morse layout. Pure state — the service
 * owns the commit timer (each signal restarts it; when it fires, [take]
 * decodes and clears) because a timer here would drag a coroutine scope into
 * an otherwise trivially testable value type.
 *
 * Backspace edits the *sequence* while one is pending — Gboard's contract —
 * and only falls through to a real delete once the sequence is empty.
 */
class MorseInput {

    private val pending = StringBuilder()

    /** The last few decoded letters, for [recordDecoded]'s SOS watch. */
    private val recent = StringBuilder()

    val isPending: Boolean get() = pending.isNotEmpty()

    /** The sequence as typed, e.g. "·−−" rendered with display glyphs. */
    val display: String
        get() = pending.toString().map { if (it == '.') '·' else '−' }.joinToString("")

    fun signal(dash: Boolean) {
        pending.append(if (dash) '-' else '.')
    }

    /** Drops the last signal. Returns false when there was nothing pending. */
    fun backspace(): Boolean {
        if (pending.isEmpty()) return false
        pending.setLength(pending.length - 1)
        return true
    }

    /**
     * Decodes and clears the pending sequence: the letter it spells, or null
     * when nothing is pending or the sequence spells nothing (which clears
     * too — a bad sequence should not poison the next letter).
     */
    fun take(): String? {
        if (pending.isEmpty()) return null
        val seq = pending.toString()
        pending.setLength(0)
        return MorseCode.decode(seq)
    }

    fun reset() {
        pending.setLength(0)
        recent.setLength(0)
    }

    /**
     * Files one decoded letter and reports whether the last three now spell
     * "sos" — the easter egg's cue. A rolling window rather than a state
     * machine: "ssos" still ends in an SOS, and that is the one that counts.
     * [reset] clears the window too, so a field change breaks the sequence.
     */
    fun recordDecoded(letter: String): Boolean {
        recent.append(letter)
        val over = recent.length - RECENT_LIMIT
        if (over > 0) recent.delete(0, over)
        return recent.endsWith("sos")
    }

    private companion object {
        const val RECENT_LIMIT = 8
    }
}

/**
 * The international (ITU-R M.1677-1) morse table, letters lowercase, plus the
 * usual punctuation extensions. Decode-only: the keyboard turns sequences into
 * text, never text into sequences.
 */
object MorseCode {

    fun decode(sequence: String): String? = TABLE[sequence]

    private val TABLE: Map<String, String> = mapOf(
        ".-" to "a", "-..." to "b", "-.-." to "c", "-.." to "d", "." to "e",
        "..-." to "f", "--." to "g", "...." to "h", ".." to "i", ".---" to "j",
        "-.-" to "k", ".-.." to "l", "--" to "m", "-." to "n", "---" to "o",
        ".--." to "p", "--.-" to "q", ".-." to "r", "..." to "s", "-" to "t",
        "..-" to "u", "...-" to "v", ".--" to "w", "-..-" to "x", "-.--" to "y",
        "--.." to "z",
        "-----" to "0", ".----" to "1", "..---" to "2", "...--" to "3",
        "....-" to "4", "....." to "5", "-...." to "6", "--..." to "7",
        "---.." to "8", "----." to "9",
        ".-.-.-" to ".", "--..--" to ",", "..--.." to "?", ".----." to "'",
        "-.-.--" to "!", "-..-." to "/", "-.--." to "(", "-.--.-" to ")",
        ".-..." to "&", "---..." to ":", "-.-.-." to ";", "-...-" to "=",
        ".-.-." to "+", "-....-" to "-", "..--.-" to "_", ".-..-." to "\"",
        "...-..-" to "$", ".--.-." to "@",
    )
}
