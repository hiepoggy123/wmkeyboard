package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.settings.InputMode

/**
 * Physical adjacency on a Latin keyboard layout, used to weight typo
 * corrections: substituting a key with one of its neighbours is a likely
 * fat-finger slip, substituting a distant key is probably a different
 * word entirely.
 *
 * Neighbours are derived from the layout's letter rows: the keys either
 * side in the same row, plus the three keys straddling the same column in
 * the rows above and below (the phone grid is only half-key staggered, so
 * i-1..i+1 covers every touching key).
 */
class KeyProximity private constructor(rows: List<String>) {

    private val neighbors: Map<Char, String> = buildMap {
        for ((r, row) in rows.withIndex()) {
            for ((i, key) in row.withIndex()) {
                val adjacent = StringBuilder()
                if (i > 0) adjacent.append(row[i - 1])
                if (i < row.length - 1) adjacent.append(row[i + 1])
                for (other in listOfNotNull(rows.getOrNull(r - 1), rows.getOrNull(r + 1))) {
                    for (j in (i - 1)..(i + 1)) {
                        other.getOrNull(j)?.let { adjacent.append(it) }
                    }
                }
                put(key, adjacent.toString())
            }
        }
    }

    fun areAdjacent(a: Char, b: Char): Boolean = neighbors[a]?.contains(b) == true

    companion object {
        val QWERTY = KeyProximity(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))
        val AZERTY = KeyProximity(listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"))
        val DVORAK = KeyProximity(listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz"))
        val QWERTZ = KeyProximity(listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"))
        val SPANISH = KeyProximity(listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"))

        /** The proximity map matching the layout a mode renders. */
        fun forMode(mode: InputMode): KeyProximity = when (mode) {
            InputMode.AZERTY, InputMode.FRENCH -> AZERTY
            InputMode.DVORAK -> DVORAK
            InputMode.GERMAN -> QWERTZ
            InputMode.SPANISH -> SPANISH
            else -> QWERTY
        }
    }
}
