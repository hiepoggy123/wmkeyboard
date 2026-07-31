package com.wasimaster.wmkeyboard.core.emoji

/**
 * Words in the user's own languages, for the settings copy that explains emoji
 * search.
 *
 * "Type টাকা and get 💰" only lands for someone who reads Bengali. Everyone else
 * is being shown a script they can't read as proof that a feature works, which
 * is worse than showing them nothing. So the examples follow the languages the
 * user has actually enabled, and fall back to a fixed set only when none of them
 * has an entry here.
 *
 * This is illustrative copy, not a lookup table the search itself uses — the
 * real keywords live in the emoji catalogue and its downloadable packs.
 */
object EmojiSearchExamples {

    /** One language's word for a concept, with the language it belongs to. */
    data class Example(val languageId: String, val word: String)

    /** "money" → 💰. */
    val money: List<Example> = listOf(
        Example("bn", "টাকা"),
        Example("hi", "पैसा"),
        Example("ar", "مال"),
        Example("ru", "деньги"),
        Example("ja", "お金"),
        Example("zh", "钱"),
        Example("ko", "돈"),
        Example("es", "dinero"),
        Example("fr", "argent"),
        Example("de", "Geld"),
        Example("pt", "dinheiro"),
        Example("it", "soldi"),
        Example("tr", "para"),
        Example("id", "uang"),
        Example("vi", "tiền"),
        Example("th", "เงิน"),
        Example("fa", "پول"),
        Example("ur", "پیسہ"),
        Example("ta", "பணம்"),
        Example("te", "డబ్బు"),
        Example("mr", "पैसा"),
        Example("pl", "pieniądze"),
        Example("nl", "geld"),
        Example("uk", "гроші"),
        Example("he", "כסף"),
        Example("el", "χρήματα"),
        Example("sw", "pesa"),
        Example("ms", "wang"),
        Example("tl", "pera"),
    )

    /** "birthday" → 🎂. */
    val birthday: List<Example> = listOf(
        Example("bn", "জন্মদিন"),
        Example("hi", "जन्मदिन"),
        Example("ar", "عيد ميلاد"),
        Example("ru", "день рождения"),
        Example("ja", "誕生日"),
        Example("zh", "生日"),
        Example("ko", "생일"),
        Example("es", "cumpleaños"),
        Example("fr", "anniversaire"),
        Example("de", "Geburtstag"),
        Example("pt", "aniversário"),
        Example("it", "compleanno"),
        Example("tr", "doğum günü"),
        Example("id", "ulang tahun"),
        Example("vi", "sinh nhật"),
        Example("th", "วันเกิด"),
        Example("fa", "تولد"),
        Example("ur", "سالگرہ"),
        Example("ta", "பிறந்தநாள்"),
        Example("te", "పుట్టినరోజు"),
        Example("mr", "वाढदिवस"),
        Example("pl", "urodziny"),
        Example("nl", "verjaardag"),
        Example("uk", "день народження"),
        Example("he", "יום הולדת"),
        Example("el", "γενέθλια"),
        Example("sw", "siku ya kuzaliwa"),
        Example("ms", "hari lahir"),
        Example("tl", "kaarawan"),
    )

    /**
     * Up to [limit] words from [examples], preferring the user's own languages
     * in their own order and topping up from the front of the list when they
     * don't cover enough.
     *
     * English is skipped throughout: the surrounding copy always names English
     * separately, so repeating it here would spend one of very few slots saying
     * something the sentence already said.
     */
    fun pick(
        examples: List<Example>,
        enabledLanguageIds: List<String>,
        limit: Int = 3,
    ): List<String> {
        val byLanguage = examples.associateBy { it.languageId }
        val out = LinkedHashSet<String>()
        for (id in enabledLanguageIds) {
            if (id == "en") continue
            byLanguage[id]?.let { out += it.word }
            if (out.size >= limit) break
        }
        for (example in examples) {
            if (out.size >= limit) break
            out += example.word
        }
        return out.take(limit).toList()
    }

    /**
     * The one word to use where the copy has room for a single example — the
     * user's own first, or Bengali as the stand-in, since it is the one non-English
     * language whose keywords are bundled rather than downloaded.
     */
    fun one(examples: List<Example>, enabledLanguageIds: List<String>): String =
        pick(examples, enabledLanguageIds, limit = 1).first()
}
