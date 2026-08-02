package com.wasimaster.wmkeyboard.core.aihistory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One finished AI run, as the history screen shows it.
 *
 * The action and the provider are plain strings, not enums: this module sits
 * below the one that declares them, and a stored record has to survive an
 * action being renamed or deleted anyway.
 */
@Serializable
data class AiHistoryEntry(
    val id: Long,
    /** Wall clock, milliseconds. */
    val timestamp: Long,
    /** Stable id of the action that ran. */
    val actionId: String,
    /** Name of the action at the time it ran, so an old record still reads. */
    val actionName: String,
    /** [com.wasimaster.wmkeyboard.core.settings.AiProvider] name. */
    val provider: String,
    /** The model that answered: a service's model name, or an on-device id. */
    val model: String,
    val input: String,
    val output: String,
    /** Wall time from the request to the result. */
    val durationMs: Long,
    /** The instruction, for an action whose prompt is typed each run. */
    val instruction: String = "",
    /** Characters of reasoning the model produced; 0 when it did not reason. */
    val reasoningChars: Int = 0,
    /** The answer arrived as a stream rather than as one response. */
    val streamed: Boolean = true,
    /** NONE, REPLACE or INSERT: what the user did with the answer. */
    val committed: String = COMMITTED_NONE,
    /** The message the panel showed. Not empty means the run failed. */
    val error: String = "",
    /** The provider stopped early, so [output] is cut off. */
    val truncated: Boolean = false,
    /** [input] or [output] was cut to [AiHistoryStore.MAX_TEXT]. */
    val textCut: Boolean = false,
) {
    val failed: Boolean get() = error.isNotEmpty()

    companion object {
        const val COMMITTED_NONE = "NONE"
        const val COMMITTED_REPLACE = "REPLACE"
        const val COMMITTED_INSERT = "INSERT"
    }
}

/**
 * What the AI tool did, kept on the device only, and only when the user turns
 * the setting on. Same offline-first shape as SnippetStore and ClipboardStore:
 * a JSON file in app-private storage that the settings app and the keyboard
 * both open.
 *
 * A null [storageFile] means "hold this in memory and never write it". That is
 * what the keyboard passes before the device has been unlocked since a restart,
 * so a locked session leaves nothing behind without any special case here.
 *
 * Deliberately outside the settings backup. The two preference keys travel,
 * because they are configuration, but the records do not: an off-by-default log
 * of the user's own writing is the last thing that should end up in a file they
 * mail to themselves. The learned dictionary is left out for the same reason.
 */
class AiHistoryStore(
    private val storageFile: File?,
    var maxItems: Int = DEFAULT_MAX_ITEMS,
) {

    @Serializable
    private data class Snapshot(val entries: List<AiHistoryEntry> = emptyList())

    private val entries = ArrayList<AiHistoryEntry>()
    private val json = Json { ignoreUnknownKeys = true }
    private var nextId = 1L

    init {
        reload()
    }

    /** Newest first, which is the order the screen wants. */
    @Synchronized
    fun items(): List<AiHistoryEntry> = entries.toList()

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    @Synchronized
    fun size(): Int = entries.size

    /**
     * Records one run, assigning its id. Long texts are cut to [MAX_TEXT] and
     * the oldest records fall off the end once [maxItems] is reached.
     */
    @Synchronized
    fun add(entry: AiHistoryEntry): AiHistoryEntry {
        val input = entry.input.cut()
        val output = entry.output.cut()
        val instruction = entry.instruction.cut()
        val stored = entry.copy(
            id = nextId++,
            input = input,
            output = output,
            instruction = instruction,
            textCut = input.length < entry.input.length ||
                output.length < entry.output.length ||
                instruction.length < entry.instruction.length,
        )
        entries.add(0, stored)
        while (entries.size > maxItems.coerceAtLeast(1)) entries.removeAt(entries.lastIndex)
        return stored
    }

    /** Records that the user put this answer into the field. */
    @Synchronized
    fun markCommitted(id: Long, how: String) {
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) entries[index] = entries[index].copy(committed = how)
    }

    @Synchronized
    fun delete(id: Long) {
        entries.removeAll { it.id == id }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    /** Applies a changed retention setting to what is already stored. */
    @Synchronized
    fun trimTo(limit: Int) {
        maxItems = limit.coerceAtLeast(1)
        while (entries.size > maxItems) entries.removeAt(entries.lastIndex)
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(entries.toList())))
        }
    }

    /**
     * Re-reads the backing file. The settings app and the keyboard hold their
     * own instance of this class, so the keyboard has to re-read before it
     * writes or a "Delete all history" done in the app comes back on the next
     * run.
     */
    @Synchronized
    fun reload() {
        entries.clear()
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            entries.addAll(snapshot.entries)
        }
        nextId = (entries.maxOfOrNull { it.id } ?: 0) + 1
    }

    /** Deletes the file and empties memory: what turning the setting off does. */
    @Synchronized
    fun deleteStorage() {
        entries.clear()
        runCatching { storageFile?.delete() }
    }

    /**
     * Cuts a long text to [MAX_TEXT] without orphaning half a character. Ending
     * on a lone high surrogate would put a broken code unit in the record, and
     * from there into whatever the user copies out of the history screen.
     */
    private fun String.cut(): String {
        if (length <= MAX_TEXT) return this
        val end = if (this[MAX_TEXT - 1].isHighSurrogate()) MAX_TEXT - 1 else MAX_TEXT
        return substring(0, end)
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 100
        const val MIN_MAX_ITEMS = 10
        const val MAX_ITEMS_CEILING = 500

        /**
         * Longest run of the user's own text one record keeps. The record count
         * and this are a pair: their product is how much of the user's writing
         * sits on disk, and how much is rewritten on every run.
         */
        const val MAX_TEXT = 4_000

        /** Path under the app's private files directory. */
        const val FILE_PATH = "ai/history.json"
    }
}
