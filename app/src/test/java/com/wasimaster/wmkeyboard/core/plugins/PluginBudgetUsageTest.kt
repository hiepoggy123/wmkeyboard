package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plugin editor's budget meter reads [PluginBudget.usage]; these pin what it can trust. */
class PluginBudgetUsageTest {

    @Test
    fun `usage is empty before any call`() {
        assertEquals(PluginBudget.Usage.NONE, PluginBudget().usage())
    }

    @Test
    fun `usage is exact once a call ends`() {
        var now = 0L
        val budget = PluginBudget { now }
        budget.begin(PluginLimit(instructions = 10_000, wallMillis = 1_000))
        repeat(3_000) { budget.onInstruction() }
        now = 7_000_000L
        budget.end()

        val usage = budget.usage()
        assertEquals(3_000L, usage.instructions)
        assertEquals(10_000L, usage.instructionLimit)
        assertEquals(7_000_000L, usage.elapsedNanos)
        assertEquals(1_000L, usage.wallLimitMillis)
        assertFalse(usage.running)
    }

    @Test
    fun `a running call is reported to within one clock interval`() {
        val budget = PluginBudget { 0L }
        budget.begin(PluginLimit(instructions = 100_000, wallMillis = 1_000))
        repeat(5_000) { budget.onInstruction() }

        val usage = budget.usage()
        assertTrue(usage.running)
        assertTrue("sampled ${usage.instructions}", usage.instructions in (5_000L - 1_024L)..5_000L)
    }

    @Test
    fun `a call stopped for instructions counts its whole allowance`() {
        val budget = PluginBudget { 0L }
        budget.begin(PluginLimit(instructions = 2_000, wallMillis = 1_000))
        val abort = runCatching { while (true) budget.onInstruction() }.exceptionOrNull() as PluginAbort
        budget.end()

        assertEquals(PluginAbortReason.INSTRUCTIONS, abort.reason)
        assertEquals(2_000L, budget.usage().instructions)
    }

    @Test
    fun `a fresh call starts counting from zero`() {
        val budget = PluginBudget { 0L }
        budget.begin(PluginLimit(instructions = 10_000, wallMillis = 1_000))
        repeat(500) { budget.onInstruction() }
        budget.end()
        budget.begin(PluginLimit(instructions = 4_000, wallMillis = 500))
        budget.end()

        val usage = budget.usage()
        assertEquals(0L, usage.instructions)
        assertEquals(4_000L, usage.instructionLimit)
        assertEquals(500L, usage.wallLimitMillis)
    }

    @Test
    fun `usage can be read from another thread while a real script loops`() {
        val budget = PluginBudget()
        val globals = PluginSandbox.create(budget) {}
        val worker = Thread {
            budget.begin(PluginLimit(instructions = 50_000_000, wallMillis = 5_000))
            try {
                runCatching { PluginSandbox.compile(globals, "local n = 0 while true do n = n + 1 end").call() }
            } finally {
                budget.end()
            }
        }
        worker.start()

        var last = 0L
        var sawProgress = false
        val stopAt = System.currentTimeMillis() + 2_000
        while (worker.isAlive && System.currentTimeMillis() < stopAt && !sawProgress) {
            val usage = budget.usage()
            if (usage.running) {
                assertTrue("went backwards: ${usage.instructions} < $last", usage.instructions >= last)
                last = usage.instructions
                sawProgress = last > 0L
            }
            Thread.sleep(2)
        }
        budget.cancel()
        worker.join(5_000)

        assertTrue("the meter never moved", sawProgress)
        assertFalse(budget.usage().running)
    }
}
