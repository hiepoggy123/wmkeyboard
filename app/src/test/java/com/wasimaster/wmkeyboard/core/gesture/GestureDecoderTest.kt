package com.wasimaster.wmkeyboard.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDecoderTest {

    // A synthetic QWERTY grid: 60px keys, rows offset like a real keyboard.
    private val keyWidth = 60f
    private val keys: List<KeyCenter> = buildList {
        "qwertyuiop".forEachIndexed { i, c -> add(KeyCenter(c, 30f + i * 60f, 30f)) }
        "asdfghjkl".forEachIndexed { i, c -> add(KeyCenter(c, 60f + i * 60f, 90f)) }
        "zxcvbnm".forEachIndexed { i, c -> add(KeyCenter(c, 90f + i * 60f, 150f)) }
    }
    private val centers = keys.associateBy { it.char }

    private val lexicon = listOf(
        "hello" to 900, "help" to 700, "held" to 300, "hell" to 200,
        "the" to 1000, "they" to 800, "then" to 700, "them" to 650,
        "was" to 900, "war" to 400, "what" to 850, "good" to 800,
        "god" to 300, "food" to 500, "test" to 400, "text" to 350,
    )

    private val decoder = GestureDecoder(keys, keyWidth)

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

    private fun CharArray.distinctConsecutive(): List<Char> {
        val out = ArrayList<Char>()
        for (c in this) if (out.lastOrNull() != c) out.add(c)
        return out
    }

    @Test
    fun `perfect trace decodes the word`() {
        val results = decoder.decode(gestureFor("hello"), lexicon)
        assertEquals("hello", results.first().word)
    }

    @Test
    fun `double letters need no repeat in the path`() {
        // "good" traces g-o-d only.
        val results = decoder.decode(gestureFor("god"), lexicon)
        val words = results.map { it.word }
        assertTrue("expected good/god in $words", "good" in words || "god" in words)
    }

    @Test
    fun `noisy trace still decodes`() {
        val results = decoder.decode(gestureFor("what", jitter = 14f), lexicon)
        assertEquals("what", results.first().word)
    }

    @Test
    fun `context boost flips a near-tie without rescuing bad shapes`() {
        // "good" and "god" draw the exact same g-o-d trace (double letters
        // collapse); "good" wins on frequency, a learned habit flips it.
        val trace = gestureFor("god")
        assertEquals("good", decoder.decode(trace, lexicon).first().word)
        val boosted = decoder.decode(trace, lexicon, contextBoosts = mapOf("god" to 30))
        assertEquals("god", boosted.first().word)
        // An empty map is byte-identical to the old behavior.
        assertEquals(
            decoder.decode(trace, lexicon).map { it.word },
            decoder.decode(trace, lexicon, contextBoosts = emptyMap()).map { it.word },
        )
        // No boost can admit a word whose shape was pruned: "was" is nowhere
        // near the t-h-e trace.
        val impossible = decoder.decode(trace, lexicon, contextBoosts = mapOf("was" to 1_000_000))
        assertTrue(impossible.none { it.word == "was" })
    }

    @Test
    fun `frequency breaks shape ties`() {
        // t-h-e is a prefix of they/then/them paths; "the" must win on prior.
        val results = decoder.decode(gestureFor("the"), lexicon)
        assertEquals("the", results.first().word)
    }

    @Test
    fun `alternates include shape neighbours`() {
        val results = decoder.decode(gestureFor("hello"), lexicon)
        assertTrue(results.size > 1)
        assertTrue(results.map { it.word }.contains("hell"))
    }

    @Test
    fun `start anchor prunes distant words`() {
        // Gesture around "was" must not offer "the".
        val words = decoder.decode(gestureFor("was"), lexicon).map { it.word }
        assertTrue("the" !in words)
    }

    @Test
    fun `too short a path returns nothing`() {
        val h = centers.getValue('h')
        val results = decoder.decode(
            listOf(GesturePoint(h.x, h.y), GesturePoint(h.x + 2, h.y), GesturePoint(h.x + 4, h.y)),
            lexicon,
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `empty lexicon returns nothing`() {
        assertTrue(decoder.decode(gestureFor("hello"), emptyList()).isEmpty())
    }

    @Test
    fun `words with unmapped characters are skipped`() {
        val results = decoder.decode(gestureFor("the"), listOf("thé" to 5000, "the" to 10))
        assertEquals(listOf("the"), results.map { it.word })
    }

    @Test
    fun `limit caps the result count`() {
        val results = decoder.decode(gestureFor("the"), lexicon, limit = 2)
        assertTrue(results.size <= 2)
    }
}
