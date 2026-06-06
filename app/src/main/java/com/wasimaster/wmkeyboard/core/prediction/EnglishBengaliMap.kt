package com.wasimaster.wmkeyboard.core.prediction

import java.io.InputStream

/**
 * Manual English → Bengali map for common loanwords.
 *
 * Words like "keyboard" (কিবোর্ড) or "chair" (চেয়ার) are typed constantly in
 * Bengali script but are not reliably reachable from Avro phonetic rules —
 * their Bengali spelling is fixed by convention, not by sound-for-sound
 * transliteration. When the keyboard is in the Bengali (Avro) input mode,
 * the suggestion engine consults this map first so that typing the English
 * spelling offers the accepted Bengali form ahead of the raw transliteration.
 *
 * The backing asset ([load]) is a two-column TSV (`english<TAB>bengali`).
 * A single English key may map to several Bengali forms (e.g. ticket →
 * টিকেট / টিকিট); they are returned in file order, most-preferred first.
 */
class EnglishBengaliMap private constructor(
    private val byWord: Map<String, List<String>>,
) {

    /** Bengali forms for [english], best first; empty if unmapped. */
    fun lookup(english: String): List<String> =
        byWord[english.trim().lowercase()].orEmpty()

    /** True when [english] has at least one mapped Bengali form. */
    fun contains(english: String): Boolean =
        byWord.containsKey(english.trim().lowercase())

    val size: Int get() = byWord.size

    companion object {
        /** Empty map, used as the default when no asset is supplied (tests). */
        val EMPTY = EnglishBengaliMap(emptyMap())

        /**
         * Parses the `english<TAB>bengali` TSV. Blank lines and `#` comments
         * are skipped; malformed lines (no tab, empty side) are ignored so the
         * asset survives hand editing. Duplicate English keys accumulate their
         * Bengali forms in file order, de-duplicated.
         */
        fun load(stream: InputStream): EnglishBengaliMap {
            val byWord = LinkedHashMap<String, MutableList<String>>()
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val tab = trimmed.indexOf('\t')
                    if (tab <= 0) continue
                    val english = trimmed.substring(0, tab).trim().lowercase()
                    val bengali = trimmed.substring(tab + 1).trim()
                    if (english.isEmpty() || bengali.isEmpty()) continue
                    val forms = byWord.getOrPut(english) { mutableListOf() }
                    if (bengali !in forms) forms.add(bengali)
                }
            }
            return EnglishBengaliMap(byWord)
        }
    }
}
