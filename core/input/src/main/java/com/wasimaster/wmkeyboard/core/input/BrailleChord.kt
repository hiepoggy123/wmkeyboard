package com.wasimaster.wmkeyboard.core.input

/**
 * The chord state of the six-key braille layout: which dot keys are held right
 * now and which dots have been gathered into the pending cell. A cell is
 * everything touched between the first finger down and the last finger up —
 * the standard Perkins chord contract, which is what lets "dots 1+4+5" mean
 * one `d` rather than three letters.
 *
 * Pure state, no timers: the pointer handler reports downs and ups, and the
 * cell commits exactly when the held count returns to zero. A pointer the
 * view loses track of (stolen by another gesture handler, cancelled by the
 * system) still reports as an up, so [held] cannot leak upward; [reset]
 * covers the remaining ways the world can change under a half-typed chord
 * (field switch, layout switch, panel opening).
 */
class BrailleChord {

    /** Bitmask of gathered dots: bit 0 = dot 1 … bit 5 = dot 6. */
    private var pending = 0
    private var held = 0

    /** A dot key went down. [dot] outside 1..6 is ignored (a malformed layout). */
    fun down(dot: Int) {
        if (dot !in 1..6) return
        pending = pending or (1 shl (dot - 1))
        held++
    }

    /**
     * A dot key lifted. Returns the finished cell's dot mask when this was the
     * last held key, or null while other fingers are still down (or when the
     * chord was empty — an up with no tracked down, after a [reset]).
     */
    fun up(): Int? {
        if (held > 0) held--
        if (held > 0) return null
        val cell = pending
        pending = 0
        return cell.takeIf { it != 0 }
    }

    /** Drops any half-typed chord: field change, layout switch, panel open. */
    fun reset() {
        pending = 0
        held = 0
    }
}

/**
 * Grade-1 (uncontracted) English braille: one cell, one character. Stateful
 * only for the two prefix indicators the code defines — dot 6 capitalizes the
 * next letter, dots 3456 shifts into number mode until a cell that isn't a
 * digit (or a [reset]) ends it. A cell no table knows falls back to the raw
 * Unicode braille pattern (U+2800 + mask), so nothing a user chords is ever
 * swallowed silently — and chording arbitrary patterns is itself a way to
 * type the Braille Patterns block.
 */
class BrailleGrade1 {

    private var capitalNext = false
    private var numberMode = false

    /** The committed text for a finished [cell] mask, empty for a bare indicator. */
    fun decode(cell: Int): String {
        when (cell) {
            CAPITAL_INDICATOR -> {
                capitalNext = true
                return ""
            }
            NUMBER_INDICATOR -> {
                numberMode = true
                return ""
            }
        }
        if (numberMode) {
            val digit = DIGITS[cell]
            if (digit != null) return digit
            numberMode = false
        }
        val letter = LETTERS[cell]
        if (letter != null) {
            val out = if (capitalNext) letter.uppercase() else letter
            capitalNext = false
            return out
        }
        PUNCTUATION[cell]?.let { return it }
        // Not Grade-1: commit the raw braille cell itself.
        return ((BRAILLE_BLOCK_BASE + cell).toChar()).toString()
    }

    /** Clears the indicator latches: field change, layout switch. */
    fun reset() {
        capitalNext = false
        numberMode = false
    }

    companion object {
        private const val BRAILLE_BLOCK_BASE = 0x2800

        /** Dot 6 alone: the next letter is a capital. */
        const val CAPITAL_INDICATOR = 0b100000

        /** Dots 3456: digits until a non-digit cell. */
        const val NUMBER_INDICATOR = 0b111100

        private fun mask(vararg dots: Int): Int =
            dots.fold(0) { acc, d -> acc or (1 shl (d - 1)) }

        private val LETTERS: Map<Int, String> = mapOf(
            mask(1) to "a", mask(1, 2) to "b", mask(1, 4) to "c",
            mask(1, 4, 5) to "d", mask(1, 5) to "e", mask(1, 2, 4) to "f",
            mask(1, 2, 4, 5) to "g", mask(1, 2, 5) to "h", mask(2, 4) to "i",
            mask(2, 4, 5) to "j", mask(1, 3) to "k", mask(1, 2, 3) to "l",
            mask(1, 3, 4) to "m", mask(1, 3, 4, 5) to "n", mask(1, 3, 5) to "o",
            mask(1, 2, 3, 4) to "p", mask(1, 2, 3, 4, 5) to "q",
            mask(1, 2, 3, 5) to "r", mask(2, 3, 4) to "s", mask(2, 3, 4, 5) to "t",
            mask(1, 3, 6) to "u", mask(1, 2, 3, 6) to "v", mask(2, 4, 5, 6) to "w",
            mask(1, 3, 4, 6) to "x", mask(1, 3, 4, 5, 6) to "y", mask(1, 3, 5, 6) to "z",
        )

        /** Digits reuse the a..j cells; only reachable in number mode. */
        private val DIGITS: Map<Int, String> = mapOf(
            mask(1) to "1", mask(1, 2) to "2", mask(1, 4) to "3",
            mask(1, 4, 5) to "4", mask(1, 5) to "5", mask(1, 2, 4) to "6",
            mask(1, 2, 4, 5) to "7", mask(1, 2, 5) to "8", mask(2, 4) to "9",
            mask(2, 4, 5) to "0",
        )

        private val PUNCTUATION: Map<Int, String> = mapOf(
            mask(2) to ",", mask(2, 3) to ";", mask(2, 5) to ":",
            mask(2, 5, 6) to ".", mask(2, 3, 5) to "!", mask(2, 3, 6) to "?",
            mask(3) to "'", mask(3, 6) to "-",
        )
    }
}
