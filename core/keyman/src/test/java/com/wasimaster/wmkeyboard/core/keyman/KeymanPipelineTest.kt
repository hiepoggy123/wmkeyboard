package com.wasimaster.wmkeyboard.core.keyman

import com.wasimaster.wmkeyboard.core.layout.LayoutFile
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ingest pipeline: turns a checkout of `keymanapp/keyboards` into the files
 * this repository commits.
 *
 * ## Why this is a test
 *
 * It needs the real [TouchLayoutConverter], which needs `LayoutSpec`, which
 * reaches `AssetManager` and Compose's `@Immutable` through its own
 * dependencies. A `kotlin("jvm")` module cannot compile that, so the
 * `:tools:dictc` trick of symlinking sources into a plain JVM module does not
 * carry over. An Android library's unit-test runtime *is* the supported way to
 * run JVM code against that library's classes, so the generator lives here and
 * runs the same code the app runs. Nothing is duplicated, so nothing can drift.
 *
 * ## Running it
 *
 * ```
 * KEYMAN_CORPUS=/path/to/keyboards KEYMAN_OUT=/path/to/out \
 *   ./gradlew :core:keyman:testFullDebugUnitTest --tests '*KeymanPipelineTest*' --rerun-tasks
 * ```
 *
 * Without both variables it does nothing and passes, so an ordinary build needs
 * no checkout.
 */
class KeymanPipelineTest {

    @Test
    fun `generate the committed artifacts`() {
        val corpus = System.getenv("KEYMAN_CORPUS")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val out = System.getenv("KEYMAN_OUT")?.let(::File) ?: return
        val layoutsDir = File(out, "layouts").apply { mkdirs() }

        val release = File(corpus, "release")
        assertTrue("no release/ in $corpus", release.isDirectory)

        val rows = mutableListOf<SourceRow>()
        val skipped = mutableListOf<String>()
        var rawBytes = 0L

        for (dir in release.listFiles().orEmpty().sortedBy { it.name }) {
            if (!dir.isDirectory) continue
            for (board in dir.listFiles().orEmpty().sortedBy { it.name }) {
                if (!board.isDirectory) continue
                val id = board.name
                val source = File(board, "source")
                val touch = File(source, "$id.keyman-touch-layout")
                if (!touch.isFile) {
                    skipped += "$id: no touch layout"
                    continue
                }

                val doc = when (val r = KeymanTouchLayoutReader.parse(touch.readText())) {
                    is KeymanResult.Success -> r.value
                    is KeymanResult.Failure -> {
                        skipped += "$id: parse ${r.fault}"
                        continue
                    }
                }

                val meta = readKps(File(source, "$id.kps"))
                val converted = when (
                    val r = TouchLayoutConverter.convert(doc, id, meta.name.ifBlank { id })
                ) {
                    is KeymanResult.Success -> r.value
                    is KeymanResult.Failure -> {
                        skipped += "$id: convert ${r.fault}"
                        continue
                    }
                }

                val langId = meta.languages.firstOrNull()?.let(::canonicalTag) ?: ""
                val spec = converted.layout.copy(
                    langId = langId,
                    keyman = com.wasimaster.wmkeyboard.core.layout.KeymanBinding(
                        keyboardId = id,
                        version = meta.version,
                    ),
                )

                val json = LayoutFile.encodeAsset(spec)
                val file = File(layoutsDir, "kmn_$id.${LayoutFile.FILE_EXTENSION}")
                file.writeText(json)
                rawBytes += json.length.toLong()

                rows += SourceRow(
                    layoutId = spec.id,
                    keyboardId = id,
                    version = meta.version,
                    copyright = meta.copyright,
                    langTags = meta.languages.map(::canonicalTag),
                    droppedMultitaps = converted.report.droppedMultitaps,
                    droppedFlicks = converted.report.droppedDiagonalFlicks,
                    repairs = converted.repairNotes.size,
                )
            }
        }

        File(out, "keyman-sources.tsv").writeText(
            buildString {
                appendLine(
                    listOf(
                        "layout_id", "keyman_id", "version", "copyright",
                        "lang_tags", "dropped_multitaps", "dropped_flicks", "repairs",
                    ).joinToString("\t"),
                )
                for (r in rows.sortedBy { it.keyboardId }) {
                    appendLine(
                        listOf(
                            r.layoutId, r.keyboardId, r.version, r.copyright.replace('\t', ' '),
                            r.langTags.joinToString(" "), r.droppedMultitaps,
                            r.droppedFlicks, r.repairs,
                        ).joinToString("\t"),
                    )
                }
            },
        )

        File(out, "skipped.txt").writeText(skipped.joinToString("\n"))

        val tags = rows.flatMap { it.langTags }.map { it.substringBefore('-') }.filter { it.isNotEmpty() }
        println(
            "keymanc: ${rows.size} layouts, ${skipped.size} skipped, " +
                "${rawBytes / 1024} KiB raw, ${tags.toSet().size} distinct base subtags",
        )
        assertTrue("pipeline produced nothing", rows.isNotEmpty())
    }

