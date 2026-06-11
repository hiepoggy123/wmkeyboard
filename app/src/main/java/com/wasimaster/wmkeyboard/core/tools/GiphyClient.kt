package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.GifContentFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Minimal GIPHY v1 client — the second GIF/sticker provider next to
 * [KlipyClient]. Same shape: blank query returns trending, stickers are a
 * separate endpoint with transparent results. Requires a (free) GIPHY API
 * key. The shared [GifContentFilter] maps onto GIPHY's MPAA-style rating.
 */
object GiphyClient {

    /** Blocking; call on an IO dispatcher. Empty [query] returns trending. */
    fun search(
        query: String,
        apiKey: String,
        stickers: Boolean,
        filter: GifContentFilter,
        limit: Int = 24,
    ): List<GifItem> {
        val type = if (stickers) "stickers" else "gifs"
        val endpoint = if (query.isBlank()) "trending" else "search"
        val url = buildString {
            append("https://api.giphy.com/v1/$type/$endpoint")
            append("?api_key=${ToolHttp.encode(apiKey)}")
            if (query.isNotBlank()) append("&q=${ToolHttp.encode(query.trim())}")
            append("&limit=$limit&rating=${rating(filter)}")
        }
        return parse(ToolHttp.get(url))
    }

    /** GIPHY ratings are cumulative: "pg" means pg and below. */
    private fun rating(filter: GifContentFilter): String = when (filter) {
        GifContentFilter.HIGH -> "g"
        GifContentFilter.MEDIUM -> "pg"
        GifContentFilter.LOW -> "pg-13"
        GifContentFilter.OFF -> "r"
    }

    internal fun parse(body: String): List<GifItem> {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val result = element.jsonObject
            val images = result["images"]?.jsonObject ?: return@mapNotNull null
            val preview = images.rendition("fixed_width_small")
                ?: images.rendition("preview_gif")
                ?: images.rendition("original")
                ?: return@mapNotNull null
            val full = images.rendition("original") ?: preview
            GifItem(
                id = "giphy_" + (result["id"]?.jsonPrimitive?.content ?: return@mapNotNull null),
                previewUrl = preview.url,
                fullUrl = full.url,
                mime = "image/gif",
                aspectRatio = preview.aspectRatio,
                source = GifSource.GIPHY,
            )
        }
    }

    private class Rendition(val url: String, val aspectRatio: Float)

    private fun JsonObject.rendition(name: String): Rendition? {
        val rendition = this[name]?.jsonObject ?: return null
        val url = rendition["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val width = rendition["width"]?.jsonPrimitive?.floatOrNull
        val height = rendition["height"]?.jsonPrimitive?.floatOrNull
        val ratio = if (width != null && height != null && height > 0f) width / height else 1f
        return Rendition(url, ratio)
    }
}
