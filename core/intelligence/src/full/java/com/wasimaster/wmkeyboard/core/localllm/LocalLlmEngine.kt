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
import com.wasimaster.wmkeyboard.intelligence.R
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
    private var loadedKey: EngineKey? = null

    /**
     * Bumped every time the cached engine is torn down. A [ChatSession]
     * remembers the epoch its conversation was built under and rebuilds —
     * replaying its transcript — when the number has moved on, which is how
     * a chat survives the IME's trim-memory release of the shared engine.
     */
    private var engineEpoch = 0

    /**
     * Conversations that are done but not yet closed. Closed lazily right
     * before the next conversation is created (or on [release]) instead of
     * immediately after their generation finishes — closing the native
     * conversation the instant the completion callback returns raced the
     * runtime's own finalization and crashed the IME process.
     */
    private val retiredConversations = ArrayList<Conversation>()

    /**
     * Conversations a live [ChatSession] still holds. Closed on
     * [releaseLocked] — before the engine, in dependency order — after which
     * the epoch tells their session to rebuild.
     */
    private val sessionConversations = ArrayList<Conversation>()

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
        contextTokens: Int,
        system: String,
        user: String,
        onPartial: ((String) -> Unit)? = null,
    ): String {
        lock.lock()
        try {
            val activeEngine = obtainEngineOrThrow(context, modelFile, backend, contextTokens)
            try {
                val conversation = newConversation(activeEngine, system)
                // Done after this call; close it on the next drain.
                retiredConversations.add(conversation)
                return runMessage(conversation, user, onPartial)
            } catch (e: Throwable) {
                throw generationFailure(context, modelFile, e)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Opens a chat whose context lives across [ChatSession.sendMessage] calls
     * — real multi-turn on the conversation's KV cache, unlike [generate],
     * which starts over every call. Creating the session is free; the engine
     * and the native conversation are built on the first message.
     */
    fun chatSession(
        context: Context,
        modelFile: File,
        backend: LocalLlmBackend,
        contextTokens: Int,
        system: String,
    ): ChatSession = ChatSession(context, modelFile, backend, contextTokens, system)

    /**
     * One live chat. All methods that touch the native runtime take the same
     * global lock as [generate], so a chat turn and a keyboard AI action
     * queue behind each other rather than racing. Blocking — call
     * [sendMessage] on an IO dispatcher only.
     */
    class ChatSession internal constructor(
        private val context: Context,
        private val modelFile: File,
        private val backend: LocalLlmBackend,
        private val contextTokens: Int,
        private val system: String,
    ) {

        private var conversation: Conversation? = null
        private var conversationEpoch = -1
        private val history = ArrayList<ChatReplay.Turn>()

        // Volatile: close() can set it without the lock when a generation is
        // holding it.
        @Volatile
        private var closed = false

        /**
         * Sends one user message and blocks until the full answer is ready,
         * forwarding accumulated text to [onPartial] as it streams (from the
         * native callback thread). When the shared engine was torn down since
         * the last turn — the IME trims memory whenever the keyboard hides —
         * the conversation is rebuilt and the transcript replayed first.
         *
         * Throws [IOException] with a user-presentable message on failure;
         * the session itself stays usable and rebuilds on the next call.
         */
        fun sendMessage(user: String, onPartial: ((String) -> Unit)? = null): String {
            lock.lock()
            try {
                check(!closed) { "Session is closed" }
                val activeEngine =
                    obtainEngineOrThrow(context, modelFile, backend, contextTokens)
                try {
                    var message = user
                    var active = conversation
                    if (active == null || conversationEpoch != engineEpoch) {
                        active = newConversation(activeEngine, system)
                        sessionConversations.add(active)
                        conversation = active
                        conversationEpoch = engineEpoch
                        message = ChatReplay.replayMessage(history, user)
                    }
                    val raw = runMessage(active, message, onPartial)
                    history.add(ChatReplay.Turn(fromUser = true, text = user))
                    history.add(ChatReplay.Turn(fromUser = false, text = stripThink(raw)))
                    return raw
                } catch (e: Throwable) {
                    // releaseLocked inside closed this conversation with the
                    // engine; the epoch check rebuilds it on the next call.
                    conversation = null
                    throw generationFailure(context, modelFile, e)
                }
            } finally {
                lock.unlock()
            }
        }

        /**
         * Pre-fills the replay mirror for a conversation resumed from disk,
         * so the first message replays the saved transcript. Only effective
         * before the first [sendMessage]; a live conversation already holds
         * its own context.
         */
        fun seed(transcript: List<ChatReplay.Turn>) {
            lock.lock()
            try {
                if (conversation == null && history.isEmpty()) history.addAll(transcript)
            } finally {
                lock.unlock()
            }
        }

        /**
         * Records an answer the caller cut short with Stop. The native call
         * ran to completion behind the scenes — a blocking generation cannot
         * be interrupted — so the conversation's KV cache holds the full
         * text; mirror it, not the partial, or a replay would diverge from
         * what the model remembers.
         */
        fun recordStoppedTurn(user: String, fullAnswer: String) {
            lock.lock()
            try {
                if (closed) return
                history.add(ChatReplay.Turn(fromUser = true, text = user))
                history.add(ChatReplay.Turn(fromUser = false, text = stripThink(fullAnswer)))
            } finally {
                lock.unlock()
            }
        }

        /**
         * Retires the session. Non-blocking best effort: when a generation
         * holds the lock the conversation is left for the next drain or
         * [release] to close — never closed immediately, see
         * [retiredConversations].
         */
        fun close() {
            if (!lock.tryLock()) {
                closed = true
                return
            }
            try {
                closed = true
                conversation?.let {
                    sessionConversations.remove(it)
                    retiredConversations.add(it)
                }
                conversation = null
            } finally {
                lock.unlock()
            }
        }

        /**
         * The transcript without reasoning, for the replay mirror. Keeps to
         * the last close tag so both explicit `<think>…</think>` blocks and
         * the close-tag-only shape reasoning templates emit strip the same
         * way. Local copy: this module sits below the one that owns
         * AiThinking.
         */
        private fun stripThink(raw: String): String {
            val close = raw.lastIndexOf("</think>")
            return if (close >= 0) raw.substring(close + "</think>".length).trim()
            else raw.trim()
        }
    }

    /** [obtainEngine] with the load failure mapped for the caller. Lock held. */
    private fun obtainEngineOrThrow(
        context: Context,
        modelFile: File,
        backend: LocalLlmBackend,
        contextTokens: Int,
    ): Engine = try {
        obtainEngine(context, modelFile, backend, contextTokens)
    } catch (e: Throwable) {
        releaseLocked()
        throw IOException(context.getString(R.string.core_intel_llm_engine_error_load), e)
    }

    /** Drains the retired conversations, then opens a new one. Lock held. */
    private fun newConversation(activeEngine: Engine, system: String): Conversation {
        drainRetired()
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
        )
        return activeEngine.createConversation(config)
    }

    /**
     * Sends one message on [conversation] and blocks for the full answer.
     * Streaming goes via the raw MessageCallback overload, NOT the Flow one:
     * litertlm 0.14.0's Flow bridge was compiled against a newer
     * kotlinx-coroutines and its internal SendChannel.close call throws
     * NoSuchMethodError on this project's coroutines — on litertlm's own
     * callback thread, where nothing can catch it, killing the IME the
     * moment a response finished.
     */
    private fun runMessage(
        conversation: Conversation,
        text: String,
        onPartial: ((String) -> Unit)?,
    ): String {
        if (onPartial == null) return textOf(conversation.sendMessage(text))
        val out = StringBuilder()
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        conversation.sendMessageAsync(
            text,
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
    }

    /**
     * Maps a native generation failure to a user-presentable [IOException].
     * Native failures (OOM included) can leave the engine wedged, so the
     * whole cache is torn down. Lock held.
     */
    private fun generationFailure(
        context: Context,
        modelFile: File,
        e: Throwable,
    ): IOException {
        val wasGpu = loadedKey?.backend == "gpu"
        releaseLocked()
        if (wasGpu) {
            // GPU init succeeded but generation blew up — usually GPU
            // memory, which is far tighter than system RAM. Blacklist
            // this model on GPU so the retry runs on CPU without the
            // user having to work out that the backend was the problem.
            gpuFallback += modelFile.path
            return IOException(
                context.getString(R.string.core_intel_llm_engine_error_gpu),
                e,
            )
        }
        return IOException(
            context.getString(R.string.core_intel_llm_engine_error_memory),
            e,
        )
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

    private fun obtainEngine(
        context: Context,
        modelFile: File,
        backend: LocalLlmBackend,
        contextTokens: Int,
    ): Engine {
        val wantGpu = backend == LocalLlmBackend.GPU && modelFile.path !in gpuFallback
        // The context size is part of the key, not just the config: it is baked
        // into the loaded engine, so a cached one built at the old size would
        // quietly ignore a change until the process restarted.
        val key = EngineKey(modelFile.path, if (wantGpu) "gpu" else "cpu", contextTokens)
        engine?.let { if (loadedKey == key) return it }
        releaseLocked()

        if (wantGpu) {
            try {
                return buildEngine(context, modelFile, Backend.GPU(), contextTokens).also {
                    engine = it
                    loadedKey = key
                }
            } catch (_: Throwable) {
                gpuFallback += modelFile.path
            }
        }
        return buildEngine(context, modelFile, Backend.CPU(), contextTokens).also {
            engine = it
            loadedKey = key.copy(backend = "cpu")
        }
    }

    private fun buildEngine(
        context: Context,
        modelFile: File,
        backend: Backend,
        contextTokens: Int,
    ): Engine {
        val cache = File(context.cacheDir, "litertlm").apply { mkdirs() }
        val engine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                cacheDir = cache.path,
                // Null leaves the window to the model. This is the whole window,
                // prompt and answer together — litertlm has no per-response
                // ceiling to set, so this is the only lever there is.
                maxNumTokens = contextTokens.takeIf { it > 0 },
            ),
        )
        engine.initialize()
        return engine
    }

    /** Identifies a loaded engine: the same three inputs that built it. */
    private data class EngineKey(
        val modelPath: String,
        val backend: String,
        val contextTokens: Int,
    )

    /** Closes conversations that are done. Lock held. */
    private fun drainRetired() {
        for (conversation in retiredConversations) {
            runCatching { conversation.close() }
        }
        retiredConversations.clear()
    }

    private fun releaseLocked() {
        drainRetired()
        // Sessions' conversations close before their engine, in dependency
        // order; the epoch bump tells each session to rebuild from its
        // transcript on its next message.
        for (conversation in sessionConversations) {
            runCatching { conversation.close() }
        }
        sessionConversations.clear()
        runCatching { engine?.close() }
        engine = null
        loadedKey = null
        engineEpoch++
    }

    private fun textOf(message: Message): String =
        message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
}
