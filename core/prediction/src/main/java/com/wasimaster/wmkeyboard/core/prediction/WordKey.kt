package com.wasimaster.wmkeyboard.core.prediction

import java.text.Normalizer

/**
 * The one spelling a word is stored and looked up under inside an n-gram pack.
 *
 * A pack matches words by their bytes — [MappedNgramPack] binary-searches a
 * follower run by string — so two spellings of the same word are two different
 * words to it, and the lookup simply misses. Case was already folded here.
 * Composition is the other half, and it is not hypothetical:
 *
 * Bengali য় has both a precomposed spelling (U+09DF) and a decomposed one
 * (য + U+09BC NUKTA). NFC keeps the decomposed one, because U+09DF and its two
 * siblings U+09DC and U+09DD are on Unicode's composition-exclusion list, so
 * NFC takes them apart and never puts them back. The published word lists are
 * NFC and so are the Probhat and Jatiya layouts, but `AvroPhonetic` commits the
 * precomposed form — and 13% of the context words in the shipped Bengali pack
 * carry one. Without this, every one of those lookups failed for an Avro user
 * while looking, on screen, exactly like the spelling that would have worked.
 *
 * Applied on both sides: [NgramPackDownloadManager] keys the pack it compiles
 * through here, and [NgramPack] keys every query through here, so the two
 * cannot drift. Nothing else in the app needs to care which spelling it holds.
 *
 * Cost is a quick-check per word — a table lookup per character that exits
 * early on ASCII — and no allocation at all unless a word is genuinely
 * mis-composed.
 */
internal object NgramKey {

    fun of(word: String): String {
        val lower = word.lowercase()
        return if (Normalizer.isNormalized(lower, Normalizer.Form.NFC)) {
            lower
        } else {
            Normalizer.normalize(lower, Normalizer.Form.NFC)
        }
    }
}
