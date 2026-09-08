package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.Layouts
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.layout.letterSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the keyboard publishes about a grid whose keys carry several letters
 * (discussion #103): the alphabet it can write, the glide grid a finger is
 * decoded against, and the flag that tells the composing path to read its
 * keystrokes as key sets rather than as characters.
 *
 * All three used to come out empty for such a board, and from one cause:
 * [keySpelling] rejects a label like `ABC` at its second character, so a key
 * anchored by its label was not on any grid at all. [glideAnchor] is the fix
 * and this is where both sides of it are pinned.
 */
class AmbiguousGridPublicationTest {

    private val keyWidth = 10f

    private fun setOf(id: String): LayoutSet {
        val spec = BuiltInLayouts.byId(id)!!
        return LayoutSet(
            spec.compile(LayoutLayer.LETTERS),
            Layouts.SYMBOLS,
            Layouts.SYMBOLS_SHIFTED,
        )
    }

    private val t9 = setOf(BuiltInLayouts.T9_ID)
    private val compact = setOf(BuiltInLayouts.COMPACT_ID)
    private val qwerty = LayoutSet(Layouts.QWERTY, Layouts.SYMBOLS, Layouts.SYMBOLS_SHIFTED)

    /**
     * Key centres the way the renderer reports them: one entry per key, filed
     * under [glideAnchor] — which is the whole point, since that is the code
     * point `glideKeys` will look the key up by.
     */
    private fun centersOf(set: LayoutSet): Map<Int, Pair<Float, Float>> = buildMap {
        for ((rowIndex, row) in set.letters.rows.withIndex()) {
            var column = 0
            for (key in row) {
                key.glideAnchor()?.let {
                    putIfAbsent(it, (column * keyWidth) to (rowIndex * keyWidth))
                }
                column++
            }
        }
    }

    private fun gridOf(set: LayoutSet): GlideKeyMap {
        val centers = centersOf(set)
        return GlideKeyMap.of(set.glideKeys { centers[it] }, keyWidth = keyWidth)
    }

    @Test
    fun `an ambiguous key is anchored by its first letter, not its label`() {
        val abc = t9.letters.rows[0].first { it.letters == "abc" }
        assertEquals('a'.code, abc.glideAnchor())
        // The label is what broke: it is not a spelling.
        assertNull(keySpelling(abc.label))
    }

    @Test
    fun `an ordinary key is anchored exactly as it always was`() {
        for (row in qwerty.letters.rows) {
            for (key in row.filter { it.action == KeyAction.Text }) {
                assertEquals(
                    "anchor moved for '${key.label}'",
                    keySpelling(key.label)?.first(),
                    key.glideAnchor(),
                )
            }
        }
    }

    @Test
    fun `the alphabet holds every letter the board can write`() {
        for (set in listOf(t9, compact)) {
            for (ch in 'a'..'z') {
                assertTrue("'$ch' missing from the alphabet", ch.code in set.letterAlphabet)
            }
        }
    }

    @Test
    fun `every letter reaches the glide grid at its own key's centre`() {
        for (set in listOf(t9, compact)) {
            val grid = gridOf(set)
            for (ch in 'a'..'z') assertTrue("'$ch' is not glidable", grid.knows(ch))
        }
        // And they share the key, rather than each landing somewhere of its own.
        val grid = gridOf(t9)
        val key = grid.keyIndex('p'.code)
        for (ch in listOf('q', 'r', 's')) assertEquals(key, grid.keyIndex(ch.code))
    }

    @Test
    fun `the ambiguity flag follows the letters grid`() {
        assertTrue(t9.ambiguousKeys)
        assertTrue(compact.ambiguousKeys)
        assertFalse(qwerty.ambiguousKeys)
    }

    @Test
    fun `the compact board's single-letter keys are not ambiguous`() {
        // `l` and `m` end their rows alone, and a key of one letter decodes as
        // the plain character it is.
        val single = compact.letters.rows.flatten().filter {
            it.action == KeyAction.Text && it.letterSet().length == 1
        }
        assertEquals(listOf("l", "m"), single.map { it.letterSet() })
    }
}
