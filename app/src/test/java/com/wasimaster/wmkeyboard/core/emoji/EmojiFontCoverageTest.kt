package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader is pure byte work, so it is tested against fonts built here rather
 * than against a megabyte of checked-in binary — and building them is the only
 * way to test the cases that matter, which are all about what a font *omits*.
 */
class EmojiFontCoverageTest {

    @Test
    fun `a font without a format 14 subtable declares no variation sequence`() {
        val font = TestFont.build(variationSequences = false)
        val coverage = requireNotNull(EmojiFontCoverage.read(font))
        assertTrue(coverage.covers(HEART))
        assertTrue(coverage.covers(SELECTOR))
        assertFalse(coverage.hasVariationSequence(HEART, SELECTOR))
    }

    @Test
    fun `a format 14 subtable is read`() {
        val font = TestFont.build(variationSequences = true)
        val coverage = requireNotNull(EmojiFontCoverage.read(font))
        assertTrue(coverage.hasVariationSequence(HEART, SELECTOR))
        // Only the pair the font actually lists.
        assertFalse(coverage.hasVariationSequence(FIRE, SELECTOR))
    }

    @Test
    fun `code points the font omits are not covered`() {
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build()))
        assertTrue(coverage.covers(FIRE))
        // U+1FAE0 melting face, absent from every pre-Unicode-14 set.
        assertFalse(coverage.covers(0x1FAE0))
    }

    @Test
    fun `a ligature keyed on the unqualified sequence is found`() {
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build()))
        assertTrue(coverage.hasLigatures)
        assertTrue(coverage.ligates(intArrayOf(HEART, ZWJ, FIRE)))
        // The qualified spelling is a different sequence and this font
        // does not have it.
        assertFalse(coverage.ligates(intArrayOf(HEART, SELECTOR, ZWJ, FIRE)))
    }

    @Test
    fun `a ligature keyed on the qualified sequence is found`() {
        val font = TestFont.build(ligatureComponents = intArrayOf(SELECTOR, ZWJ, FIRE))
        val coverage = requireNotNull(EmojiFontCoverage.read(font))
        assertTrue(coverage.ligates(intArrayOf(HEART, SELECTOR, ZWJ, FIRE)))
        assertFalse(coverage.ligates(intArrayOf(HEART, ZWJ, FIRE)))
    }

    @Test
    fun `a sequence with an uncovered code point never ligates`() {
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build()))
        assertFalse(coverage.ligates(intArrayOf(HEART, ZWJ, 0x1FAE0)))
    }

    @Test
    fun `a font with no GSUB reports no ligatures`() {
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build(ligature = false)))
        assertFalse(coverage.hasLigatures)
        assertFalse(coverage.ligates(intArrayOf(HEART, ZWJ, FIRE)))
    }

    @Test
    fun `an extension lookup is followed through to the ligatures behind it`() {
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build(extension = true)))
        assertTrue(coverage.ligates(intArrayOf(HEART, ZWJ, FIRE)))
    }

    @Test
    fun `rubbish is rejected rather than thrown from`() {
        assertNull(EmojiFontCoverage.read(ByteArray(0)))
        assertNull(EmojiFontCoverage.read(ByteArray(64) { it.toByte() }))
        // A real header with the tables truncated away.
        assertNull(EmojiFontCoverage.read(TestFont.build().copyOf(40)))
    }

    @Test
    fun `truncation mid-table yields empty coverage rather than an exception`() {
        val font = TestFont.build()
        // Every prefix either parses or is rejected; none may throw.
        for (cut in font.indices step 7) {
            EmojiFontCoverage.read(font.copyOf(cut))
        }
    }

    @Test
    fun `a single code point is not a ligature`() {
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build()))
        assertFalse(coverage.ligates(intArrayOf(HEART)))
    }

    @Test
    fun `glyph ids come from the right subtable`() {
        // Nothing exposes glyph ids directly; the ligature lookup is keyed on
        // them, so a BMP/supplementary mix-up would break this.
        val coverage = requireNotNull(EmojiFontCoverage.read(TestFont.build()))
        assertEquals(true, coverage.ligates(intArrayOf(HEART, ZWJ, FIRE)))
    }

    private companion object {
        const val ZWJ = 0x200D
        const val HEART = 0x2764
        const val SELECTOR = 0xFE0F
        const val FIRE = 0x1F525
    }
}

/**
 * The smallest font that exercises the reader: a `cmap` with a format 4
 * subtable (the BMP characters), a format 12 one (the supplementary fire), an
 * optional format 14 one, and a `GSUB` holding a single ligature.
 */
private object TestFont {

    private const val GLYPH_HEART = 10
    private const val GLYPH_ZWJ = 11
    private const val GLYPH_SELECTOR = 12
    private const val GLYPH_FIRE = 13
    private const val GLYPH_LIGATURE = 20
    private const val GLYPH_VARIANT = 21

    private const val ZWJ = 0x200D
    private const val HEART = 0x2764
    private const val SELECTOR = 0xFE0F
    private const val FIRE = 0x1F525

    fun build(
        variationSequences: Boolean = false,
        ligature: Boolean = true,
        extension: Boolean = false,
        ligatureComponents: IntArray = intArrayOf(ZWJ, FIRE),
    ): ByteArray {
        val cmap = cmap(variationSequences)
        val gsub = if (ligature) gsub(ligatureComponents, extension) else null
        val tables = buildList {
            if (gsub != null) add(0x47535542 to gsub) // 'GSUB' sorts before 'cmap'
            add(0x636D6170 to cmap)
        }
        val header = 12 + tables.size * 16
        val out = Writer()
        out.u32(0x00010000)
        out.u16(tables.size)
        out.u16(0)
        out.u16(0)
        out.u16(0)
        var offset = header
        for ((tag, body) in tables) {
            out.u32(tag.toLong())
            out.u32(0)
            out.u32(offset.toLong())
            out.u32(body.size.toLong())
            offset += body.size
        }
        for ((_, body) in tables) out.bytes(body)
        return out.toByteArray()
    }

