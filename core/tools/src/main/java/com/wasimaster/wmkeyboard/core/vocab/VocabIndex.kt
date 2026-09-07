package com.wasimaster.wmkeyboard.core.vocab

import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every enabled pack of one language merged into two hash maps: lemma to
 * record, and trigger token to the records it should offer. Built once off
 * the main thread and swapped in by reference; the keyboard's smart-chip
 * detector then does one `get` per keystroke and nothing else.
 *
 * Merging: the first pack to define a lemma wins the record, later packs
 * add their source tags and fill fields the first left empty; a user-made
 * pack's mnemonic and translations always override, since the user wrote
 * them. Triggers are expanded from the merged record, so a word in two packs
 * is offered once.
 *
 * A word's translations in the languages the user reads are triggers too:
 * typing ঘৃণা, or "ghrina" on a romanised layout, offers *abhor* the way
 * typing "hate" does. Which languages those are is a build input
 * ([translationCodes]), since the index is rebuilt when they change.
 */
class VocabIndex private constructor(
    val packs: List<VocabPack>,
    /** [VocabPacks.stateToken] of the files this was built from. */
    val token: Int,
    /** The sidecar codes whose glosses were indexed as triggers. */
    val translationCodes: List<String>,
    private val words: HashMap<String, VocabWord>,
    private val triggers: HashMap<String, List<TriggerHit>>,
    private val packOfWord: HashMap<String, VocabPackMeta>,
    /** Inflected form → lemma, so a typed "abhorred" still finds its card. */
    private val forms: HashMap<String, String>,
    /** Every list id a record may name, for badges. */
    val sources: Map<String, VocabSource>,
    /** The longest multi-word trigger, in words; 1 unless a gloss had spaces. */
    val maxTriggerWords: Int,
) {

    /**
     * One word to offer for a typed token. [replacement] is the word bent to
     * the typed form when the pack knows how: "hated" → "abhorred".
     */
    data class TriggerHit(
        val lemma: String,
        val replacement: String,
        val gap: Double,
    )

    val size: Int get() = words.size
    val isEmpty: Boolean get() = words.isEmpty()

    /** The record for [lemma] (any case), or null. */
    fun lookup(lemma: String): VocabWord? = words[lemma.lowercase(Locale.ROOT)]

    /** The record for [token] as a lemma or as one of a record's inflected forms. */
    fun lookupAnyForm(token: String): VocabWord? {
        val key = token.lowercase(Locale.ROOT)
        return words[key] ?: forms[key]?.let { words[it] }
    }

    /**
     * The words to offer for [token], plainest-gap first, at most
     * [MAX_HITS_PER_TRIGGER], keeping only triggers at least [minGap] apart.
     */
    fun hitsFor(token: String, minGap: Double = 0.0): List<TriggerHit> {
        val hits = triggers[foldKey(token)] ?: return emptyList()
        if (minGap <= 0.0) return hits
        return hits.filter { it.gap >= minGap }
    }

    fun byPack(packId: String): List<VocabWord> =
        packs.firstOrNull { it.id == packId }?.words?.mapNotNull { words[it.word] }.orEmpty()

    fun packOf(lemma: String): VocabPackMeta? = packOfWord[lemma.lowercase(Locale.ROOT)]

    /** Every lemma, sorted, for the word-of-the-day draw and the browse tab. */
    val lemmas: List<String> by lazy { words.keys.sorted() }

    val allWords: Collection<VocabWord> get() = words.values

    companion object {
        /** More than this on one token is noise, whatever the packs say. */
        const val MAX_HITS_PER_TRIGGER = 4

        /**
         * The gap a translation trigger carries: above every sensitivity
         * threshold, since a gloss in another language is never "too close"
         * to the English word it stands for.
         */
        const val TRANSLATION_GAP = 9.0

        val EMPTY: VocabIndex = VocabIndex(emptyList(), 0, emptyList(), HashMap(), HashMap(), HashMap(), HashMap(), emptyMap(), 1)

        /**
         * The key a typed token or a gloss is looked up under. Latin text loses
         * case and accents ("odiar", "Việt" → "viet") so a romanisation typed
         * without diacritics still matches; any other script keeps its marks,
         * because in Bengali or Hindi a vowel sign is a letter, not an accent.
         */
        fun foldKey(raw: String): String {
            val lower = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
                .lowercase(Locale.ROOT)
                .replace(WHITESPACE, " ")
            if (lower.all { it.code < ASCII_LIMIT }) return lower
            if (lower.any { Character.isLetter(it) && it.code > LATIN_LIMIT && it.code !in LATIN_EXTENDED }) return lower
            val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
            return decomposed.filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        }

        private const val ASCII_LIMIT = 0x80
        private const val LATIN_LIMIT = 0x24F
        private val LATIN_EXTENDED = 0x1E00..0x1EFF
        private val WHITESPACE = Regex("\\s+")

        /** Pure and synchronous, so it is testable with packs built in memory. */
        fun build(packs: List<VocabPack>, token: Int = 0, translationCodes: Collection<String> = emptyList()): VocabIndex {
            val enabled = packs.filter { it.enabled }
            // Hosted packs first: a hand-typed list should annotate a hosted
            // record, not replace it wholesale.
            val ordered = enabled.filter { !it.meta.userCreated } + enabled.filter { it.meta.userCreated }
            val words = HashMap<String, VocabWord>()
            val packOfWord = HashMap<String, VocabPackMeta>()
            val sources = LinkedHashMap<String, VocabSource>()
            for (pack in ordered) {
                for (source in pack.meta.sources) sources.putIfAbsent(source.id, source)
                for (record in pack.words) {
                    val existing = words[record.word]
                    if (existing == null) {
                        words[record.word] = record
                        packOfWord[record.word] = pack.meta
                    } else {
                        words[record.word] = merge(existing, record, pack.meta.userCreated)
                    }
                }
            }
            val forms = HashMap<String, String>()
            for (record in words.values) {
                for (form in record.forms) {
                    val key = form.lowercase(Locale.ROOT)
                    if (key.isNotEmpty() && key !in words) forms.putIfAbsent(key, record.word)
                }
            }
            val triggers = HashMap<String, ArrayList<TriggerHit>>()
            for (record in words.values) {
                for (trigger in record.triggers) {
                    val triggerToken = foldKey(trigger.w)
                    if (triggerToken.isEmpty()) continue
                    triggers.getOrPut(triggerToken) { ArrayList() } += TriggerHit(record.word, record.word, trigger.gap)
                    for (form in trigger.forms) {
                        val formToken = foldKey(form)
                        if (formToken.isEmpty() || formToken == triggerToken) continue
                        triggers.getOrPut(formToken) { ArrayList() } +=
                            TriggerHit(record.word, matchingForm(record, formToken), trigger.gap)
                    }
                }
            }
            // Glosses in the user's languages, and their romanisations, offer
            // the English word. A gloss shared by many words ("করা", to do) is
            // capped like any other trigger, so it never fans out into noise.
            var maxWords = 1
            val codes = translationCodes.distinct()
            if (codes.isNotEmpty()) {
                for (record in words.values) {
                    for (code in codes) {
                        val gloss = record.translations[code] ?: continue
                        for (spelling in gloss.w + gloss.r) {
                            val key = foldKey(spelling)
                            if (key.isEmpty() || key in words) continue
                            maxWords = maxOf(maxWords, key.count { it == ' ' } + 1)
                            triggers.getOrPut(key) { ArrayList() } += TriggerHit(record.word, record.word, TRANSLATION_GAP)
                        }
                    }
                }
            }
            val capped = HashMap<String, List<TriggerHit>>(triggers.size)
            for ((triggerToken, hits) in triggers) {
                capped[triggerToken] = hits
                    .distinctBy { it.lemma }
                    .sortedWith(compareByDescending<TriggerHit> { it.gap }.thenBy { it.lemma })
                    .take(MAX_HITS_PER_TRIGGER)
            }
            return VocabIndex(enabled, token, codes, words, capped, packOfWord, forms, sources, maxWords.coerceAtMost(MAX_TRIGGER_WORDS))
        }

        /** A gloss longer than this is a sentence, not a word; the detector never looks that far back. */
        const val MAX_TRIGGER_WORDS = 4

        suspend fun load(filesDir: File, langId: String, translationCodes: List<String> = emptyList()): VocabIndex =
            withContext(Dispatchers.IO) {
                build(VocabPacks.load(filesDir, langId), VocabPacks.stateToken(filesDir), translationCodes)
            }

        /** [current] when nothing on disk changed since it was built, else a fresh index. */
        suspend fun reloadIfChanged(
            filesDir: File,
            langId: String,
            current: VocabIndex?,
            translationCodes: List<String> = emptyList(),
        ): VocabIndex = withContext(Dispatchers.IO) {
            val token = VocabPacks.stateToken(filesDir)
            if (current != null && current.token == token && current.translationCodes == translationCodes) {
                current
            } else {
                load(filesDir, langId, translationCodes)
            }
        }

        private fun merge(first: VocabWord, other: VocabWord, otherIsUser: Boolean): VocabWord =
            first.copy(
                pos = first.pos.ifEmpty { other.pos },
                ipa = first.ipa.ifEmpty { other.ipa },
                respelling = first.respelling ?: other.respelling,
                audio = first.audio.ifEmpty { other.audio },
                senses = first.senses.ifEmpty { other.senses },
                synonyms = first.synonyms.ifEmpty { other.synonyms },
                antonyms = first.antonyms.ifEmpty { other.antonyms },
                family = first.family ?: other.family,
                forms = first.forms.ifEmpty { other.forms },
                etymology = first.etymology ?: other.etymology,
                origin = first.origin.ifEmpty { other.origin },
                mnemonic = if (otherIsUser) other.mnemonic ?: first.mnemonic else first.mnemonic ?: other.mnemonic,
                translations = if (otherIsUser) first.translations + other.translations else other.translations + first.translations,
                sources = (first.sources + other.sources).distinct(),
                triggers = first.triggers.ifEmpty { other.triggers },
            )

        /**
         * The record's own form that matches the typed inflection, by suffix:
         * a typed "-ed" wants the record's "-ed" form. Falls back to the lemma
         * when the pack lists no such form.
         */
        internal fun matchingForm(record: VocabWord, typedForm: String): String {
            if (record.forms.isEmpty()) return record.word
            val suffixes = listOf("ing", "ied", "ies", "est", "er", "ed", "es", "s")
            val suffix = suffixes.firstOrNull { typedForm.endsWith(it) } ?: return record.word
            val wanted = when (suffix) {
                "ied" -> listOf("ied", "ed")
                "ies" -> listOf("ies", "es", "s")
                "es" -> listOf("es", "s")
                "s" -> listOf("s", "es")
                else -> listOf(suffix)
            }
            for (candidate in wanted) {
                record.forms.firstOrNull { it.endsWith(candidate) }?.let { return it }
            }
            return record.word
        }
    }
}
