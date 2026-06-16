package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings

/**
 * Built-in system prompts for the AI tool's actions. Each can be overridden
 * per-action in the tool's settings (blank = use the default here). Every
 * prompt demands output-only responses so the result can be inserted
 * verbatim into the text field.
 */
object AiPrompts {

    private const val OUTPUT_ONLY =
        " Reply with ONLY the resulting text — no preamble, no explanations, no quotes around it."

    fun defaultPrompt(action: AiAction, translateTo: String = "English"): String = when (action) {
        AiAction.REWRITE ->
            "Rewrite the user's text, keeping the meaning but improving flow and clarity." + OUTPUT_ONLY
        AiAction.SUMMARIZE ->
            "Summarize the user's text concisely, keeping the key points. Match the input language." + OUTPUT_ONLY
        AiAction.TRANSLATE ->
            "Translate the user's text into $translateTo. Preserve tone and formatting." + OUTPUT_ONLY
        AiAction.IMPROVE ->
            "Improve the user's writing: stronger word choice, better structure, same voice and meaning." + OUTPUT_ONLY
        AiAction.FIX_GRAMMAR ->
            "Fix all spelling, grammar and punctuation mistakes in the user's text. Change nothing else." + OUTPUT_ONLY
        AiAction.EXPLAIN ->
            "Explain the user's text in simple, clear terms a layperson would understand. Be brief."
        AiAction.CONTINUE ->
            "Continue the user's text naturally in the same style, tone and language. " +
                "Write the continuation only — do not repeat the given text." + OUTPUT_ONLY
    }

    /** Effective system prompt: the user's override, or the built-in. */
    fun systemPrompt(action: AiAction, settings: KeyboardSettings): String {
        val custom = when (action) {
            AiAction.REWRITE -> settings.aiPromptRewrite
            AiAction.SUMMARIZE -> settings.aiPromptSummarize
            AiAction.TRANSLATE -> settings.aiPromptTranslate
            AiAction.IMPROVE -> settings.aiPromptImprove
            AiAction.FIX_GRAMMAR -> settings.aiPromptFixGrammar
            AiAction.EXPLAIN -> settings.aiPromptExplain
            AiAction.CONTINUE -> settings.aiPromptContinue
        }
        return custom.ifBlank { defaultPrompt(action, settings.aiTranslateTo) }
    }
}
