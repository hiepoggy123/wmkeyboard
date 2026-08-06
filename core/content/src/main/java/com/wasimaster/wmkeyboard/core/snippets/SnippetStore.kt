package com.wasimaster.wmkeyboard.core.snippets

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.content.R
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A reusable text snippet inserted from the keyboard's snippet panel.
 *
 * A snippet may also carry a trigger that expands it as the user types: either
 * [trigger], one exact word, or [triggerPattern], a regular expression over the
 * words behind the cursor. A snippet that somehow carries both keeps the word,
 * which is the more specific and the cheaper of the two.
 *
 * [confirm] turns that trigger from a rewrite into an offer: the keyboard shows
 * a chip and waits to be tapped rather than replacing what was typed.
 *
 * The three optional trigger fields are written only when they are set. Every
 * published pack is a hand-maintained file, and [SnippetFile] encodes defaults,
 * so without that a plain snippet would grow three empty keys it never uses.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Snippet(
    val id: Long,
    val label: String,
    val text: String,
    val createdAt: Long = 0,
    /** Word that, typed on its own and finished with a space/punctuation/enter, auto-expands to [text]. */
    val trigger: String? = null,
    /**
     * Regular expression matched against the words behind the cursor. Capture
     * groups reach [text] as `$1` to `$9`; see [SnippetMatcher].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val triggerPattern: String? = null,
    /**
     * How many words back the match may reach. 0 asks for
     * [SnippetMatcher.DEFAULT_WORDS].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val triggerWords: Int = 0,
    /**
     * Ask before expanding. The trigger still matches, but instead of rewriting
     * the text the keyboard offers the expansion as a chip on the suggestion
     * strip and inserts nothing until it is tapped.
     *
     * For text somebody else wrote — a downloaded pack of replies — this is the
     * difference between a keyboard that helps and one that rewrites sentences
     * out from under the person typing them.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val confirm: Boolean = false,
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

    /**
     * The triggers, prepared for lookup. Built on first use and thrown away by
     * every mutator, so the keyboard never pays for a scan on the typing path
     * and never has to be told the list changed.
     */
    @Volatile
    private var lookup: SnippetIndex? = null

    init {
        reload()
    }

    @Synchronized
    fun items(): List<Snippet> = snippets.toList()

    @Synchronized
    fun add(label: String, text: String, trigger: String? = null, now: Long = System.currentTimeMillis()): Snippet =
        add(Snippet(id = 0, label = label, text = text, trigger = trigger), now)

    /**
     * Adds [snippet] under a fresh id, keeping every other field it carries.
     *
     * Import and add-on installation both go through here. Rebuilding a snippet
     * out of a handful of named values instead would quietly drop whatever
     * field was added last, with no error and no repair note.
     */
    @Synchronized
    fun add(snippet: Snippet, now: Long = System.currentTimeMillis()): Snippet {
        val added = snippet.copy(
            id = nextId++,
            label = snippet.label.trim(),
            createdAt = now,
            trigger = normalizeTrigger(snippet.trigger),
            triggerPattern = normalizeTrigger(snippet.triggerPattern),
            triggerWords = snippet.triggerWords.coerceIn(0, SnippetMatcher.MAX_WORDS),
        )
        snippets.add(added)
        lookup = null
        return added
    }

    @Synchronized
    fun update(
        id: Long,
        label: String,
        text: String,
        trigger: String? = null,
        triggerPattern: String? = null,
        triggerWords: Int = 0,
        confirm: Boolean = false,
    ) {
        val index = snippets.indexOfFirst { it.id == id }
        if (index >= 0) {
            snippets[index] = snippets[index].copy(
                label = label.trim(),
                text = text,
                trigger = normalizeTrigger(trigger),
                triggerPattern = normalizeTrigger(triggerPattern),
                triggerWords = triggerWords.coerceIn(0, SnippetMatcher.MAX_WORDS),
                confirm = confirm,
            )
            lookup = null
        }
    }

    @Synchronized
    fun remove(id: Long) {
        snippets.removeAll { it.id == id }
        lookup = null
    }

    /** The snippet whose trigger matches [word] exactly (case-insensitive), if any. */
    fun matchTrigger(word: String): Snippet? = index().matchTrigger(word)

    /**
     * The pattern snippet that fits the end of [window], or null.
     *
     * See [SnippetIndex.matchPattern] for what the window has to be and what
     * [atFieldStart] promises.
     */
    fun matchPattern(
        window: CharSequence,
        atFieldStart: Boolean = false,
        now: Long = System.currentTimeMillis(),
        context: Companion.Context = Companion.Context(),
        confirm: Boolean = false,
    ): SnippetMatch? = index().matchPattern(window, atFieldStart, now, context, confirm)

    /** True when any snippet carries a pattern, so the keyboard need not look. */
    fun hasPatterns(): Boolean = index().hasPatterns

    /** True when some pattern expands on its own, the commit path's question. */
    fun hasAutoPatterns(): Boolean = index().hasAutoPatterns

    /** True when some pattern offers itself instead, the strip's question. */
    fun hasConfirmPatterns(): Boolean = index().hasConfirmPatterns

    /** True when some plain trigger offers itself instead of expanding. */
    fun hasConfirmTriggers(): Boolean = index().hasConfirmTriggers

    /** True when a word starting with [first] could begin a gated pattern. */
    fun couldStartPattern(first: Char): Boolean = index().let { it.hasUngated || it.couldStartAt(first) }

    /** Ids of the patterns the app stopped for taking too long. */
    fun stoppedPatterns(): Set<Long> = index().stopped()

    private fun index(): SnippetIndex = lookup ?: build()

    @Synchronized
    private fun build(): SnippetIndex =
        lookup ?: SnippetIndex.of(snippets.toList()).also { lookup = it }

    private fun normalizeTrigger(trigger: String?): String? =
        trigger?.trim()?.takeIf { it.isNotEmpty() }

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
        lookup = null
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
    @StringRes val descriptionRes: Int,
) {
    DATE("{date}", R.string.core_content_snippet_var_date_info),
    TIME("{time}", R.string.core_content_snippet_var_time_info),
    TIME12("{time12}", R.string.core_content_snippet_var_time12_info),
    DATETIME("{datetime}", R.string.core_content_snippet_var_datetime_info),
    ISODATE("{isodate}", R.string.core_content_snippet_var_isodate_info),
    ISOTIME("{isotime}", R.string.core_content_snippet_var_isotime_info),
    WEEKDAY("{weekday}", R.string.core_content_snippet_var_weekday_info),
    DAY("{day}", R.string.core_content_snippet_var_day_info),
    MONTH("{month}", R.string.core_content_snippet_var_month_info),
    YEAR("{year}", R.string.core_content_snippet_var_year_info),
    TIMEZONE("{timezone}", R.string.core_content_snippet_var_timezone_info),
    TIMESTAMP("{timestamp}", R.string.core_content_snippet_var_timestamp_info),
    CLIP("{clip}", R.string.core_content_snippet_var_clip_info),
    SELECTION("{selection}", R.string.core_content_snippet_var_selection_info),
    APP("{app}", R.string.core_content_snippet_var_app_info),
    PACKAGE("{package}", R.string.core_content_snippet_var_package_info),
    UUID("{uuid}", R.string.core_content_snippet_var_uuid_info),
    CURSOR("{cursor}", R.string.core_content_snippet_var_cursor_info);

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
