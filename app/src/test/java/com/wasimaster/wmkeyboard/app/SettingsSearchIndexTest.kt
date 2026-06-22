package com.wasimaster.wmkeyboard.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search index lists setting titles by hand, so it silently rots when a
 * setting is renamed. These tests read the settings sources and fail when an
 * indexed title no longer appears in any of them.
 */
class SettingsSearchIndexTest {

    private val sources: String by lazy {
        val dir = File("src/main/java/com/wasimaster/wmkeyboard/app")
        assertTrue("settings sources not found at ${dir.absolutePath}", dir.isDirectory)
        dir.listFiles { f -> f.extension == "kt" }
            .orEmpty()
            .filterNot { it.name == "SettingsSearch.kt" }
            .joinToString("\n") { it.readText() }
    }

    @Test
    fun `every indexed title still exists in a settings screen`() {
        // Titles are matched as source string literals, which is why the
        // index stores them verbatim rather than in a normalised form.
        val missing = SettingsSearchIndex
            .map { it.title }
            .distinct()
            .filterNot { title ->
                sources.contains("\"$title\"") ||
                    // Tool titles come from toolTitle()'s when-branches.
                    sources.contains("-> \"$title\"")
            }
        assertEquals("indexed settings no longer present in any screen", emptyList<String>(), missing)
    }

    @Test
    fun `routes are unique per title and non-blank`() {
        val bad = SettingsSearchIndex.filter { it.route.isBlank() || it.screen.isBlank() }
        assertEquals(emptyList<SettingsSearchEntry>(), bad)
        val duplicates = SettingsSearchIndex
            .groupBy { it.route to it.title }
            .filterValues { it.size > 1 }
            .keys
        assertEquals(emptySet<Pair<String, String>>(), duplicates)
    }

    @Test
    fun `search ranks an exact title above a subtitle mention`() {
        val results = searchSettings("autocorrect")
        assertTrue(results.isNotEmpty())
        assertEquals("Autocorrect", results.first().title)
    }

    @Test
    fun `every token must match`() {
        // "row" alone hits many settings; pairing it narrows to number row.
        val results = searchSettings("number row")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { "number" in (it.title + it.subtitle + it.screen).lowercase() })
    }

    @Test
    fun `a query matching nothing returns nothing`() {
        assertEquals(emptyList<SettingsSearchEntry>(), searchSettings("zzzznotasetting"))
    }
}
