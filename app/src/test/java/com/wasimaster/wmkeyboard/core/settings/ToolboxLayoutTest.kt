package com.wasimaster.wmkeyboard.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The paging arithmetic behind the toolbox's swipe-through pages, and the
 *  chevron rule the pill layout draws with. */
class ToolboxLayoutTest {

    private val tools = DefaultToolOrder.take(10)

    @Test
    fun `an exact fit is not one page too many`() {
        assertEquals(2, toolboxPageCount(10, 5))
        assertEquals(3, toolboxPageCount(11, 5))
    }

    @Test
    fun `an empty toolbox is still one page`() {
        // A pager with zero pages crashes on the first swipe.
        assertEquals(1, toolboxPageCount(0, 5))
    }

    @Test
    fun `a nonsense page size does not divide by zero`() {
        assertEquals(1, toolboxPageCount(10, 0))
        assertEquals(tools, toolboxPage(tools, 3, 0))
    }

    @Test
    fun `pages slice in order and the last one is short`() {
        assertEquals(tools.subList(0, 4), toolboxPage(tools, 0, 4))
        assertEquals(tools.subList(4, 8), toolboxPage(tools, 1, 4))
        assertEquals(tools.subList(8, 10), toolboxPage(tools, 2, 4))
    }

    @Test
    fun `a page past the end is empty, not an exception`() {
        // The page count follows a live list: pinning a tool away can leave the
        // pager on a page that no longer has anything in it.
        assertTrue(toolboxPage(tools, 9, 4).isEmpty())
        assertTrue(toolboxPage(tools, -1, 4).isNotEmpty())
    }

    @Test
    fun `panels and activities get a chevron, toggles and one-shots do not`() {
        assertTrue(toolOpensScreen(ToolbarTool.THEMES))
        assertTrue(toolOpensScreen(ToolbarTool.CLIPBOARD))
        // Not a panel, but it does leave the keyboard for an activity.
        assertTrue(toolOpensScreen(ToolbarTool.SETTINGS))
        assertTrue(toolOpensScreen(ToolbarTool.DOC_SCAN))

        assertFalse(toolOpensScreen(ToolbarTool.FLASHLIGHT))
        assertFalse(toolOpensScreen(ToolbarTool.UNDO))
        assertFalse(toolOpensScreen(ToolbarTool.INCOGNITO))
        assertFalse(toolOpensScreen(ToolbarTool.HIDE_KEYBOARD))
        CursorTools.forEach { assertFalse(it.name, toolOpensScreen(it)) }
    }
}
