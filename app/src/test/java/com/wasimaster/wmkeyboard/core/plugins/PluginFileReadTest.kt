package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** [PluginFile.read], the editor's way to open a file, must refuse exactly what [PluginFile.import] refuses. */
class PluginFileReadTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): PluginStore = PluginStore(temp.newFolder())

    private fun manifest(entry: String = "main.lua", permissions: List<String> = emptyList()) = PluginManifest(
        format = PluginManifestCodec.FORMAT,
        version = PluginManifestCodec.VERSION,
        id = "com.example.demo",
        name = "Demo",
        pluginVersion = "1.0.0",
        author = "Example",
        description = "A demo.",
        apiVersion = 1,
        entry = entry,
        permissions = permissions,
    )

    private fun archive(manifest: PluginManifest, script: String): ByteArray =
        ByteArrayOutputStream().also { PluginFile.write(it, manifest, script) }.toByteArray()

    private fun rawArchive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifestEntry(manifest: PluginManifest = manifest()) =
        PluginFile.MANIFEST to PluginManifestCodec.encode(manifest).toByteArray()

    private fun read(bytes: ByteArray) = PluginFile.read(ByteArrayInputStream(bytes))

    @Test
    fun `a good archive reads with its script and permissions`() {
        val result = read(archive(manifest(permissions = listOf("storage")), "return 1")) as PluginReadResult.Ok
        assertEquals("return 1", result.script)
        assertEquals("com.example.demo", result.manifest.id)
        assertEquals(listOf<PluginPermission>(PluginPermission.Storage), result.permissions)
    }

    @Test
    fun `import installs exactly what read returned`() {
        val bytes = archive(manifest(), "return 42")
        val read = read(bytes) as PluginReadResult.Ok
        val store = store()

        val imported = PluginFile.import(ByteArrayInputStream(bytes), store) as PluginImportResult.Imported

        assertEquals(read.manifest.id, imported.plugin.id)
        assertEquals(read.script, store.script(read.manifest.id))
    }

    @Test
    fun `a script under a folder resolves as it does for import`() {
        val bytes = rawArchive(manifestEntry(), "src/main.lua" to "return 7".toByteArray())
        assertEquals("return 7", (read(bytes) as PluginReadResult.Ok).script)
    }

    @Test
    fun `read refuses everything import refuses, in the same words`() {
        val cases = listOf(
            "garbage" to byteArrayOf(1, 2, 3, 4),
            "no manifest" to rawArchive("main.lua" to "return 1".toByteArray()),
            "another format" to rawArchive(
                PluginFile.MANIFEST to """{"format":"something-else"}""".toByteArray(),
                "main.lua" to "return 1".toByteArray(),
            ),
            "missing script" to rawArchive(manifestEntry(manifest(entry = "other.lua")), "main.lua" to "x".toByteArray()),
            "empty script" to rawArchive(manifestEntry(), "main.lua" to ByteArray(0)),
            "oversized script" to rawArchive(
                manifestEntry(),
                "main.lua" to ByteArray(PluginFile.MAX_SCRIPT_BYTES + 1) { 'a'.code.toByte() },
            ),
            "compiled Lua" to rawArchive(manifestEntry(), "main.lua" to byteArrayOf(0x1B, 0x4C, 0x75, 0x61)),
            "not UTF-8" to rawArchive(manifestEntry(), "main.lua" to byteArrayOf(0xC3.toByte(), 0x28)),
            "unknown permission" to rawArchive(
                manifestEntry(manifest(permissions = listOf("network"))),
                "main.lua" to "x".toByteArray(),
            ),
        )
        var refusals = 0
        for ((label, bytes) in cases) {
            val imported = PluginFile.import(ByteArrayInputStream(bytes), store())
            when (val read = read(bytes)) {
                is PluginReadResult.Ok -> fail("$label: read accepted it")
                PluginReadResult.NotAPlugin -> assertEquals(label, PluginImportResult.NotAPlugin, imported)
                PluginReadResult.Failed -> assertEquals(label, PluginImportResult.Failed, imported)
                is PluginReadResult.Rejected -> {
                    refusals++
                    assertEquals(label, PluginImportResult.Rejected(read.reasonText), imported)
                }
            }
        }
        assertTrue("expected the six script and manifest refusals, got $refusals", refusals >= 6)
    }
}
