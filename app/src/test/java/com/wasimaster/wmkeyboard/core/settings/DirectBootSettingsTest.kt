package com.wasimaster.wmkeyboard.core.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectBootSettingsTest {

    @Test
    fun `tools that need locked storage are dropped`() {
        val settings = KeyboardSettings(
            enabledTools = listOf(
                ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS, ToolbarTool.AI, ToolbarTool.CALENDAR,
                ToolbarTool.EMOJI, ToolbarTool.CALCULATOR, ToolbarTool.CURSOR_LEFT,
            ),
        ).restrictedToDirectBoot()

        assertEquals(
            listOf(ToolbarTool.EMOJI, ToolbarTool.CALCULATOR, ToolbarTool.CURSOR_LEFT),
            settings.enabledTools,
        )
    }

    @Test
    fun `the toolbar and toolbox shrink to the same set`() {
        val settings = KeyboardSettings(
            toolbarTools = listOf(ToolbarTool.CLIPBOARD, ToolbarTool.EMOJI),
            toolboxOrder = listOf(ToolbarTool.GIF, ToolbarTool.LEVEL),
        ).restrictedToDirectBoot()

        assertEquals(listOf(ToolbarTool.EMOJI), settings.toolbarTools)
        assertEquals(listOf(ToolbarTool.LEVEL), settings.toolboxOrder)
    }

    @Test
    fun `the settings app is not offered from a lock screen`() {
        // It would have to start an activity, which a locked device refuses.
        assertFalse(isDirectBootSafeTool(ToolbarTool.SETTINGS))
    }

    @Test
    fun `everything backed by filesDir falls back to a bundled default`() {
        val settings = KeyboardSettings(
            keyFontId = "custom",
            bengaliFontId = "google:Hind Siliguri",
            scriptFontIds = mapOf("DEVANAGARI" to "google:Noto Sans Devanagari"),
            emojiFont = EmojiFontChoice.CUSTOM,
            customThemes = listOf(
                ThemeSpec(
                    id = "mine",
                    name = "Mine",
                    backgroundImage = "/data/user/0/pkg/files/theme_images/mine.img",
                    backgroundImageLandscape = "/data/user/0/pkg/files/theme_images/mine_land.img",
                ),
            ),
        ).restrictedToDirectBoot()

        assertEquals("default", settings.keyFontId)
        assertEquals("default", settings.bengaliFontId)
        assertTrue(settings.scriptFontIds.isEmpty())
        assertEquals(EmojiFontChoice.SYSTEM, settings.emojiFont)
        // The theme itself survives — only the unreadable image path goes.
        assertEquals("mine", settings.customThemes.single().id)
        assertNull(settings.customThemes.single().backgroundImage)
        assertNull(settings.customThemes.single().backgroundImageLandscape)
    }

    @Test
    fun `nothing personal is read or written while locked`() {
        val settings = KeyboardSettings(
            contactSuggestions = true,
            contactEmailSuggestions = true,
            appNameSuggestions = true,
            addWordsToSystemDictionary = true,
        ).restrictedToDirectBoot()

        assertFalse(settings.contactSuggestions)
        assertFalse(settings.contactEmailSuggestions)
        assertFalse(settings.appNameSuggestions)
        assertFalse(settings.addWordsToSystemDictionary)
        assertFalse(settings.clipboard.suggestRecent)
    }

    @Test
    fun `credentials are never mirrored into device-protected storage`() {
        // What LockedSettings writes is exactly a secretless settings export,
        // so the guarantee is testable without an Android SharedPreferences.
        val prefs = mutablePreferencesOf(
            *SettingsBackup.SECRET_KEYS
                .map { stringPreferencesKey(it) to "secret-$it" }
                .toTypedArray(),
            stringPreferencesKey("active_layout_id") to "qwerty",
        )

        val mirrored = SettingsBackup.encodeSettings(prefs, includeSecrets = false)

        assertEquals(setOf("active_layout_id"), mirrored.keys)
    }

    @Test
    fun `every credential the settings hold is on the secret list`() {
        // The mirror filters by key name, so a credential added to
        // KeyboardSettings without a SECRET_KEYS entry would silently land in
        // storage that is not covered by the user's credential.
        val credentials = setOf(
            "translate_api_key", "klipy_api_key", "brave_api_key", "giphy_api_key",
            "ai_anthropic_key", "ai_openai_key", "ai_gemini_key", "hf_token",
        )
        assertTrue(credentials.all { it in SettingsBackup.SECRET_KEYS })
    }
}
