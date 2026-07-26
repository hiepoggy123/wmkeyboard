package com.wasimaster.wmkeyboard.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgParserTest {

    private fun svg(body: String, attrs: String = """viewBox="0 0 24 24""""): String =
        """<svg xmlns="http://www.w3.org/2000/svg" $attrs>$body</svg>"""

    // ---- viewport ----

    @Test
    fun `viewBox sets the viewport`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h24v24H0Z"/>""", """viewBox="0 0 48 32""""))!!
        assertEquals(48f, doc.viewportWidth, 0f)
        assertEquals(32f, doc.viewportHeight, 0f)
    }

    @Test
    fun `width and height stand in for a missing viewBox`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h10v10H0Z"/>""", """width="16px" height="20""""))!!
        assertEquals(16f, doc.viewportWidth, 0f)
        assertEquals(20f, doc.viewportHeight, 0f)
    }

    @Test
    fun `no size at all falls back to 24`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h10v10H0Z"/>""", ""))!!
        assertEquals(24f, doc.viewportWidth, 0f)
        assertEquals(24f, doc.viewportHeight, 0f)
    }

    /** A percentage size says nothing about the coordinate system. */
    @Test
    fun `percentage sizes are ignored`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h10v10H0Z"/>""", """width="100%" height="100%""""))!!
        assertEquals(24f, doc.viewportWidth, 0f)
    }

    // ---- monochrome detection ----

    @Test
    fun `an unstyled path is monochrome and filled`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h24v24H0Z"/>"""))!!
        assertTrue(doc.monochrome)
        assertEquals(1, doc.paths.size)
        assertTrue(doc.paths[0].filled)
        assertNull(doc.paths[0].fillArgb)
    }

    @Test
    fun `currentColor stays monochrome`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h24v24H0Z" fill="currentColor"/>"""))!!
        assertTrue(doc.monochrome)
        assertNull(doc.paths[0].fillArgb)
    }

    @Test
    fun `an explicit fill is not monochrome`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h24v24H0Z" fill="#ff0000"/>"""))!!
        assertFalse(doc.monochrome)
        assertEquals(0xFFFF0000L, doc.paths[0].fillArgb)
    }

    /**
     * `fill="none"` with a stroke is the common outline-icon shape: nothing
     * declared a colour, so it must stay tintable.
     */
    @Test
    fun `fill none with an uncoloured stroke stays monochrome`() {
        val doc = SvgParser.parse(
            svg("""<path d="M2 2h20" fill="none" stroke="currentColor" stroke-width="2"/>""")
        )!!
        assertTrue(doc.monochrome)
        assertFalse(doc.paths[0].filled)
        assertTrue(doc.paths[0].stroked)
        assertEquals(2f, doc.paths[0].strokeWidth, 0f)
    }

    @Test
    fun `fill none and no stroke draws nothing`() {
        assertNull(SvgParser.parse(svg("""<path d="M0 0h24v24H0Z" fill="none"/>""")))
    }

    // ---- colour formats ----

    @Test
    fun `short hex expands`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z" fill="#0f0"/>"""))!!
        assertEquals(0xFF00FF00L, doc.paths[0].fillArgb)
    }

    @Test
    fun `eight digit hex moves the alpha to the front`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z" fill="#11223380"/>"""))!!
        assertEquals(0x80112233L, doc.paths[0].fillArgb)
    }

    @Test
    fun `rgb function parses, with and without percentages`() {
        val plain = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z" fill="rgb(255, 128, 0)"/>"""))!!
        assertEquals(0xFFFF8000L, plain.paths[0].fillArgb)
        val percent = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z" fill="rgb(100%, 0%, 0%)"/>"""))!!
        assertEquals(0xFFFF0000L, percent.paths[0].fillArgb)
    }

    @Test
    fun `named colours parse`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z" fill="teal"/>"""))!!
        assertEquals(0xFF008080L, doc.paths[0].fillArgb)
    }

    /** An unreadable colour must not be treated as an authored one. */
    @Test
    fun `an unrecognised colour leaves the path tintable`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z" fill="url(#grad)"/>"""))!!
        assertTrue(doc.monochrome)
        assertNull(doc.paths[0].fillArgb)
    }

    // ---- inherited presentation attributes ----

    @Test
    fun `a group's fill reaches its children`() {
        val doc = SvgParser.parse(
            svg("""<g fill="#123456"><path d="M0 0h1v1H0Z"/></g>""")
        )!!
        assertEquals(0xFF123456L, doc.paths[0].fillArgb)
    }

    @Test
    fun `a child overrides its group`() {
        val doc = SvgParser.parse(
            svg("""<g fill="#123456"><path d="M0 0h1v1H0Z" fill="#abcdef"/></g>""")
        )!!
        assertEquals(0xFFABCDEFL, doc.paths[0].fillArgb)
    }

    @Test
    fun `inline style beats the presentation attribute`() {
        val doc = SvgParser.parse(
            svg("""<path d="M0 0h1v1H0Z" fill="#000000" style="fill:#ff0000;fill-opacity:0.5"/>""")
        )!!
        assertEquals(0xFFFF0000L, doc.paths[0].fillArgb)
        assertEquals(0.5f, doc.paths[0].fillAlpha, 0.001f)
    }

    @Test
    fun `group opacity multiplies down the tree`() {
        val doc = SvgParser.parse(
            svg("""<g opacity="0.5"><path d="M0 0h1v1H0Z" fill-opacity="0.5"/></g>""")
        )!!
        assertEquals(0.25f, doc.paths[0].fillAlpha, 0.001f)
    }

    // ---- transforms ----

    @Test
    fun `translate becomes an affine`() {
        val doc = SvgParser.parse(
            svg("""<g transform="translate(2 3)"><path d="M0 0h1v1H0Z"/></g>""")
        )!!
        val t = doc.paths[0].transform!!
        assertEquals(6, t.size)
        assertEquals(2f, t[4], 0.001f)
        assertEquals(3f, t[5], 0.001f)
    }

    @Test
    fun `nested transforms compose`() {
        val doc = SvgParser.parse(
            svg("""<g transform="translate(10 0)"><g transform="scale(2)"><path d="M0 0h1v1H0Z"/></g></g>""")
        )!!
        val t = doc.paths[0].transform!!
        assertEquals(2f, t[0], 0.001f)
        assertEquals(2f, t[3], 0.001f)
        assertEquals(10f, t[4], 0.001f)
    }

    @Test
    fun `matrix is taken as written`() {
        val doc = SvgParser.parse(
            svg("""<path d="M0 0h1v1H0Z" transform="matrix(1 2 3 4 5 6)"/>""")
        )!!
        val t = doc.paths[0].transform!!
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f), t.toList())
    }

    @Test
    fun `an untransformed path has no matrix`() {
        val doc = SvgParser.parse(svg("""<path d="M0 0h1v1H0Z"/>"""))!!
        assertNull(doc.paths[0].transform)
    }

    // ---- basic shapes ----

    @Test
    fun `rect becomes a path`() {
        val doc = SvgParser.parse(svg("""<rect x="1" y="2" width="10" height="5"/>"""))!!
        assertEquals(1, doc.paths.size)
        assertTrue(doc.paths[0].pathData.startsWith("M1"))
    }

    @Test
    fun `a rounded rect uses arcs`() {
        val doc = SvgParser.parse(svg("""<rect width="10" height="10" rx="2"/>"""))!!
        assertTrue(doc.paths[0].pathData.contains("a"))
    }

    @Test
    fun `a zero-size rect is dropped`() {
        assertNull(SvgParser.parse(svg("""<rect width="0" height="10"/>""")))
    }

    @Test
    fun `circle and ellipse become paths`() {
        assertEquals(1, SvgParser.parse(svg("""<circle cx="12" cy="12" r="6"/>"""))!!.paths.size)
        assertEquals(1, SvgParser.parse(svg("""<ellipse cx="12" cy="12" rx="6" ry="3"/>"""))!!.paths.size)
        assertNull(SvgParser.parse(svg("""<circle cx="12" cy="12" r="0"/>""")))
    }

    @Test
    fun `polygon closes and polyline does not`() {
        val polygon = SvgParser.parse(svg("""<polygon points="0,0 10,0 10,10"/>"""))!!
        assertTrue(polygon.paths[0].pathData.endsWith("Z"))
        val polyline = SvgParser.parse(
            svg("""<polyline points="0,0 10,0 10,10" stroke="currentColor"/>""")
        )!!
        assertFalse(polyline.paths[0].pathData.endsWith("Z"))
    }

    /** A stroke-only element with no stroke declared is invisible. */
    @Test
    fun `line without a stroke is dropped`() {
        assertNull(SvgParser.parse(svg("""<line x1="0" y1="0" x2="10" y2="10"/>""")))
        assertNotNull(
            SvgParser.parse(svg("""<line x1="0" y1="0" x2="10" y2="10" stroke="black"/>"""))
        )
    }

    // ---- skipped content ----

    @Test
    fun `text, images and gradients are skipped, not fatal`() {
        val doc = SvgParser.parse(
            svg(
                """<defs><linearGradient id="g"><stop offset="0"/></linearGradient></defs>""" +
                    """<text x="0" y="0">hi</text>""" +
                    """<image href="x.png" width="4" height="4"/>""" +
                    """<path d="M0 0h1v1H0Z"/>"""
            )
        )!!
        assertEquals(1, doc.paths.size)
    }

    // ---- rejection ----

    @Test
    fun `non-svg input returns null`() {
        assertNull(SvgParser.parse(""))
        assertNull(SvgParser.parse("not xml at all"))
        assertNull(SvgParser.parse("""<html><body>hi</body></html>"""))
        assertNull(SvgParser.parse(svg("")))
    }

    @Test
    fun `truncated xml keeps whatever parsed`() {
        val doc = SvgParser.parse(
            """<svg viewBox="0 0 24 24"><path d="M0 0h24v24H0Z"/><path d="M1 1"""
        )
        assertEquals(1, doc!!.paths.size)
    }

    /**
     * An icon pack is untrusted input from a stranger's repository. A DTD is
     * the entry point for an XXE that would read app-private files, so it must
     * be refused outright rather than parsed with entities disabled.
     */
    @Test
    fun `a doctype is refused`() {
        val hostile = """<?xml version="1.0"?>
            |<!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            |<svg viewBox="0 0 24 24"><path d="M0 0h24v24H0Z"/></svg>
        """.trimMargin()
        assertNull(SvgParser.parse(hostile))
    }

    @Test
    fun `an oversized file is refused`() {
        val huge = svg("""<path d="${"M0 0h1v1H0Z".repeat(40_000)}"/>""")
        assertTrue(huge.length > SvgParser.MAX_SOURCE_BYTES)
        assertNull(SvgParser.parse(huge))
    }

    @Test
    fun `an over-long single path is dropped`() {
        val long = "M0 0" + "h1".repeat(40_000)
        val doc = SvgParser.parse(svg("""<path d="$long"/><path d="M0 0h1v1H0Z"/>"""))!!
        // The long one is dropped; the ordinary one beside it survives.
        assertEquals(1, doc.paths.size)
        assertEquals("M0 0h1v1H0Z", doc.paths[0].pathData)
    }

    @Test
    fun `too many paths are capped`() {
        val many = "<path d=\"M0 0h1v1H0Z\"/>".repeat(500)
        val doc = SvgParser.parse(svg(many))!!
        assertEquals(400, doc.paths.size)
    }
}
