package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.Layouts
import com.wasimaster.wmkeyboard.core.settings.GlideApostropheKey
import com.wasimaster.wmkeyboard.core.settings.sourceChar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The apostrophe a glide can draw: `LayoutSet.glideKeys` has to put `'` on the one
 * key the user picked and take it off every other.
 *
 * The second half is the part worth a test. Shipped QWERTY already hides an
 * apostrophe behind `c`'s press-and-hold, so `'` was *already* on the glide grid
 * before this feature existed, sitting on a letter key three rows from the
 * punctuation. A stroke drawn through the comma would spell nothing, and a stroke
 * that merely passed over `c` would spell an apostrophe the user never asked for.
 */
class GlideApostropheKeyTest {

    /** A grid whose keys sit one unit apart, so the numbers below read plainly. */
    private val set = LayoutSet(Layouts.QWERTY, Layouts.SYMBOLS, Layouts.SYMBOLS_SHIFTED)

    /** Column-major placement by row, in pixels, at a key width of 10. */
    private val centers: Map<Char, Pair<Float, Float>> = buildMap {
        for ((rowIndex, row) in set.letters.rows.withIndex()) {
            var column = 0
            for (key in row) {
                val ch = keySpelling(key.label)?.first() ?: continue
                put(ch.lowercaseChar(), (column * 10f) to (rowIndex * 10f))
                column++
            }
        }
    }

    private fun grid(apostropheCenter: Pair<Float, Float>?): GlideKeyMap =
        GlideKeyMap.of(
            set.glideKeys(apostropheCenter = apostropheCenter) { centers[it] },
            keyWidth = 10f,
        )

    /** The premise: the shipped grid reaches `'` through a letter key. */
    @Test
    fun `without the setting the apostrophe rides the c key`() {
        val keys = grid(null)
        val apostrophe = keys.keyIndex('\'')
        assertTrue("shipped QWERTY should still reach an apostrophe at all", apostrophe >= 0)
        assertEquals(
            "the long-press alternate on c is where it lives today",
            keys.keyIndex('c'),
            apostrophe,
        )
    }

    @Test
    fun `the chosen key takes the claim on the apostrophe`() {
        val comma = centers.getValue(',')
        val keys = grid(comma)
        assertEquals(keys.keyIndex(','), keys.keyIndex('\''))
        assertNotEquals(
            "c must stop standing for an apostrophe once a key is chosen",
            keys.keyIndex('c'),
            keys.keyIndex('\''),
        )
    }

    /** A word list may spell a contraction either way, and one finger draws both. */
    @Test
    fun `both apostrophe characters land on the chosen key`() {
        val keys = grid(centers.getValue('.'))
        assertEquals(keys.keyIndex('.'), keys.keyIndex('\''))
        assertEquals(keys.keyIndex('.'), keys.keyIndex('’'))
    }

    /** The letters themselves are untouched: this adds a character, it moves none. */
    @Test
    fun `choosing a key leaves every letter where it was`() {
        val before = grid(null)
        val after = grid(centers.getValue(','))
        for (ch in 'a'..'z') {
            assertEquals(
                "$ch moved",
                before.keyX[before.keyIndex(ch)],
                after.keyX[after.keyIndex(ch)],
                0f,
            )
        }
    }

    /** Which character each choice borrows. SPACE has none: it is found by rect. */
    @Test
    fun `each choice names the character it borrows`() {
        assertEquals(',', GlideApostropheKey.COMMA.sourceChar)
        assertEquals('.', GlideApostropheKey.PERIOD.sourceChar)
        assertEquals('\'', GlideApostropheKey.APOSTROPHE.sourceChar)
        assertEquals(null, GlideApostropheKey.OFF.sourceChar)
        assertEquals(null, GlideApostropheKey.SPACE.sourceChar)
    }

    /**
     * The shipped QWERTY has the comma and the full stop the two default choices
     * point at. Without them the setting would offer a key that is not there.
     */
    @Test
    fun `shipped qwerty has the punctuation keys the choices name`() {
        val labels = BuiltInLayouts.QWERTY.layers
            .getValue(LayoutLayer.LETTERS.key)
            .rows.flatten().map { it.output ?: it.label }
        assertTrue("no comma key", labels.contains(","))
        assertTrue("no full stop key", labels.contains("."))
    }
}
