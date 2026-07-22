package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Vietnamese Telex/VNI, Japanese romaji→kana, and the composer factory wiring. */
class CjkComposerTest {

    private val latin = ScriptRegistry[ScriptId.LATIN]
    private val japanese = ScriptRegistry[ScriptId.JAPANESE]
    private val han = ScriptRegistry[ScriptId.HAN]

    // The CJK conversion tables and syllable inventory are process globals; tests
    // that load them must not leak into the many tests that assume them empty.
    @Before
    @After
    fun resetCjkGlobals() {
        CjkDictionaries.pinyin = ConversionDictionary.EMPTY
        CjkDictionaries.japanese = ConversionDictionary.EMPTY
        PinyinSyllables.valid = emptySet()
    }

    @Test
    fun `factory maps the new composer types`() {
        assertSame(VietnameseTelexComposer, composerFor(latin, ComposerType.TELEX))
        assertSame(VietnameseVniComposer, composerFor(latin, ComposerType.VNI))
        assertSame(JapaneseComposer, composerFor(japanese, ComposerType.ROMAJI))
        assertSame(PinyinComposer, composerFor(han, ComposerType.PINYIN))
    }

    @Test
    fun `telex applies tones and letter marks`() {
        val c = VietnameseTelexComposer
        assertEquals("á", c.composeBuffer("as"))
        assertEquals("à", c.composeBuffer("af"))
        assertEquals("ả", c.composeBuffer("ar"))
        assertEquals("ã", c.composeBuffer("ax"))
        assertEquals("ạ", c.composeBuffer("aj"))
        assertEquals("â", c.composeBuffer("aa"))
        assertEquals("ă", c.composeBuffer("aw"))
        assertEquals("đ", c.composeBuffer("dd"))
        assertEquals("ơ", c.composeBuffer("ow"))
        assertEquals("ư", c.composeBuffer("uw"))
    }

    @Test
    fun `telex composes whole syllables with the tone on the right vowel`() {
        val c = VietnameseTelexComposer
        assertEquals("việt", c.composeBuffer("vieejt"))
        assertEquals("tiếng", c.composeBuffer("tieengs"))
        assertEquals("đây", c.composeBuffer("ddaay"))
        // A repeated tone key cancels the tone and types the letter.
        assertEquals("as", c.composeBuffer("ass"))
    }

    @Test
    fun `vni uses digits for tones and marks`() {
        val c = VietnameseVniComposer
        assertEquals("á", c.composeBuffer("a1"))
        assertEquals("à", c.composeBuffer("a2"))
        assertEquals("â", c.composeBuffer("a6"))
        assertEquals("ă", c.composeBuffer("a8"))
        assertEquals("ô", c.composeBuffer("o6"))
        assertEquals("ơ", c.composeBuffer("o7"))
        assertEquals("ư", c.composeBuffer("u7"))
        assertEquals("đ", c.composeBuffer("d9"))
        assertEquals("việt", c.composeBuffer("viet65"))
    }

    @Test
    fun `vni consumes digit keys into the buffer`() {
        assertTrue(VietnameseVniComposer.bufferDigits)
    }

    @Test
    fun `romaji composes hiragana with sokuon and syllabic n`() {
        val c = JapaneseComposer
        assertEquals("こんにちは", c.composeBuffer("konnichiha"))
        assertEquals("ありがとう", c.composeBuffer("arigatou"))
        assertEquals("にほん", c.composeBuffer("nihon"))
        assertEquals("けっこん", c.composeBuffer("kekkon"))
        assertEquals("がっこう", c.composeBuffer("gakkou"))
        assertEquals("しんぶん", c.composeBuffer("shinbun"))
        assertEquals("おんな", c.composeBuffer("onna"))
        assertEquals("きょう", c.composeBuffer("kyou"))
        assertEquals("し", c.composeBuffer("shi"))
        assertEquals("つ", c.composeBuffer("tsu"))
    }

