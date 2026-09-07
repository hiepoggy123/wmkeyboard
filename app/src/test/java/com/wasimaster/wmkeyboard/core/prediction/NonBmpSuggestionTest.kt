package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed suggestions for a word list outside the BMP — Osage, Adlam, Warang
 * Citi — where every letter is a surrogate pair.
 *
 * The beam itself was always fine with that: it walks the trie one UTF-16 unit
 * at a time and a pair is just two edges. What was not fine was reading the
 * word back off the state chain with `StringBuilder.reverse()`, which keeps a
 * high-low pair together and so, given the leaf-first chain's *low-high* runs,
 * handed back a string with its letters' halves swapped and a lone surrogate at
 * each end. Suggestions for these scripts were garbage that no test looked at.
 */
class NonBmpSuggestionTest {

    /** `a`..`z` onto the Warang Citi small letters, U+118C0 onward. */
    private fun word(latin: String): String = buildString {
        for (c in latin) appendCodePoint(0x118C0 + (c - 'a'))
    }

    private val engine = SuggestionEngine(
        Trie().apply {
            insert(word("hello"), 100)
            insert(word("help"), 90)
            insert(word("held"), 80)
            insert(word("world"), 50)
        },
        BengaliPhoneticIndex(emptyList()),
        UserLexicon(null),
    )

    @Test
    fun `a prefix completes to whole words`() {
        val got = engine.suggest(word("hel"), previousWord = null)
        assertTrue("expected hello/help/held in $got", got.isNotEmpty())
        assertEquals(word("hello"), got.first())
        for (w in got) {
            assertEquals("'$w' is not whole letters", w.length, 2 * w.codePointCount(0, w.length))
            var at = 0
            while (at < w.length) {
                val cp = w.codePointAt(at)
                assertTrue("'$w' holds $cp outside Warang Citi", cp in 0x118C0..0x118D9)
                at += Character.charCount(cp)
            }
        }
    }

    @Test
    fun `a typo one letter off still finds the word`() {
        // h-e-l-o for hello: a deletion the fuzzy walk has to bridge across a
        // pair boundary.
        val got = engine.suggest(word("helo"), previousWord = null)
        assertTrue("expected hello in $got", word("hello") in got)
    }
}
