package com.wasimaster.wmkeyboard.core.emoji

/**
 * Word → emoji prediction for the suggestion strip: typing "birthday"
 * offers 🎂🎉🥳🎁, "coffee" offers ☕, "bangladesh" offers 🇧🇩.
 *
 * Three layers, high precision on purpose (this rides along with word
 * suggestions, so a wrong emoji is worse than no emoji):
 *  1. a small curated trigger map, which is where the ordering judgements and
 *     the Bengali triggers live — gemoji has neither;
 *  2. [EmojiTriggers], GitHub's own word→emoji data, which covers far more
 *     English than a hand-written table can ("hooray", "espresso", "workout");
 *  3. exact keyword hits from the catalog (with a trailing plural 's'
 *     stripped) — no prefix or fuzzy matching.
 *
 * The layers stack rather than replace: gemoji has nothing for "sorry",
 * "work" or "sleep", and the curated map has nothing for the other thousand
 * words gemoji names.
 */
class EmojiSuggester(
    entries: List<EmojiEntry>,
    private val triggers: EmojiTriggers = EmojiTriggers.EMPTY,
) {

    private val byKeyword = HashMap<String, MutableList<String>>()

    init {
        for (entry in entries) {
            // Gender/role variants would flood matches like "man"/"woman";
            // their base emoji already carries the concept.
            if (entry.parent != null) continue
            for (keyword in entry.keywords) {
                byKeyword.getOrPut(keyword) { mutableListOf() }.add(entry.emoji)
            }
        }
    }

    fun suggest(word: String, limit: Int = 4): List<String> {
        val cleaned = word.trim().lowercase()
        if (cleaned.length < 2) return emptyList()
        val results = LinkedHashSet<String>()
        addLayers(cleaned, results)
        if (results.size < limit && cleaned.length >= 4 && cleaned.endsWith("s")) {
            addLayers(cleaned.dropLast(1), results)
        }
        return results.take(limit).toList()
    }

    /** The three layers for one word, in the order they should be offered. */
    private fun addLayers(word: String, into: MutableSet<String>) {
        TRIGGERS[word]?.let { into.addAll(it) }
        into.addAll(triggers.of(word))
        byKeyword[word]?.let { into.addAll(it) }
    }

    companion object {
        /**
         * Curated word → emoji triggers; listed emojis always come first.
         *
         * Deliberately kept alongside [EmojiTriggers] rather than folded into
         * it: this is where the Bengali triggers live, and where an ordering
         * is chosen by hand ("birthday" leading with 🎂 rather than 🥳).
         */
        private val TRIGGERS: Map<String, List<String>> = mapOf(
            "birthday" to listOf("🎂", "🎉", "🥳", "🎁"),
            "coffee" to listOf("☕", "😊"),
            "tea" to listOf("🍵", "☕"),
            "party" to listOf("🥳", "🎉", "🪩"),
            "congrats" to listOf("🎉", "👏", "🥳"),
            "congratulations" to listOf("🎉", "👏", "🥳"),
            "thanks" to listOf("🙏", "😊", "❤️"),
            "thank" to listOf("🙏", "😊"),
            "love" to listOf("❤️", "😍", "🥰"),
            "haha" to listOf("😂", "🤣"),
            "lol" to listOf("😂", "🤣"),
            "wow" to listOf("😮", "🤩"),
            "sad" to listOf("😢", "😞", "💔"),
            "cry" to listOf("😭", "😢"),
            "angry" to listOf("😠", "😡"),
            "sorry" to listOf("😔", "🙏"),
            "please" to listOf("🙏", "🥺"),
            "good" to listOf("👍", "😊"),
            "great" to listOf("👏", "🔥", "💯"),
            "night" to listOf("🌙", "😴", "✨"),
            "morning" to listOf("🌅", "☀️", "☕"),
            "sleep" to listOf("😴", "💤", "🛌"),
            "fire" to listOf("🔥"),
            "hot" to listOf("🥵", "🔥", "☀️"),
            "cold" to listOf("🥶", "❄️"),
            "rain" to listOf("🌧️", "☔", "⛈️"),
            "sun" to listOf("☀️", "😎"),
            "food" to listOf("🍽️", "😋", "🍚"),
            "hungry" to listOf("😋", "🍽️"),
            "pizza" to listOf("🍕"),
            "cake" to listOf("🍰", "🎂"),
            "music" to listOf("🎵", "🎶", "🎧"),
            "cricket" to listOf("🏏"),
            "football" to listOf("⚽", "🏈"),
            "win" to listOf("🏆", "🥇", "🎉"),
            "money" to listOf("💰", "💸"),
            "work" to listOf("💼", "💻"),
            "home" to listOf("🏠", "🏡"),
            "car" to listOf("🚗"),
            "flight" to listOf("✈️", "🛫"),
            "beach" to listOf("🏖️", "🌊"),
            "book" to listOf("📚", "📖"),
            "idea" to listOf("💡", "🤔"),
            "ok" to listOf("👌", "👍"),
            "yes" to listOf("✅", "👍"),
            "no" to listOf("❌", "🙅"),
            "wedding" to listOf("💍", "👰", "🤵", "💒"),
            "baby" to listOf("👶", "🍼"),
            "eid" to listOf("🌙", "🕌", "✨"),
            "ramadan" to listOf("🌙", "🕌", "🤲"),
            "iftar" to listOf("🌙", "🍽️", "🕌"),
            "prayer" to listOf("🤲", "🙏", "🕌"),
            "puja" to listOf("🙏", "🪔", "🌺"),
            // Bengali triggers
            "জন্মদিন" to listOf("🎂", "🎉", "🥳", "🎁"),
            "শুভ" to listOf("✨", "🎉", "😊"),
            "ভালোবাসা" to listOf("❤️", "😍", "🥰"),
            "ভালোবাসি" to listOf("❤️", "😍", "🥰"),
            "হাসি" to listOf("😂", "😄"),
            "কান্না" to listOf("😭", "😢"),
            "ধন্যবাদ" to listOf("🙏", "😊", "❤️"),
            "অভিনন্দন" to listOf("🎉", "👏", "🥳"),
            "চা" to listOf("🍵", "☕"),
            "কফি" to listOf("☕", "😊"),
            "বাংলাদেশ" to listOf("🇧🇩"),
            "ঈদ" to listOf("🌙", "🕌", "✨"),
            "রমজান" to listOf("🌙", "🕌", "🤲"),
            "ইফতার" to listOf("🌙", "🍽️", "🕌"),
            "নামাজ" to listOf("🤲", "🕌"),
            "দোয়া" to listOf("🤲", "🙏"),
            "পূজা" to listOf("🙏", "🪔", "🌺"),
            "বৃষ্টি" to listOf("🌧️", "☔"),
            "ঘুম" to listOf("😴", "💤"),
            "খাবার" to listOf("🍽️", "😋", "🍚"),
            "ক্রিকেট" to listOf("🏏"),
            "ফুটবল" to listOf("⚽"),
            "আগুন" to listOf("🔥"),
            "মন" to listOf("💙", "🥰"),
            "মিষ্টি" to listOf("🍬", "🍮", "😋"),
        )
    }
}
