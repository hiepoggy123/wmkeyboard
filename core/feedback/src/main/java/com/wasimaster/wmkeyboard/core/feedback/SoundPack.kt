package com.wasimaster.wmkeyboard.core.feedback

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.feedback.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Which kind of key was pressed.
 *
 * A recording of a real keyboard does not sound the same everywhere on the
 * board — a spacebar has stabilisers under it and a different cavity behind it —
 * so a pack may carry a separate set of samples per role. A role a pack does
 * not fill falls back to [DEFAULT], per field, so a pack that names none of
 * them (which is every pack imported from monkeytype, because monkeytype has no
 * per-key sounds at all) behaves exactly like a single-sound addon that happens
 * to have variants.
 *
 * [serialName] is what appears under `roles` in `pack.json`. Spelled out rather
 * than lowercasing [name], so renaming a constant here cannot silently change
 * the published format.
 */
enum class KeySoundRole(val serialName: String) {
    DEFAULT("default"),
    SPACE("space"),
    ENTER("enter"),
    DELETE("delete"),
    MODIFIER("modifier"),
    ;

    companion object {
        /** Null for a role this build does not know — a pack from a later format. */
        fun of(serialName: String): KeySoundRole? =
            entries.firstOrNull { it.serialName == serialName }
    }
}

/**
 * Which half of a keystroke a sound belongs to.
 *
 * A mechanical keyboard makes two noises per key: the switch actuating under
 * the finger and the stem returning when it lifts. A pack that recorded both
 * fills [RELEASE] as well as [PRESS] and the keyboard plays each at the moment
 * it happens, so holding a key really does hold the sound open.
 *
 * [RELEASE] is optional everywhere, and an empty release list means silence
 * rather than a fallback: most sounds worth typing on — a beep, a pop, an
 * interface click — are one event, and inventing a second one for them would
 * double every keystroke.
 */
enum class KeySoundPhase { PRESS, RELEASE }

/** One role's samples. Every field is optional and falls back to the pack's default. */
@Serializable
data class SoundPackRoleSpec(
    val press: List<String> = emptyList(),
    val release: List<String> = emptyList(),
    /** Null means "no opinion" — the pack's own gain applies. */
    val gain: Float? = null,
)

/**
 * A sound pack's `pack.json`.
 *
 * Two shapes share this class: the one a publisher writes, whose `press` paths
 * point at files inside the archive, and the normalised one
 * [SoundPackFile.import] writes into the store, whose entries are the
 * importer's own generated file names. They are the same schema so a pack can
 * round-trip, but only the second is ever joined onto a directory — see
 * [SoundPackStore.SAMPLE_NAME] for why that distinction is the whole security
 * story of this format.
 */
@Serializable
data class SoundPackManifest(
    val format: String = "",
    val version: Int = 0,
    val id: String = "",
    val name: String = "",
    val author: String = "",
    @SerialName("packVersion") val packVersion: String = "1.0.0",
    val description: String = "",
    val gain: Float = 1f,
    val press: List<String> = emptyList(),
    val release: List<String> = emptyList(),
    val roles: Map<String, SoundPackRoleSpec> = emptyMap(),
) {

    /** The samples [role] should play on key-down, falling back to the default set. */
    fun pressFor(role: KeySoundRole): List<String> =
        roles[role.serialName]?.press?.takeIf { it.isNotEmpty() } ?: press

    /** The samples [role] should play on key-up. Empty is normal and means silence. */
    fun releaseFor(role: KeySoundRole): List<String> =
        roles[role.serialName]?.release?.takeIf { it.isNotEmpty() } ?: release

    /** The samples [role] plays for one half of a keystroke. */
    fun samplesFor(role: KeySoundRole, phase: KeySoundPhase): List<String> = when (phase) {
        KeySoundPhase.PRESS -> pressFor(role)
        KeySoundPhase.RELEASE -> releaseFor(role)
    }

    /**
     * Whether any key on the board would make a sound when it comes back up.
     *
     * Asked of a role that fills only `press` too, because that role still
     * falls back to the pack's top-level `release`.
     */
    fun hasRelease(): Boolean =
        release.isNotEmpty() || roles.values.any { it.release.isNotEmpty() }

    /**
     * [role]'s volume multiplier.
     *
     * Clamped into `0f..1f` because `SoundPool.play` cannot amplify: a pack
     * asking for 2.0 gets 1.0 rather than a silently ignored field. Gain is a
     * cut, and packs are expected to ship normalised loud.
     */
    fun gainFor(role: KeySoundRole): Float =
        (roles[role.serialName]?.gain ?: gain).coerceIn(0f, 1f)

    /** The roles this pack actually overrides, for the UI to show what it covers. */
    fun filledRoles(): List<KeySoundRole> =
        KeySoundRole.entries.filter { role ->
            role != KeySoundRole.DEFAULT &&
                roles[role.serialName]?.let { it.press.isNotEmpty() || it.release.isNotEmpty() } == true
        }
}

