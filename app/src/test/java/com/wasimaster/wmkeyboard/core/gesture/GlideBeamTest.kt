package com.wasimaster.wmkeyboard.core.gesture

import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideBeamTest {

    // A synthetic QWERTY grid: 60px keys, rows offset like a real keyboard.
    private val keyWidth = 60f
    private val keys: List<KeyCenter> = buildList {
        "qwertyuiop".forEachIndexed { i, c -> add(KeyCenter(c, 30f + i * 60f, 30f)) }
        "asdfghjkl".forEachIndexed { i, c -> add(KeyCenter(c, 60f + i * 60f, 90f)) }
        "zxcvbnm".forEachIndexed { i, c -> add(KeyCenter(c, 90f + i * 60f, 150f)) }
    }
    private val centers = keys.associateBy { it.char }
    private val grid = GlideKeyMap.of(keys, keyWidth)

    private val lexicon = listOf(
        "hello" to 900, "help" to 700, "held" to 300, "hell" to 200, "ho" to 400,
        "the" to 1000, "they" to 800, "then" to 700, "them" to 650,
        "was" to 900, "war" to 400, "what" to 850, "good" to 800,
        "god" to 300, "food" to 500, "test" to 400, "text" to 350,
    )

    private val beam = GlideBeam()
    private val workspace = GlideWorkspace()

    private fun sourcesOf(entries: List<Pair<String, Int>>): List<FuzzyBeamSearch.WalkSource> {
        val trie = Trie().apply { entries.forEach { (word, frequency) -> insert(word, frequency) } }
        return trie.walkers().map {
            FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        }
    }

    private val sources = sourcesOf(lexicon)

    private fun decode(
        path: List<GesturePoint>,
        from: List<FuzzyBeamSearch.WalkSource> = sources,
        limit: Int = 4,
        on: GlideKeyMap = grid,
    ): List<String> =
        beam.decode(path, on, keyWidth, from, workspace, limit).map { it.word }

    /** Ideal gesture: straight lines through the word's key centres, densely sampled. */
    private fun gestureFor(word: String, jitter: Float = 0f): List<GesturePoint> {
        val anchors = word.toCharArray().distinctConsecutive().map { centers.getValue(it) }
        val points = ArrayList<GesturePoint>()
        for (i in 0 until anchors.size - 1) {
            val a = anchors[i]
            val b = anchors[i + 1]
            for (step in 0..10) {
                val t = step / 10f
                // Deterministic zig-zag "noise" so tests stay reproducible.
                val offset = if (jitter == 0f) 0f else jitter * (if (step % 2 == 0) 1 else -1)
                points.add(GesturePoint(a.x + t * (b.x - a.x) + offset, a.y + t * (b.y - a.y) + offset))
            }
        }
        if (anchors.size == 1) points.add(GesturePoint(anchors[0].x, anchors[0].y))
        return points
    }

    /**
     * The same stroke with the finger holding still on [letter]'s key, and a
     * clock attached — a synthetic path has no timestamps at all, which reads
     * as "never paused anywhere" and is the right default.
     */
    private fun pausedAt(path: List<GesturePoint>, letter: Char): List<GesturePoint> {
        val key = centers.getValue(letter)
        var nearest = 0
        var best = Float.MAX_VALUE
        path.forEachIndexed { i, p ->
            val d = (p.x - key.x) * (p.x - key.x) + (p.y - key.y) * (p.y - key.y)
            if (d < best) {
                best = d
                nearest = i
            }
        }
        val out = ArrayList<GesturePoint>(path.size + HOLD_SAMPLES)
        var clock = 0L
        path.forEachIndexed { i, p ->
            out.add(GesturePoint(p.x, p.y, clock))
            clock += SAMPLE_MS
            if (i == nearest) {
                repeat(HOLD_SAMPLES) {
                    out.add(GesturePoint(p.x, p.y, clock))
                    clock += SAMPLE_MS
                }
            }
        }
        return out
    }

    private fun CharArray.distinctConsecutive(): List<Char> {
        val out = ArrayList<Char>()
        for (c in this) if (out.lastOrNull() != c) out.add(c)
        return out
    }

    @Test
    fun `perfect trace decodes the word`() {
        assertEquals("hello", decode(gestureFor("hello")).first())
    }

    @Test
    fun `double letters need no repeat in the path`() {
        // "good" traces g-o-d only, so both spellings must be on offer.
        val words = decode(gestureFor("god"))
        assertTrue("expected both spellings in $words", "good" in words && "god" in words)
    }

    @Test
    fun `a doubled letter needs the finger to have paused on it`() {
        // The stroke is identical either way — the o is one key, crossed once —
        // so the only thing that can separate "good" from "god" is whether the
        // finger hesitated there. "good" is the commoner word by frequency, and
        // that deliberately is not enough on its own.
        assertEquals("god", decode(gestureFor("god")).first())
        assertEquals("good", decode(pausedAt(gestureFor("god"), 'o')).first())
    }

    @Test
    fun `noisy trace still decodes`() {
        assertEquals("what", decode(gestureFor("what", jitter = 14f)).first())
    }

    @Test
    fun `travel between letters rules out a word that only hits the endpoints`() {
        // "ho" puts both its letters exactly where the "hello" stroke starts
        // and ends. Only the distance the finger covered in between separates
        // them, which is the whole reason the gap term exists.
        val words = decode(gestureFor("hello"))
        assertEquals("hello", words.first())
        assertTrue("ho should rank below hello, got $words", words.indexOf("ho") != 0)
    }

    @Test
    fun `frequency breaks shape ties`() {
        // t-h-e is a prefix of they/then/them paths; "the" must win on prior.
        assertEquals("the", decode(gestureFor("the")).first())
    }

    @Test
    fun `a weighted personal source can outrank the dictionary`() {
        // The user tier is how a learned habit reaches the glide decoder, the
        // same way it reaches the typing beam.
        val personal = sourcesOf(listOf("good" to 50)).map {
            FuzzyBeamSearch.WalkSource(it.walker, 4.0, FuzzyBeamSearch.Tier.USER)
        }
        assertEquals("god", decode(gestureFor("god")).first())
        assertEquals("good", decode(gestureFor("god"), from = sources + personal).first())
    }

    @Test
    fun `alternates include shape neighbours`() {
        val words = decode(gestureFor("hello"))
        assertTrue(words.size > 1)
        assertTrue("expected hell in $words", "hell" in words)
    }

    @Test
    fun `start anchor prunes distant words`() {
        assertFalse("the" in decode(gestureFor("was")))
    }

    @Test
    fun `too short a path returns nothing`() {
        val h = centers.getValue('h')
        val stub = listOf(
            GesturePoint(h.x, h.y),
            GesturePoint(h.x + 2, h.y),
            GesturePoint(h.x + 4, h.y),
        )
        assertTrue(decode(stub).isEmpty())
    }

    @Test
    fun `no sources returns nothing`() {
        assertTrue(decode(gestureFor("hello"), from = emptyList()).isEmpty())
    }

    @Test
    fun `words with unmapped characters are skipped`() {
        val accented = sourcesOf(listOf("thé" to 5000, "the" to 10))
        assertEquals(listOf("the"), decode(gestureFor("the"), from = accented))
    }

    @Test
    fun `limit caps the result count`() {
        assertTrue(decode(gestureFor("the"), limit = 2).size <= 2)
    }

    @Test
    fun `characters sharing a key are all reachable`() {
        // The Bengali case in miniature. Probhat puts ক and খ on one key, so a
        // stroke crossing it cannot say which was meant: both spellings have to
        // come back, and the language model has to be what separates them.
        //
        // The second alphabet is built by codepoint arithmetic rather than
        // written out, because Bengali literals in source have a history of
        // arriving decomposed and failing invisibly.
        val other = { index: Int -> BENGALI_KA + index }
        val doubled = keys + keys.mapIndexed { i, k -> KeyCenter(other(i), k.x, k.y) }
        val doubledGrid = GlideKeyMap.of(doubled, keyWidth)
        val twin = buildString {
            for (c in "the") append(other(keys.indexOfFirst { it.char == c }))
        }
        val paired = sourcesOf(listOf("the" to 100, twin to 900))

        val words = beam.decode(gestureFor("the"), doubledGrid, keyWidth, paired, workspace, 4)
            .map { it.word }
        assertEquals("both spellings should survive, got $words", 2, words.size)
        assertEquals(twin, words.first())
    }

    @Test
    fun `a grid the word cannot be drawn on returns nothing for it`() {
        // Only the top row exists: nothing spelling "was" is reachable.
        val topRow = GlideKeyMap.of(keys.filter { it.y < 60f }, keyWidth)
        assertFalse("was" in decode(gestureFor("was"), on = topRow))
    }

    private companion object {
        /** U+0995 BENGALI LETTER KA, the base of the synthetic second alphabet. */
        const val BENGALI_KA = 'ক'

        /** A plausible digitizer interval, and a hold long enough to read as one. */
        const val SAMPLE_MS = 8L
        const val HOLD_SAMPLES = 30
    }
}
