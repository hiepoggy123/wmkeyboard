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
    /**
     * The [SnippetFolder] this snippet belongs to, or 0 for none.
     *
     * Folders are one level deep and a snippet is in at most one of them, so
     * this is an id rather than a list. 0 rather than null because "no folder"
     * is the overwhelmingly common case and a null would be written as an
     * explicit key by every exporter that encodes defaults.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val folderId: Long = 0,
)

/**
 * A named group of snippets, and the switch that arms or disarms their
 * triggers together.
 *
 * [enabled] is about *automatic* behaviour only: a snippet in a folder that is
 * switched off never expands as you type and never offers itself as a chip,
 * but it is still listed in the snippets panel and still inserts when tapped.
 * That is the whole point of the switch — a folder of work replies that must
 * not fire mid-message is still a folder you want to reach for on purpose.
 *
 * Folders are drawn in list order, which [SnippetStore.reorderFolders] rewrites.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SnippetFolder(
    val id: Long,
    val name: String,
    /** Whether the folder's snippets may expand on their own. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val createdAt: Long = 0,
)

/**
 * User-defined snippets with template variables, persisted as JSON in
 * app-private storage (same offline-first pattern as ClipboardStore).
 *
 * Supported variables, expanded at insertion time — see [SnippetVariable] for
 * the full list, which covers date/time parts, the clipboard, the app being
 * typed into, the current selection, and a `{cursor}` placement marker.
 *
 * Snippets may be grouped into [SnippetFolder]s, one level deep. A folder that
 * is switched off keeps its snippets out of the trigger index and nowhere else;
 * see [SnippetFolder].
 */
class SnippetStore(private val storageFile: File?) {

    @Serializable
    private data class Snapshot(
        val snippets: List<Snippet> = emptyList(),
        val folders: List<SnippetFolder> = emptyList(),
    )

