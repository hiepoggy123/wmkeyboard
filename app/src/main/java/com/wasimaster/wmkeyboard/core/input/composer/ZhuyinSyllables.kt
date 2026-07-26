package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Zhuyin (注音 / Bopomofo) syllables, derived from the pinyin inventory.
 *
 * Zhuyin and toneless pinyin are two spellings of the same Mandarin syllable set,
 * so rather than ship a second reference table this encodes every syllable in
 * [PinyinSyllables.valid] to bopomofo once ([encode]) and keeps the reverse map
 * in [table]. Typing then segments the bopomofo buffer against `table.keys` and
 * looks the *pinyin* value up in the conversion pack the full keyboard already
 * uses — Zhuyin needs no dictionary of its own. Same derive-don't-ship trick as
 * [T9Pinyin], which encodes the same inventory to keypad digits.
 *
 * Tone marks are typed but never looked up: the pinyin pack is toneless, and a
 * tone mark is a natural syllable terminator, so [segment] folds it into the
 * span of the syllable it follows and drops it from the reading.
 */
object ZhuyinSyllables {

    /** Bopomofo syllable → toneless pinyin. Empty until [buildTable] runs. */
    @Volatile
    var table: Map<String, String> = emptyMap()

    /** Initial + medial + final: ㄓㄨㄤ (zhuang) is the longest a syllable gets. */
    private const val MAX_SYLLABLE = 3

    /**
     * Tone 2/3/4 and the neutral tone. Tone 1 is unmarked, so an untoned syllable
     * is complete on its own — a following initial can only begin a new syllable,
     * since bopomofo's initials, medials and finals are disjoint character classes.
     */
    private val TONES = setOf('ˊ', 'ˇ', 'ˋ', '˙')

    /** One segmented syllable, its pinyin reading, and the bopomofo chars it spanned. */
    data class Seg(val syllable: String, val pinyin: String, val inputLen: Int)

    /**
     * Splits [buffer] into leading syllables against [table], greedily taking the
     * longest match and folding any trailing tone mark into that syllable's
     * [Seg.inputLen] — the mirror of the way [PinyinSyllables.segment] folds a
     * *leading* apostrophe. Stops at the first position nothing covers, so a
     * half-typed syllable is left for the caller to treat as raw input.
     */
    fun segment(buffer: String, table: Map<String, String>): List<Seg> {
        if (buffer.isEmpty() || table.isEmpty()) return emptyList()
        val out = ArrayList<Seg>()
        var i = 0
        while (i < buffer.length) {
            var matchLen = 0
            val maxLen = minOf(MAX_SYLLABLE, buffer.length - i)
            for (len in maxLen downTo 1) {
                if (buffer.substring(i, i + len) in table) { matchLen = len; break }
            }
            if (matchLen == 0) break
            val syllable = buffer.substring(i, i + matchLen)
            i += matchLen
            // A tone mark belongs to the syllable it follows: counted in the span
            // so a prefix commit deletes it, but never part of the reading.
            val toned = i < buffer.length && buffer[i] in TONES
            if (toned) i++
            out.add(Seg(syllable, table.getValue(syllable), matchLen + if (toned) 1 else 0))
        }
        return out
    }

    /** [segment] against the loaded global table. */
    fun segment(buffer: String): List<Seg> = segment(buffer, table)

    /** Groups an inventory into bopomofo → pinyin, skipping anything unencodable. */
    fun buildTable(inventory: Set<String>): Map<String, String> {
        if (inventory.isEmpty()) return emptyMap()
        val acc = HashMap<String, String>()
        for (syllable in inventory) {
            val zhuyin = encode(syllable)
            // Two pinyin spellings never share a bopomofo form, so first-wins is
            // only a guard against a malformed inventory, not a real collision.
            if (zhuyin.isNotEmpty()) acc.putIfAbsent(zhuyin, syllable.lowercase())
        }
        return acc
    }

