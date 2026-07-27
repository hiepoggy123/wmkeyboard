package com.wasimaster.wmkeyboard.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher shortcuts name in-app routes as plain strings in an XML file, so
 * nothing but a test connects them to the screens they open. These read both
 * sides and fail when they drift apart.
 */
class SettingsShortcutsTest {

    private val shortcutsXml: String by lazy {
        val file = File("src/main/res/xml/shortcuts.xml")
        assertTrue("shortcuts.xml not found at ${file.absolutePath}", file.isFile)
        file.readText()
    }

    private val navHostSource: String by lazy {
        File("src/main/java/com/wasimaster/wmkeyboard/app/MainActivity.kt").readText()
    }

    @Test
    fun `every allowed route is a real destination`() {
        val missing = SettingsShortcuts.Routes
            .filterNot { navHostSource.contains("composable(\"$it\")") }
        assertEquals("shortcut routes with no NavHost destination", emptyList<String>(), missing)
    }

    @Test
    fun `every link in the shortcut xml resolves`() {
        val links = Regex("""android:data="([^"]+)"""")
            .findAll(shortcutsXml)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("no android:data links found in shortcuts.xml", links.isNotEmpty())
        val unresolved = links.filter { SettingsShortcuts.routeFor(it) == null }
        assertEquals("shortcut links that resolve to no route", emptyList<String>(), unresolved)
    }

    @Test
    fun `shortcut ids match the route they open`() {
        val shortcuts = Regex(
            """android:shortcutId="([^"]+)"[\s\S]*?android:data="([^"]+)"""",
        ).findAll(shortcutsXml).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(shortcuts.isNotEmpty())
        for ((id, link) in shortcuts) {
            assertEquals("shortcut \"$id\" opens a different route", id, SettingsShortcuts.routeFor(link))
        }
    }

    @Test
    fun `both uri forms parse`() {
        assertEquals("themes", SettingsShortcuts.routeFor("wmkeyboard://settings/themes"))
        assertEquals("themes", SettingsShortcuts.routeFor("wmkeyboard:settings/themes"))
    }

    @Test
    fun `an unlisted route is refused`() {
        assertNull(SettingsShortcuts.routeFor("wmkeyboard://settings/backup"))
        assertNull(SettingsShortcuts.routeFor("wmkeyboard://settings/"))
        assertNull(SettingsShortcuts.routeFor("wmkeyboard://addons"))
        assertNull(SettingsShortcuts.routeFor("https://example.com/settings/themes"))
        assertNull(SettingsShortcuts.routeFor(null as String?))
        assertNull(SettingsShortcuts.routeFor(""))
    }
}
