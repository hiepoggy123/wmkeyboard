package com.wasimaster.wmkeyboard.core.keyman

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
     * Everything a package needs to give up to become a usable layout.
     *
     * [touchLayoutJson] comes out of the compiled `.js`, because a distributed
     * package carries no `.keyman-touch-layout`; the compiler folds it in as the
     * `KVKL` property. [rules] is the `.kmx`, which is what makes the keys type
     * what their author meant rather than what they say.
     */
    data class Contents(
        val keyboardId: String,
        val name: String,
        /** BCP-47 tags the package declares, in its own order. */
        val languages: List<String>,
        val touchLayoutJson: String?,
        val rules: ByteArray?,
    ) {
        /** Nothing to show the user without a grid. */
        val isUsable: Boolean get() = touchLayoutJson != null

        // Data class with a ByteArray: the generated equals would compare it by
        // identity, which is a trap for anyone who writes `a == b` later.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * Reads a whole package in one pass.
     *
     * One pass because [ZipInputStream] is a stream: rewinding it means holding
     * the archive twice, and a package is mostly fonts we do not want in memory
     * at all. The manifest, the compiled keyboard and the rules are picked out
     * as they go past and everything else is skipped without being read.
     */
    fun read(input: InputStream): Contents? {
        var manifest: String? = null
        var js: String? = null
        var rules: ByteArray? = null
        var entries = 0

        runCatching {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return null
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/').lowercase()
                    when {
                        name == MANIFEST -> manifest = readCapped(zip, MAX_MANIFEST_BYTES)
                            ?.decodeToString()
                        name.endsWith(".js") && js == null ->
                            js = readCapped(zip, MAX_JS_BYTES)?.decodeToString()
                        name.endsWith(".$RULES_EXTENSION") && rules == null ->
                            rules = readCapped(zip, KeymanLimits.MAX_KMX_BYTES.toLong())
                        else -> Unit
                    }
                }
            }
        }.getOrElse { return null }

        val meta = manifest?.let(::readManifest) ?: return null
        val touchLayout = js?.let { text ->
            (KeymanJs.extractTouchLayout(KeymanJs.collapseVersionTernaries(text))
                as? KeymanResult.Success)?.value
        }
        return Contents(
            keyboardId = meta.first,
            name = meta.second,
            languages = meta.third,
            touchLayoutJson = touchLayout,
            rules = rules,
        )
    }

    /** `kmp.json`'s id, name and language tags. Null when it is not one. */
    private fun readManifest(text: String): Triple<String, String, List<String>>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return null
        val keyboard = (root["keyboards"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return null
        val id = (keyboard["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return null
        val name = (keyboard["name"] as? JsonPrimitive)?.content.orEmpty()
        val languages = (keyboard["languages"] as? JsonArray).orEmpty().mapNotNull {
            ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.content
        }
        return Triple(id, name.ifBlank { id }, languages)
    }

    private fun readCapped(zip: ZipInputStream, cap: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > cap) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

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
                    return@use readCapped(zip, KeymanLimits.MAX_KMX_BYTES.toLong())
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        }.getOrNull()
    }

    /** Rules file extension inside a package. */
    const val RULES_EXTENSION = "kmx"

    /** The manifest every package carries. Its presence is what identifies one. */
    const val MANIFEST = "kmp.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A manifest is a few kilobytes; more than this is not one. */
    private const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024

    /**
     * A compiled keyboard, generously. The largest in the corpus is about
     * 140 KB, and the cap only has to stop a package from being read into
     * memory whole.
     */
    private const val MAX_JS_BYTES = 4L * 1024 * 1024

    /**
     * Most entries worth walking. Real packages hold a couple of dozen; this
     * only has to stop an archive built to make us walk forever.
     */
    private const val MAX_ENTRIES = 512

    private const val BUFFER = 8192
}
