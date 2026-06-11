package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.GifContentFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One GIF or sticker result: a small preview for the panel grid and the
 * full-size file that actually gets committed to the editor. Produced by
 * [TenorClient], [GiphyClient] and [GoogleSearchClient.gifSearch].
 */
data class GifItem(
    val id: String,
    /** Compact preview shown in the picker grid (animated for Tenor/GIPHY). */
    val previewUrl: String,
    /** Full-size file downloaded and inserted on tap. */
    val fullUrl: String,
    /** MIME of [fullUrl] — image/gif, image/webp or image/png. */
    val mime: String,
    /** width/height of the preview, for grid cell sizing. */
    val aspectRatio: Float,
    val source: GifSource,
)

/**
 * Minimal Tenor v2 client used by both the GIF tool and the sticker tool
 * (stickers are Tenor's transparent-background search filter). Requires an
 * API key — Tenor has no keyless tier. Search and featured ("trending")
 * only; the panel's search box drives everything else.
 */
object TenorClient {

    /** Blocking; call on an IO dispatcher. Empty [query] returns featured results. */
    fun search(
        query: String,
        apiKey: String,
        stickers: Boolean,
        filter: GifContentFilter,
        limit: Int = 24,
    ): List<GifItem> {
        val endpoint = if (query.isBlank()) "featured" else "search"
        val url = buildString {
            append("https://tenor.googleapis.com/v2/$endpoint")
            append("?key=${ToolHttp.encode(apiKey)}&client_key=wmkeyboard")
            if (query.isNotBlank()) append("&q=${ToolHttp.encode(query.trim())}")
            append("&limit=$limit")
            append("&contentfilter=${filter.name.lowercase()}")
            if (stickers) {
                append("&searchfilter=sticker")
                append("&media_filter=tinygif_transparent,gif_transparent,tinywebp_transparent,webp_transparent")
            } else {
                append("&media_filter=tinygif,gif")
            }
        }
        return parse(ToolHttp.get(url), stickers)
    }

    internal fun parse(body: String, stickers: Boolean): List<GifItem> {
        val results = Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val result = element.jsonObject
            val formats = result["media_formats"]?.jsonObject ?: return@mapNotNull null
            val preview = if (stickers) {
                formats.format("tinygif_transparent") ?: formats.format("tinywebp_transparent")
            } else {
                formats.format("tinygif")
            }
            val full = if (stickers) {
                formats.format("gif_transparent")?.let { it to "image/gif" }
                    ?: formats.format("webp_transparent")?.let { it to "image/webp" }
            } else {
                formats.format("gif")?.let { it to "image/gif" }
            }
            if (preview == null || full == null) return@mapNotNull null
            GifItem(
                id = result["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                previewUrl = preview.url,
                fullUrl = full.first.url,
                mime = full.second,
                aspectRatio = preview.aspectRatio,
                source = GifSource.TENOR,
            )
        }
    }

    private class Format(val url: String, val aspectRatio: Float)

    private fun JsonObject.format(name: String): Format? {
        val format = this[name]?.jsonObject ?: return null
        val url = format["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val dims = format["dims"]?.jsonArray
        val width = dims?.getOrNull(0)?.jsonPrimitive?.floatOrNull
        val height = dims?.getOrNull(1)?.jsonPrimitive?.floatOrNull
        val ratio = if (width != null && height != null && height > 0f) width / height else 1f
        return Format(url, ratio)
    }
}
