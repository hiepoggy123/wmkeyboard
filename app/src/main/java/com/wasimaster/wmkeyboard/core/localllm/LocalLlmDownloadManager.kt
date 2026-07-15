package com.wasimaster.wmkeyboard.core.localllm

import android.os.StatFs
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
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
 * Downloads catalog models from Hugging Face into [LocalLlmStore]'s layout.
 *
 * Process-level singleton with its own IO scope, so progress survives
 * recomposition, rotation, and screen navigation; the settings UI just
 * collects [states]. Deliberately no WorkManager or foreground service
 * (the app uses neither): leaving the app can stop an in-flight download,
 * but the `.part` file plus an HTTP Range request make Resume pick up
 * exactly where it stopped — including after process death.
 *
 * One download at a time: these are multi-GB files and parallel downloads
 * just split the same bandwidth while doubling failure modes.
 */
object LocalLlmDownloadManager {

    sealed interface DownloadStatus {
        data object NotDownloaded : DownloadStatus

        /** [total] is -1 while unknown (no Content-Length yet). */
        data class Downloading(val bytes: Long, val total: Long) : DownloadStatus

        /** Cancelled or interrupted with a `.part` on disk — resumable. */
        data class Paused(val bytes: Long, val total: Long) : DownloadStatus
        data object Downloaded : DownloadStatus
        data class Failed(val reason: FailReason, val message: String) : DownloadStatus
    }

    enum class FailReason { GATED_NO_TOKEN, LICENSE_NOT_ACCEPTED, NETWORK, NO_SPACE, OTHER }

    /** Free space to leave untouched after a download completes. */
    private const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024
    private const val PROGRESS_INTERVAL_MS = 250L
    private const val USER_AGENT = "WMKeyboard model downloader"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeId: String? = null

    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())

    /** Model id → status for every catalog entry touched so far. */
    val states: StateFlow<Map<String, DownloadStatus>> = _states.asStateFlow()

    val isBusy: Boolean get() = activeJob?.isActive == true

    /** Seeds [states] from disk; call when the model manager UI appears. */
    fun refresh(filesDir: File) {
        _states.update { current ->
            LocalLlmCatalog.models.associate { model ->
                val active = current[model.id]
                model.id to when {
                    // Never clobber a live download's progress state.
                    model.id == activeId && isBusy && active != null -> active
                    LocalLlmStore.isDownloaded(filesDir, model) -> DownloadStatus.Downloaded
                    LocalLlmStore.partFile(filesDir, model).isFile -> DownloadStatus.Paused(
                        LocalLlmStore.partFile(filesDir, model).length(), model.sizeBytes,
                    )
                    else -> DownloadStatus.NotDownloaded
                }
            }
        }
    }

    fun start(filesDir: File, model: LocalLlmModel, hfToken: String) {
        if (isBusy) return
        activeId = model.id
        val part = LocalLlmStore.partFile(filesDir, model)
        set(model.id, DownloadStatus.Downloading(part.length(), model.sizeBytes))
        activeJob = scope.launch {
            try {
                downloadInto(model, part, hfToken)
                check(part.renameTo(LocalLlmStore.modelFile(filesDir, model))) {
                    "could not move the finished download into place"
                }
                set(model.id, DownloadStatus.Downloaded)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Only advertise a resumable Paused state when the .part still
                // exists. delete() cancels, removes the file, and sets
                // NotDownloaded; without this guard the unwinding coroutine
                // overwrites that with Paused(0, size), resurrecting a model
                // the user just deleted.
                if (part.isFile) set(model.id, DownloadStatus.Paused(part.length(), model.sizeBytes))
                throw e
            } catch (e: FailedException) {
                set(model.id, DownloadStatus.Failed(e.reason, e.message.orEmpty()))
            } catch (e: Exception) {
                // IOException mid-stream leaves a resumable .part behind.
                set(
                    model.id,
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

    fun delete(filesDir: File, model: LocalLlmModel) {
        if (model.id == activeId) cancel()
        LocalLlmStore.delete(filesDir, model)
        set(model.id, DownloadStatus.NotDownloaded)
    }

    private fun set(id: String, status: DownloadStatus) {
        _states.update { it + (id to status) }
    }

    private class FailedException(val reason: FailReason, message: String) : IOException(message)

    private suspend fun downloadInto(model: LocalLlmModel, part: File, hfToken: String) {
        part.parentFile?.mkdirs()
        var resumeFrom = part.length()

        val free = StatFs(part.parentFile!!.path).availableBytes
        if (free < model.sizeBytes - resumeFrom + SPACE_MARGIN_BYTES) {
            val neededGb = (model.sizeBytes - resumeFrom + SPACE_MARGIN_BYTES) / 1e9
            throw FailedException(
                FailReason.NO_SPACE,
                "Not enough storage — free up about %.1f GB first".format(neededGb),
            )
        }

        val connection = URL(LocalLlmCatalog.downloadUrl(model)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (hfToken.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }
            if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit // appending below
                HttpURLConnection.HTTP_OK -> {
                    // Server ignored the Range (or none was sent) — start over.
                    resumeFrom = 0
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw FailedException(
                    FailReason.GATED_NO_TOKEN,
                    if (hfToken.isBlank()) {
                        "This model is gated — add your Hugging Face token first"
                    } else {
                        "Hugging Face rejected the token — check it and try again"
                    },
                )
                HttpURLConnection.HTTP_FORBIDDEN -> throw FailedException(
                    FailReason.LICENSE_NOT_ACCEPTED,
                    "Accept the model's license on its Hugging Face page, then retry",
                )
                else -> throw FailedException(
                    FailReason.OTHER, "Hugging Face returned HTTP $status",
                )
            }

            val remaining = connection.contentLengthLong
            val total = if (remaining >= 0) resumeFrom + remaining else -1L
            var written = resumeFrom
            var lastUpdate = 0L

            connection.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(resumeFrom)
                    out.seek(resumeFrom)
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                            lastUpdate = now
                            set(model.id, DownloadStatus.Downloading(written, total))
                        }
                    }
                }
            }

            if (total >= 0) {
                if (written != total) {
                    throw IOException("The connection dropped mid-download — resume to continue")
                }
            } else if (written < model.sizeBytes * 9 / 10) {
                // No Content-Length to validate against. sizeBytes is only
                // approximate, so use a loose floor: catch a grossly truncated
                // body (clean EOF after a partial transfer) without false-
                // rejecting a complete download whose real size differs a little
                // from the catalog estimate. Otherwise a truncated file would be
                // renamed into place and wrongly count as a valid model.
                throw IOException("The connection dropped mid-download — resume to continue")
            }
        } finally {
            connection.disconnect()
        }
    }
}
