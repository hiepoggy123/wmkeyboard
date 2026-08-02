package com.wasimaster.wmkeyboard.core.tools

import android.content.Context
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.wasimaster.wmkeyboard.common.R as CommonR

/**
 * A tool network call that failed with something the user should read.
 *
 * [apiMessage] holds the provider's own words when the response carried any;
 * those are already in the provider's language and pass straight through.
 * Otherwise [messageRes] names our wording and [status] is the one argument it
 * takes. [ToolHttp.friendlyMessage] turns the pair into a line for a panel.
 */
class ToolHttpException(
    @StringRes val messageRes: Int,
    val status: Int = 0,
    val apiMessage: String? = null,
) : IOException(apiMessage ?: "HTTP $status")

/**
 * Tiny blocking HTTP helper shared by the network tool clients (translate,
 * KLIPY/GIPHY, Google search). Same HttpURLConnection approach as [WeatherClient];
 * always call on an IO dispatcher. On an HTTP error it tries to surface the
 * API's own error message (Google APIs return `{"error": {"message": …}}`)
 * so the panels can show something actionable instead of a bare failure.
 */
// Public (not internal) because the network clients in :feature:tools —
// AiClient, GiphyClient, KlipyClient — live one module up and share this
// plumbing. Still not a public API in spirit; nothing outside core.tools
// should call it.
object ToolHttp {

    /**
     * Browser-ish UA for every request: arbitrary image hosts surfaced by
     * Google image/GIF search often 403 the default Java agent, and the
     * APIs don't mind either way.
     */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"

    fun get(url: String, timeoutMs: Int = 10_000, headers: Map<String, String> = emptyMap()): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw httpFailure(status, body)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun postForm(url: String, form: Map<String, String>, timeoutMs: Int = 10_000): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val body = form.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw httpFailure(status, error)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun postJson(
        url: String,
        body: String,
        timeoutMs: Int = 60_000,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw httpFailure(status, error)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * POSTs [body], then hands each response line to [onLine] as it arrives —
     * for the streaming shapes the AI providers use (SSE, or the newline-
     * delimited JSON Ollama returns). [onConnected] fires once the response
     * headers are in, which is the moment the request stopped being "in
     * flight" and started being "the model is working on it".
     *
     * [onLine] returns whether to keep reading: `false` closes the connection
     * where it stands, so an abandoned request stops holding a socket (and, for
     * a metered provider, stops being billed for tokens nobody will see).
     * Throwing from it also aborts, which is how a mid-stream error event is
     * surfaced.
     */
    fun postJsonStream(
        url: String,
        body: String,
        timeoutMs: Int = 120_000,
        headers: Map<String, String> = emptyMap(),
        onConnected: () -> Unit = {},
        onLine: (String) -> Boolean,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw httpFailure(status, error)
            }
            onConnected()
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!onLine(line)) break
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Streams a URL into [target] (creating parent dirs); deletes the partial file on
     * failure. [maxBytes] aborts the transfer once exceeded rather than trusting a
     * (possibly absent or false) Content-Length header.
     */
    fun download(url: String, target: File, timeoutMs: Int = 20_000, maxBytes: Long = Long.MAX_VALUE) {
        target.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.instanceFollowRedirects = true
            val status = connection.responseCode
            if (status !in 200..299) throw httpFailure(status, null)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            throw ToolHttpException(R.string.core_tools_error_file_too_large)
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * A short, human-facing line for any failure a tool network call can throw.
     * Connectivity problems (no network, DNS, timeout, refused) get a plain
     * "you're offline"-style message instead of the raw
     * `Unable to resolve host "api.giphy.com"…` the OS produces. An HTTP error
     * shows the provider's own words when it sent any, and our wording for the
     * status when it did not.
     */
    fun friendlyMessage(context: Context, t: Throwable): String = when (t) {
        is ToolHttpException -> httpText(context, t)
        is UnknownHostException -> context.getString(CommonR.string.common_error_network)
        is SocketTimeoutException -> context.getString(CommonR.string.common_error_timeout)
        is ConnectException -> context.getString(R.string.core_tools_error_server_unreachable)
        else -> t.message?.takeIf { it.isNotBlank() }
            ?: context.getString(CommonR.string.common_error_generic)
    }

    /** The provider's own words, or our wording for the status it answered with. */
    private fun httpText(context: Context, t: ToolHttpException): String {
        t.apiMessage?.takeIf { it.isNotBlank() }?.let { return it }
        // A failure with no status of its own (the size cap) takes no argument.
        if (t.status <= 0) return context.getString(t.messageRes)
        return context.getString(t.messageRes, t.status)
    }

    /** The failure to throw for an HTTP status outside 200..299. */
    fun httpFailure(status: Int, body: String?): ToolHttpException =
        ToolHttpException(statusMessageRes(status), status, apiErrorText(body))

    /**
     * The provider's own error text, when the response body carried any.
     * Google APIs return `{"error": {"message": …}}`; anything else reads as
     * no message at all.
     */
    fun apiErrorText(body: String?): String? = body?.let {
        runCatching {
            Json.parseToJsonElement(it).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
    }?.takeIf { it.isNotBlank() }

    /** Our own wording for an HTTP status, for when the API sent none. */
    @StringRes
    fun statusMessageRes(status: Int): Int = when (status) {
        400 -> R.string.core_tools_error_http_bad_request
        401, 403 -> R.string.core_tools_error_http_key_rejected
        429 -> R.string.core_tools_error_http_rate_limit
        else -> R.string.core_tools_error_http_failed
    }
}
