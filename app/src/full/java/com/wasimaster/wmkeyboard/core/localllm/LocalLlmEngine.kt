package com.wasimaster.wmkeyboard.core.localllm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.wasimaster.wmkeyboard.core.settings.LocalLlmBackend
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

/**
 * Full-flavor wrapper around the LiteRT-LM [Engine]. Engine initialization
 * takes seconds and pins the model weights in memory, so one engine is
 * cached across requests and only rebuilt when the model file or backend
 * changes. The lite flavor replaces this object with a stub that throws.
 */
object LocalLlmEngine {

    const val AVAILABLE = true

    private val lock = ReentrantLock()
    private var engine: Engine? = null
    private var loadedKey: Pair<String, String>? = null

    /**
     * The previous request's conversation, closed lazily right before the
     * next one is created (or on [release]) instead of immediately after its
     * generation finishes — closing the native conversation the instant the
     * completion callback returns raced the runtime's own finalization and
     * crashed the IME process.
     */
    private var staleConversation: Conversation? = null

    /** Model paths whose GPU init already failed once — go straight to CPU. */
    private val gpuFallback = mutableSetOf<String>()

    /**
     * Runs one system+user exchange on the local model, blocking until the
     * full response is ready. Call on an IO dispatcher only. When [onPartial]
     * is set, the accumulated response text is forwarded as it streams.
     *
     * Throws [IOException] with a user-presentable message on any failure.
     */
    fun generate(
        context: Context,
        modelFile: File,
        backend: LocalLlmBackend,
        system: String,
        user: String,
        onPartial: ((String) -> Unit)? = null,
    ): String {
        lock.lock()
        try {
            val activeEngine = try {
                obtainEngine(context, modelFile, backend)
            } catch (e: Throwable) {
                releaseLocked()
                throw IOException(
                    "The model failed to load — its file may be corrupted; " +
                        "delete and re-download it in settings",
                    e,
                )
            }
            try {
                runCatching { staleConversation?.close() }
                staleConversation = null
                val config = ConversationConfig(
                    systemInstruction = Contents.of(system),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
                )
                val conversation = activeEngine.createConversation(config)
                staleConversation = conversation
                if (onPartial == null) return textOf(conversation.sendMessage(user))
                // Streaming via the raw MessageCallback overload, NOT the
                // Flow one: litertlm 0.14.0's Flow bridge was compiled
                // against a newer kotlinx-coroutines and its internal
                // SendChannel.close call throws NoSuchMethodError on this
                // project's coroutines — on litertlm's own callback thread,
                // where nothing can catch it, killing the IME the moment a
                // response finished.
                val out = StringBuilder()
                val done = CountDownLatch(1)
                var failure: Throwable? = null
                conversation.sendMessageAsync(
                    user,
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            // Never let an exception escape into native code.
                            runCatching {
                                out.append(textOf(message))
                                onPartial(out.toString())
                            }
                        }

                        override fun onDone() {
                            done.countDown()
                        }

                        override fun onError(throwable: Throwable) {
                            failure = throwable
                            done.countDown()
                        }
                    },
                )
                done.await()
                failure?.let { throw it }
                return out.toString()
            } catch (e: Throwable) {
                // Native failures (OOM included) can leave the engine wedged.
                releaseLocked()
                throw IOException(
                    "The model ran out of memory or crashed — try a smaller model", e,
                )
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Frees the cached engine. Non-blocking: when a generation is running the
     * weights are in use anyway, so the release is skipped rather than making
     * the caller (IME main thread on trim-memory) wait.
     */
    fun release() {
        if (!lock.tryLock()) return
        try {
            releaseLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun obtainEngine(context: Context, modelFile: File, backend: LocalLlmBackend): Engine {
        val wantGpu = backend == LocalLlmBackend.GPU && modelFile.path !in gpuFallback
        val key = modelFile.path to (if (wantGpu) "gpu" else "cpu")
        engine?.let { if (loadedKey == key) return it }
        releaseLocked()

        if (wantGpu) {
            try {
                return buildEngine(context, modelFile, Backend.GPU()).also {
                    engine = it
                    loadedKey = key
                }
            } catch (_: Throwable) {
                gpuFallback += modelFile.path
            }
        }
        return buildEngine(context, modelFile, Backend.CPU()).also {
            engine = it
            loadedKey = modelFile.path to "cpu"
        }
    }

    private fun buildEngine(context: Context, modelFile: File, backend: Backend): Engine {
        val cache = File(context.cacheDir, "litertlm").apply { mkdirs() }
        val engine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                cacheDir = cache.path,
            ),
        )
        engine.initialize()
        return engine
    }

    private fun releaseLocked() {
        runCatching { staleConversation?.close() }
        staleConversation = null
        runCatching { engine?.close() }
        engine = null
        loadedKey = null
    }

    private fun textOf(message: Message): String =
        message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
}
