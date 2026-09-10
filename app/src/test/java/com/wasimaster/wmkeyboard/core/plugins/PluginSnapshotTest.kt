package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A draft's snapshot history: what is kept, what is pruned, and how a restore undoes itself. */
class PluginSnapshotTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: PluginWorkspace

    private fun draft(script: String = "v0"): String {
        workspace = PluginWorkspace(temp.newFolder())
        val manifest = PluginManifest(format = PluginManifestCodec.FORMAT, id = "com.example.demo", name = "Demo", pluginVersion = "1")
        return workspace.create(manifest, script, "blank")!!.draftId
    }

    private fun write(id: String, text: String, now: Long) {
        assertTrue(workspace.writeScript(id, text, now))
    }

    @Test
    fun `a snapshot keeps the script as it was`() {
        val id = draft("first")
        val snapshot = workspace.snapshot(id, SnapshotReason.MANUAL, note = "before the big change", now = 10)!!
        write(id, "second", now = 20)

        assertEquals("first", workspace.snapshotBody(id, snapshot.snapshotId))
        assertEquals("manual", snapshot.reason)
        assertEquals(5, snapshot.characters)
        assertEquals("before the big change", snapshot.note)
    }

    @Test
    fun `the same text is never kept twice in a row`() {
        val id = draft("same")
        assertNotNull(workspace.snapshot(id, SnapshotReason.PERIODIC, now = 1))
        assertNull(workspace.snapshot(id, SnapshotReason.PERIODIC, now = 2))
        assertEquals(1, workspace.snapshots(id).size)
    }

    @Test
    fun `snapshots are listed newest first`() {
        val id = draft()
        for (n in 1..3) {
            write(id, "v$n", now = n * 10L)
            workspace.snapshot(id, SnapshotReason.MANUAL, now = n * 10L)
        }
        assertEquals(listOf(30L, 20L, 10L), workspace.snapshots(id).map { it.at })
    }

    @Test
    fun `past the maximum the oldest goes`() {
        val id = draft()
        for (n in 1..(PluginWorkspace.MAX_SNAPSHOTS + 1)) {
            write(id, "v$n", now = n.toLong())
            workspace.snapshot(id, SnapshotReason.MANUAL, now = n.toLong())
        }
        val kept = workspace.snapshots(id)
        assertEquals(PluginWorkspace.MAX_SNAPSHOTS, kept.size)
        assertEquals(2L, kept.last().at)
    }

    @Test
    fun `periodic snapshots are pruned before the ones the author asked for`() {
        val id = draft()
        write(id, "manual", now = 1)
        workspace.snapshot(id, SnapshotReason.MANUAL, now = 1)
        for (n in 2..(PluginWorkspace.MAX_SNAPSHOTS + 1)) {
            write(id, "periodic $n", now = n.toLong())
            workspace.snapshot(id, SnapshotReason.PERIODIC, now = n.toLong())
        }
        val kept = workspace.snapshots(id)
        assertEquals(PluginWorkspace.MAX_SNAPSHOTS, kept.size)
        assertTrue("the author's own snapshot was pruned", kept.any { it.reason == "manual" })
        assertFalse(kept.any { it.at == 2L })
    }

    @Test
    fun `history stays inside its character budget`() {
        val id = draft()
        val large = 200_000
        for (n in 1..4) {
            write(id, n.toString().repeat(large), now = n.toLong())
            workspace.snapshot(id, SnapshotReason.MANUAL, now = n.toLong())
        }
        val kept = workspace.snapshots(id)
        assertTrue(kept.sumOf { it.characters } <= PluginWorkspace.MAX_SNAPSHOT_CHARACTERS)
        assertEquals(4L, kept.first().at)
        assertEquals(2, kept.size)
    }

    @Test
    fun `restoring saves the script it replaces first`() {
        val id = draft("good")
        val good = workspace.snapshot(id, SnapshotReason.MANUAL, now = 1)!!
        write(id, "broken", now = 2)

        assertTrue(workspace.restore(id, good.snapshotId, now = 3))

        assertEquals("good", workspace.script(id))
        val newest = workspace.snapshots(id).first()
        assertEquals("before_restore", newest.reason)
        assertEquals("broken", workspace.snapshotBody(id, newest.snapshotId))
    }

    @Test
    fun `restoring an unknown snapshot changes nothing`() {
        val id = draft("kept")
        assertFalse(workspace.restore(id, "s-missing-0000", now = 1))
        assertEquals("kept", workspace.script(id))
        assertTrue(workspace.snapshots(id).isEmpty())
    }

    @Test
    fun `a deleted draft takes its history with it`() {
        val id = draft("x")
        workspace.snapshot(id, SnapshotReason.MANUAL, now = 1)
        workspace.delete(id)
        assertTrue(workspace.snapshots(id).isEmpty())
    }
}
