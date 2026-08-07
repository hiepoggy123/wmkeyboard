package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.settings.sink.AutoBackupNaming
import com.wasimaster.wmkeyboard.core.settings.sink.SinkEntry
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupNamingTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun entry(name: String, modifiedAtMs: Long) =
        SinkEntry(id = name, name = name, sizeBytes = 1, modifiedAtMs = modifiedAtMs)

    @Test
    fun `a name stamps the second and carries the right extension`() {
        val stamp = 1_754_575_353_000L // 2025-08-07 14:02:33 UTC
        assertEquals(
            "wmkeyboard-auto-20250807-140233.wmconfig.json",
            AutoBackupNaming.name(stamp, encrypted = false, zone = utc),
        )
        assertEquals(
            "wmkeyboard-auto-20250807-140233.wmconfig.enc",
            AutoBackupNaming.name(stamp, encrypted = true, zone = utc),
        )
    }

    @Test
    fun `a generated name is recognised as ours`() {
        for (encrypted in listOf(false, true)) {
            val name = AutoBackupNaming.name(0L, encrypted, utc)
            assertTrue(name, AutoBackupNaming.isOurs(name))
            assertFalse(name, AutoBackupNaming.isPart(name))
        }
    }

    @Test
    fun `nothing else in the folder is ours`() {
        val strangers = listOf(
            // The user's own files, which the destination folder is full of.
            "tax-return-2025.pdf",
            "notes.wmconfig.json",
            "",
            // A hand-made export. Deliberate, so rotation must not eat it.
            "wmkeyboard-backup-20250807-140233.wmconfig.json",
            // Our prefix but not our format.
            "wmkeyboard-auto-20250807-140233.txt",
            "wmkeyboard-auto-20250807-140233.wmsettings.json",
            // Nearly right, and the near miss is the dangerous one.
            "wmkeyboard-auto-20250807-140233.wmconfig.json.bak",
        )
        for (name in strangers) assertFalse(name, AutoBackupNaming.isOurs(name))
    }

    @Test
    fun `a half-written file is ours to sweep but never ours to rotate`() {
        val part = AutoBackupNaming.name(0L, encrypted = false, zone = utc) +
            AutoBackupNaming.PART_SUFFIX
        assertTrue(AutoBackupNaming.isPart(part))
        assertFalse(AutoBackupNaming.isOurs(part))
        assertFalse(AutoBackupNaming.isPart("someone-elses.part"))
    }

    @Test
    fun `rotation deletes the oldest and keeps the newest`() {
        val entries = listOf(
            entry("c", 300),
            entry("a", 100),
            entry("d", 400),
            entry("b", 200),
        )
        assertEquals(listOf("a", "b"), AutoBackupNaming.rotation(entries, keep = 2).map { it.name })
    }

    @Test
    fun `rotation is a no-op while there is room`() {
        val entries = listOf(entry("a", 100), entry("b", 200))
        assertTrue(AutoBackupNaming.rotation(entries, keep = 2).isEmpty())
        assertTrue(AutoBackupNaming.rotation(entries, keep = 9).isEmpty())
        assertTrue(AutoBackupNaming.rotation(emptyList(), keep = 1).isEmpty())
    }

    @Test
    fun `the name breaks a tie when a provider reports no modified time`() {
        // Several providers return 0 for COLUMN_LAST_MODIFIED. The stamp in the
        // name is then the only ordering left, and it has to be enough.
        val entries = listOf(
            entry(AutoBackupNaming.name(3_000L, false, utc), 0),
            entry(AutoBackupNaming.name(1_000L, false, utc), 0),
            entry(AutoBackupNaming.name(2_000L, false, utc), 0),
        )
        val deleted = AutoBackupNaming.rotation(entries, keep = 1).map { it.name }
        assertEquals(
            listOf(
                AutoBackupNaming.name(1_000L, false, utc),
                AutoBackupNaming.name(2_000L, false, utc),
            ),
            deleted,
        )
    }

    @Test
    fun `rotation never empties the folder`() {
        val entries = listOf(entry("a", 100), entry("b", 200))
        // A keep of 0 is not a request to delete everything, whatever the box says.
        assertEquals(listOf("a"), AutoBackupNaming.rotation(entries, keep = 0).map { it.name })
        assertEquals(listOf("a"), AutoBackupNaming.rotation(entries, keep = -5).map { it.name })
    }
}
