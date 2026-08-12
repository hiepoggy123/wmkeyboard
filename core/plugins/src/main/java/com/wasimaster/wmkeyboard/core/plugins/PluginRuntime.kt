package com.wasimaster.wmkeyboard.core.plugins

import com.wasimaster.wmkeyboard.plugins.R
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one plugin at a time, off the keyboard's thread, with a watchdog.
 *
 * The contract the rest of the app relies on: **nothing here can make the
 * keyboard wait.** Every Lua call happens on a dedicated daemon thread, results
 * come back through [Listener], and the worst a broken plugin can do is fail to
 * draw its own panel.
 *
 * ## Stopping a plugin that will not stop
 *
 * Two mechanisms, because one is not enough. [PluginBudget]'s instruction hook
 * catches everything that is executing Lua — an infinite loop dies in
 * microseconds. What it cannot catch is a thread inside luaj's own Java code,
 * where no bytecode boundary is ever crossed; a pathological `string.gsub` is
 * the realistic case. The watchdog covers that: it notices a call that is past
 * its deadline with the hook plainly not firing, and *abandons* the thread.
 *
 * Abandoning is not killing. `Thread.stop` is gone from the platform for good
 * reasons and luaj never polls for interrupts, so a thread spinning in Java is a
 * thread that keeps spinning. What abandonment does is sever it: the session is
 * marked revoked so every `wm.*` call it might still make throws instead of
 * doing anything, its executor is shut down, its priority is dropped, and the
 * runtime forgets it exists. It burns CPU until the process ends and can affect
 * nothing else. A plugin that does this twice is disabled — see
 * [PluginStore.recordAbandon] — because a plugin that hangs the panel twice has
 * used up the benefit of the doubt.
 *
 * ## Threading
 *
 * [open], [dispatch] and [close] are called from the keyboard's thread. Every
 * [Listener] callback is handed to [post], which the host supplies to hop back
 * to its own thread; tests pass a direct runner. Nothing in here calls back into
 * itself, and the listener must not re-enter the runtime synchronously.
 */
