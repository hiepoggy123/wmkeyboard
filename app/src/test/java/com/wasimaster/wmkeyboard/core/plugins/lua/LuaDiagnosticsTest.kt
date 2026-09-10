package com.wasimaster.wmkeyboard.core.plugins.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaDiagnosticCode as Code

/** Each diagnostic on the smallest script that earns it, and the nearest script that must not. */
class LuaDiagnosticsTest {

    private val render = "\nfunction render()\n  return ui.label { text = \"x\" }\nend\n"

    private fun diagnose(source: String, host: LuaHostShape? = LuaHostShape(storage = false)) =
        LuaDiagnostics.of(LuaDocument.of(source), host)

    private fun codes(source: String, host: LuaHostShape? = LuaHostShape(storage = false)) = diagnose(source, host).map { it.code }

    private fun one(source: String, code: Code): LuaDiagnostic {
        val found = diagnose(source).filter { it.code == code }
        assertEquals("expected one $code in:\n$source\ngot ${diagnose(source)}", 1, found.size)
        return found.single()
    }

    private fun none(source: String, code: Code, host: LuaHostShape? = LuaHostShape(storage = false)) {
        val found = diagnose(source, host).filter { it.code == code }
        assertEquals("expected no $code in:\n$source", emptyList<LuaDiagnostic>(), found)
    }

    // ---- the text ----------------------------------------------------------

    @Test
    fun `an unclosed string is marked at its quote and hides the parser's complaint`() {
        val found = diagnose("x = \"abc\ny = 1")
        assertEquals(listOf(Code.UNCLOSED_STRING), found.map { it.code })
        assertEquals(LuaSpan(4, 5), found.single().span)
    }

    @Test
    fun `a character Lua cannot read is named`() {
        val found = one("x = \$", Code.UNKNOWN_CHARACTER)
        assertEquals("\$", found.arg1)
    }

    @Test
    fun `brackets are paired before the parser is asked`() {
        assertEquals(5, one("print(foo(1)", Code.UNCLOSED_BRACKET).span.start)
        val wrong = one("f({a = 1)", Code.WRONG_CLOSER)
        assertEquals(8, wrong.span.start)
        assertEquals(")" to "}", wrong.arg1 to wrong.arg2)
        assertEquals(")", one("x = 1)", Code.STRAY_CLOSER).arg1)
    }

    @Test
    fun `a missing end is given to the block its indentation says lost it`() {
        val source = "function render()\n  if x then\n    y()\nend"
        val found = one(source, Code.UNCLOSED_BLOCK)
        assertEquals(source.indexOf("if"), found.span.start)
        assertEquals("if" to "end", found.arg1 to found.arg2)
    }

    @Test
    fun `the parser's own failure lands where it stopped`() {
        assertEquals(10, one("local x = = 2", Code.SYNTAX_EXPECTED).span.start)
        assertEquals(LuaSpan(2, 3), one("x =", Code.SYNTAX_AT_END_EXPECTED).span)
    }

    @Test
    fun `a script that does not parse gets no other check`() {
        assertEquals(listOf(Code.SYNTAX_EXPECTED), codes("x = = 1\nprint(cuont)"))
    }

    // ---- the sandbox -------------------------------------------------------

    @Test
    fun `names the sandbox removes say why`() {
        assertEquals("io", one("local data = io.read()$render", Code.REMOVED_FILES).arg1)
        one("require(\"x\")$render", Code.REMOVED_LOADS_CODE)
        one("local co = coroutine.wrap(f)$render", Code.REMOVED_COROUTINES)
        one("load(\"x\")$render", Code.REMOVED_CODE_FROM_TEXT)
        one("debug.traceback()$render", Code.REMOVED_JAVA_OR_DEBUGGER)
        none("function require(name) return name end\nrequire(\"x\")$render", Code.REMOVED_LOADS_CODE)
    }

    @Test
    fun `members that never exist, and the os the sandbox keeps`() {
        assertEquals("wm.clipboard", one("wm.clipboard.get()$render", Code.NEVER_IN_API).arg1)
        assertEquals("os.getenv", one("os.getenv(\"HOME\")$render", Code.OS_REDUCED).arg1)
    }

