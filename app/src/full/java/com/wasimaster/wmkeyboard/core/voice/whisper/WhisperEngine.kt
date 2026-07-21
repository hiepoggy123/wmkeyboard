package com.wasimaster.wmkeyboard.core.voice.whisper

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.locks.ReentrantLock
import org.tensorflow.lite.Interpreter

/**
 * Full-flavor on-device Whisper speech-to-text via the LiteRT/TF-Lite
 * [Interpreter]. The DocWolle `.tflite` graphs run the whole autoregressive
 * decode internally: feed the `[1, 80, 3000]` log-mel spectrogram, read back
 * the `sequences` token ids, detokenize.
 *
 * Interpreter init memory-maps the model and is slow, so one interpreter and
 * one parsed vocab are cached and rebuilt only when the file paths change. The
 * lite flavor replaces this object with a stub that throws.
 *
 * Language is auto-detected by the multilingual graphs (there is no runtime
 * language input); the only runtime choice is transcribe vs translate, selected
 * by signature. Single-language and `.en` graphs expose just `serving_default`.
 */
object WhisperEngine {

    const val AVAILABLE = true

    private const val SIG_TRANSCRIBE = "serving_transcribe"
    private const val SIG_TRANSLATE = "serving_translate"
    private const val SIG_DEFAULT = "serving_default"
    private const val INPUT_NAME = "input_features"
    private const val OUTPUT_NAME = "sequences"

    private val lock = ReentrantLock()
    private var interpreter: Interpreter? = null
    private var loadedModelPath: String? = null
    private var vocab: WhisperVocab? = null
    private var loadedVocabPath: String? = null

    /**
     * Transcribes one PCM utterance (16 kHz mono float in [-1, 1]) to text.
     * [translate] forces the translate-to-English task where the model supports
     * it. Call on a background dispatcher only. Throws [IOException] with a
     * user-presentable message on failure.
     */
    fun transcribe(
        context: Context,
        modelFile: File,
        vocabFile: File,
        pcm: FloatArray,
        translate: Boolean,
    ): String {
        lock.lock()
        try {
            val voc = obtainVocab(vocabFile)
            val itp = try {
                obtainInterpreter(modelFile)
            } catch (e: Throwable) {
                releaseLocked()
                throw IOException(
                    "The voice model failed to load — its file may be corrupted; " +
                        "delete and re-download it in settings",
                    e,
                )
            }

            val mel = WhisperMel.compute(pcm, voc.filters, voc.nFft)
            val input = ByteBuffer
                .allocateDirect(WhisperMel.N_MEL * WhisperMel.MEL_LEN * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            for (v in mel) input.putFloat(v)
            input.rewind()

            val outLen = (itp.getOutputTensor(0)?.shape()?.lastOrNull() ?: 451).coerceAtLeast(1)
            val output = Array(1) { IntArray(outLen) }

            val signature = pickSignature(itp, translate)
            try {
                if (signature != null) {
                    itp.runSignature(mapOf(INPUT_NAME to input), mapOf(OUTPUT_NAME to output), signature)
                } else {
                    itp.run(input, output)
                }
            } catch (e: Throwable) {
                releaseLocked()
                throw IOException("Transcription failed — try again or pick a smaller model", e)
            }

            return voc.decode(output[0]).trim()
        } finally {
            lock.unlock()
        }
    }

    /** Frees the cached interpreter. Non-blocking, so IME trim-memory never waits. */
    fun release() {
        if (!lock.tryLock()) return
        try {
            releaseLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun pickSignature(itp: Interpreter, translate: Boolean): String? {
        val keys = runCatching { itp.signatureKeys }.getOrNull()?.toSet() ?: return null
        if (keys.isEmpty()) return null
        val want = if (translate) SIG_TRANSLATE else SIG_TRANSCRIBE
        return when {
            want in keys -> want
            SIG_TRANSCRIBE in keys -> SIG_TRANSCRIBE
            SIG_DEFAULT in keys -> SIG_DEFAULT
            else -> keys.first()
        }
    }

    private fun obtainVocab(vocabFile: File): WhisperVocab {
        vocab?.let { if (loadedVocabPath == vocabFile.path) return it }
        val multilingual = !vocabFile.name.contains("_en", ignoreCase = true)
        return WhisperVocab.load(vocabFile, multilingual).also {
            vocab = it
            loadedVocabPath = vocabFile.path
        }
    }

    private fun obtainInterpreter(modelFile: File): Interpreter {
        interpreter?.let { if (loadedModelPath == modelFile.path) return it }
        releaseLocked()
        val buffer = FileInputStream(modelFile).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, fis.channel.size())
        }
        val options = Interpreter.Options().apply {
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        }
        return Interpreter(buffer, options).also {
            interpreter = it
            loadedModelPath = modelFile.path
        }
    }

    private fun releaseLocked() {
        runCatching { interpreter?.close() }
        interpreter = null
        loadedModelPath = null
    }
}
