package com.wasimaster.wmkeyboard.core.localllm

import android.content.Context
import com.wasimaster.wmkeyboard.core.settings.LocalLlmBackend
import java.io.File

/**
 * Lite-flavor stub — the LiteRT-LM runtime is a full-build feature, so this
 * object only keeps the shared callers (IME service) compiling. The
 * On-device provider is hidden from lite settings, making this unreachable
 * in practice.
 */
object LocalLlmEngine {

    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun generate(
        context: Context,
        modelFile: File,
        backend: LocalLlmBackend,
        contextTokens: Int,
        system: String,
        user: String,
        onPartial: ((String) -> Unit)? = null,
    ): String = error("On-device AI is not available in the lite build")

    fun release() = Unit
}
