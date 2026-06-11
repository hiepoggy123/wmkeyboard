package com.wasimaster.wmkeyboard.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One web hit from Programmable Search. */
data class WebResult(
    val title: String,
    val snippet: String,
    val url: String,
    /** Short host shown under the title ("en.wikipedia.org"). */
    val displayUrl: String,
)

/** One image hit from Programmable Search's image mode. */
data class ImageResult(
    val title: String,
    /** Small thumbnail rendered in the picker grid. */
    val thumbUrl: String,
    /** Full image downloaded and inserted on tap. */
    val imageUrl: String,
    val mime: String,
    /** Page the image came from, for the link-insert action. */
    val contextUrl: String,
)

/**
 * Google Programmable Search (Custom Search JSON API) client, shared by the
 * web search and image search tools. Needs an API key plus an engine id
 * (cx) configured to search the whole web. The free tier is 100 queries a
 * day, which is why searches only fire on the enter key, never per
 * keystroke.
 */
object GoogleSearchClient {

    /** Blocking; call on an IO dispatcher. Throws on failure. */
    fun webSearch(query: String, apiKey: String, cx: String, count: Int, safe: Boolean): List<WebResult> =
        parseWeb(ToolHttp.get(searchUrl(query, apiKey, cx, count, safe, image = false)))

    /** Blocking; call on an IO dispatcher. Throws on failure. */
    fun imageSearch(query: String, apiKey: String, cx: String, count: Int, safe: Boolean): List<ImageResult> =
        parseImages(ToolHttp.get(searchUrl(query, apiKey, cx, count, safe, image = true)))

    private fun searchUrl(
        query: String,
        apiKey: String,
        cx: String,
        count: Int,
        safe: Boolean,
        image: Boolean,
    ): String = buildString {
        append("https://www.googleapis.com/customsearch/v1")
        append("?key=${ToolHttp.encode(apiKey)}&cx=${ToolHttp.encode(cx)}")
        append("&q=${ToolHttp.encode(query.trim())}")
        append("&num=${count.coerceIn(1, 10)}")
        append("&safe=${if (safe) "active" else "off"}")
        if (image) append("&searchType=image")
    }

    internal fun parseWeb(body: String): List<WebResult> {
        val items = Json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element.jsonObject
            WebResult(
                title = item["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                snippet = item["snippet"]?.jsonPrimitive?.content.orEmpty(),
                url = item["link"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                displayUrl = item["displayLink"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    internal fun parseImages(body: String): List<ImageResult> {
        val items = Json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element.jsonObject
            val image = item["image"]?.jsonObject
            val full = item["link"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ImageResult(
                title = item["title"]?.jsonPrimitive?.content.orEmpty(),
                thumbUrl = image?.get("thumbnailLink")?.jsonPrimitive?.content ?: full,
                imageUrl = full,
                mime = item["mime"]?.jsonPrimitive?.content ?: "image/jpeg",
                contextUrl = image?.get("contextLink")?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }
}
