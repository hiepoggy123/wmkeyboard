package com.wasimaster.wmkeyboard.core.handwriting

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeyboardLanguage
import com.wasimaster.wmkeyboard.core.settings.language
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** One sampled point of a handwriting stroke, in canvas pixels. */
data class HwPoint(val x: Float, val y: Float, val t: Long)

/** A finished stroke: the points between one touch-down and its release. */
data class HwStroke(val points: List<HwPoint>)

/** Awaits a Play-services Task without the coroutines-play-services artifact. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { if (cont.isActive) cont.cancel() }
}

/**
 * ML Kit Digital Ink model catalog and download management. Shared by the
 * IME (recognition, in-panel download) and the settings app (model manager),
 * so both always agree on which models exist and their language tags.
 */
object HandwritingModels {

    /** Languages the keyboard offers handwriting for, in display order. */
    val supported: List<HandwritingLanguage> = listOf(
        HandwritingLanguage("en-US", "English"),
        HandwritingLanguage("bn", "বাংলা (Bengali)"),
        HandwritingLanguage("fr", "Français (French)"),
        HandwritingLanguage("de", "Deutsch (German)"),
        HandwritingLanguage("es", "Español (Spanish)"),
    )

    /** The recognition model tag for an input mode (all Bengali modes share bn). */
    fun tagForMode(mode: InputMode): String = when (mode.language) {
        KeyboardLanguage.ENGLISH -> "en-US"
        KeyboardLanguage.BANGLA -> "bn"
        KeyboardLanguage.FRENCH -> "fr"
        KeyboardLanguage.GERMAN -> "de"
        KeyboardLanguage.SPANISH -> "es"
    }

    /** Compact badge label for the in-panel language toggle. */
    fun shortLabel(tag: String): String = when (tag) {
        "en-US" -> "EN"
        "bn" -> "বাং"
        "fr" -> "FR"
        "de" -> "DE"
        "es" -> "ES"
        else -> tag.uppercase()
    }

    fun displayName(tag: String): String =
        supported.firstOrNull { it.tag == tag }?.displayName ?: tag

    private val manager = RemoteModelManager.getInstance()

    fun model(tag: String): DigitalInkRecognitionModel? {
        val identifier = runCatching {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
        }.getOrNull() ?: return null
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    suspend fun isDownloaded(tag: String): Boolean {
        val model = model(tag) ?: return false
        return runCatching { manager.isModelDownloaded(model).await() }.getOrDefault(false)
    }

    /** Downloads the model for [tag]; throws on failure (no network, no space). */
    suspend fun download(tag: String) {
        val model = model(tag) ?: throw IllegalArgumentException("No model for $tag")
        manager.download(model, DownloadConditions.Builder().build()).await()
    }

    suspend fun delete(tag: String) {
        val model = model(tag) ?: return
        runCatching { manager.deleteDownloadedModel(model).await() }
    }
}

data class HandwritingLanguage(val tag: String, val displayName: String)

/**
 * A recognizer for the active language. Holds one ML Kit recognizer at a
 * time; switching languages closes the old one. All calls are main-thread
 * safe — ML Kit runs recognition on its own executor.
 */
class HandwritingRecognizerCache {

    private var recognizer: DigitalInkRecognizer? = null
    private var recognizerTag: String? = null

    private fun recognizerFor(tag: String): DigitalInkRecognizer? {
        if (recognizerTag == tag) return recognizer
        recognizer?.close()
        recognizer = null
        recognizerTag = null
        val model = HandwritingModels.model(tag) ?: return null
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        recognizerTag = tag
        return recognizer
    }

    /**
     * Recognizes [strokes] as text. [preContext] is the text before the
     * cursor (last ~20 chars) and [writingAreaWidth]/[writingAreaHeight]
     * the canvas size in px — both improve accuracy (case, segmentation).
     * Returns candidate texts, best first; empty when nothing is recognized.
     * Throws when the model is missing or recognition fails.
     */
    suspend fun recognize(
        tag: String,
        strokes: List<HwStroke>,
        preContext: String,
        writingAreaWidth: Float,
        writingAreaHeight: Float,
        maxCandidates: Int = 4,
    ): List<String> {
        if (strokes.isEmpty()) return emptyList()
        val recognizer = recognizerFor(tag)
            ?: throw IllegalStateException("No recognizer for $tag")
        val inkBuilder = Ink.builder()
        for (stroke in strokes) {
            val strokeBuilder = Ink.Stroke.builder()
            for (point in stroke.points) {
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.t))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val contextBuilder = RecognitionContext.builder()
            // ML Kit caps pre-context at 20 chars; longer values throw.
            .setPreContext(preContext.takeLast(20))
        if (writingAreaWidth > 0f && writingAreaHeight > 0f) {
            contextBuilder.setWritingArea(WritingArea(writingAreaWidth, writingAreaHeight))
        }
        val result = recognizer.recognize(inkBuilder.build(), contextBuilder.build()).await()
        return result.candidates
            .map { it.text }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(maxCandidates)
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        recognizerTag = null
    }
}
