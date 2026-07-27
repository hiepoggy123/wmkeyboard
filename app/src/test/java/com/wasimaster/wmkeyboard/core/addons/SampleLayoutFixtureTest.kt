package com.wasimaster.wmkeyboard.core.addons

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutFile
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSeverity
import com.wasimaster.wmkeyboard.core.layout.validateLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout the sample repository ships, run through the importer that will
 * actually receive it.
 *
 * A repository payload that the app refuses is a broken addon, and the failure
 * only shows up on a phone. Checking the shipped file here is the cheap half of
 * that.
 */
class SampleLayoutFixtureTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("addons/$name")) {
            "missing test fixture addons/$name"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun `the braille layout imports cleanly`() {
        val imported = LayoutFile.decode(fixture("braille.wmlayout.json"))
        assertNotNull("the sample braille layout did not decode", imported)
        imported!!
        assertEquals(emptyList<String>(), imported.repairs)

        val findings = validateLayout(imported.layout)
        val blocking = findings
            .filter { it.severity == LayoutSeverity.BLOCKING }
            .map { it.message }
        assertEquals(emptyList<String>(), blocking)
    }

    @Test
    fun `the braille layout types braille cells, with the latin letter on long-press`() {
        val layout = LayoutFile.decode(fixture("braille.wmlayout.json"))!!.layout
        val keys = layout.layer(LayoutLayer.LETTERS)!!.rows.flatten()
        val q = keys.first { it.longPress.firstOrNull() == "q" }
        assertEquals("⠟", q.label)
        assertEquals("⠟", q.output)

        // Every cell it commits is in the Unicode braille block, which is the
        // whole point — otherwise it is just QWERTY with odd labels.
        val committed = keys.filter { it.action == KeyAction.Text && it.longPress.isNotEmpty() }
        assertTrue("nothing committed", committed.isNotEmpty())
        for (key in committed) {
            val code = key.label.single().code
            assertTrue("${key.label} is not a braille cell", code in 0x2800..0x28FF)
        }
    }

    @Test
    fun `the braille layout does not collide with a layout the app already ships`() {
        // The repository's previous layout used the id of a shipped asset
        // layout, so installing it showed the same name twice. An addon layout
        // has to be its own thing.
        val layout = LayoutFile.decode(fixture("braille.wmlayout.json"))!!.layout
        assertEquals(null, BuiltInLayouts.byId(layout.id))
        assertTrue(
            "an addon layout must not reuse a shipped asset id",
            !layout.id.startsWith("asset_"),
        )
    }
}
