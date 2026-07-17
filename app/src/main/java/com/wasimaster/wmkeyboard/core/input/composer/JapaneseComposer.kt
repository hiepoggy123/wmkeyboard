package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Japanese input: romaji keystrokes compose to hiragana ([composeBuffer]), and
 * the hiragana reading offers kanji/word candidates in the strip
 * ([isConversion] + [candidates]), chosen from the shipped kana→kanji table.
 * With no dictionary match the reading still commits as kana, so it works as a
 * plain kana keyboard on its own.
 */
object JapaneseComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    override fun composeBuffer(buffer: String): String = Kana.toHiragana(buffer)

    override fun candidates(buffer: String): List<String> {
        val kana = Kana.toHiragana(buffer)
        if (kana.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out.addAll(CjkDictionaries.japanese.candidates(kana))
        out.add(kana)                       // keep the plain hiragana as a choice
        Kana.toKatakana(kana).let { if (it != kana) out.add(it) }
        return out.toList()
    }
}

/** Romaji↔kana transliteration (Hepburn/wāpuro), longest-match. */
internal object Kana {

    fun toHiragana(romaji: String): String {
        val s = romaji.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // Sokuon: a doubled consonant (kk, tt, ss, …, but not n or a vowel)
            // becomes っ before the next syllable.
            if (i + 1 < s.length && c == s[i + 1] && c !in "aeioun" && c in 'a'..'z') {
                out.append('っ'); i++; continue
            }
            // Syllabic ん. `n'` is an explicit ん. For `nn`: if a vowel/y follows
            // the second n it starts a な-row syllable (`onna`→おんな, `konnichi`→
            // こんにち), so only the first n is consumed here; otherwise both n's
            // fold to a single ん (`onn`→おん). A lone n before a consonant or the
            // word end is also ん; before a vowel/y it falls through to the table.
            if (c == 'n') {
                if (i + 1 < s.length && s[i + 1] == '\'') { out.append('ん'); i += 2; continue }
                if (i + 1 < s.length && s[i + 1] == 'n') {
                    val after = s.getOrNull(i + 2)
                    if (after != null && after in "aeiouy") { out.append('ん'); i++; continue }
                    out.append('ん'); i += 2; continue
                }
                val nxt = s.getOrNull(i + 1)
                if (nxt == null || nxt !in "aeiouy") { out.append('ん'); i++; continue }
            }
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len <= s.length) {
                    TABLE[s.substring(i, i + len)]?.let {
                        out.append(it); i += len; matched = true
                    }
                    if (matched) break
                }
            }
            if (!matched) { out.append(s[i]); i++ }
        }
        return out.toString()
    }

    /** Hiragana → katakana (the two blocks differ by a fixed 0x60 offset). */
    fun toKatakana(hiragana: String): String = buildString {
        for (c in hiragana) {
            if (c.code in 0x3041..0x3096) append((c.code + 0x60).toChar()) else append(c)
        }
    }

    private val TABLE: Map<String, String> = buildMap {
        put("a", "あ"); put("i", "い"); put("u", "う"); put("e", "え"); put("o", "お")
        put("ka", "か"); put("ki", "き"); put("ku", "く"); put("ke", "け"); put("ko", "こ")
        put("ga", "が"); put("gi", "ぎ"); put("gu", "ぐ"); put("ge", "げ"); put("go", "ご")
        put("sa", "さ"); put("shi", "し"); put("si", "し"); put("su", "す"); put("se", "せ"); put("so", "そ")
        put("za", "ざ"); put("ji", "じ"); put("zi", "じ"); put("zu", "ず"); put("ze", "ぜ"); put("zo", "ぞ")
        put("ta", "た"); put("chi", "ち"); put("ti", "ち"); put("tsu", "つ"); put("tu", "つ"); put("te", "て"); put("to", "と")
        put("da", "だ"); put("di", "ぢ"); put("du", "づ"); put("de", "で"); put("do", "ど")
        put("na", "な"); put("ni", "に"); put("nu", "ぬ"); put("ne", "ね"); put("no", "の")
        put("ha", "は"); put("hi", "ひ"); put("fu", "ふ"); put("hu", "ふ"); put("he", "へ"); put("ho", "ほ")
        put("ba", "ば"); put("bi", "び"); put("bu", "ぶ"); put("be", "べ"); put("bo", "ぼ")
        put("pa", "ぱ"); put("pi", "ぴ"); put("pu", "ぷ"); put("pe", "ぺ"); put("po", "ぽ")
        put("ma", "ま"); put("mi", "み"); put("mu", "む"); put("me", "め"); put("mo", "も")
        put("ya", "や"); put("yu", "ゆ"); put("yo", "よ")
        put("ra", "ら"); put("ri", "り"); put("ru", "る"); put("re", "れ"); put("ro", "ろ")
        put("wa", "わ"); put("wo", "を"); put("wi", "うぃ"); put("we", "うぇ")
        put("vu", "ゔ")
        // Yōon (palatalised)
        put("kya", "きゃ"); put("kyu", "きゅ"); put("kyo", "きょ")
        put("gya", "ぎゃ"); put("gyu", "ぎゅ"); put("gyo", "ぎょ")
        put("sha", "しゃ"); put("shu", "しゅ"); put("sho", "しょ")
        put("sya", "しゃ"); put("syu", "しゅ"); put("syo", "しょ")
        put("ja", "じゃ"); put("ju", "じゅ"); put("jo", "じょ")
        put("jya", "じゃ"); put("jyu", "じゅ"); put("jyo", "じょ")
        put("zya", "じゃ"); put("zyu", "じゅ"); put("zyo", "じょ")
        put("cha", "ちゃ"); put("chu", "ちゅ"); put("cho", "ちょ")
        put("tya", "ちゃ"); put("tyu", "ちゅ"); put("tyo", "ちょ")
        put("nya", "にゃ"); put("nyu", "にゅ"); put("nyo", "にょ")
        put("hya", "ひゃ"); put("hyu", "ひゅ"); put("hyo", "ひょ")
        put("bya", "びゃ"); put("byu", "びゅ"); put("byo", "びょ")
        put("pya", "ぴゃ"); put("pyu", "ぴゅ"); put("pyo", "ぴょ")
        put("mya", "みゃ"); put("myu", "みゅ"); put("myo", "みょ")
        put("rya", "りゃ"); put("ryu", "りゅ"); put("ryo", "りょ")
        // Foreign-sound kana
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")
        put("tsa", "つぁ"); put("tsi", "つぃ"); put("tse", "つぇ"); put("tso", "つぉ")
        put("che", "ちぇ"); put("she", "しぇ"); put("je", "じぇ")
        // Small kana (l-/x- prefix)
        put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
        put("la", "ぁ"); put("li", "ぃ"); put("lu", "ぅ"); put("le", "ぇ"); put("lo", "ぉ")
        put("xtu", "っ"); put("ltu", "っ"); put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
        // Long vowel + punctuation the composer may see
        put("-", "ー")
    }
}
