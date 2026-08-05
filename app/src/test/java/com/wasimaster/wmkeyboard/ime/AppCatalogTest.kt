package com.wasimaster.wmkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The app-launcher catalog's pure halves: query filtering and grid ordering.
 * The PackageManager-facing loaders need a device; [LauncherApp] stores plain
 * strings (never a ComponentName) precisely so these tests can run on the JVM.
 */
class AppCatalogTest {

    private fun app(pkg: String, label: String) =
        LauncherApp(packageName = pkg, label = label, activityName = "$pkg.Main")

    private val apps = listOf(
        app("com.example.mail", "Mail"),
        app("com.example.maps", "Maps"),
        app("org.chat.app", "Chatter"),
        app("com.bank.two", "Two Bank"),
    )

    // ---- filtering ----------------------------------------------------

    @Test
    fun `an empty query returns the list untouched`() {
        assertSame(apps, AppCatalog.filterApps(apps, ""))
        assertSame(apps, AppCatalog.filterApps(apps, "   "))
    }

    @Test
    fun `filtering matches labels case-insensitively`() {
        assertEquals(
            listOf("Mail", "Maps"),
            AppCatalog.filterApps(apps, "mA").map { it.label },
        )
    }

    @Test
    fun `filtering matches package names too`() {
        assertEquals(
            listOf("Chatter"),
            AppCatalog.filterApps(apps, "org.chat").map { it.label },
        )
    }

    // ---- ordering -----------------------------------------------------

    @Test
    fun `pinned apps lead in pin order, the rest keep alphabetical order`() {
        val sorted = AppCatalog.sortApps(
            apps,
            recentFirst = false,
            pinned = listOf("com.bank.two", "com.example.mail"),
            recents = emptyList(),
        )
        assertEquals(
            listOf("Two Bank", "Mail", "Maps", "Chatter"),
            sorted.map { it.label },
        )
    }

    @Test
    fun `recent-first ranks by recency and falls back to the label`() {
        val sorted = AppCatalog.sortApps(
            apps,
            recentFirst = true,
            pinned = emptyList(),
            recents = listOf("org.chat.app", "com.example.maps"),
        )
        assertEquals(
            listOf("Chatter", "Maps", "Mail", "Two Bank"),
            sorted.map { it.label },
        )
    }

    @Test
    fun `a pinned app never repeats in the recent ordering`() {
        val sorted = AppCatalog.sortApps(
            apps,
            recentFirst = true,
            pinned = listOf("org.chat.app"),
            recents = listOf("org.chat.app", "com.example.mail"),
        )
        assertEquals(
            listOf("Chatter", "Mail", "Maps", "Two Bank"),
            sorted.map { it.label },
        )
        assertEquals(sorted.size, sorted.distinctBy { it.packageName }.size)
    }

    @Test
    fun `a pin for an uninstalled package is skipped`() {
        val sorted = AppCatalog.sortApps(
            apps,
            recentFirst = false,
            pinned = listOf("gone.package", "com.example.maps"),
            recents = emptyList(),
        )
        assertEquals(
            listOf("Maps", "Mail", "Chatter", "Two Bank"),
            sorted.map { it.label },
        )
    }
}