    private val snippets = ArrayList<Snippet>()
    private val folders = ArrayList<SnippetFolder>()
    private val json = Json { ignoreUnknownKeys = true }
    private var nextId = 1L
    private var nextFolderId = 1L

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
            folderId = knownFolder(snippet.folderId),
        )
        snippets.add(added)
        lookup = null
        return added
    }

    /**
     * Adds a whole file's worth of snippets, recreating the folders they came
     * in and keeping which snippet sat in which one.
     *
     * Folder ids in a file are as untrustworthy as snippet ids — two files
     * written on two phones both start at 1 — so every folder here is created
     * afresh and the snippets are re-pointed at the new ids. A snippet naming a
     * folder the file never declared, and a snippet that named none, both land
     * in [fallbackFolderId]: 0 for an ordinary import, and the pack's own folder
     * when an add-on is being installed.
     *
     * Returns the snippets as stored, in the order they were given.
     */
    @Synchronized
    fun addAll(
        snippets: List<Snippet>,
        folders: List<SnippetFolder> = emptyList(),
        fallbackFolderId: Long = 0,
        now: Long = System.currentTimeMillis(),
    ): List<Snippet> {
        val remapped = HashMap<Long, Long>(folders.size)
        for (folder in folders) {
            remapped[folder.id] = addFolder(folder.name, folder.enabled, now).id
        }
        return snippets.map { snippet ->
            add(snippet.copy(folderId = remapped[snippet.folderId] ?: fallbackFolderId), now)
        }
    }

    /**
     * Rewrites one snippet's editable fields.
     *
     * [folderId] is the one field that is left alone when it is not passed: a
     * caller editing a snippet's text has no business moving it, and the two
     * screens that do move snippets say so explicitly.
     */
    @Synchronized
    fun update(
        id: Long,
        label: String,
        text: String,
        trigger: String? = null,
        triggerPattern: String? = null,
        triggerWords: Int = 0,
        confirm: Boolean = false,
        folderId: Long? = null,
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
                folderId = folderId?.let(::knownFolder) ?: snippets[index].folderId,
            )
            lookup = null
        }
    }

    @Synchronized
    fun remove(id: Long) {
        snippets.removeAll { it.id == id }
        lookup = null
    }

    /**
     * Rewrites the stored order to [ids], which is also the order the snippets
     * panel draws in.
     *
     * The list was creation order everywhere, and the panel has no search, so a
     * snippet used every day sank under a year of one-off ones with no way to
     * lift it back. New snippets still append; this is the only thing that
     * moves an existing one.
     *
     * Ids the store does not know are dropped and ids missing from [ids] keep
     * their relative order at the end, so a reorder raced against a delete or
     * an import can neither lose a snippet nor resurrect one.
     */
    @Synchronized
    fun reorder(ids: List<Long>) {
        val byId = snippets.associateBy { it.id }
        val moved = ids.mapNotNull(byId::get)
        val movedIds = moved.mapTo(HashSet()) { it.id }
        val rest = snippets.filter { it.id !in movedIds }
        snippets.clear()
        snippets.addAll(moved)
        snippets.addAll(rest)
        lookup = null
    }

    // ---- folders ---------------------------------------------------------

    @Synchronized
    fun folders(): List<SnippetFolder> = folders.toList()

    @Synchronized
    fun folder(id: Long): SnippetFolder? = folders.firstOrNull { it.id == id }

    /**
     * Adds a folder under a fresh id and returns it.
     *
     * The name is taken as given beyond a trim; an empty one is the caller's
     * problem, since only a screen knows what to call an unnamed folder.
     */
    @Synchronized
    fun addFolder(
        name: String,
        enabled: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): SnippetFolder {
        val added = SnippetFolder(
            id = nextFolderId++,
            name = name.trim(),
            enabled = enabled,
            createdAt = now,
        )
        folders.add(added)
        // A folder arrives switched off often enough — an installed pack that
        // must not fire yet — that the index cannot be assumed still good.
        lookup = null
        return added
    }

    @Synchronized
    fun renameFolder(id: Long, name: String) {
        val index = folders.indexOfFirst { it.id == id }
        if (index >= 0) folders[index] = folders[index].copy(name = name.trim())
    }

    /** Arms or disarms every trigger in the folder. See [SnippetFolder]. */
    @Synchronized
    fun setFolderEnabled(id: Long, enabled: Boolean) {
        val index = folders.indexOfFirst { it.id == id }
        if (index >= 0 && folders[index].enabled != enabled) {
            folders[index] = folders[index].copy(enabled = enabled)
            lookup = null
        }
    }

    /**
     * Deletes a folder. Its snippets survive it and become ungrouped unless
     * [withSnippets], which is the "delete the pack and everything in it" case.
     *
     * Losing a folder must never quietly lose the text inside it, so the
     * surviving-snippets path is the default and the destructive one has to be
     * asked for by name.
     */
    @Synchronized
    fun removeFolder(id: Long, withSnippets: Boolean = false) {
        if (!folders.removeAll { it.id == id }) return
        if (withSnippets) {
            snippets.removeAll { it.folderId == id }
        } else {
            for (i in snippets.indices) {
                if (snippets[i].folderId == id) snippets[i] = snippets[i].copy(folderId = 0)
            }
        }
        lookup = null
    }

    /**
     * Deletes [id] if nothing is left in it.
     *
     * What uninstalling a pack does after removing the pack's own snippets: the
     * folder goes too, unless the user has moved something else into it, in
     * which case it is now theirs.
     */
    @Synchronized
    fun removeFolderIfEmpty(id: Long) {
        if (snippets.none { it.folderId == id }) removeFolder(id)
    }

    /** Rewrites folder order, the order every list of folders draws in. */
    @Synchronized
    fun reorderFolders(ids: List<Long>) {
        val byId = folders.associateBy { it.id }
        val moved = ids.mapNotNull(byId::get)
        val movedIds = moved.mapTo(HashSet()) { it.id }
        val rest = folders.filter { it.id !in movedIds }
        folders.clear()
        folders.addAll(moved)
        folders.addAll(rest)
    }

    /** Moves one snippet into [folderId], or out of every folder when it is 0. */
    @Synchronized
    fun moveToFolder(snippetId: Long, folderId: Long) {
        val index = snippets.indexOfFirst { it.id == snippetId }
        if (index >= 0) {
            snippets[index] = snippets[index].copy(folderId = knownFolder(folderId))
            lookup = null
        }
    }

    /** [id] itself when a folder has it, else 0 — no snippet points at nothing. */
    private fun knownFolder(id: Long): Long =
        if (id != 0L && folders.any { it.id == id }) id else 0L

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
        lookup ?: SnippetIndex.of(armed()).also { lookup = it }

    /**
     * The snippets whose triggers may fire: everything outside a folder that is
     * switched off.
     *
     * Filtering here rather than inside [SnippetIndex] keeps the matcher's one
     * job — decide what the text behind the cursor matches — free of a second
     * notion of whether a snippet counts. A disarmed snippet is simply not in
     * the index, so every one of the index's questions (`hasPatterns`,
     * `couldStartPattern`, the confirm-chip gates) answers correctly without
     * being told about folders at all.
     */
    private fun armed(): List<Snippet> {
        val off = folders.mapNotNullTo(HashSet()) { if (it.enabled) null else it.id }
        if (off.isEmpty()) return snippets.toList()
        return snippets.filter { it.folderId !in off }
    }

    private fun normalizeTrigger(trigger: String?): String? =
        trigger?.trim()?.takeIf { it.isNotEmpty() }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(snippets.toList(), folders.toList())))
        }
    }

    /** Re-reads the backing file (settings app and IME share the store). */
    @Synchronized
    fun reload() {
        snippets.clear()
        folders.clear()
        lookup = null
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            folders.addAll(snapshot.folders)
            // Read after the folders, so a file hand-edited to point a snippet
            // at a folder it never declared lands ungrouped rather than at an
            // id a later addFolder would hand out to something else.
            snippets.addAll(snapshot.snippets.map { it.copy(folderId = knownFolder(it.folderId)) })
        }
        nextId = (snippets.maxOfOrNull { it.id } ?: 0) + 1
        nextFolderId = (folders.maxOfOrNull { it.id } ?: 0) + 1
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
