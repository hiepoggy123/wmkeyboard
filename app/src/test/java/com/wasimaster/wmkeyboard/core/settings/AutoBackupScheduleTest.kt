package com.wasimaster.wmkeyboard.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupScheduleTest {

    private val hour = 60L * 60L * 1000L
    private val now = 1_754_575_353_000L

    private fun settings(lastRunAtMs: Long, intervalHours: Int = 24) =
        AutoBackupSettings(lastRunAtMs = lastRunAtMs, intervalHours = intervalHours)

    @Test
    fun `a backup that has never run is due`() {
        assertTrue(AutoBackupRunner.isDue(settings(lastRunAtMs = 0), now))
    }

    @Test
    fun `the interval has to have elapsed`() {
        assertFalse(AutoBackupRunner.isDue(settings(now - 23 * hour), now))
        assertTrue(AutoBackupRunner.isDue(settings(now - 24 * hour), now))
        assertTrue(AutoBackupRunner.isDue(settings(now - 400 * hour), now))
    }

    @Test
    fun `the interval is the one the user set`() {
        assertFalse(AutoBackupRunner.isDue(settings(now - 5 * hour, intervalHours = 6), now))
        assertTrue(AutoBackupRunner.isDue(settings(now - 6 * hour, intervalHours = 6), now))
    }

    @Test
    fun `a last run in the future does not postpone the next one`() {
        // The clock moved back, or a bundle from another device carried its
        // timestamp in. Waiting for the stamp to come round again could mean
        // waiting weeks, with nothing anywhere saying why.
        assertTrue(AutoBackupRunner.isDue(settings(now + 400 * hour), now))
    }

    @Test
    fun `a nonsense interval cannot stop backups altogether`() {
        assertTrue(AutoBackupRunner.isDue(settings(now - hour, intervalHours = 0), now))
        assertTrue(AutoBackupRunner.isDue(settings(now - hour, intervalHours = -3), now))
    }

    @Test
    fun `the default is a daily backup`() {
        assertFalse(AutoBackupRunner.isDue(AutoBackupSettings(lastRunAtMs = now - 23 * hour), now))
        assertTrue(AutoBackupRunner.isDue(AutoBackupSettings(lastRunAtMs = now - 25 * hour), now))
    }
}
