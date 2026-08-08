package com.wasimaster.wmkeyboard.core.theme

import com.wasimaster.wmkeyboard.core.theme.FlexTheme.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * FlorisBoard's style language, as much of it as maps onto a [ThemeSpec].
 *
 * A stylesheet is a JSON object of rule to declarations: `"key": {"background":
 * "#2C2C2C"}`. A rule names an element, optionally a state after a colon
 * (`key:pressed`) and optionally an attribute in brackets (`key[code=32]`).
 * `@defines` holds variables that the rest reference as `var(--name)`.
 *
 * ### Why the matching is deliberately loose
 *
 * The dialect is still moving upstream: v2's own documentation is marked
 * work-in-progress, element names have been renamed between releases
 * (`keyboard` became `window`, `key-hint` became `key hint`), and property
 * spellings vary between the editor's output and hand-written files. Rules and
 * properties are therefore normalized — lowercased, separators collapsed — and
 * matched against alias sets rather than exact strings. A rename upstream costs
 * one entry in a list here instead of a silently empty theme.
 *
 * What is *not* loose is the dialect check. A v1 stylesheet is recognised and
 * refused rather than partly read: see [Stylesheet.parse].
 */
internal class Stylesheet(
    val rules: List<SnyggRule>,
    val ruleCount: Int,
    val mappedCount: Int,
    val dropped: Set<FlexUnsupported>,
) {

    fun first(element: String, state: String? = null): SnyggRule? =
        rules.firstOrNull { it.element == element && it.state == state && it.attribute == null }

    fun attributed(element: String): List<SnyggRule> =
        rules.filter { it.element == element && it.attribute != null }

    companion object {

        fun parse(text: String): Stylesheet? {
            val root = runCatching { FlexTheme.json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                ?: return null
            val defines = (root[DEFINES] as? JsonObject)
                ?.mapValues { (_, value) -> (value as? JsonPrimitive)?.content.orEmpty() }
                .orEmpty()

            val dropped = linkedSetOf<FlexUnsupported>()
            val rules = mutableListOf<SnyggRule>()
            var count = 0
            for ((raw, value) in root) {
                if (raw.startsWith('@')) {
                    if (raw.equals(FONT_RULE, ignoreCase = true)) dropped += FlexUnsupported.FONT
                    continue
                }
                val declarations = (value as? JsonObject) ?: continue
                count++
                val rule = ruleOf(raw, declarations, defines, dropped)
                if (rule == null) dropped += FlexUnsupported.UNKNOWN_ELEMENT else rules += rule
            }
            if (rules.isEmpty()) return null
            // v1 spoke of a `keyboard`, v2 of a `window` inside a `root`. A sheet
            // that names neither of the v2 elements is the older dialect, whose
            // property names and value syntax this mapper does not read.
            if (rules.none { it.element in V2_ELEMENTS }) {
                return Stylesheet(emptyList(), count, 0, dropped + FlexUnsupported.SNYGG_V1)
            }
            return Stylesheet(rules, count, rules.size, dropped)
        }

        private fun ruleOf(
            raw: String,
            declarations: JsonObject,
            defines: Map<String, String>,
            dropped: MutableSet<FlexUnsupported>,
        ): SnyggRule? {
            val selector = raw.substringBefore('[').substringBefore(':').normalizeName()
            val element = ELEMENTS[selector] ?: return null
            val state = raw.substringBefore('[').substringAfter(':', "").normalizeName().ifEmpty { null }
            val attribute = raw.substringAfter('[', "").substringBefore(']').ifEmpty { null }
            val properties = declarations.mapNotNull { (name, value) ->
                val key = name.normalizeName()
                noteUnsupported(key, dropped)
                val text = valueOf(value, defines) ?: return@mapNotNull null
                if (text.contains(DYNAMIC, ignoreCase = true)) dropped += FlexUnsupported.DYNAMIC_COLOR
                PROPERTIES[key]?.let { it to text }
            }.toMap()
            return SnyggRule(element, state, attribute, properties)
        }

        /**
         * A declaration's value as text. An object-shaped value hands over the
         * one field that carries the colour, which is how the theme editor
         * writes a value it also wants to remember the picker state for.
         */
        private fun valueOf(value: JsonElement, defines: Map<String, String>): String? {
            val raw = when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.string("value") ?: value.string("color") ?: value.string("\$")
                is JsonArray -> null
            } ?: return null
            return resolve(raw, defines)
        }

        /** `var(--name)` against `@defines`, once. A define that names another is left alone. */
        private fun resolve(raw: String, defines: Map<String, String>): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith(VAR_PREFIX)) return trimmed
            val name = trimmed.removePrefix(VAR_PREFIX).substringBefore(')').trim()
            return defines[name] ?: defines["--$name"] ?: trimmed
        }

        private fun noteUnsupported(property: String, dropped: MutableSet<FlexUnsupported>) {
            when {
                property.contains("elevation") || property.contains("shadow") ->
                    dropped += FlexUnsupported.ELEVATION
                property.contains("margin") || property.contains("padding") ->
                    dropped += FlexUnsupported.PER_ELEMENT_SPACING
                property.contains("font family") -> dropped += FlexUnsupported.FONT
            }
        }

        /** Lowercase, separators collapsed to single spaces. */
        private fun String.normalizeName(): String =
            trim().lowercase().replace('-', ' ').replace('_', ' ').split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ")

        private const val DEFINES = "@defines"
        private const val FONT_RULE = "@font"
        private const val VAR_PREFIX = "var("
        private const val DYNAMIC = "dynamic"

        /**
         * Element aliases. The keys are normalized rule names as they appear in
         * a stylesheet; the values are the names the mapper below uses.
         */
        private val ELEMENTS: Map<String, String> = buildMap {
            for (name in listOf("window", "keyboard", "root", "smartbar background")) put(name, EL_BOARD)
            for (name in listOf("key", "key background")) put(name, EL_KEY)
            for (name in listOf("key hint", "key hint text", "keyhint")) put(name, EL_HINT)
            for (name in listOf("key popup box", "key popup", "key popup element", "popup")) put(name, EL_POPUP)
            for (name in listOf("smartbar", "smartbar primary row", "smartbar secondary row")) put(name, EL_TOOLBAR)
            for (name in listOf("smartbar candidate", "smartbar candidate word", "candidate")) put(name, EL_CANDIDATE)
            for (name in listOf("glide trail", "glide")) put(name, EL_GLIDE)
        }

        /** Rules only the newer dialect has. Their absence is what dates a sheet. */
        private val V2_ELEMENTS = setOf(EL_KEY, EL_POPUP, EL_HINT)

        /** Property aliases, normalized name to the one the mapper reads. */
        private val PROPERTIES: Map<String, String> = buildMap {
            for (name in listOf("background", "background color", "bg")) put(name, PROP_BACKGROUND)
            for (name in listOf("foreground", "color", "text color", "fg")) put(name, PROP_FOREGROUND)
            put("shape", PROP_SHAPE)
            for (name in listOf("border color", "outline color")) put(name, PROP_BORDER_COLOR)
            for (name in listOf("border width", "outline width")) put(name, PROP_BORDER_WIDTH)
            put("font weight", PROP_FONT_WEIGHT)
            for (name in listOf("background image", "image")) put(name, PROP_IMAGE)
        }
    }
}

