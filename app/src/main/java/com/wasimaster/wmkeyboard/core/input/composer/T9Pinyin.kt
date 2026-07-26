package com.wasimaster.wmkeyboard.core.input.composer

/**
 * T9 (九宫格) pinyin: the phone-keypad digit encoding of pinyin syllables.
 *
 * On a 9-key pad each digit stands for three or four letters, so a typed digit
 * run is ambiguous — `64` could be any of mg, mh, mi, ng, nh, ni, … The trick
 * that keeps this tractable is to never expand the digits into letters at all:
 * every *valid* pinyin syllable is encoded to its digit string once
 * ([encode]), and the resulting [index] is keyed by that digit string. Typing
 * then segments the digit buffer against `index.keys` exactly the way full
 * pinyin segments against its syllable inventory, and each matched code hands
 * back the handful of real syllables it can spell.
 *
 * That inverts the naive approach — expand 3^n letter combinations, then filter
 * to the ones that happen to be syllables — into a lookup that is bounded by the
 * ~410-entry syllable inventory no matter how long the buffer gets.
 *
 * The index is derived from [PinyinSyllables.valid], so it is empty until that
 * inventory loads and rebuilt alongside it; until then [T9PinyinComposer]
 * segments nothing and falls back to committing the raw digits.
 */
object T9Pinyin {

    /** Digit → syllables it can spell, e.g. `64` → [mi, ni]. Empty until built. */
    @Volatile
    var index: Map<String, List<String>> = emptyMap()

    /** Classic phone keypad: 2=abc, 3=def, … 9=wxyz. 1 and 0 carry no letters. */
    private val DIGIT: Map<Char, Char> = buildMap {
        fun group(digit: Char, letters: String) { for (c in letters) put(c, digit) }
        group('2', "abc"); group('3', "def"); group('4', "ghi"); group('5', "jkl")
        group('6', "mno"); group('7', "pqrs"); group('8', "tuv"); group('9', "wxyz")
    }

    /**
     * The digit string that spells [syllable] on a 9-key pad (`ni` → `64`,
     * `hao` → `426`), or an empty string if it contains a letter the keypad has
     * no key for — such a syllable is simply left out of the index rather than
     * indexed under a code no keystroke can produce.
     */
    fun encode(syllable: String): String {
        val out = StringBuilder(syllable.length)
        for (c in syllable.lowercase()) out.append(DIGIT[c] ?: return "")
        return out.toString()
    }

    /**
     * Groups [inventory] by digit code. Syllables sharing a code are sorted so
     * the index is deterministic; their real ranking comes from the conversion
     * dictionary's word frequencies downstream, not from this order.
     */
    fun buildIndex(inventory: Set<String>): Map<String, List<String>> {
        if (inventory.isEmpty()) return emptyMap()
        val acc = HashMap<String, MutableList<String>>()
        for (syllable in inventory) {
            val code = encode(syllable)
            if (code.isEmpty()) continue
            acc.getOrPut(code) { mutableListOf() }.add(syllable.lowercase())
        }
        return acc.mapValues { (_, v) -> v.distinct().sorted() }
    }
}
