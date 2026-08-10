package com.wasimaster.wmkeyboard.app.llm

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.wasimaster.wmkeyboard.core.localllm.LlmModule
import com.wasimaster.wmkeyboard.core.localllm.LlmModuleGate

/**
 * Play-channel delivery of the on-demand `:feature:llm` module. This
 * directory replaces `src/noplay/java` when `wmkb.enablePlayStore` is on —
 * the same seam that keeps every Play Core binary out of the F-Droid and
 * direct-download APKs (see NoPlayUpdater).
 */

/** The split's name: the last segment of the `:feature:llm` project path. */
private const val LLM_MODULE = "llm"

/**
 * Publishes the SplitInstall-backed [LlmModuleGate] so `LocalLlmEngine` can
 * see and fetch its runtime. Called once from [WMApplication.onCreate];
 * everything it does is lazy, nothing here touches the network by itself.
 */
fun installLlmDelivery(app: Application) {
    val manager = SplitInstallManagerFactory.create(app)
    LlmModule.gate = object : LlmModuleGate {
        override fun installed() = LLM_MODULE in manager.installedModules

        override fun requestInstall() {
            if (installed()) return
            // Fire-and-forget by design: LocalLlmEngine reports "downloading,
            // try again shortly" to the user, and re-requesting the module of
            // an active session just returns that session. A failure (no
            // network, no space) leaves installedModules unchanged, so the
            // next attempt retries rather than wedging.
            manager.startInstall(
                SplitInstallRequest.newBuilder().addModule(LLM_MODULE).build(),
            )
        }
    }
}

/**
 * Lets this process load classes and native libraries from splits installed
 * after it started — without this, a freshly downloaded module would need an
 * app restart before [Class.forName] on the bridge succeeds. Called from
 * [WMApplication.attachBaseContext], which covers the IME service and the
 * settings activity alike (one process).
 */
fun llmSplitCompat(context: Context) {
    SplitCompat.install(context)
}
