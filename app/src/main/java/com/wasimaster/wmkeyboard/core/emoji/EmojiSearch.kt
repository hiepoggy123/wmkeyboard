package com.wasimaster.wmkeyboard.core.emoji

/**
 * Semantic emoji search over the catalog.
 *
 * Beyond literal keyword matching this understands:
 *  - synonyms and related concepts ("party" also surfaces 🍾 via
 *    "celebration"; "fire" surfaces 💥 and ☄️);
 *  - prefix matching while the user is still typing;
 *  - fuzzy matching for one-typo queries ("hapy" still finds 😀);
 *  - multilingual queries — Bengali keywords live in the same index, so
 *    বিড়াল finds the cat emojis and হাসি the smiles.
 *
 * Scoring: exact keyword > synonym-expanded > prefix > fuzzy; matches on
 * multiple query tokens accumulate.
 */
class EmojiSearch(private val entries: List<EmojiEntry>) {

    private val tokenIndex = HashMap<String, MutableSet<Int>>()

    init {
        entries.forEachIndexed { index, entry ->
            for (keyword in entry.keywords) {
                // Index both the full keyword ("heart on fire") and its tokens.
                tokenIndex.getOrPut(keyword) { mutableSetOf() }.add(index)
                for (token in keyword.split(' ')) {
                    if (token.isNotEmpty()) {
                        tokenIndex.getOrPut(token) { mutableSetOf() }.add(index)
                    }
                }
            }
        }
    }

    fun search(query: String, limit: Int = 40): List<EmojiEntry> {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        val scores = HashMap<Int, Int>()
        for (token in tokens) {
            score(token, weight = 100, scores)
            for (synonym in SYNONYMS[token].orEmpty()) {
                score(synonym, weight = 60, scores)
            }
            if (token.length >= 2) {
                for ((keyword, indices) in tokenIndex) {
                    if (keyword.length > token.length && keyword.startsWith(token)) {
                        for (i in indices) scores.merge(i, 40, Int::plus)
                    }
                }
            }
            if (token.length >= 4) {
                for ((keyword, indices) in tokenIndex) {
                    if (isOneEditAway(token, keyword)) {
                        for (i in indices) scores.merge(i, 30, Int::plus)
                    }
                }
            }
        }

        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { entries[it.key] }
    }

    private fun score(token: String, weight: Int, scores: HashMap<Int, Int>) {
        tokenIndex[token]?.forEach { scores.merge(it, weight, Int::plus) }
    }

    /** Damerau-Levenshtein distance exactly 1 (substitution, indel, swap). */
    private fun isOneEditAway(a: String, b: String): Boolean {
        if (a == b) return false
        val lenDiff = a.length - b.length
        if (lenDiff < -1 || lenDiff > 1) return false
        if (lenDiff == 0) {
            var mismatches = 0
            var swapPossible = false
            for (i in a.indices) {
                if (a[i] != b[i]) {
                    mismatches++
                    if (mismatches == 2) {
                        swapPossible = a[i] == b[i - 1] && a[i - 1] == b[i]
                    }
                    if (mismatches > 2) return false
                }
            }
            return mismatches == 1 || (mismatches == 2 && swapPossible)
        }
        val (short, long) = if (a.length < b.length) a to b else b to a
        var i = 0
        var j = 0
        var skipped = false
        while (i < short.length && j < long.length) {
            if (short[i] == long[j]) {
                i++; j++
            } else {
                if (skipped) return false
                skipped = true
                j++
            }
        }
        return true
    }

    companion object {
        /**
         * Concept expansion for common searches. Keys are query tokens,
         * values are additional index tokens to look up. Deliberately small
         * and curated — the catalog's own keyword lists carry most synonyms.
         */
        private val SYNONYMS: Map<String, List<String>> = mapOf(
            "happy" to listOf("joy", "smile", "cheerful", "celebrate"),
            "sad" to listOf("cry", "tear", "disappointed", "unhappy"),
            "laugh" to listOf("lol", "funny", "haha", "joy", "rofl"),
            "funny" to listOf("laugh", "lol", "joke"),
            "love" to listOf("heart", "romance", "affection", "adore", "kiss"),
            "party" to listOf("celebrate", "celebration", "festive", "confetti", "birthday"),
            "birthday" to listOf("party", "celebration", "cake", "gift", "balloon"),
            "celebrate" to listOf("party", "celebration", "congrats"),
            "angry" to listOf("mad", "rage", "furious"),
            "food" to listOf("eat", "hungry", "tasty", "meal"),
            "eat" to listOf("food", "tasty", "hungry"),
            "drink" to listOf("beverage", "coffee", "tea", "juice"),
            "fire" to listOf("flame", "hot", "burn", "explosion", "lit", "comet"),
            "hot" to listOf("fire", "heat", "sun"),
            "cold" to listOf("snow", "ice", "freezing", "winter"),
            "fast" to listOf("speed", "quick", "rocket", "run"),
            "money" to listOf("cash", "dollar", "rich", "payment"),
            "work" to listOf("office", "job", "laptop", "computer"),
            "sleep" to listOf("tired", "zzz", "nap", "bed"),
            "music" to listOf("song", "melody", "sing", "instrument"),
            "travel" to listOf("flight", "vacation", "trip", "airplane"),
            "win" to listOf("trophy", "winner", "champion", "medal", "first"),
            "cute" to listOf("adorable", "sweet", "baby", "kitten", "puppy"),
            "animal" to listOf("pet", "dog", "cat", "bird"),
            "flower" to listOf("blossom", "rose", "bouquet", "spring"),
            "rain" to listOf("weather", "storm", "umbrella", "cloud"),
            "night" to listOf("moon", "star", "sleep", "dark"),
            "pray" to listOf("prayer", "dua", "worship", "religion"),
            "sick" to listOf("ill", "fever", "doctor", "medicine"),
            "strong" to listOf("muscle", "power", "gym", "flex"),
            "think" to listOf("thinking", "wonder", "brain", "idea"),
            "scared" to listOf("fear", "afraid", "horror", "scream"),
            "cool" to listOf("sunglasses", "awesome"),
            "ok" to listOf("okay", "thumbs up", "check"),
            "yes" to listOf("check", "correct", "thumbs up"),
            "no" to listOf("cross", "wrong", "prohibited", "thumbs down"),
            // Bengali concept expansion
            "খুশি" to listOf("হাসি", "আনন্দ", "joy"),
            "মজা" to listOf("হাসি", "funny"),
            "কান্না" to listOf("দুঃখ", "cry"),
            "রাগ" to listOf("angry", "mad"),
            "ভালোবাসা" to listOf("প্রেম", "হৃদয়", "heart"),
            "খাবার" to listOf("food", "ভাত"),
            "আগুন" to listOf("fire", "শিখা"),
        )
    }
}
