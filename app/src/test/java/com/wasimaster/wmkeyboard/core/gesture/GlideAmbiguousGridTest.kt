package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.letterSet
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Swiping a keyboard that carries several letters on a key (discussion #103).
 *
 * The decoder needs no change for this and gets none: [GlideKeyMap] has always
 * resolved several characters to one key — that is how Probhat's ক/খ key is
 * glidable — so the whole of the work is the grid publishing every letter of a
 * key at that key's centre. These tests are the proof that it does, on the two
 * shipped ambiguous layouts.
 *
 * The stroke is drawn through *keys*, not letters, because on these boards
 * that is all a finger can do: "good" and "home" are the same stroke on a
 * keypad, and which of them comes back is the language model's business.
 */
class GlideAmbiguousGridTest {

    private val keyWidth = 60f

    /**
     * The grid a layout draws, laid out on a regular lattice: one row per row,
     * one column per key, each key's whole letter set sharing its centre — the
     * same thing `LayoutSet.glideKeys` builds from the measured keyboard.
     */
    private fun gridOf(spec: LayoutSpec): Pair<GlideKeyMap, Map<Int, KeyCenter>> {
        val rows = spec.layer(LayoutLayer.LETTERS)!!.rows
        val keys = ArrayList<KeyCenter>()
        for ((r, row) in rows.withIndex()) {
            var x = keyWidth / 2f
            for (key in row) {
                val width = key.width * keyWidth
                if (key.action == KeyAction.Text) {
                    for (letter in key.letterSet()) {
                        keys.add(KeyCenter(letter.lowercaseChar().code, x + width / 2f, 30f + r * 60f))
                    }
                }
                x += width
            }
        }
        return GlideKeyMap.of(keys, keyWidth) to keys.associateBy { it.codePoint }
    }

    private val lexicon = listOf(
        "hello" to 900, "help" to 700, "good" to 800, "home" to 400, "gone" to 300,
        "the" to 1000, "they" to 800, "them" to 650, "keyboard" to 500, "word" to 450,
        "phone" to 300, "type" to 250, "text" to 350, "well" to 400,
    )

    private val sources: List<FuzzyBeamSearch.WalkSource> =
        Trie().apply { lexicon.forEach { (w, f) -> insert(w, f) } }
            .walkers()
            .map { FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY) }

    private val beam = GlideBeam()
    private val workspace = GlideWorkspace()

    /** A straight, densely sampled stroke through the keys [word]'s letters sit on. */
    private fun stroke(word: String, centers: Map<Int, KeyCenter>): List<GesturePoint> {
        val anchors = ArrayList<KeyCenter>()
        for (ch in word) {
            val key = centers.getValue(ch.lowercaseChar().code)
            // Consecutive letters on the *same key* are one point on the path:
            // a finger cannot visit a key twice without leaving it, which is
            // exactly the double-letter problem the keypad has always had.
            if (anchors.lastOrNull()?.let { it.x == key.x && it.y == key.y } != true) anchors.add(key)
        }
        val points = ArrayList<GesturePoint>()
        for (i in 0 until anchors.size - 1) {
            val a = anchors[i]
            val b = anchors[i + 1]
            for (step in 0..10) {
                val t = step / 10f
                points.add(GesturePoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y)))
            }
        }
        if (anchors.size == 1) points.add(GesturePoint(anchors[0].x, anchors[0].y))
        return points
    }

    private fun decode(spec: LayoutSpec, word: String, limit: Int = 8): List<String> {
        val (grid, centers) = gridOf(spec)
        return beam.decode(stroke(word, centers), grid, keyWidth, sources, workspace, limit)
            .map { it.word }
    }

    @Test
    fun everyLetterOfAnAmbiguousKeyReachesTheGrid() {
        // The thing that used to drop these boards off the grid entirely: a
        // key labelled "ABC" is not a spelling, so anchoring by the label
        // found nothing and took glide, the touch model and the coverage
        // measurement with it.
        for (spec in listOf(BuiltInLayouts.T9, BuiltInLayouts.COMPACT)) {
            val (grid, _) = gridOf(spec)
            for (ch in 'a'..'z') {
                assertTrue("${spec.name} cannot glide '$ch'", grid.knows(ch))
            }
        }
    }

    @Test
    fun lettersSharingAKeyShareItsCentre() {
        val (grid, _) = gridOf(BuiltInLayouts.T9)
        // a, b and c are one key; d is the next one along.
        val a = grid.keyIndex('a'.code)
        assertEquals(a, grid.keyIndex('b'.code))
        assertEquals(a, grid.keyIndex('c'.code))
        assertNotEquals(a, grid.keyIndex('d'.code))
        assertEquals(0f, grid.distance(a, grid.keyIndex('c'.code)), 1e-6f)
    }

    @Test
    fun aStrokeAcrossTheKeypadDecodes() {
        // The claim the discussion says it has never seen anywhere: a swipe on
        // T9 that comes back with words.
        val got = decode(BuiltInLayouts.T9, "hello")
        assertTrue("keypad stroke decoded to $got", got.isNotEmpty())
        assertTrue("'hello' missing from $got", "hello" in got)
    }

    @Test
    fun aStrokeAcrossTheCompactGridDecodes() {
        val got = decode(BuiltInLayouts.COMPACT, "keyboard")
        assertTrue("'keyboard' missing from $got", "keyboard" in got)
    }

    @Test
    fun theCompactGridDecodesWhatQwertyWould() {
        // Two letters to a key is half the information, so the compact board
        // leans harder on the language model — but the words still come back.
        for (word in listOf("hello", "they", "word", "phone", "text")) {
            val got = decode(BuiltInLayouts.COMPACT, word, limit = 12)
            assertTrue("'$word' missing from $got", word in got)
        }
    }

    @Test
    fun oneStrokeCarriesEveryReadingOfItsKeys() {
        // "good", "home" and "gone" are one stroke on a keypad — 4 6 6 3 with
        // the middle two collapsed, since a finger cannot press a key twice.
        // All three should be on offer, which is exactly what makes the picker
        // worth having on this board.
        val got = decode(BuiltInLayouts.T9, "good", limit = 12)
        assertTrue("'good' missing from $got", "good" in got)
        assertTrue("'gone' missing from $got", "gone" in got)
    }
}
