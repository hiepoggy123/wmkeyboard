package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The octopus (discussion #102) floats a word over the key that would type its
 * next character, so it needs to be able to answer "which key writes this?" for
 * any character a word can contain. [LayoutSet.keyAnchors] is that answer, and
 * the characters it gets wrong are the ones whose words silently never appear —
 * or, worse, appear over the wrong key.
 */
class OctopusKeyAnchorsTest {

    private val ngaa = 0x118C0
    private val ngaaLabel = String(Character.toChars(ngaa))

    private fun setOf(vararg rows: List<Key>) = LayoutSet(
        KeyboardLayout(name = "test", rows = rows.toList()),
        KeyboardLayout(name = "symbols", rows = emptyList()),
        KeyboardLayout(name = "symbols-shifted", rows = emptyList()),
    )

    private fun anchors(vararg rows: List<Key>, longPress: Boolean = false) =
        setOf(*rows).keyAnchors(longPress)

    @Test
    fun `a plain letter key answers for itself`() {
        val map = anchors(listOf(Key("q"), Key("w")))
        assertEquals('q'.code, map['q'.code])
        assertEquals('w'.code, map['w'.code])
    }

    @Test
    fun `a shifted character answers to the key that shows it`() {
        // QWERTZ's u/ü: a word continuing with ü floats over u, because that is
        // the key you press to write it.
        val map = anchors(listOf(Key("u", shiftLabel = "ü")))
        assertEquals('u'.code, map['ü'.code])
    }

    @Test
    fun `a long-press character answers only when long presses are allowed`() {
        val row = listOf(Key("e", longPress = listOf("é")))
        assertNull(
            "off by default: a press of e does not write é",
            anchors(row)['é'.code],
        )
        assertEquals('e'.code, anchors(row, longPress = true)['é'.code])
    }

    @Test
    fun `every letter of an ambiguous key answers to the one key`() {
        // A T9 pad: a word continuing with any of a, b or c floats over the one
        // key, and there is one word over it rather than three.
        val map = anchors(listOf(Key("ABC", output = "a", letters = "abc"), Key("DEF", output = "d", letters = "def")))
        assertEquals('a'.code, map['a'.code])
        assertEquals('a'.code, map['b'.code])
        assertEquals('a'.code, map['c'.code])
        assertEquals('d'.code, map['e'.code])
    }

    @Test
    fun `a letter outside the BMP is one key, not two surrogate halves`() {
        val map = anchors(listOf(Key(ngaaLabel), Key("a")))
        assertEquals(ngaa, map[ngaa])
        val pair = Character.toChars(ngaa)
        assertNull("the high surrogate is not a character any word contains", map[pair[0].code])
        assertNull(map[pair[1].code])
    }

    @Test
    fun `a nukta letter answers under both of its spellings`() {
        // Written by code point rather than as a literal: DA + NUKTA and the
        // precomposed RRA are the same letter, a word list may hold either, and
        // a source file silently stores whichever form it was saved in — so a
        // literal here would compare a spelling against itself and pass while
        // proving nothing.
        val da = 0x09A1
        val nukta = 0x09BC
        val rra = 0x09DC
        val label = String(Character.toChars(da)) + String(Character.toChars(nukta))
        val map = anchors(listOf(Key(label), Key("a")))
        val anchor = map[da]
        assertEquals("the key's own decomposed spelling", da, anchor)
        assertEquals("the nukta mark reaches the same key", anchor, map[nukta])
        assertEquals("and so does the precomposed letter", anchor, map[rra])
    }

    @Test
    fun `the key that shows a character beats the key that hides it`() {
        // `a` is `q`'s long-press alternate and its own key's base label. The
        // word floats over the key that draws it.
        val map = anchors(
            listOf(Key("q", longPress = listOf("a")), Key("a")),
            longPress = true,
        )
        assertEquals('a'.code, map['a'.code])
    }

    @Test
    fun `keys that are not text keys answer for nothing`() {
        val map = anchors(listOf(Key(" ", action = KeyAction.Space), Key("a")))
        assertEquals(mapOf('a'.code to 'a'.code), map)
    }
}
