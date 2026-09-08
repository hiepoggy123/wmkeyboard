package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The octopus (discussion #102) end to end through the engine: which words come
 * back for a buffer, and which key each is hung off.
 *
 * [OctopusAssignmentTest] pins the assignment on candidates handed to it; this
 * pins that the engine actually produces those candidates, sparse and dense,
 * which is the half a pure test cannot see.
 */
class OctopusEngineTest {

    /** Every letter its own key, which is every Latin board. */
    private val everyLetter: (Int) -> Int = { it }

    private fun engine(): SuggestionEngine {
        val dictionary = Trie().apply {
            insert("hello", 100)
            insert("help", 90)
            insert("held", 80)
            insert("hellish", 10)
            insert("heavy", 70)
            insert("her", 60)
            insert("world", 50)
            insert("wonder", 40)
        }
        return SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
    }

    private fun words(
        typed: String,
        limit: Int = 4,
        dense: Boolean = false,
        kinds: Set<OctopusKind> = OctopusKind.entries.toSet(),
        pool: List<String> = emptyList(),
        engine: SuggestionEngine = engine(),
    ) = engine.octopusWords(
        composing = typed,
        previousWord = null,
        limit = limit,
        kinds = kinds,
        dense = dense,
        pool = pool,
        keyOf = everyLetter,
    )

    /** What the strip would show for this buffer, which is the octopus's pool. */
    private fun strip(engine: SuggestionEngine, typed: String) =
        engine.suggest(typed, previousWord = null)

    @Test
    fun `completions hang off the letters that reach them`() {
        val floated = words("hel").associate { it.keyCodePoint.toChar() to it.word }
        assertEquals("hello", floated['l'])
        assertEquals("help", floated['p'])
        assertEquals("held", floated['d'])
    }

    @Test
    fun `the drawn head is what has already been typed`() {
        val hello = words("hel").single { it.word == "hello" }
        assertEquals(3, hello.typedChars)
        assertEquals(OctopusKind.COMPLETION, hello.kind)
    }

    @Test
    fun `dense reaches every key the prefix can be carried on with`() {
        // Sparse is a few good words; dense is "fill the board". After "he"
        // this dictionary can be carried on with l, a or r, and dense finds all
        // three where a sparse board of two shows two.
        val sparse = words("he", limit = 2).map { it.keyCodePoint }.toSet()
        val dense = words("he", limit = 26, dense = true).map { it.keyCodePoint }.toSet()
        assertEquals(2, sparse.size)
        assertEquals(setOf('l'.code, 'a'.code, 'r'.code), dense)
        assertTrue("and it keeps everything sparse found", dense.containsAll(sparse))
    }

    @Test
    fun `dense still gives one word per key`() {
        val floated = words("he", limit = 26, dense = true)
        assertEquals(floated.map { it.keyCodePoint }.distinct().size, floated.size)
        assertEquals(floated.map { it.word.lowercase() }.distinct().size, floated.size)
    }

    @Test
    fun `a single letter floats nothing`() {
        // After one letter the completions are the unigram list and every key
        // would light up with a word that says nothing about what is meant.
        assertTrue(words("h").isEmpty())
    }

    @Test
    fun `an empty buffer with no previous word floats nothing`() {
        assertTrue(words("").isEmpty())
    }

    @Test
    fun `turning completions off leaves the keys bare`() {
        assertTrue(words("hel", kinds = setOf(OctopusKind.NEXT_WORD)).isEmpty())
    }

    @Test
    fun `the density limit is honoured`() {
        assertTrue(words("hel", limit = 2).size <= 2)
    }

    @Test
    fun `the keys say what the strip says`() {
        // The bug this pins: the octopus used to rank off the raw fuzzy walk,
        // which is only the engine's first half — no context boosts, no
        // personal ranks, no contacts, no rerank. The board then disagreed with
        // the strip beside it, and the strip was the one that was right.
        val e = engine()
        val pool = strip(e, "hel")
        val floated = words("hel", limit = 26, pool = pool, engine = e).map { it.word }
        // Every strip word reaches a key, except where two of them want the
        // same one: "hello" and "hellish" both carry on with an l, and a key
        // can only say one word. The strip's order decides which.
        assertEquals(listOf("hello", "help", "held", "her", "hellish"), pool)
        assertTrue(floated.containsAll(listOf("hello", "help", "held", "her")))
        assertFalse(
            "hellish wants the l that hello is already on",
            floated.contains("hellish"),
        )
    }

    @Test
    fun `a strip word keeps its key against anything the walk finds`() {
        val e = engine()
        // "hellish" is what the walk would put on the l; the strip's own answer
        // for that key is "hello", and the strip wins.
        val floated = words("hel", limit = 26, pool = listOf("hello"), engine = e)
        assertEquals("hello", floated.single { it.keyCodePoint == 'l'.code }.word)
    }

    @Test
    fun `the walk still fills keys the strip had no room for`() {
        val e = engine()
        val floated = words("hel", limit = 26, pool = listOf("hello"), engine = e)
        assertTrue(
            "a strip of one word still leaves p and d to be answered",
            floated.map { it.keyCodePoint }.containsAll(listOf('p'.code, 'd'.code)),
        )
    }

    @Test
    fun `the word floated is the word that would be committed`() {
        // Cased the way the strip cases it, so the two-tone split lands on the
        // right glyph and the flick writes what the eye read.
        val floated = words("Hel", pool = listOf("hello")).single { it.word.lowercase() == "hello" }
        assertEquals("Hello", floated.word)
        assertEquals(3, floated.typedChars)
    }
}
