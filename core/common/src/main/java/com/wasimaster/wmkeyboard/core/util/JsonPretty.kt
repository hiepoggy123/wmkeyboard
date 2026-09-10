package com.wasimaster.wmkeyboard.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Prints JSON for a person to read: indented, but only where indenting helps.
 *
 * `Json { prettyPrint = true }` breaks every object and every array, whatever
 * their size. On a key layout that turns a three-item list of long-press
 * alternates into five lines and the document into thousands, which is harder
 * to read rather than easier: the shape of the thing disappears into scrolling.
 *
 * This printer breaks a container only when its own single line would be too
 * long, so a short object or array stays whole and the structure stays visible.
 * It is the one printer behind everything a person reads: the raw-JSON editor's
 * text, its Format button, and a layer copied to the clipboard.
 */
object JsonPretty {

    /** How wide a line may get before its container is broken over several. */
    const val LineBudget: Int = 100

    private const val Indent = "  "

    /** [element], printed. */
    fun print(element: JsonElement, budget: Int = LineBudget): String =
        buildString { render(element, 0, 0, budget, this) }

    /** [source], parsed and printed again, or null when it is not JSON. */
    fun reprint(source: String, budget: Int = LineBudget): String? =
        runCatching { print(Json.parseToJsonElement(source), budget) }.getOrNull()

    /**
     * Writes [element] into [out], on one line when it fits and over several
     * when it does not. [column] is how much of the line is already spoken for,
     * which is the indent plus, for a value in an object, the key in front of
     * it.
     */
    private fun render(element: JsonElement, depth: Int, column: Int, budget: Int, out: StringBuilder) {
        val flat = StringBuilder().also { compact(element, it) }
        if (column + flat.length <= budget) {
            out.append(flat)
            return
        }
        when (element) {
            is JsonObject -> {
                out.append("{\n")
                element.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) out.append(",\n")
                    val name = JsonPrimitive(key).toString()
                    indent(out, depth + 1)
                    out.append(name).append(": ")
                    render(value, depth + 1, (depth + 1) * Indent.length + name.length + 2, budget, out)
                }
                out.append('\n')
                indent(out, depth)
                out.append('}')
            }
            is JsonArray -> {
                out.append("[\n")
                element.forEachIndexed { index, value ->
                    if (index > 0) out.append(",\n")
                    indent(out, depth + 1)
                    render(value, depth + 1, (depth + 1) * Indent.length, budget, out)
                }
                out.append('\n')
                indent(out, depth)
                out.append(']')
            }
            // A single string longer than the budget has nowhere to break.
            else -> out.append(flat)
        }
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth) { out.append(Indent) }
    }

    /**
     * [element] on one line. `JsonElement.toString()` would nearly do, but it
     * packs everything tight; a space after each colon and comma is what makes
     * the short line worth keeping.
     */
    private fun compact(element: JsonElement, out: StringBuilder) {
        when (element) {
            is JsonObject -> {
                out.append('{')
                element.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) out.append(", ")
                    out.append(JsonPrimitive(key).toString()).append(": ")
                    compact(value, out)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                element.forEachIndexed { index, value ->
                    if (index > 0) out.append(", ")
                    compact(value, out)
                }
                out.append(']')
            }
            else -> out.append(element.toString())
        }
    }
}
