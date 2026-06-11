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

class KlipyClientTest {

    private val gifBody = """
        {"result":true,"data":{"data":[
            {"id":111,"title":"cat","file":{
                "sm":{"gif":{"url":"https://k/sm1.gif","width":200,"height":100}},
                "hd":{"gif":{"url":"https://k/hd1.gif","width":400,"height":200}}
            }},
            {"id":222,"title":"no file"}
        ],"current_page":1,"has_next":true}}
    """.trimIndent()

    @Test
    fun `gif results parse preview, full url, aspect ratio and source`() {
        val items = KlipyClient.parse(gifBody)
        // The second result has no file object and is skipped.
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("klipy_111", item.id)
        assertEquals("https://k/sm1.gif", item.previewUrl)
        assertEquals("https://k/hd1.gif", item.fullUrl)
        assertEquals("image/gif", item.mime)
        assertEquals(2f, item.aspectRatio)
        assertEquals(GifSource.KLIPY, item.source)
    }

    @Test
    fun `falls back through sizes when sm and hd missing`() {
        val body = """
            {"result":true,"data":{"data":[
                {"id":"x","file":{
                    "xs":{"gif":{"url":"https://k/xs.gif"}},
                    "md":{"gif":{"url":"https://k/md.gif"}}
                }}
            ]}}
        """.trimIndent()
        val item = KlipyClient.parse(body)[0]
        assertEquals("https://k/xs.gif", item.previewUrl)
        assertEquals("https://k/md.gif", item.fullUrl)
    }

    @Test
    fun `data as a bare array also parses`() {
        val body = """
            {"result":true,"data":[
                {"id":"y","file":{"sm":{"gif":{"url":"https://k/s.gif"}},"hd":{"gif":{"url":"https://k/h.gif"}}}}
            ]}
        """.trimIndent()
        assertEquals(1, KlipyClient.parse(body).size)
    }

    @Test
    fun `missing data parses to empty list`() {
        assertTrue(KlipyClient.parse("{}").isEmpty())
        assertTrue(KlipyClient.parse("""{"result":true,"data":{}}""").isEmpty())
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
        val klipy = listOf(item("k1", GifSource.KLIPY), item("k2", GifSource.KLIPY), item("k3", GifSource.KLIPY))
        val giphy = listOf(item("g1", GifSource.GIPHY))
        val merged = GifSources.interleave(listOf(klipy, giphy))
        assertEquals(listOf("k1", "g1", "k2", "k3"), merged.map { it.id })
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
