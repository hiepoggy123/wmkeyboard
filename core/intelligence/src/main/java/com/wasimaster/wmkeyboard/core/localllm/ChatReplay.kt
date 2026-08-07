package com.wasimaster.wmkeyboard.core.localllm

/**
 * Rebuilds chat context after the native engine went away mid-conversation.
 *
 * A LiteRT-LM Conversation holds the chat history in its KV cache and has no
 * way to serialize it, so when the engine is evicted — the IME releases it on
 * trim-memory, or the model/backend changes — the only way to continue the
 * chat is to replay the transcript as text into a fresh conversation. That
 * costs one long prefill; every turn after it is warm again.
 */
object ChatReplay {

    /** One earlier exchange half, oldest first. */
    data class Turn(val fromUser: Boolean, val text: String)

    /**
     * The rough character budget the replayed transcript may spend. Prompt
     * and answer share one token window on-device, so an unbounded replay
     * would leave no room to answer; oldest turns fall off first.
     */
    const val MAX_REPLAY_CHARS = 12_000

    /**
     * The user message to send into a fresh conversation so the model picks
     * the chat up where it left off: the recent transcript, then [user].
     * With no history it is just [user].
     */
    fun replayMessage(history: List<Turn>, user: String): String {
        val kept = recentTurns(history)
        if (kept.isEmpty()) return user
        return buildString {
            append("The session restarted, so here is our conversation so far:\n\n")
            for (turn in kept) {
                append(if (turn.fromUser) "User: " else "Assistant: ")
                append(turn.text.trim())
                append("\n\n")
            }
            append("That is the end of the transcript. ")
            append("Continue the conversation naturally. My next message:\n\n")
            append(user)
        }
    }

    /** The newest turns that fit [MAX_REPLAY_CHARS], oldest first. */
    private fun recentTurns(history: List<Turn>): List<Turn> {
        var budget = MAX_REPLAY_CHARS
        val kept = ArrayList<Turn>()
        for (turn in history.asReversed()) {
            val text = turn.text.trim()
            if (text.isEmpty()) continue
            if (text.length > budget) break
            budget -= text.length
            kept.add(turn)
        }
        return kept.asReversed()
    }
}
