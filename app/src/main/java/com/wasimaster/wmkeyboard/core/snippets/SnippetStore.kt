package com.wasimaster.wmkeyboard.core.snippets

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A reusable text snippet inserted from the keyboard's snippet panel. */
@Serializable
data class Snippet(
    val id: Long,
    val label: String,
    val text: String,
    val createdAt: Long = 0,
)

/**
 * User-defined snippets with template variables, persisted as JSON in
 * app-private storage (same offline-first pattern as ClipboardStore).
 *
 * Supported variables, expanded at insertion time — see [SnippetVariable] for
 * the full list, which covers date/time parts, the clipboard, the app being
 * typed into, the current selection, and a `{cursor}` placement marker.
 */
class SnippetStore(private val storageFile: File?) {

    @Serializable
    private data class Snapshot(val snippets: List<Snippet> = emptyList())

    private val snippets = ArrayList<Snippet>()
    private val json = Json { ignoreUnknownKeys = true }
    private var nextId = 1L

    init {
        reload()
    }

    @Synchronized
    fun items(): List<Snippet> = snippets.toList()

    @Synchronized
    fun add(label: String, text: String, now: Long = System.currentTimeMillis()): Snippet {
        val snippet = Snippet(id = nextId++, label = label.trim(), text = text, createdAt = now)
        snippets.add(snippet)
        return snippet
    }

    @Synchronized
    fun update(id: Long, label: String, text: String) {
        val index = snippets.indexOfFirst { it.id == id }
        if (index >= 0) {
            snippets[index] = snippets[index].copy(label = label.trim(), text = text)
        }
    }

    @Synchronized
    fun remove(id: Long) {
        snippets.removeAll { it.id == id }
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(snippets.toList())))
        }
    }

    /** Re-reads the backing file (settings app and IME share the store). */
    @Synchronized
    fun reload() {
        snippets.clear()
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            snippets.addAll(snapshot.snippets)
        }
        nextId = (snippets.maxOfOrNull { it.id } ?: 0) + 1
    }

    companion object {

        /**
         * Marker for where the cursor should land after insertion. [expand]
         * leaves it in place; [expandWithCursor] strips it and reports the
         * offset. Chosen from a private-use code point so it can never collide
         * with snippet text.
         */
        const val CURSOR_MARKER = "\uE000"

        /** Expansion inputs the IME knows and the settings preview doesn't. */
        data class Context(
            /** Most recent clipboard entry. */
            val clipboard: String? = null,
            /** Label of the app being typed into, e.g. "Messages". */
            val appName: String? = null,
            /** Package name of the app being typed into. */
            val packageName: String? = null,
            /** Text currently selected in the field. */
            val selection: String? = null,
        )

        /** Expanded text plus where the cursor should end up inside it. */
        data class Expanded(val text: String, val cursorOffset: Int)

        private val CUSTOM_DATE = Regex("""\{date:([^}\n]{1,40})\}""")

        /** Expands template variables, leaving [CURSOR_MARKER] in place. */
        fun expand(
            text: String,
            now: Long = System.currentTimeMillis(),
            clipboard: String? = null,
            context: Context = Context(),
        ): String {
            val ctx = if (clipboard != null) context.copy(clipboard = clipboard) else context
            val date = Date(now)
            fun fmt(pattern: String) =
                runCatching { SimpleDateFormat(pattern, Locale.getDefault()).format(date) }
                    .getOrDefault("")

            // {date:pattern} first, so a literal pattern can't be eaten by {date}.
            var out = CUSTOM_DATE.replace(text) { fmt(it.groupValues[1]) }
            for (variable in SnippetVariable.entries) {
                if (!out.contains(variable.token)) continue
                out = out.replace(variable.token, variable.value(::fmt, ctx, now))
            }
            return out
        }

        /** Expands, then strips the cursor marker and reports its offset. */
        fun expandWithCursor(
            text: String,
            now: Long = System.currentTimeMillis(),
            context: Context = Context(),
        ): Expanded {
            val expanded = expand(text, now, context = context)
            val index = expanded.indexOf(CURSOR_MARKER)
            if (index < 0) return Expanded(expanded, expanded.length)
            return Expanded(expanded.replace(CURSOR_MARKER, ""), index)
        }
    }
}

/**
 * The template variables a snippet may contain. Kept as an enum so the
 * expander and the settings screen's reference table can never drift apart.
 *
 * `{date:pattern}` is handled separately in [SnippetStore.expand] since it
 * takes an argument (any SimpleDateFormat pattern, e.g. `{date:EEEE 'week' w}`).
 */
enum class SnippetVariable(
    val token: String,
    /** What the settings screen shows next to the token. */
    val description: String,
) {
    DATE("{date}", "today's date"),
    TIME("{time}", "current time (24-hour)"),
    TIME12("{time12}", "current time (12-hour)"),
    DATETIME("{datetime}", "date and time"),
    ISODATE("{isodate}", "ISO date, 2026-07-19"),
    ISOTIME("{isotime}", "ISO timestamp"),
    WEEKDAY("{weekday}", "day name, Sunday"),
    DAY("{day}", "day of month"),
    MONTH("{month}", "month name"),
    YEAR("{year}", "four-digit year"),
    TIMEZONE("{timezone}", "time zone, e.g. GMT+06:00"),
    TIMESTAMP("{timestamp}", "Unix seconds"),
    CLIP("{clip}", "latest clipboard entry"),
    SELECTION("{selection}", "text selected in the field"),
    APP("{app}", "name of the app you're typing in"),
    PACKAGE("{package}", "package name of that app"),
    UUID("{uuid}", "a fresh random UUID"),
    CURSOR("{cursor}", "where the cursor lands afterwards");

    internal fun value(
        fmt: (String) -> String,
        context: SnippetStore.Companion.Context,
        now: Long,
    ): String = when (this) {
        DATE -> fmt("d MMM yyyy")
        TIME -> fmt("HH:mm")
        TIME12 -> fmt("h:mm a")
        DATETIME -> fmt("d MMM yyyy HH:mm")
        ISODATE -> fmt("yyyy-MM-dd")
        ISOTIME -> fmt("yyyy-MM-dd'T'HH:mm:ssXXX")
        WEEKDAY -> fmt("EEEE")
        DAY -> fmt("d")
        MONTH -> fmt("MMMM")
        YEAR -> fmt("yyyy")
        TIMEZONE -> fmt("zzzz")
        TIMESTAMP -> (now / 1000).toString()
        CLIP -> context.clipboard.orEmpty()
        SELECTION -> context.selection.orEmpty()
        APP -> context.appName.orEmpty()
        PACKAGE -> context.packageName.orEmpty()
        UUID -> java.util.UUID.randomUUID().toString()
        CURSOR -> SnippetStore.CURSOR_MARKER
    }
}
