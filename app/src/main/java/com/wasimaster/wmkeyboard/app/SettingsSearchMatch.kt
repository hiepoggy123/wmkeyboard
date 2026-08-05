package com.wasimaster.wmkeyboard.app

import android.content.res.Resources
import com.wasimaster.wmkeyboard.R
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How settings search matches what you type against what the app calls things.
 *
 * The index in `SettingsSearch.kt` names the rows; this file decides which of
 * them a query means. Three things it has to survive:
 *
 *  1. The settings screens keep one glossary. They say "press and hold", never
 *     "long press"; "glide typing", never "swipe typing"; "turn on", never
 *     "enable". People type the other word. `search_word_groups` joins the two.
 *  2. Words are typed with a thumb. "hapitc" and "vibraton" have to land, so a
 *     word that is one or two edits away from a word of the row still matches,
 *     below anything spelt right.
 *  3. Half a query is better than none. When no row carries every word, the
 *     rows carrying most of them answer instead of an empty screen.
 */

/**
 * Which part of an entry a word was found in, what a hit there is worth, and
 * whether a misspelling counts as a hit at all.
 *
 * Only the short fields spell-correct. A subtitle is a sentence, and in a
 * sentence of twenty words something is always one edit away from what you
 * typed: "mode" finds "more", "dark" finds "mark". On a name of three words
 * that same rule is what rescues a typo.
 */
internal enum class MatchField(val percent: Int, val correctsSpelling: Boolean) {
    /** The row's own name. */
    TITLE(100, true),

    /** Words the row is searched by and never draws. */
    KEYWORDS(60, true),

    /** The breadcrumb: the screen, and the tool, the row sits on. */
    SCREEN(35, false),

    /** The line under the name. */
    SUBTITLE(22, false),
}

/** One field of an entry, in the form the matching below compares against. */
internal class SearchText(val field: MatchField, raw: String) {
    val text: String = normalizeForSearch(raw)
    val words: List<String> = text.split(' ').filter { it.isNotEmpty() }

    /**
     * The words run together. It is what lets "2d" find "2-D cursor touchpad"
     * and "onehanded" find "One-handed mode": a query drops the punctuation
     * that the name keeps.
     */
    val compact: String = words.joinToString("")
}

/** A hit on the whole field: the row is called exactly this. */
private const val TIER_FIELD_EXACT = 100

/** The field starts with the word: "auto" on "Autocorrect". */
private const val TIER_FIELD_PREFIX = 80

/** One whole word of the field, in any position. */
private const val TIER_WORD_EXACT = 70

/** A word of the field starts with it: "sugg" on "Suggestions". */
private const val TIER_WORD_PREFIX = 60

/** A word of the field with the plural or the -y/-ies ending taken off. */
private const val TIER_WORD_STEM = 65

/** The word is inside the field, across word boundaries and all. */
private const val TIER_CONTAINS = 40

/** The word is in the field once the field's punctuation is dropped. */
private const val TIER_COMPACT = 34

/** A word of the field, misspelt. Below every spelt-right hit above. */
private const val TIER_FUZZY = 26

/**
 * What a hit through [SettingsSearchVocabulary] keeps of its score.
 *
 * Low enough that a row named after the word you typed always beats a row named
 * after a word that only means the same, even when the second is a whole screen
 * and the first is one switch: "skin" is the emoji tone before it is a theme.
 */
private const val ALIAS_PERCENT = 45

/**
 * Short words are not spell-corrected. Under five letters one edit joins words
 * that have nothing to do with each other ("mode" and "more", "dark" and
 * "mark"), and a short word is cheap to type again.
 */
private const val FUZZY_MIN_LENGTH = 5

/** Two edits are allowed only once a word is long enough for two to be a slip. */
private const val FUZZY_TWO_EDITS_LENGTH = 8

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Lower case, no accents, and one space between runs of letters and digits.
 *
 * Both sides of every comparison go through this, so "Émoji"/"emoji" and
 * "One-handed"/"one handed" are the same words, and a query needs no
 * punctuation to match a name that has some.
 */
