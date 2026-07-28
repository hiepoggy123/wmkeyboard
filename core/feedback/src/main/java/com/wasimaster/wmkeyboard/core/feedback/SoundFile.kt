package com.wasimaster.wmkeyboard.core.feedback

import java.io.File
import java.io.InputStream

/** The outcome of reading a key-sound file. */
sealed interface SoundImportResult {
    data class Imported(val sound: InstalledSound) : SoundImportResult

    /** Not an audio format `SoundPool` can play. */
    data class NotASound(val message: String) : SoundImportResult

    /** [SoundStore.MAX_SOUNDS] reached. */
    data object TooManySounds : SoundImportResult

    /** Over [SoundStore.MAX_BYTES], unreadable, or nowhere to write. */
    data class Failed(val message: String) : SoundImportResult
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
        val staging = store.stagingDir() ?: return SoundImportResult.Failed("No storage available")
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
                            "Key sounds are limited to ${SoundStore.MAX_BYTES / (1024 * 1024)} MB",
                        )
                    }
                    sink.write(buffer, 0, read)
                }
            }
            if (written == 0L) return SoundImportResult.NotASound("The file is empty")

            describeMagic(staged)?.let { return SoundImportResult.NotASound(it) }

            val id = store.freeId(now)
            if (!store.adoptFile(id, staged)) {
                return SoundImportResult.Failed("Couldn't save the sound")
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
            return SoundImportResult.Failed(e.message ?: "Couldn't read the sound")
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Null when the header looks playable; otherwise what the file really is. */
    private fun describeMagic(file: File): String? {
        val header = ByteArray(12)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return "The file is too short to be a sound"

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
            tag.startsWith("PK") -> "That's a zip archive, not a sound file"
            tag.startsWith("<") -> "That's an HTML or XML file, not a sound"
            brand == "ftyp" -> "MP4 and M4A files aren't supported — use an MP3"
            else -> "That doesn't look like an MP3, OGG or WAV file"
        }
    }
}
