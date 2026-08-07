package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.settings.AutoBackupScheduler
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup job runs in `:app` and is scheduled from `:core:settings`, which
 * cannot name an `:app` class, so the link between them is a string. Nothing at
 * compile time ties that string to the class, and the failure is silent in the
 * worst way: scheduling succeeds, the job never runs, and the user is told
 * nothing because from the app's side everything is set up correctly.
 *
 * This reads the real manifest and checks the two still agree.
 */
class AutoBackupSchedulerTest {

    /** Unit tests run with the module directory as the working directory. */
    private val manifest = File("src/main/AndroidManifest.xml").readText()
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `the scheduled component names a service the manifest declares`() {
        assertTrue(
            "${AutoBackupScheduler.SERVICE_CLASS} is not declared in the manifest",
            manifest.contains("""android:name="${AutoBackupScheduler.SERVICE_CLASS}""""),
        )
    }

    @Test
    fun `the scheduled component names a class that exists`() {
        // Catches the other half: a manifest entry and a constant that agree
        // with each other and with nothing else.
        Class.forName(AutoBackupScheduler.SERVICE_CLASS)
    }

    @Test
    fun `the job service is bound by the platform and not exported`() {
        val declaration = manifest.substringAfter(
            """android:name="${AutoBackupScheduler.SERVICE_CLASS}"""",
        ).substringBefore(">")
        assertTrue(
            "a job service the platform cannot bind is never run",
            declaration.contains("android.permission.BIND_JOB_SERVICE"),
        )
        assertTrue(declaration.contains("""android:exported="false""""))
    }
}
