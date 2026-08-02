package com.wasimaster.wmkeyboard.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URL building and response parsing for both photo providers, against captured
 * response shapes. The two APIs disagree about more than they agree on, so most
 * of these tests exist to stop one provider's spelling leaking into the other.
 */
class PhotoClientsTest {

    // ================= Unsplash =================

    @Test
    fun `unsplash search sends the safety filter and escapes the query`() {
        val url = UnsplashClient.searchUrl(
            PhotoQuery(text = " black & white ", page = 2, perPage = 24, safe = true),
        )
        assertTrue(url, url.startsWith("https://api.unsplash.com/search/photos?"))
        assertTrue(url, url.contains("query=black+%26+white"))
        assertTrue(url, url.contains("page=2"))
        assertTrue(url, url.contains("per_page=24"))
        assertTrue(url, url.contains("content_filter=high"))
    }

    @Test
    fun `unsplash drops the safety filter when it is off`() {
        val url = UnsplashClient.searchUrl(PhotoQuery(text = "cats", safe = false))
        assertFalse(url, url.contains("content_filter"))
    }

    @Test
    fun `unsplash clamps per_page to what the API accepts`() {
        assertTrue(UnsplashClient.searchUrl(PhotoQuery(text = "a", perPage = 500)).contains("per_page=30"))
        assertTrue(UnsplashClient.searchUrl(PhotoQuery(text = "a", perPage = 0)).contains("per_page=1"))
    }

    @Test
    fun `unsplash calls a square photo squarish`() {
        assertEquals("squarish", UnsplashClient.orientationParam(PhotoOrientation.SQUARE))
        assertNull(UnsplashClient.orientationParam(PhotoOrientation.ANY))
    }

    @Test
    fun `unsplash drops the colours it has no name for`() {
        assertNull(UnsplashClient.colorParam(PhotoColor.GRAY))
        assertNull(UnsplashClient.colorParam(PhotoColor.BROWN))
        assertEquals("black_and_white", UnsplashClient.colorParam(PhotoColor.MONOCHROME))
        assertEquals("teal", UnsplashClient.colorParam(PhotoColor.TEAL))
    }

    @Test
    fun `unsplash feed uses the topic endpoint when a topic is set`() {
        val topic = UnsplashClient.feedUrl(PhotoQuery(topicId = "textures-patterns"))
        assertTrue(topic, topic.startsWith("https://api.unsplash.com/topics/textures-patterns/photos?"))
        val plain = UnsplashClient.feedUrl(PhotoQuery())
        assertTrue(plain, plain.startsWith("https://api.unsplash.com/photos?"))
    }

    @Test
    fun `unsplash random asks for many photos in one request`() {
        val url = UnsplashClient.randomUrl(PhotoQuery(topicId = "nature", safe = true), count = 10)
        assertTrue(url, url.contains("count=10"))
        assertTrue(url, url.contains("topics=nature"))
        assertTrue(url, url.contains("content_filter=high"))
        // Their own cap; asking for more is an error rather than a truncation.
        assertTrue(UnsplashClient.randomUrl(PhotoQuery(), count = 99).contains("count=30"))
    }

    @Test
    fun `unsplash search parses the envelope and its paging`() {
        val page = UnsplashClient.parseSearch(UNSPLASH_SEARCH, page = 1)
        assertEquals(1, page.items.size)
        assertEquals(1234, page.totalResults)
        assertTrue(page.hasMore)
        assertEquals(PhotoSource.UNSPLASH, page.source)
        assertFalse(UnsplashClient.parseSearch(UNSPLASH_SEARCH, page = 52).hasMore)
    }

    @Test
    fun `unsplash feed parses a bare array with no envelope`() {
        val full = UnsplashClient.parseList(UNSPLASH_LIST, page = 1, perPage = 1)
        assertEquals(1, full.items.size)
        // A full page is the only hint another one exists.
        assertTrue(full.hasMore)
        assertFalse(UnsplashClient.parseList(UNSPLASH_LIST, page = 1, perPage = 24).hasMore)
    }

    @Test
    fun `unsplash maps every field the credit and the download need`() {
        val photo = UnsplashClient.parseSearch(UNSPLASH_SEARCH, page = 1).items.single()
        assertEquals("abc123", photo.id)
        assertEquals("https://images.unsplash.com/photo-1?ixid=xyz", photo.fullUrl)
        assertEquals("https://images.unsplash.com/photo-1-small", photo.thumbUrl)
        assertEquals("https://unsplash.com/photos/abc123", photo.pageUrl)
        assertEquals("Ana Silva", photo.photographer)
        assertEquals("https://unsplash.com/@ana", photo.photographerUrl)
        assertEquals("https://api.unsplash.com/photos/abc123/download", photo.downloadLocation)
        assertEquals("#0c1014", photo.avgColor)
        assertEquals("a misty forest", photo.altText)
        assertEquals(4000, photo.width)
        assertEquals(3000, photo.height)
        assertTrue(photo.resizable)
        assertTrue(photo.variants.isEmpty())
    }

