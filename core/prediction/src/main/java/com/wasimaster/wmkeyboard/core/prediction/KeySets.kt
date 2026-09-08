package com.wasimaster.wmkeyboard.core.prediction

/**
 * Which letters each keystroke of a word could have meant, for a keyboard that
 * puts more than one letter on a key — T9's `2 = abc`, a compact grid's
 * `qw` (discussion #103).
 *
 * The typed buffer still holds one character per keystroke: the *anchor* letter
 * the key commits, which keeps backspace, the caret and every word boundary
 * working exactly as they do on a 1:1 board. This is the side-channel that says
 * what else that keystroke might have been, and it is what turns the literal
 * `adg` into "big". Positions the user resolved themselves — a letter picked
 * out of the key's long-press popup — carry a single letter and are decoded as
 * the plain input they are.
 *
 * The twin of [TouchPoint]'s frame in every respect that matters: one entry per
 * composing character, snapshotted with the buffer, and absent (null) on the
 * ordinary keyboards, where the decoder takes its historical path untouched.
 *
 * Letters are folded to lower case on the way in, because the tries are.
 */
class KeySets private constructor(private val sets: Array<String>) {

    /** Whether any position carries a choice at all. */
    val isAmbiguous: Boolean = sets.any { it.length > 1 }

    /**
     * The letters keystroke [pos] could have meant, or null where it could only
     * have meant the one character the buffer already holds — an unambiguous
     * key, a long-press pick, a position past the end of the frame.
     */
    fun at(pos: Int): String? = sets.getOrNull(pos)?.takeIf { it.length > 1 }

    /** Whether keystroke [pos] could have meant [label]. */
    fun accepts(pos: Int, label: Char): Boolean = at(pos)?.contains(label) == true

    /**
     * Structural equality, for the same reason [KeyProximity] has it: the
     * ranked-walk cache keys on this, and a frame rebuilt per keystroke from
     * an unchanged grid must compare equal to the one it replaces.
     */
    override fun equals(other: Any?): Boolean =
        other is KeySets && other.sets.contentEquals(sets)

    override fun hashCode(): Int = sets.contentHashCode()

    companion object {

        /**
         * The frame for a buffer whose keystrokes carried [letters], or null
         * when none of them carried a choice — which is every ordinary layout,
         * and the answer that keeps the decoder on its 1:1 path.
         *
         * A frame shorter or longer than the buffer is tolerated rather than
         * rejected: the decoder reads it by position and a missing entry is
         * simply an unambiguous keystroke, which is what a buffer re-armed from
         * the field (nothing known about how its characters were typed) should
         * decode as anyway.
         */
        fun of(letters: List<String>): KeySets? {
            if (letters.none { it.length > 1 }) return null
            return KeySets(Array(letters.size) { letters[it].lowercase() })
        }
    }
}
