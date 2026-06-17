package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardModeTest {

    private val email = KeyboardMode(
        id = "email", name = "Email",
        symbolSetIds = listOf(BuiltInSymbolSets.EMAIL_ID),
        apps = listOf("com.google.android.gm"),
        fieldKinds = listOf(ModeField.EMAIL),
    )
    private val password = KeyboardMode(
        id = "password", name = "Passwords",
        emojiBarMode = EmojiBarMode.OFF,
        symbolRowEnabled = false,
        fieldKinds = listOf(ModeField.PASSWORD),
    )
    private val browser = KeyboardMode(
        id = "browser", name = "Browser",
        apps = listOf("com.android.chrome"),
        fieldKinds = listOf(ModeField.URL),
    )
    private val modes = listOf(password, email, browser)

    @Test
    fun `no match resolves to null`() {
        assertNull(resolveKeyboardMode(modes, "com.example.other", emptySet(), null))
    }

    @Test
    fun `app binding matches`() {
        assertEquals(
            "browser",
            resolveKeyboardMode(modes, "com.android.chrome", emptySet(), null)?.id,
        )
    }

    @Test
    fun `field kind beats app binding`() {
        // A password box inside the browser gets the password mode.
        assertEquals(
            "password",
            resolveKeyboardMode(
                modes, "com.android.chrome", setOf(ModeField.PASSWORD), null,
            )?.id,
        )
    }

    @Test
    fun `manual pick beats everything`() {
        assertEquals(
            "email",
            resolveKeyboardMode(
                modes, "com.android.chrome", setOf(ModeField.PASSWORD), "email",
            )?.id,
        )
    }

    @Test
    fun `stale manual id falls back to automatic`() {
        assertEquals(
            "browser",
            resolveKeyboardMode(modes, "com.android.chrome", emptySet(), "deleted-mode")?.id,
        )
    }

    @Test
    fun `applyMode overrides only the mode's fields`() {
        val base = KeyboardSettings(emojiBarMode = EmojiBarMode.ALWAYS, symbolRowEnabled = true)
        val applied = base.applyMode(password)
        assertEquals(EmojiBarMode.OFF, applied.emojiBarMode)
        assertEquals(false, applied.symbolRowEnabled)
        // Untouched fields inherit.
        assertEquals(base.toolbarTools, applied.toolbarTools)
        assertEquals(base.symbolRowSetIds, applied.symbolRowSetIds)
    }

    @Test
    fun `applyMode with sets switches the active set to the mode's first`() {
        val base = KeyboardSettings(symbolRowActiveSetId = BuiltInSymbolSets.PUNCTUATION_ID)
        val applied = base.applyMode(email)
        assertEquals(listOf(BuiltInSymbolSets.EMAIL_ID), applied.symbolRowSetIds)
        assertEquals(BuiltInSymbolSets.EMAIL_ID, applied.symbolRowActiveSetId)
    }

    @Test
    fun `mode list round-trips through the codec`() {
        val decoded = KeyboardModeCodec.decodeList(KeyboardModeCodec.encodeList(modes))
        assertEquals(modes, decoded)
    }

    @Test
    fun `sanitizeBarOrder repairs missing and duplicate rows`() {
        assertEquals(
            listOf(BarRow.EMOJI, BarRow.TOPBAR, BarRow.SYMBOL),
            sanitizeBarOrder(listOf(BarRow.EMOJI, BarRow.EMOJI, BarRow.TOPBAR)),
        )
        assertEquals(BarRow.entries.toList(), sanitizeBarOrder(emptyList()))
    }
}
