package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM Unit tests for Vietnamese Telex and VNI transliteration engine.
 * Fast local test execution without requiring ADB or Android devices.
 */
class VietnameseComposerTest {

    private val latinScript = ScriptRegistry[ScriptId.LATIN]

    @Test
    fun factoryMapsTelexAndVniComposers() {
        val telex = composerFor(latinScript, ComposerType.TELEX)
        val vni = composerFor(latinScript, ComposerType.VNI)

        assertNotNull(telex)
        assertTrue(telex is VietnameseTelexComposer)
        assertTrue(telex.isTransliterating)

        assertNotNull(vni)
        assertTrue(vni is VietnameseVniComposer)
        assertTrue(vni.isTransliterating)
        assertTrue(vni.bufferDigits)
    }

    // --- Telex Tests ---

    @Test
    fun telexBasicTones() {
        val c = VietnameseTelexComposer
        assertEquals("á", c.composeBuffer("as"))
        assertEquals("à", c.composeBuffer("af"))
        assertEquals("ả", c.composeBuffer("ar"))
        assertEquals("ã", c.composeBuffer("ax"))
        assertEquals("ạ", c.composeBuffer("aj"))
    }

    @Test
    fun telexLetterMarks() {
        val c = VietnameseTelexComposer
        assertEquals("â", c.composeBuffer("aa"))
        assertEquals("ă", c.composeBuffer("aw"))
        assertEquals("ê", c.composeBuffer("ee"))
        assertEquals("ô", c.composeBuffer("oo"))
        assertEquals("ơ", c.composeBuffer("ow"))
        assertEquals("ư", c.composeBuffer("uw"))
        assertEquals("w", c.composeBuffer("w"))
        assertEquals("ww", c.composeBuffer("ww"))
        assertEquals("đ", c.composeBuffer("dd"))
    }

    @Test
    fun telexFullSyllables() {
        val c = VietnameseTelexComposer
        assertEquals("việt", c.composeBuffer("vieejt"))
        assertEquals("tiếng", c.composeBuffer("tieengs"))
        assertEquals("đây", c.composeBuffer("ddaay"))
        assertEquals("nước", c.composeBuffer("nuocsw"))
        assertEquals("quả", c.composeBuffer("quar"))
    }

    @Test
    fun telexToneAndMarkCancellation() {
        val c = VietnameseTelexComposer
        assertEquals("as", c.composeBuffer("ass"))
        assertEquals("af", c.composeBuffer("aff"))
        assertEquals("dd", c.composeBuffer("ddd"))
        assertEquals("aa", c.composeBuffer("aaa"))
        assertEquals("ee", c.composeBuffer("eee"))
        assertEquals("oo", c.composeBuffer("ooo"))
    }

    @Test
    fun telexFreeFormAndWKey() {
        val c = VietnameseTelexComposer
        // w cancellation for English words
        assertEquals("row", c.composeBuffer("roww"))
        assertEquals("draw", c.composeBuffer("draww"))
        assertEquals("show", c.composeBuffer("showw"))
        assertEquals("flow", c.composeBuffer("floww"))

        // Free-form Telex (Unikey / EVKey / OpenKey style)
        assertEquals("đông", c.composeBuffer("dodong"))
        assertEquals("đông", c.composeBuffer("dongod"))
        assertEquals("việt", c.composeBuffer("vietej"))
        assertEquals("dương", c.composeBuffer("duongw"))
        assertEquals("đa", c.composeBuffer("dda"))
        assertEquals("đa", c.composeBuffer("dad"))
        assertEquals("đâu", c.composeBuffer("daud"))
        assertEquals("đâ", c.composeBuffer("dada"))

        // OpenKey features: 'z' key clears tone mark
        assertEquals("toan", c.composeBuffer("toansz"))
        assertEquals("toan", c.composeBuffer("toanz"))

        // OpenKey features: 3-vowel clusters (tone on middle vowel)
        assertEquals("xoài", c.composeBuffer("xoaif"))
        assertEquals("khuỷu", c.composeBuffer("khuyur"))
        assertEquals("ngoèo", c.composeBuffer("ngoeof"))
    }

    @Test
    fun telexCapitalization() {
        val c = VietnameseTelexComposer
        assertEquals("Việt", c.composeBuffer("Vieejt"))
        assertEquals("Tiếng", c.composeBuffer("Tieengs"))
        assertEquals("ĐÂY", c.composeBuffer("DDAAY"))
    }

    // --- VNI Tests ---

    @Test
    fun vniBasicTones() {
        val c = VietnameseVniComposer
        assertEquals("á", c.composeBuffer("a1"))
        assertEquals("à", c.composeBuffer("a2"))
        assertEquals("ả", c.composeBuffer("a3"))
        assertEquals("ã", c.composeBuffer("a4"))
        assertEquals("ạ", c.composeBuffer("a5"))
        assertEquals("a", c.composeBuffer("a10")) // 0 clears tone
    }

    @Test
    fun vniLetterMarks() {
        val c = VietnameseVniComposer
        assertEquals("â", c.composeBuffer("a6"))
        assertEquals("ơ", c.composeBuffer("o7"))
        assertEquals("ư", c.composeBuffer("u7"))
        assertEquals("ă", c.composeBuffer("a8"))
        assertEquals("đ", c.composeBuffer("d9"))
    }

    @Test
    fun vniFullSyllables() {
        val c = VietnameseVniComposer
        assertEquals("việt", c.composeBuffer("viet65"))
        assertEquals("tiếng", c.composeBuffer("tieng61"))
        assertEquals("đây", c.composeBuffer("d9ay6"))
    }

    @Test
    fun vniMixedWords() {
        val c = VietnameseVniComposer
        assertEquals("Việt", c.composeBuffer("Vie65t"))
        assertEquals("Đường", c.composeBuffer("D9u7o7ng2"))
    }

    @Test
    fun testDirectToneMarks() {
        val c = VietnameseTelexComposer
        assertEquals("chào", c.composeBuffer("chao\u0300"))
        assertEquals("chào", c.composeBuffer("chaò"))
        assertEquals("cháo", c.composeBuffer("chao\u0301"))
        assertEquals("chảo", c.composeBuffer("chao\u0309"))
        assertEquals("chão", c.composeBuffer("chao\u0303"))
        assertEquals("chạo", c.composeBuffer("chao\u0323"))
    }

    @Test
    fun vniCapitalization() {
        val c = VietnameseVniComposer
        assertEquals("Việt", c.composeBuffer("Viet65"))
        assertEquals("Tiếng", c.composeBuffer("Tieng61"))
    }
}