    /**
     * The bopomofo spelling of a toneless pinyin [syllable] (`ni` → `ㄋㄧ`,
     * `zhuang` → `ㄓㄨㄤ`), or an empty string for anything outside the standard
     * syllable set — interjections like `hm`/`ng` have no bopomofo form and are
     * simply left out of the table.
     */
    fun encode(syllable: String): String {
        val s = syllable.lowercase()
        WHOLE[s]?.let { return it }
        // A syllable with no initial at all is written as its final alone
        // (a → ㄚ, ang → ㄤ, ou → ㄡ). Safe to try before the initial split: no
        // final begins with a consonant, so this can never shadow a real one.
        FINALS[s]?.let { return it }
        // Longest initial first, so zh/ch/sh are not read as z/c/s.
        for (len in 2 downTo 1) {
            if (s.length <= len) continue
            val initial = INITIALS[s.substring(0, len)] ?: continue
            var rest = s.substring(len)
            // After j/q/x the letter u always spells ü — there is no plain -u
            // final in that series, so ju/jue/juan/jun are ㄐㄩ/ㄐㄩㄝ/ㄐㄩㄢ/ㄐㄩㄣ.
            if (s[0] in "jqx" && rest.startsWith("u")) rest = "v" + rest.drop(1)
            val final = FINALS[rest] ?: return ""
            return initial + final
        }
        return ""
    }

    /**
     * Syllables that are not initial+final: the seven whose written -i is no vowel
     * at all (`zhi` is bare ㄓ), the standalone ㄦ, and the y-/w-/yu- spellings
     * pinyin uses when a medial carries the syllable on its own.
     */
    private val WHOLE: Map<String, String> = buildMap {
        put("zhi", "ㄓ"); put("chi", "ㄔ"); put("shi", "ㄕ"); put("ri", "ㄖ")
        put("zi", "ㄗ"); put("ci", "ㄘ"); put("si", "ㄙ")
        put("er", "ㄦ")
        put("yi", "ㄧ"); put("ya", "ㄧㄚ"); put("yo", "ㄧㄛ"); put("ye", "ㄧㄝ")
        put("yai", "ㄧㄞ"); put("yao", "ㄧㄠ"); put("you", "ㄧㄡ"); put("yan", "ㄧㄢ")
        put("yin", "ㄧㄣ"); put("yang", "ㄧㄤ"); put("ying", "ㄧㄥ"); put("yong", "ㄩㄥ")
        put("yu", "ㄩ"); put("yue", "ㄩㄝ"); put("yuan", "ㄩㄢ"); put("yun", "ㄩㄣ")
        put("wu", "ㄨ"); put("wa", "ㄨㄚ"); put("wo", "ㄨㄛ"); put("wai", "ㄨㄞ")
        put("wei", "ㄨㄟ"); put("wan", "ㄨㄢ"); put("wen", "ㄨㄣ"); put("wang", "ㄨㄤ")
        put("weng", "ㄨㄥ")
    }

    private val INITIALS: Map<String, String> = buildMap {
        put("b", "ㄅ"); put("p", "ㄆ"); put("m", "ㄇ"); put("f", "ㄈ")
        put("d", "ㄉ"); put("t", "ㄊ"); put("n", "ㄋ"); put("l", "ㄌ")
        put("g", "ㄍ"); put("k", "ㄎ"); put("h", "ㄏ")
        put("j", "ㄐ"); put("q", "ㄑ"); put("x", "ㄒ")
        put("zh", "ㄓ"); put("ch", "ㄔ"); put("sh", "ㄕ"); put("r", "ㄖ")
        put("z", "ㄗ"); put("c", "ㄘ"); put("s", "ㄙ")
    }

    private val FINALS: Map<String, String> = buildMap {
        put("a", "ㄚ"); put("o", "ㄛ"); put("e", "ㄜ"); put("ê", "ㄝ")
        put("ai", "ㄞ"); put("ei", "ㄟ"); put("ao", "ㄠ"); put("ou", "ㄡ")
        put("an", "ㄢ"); put("en", "ㄣ"); put("ang", "ㄤ"); put("eng", "ㄥ")
        put("i", "ㄧ"); put("ia", "ㄧㄚ"); put("ie", "ㄧㄝ"); put("iao", "ㄧㄠ")
        put("iu", "ㄧㄡ"); put("ian", "ㄧㄢ"); put("in", "ㄧㄣ"); put("iang", "ㄧㄤ")
        put("ing", "ㄧㄥ"); put("iong", "ㄩㄥ")
        put("u", "ㄨ"); put("ua", "ㄨㄚ"); put("uo", "ㄨㄛ"); put("uai", "ㄨㄞ")
        put("ui", "ㄨㄟ"); put("uan", "ㄨㄢ"); put("un", "ㄨㄣ"); put("uang", "ㄨㄤ")
        put("ong", "ㄨㄥ")
        put("v", "ㄩ"); put("ve", "ㄩㄝ"); put("van", "ㄩㄢ"); put("vn", "ㄩㄣ")
    }
}
