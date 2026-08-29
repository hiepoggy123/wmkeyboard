package com.wasimaster.wmkeyboard.core.prediction.ai

import java.util.concurrent.atomic.AtomicBoolean

class V7BiasVectorManager(
    val size: Int = V7GPTTokenizer.VOCAB_SIZE,
    val initialVector: FloatArray? = null
) {
    val biasVector = FloatArray(size) { i ->
        if (initialVector != null && i < initialVector.size) initialVector[i] else 0.0f
    }

    val isDirty = AtomicBoolean(false)

    companion object {
        const val BIAS_INCREMENT_STEP: Float = 50.0f / V7GPTTokenizer.VOCAB_SIZE.toFloat()
        const val MAX_BIAS: Float = 1.0f
    }

    fun updateBias(word: String, tokenizer: V7GPTTokenizer) {
        val id = tokenizer.enumDict[word.lowercase()] ?: return
        if (id in biasVector.indices) {
            synchronized(biasVector) {
                biasVector[id] = minOf(MAX_BIAS, biasVector[id] + BIAS_INCREMENT_STEP)
            }
            isDirty.set(true)
        }
    }

    fun getVectorSnapshot(): FloatArray {
        synchronized(biasVector) {
            return biasVector.copyOf()
        }
    }
}
