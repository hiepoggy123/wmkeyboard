package com.wasimaster.wmkeyboard.core.snippets.espanso

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * The one place this app parses YAML, and the limits it parses under.
 *
 * Every file that reaches here was written by somebody else — an Espanso Hub
 * package, a file picked from the downloads folder, a URL somebody pasted — so
 * the loader is configured for hostile input rather than for convenience:
 *
 * - [SafeConstructor] builds nothing but maps, lists and scalars. The default
 *   constructor would instantiate whatever class a `!!` tag named, which is
 *   CVE-2022-1471 and has no place anywhere near a downloaded file.
 * - `codePointLimit` bounds the document before any of it is built.
 * - `maxAliasesForCollections` bounds the billion-laughs shape, where a handful
 *   of nested anchors expand into gigabytes.
 * - `nestingDepthLimit` bounds a deeply nested document, which would otherwise
 *   recurse the parser until the stack gives out.
 *
 * Nothing here writes YAML. [EspansoWriter] hand-rolls that, so the emitter half
 * of the library is never referenced and R8 drops it.
 */
internal object EspansoYaml {

    /** Aliases one document may expand, well above any honest match file. */
    private const val MAX_ALIASES = 64

    /** How deeply a document may nest. A match file needs about five. */
    private const val MAX_DEPTH = 32

    /**
     * Parses [text], or null when it is not YAML at all.
     *
     * Every failure is the same answer. A caller cannot do anything useful with
     * the difference between a syntax error and a limit being hit, and the
     * library's own messages name line and column in a file the user did not
     * write.
     */
    fun load(text: String, maxCodePoints: Int): Any? {
        val options = LoaderOptions().apply {
            codePointLimit = maxCodePoints
            maxAliasesForCollections = MAX_ALIASES
            setAllowRecursiveKeys(false)
            nestingDepthLimit = MAX_DEPTH
        }
        return runCatching { Yaml(SafeConstructor(options)).load<Any?>(text) }.getOrNull()
    }

    /** [value] as a map with string keys, or null when it is anything else. */
    fun asMap(value: Any?): Map<String, Any?>? {
        val map = value as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, Any?>(map.size)
        for ((key, entry) in map) {
            if (key is String) out[key] = entry
        }
        return out
    }

    /** [value] as a list, or an empty one. A bare scalar counts as a list of one. */
    fun asList(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value
        else -> listOf(value)
    }

    /**
     * [value] as text.
     *
     * YAML types a bare `1.0` as a double and `yes` as a boolean, and a snippet
     * whose replacement is a version number or the word "no" is perfectly
     * ordinary — so anything scalar is taken as its printed form rather than
     * refused. A map or a list is not text and gives null.
     */
    fun asText(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Map<*, *>, is List<*> -> null
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    /** [value] as a boolean, accepting the strings YAML 1.1 would have. */
    fun asBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> when (value.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> null
        }
        else -> null
    }

    /** [value] as a whole number, or null. */
    fun asLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }
}