    private fun cmap(variationSequences: Boolean): ByteArray {
        val format4 = format4()
        val format12 = format12()
        val format14 = if (variationSequences) format14() else null
        val subtables = listOfNotNull(
            Triple(3, 1, format4),
            Triple(3, 10, format12),
            format14?.let { Triple(0, 5, it) },
        )
        val out = Writer()
        out.u16(0)
        out.u16(subtables.size)
        var offset = 4 + subtables.size * 8
        for ((platform, encoding, body) in subtables) {
            out.u16(platform)
            out.u16(encoding)
            out.u32(offset.toLong())
            offset += body.size
        }
        for ((_, _, body) in subtables) out.bytes(body)
        return out.toByteArray()
    }

    /** Segments must be sorted by end code, with the mandatory 0xFFFF last. */
    private fun format4(): ByteArray {
        val codes = intArrayOf(ZWJ, HEART, SELECTOR, 0xFFFF)
        val glyphs = intArrayOf(GLYPH_ZWJ, GLYPH_HEART, GLYPH_SELECTOR, 1)
        val out = Writer()
        out.u16(4)
        out.u16(16 + codes.size * 8)
        out.u16(0)
        out.u16(codes.size * 2)
        out.u16(0)
        out.u16(0)
        out.u16(0)
        for (code in codes) out.u16(code)
        out.u16(0) // reservedPad
        for (code in codes) out.u16(code)
        for (i in codes.indices) out.u16((glyphs[i] - codes[i]) and 0xFFFF)
        for (i in codes.indices) out.u16(0)
        return out.toByteArray()
    }

    private fun format12(): ByteArray {
        val out = Writer()
        out.u16(12)
        out.u16(0)
        out.u32(28)
        out.u32(0)
        out.u32(1)
        out.u32(FIRE.toLong())
        out.u32(FIRE.toLong())
        out.u32(GLYPH_FIRE.toLong())
        return out.toByteArray()
    }

    private fun format14(): ByteArray {
        val out = Writer()
        out.u16(14)
        out.u32(30)
        out.u32(1)
        out.u24(SELECTOR)
        out.u32(0) // no default UVS ranges
        out.u32(21) // non-default UVS mappings follow the record
        out.u32(1)
        out.u24(HEART)
        out.u16(GLYPH_VARIANT)
        return out.toByteArray()
    }

    private fun glyphOf(codePoint: Int): Int = when (codePoint) {
        ZWJ -> GLYPH_ZWJ
        HEART -> GLYPH_HEART
        SELECTOR -> GLYPH_SELECTOR
        FIRE -> GLYPH_FIRE
        else -> error("no glyph for ${codePoint.toString(16)}")
    }

    /**
     * A lookup list of one, holding a `LigatureSubst` whose first glyph is the
     * heart. Wrapped in an extension subtable when [extension] is set, which is
     * how a large font reaches lookups past the 64 KB an Offset16 can address.
     */
    private fun gsub(components: IntArray, extension: Boolean): ByteArray {
        val subtable = ligatureSubst(components)
        val out = Writer()
        out.u16(1) // major
        out.u16(0) // minor
        out.u16(10) // scriptList
        out.u16(12) // featureList
        out.u16(14) // lookupList
        out.u16(0) // scriptCount
        out.u16(0) // featureCount
        out.u16(1) // lookupCount
        out.u16(4) // lookup at 14 + 4 = 18
        out.u16(if (extension) 7 else 4)
        out.u16(0) // flags
        out.u16(1) // subtable count
        out.u16(8) // subtable at 18 + 8 = 26
        if (extension) {
            out.u16(1) // extension format
            out.u16(4) // the real lookup type
            out.u32(8) // the real subtable at 26 + 8 = 34
        }
        out.bytes(subtable)
        return out.toByteArray()
    }

    private fun ligatureSubst(components: IntArray): ByteArray {
        // Laid out as: header, LigatureSet, Ligature, Coverage.
        val ligatureOffset = 4
        val setOffset = 8
        val ligature = Writer().apply {
            u16(GLYPH_LIGATURE)
            u16(components.size + 1)
            for (component in components) u16(glyphOf(component))
        }.toByteArray()
        val coverageOffset = setOffset + 2 + 2 + ligature.size
        val out = Writer()
        out.u16(1) // substFormat
        out.u16(coverageOffset)
        out.u16(1) // ligatureSetCount
        out.u16(setOffset)
        out.u16(1) // ligatureCount
        out.u16(ligatureOffset)
        out.bytes(ligature)
        out.u16(1) // coverage format
        out.u16(1) // glyph count
        out.u16(GLYPH_HEART)
        return out.toByteArray()
    }
}

/** Big-endian byte sink; the font formats are all big-endian. */
private class Writer {
    private val bytes = ArrayList<Byte>(256)

    fun u16(value: Int) {
        bytes.add(((value shr 8) and 0xFF).toByte())
        bytes.add((value and 0xFF).toByte())
    }

    fun u24(value: Int) {
        bytes.add(((value shr 16) and 0xFF).toByte())
        bytes.add(((value shr 8) and 0xFF).toByte())
        bytes.add((value and 0xFF).toByte())
    }

    fun u32(value: Long) {
        for (shift in intArrayOf(24, 16, 8, 0)) {
            bytes.add(((value shr shift) and 0xFF).toByte())
        }
    }

    fun bytes(value: ByteArray) {
        for (b in value) bytes.add(b)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}
