package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import java.io.File

class PluginHostApiTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val writes = ArrayList<Pair<String, String>>()
    private var revoked = false

    private fun plugin(permissions: List<String> = emptyList()) = InstalledPlugin(
        id = "com.example.demo",
        name = "Demo",
        version = "1.0.0",
        permissions = permissions,
    )

    private fun sandbox(
        permissions: List<String> = emptyList(),
        storageFile: File? = null,
    ): Pair<Globals, PluginBudget> {
        val budget = PluginBudget()
        val globals = PluginSandbox.create(budget) { }
        val api = PluginHostApi(
            plugin = plugin(permissions),
            log = PluginLog(null),
            storage = if ("storage" in permissions) PluginStorage(storageFile) else null,
            setInput = { id, text -> writes += id to text },
            revoked = { revoked },
        )
        globals.set("wm", api.table())
        budget.begin(PluginLimit.LOAD)
        PluginSandbox.compile(globals, PluginPrelude.SOURCE, PluginPrelude.CHUNK_NAME).call()
        return globals to budget
    }

    private fun run(source: String, permissions: List<String> = emptyList(), file: File? = null): LuaValue {
        val (globals, _) = sandbox(permissions, file)
        return PluginSandbox.compile(globals, source).call()
    }

    // ---- the surface itself ----------------------------------------------

    @Test
    fun `the wm table is exactly what it is supposed to be`() {
        // The point of this test is the absence, not the presence. If a later
        // change adds a way for a plugin to read text, the clipboard or the
        // network, this fails -- which is the whole idea.
        val keys = run(
            """
            local names = {}
            for k in pairs(wm) do names[#names + 1] = k end
            table.sort(names)
            return table.concat(names, ',')
            """.trimIndent(),
            permissions = listOf("storage"),
        ).tojstring()

        assertEquals("api_version,json,log,plugin_id,plugin_version,storage,ui", keys)
    }

    @Test
    fun `there is no way to reach text the clipboard or the network`() {
        val forbidden = listOf(
            "wm.text", "wm.clipboard", "wm.http", "wm.net", "wm.field", "wm.insert",
            "wm.keys", "wm.input", "wm.contacts", "wm.files", "wm.fs", "wm.exec",
        )
        for (name in forbidden) {
            assertEquals("$name must not exist", LuaValue.TRUE, run("return ($name) == nil"))
        }
    }

    @Test
    fun `storage is absent unless the manifest declared it`() {
        assertEquals(LuaValue.TRUE, run("return wm.storage == nil"))
        assertEquals(
            LuaValue.TRUE,
            run("return wm.storage ~= nil", permissions = listOf("storage")),
        )
    }

    @Test
    fun `a plugin can see its own identity`() {
        assertEquals(LuaValue.valueOf("com.example.demo"), run("return wm.plugin_id"))
        assertEquals(LuaValue.valueOf("1.0.0"), run("return wm.plugin_version"))
        assertEquals(1, run("return wm.api_version").toint())
    }

    // ---- storage ----------------------------------------------------------

    @Test
    fun `storage round trips through a file`() {
        val file = File(temp.newFolder("store"), "storage.json")
        run(
            "wm.storage.set('todos', '[1,2,3]')",
            permissions = listOf("storage"),
            file = file,
        )
        assertEquals("[1,2,3]", PluginStorage(file).get("todos"))

        val read = run("return wm.storage.get('todos')", permissions = listOf("storage"), file = file)
        assertEquals(LuaValue.valueOf("[1,2,3]"), read)
    }

    @Test
    fun `storage reports quota failures rather than throwing`() {
        val file = File(temp.newFolder("quota"), "storage.json")
        val result = run(
            """
            local ok, err = wm.storage.set('big', ('x'):rep(200000))
            return err ~= nil
            """.trimIndent(),
            permissions = listOf("storage"),
            file = file,
        )
        assertEquals(LuaValue.TRUE, result)
    }

    @Test
    fun `storage keys and remove work`() {
        val file = File(temp.newFolder("keys"), "storage.json")
        val result = run(
            """
            wm.storage.set('a', '1')
            wm.storage.set('b', '2')
            wm.storage.remove('a')
            local ks = wm.storage.keys()
            return table.concat(ks, ',')
            """.trimIndent(),
            permissions = listOf("storage"),
            file = file,
        )
        assertEquals(LuaValue.valueOf("b"), result)
    }

    // ---- json -------------------------------------------------------------

    @Test
    fun `json decodes objects and arrays`() {
        assertEquals(LuaValue.valueOf("Ada"), run("""return wm.json.decode('{"name":"Ada"}').name"""))
        assertEquals(2.0, run("""return wm.json.decode('[1,2,3]')[2]""").todouble(), 0.0)
        assertEquals(LuaValue.TRUE, run("""return wm.json.decode('{"ok":true}').ok"""))
    }

    @Test
    fun `json encodes arrays as arrays and maps as objects`() {
        assertEquals(LuaValue.valueOf("[1,2]"), run("return wm.json.encode({1, 2})"))
        assertEquals(LuaValue.valueOf("""{"a":"b"}"""), run("return wm.json.encode({a = 'b'})"))
    }

    @Test
    fun `invalid json is an error value rather than a crash`() {
        assertEquals(
            LuaValue.TRUE,
            run("local v, err = wm.json.decode('nonsense{'); return v == nil and err ~= nil"),
        )
    }

    @Test
    fun `a cyclic table cannot be encoded forever`() {
        val result = run(
            """
            local t = {}
            t.self = t
            local v, err = wm.json.encode(t)
            return v == nil and err ~= nil
            """.trimIndent(),
        )
        assertEquals(LuaValue.TRUE, result)
    }

    // ---- ui.set_input ------------------------------------------------------

    @Test
    fun `set_input is collected for the host to apply`() {
        run("wm.ui.set_input('note', 'hello')")
        assertEquals(listOf("note" to "hello"), writes)
    }

    @Test
    fun `set_input text is capped`() {
        run("wm.ui.set_input('note', ('x'):rep(100000))")
        assertTrue(writes.single().second.length <= 8 * 1024)
    }

    // ---- revocation --------------------------------------------------------

    @Test
    fun `an abandoned session cannot touch anything`() {
        val file = File(temp.newFolder("revoked"), "storage.json")
        revoked = true
        val thrown = runCatching {
            run("wm.storage.set('a', '1')", permissions = listOf("storage"), file = file)
        }.exceptionOrNull()

        assertTrue("expected the call to be refused", thrown is PluginAbort)
        assertNull(PluginStorage(file).get("a"))
        assertTrue(writes.isEmpty())
    }
}
