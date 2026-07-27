package com.wasimaster.wmkeyboard.core.addons

import android.content.Context
import android.os.StatFs
import com.wasimaster.wmkeyboard.core.tools.ToolHttp
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext

/**
 * Fetching repository manifests and installing addons from them.
 *
 * Process singleton with its own IO scope and a [StateFlow] of per-addon
 * status, the same shape as `WordlistDownloadManager` and
 * `LocalLlmDownloadManager` — the settings UI collects [states] so a download
 * survives rotation and navigating away, and only one install runs at a time.
 *
 * The transfer borrows Range/`.part` resume from the model downloader, because
 * a sticker pack can be tens of megabytes on a phone connection. Verification
 * is a separate pass over the finished `.part` rather than a digest fed during
 * transfer: resuming would otherwise have to re-hash the existing prefix
 * anyway, and one extra sequential read of a file this size costs nothing next
 * to the download that produced it.
 */
object AddonDownloadManager {

    sealed interface AddonStatus {
        data object NotInstalled : AddonStatus

        /** [totalBytes] is 0 when neither the manifest nor the server said. */
        data class Downloading(val bytes: Long, val totalBytes: Long) : AddonStatus

        /** Transfer done; hashing before anything touches the importer. */
        data object Verifying : AddonStatus

        /** Handing the payload to the importer that owns this type. */
        data object Installing : AddonStatus

        data class Installed(val version: String) : AddonStatus

        data class UpdateAvailable(val installed: String, val latest: String) : AddonStatus

        data class Failed(val reason: FailReason, val message: String) : AddonStatus
    }

    enum class FailReason {
        /** Offline, DNS, timeout, or a non-2xx response. */
        NETWORK,

        /** Not enough free space for the payload. */
        NO_SPACE,

        /** The payload's SHA-256 did not match the one the manifest declared. */
        CHECKSUM,

        /** The importer refused it: wrong format, unsupported language, empty. */
        REJECTED,

        /** This build predates the addon's `minAppVersion`. */
        APP_TOO_OLD,

        OTHER,
    }

    private const val PROGRESS_INTERVAL_MS = 250L
    private const val BUFFER_BYTES = 256 * 1024

    /** Headroom over the payload size, so an install never fills the disk. */
    private const val SPACE_MARGIN_BYTES = 32L * 1024 * 1024

    /**
     * Ceiling on a *preview* download, under every type's install cap.
     *
     * A preview is a courtesy — pulling 60 MB of sticker pack to show a grid
     * the user may glance at and leave isn't one. Past this the detail page
     * says so and offers Install, which has the real cap.
     */
    private const val PREVIEW_MAX_BYTES = 12L * 1024 * 1024

    /** A licence file longer than this is not a licence file. */
    private const val MAX_TEXT_BYTES = 256L * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeKey: String? = null

    private val _states = MutableStateFlow<Map<String, AddonStatus>>(emptyMap())

    /** `"<repoId>/<addonId>"` -> status, for every addon the UI has looked at. */
    val states: StateFlow<Map<String, AddonStatus>> = _states.asStateFlow()

    val isBusy: Boolean get() = activeJob?.isActive == true

    // ---- manifests -----------------------------------------------------

    /**
     * Fetches a repository's manifest and caches it, returning the parsed form.
     *
     * Blocking; call on IO. A failure returns null and leaves the previous
     * cached manifest in place — a repository that is briefly unreachable
     * should keep showing what it had, not empty itself out.
     */
    fun fetchManifest(store: AddonStore, ref: AddonRepoRef, cacheDir: File): AddonRepoManifest? =
        fetchManifest(ref.manifestUrl, cacheDir, store)

