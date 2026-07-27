package com.wasimaster.wmkeyboard.core.addons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `wmkeyboard-repo.json` manifest: an index over addon files served from a
 * URL the user pasted in.
 *
 * A repository is deliberately *only* an index. Every payload it points at is
 * already one of the app's own import formats — a `.wmtheme.json` is the theme
 * export, a `.wmicons` is the icon-pack export — so installing an addon is
 * "download the file and hand it to the importer that already reads it". There
 * is no packaging step for a publisher and no new parser here.
 *
 * The spec these types mirror is `docs/addons/REPO_FORMAT.md`.
 *
 * Every field past the required ones has a default, and decoding is tolerant
 * (see [AddonRepoCodec]), so a manifest written against a later version of the
 * format still loads on an older build instead of failing wholesale.
 */
@Serializable
data class AddonRepoManifest(
    /** Magic tag; anything but [AddonRepoCodec.FORMAT] is rejected outright. */
    val format: String = "",
    val version: Int = 1,
    val repo: AddonRepoInfo = AddonRepoInfo(),
    val addons: List<AddonEntry> = emptyList(),
)

@Serializable
data class AddonRepoInfo(
    /** Stable id, reverse-DNS by convention. Namespaces the installed addons. */
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val homepage: String = "",
    /** Relative to the manifest, or an absolute https URL. */
    val icon: String? = null,
    /** ISO-8601 date the publisher last touched the manifest. */
    val updatedAt: String = "",
)

@Serializable
data class AddonEntry(
    /** Unique within its repository. Half of the install key; never recycled. */
    val id: String = "",
    val type: AddonType = AddonType.Unknown,
    val name: String = "",
    /** Semver. Bumping it is how a publisher offers an update. */
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    /** Payload location: relative to the manifest, or an absolute https URL. */
    val path: String = "",
    /**
     * Lowercase hex SHA-256 of the payload, **optional**.
     *
     * Verified before install when present. When absent the addon still
     * installs and is simply shown as unverified — requiring it would put a
     * hashing step between a beginner and their first published pack, and the
     * transport is https either way.
     */
    val sha256: String? = null,
    /** Payload size for the UI and a pre-download guard; also optional. */
    val sizeBytes: Long? = null,
    /** Screenshots, relative or absolute. */
    val previews: List<String> = emptyList(),
    /** App `versionCode` floor. Older builds show the addon but can't install it. */
    val minAppVersion: Int? = null,
    /** Required for [AddonType.Dictionary]; a grouping hint for layouts. */
    val langId: String? = null,
) {
    /** `"<repoId>/<addonId>"` — how an install is tracked in [AddonStore]. */
    fun key(repoId: String): String = "$repoId/$id"
}

/**
 * The addon kinds the format defines.
 *
 * [Unknown] is the forward-compatibility hatch: `type` defaults to it and the
 * decoder coerces invalid values to the default, so a manifest listing a type
 * this build has never heard of loads with that one entry unusable rather than
 * failing the whole repository.
 */
@Serializable
enum class AddonType {
    @SerialName("theme") Theme,
    @SerialName("layout") Layout,
    @SerialName("dictionary") Dictionary,
    @SerialName("snippets") Snippets,
    @SerialName("stickers") Stickers,
    @SerialName("icon_pack") IconPack,
    @SerialName("font") Font,
    @SerialName("sound") Sound,
    @SerialName("unknown") Unknown,
    ;

    /** Plural heading for the type filter and section headers. */
    val label: String
        get() = when (this) {
            Theme -> "Themes"
            Layout -> "Layouts"
            Dictionary -> "Dictionaries"
            Snippets -> "Snippets"
            Stickers -> "Sticker packs"
            IconPack -> "Icon packs"
            Font -> "Fonts"
            Sound -> "Sounds"
            Unknown -> "Other"
        }

    /**
     * Singular form, for naming one addon.
     *
     * Spelled out rather than derived from [label], because dropping a
     * trailing "s" turns "Dictionaries" into "Dictionarie".
     */
    val singularLabel: String
        get() = when (this) {
            Theme -> "Theme"
            Layout -> "Layout"
            Dictionary -> "Dictionary"
            Snippets -> "Snippet pack"
            Stickers -> "Sticker pack"
            IconPack -> "Icon pack"
            Font -> "Font"
            Sound -> "Key sound"
            Unknown -> "Addon"
        }

    /**
     * Largest payload worth downloading, matched to whatever the importer on
     * the other end will actually accept — there is no point streaming 60 MB
     * of dictionary only for the importer to refuse it at 32.
     */
    val maxBytes: Long
        get() = when (this) {
            Theme, Layout, Snippets -> 4L * 1024 * 1024
            Dictionary -> 32L * 1024 * 1024
            IconPack -> 8L * 1024 * 1024
            Stickers -> 64L * 1024 * 1024
            Font -> 32L * 1024 * 1024
            Sound -> 4L * 1024 * 1024
            Unknown -> 0L
        }
}
