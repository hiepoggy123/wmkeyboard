package com.wasimaster.wmkeyboard.core.emoji

import java.io.InputStream

/**
 * Exact RGI skin-toned sequences, keyed by their tone-free base emoji.
 *
 * Data comes from the bundled `emoji/variants.tsv` (generated straight out
 * of Unicode's emoji-test.txt by tools/emoji/generate_catalog.py), so every
 * sequence here is a real, fully-qualified emoji — no runtime composition
 * that could produce an unsupported combination.
 *
 * Tone indices are Fitzpatrick 1..5 (🏻..🏿); 0 means "no tone" (the
 * neutral yellow base). Bases like the handshake carry two independent
 * tone slots — [tonedPair] resolves any left/right combination.
 */
class EmojiVariantIndex private constructor(
    private val byBase: Map<String, List<ToneVariant>>,
) {

    data class ToneVariant(val tones: List<Int>, val sequence: String)

    /** True when [base] has any skin-tone variants at all. */
    fun hasTones(base: String): Boolean = byBase.containsKey(base)

    /** True when [base] supports two independent tones (handshake, couples…). */
    fun hasDualTones(base: String): Boolean =
        byBase[base]?.any { it.tones.size == 2 } == true

    /**
     * The five uniformly-toned variants of [base], in tone order 1..5.
     * Empty when the base takes no tones.
     */
    fun uniformVariants(base: String): List<String> {
        val rows = byBase[base] ?: return emptyList()
        return (1..5).mapNotNull { tone -> rows.firstOrNull { it.tones == listOf(tone) }?.sequence }
    }

    /**
     * The RGI sequence for [base] with the given tones, where 0 = neutral.
     * Equal (or single-slot) tones resolve to the uniform variant; mixed
     * pairs to the per-person sequence. Null when that combination has no
     * RGI form (mixing one toned and one neutral person, for instance).
     */
    fun tonedPair(base: String, first: Int, second: Int = first): String? {
        if (first == 0 && second == 0) return base
        val rows = byBase[base] ?: return null
        val want = if (first == second) listOf(first) else listOf(first, second)
        return rows.firstOrNull { it.tones == want }?.sequence
    }

    /**
     * All variants a long-press popup should offer for [base]: the neutral
     * base plus the uniform tone row. (Dual-tone combinations are picked
     * through the two-slot selector, not listed exhaustively.)
     */
    fun popupVariants(base: String): List<String> {
        val uniform = uniformVariants(base)
        return if (uniform.isEmpty()) listOf(base) else listOf(base) + uniform
    }

    companion object {

        fun empty() = EmojiVariantIndex(emptyMap())

        /** Parses `base<TAB>tones<TAB>sequence` rows; tones is "3" or "1,4". */
        fun load(stream: InputStream): EmojiVariantIndex {
            val map = HashMap<String, MutableList<ToneVariant>>()
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    val parts = line.split('\t')
                    if (parts.size < 3) continue
                    val tones = parts[1].split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (tones.isEmpty() || tones.any { it !in 1..5 }) continue
                    map.getOrPut(parts[0]) { mutableListOf() }
                        .add(ToneVariant(tones, parts[2]))
                }
            }
            return EmojiVariantIndex(map)
        }
    }
}
