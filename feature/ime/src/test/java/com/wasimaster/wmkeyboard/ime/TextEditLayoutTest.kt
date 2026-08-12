package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.spanSlots
import com.wasimaster.wmkeyboard.core.settings.DefaultTextEditLayout
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditKeySpan
import com.wasimaster.wmkeyboard.core.settings.MaxTextEditKeyWidth
import com.wasimaster.wmkeyboard.core.settings.TextEditAction
import com.wasimaster.wmkeyboard.core.settings.TextEditKey
import com.wasimaster.wmkeyboard.core.settings.TextEditLayout
import com.wasimaster.wmkeyboard.core.settings.TextEditLayoutCodec
import com.wasimaster.wmkeyboard.core.settings.repeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editable text-editing panel: its stored form, its repairs, and the geometry
 * the panel draws it with.
 *
 * The panel used to be hand-built Compose, so the thing worth pinning is that the
 * default layout is the *same* arrangement: four rows, the tall arrows either side
 * of the d-pad, and every row landing on the same grid width.
 */
class TextEditLayoutTest {

    @Test
    fun `the default layout keeps the shipped arrangement`() {
        val rows = DefaultTextEditLayout.rows
        assertEquals(4, rows.size)
        assertEquals(
            listOf(
                TextEditAction.LEFT, TextEditAction.UP,
                TextEditAction.RIGHT, TextEditAction.SELECT_ALL,
            ),
            rows[0].map { it.action },
        )
        // The tall arrows are what needed a span in the first place.
        assertEquals(3, rows[0][0].rowSpan)
        assertEquals(3, rows[0][2].rowSpan)
        assertEquals(listOf(TextEditAction.SELECT, TextEditAction.COPY), rows[1].map { it.action })
        assertEquals(listOf(TextEditAction.DOWN, TextEditAction.PASTE), rows[2].map { it.action })
        assertEquals(
            listOf(TextEditAction.HOME, TextEditAction.END, TextEditAction.BACKSPACE),
            rows[3].map { it.action },
        )
    }

    /**
     * Every row has to measure the same width once the columns held over it are
     * counted, or the rows would be centred against different grids and the tall
     * arrows would not line up with the keys beside them.
     */
    @Test
    fun `every row of the default layout lands on the same grid width`() {
        val grid = DefaultTextEditLayout.gridWeight
        assertEquals(4.4f, grid, 0.001f)
        for (r in DefaultTextEditLayout.rows.indices) {
            assertEquals("row $r", grid, DefaultTextEditLayout.rowWidth(r), 0.011f)
        }
    }

    /**
     * The geometry the panel places with, borrowed from the key layouts: the two
     * middle keys of rows 2 and 3 have to flow *around* the columns the arrows
     * hold, which is the whole reason the panel needs `spanSlots` rather than a
     * plain Row per row.
     */
    @Test
    fun `the middle rows flow around the tall arrows`() {
        val slots = spanSlots(
            DefaultTextEditLayout.rows.map { row ->
                row.map { Key(label = "", width = it.width, rowSpan = it.rowSpan) }
            },
            DefaultTextEditLayout.gridWeight,
        )
        val left = slots.first { it.row == 0 && it.col == 0 }
        val up = slots.first { it.row == 0 && it.col == 1 }
        val select = slots.first { it.row == 1 && it.col == 0 }
        val copy = slots.first { it.row == 1 && it.col == 1 }
        // Select sits under Up, not at the left edge where the arrow is standing.
        assertEquals(up.x, select.x, 0.001f)
        assertEquals(0f, left.x, 0.001f)
        // And the clipboard column clears the right-hand arrow.
        assertTrue("copy should clear the right arrow", copy.x > select.x)
    }

