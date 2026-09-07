package com.wasimaster.wmkeyboard.core.vocab

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one merged [VocabIndex] for the process. The keyboard service and the
 * settings app run in the same process, and every vocabulary screen used to
 * read every pack from disk and build its own copy — a second or two of JSON
 * parsing per screen. Now the first caller builds it and everyone else gets
 * the reference; it is rebuilt only when a pack file changes on disk
 * ([VocabPacks.stateToken]) or the translation languages do.
 */
object VocabIndexCache {

    @Volatile
    private var current: VocabIndex? = null
    private val lock = Mutex()

    /** Whatever was built last, possibly stale; for painting a first frame while [get] checks the disk. */
    fun peek(): VocabIndex? = current

    /** The index for the packs on disk, built or reused off the main thread. */
    suspend fun get(filesDir: File, translationCodes: List<String> = emptyList()): VocabIndex = withContext(Dispatchers.IO) {
        val token = VocabPacks.stateToken(filesDir)
        current?.takeIf { it.token == token && it.translationCodes == translationCodes }?.let { return@withContext it }
        lock.withLock {
            // Another caller may have built it while this one waited.
            current?.takeIf { it.token == token && it.translationCodes == translationCodes }?.let { return@withLock it }
            val packs = VocabPacks.languages(filesDir).flatMap { VocabPacks.load(filesDir, it) }
            VocabIndex.build(packs, token, translationCodes).also { current = it }
        }
    }

    /** Forgets the built index; the next [get] reads the disk again. */
    fun invalidate() {
        current = null
    }
}
