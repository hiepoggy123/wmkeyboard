package com.wasimaster.wmkeyboard.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.wasimaster.wmkeyboard.common.R
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
 *  * **No Compose and no app theme.** Plain views and a platform theme, built in
 *    code. If the crash was in the theme engine or in Compose startup, anything
 *    reaching for those would crash the report screen too. The only resources it
 *    reads are its own labels, so the screen can be translated.
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
        // Edge to edge on every version, rather than only on the ones that
        // force it, so [applyBarInsets] is the single place that decides where
        // the bars are. The alternative is a window that pads itself on old
        // Android and not on new, and a title behind the status bar on one of
        // the two.
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        applyBarInsets(root, pad)

        root.addView(
            TextView(this).apply {
                text = getString(R.string.common_crash_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SP)
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.common_crash_body)
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

        // First of the actions, because it is the only one that is urgent: if
        // what crashed was the keyboard itself, every other button — and every
        // app the user opens next — needs a keyboard that works to be any use.
        root.addView(
            button(getString(R.string.common_crash_switch_action)) { switchKeyboard() },
            wideButtonParams(),
        )
        root.addView(
            button(getString(R.string.common_crash_email_action)) { emailReport() },
            wideButtonParams(),
        )
        root.addView(buttonRow(), wideButtonParams())
        return root
    }

    /**
     * Full-width, with a gap above it. Platform buttons draw their own
     * background inset, which reads as a hairline of space and nothing more —
     * these are the buttons that send a report and change the keyboard, so
     * hitting the wrong one has to take an actual mis-tap.
     */
    private fun wideButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(GAP_DP)
        }

    private fun buttonRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(button(getString(R.string.common_copy)) { copyReport() }, rowButtonParams())
        row.addView(button(getString(R.string.common_share)) { shareReport() }, rowButtonParams())
        row.addView(
            button(getString(R.string.common_close)) { finishAndRemoveTask() },
            rowButtonParams(),
        )
        return row
    }

    /** An equal share of the row, with half a gap either side of each button. */
    private fun rowButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            val half = dp(GAP_DP) / 2
            marginStart = half
            marginEnd = half
        }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    /**
     * Opens the system's "change keyboard" picker, the way out of a crashed
     * keyboard: whatever is typed next needs a different IME until this build
     * is fixed or restarted.
     *
     * The picker is only granted to the window that currently has input focus,
     * which this one does; where it cannot be reached at all the input-method
     * settings screen is the fallback, since that one is always openable and
     * gets to the same place in two taps.
     */
    private fun switchKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm != null && runCatching { imm.showInputMethodPicker() }.isSuccess) return
        val opened = runCatching {
            startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) toast(getString(R.string.common_crash_switch_error))
    }

    /**
     * Keeps the screen clear of the status bar, the navigation bar and any
     * cutout. The activity runs under a platform theme on a target that is
     * always edge-to-edge, so without this the title sits behind the status bar
     * and the bottom row behind the gesture handle.
     */
    private fun applyBarInsets(root: View, pad: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(
                pad + bars.left,
                pad + bars.top,
                pad + bars.right,
                pad + bars.bottom,
            )
            insets
        }
    }

    private fun copyReport() {
        val copied = runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("WM Keyboard crash", report))
        }.isSuccess
        toast(
            getString(
                if (copied) R.string.common_crash_copied else R.string.common_crash_clipboard_error,
            ),
        )
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
            startActivity(
                Intent.createChooser(intent, getString(R.string.common_crash_share_chooser_title)),
            )
        }.isSuccess
        if (!sent) toast(getString(R.string.common_crash_share_error))
    }

    private fun emailReport() {
        val body = if (report.length <= EMAIL_BODY_LIMIT) {
            report
        } else {
            report.take(EMAIL_BODY_LIMIT) + "\n… (truncated — use Share for the full report)"
        }
        if (!Support.email(this, "WM Keyboard crash report", body)) {
            toast(getString(R.string.common_crash_email_error))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
