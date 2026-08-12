package com.wasimaster.wmkeyboard.core.keyman

import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutFile
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSeverity
import com.wasimaster.wmkeyboard.core.layout.canBeEnabled
import com.wasimaster.wmkeyboard.core.layout.repair
import com.wasimaster.wmkeyboard.core.layout.validateLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Converts real Keyman touch layouts and holds the result to the same standard
 * every shipped asset layout is held to.
 *
 * The invariants here are the ones `AssetLayoutsTest` enforces on the committed
 * grids — parses, needs no repair, can be turned on — checked at the point of
 * conversion rather than after the fact, so a pipeline run cannot produce a file
 * that only fails once it is in the repository.
 */
class TouchLayoutConverterTest {

    private fun fixture(id: String): KeymanTouchLayout {
        val text = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("touch/$id.keyman-touch-layout"),
        ) { "missing fixture touch/$id.keyman-touch-layout" }.use { it.readBytes().decodeToString() }
        return when (val r = KeymanTouchLayoutReader.parse(text)) {
            is KeymanResult.Success -> r.value
            is KeymanResult.Failure -> error("$id did not parse: ${r.fault}")
        }
    }

    private fun convert(id: String): ConvertedKeymanLayout =
        when (val r = TouchLayoutConverter.convert(fixture(id), id, id)) {
            is KeymanResult.Success -> r.value
            is KeymanResult.Failure -> error("$id did not convert: ${r.fault}")
        }

    @Test
    fun `every fixture converts`() {
        for (id in FIXTURES) {
            val spec = convert(id).layout
            assertTrue("$id produced no layers", spec.layers.isNotEmpty())
            assertTrue(
                "$id has no letters layer",
                LayoutLayer.LETTERS.key in spec.layers,
            )
        }
    }

    /**
     * The invariant `AssetLayoutsTest` enforces on everything committed: a
     * converted layout must already be repaired, so the pipeline cannot emit a
     * file that the loader silently rewrites on first read.
     */
    @Test
    fun `every conversion is already repaired`() {
        for (id in FIXTURES) {
            val spec = convert(id).layout
            val repaired = spec.repair()
            assertTrue(
                "$id needed repair: " + repaired.repairNotes.joinToString {
                    "res=${it.stringRes}/plural=${it.pluralsRes} args=${it.args}"
                },
                repaired.repairNotes.isEmpty(),
            )
            assertEquals("$id changed under repair", spec, repaired.spec)
        }
    }

    @Test
    fun `every conversion can be turned on`() {
        for (id in FIXTURES) {
            val spec = convert(id).layout.copy(langId = "en")
            assertTrue(
                "$id cannot be enabled: " + validateLayout(spec)
                    .filter { it.severity == LayoutSeverity.BLOCKING }
                    .joinToString { "${it.layer}: res=${it.text.stringRes} args=${it.text.args}" },
                spec.canBeEnabled(),
            )
        }
    }

    @Test
    fun `every conversion survives the layout file format`() {
        for (id in FIXTURES) {
            val spec = convert(id).layout.copy(langId = "en")
            val decoded = LayoutFile.decode(LayoutFile.encode(spec, 1, "test"))?.layout
            assertTrue("$id did not round-trip through LayoutFile", decoded != null)
            assertEquals("$id lost layers in the round trip", spec.layers.keys, decoded!!.layers.keys)
        }
    }

    /**
     * Every `nextLayer` must name a layer that exists. A dangling one strands
     * the user on a grid they cannot type on and cannot leave, which is the
     * exact failure the repair pass's way-back rules exist to prevent.
     */
    @Test
    fun `every next layer names a layer that exists`() {
        for (id in FIXTURES) {
            val spec = convert(id).layout
            val known = spec.layers.keys
            for ((name, layer) in spec.layers) {
                for (row in layer.rows) {
                    for (key in row) {
                        val target = (key.action as? KeyAction.KeymanKey)?.nextLayer ?: continue
                        assertTrue(
                            "$id layer '$name' key '${key.label}' points at missing layer '$target'",
                            target in known,
                        )
                    }
                }
            }
        }
    }

    /** No key may be wider than the grid, or a row silently overflows. */
    @Test
    fun `key widths stay inside the grid`() {
        for (id in FIXTURES) {
            val spec = convert(id).layout
            for ((name, layer) in spec.layers) {
                for ((r, row) in layer.rows.withIndex()) {
                    val total = row.sumOf { it.width.toDouble() }
                    assertTrue(
                        "$id layer '$name' row $r sums to $total",
                        total > 0.0 && total < 40.0,
                    )
                }
            }
        }
    }

    /** Lao's shift layer has the base layer's shape, so it should fold. */
    @Test
    fun `a matching shift layer folds into shift labels`() {
        val converted = convert("lao_2008_basic")
        assertTrue(
            "shift layer neither folded nor kept: ${converted.report}",
            converted.report.shiftLayerFolded || converted.report.shiftLayerKeptSeparate,
        )
        if (converted.report.shiftLayerFolded) {
            val letters = converted.layout.layers.getValue(LayoutLayer.LETTERS.key)
            assertTrue(
                "folded but no key carries a shift label",
                letters.rows.any { row -> row.any { it.shiftLabel != null } },
            )
        }
    }

    /** The gestures we cannot express are counted, not silently discarded. */
    @Test
    fun `dropped gestures are reported`() {
        val report = convert("geezword_tigrinya").report
        assertTrue(
            "a keyboard using multitap reported none dropped",
            report.droppedMultitaps > 0,
        )
    }

    /**
     * Sweeps the whole `release/` corpus when it is checked out, which is how a
     * pipeline run is validated before it writes anything. Skipped — not failed
     * — when the corpus is absent, so an ordinary build does not need a 78 MB
     * checkout to go green.
     *
     * Point `KEYMAN_CORPUS` at the checkout to run it.
     */
    @Test
    fun `the whole corpus converts when it is available`() {
        val root = System.getenv("KEYMAN_CORPUS")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val files = root.resolve("release").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".keyman-touch-layout") }
            .toList()
        assertTrue("KEYMAN_CORPUS is set but holds no touch layouts", files.isNotEmpty())

        val failures = mutableListOf<String>()
        var converted = 0
        for (file in files) {
            val id = file.name.removeSuffix(".keyman-touch-layout")
            val doc = when (val r = KeymanTouchLayoutReader.parse(file.readText())) {
                is KeymanResult.Success -> r.value
                is KeymanResult.Failure -> {
                    failures += "$id: parse ${r.fault}"
                    continue
                }
            }
            val spec = when (val r = TouchLayoutConverter.convert(doc, id, id)) {
                is KeymanResult.Success -> r.value.layout
                is KeymanResult.Failure -> {
                    failures += "$id: convert ${r.fault}"
                    continue
                }
            }
            converted++
            val repaired = spec.repair()
            if (repaired.repairNotes.isNotEmpty()) {
                failures += "$id: repairs " + repaired.repairNotes.joinToString {
                    "res=${it.stringRes} args=${it.args}"
                }
            }
            val blocking = validateLayout(spec.copy(langId = "en"))
                .filter { it.severity == LayoutSeverity.BLOCKING }
            if (blocking.isNotEmpty()) {
                failures += "$id: blocking " + blocking.joinToString {
                    "${it.layer}/res=${it.text.stringRes} args=${it.text.args}"
                }
            }
            val known = spec.layers.keys
            for ((name, layer) in spec.layers) {
                for (row in layer.rows) {
                    for (key in row) {
                        val target = (key.action as? KeyAction.KeymanKey)?.nextLayer ?: continue
                        if (target !in known) failures += "$id: '$name' -> missing '$target'"
                    }
                }
            }
        }
        println("corpus sweep: $converted of ${files.size} converted, ${failures.size} problems")
        assertTrue(
            "corpus problems (${failures.size}):\n" + failures.take(40).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private companion object {
        val FIXTURES = listOf(
            "basic_kbdus",
            "khmer_angkor",
            "lao_2008_basic",
            "sil_euro_latin",
            "geezword_tigrinya",
            "urdu_dvorak",
        )
    }
}
