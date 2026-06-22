package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.settings.InputMode

/**
 * Physical adjacency on a keyboard layout, used to weight typo corrections:
 * substituting a key with one of its neighbours is a likely fat-finger slip,
 * substituting a distant key is probably a different word entirely.
 *
 * Neighbours are derived from the layout's letter rows: the keys either side in
 * the same row, plus the three keys straddling the same column in the rows above
 * and below (the phone grid is only half-key staggered, so i-1..i+1 covers every
 * touching key).
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

        /**
         * Adjacency for the grid [spec] actually draws.
         *
         * This used to be five hand-written tables of row strings, one per Latin
         * layout, which a custom layout could never appear in — it would have
         * silently borrowed QWERTY's neighbours and scored typos against keys
         * that were nowhere near the user's finger. Deriving them means a
         * rearranged grid gets correct proximity for free, and `KeyProximityTest`
         * pins the derivation against the old tables so this stayed a refactor.
         *
         * [LayoutSpec.proximityRows] overrides the derivation for the one thing a
         * grid cannot express: a staggered or split arrangement whose rows do not
         * share a column origin.
         */
        fun forLayout(spec: LayoutSpec): KeyProximity =
            KeyProximity(spec.proximityRows ?: letterRows(spec))

        /**
         * The layout a mode renders, as proximity rows.
         *
         * Scaffolding: once the service resolves the active layout it passes the
         * spec to [forLayout] directly, and a custom layout stops being invisible
         * to typo weighting.
         */
        fun forMode(mode: InputMode): KeyProximity = forLayout(BuiltInLayouts.forMode(mode))

        /** The default a suggestion engine starts on, before a layout is known. */
        val QWERTY: KeyProximity = forLayout(BuiltInLayouts.QWERTY)

        /**
         * The letter grid projected to one string of committed characters per row.
         *
         * Only single-character text keys count, so shift, delete and the layer
         * switches drop out and the remaining keys keep their column positions —
         * which is what makes the row-above/row-below straddle meaningful.
         *
         * The row holding the spacebar is skipped entirely. It is punctuation and
         * layer keys rather than letters, and including it would hang a fourth
         * proximity row of "," and "." under the alphabet, making them neighbours
         * of whatever happened to sit above.
         *
         * Rows of one character or none are dropped: a lone character has no
         * in-row neighbour, and leaving it in would shift the column alignment of
         * every row below it. The filter is deliberately *any* single character
         * rather than `Char::isLetter` — Dvorak's top row is `'`, `,` and `.`
         * followed by seven letters, and dropping those three would move the rest
         * three columns left.
         */
        private fun letterRows(spec: LayoutSpec): List<String> {
            val rows = spec.layer(LayoutLayer.LETTERS)?.rows ?: return emptyList()
            return rows
                .filterNot { row -> row.any { it.action == KeyAction.Space } }
                .map { row ->
                    buildString {
                        for (key in row) {
                            if (key.action != KeyAction.Text) continue
                            val text = key.output ?: key.label
                            text.singleOrNull()?.let(::append)
                        }
                    }
                }
                .filter { it.length > 1 }
        }
    }
}
