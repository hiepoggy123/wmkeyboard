package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginThumbnailTest {

    private fun demo(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("plugins/$name.lua")) { "missing demo $name" }
            .bufferedReader()
            .use { it.readText() }

    private fun plugin(storage: Boolean = false) = InstalledPlugin(
        id = "com.example.thumbnail",
        name = "Thumbnail",
        version = "1",
        permissions = if (storage) listOf(PluginPermission.Storage.wire) else emptyList(),
    )

    @Test
    fun `every template demo draws a panel with nothing repaired`() {
        for ((name, storage) in listOf("text-tools" to false, "cipher-tool" to false, "todo-list" to true, "ui-kitchen-sink" to false)) {
            val drawn = PluginThumbnail.render(plugin(storage), demo(name))
            assertNotNull(name, drawn)
            val ui = drawn!!
            assertTrue(name, ui.root.isNotEmpty())
            assertTrue("$name: ${ui.repairs}", ui.repairs.isEmpty())
        }
    }

    @Test
    fun `a script that fails, never stops, or cannot draw gives no picture`() {
        assertNull(PluginThumbnail.render(plugin(), "error('no')"))
        assertNull(PluginThumbnail.render(plugin(), "while true do end"))
        assertNull(PluginThumbnail.render(plugin(), "local x = 1"))
        assertNull(PluginThumbnail.render(plugin(), "function render() while true do end end"))
    }
}
