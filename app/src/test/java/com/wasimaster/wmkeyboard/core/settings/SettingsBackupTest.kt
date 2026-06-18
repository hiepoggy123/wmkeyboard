package com.wasimaster.wmkeyboard.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val prefs = mutablePreferencesOf(
        booleanPreferencesKey("volume_cursor") to true,
        intPreferencesKey("key_height") to 52,
        floatPreferencesKey("key_sound_volume") to 0.4f,
        stringPreferencesKey("input_mode") to "AVRO",
        stringSetPreferencesKey("enabled_tools") to setOf("EMOJI", "CLIPBOARD"),
        stringPreferencesKey("ai_openai_key") to "sk-secret",
    )

    private fun encode(includeSecrets: Boolean) =
        SettingsBackup.encode(prefs, includeSecrets, appVersion = 12, appVersionName = "1.2")

    private fun entries(text: String) =
        SettingsBackup.decode(text)!!.entries.associate { it.name to it.value }

    @Test
    fun `every preference type survives a round trip`() {
        val decoded = entries(encode(includeSecrets = true))
        assertEquals(true, decoded["volume_cursor"])
        assertEquals(52, decoded["key_height"])
        assertEquals(0.4f, decoded["key_sound_volume"])
        assertEquals("AVRO", decoded["input_mode"])
        assertEquals(setOf("EMOJI", "CLIPBOARD"), decoded["enabled_tools"])
    }

    @Test
    fun `secrets are withheld unless asked for`() {
        val without = SettingsBackup.decode(encode(includeSecrets = false))!!
        assertFalse(without.containsSecrets)
        assertFalse(encode(includeSecrets = false).contains("sk-secret"))

        val with = SettingsBackup.decode(encode(includeSecrets = true))!!
        assertTrue(with.containsSecrets)
        assertEquals("sk-secret", entries(encode(includeSecrets = true))["ai_openai_key"])
    }

    @Test
    fun `an int is not restored as a long`() {
        // DataStore keys are typed: restoring 52 under a Long key would make
        // the setting unreadable, so the type tag has to survive too.
        val type = SettingsBackup.decode(encode(includeSecrets = false))!!
            .entries.first { it.name == "key_height" }.type
        assertEquals("int", type)
    }

    @Test
    fun `foreign json is not mistaken for a backup`() {
        assertNull(SettingsBackup.decode("{\"format\":\"wmtheme\",\"settings\":{}}"))
        assertNull(SettingsBackup.decode("not json at all"))
        assertNull(SettingsBackup.decode("{}"))
    }

    @Test
    fun `one unreadable entry does not cost the rest of the file`() {
        val text = """
            {
              "format": "wmkeyboard-settings",
              "version": 1,
              "settings": {
                "key_height": { "type": "int", "value": 52 },
                "mystery": { "type": "widget", "value": 1 },
                "input_mode": { "type": "string", "value": "AVRO" }
              }
            }
        """.trimIndent()
        val parsed = SettingsBackup.decode(text)!!
        assertEquals(1, parsed.skipped)
        assertEquals(2, parsed.entries.size)
        assertEquals("AVRO", parsed.entries.first { it.name == "input_mode" }.value)
    }

    @Test
    fun `a value that does not match its type tag is skipped`() {
        val text = """
            {
              "format": "wmkeyboard-settings",
              "settings": {
                "key_height": { "type": "int", "value": "tall" },
                "input_mode": { "type": "string", "value": "AVRO" }
              }
            }
        """.trimIndent()
        val parsed = SettingsBackup.decode(text)!!
        assertEquals(1, parsed.skipped)
        assertEquals(1, parsed.entries.size)
    }
}
