package com.wasimaster.wmkeyboard.core.voice.whisper

import android.content.Context
import java.io.File

/**
 * Full-flavor on-device Whisper speech-to-text via the LiteRT/TF-Lite
 * [org.tensorflow.lite.Interpreter]. Interpreter init is slow and pins the
 * model, so one interpreter is cached across calls and rebuilt only when the
 * model file changes. The lite flavor replaces this object with a stub.
 *
 * NOTE: [transcribe] is filled in during phase 3 (mel + vocab + interpreter);
 * the phase-1 skeleton only establishes the object and API surface so callers
 * and the flavor split compile. Nothing invokes [transcribe] until the phase-4
 * service wiring lands.
 */
object WhisperEngine {

    const val AVAILABLE = true

    /**
     * Transcribes one PCM utterance (16 kHz mono float, already resampled) to
     * text. [langToken] forces a language on multilingual graphs (null =
     * auto-detect, or a single-language/`.en` model); [translate] forces the
     * translate-to-English task. Call on a background dispatcher only. Throws
     * with a user-presentable message on failure.
     */
    fun transcribe(
        context: Context,
        modelFile: File,
        vocabFile: File,
        pcm: FloatArray,
        langToken: Int?,
        translate: Boolean,
    ): String = throw NotImplementedError("WhisperEngine.transcribe lands in phase 3")

    /** Frees the cached interpreter. Non-blocking (see phase-3 impl). */
    fun release() = Unit
}
