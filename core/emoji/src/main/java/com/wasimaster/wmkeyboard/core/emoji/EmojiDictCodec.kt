package com.wasimaster.wmkeyboard.core.emoji

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One row of the data repo's emoji dictionary format:
 * `{ "emoji": "😀", "name": "…", "keywords": [...], "category": "…" }`.
 *
 * `category` is decoded and ignored — the app's own catalogue already decides
 * which grid tab an emoji lives in, and a translated category name would only
 * disagree with it.
 */
@Serializable
private data class RepoEmojiRow(
    @SerialName("emoji") val emoji: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("keywords") val keywords: List<String> = emptyList(),
)

/**
 * Reads the gzipped JSON emoji dictionaries from the wmkeyboard-data repo
 * (see [EmojiDictCatalog]) and converts them to the app's pack format.
 *
 * The conversion happens once, at download time, rather than on every load:
 * the JSON is roughly twice the size of the TSV it becomes and costs a full
 * object graph to parse, which is not a thing to redo each time the keyboard
 * starts. It is the same trade the word lists make when they compile a
 * downloaded list into a `.wmdict`.
 */
object EmojiDictCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses the repo's JSON array. Returns null when [text] is not that
     * format at all; an empty pack when it parses but names nothing, which the
     * caller should treat as a failed download rather than an empty success.
     */
    fun decode(text: String): EmojiKeywordPack? {
        val rows = runCatching { json.decodeFromString<List<RepoEmojiRow>>(text) }.getOrNull()
            ?: return null
        val keywords = LinkedHashMap<String, List<String>>(rows.size)
        val names = LinkedHashMap<String, String>(rows.size)
        for (row in rows) {
            val emoji = row.emoji.trim()
            if (emoji.isEmpty()) continue
            val words = row.keywords
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (words.isNotEmpty()) keywords[emoji] = words
            row.name.trim().takeIf { it.isNotEmpty() }?.let { names[emoji] = it }
        }
        return EmojiKeywordPack(keywords, names)
    }

    /**
     * The pack as the TSV the importer and [EmojiKeywordPack.load] read, so a
     * downloaded pack and an imported one are the same thing on disk.
     *
     * Tabs and newlines inside a keyword or name would split a row in two, so
     * they are flattened to spaces rather than escaped — a keyword containing
     * a tab is upstream noise, not something worth a quoting scheme.
     */
    fun encodeTsv(pack: EmojiKeywordPack): String = buildString {
        append("# emoji\tkeywords\tname  (wmkeyboard-data emoji dictionary)\n")
        for (emoji in (pack.keywords.keys + pack.names.keys)) {
            val words = pack.keywords[emoji].orEmpty().joinToString(",") { clean(it).replace(",", " ") }
            val name = pack.names[emoji]?.let(::clean).orEmpty()
            if (words.isEmpty() && name.isEmpty()) continue
            append(clean(emoji)).append('\t').append(words)
            if (name.isNotEmpty()) append('\t').append(name)
            append('\n')
        }
    }

    private fun clean(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
