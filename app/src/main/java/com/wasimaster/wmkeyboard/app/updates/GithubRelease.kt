package com.wasimaster.wmkeyboard.app.updates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One release, as GitHub's API describes it.
 *
 * Every field has a default so that a response shaped slightly differently
 * from the one this was written against still decodes. That is the same rule
 * the addon repository codec follows, and for the same reason: the answer the
 * user needs from a surprise is "there is no update", never a crash.
 */
@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

/**
 * One file attached to a release.
 *
 * [digest] is GitHub's own checksum, in the form `sha256:<64 hex>`. It arrived
 * relatively recently, so a release cut before it exists has none, which is
 * what [ReleaseAssets.sha256Of] returning null means and why there is a
 * fallback to the `SHA256SUMS.txt` the release workflow attaches.
 */
@Serializable
internal data class GithubAsset(
    val name: String = "",
    val size: Long = 0,
    val digest: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String = "",
)

/** The releases this app knows how to read, and where to read them from. */
internal object GithubReleases {

    /**
     * Owner and repository, as the API path wants them.
     *
     * Case does not matter to GitHub, which resolves either spelling, so this
     * deliberately matches [com.wasimaster.wmkeyboard.core.support.Support.SOURCE_URL]
     * rather than introducing a second spelling of the same repository.
     */
    const val REPO = "wasi-master/WMKeyboard"

    /**
     * The five most recent releases.
     *
     * The list, not `/releases/latest`: that endpoint hides pre-releases, and
     * offering those is a setting. Five is enough to find the newest release
     * that actually carries a file for this device, and small enough that the
     * response stays a few kilobytes.
     */
    const val LIST_URL = "https://api.github.com/repos/$REPO/releases?per_page=5"

    /** The page a user is sent to when the app cannot install an update itself. */
    fun releasePage(tag: String): String = "https://github.com/$REPO/releases/tag/$tag"

    /**
     * The human release notes for one version.
     *
     * Not the release body, which is a generated list of commits plus fixed
     * installation boilerplate. The notes a person wrote are the changelog
     * file that Play and F-Droid already read, and it is in the tag's tree.
     */
    fun changelogUrl(tag: String, versionCode: Int): String =
        "https://raw.githubusercontent.com/$REPO/$tag/" +
            "fastlane/metadata/android/en-US/changelogs/$versionCode.txt"

    /** The checksums file the release workflow attaches beside the APKs. */
    const val CHECKSUMS_ASSET = "SHA256SUMS.txt"

    /** Longest release-notes text worth showing. Anything past this is a mistake. */
    const val MAX_NOTES_CHARS = 16_384

    /** Longest release list worth reading. Five releases are a few kilobytes. */
    const val MAX_LIST_BYTES = 512L * 1024
}

/** Turns GitHub's JSON into releases, or into nothing. */
internal object GithubReleaseCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** The releases in [text], or null when it is not a release list at all. */
    fun decode(text: String): List<GithubRelease>? =
        runCatching { json.decodeFromString<List<GithubRelease>>(text) }.getOrNull()

    /**
     * The message GitHub puts in an error body.
     *
     * Its own parse rather than [com.wasimaster.wmkeyboard.core.tools.ToolHttp.apiErrorText],
     * which looks for the `{"error": {"message": …}}` shape the AI providers
     * use. GitHub puts `message` at the top level, so that helper reads every
     * GitHub error as having nothing to say.
     */
    fun errorMessage(body: String?): String? = body?.let {
        runCatching {
            json.parseToJsonElement(it).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
    }?.takeIf { it.isNotBlank() }
}
