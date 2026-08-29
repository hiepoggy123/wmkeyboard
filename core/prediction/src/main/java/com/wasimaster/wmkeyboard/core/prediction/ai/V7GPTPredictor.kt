package com.wasimaster.wmkeyboard.core.prediction.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.Collections

class V7GPTPredictor private constructor() {

    companion object {
        @Volatile
        private var instance: V7GPTPredictor? = null

        fun getInstance(): V7GPTPredictor {
            return instance ?: synchronized(this) {
                instance ?: V7GPTPredictor().also { instance = it }
            }
        }

        const val BIAS_ALPHA = 0.3f
        const val TEMPERATURE = 1.0f
        const val TOP_K_PREDICTIONS = 3000
    }

    val tokenizer = V7GPTTokenizer()
    val biasManager = V7BiasVectorManager()

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    @Volatile
    var isReady = false
        private set

    // Single-pass probability distribution cache
    private var lastContext: String? = null
    private var lastPredictionIds: IntArray? = null

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        try {
            val assets = context.assets

            // 1. Load Tokenizer files
            assets.open("ai/enum_21869.bin").use { enumStream ->
                assets.open("ai/token_strings_21869.bin").use { tokenStream ->
                    assets.open("ai/token_tones.bin").use { toneStream ->
                        tokenizer.load(enumStream, tokenStream, toneStream)
                    }
                }
            }

            // 2. Load ONNX Model
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val modelBytes = assets.open("ai/v7gpt_int8.onnx").use { it.readBytes() }
            val session = env.createSession(modelBytes, sessionOptions)

            ortEnv = env
            ortSession = session
            isReady = true
        } catch (e: Exception) {
            e.printStackTrace()
            isReady = false
        }
    }

    /**
     * Predict next words or shorthand completions given preceding context and optional prefix/tone.
     */
    fun predict(
        contextText: String,
        prefix: String = "",
        toneMark: String = "",
        maxResults: Int = 5
    ): List<String> {
        if (!isReady) return emptyList()

        val cleanContext = contextText.trimEnd()
        val predictionIds = synchronized(this) {
            if (cleanContext == lastContext && lastPredictionIds != null) {
                lastPredictionIds!!
            } else {
                val computed = computePredictions(cleanContext)
                lastContext = cleanContext
                lastPredictionIds = computed
                computed
            }
        }

        if (predictionIds.isEmpty()) return emptyList()

        return tokenizer.filter(
            pattern = prefix,
            predictionIds = predictionIds,
            toneMark = toneMark,
            maxResults = maxResults
        )
    }

    private fun computePredictions(contextText: String): IntArray {
        val session = ortSession ?: return IntArray(0)
        val env = ortEnv ?: return IntArray(0)

        val tokenIds = if (contextText.isEmpty()) {
            tokenizer.tokenize("vậy")
        } else {
            tokenizer.tokenize(contextText)
        }

        if (tokenIds.isEmpty()) return IntArray(0)

        try {
            val shape = longArrayOf(1, tokenIds.size.toLong())
            val buffer = LongBuffer.wrap(tokenIds)
            val inputTensor = OnnxTensor.createTensor(env, buffer, shape)

            val inputs = Collections.singletonMap("input_token_ids", inputTensor)
            val results = session.run(inputs)

            @Suppress("UNCHECKED_CAST")
            val outputTensor = results[0].value as Array<FloatArray>
            val logits = outputTensor[0] // shape: [vocab_size, 17789]

            inputTensor.close()
            results.close()

            val biasVec = biasManager.biasVector
            val vocabSize = minOf(logits.size, tokenizer.renumList.size)

            // Index-score pairs
            val scored = ArrayList<IndexedScore>(vocabSize)
            for (i in 0 until vocabSize) {
                val bias = if (i < biasVec.size) biasVec[i] else 0.0f
                val score = (logits[i] + BIAS_ALPHA * bias) / TEMPERATURE
                scored.add(IndexedScore(i, score))
            }

            scored.sortByDescending { it.score }

            val topCount = minOf(TOP_K_PREDICTIONS, scored.size)
            val resultIds = IntArray(topCount)
            for (i in 0 until topCount) {
                resultIds[i] = scored[i].index
            }
            return resultIds
        } catch (e: Exception) {
            e.printStackTrace()
            return IntArray(0)
        }
    }

    fun updateBias(word: String) {
        biasManager.updateBias(word, tokenizer)
    }

    private data class IndexedScore(val index: Int, val score: Float)
}
