package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Drafts in the plugin editor's workspace, apart from the store. */
class PluginWorkspaceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun manifest(id: String = "com.example.demo", name: String = "Demo") = PluginManifest(
        format = PluginManifestCodec.FORMAT,
        id = id,
        name = name,
        pluginVersion = "1.0.0",
    )

    @Test
    fun `a new draft has its manifest and script on disk`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val draft = workspace.create(manifest(), "return 1", "blank", now = 1_000)!!
        assertEquals("return 1", workspace.script(draft.draftId))
        assertEquals("com.example.demo", workspace.manifest(draft.draftId)!!.id)
        assertEquals(listOf(draft), workspace.drafts())
        assertEquals("blank", draft.origin)
    }

    @Test
    fun `draft ids are safe path segments and never repeat`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val ids = (1..10).map { workspace.create(manifest(), "x", "blank", now = 5_000)!!.draftId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { Regex("^[a-z0-9][a-z0-9._-]{2,63}$").matches(it) })
    }

    @Test
    fun `a manifest that is not valid yet is kept as written`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val draft = workspace.create(manifest(id = "Not A Valid Id", name = ""), "x", "blank")!!
        assertEquals("Not A Valid Id", workspace.manifest(draft.draftId)!!.id)
        assertTrue(workspace.writeManifest(draft.draftId, manifest(id = "still bad")))
        assertEquals("still bad", workspace.manifest(draft.draftId)!!.id)
    }

    @Test
    fun `the workspace holds at most its maximum of drafts`() {
        val workspace = PluginWorkspace(temp.newFolder())
        repeat(PluginWorkspace.MAX_DRAFTS) { assertNotNull(workspace.create(manifest(), "x", "blank")) }
        assertNull(workspace.create(manifest(), "x", "blank"))
    }

    @Test
    fun `writing a script leaves no part file and moves the draft to the top`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val first = workspace.create(manifest(), "one", "blank", now = 1_000)!!
        val second = workspace.create(manifest(), "two", "blank", now = 2_000)!!
        assertEquals(second.draftId, workspace.drafts().first().draftId)

        assertTrue(workspace.writeScript(first.draftId, "one, edited", now = 3_000))

        assertEquals("one, edited", workspace.script(first.draftId))
        assertEquals(first.draftId, workspace.drafts().first().draftId)
        assertTrue(workspace.dirFor(first.draftId)!!.walkTopDown().none { it.name.endsWith(".part") })
        assertFalse(workspace.writeScript("d-missing-0000", "x"))
    }

    @Test
    fun `drafts survive a restart`() {
        val dir = temp.newFolder()
        val draft = PluginWorkspace(dir).create(manifest(), "kept", "blank")!!
        val reopened = PluginWorkspace(dir)
        assertEquals(listOf(draft.draftId), reopened.drafts().map { it.draftId })
        assertEquals("kept", reopened.script(draft.draftId))
    }

    @Test
    fun `deleting a draft removes its directory`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val draft = workspace.create(manifest(), "x", "blank")!!
        val dir = workspace.dirFor(draft.draftId)!!
        workspace.delete(draft.draftId)
        assertFalse(dir.exists())
        assertTrue(workspace.drafts().isEmpty())
    }

    @Test
    fun `the latest draft for a plugin is found by its manifest id`() {
        val workspace = PluginWorkspace(temp.newFolder())
        workspace.create(manifest(id = "com.example.other"), "x", "blank", now = 1_000)
        val older = workspace.create(manifest(), "x", "installed:com.example.demo", now = 2_000)!!
        val newer = workspace.create(manifest(), "x", "installed:com.example.demo", now = 3_000)!!
        assertEquals(newer.draftId, workspace.draftFor("com.example.demo")!!.draftId)
        workspace.writeScript(older.draftId, "y", now = 4_000)
        assertEquals(older.draftId, workspace.draftFor("com.example.demo")!!.draftId)
        assertNull(workspace.draftFor("com.example.absent"))
    }

    @Test
    fun `a duplicate copies the manifest and script but not the history`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val source = workspace.create(manifest(), "original", "blank")!!
        workspace.snapshot(source.draftId, SnapshotReason.MANUAL)

        val copy = workspace.duplicate(source.draftId)!!

        assertNotEquals(source.draftId, copy.draftId)
        assertEquals("original", workspace.script(copy.draftId))
        assertEquals("duplicate:${source.draftId}", copy.origin)
        assertTrue(workspace.snapshots(copy.draftId).isEmpty())
    }

    @Test
    fun `preview data lives in the draft, never among installed plugins`() {
        val root = temp.newFolder()
        val workspace = PluginWorkspace(File(root, PluginWorkspace.DIR_NAME))
        val draft = workspace.create(manifest(), "x", "blank")!!
        val storage = workspace.previewStorageFile(draft.draftId)!!
        assertTrue(storage.path.startsWith(workspace.dirFor(draft.draftId)!!.path))
        assertFalse(storage.path.contains("${File.separator}${PluginStore.DIR_NAME}${File.separator}"))
    }

    @Test
    fun `every change bumps the revision`() {
        val workspace = PluginWorkspace(temp.newFolder())
        var last = workspace.revision.value
        fun bumped(): Boolean = (workspace.revision.value > last).also { last = workspace.revision.value }

        val draft = workspace.create(manifest(), "x", "blank")!!
        assertTrue(bumped())
        workspace.writeScript(draft.draftId, "y")
        assertTrue(bumped())
        workspace.snapshot(draft.draftId, SnapshotReason.MANUAL)
        assertTrue(bumped())
        workspace.delete(draft.draftId)
        assertTrue(bumped())
    }

    @Test
    fun `reconcile drops a draft whose files were removed by hand`() {
        val workspace = PluginWorkspace(temp.newFolder())
        val draft = workspace.create(manifest(), "x", "blank")!!
        workspace.dirFor(draft.draftId)!!.deleteRecursively()
        assertTrue(workspace.reconcile())
        assertTrue(workspace.drafts().isEmpty())
        assertFalse(workspace.reconcile())
    }

    @Test
    fun `a locked workspace is empty and refuses writes`() {
        val workspace = PluginWorkspace(null)
        assertNull(workspace.create(manifest(), "x", "blank"))
        assertTrue(workspace.drafts().isEmpty())
        assertNull(workspace.previewStorageFile("d1234-abcd"))
    }

    @Test
    fun `unsafe ids never become paths`() {
        val workspace = PluginWorkspace(temp.newFolder())
        for (id in listOf("../escape", "", "ab", "UPPER", "a/b")) assertNull(id, workspace.dirFor(id))
    }
}
