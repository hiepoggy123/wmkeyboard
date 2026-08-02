package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.AiSettings
import com.wasimaster.wmkeyboard.tools.feature.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * One-shot chat completion against the AI tool's configured provider —
 * Anthropic, OpenAI, Gemini, or a self-hosted Ollama / LM Studio server
 * (OpenAI-compatible). Bring-your-own-key: keys and base URLs live in the
 * tool's settings; nothing is sent anywhere until the user runs an action.
 */
object AiClient {

    private val json = Json { ignoreUnknownKeys = true }

    /** Resolved connection details for one provider, from settings. */
    data class Config(
        val provider: AiProvider,
        val apiKey: String,
        val model: String,
        val baseUrl: String,
    )

    /**
     * Model each provider falls back to when its settings field is blank.
     * Kept in one place because the settings screen shows the same strings as
     * "Blank = …" hints — they must not drift apart.
     */
    object DefaultModels {
        const val ANTHROPIC = "claude-sonnet-5"
        const val OPENAI = "gpt-5.6-luna"
        const val GEMINI = "gemini-3.5-flash"
        const val OLLAMA = "qwen3"
        const val XAI = "grok-4.5"
        const val DEEPSEEK = "deepseek-v4-flash"
    }

    /**
     * Fixed addresses of the services that speak the OpenAI chat-completions
     * shape. [AiProvider.OPENAI_COMPATIBLE] is absent on purpose: its address is
     * the one the user supplies.
     */
    private object Endpoints {
        const val OPENAI = "https://api.openai.com/v1/chat/completions"
        const val XAI = "https://api.x.ai/v1/chat/completions"
        const val DEEPSEEK = "https://api.deepseek.com/v1/chat/completions"
    }

    /**
     * The chat-completions address for a provider whose request shape is
     * OpenAI's. The three self-hosted or user-supplied entries build it from the
     * address in settings; the rest are fixed.
     */
    internal fun openAiCompatibleUrl(config: Config): String = when (config.provider) {
        AiProvider.OPENAI -> Endpoints.OPENAI
        AiProvider.XAI -> Endpoints.XAI
        AiProvider.DEEPSEEK -> Endpoints.DEEPSEEK
        AiProvider.LM_STUDIO -> "${config.baseUrl.trimEnd('/')}/v1/chat/completions"
        // The user gives the address up to and including any version segment,
        // because those differ between services (/v1, /openai/v1, /api/v1).
        AiProvider.OPENAI_COMPATIBLE -> "${config.baseUrl.trimEnd('/')}/chat/completions"
        else -> error("${config.provider} does not use the OpenAI request shape")
    }

    fun config(settings: AiSettings): Config = when (settings.provider) {
        AiProvider.ANTHROPIC -> Config(
            AiProvider.ANTHROPIC, settings.anthropicKey,
            settings.anthropicModel.ifBlank { DefaultModels.ANTHROPIC }, "",
        )
        AiProvider.OPENAI -> Config(
            AiProvider.OPENAI, settings.openAiKey,
            settings.openAiModel.ifBlank { DefaultModels.OPENAI }, "",
        )
        AiProvider.GEMINI -> Config(
            AiProvider.GEMINI, settings.geminiKey,
            settings.geminiModel.ifBlank { DefaultModels.GEMINI }, "",
        )
        AiProvider.OLLAMA -> Config(
            AiProvider.OLLAMA, "",
            settings.ollamaModel.ifBlank { DefaultModels.OLLAMA },
            settings.ollamaUrl,
        )
        AiProvider.LM_STUDIO -> Config(
            AiProvider.LM_STUDIO, "",
            settings.lmStudioModel,
            settings.lmStudioUrl,
        )
        AiProvider.XAI -> Config(
            AiProvider.XAI, settings.xaiKey,
            settings.xaiModel.ifBlank { DefaultModels.XAI }, "",
        )
        AiProvider.DEEPSEEK -> Config(
            AiProvider.DEEPSEEK, settings.deepSeekKey,
            settings.deepSeekModel.ifBlank { DefaultModels.DEEPSEEK }, "",
        )
        AiProvider.OPENAI_COMPATIBLE -> Config(
            AiProvider.OPENAI_COMPATIBLE, settings.compatibleKey,
            settings.compatibleModel,
            settings.compatibleUrl,
        )
        AiProvider.ON_DEVICE -> Config(
            AiProvider.ON_DEVICE, "",
            settings.localModelId, "",
        )
    }