internal fun normalizeForSearch(raw: String): String {
    val folded = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
    val out = StringBuilder(folded.length)
    var pendingSpace = false
    for (ch in folded) {
        if (ch.isLetterOrDigit()) {
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
            out.append(ch)
        } else {
            pendingSpace = true
        }
    }
    return out.toString()
}

/**
 * The words a query and a row can be looked for by, beyond the ones the screens
 * draw: the groups of same-meaning words, and the words too common to narrow
 * anything down.
 *
 * Built once per settings-search screen, from resources, so it speaks the
 * language the rest of the index was resolved in.
 */
internal class SettingsSearchVocabulary(
    groups: List<List<String>>,
    private val stopWords: Set<String>,
) {
    private val alternatives: Map<String, Set<String>> =
        buildMap<String, MutableSet<String>> {
            for (group in groups) {
                for (word in group) {
                    getOrPut(word) { mutableSetOf() } += group.filterNot { it == word }
                }
            }
        }

    /** The other words of every group [word] is in. Empty for an unknown word. */
    fun alternativesOf(word: String): Set<String> = alternatives[word].orEmpty()

    fun isStopWord(word: String): Boolean = word in stopWords

    companion object {
        /** No groups and no stop words: what the ranking tests rank against. */
        val NONE = SettingsSearchVocabulary(emptyList(), emptySet())
    }
}

/** Reads the vocabulary out of the resources [res] is configured for. */
internal fun settingsSearchVocabulary(res: Resources): SettingsSearchVocabulary =
    SettingsSearchVocabulary(
        groups = res.getStringArray(R.array.search_word_groups)
            .map { group -> normalizeForSearch(group).split(' ').filter { it.isNotEmpty() } }
            .filter { it.size > 1 },
        stopWords = normalizeForSearch(res.getString(R.string.search_stop_words))
            .split(' ')
            .filterTo(mutableSetOf()) { it.isNotEmpty() },
    )

/**
 * The plural taken off, so "themes" finds "Theme" and "dictionaries" finds
 * "Dictionary". Deliberately only the plural: an -ing or -ed rule turns real
 * words into each other and costs more accuracy than it buys.
 */
private fun stem(word: String): String = when {
    word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
    word.length > 4 && (word.endsWith("ses") || word.endsWith("xes") || word.endsWith("hes")) ->
        word.dropLast(2)
    word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
    else -> word
}

/**
 * True when [a] and [b] are at most [maxEdits] insertions, deletions,
 * substitutions or swaps of neighbours apart.
 *
 * Damerau rather than plain Levenshtein because the typo a thumb makes most
 * often is two letters in the wrong order, and that is one slip, not two.
 */
private fun withinEdits(a: String, b: String, maxEdits: Int): Boolean {
    if (abs(a.length - b.length) > maxEdits) return false
    if (a == b) return true
    var beforePrevious = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowBest = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                value = min(value, beforePrevious[j - 2] + 1)
            }
            current[j] = value
            rowBest = min(rowBest, value)
        }
        // Every path through this row already costs too much, so every path
        // through the rows below it does too.
        if (rowBest > maxEdits) return false
        val spare = beforePrevious
        beforePrevious = previous
        previous = current
        current = spare
    }
    return previous[b.length] <= maxEdits
}

private fun fuzzyMatches(word: String, token: String): Boolean {
    val maxEdits = if (token.length >= FUZZY_TWO_EDITS_LENGTH) 2 else 1
    return withinEdits(word, token, maxEdits)
}

/**
 * How well [token] matches this field, from [TIER_FUZZY] up to
 * [TIER_FIELD_EXACT]. [spellCorrect] is off for a word the user did not type:
 * a word group already rescues one word with another, and spell-correcting the
 * rescue costs a lot of work for a hit nobody asked for.
 */
