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
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.ScriptId
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

    /**
     * ML Kit's own catalogue, minus the non-language recognizers (autodraw,
     * emoji, shapes) and the gesture models, which all carry an `-x-` private
     * subtag. Read once — it is a static table inside the library.
     */
    private val allIdentifiers: List<DigitalInkRecognitionModelIdentifier> by lazy {
        runCatching {
            DigitalInkRecognitionModelIdentifier.allModelIdentifiers()
                .filter { !it.languageTag.contains("-x-") }
        }.getOrDefault(emptyList())
    }

    /**
     * Language ids whose ML Kit subtag is spelled differently. Only the ones
     * the keyboard's own registry actually offers are worth listing.
     */
    private val SUBTAG_ALIASES = mapOf(
        "nb" to "no",   // Bokmål — ML Kit ships plain Norwegian
        "tl" to "fil",  // Tagalog — ML Kit ships Filipino
    )

    /** ISO 15924 codes, the way ML Kit spells its script subtags. */
    private val SCRIPT_CODES = mapOf(
        ScriptId.LATIN to "Latn", ScriptId.CYRILLIC to "Cyrl", ScriptId.GREEK to "Grek",
        ScriptId.ARMENIAN to "Armn", ScriptId.GEORGIAN to "Geor", ScriptId.ARABIC to "Arab",
        ScriptId.HEBREW to "Hebr", ScriptId.SYRIAC to "Syrc", ScriptId.DEVANAGARI to "Deva",
        ScriptId.BENGALI to "Beng", ScriptId.GURMUKHI to "Guru", ScriptId.GUJARATI to "Gujr",
        ScriptId.ORIYA to "Orya", ScriptId.TAMIL to "Taml", ScriptId.TELUGU to "Telu",
        ScriptId.KANNADA to "Knda", ScriptId.MALAYALAM to "Mlym", ScriptId.SINHALA to "Sinh",
        ScriptId.THAI to "Thai", ScriptId.LAO to "Laoo", ScriptId.KHMER to "Khmr",
        ScriptId.MYANMAR to "Mymr", ScriptId.HANGUL to "Hang", ScriptId.ETHIOPIC to "Ethi",
        ScriptId.THAANA to "Thaa", ScriptId.JAPANESE to "Jpan", ScriptId.HAN to "Hani",
        ScriptId.TIFINAGH to "Tfng", ScriptId.CHEROKEE to "Cher", ScriptId.NKO to "Nkoo",
        ScriptId.CANADIAN_ABORIGINAL_SYLLABICS to "Cans", ScriptId.TIBETAN to "Tibt",
    )

    /**
     * The recognition model tag for one of the keyboard's languages, or null
     * when ML Kit has no model for it. Among a language's variants: the one
     * matching the language's own locale wins, then the bare tag, then one
     * written in the language's own script — that last step is what keeps
     * Sanskrit on `sa-Deva-IN` rather than the romanised `sa-Latn`.
     */
    fun tagFor(language: LanguageDef): String? {
        val subtag = SUBTAG_ALIASES[language.id] ?: language.id
        val candidates = allIdentifiers.filter { it.languageSubtag == subtag }
        if (candidates.isEmpty()) return null
        val locale = language.localeTag.lowercase()
        val region = locale.substringAfter('-', "")
        val script = SCRIPT_CODES[language.script]
        return candidates.firstOrNull { it.languageTag.lowercase() == locale }?.languageTag
            ?: candidates.firstOrNull { it.languageTag.lowercase() == subtag }?.languageTag
            ?: candidates.filter { it.scriptSubtag == script }
                .minByOrNull { it.languageTag.length }?.languageTag
            ?: candidates.firstOrNull {
                it.scriptSubtag.isNullOrEmpty() && it.regionSubtag.orEmpty().lowercase() == region
            }?.languageTag
            ?: candidates.minByOrNull { it.languageTag.length }?.languageTag
    }

    /** The recognition model tag for a language id; falls back to the id itself. */
    fun tagForLangId(langId: String): String =
        tagFor(LanguageRegistry.byId(langId)) ?: if (langId == "en") "en-US" else langId

    /**
     * The models worth offering: one per language the user actually types in,
     * skipping the ones ML Kit cannot recognize. This is what the settings
     * screen lists, so enabling a language is all it takes for its model to
     * show up there.
     */
    fun modelsFor(languages: List<LanguageDef>): List<HandwritingLanguage> =
        languages.distinctBy { it.id }.mapNotNull { language ->
            tagFor(language)?.let { HandwritingLanguage(it, language.displayName) }
        }.distinctBy { it.tag }

    /**
     * Compact badge for the in-panel language toggle. The keyboard's own
     * language id rather than ML Kit's tag, so the badge reads NB and TL where
     * the model is called `no` and `fil-Latn`.
     */
    fun shortLabel(tag: String): String =
        (languageForTag(tag)?.id ?: tag.substringBefore('-')).uppercase()

    fun displayName(tag: String): String = languageForTag(tag)?.displayName ?: tag

    private fun languageForTag(tag: String): LanguageDef? {
        val subtag = tag.substringBefore('-')
        return LanguageRegistry.all.firstOrNull { (SUBTAG_ALIASES[it.id] ?: it.id) == subtag }
    }

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
