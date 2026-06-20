package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
    }

    fun config(settings: KeyboardSettings): Config = when (settings.aiProvider) {
        AiProvider.ANTHROPIC -> Config(
            AiProvider.ANTHROPIC, settings.aiAnthropicKey,
            settings.aiAnthropicModel.ifBlank { DefaultModels.ANTHROPIC }, "",
        )
        AiProvider.OPENAI -> Config(
            AiProvider.OPENAI, settings.aiOpenAiKey,
            settings.aiOpenAiModel.ifBlank { DefaultModels.OPENAI }, "",
        )
        AiProvider.GEMINI -> Config(
            AiProvider.GEMINI, settings.aiGeminiKey,
            settings.aiGeminiModel.ifBlank { DefaultModels.GEMINI }, "",
        )
        AiProvider.OLLAMA -> Config(
            AiProvider.OLLAMA, "",
            settings.aiOllamaModel.ifBlank { DefaultModels.OLLAMA },
            settings.aiOllamaUrl,
        )
        AiProvider.LM_STUDIO -> Config(
            AiProvider.LM_STUDIO, "",
            settings.aiLmStudioModel,
            settings.aiLmStudioUrl,
        )
        AiProvider.ON_DEVICE -> Config(
            AiProvider.ON_DEVICE, "",
            settings.aiLocalModelId, "",
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
    )

    /** Reasoning models get this much more room than the user's setting. */
    private const val REASONING_HEADROOM = 4
    private const val MAX_TOKENS_CEILING = 32_768

    /**
     * The token ceiling to send for one request. A reasoning model spends most
     * of its budget on the think block before writing a word of the answer, so
     * the user's "max response length" — a number they picked thinking about
     * the *answer* — buys them nothing but truncated reasoning. Multiply it for
     * those models instead of making the user discover the problem.
     */
    fun effectiveMaxTokens(settings: KeyboardSettings): Int {
        val model = config(settings).model.lowercase()
        if (REASONING_MODEL_HINTS.none { it in model }) return settings.aiMaxTokens
        return (settings.aiMaxTokens.toLong() * REASONING_HEADROOM)
            .coerceAtMost(MAX_TOKENS_CEILING.toLong())
            .toInt()
    }

    /** Whether the selected provider has what it needs to make a request. */
    fun isConfigured(settings: KeyboardSettings): Boolean {
        val config = config(settings)
        return when (config.provider) {
            AiProvider.OLLAMA, AiProvider.LM_STUDIO -> config.baseUrl.isNotBlank()
            AiProvider.ON_DEVICE -> config.model.isNotBlank()
            else -> config.apiKey.isNotBlank()
        }
    }

    /**
     * The cloud/server providers that are ready to use right now — the AI
     * panel's model picker only offers these. ON_DEVICE is excluded: its
     * choices are per-model and need file checks the caller owns.
     */
    fun configuredRemoteProviders(settings: KeyboardSettings): List<AiProvider> =
        AiProvider.entries.filter { provider ->
            when (provider) {
                AiProvider.ANTHROPIC -> settings.aiAnthropicKey.isNotBlank()
                AiProvider.OPENAI -> settings.aiOpenAiKey.isNotBlank()
                AiProvider.GEMINI -> settings.aiGeminiKey.isNotBlank()
                AiProvider.OLLAMA -> settings.aiOllamaUrl.isNotBlank()
                AiProvider.LM_STUDIO -> settings.aiLmStudioUrl.isNotBlank()
                AiProvider.ON_DEVICE -> false
            }
        }

    /** Runs one system+user exchange, returning the assistant's text. */
    fun complete(config: Config, system: String, user: String, maxTokens: Int): String =
        when (config.provider) {
            AiProvider.ANTHROPIC -> anthropic(config, system, user, maxTokens)
            AiProvider.OPENAI -> openAiCompatible(
                "https://api.openai.com/v1/chat/completions", config, system, user, maxTokens,
            )
            AiProvider.GEMINI -> gemini(config, system, user, maxTokens)
            AiProvider.OLLAMA -> ollama(config, system, user)
            AiProvider.LM_STUDIO -> openAiCompatible(
                "${config.baseUrl.trimEnd('/')}/v1/chat/completions", config, system, user, maxTokens,
            )
            // On-device inference needs a Context and model file; the IME
            // service routes to LocalLlmEngine before ever calling here.
            AiProvider.ON_DEVICE ->
                throw IllegalStateException("On-device models run locally, not over HTTP")
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
}