/** One rule: an element, optionally a state and an attribute, and its properties. */
internal data class SnyggRule(
    val element: String,
    val state: String?,
    val attribute: String?,
    val properties: Map<String, String>,
) {
    fun value(property: String): String? = properties[property]
}

internal const val EL_BOARD = "board"
internal const val EL_KEY = "key"
internal const val EL_HINT = "hint"
internal const val EL_POPUP = "popup"
internal const val EL_TOOLBAR = "toolbar"
internal const val EL_CANDIDATE = "candidate"
internal const val EL_GLIDE = "glide"

internal const val PROP_BACKGROUND = "background"
internal const val PROP_FOREGROUND = "foreground"
internal const val PROP_SHAPE = "shape"
internal const val PROP_BORDER_COLOR = "borderColor"
internal const val PROP_BORDER_WIDTH = "borderWidth"
internal const val PROP_FONT_WEIGHT = "fontWeight"
internal const val PROP_IMAGE = "image"

/**
 * A snygg colour as ARGB, or null when the value is not a colour this
 * understands.
 *
 * Accepts what stylesheets actually carry: `#RGB`, `#RRGGBB`, `#RRGGBBAA`,
 * `rgb(…)`, `rgba(…)` with the alpha as either 0..1 or 0..255, and
 * `transparent`. A value it cannot read returns null, and null means the field
 * is left at its default rather than set to black.
 */
