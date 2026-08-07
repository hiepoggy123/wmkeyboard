package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.ime.LayoutSet
import com.wasimaster.wmkeyboard.ime.keySpelling

/**
 * A real shipped layout's letter grid, laid out the way the renderer lays it
 * out, for the swipe corpus to draw on.
 *
 * The harness used to carry its own hand-written QWERTY table, which was fine
 * while glide was English-only and is not fine now: the interesting layouts are
 * the ones nobody would copy by hand — Probhat, where most keys are Bengali
 * vowel signs and every consonant hides an aspirated twin behind shift, and
 * ЙЦУКЕН, whose twelve-column rows are a different shape of problem from
 * QWERTY's ten. Deriving the geometry from [LayoutSpec] means the corpus draws
 * on the grid the user actually sees, and a layout edit shows up here.
 *
 * Coordinates are in key widths. Rows narrower than the widest are centred, the
 * same rule `KeyRows` follows, so the stagger a shift or backspace key creates
 * is real rather than assumed.
 */
class GlideGrid private constructor(
    val name: String,
    /** Every character the grid can produce, at its key's centre. */
    val keys: List<KeyCenter>,
) {

    private val byChar: Map<Char, KeyCenter> = keys.associateBy { it.char }

    /** Characters this grid can spell. */
    val alphabet: Set<Char> get() = byChar.keys

    /** Where [ch] sits, in key widths, or null when the grid cannot produce it. */
    fun centerOf(ch: Char): KeyCenter? = byChar[ch.lowercaseChar()]

    /** The character whose key centre sits closest to a point, in key widths. */
    fun nearestKey(x: Float, y: Float): Char {
        var best = keys.first()
        var bestDistance = Float.MAX_VALUE
        for (key in keys) {
            val d = (key.x - x) * (key.x - x) + (key.y - y) * (key.y - y)
            if (d < bestDistance) {
                bestDistance = d
                best = key
            }
        }
        return best.char
    }

    /** The grid in the pixel space of a keyboard with keys [keyWidth] wide. */
    fun keyCenters(keyWidth: Float): List<KeyCenter> =
        keys.map { KeyCenter(it.char, it.x * keyWidth, it.y * keyWidth) }

    companion object {

        fun of(spec: LayoutSpec): GlideGrid {
            val layer = spec.compile(LayoutLayer.LETTERS)
            val rowWidths = layer.rows.map { row -> row.sumOf { it.width.toDouble() }.toFloat() }
            val widest = rowWidths.maxOrNull() ?: 0f
            val centres = HashMap<Char, Pair<Float, Float>>()
            for ((index, row) in layer.rows.withIndex()) {
                var x = (widest - rowWidths[index]) / 2f
                for (key in row) {
                    val centre = x + key.width / 2f
                    x += key.width
                    // Anchored by the first character the key writes, matching
                    // what the renderer reports — a Bengali nukta key writes
                    // two characters and would vanish from a "exactly one" test.
                    val anchor = keySpelling(key.label)?.first() ?: continue
                    centres.putIfAbsent(anchor, centre to index.toFloat())
                }
            }
            // Through LayoutSet so the corpus and the decoder agree on which
            // characters a key can produce, down to the order they are claimed
            // in. Two grids that disagree about that would make every number
            // here a measurement of the disagreement.
            val set = LayoutSet(layer, layer, layer)
            return GlideGrid(spec.name, set.glideKeys { centres[it] })
        }
    }
}
