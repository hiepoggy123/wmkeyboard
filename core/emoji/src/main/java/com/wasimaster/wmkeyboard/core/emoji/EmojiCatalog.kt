package com.wasimaster.wmkeyboard.core.emoji

import java.io.InputStream

data class EmojiEntry(
    val emoji: String,
    val category: String,
    val keywords: List<String>,
    /**
     * Base emoji this entry is a gender/role variant of (woman-running →
     * runner, man-health-worker → person-health-worker). Variant entries
     * stay searchable but the palette grid shows only the base; variants
     * live in its long-press popup.
     */
    val parent: String? = null,
    /**
     * Unicode short name ("grinning face"), shown in the long-press popup
     * when emoji descriptions are on. Kept case- and phrase-intact, unlike
     * [keywords] which are lowercased and split for search.
     */
    val name: String? = null,
)

/**
 * Loads the bundled emoji catalog.
 *
 * Format: TSV with
 * `emoji<TAB>category<TAB>en,keywords<TAB>bn,keywords<TAB>parent<TAB>name`.
 * `parent` may be empty. English and Bengali keywords are merged into one
 * list so search is language-agnostic — searching বিড়াল or "cat" hits the
 * same entries.
 */
object EmojiCatalog {

    fun load(stream: InputStream): List<EmojiEntry> {
        val entries = ArrayList<EmojiEntry>()
        stream.bufferedReader().useLines { lines ->
            for (line in lines) {
                // "# " opens a comment, a bare "#" does not: the keycap hash
                // emoji (#️⃣) is a data row that starts with one.
                if (line.isBlank() || line.startsWith("# ")) continue
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val keywords = buildList {
                    addAll(parts[2].split(','))
                    if (parts.size > 3) addAll(parts[3].split(','))
                }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val parent = parts.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }
                val name = parts.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
                entries.add(EmojiEntry(parts[0].trim(), parts[1].trim(), keywords, parent, name))
            }
        }
        return entries
    }
}
