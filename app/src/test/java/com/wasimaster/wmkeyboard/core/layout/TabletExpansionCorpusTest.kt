package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.settings.DeviceForm
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet expansion, run over every layout the app ships — 18 built in plus
 * 354 assets, on both tablet forms with the digit row on and off.
 *
 * `TabletExpansionTest` pins the arithmetic on grids it builds by hand; this
 * pins it against reality. The transform relocates keys by role across 393
 * genuinely different grids — Bengali and Devanagari rows twelve wide, Arabic
 * and Hebrew right-to-left, five-row Khmer, layouts whose top row already
 * overflows their own grid weight — and the only honest way to know it survives
 * all of them is to run it on all of them.
 *
 * Read from disk rather than through an AssetManager, the same way
 * [AssetLayoutsTest] does, so this needs no device.
 */
class TabletExpansionCorpusTest {

    private data class Case(val id: String, val letters: KeyboardLayout)

    private val corpus: List<Case> = buildList {
        BuiltInLayouts.all.forEach { add(Case(it.id, it.compile(LayoutLayer.LETTERS))) }
        File("src/main/assets/layouts")
            .listFiles { f -> f.name.endsWith(".${LayoutFile.FILE_EXTENSION}") }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val spec = LayoutFile.decode(file.readText())!!.layout
                add(Case(file.name.removeSuffix(".${LayoutFile.FILE_EXTENSION}"), spec.compile(LayoutLayer.LETTERS)))
            }
    }

    private val forms = listOf(DeviceForm.SMALL_TABLET, DeviceForm.LARGE_TABLET)

    private fun List<Key>.width() = sumOf { it.width.toDouble() }.toFloat()

    @Test
    fun `the corpus is the whole shipped set`() {
        assertEquals("built-ins plus assets", 18 + 354, corpus.size)
    }

    /**
     * The exact set of layouts that decline, spelled out.
     *
     * An assertion on the count alone would pass while the transform silently
     * started rejecting Bengali and accepting braille. These fourteen are the
     * grids where a wide alphabetic layout is not what the user is looking at:
     * kana flick pads and braille are typed by position, morse by timing, and
     * the rest have no shift key to mirror.
     */
    @Test
    fun `exactly the non-alphabetic layouts decline`() {
        val declined = corpus
            .filter { tabletGridWidth(it.letters, DeviceForm.LARGE_TABLET) == null }
            .map { it.id }
            .toSet()

        assertEquals(
            setOf(
                "braille_chord", "ja_flick", "ja_kana_jis", "morse", "zh_stroke",
                "ipa", "iu_syllabics", "music", "nqo_nko", "syc_syriac",
                "zh_cangjie", "zh_cangjie_quick", "zh_pinyin_t9", "zh_zhuyin",
            ),
            declined,
        )
    }

    @Test
    fun `a declining layout is handed back untouched, on every form`() {
        for (case in corpus) {
            for (form in forms) {
                if (tabletGridWidth(case.letters, form) != null) continue
                for (numberRow in listOf(true, false)) {
                    assertSame(
                        "${case.id} $form",
                        case.letters,
                        case.letters.expandForTablet(form, numberRow),
                    )
                }
            }
        }
    }

    @Test
    fun `every expansion stays inside the layout shape limits`() {
        forEachExpansion { where, _, after ->
            after.rows.forEach { row ->
                assertTrue("$where: ${row.size} keys in a row", row.size <= MaxKeysPerRow)
                assertTrue("$where: row ${row.width()} wide", row.width() <= MaxRowWidth)
                row.forEach { key ->
                    assertTrue("$where: key width ${key.width}", key.width > 0f)
                    assertTrue("$where: key width ${key.width}", key.width <= MaxKeyWidth)
                }
            }
        }
    }

    /**
     * The constraint the whole feature rests on: `LayoutSet.rowSpan` and
     * `keyRowsHeight` size the IME window from layer row *counts*, and neither
     * sees this transform. A row added here draws a keyboard taller than the
     * window that was measured for it.
     */
    @Test
    fun `no expansion changes the row count or the row heights`() {
        forEachExpansion { where, before, after ->
            assertEquals("$where: row count", before.rows.size, after.rows.size)
            assertEquals("$where: row heights", before.rowHeights, after.rowHeights)
        }
    }

    @Test
    fun `every expansion keeps exactly one of each essential key`() {
        forEachExpansion { where, _, after, numberRow ->
            fun count(pred: (Key) -> Boolean) = after.rows.sumOf { it.count(pred) }
            assertEquals("$where: space", 1, count { it.action == KeyAction.Space })
            assertEquals("$where: enter", 1, count { it.action == KeyAction.Enter })
            assertEquals("$where: emoji", 1, count { it.action == KeyAction.Emoji })
            // Backspace moves onto the digit row when that row is drawn, and
            // expandNumberRowForTablet puts it there. The two answers have to
            // agree, or the board has no backspace at all.
            assertEquals(
                "$where: delete",
                if (numberRow) 0 else 1,
                count { it.action == KeyAction.Delete },
            )
            assertEquals(
                "$where: shift",
                if (numberRow) 2 else 1,
                count { it.action == KeyAction.Shift },
            )
        }
    }

    @Test
    fun `no expansion loses a character key`() {
        forEachExpansion { where, before, after ->
            val text = { grid: KeyboardLayout ->
                grid.rows.flatten()
                    .filter { it.action == KeyAction.Text }
                    .map { it.output ?: it.label }
            }
            val lost = text(before) - text(after).toSet()
            assertTrue("$where: lost $lost", lost.isEmpty())
        }
    }

    /**
     * `roleIn` only infers a role positionally on the *bottom* row. Once the
     * comma moves up to the shift row, a layout that relied on that inference
     * would silently lose its email and URI adaptation — the `@` key would stop
     * appearing — with nothing on screen to explain why.
     */
    @Test
    fun `relocated punctuation carries its role explicitly`() {
        forEachExpansion { where, _, after ->
            val shiftRow = after.rows[after.rows.lastIndex - 1]
            assertTrue(
                "$where: comma lost its role",
                shiftRow.any { it.role == KeyRole.Comma },
            )
        }
    }

    /** Every eligible layout, both forms, digit row on and off. */
    private fun forEachExpansion(
        check: (where: String, before: KeyboardLayout, after: KeyboardLayout, numberRow: Boolean) -> Unit,
    ) {
        var ran = 0
        for (case in corpus) {
            for (form in forms) {
                if (tabletGridWidth(case.letters, form) == null) continue
                for (numberRow in listOf(true, false)) {
                    val after = case.letters.expandForTablet(form, numberRow)
                    check("${case.id} $form numberRow=$numberRow", case.letters, after, numberRow)
                    ran++
                }
            }
        }
        assertTrue("no expansions ran", ran > 1000)
    }

    private fun forEachExpansion(
        check: (where: String, before: KeyboardLayout, after: KeyboardLayout) -> Unit,
    ) = forEachExpansion { where, before, after, _ -> check(where, before, after) }
}
