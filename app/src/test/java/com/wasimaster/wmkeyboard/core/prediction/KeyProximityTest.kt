package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayerSpec
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.settings.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proximity used to be five hand-written tables of row strings. They are kept
 * here verbatim as the oracle: deriving the same adjacency from the grids is
 * only safe if it reproduces them exactly, and asserting that over every pair of
 * characters turns "I checked it by eye" into something the build enforces.
 *
 * When a built-in layout is deliberately changed, the matching literal below has
 * to change with it — that is the point, not an inconvenience.
 */
class KeyProximityTest {

    private val oldTables = mapOf(
        InputMode.ENGLISH to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        InputMode.AZERTY to listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"),
        InputMode.DVORAK to listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz"),
        InputMode.GERMAN to listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),
        InputMode.SPANISH to listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
    )

    /** Reimplements the old constructor input so the two can be compared. */
    private fun reference(rows: List<String>): KeyProximity =
        KeyProximity.forLayout(
            LayoutSpec(id = "reference", name = "reference", proximityRows = rows),
        )

    @Test
    fun `derived adjacency matches the old tables for every character pair`() {
        for ((mode, rows) in oldTables) {
            val expected = reference(rows)
            val actual = KeyProximity.forLayout(BuiltInLayouts.forMode(mode))
            val chars = rows.joinToString("").toSet()

            for (a in chars) {
                for (b in chars) {
                    assertEquals(
                        "$mode: adjacency of '$a' and '$b' changed",
                        expected.areAdjacent(a, b),
                        actual.areAdjacent(a, b),
                    )
                }
            }
        }
    }

    @Test
    fun `the letter rows project exactly as the old tables spelled them`() {
        for ((mode, rows) in oldTables) {
            val proximity = KeyProximity.forLayout(BuiltInLayouts.forMode(mode))
            // Every character the table names has to be known to the derived map,
            // which catches a row being dropped or shortened rather than merely
            // reordered.
            for (c in rows.joinToString("")) {
                assertTrue(
                    "$mode: '$c' is missing from the derived proximity",
                    rows.joinToString("").any { other -> other != c && proximity.areAdjacent(c, other) },
                )
            }
        }
    }

    @Test
    fun `avro borrows the qwerty grid so it keeps qwerty proximity`() {
        val avro = KeyProximity.forLayout(BuiltInLayouts.AVRO)
        assertTrue(avro.areAdjacent('q', 'w'))
        assertTrue(avro.areAdjacent('a', 's'))
        assertFalse(avro.areAdjacent('q', 'p'))
    }

    @Test
    fun `french borrows the azerty grid`() {
        val french = KeyProximity.forLayout(BuiltInLayouts.FRENCH)
        assertTrue("'a' and 'z' lead the AZERTY top row", french.areAdjacent('a', 'z'))
        assertTrue("'q' sits directly under 'a'", french.areAdjacent('a', 'q'))
        assertFalse("opposite ends of the top row are not neighbours", french.areAdjacent('a', 'p'))
    }

    @Test
    fun `the spacebar row is excluded so punctuation is not made a neighbour`() {
        // The bottom row of every built-in holds "," and "." as text keys. If it
        // were projected they would become a fourth row and neighbours of the
        // letters above, which is exactly the bug the skip exists to prevent.
        val qwerty = KeyProximity.forLayout(BuiltInLayouts.QWERTY)
        assertFalse("',' must not be adjacent to a letter", qwerty.areAdjacent(',', 'z'))
        assertFalse("'.' must not be adjacent to a letter", qwerty.areAdjacent('.', 'm'))
    }

    @Test
    fun `dvorak keeps its punctuation keys so the columns do not shift`() {
        val dvorak = KeyProximity.forLayout(BuiltInLayouts.DVORAK)
        // ' , . lead Dvorak's top row. Dropping them as non-letters would move
        // p y f g c r l three columns left and break every column relation.
        assertTrue(dvorak.areAdjacent('\'', ','))
        assertTrue(dvorak.areAdjacent(',', '.'))
        assertTrue("'a' sits under '\'' in the real grid", dvorak.areAdjacent('a', '\''))
    }

    @Test
    fun `a custom grid gets its own proximity rather than borrowing qwerty`() {
        val custom = LayoutSpec(
            id = "custom_1",
            name = "Mine",
            layers = mapOf(
                LayoutLayer.LETTERS.key to LayerSpec(
                    listOf(
                        listOf(Key("z"), Key("y"), Key("x")),
                        listOf(Key("a"), Key("b"), Key("c")),
                        listOf(Key(" ", action = KeyAction.Space)),
                    ),
                ),
            ),
        )
        val proximity = KeyProximity.forLayout(custom)
        assertTrue(proximity.areAdjacent('z', 'y'))
        assertTrue(proximity.areAdjacent('z', 'b'))
        assertFalse("'q' and 'w' are not in this layout at all", proximity.areAdjacent('q', 'w'))
    }

    @Test
    fun `an explicit proximity override wins over the grid`() {
        val custom = LayoutSpec(
            id = "custom_1",
            name = "Mine",
            layers = mapOf(
                LayoutLayer.LETTERS.key to LayerSpec(listOf(listOf(Key("a"), Key("b")))),
            ),
            proximityRows = listOf("xy", "zw"),
        )
        val proximity = KeyProximity.forLayout(custom)
        assertTrue(proximity.areAdjacent('x', 'y'))
        assertFalse(proximity.areAdjacent('a', 'b'))
    }
}
