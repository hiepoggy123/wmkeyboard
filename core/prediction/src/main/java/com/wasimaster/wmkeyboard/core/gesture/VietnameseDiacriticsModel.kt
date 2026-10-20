package com.wasimaster.wmkeyboard.core.gesture

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.io.InputStream
import java.text.Normalizer
import java.util.Collections

/**
 * On-device neural Vietnamese diacritics restoration model powered by ONNX Runtime.
 *
 * Restores diacritics / tones for unaccented Vietnamese words or glide gestures,
 * leveraging contextual awareness from preceding words.
 */
object VietnameseDiacriticsModel {

    private const val MODEL_ASSET_PATH = "models/vi/vi_diacritics_int8.onnx"
    private const val VOCAB_ASSET_PATH = "models/vi/vocab.json"
    private const val UNK_ID = 1L
    private const val PAD_ID = 0L

    @Volatile
    private var env: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var char2idx: Map<Char, Long> = emptyMap()

    @Volatile
    private var idx2char: Map<Long, Char> = emptyMap()

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Initializes the model from application assets if available.
     * Safe against exceptions (e.g. missing assets or unsupported environments).
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isReady) return
        runCatching {
            val assets = context.assets
            val vocabString = assets.open(VOCAB_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
            loadVocab(vocabString)

            val modelBytes = assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
            val environment = OrtEnvironment.getEnvironment().also { env = it }
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = environment.createSession(modelBytes, options)
            isReady = true
        }
    }

    /**
     * Direct initialization from an input stream and vocab JSON string.
     */
    @Synchronized
    fun initialize(modelStream: InputStream, vocabJson: String) {
        if (isReady) return
        runCatching {
            loadVocab(vocabJson)
            val modelBytes = modelStream.use { it.readBytes() }
            val environment = OrtEnvironment.getEnvironment().also { env = it }
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = environment.createSession(modelBytes, options)
            isReady = true
        }
    }

    private fun loadVocab(vocabJson: String) {
        val root = JSONObject(vocabJson)
        val c2iObj = root.optJSONObject("char2idx")
        val i2cObj = root.optJSONObject("idx2char")

        val c2i = HashMap<Char, Long>()
        c2iObj?.keys()?.forEach { key ->
            if (key.length == 1) {
                c2i[key[0]] = c2iObj.getLong(key)
            }
        }
        char2idx = c2i

        val i2c = HashMap<Long, Char>()
        i2cObj?.keys()?.forEach { key ->
            val charStr = i2cObj.getString(key)
            if (charStr.length == 1) {
                key.toLongOrNull()?.let { id ->
                    i2c[id] = charStr[0]
                }
            }
        }
        idx2char = i2c
    }

    /**
     * Strips diacritics / tones from a Vietnamese string, converting 'đ'/'Đ' to 'd'/'D'.
     */
    fun stripDiacritics(s: String): String {
        if (s.isEmpty()) return s
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            val type = Character.getType(ch)
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt()
            ) {
                when (ch) {
                    'đ' -> sb.append('d')
                    'Đ' -> sb.append('D')
                    else -> sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Restores Vietnamese diacritics on [candidate] using the trained neural model,
     * conditioned on [context] (e.g. preceding words in the sentence).
     */
    fun restore(candidate: String, context: String? = null): String {
        val activeSession = session
        val activeEnv = env
        if (!isReady || activeSession == null || activeEnv == null || candidate.isEmpty()) {
            return candidate
        }

        return runCatching {
            val cleanCtx = context?.trim()?.let { stripDiacritics(it) }?.takeIf { it.isNotEmpty() }
            val shortCtx = cleanCtx?.split(' ')?.filter { it.isNotEmpty() }?.takeLast(2)?.joinToString(" ")
            val inputStr = if (!shortCtx.isNullOrEmpty()) "$shortCtx $candidate" else candidate

            val inputIds = LongArray(inputStr.length) { char2idx[inputStr[it]] ?: UNK_ID }
            val predChars = CharArray(inputStr.length)

            OnnxTensor.createTensor(activeEnv, arrayOf(inputIds)).use { tensor ->
                activeSession.run(Collections.singletonMap("input_ids", tensor)).use { results ->
                    @Suppress("UNCHECKED_CAST")
                    val outputVal = results.get(0).value as Array<Array<FloatArray>>
                    val logits = outputVal[0]

                    for (i in inputStr.indices) {
                        val stepLogits = logits[i]
                        var bestClass = 0
                        var maxLogit = Float.NEGATIVE_INFINITY
                        for (c in stepLogits.indices) {
                            val logit = stepLogits[c]
                            if (logit > maxLogit) {
                                maxLogit = logit
                                bestClass = c
                            }
                        }
                        predChars[i] = if (bestClass.toLong() <= UNK_ID) {
                            inputStr[i]
                        } else {
                            idx2char[bestClass.toLong()] ?: inputStr[i]
                        }
                    }
                }
            }

            val predicted = String(predChars)
            val result = if (!shortCtx.isNullOrEmpty() && predicted.length >= shortCtx.length + 1) {
                predicted.substring(shortCtx.length + 1)
            } else {
                predicted
            }

            if (candidate.first().isUpperCase() && result.isNotEmpty()) {
                result.replaceFirstChar { it.uppercaseChar() }
            } else {
                result
            }
        }.getOrDefault(candidate)
    }
}
