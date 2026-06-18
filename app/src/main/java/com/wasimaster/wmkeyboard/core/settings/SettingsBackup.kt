package com.wasimaster.wmkeyboard.core.settings

import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Whole-settings backup: every DataStore preference, written out as JSON.
 *
 * Deliberately generic rather than a field-by-field mapping of
 * [KeyboardSettings] — the settings list grows most weeks, and a codec that
 * had to be edited in step with it would silently start dropping whatever
 * the author forgot to add. Walking the raw preference map means a new
 * setting is backed up the day it lands.
 *
 * The type of each value has to be carried explicitly: DataStore keys are
 * typed, JSON numbers are not, and restoring an Int as a Long would make
 * the setting unreadable.
 *
 * Not included: themes, snippets and imported word lists, which are
 * separate files with their own import flows, and the learned dictionary,
 * which is personal rather than configuration.
 */
object SettingsBackup {

    const val FORMAT = "wmkeyboard-settings"
    const val VERSION = 1
    const val FILE_EXTENSION = "wmsettings.json"
    const val MIME_TYPE = "application/json"

    /**
     * Credentials, kept out of an export unless explicitly asked for. A
     * settings file is the kind of thing people mail to themselves or drop
     * in a shared folder, and these grant real spend on the user's account.
     */
    val SECRET_KEYS = setOf(
        "translate_api_key",
        "klipy_api_key",
        "brave_api_key",
        "giphy_api_key",
        "ai_anthropic_key",
        "ai_openai_key",
        "ai_gemini_key",
    )

    private val json = Json { prettyPrint = true }
    private val parser = Json { ignoreUnknownKeys = true }

    /** A decoded backup, ready to be written into DataStore. */
    data class Parsed(
        val appVersion: Int,
        val entries: List<Entry>,
        /** Keys skipped because their type tag was not recognized. */
        val skipped: Int,
    ) {
        val containsSecrets: Boolean get() = entries.any { it.name in SECRET_KEYS }
    }

    data class Entry(val name: String, val type: String, val value: Any)

    fun encode(
        prefs: Preferences,
        includeSecrets: Boolean,
        appVersion: Int,
        appVersionName: String,
    ): String {
        val values = buildJsonObject {
            for ((key, value) in prefs.asMap().entries.sortedBy { it.key.name }) {
                if (!includeSecrets && key.name in SECRET_KEYS) continue
                val encoded = encodeValue(value) ?: continue
                put(key.name, encoded)
            }
        }
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("appVersion", appVersion)
            put("appVersionName", appVersionName)
            put("settings", values)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun encodeValue(value: Any): JsonObject? {
        val (type, encoded) = when (value) {
            is Boolean -> "boolean" to JsonPrimitive(value)
            is Int -> "int" to JsonPrimitive(value)
            is Long -> "long" to JsonPrimitive(value)
            is Float -> "float" to JsonPrimitive(value)
            is Double -> "double" to JsonPrimitive(value)
            is String -> "string" to JsonPrimitive(value)
            is Set<*> -> "stringSet" to buildJsonArray {
                for (item in value) add(JsonPrimitive(item as? String ?: return null))
            }
            else -> return null
        }
        return buildJsonObject {
            put("type", type)
            put("value", encoded)
        }
    }

    /**
     * Parses [text], or returns null when it is not a settings backup at
     * all. Individual entries that fail to parse are counted in
     * [Parsed.skipped] rather than failing the whole file, so one bad line
     * does not cost the user the other two hundred settings.
     */
    fun decode(text: String): Parsed? {
        val root = runCatching { parser.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT) return null
        val settings = runCatching { root.getValue("settings").jsonObject }.getOrNull() ?: return null
        val entries = ArrayList<Entry>()
        var skipped = 0
        for ((name, element) in settings) {
            val parsed = runCatching { decodeEntry(name, element.jsonObject) }.getOrNull()
            if (parsed == null) skipped++ else entries.add(parsed)
        }
        return Parsed(
            appVersion = root["appVersion"]?.jsonPrimitive?.intOrNull ?: 0,
            entries = entries,
            skipped = skipped,
        )
    }

    private fun decodeEntry(name: String, obj: JsonObject): Entry? {
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val raw = obj["value"] ?: return null
        val value: Any = when (type) {
            "boolean" -> raw.jsonPrimitive.booleanOrNull ?: return null
            "int" -> raw.jsonPrimitive.intOrNull ?: return null
            "long" -> raw.jsonPrimitive.longOrNull ?: return null
            "float" -> raw.jsonPrimitive.floatOrNull ?: return null
            "double" -> raw.jsonPrimitive.doubleOrNull ?: return null
            "string" -> raw.jsonPrimitive.contentOrNull ?: return null
            "stringSet" -> raw.jsonArray.map { it.jsonPrimitive.contentOrNull ?: return null }.toSet()
            else -> return null
        }
        return Entry(name, type, value)
    }
}
