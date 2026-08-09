package com.wasimaster.wmkeyboard.core.fonts

import android.content.Context
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.content.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one emoji font the app will fetch by itself.
 *
 * Two things ship the same file. It is published as an `emoji_font` add-on, so
 * it shows up in the add-on catalogue with its licence and its preview beside
 * every other face; and it is named here, so the emoji page can offer it in
 * one tap without asking anyone to add a repository first. The add-on route is
 * the general answer and this is the shortcut to the one font almost everybody
 * who lands on that page actually wants.
 *
 * Why it exists at all: [com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice.NOTO]
 * asks Google's downloadable-font provider for "Noto Color Emoji", and a
 * downloadable font is requested by family name — there is no version in the
 * request and no way to ask for the current one. The provider serves whatever
 * build it has, which on a phone with a recent system emoji font is the older
 * of the two. Fetching the file directly is the only way to be sure which
 * build the user gets.
 */
object EmojiFontCatalog {

    /**
     * Where the file lives. The same URL the add-on entry points at, so the
     * two routes cannot drift into shipping different fonts.
     */
    const val NOTO_URL: String =
        "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/" +
            "fonts/emoji/NotoColorEmoji.ttf"

    /** Name the installed font is listed under. */
    const val NOTO_NAME: String = "Noto Color Emoji"

    const val NOTO_AUTHOR: String = "Google"

    /**
     * Rough transfer size, for the row that offers the download. Only used
     * before the server states a length, and to word the offer honestly —
     * this is a ten-megabyte file on whatever connection the user is on.
     */
    const val NOTO_APPROX_BYTES: Long = 10L * 1024 * 1024
}

/**
 * Fetches [EmojiFontCatalog]'s font and installs it into the [FontStore], so
 * it becomes selectable as an installed emoji face.
 *
 * One download at a time, one state flow for the UI, and no retry policy: this
 * only ever runs because someone pressed a button, so a failure belongs on
 * screen rather than in a queue.
 */
object EmojiFontDownload {

    sealed interface Status {
        data object Idle : Status

        /** [total] is 0 until the server states a length. */
        data class Downloading(val bytes: Long, val total: Long) : Status

        /** The store id of the installed face — what the setting points at. */
        data class Installed(val fontId: String, val name: String) : Status

        data class Failed(@StringRes val messageRes: Int) : Status
    }

    private const val USER_AGENT = "WMKeyboard emoji font downloader"
    private const val PROGRESS_INTERVAL_MS = 200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<Status>(Status.Idle)
    val state: StateFlow<Status> = _state.asStateFlow()

    /** True while a fetch is in flight, so the row can disable its button. */
    val running: Boolean get() = job?.isActive == true

    /**
     * Starts the download, or does nothing when one is already running.
     *
     * The caller points the setting at [Status.Installed.fontId] itself rather
     * than this doing it: the settings repository lives above this module, and
     * "put the font on disk" and "start using it" are separately useful.
     */
    @Synchronized
    fun start(context: Context) {
        if (running) return
        val app = context.applicationContext
        _state.value = Status.Downloading(0, EmojiFontCatalog.NOTO_APPROX_BYTES)
        job = scope.launch {
            _state.value = runCatching { fetchAndInstall(app) }
                .getOrElse { Status.Failed(R.string.core_content_font_error_read) }
        }
    }

    /** Clears a finished or failed run, so the row goes back to offering it. */
    @Synchronized
    fun reset() {
        if (running) return
        _state.value = Status.Idle
    }

    private fun fetchAndInstall(context: Context): Status {
        val connection = URL(EmojiFontCatalog.NOTO_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return Status.Failed(R.string.core_content_font_error_download)
            }
            val total = connection.contentLengthLong
                .takeIf { it > 0 } ?: EmojiFontCatalog.NOTO_APPROX_BYTES
            val store = FontStore.get(context)
            // Streamed straight into the importer, which is what enforces the
            // size ceiling and checks that the bytes really are a font before
            // any of them are registered.
            val result = connection.inputStream.buffered().reporting(total).use { stream ->
                FontFile.import(
                    input = stream,
                    store = store,
                    name = EmojiFontCatalog.NOTO_NAME,
                    author = EmojiFontCatalog.NOTO_AUTHOR,
                    emoji = true,
                )
            }
            return when (result) {
                is FontImportResult.Imported ->
                    Status.Installed(result.font.id, result.font.name)
                is FontImportResult.TooManyFonts ->
                    Status.Failed(R.string.core_content_font_error_too_many)
                is FontImportResult.NotAFont -> Status.Failed(result.messageRes)
                is FontImportResult.Failed -> Status.Failed(result.messageRes)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Wraps the body so the progress row moves while the bytes arrive. */
    private fun InputStream.reporting(total: Long): InputStream = object : FilterInputStream(this) {
        private var read = 0L
        private var lastPost = 0L

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            if (count > 0) {
                read += count
                val now = System.currentTimeMillis()
                if (now - lastPost >= PROGRESS_INTERVAL_MS) {
                    lastPost = now
                    _state.value = Status.Downloading(read, total)
                }
            }
            return count
        }
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
}
