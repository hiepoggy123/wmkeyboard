package com.wasimaster.wmkeyboard.core.plugins

import kotlinx.serialization.json.Json
import java.io.File

/**
 * `wm.storage`: a small string-to-string map that belongs to one plugin.
 *
 * Deliberately boring, and safe precisely because of what surrounds it. With no
 * network API anywhere in the sandbox there is nowhere for stored data to go, so
 * this is scratch space rather than a data-collection surface — which is why the
 * `storage` permission is disclosed before install but never prompts at use.
 *
 * The quotas are per-plugin and enforced before every write, so a plugin cannot
 * fill the user's device by looping. A null [file] — direct boot, before first
 * unlock — makes the whole thing an in-memory map that is never persisted, the
 * same contract every other store in the app follows.
 */
class PluginStorage(private val file: File?) {

    private val entries = LinkedHashMap<String, String>()
    private var loaded = false

    companion object {
        const val MAX_KEY_LENGTH = 64
        const val MAX_VALUE_LENGTH = 8 * 1024
        const val MAX_KEYS = 128
        const val MAX_TOTAL_BYTES = 64 * 1024

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    /** Why a write was refused, or null when it went through. */
    fun set(key: String, value: String): String? {
        load()
        if (key.isEmpty()) return "storage keys can't be empty"
        if (key.length > MAX_KEY_LENGTH) return "storage keys can't be longer than $MAX_KEY_LENGTH characters"
        if (value.length > MAX_VALUE_LENGTH) {
            return "that value is larger than the ${MAX_VALUE_LENGTH / 1024} KB limit for one key"
        }
        if (!entries.containsKey(key) && entries.size >= MAX_KEYS) {
            return "this plugin already has the maximum of $MAX_KEYS stored keys"
        }
        val existing = entries[key]
        // A new key costs its name as well as its value; overwriting one only
        // costs the difference between the old value and the new.
        val delta = if (existing == null) key.length + value.length else value.length - existing.length
        if (usedBytes() + delta > MAX_TOTAL_BYTES) {
            return "this plugin has used all ${MAX_TOTAL_BYTES / 1024} KB of its storage"
        }
        entries[key] = value
        save()
        return null
    }

    fun get(key: String): String? {
        load()
        return entries[key]
    }

    fun remove(key: String) {
        load()
        if (entries.remove(key) != null) save()
    }

    fun keys(): List<String> {
        load()
        return entries.keys.toList()
    }

    fun clear() {
        load()
        if (entries.isEmpty()) return
        entries.clear()
        save()
    }

    fun usedBytes(): Int {
        load()
        return entries.entries.sumOf { it.key.length + it.value.length }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val source = file ?: return
        if (!source.isFile) return
        runCatching {
            val decoded = json.decodeFromString<Map<String, String>>(source.readText())
            for ((key, value) in decoded) {
                if (entries.size >= MAX_KEYS) break
                if (key.length <= MAX_KEY_LENGTH && value.length <= MAX_VALUE_LENGTH) {
                    entries[key] = value
                }
            }
        }
    }

    private fun save() {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            part.writeText(json.encodeToString(entries))
            if (!part.renameTo(target)) {
                target.delete()
                part.renameTo(target)
            }
        }
    }
}
