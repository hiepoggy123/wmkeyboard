package com.wasimaster.wmkeyboard.core.layout

import kotlinx.serialization.Serializable

/**
 * A single layout as a shareable file.
 *
 * A versioned envelope rather than the bare object the theme export writes, and
 * for a specific reason: every [com.wasimaster.wmkeyboard.core.theme.ThemeSpec]
 * field has a default, so decoding an arbitrary JSON object succeeds and yields
 * an all-defaults theme instead of an error — importing a shopping list would
 * "work". A layout has no sane all-defaults value, and the format tag is the one
 * thing that can say "this is not a layout file" before anything is applied.
 *
 * Past the tag it is deliberately tolerant. The raw-JSON editor means
 * hand-written files, so mistakes will be common, and refusing an import leaves
 * someone with a file they cannot use and no idea which of two hundred keys is
 * wrong. [LayoutSpec.repair] fixes what it can and reports every change.
 */
@Serializable
private data class LayoutEnvelope(
    val format: String,
    val version: Int,
    val appVersion: Int = 0,
    val appVersionName: String = "",
    val layout: LayoutSpec,
)

/** A layout read off disk, with whatever had to be fixed to make it usable. */
data class ImportedLayout(
    val layout: LayoutSpec,
    val repairs: List<String>,
    /** Version code of the build that wrote the file; 0 when unstated. */
    val fromAppVersion: Int,
)

object LayoutFile {

    const val FORMAT = "wmkeyboard-layout"
    const val VERSION = 1

    /** Matches the settings backup's `wmsettings.json` shape. */
    const val FILE_EXTENSION = "wmlayout.json"

    /**
     * Plain JSON rather than a vendor type. A custom MIME would stop most file
     * managers and chat apps from offering the file at all, and the format tag
     * inside already does the identifying.
     */
    const val MIME_TYPE = "application/json"

    /**
     * What the import picker accepts. Deliberately permissive: providers
     * routinely hand back `application/octet-stream` or `text/plain` for a
     * `.json` file, and the strict check is the format tag, not the MIME.
     */
    val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

    fun fileName(layout: LayoutSpec): String {
        val stem = layout.name.ifBlank { "layout" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .ifBlank { "layout" }
        return "$stem.$FILE_EXTENSION"
    }

    fun encode(layout: LayoutSpec, appVersion: Int, appVersionName: String): String =
        LayoutCodec.json.encodeToString(
            LayoutEnvelope(FORMAT, VERSION, appVersion, appVersionName, layout),
        )

    /**
     * Parses [text], or returns null when it is not a layout file at all — the
     * one strict check in the import path.
     */
    fun decode(text: String): ImportedLayout? {
        val envelope = runCatching {
            LayoutCodec.json.decodeFromString<LayoutEnvelope>(text)
        }.getOrNull() ?: return null
        if (envelope.format != FORMAT) return null
        val repaired = envelope.layout.repair()
        return ImportedLayout(
            layout = repaired.spec,
            repairs = repaired.repairs,
            fromAppVersion = envelope.appVersion,
        )
    }
}