    @Test
    fun `japanese offers the kana and katakana as candidates when no dictionary is loaded`() {
        // In a plain JVM test the conversion tables are empty, so candidates are
        // just the reading itself in both kana.
        val cands = JapaneseComposer.candidates("nihon")
        assertTrue("にほん" in cands)
        assertTrue("ニホン" in cands)
    }

    @Test
    fun `pinyin shows the raw reading and is a conversion ime`() {
        assertTrue(PinyinComposer.isConversion)
        assertEquals("nihao", PinyinComposer.composeBuffer("nihao"))
        // No dictionary loaded → no character candidates, but it never crashes.
        assertEquals(emptyList<String>(), PinyinComposer.candidates("nihao"))
    }

    // --- Pinyin syllable segmentation (Phase 1) ---------------------------------
    // Dictionary "words" below are ASCII stand-ins (N1, NH, …): the segmenter and
    // consumed-length math are script-agnostic, so the tests need no real Hanzi.

    private val fixtureSyllables = setOf("ni", "hao", "xi", "an", "wo")

    @Test
    fun `segmenter splits a multi-syllable buffer greedily`() {
        val segs = PinyinSyllables.segment("nihao", fixtureSyllables)
        assertEquals(listOf("ni", "hao"), segs.map { it.syllable })
        assertEquals(listOf(2, 3), segs.map { it.inputLen })
    }

    @Test
    fun `segmenter folds an apostrophe boundary into the next syllable span`() {
        val segs = PinyinSyllables.segment("xi'an", fixtureSyllables)
        assertEquals(listOf("xi", "an"), segs.map { it.syllable })
        // The apostrophe counts toward "an"'s input length so a prefix commit
        // deletes it along with the syllable.
        assertEquals(listOf(2, 3), segs.map { it.inputLen })
    }

    @Test
    fun `segmenter stops at the first uncovered position`() {
        // "ni" is valid, "zz" is not: the trailing junk is left unconsumed.
        assertEquals(listOf("ni"), PinyinSyllables.segment("nizz", fixtureSyllables).map { it.syllable })
        assertEquals(emptyList<String>(), PinyinSyllables.segment("zz", fixtureSyllables).map { it.syllable })
    }

    @Test
    fun `pinyin ranks whole-phrase above leading syllable and reports consumed length`() {
        PinyinSyllables.valid = fixtureSyllables
        CjkDictionaries.pinyin = ConversionDictionary.parse(
            sequenceOf(
                "ni\tN1\t100",
                "hao\tH1\t100",
                "nihao\tNH\t50",
            ),
        )
        // Whole-phrase NH (from nihao) outranks the single-syllable N1 (from ni).
        assertEquals(listOf("NH", "N1"), PinyinComposer.candidates("nihao"))
        // NH consumed the whole 5-char buffer; N1 consumed only the "ni" prefix.
        assertEquals(5, PinyinComposer.consumedFor("nihao", "NH"))
        assertEquals(2, PinyinComposer.consumedFor("nihao", "N1"))
        // An unknown candidate falls back to consuming the whole buffer.
        assertEquals(5, PinyinComposer.consumedFor("nihao", "??"))
    }

    @Test
    fun `pinyin without a loaded inventory falls back to whole-buffer lookup`() {
        // No syllable inventory → no segmentation → dictionary prefix matching,
        // each candidate consuming the whole buffer (old behaviour, no regression).
        CjkDictionaries.pinyin = ConversionDictionary.parse(sequenceOf("ni\tN1\t100"))
        assertEquals(listOf("N1"), PinyinComposer.candidates("ni"))
        assertEquals(2, PinyinComposer.consumedFor("ni", "N1"))
    }

    @Test
    fun `conversion dictionary parses tsv and ranks by frequency`() {
        val dict = ConversionDictionary.parse(
            sequenceOf(
                "ni\t你\t100",
                "ni\t尼\t10",
                "hao\t好\t100",
                "# comment",
                "nihao\t你好\t50",
            ),
        )
        assertEquals(listOf("你", "尼"), dict.exact("ni"))
        assertEquals(listOf("好"), dict.exact("hao"))
        assertTrue("你好" in dict.candidates("nihao"))
    }
}
