package com.wasimaster.wmkeyboard.core.vocab

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One word's Wiktionary entry from kaikki.org, the same per-word JSONL the
 * pack builder reads, folded into a [VocabWord] the same way. It is the
 * first place the list editor asks online: the record it gives matches the
 * packs' (senses with quotations, both accents, the family, the etymology,
 * translations), where the free dictionary API gives a few definitions.
 *
 * kaikki is a static site, one file per word; a word it does not have is a
 * plain 404, which [lookup] reports as null.
 */
object KaikkiClient : VocabAutofill.Source {

    private const val USER_AGENT = "WMKeyboard vocabulary (+https://github.com/wasi-master/WMKeyboard)"
    private const val MAX_SENSES_PER_POS = 3
    private const val MAX_RELATED = 12
    private const val MAX_QUOTATIONS = 2
    private const val MAX_TRANSLATIONS_PER_LANGUAGE = 3
    private const val MAX_BYTES = 4L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    private val POS_MAP = mapOf(
        "adj" to "adjective", "adv" to "adverb", "intj" to "interjection", "prep" to "preposition",
        "conj" to "conjunction", "det" to "determiner", "pron" to "pronoun", "num" to "numeral",
        "prep_phrase" to "phrase", "adv_phrase" to "phrase",
    )
    private val POS_DROP = setOf("name", "prefix", "suffix", "character", "symbol", "proverb", "infix", "affix")
    private val FORM_SKIP_TAGS = setOf("table-tags", "inflection-template", "class", "canonical", "romanization")
    private val KEPT_TAGS = setOf(
        "transitive", "intransitive", "formal", "informal", "figurative", "archaic", "dated",
        "rare", "slang", "colloquial", "literary", "obsolete", "countable", "uncountable", "reflexive",
    )

