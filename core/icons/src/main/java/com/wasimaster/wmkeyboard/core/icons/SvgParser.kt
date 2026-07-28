package com.wasimaster.wmkeyboard.core.icons

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * One drawable path from an SVG, already resolved: inherited presentation
 * attributes are folded in and the enclosing `<g transform>` chain is composed
 * into [transform].
 *
 * Colours are ARGB longs, matching how the theme format stores them.
 * [filled]/[stroked] say *whether* to paint, [fillArgb]/[strokeArgb] say what
 * with — and a null colour on a painted path means "no colour of its own", so
 * the renderer supplies the caller's tint. Keeping the two apart is what makes
 * `fill="none"` (draw nothing) different from an unstyled path (draw in the
 * theme colour), which would otherwise both read as a null fill.
 */
data class SvgPath(
    val pathData: String,
    /** `[a, b, c, d, e, f]` column-major affine, or null for the identity. */
    val transform: FloatArray? = null,
    val filled: Boolean = true,
    val fillArgb: Long? = null,
    val evenOdd: Boolean = false,
    val fillAlpha: Float = 1f,
    val stroked: Boolean = false,
    val strokeArgb: Long? = null,
    val strokeWidth: Float = 0f,
    val strokeAlpha: Float = 1f,
    val strokeCap: String = "butt",
    val strokeJoin: String = "miter",
    val strokeMiter: Float = 4f,
) {
    // FloatArray has identity equals, so the generated ones would be wrong.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SvgPath) return false
        return pathData == other.pathData &&
            transform.contentEqualsOrNull(other.transform) &&
            filled == other.filled &&
            fillArgb == other.fillArgb && evenOdd == other.evenOdd &&
            fillAlpha == other.fillAlpha && stroked == other.stroked &&
            strokeArgb == other.strokeArgb &&
            strokeWidth == other.strokeWidth && strokeAlpha == other.strokeAlpha &&
            strokeCap == other.strokeCap && strokeJoin == other.strokeJoin &&
            strokeMiter == other.strokeMiter
    }

    override fun hashCode(): Int {
        var result = pathData.hashCode()
        result = 31 * result + (transform?.contentHashCode() ?: 0)
        result = 31 * result + filled.hashCode()
        result = 31 * result + (fillArgb?.hashCode() ?: 0)
        result = 31 * result + evenOdd.hashCode()
        result = 31 * result + fillAlpha.hashCode()
        result = 31 * result + stroked.hashCode()
        result = 31 * result + (strokeArgb?.hashCode() ?: 0)
        result = 31 * result + strokeWidth.hashCode()
        result = 31 * result + strokeAlpha.hashCode()
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + strokeJoin.hashCode()
        result = 31 * result + strokeMiter.hashCode()
        return result
    }

    private fun FloatArray?.contentEqualsOrNull(other: FloatArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)
}

/**
 * A parsed icon: a viewport and the paths that fill it.
 *
 * [monochrome] is the property the renderer cares about. When it is true the
 * icon declared no colours of its own, so every path can be painted in the
 * caller's tint and the icon tracks the theme like a built-in one. When it is
 * false the author picked colours deliberately and they are drawn as authored,
 * which also means the per-tool accent colour stops applying to it.
 */
data class SvgDoc(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<SvgPath>,
    val monochrome: Boolean,
)

/**
 * A deliberately small SVG reader: enough to turn hand-drawn or exported icons
 * into vectors, and nothing else.
 *
 * Supported: `<svg viewBox|width|height>`, `<path d>`, `<g transform>` nesting,
 * the basic shapes (`rect`, `circle`, `ellipse`, `line`, `polyline`, `polygon`)
 * rewritten as path data, presentation attributes and a minimal inline
 * `style="…"`, and `fill`/`stroke` as `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb()`,
 * `none`, `currentColor` or one of a short list of named colours.
 *
 * Everything else — text, embedded images, `<use>`, gradients, filters, masks,
 * clip paths, CSS `<style>` blocks — is skipped rather than rejected. An icon
 * that leans on them renders as whatever remains, which is a better outcome for
 * the user than an import that fails with "unsupported".
 *
 * **This parses untrusted input.** An icon pack is a file from a stranger's
 * repository, so [parse] hard-disables DTDs and external entities: an XXE here
 * would let a pack read app-private files. The size caps are the other half of
 * that — a 2 GB path string is a denial of service even without an exploit.
 */
