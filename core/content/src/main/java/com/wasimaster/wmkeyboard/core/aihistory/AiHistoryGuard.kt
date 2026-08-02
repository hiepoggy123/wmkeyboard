package com.wasimaster.wmkeyboard.core.aihistory

/**
 * The one decision that must never be wrong: whether a finished AI run may be
 * written down.
 *
 * A pure function in its own file, so it can be tested in every combination
 * rather than reasoned about at a call site four levels inside the keyboard
 * service.
 */
object AiHistoryGuard {

    /**
     * Four independent noes, any one of which is final.
     *
     * @param enabled the user turned the history on. Off by default.
     * @param unlocked the device has been unlocked since it restarted. Before
     *   that the app's real storage cannot be read or written at all.
     * @param secureField the field is a password box. The user never asked for
     *   this check and must never have to think about it.
     * @param incognito incognito is in force, from the setting or because the
     *   field asked for it (a private browser tab does).
     *
     * API keys need no check here: the record carries the model name, which is
     * a plain string, and never the connection details that hold the key.
     */
    fun shouldRecord(
        enabled: Boolean,
        unlocked: Boolean,
        secureField: Boolean,
        incognito: Boolean,
    ): Boolean = enabled && unlocked && !secureField && !incognito
}
