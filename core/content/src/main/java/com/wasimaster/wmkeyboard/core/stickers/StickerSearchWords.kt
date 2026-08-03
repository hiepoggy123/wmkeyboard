package com.wasimaster.wmkeyboard.core.stickers

/**
 * The one field the sticker editor shows for [CustomSticker.name] and
 * [CustomSticker.emojis].
 *
 * Both model fields do the same job — [StickerPackStore.searchAsGifItems] is
 * the only reader of either — so the UI asks for one list of search words
 * instead of a name plus a bag of "emoji tags" that nobody could tell apart.
 * The split survives underneath because pack files carry the two fields
 * separately, and a pack written by an older build must still read back the
 * way it was written.
 *
 * The first word is the name, so it stays the sticker's label (the grid reads
 * it out to screen readers) and the rest are tags. That makes the round trip
 * idempotent: open a sticker that arrived from an imported pack, save it
 * untouched, and both fields hold exactly what they held before.
 */
object StickerSearchWords {

    /** The words for [sticker], as one space-separated line for the field. */
    fun of(sticker: CustomSticker): String =
        (listOf(sticker.name) + sticker.emojis).filter { it.isNotBlank() }.joinToString(" ")

    /** Splits what the user typed back into (name, tags). */
    fun split(text: String): Pair<String, List<String>> {
        val words = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        return words.firstOrNull().orEmpty() to words.drop(1)
    }

    /** What a query is matched against: every word, name and tags alike. */
    fun haystack(sticker: CustomSticker): String = of(sticker)

    private val WHITESPACE = Regex("\\s+")
}
