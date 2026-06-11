package com.wasimaster.wmkeyboard.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A finished translation plus the language the source was detected as. */
data class Translation(
    val text: String,
    /** ISO 639-1 code of the detected source language, or "" when unknown. */
    val detectedSource: String,
)

/**
 * Translation client with two providers behind one call:
 *  - no API key: Google's free public web endpoint (translate.googleapis.com),
 *    the same one the translate extensions use — fine for personal keyboard
 *    volumes, not an SLA;
 *  - with a key: the official Cloud Translation v2 API.
 * Source language is always auto-detected; both providers report what they
 * detected so the panel can show "Bengali → English".
 */
object TranslateClient {

    /** Longest text we send; keyboard fields beyond this get truncated. */
    const val MAX_CHARS = 2500

    /** The languages offered by the target-language picker. */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "bn" to "Bengali",
        "ar" to "Arabic",
        "de" to "German",
        "es" to "Spanish",
        "fa" to "Persian",
        "fr" to "French",
        "gu" to "Gujarati",
        "hi" to "Hindi",
        "id" to "Indonesian",
        "it" to "Italian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ml" to "Malayalam",
        "mr" to "Marathi",
        "ms" to "Malay",
        "ne" to "Nepali",
        "nl" to "Dutch",
        "pa" to "Punjabi",
        "pl" to "Polish",
        "pt" to "Portuguese",
        "ro" to "Romanian",
        "ru" to "Russian",
        "sv" to "Swedish",
        "ta" to "Tamil",
        "te" to "Telugu",
        "th" to "Thai",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "ur" to "Urdu",
        "vi" to "Vietnamese",
        "zh-CN" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
    )

    fun languageName(code: String): String =
        languages.firstOrNull { it.first.equals(code, ignoreCase = true) }?.second ?: code

    /** Blocking; call on an IO dispatcher. Throws on any failure. */
    fun translate(text: String, targetLang: String, apiKey: String): Translation {
        val trimmed = text.take(MAX_CHARS)
        return if (apiKey.isBlank()) translateFree(trimmed, targetLang)
        else translateOfficial(trimmed, targetLang, apiKey)
    }

    private fun translateFree(text: String, targetLang: String): Translation {
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=auto&tl=${ToolHttp.encode(targetLang)}" +
            "&dt=t&ie=UTF-8&oe=UTF-8&q=${ToolHttp.encode(text)}"
        return parseFree(ToolHttp.get(url))
    }

    private fun translateOfficial(text: String, targetLang: String, apiKey: String): Translation {
        val body = ToolHttp.postForm(
            "https://translation.googleapis.com/language/translate/v2?key=${ToolHttp.encode(apiKey)}",
            mapOf("q" to text, "target" to targetLang, "format" to "text"),
        )
        return parseOfficial(body)
    }

    /**
     * The free endpoint returns a bare nested array:
     * `[[["Hello","হ্যালো",…], ["…","…",…]], null, "bn", …]` — element 0 is
     * the list of translated segments, element 2 the detected language.
     */
    internal fun parseFree(body: String): Translation {
        val root = Json.parseToJsonElement(body).jsonArray
        val translated = root[0].jsonArray.joinToString("") { segment ->
            segment.jsonArray[0].jsonPrimitive.content
        }
        val detected = root.getOrNull(2)?.jsonPrimitive?.content.orEmpty()
        return Translation(translated, detected)
    }

    internal fun parseOfficial(body: String): Translation {
        val first = Json.parseToJsonElement(body).jsonObject
            .getValue("data").jsonObject
            .getValue("translations").jsonArray[0].jsonObject
        return Translation(
            text = first.getValue("translatedText").jsonPrimitive.content,
            detectedSource = first["detectedSourceLanguage"]?.jsonPrimitive?.content.orEmpty(),
        )
    }
}