/** The outcome of reading a `.wmsoundpack` file. */
sealed interface SoundPackImportResult {
    data class Imported(val pack: InstalledSoundPack) : SoundPackImportResult

    /** No `pack.json`, or one for a different format entirely. */
    data object NotASoundPack : SoundPackImportResult

    /** A pack this build will not accept. [messageRes] takes [messageArg], or "" for none. */
    data class Rejected(
        @StringRes val messageRes: Int,
        val messageArg: String = "",
    ) : SoundPackImportResult

    /** [SoundPackStore.MAX_PACKS] reached. */
    data object TooManyPacks : SoundPackImportResult

    /** Unreadable archive, or nowhere to write. */
    data object Failed : SoundPackImportResult
}

/**
 * A key-sound pack as a shareable file: a ZIP holding `pack.json` and the
 * recordings beside it.
 *
 * ```
 * cherrymx-blue-abs.wmsoundpack
 * ├── pack.json
 * └── sounds/
 *     ├── 1.wav
 *     └── …
 * ```
 *
 * **Entry names are never used as paths**, the same rule
 * [com.wasimaster.wmkeyboard.core.plugins.PluginFile] follows: every entry is
 * read into memory under its archive name used purely as a map key, and every
 * file this importer writes is called `s000.snd`, `s001.snd`, … — names it
 * chose itself. The manifest's `press` list is resolved against that map and
 * then rewritten to the generated names, so neither a `../` entry name nor a
 * `../` inside `pack.json` has anywhere to go.
 *
 * Unlike a plugin, a pack that is slightly wrong is still worth having: a
 * `press` entry naming a file that is not in the archive is dropped with the
 * rest of the pack kept, because losing one of ten switch recordings is not
 * worth refusing the other nine. A pack with *nothing* left after that is
 * refused — a key sound that makes no sound reads as a broken keyboard.
 */
object SoundPackFile {

    const val FORMAT = "wmkeyboard-sound-pack"
    const val VERSION = 1
    const val FILE_EXTENSION = "wmsoundpack"
    const val MANIFEST = "pack.json"