@Suppress("ReturnCount")
internal fun snyggColor(raw: String?): Long? {
    val text = raw?.trim()?.lowercase() ?: return null
    if (text == "transparent" || text == "none") return 0x00000000L
    if (text.startsWith("#")) return hexColor(text.removePrefix("#"))
    if (!text.startsWith("rgb")) return null
    val parts = text.substringAfter('(').substringBefore(')').split(',', ' ', '/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (parts.size < 3) return null
    val r = parts[0].toFloatOrNull()?.roundToInt() ?: return null
    val g = parts[1].toFloatOrNull()?.roundToInt() ?: return null
    val b = parts[2].toFloatOrNull()?.roundToInt() ?: return null
    val a = parts.getOrNull(3)?.toFloatOrNull()?.let { if (it <= 1f) (it * 255f).roundToInt() else it.roundToInt() }
        ?: 255
    return argb(a, r, g, b)
}

private fun hexColor(hex: String): Long? {
    val digits = when (hex.length) {
        // #RGB is shorthand for #RRGGBB.
        3 -> hex.map { "$it$it" }.joinToString("")
        6, 8 -> hex
        else -> return null
    }
    if (digits.any { it !in "0123456789abcdef" }) return null
    val value = digits.toLongOrNull(16) ?: return null
    // Eight digits are RRGGBBAA in CSS, which is not the order stored here.
    return if (digits.length == 8) {
        ((value and 0xFFL) shl 24) or (value ushr 8)
    } else {
        0xFF000000L or value
    }
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Long =
    ((a.coerceIn(0, 255).toLong() shl 24) or
        (r.coerceIn(0, 255).toLong() shl 16) or
        (g.coerceIn(0, 255).toLong() shl 8) or
        b.coerceIn(0, 255).toLong())

/**
 * A snygg shape as a key shape and its corner radius.
 *
 * `rounded-corner(8dp)`, `cut-corner(4dp)`, `circle()`, `rectangle()`. A shape
 * that gives a different size per corner cannot be carried — there is one
 * radius field — so it reports [FlexUnsupported.PER_CORNER_RADIUS] and the
 * first size is used.
 */
internal fun snyggShape(raw: String?, dropped: MutableSet<FlexUnsupported>): Pair<KeyShapeKind, Int?>? {
    val text = raw?.trim()?.lowercase() ?: return null
    val name = text.substringBefore('(').replace('-', ' ').replace('_', ' ').trim()
    val sizes = text.substringAfter('(', "").substringBefore(')')
        .split(',', ' ')
        .mapNotNull { it.trim().removeSuffix("dp").removeSuffix("%").toFloatOrNull() }
    if (sizes.size > 1 && sizes.distinct().size > 1) dropped += FlexUnsupported.PER_CORNER_RADIUS
    val radius = sizes.firstOrNull()?.roundToInt()
    return when {
        name.startsWith("circle") -> KeyShapeKind.CIRCLE to null
        name.startsWith("cut") -> KeyShapeKind.CUT to radius
        name.startsWith("rectangle") || name.startsWith("sharp") -> KeyShapeKind.SHARP to 0
        name.startsWith("rounded") -> KeyShapeKind.ROUNDED to radius
        else -> null
    }
}

/** A dp size from `8dp`, `8` or `8.0dp`. */
internal fun snyggDp(raw: String?): Float? =
    raw?.trim()?.removeSuffix("dp")?.trim()?.toFloatOrNull()

/**
 * Contrast between two ARGB colours, as the WCAG ratio (1.0 to 21.0).
 *
 * Written out rather than taken from Compose so that it is plain arithmetic on
 * two longs: this runs in a unit test, and the check it guards — a text colour
 * scraped from the wrong surface — is exactly the kind of thing that has to be
 * tested rather than eyeballed.
 */
internal fun contrastRatio(foreground: Long, background: Long): Float {
    val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
    val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun relativeLuminance(color: Long): Float {
    fun channel(shift: Int): Float {
        val raw = ((color ushr shift) and 0xFFL).toFloat() / 255f
        return if (raw <= 0.03928f) raw / 12.92f else Math.pow(((raw + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
}
