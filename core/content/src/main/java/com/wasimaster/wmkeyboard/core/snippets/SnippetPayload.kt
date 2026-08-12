package com.wasimaster.wmkeyboard.core.snippets

import com.wasimaster.wmkeyboard.core.content.ContentText
import com.wasimaster.wmkeyboard.core.snippets.espanso.EspansoFile
import com.wasimaster.wmkeyboard.core.snippets.espanso.EspansoPackage
import java.io.File

/**
 * Works out what a file of snippets is, and reads it.
 *
 * Three places ask this question — the file picker on the Text Expander screen,
 * the import-from-a-URL dialog, and the add-on installer — and they must not be
 * able to disagree, so the decision lives here rather than three times over.
 *
 * The order is the safety property, and it is the same one `WMFileTypes` uses
 * for the app's other formats: an archive is recognised by its first four bytes,
 * then the app's own **tagged** format is tried, and only then the untagged
 * Espanso one. An untagged format must never be tried ahead of a tagged one,
 * because it has no way to say the file was not meant for it.
 */
object SnippetPayload {

    /** Which format a payload turned out to be in. */
    enum class Source {
        /** The app's own `.wmsnippets.json`. */
        NATIVE,

        /** An Espanso match file. */
        ESPANSO_FILE,

        /** An Espanso package archive. */
        ESPANSO_PACKAGE,
    }

    /**
     * A payload after reading, whatever it turned out to be.
     *
     * [folders] is only ever filled by the app's own format; Espanso has no
     * folder of its own, and its packages arrive as one folder named by
     * [suggestedName].
     */
    data class Parsed(
        val snippets: List<Snippet>,
        val folders: List<SnippetFolder>,
        val notes: List<ContentText>,
        val source: Source,
        val suggestedName: String,
    ) {

        /** True when this came from Espanso, in either of its two shapes. */
        val isEspanso: Boolean get() = source != Source.NATIVE
    }

    /** Bytes read to tell an archive from text. */
    private const val MAGIC_BYTES = 4

    /** MIME types the file picker offers for either format. */
    val IMPORT_MIME_TYPES = arrayOf(
        "application/json",
        "text/yaml",
        "application/x-yaml",
        "application/yaml",
        "application/zip",
        "text/plain",
        "application/octet-stream",
    )

    /**
     * Reads [payload], or returns null when it is none of the three.
     *
     * [fallbackName] names the folder an Espanso file lands in when it carries
     * no better name of its own; pass the file name.
     */
    fun read(payload: File, fallbackName: String): Parsed? = runCatching {
        if (payload.length() > EspansoFile.MAX_BYTES) return@runCatching null
        read(payload.readBytes(), fallbackName)
    }.getOrNull()

    /**
     * The same question asked of bytes already in memory.
     *
     * What the file picker and the URL import both have: a stream that has to be
     * drained anyway, and no file on disk to hand over.
     */
    fun read(payload: ByteArray, fallbackName: String): Parsed? = runCatching {
        if (payload.size > EspansoFile.MAX_BYTES) return@runCatching null
        if (payload.size >= MAGIC_BYTES && EspansoPackage.looksLikeZip(payload.copyOf(MAGIC_BYTES))) {
            val archive = payload.inputStream().use { EspansoPackage.read(it, fallbackName) }
                ?: return@runCatching null
            return@runCatching Parsed(
                snippets = archive.snippets,
                folders = emptyList(),
                notes = archive.notes,
                source = Source.ESPANSO_PACKAGE,
                suggestedName = archive.folderName,
            )
        }
        readText(payload.decodeToString(), fallbackName)
    }.getOrNull()

    /** The text half of [read], split out so the URL import can reuse it. */
    fun readText(text: String, fallbackName: String): Parsed? {
        SnippetFile.decode(text)?.let { native ->
            return Parsed(
                snippets = native.snippets,
                folders = native.folders,
                notes = native.repairs,
                source = Source.NATIVE,
                suggestedName = fallbackName.substringBefore('.'),
            )
        }
        if (!EspansoFile.looksLikeEspanso(text)) return null
        val espanso = EspansoFile.read(text, fallbackName) ?: return null
        return Parsed(
            snippets = espanso.snippets,
            folders = emptyList(),
            notes = espanso.notes,
            source = Source.ESPANSO_FILE,
            suggestedName = espanso.folderName,
        )
    }
}
