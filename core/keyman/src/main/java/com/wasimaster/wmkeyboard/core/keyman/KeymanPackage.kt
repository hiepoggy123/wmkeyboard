package com.wasimaster.wmkeyboard.core.keyman

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a `.kmp`, which is a ZIP holding a keyboard and everything that ships
 * with it.
 *
 * Only the rules are wanted. The fonts are Keyman's answer to a problem this
 * app already solves its own way, the help pages are HTML we would not show, and
 * the `.kvk` is a desktop on-screen keyboard. Taking one entry and ignoring
 * twenty keeps the download small and the trust surface one file wide.
 *
 * Lives beside the parser rather than next to the code that downloads it,
 * because reading an archive is a format question with no network in it, and
 * this is where the tests that can hand it a hostile archive already run.
 */
object KeymanPackage {

    /**
     * The `<keyboardId>.kmx` inside [input], or null when it is absent, oversized
     * or the archive is unreadable.
     *
     * ## Why this cannot be tricked into writing somewhere
     *
     * Entry names are **compared, never used as a path**. An archive naming its
     * entry `../../../databases/keys.kmx` matches on the base name alone, and the
     * bytes come back as a return value for the caller to place. There is no
     * path here to escape from, which is a stronger guarantee than sanitising
     * one would be.
     *
     * Length is counted as bytes are read rather than taken from the entry
     * header, because a hostile archive writes that header. A zip bomb therefore
     * runs out of budget at [KeymanLimits.MAX_KMX_BYTES] instead of filling the
     * disk, and the entry count is capped so an archive of millions of empty
     * entries cannot spin here either.
     */
    fun rulesFrom(input: InputStream, keyboardId: String): ByteArray? {
        val wanted = "$keyboardId.$RULES_EXTENSION"
        var entries = 0
        return runCatching {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return@use null
                    if (++entries > MAX_ENTRIES) return@use null
                    if (entry.isDirectory) continue
                    if (!entry.name.substringAfterLast('/').equals(wanted, ignoreCase = true)) {
                        continue
                    }
                    return@use readCapped(zip)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        }.getOrNull()
    }

    private fun readCapped(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > KeymanLimits.MAX_KMX_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Rules file extension inside a package. */
    const val RULES_EXTENSION = "kmx"

    /**
     * Most entries worth walking. Real packages hold a couple of dozen; this
     * only has to stop an archive built to make us walk forever.
     */
    private const val MAX_ENTRIES = 512

    private const val BUFFER = 8192
}
