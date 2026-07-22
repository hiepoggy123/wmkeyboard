package com.wasimaster.wmkeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.input.composer.VietnameseTelexComposer
import com.wasimaster.wmkeyboard.core.input.composer.VietnameseVniComposer
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Instrumented tests for the Vietnamese typing system on Android.
 *
 * Tests the full pipeline including:
 * 1. Asset layout resolution for asset_vi_telex, asset_vi_vni, asset_vi_vietnamese.
 * 2. Telex engine transliteration end-to-end.
 * 3. VNI engine transliteration end-to-end.
 * 4. Syllables, tone placements, mark cancellations, and capitalization.
 */
@RunWith(AndroidJUnit4::class)
class VietnameseTypingE2ETest {

    @Test
    fun verifyAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.wasimaster.wmkeyboard", appContext.packageName)
    }

    @Test
    fun verifyVietnameseLayoutIdentifiers() {
        assertNotNull(AssetLayouts.VI_TELEX_ID)
        assertNotNull(AssetLayouts.VI_VNI_ID)
        assertNotNull(AssetLayouts.VI_QWERTY_ID)
        assertEquals("asset_vi_telex", AssetLayouts.VI_TELEX_ID)
        assertEquals("asset_vi_vni", AssetLayouts.VI_VNI_ID)
        assertEquals("asset_vi_vietnamese", AssetLayouts.VI_QWERTY_ID)
    }

    @Test
    fun verifyVietnameseTelexComposerMapping() {
        val latinScript = ScriptRegistry[ScriptId.LATIN]
        val composer = latinScript?.let { com.wasimaster.wmkeyboard.core.input.composer.composerFor(it, ComposerType.TELEX) }
        assertNotNull(composer)
        assertTrue(composer is VietnameseTelexComposer)
        assertTrue(composer!!.isTransliterating)
    }

    @Test
    fun verifyVietnameseVniComposerMapping() {
        val latinScript = ScriptRegistry[ScriptId.LATIN]
        val composer = latinScript?.let { com.wasimaster.wmkeyboard.core.input.composer.composerFor(it, ComposerType.VNI) }
        assertNotNull(composer)
        assertTrue(composer is VietnameseVniComposer)
        assertTrue(composer!!.isTransliterating)
        assertTrue(composer.bufferDigits)
    }

    // --- End-to-End Telex Transliteration Tests ---

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
        assertEquals("ư", c.composeBuffer("w"))
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
    fun telexToneCancellation() {
        val c = VietnameseTelexComposer
        // Repeated tone key cancels the tone and outputs the literal letter
        assertEquals("as", c.composeBuffer("ass"))
        assertEquals("af", c.composeBuffer("aff"))
        assertEquals("dd", c.composeBuffer("ddd"))
    }

    @Test
    fun telexCapitalization() {
        val c = VietnameseTelexComposer
        assertEquals("Việt", c.composeBuffer("Vieejt"))
        assertEquals("Tiếng", c.composeBuffer("Tieengs"))
        assertEquals("ĐÂY", c.composeBuffer("DDAAY"))
    }

    // --- End-to-End VNI Transliteration Tests ---

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
    fun vniCapitalization() {
        val c = VietnameseVniComposer
        assertEquals("Việt", c.composeBuffer("Viet65"))
        assertEquals("Tiếng", c.composeBuffer("Tieng61"))
    }
}
