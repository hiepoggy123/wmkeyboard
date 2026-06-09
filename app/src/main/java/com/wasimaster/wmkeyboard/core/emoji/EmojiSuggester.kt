package com.wasimaster.wmkeyboard.core.emoji

/**
 * Word → emoji prediction for the suggestion strip: typing "birthday"
 * offers 🎂🎉🥳🎁, "coffee" offers ☕, "bangladesh" offers 🇧🇩.
 *
 * Two layers, high precision on purpose (this rides along with word
 * suggestions, so a wrong emoji is worse than no emoji):
 *  1. a small curated trigger map for words whose best emojis aren't the
 *     literal keyword matches (and their common Bengali counterparts);
 *  2. exact keyword hits from the catalog (with a trailing plural 's'
 *     stripped) — no prefix or fuzzy matching.
 */
class EmojiSuggester(entries: List<EmojiEntry>) {

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
        TRIGGERS[cleaned]?.let { results.addAll(it) }
        byKeyword[cleaned]?.let { results.addAll(it) }
        if (results.size < limit && cleaned.length >= 4 && cleaned.endsWith("s")) {
            val singular = cleaned.dropLast(1)
            TRIGGERS[singular]?.let { results.addAll(it) }
            byKeyword[singular]?.let { results.addAll(it) }
        }
        return results.take(limit).toList()
    }

    companion object {
        /** Curated word → emoji triggers; listed emojis always come first. */
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
