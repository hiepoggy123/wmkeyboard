package com.wasimaster.wmkeyboard.core.prediction

/**
 * Fixes contractions typed without their apostrophe: arent → aren't,
 * im → I'm, isnt → isn't. Applied at word-commit time (space) when the
 * "Fix missing apostrophes" setting is on, before autocorrect gets a look.
 *
 * The table deliberately excludes every apostrophe-less form that is a
 * real English word in its own right — its, were, well, ill, id, hell,
 * shell, wed, shed, lets, whore, hes-vs-hers style near-misses aside —
 * so the fix never rewrites something the user may have meant literally.
 * "cant" and "wont" are technically words (a cant, wont to do) but are
 * rare enough that every mainstream keyboard corrects them anyway.
 */
object Apostrophes {

    private val CONTRACTIONS: Map<String, String> = mapOf(
        // to be / negations
        "aint" to "ain't",
        "arent" to "aren't",
        "isnt" to "isn't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        // auxiliaries + not
        "cant" to "can't",
        "couldnt" to "couldn't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "dont" to "don't",
        "hadnt" to "hadn't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "mightnt" to "mightn't",
        "mustnt" to "mustn't",
        "neednt" to "needn't",
        "shant" to "shan't",
        "shouldnt" to "shouldn't",
        "wont" to "won't",
        "wouldnt" to "wouldn't",
        // auxiliary + have
        "couldve" to "could've",
        "mightve" to "might've",
        "mustve" to "must've",
        "shouldve" to "should've",
        "wouldve" to "would've",
        // I
        "im" to "I'm",
        "ive" to "I've",
        // he / she / it / that
        "hed" to "he'd",
        "hes" to "he's",
        "shes" to "she's",
        "itd" to "it'd",
        "itll" to "it'll",
        "thatd" to "that'd",
        "thatll" to "that'll",
        "thats" to "that's",
        // they / we / you
        "theyd" to "they'd",
        "theyll" to "they'll",
        "theyre" to "they're",
        "theyve" to "they've",
        "weve" to "we've",
        "youd" to "you'd",
        "youll" to "you'll",
        "youre" to "you're",
        "youve" to "you've",
        "yall" to "y'all",
        // question words
        "whatd" to "what'd",
        "whatll" to "what'll",
        "whatre" to "what're",
        "whats" to "what's",
        "whatve" to "what've",
        "whens" to "when's",
        "whered" to "where'd",
        "wheres" to "where's",
        "whod" to "who'd",
        "wholl" to "who'll",
        "whos" to "who's",
        "whove" to "who've",
        "whyd" to "why'd",
        "whys" to "why's",
        "howd" to "how'd",
        "hows" to "how's",
        // there / here
        "heres" to "here's",
        "theres" to "there's",
        // odds and ends
        "cmon" to "c'mon",
        "maam" to "ma'am",
        "oclock" to "o'clock",
        // Not a contraction, but the same class of slip — and autocorrect's
        // minimum word length means nothing else ever fixes a lone "i".
        "i" to "I",
    )

    /**
     * The corrected word, or null when [word] needs no fixing. The typed
     * capitalization is preserved: Dont → Don't, ARENT → AREN'T; words the
     * fix itself capitalizes (im → I'm) stay capitalized regardless.
     */
    fun fix(word: String): String? {
        val fixed = CONTRACTIONS[word.lowercase()] ?: return null
        val result = when {
            word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() } ->
                fixed.uppercase()
            word.firstOrNull()?.isUpperCase() == true ->
                fixed.replaceFirstChar { it.uppercase() }
            else -> fixed
        }
        return if (result == word) null else result
    }
}
