package com.wasimaster.wmkeyboard.core.voice.whisper

import android.content.Context
import java.io.File

/**
 * Lite-flavor stub — the LiteRT Whisper runtime is a full-build feature, so
 * this object only keeps the shared callers (IME service) compiling. The
 * Whisper voice engine is hidden from lite settings, making this unreachable
 * in practice.
 */
object WhisperEngine {

    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun transcribe(
        context: Context,
        modelFile: File,
        vocabFile: File,
        pcm: FloatArray,
        translate: Boolean,
        langToken: Int? = null,
    ): String = throw IllegalStateException("Offline Whisper is not available in the lite build")

    fun release() = Unit
}
