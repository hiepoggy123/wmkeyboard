package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sizing and link rules that keep a downloaded background small enough for
 * the keyboard process and keep both providers' attribution intact.
 */
class PhotoSizingTest {

    // ---- withImgix ----------------------------------------------------

    @Test
    fun `withImgix keeps the tracking parameters Unsplash requires`() {
        val url = withImgix(
            "https://images.unsplash.com/photo-1?ixid=M3wxMjA&ixlib=rb-4.0.3",
            mapOf("w" to "1080", "fm" to "jpg"),
        )
        assertTrue(url, url.contains("ixid=M3wxMjA"))
        assertTrue(url, url.contains("ixlib=rb-4.0.3"))
        assertTrue(url, url.contains("w=1080"))
        assertTrue(url, url.contains("fm=jpg"))
    }

    @Test
    fun `withImgix replaces a parameter the URL already carried`() {
        val url = withImgix(
            "https://images.unsplash.com/photo-1?w=1080&q=80&ixid=abc",
            mapOf("w" to "2160"),
        )
        assertTrue(url, url.contains("w=2160"))
        assertFalse(url, url.contains("w=1080"))
        // Untouched keys stay exactly as they arrived.
        assertTrue(url, url.contains("q=80"))
        assertTrue(url, url.contains("ixid=abc"))
    }

    @Test
    fun `withImgix opens a query string when the URL has none`() {
        val url = withImgix("https://images.unsplash.com/photo-1", mapOf("w" to "800"))
        assertEquals("https://images.unsplash.com/photo-1?w=800", url)
    }

    @Test
    fun `withImgix leaves a URL alone when there is nothing to add`() {
        val original = "https://images.unsplash.com/photo-1?ixid=abc"
        assertEquals(original, withImgix(original, emptyMap()))
    }

    // ---- downloadUrl --------------------------------------------------

    @Test
    fun `a resizable photo is asked for the exact target size`() {
        val url = PhotoSizing.downloadUrl(unsplashItem(), PhotoSizing.PORTRAIT_STRIP)
        assertTrue(url, url.contains("w=1080"))
        assertTrue(url, url.contains("h=560"))
        assertTrue(url, url.contains("fit=crop"))
        assertTrue(url, url.contains("crop=entropy"))
        assertTrue(url, url.contains("fm=jpg"))
        assertTrue(url, url.contains("dpr=1"))
        assertTrue(url, url.contains("ixid=keepme"))
        // `auto=format` would make the returned format depend on an Accept
        // header the downloader never sends.
        assertFalse(url, url.contains("auto="))
    }

    @Test
    fun `a resizable photo gets the wider target in landscape`() {
        val url = PhotoSizing.downloadUrl(unsplashItem(), PhotoSizing.LANDSCAPE_STRIP)
        assertTrue(url, url.contains("w=2160"))
        assertTrue(url, url.contains("h=640"))
    }

    @Test
    fun `a fixed-size photo takes the smallest variant that covers the target`() {
        val url = PhotoSizing.downloadUrl(pexelsItem(), PhotoSizing.PORTRAIT_STRIP)
        // large2x (1880x1300) covers 1080x560 and so does original, but
        // `landscape` at 1200x627 is the smallest that does.
        assertEquals("https://images.pexels.com/p/landscape", url)
    }

    @Test
    fun `a fixed-size photo needs a big variant to cover the landscape target`() {
        // 2160 px wide rules out every variant below the original.
        val url = PhotoSizing.downloadUrl(pexelsItem(), PhotoSizing.LANDSCAPE_STRIP)
        assertEquals("https://images.pexels.com/p/original", url)
    }

    @Test
    fun `a fixed-size photo takes the largest variant when none covers the target`() {
        val small = pexelsItem().copy(
            variants = listOf(
                PhotoVariant("https://images.pexels.com/p/tiny", 280, 200),
                PhotoVariant("https://images.pexels.com/p/medium", 350, 233),
            ),
        )
        // Soft beats absent: `Crop` upscales, but there is nothing better.
        assertEquals(
            "https://images.pexels.com/p/medium",
            PhotoSizing.downloadUrl(small, PhotoSizing.PORTRAIT_STRIP),
        )
    }

