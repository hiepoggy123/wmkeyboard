package com.wasimaster.wmkeyboard.core.emoji

/**
 * Emoji → Unicode short name lookup over a loaded [EmojiCatalog].
 *
 * The catalog is a flat list built once at startup, so the index is cached
 * against its identity: the first call after a (re)load pays for the map,
 * every later lookup is a hash hit. Only the long-press popup asks, so this
 * never runs per grid cell.
 */
object EmojiNames {

    private var source: List<EmojiEntry>? = null
    private var index: Map<String, String> = emptyMap()

    private val TONES = 0x1F3FB..0x1F3FF
    private const val VS16 = '️'

    /**
     * Display name for [emoji], falling back to [base] (the palette entry a
     * skin-toned or gendered variant was picked from) and then to the
     * tone/selector-free form, which is how the catalog keys most entries.
     */
    fun of(catalog: List<EmojiEntry>, emoji: String, base: String = emoji): String? {
        val map = indexFor(catalog)
        return map[emoji]
            ?: map[strip(emoji)]
            ?: map[base]
            ?: map[strip(base)]
    }

    @Synchronized
    private fun indexFor(catalog: List<EmojiEntry>): Map<String, String> {
        if (source !== catalog) {
            source = catalog
            index = buildMap {
                for (entry in catalog) {
                    val name = entry.name ?: continue
                    put(entry.emoji, name)
                    // Same name under the selector-free key so a variant that
                    // dropped its FE0F still resolves.
                    putIfAbsent(strip(entry.emoji), name)
                }
            }
        }
        return index
    }

    /** Drops skin-tone modifiers and the FE0F presentation selector. */
    private fun strip(emoji: String): String = buildString {
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            val size = Character.charCount(cp)
            if (cp !in TONES && cp != VS16.code) appendCodePoint(cp)
            i += size
        }
    }
}
