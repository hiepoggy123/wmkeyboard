package com.wasimaster.wmkeyboard.core.snippets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SnippetEnvelope(
    val format: String = "",
    val version: Int = 0,
    val appVersion: Int = 0,
    val appVersionName: String = "",
    val snippets: List<Snippet> = emptyList(),
)

/** Snippets read out of a file, with whatever had to be fixed to use them. */
data class ImportedSnippets(
    val snippets: List<Snippet>,
    val repairs: List<String>,
    /** Version code of the build that wrote the file; 0 when unstated. */
    val fromAppVersion: Int,
)

/**
 * The `.wmsnippets.json` file: the app's native snippet export and import.
 *
 * ```json
 * {
 *   "format": "wmkeyboard-snippets",
 *   "version": 1,
 *   "appVersion": 41,
 *   "appVersionName": "1.4.0",
 *   "snippets": [
 *     { "id": 1, "label": "Shrug", "text": "¯\\_(ツ)_/¯", "trigger": "shrug" }
 *   ]
 * }
 * ```
 *
 * Same versioned envelope as [com.wasimaster.wmkeyboard.core.layout.LayoutFile]:
 * `format` is the one strict check, and everything past it is repaired and
 * reported rather than refused, because a file assembled by hand shouldn't fail
 * over one bad row.
 *
 * `id` is written for readability but **never trusted** — [SnippetStore.add]
 * assigns fresh ids on the way in, so importing the same pack twice produces
 * two independent sets rather than silently overwriting the first.
 */
object SnippetFile {

    const val FORMAT = "wmkeyboard-snippets"
    const val VERSION = 1

    const val FILE_EXTENSION = "wmsnippets.json"

    /**
     * Plain JSON rather than a vendor type, for the reason the layout format
     * gives: a custom MIME would stop most file managers and chat apps from
     * offering the file at all.
     */
    const val MIME_TYPE = "application/json"

    /** Permissive on purpose; the format tag inside is the real check. */
    val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

    /** A snippet longer than this is a pasted document, not a snippet. */
    private const val MAX_TEXT_LENGTH = 20_000

    /** Enough for anyone; a file past this is a generated dump. */
    private const val MAX_SNIPPETS = 500

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun fileName(): String = "snippets.$FILE_EXTENSION"

    fun encode(snippets: List<Snippet>, appVersion: Int, appVersionName: String): String =
        json.encodeToString(
            SnippetEnvelope(
                format = FORMAT,
                version = VERSION,
                appVersion = appVersion,
                appVersionName = appVersionName,
                snippets = snippets,
            ),
        )

    /**
     * Parses [text], or returns null when it is not a snippet file at all.
     *
     * A row with no text is dropped — there is nothing for it to insert. A row
     * with no label is kept and labelled from its own text, since the label is
     * only how the panel lists it.
     */
    fun decode(text: String): ImportedSnippets? {
        val envelope = runCatching { json.decodeFromString<SnippetEnvelope>(text) }.getOrNull()
            ?: return null
        if (envelope.format != FORMAT) return null

        val repairs = ArrayList<String>()
        val kept = ArrayList<Snippet>()

        for (snippet in envelope.snippets) {
            if (kept.size >= MAX_SNIPPETS) {
                repairs += "Only the first $MAX_SNIPPETS snippets were imported"
                break
            }
            val body = snippet.text
            if (body.isBlank()) {
                repairs += "Dropped “${snippet.label.take(30)}” — it had no text"
                continue
            }
            val trimmedBody = if (body.length > MAX_TEXT_LENGTH) {
                repairs += "Shortened “${snippet.label.take(30)}” — it was over $MAX_TEXT_LENGTH characters"
                body.take(MAX_TEXT_LENGTH)
            } else {
                body
            }
            val label = snippet.label.ifBlank {
                repairs += "Named a snippet after its text — it had no label"
                trimmedBody.lineSequence().first().take(40)
            }
            kept += snippet.copy(
                label = label,
                text = trimmedBody,
                trigger = snippet.trigger?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        return ImportedSnippets(
            snippets = kept,
            repairs = repairs,
            fromAppVersion = envelope.appVersion,
        )
    }
}
