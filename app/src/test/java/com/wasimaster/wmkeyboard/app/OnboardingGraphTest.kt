package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.settings.OnboardingSettings
import com.wasimaster.wmkeyboard.core.settings.PersonaDepth
import com.wasimaster.wmkeyboard.core.settings.PersonaLanguages
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGraphTest {

    private fun pages(
        missingEmoji: Int? = 0,
        samsungEmoji: Boolean = false,
        persona: OnboardingSettings = OnboardingSettings(),
        enabledTools: Collection<ToolbarTool> = emptyList(),
        enabledLanguageCount: Int = 2,
        replay: Boolean = false,
        imeReady: Boolean = false,
    ) = onboardingPages(
        missingEmoji, samsungEmoji, persona, enabledTools,
        enabledLanguageCount, replay, imeReady,
    )

    @Test
    fun `fresh run with unanswered quiz takes the middle path`() {
        assertEquals(
            listOf(
                OnboardingPage.WELCOME, OnboardingPage.PERSONA, OnboardingPage.LANGUAGES,
                OnboardingPage.LOOK, OnboardingPage.FEEDBACK, OnboardingPage.GESTURES,
                OnboardingPage.DISCOVER, OnboardingPage.TRY,
            ),
            pages(),
        )
    }

    @Test
    fun `minimal persona skips the fine-tuning pages`() {
        val result = pages(persona = OnboardingSettings(personaDepth = PersonaDepth.MINIMAL))
        assertFalse(OnboardingPage.FEEDBACK in result)
        assertFalse(OnboardingPage.GESTURES in result)
        assertFalse(OnboardingPage.TOOLS in result)
        assertFalse(OnboardingPage.TOOL_SETUP in result)
    }

    @Test
    fun `power persona gets the tools page`() {
        val result = pages(persona = OnboardingSettings(personaDepth = PersonaDepth.POWER))
        assertTrue(OnboardingPage.TOOLS in result)
        assertTrue(OnboardingPage.FEEDBACK in result)
    }

    @Test
    fun `tool setup needs power and a tool with choices`() {
        val power = OnboardingSettings(personaDepth = PersonaDepth.POWER)
        assertTrue(
            OnboardingPage.TOOL_SETUP in
                pages(persona = power, enabledTools = listOf(ToolbarTool.WEATHER)),
        )
        assertFalse(
            OnboardingPage.TOOL_SETUP in
                pages(persona = power, enabledTools = listOf(ToolbarTool.EMOJI)),
        )
        // Balanced users with the calendar tool on still skip it: the page is
        // power-user territory now.
        assertFalse(
            OnboardingPage.TOOL_SETUP in
                pages(
                    persona = OnboardingSettings(personaDepth = PersonaDepth.BALANCED),
                    enabledTools = listOf(ToolbarTool.CALENDAR),
                ),
        )
    }

    @Test
    fun `single-language persona hides the languages page until a second language exists`() {
        val one = OnboardingSettings(personaLanguages = PersonaLanguages.ONE)
        assertFalse(OnboardingPage.LANGUAGES in pages(persona = one, enabledLanguageCount = 1))
        assertTrue(OnboardingPage.LANGUAGES in pages(persona = one, enabledLanguageCount = 2))
    }

    @Test
    fun `emoji page waits for the count and shows for missing glyphs or samsung`() {
        assertFalse(OnboardingPage.EMOJI in pages(missingEmoji = null))
        assertFalse(OnboardingPage.EMOJI in pages(missingEmoji = 0))
        assertTrue(OnboardingPage.EMOJI in pages(missingEmoji = 3))
        assertTrue(OnboardingPage.EMOJI in pages(missingEmoji = 0, samsungEmoji = true))
    }

    @Test
    fun `replay hides welcome only while the keyboard is ready`() {
        assertFalse(OnboardingPage.WELCOME in pages(replay = true, imeReady = true))
        assertTrue(OnboardingPage.WELCOME in pages(replay = true, imeReady = false))
        assertTrue(OnboardingPage.WELCOME in pages(replay = false, imeReady = true))
    }

    @Test
    fun `resolvePageIndex finds the current page`() {
        val list = pages()
        assertEquals(2, resolvePageIndex(list, OnboardingPage.LANGUAGES))
    }

    @Test
    fun `resolvePageIndex falls back to the nearest earlier survivor`() {
        val list = listOf(
            OnboardingPage.WELCOME, OnboardingPage.PERSONA,
            OnboardingPage.LOOK, OnboardingPage.TRY,
        )
        // GESTURES vanished; LOOK is the nearest earlier page still present.
        assertEquals(2, resolvePageIndex(list, OnboardingPage.GESTURES))
        // Nothing earlier survives: land on the first page.
        val tail = listOf(OnboardingPage.LOOK, OnboardingPage.TRY)
        assertEquals(0, resolvePageIndex(tail, OnboardingPage.WELCOME))
    }

    @Test
    fun `every page has an accent`() {
        for (page in OnboardingPage.entries) {
            assertTrue("$page has no accent", OnboardingPageAccents.containsKey(page))
        }
    }
}
