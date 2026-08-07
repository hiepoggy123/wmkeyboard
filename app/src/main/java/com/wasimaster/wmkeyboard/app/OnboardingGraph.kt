package com.wasimaster.wmkeyboard.app

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.MinimalTools
import com.wasimaster.wmkeyboard.core.settings.OnboardingSettings
import com.wasimaster.wmkeyboard.core.settings.PersonaDepth
import com.wasimaster.wmkeyboard.core.settings.PersonaLanguages
import com.wasimaster.wmkeyboard.core.settings.PersonaPrivacy
import com.wasimaster.wmkeyboard.core.settings.RecommendedTools
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool

/**
 * Every wizard step, in the order they're asked. Which ones actually appear is
 * decided per device, per persona and per run — see [onboardingPages].
 */
internal enum class OnboardingPage {
    WELCOME, PERSONA, LANGUAGES, LOOK, EMOJI, FEEDBACK, GESTURES,
    DISCOVER, TOOLS, TOOL_SETUP, TRY,
}

/** Tools with a first-run choice worth asking about on the setup page. */
internal val ToolSetupTools = setOf(
    ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.COMPASS,
)

/**
 * The wizard's live page list.
 *
 * The persona answers trim it: someone who asked for a simple keyboard is not
 * walked through haptic styles and swipe slots, and the full tool list only
 * exists for the person who asked to see everything. An unanswered quiz
 * (UNSET) takes the middle path. The emoji page has nothing to say on a phone
 * whose font already draws the whole catalog (and is held back until the count
 * lands, so it never flashes in and out). On a replay the welcome page only
 * appears when the keyboard actually needs setting up again.
 */
internal fun onboardingPages(
    missingEmoji: Int?,
    samsungEmoji: Boolean,
    persona: OnboardingSettings,
    enabledTools: Collection<ToolbarTool>,
    enabledLanguageCount: Int,
    replay: Boolean,
    imeReady: Boolean,
): List<OnboardingPage> = OnboardingPage.entries.filter { page ->
    when (page) {
        OnboardingPage.WELCOME -> !(replay && imeReady)
        OnboardingPage.LANGUAGES ->
            persona.personaLanguages != PersonaLanguages.ONE || enabledLanguageCount > 1
        OnboardingPage.EMOJI -> (missingEmoji ?: 0) > 0 || samsungEmoji
        OnboardingPage.FEEDBACK, OnboardingPage.GESTURES ->
            persona.personaDepth != PersonaDepth.MINIMAL
        OnboardingPage.TOOLS -> persona.personaDepth == PersonaDepth.POWER
        OnboardingPage.TOOL_SETUP ->
            persona.personaDepth == PersonaDepth.POWER &&
                enabledTools.any { it in ToolSetupTools }
        else -> true
    }
}

/**
 * Where [current] sits in [pages]. A page can disappear under the user (the
 * emoji count landing at zero, or a persona re-answer hiding the page they are
 * on); fall back to the nearest earlier page that survived rather than
 * snapping to the start.
 */
internal fun resolvePageIndex(pages: List<OnboardingPage>, current: OnboardingPage): Int =
    pages.indexOf(current).let { found ->
        if (found >= 0) found else pages.indexOfLast { it < current }.coerceAtLeast(0)
    }

/**
 * Accent colour per page, matching the route colours the settings screens use
 * ([SettingsRouteColors]) so the wizard reads as part of the same app.
 */
internal val OnboardingPageAccents: Map<OnboardingPage, Color> = mapOf(
    OnboardingPage.WELCOME to Color(0xFF42A5F5),
    OnboardingPage.PERSONA to Color(0xFF7E57C2),
    OnboardingPage.LANGUAGES to Color(0xFF66BB6A),
    OnboardingPage.LOOK to Color(0xFFEC407A),
    OnboardingPage.EMOJI to Color(0xFFFFB300),
    OnboardingPage.FEEDBACK to Color(0xFF7E57C2),
    OnboardingPage.GESTURES to Color(0xFF26C6DA),
    OnboardingPage.DISCOVER to Color(0xFF00ACC1),
    OnboardingPage.TOOLS to Color(0xFFFF7043),
    OnboardingPage.TOOL_SETUP to Color(0xFFFF7043),
    OnboardingPage.TRY to Color(0xFF26A69A),
)

/**
 * One combined choice for the two spacebar-swipe slots, so the wizard asks
 * a single question; the two independent settings stay available under
 * Typing → Gestures.
 */
