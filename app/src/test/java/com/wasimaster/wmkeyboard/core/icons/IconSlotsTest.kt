package com.wasimaster.wmkeyboard.core.icons

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSlotsTest {

    @Test
    fun `every tool has a slot`() {
        for (tool in ToolbarTool.entries) {
            val slot = IconSlots.byId(IconSlots.forTool(tool))
            assertNotNull("no slot for $tool", slot)
            assertEquals(tool, slot!!.tool)
            assertEquals(IconSlotGroup.TOOL, slot.group)
        }
    }

    @Test
    fun `slot ids are unique`() {
        val ids = IconSlots.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * The overrides persist as a CSV of `slot=source` pairs, so an id carrying
     * either separator would corrupt every entry after it.
     */
    @Test
    fun `slot ids are safe in the overrides CSV`() {
        for (slot in IconSlots.all) {
            assertTrue("${slot.id} is not well formed", IconSlots.isWellFormed(slot.id))
            assertTrue(',' !in slot.id)
            assertTrue('=' !in slot.id)
        }
    }

    @Test
    fun `slot ids are safe as file names`() {
        for (slot in IconSlots.all) {
            assertTrue('/' !in slot.id)
            assertTrue('\\' !in slot.id)
            assertTrue(".." !in slot.id)
        }
    }

    /**
     * A slot the settings list cannot name is a row the user cannot recognise.
     * A slot names a string resource rather than carrying English, so this
     * checks the id is there rather than checking any particular wording —
     * which is what keeps it from breaking on every rewrite of a label.
     *
     * The tool slots are the deliberate exception: they carry no label of
     * their own and the screen titles them from their [ToolbarTool], so for
     * those the thing that must be present is the tool.
     */
    @Test
    fun `every slot can be named`() {
        for (slot in IconSlots.all) {
            if (slot.group == IconSlotGroup.TOOL) {
                assertNotNull("${slot.id} has no tool to be named after", slot.tool)
                assertNull("${slot.id} should be titled from its tool", slot.labelRes)
            } else {
                assertNotNull("${slot.id} has no label", slot.labelRes)
                assertTrue("${slot.id} label resource is unset", slot.labelRes != 0)
            }
        }
    }

    @Test
    fun `path traversal ids are rejected`() {
        assertTrue(!IconSlots.isWellFormed("../etc/passwd"))
        assertTrue(!IconSlots.isWellFormed("tool/clipboard"))
        assertTrue(!IconSlots.isWellFormed("tool\\clipboard"))
        assertTrue(!IconSlots.isWellFormed(".."))
        assertTrue(!IconSlots.isWellFormed(""))
        assertTrue(!IconSlots.isWellFormed("Tool.Clipboard"))
        assertTrue(!IconSlots.isWellFormed("a".repeat(65)))
    }

    @Test
    fun `unknown ids resolve to null`() {
        assertNull(IconSlots.byId("tool.nonexistent"))
        assertNull(IconSlots.byId(null))
        assertNull(IconSlots.byId(""))
    }

    @Test
    fun `groups partition every slot`() {
        val grouped = IconSlotGroup.entries.flatMap { IconSlots.inGroup(it) }
        assertEquals(IconSlots.all.size, grouped.size)
    }
}
