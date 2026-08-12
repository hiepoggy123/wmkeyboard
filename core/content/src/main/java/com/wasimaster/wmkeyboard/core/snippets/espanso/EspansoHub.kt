package com.wasimaster.wmkeyboard.core.snippets.espanso

/**
 * Turns a pasted address into one a snippet file can actually be fetched from.
 *
 * The Espanso Hub is a website over a GitHub repository, so the address a person
 * is looking at when they decide they want a package is almost never the address
 * the file lives at. Rather than ask them to hunt for a raw link, the three
 * shapes worth knowing about are recognised here:
 *
 * - a direct `https://` link to a `.yml`, `.yaml` or `.zip`, which passes through
 * - a GitHub `blob` page, rewritten to `raw.githubusercontent.com`
 * - `https://hub.espanso.org/<package>`, which needs the repository listed to
 *   find the newest version, so it is answered in two steps
 *
 * Same shape and the same reasoning as `AddonRepoCodec.resolveManifestUrl`.
 * HTTPS only, throughout: an `http://`, `file://` or `content://` address is
 * refused rather than upgraded, since a snippet pack is somebody else's text and
 * the transport is the only thing saying where it really came from.
 */
object EspansoHub {

    /** Where the Hub's packages actually live. */
    const val REPO_OWNER = "espanso"
    const val REPO_NAME = "hub"

    private const val HUB_HOST = "hub.espanso.org"
    private const val GITHUB_HOST = "github.com"
    private const val RAW_HOST = "raw.githubusercontent.com"

    /** What [resolve] decided a pasted address means. */
    sealed interface Target {

        /** Fetch this address and read what comes back. */
        data class Direct(val url: String) : Target

        /**
          * A Hub package named but not yet located. The caller lists
          * [contentsUrl], picks the newest version directory, and then fetches
          * [packageUrl] for that version.
          */
        data class HubPackage(val slug: String) : Target {
            val contentsUrl: String
                get() = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/contents/packages/$slug"

            fun packageUrl(version: String): String =
                "https://$RAW_HOST/$REPO_OWNER/$REPO_NAME/main/packages/$slug/$version/package.yml"
        }
    }

    /** File extensions a direct link may point at. */
    private val EXTENSIONS = EspansoFile.EXTENSIONS + listOf("zip", "json")

    /** A Hub package name: the specification allows lowercase, digits and hyphens. */
    private val SLUG = Regex("""[a-z0-9][a-z0-9-]{0,60}""")

    /** [pasted] as something fetchable, or null when it is not usable. */
    fun resolve(pasted: String): Target? {
        val url = pasted.trim()
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val rest = url.removePrefix("https://").removePrefix("HTTPS://")
        val host = rest.substringBefore('/').substringBefore(':').lowercase()
        val path = rest.substringAfter('/', missingDelimiterValue = "").substringBefore('?')

        if (host == HUB_HOST || host == "www.$HUB_HOST") {
            // The Hub's own package pages, and nothing else on the site.
            val slug = path.trim('/').substringAfterLast('/')
            if (slug.isEmpty() || !SLUG.matches(slug)) return null
            return Target.HubPackage(slug)
        }
        if (host == GITHUB_HOST || host == "www.$GITHUB_HOST") {
            // ".../blob/<ref>/<path>" is a page; the same path under the raw
            // host is the file.
            val parts = path.split('/')
            val blob = parts.indexOf("blob")
            if (blob >= 2 && parts.size > blob + 2) {
                val owner = parts[0]
                val repo = parts[1]
                val tail = parts.drop(blob + 1).joinToString("/")
                if (!hasKnownExtension(tail)) return null
                return Target.Direct("https://$RAW_HOST/$owner/$repo/$tail")
            }
            return null
        }
        if (!hasKnownExtension(path)) return null
        return Target.Direct(url)
    }

    private fun hasKnownExtension(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        return EXTENSIONS.any { name.endsWith(".$it") }
    }

    /**
     * The newest version directory in a GitHub contents listing.
     *
     * Read with a regular expression rather than a JSON parser on purpose: the
     * one field wanted is `"name"`, the answer is checked against a strict
     * version shape before it is used, and it has to survive the listing gaining
     * fields. Newest by number rather than by listing order, which is
     * alphabetical and would call 1.9.0 newer than 1.10.0.
     */
    fun newestVersion(listingJson: String): String? =
        Regex(""""name"\s*:\s*"(\d{1,4}(?:\.\d{1,4}){0,2})"""")
            .findAll(listingJson)
            .map { it.groupValues[1] }
            .maxWithOrNull(compareBy(VERSION_ORDER))

    /** Compares two dotted version strings a part at a time. */
    private val VERSION_ORDER: (String) -> Comparable<*> = { version ->
        val parts = version.split('.').map { it.toIntOrNull() ?: 0 }
        VersionKey(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
    }

    private data class VersionKey(val major: Int, val minor: Int, val patch: Int) : Comparable<VersionKey> {
        override fun compareTo(other: VersionKey): Int = compareValuesBy(
            this, other, VersionKey::major, VersionKey::minor, VersionKey::patch,
        )
    }
}
