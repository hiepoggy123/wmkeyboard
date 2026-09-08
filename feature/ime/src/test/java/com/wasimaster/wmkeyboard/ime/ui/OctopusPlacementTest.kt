package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.wasimaster.wmkeyboard.core.prediction.OctopusKind
import com.wasimaster.wmkeyboard.core.prediction.OctopusWord
import com.wasimaster.wmkeyboard.core.settings.OctopusPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where each floating word lands (discussion #102). The geometry is pure so the
 * awkward cases — the top row hanging off the board, a word too wide for its
 * key, two words reaching for the same space — can be checked without a
 * keyboard, a font or a frame.
 */
class OctopusPlacementTest {

    private val band = 20f

    /** Three quarters of the band clears the key; the rest overlaps it. */
    private val straddle = 0.75f
    private val gapV = 2f
    private val overhang = 10f
    private val board = Size(300f, 200f)

    /** Row 1 across the top, row 2 under it; three 100-wide keys each. */
    private val cells = mapOf(
        'q'.code to Rect(0f, 0f, 100f, 100f),
        'w'.code to Rect(100f, 0f, 200f, 100f),
        'e'.code to Rect(200f, 0f, 300f, 100f),
        'a'.code to Rect(0f, 100f, 100f, 200f),
        's'.code to Rect(100f, 100f, 200f, 200f),
        'd'.code to Rect(200f, 100f, 300f, 200f),
    )

    private fun word(key: Char, text: String, rank: Int = 0, typed: Int = 0) =
        OctopusWord(key.code, text, typed, OctopusKind.COMPLETION, rank)

    private fun slots(
        vararg words: OctopusWord,
        placement: OctopusPlacement = OctopusPlacement.FLOAT,
        bounds: Map<Int, Rect> = cells,
        /** Ten pixels a character, so a word's width is readable in the test. */
        widthOf: (String) -> Float = { it.length * 10f },
    ) = octopusSlots(
        words = words.toList(),
        bounds = bounds,
        placement = placement,
        bandHeightPx = band,
        straddle = straddle,
        gapVPx = gapV,
        maxOverhangPx = overhang,
        boardSize = board,
        widthOf = widthOf,
    )

    // ---- the three placements ----

    @Test
    fun `floating sits in the band above the key`() {
        val slot = slots(word('s', "hello")).single()
        assertEquals(100f - band * straddle, slot.area.top, 0f)
        assertTrue("and straddles its key rather than clearing it", slot.area.bottom > 100f)
        assertEquals("centred on its key", 150f, slot.area.center.x, 0f)
    }

    @Test
    fun `a reserved lane is the same band, because the lane moved the key`() {
        // STRIP does not move the words; it moves the cells, and the renderer
        // reports the moved ones. The arithmetic here must stay identical or
        // the two would disagree about where the lane is.
        assertEquals(
            slots(word('s', "hello"), placement = OctopusPlacement.FLOAT).single().area,
            slots(word('s', "hello"), placement = OctopusPlacement.STRIP).single().area,
        )
    }

    @Test
    fun `inside the key draws under the key's top edge`() {
        val slot = slots(word('s', "hello"), placement = OctopusPlacement.IN_KEY).single()
        assertEquals(100f + gapV, slot.area.top, 0f)
        assertTrue("wholly inside its key", slot.area.bottom <= 200f)
    }

    // ---- the top row ----

    @Test
    fun `a top-row word straddles the grid and is tapped on the half that is on it`() {
        val slot = slots(word('w', "hello")).single()
        assertTrue("drawn above the board, over the strip", slot.area.top < 0f)
        assertEquals("but only tapped from the board's own edge down", 0f, slot.hit.top, 0f)
        assertTrue("and there is still something to tap", slot.hit.height > 0f)
    }

    // ---- fitting ----

    @Test
    fun `a word may lean into the gaps beside its key`() {
        // 110 wide over a 100 key: inside the key's width plus one overhang.
        assertEquals(1, slots(word('s', "everything!")).size)
    }

    @Test
    fun `a word too wide even with the overhang is dropped, not cut down`() {
        // A dense board of ellipsised stubs says less than a bare one.
        assertTrue(slots(word('s', "a".repeat(13))).isEmpty())
    }

    @Test
    fun `a word on an edge key is pulled inside the board`() {
        val left = slots(word('a', "everything!")).single()
        assertEquals(0f, left.area.left, 0f)
        assertEquals("and keeps its full width", 110f, left.area.width, 0.01f)
        val right = slots(word('d', "everything!")).single()
        assertEquals(board.width, right.area.right, 0f)
    }

    // ---- collisions ----

    @Test
    fun `two words reaching for the same space keep the better one`() {
        val kept = slots(
            word('s', "everything!", rank = 1),
            word('a', "everything!", rank = 0),
        )
        assertEquals(listOf("everything!"), kept.map { it.word.word })
        assertEquals('a'.code, kept.single().word.keyCodePoint)
    }

    @Test
    fun `words that do not touch both stay`() {
        val kept = slots(word('a', "and"), word('d', "did"))
        assertEquals(2, kept.size)
    }

    @Test
    fun `words on different rows do not collide`() {
        val kept = slots(word('w', "everything!"), word('s', "everything!"))
        assertEquals(2, kept.size)
    }

    // ---- absent geometry ----

    @Test
    fun `a key that has not reported its bounds yields no slot`() {
        assertTrue(slots(word('z', "zebra")).isEmpty())
    }

    @Test
    fun `a word that measures to nothing yields no slot`() {
        assertTrue(slots(word('s', "hello"), widthOf = { 0f }).isEmpty())
    }

    @Test
    fun `no bounds at all yields nothing`() {
        assertTrue(slots(word('s', "hello"), bounds = emptyMap()).isEmpty())
    }

    // ---- the hit-test table ----

    @Test
    fun `the rect table answers by point and by key`() {
        val rects = OctopusRects()
        rects.publish(slots(word('a', "and"), word('d', "did")))
        assertEquals("and", rects.wordAt(Offset(50f, 90f))?.word)
        assertEquals("did", rects.wordFor('d'.code)?.word)
        assertNull("the gap between two words picks nothing", rects.wordAt(Offset(150f, 90f)))
        assertNull(rects.wordFor('s'.code))
    }

    @Test
    fun `a table from a previous layout answers nothing`() {
        // The trap KeyRects documents: a stale table hit-tests the finger
        // against a board that is no longer on the screen.
        val rects = OctopusRects()
        rects.publish(slots(word('a', "and")), gridToken = 1)
        assertNull(rects.wordAt(Offset(50f, 90f), gridToken = 2))
        assertNull(rects.wordFor('a'.code, gridToken = 2))
        assertEquals("and", rects.wordAt(Offset(50f, 90f), gridToken = 1)?.word)
    }
}
