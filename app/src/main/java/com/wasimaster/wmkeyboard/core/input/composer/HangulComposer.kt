package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Korean Hangul input: composes a run of compatibility jamo (the ㄱ/ㅏ letters a
 * Korean keyboard emits) into syllable blocks (한, 글). Like Avro it is a
 * transliterating composer — the keys go into a buffer and [composeBuffer]
 * renders the buffer to text, which the service shows as the composing region
 * and commits as a unit.
 *
 * A syllable is `0xAC00 + (L*21 + V)*28 + T`, L an initial consonant (19), V a
 * medial vowel (21), T a final consonant (28, 0 = none). The automaton adds jamo
 * left to right, handling compound medials (ㅗ+ㅏ→ㅘ), compound finals
 * (ㄱ+ㅅ→ㄳ) and the re-split where a final becomes the next syllable's initial
 * when a vowel follows (간+ㅏ → 가나, 갉+ㅣ → 갈기).
 */
object HangulComposer : Composer {

    override val isTransliterating: Boolean get() = true

    override fun composeBuffer(buffer: String): String = compose(buffer)

    // Compatibility jamo (U+3131…), in Unicode syllable order.
    private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ" // 19 initials
    private const val JUNGSEONG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ" // 21 medials
    // 28 finals; index 0 is "no final", written as a space placeholder.
    private const val JONGSEONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val COMPOUND_MEDIAL = mapOf(
        ('ㅗ' to 'ㅏ') to 'ㅘ', ('ㅗ' to 'ㅐ') to 'ㅙ', ('ㅗ' to 'ㅣ') to 'ㅚ',
        ('ㅜ' to 'ㅓ') to 'ㅝ', ('ㅜ' to 'ㅔ') to 'ㅞ', ('ㅜ' to 'ㅣ') to 'ㅟ',
        ('ㅡ' to 'ㅣ') to 'ㅢ',
    )

    private val COMPOUND_FINAL = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ',
        ('ㄹ' to 'ㄱ') to 'ㄺ', ('ㄹ' to 'ㅁ') to 'ㄻ', ('ㄹ' to 'ㅂ') to 'ㄼ',
        ('ㄹ' to 'ㅅ') to 'ㄽ', ('ㄹ' to 'ㅌ') to 'ㄾ', ('ㄹ' to 'ㅍ') to 'ㄿ',
        ('ㄹ' to 'ㅎ') to 'ㅀ', ('ㅂ' to 'ㅅ') to 'ㅄ',
    )

    /** A compound final splits into (kept final, consonant moved to next initial). */
    private val FINAL_SPLIT = mapOf(
        'ㄳ' to ('ㄱ' to 'ㅅ'), 'ㄵ' to ('ㄴ' to 'ㅈ'), 'ㄶ' to ('ㄴ' to 'ㅎ'),
        'ㄺ' to ('ㄹ' to 'ㄱ'), 'ㄻ' to ('ㄹ' to 'ㅁ'), 'ㄼ' to ('ㄹ' to 'ㅂ'),
        'ㄽ' to ('ㄹ' to 'ㅅ'), 'ㄾ' to ('ㄹ' to 'ㅌ'), 'ㄿ' to ('ㄹ' to 'ㅍ'),
        'ㅀ' to ('ㄹ' to 'ㅎ'), 'ㅄ' to ('ㅂ' to 'ㅅ'),
    )

    private fun lIndex(c: Char) = CHOSEONG.indexOf(c)
    private fun vIndex(c: Char) = JUNGSEONG.indexOf(c)
    private fun tIndex(c: Char) = JONGSEONG.indexOf(c) // 0 = none, >0 = a real final

    private fun syllable(l: Int, v: Int, t: Int): Char =
        (0xAC00 + (l * 21 + v) * 28 + t).toChar()

    private fun compose(jamos: String): String {
        val out = StringBuilder()
        var l = -1 // choseong index, -1 none
        var v = -1 // jungseong index, -1 none
        var t = 0 // jongseong index, 0 none

        fun flush() {
            when {
                l >= 0 && v >= 0 -> out.append(syllable(l, v, t))
                l >= 0 -> out.append(CHOSEONG[l])
                v >= 0 -> out.append(JUNGSEONG[v])
            }
            l = -1; v = -1; t = 0
        }

        for (c in jamos) {
            val ci = lIndex(c)
            val vi = vIndex(c)
            val ti = tIndex(c)
            when {
                vi >= 0 -> when { // a vowel
                    v < 0 -> v = vi // starts or follows an initial
                    t == 0 -> {
                        val cm = COMPOUND_MEDIAL[JUNGSEONG[v] to c]
                        if (cm != null) v = vIndex(cm) else { flush(); v = vi }
                    }
                    else -> { // final becomes the next syllable's initial
                        val split = FINAL_SPLIT[JONGSEONG[t]]
                        val moved: Char
                        if (split != null) { t = tIndex(split.first); moved = split.second }
                        else { moved = JONGSEONG[t]; t = 0 }
                        flush(); l = lIndex(moved); v = vi
                    }
                }
                ci >= 0 || ti > 0 -> when { // a consonant
                    l < 0 && v < 0 -> if (ci >= 0) l = ci else out.append(c)
                    v < 0 -> { flush(); if (ci >= 0) l = ci else out.append(c) }
                    t == 0 -> if (ti > 0) t = ti else { flush(); if (ci >= 0) l = ci else out.append(c) }
                    else -> {
                        val cf = COMPOUND_FINAL[JONGSEONG[t] to c]
                        if (cf != null) t = tIndex(cf)
                        else { flush(); if (ci >= 0) l = ci else out.append(c) }
                    }
                }
                else -> { flush(); out.append(c) } // not a jamo
            }
        }
        flush()
        return out.toString()
    }
}
