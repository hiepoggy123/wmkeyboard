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
 * The runtime, driven with real threads.
 *
 * Not fast tests, but the things worth proving here — that a runaway script is
 * contained, that a stuck one is abandoned, that a second offence disables the
 * plugin — are all about real concurrency, and a fake executor would prove none
 * of them.
 */
class PluginRuntimeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: PluginStore
    private var runtime: PluginRuntime? = null

    private val lock = Any()
    private val events = ArrayList<String>()
    private val uis = ArrayList<RenderedUi>()

    private val listener = object : PluginRuntime.Listener {
        override fun onUi(pluginId: String, ui: RenderedUi) = synchronized(lock) {
            uis += ui
            events += "ui"
        }

        override fun onBusy(pluginId: String, busy: Boolean) = Unit

        override fun onError(pluginId: String, message: String) {
            synchronized(lock) { events += "error:$message" }
        }

        override fun onInputWrite(pluginId: String, inputId: String, text: String) {
            synchronized(lock) { events += "input:$inputId=$text" }
        }

        override fun onStopped(pluginId: String, message: String, disabled: Boolean) {
            synchronized(lock) { events += "stopped:disabled=$disabled" }
        }
    }

    @After
    fun tearDown() {
        runtime?.shutdown()
    }

    // ---- helpers ---------------------------------------------------------

    private fun runtime(limits: PluginRuntime.Limits = PluginRuntime.Limits()): PluginRuntime {
        store = PluginStore(temp.newFolder("plugins-${counter++}"))
        return PluginRuntime(store, listener, limits).also { runtime = it }
    }

    private fun install(
        script: String,
        id: String = "com.example.demo",
        permissions: List<String> = emptyList(),
    ): String {
        store.adopt(
            PluginManifest(
                format = PluginManifestCodec.FORMAT,
                id = id,
                name = "Demo",
                pluginVersion = "1.0.0",
                permissions = permissions,
            ),
            script,
        )
        return id
    }

    /** Polls rather than latching: callbacks arrive on threads we do not control. */
    private fun waitUntil(timeoutMillis: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            synchronized(lock) { if (predicate()) return }
            Thread.sleep(20)
        }
        synchronized(lock) { throw AssertionError("timed out; events so far: $events") }
    }

    private fun awaitUis(count: Int) = waitUntil { uis.size >= count }

    private fun awaitTag(prefix: String) = waitUntil { events.any { it.startsWith(prefix) } }

    private fun reset() = synchronized(lock) {
        events.clear()
        uis.clear()
    }

    private fun lastLabel(): String =
        synchronized(lock) { (uis.last().root.single() as PluginWidget.Label).text }

    // ---- the happy path ---------------------------------------------------

    @Test
    fun `opening a plugin renders it`() {
        val engine = runtime()
        install("function render() return ui.column { ui.label { text = 'hello' } } end")
        engine.open("com.example.demo")
        awaitUis(1)

        val column = synchronized(lock) { uis.last().root.single() as PluginWidget.Column }
        assertEquals("hello", (column.children.single() as PluginWidget.Label).text)
    }

    @Test
    fun `an event reaches the script and the result is re-rendered`() {
        val engine = runtime()
        install(
            """
            local count = 0
            function on_event(e)
              if e.type == "click" then count = count + 1 end
            end
            function render() return ui.label { text = "clicks: " .. count } end
            """.trimIndent(),
        )
        engine.open("com.example.demo")
        awaitUis(1)

        engine.dispatch(PluginEvent.Click("go"))
        awaitUis(2)
        engine.dispatch(PluginEvent.Click("go"))
        awaitUis(3)

        assertEquals("clicks: 2", lastLabel())
    }

    @Test
    fun `an input event carries its text`() {
        val engine = runtime()
        install(
            """
            local typed = ""
            function on_event(e)
              if e.type == "input_changed" then typed = e.value end
            end
            function render() return ui.label { text = typed } end
            """.trimIndent(),
        )
        engine.open("com.example.demo")
        awaitUis(1)

        engine.dispatch(PluginEvent.InputChanged("msg", "typed by the user"))
        awaitUis(2)
        assertEquals("typed by the user", lastLabel())
    }

    @Test
    fun `a toggle event carries its value`() {
        val engine = runtime()
        install(
            """
            local on = false
            function on_event(e)
              if e.type == "toggle" then on = e.value end
            end
            function render() return ui.label { text = tostring(on) } end
            """.trimIndent(),
        )
        engine.open("com.example.demo")
        awaitUis(1)

        engine.dispatch(PluginEvent.ToggleChanged("caps", true))
        awaitUis(2)
        assertEquals("true", lastLabel())
    }

    @Test
    fun `set_input reaches the host`() {
        val engine = runtime()
        install(
            """
            function on_event(e) wm.ui.set_input("box", "cleared") end
            function render() return ui.input { id = "box" } end
            """.trimIndent(),
        )
        engine.open("com.example.demo")
        awaitUis(1)

        engine.dispatch(PluginEvent.Click("go"))
        awaitTag("input:box=cleared")
    }

    @Test
    fun `storage survives a session ending`() {
        val engine = runtime()
        install(
            """
            function on_event(e) wm.storage.set("note", "kept") end
            function render() return ui.label { text = wm.storage.get("note") or "empty" } end
            """.trimIndent(),
            permissions = listOf("storage"),
        )
        engine.open("com.example.demo")
        awaitUis(1)
        engine.dispatch(PluginEvent.Click("save"))
        awaitUis(2)
        assertEquals("kept", lastLabel())
        engine.close()

        reset()
        engine.open("com.example.demo")
        awaitUis(1)
        assertEquals("kept", lastLabel())
    }

    // ---- broken plugins ---------------------------------------------------

    @Test
    fun `a script that fails to load reports an error`() {
        val engine = runtime()
        install("this is not lua at all !!!")
        engine.open("com.example.demo")
        awaitTag("error")
    }

    @Test
    fun `a script with no render function says so`() {
        val engine = runtime()
        install("local x = 1")
        engine.open("com.example.demo")
        awaitTag("error")
    }

    @Test
    fun `an error inside an event handler does not end the session`() {
        val engine = runtime()
        install(
            """
            function on_event(e) error("handler exploded") end
            function render() return ui.label { text = "still here" } end
            """.trimIndent(),
        )
        engine.open("com.example.demo")
        awaitUis(1)

        engine.dispatch(PluginEvent.Click("boom"))
        awaitTag("error")

        // Still loaded, so the next event still arrives.
        reset()
        engine.dispatch(PluginEvent.Click("again"))
        awaitTag("error")
        assertTrue(store.plugin("com.example.demo")!!.enabled)
    }

    @Test
    fun `an infinite loop is stopped and reported`() {
        val engine = runtime(
            PluginRuntime.Limits(load = PluginLimit(instructions = 200_000, wallMillis = 5_000)),
        )
        install("while true do end function render() return ui.label{text='x'} end")
        engine.open("com.example.demo")
        awaitTag("stopped")
    }

    @Test
    fun `an unresponsive plugin is abandoned and a repeat offence disables it`() {
        // Chained lazy quantifiers over a long subject. Lua patterns have no
        // alternation and no nested group repeats, so the classic regex bomb does
        // not apply -- but `.-` sequences do backtrack, and each one multiplies
        // the work. This runs entirely inside luaj's Java matcher, where the
        // instruction hook never fires, which is the exact case the watchdog
        // exists for and the only way to reach it.
        val engine = runtime(
            PluginRuntime.Limits(
                load = PluginLimit(instructions = Long.MAX_VALUE, wallMillis = 200),
                watchdogPeriodMillis = 50,
                watchdogGraceMillis = 100,
            ),
        )
        install(
            """
            local subject = ("x"):rep(4000)
            subject:match(".-.-.-.-.-.-.-.-.-.-zzz")
            function render() return ui.label { text = "never reached" } end
            """.trimIndent(),
        )

        engine.open("com.example.demo")
        awaitTag("stopped")
        assertEquals(1, store.plugin("com.example.demo")!!.abandonedCount)
        assertTrue(store.plugin("com.example.demo")!!.enabled)

        reset()
        engine.open("com.example.demo")
        awaitTag("stopped:disabled=true")
        assertFalse(store.plugin("com.example.demo")!!.enabled)

        // Once disabled it will not open at all.
        reset()
        engine.open("com.example.demo")
        awaitTag("error")
    }

    // ---- lifecycle ---------------------------------------------------------

    @Test
    fun `closing stops callbacks arriving`() {
        val engine = runtime()
        install(
            """
            function on_event(e) end
            function render() return ui.label { text = "hi" } end
            """.trimIndent(),
        )
        engine.open("com.example.demo")
        awaitUis(1)

        engine.close()
        reset()
        engine.dispatch(PluginEvent.Click("go"))
        Thread.sleep(300)
        synchronized(lock) {
            assertTrue("a closed session still spoke: $events", events.isEmpty())
        }
        assertNull(engine.activePluginId)
    }

    @Test
    fun `opening a second plugin replaces the first`() {
        val engine = runtime()
        install("function render() return ui.label { text = 'one' } end")
        engine.open("com.example.demo")
        awaitUis(1)

        install("function render() return ui.label { text = 'two' } end", id = "com.example.other")

        reset()
        engine.open("com.example.other")
        awaitUis(1)
        assertEquals("com.example.other", engine.activePluginId)
        assertEquals("two", lastLabel())
    }

    @Test
    fun `a missing plugin is an error rather than a crash`() {
        val engine = runtime()
        engine.open("com.example.absent")
        awaitTag("error")
    }

    @Test
    fun `a disabled plugin refuses to open`() {
        val engine = runtime()
        install("function render() return ui.label { text = 'x' } end")
        store.setEnabled("com.example.demo", false)
        engine.open("com.example.demo")
        awaitTag("error")
    }

    private companion object {
        var counter = 0
    }
}