private fun SearchText.tier(token: String, spellCorrect: Boolean = true): Int {
    if (text.isEmpty()) return 0
    if (text == token) return TIER_FIELD_EXACT
    if (text.startsWith(token)) return TIER_FIELD_PREFIX
    var best = 0
    for (word in words) {
        if (word == token) return TIER_WORD_EXACT
        if (word.startsWith(token)) {
            best = max(best, TIER_WORD_PREFIX)
        } else if (stem(word) == stem(token)) {
            best = max(best, TIER_WORD_STEM)
        }
    }
    if (best > 0) return best
    if (text.contains(token)) return TIER_CONTAINS
    if (token.length >= 3 && compact.contains(token)) return TIER_COMPACT
    val fuzzy = spellCorrect && field.correctsSpelling && token.length >= FUZZY_MIN_LENGTH
    if (fuzzy && words.any { fuzzyMatches(it, token) }) return TIER_FUZZY
    return 0
}

/** The best hit [token] gets anywhere in [entry], already weighted by field. */
private fun rawScore(entry: SettingsSearchEntry, token: String, spellCorrect: Boolean = true): Int =
    entry.searchText.maxOf { it.tier(token, spellCorrect) * it.field.percent } / 100

/**
 * What [token] is worth against [entry]: the word as typed, or one that means
 * the same thing at a discount, whichever is higher.
 */
private fun tokenScore(
    entry: SettingsSearchEntry,
    token: String,
    vocabulary: SettingsSearchVocabulary,
): Int {
    val direct = rawScore(entry, token)
    // Nothing an alias can find beats the word itself spelt right on a title.
    if (direct >= TIER_WORD_EXACT) return direct
    val alias = vocabulary.alternativesOf(token)
        .maxOfOrNull { rawScore(entry, it, spellCorrect = false) } ?: 0
    return max(direct, alias * ALIAS_PERCENT / 100)
}

/**
 * The words of [query] worth searching for: normalized, and with the words that
 * are in half the settings dropped. A query made only of those keeps them,
 * because dropping every word answers nothing.
 */
internal fun searchTokens(query: String, vocabulary: SettingsSearchVocabulary): List<String> {
    val all = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    val meaningful = all.filterNot { vocabulary.isStopWord(it) }
    return meaningful.ifEmpty { all }
}

private class Ranked(val entry: SettingsSearchEntry, val matched: Int, val score: Int)

/**
 * Ranked matches for [query] over [index].
 *
 * A row's score is the sum of what each query word is worth against it, scaled
 * by the entry's [EntryWeight], so a destination screen beats a row that names
 * it just as well and a backup toggle sinks below the feature it backs up.
 * Ties break on title length, so the plainest setting with a matching name
 * floats above the wordier ones.
 *
 * Rows that carry every word of the query win outright. When none does, the
 * rows carrying the most words answer, as long as that is most of the query:
 * "emoji row" with no such row is better answered by the emoji rows than by an
 * empty screen, while one word out of four is a different question.
 */
internal fun searchSettings(
    query: String,
    index: List<SettingsSearchEntry>,
    vocabulary: SettingsSearchVocabulary = SettingsSearchVocabulary.NONE,
): List<SettingsSearchEntry> {
    val tokens = searchTokens(query, vocabulary)
    if (tokens.isEmpty()) return emptyList()
    val scored = index.mapNotNull { entry ->
        val scores = tokens.map { tokenScore(entry, it, vocabulary) }
        val matched = scores.count { it > 0 }
        if (matched == 0) null else Ranked(entry, matched, scores.sum() * entry.weight.percent)
    }
    if (scored.isEmpty()) return emptyList()
    val bestMatched = scored.maxOf { it.matched }
    if (bestMatched * 2 < tokens.size) return emptyList()
    return scored
        .filter { it.matched == bestMatched }
        .sortedWith(
            compareByDescending<Ranked> { it.score }.thenBy { it.entry.title.length },
        )
        .map { it.entry }
}
