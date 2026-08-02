package com.wasimaster.wmkeyboard.core.icons

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The icon pack the sample addon repository actually publishes, imported
 * through the real code path.
 *
 * `IconPackFileTest` covers the format with packs it builds itself; this one
 * guards the other direction — that what a publisher ships, and what the addon
 * installer downloads, is something this build can read.
 */
class IconPackFixtureTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the published Lucide pack imports`() {
        val store = IconPackStore(File(temp.root, "iconpacks").apply { mkdirs() })
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("addons/lucide.wmicons")) {
            "missing test fixture addons/lucide.wmicons"
        }
        // The import has no context, so the caller resolves
        // `core_icons_pack_imported_label` and hands the name in. This pack
        // names itself, so the default never gets used.
        val result = stream.use { IconPackFile.import(it, store, defaultName = "Imported icons") }
        assertTrue("import said: $result", result is IconImportResult.Imported)
        val pack = (result as IconImportResult.Imported).pack
        assertTrue("no slots survived import", pack.slots.isNotEmpty())
        assertTrue("pack was not registered", store.packs().any { it.id == pack.id })
    }
}