    /**
     * Fetches a manifest by URL, optionally caching it against an already-added
     * repository.
     *
     * The store-less form is what a deep link uses: following a link has to be
     * able to *show* an addon without the link having quietly added its
     * repository to the user's list. Adding it is the user's decision, taken
     * when they install.
     */
    fun fetchManifest(
        manifestUrl: String,
        cacheDir: File,
        store: AddonStore? = null,
    ): AddonRepoManifest? {
        val temp = File(cacheDir, "manifest_${System.nanoTime()}.json")
        return try {
            ToolHttp.download(
                url = manifestUrl,
                target = temp,
                maxBytes = AddonRepoCodec.MAX_MANIFEST_BYTES,
            )
            val text = temp.readText()
            val manifest = AddonRepoCodec.decode(text) ?: return null
            store?.cacheManifest(manifestUrl, text)
            manifest
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    /** The cached manifest for [ref], or null if it has never been fetched. */
    fun cachedManifest(ref: AddonRepoRef): AddonRepoManifest? =
        ref.cachedManifest.takeIf { it.isNotBlank() }?.let { AddonRepoCodec.decode(it) }

    // ---- fetching without installing -------------------------------------

    /**
     * Downloads an addon's payload into [cacheDir] without installing it, for
     * the preview panel. Blocking; call on IO.
     *
     * Deliberately separate from [install]: it touches neither [states] nor the
     * single-install lock, so looking at what is in a snippet pack never
     * interferes with a download in flight, and never leaves an addon looking
     * half-installed if the user backs out.
     */
    fun fetchPayload(manifestUrl: String, entry: AddonEntry, cacheDir: File): File? {
        val url = AddonRepoCodec.resolveAsset(manifestUrl, entry.path) ?: return null
        val target = File(previewDir(cacheDir), sanitise("${entry.type.name}_${entry.id}"))
        // A preview the user already opened this session is on disk; refetching
        // tens of megabytes of sticker pack to show the same grid twice is
        // exactly the sort of thing a metered connection notices.
        if (target.isFile && target.length() > 0) return target
        return try {
            ToolHttp.download(url, target, maxBytes = minOf(entry.type.maxBytes, PREVIEW_MAX_BYTES))
            target.takeIf { it.length() > 0 }
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    /** Fetches a plain-text asset (a licence file) for display. */
    fun fetchText(manifestUrl: String, path: String, cacheDir: File): String? {
        val url = AddonRepoCodec.resolveAsset(manifestUrl, path) ?: return null
        val temp = File(cacheDir, "addon_text_${System.nanoTime()}.txt")
        return try {
            ToolHttp.download(url, temp, maxBytes = MAX_TEXT_BYTES)
            temp.readText().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    // ---- status --------------------------------------------------------

    /**
     * Recomputes the status of every addon in [manifest] against what the store
     * says is installed. Call when the Addons screen appears or after a
     * refresh; an install in flight keeps its live status.
     */
    fun refresh(store: AddonStore, repoId: String, manifest: AddonRepoManifest) {
        val installed = store.installed()
        _states.update { current ->
            current + manifest.addons.associate { entry ->
                val key = entry.key(repoId)
                key to when {
                    key == activeKey && isBusy -> current[key] ?: AddonStatus.NotInstalled
                    else -> statusFor(installed[key], entry)
                }
            }
        }
    }

    private fun statusFor(record: InstalledAddon?, entry: AddonEntry): AddonStatus = when {
        record == null -> AddonStatus.NotInstalled
        Semver.isNewer(entry.version, record.version) ->
            AddonStatus.UpdateAvailable(record.version, entry.version)
        else -> AddonStatus.Installed(record.version)
    }

    // ---- install -------------------------------------------------------

    /**
     * Downloads [entry], verifies it if the manifest said what to expect, and
     * hands it to its importer. Re-running this on an installed addon is how an
     * update is applied.
     */
    fun install(
        context: Context,
        store: AddonStore,
        manifestUrl: String,
        repo: AddonRepoInfo,
        entry: AddonEntry,
        appVersionCode: Int,
    ) {
        if (isBusy) return
        val key = entry.key(repo.id)

        if (entry.minAppVersion != null && entry.minAppVersion > appVersionCode) {
            set(key, AddonStatus.Failed(FailReason.APP_TOO_OLD, "Needs a newer version of the app"))
            return
        }
        val url = AddonRepoCodec.resolveAsset(manifestUrl, entry.path)
        if (url == null) {
            set(key, AddonStatus.Failed(FailReason.REJECTED, "The download link isn't a valid https URL"))
            return
        }

        activeKey = key
        set(key, AddonStatus.Downloading(0, entry.sizeBytes ?: 0L))

        val appContext = context.applicationContext
        activeJob = scope.launch {
            val part = File(stagingDir(appContext), "${sanitise(key)}.part")
            try {
                requireSpace(appContext, entry)
                transfer(url, part, entry, key)

                currentCoroutineContext().ensureActive()
                set(key, AddonStatus.Verifying)
                verify(part, entry)

                currentCoroutineContext().ensureActive()
                set(key, AddonStatus.Installing)

                // Updating means replacing, not accumulating. Every importer
                // mints a fresh local id, so without this an update would leave
                // the previous version behind as a second theme, a second pack,
                // a second copy of the dictionary. Removed only once the new
                // payload is downloaded and verified, so a failed update leaves
                // the working version in place.
                val previous = store.installed(key)
                if (previous != null) {
                    AddonInstaller.uninstall(appContext, previous)
                }

                val outcome = AddonInstaller.install(appContext, entry, part)

                when (outcome) {
                    is AddonInstaller.Outcome.Installed -> {
                        store.markInstalled(
                            key,
                            InstalledAddon(
                                version = entry.version,
                                type = entry.type,
                                localRef = outcome.localRef,
                                installedAt = System.currentTimeMillis(),
                                name = entry.name,
                                repoName = repo.name,
                            ),
                        )
                        set(key, AddonStatus.Installed(entry.version))
                    }
                    is AddonInstaller.Outcome.Rejected -> {
                        // The old version was already removed above, so the
                        // record has to go too — claiming it is still installed
                        // would offer an Update for something that is gone.
                        if (previous != null) store.markUninstalled(key)
                        set(key, AddonStatus.Failed(FailReason.REJECTED, outcome.message))
                    }
                }
            } catch (e: CancellationException) {
                part.delete()
                set(key, statusFor(store.installed(key), entry))
                throw e
            } catch (e: FailedException) {
                part.delete()
                set(key, AddonStatus.Failed(e.reason, e.message.orEmpty()))
            } catch (e: Exception) {
                part.delete()
                set(key, AddonStatus.Failed(FailReason.NETWORK, ToolHttp.friendlyMessage(e)))
            } finally {
                part.delete()
                activeKey = null
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
    }

    /**
     * Reverses an install and forgets it. The addon goes back to being offered
     * in the list, which is the point: uninstall should leave no trace beyond
     * what the user themselves added afterwards.
     */
    suspend fun uninstall(context: Context, store: AddonStore, key: String, entry: AddonEntry?) {
        val record = store.installed(key) ?: return
        withContext(Dispatchers.IO) {
            AddonInstaller.uninstall(context.applicationContext, record)
        }
        store.markUninstalled(key)
        set(key, entry?.let { statusFor(null, it) } ?: AddonStatus.NotInstalled)
    }

    // ---- transfer ------------------------------------------------------

    private suspend fun transfer(url: String, part: File, entry: AddonEntry, key: String) {
        part.parentFile?.mkdirs()
        val resumeFrom = if (part.exists()) part.length() else 0L
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")

            val status = connection.responseCode
            // A server that ignored the Range header sends 200 with the whole
            // file, so the partial we have is worthless and we start over.
            val appending = status == HttpURLConnection.HTTP_PARTIAL
            if (status != HttpURLConnection.HTTP_OK && !appending) {
                throw FailedException(FailReason.NETWORK, "The server returned HTTP $status")
            }
            if (!appending && resumeFrom > 0) part.delete()

            val already = if (appending) resumeFrom else 0L
            val reported = connection.contentLengthLong.takeIf { it > 0 } ?: 0L
            val total = when {
                reported > 0 -> already + reported
                else -> entry.sizeBytes ?: 0L
            }
            val cap = entry.type.maxBytes

            connection.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { output ->
                    output.setLength(already)
                    output.seek(already)
                    val buffer = ByteArray(BUFFER_BYTES)
                    var written = already
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (cap > 0 && written > cap) {
                            throw FailedException(
                                FailReason.REJECTED,
                                "This addon is larger than the ${cap / (1024 * 1024)} MB limit for its type",
                            )
                        }
                        output.write(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            set(key, AddonStatus.Downloading(written, total))
                        }
                    }
                    set(key, AddonStatus.Downloading(written, maxOf(total, written)))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Checks the payload against the manifest's `sha256`, when there is one.
     *
     * There usually will be, but the field is optional by design: a publisher
     * writing their first manifest by hand shouldn't have to run a hashing tool
     * before anyone can install their theme. An unverified addon installs
     * normally; the UI is what says so.
     */
    private fun verify(part: File, entry: AddonEntry) {
        val expected = entry.sha256?.lowercase() ?: return
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            throw FailedException(
                FailReason.CHECKSUM,
                "The downloaded file doesn't match the checksum in the manifest",
            )
        }
    }

    private fun requireSpace(context: Context, entry: AddonEntry) {
        val needed = (entry.sizeBytes ?: 0L) + SPACE_MARGIN_BYTES
        val available = runCatching {
            StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
        if (available < needed) {
            throw FailedException(FailReason.NO_SPACE, "Not enough free space to install this")
        }
    }

    private fun stagingDir(context: Context): File =
        File(context.cacheDir, "addon_downloads").apply { mkdirs() }

    private fun previewDir(cacheDir: File): File =
        File(cacheDir, "addon_previews").apply { mkdirs() }

    /** Install keys contain a `/`; file names can't. */
    private fun sanitise(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun set(key: String, status: AddonStatus) {
        _states.update { it + (key to status) }
    }

    private class FailedException(val reason: FailReason, message: String) : IOException(message)
}
