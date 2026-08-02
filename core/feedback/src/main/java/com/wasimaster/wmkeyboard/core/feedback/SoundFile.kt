package com.wasimaster.wmkeyboard.core.feedback

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.feedback.R
import java.io.File
import java.io.InputStream

/** The outcome of reading a key-sound file. */
sealed interface SoundImportResult {
    data class Imported(val sound: InstalledSound) : SoundImportResult

    /** Not an audio format `SoundPool` can play. [messageRes] takes no argument. */
    data class NotASound(@StringRes val messageRes: Int) : SoundImportResult

    /** [SoundStore.MAX_SOUNDS] reached. */
    data object TooManySounds : SoundImportResult

    /**
     * Over [SoundStore.MAX_BYTES], unreadable, or nowhere to write.
     *
     * [messageRes] is the text to show the user. [messageArg] is the one
     * format argument that text takes, or "" when it takes none. The UI
     * resolves the pair together:
     * `if (messageArg.isEmpty()) getString(messageRes)`
     * `else getString(messageRes, messageArg)`.
     */
    data class Failed(
        @StringRes val messageRes: Int,
        val messageArg: String = "",
    ) : SoundImportResult
}

/**
 * Reading a key-press sound into the [SoundStore].
 *
 * The published format says `.mp3`, and that is what a repository should ship
 * for portability. Import is wider than the spec — ogg and wav are accepted
 * too, because `SoundPool` plays them natively and a user importing their own
 * file has no reason to convert first. A publisher who ships an ogg simply
 * narrows who can use their addon; nothing breaks.
 *
 * Validation is a header sniff rather than a decode. Unlike a font, there is no
 * cheap way to ask the platform "will this play?" without instantiating a
 * decoder, and the failure mode of a bad file here is a silent keystroke rather
 * than a broken keyboard — so the check is aimed at catching the obvious
 * mistake (an HTML error page, a zip, a video) and no further.
 */
object SoundFile {

    const val FILE_EXTENSION = "mp3"

    val IMPORT_MIME_TYPES = arrayOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/ogg",
        "audio/wav",
        "audio/x-wav",
        "application/octet-stream",
    )

    fun import(
        input: InputStream,
        store: SoundStore,
        name: String,
        author: String = "",
        version: String = "",
        now: Long = System.currentTimeMillis(),
    ): SoundImportResult {
        if (store.sounds().size >= SoundStore.MAX_SOUNDS) return SoundImportResult.TooManySounds
        val staging = store.stagingDir()
            ?: return SoundImportResult.Failed(R.string.core_feedback_sound_import_no_storage_error)
        val staged = File(staging, "sound.snd")

        try {
            var written = 0L
            staged.outputStream().buffered().use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    written += read
                    if (written > SoundStore.MAX_BYTES) {
                        return SoundImportResult.Failed(
                            R.string.core_feedback_sound_import_too_large_error,
                            (SoundStore.MAX_BYTES / (1024 * 1024)).toString(),
                        )
                    }
                    sink.write(buffer, 0, read)
                }
            }
            if (written == 0L) {
                return SoundImportResult.NotASound(R.string.core_feedback_sound_import_empty_error)
            }

            describeMagic(staged)?.let { return SoundImportResult.NotASound(it) }

            val id = store.freeId(now)
            if (!store.adoptFile(id, staged)) {
                return SoundImportResult.Failed(R.string.core_feedback_sound_import_save_error)
            }
            val sound = store.adopt(
                InstalledSound(
                    id = id,
                    name = name.trim().ifBlank { "Sound" },
                    author = author.trim(),
                    version = version.trim(),
                    fileName = SoundStore.fileNameFor(id),
                    addedAt = now,
                ),
            )
            if (sound == null) {
                store.fileFor(id)?.delete()
                return SoundImportResult.TooManySounds
            }
            return SoundImportResult.Imported(sound)
        } catch (e: Exception) {
            // The platform's own wording is English and cannot be translated,
            // so the user gets our text and the detail goes to the log.
            DebugLog.e("sound", "key sound import failed", e)
            return SoundImportResult.Failed(R.string.core_feedback_sound_import_read_error)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Null when the header looks playable; otherwise what the file really is. */
    @StringRes
    private fun describeMagic(file: File): Int? {
        val header = ByteArray(12)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return R.string.core_feedback_sound_import_too_short_error

        val tag = String(header, 0, minOf(read, 4), Charsets.ISO_8859_1)
        val riffType = if (read >= 12) String(header, 8, 4, Charsets.ISO_8859_1) else ""
        // ISO base media (mp4/m4a) puts its brand box at offset 4, not 0.
        val brand = if (read >= 8) String(header, 4, 4, Charsets.ISO_8859_1) else ""

        return when {
            // An MP3 with metadata starts at its ID3 tag; a bare one starts at
            // a frame sync — eleven set bits, so 0xFF followed by 0xE0-or-more.
            tag.startsWith("ID3") -> null
            (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0 -> null
            tag == "OggS" -> null
            tag == "RIFF" && riffType == "WAVE" -> null
            tag.startsWith("PK") -> R.string.core_feedback_sound_import_zip_error
            tag.startsWith("<") -> R.string.core_feedback_sound_import_html_error
            brand == "ftyp" -> R.string.core_feedback_sound_import_mp4_error
            else -> R.string.core_feedback_sound_import_unknown_format_error
        }
    }
}