    @Test
    fun `a fixed-size photo with no variants falls back to its full URL`() {
        val bare = pexelsItem().copy(variants = emptyList())
        assertEquals(bare.fullUrl, PhotoSizing.downloadUrl(bare, PhotoSizing.PORTRAIT_STRIP))
    }

    // ---- PhotoLinks ---------------------------------------------------

    @Test
    fun `an Unsplash link carries the referral parameters`() {
        val url = PhotoLinks.credited("https://unsplash.com/@ana", PhotoSource.UNSPLASH)
        assertEquals(
            "https://unsplash.com/@ana?utm_source=wm_keyboard&utm_medium=referral",
            url,
        )
    }

    @Test
    fun `an Unsplash link that already has a query keeps it`() {
        val url = PhotoLinks.credited("https://unsplash.com/@ana?foo=1", PhotoSource.UNSPLASH)
        assertEquals(
            "https://unsplash.com/@ana?foo=1&utm_source=wm_keyboard&utm_medium=referral",
            url,
        )
    }

    @Test
    fun `a Pexels link is left as it is`() {
        val url = "https://www.pexels.com/@ana"
        assertEquals(url, PhotoLinks.credited(url, PhotoSource.PEXELS))
    }

    @Test
    fun `a blank link stays blank rather than becoming a bare query string`() {
        assertEquals("", PhotoLinks.credited("", PhotoSource.UNSPLASH))
    }

    @Test
    fun `a provider key round-trips and an unknown one is not guessed at`() {
        for (source in PhotoSource.entries) {
            assertEquals(source, PhotoLinks.sourceOf(PhotoLinks.providerKey(source)))
        }
        assertEquals(PhotoSource.UNSPLASH, PhotoLinks.sourceOf("UNSPLASH"))
        assertEquals(null, PhotoLinks.sourceOf("flickr"))
    }

    // ---- interleavePhotos ---------------------------------------------

    @Test
    fun `interleaving alternates providers and drops repeated keys`() {
        val unsplash = listOf(item(PhotoSource.UNSPLASH, "a"), item(PhotoSource.UNSPLASH, "b"))
        // "x" repeats: a provider that reshuffles between page requests really
        // does hand the same photo back twice, and a duplicate LazyGrid key
        // crashes the panel.
        val pexels = listOf(item(PhotoSource.PEXELS, "x"), item(PhotoSource.PEXELS, "x"))
        val merged = interleavePhotos(listOf(unsplash, pexels))
        assertEquals(listOf("UNSPLASH:a", "PEXELS:x", "UNSPLASH:b"), merged.map { it.key })
    }

    @Test
    fun `interleaving a single list keeps its order`() {
        val only = listOf(item(PhotoSource.UNSPLASH, "a"), item(PhotoSource.UNSPLASH, "b"))
        assertEquals(only, interleavePhotos(listOf(only)))
    }

    // ---- fixtures -----------------------------------------------------

    private fun item(source: PhotoSource, id: String) = PhotoItem(
        id = id,
        source = source,
        thumbUrl = "https://example.test/$id/thumb",
        fullUrl = "https://example.test/$id/full",
        pageUrl = "https://example.test/$id",
        photographer = "Ana",
        photographerUrl = "https://example.test/ana",
        width = 4000,
        height = 3000,
        avgColor = "#112233",
        altText = "",
        resizable = source == PhotoSource.UNSPLASH,
    )

    private fun unsplashItem() = item(PhotoSource.UNSPLASH, "u1").copy(
        fullUrl = "https://images.unsplash.com/photo-1?ixid=keepme",
    )

    private fun pexelsItem() = item(PhotoSource.PEXELS, "1234").copy(
        fullUrl = "https://images.pexels.com/p/original",
        resizable = false,
        variants = listOf(
            PhotoVariant("https://images.pexels.com/p/medium", 350, 233),
            PhotoVariant("https://images.pexels.com/p/large", 940, 650),
            PhotoVariant("https://images.pexels.com/p/landscape", 1200, 627),
            PhotoVariant("https://images.pexels.com/p/large2x", 1880, 1300),
            PhotoVariant("https://images.pexels.com/p/original", 4000, 3000),
        ),
    )
}
