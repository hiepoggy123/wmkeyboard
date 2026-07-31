package com.wasimaster.wmkeyboard.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.core.support.Support
import java.io.File

/**
 * The screen a diagnostic build puts up instead of Android's "app has stopped"
 * dialog: the stack trace, in full, with a button to copy or send it.
 *
 * Why this exists at all. A crash on someone else's phone is normally
 * unreachable — the trace goes to logcat, and getting logcat off an Android
 * device needs a computer, a cable and developer options. The in-app debug log
 * screen covers the case where the app still opens; this one covers the case
 * where it does not, which is the worst case and the one where a trace is worth
 * most. So the report is put on the screen at the moment of the crash, where the
 * user can read it and send it back without any tooling.
 *
 * Three constraints shape it, all from the same fact — *the app just crashed,
 * so nothing about the app can be trusted*:
 *
 *  * **Its own process** (`android:process=":crash"` in the manifest). The
 *    process that crashed is killed a moment after this is launched; an activity
 *    inside it would die with it.
 *  * **No Compose, no app theme, no app resources.** Plain views and a platform
 *    theme, built in code. If the crash was in the theme engine, in resource
 *    loading, or in Compose startup, anything reaching for those would crash the
 *    report screen too.
 *  * **No initialisation.** [DebugLog.useCrashFile] points at the record and
 *    nothing else runs. It deliberately does *not* install a crash handler here,
 *    so a failure in this screen cannot launch another copy of it.
 *
 * Only reachable in a build made with `-Pwmkb.enableCrashScreen=true`; a normal
 * build never launches it and keeps Android's own crash dialog.
 */
class CrashReportActivity : Activity() {

    companion object {
        /** The record of the crash that just happened, from [DebugLog]. */
        const val EXTRA_REPORT = "com.wasimaster.wmkeyboard.extra.CRASH_REPORT"

        private const val SHARE_DIR = "diagnostics"
        private const val SHARE_FILE = "wmkeyboard-crash.txt"

        /**
         * How much of the report a `mailto:` draft carries. Mail apps drop or
         * mangle very long bodies, and the top of the report — the build, the
         * device, the exception and its first frames — is the part that gets a
         * bug diagnosed. The full text is one tap away under Share.
         */
        private const val EMAIL_BODY_LIMIT = 4_000

        private const val TRACE_TEXT_SP = 11f
        private const val TITLE_TEXT_SP = 20f
        private const val BODY_TEXT_SP = 13f
        private const val EDGE_PADDING_DP = 16
        private const val GAP_DP = 8
    }

    private var report: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.useCrashFile(this)
        report = buildReport(intent?.getStringExtra(EXTRA_REPORT).orEmpty())
        setContentView(buildLayout())
    }

    /**
     * The crash that just happened, plus whatever else is on disk. Both,
     * because they fail in different ways: the file holds the history but may
     * not have been writable (a locked device, a full disk), and the intent
     * holds only the newest one but always arrives.
     */
    private fun buildReport(fresh: String): String {
        val stored = DebugLog.crashes()
        return buildString {
            appendLine("WM Keyboard crash report")
            appendLine(Support.environment())
            appendLine()
            if (stored.isNotBlank()) append(stored)
            if (fresh.isNotBlank() && !stored.contains(fresh)) {
                appendLine()
                append(fresh)
            }
        }
    }

    private fun buildLayout(): View {
        val pad = dp(EDGE_PADDING_DP)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(
            TextView(this).apply {
                text = "WM Keyboard stopped"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SP)
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        root.addView(
            TextView(this).apply {
                text = "This build shows crashes instead of hiding them. Copy or share the " +
                    "report below and send it to the developer — it says what failed, on " +
                    "which build and which device. It contains nothing you have typed."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SP)
                setPadding(0, dp(GAP_DP), 0, dp(GAP_DP))
            },
        )

        // Both ways: stack frames are long lines, and wrapping them makes the
        // trace almost unreadable.
        val trace = TextView(this).apply {
            text = report
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TRACE_TEXT_SP)
            setTextIsSelectable(true)
        }
        val horizontal = HorizontalScrollView(this).apply { addView(trace) }
        root.addView(
            ScrollView(this).apply { addView(horizontal) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f),
        )

        root.addView(
            button("Email the developer") { emailReport() },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )
        root.addView(buttonRow())
        return root
    }

    private fun buttonRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val even = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        row.addView(button("Copy") { copyReport() }, even)
        row.addView(button("Share") { shareReport() }, even)
        row.addView(button("Close") { finishAndRemoveTask() }, even)
        return row
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun copyReport() {
        val copied = runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("WM Keyboard crash", report))
        }.isSuccess
        toast(if (copied) "Crash report copied" else "Couldn't reach the clipboard")
    }

    /**
     * Shares the report as a file rather than as EXTRA_TEXT: with a few crashes
     * on record it runs to tens of kilobytes, and an intent that size is
     * silently dropped by half the apps that would receive it. Falls back to
     * plain text when the file cannot be written, since a truncated report
     * still beats no report.
     */
    private fun shareReport() {
        val uri = runCatching {
            val dir = File(cacheDir, SHARE_DIR).apply { mkdirs() }
            val file = File(dir, SHARE_FILE)
            file.writeText(report)
            FileProvider.getUriForFile(this, "$packageName.clipboard", file)
        }.getOrNull()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "WM Keyboard crash report")
            if (uri == null) {
                putExtra(Intent.EXTRA_TEXT, report)
            } else {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val sent = runCatching {
            startActivity(Intent.createChooser(intent, "Share crash report"))
        }.isSuccess
        if (!sent) toast("Nothing on this device can share it — use Copy")
    }

    private fun emailReport() {
        val body = if (report.length <= EMAIL_BODY_LIMIT) {
            report
        } else {
            report.take(EMAIL_BODY_LIMIT) + "\n… (truncated — use Share for the full report)"
        }
        if (!Support.email(this, "WM Keyboard crash report", body)) {
            toast("No mail app on this device — use Copy or Share")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