    @Test
    fun `round trips a layout`() {
        val layout = TextEditLayout(
            rows = listOf(
                listOf(TextEditKey(TextEditAction.HOME, longPress = TextEditAction.PAGE_UP, width = 2f)),
                listOf(TextEditKey(TextEditAction.LEFT, rowSpan = 1)),
            ),
            rowHeights = listOf(1.5f, 1f),
        )
        assertEquals(layout, TextEditLayoutCodec.decode(TextEditLayoutCodec.encode(layout)))
    }

    @Test
    fun `nothing stored means the shipped layout`() {
        assertNull(TextEditLayoutCodec.decode(null))
        assertNull(TextEditLayoutCodec.decode(""))
        assertNull(TextEditLayoutCodec.decode("{\"rows\":[]}"))
        assertNull(TextEditLayoutCodec.decode("not json at all"))
    }

    /** A file from a hand edit or a downgrade is repaired, never rejected. */
    @Test
    fun `absurd numbers are clamped rather than refused`() {
        val repaired = TextEditLayoutCodec.repair(
            TextEditLayout(
                rows = listOf(
                    listOf(
                        TextEditKey(TextEditAction.LEFT, width = 99f, rowSpan = 99),
                        TextEditKey(TextEditAction.UP, width = 0f),
                        TextEditKey(TextEditAction.DOWN, width = Float.NaN),
                    ),
                ),
                rowHeights = listOf(99f),
            ),
        )
        assertNotNull(repaired)
        val row = repaired!!.rows[0]
        assertEquals(MaxTextEditKeyWidth, row[0].width, 0f)
        assertEquals(MaxTextEditKeySpan, row[0].rowSpan)
        assertEquals(1f, row[1].width, 0f)
        assertEquals(1f, row[2].width, 0f)
        assertEquals(2.5f, repaired.rowHeight(0), 0f)
    }

    /**
     * A hold on a repeating key can never fire, so the repair drops it: the stored
     * layout then says what the panel actually does, which is what the editor
     * shows the user.
     */
    @Test
    fun `a hold on a repeating key is dropped`() {
        val repaired = TextEditLayoutCodec.repair(
            TextEditLayout(
                rows = listOf(
                    listOf(
                        TextEditKey(TextEditAction.LEFT, longPress = TextEditAction.COPY),
                        TextEditKey(TextEditAction.HOME, longPress = TextEditAction.PAGE_UP),
                        TextEditKey(TextEditAction.END, longPress = TextEditAction.END),
                    ),
                ),
            ),
        )!!
        assertNull("a repeating key keeps no hold", repaired.rows[0][0].longPress)
        assertEquals(TextEditAction.PAGE_UP, repaired.rows[0][1].longPress)
        assertNull("holding to do the same thing is a slow tap", repaired.rows[0][2].longPress)
    }

    /** Which actions repeat, since that is what decides who gets a hold. */
    @Test
    fun `the moves repeat and the jumps do not`() {
        for (action in listOf(
            TextEditAction.LEFT, TextEditAction.RIGHT, TextEditAction.UP, TextEditAction.DOWN,
            TextEditAction.WORD_LEFT, TextEditAction.WORD_RIGHT,
            TextEditAction.PAGE_UP, TextEditAction.PAGE_DOWN, TextEditAction.BACKSPACE,
        )) {
            assertTrue("$action should repeat", action.repeats)
        }
        for (action in listOf(
            TextEditAction.HOME, TextEditAction.END, TextEditAction.SELECT,
            TextEditAction.SELECT_ALL, TextEditAction.SELECT_WORD, TextEditAction.SELECT_LINE,
            TextEditAction.COPY, TextEditAction.PASTE,
        )) {
            assertFalse("$action should not repeat", action.repeats)
        }
    }

    /** The shipped layout gives its holds to keys that can run them. */
    @Test
    fun `the default holds sit on keys that do not repeat`() {
        val held = DefaultTextEditLayout.rows.flatten().filter { it.longPress != null }
        assertTrue("the shipped layout should demonstrate the feature", held.isNotEmpty())
        for (key in held) {
            assertFalse("${key.action} repeats, so its hold would never fire", key.action.repeats)
        }
    }
}
