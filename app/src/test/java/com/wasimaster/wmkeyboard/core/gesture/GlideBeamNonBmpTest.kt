package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glide over a script outside the BMP — Osage, Adlam, Warang Citi — where every
 * letter is a surrogate pair and the trie therefore spells each one as two
 * edges.
 *
 * The grid here is QWERTY with each Latin letter replaced by a Warang Citi small
 * letter (U+118C0 onward), so every stroke is one the Latin tests already know
 * decodes, and the only thing under test is that a letter split across two
 * UTF-16 units is still one key. Before this the high surrogate — the same one
 * for every letter in the block — was asked for as a key of its own, found
 * nothing, and pruned the whole dictionary at its first letter.
 */
class GlideBeamNonBmpTest {

    private val keyWidth = 60f

    /** `a`..`z` onto U+118C0..U+118D9, the Warang Citi small letters. */
    private fun letter(latin: Char): Int = 0x118C0 + (latin - 'a')

    private fun word(latin: String): String = buildString {
        for (c in latin) appendCodePoint(letter(c))
    }

    private val keys: List<KeyCenter> = buildList {
        "qwertyuiop".forEachIndexed { i, c -> add(KeyCenter(letter(c), 30f + i * 60f, 30f)) }
        "asdfghjkl".forEachIndexed { i, c -> add(KeyCenter(letter(c), 60f + i * 60f, 90f)) }
        "zxcvbnm".forEachIndexed { i, c -> add(KeyCenter(letter(c), 90f + i * 60f, 150f)) }
    }
    private val centers = keys.associateBy { it.codePoint }
    private val grid = GlideKeyMap.of(keys, keyWidth)

    private val lexicon = listOf(
        "hello" to 900, "help" to 700, "held" to 300, "hell" to 200, "ho" to 400,
        "the" to 1000, "they" to 800, "then" to 700, "them" to 650,
        "was" to 900, "war" to 400, "what" to 850, "good" to 800,
        "god" to 300, "food" to 500, "test" to 400, "text" to 350,
    ).map { (latin, frequency) -> word(latin) to frequency }

    private val beam = GlideBeam()
    private val workspace = GlideWorkspace()

    private val sources: List<FuzzyBeamSearch.WalkSource> = Trie()
        .apply { lexicon.forEach { (w, frequency) -> insert(w, frequency) } }
        .walkers()
        .map { FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY) }

    private fun decode(path: List<GesturePoint>, limit: Int = 4): List<String> =
        beam.decode(path, grid, keyWidth, sources, workspace, limit).map { it.word }

    /** Ideal gesture through the Latin spelling's keys, densely sampled. */
    private fun gestureFor(latin: String): List<GesturePoint> {
        val anchors = ArrayList<KeyCenter>()
        for (c in latin) {
            val key = centers.getValue(letter(c))
            if (anchors.lastOrNull() != key) anchors.add(key)
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
        return points
    }

    @Test
    fun `the grid knows a letter outside the BMP as one key`() {
        assertTrue(grid.knows(letter('h')))
        // Neither half of the pair is a key: the surrogates are not characters.
        val pair = Character.toChars(letter('h'))
        assertFalse(grid.knows(pair[0]))
        assertFalse(grid.knows(pair[1]))
        assertEquals(26, grid.keyCount)
    }

    @Test
    fun `a perfect trace decodes to the word, spelled as surrogate pairs`() {
        val decoded = decode(gestureFor("hello")).first()
        assertEquals(word("hello"), decoded)
        assertEquals("five letters, ten UTF-16 units", 10, decoded.length)
        assertEquals(5, decoded.codePointCount(0, decoded.length))
    }

    @Test
    fun `every candidate comes back as whole letters`() {
        // The word is rebuilt from the trie edges leaf to root, and a reversal
        // that keeps surrogate pairs together — StringBuilder.reverse() — turns
        // that unit chain into a stray surrogate at each end with every
        // letter's halves swapped. Every candidate has to be well-formed.
        for (latin in listOf("the", "hello", "was", "food")) {
            val words = decode(gestureFor(latin))
            assertEquals("$latin: best candidate", word(latin), words.first())
            for (w in words) {
                assertEquals("$latin: '$w' is not whole letters", w.length, 2 * w.codePointCount(0, w.length))
                var at = 0
                while (at < w.length) {
                    val cp = w.codePointAt(at)
                    assertTrue("$latin: '$w' holds $cp outside Warang Citi", cp in 0x118C0..0x118D9)
                    at += Character.charCount(cp)
                }
            }
        }
    }

    @Test
    fun `a doubled letter needs no repeat in the path`() {
        // g-o-d on the grid; the lexicon has both "god" and "good", and the
        // repeat charge is compared letter to letter rather than unit to unit.
        val words = decode(gestureFor("good"))
        assertTrue("expected good in $words", word("good") in words)
        assertTrue("expected god in $words", word("god") in words)
    }

    @Test
    fun `the shape rescore can draw a word outside the BMP`() {
        // The rescore builds each candidate's ideal path by code point; a Char
        // walk would ask for the high surrogate's key and refuse every word.
        val ranked = decode(gestureFor("what"))
        assertEquals(word("what"), ranked.first())
    }
}
