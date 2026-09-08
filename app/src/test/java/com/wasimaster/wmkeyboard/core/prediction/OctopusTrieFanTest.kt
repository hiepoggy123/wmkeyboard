package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dense mode's fan-out: the best word under every key that could extend the
 * prefix. The sparse board is filled by the ranked walk, but "one word per
 * key" needs an answer for the keys the walk never reached, and asking the
 * trie directly is the cheap way to get one.
 */
class OctopusTrieFanTest {

    private fun trie(vararg words: Pair<String, Int>) = Trie().apply {
        for ((word, frequency) in words) insert(word, frequency)
    }

    private fun fan(trie: Trie, prefix: String) =
        octopusTrieFan(trie.walkers().single(), prefix)

    @Test
    fun `each edge reports the best word beneath it`() {
        val trie = trie(
            "hello" to 100, "hellish" to 5,
            "help" to 90, "helping" to 3,
            "held" to 40,
        )
        val fanned = fan(trie, "hel")
        assertEquals(setOf('l'.code, 'p'.code, 'd'.code), fanned.keys)
        assertEquals("hello", fanned['l'.code]?.word)
        assertEquals("help", fanned['p'.code]?.word)
        assertEquals("held", fanned['d'.code]?.word)
    }

    @Test
    fun `a supplementary letter is one whole code point, not a lone surrogate`() {
        // Osage 𐒰 (U+104B0). The trie is Char-keyed, so this letter is two
        // edges deep; reporting the high surrogate would key the word to a
        // code point no keyboard has, and the word would silently never float.
        val osage = "𐒰"
        val trie = trie("ha${osage}wa" to 10)
        val fanned = fan(trie, "ha")
        assertEquals(setOf(osage.codePointAt(0)), fanned.keys)
        assertTrue(osage.codePointAt(0) > 0xFFFF)
        assertEquals("ha${osage}wa", fanned[osage.codePointAt(0)]?.word)
    }

    @Test
    fun `a prefix the trie does not know fans nothing`() {
        assertEquals(emptyMap<Int, Suggestion>(), fan(trie("hello" to 1), "zzz"))
    }

    @Test
    fun `a prefix that is a whole word with no continuations fans nothing`() {
        assertEquals(emptyMap<Int, Suggestion>(), fan(trie("hello" to 1), "hello"))
    }

    @Test
    fun `frequency decides between two words under the same edge`() {
        val fanned = fan(trie("hello" to 5, "hellish" to 50), "hel")
        assertEquals("hellish", fanned['l'.code]?.word)
        assertEquals(50, fanned['l'.code]?.frequency)
    }
}
