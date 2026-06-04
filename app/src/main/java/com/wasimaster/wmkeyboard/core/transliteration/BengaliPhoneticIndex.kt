package com.wasimaster.wmkeyboard.core.transliteration

/**
 * Reverse-phonetic lookup from romanized Bengali to dictionary words.
 *
 * Banglish spelling is loose — আছি gets typed as "asi", "achi" or "achhi".
 * Instead of trying to predict every spelling, both the romanized input and
 * each Bengali dictionary word are folded to a lenient canonical key where
 * confusable sounds collapse (স/শ/ষ/ছ/চ → s, ত/ট → t, ব/ভ → b, aspiration
 * dropped, inherent vowels dropped). Words sharing a key are phonetic
 * siblings; the highest-frequency sibling is offered as the primary
 * suggestion, which is what makes "ami valo asi" become আমি ভালো আছি.
 */
class BengaliPhoneticIndex(entries: List<Pair<String, Int>>) {

    private val byKey = HashMap<String, MutableList<Pair<String, Int>>>()

    init {
        for ((word, frequency) in entries) {
            val key = foldBengali(word)
            if (key.isEmpty()) continue
            byKey.getOrPut(key) { mutableListOf() }.add(word to frequency)
        }
        byKey.values.forEach { list -> list.sortByDescending { it.second } }
    }

    /** Dictionary words phonetically matching the romanized [input], best first. */
    fun lookup(input: String): List<String> =
        byKey[foldRoman(input)]?.map { it.first }.orEmpty()

    companion object {

        private val consonantClasses = mapOf(
            'ক' to 'k', 'খ' to 'k', 'গ' to 'g', 'ঘ' to 'g', 'ঙ' to 'q',
            'চ' to 's', 'ছ' to 's', 'জ' to 'j', 'ঝ' to 'j', 'ঞ' to 'q',
            'ট' to 't', 'ঠ' to 't', 'ড' to 'd', 'ঢ' to 'd', 'ণ' to 'n',
            'ত' to 't', 'থ' to 't', 'দ' to 'd', 'ধ' to 'd', 'ন' to 'n',
            'প' to 'p', 'ফ' to 'p', 'ব' to 'b', 'ভ' to 'b', 'ম' to 'm',
            'য' to 'j', 'র' to 'r', 'ল' to 'l', 'শ' to 's', 'ষ' to 's',
            'স' to 's', 'হ' to 'h',
            // Nukta letters as precomposed code points (ড় ঢ় য়); decomposed
            // input is normalized before lookup.
            'ড়' to 'r', 'ঢ়' to 'r', 'য়' to 'y',
            'ং' to 'q', 'ঃ' to 'h',
        )

        private val vowelClasses = mapOf(
            'অ' to 'o', 'আ' to 'a', 'া' to 'a',
            'ই' to 'i', 'ঈ' to 'i', 'ি' to 'i', 'ী' to 'i',
            'উ' to 'u', 'ঊ' to 'u', 'ু' to 'u', 'ূ' to 'u',
            'এ' to 'e', 'ে' to 'e', 'ঐ' to 'i', 'ৈ' to 'i',
            'ও' to 'o', 'ো' to 'o', 'ঔ' to 'u', 'ৌ' to 'u',
            'ঋ' to 'r', 'ৃ' to 'r',
        )

        /** Folds a Bengali word to its canonical phonetic key. */
        fun foldBengali(word: String): String {
            // Normalize decomposed nukta pairs to precomposed code points.
            val normalized = word
                .replace("\u09A1\u09BC", "\u09DC")
                .replace("\u09A2\u09BC", "\u09DD")
                .replace("\u09AF\u09BC", "\u09DF")
            val out = StringBuilder()
            for (ch in normalized) {
                val c = consonantClasses[ch]
                val v = vowelClasses[ch]
                when {
                    c != null -> out.append(c)
                    v != null -> out.append(v)
                    // hasant, chandrabindu and anything unmapped fold away
                }
            }
            return dropTrailingO(out.toString())
        }

        /** Folds romanized Bengali typing to the same canonical key. */
        fun foldRoman(input: String): String {
            val lower = input.lowercase()
            val out = StringBuilder()
            var i = 0
            while (i < lower.length) {
                val ch = lower[i]
                val next = lower.getOrNull(i + 1)
                when {
                    // Aspiration folds: kh→k, gh→g, ch→s, th→t, dh→d, ph→p, bh→b, sh→s
                    next == 'h' && ch in "kgctdpbs" -> {
                        out.append(if (ch == 'c') 's' else ch)
                        i += 2
                        // chh → s as well
                        if (ch == 'c' && lower.getOrNull(i) == 'h') i++
                        continue
                    }
                    ch == 'c' -> out.append('s')
                    ch == 'v' -> out.append('b')
                    ch == 'w' -> out.append('o')
                    ch == 'z' -> out.append('j')
                    ch == 'f' -> out.append('p')
                    ch == 'x' -> out.append('k').append('s')
                    ch in "aeiou" -> {
                        // Doubled vowels lengthen: ee→i (ঈ), oo→u (উ)
                        if (next == ch) {
                            i++
                            out.append(when (ch) { 'e' -> 'i'; 'o' -> 'u'; else -> ch })
                        } else {
                            out.append(ch)
                        }
                    }
                    ch.isLetter() -> out.append(ch)
                    // punctuation and digits fold away
                }
                i++
            }
            return dropTrailingO(collapseInherent(out.toString()))
        }

        /**
         * Romanized inherent vowels: "kori" carries its vowels explicitly, but
         * the Bengali fold of করি is "kri" (inherent o is invisible). Dropping
         * medial "o" between consonants from the roman side aligns the two.
         */
        private fun collapseInherent(key: String): String {
            val out = StringBuilder()
            for ((index, ch) in key.withIndex()) {
                val prevIsConsonant = index > 0 && key[index - 1] !in "aeiou"
                val nextIsConsonant = index < key.length - 1 && key[index + 1] !in "aeiou"
                if (ch == 'o' && prevIsConsonant && nextIsConsonant) continue
                out.append(ch)
            }
            return out.toString()
        }

        /** Word-final inherent/o-kar ambiguity: ভালো vs ভাল both end "balo". */
        private fun dropTrailingO(key: String): String =
            if (key.length > 1 && key.endsWith('o')) key.dropLast(1) else key
    }
}
