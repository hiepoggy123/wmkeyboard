package com.wasimaster.wmkeyboard.core.emoji

import java.io.File

/**
 * File layout for downloaded emoji dictionaries, under
 * `filesDir/emoji_dict/<langId>/pack.tsv` (with `pack.tsv.part` while the
 * download runs).
 *
 * The atomic-rename contract means a final file exists only if it was written
 * completely, so presence == valid — the same rule [
 * com.wasimaster.wmkeyboard.core.dictionaries.DictionaryStore] uses for
 * `.wmdict` files.
 *
 * Deliberately separate from `filesDir/emoji_keywords/` ([EmojiKeywordPacks],
 * the user's own imports) for the reason the word lists keep the two apart:
 * one is ours to replace whenever the catalogue moves, the other is the
 * user's and must never be deleted by a background refresh. Both are merged
 * into the catalogue at load, so a language can have both.
 */
object EmojiDictStore {

    private const val FILE_NAME = "pack.tsv"

    /**
     * Marker left behind when the user deletes a pack, so the automatic
     * download does not immediately fetch it again. Without it, Delete is a
     * button that undoes itself on the next settings emission.
     */
    private const val DECLINED_NAME = "declined"

    private fun root(filesDir: File) = File(filesDir, "emoji_dict")

    fun packFile(filesDir: File, langId: String): File =
        File(File(root(filesDir), langId), FILE_NAME)

    fun partFile(filesDir: File, langId: String): File =
        File(File(root(filesDir), langId), "$FILE_NAME.part")

    fun isDownloaded(filesDir: File, langId: String): Boolean =
        packFile(filesDir, langId).isFile

    /**
     * Removes a downloaded pack and records that the user did not want it, so
     * the automatic pass leaves the language alone from here on. An explicit
     * download ([clearDeclined]) takes the mark off again.
     */
    fun delete(filesDir: File, langId: String) {
        runCatching {
            val dir = File(root(filesDir), langId)
            dir.deleteRecursively()
            dir.mkdirs()
            File(dir, DECLINED_NAME).createNewFile()
        }
    }

    fun isDeclined(filesDir: File, langId: String): Boolean =
        File(File(root(filesDir), langId), DECLINED_NAME).exists()

    fun clearDeclined(filesDir: File, langId: String) {
        runCatching { File(File(root(filesDir), langId), DECLINED_NAME).delete() }
    }

    /**
     * Languages with a finished download, ignoring any half-written ones.
     *
     * Written out longhand rather than as a chain of safe calls over
     * `listFiles`: the concise form trips a K2/UAST resolution bug in Android
     * Lint ("No fir element was found for KtParameter") that aborts the whole
     * analysis task on this file.
     */
    fun downloadedLanguageIds(filesDir: File): List<String> {
        val children: Array<File> = root(filesDir).listFiles() ?: return emptyList()
        val ids = ArrayList<String>(children.size)
        for (child in children) {
            if (child.isDirectory && isDownloaded(filesDir, child.name)) ids.add(child.name)
        }
        ids.sort()
        return ids
    }

    fun load(filesDir: File, langId: String): EmojiKeywordPack? {
        val file = packFile(filesDir, langId)
        if (!file.isFile) return null
        // A pack that has been truncated or scribbled on is simply absent as
        // far as the catalogue is concerned; there is no user to tell, and the
        // download row already shows what is on disk.
        val pack = runCatching {
            file.inputStream().use { stream -> EmojiKeywordPack.load(stream, langId) }
        }.getOrNull()
        return if (pack == null || pack.isEmpty) null else pack
    }

    /** Every downloaded pack, for folding into the catalogue at load. */
    fun loadAll(filesDir: File): List<EmojiKeywordPack> {
        val packs = ArrayList<EmojiKeywordPack>()
        for (langId in downloadedLanguageIds(filesDir)) {
            load(filesDir, langId)?.let { packs.add(it) }
        }
        return packs
    }
}
