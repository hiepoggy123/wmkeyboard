package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.plugins.PluginManifest
import com.wasimaster.wmkeyboard.core.plugins.PluginManifestCodec
import com.wasimaster.wmkeyboard.core.plugins.PluginStore
import com.wasimaster.wmkeyboard.core.plugins.PluginWorkspace
import com.wasimaster.wmkeyboard.core.plugins.SnapshotReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The editor's versions, over a real workspace on disk. */
class PluginIdeHistoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun editor(): PluginIdeState {
        val workspace = PluginWorkspace(temp.newFolder("workspace"))
        val manifest = PluginManifest(format = PluginManifestCodec.FORMAT, id = "my.test", name = "Test", pluginVersion = "1")
        val draft = requireNotNull(workspace.create(manifest, "-- one\n", "blank"))
        return PluginIdeState(draft.draftId, WorkspaceIdePorts(workspace, PluginStore(null), draft.draftId))
    }

    @Test
    fun `a version kept while working skips text the newest version already has`() {
        val ide = editor()
        assertNotNull(ide.keepWhileWorking("-- two\n"))
        assertNull(ide.keepWhileWorking("-- two\n"))
        assertEquals(SnapshotReason.PERIODIC.wire, ide.versions().first().reason)
    }

    @Test
    fun `restoring brings a version back and keeps the code it replaced`() {
        val ide = editor()
        val first = requireNotNull(ide.saveVersion("-- first\n"))
        assertEquals("-- first\n", ide.restore(first.snapshotId, "-- later\n"))
        assertEquals("-- first\n", ide.savedText)
        val newest = ide.versions().first()
        assertEquals(SnapshotReason.BEFORE_RESTORE.wire, newest.reason)
        assertEquals("-- later\n", ide.versionText(newest.snapshotId))
    }

    @Test
    fun `restoring a version that is not there changes nothing but the save`() {
        val ide = editor()
        assertNull(ide.restore("s0000", "-- keep\n"))
        assertEquals("-- keep\n", ide.savedText)
    }
}
