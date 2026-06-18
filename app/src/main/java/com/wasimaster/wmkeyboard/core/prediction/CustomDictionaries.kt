package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.settings.KeyboardLanguage
import java.io.File
import java.io.InputStream

/**
 * User-supplied word lists, one folder per language.
 *
 * The bundled lists only cover English and Bengali; French, German and
 * Spanish ship with no dictionary at all. Rather than wait for a curated
 * list per language, this lets anyone drop in their own — a Hunspell
 * `.dic`, a frequency list, or a plain column of words — and get
 * completions and autocorrect for a language the app knows nothing about.
 *
 * Lists live in `filesDir/dictionaries/<LANGUAGE>/<name>.txt` and are
 * additive: several lists may sit in one language, and for English and
 * Bengali they stack on top of the bundled list instead of replacing it.
 *
 * Format is [DictionaryLoader]'s: `word<space>frequency`, frequency
 * optional. `#` comments and junk lines are skipped, so most word lists
 * found in the wild import as-is.
 */
object CustomDictionaries {

    /** Refuse absurd files outright rather than spending a minute parsing one. */
    const val MAX_BYTES = 32L * 1024 * 1024

    fun root(filesDir: File): File = File(filesDir, "dictionaries")

    fun languageDir(filesDir: File, language: KeyboardLanguage): File =
        File(root(filesDir), language.name)

    /** Imported lists for one language, oldest first. */
    fun lists(filesDir: File, language: KeyboardLanguage): List<File> =
        languageDir(filesDir, language)
            .listFiles { f -> f.isFile && f.extension == "txt" }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** Every entry across every list for one language, in file order. */
    fun entries(filesDir: File, language: KeyboardLanguage): List<Pair<String, Int>> {
        val all = ArrayList<Pair<String, Int>>()
        for (file in lists(filesDir, language)) {
            runCatching { file.inputStream().use { all += DictionaryLoader.loadEntries(it) } }
        }
        return all
    }

    fun trie(filesDir: File, language: KeyboardLanguage): Trie =
        Trie().apply { for ((word, freq) in entries(filesDir, language)) insert(word, freq) }

    /**
     * Copies [stream] in as a list named after [displayName], returning how
     * many words it parsed to. A file that yields nothing usable is deleted
     * again and reported as 0, so a wrong pick (a PDF, an image) fails
     * visibly instead of sitting in the list contributing nothing.
     */
    fun import(
        filesDir: File,
        language: KeyboardLanguage,
        displayName: String,
        stream: InputStream,
    ): Int {
        val dir = languageDir(filesDir, language).apply { mkdirs() }
        val target = uniqueFile(dir, displayName)
        target.outputStream().use { stream.copyTo(it) }
        val count = runCatching {
            target.inputStream().use { DictionaryLoader.loadEntries(it).size }
        }.getOrDefault(0)
        if (count == 0) target.delete()
        return count
    }

    fun remove(file: File): Boolean = file.delete()

    /**
     * Picked names arrive straight from the document provider, so strip
     * anything that could climb out of the language folder and settle
     * collisions with a numeric suffix.
     */
    private fun uniqueFile(dir: File, displayName: String): File {
        val base = displayName
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .trim()
            .take(48)
            .ifEmpty { "wordlist" }
        var candidate = File(dir, "$base.txt")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).txt")
            n++
        }
        return candidate
    }
}