    @Test
    fun `unsplash JSON null never reaches the screen as the word null`() {
        val photo = UnsplashClient.parseList(UNSPLASH_NULLS, page = 1, perPage = 24).items.single()
        // `jsonPrimitive.content` on JsonNull yields "null", which would show up
        // as a caption reading "null" under the photo.
        assertEquals("", photo.avgColor)
        assertEquals("", photo.altText)
        assertEquals("", photo.blurHash)
    }

    @Test
    fun `unsplash falls back to the description when there is no alt text`() {
        val photo = UnsplashClient.parseList(UNSPLASH_DESCRIPTION_ONLY, page = 1, perPage = 24).items.single()
        assertEquals("a lighthouse", photo.altText)
    }

    @Test
    fun `unsplash skips a result with no usable image URL`() {
        assertNull(UnsplashClient.parsePhoto(json("""{"id":"x","urls":{}}""")))
        assertNull(UnsplashClient.parsePhoto(json("""{"urls":{"raw":"https://x"}}""")))
    }

    // ================= Pexels =================

    @Test
    fun `pexels sends the key with no scheme`() {
        assertEquals("secret", PexelsClient.headers("secret")["Authorization"])
    }

    @Test
    fun `pexels sends no safety parameter because it has none`() {
        val url = PexelsClient.searchUrl(PhotoQuery(text = "cats", safe = true))
        assertFalse(url, url.contains("content_filter"))
        assertFalse(url, url.contains("safe"))
    }

    @Test
    fun `pexels clamps per_page to its own higher maximum`() {
        assertTrue(PexelsClient.searchUrl(PhotoQuery(text = "a", perPage = 500)).contains("per_page=80"))
    }

    @Test
    fun `pexels calls a square photo square`() {
        assertEquals("square", PexelsClient.orientationParam(PhotoOrientation.SQUARE))
        assertNull(PexelsClient.orientationParam(PhotoOrientation.ANY))
    }

    @Test
    fun `pexels uses its own colour vocabulary`() {
        assertEquals("turquoise", PexelsClient.colorParam(PhotoColor.TEAL))
        assertEquals("violet", PexelsClient.colorParam(PhotoColor.PURPLE))
        assertEquals("pink", PexelsClient.colorParam(PhotoColor.MAGENTA))
        assertEquals("gray", PexelsClient.colorParam(PhotoColor.GRAY))
        assertNull(PexelsClient.colorParam(PhotoColor.MONOCHROME))
    }

    @Test
    fun `pexels reads a numeric id as text`() {
        val photo = PexelsClient.parsePage(PEXELS_SEARCH, page = 1, perPage = 24).items.single()
        assertEquals("2014422", photo.id)
    }

    @Test
    fun `pexels pages on next_page rather than a page count`() {
        val withNext = PexelsClient.parsePage(PEXELS_SEARCH, page = 1, perPage = 24)
        assertTrue(withNext.hasMore)
        val last = PexelsClient.parsePage(PEXELS_LAST_PAGE, page = 2, perPage = 24)
        assertFalse(last.hasMore)
    }

    @Test
    fun `pexels falls back to counting when next_page is absent`() {
        // No next_page field at all: 1 * 24 < 100, so there is more to come.
        val more = PexelsClient.parsePage(PEXELS_NO_NEXT, page = 1, perPage = 24)
        assertTrue(more.hasMore)
        val done = PexelsClient.parsePage(PEXELS_NO_NEXT, page = 5, perPage = 24)
        assertFalse(done.hasMore)
    }

    @Test
    fun `pexels maps the credit fields`() {
        val photo = PexelsClient.parsePage(PEXELS_SEARCH, page = 1, perPage = 24).items.single()
        assertEquals("Ana Silva", photo.photographer)
        assertEquals("https://www.pexels.com/@ana", photo.photographerUrl)
        assertEquals("https://www.pexels.com/photo/2014422/", photo.pageUrl)
        assertEquals("#3a4a5a", photo.avgColor)
        assertEquals("green trees", photo.altText)
        // Pexels never asks for a download ping.
        assertEquals("", photo.downloadLocation)
        assertFalse(photo.resizable)
    }

