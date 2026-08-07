package com.wasimaster.wmkeyboard.core.gesture

/**
 * The letter grid a glide is drawn on: every character the layout can produce,
 * resolved to the key that produces it.
 *
 * Two things separate this from the raw [KeyCenter] list it is built from.
 *
 * **Several characters share one key.** On Latin that is only case — `a` and
 * `A` are one key — but it is the general rule, not a Latin quirk. Probhat puts
 * ক and খ on one key, ঞ behind a long press on another; QWERTZ hides ü behind
 * u. A finger crossing a key cannot say which of its characters was meant, so
 * all of them resolve to the same centre and the language model separates them
 * afterwards. Refusing the shifted characters instead would make every aspirated
 * Bengali consonant unglidable, which is most of the language.
 *
 * **Lookup is boxing-free.** The decoder resolves an edge label to a key for
 * every trie edge it considers — hundreds of thousands of times per decode — so
 * the map is a sorted [CharArray] searched by bisection rather than a
 * `HashMap<Char, Int>`, whose keys would box on every probe for any character
 * outside the cached ASCII range (which is to say, every non-Latin script).
 *
 * Coordinates are in key widths, the convention shared with `KeyTouchModel`, so
 * nothing downstream depends on density or keyboard size.
 */
class GlideKeyMap private constructor(
    /** Distinct key centres, x then y, in key widths. */
    val keyX: FloatArray,
    val keyY: FloatArray,
    private val chars: CharArray,
    private val keyOf: IntArray,
) {

    /** How many distinct keys the grid has — the width of the decoder's masks. */
    val keyCount: Int get() = keyX.size

    /**
     * Centre-to-centre distances, `keyCount²` of them. The decoder asks for one
     * on every trie edge it considers, so the square root is paid once per grid
     * rather than a hundred thousand times per stroke.
     */
    private val distances: FloatArray = FloatArray(keyX.size * keyX.size).also { table ->
        for (a in keyX.indices) {
            for (b in keyX.indices) {
                val dx = keyX[a] - keyX[b]
                val dy = keyY[a] - keyY[b]
                table[a * keyX.size + b] = kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
    }

    /** Distance between two keys in key widths. */
    fun distance(a: Int, b: Int): Float = distances[a * keyX.size + b]

    /** True when [ch] is somewhere on the grid. */
    fun knows(ch: Char): Boolean = keyIndex(ch) >= 0

    /**
     * Index of the key that produces [ch], or -1 when the grid cannot. Case is
     * folded, so a dictionary's lowercase spelling finds an uppercase key.
     */
    fun keyIndex(ch: Char): Int {
        val folded = ch.lowercaseChar()
        var lo = 0
        var hi = chars.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val here = chars[mid]
            when {
                here < folded -> lo = mid + 1
                here > folded -> hi = mid - 1
                else -> return keyOf[mid]
            }
        }
        return -1
    }

    companion object {

        /**
         * Builds the grid from [keys], whose coordinates are in the pixel space
         * of a keyboard with keys [keyWidth] wide.
         *
         * Entries sharing a position collapse to one key: the caller emits one
         * [KeyCenter] per character a key can produce, and several characters
         * landing on the same centre is the normal case, not a mistake. Where
         * two keys claim the same character the first wins, which makes the
         * caller's order the priority order (base label, then shifted, then
         * long-press).
         */
        fun of(keys: List<KeyCenter>, keyWidth: Float): GlideKeyMap {
            require(keyWidth > 0f) { "keyWidth must be positive" }
            val xs = ArrayList<Float>(keys.size)
            val ys = ArrayList<Float>(keys.size)
            val byChar = LinkedHashMap<Char, Int>(keys.size * 2)
            for (key in keys) {
                val x = key.x / keyWidth
                val y = key.y / keyWidth
                var index = -1
                for (i in xs.indices) {
                    if (near(xs[i], x) && near(ys[i], y)) {
                        index = i
                        break
                    }
                }
                if (index < 0) {
                    index = xs.size
                    xs.add(x)
                    ys.add(y)
                }
                byChar.putIfAbsent(key.char.lowercaseChar(), index)
            }
            val sorted = byChar.keys.sorted()
            val chars = CharArray(sorted.size) { sorted[it] }
            val keyOf = IntArray(sorted.size) { byChar.getValue(sorted[it]) }
            return GlideKeyMap(xs.toFloatArray(), ys.toFloatArray(), chars, keyOf)
        }

        /**
         * Two centres are the same key when they agree to well under a pixel.
         * Measured centres arrive from `onGloballyPositioned` as floats and the
         * characters of one key are reported from one measurement, so exact
         * equality would very nearly work; the epsilon covers the arithmetic.
         */
        private fun near(a: Float, b: Float): Boolean =
            kotlin.math.abs(a - b) < POSITION_EPSILON

        private const val POSITION_EPSILON = 1e-3f
    }
}