object SvgParser {

    /** Caps applied before and during parsing; nothing legitimate approaches them. */
    const val MAX_SOURCE_BYTES = 256 * 1024
    private const val MAX_PATHS = 400
    private const val MAX_PATH_DATA = 64 * 1024
    private const val MAX_DEPTH = 32

    /** What an icon with no `viewBox` and no size is assumed to be. */
    private const val DEFAULT_VIEWPORT = 24f

    /**
     * Largest viewport a file may declare.
     *
     * `width="1e999"` parses to `Float.POSITIVE_INFINITY`, which passes a naive
     * `> 0` check and then turns every coordinate Compose derives from it into
     * NaN — a vector that draws nothing and measures unpredictably. Anything
     * past this is not a real icon, so it falls back to [DEFAULT_VIEWPORT]
     * rather than being trusted or rejected.
     */
    private const val MAX_VIEWPORT = 100_000f

    /**
     * Any DOCTYPE at all, wherever it appears. Matching one inside a comment
     * costs a legitimate file nothing but a rewrite, and the alternative is
     * reasoning about where a declaration can hide.
     */
    private val DOCTYPE = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)

    /**
     * Sets a parser feature where the implementation has one, and shrugs where
     * it doesn't. Safe only because [parse] has already refused any DOCTYPE by
     * inspection; without that, a silently-unset feature would be a hole.
     */
    private fun SAXParserFactory.disableQuietly(feature: String, value: Boolean) {
        runCatching { setFeature(feature, value) }
    }

    /**
     * The [SvgDoc] for [text], or null when it is not usable SVG at all —
     * malformed XML, no root `<svg>`, or nothing drawable inside.
     */
    fun parse(text: String): SvgDoc? {
        if (text.length > MAX_SOURCE_BYTES) return null
        // The XXE guard, done here rather than by asking the parser to do it.
        //
        // Android's SAX implementation recognises only the two `namespaces`
        // features and throws SAXNotRecognizedException for anything else —
        // including Apache's `disallow-doctype-decl`. Setting it inside the
        // runCatching below therefore failed *every* parse on device while
        // passing on the JVM, which is exactly the kind of difference a unit
        // test cannot see. Rejecting the declaration by inspection works on
        // both, and is the guarantee the format documents anyway: "a DOCTYPE
        // declaration causes the file to be rejected outright".
        //
        // With no DOCTYPE there is no DTD, so there are no entities to expand
        // and nothing for an external reference to hang off — which is what
        // makes the best-effort feature calls below safe to lose.
        if (DOCTYPE.containsMatchIn(text)) return null

        val handler = SvgHandler()
        val parsed = runCatching {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                disableQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
                disableQuietly("http://xml.org/sax/features/external-general-entities", false)
                disableQuietly("http://xml.org/sax/features/external-parameter-entities", false)
                runCatching { setXIncludeAware(false) }
            }
            factory.newSAXParser().parse(InputSource(StringReader(text)), handler)
        }
        // A truncated file still yields the shapes read before the error, which
        // is worth keeping; a file that produced nothing is simply not an icon.
        if (parsed.isFailure && handler.paths.isEmpty()) return null
        if (!handler.sawSvg || handler.paths.isEmpty()) return null
        return SvgDoc(
            viewportWidth = handler.viewportWidth,
            viewportHeight = handler.viewportHeight,
            paths = handler.paths.toList(),
            monochrome = !handler.sawColor,
        )
    }

    /**
     * Inherited paint state. SVG resolves presentation attributes down the
     * tree, so each element starts from its parent's values and overrides only
     * what it declares.
     */
    private data class Paint(
        val fill: String? = null,
        val stroke: String? = null,
        val fillRule: String? = null,
        val fillOpacity: Float = 1f,
        val strokeOpacity: Float = 1f,
        val opacity: Float = 1f,
        val strokeWidth: Float = 1f,
        val strokeCap: String = "butt",
        val strokeJoin: String = "miter",
        val strokeMiter: Float = 4f,
        val transform: FloatArray? = null,
    )

    private class SvgHandler : DefaultHandler() {
        val paths = ArrayList<SvgPath>()
        var viewportWidth = DEFAULT_VIEWPORT
        var viewportHeight = DEFAULT_VIEWPORT
        var sawSvg = false

        /** True once any element declares a real colour — the not-tintable signal. */
        var sawColor = false

        private val stack = ArrayDeque<Paint>()

        /** Depth of the subtree being skipped (`<text>`, `<defs>`, …), or 0. */
        private var skipping = 0

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = (qName ?: localName ?: return).substringAfter(':')
            if (skipping > 0) {
                skipping++
                return
            }
            if (name in SKIPPED) {
                skipping = 1
                return
            }
            if (stack.size >= MAX_DEPTH) {
                skipping = 1
                return
            }
            val parent = stack.lastOrNull() ?: Paint()
            val paint = parent.inherit(attributes)
            stack.addLast(paint)

            when (name) {
                "svg" -> {
                    sawSvg = true
                    readViewport(attributes)
                }
                "path" -> addPath(attributes.value("d"), paint)
                "rect" -> addPath(rectPath(attributes), paint)
                "circle" -> addPath(circlePath(attributes), paint)
                "ellipse" -> addPath(ellipsePath(attributes), paint)
                "line" -> addPath(linePath(attributes), paint, strokeOnly = true)
                "polyline" -> addPath(polyPath(attributes, close = false), paint, strokeOnly = true)
                "polygon" -> addPath(polyPath(attributes, close = true), paint)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (skipping > 0) {
                skipping--
                return
            }
            stack.removeLastOrNull()
        }

        private fun readViewport(attributes: Attributes) {
            val box = attributes.value("viewBox")?.trim()?.split(Regex("[,\\s]+"))
            if (box != null && box.size == 4) {
                val w = usable(box[2].toFloatOrNull())
                val h = usable(box[3].toFloatOrNull())
                // A non-zero origin would need every path shifted; icons
                // essentially never use one, so the width and height are taken
                // and the origin ignored rather than silently mis-drawing.
                if (w != null && h != null) {
                    viewportWidth = w
                    viewportHeight = h
                    return
                }
            }
            usable(attributes.value("width")?.let(::parseLength))?.let { viewportWidth = it }
            usable(attributes.value("height")?.let(::parseLength))?.let { viewportHeight = it }
        }

        /** [value] back when it is finite, positive and not absurd — see [MAX_VIEWPORT] — else null. */
        private fun usable(value: Float?): Float? =
            value?.takeIf { it.isFinite() && it > 0f && it <= MAX_VIEWPORT }

        /**
         * [strokeOnly] elements (`line`, `polyline`) are not filled by SVG's
         * defaults in any useful way — an unclosed stroke path filled black is
         * a blob — so their fill is dropped unless one was declared.
         */
        private fun addPath(data: String?, paint: Paint, strokeOnly: Boolean = false) {
            if (data.isNullOrBlank()) return
            if (paths.size >= MAX_PATHS || data.length > MAX_PATH_DATA) return

            val fillSpec = paint.fill ?: if (strokeOnly) "none" else null
            val fill = parseColor(fillSpec)
            val stroke = parseColor(paint.stroke)
            val filled = fillSpec != "none"
            val stroked = paint.stroke != null && paint.stroke != "none" && paint.strokeWidth > 0f
            // Nothing to draw at all.
            if (!filled && !stroked) return
            if ((filled && fill.isExplicit) || (stroked && stroke.isExplicit)) sawColor = true

            paths += SvgPath(
                pathData = data,
                transform = paint.transform,
                // A path with no declared fill still paints — SVG's initial
                // fill is black — and a null colour on a painted path means
                // "use the caller's tint", which is that same unstyled case.
                filled = filled,
                fillArgb = fill.argb,
                evenOdd = paint.fillRule == "evenodd",
                fillAlpha = (paint.fillOpacity * paint.opacity).coerceIn(0f, 1f),
                stroked = stroked,
                strokeArgb = stroke.argb,
                strokeWidth = paint.strokeWidth,
                strokeAlpha = (paint.strokeOpacity * paint.opacity).coerceIn(0f, 1f),
                strokeCap = paint.strokeCap,
                strokeJoin = paint.strokeJoin,
                strokeMiter = paint.strokeMiter,
            )
        }
    }

    /** Elements whose whole subtree is ignored. */
    private val SKIPPED = setOf(
        "text", "tspan", "image", "defs", "style", "linearGradient", "radialGradient",
        "filter", "mask", "clipPath", "pattern", "marker", "symbol", "use", "switch",
        "foreignObject", "script", "animate", "animateTransform", "animateMotion", "set",
    )

    private fun Attributes.value(name: String): String? {
        // Namespace-unaware parsing means attributes arrive as written, but a
        // file may still spell one with a prefix.
        for (i in 0 until length) {
            val q = getQName(i) ?: continue
            if (q == name || q.substringAfter(':') == name) return getValue(i)
        }
        return null
    }

    /**
     * Applies [attributes] on top of the receiver, honouring both presentation
     * attributes and the equivalent properties in an inline `style`.
     */
    private fun Paint.inherit(attributes: Attributes): Paint {
        val style = attributes.value("style")?.let(::parseStyle).orEmpty()
        fun attr(name: String): String? = style[name] ?: attributes.value(name)

        val childTransform = attributes.value("transform")?.let(::parseTransform)
        return Paint(
            fill = attr("fill") ?: fill,
            stroke = attr("stroke") ?: stroke,
            fillRule = attr("fill-rule") ?: fillRule,
            fillOpacity = attr("fill-opacity")?.toFloatOrNull() ?: fillOpacity,
            strokeOpacity = attr("stroke-opacity")?.toFloatOrNull() ?: strokeOpacity,
            // opacity is a group property: it multiplies down the tree rather
            // than replacing the parent's.
            opacity = opacity * (attr("opacity")?.toFloatOrNull() ?: 1f),
            strokeWidth = attr("stroke-width")?.let(::parseLength) ?: strokeWidth,
            strokeCap = attr("stroke-linecap") ?: strokeCap,
            strokeJoin = attr("stroke-linejoin") ?: strokeJoin,
            strokeMiter = attr("stroke-miterlimit")?.toFloatOrNull() ?: strokeMiter,
            transform = if (childTransform == null) transform else multiply(transform, childTransform),
        )
    }

    /** `fill:#f00;stroke:none` → map. Values are trimmed and lowercased keys. */
    private fun parseStyle(style: String): Map<String, String> = buildMap {
        for (declaration in style.split(';')) {
            val colon = declaration.indexOf(':')
            if (colon <= 0) continue
            val key = declaration.substring(0, colon).trim().lowercase()
            val value = declaration.substring(colon + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
        }
    }

    /** `12`, `12px`, `12.5pt`, `100%` → 12f, 12f, 12.5f, null. */
    private fun parseLength(raw: String): Float? {
        val text = raw.trim()
        if (text.endsWith("%")) return null
        val digits = text.takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' || it == 'e' || it == 'E' }
        return digits.toFloatOrNull()
    }

    /** A resolved paint colour, and whether the author actually named one. */
    private data class ParsedColor(val argb: Long?, val isExplicit: Boolean)

    private val NAMED_COLORS = mapOf(
        "black" to 0xFF000000L, "white" to 0xFFFFFFFFL, "red" to 0xFFFF0000L,
        "green" to 0xFF008000L, "lime" to 0xFF00FF00L, "blue" to 0xFF0000FFL,
        "yellow" to 0xFFFFFF00L, "cyan" to 0xFF00FFFFL, "aqua" to 0xFF00FFFFL,
        "magenta" to 0xFFFF00FFL, "fuchsia" to 0xFFFF00FFL, "gray" to 0xFF808080L,
        "grey" to 0xFF808080L, "silver" to 0xFFC0C0C0L, "maroon" to 0xFF800000L,
        "olive" to 0xFF808000L, "navy" to 0xFF000080L, "teal" to 0xFF008080L,
        "purple" to 0xFF800080L, "orange" to 0xFFFFA500L, "pink" to 0xFFFFC0CBL,
        "brown" to 0xFFA52A2AL, "gold" to 0xFFFFD700L, "indigo" to 0xFF4B0082L,
        "violet" to 0xFFEE82EEL, "transparent" to 0x00000000L,
    )

    /**
     * A colour spec to ARGB. `null`, `none`, `currentColor` and anything
     * unrecognised are *not* explicit: they leave the path tintable, which is
     * what keeps a plain single-path icon following the theme.
     */
    private fun parseColor(spec: String?): ParsedColor {
        val text = spec?.trim() ?: return ParsedColor(null, false)
        if (text.isEmpty() || text == "none" || text.equals("currentColor", ignoreCase = true)) {
            return ParsedColor(null, false)
        }
        if (text.startsWith("#")) {
            val hex = text.drop(1)
            val argb = when (hex.length) {
                3 -> hex.map { "$it$it" }.joinToString("").toLongOrNull(16)?.or(0xFF000000L)
                4 -> {
                    val expanded = hex.map { "$it$it" }.joinToString("")
                    // #rgba is r,g,b,a — the alpha moves to the front.
                    expanded.toLongOrNull(16)?.let { rgba ->
                        ((rgba and 0xFFL) shl 24) or (rgba ushr 8)
                    }
                }
                6 -> hex.toLongOrNull(16)?.or(0xFF000000L)
                8 -> hex.toLongOrNull(16)?.let { rgba -> ((rgba and 0xFFL) shl 24) or (rgba ushr 8) }
                else -> null
            }
            return ParsedColor(argb, argb != null)
        }
        if (text.startsWith("rgb", ignoreCase = true)) {
            val inside = text.substringAfter('(', "").substringBefore(')')
            val parts = inside.split(',').map { it.trim() }
            if (parts.size < 3) return ParsedColor(null, false)
            fun channel(raw: String): Long? {
                val value = if (raw.endsWith("%")) {
                    raw.dropLast(1).toFloatOrNull()?.let { it * 255f / 100f }
                } else {
                    raw.toFloatOrNull()
                }
                return value?.toInt()?.coerceIn(0, 255)?.toLong()
            }
            val r = channel(parts[0]) ?: return ParsedColor(null, false)
            val g = channel(parts[1]) ?: return ParsedColor(null, false)
            val b = channel(parts[2]) ?: return ParsedColor(null, false)
            val a = parts.getOrNull(3)?.toFloatOrNull()?.let { (it * 255f).toInt().coerceIn(0, 255).toLong() }
                ?: 255L
            return ParsedColor((a shl 24) or (r shl 16) or (g shl 8) or b, true)
        }
        val named = NAMED_COLORS[text.lowercase()] ?: return ParsedColor(null, false)
        return ParsedColor(named, true)
    }

    // ---- transforms ----

    /**
     * `translate(2 3) scale(1.5) rotate(45)` → one composed affine, or null if
     * nothing was understood. Column-major `[a, b, c, d, e, f]`, matching both
     * SVG's `matrix()` argument order and Compose's group properties.
     */
    private fun parseTransform(raw: String): FloatArray? {
        var result: FloatArray? = null
        val calls = Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)").findAll(raw)
        for (call in calls) {
            val name = call.groupValues[1].lowercase()
            val args = call.groupValues[2].trim().split(Regex("[,\\s]+"))
                .mapNotNull { it.toFloatOrNull() }
            val matrix = when (name) {
                "translate" -> {
                    val tx = args.getOrNull(0) ?: continue
                    floatArrayOf(1f, 0f, 0f, 1f, tx, args.getOrNull(1) ?: 0f)
                }
                "scale" -> {
                    val sx = args.getOrNull(0) ?: continue
                    floatArrayOf(sx, 0f, 0f, args.getOrNull(1) ?: sx, 0f, 0f)
                }
                "rotate" -> {
                    val degrees = args.getOrNull(0) ?: continue
                    val radians = Math.toRadians(degrees.toDouble())
                    val cos = kotlin.math.cos(radians).toFloat()
                    val sin = kotlin.math.sin(radians).toFloat()
                    val rotation = floatArrayOf(cos, sin, -sin, cos, 0f, 0f)
                    // rotate(angle cx cy) rotates about a point: translate in,
                    // rotate, translate back.
                    val cx = args.getOrNull(1)
                    val cy = args.getOrNull(2)
                    if (cx != null && cy != null) {
                        multiply(
                            multiply(floatArrayOf(1f, 0f, 0f, 1f, cx, cy), rotation),
                            floatArrayOf(1f, 0f, 0f, 1f, -cx, -cy),
                        )
                    } else {
                        rotation
                    }
                }
                "matrix" -> if (args.size >= 6) args.take(6).toFloatArray() else continue
                "skewx" -> {
                    val t = args.getOrNull(0) ?: continue
                    floatArrayOf(1f, 0f, kotlin.math.tan(Math.toRadians(t.toDouble())).toFloat(), 1f, 0f, 0f)
                }
                "skewy" -> {
                    val t = args.getOrNull(0) ?: continue
                    floatArrayOf(1f, kotlin.math.tan(Math.toRadians(t.toDouble())).toFloat(), 0f, 1f, 0f, 0f)
                }
                else -> continue
            }
            result = if (result == null) matrix else multiply(result, matrix)
        }
        return result
    }

    /** `a` then `b`, as SVG composes them (`b` applies first to the geometry). */
    private fun multiply(a: FloatArray?, b: FloatArray?): FloatArray? {
        if (a == null) return b
        if (b == null) return a
        return floatArrayOf(
            a[0] * b[0] + a[2] * b[1],
            a[1] * b[0] + a[3] * b[1],
            a[0] * b[2] + a[2] * b[3],
            a[1] * b[2] + a[3] * b[3],
            a[0] * b[4] + a[2] * b[5] + a[4],
            a[1] * b[4] + a[3] * b[5] + a[5],
        )
    }

    // ---- basic shapes, rewritten as path data ----

    private fun Attributes.number(name: String, fallback: Float = 0f): Float =
        value(name)?.let(::parseLength) ?: fallback

    private fun rectPath(a: Attributes): String? {
        val w = a.number("width")
        val h = a.number("height")
        if (w <= 0f || h <= 0f) return null
        val x = a.number("x")
        val y = a.number("y")
        // An unset rx mirrors ry and vice versa, per the spec.
        var rx = a.value("rx")?.let(::parseLength) ?: a.value("ry")?.let(::parseLength) ?: 0f
        var ry = a.value("ry")?.let(::parseLength) ?: rx
        rx = rx.coerceIn(0f, w / 2f)
        ry = ry.coerceIn(0f, h / 2f)
        if (rx <= 0f || ry <= 0f) {
            return "M$x,${y}h${w}v${h}h${-w}Z"
        }
        return buildString {
            append("M${x + rx},$y")
            append("h${w - 2 * rx}")
            append("a$rx,$ry 0 0 1 $rx,$ry")
            append("v${h - 2 * ry}")
            append("a$rx,$ry 0 0 1 ${-rx},$ry")
            append("h${-(w - 2 * rx)}")
            append("a$rx,$ry 0 0 1 ${-rx},${-ry}")
            append("v${-(h - 2 * ry)}")
            append("a$rx,$ry 0 0 1 $rx,${-ry}")
            append("Z")
        }
    }

    private fun circlePath(a: Attributes): String? {
        val r = a.number("r")
        if (r <= 0f) return null
        return ellipseData(a.number("cx"), a.number("cy"), r, r)
    }

    private fun ellipsePath(a: Attributes): String? {
        val rx = a.number("rx")
        val ry = a.number("ry")
        if (rx <= 0f || ry <= 0f) return null
        return ellipseData(a.number("cx"), a.number("cy"), rx, ry)
    }

    /** Two half-arcs: one arc of exactly 360° is degenerate and draws nothing. */
    private fun ellipseData(cx: Float, cy: Float, rx: Float, ry: Float): String =
        "M${cx - rx},${cy}a$rx,$ry 0 1 0 ${2 * rx},0a$rx,$ry 0 1 0 ${-2 * rx},0Z"

    private fun linePath(a: Attributes): String =
        "M${a.number("x1")},${a.number("y1")}L${a.number("x2")},${a.number("y2")}"

    private fun polyPath(a: Attributes, close: Boolean): String? {
        val numbers = a.value("points")?.trim()?.split(Regex("[,\\s]+"))
            ?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (numbers.size < 4) return null
        return buildString {
            append("M${numbers[0]},${numbers[1]}")
            var i = 2
            while (i + 1 < numbers.size) {
                append("L${numbers[i]},${numbers[i + 1]}")
                i += 2
            }
            if (close) append("Z")
        }
    }
}