    /**
     * Substrings that mark a model as one that reasons before answering.
     * Matched against the lowercased model id — deliberately loose, since the
     * cost of a false positive is a slightly larger token ceiling and the cost
     * of a false negative is an answer that never arrives.
     */
    private val REASONING_MODEL_HINTS = listOf(
        "thinking", "reason", "-r1", "qwq", "magistral",
        "gpt-5", "o1-", "o3", "o4-", "qwen3", "qwen-3",
        "grok-4", "deepseek-v4",
    )

    /** Reasoning models get this much more room than the user's setting. */
    private const val REASONING_HEADROOM = 4
    private const val MAX_TOKENS_CEILING = 32_768

    /**
     * Whether the selected model is expected to reason before answering. A
     * guess from the model id — see [REASONING_MODEL_HINTS] — so callers must
     * treat a `false` as "probably not" rather than proof.
     */
    fun expectsReasoning(settings: AiSettings): Boolean {
        val model = config(settings).model.lowercase()
        return REASONING_MODEL_HINTS.any { it in model }
    }

    /**
     * The token ceiling to send for one request. A reasoning model spends most
     * of its budget on the think block before writing a word of the answer, so
     * the user's "max response length" — a number they picked thinking about
     * the *answer* — buys them nothing but truncated reasoning. Multiply it for
     * those models instead of making the user discover the problem.
     */
    fun effectiveMaxTokens(settings: AiSettings): Int {
        if (!expectsReasoning(settings)) return settings.maxTokens
        return (settings.maxTokens.toLong() * REASONING_HEADROOM)
            .coerceAtMost(MAX_TOKENS_CEILING.toLong())
            .toInt()
    }

    /** Whether the selected provider has what it needs to make a request. */
    fun isConfigured(settings: AiSettings): Boolean {
        val config = config(settings)
        return when (config.provider) {
            AiProvider.OLLAMA, AiProvider.LM_STUDIO -> config.baseUrl.isNotBlank()
            // A key is optional here: a gateway on the user's own network often
            // wants none. The model is not, because there is no sensible default
            // for a service we know nothing about.
            AiProvider.OPENAI_COMPATIBLE ->
                config.baseUrl.isNotBlank() && config.model.isNotBlank()
            AiProvider.ON_DEVICE -> config.model.isNotBlank()
            else -> config.apiKey.isNotBlank()
        }
    }

    /**
     * The cloud/server providers that are ready to use right now — the AI
     * panel's model picker only offers these. ON_DEVICE is excluded: its
     * choices are per-model and need file checks the caller owns.
     */
    fun configuredRemoteProviders(settings: AiSettings): List<AiProvider> =
        AiProvider.displayOrder.filter { provider ->
            when (provider) {
                AiProvider.ANTHROPIC -> settings.anthropicKey.isNotBlank()
                AiProvider.OPENAI -> settings.openAiKey.isNotBlank()
                AiProvider.GEMINI -> settings.geminiKey.isNotBlank()
                AiProvider.OLLAMA -> settings.ollamaUrl.isNotBlank()
                AiProvider.LM_STUDIO -> settings.lmStudioUrl.isNotBlank()
                AiProvider.XAI -> settings.xaiKey.isNotBlank()
                AiProvider.DEEPSEEK -> settings.deepSeekKey.isNotBlank()
                AiProvider.OPENAI_COMPATIBLE ->
                    settings.compatibleUrl.isNotBlank() && settings.compatibleModel.isNotBlank()
                AiProvider.ON_DEVICE -> false
            }
        }

    /**
     * Runs one system+user exchange as a *stream*, returning the assembled
     * text. [onPhase] reports how far the request has got and [onPartial] the
     * response so far, so the panel can show the answer forming instead of a
     * spinner. Both may be called from the calling (IO) thread many times.
     *
     * Reasoning that a provider sends on a side channel is folded into the
     * returned text wrapped in `<think>…</think>` — the same shape on-device
     * models emit inline — so [AiThinking], the panel's gray-out and the "show
     * reasoning" setting all keep working with no second code path.
     */
    fun completeStreaming(
        config: Config,
        system: String,
        user: String,
        maxTokens: Int,
        onPhase: (AiPhase) -> Unit,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean = { true },
    ): String = when (config.provider) {
        AiProvider.ANTHROPIC ->
            anthropicStream(config, system, user, maxTokens, onPhase, onPartial, isActive)
        AiProvider.GEMINI ->
            geminiStream(config, system, user, maxTokens, onPhase, onPartial, isActive)
        AiProvider.OLLAMA -> ollamaStream(config, system, user, onPhase, onPartial, isActive)
        AiProvider.OPENAI, AiProvider.LM_STUDIO, AiProvider.XAI,
        AiProvider.DEEPSEEK, AiProvider.OPENAI_COMPATIBLE,
        -> openAiCompatibleStream(
            openAiCompatibleUrl(config),
            config, system, user, maxTokens, onPhase, onPartial, isActive,
        )
        AiProvider.ON_DEVICE ->
            error("On-device models run locally, not over HTTP")
    }