internal enum class SpacebarChoice(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val short: SpaceSwipeAction,
    val long: SpaceSwipeAction,
) {
    CURSOR_THEN_LANGUAGE(
        R.string.onboarding_spacebar_cursor_language_title,
        R.string.onboarding_spacebar_cursor_language_subtitle,
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.LANGUAGE,
    ),
    LANGUAGE_THEN_CURSOR(
        R.string.onboarding_spacebar_language_cursor_title,
        R.string.onboarding_spacebar_language_cursor_subtitle,
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.CURSOR,
    ),
    LANGUAGE_ONLY(
        R.string.onboarding_spacebar_language_only_title,
        R.string.onboarding_spacebar_language_only_subtitle,
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.LANGUAGE,
    ),
    CURSOR_ONLY(
        R.string.onboarding_spacebar_cursor_only_title,
        R.string.onboarding_spacebar_cursor_only_subtitle,
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.CURSOR,
    ),
    OFF(
        R.string.onboarding_spacebar_off_title,
        R.string.onboarding_spacebar_off_subtitle,
        SpaceSwipeAction.NONE, SpaceSwipeAction.NONE,
    ),
}

/**
 * Which spacebar choice the gestures page badges as recommended. Follows the
 * persona: with several languages both swipes went to language switching, with
 * one language there is nothing to switch, and an unanswered quiz gets the
 * shipped default.
 */
internal fun recommendedSpacebarChoice(persona: OnboardingSettings): SpacebarChoice =
    when (persona.personaLanguages) {
        PersonaLanguages.MANY -> SpacebarChoice.LANGUAGE_ONLY
        PersonaLanguages.ONE -> SpacebarChoice.CURSOR_ONLY
        PersonaLanguages.UNSET -> SpacebarChoice.LANGUAGE_THEN_CURSOR
    }

/**
 * One settings write a persona answer stands for. Kept as data rather than
 * done inline so the answer-to-write mapping is a pure function tests can
 * check, and the page applies whatever the mapping says.
 */
internal sealed interface PresetWrite {
    data class SpaceSwipes(val short: SpaceSwipeAction, val long: SpaceSwipeAction) : PresetWrite
    data class GlobeAsEmoji(val value: Boolean) : PresetWrite
    data class Tools(val tools: Set<ToolbarTool>) : PresetWrite
    data class LearnFromTyping(val value: Boolean) : PresetWrite
    data class ClipboardHistory(val value: Boolean) : PresetWrite
    data class TypingStats(val value: Boolean) : PresetWrite
}

/**
 * The defaults a language answer applies. With several languages both swipe
 * slots switch language (the persona page says so in a notice); with one, the
 * swipes go to the cursor and the globe key becomes an emoji key, because a
 * language switcher with nothing to switch is a dead key.
 */
internal fun presetsFor(answer: PersonaLanguages): List<PresetWrite> = when (answer) {
    PersonaLanguages.MANY -> listOf(
        PresetWrite.SpaceSwipes(SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.LANGUAGE),
        PresetWrite.GlobeAsEmoji(false),
    )
    PersonaLanguages.ONE -> listOf(
        PresetWrite.SpaceSwipes(SpaceSwipeAction.CURSOR, SpaceSwipeAction.CURSOR),
        PresetWrite.GlobeAsEmoji(true),
    )
    PersonaLanguages.UNSET -> emptyList()
}

/**
 * The tool set a depth answer lands. POWER seeds the recommended set too: the
 * full tools page follows for fine-tuning, and starting it from
 * everything-enabled would bury the choice under seventy switches.
 */
internal fun presetsFor(answer: PersonaDepth): List<PresetWrite> = when (answer) {
    PersonaDepth.MINIMAL -> listOf(PresetWrite.Tools(MinimalTools))
    PersonaDepth.BALANCED, PersonaDepth.POWER -> listOf(PresetWrite.Tools(RecommendedTools))
    PersonaDepth.UNSET -> emptyList()
}

/**
 * What the strict privacy answer turns off. Incognito in private fields is
 * already on for everyone, so it is not repeated here; STANDARD writes nothing
 * because the defaults already are the standard behaviour.
 */
internal fun presetsFor(answer: PersonaPrivacy): List<PresetWrite> = when (answer) {
    PersonaPrivacy.STRICT -> listOf(
        PresetWrite.LearnFromTyping(false),
        PresetWrite.ClipboardHistory(false),
        PresetWrite.TypingStats(false),
    )
    PersonaPrivacy.STANDARD, PersonaPrivacy.UNSET -> emptyList()
}

/**
 * The tool set to land when the wizard closes, or null when the enabled set
 * needs no touch. Skipping from the first page used to leave every one of the
 * 70+ tools enabled, because only the tools page applied the starter set; now
 * finishing over the untouched everything-on default lands the persona's set
 * no matter which page the wizard closed from.
 */
internal fun toolsAfterFinish(settings: KeyboardSettings): Set<ToolbarTool>? {
    if (settings.enabledTools.toSet() != ToolbarTool.entries.toSet()) return null
    return when (settings.onboarding.personaDepth) {
        PersonaDepth.MINIMAL -> MinimalTools
        else -> RecommendedTools
    }
}
