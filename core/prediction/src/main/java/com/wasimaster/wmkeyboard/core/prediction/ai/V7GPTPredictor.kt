package com.wasimaster.wmkeyboard.core.prediction.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import java.util.Collections
import java.util.PriorityQueue

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
        const val TOP_K_PREDICTIONS = 1000
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

            // 2. Load ONNX Model via mmap from file (zero Java heap allocation)
            val modelFile = File(context.filesDir, "v7gpt_int8.onnx")
            val expectedSize = 15688676L
            if (!modelFile.exists() || modelFile.length() != expectedSize) {
                assets.open("ai/v7gpt_int8.onnx").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val session = env.createSession(modelFile.absolutePath, sessionOptions)

            ortEnv = env
            ortSession = session
            isReady = true
        } catch (t: Throwable) {
            t.printStackTrace()
            isReady = false
        }
    }

    /**
     * Update personalization bias when user commits a word.
     */
    fun updateBias(word: String) {
        if (!isReady || word.isBlank()) return
        try {
            biasManager.updateBias(word, tokenizer)
        } catch (t: Throwable) {
            t.printStackTrace()
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

        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            val shape = longArrayOf(1, tokenIds.size.toLong())
            val buffer = LongBuffer.wrap(tokenIds)
            inputTensor = OnnxTensor.createTensor(env, buffer, shape)

            val inputs = Collections.singletonMap("input_token_ids", inputTensor)
            results = session.run(inputs)

            val tensor = runCatching {
                (results.get("logits").orElse(null)
                    ?: results.iterator().takeIf { it.hasNext() }?.next()?.value) as? OnnxTensor
            }.getOrNull() ?: return IntArray(0)

            val floatBuffer = tensor.floatBuffer
            val vocabSize = minOf(floatBuffer.remaining(), tokenizer.renumList.size)
            if (vocabSize <= 0) return IntArray(0)

            val logits = FloatArray(vocabSize)
            floatBuffer.get(logits)

            val biasVec = biasManager.biasVector

            // Use min-heap to find top K efficiently without sorting all 17,789 items
            val topK = minOf(TOP_K_PREDICTIONS, vocabSize)
            val minHeap = PriorityQueue<IndexedScore>(topK + 1, compareBy { it.score })

            for (i in 0 until vocabSize) {
                val bias = if (i < biasVec.size) biasVec[i] else 0.0f
                val score = (logits[i] + BIAS_ALPHA * bias) / TEMPERATURE
                minHeap.offer(IndexedScore(i, score))
                if (minHeap.size > topK) {
                    minHeap.poll()
                }
            }

            val resultIds = IntArray(minHeap.size)
            var idx = resultIds.size - 1
            while (!minHeap.isEmpty()) {
                resultIds[idx--] = minHeap.poll().index
            }
            return resultIds
        } catch (t: Throwable) {
            t.printStackTrace()
            return IntArray(0)
        } finally {
            try { inputTensor?.close() } catch (_: Throwable) {}
            try { results?.close() } catch (_: Throwable) {}
        }
    }

    private class IndexedScore(val index: Int, val score: Float)
}