    @Test
    fun `a misspelt member names the real one`() {
        val typo = one("string.fromat(\"%d\", 1)$render", Code.MEMBER_TYPO)
        assertEquals("string.fromat" to "string.format", typo.arg1 to typo.arg2)
        one("math.zzzzzz()$render", Code.UNKNOWN_MEMBER)
        none("function string.shout(s) return s:upper() end\nlocal x = string.shout(\"a\")\nprint(x)$render", Code.UNKNOWN_MEMBER)
    }

    @Test
    fun `Lua 5 point 1 names point at their replacement`() {
        val maxn = one("print(table.maxn(t))$render", Code.LUA51_NAME)
        assertEquals("table.maxn" to "#", maxn.arg1 to maxn.arg2)
        assertEquals("table.unpack", one("print(unpack(t))$render", Code.LUA51_NAME).arg2)
        one("setfenv(1, {})$render", Code.LUA51_GONE)
    }

    @Test
    fun `dump, storage without the permission, and frontier patterns`() {
        one("print(string.dump(print))$render", Code.STRING_DUMP)
        val storage = "wm.storage.set(\"a\", \"b\")$render"
        one(storage, Code.STORAGE_NOT_DECLARED)
        none(storage, Code.STORAGE_NOT_DECLARED, LuaHostShape(storage = true))
        none(storage, Code.STORAGE_NOT_DECLARED, host = null)
        one("print((\"a b\"):find(\"%f[%w]\"))$render", Code.FRONTIER_PATTERN)
    }

    // ---- the plugin contract -----------------------------------------------

    @Test
    fun `render must exist, take nothing and return something`() {
        one("function on_event(e) end", Code.MISSING_RENDER)
        val nothing = "function render()\n  ui.label { text = \"x\" }\nend"
        assertEquals(nothing.indexOf("render"), one(nothing, Code.RENDER_RETURNS_NOTHING).span.start)
        one("function render(state) return ui.label { text = \"x\" } end", Code.RENDER_PARAMETERS)
        assertEquals(emptyList<Code>(), codes(render.trim()))
    }

    @Test
    fun `on_event is checked against the events a plugin can hear`() {
        one("function on_event(e, extra) print(extra) end$render", Code.ON_EVENT_PARAMETERS)
        val typo = one("function on_event(e)\n  if e.type == \"clik\" then end\nend$render", Code.EVENT_TYPE_TYPO)
        assertEquals("clik" to "click", typo.arg1 to typo.arg2)
        assertEquals("tab_selected", one("function on_event(e)\n  if \"tabs_selected\" == e.type then end\nend$render", Code.EVENT_TYPE_TYPO).arg2)
        one("function on_event(e)\n  if e.type == \"zzzz\" then end\nend$render", Code.UNKNOWN_EVENT_TYPE)
        none("function on_event(e)\n  if e.type == \"click\" .. suffix then end\nend$render", Code.UNKNOWN_EVENT_TYPE)
    }

    @Test
    fun `an id no widget has is pointed out only when every id is written out`() {
        val widgets = "\nfunction render()\n  return ui.button { id = \"go\", text = \"Go\" }\nend\n"
        assertEquals("stop", one("function on_event(e)\n  if e.id == \"stop\" then end\nend$widgets", Code.UNKNOWN_EVENT_ID).arg1)
        none("function on_event(e)\n  if e.id == \"go\" then end\nend$widgets", Code.UNKNOWN_EVENT_ID)
        val built = "\nfunction render()\n  return ui.button { id = \"go\" .. n, text = \"Go\" }\nend\n"
        none("function on_event(e)\n  if e.id == \"stop\" then end\nend$built", Code.UNKNOWN_EVENT_ID)
    }

