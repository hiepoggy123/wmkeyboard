package com.wasimaster.wmkeyboard.core.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
     *
     * The same set is what [LockedSettings] refuses to copy into
     * device-protected storage, where a credential would sit outside the
     * user's own encryption. Add new credentials here and both follow.
     */
    val SECRET_KEYS = setOf(
        "translate_api_key",
        "klipy_api_key",
        "brave_api_key",
        "giphy_api_key",
        "ai_anthropic_key",
        "ai_openai_key",
        "ai_gemini_key",
        "hf_token",
        "photo_unsplash_key",
        "photo_pexels_key",
    )

    /**
     * Custom themes are stored as one JSON string preference. The full-config
     * bundle ([ConfigBackup]) carries them as their own section so they can be
     * toggled independently, so it excludes these keys from the settings
     * section to avoid exporting them twice. The standalone settings backup
     * leaves them in, unchanged.
     */
    val THEME_KEYS = setOf("custom_themes")

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
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("appVersion", appVersion)
            put("appVersionName", appVersionName)
            put("settings", encodeSettings(prefs, includeSecrets))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * The typed `{ key: { type, value } }` map on its own, without the backup
     * envelope. Shared by [encode] and by [ConfigBackup], which nests it as
     * one section of a larger bundle. [exclude] drops keys handled elsewhere.
     */
    fun encodeSettings(
        prefs: Preferences,
        includeSecrets: Boolean,
        exclude: Set<String> = emptySet(),
    ): JsonObject = buildJsonObject {
        for ((key, value) in prefs.asMap().entries.sortedBy { it.key.name }) {
            if (!includeSecrets && key.name in SECRET_KEYS) continue
            if (key.name in exclude) continue
            val encoded = encodeValue(value) ?: continue
            put(key.name, encoded)
        }
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
        // `as? JsonPrimitive` instead of `.jsonPrimitive`: the latter throws on
        // a JsonObject/JsonArray value, which would break decode()'s null-on-
        // bad-input contract and crash the import path on a crafted file.
        if ((root["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) return null
        val settings = runCatching { root.getValue("settings").jsonObject }.getOrNull() ?: return null
        val (entries, skipped) = decodeSettings(settings)
        return Parsed(
            appVersion = (root["appVersion"] as? JsonPrimitive)?.intOrNull ?: 0,
            entries = entries,
            skipped = skipped,
        )
    }

    /**
     * Parses the typed `{ key: { type, value } }` map into entries, counting
     * (not failing on) unreadable ones. Counterpart of [encodeSettings]; used
     * both by [decode] and by [ConfigBackup]'s settings section.
     */
    fun decodeSettings(settings: JsonObject): Pair<List<Entry>, Int> {
        val entries = ArrayList<Entry>()
        var skipped = 0
        for ((name, element) in settings) {
            val parsed = runCatching { decodeEntry(name, element.jsonObject) }.getOrNull()
            if (parsed == null) skipped++ else entries.add(parsed)
        }
        return entries to skipped
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

/**
 * Writes one decoded [SettingsBackup.Entry] back into a preference set, under
 * the key type its tag names. Shared by the backup import, the full-config
 * restore, and [LockedSettings] — all three turn the same typed JSON back into
 * DataStore preferences.
 */
fun MutablePreferences.put(entry: SettingsBackup.Entry) {
    when (entry.type) {
        "boolean" -> set(booleanPreferencesKey(entry.name), entry.value as Boolean)
        "int" -> set(intPreferencesKey(entry.name), entry.value as Int)
        "long" -> set(longPreferencesKey(entry.name), entry.value as Long)
        "float" -> set(floatPreferencesKey(entry.name), entry.value as Float)
        "double" -> set(doublePreferencesKey(entry.name), entry.value as Double)
        "string" -> set(stringPreferencesKey(entry.name), entry.value as String)
        "stringSet" -> {
            @Suppress("UNCHECKED_CAST")
            set(stringSetPreferencesKey(entry.name), entry.value as Set<String>)
        }
    }
}