    /** Runs one system+user exchange, returning the assistant's text. */
    fun complete(config: Config, system: String, user: String, maxTokens: Int): String =
        when (config.provider) {
            AiProvider.ANTHROPIC -> anthropic(config, system, user, maxTokens)
            AiProvider.GEMINI -> gemini(config, system, user, maxTokens)
            AiProvider.OLLAMA -> ollama(config, system, user)
            AiProvider.OPENAI, AiProvider.LM_STUDIO, AiProvider.XAI,
            AiProvider.DEEPSEEK, AiProvider.OPENAI_COMPATIBLE,
            -> openAiCompatible(openAiCompatibleUrl(config), config, system, user, maxTokens)
            // On-device inference needs a Context and model file; the IME
            // service routes to LocalLlmEngine before ever calling here.
            AiProvider.ON_DEVICE ->
                error("On-device models run locally, not over HTTP")
        }

    private fun anthropic(config: Config, system: String, user: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", user)
                })
            })
        }.toString()
        val response = ToolHttp.postJson(
            "https://api.anthropic.com/v1/messages",
            body,
            headers = mapOf(
                "x-api-key" to config.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
        )
        return parseAnthropic(response)
    }

    internal fun parseAnthropic(body: String): String =
        json.parseToJsonElement(body).jsonObject["content"]?.jsonArray
            ?.firstNotNullOfOrNull { block ->
                block.jsonObject.takeIf { it["type"]?.jsonPrimitive?.content == "text" }
                    ?.get("text")?.jsonPrimitive?.content
            }.orEmpty().trim()

    private fun openAiCompatible(
        url: String,
        config: Config,
        system: String,
        user: String,
        maxTokens: Int,
    ): String {
        val body = buildJsonObject {
            if (config.model.isNotBlank()) put("model", config.model)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val headers = if (config.apiKey.isNotBlank()) {
            mapOf("Authorization" to "Bearer ${config.apiKey}")
        } else {
            emptyMap()
        }
        return parseOpenAi(ToolHttp.postJson(url, body, headers = headers))
    }

    internal fun parseOpenAi(body: String): String =
        json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content.orEmpty().trim()

    private fun gemini(config: Config, system: String, user: String, maxTokens: Int): String {
        val body = buildJsonObject {
            putJsonObject("system_instruction") {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            }
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
                })
            })
            putJsonObject("generationConfig") { put("maxOutputTokens", maxTokens) }
        }.toString()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${config.model}:generateContent"
        val response = ToolHttp.postJson(
            url, body, headers = mapOf("x-goog-api-key" to config.apiKey),
        )
        return parseGemini(response)
    }

    internal fun parseGemini(body: String): String =
        json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("").orEmpty().trim()

    private fun ollama(config: Config, system: String, user: String): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        return parseOllama(
            ToolHttp.postJson("${config.baseUrl.trimEnd('/')}/api/chat", body, timeoutMs = 120_000),
        )
    }

    internal fun parseOllama(body: String): String =
        json.parseToJsonElement(body).jsonObject["message"]?.jsonObject
            ?.get("content")?.jsonPrimitive?.content.orEmpty().trim()

    // ---- streaming ----

    /**
     * Assembles a streamed response, wrapping reasoning that arrives on a
     * separate channel in `<think>…</think>`. See [completeStreaming] for why.
     */
    internal class StreamBuffer {
        private val text = StringBuilder()
        private var inThink = false

        fun reasoning(chunk: String) {
            if (chunk.isEmpty()) return
            if (!inThink) {
                text.append(THINK_OPEN)
                inThink = true
            }
            text.append(chunk)
        }

        fun answer(chunk: String) {
            if (chunk.isEmpty()) return
            if (inThink) {
                text.append(THINK_CLOSE)
                inThink = false
            }
            text.append(chunk)
        }

        /** The stream so far — an unclosed think block reads as "still thinking". */
        val partial: String get() = text.toString()

        val isEmpty: Boolean get() = text.isEmpty()

        /**
         * Final text, closing an unterminated think block so it parses. A
         * response that was *all* reasoning therefore strips to nothing, which
         * is what the caller reports as "spent its whole response reasoning".
         */
        fun finish(): String {
            if (inThink) {
                text.append(THINK_CLOSE)
                inThink = false
            }
            return text.toString()
        }

        private companion object {
            const val THINK_OPEN = "<think>"
            const val THINK_CLOSE = "</think>"
        }
    }

    /**
     * The JSON payload of one SSE line, or null for anything not worth parsing
     * — `event:`/`id:`/comment lines, the blank separators between events, and
     * the `[DONE]` sentinel OpenAI-compatible servers end with.
     */
    internal fun sseData(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        return payload.takeIf { it.isNotEmpty() && it != "[DONE]" }
    }

    /**
     * Runs a streaming request and returns the assembled text, falling back to
     * the provider's plain (non-streamed) response shape when nothing streamed.
     * A proxy or older server that ignores `stream: true` answers with one
     * ordinary JSON body, which would otherwise read as an empty response —
     * [fallback] parses the collected body instead of costing a second request.
     */
    private fun runStream(
        url: String,
        body: String,
        headers: Map<String, String>,
        timeoutMs: Int,
        onPhase: (AiPhase) -> Unit,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
        apply: (String, StreamBuffer) -> Unit,
        fallback: (String) -> String,
    ): String {
        val buffer = StreamBuffer()
        val collected = StringBuilder()
        onPhase(AiPhase.CONNECTING)
        ToolHttp.postJsonStream(
            url = url,
            body = body,
            timeoutMs = timeoutMs,
            headers = headers,
            onConnected = { onPhase(AiPhase.WAITING) },
        ) { line ->
            collected.append(line).append('\n')
            val before = buffer.partial
            apply(line, buffer)
            if (buffer.partial != before) onPartial(buffer.partial)
            isActive()
        }
        if (!buffer.isEmpty) return buffer.finish()
        return runCatching { fallback(collected.toString()) }.getOrDefault("")
    }

    private fun anthropicStream(
        config: Config,
        system: String,
        user: String,
        maxTokens: Int,
        onPhase: (AiPhase) -> Unit,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
    ): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("system", system)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", user)
                })
            })
        }.toString()
        return runStream(
            url = "https://api.anthropic.com/v1/messages",
            body = body,
            headers = mapOf(
                "x-api-key" to config.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
            timeoutMs = 120_000,
            onPhase = onPhase,
            onPartial = onPartial,
            isActive = isActive,
            apply = { line, buffer -> sseData(line)?.let { applyAnthropicEvent(it, buffer) } },
            fallback = ::parseAnthropic,
        )
    }

    /** Folds one Anthropic SSE payload into [buffer]; ignores lifecycle events. */
    internal fun applyAnthropicEvent(data: String, buffer: StreamBuffer) {
        val event = data.asJsonObject() ?: return
        when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "content_block_delta" -> {
                val delta = event["delta"]?.jsonObject ?: return
                when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> buffer.answer(delta.text("text"))
                    "thinking_delta" -> buffer.reasoning(delta.text("thinking"))
                }
            }
            // A failure mid-stream arrives as an event, not an HTTP status, so
            // it has to be raised from here or the answer silently truncates.
            "error" -> throw streamFailure(event["error"]?.jsonObject?.text("message"))
        }
    }

    private fun openAiCompatibleStream(
        url: String,
        config: Config,
        system: String,
        user: String,
        maxTokens: Int,
        onPhase: (AiPhase) -> Unit,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
    ): String {
        val body = buildJsonObject {
            if (config.model.isNotBlank()) put("model", config.model)
            put("max_tokens", maxTokens)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val headers = if (config.apiKey.isNotBlank()) {
            mapOf("Authorization" to "Bearer ${config.apiKey}")
        } else {
            emptyMap()
        }
        return runStream(
            url = url,
            body = body,
            headers = headers,
            timeoutMs = 120_000,
            onPhase = onPhase,
            onPartial = onPartial,
            isActive = isActive,
            apply = { line, buffer -> sseData(line)?.let { applyOpenAiEvent(it, buffer) } },
            fallback = ::parseOpenAi,
        )
    }

    /** Folds one OpenAI-compatible SSE payload into [buffer]. */
    internal fun applyOpenAiEvent(data: String, buffer: StreamBuffer) {
        val event = data.asJsonObject() ?: return
        event["error"]?.jsonObject?.let { error ->
            throw streamFailure(error.text("message"))
        }
        val delta = event["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("delta")?.jsonObject ?: return
        // Reasoning models behind an OpenAI-compatible server put the think
        // stream on a side field; vLLM and DeepSeek name it reasoning_content,
        // others just reasoning.
        buffer.reasoning(delta.text("reasoning_content").ifEmpty { delta.text("reasoning") })
        buffer.answer(delta.text("content"))
    }

    private fun geminiStream(
        config: Config,
        system: String,
        user: String,
        maxTokens: Int,
        onPhase: (AiPhase) -> Unit,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
    ): String {
        val body = buildJsonObject {
            putJsonObject("system_instruction") {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            }
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
                })
            })
            putJsonObject("generationConfig") { put("maxOutputTokens", maxTokens) }
        }.toString()
        return runStream(
            url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "${config.model}:streamGenerateContent?alt=sse",
            body = body,
            headers = mapOf("x-goog-api-key" to config.apiKey),
            timeoutMs = 120_000,
            onPhase = onPhase,
            onPartial = onPartial,
            isActive = isActive,
            apply = { line, buffer -> sseData(line)?.let { applyGeminiEvent(it, buffer) } },
            fallback = ::parseGeminiChunks,
        )
    }

    /** Folds one Gemini SSE payload into [buffer]. */
    internal fun applyGeminiEvent(data: String, buffer: StreamBuffer) {
        val event = data.asJsonObject() ?: return
        event["error"]?.jsonObject?.let { error ->
            throw streamFailure(error.text("message"))
        }
        val parts = event["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return
        for (element in parts) {
            val part = element.jsonObject
            val text = part.text("text")
            if (text.isEmpty()) continue
            // Gemini flags a reasoning part rather than sending it separately.
            if (part["thought"]?.jsonPrimitive?.booleanOrNull == true) {
                buffer.reasoning(text)
            } else {
                buffer.answer(text)
            }
        }
    }

    /**
     * A `streamGenerateContent` body that came back as one blob rather than an
     * event stream: a JSON array of the same chunks (what you get when a proxy
     * drops the `alt=sse` query). Concatenates their text; a lone object — the
     * plain `generateContent` shape — parses through [parseGemini].
     */
    internal fun parseGeminiChunks(body: String): String {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return ""
        val chunks = root as? JsonArray ?: return parseGemini(body)
        return chunks.joinToString("") { chunk ->
            val parts = chunk.jsonObject["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            parts?.joinToString("") { it.jsonObject.text("text") }.orEmpty()
        }.trim()
    }

    private fun ollamaStream(
        config: Config,
        system: String,
        user: String,
        onPhase: (AiPhase) -> Unit,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
    ): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        return runStream(
            url = "${config.baseUrl.trimEnd('/')}/api/chat",
            body = body,
            headers = emptyMap(),
            timeoutMs = 120_000,
            onPhase = onPhase,
            onPartial = onPartial,
            isActive = isActive,
            // Ollama streams newline-delimited JSON, not SSE — every line is a
            // whole object, with no `data:` prefix to strip.
            apply = ::applyOllamaEvent,
            fallback = ::parseOllama,
        )
    }

    /** Folds one line of Ollama's newline-delimited JSON stream into [buffer]. */
    internal fun applyOllamaEvent(line: String, buffer: StreamBuffer) {
        val event = line.asJsonObject() ?: return
        event["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
            throw streamFailure(it)
        }
        val message = event["message"]?.jsonObject ?: return
        buffer.reasoning(message.text("thinking"))
        buffer.answer(message.text("content"))
    }

    /**
     * The failure to raise for a provider error that arrives inside the stream.
     * The provider's own words pass through when it sent any, exactly as an
     * HTTP error body does; our own wording stands in when it sent none.
     * [ToolHttp.friendlyMessage] renders either one for the panel.
     */
    private fun streamFailure(apiMessage: String?): ToolHttpException =
        ToolHttpException(
            R.string.ftools_ai_stream_error,
            apiMessage = apiMessage?.takeIf { it.isNotBlank() },
        )

    /**
     * Parses one stream line, tolerating anything that isn't a JSON object —
     * keep-alive comments, partially flushed lines and provider-specific noise
     * are normal in a stream and must not abort a response mid-flight.
     */
    private fun String.asJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull()

    /** A string member, or "" when absent, null, or not a string. */
    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
}
