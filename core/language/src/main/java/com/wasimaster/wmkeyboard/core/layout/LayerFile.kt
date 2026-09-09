package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.util.firstJsonDocument
import kotlinx.serialization.Serializable

/**
 * One layer of a layout as a shareable document (issue #105).
 *
 * The layout editor writes this onto the system clipboard for "Copy layer" and
 * reads it back for "Paste layer", which is what makes a grid movable between
 * two layers of one layout, between two layouts, and between two devices
 * through any app that carries text.
 *
 * A versioned envelope for the same reason [LayoutFile] is one: every
 * [LayerSpec] field but `rows` has a default, so a bare object would decode
 * almost anything, and the clipboard is the one place where "almost anything"
 * is the normal case. The format tag is what says "this clipping is not a
 * layer" before a grid is replaced.
 *
 * [layer] records which layer it was copied from. Nothing enforces it on the
 * way in, because pasting the date pad into the time pad is one of the things
 * the issue asked for; it only lets the screen say what it pasted.
 */
@Serializable
private data class LayerEnvelope(
    val format: String,
    val version: Int,
    val appVersion: Int = 0,
    val appVersionName: String = "",
    val layer: String = "",
    val spec: LayerSpec,
)

/** A layer read off the clipboard, before it is repaired into its new home. */
data class ImportedLayer(
    val spec: LayerSpec,
    /** The [LayoutLayer.key] it was copied from, or empty when unstated. */
    val layerKey: String,
    /** Version code of the build that wrote it; 0 when unstated. */
    val fromAppVersion: Int,
)

object LayerFile {

    const val FORMAT = "wmkeyboard-layer"
    const val VERSION = 1

    /**
     * Written the way the raw-JSON editor prints a layout: indented, and
     * without the fields that hold their default value.
     *
     * The export file writes every field of every key instead, which is right
     * for a file that has to survive a format change. This goes onto the
     * clipboard, where a user may well read it, paste it into a message or
     * hand-edit it, and where the difference is a tenth of the characters.
     */
    fun encode(
        layerKey: String,
        spec: LayerSpec,
        appVersion: Int,
        appVersionName: String,
    ): String = layoutEditorJson.encodeToString(
        LayerEnvelope(FORMAT, VERSION, appVersion, appVersionName, layerKey, spec),
    )

    /**
     * Parses [text], or returns null when it is not a copied layer.
     *
     * Repair is left to the caller: what a layer has to guarantee depends on
     * which layer it is pasted into, and that is decided at the paste, not
     * here. See [repairAsLayer].
     */
    fun decode(text: String): ImportedLayer? {
        val envelope = runCatching {
            LayoutCodec.json.decodeFromString<LayerEnvelope>(text.firstJsonDocument())
        }.getOrNull() ?: return null
        if (envelope.format != FORMAT) return null
        return ImportedLayer(
            spec = envelope.spec,
            layerKey = envelope.layer,
            fromAppVersion = envelope.appVersion,
        )
    }
}
