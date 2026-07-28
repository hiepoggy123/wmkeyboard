package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the data repo's JSON emoji dictionaries. */
class EmojiDictCodecTest {

    private val sample = """
        [
          {"emoji": "🎂", "name": "জন্মদিনের কেক",
           "keywords": ["কেক", "জন্মদিন", "মিষ্টি"], "category": "Food & Drink"},
          {"emoji": "💰", "name": "টাকার ব্যাগ",
           "keywords": ["টাকা"], "category": "Objects"}
        ]
    """.trimIndent()

    @Test fun decodesEmojiKeywordsAndNames() {
        val pack = EmojiDictCodec.decode(sample)!!
        assertEquals(2, pack.size)
        assertEquals(listOf("কেক", "জন্মদিন", "মিষ্টি"), pack.keywords["🎂"])
        assertEquals("জন্মদিনের কেক", pack.names["🎂"])
        assertEquals("টাকার ব্যাগ", pack.names["💰"])
    }

    /** The repo carries a `category` the app has its own opinion about. */
    @Test fun unknownAndMissingFieldsAreTolerated() {
        val pack = EmojiDictCodec.decode(
            """[{"emoji":"🎂","keywords":["cake"],"future":"whatever"}]""",
        )!!
        assertEquals(listOf("cake"), pack.keywords["🎂"])
        assertNull(pack.names["🎂"])
    }

    @Test fun keywordsAreLowercasedAndDeduplicated() {
        val pack = EmojiDictCodec.decode(
            """[{"emoji":"🎂","name":"Cake","keywords":["Cake"," cake ","BIRTHDAY"]}]""",
        )!!
        assertEquals(listOf("cake", "birthday"), pack.keywords["🎂"])
        // The name keeps its case; only keywords are normalised for search.
        assertEquals("Cake", pack.names["🎂"])
    }

    @Test fun rowsWithoutAnEmojiAreSkipped() {
        val pack = EmojiDictCodec.decode("""[{"emoji":"","keywords":["x"]},{"emoji":" "}]""")!!
        assertTrue(pack.isEmpty)
    }

    @Test fun nonJsonIsRejectedRatherThanThrown() {
        assertNull(EmojiDictCodec.decode("not json at all"))
        assertNull(EmojiDictCodec.decode("""{"emoji":"🎂"}"""))
        assertNull(EmojiDictCodec.decode("[unterminated"))
    }

    @Test fun emptyArrayDecodesToAnEmptyPack() {
        assertTrue(EmojiDictCodec.decode("[]")!!.isEmpty)
    }

    /** What the downloader writes must be what the loader reads back. */
    @Test fun tsvRoundTripsThroughTheImporter() {
        val pack = EmojiDictCodec.decode(sample)!!
        val reread = EmojiKeywordPack.load(EmojiDictCodec.encodeTsv(pack).byteInputStream())
        assertEquals(pack.size, reread.size)
        assertEquals(pack.keywords["🎂"], reread.keywords["🎂"])
        assertEquals(pack.names["🎂"], reread.names["🎂"])
    }

    /** A tab or newline inside a value would otherwise split the row in two. */
    @Test fun separatorsInsideValuesCannotBreakTheRow() {
        val pack = EmojiDictCodec.decode(
            "[{\"emoji\":\"🎂\",\"name\":\"two\\tparts\",\"keywords\":[\"a\\nb\",\"c,d\"]}]",
        )!!
        val tsv = EmojiDictCodec.encodeTsv(pack)
        assertEquals(2, tsv.trim().lines().size) // the header, and one row
        val reread = EmojiKeywordPack.load(tsv.byteInputStream())
        assertEquals("two parts", reread.names["🎂"])
        assertEquals(listOf("a b", "c d"), reread.keywords["🎂"])
    }

    /** A user may paste the repo's JSON URL straight into the importer. */
    @Test fun theImporterSniffsJson() {
        assertEquals(2, EmojiKeywordPack.load(sample.byteInputStream()).size)
        assertEquals(2, EmojiKeywordPack.load("\n\n  $sample".byteInputStream()).size)
        // ...and still reads TSV, which does not start with a bracket.
        assertEquals(1, EmojiKeywordPack.load("🎂\tcake\n".byteInputStream()).size)
    }
}