    fun url(word: String): String {
        val w = word.lowercase(Locale.ROOT)
        fun q(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "https://kaikki.org/dictionary/English/meaning/${q(w.take(1))}/${q(w.take(2))}/${q(w)}.jsonl"
    }

    /** Blocking; call on an IO dispatcher. Null when kaikki has no page for the word; throws when it could not be asked. */
    override fun lookup(lemma: String, translationCodes: List<String>): VocabWord? {
        val connection = URL(url(lemma)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (status != HttpURLConnection.HTTP_OK) throw IOException("kaikki HTTP $status")
            val body = connection.inputStream.bufferedReader().use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(16 * 1024)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    text.appendRange(buffer, 0, read)
                    if (text.length > MAX_BYTES) throw IOException("kaikki entry too large")
                }
                text.toString()
            }
            return parse(body, lemma, translationCodes)
        } finally {
            connection.disconnect()
        }
    }

    /** The JSONL page folded into one record, or null when it holds no usable English entry. */
    fun parse(body: String, lemma: String, translationCodes: List<String> = emptyList()): VocabWord? {
        val entries = body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") }
            .mapNotNull { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            .filter { (it.str("lang_code") ?: "en") == "en" }
            .toList()
        if (entries.isEmpty()) return null

        val pos = ArrayList<String>()
        val senses = ArrayList<VocabSense>()
        val synonyms = LinkedHashSet<String>()
        val antonyms = LinkedHashSet<String>()
        val derived = LinkedHashSet<String>()
        val related = LinkedHashSet<String>()
        val hypernyms = LinkedHashSet<String>()
        val hyponyms = LinkedHashSet<String>()
        val forms = LinkedHashSet<String>()
        val ipa = HashMap<String, String>()
        val audio = HashMap<String, String>()
        var rhymes: String? = null
        var etymology: String? = null
        var hyphenation: List<String> = emptyList()
        var wikipedia: String? = null
        val translations = LinkedHashMap<String, Pair<ArrayList<String>, ArrayList<String>>>()

        for (entry in entries) {
            val rawPos = entry.str("pos")?.lowercase(Locale.ROOT) ?: continue
            if (rawPos in POS_DROP) continue
            val partOfSpeech = POS_MAP[rawPos] ?: rawPos
            if (partOfSpeech !in pos) pos += partOfSpeech

            var kept = 0
            for (senseElement in entry.arr("senses")) {
                val sense = senseElement as? JsonObject ?: continue
                if (kept >= MAX_SENSES_PER_POS) break
                val glosses = sense.strings("glosses")
                val definition = glosses.lastOrNull()?.trim().orEmpty()
                if (definition.isEmpty()) continue
                val examples = sense.arr("examples").mapNotNull { it as? JsonObject }
                val example = examples.firstOrNull { it.str("type") != "quotation" && it.str("ref") == null }?.str("text")
                val quotations = examples
                    .filter { it.str("ref") != null && it.str("text") != null }
                    .take(MAX_QUOTATIONS)
                    .map { VocabQuotation(text = it.str("text")!!, ref = it.str("ref")!!.trimEnd(':')) }
                val senseSynonyms = sense.arr("synonyms").words()
                val senseAntonyms = sense.arr("antonyms").words()
                synonyms += senseSynonyms
                antonyms += senseAntonyms
                senses += VocabSense(
                    pos = partOfSpeech,
                    definition = definition,
                    example = example?.trim()?.takeIf { it.isNotEmpty() },
                    quotations = quotations,
                    synonyms = senseSynonyms,
                    antonyms = senseAntonyms,
                    tags = sense.strings("tags").filter { it in KEPT_TAGS },
                    topics = sense.strings("topics").take(3),
                )
                kept++
            }
            synonyms += entry.arr("synonyms").words()
            antonyms += entry.arr("antonyms").words()
            derived += entry.arr("derived").words()
            related += entry.arr("related").words()
            hypernyms += entry.arr("hypernyms").words()
            hyponyms += entry.arr("hyponyms").words()
            for (formElement in entry.arr("forms")) {
                val form = formElement as? JsonObject ?: continue
                val tags = form.strings("tags")
                if (tags.any { it in FORM_SKIP_TAGS }) continue
                val text = form.str("form")?.trim() ?: continue
                if (text.isNotEmpty() && text != lemma && text.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' }) forms += text
            }
            for (soundElement in entry.arr("sounds")) {
                val sound = soundElement as? JsonObject ?: continue
                val tags = sound.strings("tags")
                val accent = when {
                    tags.any { it == "US" || it == "General-American" || it == "GA" } -> VocabAccent.US.key
                    tags.any { it == "UK" || it == "Received-Pronunciation" || it == "RP" } -> VocabAccent.UK.key
                    else -> null
                }
                sound.str("ipa")?.let { value ->
                    if (accent != null) ipa.putIfAbsent(accent, value) else if (ipa.isEmpty()) ipa[VocabAccent.US.key] = value
                }
                sound.str("mp3_url")?.let { value ->
                    if (accent != null) audio.putIfAbsent(accent, value) else if (audio.isEmpty()) audio[VocabAccent.US.key] = value
                }
                if (rhymes == null) rhymes = sound.str("rhymes")
            }
            if (etymology == null) etymology = entry.str("etymology_text")?.trim()?.takeIf { it.isNotEmpty() }
            if (hyphenation.isEmpty()) {
                hyphenation = entry.strings("hyphenation").ifEmpty {
                    entry.arr("hyphenations").firstNotNullOfOrNull { (it as? JsonObject)?.strings("parts") }.orEmpty()
                }
            }
            if (wikipedia == null) wikipedia = entry.strings("wikipedia").firstOrNull()
            if (translationCodes.isNotEmpty()) {
                for (trElement in entry.arr("translations")) {
                    val tr = trElement as? JsonObject ?: continue
                    val code = tr.str("code") ?: tr.str("lang_code") ?: continue
                    if (code !in translationCodes) continue
                    val word = tr.str("word")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val slot = translations.getOrPut(code) { ArrayList<String>() to ArrayList() }
                    if (word in slot.first || slot.first.size >= MAX_TRANSLATIONS_PER_LANGUAGE) continue
                    slot.first += word
                    slot.second += tr.str("roman")?.trim().orEmpty()
                }
            }
        }
        if (senses.isEmpty()) return null
        fun trim(set: Set<String>) = set.filter { it.isNotEmpty() && it != lemma }.take(MAX_RELATED)
        return VocabWord(
            word = lemma,
            pos = pos,
            ipa = ipa,
            audio = audio,
            senses = senses,
            synonyms = trim(synonyms),
            antonyms = trim(antonyms),
            family = if (derived.isEmpty() && related.isEmpty()) null else VocabFamily(trim(derived), trim(related)),
            hypernyms = trim(hypernyms),
            hyponyms = trim(hyponyms),
            forms = forms.toList(),
            hyphenation = hyphenation,
            rhymes = rhymes,
            etymology = etymology,
            attested = etymology?.let { ATTESTED.find(it)?.groupValues?.get(1) },
            wikipedia = wikipedia,
            translations = translations.mapValues { (_, slot) ->
                VocabTranslation(w = slot.first, r = if (slot.second.any { it.isNotEmpty() }) slot.second else emptyList())
            },
        )
    }

    private val ATTESTED = Regex("""[Ff]irst attested (?:in|around|from)\s+(?:the\s+)?(\d{4})""")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.arr(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.strings(key: String): List<String> =
        arr(key).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { s -> s.isNotBlank() } }

    /** `[{"word": "hate"}, …]` → the words. */
    private fun JsonArray.words(): List<String> =
        mapNotNull { (it as? JsonObject)?.str("word")?.trim()?.takeIf { w -> w.isNotEmpty() } }

    @Suppress("unused")
    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
}
