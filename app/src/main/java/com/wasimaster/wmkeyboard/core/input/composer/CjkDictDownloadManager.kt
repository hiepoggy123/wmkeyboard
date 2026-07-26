package com.wasimaster.wmkeyboard.core.input.composer

import android.os.StatFs
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Downloads [CjkDictCatalog] packs into [CjkDictStore]'s layout, resumably.
 *
 * Modeled on `LocalLlmDownloadManager` (process singleton, own IO scope, `.part`
 * + HTTP Range resume) but for small files, so it adds a SHA-256 verify before
 * the finished file is moved into place — a corrupt conversion table would
 * silently mistranslate, which is worse than falling back to the bundled base.
 * No Hugging Face token or gating: packs are on plain public hosting.
 */
object CjkDictDownloadManager {

    sealed interface DownloadStatus {
        data object NotDownloaded : DownloadStatus

        /** [total] is -1 while unknown (no Content-Length yet). */
        data class Downloading(val bytes: Long, val total: Long) : DownloadStatus

        /** Cancelled or interrupted with a `.part` on disk — resumable. */
        data class Paused(val bytes: Long, val total: Long) : DownloadStatus
        data object Downloaded : DownloadStatus
        data class Failed(val reason: FailReason, val message: String) : DownloadStatus
    }

    enum class FailReason { NETWORK, NO_SPACE, CHECKSUM, UNAVAILABLE, OTHER }

    private const val SPACE_MARGIN_BYTES = 8L * 1024 * 1024
    private const val PROGRESS_INTERVAL_MS = 250L
    private const val USER_AGENT = "WMKeyboard dictionary downloader"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeId: String? = null

    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())

    /** Pack id → status for every catalog entry touched so far. */
    val states: StateFlow<Map<String, DownloadStatus>> = _states.asStateFlow()

    val isBusy: Boolean get() = activeJob?.isActive == true

    /** Seeds [states] from disk; call when the pack manager UI appears. */
    fun refresh(filesDir: File) {
        _states.update { current ->
            CjkDictCatalog.packs.associate { pack ->
                val active = current[pack.id]
                pack.id to when {
                    pack.id == activeId && isBusy && active != null -> active
                    CjkDictStore.isDownloaded(filesDir, pack) -> DownloadStatus.Downloaded
                    CjkDictStore.partFile(filesDir, pack).isFile -> DownloadStatus.Paused(
                        CjkDictStore.partFile(filesDir, pack).length(), pack.sizeBytes,
                    )
                    else -> DownloadStatus.NotDownloaded
                }
            }
        }
    }

    fun start(filesDir: File, pack: CjkDictPack) {
        if (isBusy) return
        if (!pack.available) {
            set(pack.id, DownloadStatus.Failed(FailReason.UNAVAILABLE, "This pack is not available yet."))
            return
        }
        activeId = pack.id
        val part = CjkDictStore.partFile(filesDir, pack)
        set(pack.id, DownloadStatus.Downloading(part.length(), pack.sizeBytes))
        activeJob = scope.launch {
            try {
                downloadInto(pack, part)
                verifyChecksum(pack, part)
                check(part.renameTo(CjkDictStore.packFile(filesDir, pack))) {
                    "could not move the finished download into place"
                }
                set(pack.id, DownloadStatus.Downloaded)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (part.isFile) set(pack.id, DownloadStatus.Paused(part.length(), pack.sizeBytes))
                throw e
            } catch (e: FailedException) {
                // A checksum failure means the .part is garbage — drop it so a
                // retry starts clean instead of resuming a poisoned file.
                if (e.reason == FailReason.CHECKSUM) part.delete()
                set(pack.id, DownloadStatus.Failed(e.reason, e.message.orEmpty()))
            } catch (e: Exception) {
                set(
                    pack.id,
                    DownloadStatus.Failed(
                        FailReason.NETWORK,
                        e.message ?: "The download failed — check your connection and retry",
                    ),
                )
            } finally {
                activeId = null
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
    }

    fun delete(filesDir: File, pack: CjkDictPack) {
        if (pack.id == activeId) cancel()
        CjkDictStore.delete(filesDir, pack)
        set(pack.id, DownloadStatus.NotDownloaded)
    }

    private fun set(id: String, status: DownloadStatus) {
        _states.update { it + (id to status) }
    }

    private class FailedException(val reason: FailReason, message: String) : IOException(message)

    private suspend fun downloadInto(pack: CjkDictPack, part: File) {
        part.parentFile?.mkdirs()
        // A partial longer than the finished pack can only be junk left by an
        // earlier bad resume. Resuming past the end would just fail forever and
        // strand the row on "Download" that never completes, so start over.
        if (part.length() > pack.sizeBytes) part.delete()
        var resumeFrom = part.length()

        val free = StatFs(part.parentFile!!.path).availableBytes
        if (free < pack.sizeBytes - resumeFrom + SPACE_MARGIN_BYTES) {
            throw FailedException(FailReason.NO_SPACE, "Not enough storage — free up some space first")
        }

        val connection = URL(pack.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            // Ask for the bytes as they are on the server. Left alone,
            // HttpURLConnection advertises gzip and silently inflates the reply,
            // so what lands in the `.part` is *decoded* bytes while `Range` below
            // counts *encoded* ones — and the packs are served
            // `Content-Encoding: gzip`. Resuming then asks for a compressed
            // offset, gets a mid-stream gzip fragment, and the inflater dies on
            // its missing header ("ID1ID2 … != 1f8b"). Identity keeps the file,
            // the resume offset and the pack's SHA-256 in one coordinate space.
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit
                HttpURLConnection.HTTP_OK -> resumeFrom = 0 // server ignored Range
                else -> throw FailedException(FailReason.OTHER, "The server returned HTTP $status")
            }

            val remaining = connection.contentLengthLong
            val total = if (remaining >= 0) resumeFrom + remaining else -1L
            var written = resumeFrom
            var lastUpdate = 0L

            connection.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(resumeFrom)
                    out.seek(resumeFrom)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                            lastUpdate = now
                            set(pack.id, DownloadStatus.Downloading(written, total))
                        }
                    }
                }
            }

            if (total >= 0 && written != total) {
                throw IOException("The connection dropped mid-download — resume to continue")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** SHA-256 the finished `.part`; a mismatch is a hard failure. */
    private suspend fun verifyChecksum(pack: CjkDictPack, part: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(pack.sha256, ignoreCase = true)) {
            throw FailedException(
                FailReason.CHECKSUM,
                "The download was corrupted (checksum mismatch) — please retry",
            )
        }
    }
}
