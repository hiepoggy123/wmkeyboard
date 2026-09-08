package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The LibreTranslate client, which the F-Droid build translates with instead of
 * Google's endpoint.
 *
 * All URL building and parsing. The URL matters because the endpoint is typed
 * by a person out of a README, and a field that accepts only one of the four
 * reasonable spellings fails as a 404 that reads like the server being down.
 */
class LibreTranslateClientTest {

    // ---- LibreTranslate ----------------------------------------------------

    @Test
    fun `libretranslate accepts every reasonable spelling of an endpoint`() {
        val expected = "https://lt.example.org/translate"
        assertEquals(expected, LibreTranslateClient.translateUrl("lt.example.org"))
        assertEquals(expected, LibreTranslateClient.translateUrl("https://lt.example.org"))
        assertEquals(expected, LibreTranslateClient.translateUrl("https://lt.example.org/"))
        assertEquals(expected, LibreTranslateClient.translateUrl("https://lt.example.org/translate"))
        assertEquals(expected, LibreTranslateClient.translateUrl("  lt.example.org  "))
    }

    @Test
    fun `libretranslate keeps a self-hosted http endpoint as typed`() {
        // Someone running one on their LAN typed http:// on purpose; upgrading
        // it to https behind their back would just fail to connect.
        assertEquals(
            "http://192.168.0.10:5000/translate",
            LibreTranslateClient.translateUrl("http://192.168.0.10:5000"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `libretranslate refuses a scheme that is not http`() {
        LibreTranslateClient.translateUrl("ftp://lt.example.org")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `libretranslate refuses an empty endpoint`() {
        LibreTranslateClient.translateUrl("   ")
    }

    @Test
    fun `libretranslate reads a single translation`() {
        val body = """
            {"translatedText":"¡Hola mundo!","detectedLanguage":{"confidence":100.0,"language":"en"}}
        """.trimIndent()
        val result = LibreTranslateClient.parse(body)
        assertEquals("¡Hola mundo!", result.text)
        assertEquals("en", result.detectedSource)
    }

    @Test
    fun `libretranslate reads the batch shape a proxy may return`() {
        // We only ever send one string, but an instance behind something that
        // normalises everything to a list should not crash the panel.
        val body = """
            {"translatedText":["¡Hola mundo!"],"detectedLanguage":[{"confidence":99.0,"language":"bn"}]}
        """.trimIndent()
        val result = LibreTranslateClient.parse(body)
        assertEquals("¡Hola mundo!", result.text)
        assertEquals("bn", result.detectedSource)
    }

    @Test
    fun `libretranslate survives a response with no detected language`() {
        val result = LibreTranslateClient.parse("""{"translatedText":"hi"}""")
        assertEquals("hi", result.text)
        assertEquals("", result.detectedSource)
    }
}
