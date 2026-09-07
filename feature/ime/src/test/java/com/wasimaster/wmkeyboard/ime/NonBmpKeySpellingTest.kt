package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A key whose letter lies outside the BMP — every Warang Citi, Osage or Adlam
 * key — is one letter spelled as two UTF-16 units. Indexed per `Char` it was
 * nothing: a lone surrogate is not a letter, so [keySpelling] dropped the key,
 * [LayoutSet.letterAlphabet] never held it, the coverage gate read zero and
 * glide silently stayed off for the whole script while typing worked. These
 * pin the code-point path end to end, from the label to the decoder's grid.
 */
class NonBmpKeySpellingTest {

    /** WARANG CITI SMALL LETTER NGAA and its capital, as the Ho layout writes them. */
    private val ngaa = 0x118C0
    private val capitalNgaa = 0x118A0
    private val ngaaLabel = String(Character.toChars(ngaa))
    private val capitalLabel = String(Character.toChars(capitalNgaa))

    @Test
    fun `a letter outside the BMP spells as one code point`() {
        assertEquals(listOf(ngaa), keySpelling(ngaaLabel))
        // Lowercased, like every other key: the capital spells the small.
        assertEquals(listOf(ngaa), keySpelling(capitalLabel))
    }

    @Test
    fun `a lone surrogate is not a letter key`() {
        val pair = Character.toChars(ngaa)
        assertNull(keySpelling(pair[0].toString()))
        assertNull(keySpelling(pair[1].toString()))
    }

    @Test
    fun `the spelling bound counts letters, not units`() {
        // Four code points is the most a key may spell; two non-BMP letters are
        // four units but two letters, and a word-long label is still refused.
        val combining = "́"
        assertEquals(listOf(ngaa, 0x301, 0x301, 0x301), keySpelling(ngaaLabel + combining.repeat(3)))
        assertNull(keySpelling(ngaaLabel + combining.repeat(4)))
        assertNull(keySpelling(ngaaLabel.repeat(2)))
    }

    private val grid = KeyboardLayout(
        name = "warang",
        rows = listOf(
            listOf(
                Key(ngaaLabel, shiftLabel = capitalLabel),
                Key(String(Character.toChars(ngaa + 1)), longPress = listOf(String(Character.toChars(ngaa + 2)))),
                Key("a"),
                Key(" ", action = KeyAction.Space),
            ),
        ),
    )
    private val set = LayoutSet(grid, grid, grid)

    @Test
    fun `the alphabet holds the letters outside the BMP`() {
        assertEquals(setOf(ngaa, ngaa + 1, ngaa + 2, 'a'.code), set.letterAlphabet)
    }

    @Test
    fun `glide keys carry the letter, its shift and its long press at one centre`() {
        val keys = set.glideKeys { codePoint -> if (codePoint == ngaa) 5f to 7f else null }
        val placed = keys.filter { it.x == 5f && it.y == 7f }.map { it.codePoint }
        // The base label and its shifted form both fold to the small letter.
        assertEquals(listOf(ngaa, ngaa), placed)

        val map = GlideKeyMap.of(
            set.glideKeys { codePoint ->
                when (codePoint) {
                    ngaa -> 0f to 0f
                    ngaa + 1 -> 10f to 0f
                    'a'.code -> 20f to 0f
                    else -> null
                }
            },
            keyWidth = 10f,
        )
        assertTrue(map.knows(ngaa))
        assertTrue("the shifted capital resolves to the same key", map.keyIndex(capitalNgaa) == map.keyIndex(ngaa))
        assertTrue("the long press rides its key", map.keyIndex(ngaa + 2) == map.keyIndex(ngaa + 1))
        assertEquals(3, map.keyCount)
    }
}
