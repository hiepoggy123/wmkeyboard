package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Jyutping (粵拼) syllable segmentation — the Cantonese counterpart to
 * [PinyinSyllables]. A buffer like `neihou` is one opaque string until it is
 * split into `nei` | `hou`; only then can a commit consume the leading
 * syllable(s) and leave the tail to re-convert.
 *
 * Tones are written as the digits 1–6 *after* the syllable (`nei5hou2`). The
 * conversion table is toneless, as the pinyin one already is, so a tone digit is
 * folded into the span of the syllable it follows and dropped from the reading —
 * the same suffix fold [ZhuyinSyllables] does for its tone marks. That makes
 * tones optional: typing them narrows nothing today but costs nothing either,
 * and `neihou` behaves exactly like `nei5hou2`.
 *
 * The inventory ships as the `jyutping_syllables.txt` asset and is swapped into
 * [valid] off the main thread. Until it loads, [valid] is empty and [segment]
 * returns nothing, so the composer falls back to committing the raw roman
 * letters — no crash, no trap.
 */
object JyutpingSyllables {

    /** The loaded toneless-syllable inventory; empty until the asset is parsed in. */
    @Volatile
    var valid: Set<String> = emptySet()

    /** Longest toneless Jyutping syllable is 6 (`gwaang`, `kwaang`). */
    private const val MAX_SYLLABLE = 6

    private const val TONES = "123456"

    /**
     * Splits [buffer] into leading syllables using [inventory], folding a trailing
     * tone digit into its syllable's [Seg.inputLen] — counted for the deletion
     * math, but never part of [Seg.syllable], which stays toneless for lookup.
     *
     * Backtracks, via [SyllableSegmenter]: Jyutping finals include -p/-t/-k/-m/-n/
     * -ng, so a syllable can borrow the first letter of a vowel-initial one that
     * follows. `aakek` (啞劇) is `aa`+`kek`, but greedy takes the valid `aak` first
     * and then dead-ends on `ek`, losing a real word. Pinyin sidesteps some of
     * this with its apostrophe convention; Jyutping readings carry no such
     * separator, so the split has to be searched rather than guessed.
     */
    fun segment(buffer: String, inventory: Set<String>): List<Seg> =
        SyllableSegmenter.segment(
            buffer,
            inventory,
            maxUnit = MAX_SYLLABLE,
            skipAfter = { it in TONES },
        )

    /** [segment] against the loaded global inventory. */
    fun segment(buffer: String): List<Seg> = segment(buffer, valid)

    /** Parses one-syllable-per-line asset text into an inventory set. */
    fun parse(lines: Sequence<String>): Set<String> =
        lines.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
}
