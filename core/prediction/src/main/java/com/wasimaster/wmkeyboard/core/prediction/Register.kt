package com.wasimaster.wmkeyboard.core.prediction

/**
 * The register of the field being typed into, derived by the IME from the
 * target app and field kind when the user enables register priors. NEUTRAL
 * (the default, and the only value when the setting is off) leaves ranking
 * untouched.
 */
enum class Register { NEUTRAL, CASUAL, FORMAL }

/**
 * The word set register priors act on. Only the informal side is enumerable —
 * "formal English" is just English, but chat-speak is a small closed class.
 * CASUAL fields nudge these up; FORMAL fields push them down. Ranking only:
 * none of this ever gates what the user can type or commit.
 */
object RegisterVocabulary {

    val informal: Set<String> = setOf(
        // Chat initialisms.
        "lol", "lmao", "rofl", "omg", "idk", "idc", "btw", "fyi", "imo", "imho",
        "tbh", "irl", "nvm", "brb", "afk", "smh", "ikr", "ffs", "wtf", "tf",
        "rn", "atm", "dm", "ily", "ngl", "fr", "bc", "cuz", "bff", "gg", "wp",
        "thx", "ty", "tysm", "np", "pls", "plz", "omw", "ttyl", "hbu", "wyd",
        "wbu", "lmk", "asap", "jk", "istg", "fomo", "yolo",
        // Contracted / relaxed spellings.
        "gonna", "wanna", "gotta", "kinda", "sorta", "outta", "lemme", "gimme",
        "dunno", "aint", "cmon", "gotcha", "betcha", "coulda", "shoulda",
        "woulda", "tryna", "finna", "bout", "til", "cos",
        // Single-letter / shorthand words.
        "u", "ur", "r", "y", "k", "kk", "im", "ive", "dont", "cant", "wont",
        "didnt", "isnt", "hasnt", "havent", "wasnt", "couldnt", "shouldnt",
        "wouldnt", "yall",
        // Interjections and casual fillers.
        "yeah", "yep", "yup", "nah", "nope", "haha", "hahaha", "hehe", "huh",
        "hmm", "umm", "uhh", "ooh", "yay", "woo", "whoa", "damn", "dang",
        "welp", "meh", "bruh", "bro", "dude", "sup", "yo", "hey", "heya",
        "okay", "ok", "okey", "alright", "awesome", "cool", "sweet", "sick",
        "legit", "lowkey", "highkey", "deadass", "based", "cringe", "sus",
        "vibes", "vibe", "bestie", "fam", "squad", "goat", "slay", "stan",
        "simp", "flex", "salty", "shook", "snacc", "yeet", "oof", "rip",
    )
}