    @Test
    fun `widget fields, ids, styles and tabs`() {
        fun widget(body: String) = "function render()\n  return $body\nend"
        val field = one(widget("ui.button { id = \"go\", txt = \"Go\" }"), Code.UI_FIELD_TYPO)
        assertEquals("txt" to "text", field.arg1 to field.arg2)
        val unknown = one(widget("ui.column { spacing = 4 }"), Code.UNKNOWN_UI_FIELD)
        assertEquals("spacing" to "ui.column", unknown.arg1 to unknown.arg2)
        assertEquals("ui.button", one(widget("ui.button { text = \"Go\" }"), Code.MISSING_ID).arg1)
        none(widget("ui.button { [key] = \"go\", text = \"Go\" }"), Code.MISSING_ID)
        none(widget("ui.output { text = \"x\" }"), Code.MISSING_ID)
        one(widget("ui.column { ui.button { id = \"a\", text = \"1\" }, ui.button { id = \"a\", text = \"2\" } }"), Code.DUPLICATE_ID)
        assertEquals("title", one(widget("ui.label { text = \"x\", style = \"titel\" }"), Code.LABEL_STYLE_TYPO).arg2)
        one(widget("ui.label { text = \"x\", style = \"huge\" }"), Code.UNKNOWN_LABEL_STYLE)
        one(widget("ui.button { id = \"b\", text = \"x\", style = \"secondary\" }"), Code.PLAIN_BUTTON_STYLE)
        val pages = (1..9).joinToString(", ") { "ui.page { title = \"$it\" }" }
        one(widget("ui.tabs { id = \"t\", $pages }"), Code.TOO_MANY_TABS)
        // The commas inside a function are not the table's: only go itself is an unknown field.
        val inner = widget("ui.column { ui.button { id = \"go\", text = \"x\", go = function() local a, b = 1, 2 return a + b end } }")
        assertEquals(listOf("go"), diagnose(inner).filter { it.code == Code.UNKNOWN_UI_FIELD }.map { it.arg1 })
    }

    // ---- Lua ---------------------------------------------------------------

    @Test
    fun `a global read that nothing defines names the nearest name`() {
        val typo = one("local count = 1\nprint(cuont)$render", Code.GLOBAL_TYPO)
        assertEquals("cuont" to "count", typo.arg1 to typo.arg2)
        assertEquals("print", one("prnit(\"x\")$render", Code.GLOBAL_TYPO).arg2)
        one("print(zzq)$render", Code.UNDEFINED_GLOBAL)
        none("function helper() end\nhelper()$render", Code.UNDEFINED_GLOBAL)
    }

    @Test
    fun `a global that is really a forgotten local`() {
        val source = "function on_event(e)\n  total = 0\n  total = total + 1\nend$render"
        assertEquals(source.indexOf("total"), one(source, Code.ACCIDENTAL_GLOBAL).span.start)
        none("function on_event(e)\n  total = 1\nend\nfunction render()\n  return ui.label { text = tostring(total) }\nend", Code.ACCIDENTAL_GLOBAL)
        none("function on_event(e)\n  clicks = (clicks or 0) + 1\nend$render", Code.ACCIDENTAL_GLOBAL)
        none("count = 0\nfunction on_event(e)\n  count = 1\nend$render", Code.ACCIDENTAL_GLOBAL)
    }

    @Test
    fun `replacing a built-in, unused locals and shadowed libraries`() {
        assertEquals("print", one("print = function() end$render", Code.REPLACES_BUILTIN).arg1)
        one("local unused = 1$render", Code.UNUSED_LOCAL)
        none("local _ignored = 1$render", Code.UNUSED_LOCAL)
        one("local string = \"x\"\nprint(string)$render", Code.SHADOWS_LIBRARY)
        none("local string = \"x\"\nprint(string.zzzz)$render", Code.UNKNOWN_MEMBER)
    }

    @Test
    fun `a loop nothing can leave`() {
        one("while true do\n  x = 1\nend$render", Code.LOOP_NEVER_ENDS)
        one("while true do\n  local f = function() return 1 end\nend$render", Code.LOOP_NEVER_ENDS)
        one("repeat\n  x = 1\nuntil false$render", Code.LOOP_NEVER_ENDS)
        none("while true do\n  if x then break end\nend$render", Code.LOOP_NEVER_ENDS)
        none("repeat\n  x = 1\nuntil false or done$render", Code.LOOP_NEVER_ENDS)
    }

    @Test
    fun `a flood of problems stops at the cap`() {
        val source = (1..400).joinToString("\n") { "print(zz$it)" } + render
        assertEquals(LuaDiagnostics.MAX_DIAGNOSTICS, diagnose(source).size)
    }

    @Test
    fun `problems come in document order`() {
        val found = diagnose("print(zzq)\nlocal unused = 1\nprint(qqz)$render")
        assertTrue(found.zipWithNext().all { (a, b) -> a.span.start <= b.span.start })
    }
}