    private data class SourceRow(
        val layoutId: String,
        val keyboardId: String,
        val version: String,
        val copyright: String,
        val langTags: List<String>,
        val droppedMultitaps: Int,
        val droppedFlicks: Int,
        val repairs: Int,
    )

    private data class KpsMeta(
        val name: String = "",
        val version: String = "",
        val copyright: String = "",
        val languages: List<String> = emptyList(),
    )

    /**
     * The bits of a `.kps` the pipeline needs.
     *
     * Read with regex rather than a parser on purpose: this runs on a fixed,
     * machine-generated corpus, the three fields wanted are attributes and
     * simple elements, and pulling in an XML stack for it would be the larger
     * risk. A field that does not match yields an empty string and the row says
     * so, rather than the whole keyboard being dropped.
     */
    private fun readKps(file: File): KpsMeta {
        if (!file.isFile) return KpsMeta()
        val xml = runCatching { file.readText() }.getOrNull() ?: return KpsMeta()

        // Scoped to <Info>, and tolerant of attributes. Both matter: the
        // keyboard's own name lives in <Info> as `<Name URL="">Khmer Angkor`,
        // while <Files> carries a `<Name>` per packaged file holding a path. An
        // unscoped, attribute-blind match takes the first <Name> in the document
        // and names 867 keyboards after a build artefact.
        val info = section(xml, "Info")
        val keyboards = section(xml, "Keyboards")
        fun infoTag(name: String): String =
            Regex("<$name\\b[^>]*>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
                .find(info)?.groupValues?.get(1)?.trim().orEmpty()

        return KpsMeta(
            name = infoTag("Name"),
            version = infoTag("Version"),
            copyright = infoTag("Copyright"),
            languages = Regex("""<Language\s+ID="([^"]+)"""")
                .findAll(keyboards).map { it.groupValues[1] }.toList(),
        )
    }

    private fun section(xml: String, name: String): String =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1).orEmpty()

    /**
     * Canonicalises a BCP-47 tag the way the corpus needs.
     *
     * The upstream tags are raw author input: three-letter codes where a
     * two-letter one exists, inconsistent casing between the `.kps` and the
     * generated metadata, and a couple of invalid regions. Left alone they would
     * mint duplicate language entries that differ only in spelling.
     */
    private fun canonicalTag(raw: String): String {
        val parts = raw.trim().split('-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        var language = parts[0].lowercase(Locale.ROOT)
        language = THREE_TO_TWO[language] ?: language
        val rest = parts.drop(1).mapNotNull { part ->
            when {
                part.length == 4 -> part.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
                part.length == 2 && part.all { it.isLetter() } ->
                    part.uppercase(Locale.ROOT).takeIf { it !in INVALID_REGIONS }
                part.length == 3 && part.all { it.isDigit() } -> part
                else -> null
            }
        }
        return (listOf(language) + rest).joinToString("-")
    }

    private companion object {
        /** ISO 639-2/T codes the corpus uses where a 639-1 code exists. */
        val THREE_TO_TWO = mapOf(
            "bel" to "be", "bos" to "bs", "bul" to "bg", "ces" to "cs",
            "hrv" to "hr", "mkd" to "mk", "pol" to "pl", "rus" to "ru",
            "slk" to "sk", "slv" to "sl", "srp" to "sr", "ukr" to "uk",
        )

        /** Not real region subtags; the corpus carries them anyway. */
        val INVALID_REGIONS = setOf("QP", "XL")
    }
}
