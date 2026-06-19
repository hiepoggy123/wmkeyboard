package com.wasimaster.wmkeyboard.core.tools

/**
 * Splits reasoning-model output around `<think>…</think>` passages (Qwen 3
 * and friends emit these before the actual answer). Works on streaming
 * partials: an opened-but-unclosed block means the model is still reasoning.
 */
object AiThinking {

    private const val OPEN = "<think>"
    private const val CLOSE = "</think>"

    data class Split(
        /** Response text with the think block removed. */
        val output: String,
        /** True while a think block is open with no `</think>` yet. */
        val thinking: Boolean,
    )

    /**
     * [implicitThink] is for models (Qwen 3 style) whose chat template puts
     * the opening `<think>` inside the prompt — their raw output is bare
     * reasoning terminated by `</think>`, with no opener ever emitted. For
     * those, everything before the close tag is reasoning.
     */
    fun split(raw: String, implicitThink: Boolean = false): Split {
        val close = raw.indexOf(CLOSE)
        if (close != -1) {
            val open = raw.indexOf(OPEN)
            val head = if (open in 0 until close) raw.take(open) else ""
            return Split(head + raw.substring(close + CLOSE.length), thinking = false)
        }
        val open = raw.indexOf(OPEN)
        return when {
            open != -1 -> Split(raw.take(open), thinking = true)
            implicitThink -> Split("", thinking = true)
            else -> Split(raw, thinking = false)
        }
    }

    /**
     * Final-result cleanup: the answer without reasoning, trimmed. A finished
     * response with no think markers at all is returned whole even under
     * [implicitThink] — the model simply didn't reason this time.
     */
    fun stripped(raw: String): String = split(raw, implicitThink = false).output.trim()
}
