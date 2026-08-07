package com.wasimaster.wmkeyboard.core.tools

/**
 * Link recognition for the easter eggs. One entry so far: the QR scanner
 * quietly checks every scanned code against the most-scanned prank link on
 * the internet.
 *
 * Lives in `:core:tools` rather than in the scanner panel so both build
 * flavors compile it and the matching is testable on the JVM.
 */
object EggLinks {

    /** The video id of Rick Astley's "Never Gonna Give You Up". */
    private const val RICKROLL_ID = "dQw4w9WgXcQ"

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com",
    )

    /**
     * True when [raw] is a link to the rickroll video, in any of YouTube's
     * common shapes: `watch?v=`, `youtu.be/`, `/embed/`, `/shorts/`, `/live/`.
     * The host comparison ignores case; the video id must match exactly —
     * YouTube ids are case-sensitive, and eleven near-miss characters are
     * somebody else's video.
     */
    fun isRickroll(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.length > MAX_URL_LENGTH) return false
        // Scheme-optional: a QR code often carries a bare "youtu.be/…".
        val noScheme = trimmed
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("HTTPS://").removePrefix("HTTP://")
        val hostEnd = noScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val host = (if (hostEnd == -1) noScheme else noScheme.take(hostEnd)).lowercase()
        if (host !in YOUTUBE_HOSTS) return false
        val rest = if (hostEnd == -1) "" else noScheme.substring(hostEnd)

        if (host == "youtu.be") return rest.hasIdAfter("/")
        if (rest.queryParamIsId("v")) return true
        return rest.hasIdAfter("/embed/") || rest.hasIdAfter("/shorts/") || rest.hasIdAfter("/live/")
    }

    /** True when the id follows [prefix] and ends there or at a delimiter. */
    private fun String.hasIdAfter(prefix: String): Boolean {
        if (!startsWith(prefix)) return false
        val after = substring(prefix.length)
        if (!after.startsWith(RICKROLL_ID)) return false
        val next = after.getOrNull(RICKROLL_ID.length) ?: return true
        return next == '?' || next == '&' || next == '#' || next == '/'
    }

    /** True when the `v` query parameter is exactly the id. */
    private fun String.queryParamIsId(name: String): Boolean {
        val query = substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return false
        return query.split('&').any { param ->
            val key = param.substringBefore('=')
            val value = param.substringAfter('=', "")
            key == name && value == RICKROLL_ID
        }
    }

    /** A scanned payload longer than any plausible video link is not one. */
    private const val MAX_URL_LENGTH = 500
}
