package com.wasimaster.wmkeyboard.core.addons

import com.wasimaster.wmkeyboard.core.snippets.SnippetFile
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * What an addon's payload actually contains, read off a downloaded file without
 * installing anything.
 *
 * Screenshots answer "what does this look like"; they can't answer "which words
 * are in this dictionary" or "what does this sound like", which for four of the
 * types is the entire question. So those four get read and summarised here, and
 * the detail page shows the result.
 *
 * Everything is capped: this runs on a phone, against a file a stranger wrote.
 */
sealed interface AddonPreviewContent {

    /** The snippets in a pack, in file order. */
    data class Snippets(
        val entries: List<Entry>,
        val total: Int,
    ) : AddonPreviewContent {
        data class Entry(val label: String, val text: String, val trigger: String)
    }

    /** A sample of a word list, plus how many lines it actually has. */
    data class Dictionary(
        val words: List<String>,
        val total: Int,
        /** True when [total] is a floor rather than the real count — the file was long. */
        val truncated: Boolean,
    ) : AddonPreviewContent

    /** A playable copy of the key sound. */
    data class Sound(val file: File) : AddonPreviewContent

    /** Sticker images extracted beside the archive, ready to be shown. */
    data class Stickers(
        val images: List<File>,
        val total: Int,
    ) : AddonPreviewContent

    /** The payload downloaded but couldn't be read as its declared type. */
    data class Unreadable(val message: String) : AddonPreviewContent
}

/** Reads a downloaded payload into something showable. Blocking; call on IO. */
object AddonPreviewReader {

    /** More than this and the panel is a wall of text nobody reads. */
    private const val MAX_SNIPPETS = 40

    private const val MAX_WORDS = 60

    /** Stop counting lines here; a 300k-word list doesn't need an exact figure. */
    private const val MAX_COUNTED_LINES = 200_000

    private const val MAX_STICKER_IMAGES = 24

    /** Per-image ceiling while unpacking, so a hostile archive can't fill the cache. */
    private const val MAX_IMAGE_BYTES = 4L * 1024 * 1024

    fun read(entry: AddonEntry, payload: File): AddonPreviewContent = when (entry.type) {
        AddonType.Snippets -> readSnippets(payload)
        AddonType.Dictionary -> readDictionary(entry, payload)
        AddonType.Sound -> AddonPreviewContent.Sound(payload)
        AddonType.Stickers -> readStickers(payload)
        else -> AddonPreviewContent.Unreadable("This addon can't be previewed")
    }

    private fun readSnippets(payload: File): AddonPreviewContent {
        val imported = runCatching { SnippetFile.decode(payload.readText()) }.getOrNull()
            ?: return AddonPreviewContent.Unreadable("That file isn't a snippet pack")
        return AddonPreviewContent.Snippets(
            entries = imported.snippets.take(MAX_SNIPPETS).map {
                AddonPreviewContent.Snippets.Entry(it.label, it.text, it.trigger.orEmpty())
            },
            total = imported.snippets.size,
        )
    }

    private fun readDictionary(entry: AddonEntry, payload: File): AddonPreviewContent {
        val words = ArrayList<String>(MAX_WORDS)
        var counted = 0
        val ok = runCatching {
            openMaybeGzipped(entry, payload).bufferedReader().use { reader ->
                while (counted < MAX_COUNTED_LINES) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    // Same shape the importer reads: "word [frequency]", with
                    // '#' starting a comment.
                    if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
                    counted++
                    if (words.size < MAX_WORDS) words += trimmed.substringBefore(' ')
                }
            }
        }.isSuccess
        if (!ok || counted == 0) {
            return AddonPreviewContent.Unreadable("No words could be read out of that file")
        }
        return AddonPreviewContent.Dictionary(
            words = words,
            total = counted,
            truncated = counted >= MAX_COUNTED_LINES,
        )
    }

    private fun openMaybeGzipped(entry: AddonEntry, payload: File) =
        payload.inputStream().buffered().let {
            if (entry.path.endsWith(".gz", ignoreCase = true)) GZIPInputStream(it) else it
        }

    /**
     * Unpacks the first few sticker images beside the archive.
     *
     * Entry names are never used as paths — the same rule `StickerPackFile`
     * import follows — so a `../` inside the archive writes nowhere but the
     * preview directory.
     */
    private fun readStickers(payload: File): AddonPreviewContent {
        val outDir = File(payload.parentFile, payload.nameWithoutExtension + "_stickers")
        outDir.mkdirs()
        val images = ArrayList<File>(MAX_STICKER_IMAGES)
        var total = 0
        val ok = runCatching {
            ZipInputStream(payload.inputStream().buffered()).use { zip ->
                while (true) {
                    val zipEntry = zip.nextEntry ?: break
                    val name = zipEntry.name
                    if (zipEntry.isDirectory || !looksLikeImage(name)) {
                        zip.closeEntry()
                        continue
                    }
                    total++
                    if (images.size < MAX_STICKER_IMAGES) {
                        val target = File(outDir, "sticker_${images.size}.${extensionOf(name)}")
                        if (copyBounded(zip, target)) images += target else target.delete()
                    }
                    zip.closeEntry()
                }
            }
        }.isSuccess
        if (!ok || images.isEmpty()) {
            return AddonPreviewContent.Unreadable("The sticker images couldn't be read")
        }
        return AddonPreviewContent.Stickers(images = images, total = total)
    }

    private fun copyBounded(input: java.io.InputStream, target: File): Boolean = runCatching {
        var written = 0L
        target.outputStream().buffered().use { sink ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                written += read
                if (written > MAX_IMAGE_BYTES) return false
                sink.write(buffer, 0, read)
            }
        }
        written > 0
    }.getOrDefault(false)

    private fun looksLikeImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "png").lowercase()
}