class PluginRuntime(
    private val store: PluginStore,
    private val listener: Listener,
    private val limits: Limits = Limits(),
    private val post: (() -> Unit) -> Unit = { it() },
) {

    /**
     * Everything the host needs to hear about. Delivered through [post].
     *
     * The messages arrive as [PluginText] rather than as words, because this
     * runtime has no Context. The host turns one into a line of text with
     * `message.resolve(context)` where it draws it.
     */
    interface Listener {
        fun onUi(pluginId: String, ui: RenderedUi)

        /** A call is taking long enough to be worth a spinner. */
        fun onBusy(pluginId: String, busy: Boolean)

        /** The script failed. The plugin is still loaded and can be retried. */
        fun onError(pluginId: String, message: PluginText)

        /** `wm.ui.set_input` wrote to one of the plugin's own text boxes. */
        fun onInputWrite(pluginId: String, inputId: String, text: String)

        /** The session is over and will not recover. [disabled] means it used its last strike. */
        fun onStopped(pluginId: String, message: PluginText, disabled: Boolean)
    }

    data class Limits(
        val load: PluginLimit = PluginLimit.LOAD,
        val event: PluginLimit = PluginLimit.EVENT,
        val render: PluginLimit = PluginLimit.RENDER,
        val watchdogPeriodMillis: Long = 250,
        val watchdogGraceMillis: Long = 500,
        val busyAfterMillis: Long = 150,
    )

    private val watchdog: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "wm-plugin-watchdog").apply { isDaemon = true }
        }

    private var session: Session? = null

    /** The plugin currently loaded, if any. */
    @get:Synchronized
    val activePluginId: String?
        get() = session?.plugin?.id

    // ---- lifecycle -------------------------------------------------------

    /** Loads a plugin and renders it. Replaces whatever was open. */
    @Synchronized
    fun open(pluginId: String) {
        close()
        val plugin = store.plugin(pluginId)
        if (plugin == null) {
            post { listener.onError(pluginId, PluginText.of(R.string.core_plugins_error_not_installed)) }
            return
        }
        if (!plugin.enabled) {
            post { listener.onError(pluginId, PluginText.of(R.string.core_plugins_error_turned_off)) }
            return
        }
        val script = store.script(pluginId)
        if (script == null) {
            post { listener.onError(pluginId, PluginText.of(R.string.core_plugins_error_script_missing)) }
            return
        }

        val log = PluginLog(store.logFile(pluginId))
        val storage = if (plugin.grants(PluginPermission.Storage)) {
            PluginStorage(store.storageFile(pluginId))
        } else {
            null
        }
        val fresh = Session(plugin, log, storage)
        session = fresh
        fresh.watch = watchdog.scheduleWithFixedDelay(
            { patrol(fresh) },
            limits.watchdogPeriodMillis,
            limits.watchdogPeriodMillis,
            TimeUnit.MILLISECONDS,
        )
        submit(fresh, limits.load) { load(fresh, script) }
    }

    /** Hands a user action to the script, then re-renders. */
    @Synchronized
    fun dispatch(event: PluginEvent) {
        val current = session ?: return
        submit(current, limits.event) { handle(current, event) }
    }

    /** Ends the session. Safe to call when nothing is open. */
    @Synchronized
    fun close() {
        val current = session ?: return
        session = null
        current.revoked = true
        current.budget.cancel()
        current.watch?.cancel(false)
        current.log.flush()
        current.executor.shutdown()
    }

    /** Ends the session and stops the watchdog. For the service's own teardown. */
    @Synchronized
    fun shutdown() {
        close()
        watchdog.shutdownNow()
    }

    // ---- work on the plugin thread ---------------------------------------

    private fun load(current: Session, script: String) {
        val globals = PluginSandbox.create(current.budget) { line -> current.log.add(line) }
        val api = PluginHostApi(
            plugin = current.plugin,
            log = current.log,
            storage = current.storage,
            setInput = { id, text -> current.pendingInputs.add(id to text) },
            revoked = { current.revoked },
        )
        globals.set("wm", api.table())
        PluginSandbox.compile(globals, PluginPrelude.SOURCE, PluginPrelude.CHUNK_NAME).call()
        PluginSandbox.compile(globals, script).call()
        current.globals = globals
        render(current)
    }

    private fun handle(current: Session, event: PluginEvent) {
        val globals = current.globals ?: return
        val handler = globals.get("on_event")
        if (handler.isfunction()) handler.call(eventTable(event))
        flushInputs(current)
        render(current)
    }

    private fun render(current: Session) {
        val globals = current.globals ?: return
        // A fresh allowance every time: drawing is not charged against whatever
        // ran before it, or a plugin that thinks hard once could never draw again.
        current.budget.begin(limits.render)
        val renderFn = globals.get("render")
        if (!renderFn.isfunction()) {
            val message = PluginText.of(R.string.core_plugins_error_no_render)
            deliver(current) { listener.onError(current.plugin.id, message) }
            return
        }
        val ui = PluginUiCodec.fromLua(renderFn.call())
        flushInputs(current)
        deliver(current) { listener.onUi(current.plugin.id, ui) }
    }

    private fun flushInputs(current: Session) {
        if (current.pendingInputs.isEmpty()) return
        val writes = current.pendingInputs.toList()
        current.pendingInputs.clear()
        for ((id, text) in writes) {
            deliver(current) { listener.onInputWrite(current.plugin.id, id, text) }
        }
    }

    private fun eventTable(event: PluginEvent): LuaTable {
        val table = LuaTable()
        table.set("id", event.id)
        when (event) {
            is PluginEvent.Click -> table.set("type", "click")
            is PluginEvent.ToggleChanged -> {
                table.set("type", "toggle")
                table.set("value", LuaValue.valueOf(event.value))
            }

            is PluginEvent.InputChanged -> {
                table.set("type", "input_changed")
                table.set("value", event.value)
            }

            is PluginEvent.TabSelected -> {
                table.set("type", "tab_selected")
                table.set("index", event.index)
            }
        }
        return table
    }

    // ---- scheduling and failure -------------------------------------------

    @Suppress("TooGenericExceptionCaught")
    private fun submit(current: Session, limit: PluginLimit, body: () -> Unit) {
        val finished = AtomicBoolean(false)
        val busy = watchdog.schedule(
            {
                if (!finished.get() && !current.revoked) {
                    deliver(current) { listener.onBusy(current.plugin.id, true) }
                }
            },
            limits.busyAfterMillis,
            TimeUnit.MILLISECONDS,
        )
        val submitted = runCatching {
            current.executor.execute {
                current.thread = Thread.currentThread()
                current.budget.begin(limit)
                try {
                    body()
                } catch (failure: Throwable) {
                    onFailure(current, failure)
                } finally {
                    current.budget.end()
                    finished.set(true)
                    busy.cancel(false)
                    deliver(current) { listener.onBusy(current.plugin.id, false) }
                }
            }
        }.isSuccess
        // The executor is already shutting down: the session is over and there
        // is nobody left to tell.
        if (!submitted) busy.cancel(false)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun onFailure(current: Session, failure: Throwable) {
        when (failure) {
            is PluginAbort -> {
                current.log.add("stopped: ${failure.reason.name.lowercase()}")
                // Cancellation is the host closing the panel, which the user did
                // on purpose and does not need to be told about.
                if (failure.reason != PluginAbortReason.CANCELLED) {
                    stop(current, PluginText.of(failure.reason.messageRes), strike = true)
                }
            }

            is LuaError -> {
                val raw = failure.message?.take(MAX_ERROR)
                current.log.add("error: ${raw ?: "the script failed"}")
                // The Lua virtual machine wrote this, so it goes to the panel as
                // it arrived. Only the stand-in for a missing message is ours.
                val message = raw?.let { PluginText.Script(it) }
                    ?: PluginText.of(R.string.core_plugins_error_script_failed)
                deliver(current) { listener.onError(current.plugin.id, message) }
            }

            is StackOverflowError -> {
                current.log.add("error: stack overflow")
                stop(current, PluginText.of(R.string.core_plugins_stopped_recursion), strike = false)
            }

            else -> {
                // The class name alone is not a bug report. A bare
                // "NoClassDefFoundError" in someone's log says a plugin broke and
                // nothing about what broke it, so the message comes too.
                val detail = failure.message?.take(MAX_ERROR)
                val name = failure.javaClass.simpleName
                current.log.add("error: " + if (detail != null) "$name: $detail" else name)
                stop(current, PluginText.of(R.string.core_plugins_stopped_unexpected), strike = false)
            }
        }
    }

    /** Ends the session for a reason the user should see. */
    private fun stop(current: Session, message: PluginText, strike: Boolean) {
        val disabled = if (strike) store.recordAbandon(current.plugin.id)?.enabled == false else false
        deliverFinal { listener.onStopped(current.plugin.id, message, disabled) }
        synchronized(this) {
            if (session === current) close()
        }
    }

    /**
     * One watchdog tick. Only fires for a thread that is past its deadline and
     * is not in a host call — by which point the instruction hook has plainly
     * not run, so waiting longer would not help.
     */
    private fun patrol(current: Session) {
        if (current.revoked) return
        if (!current.budget.isStuck(limits.watchdogGraceMillis)) return
        abandon(current)
    }

    private fun abandon(current: Session) {
        current.revoked = true
        current.budget.cancel()
        current.watch?.cancel(false)
        current.log.add("abandoned: exceeded its time limit")
        current.log.flush()
        // Severed rather than stopped: it may still be running, but every route
        // from it back into the app now refuses, and it is the lowest-priority
        // daemon on the device.
        runCatching { current.executor.shutdownNow() }
        runCatching { current.thread?.priority = Thread.MIN_PRIORITY }

        val record = store.recordAbandon(current.plugin.id)
        val disabled = record?.enabled == false
        val message = PluginText.of(
            if (disabled) {
                R.string.core_plugins_stopped_not_responding_disabled
            } else {
                R.string.core_plugins_stopped_not_responding
            },
        )
        deliverFinal { listener.onStopped(current.plugin.id, message, disabled) }
        synchronized(this) { if (session === current) session = null }
    }

    /**
     * Delivers an ordinary update, dropped if the session has been superseded —
     * a render that finished after the user closed the panel is noise.
     */
    private fun deliver(current: Session, body: () -> Unit) {
        if (current.revoked) return
        post {
            if (!current.revoked) body()
        }
    }

    /**
     * Delivers a terminal message, always.
     *
     * Deliberately not routed through [deliver]: ending a session is precisely
     * the moment [Session.revoked] gets set, so filtering on it here would drop
     * every "this plugin stopped responding" notice and leave the panel sitting
     * empty forever with nothing to explain why.
     */
    private fun deliverFinal(body: () -> Unit) {
        post(body)
    }

    // ---- one plugin's world -----------------------------------------------

    private class Session(
        val plugin: InstalledPlugin,
        val log: PluginLog,
        val storage: PluginStorage?,
    ) {
        val budget = PluginBudget()

        /** Written on the plugin thread inside a handler, drained straight after. */
        val pendingInputs = ArrayList<Pair<String, String>>()

        @Volatile
        var revoked = false

        @Volatile
        var thread: Thread? = null

        var globals: Globals? = null
        var watch: ScheduledFuture<*>? = null

        val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wm-plugin-${plugin.id}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    private companion object {
        const val MAX_ERROR = 300
    }
}
