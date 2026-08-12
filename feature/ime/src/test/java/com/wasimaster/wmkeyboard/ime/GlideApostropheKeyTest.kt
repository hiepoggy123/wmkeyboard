package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.Layouts
import com.wasimaster.wmkeyboard.core.settings.GlideApostropheKey
import com.wasimaster.wmkeyboard.core.settings.sourceChar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The apostrophe a glide can draw: `LayoutSet.glideKeys` has to put `'` on the one
 * key the user picked, and nowhere else.
 *
 * The premise is worth pinning, because it is the reason the feature exists at
 * all: [keySpelling] admits letters and combining marks only, so no punctuation
 * key is on the glide grid — not the comma, not the full stop, and not the
 * apostrophe QWERTY hides behind `c`'s press and hold. Until a key is chosen here
 * a contraction cannot be drawn at all, whatever the word list holds.
 */
class GlideApostropheKeyTest {

    private val set = LayoutSet(Layouts.QWERTY, Layouts.SYMBOLS, Layouts.SYMBOLS_SHIFTED)

    /** Key width, so pixel centres below divide back to whole grid units. */
    private val keyWidth = 10f

    /**
     * Every text key's centre, punctuation included — the same map the keyboard
     * builds, which reports the punctuation keys precisely so this feature can
     * find them.
     */
    private val centers: Map<Char, Pair<Float, Float>> = buildMap {
        for ((rowIndex, row) in set.letters.rows.withIndex()) {
            var column = 0
            for (key in row) {
                if (key.action == KeyAction.Text) {
                    (key.output ?: key.label).firstOrNull()?.let {
                        putIfAbsent(it.lowercaseChar(), (column * keyWidth) to (rowIndex * keyWidth))
                    }
                }
                column++
            }
        }
    }

    private fun grid(apostropheCenter: Pair<Float, Float>? = null): GlideKeyMap =
        GlideKeyMap.of(
            set.glideKeys(apostropheCenter = apostropheCenter) { centers[it] },
            keyWidth = keyWidth,
        )

    @Test
    fun `without a chosen key no punctuation is on the glide grid`() {
        val keys = grid()
        for (ch in listOf('\'', '’', ',', '.')) {
            assertFalse("$ch should not be glidable by default", keys.knows(ch))
        }
        // The letters are, or there would be nothing to prove.
        assertTrue(keys.knows('c'))
    }

    @Test
    fun `the chosen key is where the apostrophe lands`() {
        val (commaX, commaY) = centers.getValue(',')
        val keys = grid(commaX to commaY)
        val index = keys.keyIndex('\'')
        assertTrue("the apostrophe should be on the grid now", index >= 0)
        assertEquals(commaX / keyWidth, keys.keyX[index], 0f)
        assertEquals(commaY / keyWidth, keys.keyY[index], 0f)
    }

    /** A word list may spell a contraction either way, and one finger draws both. */
    @Test
    fun `both apostrophe characters land on the chosen key`() {
        val (x, y) = centers.getValue('.')
        val keys = grid(x to y)
        assertEquals(keys.keyIndex('\''), keys.keyIndex('’'))
        assertEquals(x / keyWidth, keys.keyX[keys.keyIndex('’')], 0f)
    }

    /** The letters are untouched: this adds a character, it moves none. */
    @Test
    fun `choosing a key leaves every letter where it was`() {
        val before = grid()
        val after = grid(centers.getValue(','))
        for (ch in 'a'..'z') {
            val i = before.keyIndex(ch)
            val j = after.keyIndex(ch)
            assertTrue("$ch fell off the grid", i >= 0 && j >= 0)
            assertEquals("$ch moved", before.keyX[i], after.keyX[j], 0f)
            assertEquals("$ch moved", before.keyY[i], after.keyY[j], 0f)
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
     * Shipped QWERTY has the two keys the default choices point at. Without them
     * the setting would offer a key that is not on the board.
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
