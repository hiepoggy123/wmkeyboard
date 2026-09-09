package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The English word list has to carry the contractions themselves, not only
 * their apostrophe-less twins.
 *
 * A missing apostrophe entry does not read as a missing suggestion — it reads
 * as a wrong one. `SuggestionEngine.decideOrdinary` leaves a word alone only
 * when `inDictionaries` knows it, so an unknown "it's" is a word one deletion
 * away from a known "its" and autocorrect silently applies it; the user then
 * gets their own spelling offered back as a word to *add* to the dictionary
 * (#128). The list had zero apostrophe entries when that was reported, which
 * is what a punctuation-stripping pass over a source corpus leaves behind.
 */
class EnglishApostropheEntriesTest {

    private val entries: Map<String, Int> = listOf(
        File("dictionaries-src/en.txt"),
        File("app/dictionaries-src/en.txt"),
    ).first { it.isFile }
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val cut = line.lastIndexOf(' ')
            line.substring(0, cut).trim() to line.substring(cut + 1).toInt()
        }

    /**
     * Every spelling [Apostrophes] can hand back, from either table. The fix
     * runs ahead of autocorrect and commits its result directly, so a repair
     * landing on a word the dictionary does not know turns one wrong outcome
     * (the missing apostrophe) into another (an unknown word, offered for
     * learning and corrected away the next time it is typed).
     */
    @Test
    fun `every apostrophe fix lands on a word the list knows`() {
        val produced = Apostrophes.everyFix()
        assertTrue("Apostrophes produces nothing", produced.size > 50)
        val missing = produced.filterNot { entries.containsKey(it.lowercase()) }.sorted()
        assertEquals("not in en.txt: $missing", emptyList<String>(), missing)
    }

    /**
     * A floor, not a count: the point is that a regeneration which drops the
     * apostrophe fails here instead of on a user's keyboard.
     */
    @Test
    fun `the list carries contractions of its own`() {
        val withApostrophe = entries.keys.filter { '\'' in it }
        assertTrue("only ${withApostrophe.size} apostrophe entries", withApostrophe.size >= 80)
        for (word in listOf("it's", "don't", "i'm", "i'll", "that's", "can't", "you're")) {
            assertTrue("$word is missing", word in entries)
        }
    }

    /**
     * The apostrophe-less twins are load-bearing and must survive any cleanup
     * of the block above: the letters layer has no apostrophe key, so a glide
     * can only ever draw the bare spelling, and
     * `WMKeyboardService.restoreApostrophe` turns that back into the
     * contraction. Drop them and swiping a contraction stops working.
     */
    @Test
    fun `the glidable bare spellings stay`() {
        for (word in listOf("dont", "cant", "wont", "lets", "thats", "whats", "youre", "didnt", "im")) {
            assertTrue("$word is missing; glide cannot draw a contraction without it", word in entries)
        }
    }

    /** Case is folded before lookup, so a capital here could never match. */
    @Test
    fun `apostrophe entries are lowercase`() {
        for (word in entries.keys.filter { '\'' in it }) {
            assertEquals(word.lowercase(), word)
        }
    }

    /** U+0027, the character the layouts' apostrophe key actually types. */
    @Test
    fun `the apostrophe is the straight one`() {
        val curly = entries.keys.filter { '’' in it }
        assertEquals(emptyList<String>(), curly)
    }
}
