package com.wasimaster.wmkeyboard.core.addons

import com.wasimaster.wmkeyboard.addons.feature.R
import com.wasimaster.wmkeyboard.core.plugins.PluginFile
import com.wasimaster.wmkeyboard.core.plugins.PluginManifestResult
import com.wasimaster.wmkeyboard.core.plugins.PluginPermission
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

    /** A word list, plus how many lines it actually has. */
    data class Dictionary(
        /**
         * Every word the reader collected, up to [AddonPreviewReader.MAX_WORDS].
         * The panel shows the first few and the dialog shows the lot, so this is
         * deliberately far longer than fits on a screen.
         */
        val words: List<String>,
        val total: Int,
        /** True when [total] is a floor rather than the real count — the file was long. */
        val truncated: Boolean,
    ) : AddonPreviewContent {
        /** True when [words] is a prefix of the file rather than all of it. */
        val partial: Boolean get() = words.size < total
    }

    /**
     * A sample of an emoji keyword pack: which emoji it names, and what it
     * calls them.
     *
     * A handful of rows is the whole question here — one look at 🎂 next to
     * its keywords tells you whether the pack is the language it claims and
     * whether the keywords are words anyone would type. Unlike a word list,
     * nobody wants to read all four thousand rows.
     */
    data class EmojiKeywords(
        val samples: List<Sample>,
        val total: Int,
        /** True when [total] is a floor rather than the real count. */
        val truncated: Boolean,
    ) : AddonPreviewContent {
        data class Sample(val emoji: String, val keywords: String)
    }

    /** A playable copy of the key sound. */
    data class Sound(val file: File) : AddonPreviewContent

    /** Sticker images extracted beside the archive, ready to be shown. */
    data class Stickers(
        val images: List<File>,
        val total: Int,
    ) : AddonPreviewContent

    /**
     * What a plugin says about itself, and what it would be allowed to do.
     *
     * The odd one out: every other preview answers "is this content any good?",
     * while this one answers "should I let this run at all?". Only the manifest
     * is read — showing someone what a plugin claims it can do should not
     * involve touching the code that would do it.
     */
    data class Plugin(
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val permissions: List<PluginPermission>,
    ) : AddonPreviewContent

    /** The payload downloaded but couldn't be read as its declared type. */
    data class Unreadable(val text: AddonText) : AddonPreviewContent
}

/** Reads a downloaded payload into something showable. Blocking; call on IO. */
object AddonPreviewReader {

    /** More than this and the panel is a wall of text nobody reads. */
    private const val MAX_SNIPPETS = 40

    /**
     * How many words a dictionary preview keeps.
     *
     * The panel only ever shows a handful inline, but "show me the whole list"
     * is the question a word list actually raises, so the dialog needs the
     * words in hand. Ten thousand strings is a couple of hundred KB and covers
     * every dictionary anyone browses word-by-word; past that the dialog says
     * how much it is not showing rather than holding a 300k-word file in RAM
     * for a preview.
     */
    const val MAX_WORDS = 10_000

    /** Stop counting lines here; a 300k-word list doesn't need an exact figure. */
    private const val MAX_COUNTED_LINES = 200_000

    /** Enough rows of a keyword pack to judge it; nobody scrolls a fourth. */
    private const val MAX_KEYWORD_SAMPLES = 24

    private const val MAX_STICKER_IMAGES = 24

    /** Per-image ceiling while unpacking, so a hostile archive can't fill the cache. */
    private const val MAX_IMAGE_BYTES = 4L * 1024 * 1024

    fun read(entry: AddonEntry, payload: File): AddonPreviewContent = when (entry.type) {
        AddonType.Snippets -> readSnippets(payload)
        AddonType.Dictionary -> readDictionary(entry, payload)
        AddonType.EmojiKeywords -> readEmojiKeywords(entry, payload)
        AddonType.Sound -> AddonPreviewContent.Sound(payload)
        AddonType.Stickers -> readStickers(payload)
        AddonType.Plugin -> readPlugin(payload)
        else -> AddonPreviewContent.Unreadable(
            AddonText.of(R.string.faddons_preview_error_no_preview),
        )
    }

    private fun readPlugin(payload: File): AddonPreviewContent {
        val read = runCatching { payload.inputStream().use { PluginFile.readManifest(it) } }
            .getOrNull()
        return when (read) {
            is PluginManifestResult.Ok -> AddonPreviewContent.Plugin(
                name = read.manifest.name,
                version = read.manifest.pluginVersion,
                author = read.manifest.author,
                description = read.manifest.description,
                permissions = read.permissions,
            )

            is PluginManifestResult.Rejected ->
                AddonPreviewContent.Unreadable(read.reasonText.toAddonText())
            else -> AddonPreviewContent.Unreadable(
                AddonText.of(R.string.faddons_error_not_a_plugin),
            )
        }
    }

    /**
     * Reads the head of a keyword pack.
     *
     * Streamed and stopped early like [readDictionary]: the count is capped at
     * [MAX_COUNTED_LINES] so a pack naming every emoji in every script is
     * still one bounded pass, and only [MAX_KEYWORD_SAMPLES] rows are kept.
     */
    private fun readEmojiKeywords(entry: AddonEntry, payload: File): AddonPreviewContent {
        val samples = ArrayList<AddonPreviewContent.EmojiKeywords.Sample>()
        var counted = 0
        val ok = runCatching {
            openMaybeGzipped(entry, payload).bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    // "# " opens a comment; the keycap hash emoji (#️⃣) starts
                    // a data row with a bare "#".
                    if (line.isBlank() || line.startsWith("# ")) continue
                    val parts = line.split('\t')
                    val emoji = parts[0].trim()
                    val keywords = parts.getOrNull(1)?.trim().orEmpty()
                    if (emoji.isEmpty() || keywords.isEmpty()) continue
                    counted++
                    if (samples.size < MAX_KEYWORD_SAMPLES) {
                        samples.add(
                            AddonPreviewContent.EmojiKeywords.Sample(emoji, keywords),
                        )
                    }
                    if (counted >= MAX_COUNTED_LINES) break
                }
            }
        }.isSuccess
        if (!ok || samples.isEmpty()) {
            return AddonPreviewContent.Unreadable(
                AddonText.of(R.string.faddons_preview_error_not_keyword_pack),
            )
        }
        return AddonPreviewContent.EmojiKeywords(
            samples = samples,
            total = counted,
            truncated = counted >= MAX_COUNTED_LINES,
        )
    }

    private fun readSnippets(payload: File): AddonPreviewContent {
        val imported = runCatching { SnippetFile.decode(payload.readText()) }.getOrNull()
            ?: return AddonPreviewContent.Unreadable(
                AddonText.of(R.string.faddons_preview_error_not_snippet_pack),
            )
        return AddonPreviewContent.Snippets(
            entries = imported.snippets.take(MAX_SNIPPETS).map {
                AddonPreviewContent.Snippets.Entry(it.label, it.text, it.trigger.orEmpty())
            },
            total = imported.snippets.size,
        )
    }

    private fun readDictionary(entry: AddonEntry, payload: File): AddonPreviewContent {
        val words = ArrayList<String>()
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
            return AddonPreviewContent.Unreadable(
                AddonText.of(R.string.faddons_error_no_words),
            )
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
            return AddonPreviewContent.Unreadable(
                AddonText.of(R.string.faddons_preview_error_stickers_unreadable),
            )
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
