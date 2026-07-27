package com.wasimaster.wmkeyboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.core.debug.LogEntry
import com.wasimaster.wmkeyboard.core.debug.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Reads back what the keyboard recorded about itself, and hands it to whoever
 * asked for it.
 *
 * The reason this screen exists rather than "run adb logcat": a keyboard's
 * failures are invisible ones. The IME dies and Android quietly swaps in another
 * keyboard, a panel comes up empty, a model does not load — none of it reaches
 * the user as something they can quote in a bug report, and pulling a logcat off
 * a phone needs a computer and developer mode.
 *
 * Two sources, because they answer different questions. The app log is what the
 * keyboard chose to record — lifecycle, failures it handled — and never contains
 * anything typed. The system log is this process's own Android log, which
 * catches what the app never saw: a library's stack trace, the framework's
 * complaints about our window. That one is the whole process's output and is
 * shown behind its own switch, with a warning, because we cannot promise what a
 * dependency decided to print into it.
 */
@Composable
internal fun DebugLogScreen() {
    val context = LocalContext.current
    var showSystemLog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var minLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    // Bumped to re-read the log after a clear, since neither store is a Flow.
    var revision by remember { mutableStateOf(0) }

    val entries by produceState(emptyList<LogEntry>(), revision) {
        value = DebugLog.snapshot().asReversed()
    }
    val crashes by produceState("", revision) {
        value = withContext(Dispatchers.IO) { DebugLog.crashes() }
    }
    val systemLog by produceState("", showSystemLog, revision) {
        value = if (showSystemLog) withContext(Dispatchers.IO) { DebugLog.systemLog() } else ""
    }

    val shown = remember(entries, query, minLevel) {
        entries.filter { it.level.ordinal >= minLevel.ordinal }
            .filter {
                query.isBlank() ||
                    it.message.contains(query, ignoreCase = true) ||
                    it.tag.contains(query, ignoreCase = true)
            }
    }

    CaptionText(
        "What the keyboard has recorded about itself since this process started, plus any " +
            "crash it did not survive. Nothing you type is ever recorded here.",
    )

    SettingsGroup("Report") {
        item {
            NavRow(
                "Share diagnostics",
                "Send the report to an email, an issue tracker, or a notes app",
            ) { shareReport(context, showSystemLog, systemLog) }
        }
        item {
            NavRow("Copy to clipboard", "The same report, as text") {
                copyReport(context, showSystemLog, systemLog)
            }
        }
        item {
            ToggleSetting(
                "Include the system log",
                "Add this process's own Android log to the report",
                showSystemLog,
                info = "The app log above is written by the keyboard and holds only what it " +
                    "chose to record. The system log is everything this process printed, " +
                    "including its libraries — far more useful for a hard-to-reproduce bug, " +
                    "but not something the keyboard controls the contents of. Read it before " +
                    "sending it anywhere.",
            ) { showSystemLog = it }
        }
    }

    if (crashes.isNotBlank()) {
        SettingsGroup("Crashes") {
            item {
                LogBlock(crashes)
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        DebugLog.clearCrashes()
                        revision++
                    }) { Text("Clear crashes") }
                }
            }
        }
    }

    SettingsGroup("App log") {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filter") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = minLevel == level,
                        onClick = { minLevel = level },
                        label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { revision++ }) { Text("Refresh") }
            }
        }
        if (shown.isEmpty()) {
            item {
                CaptionText(
                    if (entries.isEmpty()) {
                        "Nothing recorded yet. Open the keyboard, reproduce the problem, then " +
                            "come back — the log is kept in memory and lives as long as the app does."
                    } else {
                        "No entries match this filter."
                    },
                )
            }
        }
    }

    // Outside SettingsGroup: the entries are their own scrolling list, and
    // wrapping a few hundred rows in cards would make each one a separate
    // surface for no gain.
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(horizontal = 16.dp),
    ) {
        items(shown) { entry -> LogRow(entry) }
    }

    if (showSystemLog) {
        SettingsGroup("System log") {
            item { LogBlock(systemLog.ifBlank { "This device did not let the app read its own log." }) }
        }
    }

    SettingsGroup {
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    DebugLog.clear()
                    revision++
                }) { Text("Clear app log") }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            "${timeOf(entry.timeMillis)}  ${entry.tag}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.message,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

/** A block of pre-formatted log text: monospaced, and scrolling both ways. */
@Composable
private fun LogBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

private fun timeOf(millis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(java.util.Date(millis))

private fun report(includeSystemLog: Boolean, systemLog: String): String = buildString {
    append(DebugLog.exportText())
    if (includeSystemLog && systemLog.isNotBlank()) {
        appendLine()
        appendLine("== system log ==")
        appendLine(systemLog)
    }
}

private fun copyReport(context: Context, includeSystemLog: Boolean, systemLog: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(
                ClipData.newPlainText("WM Keyboard diagnostics", report(includeSystemLog, systemLog)),
            )
    }
    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
}

/**
 * Shares the report as a file rather than as EXTRA_TEXT: a log runs to tens of
 * kilobytes, and a share intent that size is silently dropped by half the apps
 * that would receive it.
 */
private fun shareReport(context: Context, includeSystemLog: Boolean, systemLog: String) {
    val uri = runCatching {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, "wmkeyboard-log.txt")
        file.writeText(report(includeSystemLog, systemLog))
        FileProvider.getUriForFile(context, "${context.packageName}.clipboard", file)
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(context, "Couldn't write the report", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "WM Keyboard diagnostics")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "Share diagnostics")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
