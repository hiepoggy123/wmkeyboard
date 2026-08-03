package com.wasimaster.wmkeyboard.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompting rules, checked without a device or a Play install.
 *
 * This is the part of in-app updates worth testing: the Play API itself is a
 * thin wrapper over an IPC that only answers on a real Play install, but the
 * decision it feeds is ordinary arithmetic, and getting it wrong means either
 * nagging every user or never reaching them.
 */
class UpdatePolicyTest {

    private fun plan(
        priority: Int = 0,
        stalenessDays: Int = -1,
        immediateAllowed: Boolean = true,
        flexibleAllowed: Boolean = true,
        snoozed: Boolean = false,
        autoPrompt: Boolean = true,
    ) = UpdatePolicy.plan(
        priority = priority,
        stalenessDays = stalenessDays,
        immediateAllowed = immediateAllowed,
        flexibleAllowed = flexibleAllowed,
        snoozed = snoozed,
        autoPrompt = autoPrompt,
    )

    @Test
    fun `an ordinary fresh release is offered quietly`() {
        val result = plan(priority = 0, stalenessDays = 0)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertFalse(result.automatic)
    }

    @Test
    fun `a high priority release takes the blocking flow without asking`() {
        val result = plan(priority = UpdatePolicy.IMMEDIATE_PRIORITY)
        assertEquals(UpdateFlow.IMMEDIATE, result.flow)
        assertTrue(result.automatic)
    }

    @Test
    fun `a high priority release ignores the snooze and the setting`() {
        val result = plan(
            priority = UpdatePolicy.IMMEDIATE_PRIORITY,
            snoozed = true,
            autoPrompt = false,
        )
        assertEquals(UpdateFlow.IMMEDIATE, result.flow)
        assertTrue(result.automatic)
    }

    @Test
    fun `a very stale install takes the blocking flow whatever the priority`() {
        val result = plan(priority = 0, stalenessDays = UpdatePolicy.IMMEDIATE_STALENESS_DAYS)
        assertEquals(UpdateFlow.IMMEDIATE, result.flow)
        assertTrue(result.automatic)
    }

    @Test
    fun `a mid priority release prompts through the background flow`() {
        val result = plan(priority = UpdatePolicy.PROMPT_PRIORITY)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertTrue(result.automatic)
    }

    @Test
    fun `a week behind is enough to prompt on its own`() {
        val result = plan(priority = 0, stalenessDays = UpdatePolicy.PROMPT_STALENESS_DAYS)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertTrue(result.automatic)
    }

    @Test
    fun `a day short of the staleness threshold stays quiet`() {
        val result = plan(priority = 0, stalenessDays = UpdatePolicy.PROMPT_STALENESS_DAYS - 1)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertFalse(result.automatic)
    }

    @Test
    fun `a snoozed version is still offered but never opens a dialog`() {
        val result = plan(priority = UpdatePolicy.PROMPT_PRIORITY, snoozed = true)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertFalse(result.automatic)
    }

    @Test
    fun `turning prompts off leaves the card and removes the dialog`() {
        val result = plan(stalenessDays = UpdatePolicy.PROMPT_STALENESS_DAYS, autoPrompt = false)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertFalse(result.automatic)
    }

    @Test
    fun `the blocking flow is used when Play forbids the background one`() {
        val result = plan(priority = 0, flexibleAllowed = false)
        assertEquals(UpdateFlow.IMMEDIATE, result.flow)
        // Not urgent, so it waits to be pressed: the blocking flow takes over
        // the screen, and doing that unasked for a routine release is rude.
        assertFalse(result.automatic)
    }

    @Test
    fun `an urgent release falls back to the background flow when that is all Play allows`() {
        val result = plan(priority = UpdatePolicy.IMMEDIATE_PRIORITY, immediateAllowed = false)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertTrue(result.automatic)
    }

    @Test
    fun `an update Play permits no flow for is not offered at all`() {
        val result = plan(immediateAllowed = false, flexibleAllowed = false)
        assertNull(result.flow)
        assertFalse(result.automatic)
    }

    @Test
    fun `an unknown staleness never triggers anything by itself`() {
        // Play omits clientVersionStalenessDays for a release published hours
        // ago; -1 stands in for it and must not read as "older than any bound".
        val result = plan(priority = 0, stalenessDays = -1)
        assertEquals(UpdateFlow.FLEXIBLE, result.flow)
        assertFalse(result.automatic)
    }
}
