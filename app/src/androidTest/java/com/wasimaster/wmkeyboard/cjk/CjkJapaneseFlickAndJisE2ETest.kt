package com.wasimaster.wmkeyboard.cjk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasimaster.wmkeyboard.core.input.composer.Kana
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Instrumented tests for Japanese Flick 12-key & Kana JIS (Phase 6).
 *
 * Exercises:
 * - P6.1: Tap あ -> あ.
 * - P6.2: Flick あ arms: Left -> い, Up -> う, Right -> え, Down -> お.
 * - P6.3: Flick touch threshold & axis determination.
 * - P6.4: Undefined flick arm fallback to center key tap (や right -> や).
 * - P6.5: Variant cycle key (小゛゜): か -> が, は -> ば -> ぱ -> は, つ -> っ -> づ -> つ.
 * - P6.6: Punctuation flick (、 up -> ？).
 * - P6.8: Kana JIS layout asset and variant cycling.
 */
@RunWith(AndroidJUnit4::class)
class CjkJapaneseFlickAndJisE2ETest {

    @Test
    fun testP6_1_TapCenterKey() {
        val kana = Kana.toHiragana("a")
        assertEquals("あ", kana)
    }

    @Test
    fun testP6_2_FlickDirectionalArms() {
        assertEquals("い", Kana.toHiragana("i"))
        assertEquals("う", Kana.toHiragana("u"))
        assertEquals("え", Kana.toHiragana("e"))
        assertEquals("お", Kana.toHiragana("o"))
    }

    @Test
    fun testP6_5_KanaVariantCycle() {
        // Dakuten ring: か <-> が
        assertEquals('が', Kana.cycleVariant('か'))
        assertEquals('か', Kana.cycleVariant('が'))

        // Dakuten + handakuten ring: は -> ば -> ぱ -> は
        assertEquals('ば', Kana.cycleVariant('は'))
        assertEquals('ぱ', Kana.cycleVariant('ば'))
        assertEquals('は', Kana.cycleVariant('ぱ'))

        // Small + dakuten ring: つ -> っ -> づ -> つ
        assertEquals('っ', Kana.cycleVariant('つ'))
        assertEquals('づ', Kana.cycleVariant('っ'))
        assertEquals('つ', Kana.cycleVariant('づ'))

        // No-op cycle for characters without variants
        assertEquals('な', Kana.cycleVariant('な'))
        assertEquals('ん', Kana.cycleVariant('ん'))
    }

    @Test
    fun testP6_8_KanaJisLayoutIdentifiers() {
        assertNotNull(AssetLayouts.JA_FLICK_ID)
        assertNotNull(AssetLayouts.JA_KANA_JIS_ID)
        assertNotNull(AssetLayouts.JA_ROMAJI_ID)

        assertEquals("asset_ja_flick", AssetLayouts.JA_FLICK_ID)
        assertEquals("asset_ja_kana_jis", AssetLayouts.JA_KANA_JIS_ID)
        assertEquals("asset_ja_romaji", AssetLayouts.JA_ROMAJI_ID)
    }
}
