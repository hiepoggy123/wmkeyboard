package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vietnamese Telex/VNI, Japanese romaji→kana, and the composer factory wiring. */
class CjkComposerTest {

    private val latin = ScriptRegistry[ScriptId.LATIN]
    private val japanese = ScriptRegistry[ScriptId.JAPANESE]
    private val han = ScriptRegistry[ScriptId.HAN]

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
