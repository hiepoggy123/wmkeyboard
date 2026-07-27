package com.wasimaster.wmkeyboard.core.voice.whisper

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
 * Three graph shapes exist and the signature keys tell them apart: the plain
 * multilingual graphs auto-detect the language and offer transcribe/translate;
 * the grouped `TOP_WORLD`/`EUROPEAN_UNION` graphs add `serving_transcribe_lang`,
 * which takes a scalar `lang_token` so the caller forces one language; and the
 * single-language and `.en` graphs expose just `serving_default`.
 */
object WhisperEngine {

    const val AVAILABLE = true

    private const val SIG_TRANSCRIBE = "serving_transcribe"
    private const val SIG_TRANSCRIBE_LANG = "serving_transcribe_lang"
    private const val SIG_TRANSLATE = "serving_translate"
    private const val SIG_DEFAULT = "serving_default"
    private const val INPUT_NAME = "input_features"
    private const val INPUT_LANG = "lang_token"
    private const val OUTPUT_NAME = "sequences"

    private val lock = ReentrantLock()
    private var interpreter: Interpreter? = null
    private var loadedModelPath: String? = null
    private var vocab: WhisperVocab? = null
    private var loadedVocabPath: String? = null

    /**
     * Transcribes one PCM utterance (16 kHz mono float in [-1, 1]) to text.
     * [translate] forces the translate-to-English task where the model supports
     * it. [langToken] is the Whisper `<|xx|>` token id to force on a grouped
     * graph — null (or a graph without the input) means auto-detect. Call on a
     * background dispatcher only. Throws [IOException] with a user-presentable
     * message on failure.
     */
    fun transcribe(
        modelFile: File,
        vocabFile: File,
        pcm: FloatArray,
        translate: Boolean,
        langToken: Int? = null,
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

            requireMelBins(itp)

            val mel = WhisperMel.compute(pcm, voc.filters, voc.nFft)
            val input = ByteBuffer
                .allocateDirect(WhisperMel.N_MEL * WhisperMel.MEL_LEN * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            for (v in mel) input.putFloat(v)
            input.rewind()

            val outLen = (itp.getOutputTensor(0)?.shape()?.lastOrNull() ?: 451).coerceAtLeast(1)
            val output = Array(1) { IntArray(outLen) }

            val signature = pickSignature(itp, translate, langToken != null)
            try {
                when {
                    signature == SIG_TRANSCRIBE_LANG -> itp.runSignature(
                        mapOf(INPUT_NAME to input, INPUT_LANG to scalarInt(requireNotNull(langToken))),
                        mapOf(OUTPUT_NAME to output),
                        signature,
                    )
                    signature != null -> itp.runSignature(
                        mapOf(INPUT_NAME to input), mapOf(OUTPUT_NAME to output), signature,
                    )
                    else -> itp.run(input, output)
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

    /**
     * Fails a model whose spectrogram input is not the 80-bin one [WhisperMel] and
     * the shipped `filters_vocab_*.bin` filterbanks produce. Whisper switched to
     * 128 bins at large-v3, and such a graph would otherwise happily consume the
     * wrong-shaped features and decode confident nonsense instead of erroring.
     */
    private fun requireMelBins(itp: Interpreter) {
        // A grouped graph's tensor 0 is the scalar lang_token, whose shape has no
        // mel axis at all — nothing to check there.
        val bins = runCatching { itp.getInputTensor(0)?.shape() }.getOrNull()
            ?.takeIf { it.size >= 3 }?.get(1)
            ?: return
        if (bins > 0 && bins != WhisperMel.N_MEL) {
            releaseLocked()
            throw IOException(
                "This voice model expects a $bins-band spectrogram, which this " +
                    "build cannot produce — pick a different model in settings",
            )
        }
    }

    /**
     * Translating always wins over forcing a language — the translate task
     * detects the source itself, and no graph offers both at once.
     */
    private fun pickSignature(itp: Interpreter, translate: Boolean, forceLang: Boolean): String? {
        val keys = runCatching { itp.signatureKeys }.getOrNull()?.toSet() ?: return null
        if (keys.isEmpty()) return null
        return when {
            translate && SIG_TRANSLATE in keys -> SIG_TRANSLATE
            forceLang && SIG_TRANSCRIBE_LANG in keys -> SIG_TRANSCRIBE_LANG
            SIG_TRANSCRIBE in keys -> SIG_TRANSCRIBE
            SIG_DEFAULT in keys -> SIG_DEFAULT
            else -> keys.first()
        }
    }

    /** The `lang_token` scalar as a raw 4-byte buffer, which fits its `()` int32 spec. */
    private fun scalarInt(value: Int): ByteBuffer =
        ByteBuffer.allocateDirect(Int.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
            putInt(value)
            rewind()
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