    val IMPORT_MIME_TYPES = arrayOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )

    /**
     * Every sample is decoded into the `SoundPool` when the pack is selected,
     * so the ceiling is memory rather than disk. Ten variants across five roles
     * would be fifty; sixty-four leaves room without inviting a pack that
     * decodes for a second on first press.
     */
    const val MAX_SAMPLES = 64

    /** Per list, so one role cannot eat the whole [MAX_SAMPLES] budget. */
    const val MAX_VARIANTS = 32

    /** Same ceiling one standalone key sound gets. */
    const val MAX_SAMPLE_BYTES = SoundStore.MAX_BYTES

    /**
     * Zip-bomb guard. Counted from bytes actually read, never from the sizes
     * the archive's own headers declare.
     */
    const val MAX_TOTAL_BYTES = 16L * 1024 * 1024

    private const val MAX_ENTRIES = 128
    private const val MAX_MANIFEST_BYTES = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Reads a pack's manifest without installing it, for the addon preview. */
    fun readManifest(input: InputStream): SoundPackManifest? {
        val unpacked = runCatching { unpack(input) }.getOrNull() ?: return null
        return unpacked.manifest?.let { parse(it) }?.takeIf { it.format == FORMAT }
    }

    @Suppress("ReturnCount")
    fun import(
        input: InputStream,
        store: SoundPackStore,
        fallbackName: String = "",
        version: String = "",
        now: Long = System.currentTimeMillis(),
    ): SoundPackImportResult {
        if (store.packs().size >= SoundPackStore.MAX_PACKS) return SoundPackImportResult.TooManyPacks

        val unpacked = runCatching { unpack(input) }.getOrNull()
            ?: return SoundPackImportResult.Failed
        if (unpacked.overflowed) {
            return SoundPackImportResult.Rejected(
                R.string.core_feedback_pack_reject_too_large,
                (MAX_TOTAL_BYTES / (1024 * 1024)).toString(),
            )
        }

        val manifestText = unpacked.manifest ?: return SoundPackImportResult.NotASoundPack
        val declared = parse(manifestText) ?: return SoundPackImportResult.NotASoundPack
        if (declared.format != FORMAT) return SoundPackImportResult.NotASoundPack
        if (declared.version > VERSION) {
            return SoundPackImportResult.Rejected(
                R.string.core_feedback_pack_reject_newer_format,
                declared.version.toString(),
            )
        }

        // Resolve every referenced path to bytes, assigning each distinct blob
        // one generated name. Shared samples (a pack whose enter key reuses a
        // letter recording) are stored once and referenced twice.
        val assigned = LinkedHashMap<String, String>()
        val bytes = LinkedHashMap<String, ByteArray>()

        fun resolve(paths: List<String>): List<String> =
            paths.take(MAX_VARIANTS).mapNotNull { path ->
                assigned[path]?.let { return@mapNotNull it }
                val data = unpacked.lookUp(path) ?: return@mapNotNull null
                if (data.size > MAX_SAMPLE_BYTES) return@mapNotNull null
                if (!looksPlayable(data)) return@mapNotNull null
                if (bytes.size >= MAX_SAMPLES) return@mapNotNull null
                val name = SoundPackStore.sampleNameFor(bytes.size)
                assigned[path] = name
                bytes[name] = data
                name
            }

        val press = resolve(declared.press)
        if (press.isEmpty()) {
            return SoundPackImportResult.Rejected(R.string.core_feedback_pack_reject_no_samples)
        }
        val release = resolve(declared.release)
        val roles = declared.roles.mapNotNull { (key, spec) ->
            // A role this build does not know is dropped rather than kept: it
            // would occupy sample budget for something nothing can ever play.
            val role = KeySoundRole.of(key) ?: return@mapNotNull null
            if (role == KeySoundRole.DEFAULT) return@mapNotNull null
            val rolePress = resolve(spec.press)
            val roleRelease = resolve(spec.release)
            if (rolePress.isEmpty() && roleRelease.isEmpty() && spec.gain == null) return@mapNotNull null
            role.serialName to SoundPackRoleSpec(rolePress, roleRelease, spec.gain?.coerceIn(0f, 1f))
        }.toMap()

        val id = store.freeId(now)
        val normalised = SoundPackManifest(
            format = FORMAT,
            version = VERSION,
            id = declared.id,
            name = declared.name.trim().ifBlank { fallbackName.trim() }.ifBlank { "Sound pack" },
            author = declared.author,
            packVersion = declared.packVersion,
            description = declared.description,
            gain = declared.gain.coerceIn(0f, 1f),
            press = press,
            release = release,
            roles = roles,
        )

        val staged = store.stagingDir() ?: return SoundPackImportResult.Failed
        try {
            val samples = File(staged, SoundPackStore.SAMPLES_DIR).apply { mkdirs() }
            bytes.forEach { (name, data) -> File(samples, name).writeBytes(data) }
            File(staged, MANIFEST).writeText(json.encodeToString(normalised))
            if (!store.adoptDir(id, staged)) return SoundPackImportResult.Failed
        } catch (e: java.io.IOException) {
            DebugLog.e("sound", "sound pack import failed", e)
            staged.deleteRecursively()
            return SoundPackImportResult.Failed
        }

        val adopted = store.adopt(
            InstalledSoundPack(
                id = id,
                name = normalised.name,
                author = normalised.author,
                version = version.trim().ifBlank { normalised.packVersion },
                variantCount = press.size,
                sampleCount = bytes.size,
                roles = normalised.filledRoles().map { it.serialName },
                hasRelease = normalised.hasRelease(),
                addedAt = now,
            ),
        ) ?: run {
            store.dirFor(id)?.deleteRecursively()
            return SoundPackImportResult.TooManyPacks
        }
        return SoundPackImportResult.Imported(adopted)
    }

    private fun parse(text: String): SoundPackManifest? =
        runCatching { json.decodeFromString<SoundPackManifest>(text) }.getOrNull()

    /**
     * The same header sniff [SoundFile] uses, reduced to a yes/no: a pack drops
     * an unplayable sample and keeps going, so there is no single refusal to
     * word.
     */
    private fun looksPlayable(data: ByteArray): Boolean {
        if (data.size < 12) return false
        val tag = String(data, 0, 4, Charsets.ISO_8859_1)
        val riffType = String(data, 8, 4, Charsets.ISO_8859_1)
        return when {
            tag.startsWith("ID3") -> true
            (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xE0) == 0xE0 -> true
            tag == "OggS" -> true
            tag == "RIFF" && riffType == "WAVE" -> true
            else -> false
        }
    }

    /**
     * The archive read into memory: entry name -> bytes, plus the manifest as
     * text. [overflowed] records that the cap was hit, so an oversized pack is
     * refused rather than installed with whatever fitted.
     */
    private class Unpacked(
        val files: Map<String, ByteArray>,
        val manifest: String?,
        val overflowed: Boolean,
    ) {
        /**
         * Finds the entry a manifest path names, trying the exact name and then
         * its bare tail, so `sounds/1.wav` and `1.wav` both resolve.
         *
         * Safe because the result never becomes a path: it is a byte array this
         * importer already read, and the file it is written to is named by
         * [SoundPackStore.sampleNameFor].
         */
        fun lookUp(path: String): ByteArray? {
            files[path]?.let { return it }
            val tail = path.substringAfterLast('/').substringAfterLast('\\')
            if (tail.isEmpty()) return null
            return files[tail] ?: files.entries
                .firstOrNull { it.key.substringAfterLast('/') == tail }
                ?.value
        }
    }

    private fun unpack(input: InputStream): Unpacked {
        val files = HashMap<String, ByteArray>()
        var manifest: String? = null
        var overflowed = false
        ZipInputStream(input.buffered()).use { zip ->
            var count = 0
            var total = 0L
            var reading = true
            while (reading) {
                val entry = zip.nextEntry
                val remaining = MAX_TOTAL_BYTES - total
                when {
                    entry == null -> reading = false
                    entry.isDirectory -> Unit
                    ++count > MAX_ENTRIES -> { overflowed = true; reading = false }
                    entry.name == MANIFEST -> manifest = readCapped(zip, MAX_MANIFEST_BYTES).decodeToString()
                    remaining <= 0L -> { overflowed = true; reading = false }
                    else -> {
                        // One byte past the per-sample cap so an oversized
                        // sample can be recognised as oversized rather than
                        // silently truncated into an unplayable file.
                        val perEntry = minOf(remaining, MAX_SAMPLE_BYTES + 1L).toInt()
                        val data = readCapped(zip, perEntry)
                        total += data.size
                        files[entry.name] = data
                    }
                }
            }
        }
        return Unpacked(files, manifest, overflowed)
    }

    /** Reads at most [max] bytes of the current entry; a declared size is never trusted. */
    private fun readCapped(zip: ZipInputStream, max: Int): ByteArray {
        val out = ByteArray(max)
        var filled = 0
        val buffer = ByteArray(16 * 1024)
        while (filled < max) {
            val n = zip.read(buffer, 0, minOf(buffer.size, max - filled))
            if (n <= 0) break
            System.arraycopy(buffer, 0, out, filled, n)
            filled += n
        }
        return out.copyOf(filled)
    }
}
