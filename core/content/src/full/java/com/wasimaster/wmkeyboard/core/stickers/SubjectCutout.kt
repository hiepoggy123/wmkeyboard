package com.wasimaster.wmkeyboard.core.stickers

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.wasimaster.wmkeyboard.core.util.runCancellable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One-tap background removal for the sticker editor, over ML Kit's subject
 * segmentation.
 *
 * The model is not in the APK. It is a Play services module, downloaded on
 * demand, which is why this exposes [modelReady] and [ensureModel] separately
 * from [cutOut]: the editor asks first and offers the download as a visible
 * choice, rather than declaring the model in the manifest and making every
 * user of the full build pay for it at install time whether or not they ever
 * open a sticker pack.
 *
 * Callers must have run `MlKitInit.ensure(context)` first — after a reboot
 * into the lock screen, ML Kit's own ContentProvider never ran, and every
 * entry point throws for the life of the process without it.
 *
 * The lite flavor ships the same object with the same signatures, reporting
 * [supported] = false.
 */
object SubjectCutout {

    const val supported: Boolean = true

    /** Below this share of the canvas, a mask is a speck and not a subject. */
    private const val MIN_COVERAGE = 0.01f

    /** Above this, it kept everything, which is the same as doing nothing. */
    private const val MAX_COVERAGE = 0.99f

    sealed interface Result {
        /** ALPHA_8 mask the size of the input; 255 keeps the pixel. */
        data class Ok(val alpha: Bitmap) : Result

        /** The model ran and found nothing worth keeping. */
        data object NoSubject : Result

        /** Play services could not give us the model. */
        data object ModelUnavailable : Result

        data object Failed : Result

        /** This build has no segmenter at all. */
        data object Unsupported : Result
    }

    private fun options() = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    /** Whether the module is already on the device. Never asks to install. */
    suspend fun modelReady(context: Context): Boolean = runCancellable {
        val client = ModuleInstall.getClient(context)
        val segmenter = SubjectSegmentation.getClient(options())
        try {
            client.areModulesAvailable(segmenter).await().areModulesAvailable()
        } finally {
            segmenter.close()
        }
    }.getOrDefault(false)

    /**
     * Requests the module, reporting progress from 0 to 1. False when it
     * cannot be had at all — no Play services, no network, device policy —
     * in which case the editor keeps its brushes and says so.
     */
    suspend fun ensureModel(context: Context, onProgress: (Float) -> Unit = {}): Boolean =
        runCancellable {
            val client = ModuleInstall.getClient(context)
            val segmenter = SubjectSegmentation.getClient(options())
            try {
                val listener = InstallStatusListener { update: ModuleInstallStatusUpdate ->
                    val progress = update.progressInfo ?: return@InstallStatusListener
                    val total = progress.totalBytesToDownload
                    if (total > 0) {
                        onProgress((progress.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                val request = ModuleInstallRequest.newBuilder()
                    .addApi(segmenter)
                    .setListener(listener)
                    .build()
                client.installModules(request).await()
                client.areModulesAvailable(segmenter).await().areModulesAvailable()
            } finally {
                segmenter.close()
            }
        }.getOrDefault(false)

    /**
     * The subject of [image] as an alpha mask.
     *
     * `enableForegroundBitmap` gives back the input's own pixels with the
     * background cleared, so the mask is one `extractAlpha` away and there is
     * no confidence threshold to guess at.
     *
     * A mask that keeps almost nothing, or almost everything, comes back as
     * [Result.NoSubject]: applying the first would erase the sticker, and
     * since the edit is destructive there would be nothing to recover.
     */
    @Suppress("UnusedParameter")
    suspend fun cutOut(context: Context, image: Bitmap): Result {
        val segmenter = runCatching { SubjectSegmentation.getClient(options()) }.getOrNull()
            ?: return Result.ModelUnavailable
        return try {
            val result = runCancellable {
                segmenter.process(InputImage.fromBitmap(image, 0)).await()
            }.getOrNull() ?: return Result.Failed
            val foreground = result.foregroundBitmap ?: return Result.Failed
            val alpha = foreground.extractAlpha()
            if (foreground != image) foreground.recycle()
            val coverage = coverageOf(alpha)
            if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) {
                alpha.recycle()
                Result.NoSubject
            } else {
                Result.Ok(alpha)
            }
        } finally {
            segmenter.close()
        }
    }

    /** Share of [alpha]'s pixels the mask keeps, sampled on a coarse grid. */
    private fun coverageOf(alpha: Bitmap): Float {
        val step = maxOf(1, minOf(alpha.width, alpha.height) / 64)
        var kept = 0
        var seen = 0
        var y = 0
        while (y < alpha.height) {
            var x = 0
            while (x < alpha.width) {
                seen++
                if ((alpha.getPixel(x, y) ushr 24) > 127) kept++
                x += step
            }
            y += step
        }
        return if (seen == 0) 0f else kept.toFloat() / seen
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.cancel(it) }
    }
}
