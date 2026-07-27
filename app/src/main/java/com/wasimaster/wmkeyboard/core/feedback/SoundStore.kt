package com.wasimaster.wmkeyboard.core.feedback

import com.wasimaster.wmkeyboard.core.directboot.DirectBoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A key-press sound the user installed, from a repository or their own storage. */
@Serializable
data class InstalledSound(
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val version: String = "",
    /** Always derived from [id], never from anything the file claimed. */
    val fileName: String = "",
    val addedAt: Long = 0L,
)

/**
 * Key-press sounds the user has installed, under `filesDir/keysounds/`:
 *
 * ```
 * keysounds/
 * ├── sounds.json      manifest
 * └── <soundId>.snd
 * ```
 *
 * Note this is `filesDir`, deliberately distinct from the `cacheDir/keysounds/`
 * that [KeySoundPlayer] renders the five built-in waveforms into. Those are
 * derived data and are meant to be evictable; an installed sound is the user's
 * and must not vanish when the system reclaims cache.
 *
 * Same contract as the other stores: one per process, immediate writes,
 * [revision] to notify, null directory before first unlock.
 */
class SoundStore(private var baseDir: File?) {

    @Serializable
    private data class Snapshot(
        val version: Int = FORMAT_VERSION,
        val sounds: List<InstalledSound> = emptyList(),
    )

    private val sounds = ArrayList<InstalledSound>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _revision = MutableStateFlow(0)

    /** Bumped whenever the installed set changes. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    init {
        reload()
    }

    companion object {
        @Volatile
        private var shared: SoundStore? = null

        fun get(context: android.content.Context): SoundStore =
            shared ?: synchronized(this) {
                shared ?: SoundStore(soundsDir(context)).also { shared = it }
            }

        /** Re-points the shared store after a direct-boot unlock. */
        fun attach(context: android.content.Context) {
            get(context).attach(soundsDir(context))
        }

        private fun soundsDir(context: android.content.Context): File? =
            if (DirectBoot.isUserUnlocked(context)) {
                File(context.applicationContext.filesDir, DIR_NAME)
            } else {
                null
            }

        private const val FORMAT_VERSION = 1

        const val DIR_NAME = "keysounds"

        const val MAX_SOUNDS = 30

        /**
         * A key sound is replayed on every keystroke through a `SoundPool`,
         * which decodes it into memory — a few hundred milliseconds is the
         * whole use case, and 4 MB is already far past that.
         */
        const val MAX_BYTES = 4L * 1024 * 1024

        private const val STAGING_PREFIX = ".staging_"

        /**
         * Extension-less on purpose: the store accepts mp3, ogg and wav, and
         * the name is derived from the id rather than from whatever the source
         * file was called.
         */
        fun fileNameFor(id: String): String = "$id.snd"
    }

    // ---- reading -------------------------------------------------------

    @Synchronized
    fun sounds(): List<InstalledSound> = sounds.toList()

    @Synchronized
    fun sound(id: String?): InstalledSound? = sounds.firstOrNull { it.id == id }

    fun fileFor(id: String): File? = baseDir?.let { File(it, fileNameFor(id)) }

    fun existingFileFor(id: String): File? = fileFor(id)?.takeIf { it.isFile && it.length() > 0 }

    // ---- mutating ------------------------------------------------------

    @Synchronized
    fun freeId(now: Long = System.currentTimeMillis()): String {
        var candidate = "sound_$now"
        var suffix = 1
        while (sounds.any { it.id == candidate } || fileFor(candidate)?.exists() == true) {
            candidate = "sound_${now}_${suffix++}"
        }
        return candidate
    }

    @Synchronized
    fun adopt(sound: InstalledSound): InstalledSound? {
        if (sounds.size >= MAX_SOUNDS) return null
        val adopted = sound.copy(name = uniqueName(sound.name), fileName = fileNameFor(sound.id))
        sounds.add(adopted)
        save()
        return adopted
    }

    @Synchronized
    fun delete(id: String) {
        if (!sounds.removeAll { it.id == id }) return
        fileFor(id)?.delete()
        save()
    }

    fun stagingDir(): File? =
        baseDir?.let {
            File(it, STAGING_PREFIX + UUID.randomUUID().toString().take(8)).apply { mkdirs() }
        }

    @Synchronized
    fun adoptFile(id: String, staged: File): Boolean {
        val target = fileFor(id) ?: return false
        target.parentFile?.mkdirs()
        if (staged.renameTo(target)) return true
        return runCatching {
            staged.copyTo(target, overwrite = true)
            staged.delete()
            true
        }.getOrElse {
            target.delete()
            false
        }
    }

    // ---- persistence ---------------------------------------------------

    private fun manifestFile(): File? = baseDir?.let { File(it, "sounds.json") }

    @Synchronized
    fun save() {
        val file = manifestFile()
        if (file != null) {
            runCatching {
                file.parentFile?.mkdirs()
                val part = File(file.parentFile, "sounds.json.part")
                part.writeText(json.encodeToString(Snapshot(sounds = sounds.toList())))
                if (!part.renameTo(file)) {
                    file.delete()
                    part.renameTo(file)
                }
            }
        }
        _revision.value++
    }

    @Synchronized
    fun attach(dir: File?) {
        if (dir?.absolutePath == baseDir?.absolutePath) return
        baseDir = dir
        reload()
    }

    @Synchronized
    fun reload() {
        sounds.clear()
        val dir = baseDir ?: run { _revision.value++; return }
        val file = manifestFile() ?: return
        var readable = true
        if (file.exists()) {
            readable = runCatching {
                val snapshot = json.decodeFromString<Snapshot>(file.readText())
                sounds.addAll(snapshot.sounds.take(MAX_SOUNDS))
            }.isSuccess
        }
        reconcile(dir, sweepUnknown = readable)
        _revision.value++
    }

    private fun reconcile(dir: File, sweepUnknown: Boolean) {
        val alive = sounds.filter { File(dir, fileNameFor(it.id)).isFile }
        val changed = alive.size != sounds.size
        if (changed) {
            sounds.clear()
            sounds.addAll(alive)
        }
        if (sweepUnknown) {
            val referenced = alive.mapTo(HashSet()) { fileNameFor(it.id) }
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    if (!child.name.startsWith(STAGING_PREFIX)) child.deleteRecursively()
                    return@forEach
                }
                if (child.name == "sounds.json" || child.name in referenced) return@forEach
                child.delete()
            }
        }
        if (changed) save()
    }

    private fun uniqueName(name: String, exceptId: String? = null): String {
        val cleaned = name.trim().ifBlank { "Sound" }
        val taken = sounds.filter { it.id != exceptId }.mapTo(HashSet()) { it.name }
        if (cleaned !in taken) return cleaned
        var n = 2
        while ("$cleaned ($n)" in taken) n++
        return "$cleaned ($n)"
    }
}