    @Test
    fun `pexels reports the real pixel size of each named variant`() {
        val photo = PexelsClient.parsePage(PEXELS_SEARCH, page = 1, perPage = 24).items.single()
        fun variant(url: String) = photo.variants.firstOrNull { it.url.endsWith(url) }

        // "940x650 at DPR 2" is 1880x1300 of actual image, which is what
        // decides whether it covers the keyboard.
        assertEquals(1880, variant("large2x")?.width)
        assertEquals(1300, variant("large2x")?.height)
        assertEquals(1200, variant("landscape")?.width)
        assertEquals(627, variant("landscape")?.height)
        // A flexible-width size takes its width from the photo's own shape:
        // 350 px tall at 3000x2000 is 525 px wide.
        assertEquals(350, variant("medium")?.height)
        assertEquals(525, variant("medium")?.width)
        assertNotNull(variant("original"))
        assertEquals(3000, variant("original")?.width)
    }

    @Test
    fun `pexels skips a result with no src block`() {
        assertNull(PexelsClient.parsePhoto(json("""{"id":1,"width":10,"height":10}""")))
    }

    // ---- fixtures -----------------------------------------------------

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private val UNSPLASH_SEARCH = """
        {
          "total": 1234,
          "total_pages": 52,
          "results": [
            {
              "id": "abc123",
              "width": 4000,
              "height": 3000,
              "color": "#0c1014",
              "blur_hash": "LKO2",
              "description": null,
              "alt_description": "a misty forest",
              "urls": {
                "raw": "https://images.unsplash.com/photo-1?ixid=xyz",
                "full": "https://images.unsplash.com/photo-1-full",
                "small": "https://images.unsplash.com/photo-1-small",
                "thumb": "https://images.unsplash.com/photo-1-thumb"
              },
              "links": {
                "html": "https://unsplash.com/photos/abc123",
                "download": "https://unsplash.com/photos/abc123/download",
                "download_location": "https://api.unsplash.com/photos/abc123/download"
              },
              "user": {
                "username": "ana",
                "name": "Ana Silva",
                "links": { "html": "https://unsplash.com/@ana" }
              }
            }
          ]
        }
    """.trimIndent()

    private val UNSPLASH_LIST = """
        [
          {
            "id": "def456",
            "width": 3000,
            "height": 2000,
            "color": "#ffffff",
            "alt_description": "a beach",
            "urls": { "raw": "https://images.unsplash.com/photo-2", "small": "https://images.unsplash.com/photo-2-small" },
            "links": { "html": "https://unsplash.com/photos/def456" },
            "user": { "name": "Bo", "links": { "html": "https://unsplash.com/@bo" } }
          }
        ]
    """.trimIndent()

    private val UNSPLASH_NULLS = """
        [
          {
            "id": "ghi789",
            "width": 100,
            "height": 100,
            "color": null,
            "blur_hash": null,
            "description": null,
            "alt_description": null,
            "urls": { "raw": "https://images.unsplash.com/photo-3" },
            "links": { "html": "https://unsplash.com/photos/ghi789" },
            "user": { "name": "Cy", "links": { "html": "https://unsplash.com/@cy" } }
          }
        ]
    """.trimIndent()

    private val UNSPLASH_DESCRIPTION_ONLY = """
        [
          {
            "id": "jkl012",
            "width": 100,
            "height": 100,
            "description": "a lighthouse",
            "alt_description": null,
            "urls": { "raw": "https://images.unsplash.com/photo-4" },
            "links": { "html": "https://unsplash.com/photos/jkl012" },
            "user": { "name": "Di", "links": { "html": "https://unsplash.com/@di" } }
          }
        ]
    """.trimIndent()

    private val PEXELS_SEARCH = """
        {
          "page": 1,
          "per_page": 24,
          "total_results": 8000,
          "next_page": "https://api.pexels.com/v1/search/?page=2&per_page=24&query=trees",
          "photos": [
            {
              "id": 2014422,
              "width": 3000,
              "height": 2000,
              "url": "https://www.pexels.com/photo/2014422/",
              "photographer": "Ana Silva",
              "photographer_url": "https://www.pexels.com/@ana",
              "photographer_id": 2680452,
              "avg_color": "#3a4a5a",
              "alt": "green trees",
              "src": {
                "original": "https://images.pexels.com/photos/2014422/original",
                "large2x": "https://images.pexels.com/photos/2014422/large2x",
                "large": "https://images.pexels.com/photos/2014422/large",
                "medium": "https://images.pexels.com/photos/2014422/medium",
                "small": "https://images.pexels.com/photos/2014422/small",
                "portrait": "https://images.pexels.com/photos/2014422/portrait",
                "landscape": "https://images.pexels.com/photos/2014422/landscape",
                "tiny": "https://images.pexels.com/photos/2014422/tiny"
              }
            }
          ]
        }
    """.trimIndent()

    private val PEXELS_LAST_PAGE = """
        { "page": 2, "per_page": 24, "total_results": 30, "photos": [], "next_page": "" }
    """.trimIndent()

    private val PEXELS_NO_NEXT = """
        { "page": 1, "per_page": 24, "total_results": 100, "photos": [] }
    """.trimIndent()
}
