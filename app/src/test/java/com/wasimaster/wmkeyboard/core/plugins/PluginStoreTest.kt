package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PluginStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): PluginStore = PluginStore(temp.newFolder("plugins-${counter++}"))

    private fun manifest(
        id: String = "com.example.demo",
        name: String = "Demo",
        version: String = "1.0.0",
        permissions: List<String> = emptyList(),
    ) = PluginManifest(
        format = PluginManifestCodec.FORMAT,
        version = PluginManifestCodec.VERSION,
        id = id,
        name = name,
        pluginVersion = version,
        author = "Example",
        description = "A demo.",
        apiVersion = 1,
        entry = "main.lua",
        permissions = permissions,
    )

    // ---- adopt ---------------------------------------------------------

    @Test
    fun `adopting a plugin writes its manifest and script`() {
        val store = store()
        val result = store.adopt(manifest(), "return 1") as PluginAdoptResult.Adopted
        assertFalse(result.replaced)
        assertEquals("Demo", result.plugin.name)
        assertEquals("return 1", store.script("com.example.demo"))
        assertTrue(store.manifestFile("com.example.demo")!!.isFile)
    }

    @Test
    fun `an adopted plugin survives a reload`() {
        val dir = temp.newFolder("reload")
        PluginStore(dir).adopt(manifest(), "return 1")
        val reopened = PluginStore(dir)
        assertEquals(1, reopened.plugins().size)
        assertEquals("Demo", reopened.plugin("com.example.demo")?.name)
    }

    @Test
    fun `updating a plugin replaces its script and keeps its storage`() {
        val store = store()
        store.adopt(manifest(version = "1.0.0", permissions = listOf("storage")), "old")
        store.storageFile("com.example.demo")!!.writeText("""{"todos":"[]"}""")

        val result = store.adopt(manifest(version = "2.0.0"), "new") as PluginAdoptResult.Adopted
        assertTrue(result.replaced)
        assertEquals("2.0.0", result.plugin.version)
        assertEquals("new", store.script("com.example.demo"))
        assertEquals(1, store.plugins().size)
        // The user's data outlives the plugin that owns it being upgraded.
        assertEquals("""{"todos":"[]"}""", store.storageFile("com.example.demo")!!.readText())
    }

    @Test
    fun `a permission dropped by an update stops applying`() {
        val store = store()
        store.adopt(manifest(permissions = listOf("storage")), "old")
        assertTrue(store.plugin("com.example.demo")!!.grants(PluginPermission.Storage))

        store.adopt(manifest(permissions = emptyList()), "new")
        assertFalse(store.plugin("com.example.demo")!!.grants(PluginPermission.Storage))
    }

    @Test
    fun `the plugin list is capped`() {
        val store = store()
        repeat(PluginStore.MAX_PLUGINS) { i ->
            val outcome = store.adopt(manifest(id = "com.example.p$i"), "return $i")
            assertTrue(outcome is PluginAdoptResult.Adopted)
        }
        assertEquals(
            PluginAdoptResult.TooManyPlugins,
            store.adopt(manifest(id = "com.example.spill"), "return 0"),
        )
        // An update of something already installed still goes through.
        assertTrue(store.adopt(manifest(id = "com.example.p0"), "v2") is PluginAdoptResult.Adopted)
    }

    // ---- delete and reconcile ------------------------------------------

    @Test
    fun `deleting a plugin takes its whole directory with it`() {
        val store = store()
        store.adopt(manifest(permissions = listOf("storage")), "return 1")
        val dir = store.dirFor("com.example.demo")!!
        store.storageFile("com.example.demo")!!.writeText("{}")
        store.logFile("com.example.demo")!!.writeText("hello")

        store.delete("com.example.demo")
        assertFalse(dir.exists())
        assertTrue(store.plugins().isEmpty())
    }

    @Test
    fun `an index entry whose script has gone is reconciled away`() {
        val store = store()
        store.adopt(manifest(), "return 1")
        store.scriptFile("com.example.demo")!!.delete()

        assertTrue(store.reconcile())
        assertTrue(store.plugins().isEmpty())
        assertFalse(store.reconcile())
    }

    // ---- enable and strikes --------------------------------------------

    @Test
    fun `a plugin that runs away twice is disabled`() {
        val store = store()
        store.adopt(manifest(), "return 1")

        assertTrue(store.recordAbandon("com.example.demo")!!.enabled)
        val second = store.recordAbandon("com.example.demo")!!
        assertEquals(PluginStore.MAX_ABANDONS, second.abandonedCount)
        assertFalse(second.enabled)
        assertTrue(store.runnablePlugins().isEmpty())
    }

    @Test
    fun `turning a disabled plugin back on forgives its strikes`() {
        val store = store()
        store.adopt(manifest(), "return 1")
        repeat(PluginStore.MAX_ABANDONS) { store.recordAbandon("com.example.demo") }

        store.setEnabled("com.example.demo", true)
        val plugin = store.plugin("com.example.demo")!!
        assertTrue(plugin.enabled)
        assertEquals(0, plugin.abandonedCount)
    }

    // ---- direct boot ---------------------------------------------------

    @Test
    fun `a locked store is empty rather than broken`() {
        val store = PluginStore(null)
        assertTrue(store.plugins().isEmpty())
        assertNull(store.dirFor("com.example.demo"))
        assertNull(store.script("com.example.demo"))
        assertEquals(PluginAdoptResult.Failed, store.adopt(manifest(), "return 1"))
        assertFalse(store.reconcile())
        store.delete("com.example.demo")
    }

    @Test
    fun `attaching after unlock picks up what is on disk`() {
        val dir = temp.newFolder("unlock")
        PluginStore(dir).adopt(manifest(), "return 1")

        val store = PluginStore(null)
        assertTrue(store.plugins().isEmpty())
        store.attach(dir)
        assertEquals(1, store.plugins().size)
    }

    // ---- path safety ---------------------------------------------------

    @Test
    fun `an id that is not a safe path segment never becomes a directory`() {
        val store = store()
        for (bad in listOf("..", "../evil", "a/b", "a\\b", "", "UPPER", "ab")) {
            assertNull("expected $bad to resolve to nothing", store.dirFor(bad))
        }
        assertNotNull(store.dirFor("com.example.demo"))
    }

    @Test
    fun `an index hand-edited to carry an unsafe id is dropped on load`() {
        val dir = temp.newFolder("tampered")
        File(dir, "plugins.json").writeText(
            """{"version":1,"plugins":[{"id":"../escape","name":"Evil","version":"1.0.0"}]}""",
        )
        assertTrue(PluginStore(dir).plugins().isEmpty())
    }

    private companion object {
        var counter = 0
    }
}
