package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guardrail the JSON asset layouts have instead of the compiler the Kotlin
 * [BuiltInLayouts] enjoy: every shipped `.wmlayout.json` under `assets/layouts`
 * must parse as a layout file, need **zero** repairs (a repair means the file is
 * wrong, not just old), be safe to enable, and name a language the registry
 * knows. A typo in one of these files would otherwise ship a broken keyboard for
 * that language and be caught only on a device.
 *
 * The files are read from disk rather than through an [android.content.res.AssetManager]
 * (which needs a device), the same way the emoji-catalog test reaches the
 * shipped catalog.
 */
class AssetLayoutsTest {

    private val layoutFiles: List<File> =
        File("src/main/assets/layouts")
            .listFiles { f -> f.name.endsWith(".${LayoutFile.FILE_EXTENSION}") }
            .orEmpty()
            .sortedBy { it.name }

    @Test
    fun `there are asset layouts to check`() {
        assertTrue("no asset layouts found under assets/layouts", layoutFiles.isNotEmpty())
    }

    @Test
    fun `every asset layout parses and needs no repair`() {
        for (file in layoutFiles) {
            val imported = LayoutFile.decode(file.readText())
            assertNotNull("${file.name} is not a valid layout file", imported)
            assertEquals(
                "${file.name} needed repairs: ${imported!!.repairs}",
                emptyList<String>(),
                imported.repairs,
            )
        }
    }

    @Test
    fun `every asset layout is safe to enable`() {
        for (file in layoutFiles) {
            val layout = LayoutFile.decode(file.readText())!!.layout
            assertTrue(
                "${file.name} (${layout.id}) cannot be enabled: " +
                    validateLayout(layout).filter { it.severity == LayoutSeverity.BLOCKING },
                layout.canBeEnabled(),
            )
        }
    }

    @Test
    fun `every asset layout names a known language`() {
        for (file in layoutFiles) {
            val layout = LayoutFile.decode(file.readText())!!.layout
            assertTrue(
                "${file.name} has a blank langId",
                layout.langId.isNotBlank(),
            )
            assertTrue(
                "${file.name} names langId '${layout.langId}', which the registry does not know",
                LanguageRegistry.byId(layout.langId) !== LanguageRegistry.GENERIC,
            )
        }
    }

    @Test
    fun `the japanese flick layout carries flick arms and a kana-variant key`() {
        val file = layoutFiles.first { it.name == "ja_flick.${LayoutFile.FILE_EXTENSION}" }
        val keys = LayoutFile.decode(file.readText())!!.layout
            .layers.values.flatMap { it.rows.flatten() }
        // The あ key flicks to the other vowels of its row.
        val a = keys.first { it.label == "あ" }
        assertEquals("い", a.flick[FlickDirection.LEFT])
        assertEquals("う", a.flick[FlickDirection.UP])
        assertEquals("お", a.flick[FlickDirection.DOWN])
        // The 小゛゜ key cycles small/dakuten forms.
        assertTrue(
            "the flick pad has no kana-variant key",
            keys.any { it.action == KeyAction.KanaVariant },
        )
    }

    @Test
    fun `asset layout ids are unique and never shadow a built-in`() {
        val builtInIds = BuiltInLayouts.all.mapTo(HashSet()) { it.id }
        val seen = mutableSetOf<String>()
        for (file in layoutFiles) {
            val id = LayoutFile.decode(file.readText())!!.layout.id
            assertTrue("${file.name} reuses id '$id'", seen.add(id))
            assertTrue(
                "${file.name} id '$id' collides with a built-in layout",
                id !in builtInIds,
            )
        }
    }
}
