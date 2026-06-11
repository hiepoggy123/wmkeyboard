package com.wasimaster.wmkeyboard.core.tools

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
internal object ToolHttp {

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

    /** Streams a URL into [target] (creating parent dirs); deletes the partial file on failure. */
    fun download(url: String, target: File, timeoutMs: Int = 20_000) {
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
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    internal fun apiErrorMessage(status: Int, body: String?): String {
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
