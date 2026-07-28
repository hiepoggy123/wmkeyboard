package com.wasimaster.wmkeyboard.core.icons

import kotlinx.serialization.Serializable

/**
 * An installed icon pack: metadata plus the slots it replaces.
 *
 * [slots] holds slot ids only, never file names. The file backing a slot is
 * always `<slotId>.svg` inside the pack's directory — derived, never taken
 * from a manifest — so a hostile pack has no way to name a path at all.
 */
@Serializable
data class IconPack(
    val id: String,
    val name: String,
    val author: String = "",
    val description: String = "",
    /** The pack author's own version string; shown, never parsed. */
    val version: String = "",
    val slots: List<String> = emptyList(),
    val createdAt: Long = 0L,
) {
    /** True for the built-as-you-go pack that per-slot SVG imports land in. */
    val isMine: Boolean get() = id == IconPackStore.MINE_ID
}

/** Why an import ended the way it did, so the caller can say something useful. */
sealed interface IconImportResult {
    /**
     * [repairs] describes what was dropped — an unknown slot id, an SVG that
     * wouldn't parse — so the confirmation dialog can be honest about a pack
     * that only partly survived.
     */
    data class Imported(val pack: IconPack, val repairs: List<String>) : IconImportResult

    /** A ZIP, but not ours: no manifest, or the wrong format tag. */
    data object NotAnIconPack : IconImportResult

    data object TooManyPacks : IconImportResult

    /** Unreadable, or nothing usable inside. */
    data object Failed : IconImportResult
}
