package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateClientTest {

    @Test
    fun `free endpoint response parses segments and detected language`() {
        val body = """[[["Hello ","হ্যালো ",null,null,10],["world","বিশ্ব",null,null,10]],null,"bn"]"""
        val translation = TranslateClient.parseFree(body)
        assertEquals("Hello world", translation.text)
        assertEquals("bn", translation.detectedSource)
    }

    @Test
    fun `official v2 response parses translation and detected language`() {
        val body = """
            {"data":{"translations":[
                {"translatedText":"Hello world","detectedSourceLanguage":"bn"}
            ]}}
        """.trimIndent()
        val translation = TranslateClient.parseOfficial(body)
        assertEquals("Hello world", translation.text)
        assertEquals("bn", translation.detectedSource)
    }

    @Test
    fun `official v2 without detection still parses`() {
        val body = """{"data":{"translations":[{"translatedText":"Hola"}]}}"""
        assertEquals("", TranslateClient.parseOfficial(body).detectedSource)
    }
}

class TenorClientTest {

    private val gifBody = """
        {"results":[
            {"id":"111","media_formats":{
                "tinygif":{"url":"https://t/tiny1.gif","dims":[220,110]},
                "gif":{"url":"https://t/full1.gif","dims":[440,220]}
            }},
            {"id":"222","media_formats":{
                "gif":{"url":"https://t/full2.gif","dims":[300,300]}
            }}
        ],"next":"abc"}
    """.trimIndent()

    @Test
    fun `gif results parse preview, full url and aspect ratio`() {
        val items = TenorClient.parse(gifBody, stickers = false)
        // The second result lacks a tinygif preview and is skipped.
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("111", item.id)
        assertEquals("https://t/tiny1.gif", item.previewUrl)
        assertEquals("https://t/full1.gif", item.fullUrl)
        assertEquals("image/gif", item.mime)
        assertEquals(2f, item.aspectRatio)
    }

    @Test
    fun `sticker results fall back from gif_transparent to webp_transparent`() {
        val body = """
            {"results":[
                {"id":"s1","media_formats":{
                    "tinygif_transparent":{"url":"https://t/s1_tiny.gif","dims":[100,100]},
                    "webp_transparent":{"url":"https://t/s1.webp","dims":[200,200]}
                }}
            ]}
        """.trimIndent()
        val items = TenorClient.parse(body, stickers = true)
        assertEquals(1, items.size)
        assertEquals("image/webp", items[0].mime)
        assertEquals("https://t/s1.webp", items[0].fullUrl)
    }

    @Test
    fun `missing results key parses to empty list`() {
        assertTrue(TenorClient.parse("{}", stickers = false).isEmpty())
    }

    @Test
    fun `tenor results are tagged with their source`() {
        assertEquals(GifSource.TENOR, TenorClient.parse(gifBody, stickers = false)[0].source)
    }
}

class GiphyClientTest {

    @Test
    fun `results parse preview, original and aspect ratio`() {
        val body = """
            {"data":[
                {"id":"abc","images":{
                    "fixed_width_small":{"url":"https://g/small.gif","width":"200","height":"100"},
                    "original":{"url":"https://g/full.gif","width":"400","height":"200"}
                }},
                {"id":"noimages"}
            ]}
        """.trimIndent()
        val items = GiphyClient.parse(body)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("giphy_abc", item.id)
        assertEquals("https://g/small.gif", item.previewUrl)
        assertEquals("https://g/full.gif", item.fullUrl)
        assertEquals("image/gif", item.mime)
        assertEquals(2f, item.aspectRatio)
        assertEquals(GifSource.GIPHY, item.source)
    }

    @Test
    fun `falls back to original when small renditions missing`() {
        val body = """
            {"data":[{"id":"x","images":{"original":{"url":"https://g/only.gif"}}}]}
        """.trimIndent()
        val items = GiphyClient.parse(body)
        assertEquals("https://g/only.gif", items[0].previewUrl)
        assertEquals("https://g/only.gif", items[0].fullUrl)
    }

    @Test
    fun `missing data parses to empty list`() {
        assertTrue(GiphyClient.parse("{}").isEmpty())
    }
}

class GifSourcesTest {

    private fun item(id: String, source: GifSource) =
        GifItem(id, "p", "f", "image/gif", 1f, source)

    @Test
    fun `interleave alternates sources evenly`() {
        val tenor = listOf(item("t1", GifSource.TENOR), item("t2", GifSource.TENOR), item("t3", GifSource.TENOR))
        val giphy = listOf(item("g1", GifSource.GIPHY))
        val merged = GifSources.interleave(listOf(tenor, giphy))
        assertEquals(listOf("t1", "g1", "t2", "t3"), merged.map { it.id })
    }

    @Test
    fun `interleave of empty lists is empty`() {
        assertTrue(GifSources.interleave(listOf(emptyList(), emptyList())).isEmpty())
    }
}

class GoogleSearchClientTest {

    @Test
    fun `web results parse title, snippet and links`() {
        val body = """
            {"items":[
                {"title":"Kotlin","snippet":"A language.","link":"https://kotlinlang.org/","displayLink":"kotlinlang.org"},
                {"snippet":"missing title and link"}
            ]}
        """.trimIndent()
        val results = GoogleSearchClient.parseWeb(body)
        assertEquals(1, results.size)
        assertEquals("Kotlin", results[0].title)
        assertEquals("https://kotlinlang.org/", results[0].url)
        assertEquals("kotlinlang.org", results[0].displayUrl)
    }

    @Test
    fun `image results parse thumb, full url and mime`() {
        val body = """
            {"items":[
                {"title":"A cat","link":"https://x/cat.jpg","mime":"image/jpeg",
                 "image":{"thumbnailLink":"https://x/cat_t.jpg","contextLink":"https://x/page"}}
            ]}
        """.trimIndent()
        val results = GoogleSearchClient.parseImages(body)
        assertEquals(1, results.size)
        assertEquals("https://x/cat_t.jpg", results[0].thumbUrl)
        assertEquals("https://x/cat.jpg", results[0].imageUrl)
        assertEquals("image/jpeg", results[0].mime)
        assertEquals("https://x/page", results[0].contextUrl)
    }

    @Test
    fun `no items parses to empty list`() {
        assertTrue(GoogleSearchClient.parseWeb("""{"kind":"customsearch#search"}""").isEmpty())
        assertTrue(GoogleSearchClient.parseImages("{}").isEmpty())
    }
}

class ToolHttpTest {

    @Test
    fun `google style error body surfaces the api message`() {
        val body = """{"error":{"code":403,"message":"API key not valid."}}"""
        assertEquals("API key not valid.", ToolHttp.apiErrorMessage(403, body))
    }

    @Test
    fun `unparseable error body falls back to status text`() {
        assertEquals(
            "Rate limit or daily quota exceeded (HTTP 429)",
            ToolHttp.apiErrorMessage(429, "<html>nope</html>"),
        )
    }
}
