package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.keyman.KeymanResult
import com.wasimaster.wmkeyboard.core.keyman.KeymanTouchLayoutReader
import com.wasimaster.wmkeyboard.core.keyman.TouchLayoutConverter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The committed Keyman layouts still match what the converter produces.
 *
 * 862 of the shipped grids are generated, and generated files rot in two
 * directions. Someone edits one by hand and the next pipeline run silently
 * reverts it; or the converter changes and nobody reruns the pipeline, so the
 * committed assets quietly stop being what the code would make. Neither shows up
 * anywhere else: every other test reads the committed file and finds it valid,
 * because it is.
 *
 * This converts the vendored source layouts and compares. It covers six
 * keyboards rather than all 862, because the sources for the rest are a 78 MB
 * checkout that no ordinary build should need; `KeymanPipelineTest` sweeps the
 * whole corpus when that checkout is present. Six is enough to catch a converter
 * change, which is what actually happens.
 */
class KeymanConversionParityTest {

    private val fixtures = File("../core/keyman/src/test/resources/touch")

    private fun asset(id: String): File = File("src/main/assets/layouts/kmn_$id.wmlayout.json")

    @Test
    fun `the vendored sources are still there`() {
        assertTrue(
            "touch-layout fixtures missing at ${fixtures.absolutePath}",
            fixtures.isDirectory,
        )
    }

    @Test
    fun `every committed layout matches a fresh conversion of its source`() {
        val checked = mutableListOf<String>()
        for (source in fixtures.listFiles().orEmpty().sortedBy { it.name }) {
            if (!source.name.endsWith(".keyman-touch-layout")) continue
            val id = source.name.removeSuffix(".keyman-touch-layout")
            val committed = asset(id)
            // Not every fixture is bundled: some exist to exercise the reader.
            if (!committed.isFile) continue

            val doc = KeymanTouchLayoutReader.parse(source.readText())
            assertTrue("$id no longer parses", doc is KeymanResult.Success)
            val converted = TouchLayoutConverter.convert(
                (doc as KeymanResult.Success).value,
                id,
                id,
            )
            assertTrue("$id no longer converts", converted is KeymanResult.Success)
            val fresh = (converted as KeymanResult.Success).value.layout

            val stored = LayoutFile.decode(committed.readText())?.layout
            assertTrue("${committed.name} did not decode", stored != null)

            // The name and language are assigned by the pipeline from the .kps
            // and are not the converter's output, so they are not compared. The
            // grid is, key for key, which is the part that would drift.
            assertEquals(
                "${committed.name} has different layers than a fresh conversion",
                fresh.layers.keys,
                stored!!.layers.keys,
            )
            for ((name, layer) in fresh.layers) {
                assertEquals(
                    "${committed.name}: layer '$name' differs from a fresh conversion. " +
                        "Rerun KeymanPipelineTest and commit the result.",
                    layer.rows,
                    stored.layers.getValue(name).rows,
                )
            }
            assertEquals(
                "${committed.name}: tablet expansion opt-in differs",
                fresh.tabletExpand,
                stored.tabletExpand,
            )
            checked += id
        }
        assertTrue(
            "no committed Keyman layout was checked; are the fixtures or the assets missing?",
            checked.size >= 4,
        )
        println("keyman parity: ${checked.size} layouts match a fresh conversion")
    }

    /**
     * Every committed Keyman layout names the keyboard its rules come from.
     * Without a binding the engine has nothing to look up, so the layout would
     * type its key caps for ever with no way for the user to fix it.
     */
    @Test
    fun `every committed keyman layout carries a rule binding`() {
        val assets = File("src/main/assets/layouts")
            .listFiles { f -> f.name.startsWith("kmn_") }
            .orEmpty()
        assertTrue("no Keyman layouts are committed", assets.isNotEmpty())

        val missing = assets.mapNotNull { file ->
            val layout = LayoutFile.decode(file.readText())?.layout
            when {
                layout == null -> "${file.name} (did not decode)"
                layout.keyman == null -> "${file.name} (no binding)"
                layout.keyman?.keyboardId.isNullOrBlank() -> "${file.name} (blank keyboard id)"
                else -> null
            }
        }
        assertEquals("committed Keyman layouts without a usable binding", emptyList<String>(), missing)
    }

    /**
     * The binding must name the keyboard the file is named after. A mismatch
     * downloads one keyboard's rules and runs them against another's grid, which
     * types plausible-looking nonsense rather than failing.
     */
    @Test
    fun `each binding names the keyboard the file is named after`() {
        val wrong = File("src/main/assets/layouts")
            .listFiles { f -> f.name.startsWith("kmn_") }
            .orEmpty()
            .mapNotNull { file ->
                val id = file.name.removePrefix("kmn_").removeSuffix(".wmlayout.json")
                val binding = LayoutFile.decode(file.readText())?.layout?.keyman ?: return@mapNotNull null
                if (binding.keyboardId == id) null else "${file.name} -> ${binding.keyboardId}"
            }
        assertEquals("bindings pointing at another keyboard", emptyList<String>(), wrong)
    }
}
