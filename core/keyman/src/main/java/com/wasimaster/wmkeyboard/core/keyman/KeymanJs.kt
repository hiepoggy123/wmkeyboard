package com.wasimaster.wmkeyboard.core.keyman

/**
 * Pulls the touch layout out of a compiled Keyman keyboard `.js`.
 *
 * ## The `.js` is never executed
 *
 * There is no JavaScript engine here and no `eval`. A compiled keyboard is
 * scanned as text: find the assignment at statement position, brace-match its
 * value with a string-aware matcher, and hand the slice to a strict JSON parser.
 * That is the security property, and it is the reason this module can accept a
 * keyboard the user downloaded from anywhere. Shipping a JS interpreter that
 * runs downloaded keyboard code is the shape that already cost the Lua plugin
 * API its text and network surface.
 *
 * ## Why text scanning is sound here
 *
 * `kmc` emits the touch layout with `JSON.stringify`, so `this.KVKL=` is
 * followed by strict JSON: quoted keys, double-quoted strings, no trailing
 * commas, no comments. Two deviations exist and both are handled:
 *
 *  - For keyboards compiled below KMX 14 (or 17) the compiler rewrites certain
 *    special labels *after* `JSON.stringify`, splicing a live conditional into
 *    the literal: `"text":this._v>13?"*RTLBkSp*":"*BkSp*"`. Measured across the
 *    shipped corpus this affects a small number of keyboards and nothing else.
 *    [collapseVersionTernaries] rewrites each to its first branch, which is the
 *    modern label — the branch a current engine would take.
 *  - Hand-written keyboards, which predate the compiler, contain line and block
 *    comments, and at least one carries a *commented-out* assignment. So the
 *    scan skips comments and string literals rather than taking the first
 *    `indexOf`, and requires the matched value to be followed by `;`.
 *
 * `this.KV={F:' 1em "Busra"',K102:0}` is deliberately *not* parsed as JSON — it
 * has bare keys and a single-quoted string. It carries only a font name and the
 * 102-key flag, and [readK102] reads that one field with a regex.
 */
object KeymanJs {

    private const val KVKL = "this.KVKL="
    private const val KLS = "this.KV.KLS="

    /**
     * Collapses `this._v>N?"A":"B"` to `"A"`.
     *
     * Public because the same splice reaches us through a `.keyman-touch-layout`
     * that someone extracted from a compiled keyboard by hand, so the plain
     * touch-layout reader runs it too. Applying it to a file that does not
     * contain the pattern is a no-op.
     */
    fun collapseVersionTernaries(text: String): String =
        if (VERSION_TERNARY.containsMatchIn(text)) VERSION_TERNARY.replace(text) { it.groupValues[1] } else text

    /**
     * The `KVKL` value as a JSON string, ready for
     * [KeymanTouchLayoutReader.parse].
     *
     * Roughly a quarter of compiled keyboards have no `KVKL` at all — they are
     * desktop-only, and carry a `KV`/`KLS` visual keyboard instead. That is
     * [KeymanFault.JS_NO_TOUCH_LAYOUT], an ordinary outcome rather than a defect.
     */
    fun extractTouchLayout(js: String): KeymanResult<String> = extract(js, KVKL, KeymanFault.JS_NO_TOUCH_LAYOUT)

    /**
     * The `KV.KLS` value: per-layer arrays of the 65 default key outputs, indexed
     * by [VirtualKeys.DEFAULT_CODES]. Absent whenever `this.KV` is null.
     */
    fun extractDefaultKeys(js: String): KeymanResult<String> = extract(js, KLS, KeymanFault.JS_NO_TOUCH_LAYOUT)

    /** True when the keyboard declares the 102-key ISO layout (`K102:1`). */
    fun readK102(js: String): Boolean = K102.find(js)?.groupValues?.get(1) == "1"

    private fun extract(js: String, token: String, missing: KeymanFault): KeymanResult<String> {
        if (js.length > KeymanLimits.MAX_JS_BYTES) return KeymanResult.Failure(KeymanFault.JS_TOO_LARGE)
        val start = findAssignment(js, token) ?: return KeymanResult.Failure(missing)
        val open = start + token.length
        if (open >= js.length || js[open] != '{') return KeymanResult.Failure(KeymanFault.JS_UNBALANCED)
        val end = matchBraces(js, open) ?: return KeymanResult.Failure(KeymanFault.JS_UNBALANCED)
        // A real assignment ends in a semicolon. Requiring it is what stops a
        // commented-out or string-embedded lookalike from being taken as one.
        if (js.indexOf(';', end + 1).let { it == -1 || js.substring(end + 1, it).isNotBlank() }) {
            return KeymanResult.Failure(KeymanFault.JS_UNBALANCED)
        }
        return KeymanResult.Success(js.substring(open, end + 1))
    }

    /**
     * Index of [token] at statement position — outside any string literal and
     * outside any comment. Scans once; the source is a few hundred kilobytes.
     */
    private fun findAssignment(js: String, token: String): Int? {
        var i = 0
        while (i < js.length) {
            when {
                js.startsWith("//", i) -> {
                    val nl = js.indexOf('\n', i)
                    i = if (nl == -1) js.length else nl + 1
                }
                js.startsWith("/*", i) -> {
                    val close = js.indexOf("*/", i + 2)
                    i = if (close == -1) js.length else close + 2
                }
                js[i] == '"' || js[i] == '\'' -> i = skipString(js, i)
                js.startsWith(token, i) -> return i
                else -> i++
            }
        }
        return null
    }

    /** Index just past the string literal opening at [start]. */
    private fun skipString(js: String, start: Int): Int {
        val quote = js[start]
        var i = start + 1
        while (i < js.length) {
            when (js[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return js.length
    }

    /**
     * Index of the `}` closing the `{` at [open], ignoring braces inside string
     * literals. Returns null if the file ends first.
     *
     * Comments are not skipped here on purpose: the value is JSON, where a `/`
     * only ever appears inside a string, and treating `//` as a comment would
     * truncate a key cap that happens to contain two solidi.
     */
    private fun matchBraces(js: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < js.length) {
            when (js[i]) {
                '"' -> {
                    i = skipString(js, i)
                    continue
                }
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
                else -> Unit
            }
            i++
        }
        return null
    }

    private val VERSION_TERNARY =
        Regex("""this\._v>\d+\s*\?\s*("(?:[^"\\]|\\.)*")\s*:\s*("(?:[^"\\]|\\.)*")""")

    private val K102 = Regex("""K102\s*:\s*(\d)""")
}
