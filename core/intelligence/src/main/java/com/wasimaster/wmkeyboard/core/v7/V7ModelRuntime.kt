package com.wasimaster.wmkeyboard.core.v7

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.InputStream
import java.nio.LongBuffer

/**
 * On-device ONNX runtime runner for the V7 GPT model.
 */
class V7ModelRuntime private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession
) : AutoCloseable {

    @Synchronized
    fun predictLogits(tokenIds: LongArray): FloatArray {
        // Model expects input tensor of shape (1, 32)
        val contextSize = 32
        val inputBuffer = LongBuffer.allocate(contextSize)
        val padCount = maxOf(0, contextSize - tokenIds.size)
        for (i in 0 until padCount) {
            inputBuffer.put(0L)
        }
        val startIdx = maxOf(0, tokenIds.size - contextSize)
        for (i in startIdx until tokenIds.size) {
            inputBuffer.put(tokenIds[i])
        }
        inputBuffer.flip()

        val tensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(1, contextSize.toLong()))
        tensor.use {
            val outputs = session.run(mapOf("input_token_ids" to tensor))
            outputs.use {
                val outputValue = outputs[0].value
                @Suppress("UNCHECKED_CAST")
                val logits2D = outputValue as Array<FloatArray>
                return logits2D[0]
            }
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val MODEL_ASSET = "v7/v7gpt_int8.onnx"

        @Volatile
        private var instance: V7ModelRuntime? = null

        fun getInstance(context: Context): V7ModelRuntime {
            return instance ?: synchronized(this) {
                instance ?: loadFromAssets(context).also { instance = it }
            }
        }

        private fun loadFromAssets(context: Context): V7ModelRuntime {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            context.assets.open(MODEL_ASSET).use { inputStream ->
                val bytes = inputStream.readBytes()
                val session = env.createSession(bytes, opts)
                return V7ModelRuntime(env, session)
            }
        }
    }
}
