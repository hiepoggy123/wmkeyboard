package com.wasimaster.wmkeyboard.core.prediction.eval

/**
 * Test-local QWERTY key geometry for the typo synthesizer. [com.wasimaster.wmkeyboard.core.prediction.KeyProximity]
 * only knows adjacency, but generating realistic touch noise needs actual key
 * centers, so the corpus carries its own coordinate table. Units are key
 * widths (pitch 1.0); row offsets mirror a phone keyboard's stagger.
 */
object QwertyGeometry {

    data class Key(val char: Char, val x: Float, val y: Float)

    private val rows = listOf(
        "qwertyuiop" to 0.0f,
        "asdfghjkl" to 0.25f,
        "zxcvbnm" to 0.75f,
    )

    val keys: List<Key> = buildList {
        for ((rowIndex, row) in rows.withIndex()) {
            val (letters, offset) = row
            for ((col, ch) in letters.withIndex()) {
                add(Key(ch, offset + col, rowIndex.toFloat()))
            }
        }
    }

    private val byChar: Map<Char, Key> = keys.associateBy { it.char }

    fun keyFor(ch: Char): Key? = byChar[ch]

    fun nearestKey(x: Float, y: Float): Char {
        var best = keys.first()
        var bestDist = Float.MAX_VALUE
        for (key in keys) {
            val dx = key.x - x
            val dy = key.y - y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = key
            }
        }
        return best.char
    }

    /** Characters whose key centers lie within [radius] key widths of [ch]'s center, excluding [ch]. */
    fun neighborsOf(ch: Char, radius: Float = 1.3f): List<Char> {
        val center = byChar[ch] ?: return emptyList()
        val r2 = radius * radius
        return keys.filter { key ->
            key.char != ch &&
                (key.x - center.x) * (key.x - center.x) + (key.y - center.y) * (key.y - center.y) <= r2
        }.map { it.char }
    }
}
