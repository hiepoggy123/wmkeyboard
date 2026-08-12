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
                "${file.name} needed repairs: ${imported!!.repairNotes}",
                emptyList<LayoutMessage>(),
                imported.repairNotes,
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

    /**
     * None of the shipped files carries the field, so all of them get the
     * default. Pinned because flipping that default would opt 1,256 layouts out of
     * the tablet grid in one edit, and nothing else would notice.
     */
    @Test
    fun `every asset layout opts in to tablet expansion`() {
        for (file in layoutFiles) {
            val layout = LayoutFile.decode(file.readText())!!.layout
            assertTrue("${file.name} opted out of tablet expansion", layout.tabletExpand)
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
    fun `the t9 pinyin layout maps letter groups to keypad digits`() {
        val file = layoutFiles.first { it.name == "zh_pinyin_t9.${LayoutFile.FILE_EXTENSION}" }
        val keys = LayoutFile.decode(file.readText())!!.layout
            .layers.values.flatMap { it.rows.flatten() }
        // The pad types digits; the letters are only the label. 6=MNO, 4=GHI, 2=ABC
        // is what makes 64 → ni and 426 → hao.
        assertEquals("6", keys.first { it.label == "MNO" }.output)
        assertEquals("4", keys.first { it.label == "GHI" }.output)
        assertEquals("2", keys.first { it.label == "ABC" }.output)
        assertEquals("9", keys.first { it.label == "WXYZ" }.output)
        // 分词 types the apostrophe the segmenter treats as a forced boundary.
        assertEquals("'", keys.first { it.label == "分词" }.output)
        // Every letter group 2-9 is present exactly once.
        val digits = keys.mapNotNull { it.output }.filter { it.length == 1 && it[0].isDigit() }
        assertEquals(listOf("2", "3", "4", "5", "6", "7", "8", "9"), digits.sorted())
    }

    @Test
    fun `the zhuyin layout carries the full bopomofo alphabet and its tone marks`() {
        val file = layoutFiles.first { it.name == "zh_zhuyin.${LayoutFile.FILE_EXTENSION}" }
        val layout = LayoutFile.decode(file.readText())!!.layout
        val typed = layout.layers.values.flatMap { it.rows.flatten() }
            .filter { it.action == KeyAction.Text }
            .map { it.output ?: it.label }
        // All 37 bopomofo symbols must be reachable, or some syllable becomes
        // untypeable — ㄅ through ㄦ is the contiguous Unicode block.
        val bopomofo = ('ㄅ'..'ㄩ').toSet()
        val missing = bopomofo.filter { it.toString() !in typed }
        assertTrue("bopomofo with no key: $missing", missing.isEmpty())
        // Tones 2/3/4 and the neutral tone; tone 1 is unmarked by design.
        for (tone in listOf("ˊ", "ˇ", "ˋ", "˙")) {
            assertTrue("no key types tone mark $tone", tone in typed)
        }
        // Standard 大千 positions: first key of each symbol row.
        val rows = layout.layers.getValue("letters").rows
        assertEquals("ㄅ", rows[0].first().label)
        assertEquals("ㄆ", rows[1].first().label)
        assertEquals("ㄇ", rows[2].first().label)
        assertEquals("ㄈ", rows[3].first().label)
    }

    @Test
    fun `the cangjie layouts carry all 25 radicals and type their letters`() {
        for (name in listOf("zh_cangjie", "zh_cangjie_quick")) {
            val file = layoutFiles.first { it.name == "$name.${LayoutFile.FILE_EXTENSION}" }
            val keys = LayoutFile.decode(file.readText())!!.layout
                .layers.values.flatMap { it.rows.flatten() }
                .filter { it.action == KeyAction.Text && it.output != null }
            // Cangjie's alphabet is a-y: 25 radicals, no z. A missing one makes
            // every character spelled with it untypeable.
            assertEquals(
                "$name does not cover a-y",
                ('a'..'y').map { it.toString() },
                keys.mapNotNull { it.output }.filter { it.length == 1 }.sorted(),
            )
            // The key shows the radical but types the letter.
            assertEquals("$name: a should be 日", "日", keys.first { it.output == "a" }.label)
            assertEquals("$name: y should be 卜", "卜", keys.first { it.output == "y" }.label)
        }
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
