package com.wasimaster.wmkeyboard.core.plugins

import java.io.File

/**
 * A plugin's own log: whatever it `print`ed, plus whatever went wrong.
 *
 * This is the only debugging channel a plugin author has. They cannot attach a
 * debugger to a keyboard panel on someone else's phone, so `print` has to go
 * somewhere they can read, and the settings screen shows this back to them.
 *
 * A ring buffer rather than an ever-growing list, because the failure mode being
 * guarded against is a plugin that prints inside its render loop — which is
 * every plugin, the first time its author is debugging a render loop.
 */
class PluginLog(private val file: File?, private val maxEntries: Int = MAX_ENTRIES) {

    private val lines = ArrayDeque<String>()

    companion object {
        const val MAX_ENTRIES = 200
        const val MAX_LINE = 512
        private const val MAX_FILE_BYTES = 128 * 1024
    }

    @Synchronized
    fun add(line: String) {
        lines.addLast(line.take(MAX_LINE))
        while (lines.size > maxEntries) lines.removeFirst()
    }

    @Synchronized
    fun lines(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        runCatching { file?.delete() }
    }

    /**
     * Writes the buffer out. Called when a plugin session ends rather than on
     * every line: a plugin that prints per keystroke would otherwise turn the
     * log into a write amplifier on the typing path.
     */
    @Synchronized
    fun flush() {
        val target = file ?: return
        if (lines.isEmpty()) return
        runCatching {
            target.parentFile?.mkdirs()
            val text = lines.joinToString("\n").takeLast(MAX_FILE_BYTES)
            target.writeText(text)
        }
    }

    /** Reads back what previous sessions wrote, for the settings screen. */
    @Synchronized
    fun restore() {
        val source = file ?: return
        if (!source.isFile) return
        runCatching {
            source.readText().lineSequence().forEach { add(it) }
        }
    }
}
