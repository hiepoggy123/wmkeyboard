package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two jobs.
 *
 * The first is the drift guard. The index no longer copies the words of a
 * settings row; it names the row's string resource. So the test reads the
 * `R.string.…` names out of `SettingsSearch.kt` and checks two things about
 * each one: that the resource exists, and that some other screen file also
 * draws it. A key that only the index names is a row that was deleted or
 * renamed, which is the drift this test is here to catch. The check runs on
 * the sources, so it stays a plain JVM test with no Android resources.
 *
 * The second is the ranking. Those tests run on a small hand-built index,
 * because the real one now needs [android.content.res.Resources].
 */
class SettingsSearchIndexTest {

    private val appDir = File("src/main/java/com/wasimaster/wmkeyboard/app")
    private val valuesDir = File("src/main/res/values")

    private val indexSource: String by lazy {
        val file = File(appDir, "SettingsSearch.kt")
        assertTrue("search index not found at ${file.absolutePath}", file.isFile)
        file.readText()
    }

    /**
     * Every `R.string.…` the index names. `CommonR.string.…` is skipped: those
     * belong to :core:common and are not in this module's resource files.
     */
    private val indexedKeys: List<String> by lazy {
        Regex("""(?<!Common)R\.string\.([a-z0-9_]+)""")
            .findAll(indexSource)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    /** Every string this module defines, from every `values/strings*.xml`. */
    private val definedKeys: Set<String> by lazy {
        val files = valuesDir
            .listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }
            .orEmpty()
        assertTrue("no strings*.xml under ${valuesDir.absolutePath}", files.isNotEmpty())
        files.flatMapTo(mutableSetOf()) { file ->
            Regex("<string name=\"([^\"]+)\"")
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toList()
        }
    }

    /**
     * Every string some settings screen draws. The index itself is excluded.
     *
     * Walks the tree rather than listing one directory: the screens are mostly
     * flat under `app/`, but a feature with several files of its own gets a
     * package (`app/updates/`), and a row drawn from there is still a row.
     */
    private val keysDrawnByScreens: Set<String> by lazy {
        appDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "SettingsSearch.kt" }
            .flatMapTo(mutableSetOf()) { file ->
                Regex("""R\.string\.([a-z0-9_]+)""")
                    .findAll(file.readText())
                    .map { it.groupValues[1] }
                    .toList()
            }
    }

    /**
     * Strings the index owns outright, because the screen has nothing to point
     * at. Keep this list short: each entry is a second copy of some wording
     * that a translator has to keep in step by hand.
     */
    private val indexOwnedKeys = setOf(
        "search_languages_subtitle",
        // The sticker editor has no row of its own: it opens from a photo the
        // user picked or from a sticker they tapped, so the index describes it
        // and lands on the pack list instead.
        "import_sticker_editor_subtitle",
    )

    @Test
    fun `the index names only strings this module defines`() {
        val missing = indexedKeys.filterNot { it in definedKeys }
        assertEquals("R.string names in the index with no resource", emptyList<String>(), missing)
    }

    @Test
    fun `every string the index names is drawn by a settings screen`() {
        val orphans = indexedKeys
            .filterNot { it in keysDrawnByScreens }
            .filterNot { it in indexOwnedKeys }
        assertEquals("indexed rows no longer drawn by any screen", emptyList<String>(), orphans)
    }

    @Test
    fun `the index owns no wording it does not have to`() {
        val unused = indexOwnedKeys.filterNot { it in indexedKeys }
        assertEquals("index-owned strings the index stopped using", emptyList<String>(), unused)
    }

    @Test
    fun `every screen a result can open has an icon`() {
        // The routes are the only lower-case string literals in the index.
        val routes = Regex("\"([a-z][a-z_]*)\"")
            .findAll(indexSource)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        assertTrue("no routes found in the index", routes.size > 20)
        val missing = routes.filterNot { it in SettingsRouteIcons }
        assertEquals("routes with no icon in SettingsRouteIcons", emptyList<String>(), missing)
    }

    // ---- ranking ----

    private fun row(
        title: String,
        subtitle: String = "",
        screen: String = "Typing",
        route: String = "typing",
        weight: EntryWeight = EntryWeight.NORMAL,
        tool: ToolbarTool? = null,
    ) = SettingsSearchEntry(title, subtitle, screen, route, weight, tool)

    /**
     * A stand-in for the real index: one entry for every ranking rule the tests
     * below check, and nothing else.
     */
    private val fixture: List<SettingsSearchEntry> = listOf(
        row("Autocorrect", "Fix typos automatically when you press space"),
        row("Block offensive words", "Keep the words that autocorrect offers clean"),
        row("Number row", "Show a dedicated digit row above the letters", "Layout & size", "layout"),
        row("Emoji row", "A dedicated row of your emoji", "Emoji", "emoji"),
        row(
            "Keyboard themes",
            "Light, dark and AMOLED themes",
            "Appearance",
            "themes",
            weight = EntryWeight.SECTION,
        ),
        row("Themes", "Change the theme", "Tools › Themes", "tool/THEMES", tool = ToolbarTool.THEMES),
        row(
            "Sticker packs",
            "Make, edit, import and export packs of your own",
            "Tools › Stickers",
            "tool/STICKER",
            tool = ToolbarTool.STICKER,
        ),
        row(
            "Sticker packs",
            "Your own sticker packs, images and all",
            "Backup & restore",
            "backup",
            weight = EntryWeight.MIRROR,
        ),
        row("Klipy API key", "Free from partner.klipy.com", "Tools › GIF", "tool/GIF", tool = ToolbarTool.GIF),
        row(
            "Include API keys",
            "Translate, GIF, search and AI keys",
            "Backup & restore",
            "backup",
            weight = EntryWeight.MIRROR,
        ),
    )

    @Test
    fun `search ranks an exact title above a subtitle mention`() {
        val results = searchSettings("autocorrect", fixture)
        assertTrue(results.isNotEmpty())
        assertEquals("Autocorrect", results.first().title)
    }

    @Test
    fun `every token must match`() {
        // "row" alone hits several settings; pairing it narrows to number row.
        val results = searchSettings("number row", fixture)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { "number" in (it.title + it.subtitle + it.screen).lowercase() })
    }

    @Test
    fun `a query matching nothing returns nothing`() {
        assertEquals(emptyList<SettingsSearchEntry>(), searchSettings("zzzznotasetting", fixture))
    }

    @Test
    fun `a blank query returns nothing`() {
        assertEquals(emptyList<SettingsSearchEntry>(), searchSettings("   ", fixture))
    }

    @Test
    fun `a screen outranks the toolbar shortcut that opens it`() {
        // "Themes" is an exact hit on the toolbar tool and only a word of the
        // screen's name, so raw scoring put the shortcut on top.
        val results = searchSettings("themes", fixture)
        assertTrue(results.isNotEmpty())
        assertEquals("themes", results.first().route)
    }

    @Test
    fun `a feature outranks the backup toggle named after it`() {
        val results = searchSettings("sticker packs", fixture)
        assertTrue(results.isNotEmpty())
        assertEquals("Tools › Stickers", results.first().screen)
        // The toggle is still findable, just not first.
        assertTrue(results.any { it.route == "backup" })
    }

    @Test
    fun `a setting outranks the backup toggle that includes it`() {
        val results = searchSettings("api key", fixture)
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().route.startsWith("tool/"))
    }
}
