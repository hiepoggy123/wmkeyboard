package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.MinimalTools
import com.wasimaster.wmkeyboard.core.settings.OnboardingSettings
import com.wasimaster.wmkeyboard.core.settings.PersonaDepth
import com.wasimaster.wmkeyboard.core.settings.PersonaLanguages
import com.wasimaster.wmkeyboard.core.settings.PersonaPrivacy
import com.wasimaster.wmkeyboard.core.settings.RecommendedTools
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPresetsTest {

    @Test
    fun `many languages puts both swipes on language switching`() {
        assertEquals(
            listOf(
                PresetWrite.SpaceSwipes(SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.LANGUAGE),
                PresetWrite.GlobeAsEmoji(false),
            ),
            presetsFor(PersonaLanguages.MANY),
        )
    }

    @Test
    fun `one language frees the swipes and the globe key`() {
        assertEquals(
            listOf(
                PresetWrite.SpaceSwipes(SpaceSwipeAction.CURSOR, SpaceSwipeAction.CURSOR),
                PresetWrite.GlobeAsEmoji(true),
            ),
            presetsFor(PersonaLanguages.ONE),
        )
    }

    @Test
    fun `depth answers land their tool sets`() {
        assertEquals(
            listOf(PresetWrite.Tools(MinimalTools)),
            presetsFor(PersonaDepth.MINIMAL),
        )
        assertEquals(
            listOf(PresetWrite.Tools(RecommendedTools)),
            presetsFor(PersonaDepth.BALANCED),
        )
        assertEquals(
            listOf(PresetWrite.Tools(RecommendedTools)),
            presetsFor(PersonaDepth.POWER),
        )
    }

    @Test
    fun `strict privacy turns off learning history and statistics`() {
        assertEquals(
            listOf(
                PresetWrite.LearnFromTyping(false),
                PresetWrite.ClipboardHistory(false),
                PresetWrite.TypingStats(false),
            ),
            presetsFor(PersonaPrivacy.STRICT),
        )
    }

    @Test
    fun `standard and unset answers write nothing`() {
        assertTrue(presetsFor(PersonaPrivacy.STANDARD).isEmpty())
        assertTrue(presetsFor(PersonaPrivacy.UNSET).isEmpty())
        assertTrue(presetsFor(PersonaLanguages.UNSET).isEmpty())
        assertTrue(presetsFor(PersonaDepth.UNSET).isEmpty())
    }

    @Test
    fun `recommended spacebar choice follows the language answer`() {
        assertEquals(
            SpacebarChoice.LANGUAGE_ONLY,
            recommendedSpacebarChoice(
                OnboardingSettings(personaLanguages = PersonaLanguages.MANY),
            ),
        )
        assertEquals(
            SpacebarChoice.CURSOR_ONLY,
            recommendedSpacebarChoice(
                OnboardingSettings(personaLanguages = PersonaLanguages.ONE),
            ),
        )
        assertEquals(
            SpacebarChoice.LANGUAGE_THEN_CURSOR,
            recommendedSpacebarChoice(OnboardingSettings()),
        )
    }

    @Test
    fun `finishing over the untouched all-on default lands the persona's set`() {
        val allOn = KeyboardSettings(enabledTools = ToolbarTool.entries.toList())
        assertEquals(RecommendedTools, toolsAfterFinish(allOn))
        assertEquals(
            MinimalTools,
            toolsAfterFinish(
                allOn.copy(onboarding = OnboardingSettings(personaDepth = PersonaDepth.MINIMAL)),
            ),
        )
    }

    @Test
    fun `a touched tool selection is never overwritten on finish`() {
        assertNull(toolsAfterFinish(KeyboardSettings(enabledTools = listOf(ToolbarTool.EMOJI))))
    }
}
