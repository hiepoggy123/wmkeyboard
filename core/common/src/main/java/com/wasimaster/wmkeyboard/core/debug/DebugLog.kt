package com.wasimaster.wmkeyboard.core.debug

import android.content.Context
import android.os.Build
import com.wasimaster.wmkeyboard.config.BuildConfig
import com.wasimaster.wmkeyboard.core.directboot.DirectBoot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Severity of a [LogEntry], in the order the viewer's filter steps through. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** One recorded line: when, how bad, what part of the app, and what happened. */
data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * A small in-memory log the user can read and send back, plus a crash record
 * that survives the process.
 *
 * Why an app-side log at all, when Android has one: a keyboard's failures are
 * mostly invisible. The IME crashes and the platform silently swaps in another
 * keyboard; a panel comes up empty; a model does not load. None of that reaches
 * the user as an error they can quote, and getting a logcat off an Android phone
 * needs a computer and developer mode. So the interesting moments are recorded
 * here, and the About screen can show and share them.
 *
 * Two stores, on purpose:
 *
 *  * The **ring** is in memory, [CAPACITY] entries, shared by the keyboard
 *    service and the settings app when they are in the same process. Cheap
 *    enough to leave always on — there is no switch to forget to turn on before
 *    reproducing a problem, which is the failure mode of every opt-in log.
 *  * The **crash file** is the one thing written to disk, because a crash is
 *    exactly the case where the ring dies with the process. It lives in
 *    device-protected storage so a crash on the lock screen — where a keyboard
 *    spends some of its most important seconds — is still recorded.
 *
 * Nothing typed is ever logged. Call sites pass what happened, never what the
 * user wrote: this file is meant to be shareable without a second thought, and
 * that is only true if it never holds their text.
 */
object DebugLog {

    /** Entries kept in memory. A few hundred covers a whole session's events. */
    private const val CAPACITY = 500

    /** Crash records kept on disk; older ones are dropped as new ones land. */
    private const val CRASH_CAPACITY = 10

    private const val CRASH_DIR = "debug"
    private const val CRASH_FILE = "crashes.log"

    /** Separator between crash records in the file, on its own line. */
    private const val CRASH_SEPARATOR = "---- crash ----"

    private val entries = ArrayDeque<LogEntry>(CAPACITY)

    /** Set by [attach]; device-protected so it is writable from boot. */
    @Volatile
    private var crashFile: File? = null

    @Volatile
    private var handlerInstalled = false

    fun d(tag: String, message: String) = record(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = record(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = record(LogLevel.WARN, tag, message)

    fun e(tag: String, message: String, error: Throwable? = null) =
        record(LogLevel.ERROR, tag, if (error == null) message else "$message: ${error.summary()}")

    @Synchronized
    private fun record(level: LogLevel, tag: String, message: String) {
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast(LogEntry(System.currentTimeMillis(), level, tag, message))
    }

    /** Everything recorded this session, oldest first. */
    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    /**
     * Points the crash record at device-protected storage and installs the
     * uncaught-exception handler. Idempotent — both the keyboard service and the
     * settings app call it on create, and they may share a process.
     *
     * The previous handler is chained rather than replaced: Android's default is
     * what actually ends the process, and skipping it would leave a dead app on
     * screen instead of a crash.
     */
    @Synchronized
    fun attach(context: Context) {
        if (crashFile == null) {
            crashFile = runCatching {
                val dir = File(DirectBoot.deviceContext(context).filesDir, CRASH_DIR)
                dir.mkdirs()
                File(dir, CRASH_FILE)
            }.getOrNull()
        }
        if (handlerInstalled) return
        handlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private val channelTag: String
        get() = when {
            BuildConfig.ENABLE_PLAY_STORE -> " (Play Store)"
            BuildConfig.ENABLE_FDROID -> " (F-Droid)"
            else -> ""
        }

    /** Appends a crash record, trimming the file to [CRASH_CAPACITY] records. */
    private fun writeCrash(threadName: String, error: Throwable) {
        val file = crashFile ?: return
        val record = buildString {
            appendLine(CRASH_SEPARATOR)
            appendLine("time: ${timestamp(System.currentTimeMillis())}")
            appendLine("thread: $threadName")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}$channelTag")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(error.stackTraceToString())
            // What the app was doing on the way in is usually the whole story,
            // and the ring is about to die with the process.
            appendLine("recent:")
            snapshot().takeLast(40).forEach { appendLine("  ${it.format()}") }
        }
        val existing = runCatching { file.readText() }.getOrDefault("")
        val kept = (existing + record)
            .split(CRASH_SEPARATOR)
            .filter { it.isNotBlank() }
            .takeLast(CRASH_CAPACITY)
            .joinToString("") { "$CRASH_SEPARATOR$it" }
        runCatching { file.writeText(kept) }
    }

    /** Crash records written since the last [clearCrashes], oldest first. */
    fun crashes(): String = runCatching { crashFile?.readText().orEmpty() }.getOrDefault("")

    fun clearCrashes() {
        runCatching { crashFile?.delete() }
    }

    /**
     * The whole report, ready to share: a header saying what build and device
     * this is, then the crashes, then the session's entries.
     */
    fun exportText(): String = buildString {
        appendLine("WM Keyboard diagnostics")
        appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}$channelTag ${BuildConfig.BUILD_TYPE}")
        appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("taken: ${timestamp(System.currentTimeMillis())}")
        appendLine()
        val crashes = crashes()
        if (crashes.isNotBlank()) {
            appendLine("== crashes ==")
            appendLine(crashes)
            appendLine()
        }
        appendLine("== session ==")
        snapshot().forEach { appendLine(it.format()) }
    }

    /**
     * This process's own Android log, which is where anything the app did not
     * record itself ends up — the libraries, the framework's complaints about
     * our windows, the stack trace of a crash Android caught first.
     *
     * Reading it needs no permission since Android 4.1: an app can only ever see
     * its own entries. Returns an empty string on the devices that refuse.
     */
    fun systemLog(maxLines: Int = 500): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "time", "--pid=${android.os.Process.myPid()}",
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text.lineSequence().toList().takeLast(maxLines).joinToString("\n")
    }.getOrDefault("")

    private fun LogEntry.format(): String =
        "${timestamp(timeMillis)}  ${level.name.padEnd(5)} $tag: $message"

    /** Cause chain flattened to one line — the message alone often says nothing. */
    private fun Throwable.summary(): String {
        val chain = generateSequence(this) { it.cause }.take(4)
        return chain.joinToString(" ← ") { "${it::class.java.simpleName}: ${it.message.orEmpty()}" }
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(java.util.Date(millis))
}
