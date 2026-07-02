package com.wasimaster.wmkeyboard.core.input.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Korean composition, tested by driving jamo sequences through the composer the
 * way the keyboard feeds them and asserting the syllable blocks that result.
 */
class HangulComposerTest {

    private fun compose(jamos: String) = HangulComposer.composeBuffer(jamos)

    @Test fun `it is a transliterating input method`() {
        assertTrue(HangulComposer.isTransliterating)
    }

    @Test fun `a simple initial-medial-final forms one syllable`() {
        assertEquals("한", compose("ㅎㅏㄴ"))
        assertEquals("가", compose("ㄱㅏ"))
        assertEquals("강", compose("ㄱㅏㅇ"))
    }

    @Test fun `a trailing consonant starts a new syllable when no vowel follows`() {
        // 한 + 글: the ㄱ has no vowel yet, so 한 flushes and ㄱ begins the next.
        assertEquals("한글", compose("ㅎㅏㄴㄱㅡㄹ"))
    }

    @Test fun `a final becomes the next initial when a vowel follows`() {
        // 간 + ㅏ: the final ㄴ moves to a fresh syllable → 가나.
        assertEquals("가나", compose("ㄱㅏㄴㅏ"))
    }

    @Test fun `compound medials combine`() {
        assertEquals("과", compose("ㄱㅗㅏ")) // ㅗ + ㅏ → ㅘ
        assertEquals("귀", compose("ㄱㅜㅣ")) // ㅜ + ㅣ → ㅟ
    }

    @Test fun `compound finals combine and re-split before a vowel`() {
        assertEquals("갉", compose("ㄱㅏㄹㄱ")) // ㄹ + ㄱ → ㄺ
        // 갉 + ㅣ: ㄺ splits, ㄹ stays as 갈's final, ㄱ starts 기 → 갈기.
        assertEquals("갈기", compose("ㄱㅏㄹㄱㅣ"))
    }

    @Test fun `non-jamo characters pass through and flush the syllable`() {
        assertEquals("가 나", compose("ㄱㅏ ㄴㅏ"))
        assertEquals("한.", compose("ㅎㅏㄴ."))
    }

    @Test fun `a lone leading vowel is emitted as-is`() {
        assertEquals("ㅏ", compose("ㅏ"))
    }

    @Test fun `every syllable produced is in the Hangul block`() {
        for (ch in compose("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ")) {
            if (ch.isWhitespace()) continue
            assertTrue("'$ch' should be a Hangul syllable", ch.code in 0xAC00..0xD7A3)
        }
    }
}
