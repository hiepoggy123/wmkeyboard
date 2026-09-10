package com.wasimaster.wmkeyboard.core.plugins

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The runtime as the plugin editor drives it: a [PluginRuntime.Draft] nobody
 * installed, watched through a [PluginRuntime.Observer]. Real threads, for the
 * same reason [PluginRuntimeTest] uses them.
 */
class PluginRuntimeDraftTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: PluginStore
    private var runtime: PluginRuntime? = null

    private val lock = Any()
    private val events = ArrayList<String>()
    private val uis = ArrayList<RenderedUi>()
    private val prints = ArrayList<String>()
    private val phases = ArrayList<Pair<PluginRuntime.Phase, PluginBudget.Usage>>()
    private val failures = ArrayList<Pair<String, PluginRuntime.Failure>>()
    private val observedEvents = ArrayList<PluginEvent>()
    private val messages = ArrayList<PluginText>()

    private val listener = object : PluginRuntime.Listener {
        override fun onUi(pluginId: String, ui: RenderedUi) = synchronized(lock) {
            uis += ui
            events += "ui"
        }

        override fun onBusy(pluginId: String, busy: Boolean) = Unit

        override fun onError(pluginId: String, message: PluginText) = synchronized(lock) {
            messages += message
            events += "error"
        }

        override fun onInputWrite(pluginId: String, inputId: String, text: String) = Unit

        override fun onStopped(pluginId: String, message: PluginText, disabled: Boolean) = synchronized(lock) {
            messages += message
            events += "stopped:disabled=$disabled"
        }
    }

    private val observer = object : PluginRuntime.Observer {
        override fun onPrint(pluginId: String, line: String) {
            synchronized(lock) { prints += line }
        }

        override fun onPhase(pluginId: String, phase: PluginRuntime.Phase, usage: PluginBudget.Usage) {
            synchronized(lock) { phases += phase to usage }
        }

        override fun onFailure(pluginId: String, failure: PluginRuntime.Failure) {
            synchronized(lock) {
                failures += pluginId to failure
                events += "failure:${failure.kind}"
            }
        }

        override fun onEvent(pluginId: String, event: PluginEvent) {
            synchronized(lock) { observedEvents += event }
        }
    }

    @After
    fun tearDown() {
        runtime?.shutdown()
    }

    private fun runtime(limits: PluginRuntime.Limits = PluginRuntime.Limits()): PluginRuntime {
        store = PluginStore(temp.newFolder())
        return PluginRuntime(store, listener, limits, observer = observer).also { runtime = it }
    }

    private fun plugin(id: String = ID, permissions: List<String> = emptyList()) =
        InstalledPlugin(id = id, name = "Draft", version = "0.1", permissions = permissions)

    private fun draft(
        script: String,
        id: String = ID,
        storage: PluginStorage? = null,
        permissions: List<String> = emptyList(),
    ) = PluginRuntime.Draft(plugin(id, permissions), script, PluginLog(null), storage)

    private fun install(script: String) {
        store.adopt(
            PluginManifest(format = PluginManifestCodec.FORMAT, id = ID, name = "Installed", pluginVersion = "1.0.0"),
            script,
        )
    }

    private fun waitUntil(timeoutMillis: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            synchronized(lock) { if (predicate()) return }
            Thread.sleep(20)
        }
        synchronized(lock) { throw AssertionError("timed out; events so far: $events") }
    }

    private fun lastFailure(): PluginRuntime.Failure = synchronized(lock) { failures.last().second }

    @Test
    fun `a draft renders without going near the store`() {
        val engine = runtime()
        engine.open(draft("function render() return ui.label { text = 'drafted' } end"))
        waitUntil { uis.isNotEmpty() }
        assertEquals("drafted", synchronized(lock) { (uis.last().root.single() as PluginWidget.Label).text })
        assertTrue(store.plugins().isEmpty())
        assertEquals(ID, engine.activePluginId)
    }

    @Test
    fun `a runaway draft earns no strike against the installed plugin with its id`() {
        val engine = runtime(PluginRuntime.Limits(load = PluginLimit(instructions = 200_000, wallMillis = 5_000)))
        install("function render() return ui.label { text = 'installed' } end")

        engine.open(draft("while true do end"))
        waitUntil { events.any { it.startsWith("stopped") } }

        assertTrue(synchronized(lock) { "stopped:disabled=false" in events })
        assertEquals(PluginRuntime.Failure.Kind.INSTRUCTIONS, lastFailure().kind)
        assertEquals(0, store.plugin(ID)!!.abandonedCount)
        assertTrue(store.plugin(ID)!!.enabled)
    }

    @Test
    fun `an abandoned draft earns no strike either`() {
        val engine = runtime(
            PluginRuntime.Limits(
                load = PluginLimit(instructions = Long.MAX_VALUE, wallMillis = 200),
                watchdogPeriodMillis = 50,
                watchdogGraceMillis = 100,
            ),
        )
        install("function render() return ui.label { text = 'installed' } end")
        val stuck = "local s = ('x'):rep(4000)\ns:match('.-.-.-.-.-.-.-.-.-.-zzz')"

        engine.open(draft(stuck))
        waitUntil { events.any { it.startsWith("stopped") } }
        engine.open(draft(stuck))
        waitUntil { events.count { it.startsWith("stopped") } >= 2 }

        // Counted rather than read off the last failure: a severed thread can still
        // surface its cancellation after the second run was abandoned.
        assertTrue(synchronized(lock) { failures.count { it.second.kind == PluginRuntime.Failure.Kind.ABANDONED } } >= 2)
        assertEquals(0, store.plugin(ID)!!.abandonedCount)
        assertTrue(store.plugin(ID)!!.enabled)
        assertFalse(synchronized(lock) { "stopped:disabled=true" in events })
    }

    @Test
    fun `a draft writes to the storage it was given and no other`() {
        val engine = runtime()
        install("function render() return ui.label { text = 'installed' } end")
        val file = temp.newFile("draft-storage.json")
        val storage = PluginStorage(file)

        engine.open(
            draft(
                "wm.storage.set('k', 'from the draft')\nfunction render() return ui.label { text = 'x' } end",
                storage = storage,
                permissions = listOf("storage"),
            ),
        )
        waitUntil { uis.isNotEmpty() }

        assertEquals("from the draft", storage.get("k"))
        assertTrue(file.readText().contains("from the draft"))
        assertFalse(store.storageFile(ID)!!.exists())
    }

    @Test
    fun `print and wm log both reach the observer in order`() {
        val engine = runtime()
        engine.open(draft("print('a', 1)\nwm.log('b')\nfunction render() print('c') return ui.label { text = 'x' } end"))
        waitUntil { uis.isNotEmpty() }
        assertEquals(listOf("a\t1", "b", "c"), synchronized(lock) { prints.toList() })
    }

    @Test
    fun `a syntax error is observed as a compile failure at its line`() {
        val engine = runtime()
        engine.open(draft("local a = 1\nlocal b = = 2\nfunction render() return ui.label { text = 'x' } end"))
        waitUntil { events.any { it.startsWith("failure") } }

        val failure = lastFailure()
        assertEquals(PluginRuntime.Failure.Kind.COMPILE, failure.kind)
        assertEquals(PluginRuntime.Phase.LOAD, failure.phase)
        assertEquals("main.lua", failure.chunk)
        assertEquals(2, failure.line)
        // The keyboard still hears it the way it always did.
        waitUntil { events.contains("error") }
        assertTrue(synchronized(lock) { messages.last() } is PluginText.Script)
    }

    @Test
    fun `a runtime error is observed at its line in the phase it happened`() {
        val engine = runtime()
        engine.open(draft("function render()\n  local t = nil\n  return t.x\nend"))
        waitUntil { events.any { it.startsWith("failure") } }

        val failure = lastFailure()
        assertEquals(PluginRuntime.Failure.Kind.RUNTIME, failure.kind)
        assertEquals(PluginRuntime.Phase.RENDER, failure.phase)
        assertEquals("main.lua", failure.chunk)
        assertEquals(3, failure.line)
        waitUntil { events.contains("error") }
        val shown = synchronized(lock) { messages.last() } as PluginText.Script
        assertTrue("the observer's text is cut short", failure.text.length >= shown.text.length)
    }

    @Test
    fun `each phase reports what it spent`() {
        val engine = runtime()
        engine.open(draft("function on_event(e) end\nfunction render() return ui.label { text = 'x' } end"))
        waitUntil { phases.map { it.first } == listOf(PluginRuntime.Phase.LOAD, PluginRuntime.Phase.RENDER) }

        engine.dispatch(PluginEvent.Click("go"))
        waitUntil { phases.size >= 4 }

        val seen = synchronized(lock) { phases.toList() }
        assertEquals(
            listOf(PluginRuntime.Phase.LOAD, PluginRuntime.Phase.RENDER, PluginRuntime.Phase.EVENT, PluginRuntime.Phase.RENDER),
            seen.map { it.first },
        )
        assertEquals(PluginLimit.LOAD.instructions, seen[0].second.instructionLimit)
        assertEquals(PluginLimit.RENDER.instructions, seen[1].second.instructionLimit)
        assertEquals(PluginLimit.EVENT.instructions, seen[2].second.instructionLimit)
        assertTrue(seen.all { !it.second.running && it.second.instructions > 0 })
    }

    @Test
    fun `an event is observed as it arrives`() {
        val engine = runtime()
        engine.open(draft("function on_event(e) end\nfunction render() return ui.label { text = 'x' } end"))
        waitUntil { uis.isNotEmpty() }
        engine.dispatch(PluginEvent.ToggleChanged("wrap", true))
        waitUntil { observedEvents.isNotEmpty() }
        assertEquals(PluginEvent.ToggleChanged("wrap", true), synchronized(lock) { observedEvents.single() })
    }

    @Test
    fun `a run replaced mid-loop is observed as cancelled`() {
        val engine = runtime()
        engine.open(draft("while true do end", id = "com.example.first"))
        Thread.sleep(100)
        engine.open(draft("function render() return ui.label { text = 'second' } end", id = "com.example.second"))
        waitUntil { failures.any { it.first == "com.example.first" } && uis.isNotEmpty() }

        val first = synchronized(lock) { failures.first { it.first == "com.example.first" }.second }
        assertEquals(PluginRuntime.Failure.Kind.CANCELLED, first.kind)
        assertFalse(synchronized(lock) { events.any { it.startsWith("stopped") } })
    }

    @Test
    fun `locate reads the chunk and line off the first line only`() {
        assertEquals("main.lua" to 12, PluginRuntime.locate("main.lua:12: boom"))
        assertEquals("main.lua" to 3, PluginRuntime.locate("@main.lua:3 attempt to index nil"))
        assertEquals("prelude.lua" to 5, PluginRuntime.locate("prelude.lua:5: bad argument"))
        assertEquals(null to null, PluginRuntime.locate("stack traceback:\n  main.lua:9: in function"))
        assertEquals(null to null, PluginRuntime.locate("no location here"))
        val (chunk, line) = PluginRuntime.locate("")
        assertNull(chunk)
        assertNull(line)
    }

    private companion object {
        const val ID = "com.example.draft"
    }
}
