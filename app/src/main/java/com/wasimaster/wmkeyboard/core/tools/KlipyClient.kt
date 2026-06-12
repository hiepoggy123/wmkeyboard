package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.GifContentFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Minimal KLIPY client — Tenor's successor as the primary GIF/sticker
 * provider (Google retired the Tenor API for third parties on
 * 2026-06-30). Same shape as the other providers: blank query returns
 * trending, stickers are their own endpoint. The API key goes in the URL
 * path; get one at partner.klipy.com. The shared [GifContentFilter] maps
 * onto KLIPY's GIPHY-style rating parameter.
 */
object KlipyClient {

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
            append("https://api.klipy.com/api/v1/${ToolHttp.encode(apiKey)}/$type/$endpoint")
            append("?page=1&per_page=$limit&rating=${rating(filter)}")
            if (query.isNotBlank()) append("&q=${ToolHttp.encode(query.trim())}")
        }
        return parse(ToolHttp.get(url))
    }

    private fun rating(filter: GifContentFilter): String = when (filter) {
        GifContentFilter.HIGH -> "g"
        GifContentFilter.MEDIUM -> "pg"
        GifContentFilter.LOW -> "pg-13"
        GifContentFilter.OFF -> "r"
    }

    /**
     * Response shape: `{"result":true,"data":{"data":[…],"has_next":…}}`
     * (the inner "data" is the item list; some endpoints return the list
     * directly). Each item's media lives under `file.<size>.<format>.url`
     * with sizes hd/md/sm/xs and formats gif/webp/mp4/jpg.
     */
    internal fun parse(body: String): List<GifItem> {
        val data = Json.parseToJsonElement(body).jsonObject["data"] ?: return emptyList()
        val items = when (data) {
            is JsonArray -> data
            is JsonObject -> data["data"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return items.mapNotNull { element ->
            val result = element.jsonObject
            val file = result["file"]?.jsonObject ?: return@mapNotNull null
            val preview = file.variant("sm", "gif")
                ?: file.variant("xs", "gif")
                ?: file.variant("md", "gif")
                ?: file.variant("sm", "webp")
                ?: file.variant("xs", "webp")
            val full = file.variant("hd", "gif")
                ?: file.variant("md", "gif")
                ?: preview
            if (preview == null || full == null) return@mapNotNull null
            GifItem(
                id = "klipy_" + (result["id"]?.jsonPrimitive?.content
                    ?: result["slug"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null),
                previewUrl = preview.url,
                fullUrl = full.url,
                mime = if (full.url.substringAfterLast('.').startsWith("webp")) "image/webp" else "image/gif",
                aspectRatio = preview.aspectRatio,
                source = GifSource.KLIPY,
            )
        }
    }

    private class Variant(val url: String, val aspectRatio: Float)

    /** `file.<size>.<format>` — with a flat `file.<format>` fallback. */
    private fun JsonObject.variant(size: String, format: String): Variant? {
        val holder = this[size]?.jsonObject?.get(format)?.jsonObject
            ?: this[format]?.jsonObject?.takeIf { size == "hd" }
            ?: return null
        val url = holder["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val width = holder["width"]?.jsonPrimitive?.floatOrNull
        val height = holder["height"]?.jsonPrimitive?.floatOrNull
        val ratio = if (width != null && height != null && height > 0f) width / height else 1f
        return Variant(url, ratio)
    }
}
