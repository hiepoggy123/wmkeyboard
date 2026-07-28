package com.wasimaster.wmkeyboard.core.tools

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
                throw IOException(apiErrorMessage(status, body))
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
                throw IOException(apiErrorMessage(status, error))
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
                throw IOException(apiErrorMessage(status, error))
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
                throw IOException(apiErrorMessage(status, error))
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
            if (status !in 200..299) throw IOException(apiErrorMessage(status, null))
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("File exceeds the maximum allowed size")
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
     * `Unable to resolve host "api.giphy.com"…` the OS produces; HTTP errors
     * already carry [apiErrorMessage] text, so those pass through.
     */
    fun friendlyMessage(t: Throwable): String = when (t) {
        is UnknownHostException ->
            "No internet connection. Check your network and try again."
        is SocketTimeoutException ->
            "The connection timed out. Check your network and try again."
        is ConnectException ->
            "Couldn't reach the server. Check your network and try again."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }

    fun apiErrorMessage(status: Int, body: String?): String {
        val fromApi = body?.let {
            runCatching {
                Json.parseToJsonElement(it).jsonObject["error"]?.jsonObject
                    ?.get("message")?.jsonPrimitive?.content
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        return fromApi ?: when (status) {
            400 -> "Bad request (HTTP 400) — check the API key setup"
            401, 403 -> "The API key was rejected (HTTP $status)"
            429 -> "Rate limit or daily quota exceeded (HTTP 429)"
            else -> "Request failed (HTTP $status)"
        }
    }
}
