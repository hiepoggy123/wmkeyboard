package com.wasimaster.wmkeyboard.ime

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import android.content.ClipDescription
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.app.CalendarPermissionActivity
import com.wasimaster.wmkeyboard.app.CameraPermissionActivity
import com.wasimaster.wmkeyboard.app.DocScanActivity
import com.wasimaster.wmkeyboard.app.MainActivity
import com.wasimaster.wmkeyboard.app.MicPermissionActivity
import com.wasimaster.wmkeyboard.app.StoragePermissionActivity
import com.wasimaster.wmkeyboard.core.media.GallerySaver
import com.wasimaster.wmkeyboard.core.media.MediaMime
import com.wasimaster.wmkeyboard.core.settings.MediaSendMode
import android.provider.DocumentsContract
import com.wasimaster.wmkeyboard.core.clipboard.ClipKind
import com.wasimaster.wmkeyboard.core.clipboard.ClipLinks
import com.wasimaster.wmkeyboard.core.clipboard.ClipboardStore
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiRenderCheck
import com.wasimaster.wmkeyboard.core.emoji.EmojiSearch
import com.wasimaster.wmkeyboard.core.emoji.EmojiSuggester
import com.wasimaster.wmkeyboard.core.emoji.EmojiUsage
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariantIndex
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.feedback.KeySoundPlayer
import com.wasimaster.wmkeyboard.core.gesture.GestureDecoder
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.handwriting.HandwritingModels
import com.wasimaster.wmkeyboard.core.handwriting.HandwritingRecognizerCache
import com.wasimaster.wmkeyboard.core.handwriting.HwStroke
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.wasimaster.wmkeyboard.core.prediction.Apostrophes
import com.wasimaster.wmkeyboard.core.input.DeadKeys
import com.wasimaster.wmkeyboard.core.prediction.AppNames
import com.wasimaster.wmkeyboard.core.prediction.ContactEmails
import com.wasimaster.wmkeyboard.core.prediction.ContactNames
import com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.EnglishBengaliMap
import com.wasimaster.wmkeyboard.core.prediction.KeyProximity
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SeedBigrams
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.SystemUserDictionary
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.prediction.WordSource
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.core.settings.EmojiInsertMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.KeyboardMode
import com.wasimaster.wmkeyboard.core.settings.LetterSwipeAction
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ModeField
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.OneHandedSide
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.applyMode
import com.wasimaster.wmkeyboard.core.settings.resolveKeyboardMode
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import com.wasimaster.wmkeyboard.core.settings.GifSourceMode
import com.wasimaster.wmkeyboard.core.text.EmojiGraphemes
import com.wasimaster.wmkeyboard.core.text.WordDelete
import com.wasimaster.wmkeyboard.core.tools.BraveSearchClient
import com.wasimaster.wmkeyboard.core.tools.DictionaryClient
import com.wasimaster.wmkeyboard.core.tools.GifItem
import com.wasimaster.wmkeyboard.core.grammar.GrammarChecker
import com.wasimaster.wmkeyboard.core.grammar.GrammarFix
import com.wasimaster.wmkeyboard.core.grammar.GrammarLint
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.core.tools.GifSource
import com.wasimaster.wmkeyboard.core.tools.LinkPreviewClient
import com.wasimaster.wmkeyboard.core.tools.GifSources
import com.wasimaster.wmkeyboard.core.tools.GiphyClient
import com.wasimaster.wmkeyboard.core.tools.ImageResult
import com.wasimaster.wmkeyboard.core.tools.KlipyClient
import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmCatalog
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmEngine
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmStore
import com.wasimaster.wmkeyboard.core.tools.AiClient
import com.wasimaster.wmkeyboard.core.tools.AiPrompts
import com.wasimaster.wmkeyboard.core.tools.AiMarkdown
import com.wasimaster.wmkeyboard.core.tools.AiThinking
import com.wasimaster.wmkeyboard.core.tools.CurrencyClient
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.QrCodeGen
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.core.tools.ToolHttp
import com.wasimaster.wmkeyboard.core.tools.CharState
import com.wasimaster.wmkeyboard.core.tools.TranslateClient
import com.wasimaster.wmkeyboard.core.tools.TypedWord
import com.wasimaster.wmkeyboard.core.tools.TypingBests
import com.wasimaster.wmkeyboard.core.tools.TypingHistory
import com.wasimaster.wmkeyboard.core.tools.TypingResult
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import com.wasimaster.wmkeyboard.core.tools.WpmSample
import com.wasimaster.wmkeyboard.core.tools.buildTypingPrompt
import com.wasimaster.wmkeyboard.core.tools.compareWord
import com.wasimaster.wmkeyboard.core.tools.scoreTypingTest
import com.wasimaster.wmkeyboard.core.tools.typingConfigKey
import com.wasimaster.wmkeyboard.core.tools.typingConfigLabel
import com.wasimaster.wmkeyboard.core.tools.WikipediaClient
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import com.wasimaster.wmkeyboard.core.tools.WebResult
import com.wasimaster.wmkeyboard.core.voice.VoiceInputEngine
import com.wasimaster.wmkeyboard.core.voice.VoicePunctuation
import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.ClipboardKeyAction
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.ModifierKey
import com.wasimaster.wmkeyboard.core.layout.numberRowFor
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.input.composer.composerFor
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.layout.composerType
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.layout.script
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import com.wasimaster.wmkeyboard.ime.ui.KeyboardScreen
import android.inputmethodservice.InputMethodService
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.EnumMap

/**
 * The WM Keyboard input method service.
 *
 * Owns the engines (prediction, transliteration, emoji, clipboard), a
 * single [KeyboardUiState] flow, and the InputConnection plumbing. The
 * Compose view is pure presentation: it renders the state and calls back
 * into [onKey]/[onSuggestion]/[onEmoji]/etc.
 */
class WMKeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var lifecycleOwner: KeyboardViewLifecycleOwner

    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var settingsRepository: SettingsRepository
    private var suggestionEngine: SuggestionEngine? = null
    private var emojiSearch: EmojiSearch? = null
    private var emojiSuggester: EmojiSuggester? = null
    private var emojiEntries: List<EmojiEntry> = emptyList()
    private lateinit var userLexicon: UserLexicon
    private lateinit var emojiUsage: EmojiUsage
    private lateinit var clipboardStore: ClipboardStore
    /** In-flight link-metadata fetch; one at a time (see [fetchLinkPreviews]). */
    private var linkPreviewJob: Job? = null
    /** Auto-hide timer for the recently-copied strip chip (see [showClipboardSuggestion]). */
    private var clipboardSuggestionJob: Job? = null
    private lateinit var snippetStore: SnippetStore

    /** Latest settings straight from DataStore, before mode overrides. */
    private var baseSettings: KeyboardSettings? = null
    /** Manual pick from the Modes tool; wins until the user switches app. */
    private var manualModeId: String? = null
    /** Package name of the app the focused field belongs to. */
    private var currentPackage: String? = null
    /** Mode-binding kinds of the focused field (password, email, url…). */
    private var currentModeFields: Set<ModeField> = emptySet()

    /**
     * Input mode the focused field itself asked for — IME_FLAG_FORCE_ASCII
     * or a hintLocales match. Field-scoped and never persisted: it overrides
     * the saved mode while the field has focus, and the user's own language
     * switch (spacebar swipe, 🌐) clears it, because an explicit switch is
     * always a stronger signal than the app's request.
     */
    private var fieldLayoutOverride: String? = null

    /** This IME's framework id, used to register subtypes and mirror OS switches. */
    private val imeId: String by lazy { ComponentName(this, javaClass).flattenToShortString() }
    /**
     * Signature of the last subtype set pushed to the framework (enabled ids +
     * label style, or "off"); skips redundant writes, since the settings flow
     * emits on every unrelated change and re-registering thrashes the switcher.
     */
    private var registeredSubtypeSig: String? = null

    private var composing = StringBuilder()
    private var previousWord: String? = null
    private var lastSpaceTime = 0L
    /** uptime of the last spacebar/volume caret-scrub step; see [CARET_SCRUB_WINDOW_MS]. */
    private var lastCaretScrubMs = 0L
    private var lastShiftTapTime = 0L
    private var suggestionJob: Job? = null

    /** Rolling average of one suggestion computation, drives the debounce. */
    private var suggestionCostMs = 0L

    /**
     * The space/enter commit resolution the async suggestion job computed off
     * the main thread for the word currently being composed — the English
     * autocorrect target or the Bengali transliteration top. [commitComposing]
     * reads it instead of running the edit-distance search on the UI thread,
     * but only when [CommitResolution.typed] still equals the word being
     * committed; otherwise it recomputes synchronously, so a commit never uses
     * a stale result. Written from the suggestion coroutine, read on main —
     * hence [Volatile].
     */
    @Volatile
    private var commitResolution: CommitResolution? = null

    private class CommitResolution(
        val typed: String,
        val isBengali: Boolean,
        /** Transliteration top for Bengali phonetic mode; null otherwise. */
        val bengaliTop: String?,
        /** English autocorrect target, or null when the word stands as typed. */
        val correction: String?,
    )

    /**
     * The last commit autocorrect changed, as typed-to-committed, so an
     * immediate backspace can undo the correction. Any other input clears it.
     */
    private var lastAutocorrect: Pair<String, String>? = null

    /**
     * True when the last keystroke auto-inserted a space right after
     * punctuation (the double-space ". "), so the very next shift press can
     * cancel that space instead of arming caps. Any other key clears it.
     */
    private var pendingAutoSpace = false

    /** Contact-name words for suggestions, when the setting + permission allow. */
    private var contactNames: ContactNames = ContactNames.EMPTY

    /** Contact email addresses for completion, when the setting + permission allow. */
    private var contactEmails: ContactEmails = ContactEmails.EMPTY

    /** Installed-app label words for suggestions, when the setting allows. */
    private var appNames: AppNames = AppNames.EMPTY

    /**
     * Accent armed by a dead key, waiting for the letter to combine with.
     * Mirrored into [KeyboardUiState.pendingDeadKey] for the strip chip.
     */
    private var pendingDeadKey: Char? = null

    /** English word list used by the gesture decoder (bundled dictionary). */
    private var gestureLexicon: List<Pair<String, Int>> = emptyList()

    /**
     * `gestureLexicon` merged with the user's weighted personal words, cached so
     * a live swipe (many preview events) doesn't rebuild a ~17k-entry list per
     * event. Invalidated whenever either input changes (see
     * [invalidateGestureLexicon]).
     */
    private var cachedGestureLexicon: List<Pair<String, Int>>? = null

    /** Word lists the user imported, one trie per language (empty when none). */
    private var customDictionaries: Map<String, WordSource> = emptyMap()

    /** Bundled Bengali entries, kept so the phonetic index can be rebuilt. */
    private var bengaliAssetEntries: List<Pair<String, Int>> = emptyList()

    /** Last word committed by a swipe, so tapping an alternate replaces it. */
    private var lastGestureWord: String? = null
    private var previewJob: Job? = null

    // ---- network tool state (translate, gif/sticker, web/image search) ----
    private var translateJob: Job? = null
    /** Offline grammar tool (Harper); job debounces re-lints while typing. */
    private var grammarJob: Job? = null
    private var mediaFetchJob: Job? = null
    private var mediaLiveSearchJob: Job? = null
    private var mediaInsertJob: Job? = null
    private var webSearchJob: Job? = null
    private var imageSearchJob: Job? = null

    // ---- voice input state ----
    private val voiceEngine = VoiceInputEngine(this)
    /** Bumped when a session ends/aborts so late recognizer callbacks drop. */
    private var voiceGeneration = 0
    /** The text at the cursor needed a separating space when dictation began. */
    private var voiceNeedsSpace = false
    /** User tapped stop: the pending final must not chain another utterance. */
    private var voiceStopRequested = false
    /** Consecutive empty utterances in continuous mode; give up after a few. */
    private var voiceSilentRetries = 0
    /** Last dictated commit, so the undo chip can take it back whole. */
    private var lastVoiceCommit: String? = null

    // ---- handwriting recognition state ----
    private val hwRecognizer = HandwritingRecognizerCache()
    private var hwJob: Job? = null
    /** Bumped on every stroke/undo/clear so in-flight recognitions go stale. */
    private var hwGeneration = 0
    private var hwCanvasSize = IntSize.Zero
    /** True while letter-area swipes are armed for handwriting (full builds). */
    private var hwKeyboardArmed = false
    /** Show the "download a model" hint at most once per keyboard session. */
    private var hwModelHintShown = false

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val state = _uiState.value
        if (!state.settings.clipboard.history ||
            (state.incognitoOn && state.settings.incognitoPausesClipboard) ||
            state.secureField
        ) return@OnPrimaryClipChangedListener
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return@OnPrimaryClipChangedListener
        val item = clip.getItemAt(0) ?: return@OnPrimaryClipChangedListener

        val uri = item.uri
        // Skip clips we set ourselves (pasting an image re-copies it to the
        // system clipboard as a fallback); re-adding would duplicate it.
        if (uri != null && uri.authority == clipboardFileProviderAuthority) return@OnPrimaryClipChangedListener

        // Which app the user copied from, resolved now while it's still the
        // foreground app (best-effort; null unless opted in and permitted).
        val source = if (state.settings.clipboard.trackSource) resolveClipSource() else null

        val imageMime = uri?.let { u ->
            runCatching { contentResolver.getType(u) }.getOrNull()?.takeIf { it.startsWith("image/") }
        }
        // Non-image URIs are files or folders copied from a file manager;
        // record them by reference (see [addFileClips]) rather than copying.
        if (uri != null && imageMime == null && item.text == null) {
            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }
            if (uris.isNotEmpty()) {
                serviceScope.launch(Dispatchers.IO) { addFileClips(uris, source) }
                return@OnPrimaryClipChangedListener
            }
        }
        if (uri != null && imageMime != null) {
            // Copy the image out of the source app's content provider while the
            // clip's URI grant is valid; the store owns the file afterwards.
            serviceScope.launch(Dispatchers.IO) {
                val copied = runCatching {
                    val dir = File(filesDir, "clipboard/images").apply { mkdirs() }
                    val extension = when (imageMime) {
                        "image/png" -> "png"
                        "image/gif" -> "gif"
                        "image/webp" -> "webp"
                        else -> "jpg"
                    }
                    val target = File(dir, "clip_${System.currentTimeMillis()}.$extension")
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    target
                }.getOrNull()
                if (copied != null) {
                    clipboardStore.addImage(copied, imageMime, source)
                    clipboardStore.save()
                    _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
                }
            }
            return@OnPrimaryClipChangedListener
        }

        val text = item.coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
        val html = item.htmlText
        val added = if (html != null) clipboardStore.addHtml(text, html, source) else clipboardStore.add(text, source)
        clipboardStore.save()
        _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
        if (added != null && added.kind.isTextual && state.settings.clipboard.suggestRecent) {
            showClipboardSuggestion(added)
        }
        if (added?.kind == ClipKind.LINK) fetchLinkPreviews()
    }

    private val clipboardFileProviderAuthority: String
        get() = "$packageName.clipboard"

    /**
     * Best-effort label of the app that produced the current clip: the app that
     * was in the foreground in the moments before the clipboard changed, per
     * [UsageStatsManager]. Returns null when the Usage Access permission isn't
     * granted or no recent foreground app can be found — the copy still lands in
     * history, just without a source. Own package is treated as "no source".
     */
    private fun resolveClipSource(): String? {
        if (!hasUsageAccess()) return null
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        val pkg = runCatching {
            val events = usage.queryEvents(now - 10_000L, now)
            val event = android.app.usage.UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == fgType) {
                    last = event.packageName
                }
            }
            last
        }.getOrNull()
        if (pkg == null || pkg == packageName) return null
        return runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    /** Whether the user granted the Usage Access special permission. */
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Records copied files and folders as clips. Only the URI, display name
     * and size are stored — the bytes stay with the app that owns them, so a
     * copied 4 GB video costs us nothing.
     *
     * Clipboard URI grants are short-lived, so we try to persist them; most
     * providers refuse, in which case inserting later falls back to putting
     * the URI back on the system clipboard.
     */
    private fun addFileClips(uris: List<Uri>, sourceApp: String? = null) {
        var added = false
        for (uri in uris.take(MAX_FILE_CLIPS_PER_COPY)) {
            val info = resolveClipFile(uri) ?: continue
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            clipboardStore.addUri(
                uriString = uri.toString(),
                displayName = info.name,
                mimeType = info.mimeType,
                isDirectory = info.isDirectory,
                size = info.size,
                sourceApp = sourceApp,
            )
            added = true
        }
        if (!added) return
        clipboardStore.save()
        _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
    }

    private data class ClipFileInfo(
        val name: String,
        val mimeType: String,
        val size: Long,
        val isDirectory: Boolean,
    )

    /** Display name, size and directory-ness of a copied file URI. */
    private fun resolveClipFile(uri: Uri): ClipFileInfo? {
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File) ?: return null
            return ClipFileInfo(
                name = file.name.ifBlank { uri.toString() },
                mimeType = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
                else contentResolver.getType(uri) ?: "application/octet-stream",
                size = if (file.isDirectory) -1 else file.length(),
                isDirectory = file.isDirectory,
            )
        }
        if (uri.scheme != "content") return null
        // A tree URI describes a folder but can't be queried directly; its
        // document URI can.
        val queryUri = runCatching {
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.buildDocumentUriUsingTree(
                    uri, DocumentsContract.getTreeDocumentId(uri),
                )
            } else {
                uri
            }
        }.getOrDefault(uri)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        var name: String? = null
        var size = -1L
        var mime: String? = null
        runCatching {
            contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                fun column(key: String) = cursor.getColumnIndex(key).takeIf { it >= 0 }
                column(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    ?.let { if (!cursor.isNull(it)) name = cursor.getString(it) }
                column(DocumentsContract.Document.COLUMN_SIZE)
                    ?.let { if (!cursor.isNull(it)) size = cursor.getLong(it) }
                column(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    ?.let { if (!cursor.isNull(it)) mime = cursor.getString(it) }
            }
        }
        val resolvedMime = mime
            ?: runCatching { contentResolver.getType(uri) }.getOrNull()
            ?: return null
        val isDirectory = resolvedMime == DocumentsContract.Document.MIME_TYPE_DIR ||
            runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false)
        return ClipFileInfo(
            name = name?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "Unnamed",
            mimeType = resolvedMime,
            size = if (isDirectory) -1 else size,
            isDirectory = isDirectory,
        )
    }

    /**
     * Fills in Open Graph metadata for copied links, newest first, when the
     * user has link previews on. Runs one link at a time so a panel full of
     * links doesn't fire a dozen simultaneous requests.
     */
    private fun fetchLinkPreviews() {
        if (!_uiState.value.settings.clipboard.linkPreviews) return
        if (linkPreviewJob?.isActive == true) return
        val pending = clipboardStore.linksNeedingPreview().take(MAX_LINK_PREVIEWS)
        if (pending.isEmpty()) return
        linkPreviewJob = serviceScope.launch(Dispatchers.IO) {
            for (clip in pending) {
                val url = ClipLinks.asUrl(clip.text) ?: continue
                val preview = LinkPreviewClient.fetch(url)
                clipboardStore.setLinkPreview(clip.id, preview)
                _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
            }
            clipboardStore.save()
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = KeyboardViewLifecycleOwner()
        lifecycleOwner.onCreate()

        settingsRepository = SettingsRepository(this)
        // Decode the synthesized key sounds up front so the first press plays.
        KeySoundPlayer.warmUp(this)
        userLexicon = UserLexicon(File(filesDir, "learning/user_lexicon.json"))
        emojiUsage = EmojiUsage(File(filesDir, "learning/emoji_usage.json"))
        clipboardStore = ClipboardStore(
            File(filesDir, "clipboard/history.json"),
            imagesDir = File(filesDir, "clipboard/images"),
        )
        snippetStore = SnippetStore(File(filesDir, "snippets/snippets.json"))

        // Tops up an upgraded install's stored mode list with modes added
        // since it was first seeded. No-op once it has run.
        serviceScope.launch { settingsRepository.seedNewDefaultModes() }

        serviceScope.launch {
            var lexiconVersion = -1
            var customDictVersion = -1
            var contactsEnabled: Boolean? = null
            var contactEmailsEnabled: Boolean? = null
            var appNamesEnabled: Boolean? = null
            var linkPreviewsEnabled: Boolean? = null
            var pinnedLastEnabled: Boolean? = null
            // Recompute the hidden-emoji set only when the toggle or the font
            // behind it actually changes, not on every unrelated settings save.
            var hiddenEmojiKey: Pair<Boolean, EmojiFontChoice>? = null
            settingsRepository.settings.collect { settings ->
                val nextHiddenKey = settings.emoji.hideUnrenderable to settings.emojiFont
                if (hiddenEmojiKey != nextHiddenKey) {
                    hiddenEmojiKey = nextHiddenKey
                    recomputeHiddenEmoji(settings)
                }
                clipboardStore.expiryMillis = settings.clipboard.expiryHours * 60L * 60 * 1000
                // Flipping pinned-first/last re-sorts the store, so refresh the
                // panel's snapshot when the choice actually changes.
                if (pinnedLastEnabled != settings.clipboard.pinnedLast) {
                    clipboardStore.pinnedLast = settings.clipboard.pinnedLast
                    pinnedLastEnabled = settings.clipboard.pinnedLast
                    _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
                }
                // Turning the strip chip off hides any chip already showing.
                if (!settings.clipboard.suggestRecent) clearClipboardSuggestion()
                // Turning previews off throws away what was already fetched, so
                // the panel stops showing metadata the user opted out of.
                if (linkPreviewsEnabled == true && !settings.clipboard.linkPreviews) {
                    linkPreviewJob?.cancel()
                    clipboardStore.clearLinkPreviews()
                    clipboardStore.save()
                    _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
                }
                linkPreviewsEnabled = settings.clipboard.linkPreviews
                if (!settings.floatingKeyboard) floatingPanelBounds = null
                if (settings.contactSuggestions != contactsEnabled) {
                    contactsEnabled = settings.contactSuggestions
                    if (settings.contactSuggestions) {
                        loadContactNames()
                    } else {
                        contactNames = ContactNames.EMPTY
                        suggestionEngine?.contacts = ContactNames.EMPTY
                    }
                }
                if (settings.contactEmailSuggestions != contactEmailsEnabled) {
                    contactEmailsEnabled = settings.contactEmailSuggestions
                    if (settings.contactEmailSuggestions) {
                        loadContactEmails()
                    } else {
                        contactEmails = ContactEmails.EMPTY
                        suggestionEngine?.contactEmails = ContactEmails.EMPTY
                    }
                }
                if (settings.appNameSuggestions != appNamesEnabled) {
                    appNamesEnabled = settings.appNameSuggestions
                    if (settings.appNameSuggestions) {
                        loadAppNames()
                    } else {
                        appNames = AppNames.EMPTY
                        suggestionEngine?.apps = AppNames.EMPTY
                    }
                }
                // The settings app edited the learned-words file (personal
                // dictionary): drop the in-memory copy for the disk state,
                // otherwise the next save here would clobber those edits.
                if (lexiconVersion != -1 && settings.lexiconVersion != lexiconVersion) {
                    withContext(Dispatchers.Default) { userLexicon.reload() }
                    invalidateGestureLexicon()
                }
                lexiconVersion = settings.lexiconVersion
                baseSettings = settings
                val mode = resolveKeyboardMode(
                    settings.keyboardModes, currentPackage, currentModeFields, manualModeId,
                )
                // A field-scoped override (FORCE_ASCII, hintLocales) outlives
                // settings emissions — otherwise saving any unrelated setting
                // would drop the field back to a layout it cannot accept.
                val activeSpec = activeLayoutSpec(settings)
                _uiState.update {
                    it.copy(
                        settings = settings.applyMode(mode),
                        language = activeSpec.language(),
                        script = activeSpec.script(),
                        composer = composerFor(activeSpec.script(), activeSpec.composerType()),
                        layoutId = activeSpec.id,
                        layoutName = activeSpec.name,
                        layouts = resolveLayoutSet(activeSpec, it.fieldKind),
                        activeModeId = mode?.id,
                    )
                }
                // Keep the OS switcher's subtype list in step with the enabled
                // layouts. Diffed inside, so unrelated settings emissions here
                // don't thrash the framework.
                registerSubtypes(settings)
                // Switching the swipe action to handwriting (or turning the
                // gesture on) while the keyboard is up checks the model now, so
                // the first swipe writes rather than nagging.
                val nowArmed = keyboardHandwriteActive(_uiState.value)
                if (nowArmed && !hwKeyboardArmed) refreshHandwritingStatus()
                hwKeyboardArmed = nowArmed
                // Everything below keys off the mode actually being typed, so
                // a field-forced mode gets its own proximity grid and word
                // lists rather than the saved mode's.
                val activeLang = activeSpec.language()
                // Typo weighting follows the grid actually on screen, so a
                // rearranged custom layout weights its own neighbours.
                suggestionEngine?.proximity = KeyProximity.forLayout(activeSpec)
                suggestionEngine?.autocorrectConfidence =
                    settings.autocorrectConfidence.toDouble()
                suggestionEngine?.blacklist = settings.suggestionBlacklist
                suggestionEngine?.skipAllCapsAutocorrect = settings.autocorrectSkipAllCaps
                // Only English drives the bundled English word list; every other
                // language (with no bundled dictionary) drops it so autocorrect
                // and completions never offer English for their words. Bengali
                // routes through its own transliteration path either way.
                suggestionEngine?.englishSources = activeLang.isEnglish
                // Imported word lists are per language, so the active one
                // follows the mode: a French list never reaches English.
                if (customDictVersion != -1 && settings.customDictVersion != customDictVersion) {
                    withContext(Dispatchers.Default) {
                        customDictionaries = loadCustomDictionaries()
                        suggestionEngine?.bengaliIndex = buildBengaliIndex()
                    }
                }
                customDictVersion = settings.customDictVersion
                suggestionEngine?.customDictionary =
                    customDictionaries[activeLang.id] ?: PackedTrie.EMPTY
                // Secondary languages feed the strip alongside the primary. English
                // rides its bundled list (englishAsSecondary); every other language
                // its imported list.
                val secondaryIds = settings.secondaryLanguages[activeLang.id].orEmpty()
                suggestionEngine?.secondaryDictionaries =
                    secondaryIds.filter { it != "en" }.mapNotNull { customDictionaries[it] }
                suggestionEngine?.englishAsSecondary =
                    "en" in secondaryIds && !activeLang.isEnglish
            }
        }

        // Dictionaries and the emoji catalog load off the main thread; the
        // keyboard is usable immediately and suggestions appear when ready. The
        // JSON asset layouts load alongside them (idempotent) so a language
        // whose grid is a file resolves once the user switches to it.
        serviceScope.launch {
            val loaded = withContext(Dispatchers.Default) {
                AssetLayouts.load(assets)
                val englishEntries = assets.open("dictionaries/en.txt").use { DictionaryLoader.loadEntries(it) }
                val bengaliEntries = assets.open("dictionaries/bn.txt").use { DictionaryLoader.loadEntries(it) }
                val catalog = assets.open("emoji/catalog.tsv").use { EmojiCatalog.load(it) }
                Triple(englishEntries, bengaliEntries, catalog)
            }
            val (englishEntries, bengaliEntries, catalog) = loaded
            val (loanwords, variants, seedBigrams) = withContext(Dispatchers.Default) {
                val lw = assets.open("dictionaries/en_bn.tsv").use { EnglishBengaliMap.load(it) }
                val v = runCatching {
                    assets.open("emoji/variants.tsv").use { EmojiVariantIndex.load(it) }
                }.getOrDefault(EmojiVariantIndex.empty())
                val seeds = runCatching {
                    assets.open("dictionaries/en_bigrams.txt").use { SeedBigrams.load(it) }
                }.getOrDefault(SeedBigrams.EMPTY)
                Triple(lw, v, seeds)
            }
            val english = PackedTrie.of(englishEntries)
            gestureLexicon = englishEntries
            invalidateGestureLexicon()
            bengaliAssetEntries = bengaliEntries
            val customTries = withContext(Dispatchers.Default) { loadCustomDictionaries() }
            customDictionaries = customTries
            suggestionEngine = SuggestionEngine(
                english,
                buildBengaliIndex(),
                userLexicon,
                loanwords,
                seedBigrams,
            ).apply {
                contacts = contactNames
                contactEmails = this@WMKeyboardService.contactEmails
                apps = appNames
                proximity = KeyProximity.forLayout(activeLayoutSpec(_uiState.value.settings))
                autocorrectConfidence =
                    _uiState.value.settings.autocorrectConfidence.toDouble()
                blacklist = _uiState.value.settings.suggestionBlacklist
                skipAllCapsAutocorrect = _uiState.value.settings.autocorrectSkipAllCaps
                val lang = _uiState.value.language
                englishSources = lang.isEnglish
                customDictionary = customTries[lang.id] ?: PackedTrie.EMPTY
                val secondaryIds = _uiState.value.settings.secondaryLanguages[lang.id].orEmpty()
                secondaryDictionaries = secondaryIds.filter { it != "en" }.mapNotNull { customTries[it] }
                englishAsSecondary = "en" in secondaryIds && !lang.isEnglish
            }
            emojiEntries = catalog
            emojiSearch = EmojiSearch(catalog)
            emojiSuggester = EmojiSuggester(catalog)
            _uiState.update {
                it.copy(
                    emojiRecents = emojiUsage.recents(),
                    emojiFrequents = emojiUsage.frequents(),
                    emojiFavourites = emojiUsage.favourites(),
                    emojiVariantPrefs = emojiUsage.variantPrefs(),
                    emojiCatalog = catalog,
                    emojiVariants = variants,
                )
            }
            // The catalog is what the hidden-emoji check runs over; now that it
            // is loaded, populate the set if the feature is already on.
            recomputeHiddenEmoji(_uiState.value.settings)
        }

        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .addPrimaryClipChangedListener(clipboardListener)

        serviceScope.launch {
            uiState
                .map { it.panel != PanelMode.NONE || it.voice.strip }
                .distinctUntilChanged()
                .collect { updatePanelBackCallback(it) }
        }

        // Mirror the torch state so the flashlight tool lights up even when
        // the torch is toggled from outside the keyboard.
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        torchCameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        if (torchCameraId != null) {
            cameraManager.registerTorchCallback(torchCallback, null)
        }
    }

    /**
     * The root input view, kept so [doVibrate] can route the SYSTEM_* haptic
     * styles through `View.performHapticFeedback` (the platform's tuned key
     * click). Cleared when the input view goes away.
     */
    private var inputRootView: View? = null

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        inputRootView = view
        lifecycleOwner.attachTo(window.window!!.decorView)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            KeyboardScreen(
                stateFlow = uiState,
                onKey = ::onKey,
                onKeyPressed = ::vibrate,
                onHaptic = ::vibrateOnly,
                onKeySound = { playKeySound() },
                onText = ::onText,
                onGesture = ::onGesture,
                onGesturePreview = ::onGesturePreview,
                onGestureWords = ::onGestureWords,
                onCursorMove = ::onCursorMove,
                onCursorMoveVertical = ::onCursorMoveVertical,
                onLayoutSelect = ::onLayoutSelected,
                onClipboardKey = ::onClipboardKey,
                canDelete = ::canDelete,
                canDeleteField = ::canDeleteField,
                onDeleteWord = ::onDeleteWord,
                onSuggestion = ::onSuggestionTapped,
                onEmoji = ::onEmojiTapped,
                onEmojiVariant = ::onEmojiVariantPicked,
                onEmojiFavourite = ::onEmojiFavouriteToggled,
                onEmojiSuggestion = ::onEmojiSuggestionTapped,
                onPunctuation = ::onPunctuationSuggestionTapped,
                onEmojiQueryTap = ::onEmojiSearchToggled,
                onEmojiRecentsClear = ::onEmojiRecentsClear,
                onEmojiRecentRemove = ::onEmojiRecentRemoved,
                onEmojiFavouritesReorder = ::onEmojiFavouritesReordered,
                onEmojiSearchFieldDelete = ::onEmojiSearchFieldDelete,
                onTextEdit = ::onTextEdit,
                onPanelChange = ::onPanelChange,
                onClipboardItem = ::onClipboardItemTapped,
                onClipboardPin = ::onClipboardPin,
                onClipboardDelete = ::onClipboardDelete,
                onClipboardSuggestionDismiss = ::onClipboardSuggestionDismiss,
                onSnippet = ::onSnippetTapped,
                onOneHanded = ::onOneHandedChange,
                onOneHandedSide = ::onOneHandedSideChange,
                onFloatingChange = ::onFloatingChange,
                onFloatingMoved = ::onFloatingMoved,
                onFloatingResized = ::onFloatingResized,
                onFloatingBounds = ::onFloatingBounds,
                onToggleSplit = ::onToggleSplit,
                onToolbarToolsChange = ::onToolbarToolsChange,
                onToolboxOrderChange = ::onToolboxOrderChange,
                onToolSettings = ::openToolSettings,
                onToolboxHintDismiss = {
                    serviceScope.launch { settingsRepository.setToolboxHintDismissed(true) }
                },
                onFlashlightToggle = ::onFlashlightToggle,
                onUndoRedo = ::onUndoRedo,
                onWeatherRefresh = { refreshWeather(force = true) },
                onCameraSend = ::onCameraSend,
                onCameraPermissionRequest = ::onCameraPermissionRequest,
                onCalendarPermissionRequest = ::onCalendarPermissionRequest,
                onScannedInsert = ::onScannedTextInsert,
                onScannedUrlOpen = ::onScannedUrlOpen,
                onDocScan = ::onDocScanStart,
                onVoiceToggle = ::onVoiceToggle,
                onVoicePermissionRequest = ::onVoicePermissionRequest,
                onVoiceUndo = ::onVoiceUndo,
                onVoiceModelDownload = ::onVoiceModelDownload,
                onDictionaryLookup = ::onDictionaryLookup,
                onDictionarySearchToggle = ::onDictionarySearchToggle,
                onDictionaryInsert = ::onDictionaryInsert,
                onIncognitoToggle = ::onIncognitoToggle,
                onAutocorrectToggle = ::onAutocorrectToggle,
                onThemeSelect = ::onThemeSelect,
                onSoundHaptic = ::onSoundHaptic,
                onHandwritingStroke = ::onHandwritingStroke,
                onKeyboardHandwritingStroke = ::onKeyboardHandwritingStroke,
                onHandwritingUndo = ::onHandwritingUndo,
                onHandwritingDownload = ::onHandwritingDownload,
                onMediaQueryTap = ::onMediaQueryTap,
                onMediaRetry = ::onMediaRetry,
                onGifSelect = ::onGifSelect,
                onGifSourceSelect = ::onGifSourceSelect,
                onWebResult = ::onWebResultSelect,
                onWebResultOpen = ::onWebResultOpen,
                onImageResult = ::onImageResultSelect,
                onImageResultLink = ::onImageResultLink,
                onTranslateTarget = ::onTranslateTargetChange,
                onTranslateReplace = ::onTranslateReplace,
                onTranslateInsert = ::onTranslateInsert,
                onGrammarFix = ::onGrammarFix,
                onGrammarFixAll = ::onGrammarFixAll,
                onGrammarDismiss = ::onGrammarDismiss,
                onGrammarDialect = ::onGrammarDialectChange,
                onGrammarFocus = ::onGrammarFocus,
                onWikiOpen = ::onWikiOpen,
                onWikiBack = ::onWikiBack,
                onWikiLoadLinks = ::onWikiLoadLinks,
                onWikiLoadFull = ::onWikiLoadFull,
                onSymbolInsert = ::onSymbolInsert,
                onSymbolSetSelect = ::onSymbolSetSelect,
                onModeSelect = ::onModeSelect,
                onToolInsert = ::onToolTextInsert,
                // Selection memory, not a user action — persist silently.
                onUnitSelection = { selection ->
                    serviceScope.launch { settingsRepository.setUnitConvertLast(selection) }
                },
                onCurrencyPairChange = ::onCurrencyPairChange,
                onCurrencyRefresh = { refreshCurrencyRates(force = true) },
                onPwSetting = ::onPwSetting,
                onTypingTestAction = ::onTypingTestAction,
                onQrSend = ::onQrSend,
                onAiAction = ::onAiAction,
                onAiReplace = ::onAiReplace,
                onAiInsert = ::onAiInsert,
                onAiRetry = ::onAiRetry,
                onAiRunCustom = ::onAiRunCustom,
                onAiPickModel = ::onAiPickModel,
                onAiToggleStripMarkdown = ::onAiToggleStripMarkdown,
                onOpenToolSettings = ::openToolSettings,
                onDismissInlineSuggestions = ::onDismissInlineSuggestions,
                onSmartAccept = ::onSmartSuggestionTapped,
                onSmartOpen = ::onSmartSuggestionOpen,
                onToolPrefillConsumed = ::onToolPrefillConsumed,
                onHideKeyboard = ::onHideKeyboard,
                onOpenSettings = ::openSettings,
            )
        }
        return view
    }

    // ---- floating mode ----

    /** Panel bounds in IME-window coordinates, for the touchable region. */
    private var floatingPanelBounds: android.graphics.Rect? = null

    fun onFloatingBounds(bounds: IntRect) {
        val rect = android.graphics.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (rect != floatingPanelBounds) {
            floatingPanelBounds = rect
            // Insets are only re-queried on a window layout pass; the panel
            // can move without the view tree changing size, so force one.
            window?.window?.decorView?.requestLayout()
        }
    }

    fun onFloatingChange(enabled: Boolean) {
        vibrate()
        if (!enabled) floatingPanelBounds = null
        serviceScope.launch { settingsRepository.setFloatingKeyboard(enabled) }
    }

    fun onFloatingMoved(xFraction: Float, yFraction: Float) {
        serviceScope.launch { settingsRepository.setFloatingPosition(xFraction, yFraction) }
    }

    fun onFloatingResized(widthDp: Int, heightScale: Float) {
        serviceScope.launch { settingsRepository.setFloatingSize(widthDp, heightScale) }
    }

    /**
     * Floating mode: the compose root covers the whole IME window, but the
     * app behind must neither resize nor lose touches. Content insets say
     * "the keyboard occupies nothing"; the touchable region shrinks to the
     * floating panel so all other touches pass through.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!_uiState.value.settings.floatingKeyboard) return
        val decorHeight = window?.window?.decorView?.height ?: return
        outInsets.contentTopInsets = decorHeight
        outInsets.visibleTopInsets = decorHeight
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.setEmpty()
        floatingPanelBounds?.let { outInsets.touchableRegion.set(it) }
    }

    /** Never use the fullscreen (extract) editor while floating. */
    override fun onEvaluateFullscreenMode(): Boolean =
        if (_uiState.value.settings.floatingKeyboard) false else super.onEvaluateFullscreenMode()

    /** A physical keyboard is attached and not folded away. */
    private fun hasHardwareKeyboard(): Boolean {
        val config = resources.configuration
        return config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    }

    /**
     * Keep the input view on screen even with a hardware keyboard when the
     * user wants the toolbar-only view — otherwise the platform hides it, and
     * the toolbar (with the keys gated off in Compose) would never show.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        val toolbar = _uiState.value.settings.toolbarBehavior
        // Nothing to force-show when the toolbar itself is off — that would be a
        // blank sliver (no toolbar, and the keys are gated off too).
        if (toolbar.enabled && toolbar.onlyWithHardwareKeyboard && hasHardwareKeyboard()) {
            return true
        }
        return super.onEvaluateInputViewShown()
    }

    /** Docking or undocking a hardware keyboard flips the toolbar-only view. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshHardwareKeyboardState()
    }

    /** Push the current hardware-keyboard presence into the UI state. */
    private fun refreshHardwareKeyboardState() {
        val present = hasHardwareKeyboard()
        if (present != _uiState.value.hardwareKeyboardPresent) {
            _uiState.update { it.copy(hardwareKeyboardPresent = present) }
        }
    }

    /**
     * True while the device lock screen (keyguard) is showing — secure or
     * swipe-only. Read on each field start to drive the "hide toolbar &
     * clipboard on lock screen" privacy setting.
     */
    private fun isDeviceLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onResume()
        refreshHardwareKeyboardState()
        composing = StringBuilder()
        previousWord = null
        lastGestureWord = null
        lastAutocorrect = null
        smartMutedAfter = null
        // Covers the permission being granted after the setting was on.
        if (_uiState.value.settings.contactSuggestions && contactNames.isEmpty) {
            loadContactNames()
        }
        if (_uiState.value.settings.contactEmailSuggestions && contactEmails.isEmpty) {
            loadContactEmails()
        }
        hwJob?.cancel()
        hwGeneration++
        val secure = info.isSecureField()
        val fieldKind = info.fieldKind()
        // Keyboard-mode resolution: a manual pick from the Modes tool lives
        // as long as the user stays in the same app.
        val pkg = info?.packageName
        if (pkg != null && pkg != currentPackage) manualModeId = null
        if (pkg != null) currentPackage = pkg
        currentModeFields = buildSet {
            if (secure) add(ModeField.PASSWORD)
            when (fieldKind) {
                FieldKind.EMAIL -> add(ModeField.EMAIL)
                FieldKind.URI -> add(ModeField.URL)
                FieldKind.NUMBER -> add(ModeField.NUMBER)
                FieldKind.PHONE -> add(ModeField.PHONE)
                // Plain prose. A password box reports TEXT too, so it is
                // excluded — modes bound to TEXT (chat composers, document
                // bodies) must never take over a login form.
                FieldKind.TEXT -> if (!secure) add(ModeField.TEXT)
                else -> {}
            }
        }
        val base = baseSettings
        // Read the field-detection settings from the base so a per-app mode
        // can't quietly switch either of them off.
        val fieldSettings = base ?: _uiState.value.settings
        // Incognito the field asked for, e.g. a Chrome incognito tab.
        val fieldIncognito = fieldSettings.autoIncognito &&
            info.requestsNoPersonalizedLearning()
        val fieldNoSuggestions =
            info.suppressesSuggestions(fieldSettings.showSuggestionsInAllFields)
        val activeMode = base?.let {
            resolveKeyboardMode(it.keyboardModes, currentPackage, currentModeFields, manualModeId)
        }
        // Language the field asks for, layered over the base for this app (the
        // per-app remembered layout when that is on, else the global pick).
        // FORCE_ASCII is a hard constraint (the app cannot store what a Bengali
        // mode types) so it outranks a hintLocales preference, which is only ever
        // advisory. Both are compared against — and fall back to — that base.
        val current = base ?: _uiState.value.settings
        val baseSpec = resolveLayout(current.customLayouts, baseLayoutId(current))
        fieldLayoutOverride = when {
            info.forcesAscii() && baseSpec.script().id != ScriptId.LATIN ->
                current.enabledLayoutIds.firstOrNull {
                    resolveLayout(current.customLayouts, it).script().id == ScriptId.LATIN
                } ?: BuiltInLayouts.DEFAULT_ID
            // hintLocales names a language, not a layout, so it picks the first
            // enabled layout that types that language.
            else -> info.hintedLanguage(current.enabledLanguages)
                ?.takeIf { it.id != baseSpec.language().id }
                ?.let { hinted ->
                    current.enabledLayoutIds.firstOrNull {
                        resolveLayout(current.customLayouts, it).language().id == hinted.id
                    }
                }
        }
        val fieldSpec = activeLayoutSpec(current)
        _uiState.update {
            it.copy(
                settings = base?.applyMode(activeMode) ?: it.settings,
                language = fieldSpec.language(),
                script = fieldSpec.script(),
                composer = composerFor(fieldSpec.script(), fieldSpec.composerType()),
                // A locked Ctrl crossing an app boundary is the worst failure
                // this feature can have: every letter after it becomes a
                // shortcut in an app the user never armed it for.
                modifiers = Modifiers.None,
                layoutId = fieldSpec.id,
                layoutName = fieldSpec.name,
                layouts = resolveLayoutSet(fieldSpec, fieldKind),
                activeModeId = activeMode?.id,
                activeSymbolSetId = null,
                panel = PanelMode.NONE,
                // A fresh field starts on the letter layer; a restart of the
                // same field keeps whatever layer the user was on.
                layoutMode = if (restarting) it.layoutMode else LayoutMode.LETTERS,
                fieldKind = fieldKind,
                fieldNoSuggestions = fieldNoSuggestions,
                fieldIncognito = fieldIncognito,
                emojiSearchActive = false,
                emojiQuery = "",
                dictionarySearchActive = false,
                mediaSearchActive = false,
                mediaQuery = "",
                mediaDownloadingId = null,
                // A run belongs to the field it was started over; moving to
                // another one abandons it rather than resuming half-typed.
                typingTest = TypingTestUi(),
                translate = TranslateUi(),
                grammar = GrammarUi(available = GrammarChecker.available),
                composingPreview = "",
                suggestions = emptyList(),
                emojiSuggestions = emptyList(),
                // A tool-keyword chip ("wiki") belongs to the field it was
                // typed in; a fresh (often empty) field must not inherit it.
                // onUpdateSelection re-derives it once the new field settles,
                // but that can lag the switch, leaving a stale chip up.
                smart = null,
                secureField = secure,
                deviceLocked = isDeviceLocked(),
                shiftState = autoCapitalizeShift(),
                clipboardItems = clipboardStore.items(),
                enterAction = info.enterAction(),
                enterActionLabel = info?.actionLabel?.toString()?.takeIf { it.isNotBlank() },
                handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false),
                voice = it.voice.copy(
                    status = VoiceStatus.IDLE, partial = "", level = 0f,
                    strip = false, canUndo = false,
                ),
            )
        }
        refreshKarContext()
        // Pages from a just-finished document scan: the scanner activity
        // ran while the keyboard was down, so they can only insert now,
        // as the target field regains the input connection.
        for (page in DocScanActivity.consumePendingPages()) {
            saveToGalleryIfEnabled(
                page,
                MediaMime.JPEG,
                _uiState.value.settings.docScanSaveToGallery,
                "SCAN",
            )
            commitImageFile(page, MediaMime.JPEG)
        }
        // Fresh field: re-arm the on-keyboard writing hint and check the model
        // up front so the first swipe writes rather than nagging.
        hwModelHintShown = false
        hwKeyboardArmed = keyboardHandwriteActive(_uiState.value)
        if (hwKeyboardArmed) refreshHandwritingStatus()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor moved away from the composing region (tap elsewhere,
        // selection handle drag): stop composing so edits apply at the new
        // position instead of the stale word.
        if (composing.isNotEmpty() &&
            (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            composing = StringBuilder()
            currentInputConnection?.finishComposingText()
            suggestionJob?.cancel()
            _uiState.update { it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList()) }
        }
        // Partial dictation results are cumulative per utterance, so they
        // can't follow a cursor jump without duplicating what's already
        // committed — end the session instead, keeping the partial.
        val voiceStatus = _uiState.value.voice.status
        if ((voiceStatus == VoiceStatus.LISTENING || voiceStatus == VoiceStatus.FINISHING) &&
            _uiState.value.voice.partial.isNotEmpty() &&
            (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            cancelVoice()
        }
        refreshShiftForContext()
        refreshKarContext()
        // Tool chips and the word strip both read the text around the cursor, so
        // they have to be re-derived after a cursor jump or an edit made from
        // outside the keyboard, not only after a keystroke. A settled plain
        // caret re-reads the whole context — the word it landed on and the strip
        // — via restartSuggestionsAtCursor (which folds in the chip refresh). An
        // active word still being composed in place, or a range selection being
        // dragged out, only refreshes the chips.
        if (composing.isEmpty() && newSelStart == newSelEnd) {
            currentInputConnection?.let { restartSuggestionsAtCursor(it, newSelStart) }
        } else {
            refreshSmartSuggestion()
        }
        // The grammar strip follows the field: any text or cursor change
        // while it is open re-extracts and re-lints (offline, so cheap).
        // Translate deliberately does NOT — it translates its own typed
        // query, never the field.
        if (_uiState.value.panel == PanelMode.GRAMMAR) scheduleGrammarCheck()
    }

    /**
     * Tells the system autofill service how much room the strip has, which
     * is what makes password-manager chips appear there at all.
     *
     * Declining (returning null) is the documented way to opt out, and the
     * cases that decline are the ones where showing saved credentials would
     * be wrong: the feature switched off, or an incognito session, where the
     * user has asked for this typing not to be remembered or surfaced.
     */
    /**
     * Same question as [KeyboardUiState.incognitoOn], asked before the state
     * exists: the platform builds the autofill request during onStartInput,
     * ahead of onStartInputView, so the field flag has to come straight off
     * [currentInputEditorInfo] rather than the cached UI state.
     */
    private fun autofillBlockedByIncognito(): Boolean {
        val settings = _uiState.value.settings
        return settings.incognito ||
            (settings.autoIncognito && currentInputEditorInfo.requestsNoPersonalizedLearning())
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!InlineAutofill.supported) return null
        val settings = _uiState.value.settings
        if (!settings.inlineAutofill || autofillBlockedByIncognito()) return null
        val density = resources.displayMetrics
        val stripHeightPx = (INLINE_CHIP_HEIGHT_DP * density.density).toInt()
        return runCatching {
            InlineAutofill.request(
                context = this,
                uiExtras = uiExtras,
                stripHeightPx = stripHeightPx,
                maxWidthPx = density.widthPixels,
            )
        }.getOrNull()
    }

    /**
     * The manager's answer. Returning true claims the suggestions so the
     * platform does not fall back to its own dropdown over the keyboard.
     */
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!InlineAutofill.supported) return false
        val settings = _uiState.value.settings
        if (!settings.inlineAutofill || autofillBlockedByIncognito()) return false
        val density = resources.displayMetrics
        InlineAutofill.inflateAll(
            context = this,
            suggestions = response.inlineSuggestions,
            stripHeightPx = (INLINE_CHIP_HEIGHT_DP * density.density).toInt(),
            maxWidthPx = density.widthPixels,
        ) { views ->
            _uiState.update { it.copy(inlineSuggestions = views) }
        }
        return true
    }

    /** Dismiss chip on the strip: drop them until the next autofill response. */
    fun onDismissInlineSuggestions() {
        vibrate()
        _uiState.update { it.copy(inlineSuggestions = emptyList()) }
    }

    /** The hide-keyboard tool and the toolbar swipe-down: close the keyboard. */
    fun onHideKeyboard() {
        requestHideSelf(0)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleOwner.onPause()
        // Latches die with the keyboard, locked ones included.
        clearModifiers()
        // Credentials for the field just left must not linger over the next
        // one, which may belong to another app entirely.
        if (_uiState.value.inlineSuggestions.isNotEmpty()) {
            _uiState.update { it.copy(inlineSuggestions = emptyList()) }
        }
        // The keyboard is going away mid-dictation: release the mic (the
        // privacy indicator must never outlive the keyboard) and keep the
        // partial that was already on screen.
        cancelVoice()
        // The language the field asked for dies with the field; leaving it
        // set would apply an ASCII lock or a locale hint to whatever the
        // user types in next.
        fieldLayoutOverride = null
        userLexicon.save()
        emojiUsage.save()
        if (_uiState.value.settings.flashlightAutoOff && _uiState.value.torchOn) {
            setTorch(false)
        }
    }

    override fun onDestroy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .removePrimaryClipChangedListener(clipboardListener)
        if (torchCameraId != null) {
            (getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                .unregisterTorchCallback(torchCallback)
        }
        userLexicon.save()
        emojiUsage.save()
        clipboardStore.save()
        voiceEngine.cancel()
        hwRecognizer.close()
        LocalLlmEngine.release()
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // A cached local model pins hundreds of MB to a few GB — free it the
        // moment the system signals pressure; the next AI action reloads it.
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) LocalLlmEngine.release()
    }

    // ---- key handling ----

    // No vibrate() here: press-time haptics fire from the UI's pointer-down
    // callback (onKeyPressed) so feedback lands on touch, not on release.
    fun onKey(key: Key) {
        stopVoiceForManualInput()
        // Typing real content dismisses the recent-copy strip chip (Gboard
        // style): once the user is writing, the quick-paste offer has passed —
        // and clearing it here keeps it from flashing between committed words.
        if (key.action == KeyAction.Text || key.action == KeyAction.Space) {
            clearClipboardSuggestion()
        }
        // Shift keeps the gesture word so alternates can be re-cased;
        // Delete keeps it so one backspace can undo the whole swipe.
        if (key.action != KeyAction.Shift && key.action != KeyAction.Delete) {
            lastGestureWord = null
        }
        // The auto-space cancel is a one-shot for the shift press immediately
        // after the ". " — any other key means the user typed on past it.
        if (key.action != KeyAction.Shift) pendingAutoSpace = false
        // A pending Ctrl/Alt/Meta turns the next key into a shortcut, so it is
        // intercepted ahead of the normal dispatch: KeyAction.Text would
        // otherwise push the letter through the composing buffer and Ctrl+C
        // would type a "c". Modifier keys fall through so Ctrl and Alt can be
        // latched together, and so does Shift, which composes rather than fires.
        val modifiers = _uiState.value.modifiers
        val isShortcut = !modifiers.isEmpty &&
            key.action !is KeyAction.Mod &&
            key.action != KeyAction.Shift
        if (isShortcut) {
            // The result is deliberately ignored: a character with no keycode
            // has no event to send, and the latch is spent either way so the
            // user can see the modifier was used up rather than left armed.
            sendShortcut(key, modifiers)
            consumeModifiers()
            consumeShift()
            return
        }
        when (key.action) {
            KeyAction.Text -> onTextKey(key)
            KeyAction.Shift -> onShift()
            KeyAction.Delete -> onDelete()
            KeyAction.Space -> onSpace()
            KeyAction.Enter -> onEnter()
            KeyAction.Symbols -> toggleSymbols()
            KeyAction.Letters -> _uiState.update {
                it.copy(layoutMode = LayoutMode.LETTERS, fnLocked = false, fnReturn = null)
            }
            KeyAction.LanguageSwitch -> switchLanguage()
            KeyAction.Emoji -> onPanelChange(PanelMode.EMOJI)
            // Produced only by a long-press on ?123 when the opt-in is set.
            KeyAction.Numpad -> onPanelChange(PanelMode.NUMPAD)
            is KeyAction.Mod -> onModifier((key.action as KeyAction.Mod).key)
            KeyAction.Fn -> onFn()
            // A key carrying its own modifiers, so it fires with no latch.
            is KeyAction.SendKey -> sendShortcut(key, Modifiers.None)
            // A deliberate gap in the grid, and a key from a build that knows an
            // action this one does not. Both swallow the tap: a custom layout is
            // repaired before it can be enabled, so neither should reach a
            // keyboard the user is typing on.
            KeyAction.None, is KeyAction.Unknown -> Unit
        }
        // A one-shot Fn springs back after the key it modified, the same way an
        // armed shift is spent. After dispatch, not before, so the key that
        // fires is the Fn layer's key and not the one it replaced.
        if (key.action != KeyAction.Fn) consumeFn()
    }

    private var lastFnTapTime = 0L

    /**
     * Tap switches to the Fn layer for one key and springs back; a quick second
     * tap sticks. Springing back is what makes a one-off Esc or F5 cheap — the
     * common case is a single function key, not a run of them.
     */
    private fun onFn() {
        val now = System.currentTimeMillis()
        val doubleTap = now - lastFnTapTime < SHIFT_DOUBLE_TAP_MS
        lastFnTapTime = now
        _uiState.update {
            when {
                // A layout can be shared without its Fn layer, or the layer
                // deleted while a key pointing at it survives. Do nothing rather
                // than switch to a grid that is really a copy of the letters.
                it.layouts.fn == null -> it
                it.layoutMode == LayoutMode.FN && doubleTap -> it.copy(fnLocked = true)
                it.layoutMode == LayoutMode.FN -> it.copy(
                    layoutMode = it.fnReturn ?: LayoutMode.LETTERS,
                    fnLocked = false,
                    fnReturn = null,
                )
                else -> it.copy(
                    layoutMode = LayoutMode.FN,
                    fnReturn = it.layoutMode,
                    fnLocked = false,
                )
            }
        }
    }

    private fun consumeFn() {
        _uiState.update {
            if (it.layoutMode != LayoutMode.FN || it.fnLocked) {
                it
            } else {
                it.copy(layoutMode = it.fnReturn ?: LayoutMode.LETTERS, fnReturn = null)
            }
        }
    }

    private val modifierTapTimes = EnumMap<ModifierKey, Long>(ModifierKey::class.java)

    /**
     * The same three-state gesture as [onShift], reusing its double-tap window.
     *
     * A timer-free OFF → ARMED → LOCKED → OFF cycle was the alternative and was
     * rejected: arming Ctrl and immediately changing your mind would leave it
     * *locked*, which is the one state where every following letter silently
     * becomes a shortcut.
     */
    private fun onModifier(key: ModifierKey) {
        val now = System.currentTimeMillis()
        val doubleTap = now - (modifierTapTimes[key] ?: 0L) < SHIFT_DOUBLE_TAP_MS
        modifierTapTimes[key] = now
        _uiState.update { state ->
            val current = state.modifiers[key]
            val next = when {
                doubleTap && current != ModifierState.LOCKED -> ModifierState.LOCKED
                current == ModifierState.OFF -> ModifierState.ARMED
                else -> ModifierState.OFF
            }
            state.copy(modifiers = state.modifiers.with(key, next))
        }
    }

    /** Twin of [consumeShift]: drops the armed latches, keeps the locked ones. */
    private fun consumeModifiers() {
        _uiState.update {
            if (it.modifiers.isEmpty) it else it.copy(modifiers = it.modifiers.consumed())
        }
    }

    /** Clears every latch, locked ones included. */
    private fun clearModifiers() {
        modifierTapTimes.clear()
        _uiState.update {
            if (it.modifiers == Modifiers.None) it else it.copy(modifiers = Modifiers.None)
        }
    }

    /**
     * Sends [key] as a hardware-style key event with [modifiers] and any pending
     * shift folded in. Returns false when the key has no keycode to send, so the
     * caller decides what to do with the keystroke rather than this guessing.
     *
     * Modifiers are wrapped as real KEYCODE_CTRL_LEFT down/up pairs rather than
     * sent as bare meta flags — the same lesson [sendEditorKey] already learned
     * for shift, since TextView reads modifier state off the modifier key's own
     * events rather than off getMetaState().
     */
    private fun sendShortcut(key: Key, modifiers: Modifiers): Boolean {
        val ic = currentInputConnection ?: return false
        val state = _uiState.value
        val shift = state.shiftState != ShiftState.OFF

        // Ctrl+A/C/V/X have a first-class InputConnection route that works in
        // WebViews and Compose text fields, where a raw Ctrl+C reaches nothing.
        // The choice has to be made here rather than after the send:
        // InputConnection.sendKeyEvent reports that an event was queued, never
        // that anything acted on it, so "send it and check" cannot be written.
        if (modifiers.ctrl != ModifierState.OFF && !state.settings.rawClipboardShortcuts) {
            clipboardShortcutFor(key)?.let { onClipboardKey(it); return true }
        }

        val action = key.action
        val explicit = action as? KeyAction.SendKey
        val code = explicit?.keyCode ?: keyCodeForChar(
            (key.output ?: key.label).singleOrNull() ?: return false,
        ) ?: return false

        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        var meta = modifiers.metaFlags() or (explicit?.meta ?: 0)
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

        // Press order mirrors a hardware keyboard: modifiers down outermost and
        // released in reverse, so an editor that pairs down and up events never
        // ends up with a modifier still held after the shortcut.
        val holds = buildList {
            if (meta and KeyEvent.META_CTRL_ON != 0) add(KeyEvent.KEYCODE_CTRL_LEFT)
            if (meta and KeyEvent.META_ALT_ON != 0) add(KeyEvent.KEYCODE_ALT_LEFT)
            if (meta and KeyEvent.META_META_ON != 0) add(KeyEvent.KEYCODE_META_LEFT)
            if (meta and KeyEvent.META_SHIFT_ON != 0) add(KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        val time = SystemClock.uptimeMillis()
        for (hold in holds) ic.sendKeyEvent(shortcutEvent(time, KeyEvent.ACTION_DOWN, hold, meta))
        ic.sendKeyEvent(shortcutEvent(time, KeyEvent.ACTION_DOWN, code, meta))
        ic.sendKeyEvent(shortcutEvent(time, KeyEvent.ACTION_UP, code, meta))
        for (hold in holds.asReversed()) {
            ic.sendKeyEvent(shortcutEvent(time, KeyEvent.ACTION_UP, hold, meta))
        }
        return true
    }

    /** The clipboard action Ctrl plus this key stands for, if any. */
    private fun clipboardShortcutFor(key: Key): ClipboardKeyAction? =
        when ((key.output ?: key.label).lowercase()) {
            "a" -> ClipboardKeyAction.SELECT_ALL
            "c" -> ClipboardKeyAction.COPY
            "v" -> ClipboardKeyAction.PASTE
            "x" -> ClipboardKeyAction.CUT
            else -> null
        }

    /**
     * FLAG_SOFT_KEYBOARD keeps apps from dropping out of touch mode — which
     * moves focus and hides the caret — the way a hardware keypress does, and
     * the virtual device id matches the character map the keycodes came from.
     * The two older senders ([onUndoRedo], [sendEditorKey]) omit these and are
     * deliberately left alone rather than changed as a side effect of this.
     */
    private fun shortcutEvent(time: Long, action: Int, code: Int, meta: Int) = KeyEvent(
        time, time, action, code, 0, meta,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
    )

    /**
     * Keycode for a character, or null when the virtual keyboard's map has none.
     *
     * ASCII letters and digits are answered by arithmetic — KEYCODE_A..Z and
     * KEYCODE_0..9 are contiguous blocks, and they cover essentially every real
     * shortcut — which keeps a JNI call off the keystroke path. Everything else
     * goes through KeyCharacterMap, whose getEvents() documents itself as
     * unsuitable for text entry; it is used here purely as a character-to-keycode
     * lookup, which is what it is actually good at.
     *
     * Characters it cannot map (Bengali letters, ৳, the combining accents) return
     * null and the keystroke is dropped. Committing the text anyway was the
     * alternative and was rejected: a Ctrl press that quietly types "ব" into a
     * document is worse than one that visibly does nothing.
     */
    private fun keyCodeForChar(char: Char): Int? {
        val lower = char.lowercaseChar()
        if (lower in 'a'..'z') return KeyEvent.KEYCODE_A + (lower - 'a')
        if (lower in '0'..'9') return KeyEvent.KEYCODE_0 + (lower - '0')
        val events = runCatching { virtualKeyMap.getEvents(charArrayOf(lower)) }
            .getOrNull() ?: return null
        return events.firstOrNull {
            it.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(it.keyCode)
        }?.keyCode
    }

    /**
     * Loaded once: KeyCharacterMap.load crosses into native code, and the
     * BUILT_IN_KEYBOARD device's map can legitimately be empty — which is the
     * case the virtual device id exists to avoid.
     */
    private val virtualKeyMap: KeyCharacterMap by lazy {
        KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    }

    /**
     * Called from the popup with an alternate character. Routed through [onKey]
     * rather than straight to [onTextKey] so an alternate picked under a latched
     * Ctrl behaves like the base key instead of typing itself.
     */
    fun onText(text: String) {
        vibrate()
        onKey(Key(label = text))
    }

    private fun onTextKey(key: Key) {
        processTypedText(keyOutput(key, _uiState.value), applyDeadKeys = true)
    }

    /**
     * Shared path for one typed character, whether it came from a soft key
     * ([onTextKey]) or a physical keyboard ([handleHardwareKeyDown]).
     *
     * [applyDeadKeys] is true only for soft keys. A physical key's character
     * already carries the hardware layout's own shift and AltGr, and its dead
     * keys are composed by the framework before the IME sees them, so running
     * our dead-key state machine over it as well would double-apply accents.
     */
    private fun processTypedText(input: String, applyDeadKeys: Boolean) {
        val state = _uiState.value
        var text = input
        // Any new input ends the window in which backspace reverts the
        // previous autocorrect.
        lastAutocorrect = null

        if (applyDeadKeys) {
            // Dead keys: the accent arms and waits, then fuses with the next
            // letter. Pressing the same accent twice types it literally, which
            // is the standard escape hatch for wanting the accent on its own.
            val pressedMark = DeadKeys.markOf(text)
            val armedMark = pendingDeadKey
            when {
                pressedMark != null && pressedMark == armedMark -> {
                    setPendingDeadKey(null)
                    text = DeadKeys.standalone(pressedMark)
                }
                pressedMark != null -> {
                    setPendingDeadKey(pressedMark)
                    consumeShift()
                    return
                }
                armedMark != null -> {
                    setPendingDeadKey(null)
                    text = DeadKeys.apply(armedMark, text)
                }
            }
        }

        // The typing test scores keystrokes instead of committing them, so
        // it takes the character before any suggestion or field machinery
        // sees it — nothing typed during a run reaches the user's text.
        if (state.typingTestActive) {
            typingTestType(text)
            consumeShift()
            return
        }

        // The AI Custom instruction composes on the key rows — characters go
        // into its buffer, never the field behind the panel.
        if (state.aiCustomInputActive) {
            aiCustomInputEdit { it + text }
            consumeShift()
            return
        }

        if (state.emojiSearchActive) {
            text = fixedLayoutContextualVowel(text, state.emojiQuery.lastOrNull())
            _uiState.update { it.copy(emojiQuery = it.emojiQuery + text) }
            refreshKarContext()
            refreshEmojiResults()
            return
        }
        if (state.mediaSearchActive && state.panel.hasMediaSearch) {
            text = fixedLayoutContextualVowel(text, state.mediaQuery.lastOrNull())
            _uiState.update { it.copy(mediaQuery = it.mediaQuery + text) }
            refreshKarContext()
            // QR encodes locally as you type — no network search to schedule.
            if (state.panel != PanelMode.QR_GEN) scheduleMediaLiveSearch()
            return
        }

        if (state.dictionarySearchActive) {
            _uiState.update { it.copy(dictionaryQuery = it.dictionaryQuery + text) }
            consumeShift()
            return
        }

        val ic = currentInputConnection ?: return
        text = fixedLayoutContextualVowel(text, ic.getTextBeforeCursor(1, 0)?.lastOrNull())

        // Typing over a selection replaces it and puts the cursor after the
        // new character, like every other keyboard. Never route through the
        // composing buffer in that case.
        if (hasSelection(ic)) {
            // Bracket/brace/quote over a selection wraps it in the pair
            // ("foo" → "(foo)") instead of replacing it, and leaves the inner
            // text selected so it can be wrapped or re-cased again.
            val closer = if (state.settings.textEditing.wrapSelectionWithPair && text.length == 1) {
                WRAP_PAIRS[text[0]]
            } else {
                null
            }
            if (closer != null) {
                val selected = ic.getSelectedText(0)?.toString().orEmpty()
                composing = StringBuilder()
                ic.beginBatchEdit()
                ic.commitText("$text$selected$closer", 1)
                val end = ic.getExtractedText(ExtractedTextRequest(), 0)?.selectionEnd
                if (end != null) {
                    val innerEnd = end - closer.length
                    val innerStart = innerEnd - selected.length
                    if (innerStart in 0..innerEnd) ic.setSelection(innerStart, innerEnd)
                }
                ic.endBatchEdit()
                consumeShift()
                _uiState.update {
                    it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList())
                }
                return
            }
            composing = StringBuilder()
            ic.commitText(text, 1)
            consumeShift()
            _uiState.update { it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList()) }
            if (text.length == 1 && text[0] in SENTENCE_ENDERS) maybeAutoCapitalize()
            return
        }

        val isWordChar = text.length == 1 && (text[0].isLetter() || text[0] == '\'')
        // Avro is a transliterating input method: its composing must run even
        // in password fields and with the strip off, or the roman keys commit
        // untransliterated and no Bengali is produced. English composing only
        // exists to feed suggestions, so it stays gated on those.
        val composingMode = !state.composer.isClusterShaping && (
            state.composer.isTransliterating ||
                (state.allowsTypingIntelligence && state.settings.suggestions)
            )

        // ":" on a word boundary opens inline emoji search: the colon and the
        // letters after it go into the composing buffer, and refreshSuggestions
        // turns that buffer into emoji instead of words. Nothing else needs to
        // track a mode — "composing starts with a colon" *is* the mode, so
        // backspacing the colon away ends it on its own.
        if (state.settings.inlineEmojiSearch && text == ":" &&
            composing.isEmpty() && composingMode
        ) {
            commitComposing(ic, autocorrect = false)
            composing.append(text)
            updateComposingText(ic)
            refreshSuggestions()
            consumeShift()
            return
        }

        if (isWordChar && composingMode) {
            composing.append(text)
            updateComposingText(ic)
            refreshSuggestions()
            consumeShift()
        } else {
            commitComposing(ic, autocorrect = false)
            ic.commitText(text, 1)
            // Consume one-shot shift before evaluating auto-capitalize, so a
            // sentence ender can turn shift back on for the next sentence.
            consumeShift()
            if (text.length == 1 && text[0] in SENTENCE_ENDERS) {
                maybeAutoCapitalize()
            }
            // Email fields commit straight through (no composing buffer), so the
            // contact-email strip has to be refreshed off the committed text.
            if (emailFieldForceActive(state)) refreshEmailFieldSuggestions()
        }
    }

    private fun keyOutput(key: Key, state: KeyboardUiState): String {
        val base = key.output ?: key.label
        return when {
            state.shiftState != ShiftState.OFF && key.shiftLabel != null -> key.shiftLabel
            state.shiftState != ShiftState.OFF && !state.composer.isClusterShaping ->
                base.uppercase()
            else -> base
        }
    }

    /**
     * Fixed Bengali layouts (Probhat, Jatiya): a vowel-sign key yields the
     * kar form (া, ি, …) after a consonant it can attach to, the য়-glide
     * (য়া, য়ে) after another vowel — so কা + আ gives কায়া, never the
     * invalid কাআ — and the independent vowel (আ, ই, …) at a word start.
     */
    private fun fixedLayoutContextualVowel(text: String, previous: Char?): String =
        _uiState.value.composer.contextualForm(text, previous)

    /**
     * Recomputes [KeyboardUiState.vowelForm] from the character before the
     * cursor (or the emoji query) so the fixed-layout vowel keys track the
     * word position both in output and on the key labels.
     */
    private fun refreshKarContext() {
        if (!_uiState.value.composer.isClusterShaping) return
        val previous = if (_uiState.value.emojiSearchActive) {
            _uiState.value.emojiQuery.lastOrNull()
        } else {
            currentInputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        }
        val form = BengaliGraphemes.vowelFormAfter(previous)
        _uiState.update { if (it.vowelForm == form) it else it.copy(vowelForm = form) }
    }

    private fun onShift() {
        // With a selection, shift re-cases the selected text (lower → Title →
        // UPPER) rather than arming shift for the next character. Falls through
        // to normal shift when nothing is selected or the feature is off.
        if (_uiState.value.settings.textEditing.recapitalizeSelectionWithShift) {
            val ic = currentInputConnection
            if (ic != null && hasSelection(ic) && recapitalizeSelection(ic)) return
        }
        // Cancel a just-inserted auto-space after punctuation: the ". " from a
        // double space leaves a trailing space, and one shift press removes it
        // rather than arming caps, so a sentence can be continued without it.
        if (pendingAutoSpace) {
            pendingAutoSpace = false
            val ic = currentInputConnection
            if (ic != null) {
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (before.length == 2 && before[1] == ' ' && before[0] in SENTENCE_ENDERS) {
                    ic.deleteSurroundingText(1, 0)
                    // The ". " also armed auto-cap for a new sentence; cancelling
                    // the break cancels that too, so typing continues in case.
                    consumeShift()
                    return
                }
            }
        }
        val now = System.currentTimeMillis()
        val doubleTap = now - lastShiftTapTime < SHIFT_DOUBLE_TAP_MS
        lastShiftTapTime = now
        _uiState.update {
            it.copy(
                shiftState = when {
                    doubleTap && it.shiftState != ShiftState.CAPS_LOCK -> ShiftState.CAPS_LOCK
                    it.shiftState == ShiftState.OFF -> ShiftState.ON
                    else -> ShiftState.OFF
                },
            )
        }
    }

    private fun consumeShift() {
        _uiState.update {
            if (it.shiftState == ShiftState.ON) it.copy(shiftState = ShiftState.OFF) else it
        }
    }

    /**
     * Re-cases the current selection to the next form in the cycle
     * lower → Title → UPPER → lower and keeps it selected, so repeated shift
     * presses walk the cycle. Returns false (leaving the selection alone) when
     * there is nothing to change — e.g. a caseless script like Bengali.
     */
    private fun recapitalizeSelection(ic: InputConnection): Boolean {
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        if (selected.isEmpty()) return false
        val next = nextCaseForm(selected)
        if (next == selected) return false
        ic.beginBatchEdit()
        ic.commitText(next, 1)
        val end = ic.getExtractedText(ExtractedTextRequest(), 0)?.selectionEnd
        if (end != null) ic.setSelection((end - next.length).coerceAtLeast(0), end)
        ic.endBatchEdit()
        return true
    }

    /** Advances [s] one step through lower → Title → UPPER → lower. */
    private fun nextCaseForm(s: String): String {
        val lower = s.lowercase()
        val upper = s.uppercase()
        val title = toTitleCase(s)
        return when {
            s == lower -> title
            s == title && title != upper -> upper
            s == upper -> lower
            else -> lower // mixed case → normalize to lower to restart the cycle
        }
    }

    /** "hELLO wORLD" → "Hello World": first letter of each word up, rest down. */
    private fun toTitleCase(s: String): String {
        val sb = StringBuilder(s.length)
        var prevLetter = false
        for (c in s) {
            sb.append(if (!prevLetter && c.isLetter()) c.uppercaseChar() else c.lowercaseChar())
            prevLetter = c.isLetter()
        }
        return sb.toString()
    }

    private fun onDelete() {
        val state = _uiState.value
        // Backspace while handwritten ink is waiting for recognition throws
        // the ink away instead of deleting committed text — the natural
        // "no, not that" while writing. Applies to the panel and to
        // handwriting drawn straight on the keys.
        if ((state.panel == PanelMode.HANDWRITING || keyboardHandwriteActive(state)) &&
            state.handwriting.strokes.isNotEmpty()
        ) {
            hwJob?.cancel()
            hwGeneration++
            _uiState.update {
                it.copy(handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false))
            }
            return
        }
        if (state.typingTestActive) {
            typingTestBackspace()
            return
        }
        if (state.aiCustomInputActive) {
            aiCustomInputEdit { it.dropLast(1) }
            return
        }
        if (state.emojiSearchActive) {
            if (state.emojiQuery.isNotEmpty()) {
                _uiState.update { it.copy(emojiQuery = it.emojiQuery.dropLast(1)) }
                refreshKarContext()
                refreshEmojiResults()
            }
            return
        }
        if (state.dictionarySearchActive) {
            if (state.dictionaryQuery.isNotEmpty()) {
                _uiState.update { it.copy(dictionaryQuery = it.dictionaryQuery.dropLast(1)) }
            }
            return
        }
        if (state.mediaSearchActive && state.panel.hasMediaSearch) {
            if (state.mediaQuery.isNotEmpty()) {
                _uiState.update { it.copy(mediaQuery = it.mediaQuery.dropLast(1)) }
                refreshKarContext()
                scheduleMediaLiveSearch()
            }
            return
        }
        deleteFromField()
    }

    /**
     * One backspace against the real text field, regardless of any active
     * panel search: selection first, then gesture-word / autocorrect undo,
     * then composing, then a full grapheme cluster. Split from [onDelete]
     * so the emoji search bar's field-backspace can reach it while the
     * backspace key is busy editing the query.
     */
    private fun deleteFromField() {
        val state = _uiState.value
        val ic = currentInputConnection ?: return
        // Deleting with an active selection removes the selected text only.
        if (hasSelection(ic)) {
            ic.commitText("", 1)
            return
        }
        // Backspace straight after a glide removes the whole swiped word —
        // a wrong swipe shouldn't cost a letter-by-letter cleanup.
        lastGestureWord?.let { word ->
            lastGestureWord = null
            if (composing.isEmpty()) {
                val before = ic.getTextBeforeCursor(word.length, 0)?.toString()
                if (before == word) {
                    ic.deleteSurroundingText(word.length, 0)
                    // Both lists: the bar is up while either has content, so
                    // clearing only the words left a stale emoji row holding
                    // it open.
                    _uiState.update {
                        it.copy(suggestions = emptyList(), emojiSuggestions = emptyList())
                    }
                    return
                }
            }
        }
        // Backspace straight after an autocorrect undoes it: the corrected
        // word (and the space that triggered it) become the typed original,
        // which joins the personal dictionary so it is never auto-"fixed"
        // again — deleting the fix is the strongest "I meant what I typed".
        lastAutocorrect?.let { (typed, corrected) ->
            lastAutocorrect = null
            if (composing.isEmpty() && state.settings.revertAutocorrectOnBackspace) {
                val expected = "$corrected "
                val before = ic.getTextBeforeCursor(expected.length, 0)?.toString()
                if (before == expected) {
                    ic.deleteSurroundingText(expected.length, 0)
                    ic.commitText("$typed ", 1)
                    if (state.settings.learnFromTyping &&
                        !(state.incognitoOn && state.settings.incognitoPausesLearning) &&
                        !state.secureField
                    ) {
                        userLexicon.addWord(typed, boost = 5)
                        invalidateGestureLexicon()
                    }
                    previousWord = typed.lowercase().trim { !it.isLetter() }.ifEmpty { null }
                    return
                }
            }
        }
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            updateComposingText(ic)
            refreshSuggestions()
        } else {
            // Delete a full surrogate pair / grapheme; optionally a whole
            // Bengali conjunct cluster as one unit. The lookback has to
            // outrun the longest emoji ZWJ/tag sequence, not just a pair.
            val before = ic.getTextBeforeCursor(64, 0)
            val emojiLength = if (before.isNullOrEmpty()) 0 else EmojiGraphemes.deleteLength(before)
            val deleteLength = when {
                before.isNullOrEmpty() -> 1
                // Multi-code-point emoji (☠️, 👍🏽, 👨‍👩‍👧) go in one press
                // instead of shedding a piece per backspace.
                emojiLength > 0 -> emojiLength
                state.settings.conjunctBackspace ->
                    state.composer.deleteLength(before).coerceAtLeast(1)
                before.length >= 2 &&
                    Character.isSurrogatePair(before[before.length - 2], before[before.length - 1]) -> 2
                else -> 1
            }
            ic.deleteSurroundingText(deleteLength, 0)
            // Backspacing through committed text is the one way the word
            // behind the cursor changes without passing through learn(), so
            // the strip used to keep predicting from a word the user had
            // already erased. In an empty field that left it offering bigrams
            // for text that was no longer there — and since the bar and its
            // chevron are shown exactly while the strip has content, neither
            // would go away. Re-derive the context, then refresh: with no word
            // behind the cursor the engine returns nothing and the bar folds.
            syncPreviousWordFromField(ic)
            refreshSuggestions()
        }
    }

    /**
     * Re-reads the completed word before the cursor and makes it the
     * prediction context, or clears it when there is none.
     *
     * A cursor sitting mid-word has no *completed* previous word, so it
     * predicts from nothing rather than from the fragment it is inside.
     */
    private fun syncPreviousWordFromField(ic: InputConnection) {
        previousWord = completedWordBefore(ic.getTextBeforeCursor(64, 0))
    }

    /**
     * The completed word ending [text] — the bigram context for whatever comes
     * next — or null when [text] ends inside a word (a fragment is no context)
     * or holds no word at all.
     */
    private fun completedWordBefore(text: CharSequence?): String? = when {
        text.isNullOrEmpty() -> null
        // Still inside a word: the fragment is not a bigram context.
        text.last().isLetterOrDigit() -> null
        else -> text.toString()
            .trim { !it.isLetter() }
            .takeLastWhile { it.isLetter() }
            .lowercase()
            .ifEmpty { null }
    }

    /**
     * Re-reads the context around a cursor that moved without going through a
     * keystroke — a tap elsewhere, a selection-handle drag, a spacebar-swipe
     * caret move, or an edit the app itself made — so the strip reflects where
     * the caret *now* sits instead of the word last typed.
     *
     * When the caret lands at the end of a word, that word is re-entered as the
     * composing region: the strip offers its completions and corrections, and
     * typing on extends it, exactly as if it were being typed fresh (with the
     * word before it restored as the bigram context). Otherwise only the
     * preceding-word context is re-derived — a caret mid-word or after a
     * separator has no word to resume, so it predicts the next one (or clears).
     *
     * Latin-script layouts only: Avro's composing is the roman source of a
     * Bengali field that can't be reversed back into it, and the fixed-Bengali
     * layouts keep no composing buffer to resume. [newSelStart] is the caret
     * offset the field just reported, used to place the composing region.
     */
    private fun restartSuggestionsAtCursor(ic: InputConnection, newSelStart: Int) {
        val state = _uiState.value
        val scrubbing = SystemClock.uptimeMillis() - lastCaretScrubMs < CARET_SCRUB_WINDOW_MS
        val canResume = !scrubbing && state.settings.suggestions &&
            !state.secureField && !state.fieldNoSuggestions &&
            state.allowsTypingIntelligence && state.language.gestureLexicon &&
            !state.typingTestActive && !state.emojiSearchActive &&
            !state.dictionarySearchActive && !state.mediaSearchActive &&
            state.voice.status != VoiceStatus.LISTENING &&
            state.voice.status != VoiceStatus.FINISHING

        if (canResume && newSelStart >= 0) {
            val before = ic.getTextBeforeCursor(64, 0)
            val after = ic.getTextAfterCursor(1, 0)
            // A caret at a word's end: a word char behind it, nothing word-like
            // ahead (end of text, or a separator — not the middle of a token).
            val caretAtWordEnd = before != null && before.isNotEmpty() &&
                isComposingWordChar(before.last()) &&
                (after.isNullOrEmpty() || !after[0].isLetterOrDigit())
            if (caretAtWordEnd) {
                val word = before.toString().takeLastWhile { isComposingWordChar(it) }
                if (word.isNotEmpty()) {
                    // Mark the existing word as composing without disturbing it,
                    // then mirror it into the buffer so a keystroke extends it
                    // and a backspace shortens it. previousWord comes from the
                    // text ahead of the word, not the caret (which is inside it).
                    composing = StringBuilder(word)
                    ic.setComposingRegion(newSelStart - word.length, newSelStart)
                    previousWord = completedWordBefore(before.subSequence(0, before.length - word.length))
                    _uiState.update { it.copy(composingPreview = word) }
                    refreshSuggestions()
                    return
                }
            }
        }
        // No word to resume: predict from the completed word behind the caret,
        // or clear when there is none. refreshSuggestions self-gates on the
        // field flags, so this stays correct in secure / no-suggestion fields.
        syncPreviousWordFromField(ic)
        refreshSuggestions()
    }

    /** Characters that live in the composing buffer — see [onKey]'s isWordChar. */
    private fun isComposingWordChar(c: Char): Boolean = c.isLetter() || c == '\''

    /**
     * Deletes the word before the cursor — one step of the backspace swipe.
     * Trailing whitespace goes with the word, so repeated steps chew back
     * through a sentence the way ctrl+backspace does on a desktop.
     */
    private fun onDeleteWord() {
        val state = _uiState.value
        // A panel search owns the backspace key while it is open; word-deleting
        // the real field behind it would edit text the user cannot see.
        if (state.emojiSearchActive || state.dictionarySearchActive ||
            (state.mediaSearchActive && state.panel.hasMediaSearch) ||
            ((state.panel == PanelMode.HANDWRITING || keyboardHandwriteActive(state)) &&
                state.handwriting.strokes.isNotEmpty())
        ) {
            onDelete()
            return
        }
        val ic = currentInputConnection ?: return
        if (hasSelection(ic)) {
            ic.commitText("", 1)
            return
        }
        // A word in progress lives in the composing buffer, not the field.
        if (composing.isNotEmpty()) {
            composing.setLength(0)
            updateComposingText(ic)
            refreshSuggestions()
            return
        }
        val before = ic.getTextBeforeCursor(96, 0) ?: return
        val length = WordDelete.lengthBefore(before)
        if (length > 0) {
            ic.deleteSurroundingText(length, 0)
            lastGestureWord = null
            lastAutocorrect = null
            // The word that was deleted is gone as context, but whatever now
            // sits behind the cursor is the real one — nulling it outright
            // meant a swipe-delete mid-sentence stopped predicting until the
            // next word was typed.
            syncPreviousWordFromField(ic)
            // Both lists; see the gesture-undo path above.
            _uiState.update {
                it.copy(suggestions = emptyList(), emojiSuggestions = emptyList())
            }
        }
    }

    private fun onSpace() {
        // Ahead of the input-connection check: a typing test scores space as
        // the word separator and never touches the field, so it must still
        // work in a window that has no editor focused.
        if (_uiState.value.typingTestActive) {
            typingTestSpace()
            return
        }
        // Ahead of the editor check too: the Custom instruction must accept a
        // space even in a window with no focused field.
        if (_uiState.value.aiCustomInputActive) {
            aiCustomInputEdit { it + " " }
            return
        }
        val ic = currentInputConnection ?: return
        val state = _uiState.value
        val now = System.currentTimeMillis()

        if (state.emojiSearchActive) {
            _uiState.update { it.copy(emojiQuery = it.emojiQuery + " ") }
            refreshEmojiResults()
            return
        }
        if (state.mediaSearchActive && state.panel.hasMediaSearch) {
            _uiState.update { it.copy(mediaQuery = it.mediaQuery + " ") }
            if (state.panel != PanelMode.QR_GEN) scheduleMediaLiveSearch()
            return
        }

        // Multi-word dictionary entries ("give up") are legitimate lookups.
        if (state.dictionarySearchActive) {
            _uiState.update { it.copy(dictionaryQuery = it.dictionaryQuery + " ") }
            return
        }

        // Space over a selection replaces it; skip autocorrect/double-space.
        if (hasSelection(ic)) {
            ic.commitText(" ", 1)
            lastSpaceTime = 0
            maybeAutoCapitalize()
            return
        }

        val committed = commitComposing(
            ic,
            autocorrect = state.settings.autocorrect,
            fixApostrophes = state.settings.autoApostrophe,
        )

        // Double-tap space inserts a tab. Checked before the period rule so
        // enabling it wins, and unlike the period it works anywhere a space
        // was just typed (indenting at a line start has no word before it).
        if (!committed && state.settings.doubleSpaceTab && now - lastSpaceTime < 400) {
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            if (before == " ") {
                ic.deleteSurroundingText(1, 0)
                ic.commitText("\t", 1)
                lastSpaceTime = 0
                return
            }
        }

        // Double-space inserts ". "
        // Only in plain text fields: a double space in an email, URI or
        // number box must stay two spaces, not become ". ".
        if (!committed && state.settings.doubleSpacePeriod &&
            state.fieldKind == FieldKind.TEXT && now - lastSpaceTime < 400
        ) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.endsWith(" ") && before.length == 2 && !before[0].isWhitespace()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceTime = 0
                // Arm the shift-to-cancel: a shift press now drops this space.
                pendingAutoSpace = true
                maybeAutoCapitalize()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceTime = now
        maybeAutoCapitalize()
        // Next-word predictions (learned bigrams, including word → emoji)
        // appear once the word is committed.
        refreshSuggestions()
    }

    private fun onEnter() {
        val state = _uiState.value
        // Enter is not part of a typing test, and letting it through would
        // put a newline in the field behind the panel.
        if (state.typingTestActive) return
        // Enter runs the Custom action rather than dropping a newline into the
        // app behind the panel.
        if (state.aiCustomInputActive) {
            onAiRunCustom()
            return
        }
        if (state.dictionarySearchActive) {
            onDictionaryLookup(state.dictionaryQuery)
            return
        }
        // QR builds its content as you type; Enter adds a newline to the
        // buffer (WiFi/vCard payloads span lines) rather than searching.
        if (state.mediaSearchActive && state.panel == PanelMode.QR_GEN) {
            _uiState.update { it.copy(mediaQuery = it.mediaQuery + "\n") }
            return
        }
        // Enter in a media search box runs the search instead of typing a
        // newline into the app behind the keyboard.
        if (state.mediaSearchActive && state.panel.hasMediaSearch) {
            runMediaSearch()
            return
        }
        val ic = currentInputConnection ?: return
        commitComposing(ic, autocorrect = false)
        // Same decoder that labels the key, so Enter always does what the
        // key is drawing — including an app's own actionId behind a custom
        // actionLabel. Null means "no action": type a real newline.
        val action = currentInputEditorInfo.editorActionId()
        if (action != null) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            maybeAutoCapitalize()
        }
    }

    private fun toggleSymbols() {
        // Leaving the letter layer ends the on-keyboard writing surface.
        if (_uiState.value.layoutMode == LayoutMode.LETTERS) dropKeyboardHandwritingInk()
        _uiState.update {
            it.copy(
                layoutMode = when (it.layoutMode) {
                    LayoutMode.LETTERS -> LayoutMode.SYMBOLS
                    LayoutMode.SYMBOLS -> LayoutMode.SYMBOLS_SHIFTED
                    LayoutMode.SYMBOLS_SHIFTED -> LayoutMode.SYMBOLS
                    // ?123 from the Fn layer leaves it, lock and all: the user
                    // asked for a different grid, not for Fn to persist under it.
                    LayoutMode.FN -> LayoutMode.SYMBOLS
                },
                fnLocked = false,
                fnReturn = null,
            )
        }
    }

    /**
     * The grids reachable from the focused field, compiled once here rather than
     * per recomposition.
     *
     * Resolution lives in the service because it is the side that owns the
     * layout store, and because [keyRowsHeight] has to be a pure function of
     * state: the reserved row span is the maximum over *every* reachable layer,
     * which the rendering code — looking at one layer at a time — could never
     * compute for itself.
     *
     * The result is memoised so the returned instance is reference-stable.
     * [KeyboardUiState]'s generated `equals` walks its fields, so handing out a
     * fresh set per emission would make every state comparison walk every key.
     */
    /**
     * The layout being typed on: the field's override if it asked for one,
     * otherwise the user's choice. Resolved rather than raw, so an id whose
     * layout was deleted heals to the default instead of selecting nothing.
     */
    private fun activeLayoutSpec(settings: KeyboardSettings): LayoutSpec =
        resolveLayout(settings.customLayouts, fieldLayoutOverride ?: baseLayoutId(settings))

    /**
     * The layout to type on before any field override: the one remembered for the
     * focused app when per-app memory is on, otherwise the global choice. A
     * remembered id whose layout has since been deleted heals back to the global
     * pick rather than snapping to the default.
     */
    private fun baseLayoutId(settings: KeyboardSettings): String {
        if (!settings.perAppLanguage.enabled) return settings.activeLayoutId
        val remembered = settings.perAppLanguage.layoutByPackage[currentPackage]
            ?.takeIf { id -> resolveLayout(settings.customLayouts, id).id == id }
        return remembered ?: settings.activeLayoutId
    }

    private fun resolveLayoutSet(spec: LayoutSpec, fieldKind: FieldKind): LayoutSet {
        val key = spec.id to fieldKind
        layoutSetCache[key]?.let { return it }
        val set = LayoutSet(
            letters = spec.compile(LayoutLayer.LETTERS),
            symbols = spec.compile(LayoutLayer.SYMBOLS),
            symbolsShifted = spec.compile(LayoutLayer.SYMBOLS_SHIFTED),
            // Only when the layout actually defines one: compile() falls back
            // to the shipped grid for a missing layer, which would give every
            // layout an Fn layer that is really a second copy of the letters.
            fn = spec.layer(LayoutLayer.FN)?.let { spec.compile(LayoutLayer.FN) },
            numeric = fieldKind.numericLayer?.let(spec::compile),
            numberRows = buildMap {
                spec.numberRowFor(LayoutLayer.LETTERS)?.let { put(LayoutMode.LETTERS, it) }
                spec.numberRowFor(LayoutLayer.SYMBOLS)?.let { put(LayoutMode.SYMBOLS, it) }
                spec.numberRowFor(LayoutLayer.SYMBOLS_SHIFTED)
                    ?.let { put(LayoutMode.SYMBOLS_SHIFTED, it) }
                spec.numberRowFor(LayoutLayer.FN)?.let { put(LayoutMode.FN, it) }
            },
        )
        layoutSetCache[key] = set
        return set
    }

    private val layoutSetCache = HashMap<Pair<String, FieldKind>, LayoutSet>()

    private fun switchLanguage() {
        val state = _uiState.value
        // Cycles layout ids, not modes: three custom layouts all based on
        // English are three distinct stops, where cycling modes would collapse
        // them into one and make them unreachable from the keyboard.
        val ids = state.settings.enabledLayoutIds.ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) }
        onLayoutSelected(ids[(ids.indexOf(state.layoutId) + 1).mod(ids.size)])
    }

    /** Spacebar swipe (or 🌐 cycle): switch to an explicit layout. */
    fun onLayoutSelected(layoutId: String) {
        val spec = resolveLayout(_uiState.value.settings.customLayouts, layoutId)
        currentInputConnection?.let { commitComposing(it, autocorrect = false) }
        // An explicit switch beats what the field asked for: the user can
        // see the box they are typing in, FORCE_ASCII and hintLocales are
        // only the app's guess.
        fieldLayoutOverride = null
        _uiState.update {
            it.copy(
                language = spec.language(),
                script = spec.script(),
                composer = composerFor(spec.script(), spec.composerType()),
                layoutId = spec.id,
                layoutName = spec.name,
                layouts = resolveLayoutSet(spec, it.fieldKind),
                layoutMode = LayoutMode.LETTERS,
            )
        }
        refreshKarContext()
        // The handwriting model follows the input language; a switch while
        // the panel is open — or while writing on the keys — re-checks the new
        // model and drops pending ink.
        if (_uiState.value.panel == PanelMode.HANDWRITING ||
            keyboardHandwriteActive(_uiState.value)
        ) {
            refreshHandwritingStatus()
        }
        // Same for dictation: restart the session in the new language.
        if (_uiState.value.panel == PanelMode.VOICE) startVoice()
        serviceScope.launch { settingsRepository.setActiveLayoutId(spec.id) }
        // Per-app memory: an explicit pick is what this app should reopen on.
        // The global write above still moves, so apps with no stored pick keep
        // following the last-used layout.
        if (_uiState.value.settings.perAppLanguage.enabled) {
            currentPackage?.let { pkg ->
                serviceScope.launch { settingsRepository.setAppLayout(pkg, spec.id) }
            }
        }
        mirrorSubtypeToOs(spec)
    }

    /** Resource id for the subtype label the switcher shows, per the label setting. */
    private fun subtypeNameResId(settings: KeyboardSettings): Int =
        if (settings.subtypeAppNameFirst) R.string.subtype_app_label else 0

    /**
     * Mirrors the enabled layouts to the OS as additional input-method subtypes,
     * so the system language switcher lists them and can switch between them.
     * Off (the [KeyboardSettings.osLanguageSwitcher] toggle) registers an empty
     * set, clearing any previously exposed subtypes. Diffed against the last
     * write via [registeredSubtypeSig].
     */
    // The String-id overload is deprecated on new SDKs but is the only one that
    // exists at minSdk 24 — the typed replacement is API 36+.
    @Suppress("DEPRECATION")
    private fun registerSubtypes(settings: KeyboardSettings) {
        val ids = settings.enabledLayoutIds.ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) }
        val nameResId = subtypeNameResId(settings)
        val sig = if (!settings.osLanguageSwitcher) "off"
        else "${ids.joinToString(",")}#$nameResId"
        if (sig == registeredSubtypeSig) return
        val subtypes = if (settings.osLanguageSwitcher) {
            ids.map { subtypeFor(resolveLayout(settings.customLayouts, it), nameResId) }.toTypedArray()
        } else {
            emptyArray()
        }
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        runCatching { imm.setAdditionalInputMethodSubtypes(imeId, subtypes) }
            .onSuccess { registeredSubtypeSig = sig }
    }

    /**
     * Best-effort nudge of the OS switcher to match an in-app language switch so
     * the system UI's current subtype stays in sync. Skipped when the switcher
     * is turned off. API 28+ only; on 24–27 the OS→app direction still works, we
     * just don't push the other way. Swallowed: the subtype may not be
     * registered yet on a cold switch, and this is cosmetic — [onLayoutSelected]
     * has already moved the keyboard.
     */
    private fun mirrorSubtypeToOs(spec: LayoutSpec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val settings = _uiState.value.settings
        if (!settings.osLanguageSwitcher) return
        runCatching { switchInputMethod(imeId, subtypeFor(spec, subtypeNameResId(settings))) }
    }

    /**
     * The OS switcher (or system language shortcut) picked one of our subtypes:
     * follow it. Ignored when the switcher is off (we register nothing then).
     * Guarded against the echo from [mirrorSubtypeToOs] — when we are already on
     * that layout there is nothing to do, which also stops the in-app→OS→in-app
     * loop.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (!_uiState.value.settings.osLanguageSwitcher) return
        val layoutId = layoutIdOf(newSubtype) ?: return
        if (layoutId != _uiState.value.layoutId) onLayoutSelected(layoutId)
    }

    // ---- composing & suggestions ----

    private fun updateComposingText(ic: InputConnection) {
        val state = _uiState.value
        val preview = if (state.composer.isTransliterating) {
            state.composer.composeBuffer(composing.toString())
        } else {
            composing.toString()
        }
        ic.setComposingText(preview, 1)
        _uiState.update { it.copy(composingPreview = preview) }
    }

    /**
     * Commits the composing region. In Avro mode the top phonetic
     * suggestion wins (dictionary sibling over literal); in English mode
     * autocorrect may replace the typed word. Returns true if anything
     * was committed.
     */
    private fun commitComposing(
        ic: InputConnection,
        autocorrect: Boolean,
        fixApostrophes: Boolean = false,
    ): Boolean {
        if (composing.isEmpty()) return false
        val typed = composing.toString()
        val state = _uiState.value

        // An abandoned inline emoji query (":smi" then space) is literal text:
        // never transliterated, autocorrected, or learned as a word.
        if (inlineEmojiQuery() != null) {
            ic.commitText(typed, 1)
            composing = StringBuilder()
            _uiState.update {
                it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList())
            }
            return true
        }
        // Apostrophe restoration outranks autocorrect: "dont" is a known
        // contraction slip, not a typo for "font"/"done" to be guessed at.
        val apostrophized =
            if (fixApostrophes && state.allowsTypingIntelligence &&
                state.language.isEnglish
            ) {
                Apostrophes.fix(typed)
            } else {
                null
            }
        // The async suggestion job precomputes this word's commit resolution off
        // the main thread; use it only while it still matches [typed] (a
        // mismatch means the job hasn't caught up), else compute synchronously.
        // Either way the result is fresh — the commit never uses a stale strip.
        val pre = commitResolution?.takeIf { it.typed == typed }
        var corrected: String? = null
        val output = when {
            state.composer.isBengaliPhonetic ->
                (if (pre != null && pre.isBengali) pre.bengaliTop
                else suggestionEngine?.suggest(typed, previousWord = null, avroMode = true)?.firstOrNull())
                    ?: state.composer.composeBuffer(typed)
            // Other transliterators (Hangul) commit the composed text directly,
            // with no dictionary pass.
            state.composer.isTransliterating -> state.composer.composeBuffer(typed)
            apostrophized != null -> apostrophized
            autocorrect && state.allowsTypingIntelligence -> {
                corrected = if (pre != null && !pre.isBengali) pre.correction
                else suggestionEngine?.shouldAutocorrect(typed)?.takeIf { it != typed }
                corrected ?: typed
            }
            else -> typed
        }
        lastAutocorrect = corrected?.let { typed to it }
        ic.commitText(output, 1)
        // An autocorrected word was the engine's choice, not the user's —
        // it earns no personal-dictionary reinforcement, only the bigram.
        learn(output, reinforcement = if (corrected != null) 0 else 1)
        composing = StringBuilder()
        // Refill the strip in the same frame the word commits. Blanking it
        // and waiting for the async refresh left it empty for a frame or
        // two after every space, which read as a flicker.
        val (nextWords, nextEmojis) = nextWordStrip()
        _uiState.update {
            it.copy(composingPreview = "", suggestions = nextWords, emojiSuggestions = nextEmojis)
        }
        return true
    }

    /**
     * Next-word predictions for the word just committed, computed inline.
     *
     * Only safe because the empty-composing path is a handful of bigram map
     * lookups — no dictionary completion, no edit-distance search — so it
     * costs less than the dispatch it replaces. Returns words to emojis,
     * matching the split [refreshSuggestions] does.
     */
    private fun nextWordStrip(): Pair<List<String>, List<String>> {
        val engine = suggestionEngine
        val state = _uiState.value
        if (engine == null || !state.settings.suggestions || state.secureField ||
            state.fieldNoSuggestions
        ) {
            return emptyList<String>() to emptyList()
        }
        val (emojis, words) = engine
            .suggest(composing = "", previousWord = previousWord)
            .partition { isEmojiCandidate(it) }
        return words to if (state.settings.emojiPrediction) emojis else emptyList()
    }

    /**
     * [reinforcement] grades how deliberate the commit was: 2 for a tapped
     * suggestion, 1 for a plainly typed word, 0 for an autocorrect (the
     * word still anchors bigrams but doesn't join the personal lexicon).
     * Multi-word commits ("of the" from a split suggestion) learn each
     * word and the bigrams linking them.
     */
    private fun learn(word: String, reinforcement: Int = 1) {
        val state = _uiState.value
        if (!state.settings.learnFromTyping ||
            (state.incognitoOn && state.settings.incognitoPausesLearning) ||
            !state.allowsTypingIntelligence
        ) {
            previousWord = word
            return
        }
        var previous = previousWord
        var lastLearned: String? = null
        for (part in word.split(' ')) {
            val cleaned = part.trim { !it.isLetter() }
            if (cleaned.isEmpty()) continue
            userLexicon.learnWord(cleaned, reinforcement)
            // Mirror genuinely typed words (not autocorrect targets, which are
            // reinforcement 0 and already dictionary words) into Android's
            // shared personal dictionary when the user has opted in.
            if (reinforcement > 0 && state.settings.addWordsToSystemDictionary) {
                serviceScope.launch(Dispatchers.IO) {
                    SystemUserDictionary.add(applicationContext, cleaned)
                }
            }
            previous?.let { userLexicon.learnBigram(it, cleaned) }
            previous = cleaned
            lastLearned = cleaned
        }
        // A new/reinforced personal word changes the gesture decoder's merged
        // lexicon; drop the cache so the next swipe picks it up.
        if (lastLearned != null) invalidateGestureLexicon()
        previousWord = lastLearned
    }

    /**
     * A committed emoji learns the word→emoji bigram ("you" → ❤️ after
     * "I love you ❤️"), so next-word prediction can offer the emoji the
     * next time the phrase is typed. The emoji then becomes the previous
     * "word" so emoji→word habits are learned too.
     */
    private fun learnEmoji(emoji: String) {
        val state = _uiState.value
        if (!state.settings.learnFromTyping ||
            (state.incognitoOn && state.settings.incognitoPausesLearning) ||
            !state.allowsTypingIntelligence
        ) {
            previousWord = emoji
            return
        }
        previousWord?.let { userLexicon.learnBigram(it, emoji) }
        previousWord = emoji
    }

    /**
     * Reads contact display names into [contactNames] (memory only, never
     * persisted). No-op without the permission — the settings app requests
     * it, and [onStartInputView] retries once it has been granted.
     */
    private fun loadContactNames() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        serviceScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val names = ArrayList<String>()
                    contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val nameColumn = cursor.getColumnIndexOrThrow(
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                        )
                        while (cursor.moveToNext()) {
                            cursor.getString(nameColumn)?.let { names.add(it) }
                        }
                    }
                    ContactNames.fromNames(names)
                }.getOrDefault(ContactNames.EMPTY)
            }
            contactNames = loaded
            suggestionEngine?.contacts = loaded
        }
    }

    /**
     * Reads contact email addresses into [contactEmails] (memory only, never
     * persisted). No-op without the permission — the settings app requests it,
     * and [onStartInputView] retries once it has been granted.
     */
    private fun loadContactEmails() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        serviceScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val emails = ArrayList<String>()
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val addressColumn = cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Email.ADDRESS
                        )
                        while (cursor.moveToNext()) {
                            cursor.getString(addressColumn)?.let { emails.add(it) }
                        }
                    }
                    ContactEmails.fromAddresses(emails)
                }.getOrDefault(ContactEmails.EMPTY)
            }
            contactEmails = loaded
            suggestionEngine?.contactEmails = loaded
        }
    }

    /**
     * Reads the labels of launchable apps into [appNames] (memory only,
     * never persisted). Needs no permission: the launcher-intent query is
     * covered by the <queries> manifest entry.
     */
    private fun loadAppNames() {
        serviceScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                    val pm = packageManager
                    val labels = pm.queryIntentActivities(intent, 0)
                        .mapNotNull { it.loadLabel(pm)?.toString() }
                        .distinct()
                    AppNames.fromNames(labels)
                }.getOrDefault(AppNames.EMPTY)
            }
            appNames = loaded
            suggestionEngine?.apps = loaded
        }
    }

    /** Arms or clears the dead-key accent, keeping the strip chip in step. */
    private fun setPendingDeadKey(mark: Char?) {
        pendingDeadKey = mark
        val label = mark?.let { DeadKeys.standalone(it) }
        if (_uiState.value.pendingDeadKey != label) {
            _uiState.update { it.copy(pendingDeadKey = label) }
        }
    }

    /** Heuristic: a learned bigram successor that is an emoji, not a word. */
    private fun isEmojiCandidate(text: String): Boolean =
        text.isNotBlank() && text.none { it.isLetterOrDigit() } && text.any { it.code > 0x2000 }

    /**
     * The inline emoji query when the composing buffer is one — ":smi" gives
     * "smi". Null whenever inline search is off or the buffer is an ordinary
     * word, so callers can branch on it directly.
     */
    private fun inlineEmojiQuery(): String? {
        if (!_uiState.value.settings.inlineEmojiSearch) return null
        val typed = composing.toString()
        return if (typed.startsWith(":")) typed.drop(1) else null
    }

    // ---- smart suggestions (inline tool answers) ----

    /**
     * Re-scans the text before the cursor for something a tool can answer —
     * a sum, an amount in a currency, a measurement, a tool keyword — and
     * parks the result in [KeyboardUiState.smart] for the strip to draw.
     *
     * Cheap enough to run inline on every keystroke: one short
     * `getTextBeforeCursor` plus a handful of anchored regexes over at most
     * [SmartSuggest.LOOKBEHIND] characters. It deliberately does not follow
     * `settings.suggestions` — someone who turned word prediction off may
     * still want "12*4" answered — but it does respect the field's own
     * refusal to take suggestions, and never runs in a password field.
     */
    /**
     * Text before the cursor as it stood right after a chip was accepted.
     * An inserted answer is often itself a trigger ("18,300.00 BDT" reads as
     * an amount to convert back), so the chip stays down until the field
     * changes again — matching on the text rather than a flag means any
     * edit at all, from anywhere, lifts the mute.
     */
    private var smartMutedAfter: String? = null

    private fun refreshSmartSuggestion() {
        val state = _uiState.value
        val enabled = state.settings.smartSuggestions &&
            !state.secureField && !state.fieldNoSuggestions &&
            state.panel == PanelMode.NONE
        if (!enabled) {
            if (state.smart != null) _uiState.update { it.copy(smart = null) }
            return
        }
        val before = currentInputConnection
            ?.getTextBeforeCursor(SmartSuggest.LOOKBEHIND, 0)
            ?.toString()
            .orEmpty()
        if (before == smartMutedAfter) {
            if (state.smart != null) _uiState.update { it.copy(smart = null) }
            return
        }
        smartMutedAfter = null
        val hit = SmartSuggest.detect(before, smartContext(state))
        if (hit != state.smart) _uiState.update { it.copy(smart = hit) }
        // An amount was recognised but there are no rates to convert it
        // with: fetch them, and the collector below redraws the chip.
        if (hit?.pending == true) refreshCurrencyRates()
    }

    private fun smartContext(state: KeyboardUiState): SmartSuggest.Context =
        SmartSuggest.Context(
            calcEnabled = state.settings.smartCalc,
            currencyEnabled = state.settings.smartCurrency,
            unitsEnabled = state.settings.smartUnits,
            keywordsEnabled = state.settings.smartToolKeywords,
            degrees = state.settings.calcDegrees,
            precision = state.settings.calcPrecision,
            rates = (state.currency as? CurrencyUi.Ready)?.rates,
            currencyFrom = state.settings.currencyFrom,
            currencyTo = state.settings.currencyTo,
            currencyDecimals = state.settings.currencyDecimals,
            unitLast = state.settings.unitConvertLast,
            enabledTools = state.settings.enabledTools,
            keywordOverrides = state.settings.toolKeywords,
        )

    /**
     * Chip tapped: swap the recognised text for the answer. The span is
     * whatever the trigger occupied, so "150usd" is replaced outright while
     * a trailing "=" keeps what was typed and appends the result.
     */
    fun onSmartSuggestionTapped() {
        val hit = _uiState.value.smart ?: return
        val insert = hit.insert ?: return
        stopVoiceForManualInput()
        vibrate()
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        commitComposing(ic, autocorrect = false)
        if (hit.replaceSpan > 0) ic.deleteSurroundingText(hit.replaceSpan, 0)
        ic.commitText(insert, 1)
        ic.endBatchEdit()
        smartMutedAfter = ic.getTextBeforeCursor(SmartSuggest.LOOKBEHIND, 0)?.toString()
        composing = StringBuilder()
        lastGestureWord = null
        _uiState.update {
            it.copy(
                composingPreview = "", smart = null,
                suggestions = emptyList(), emojiSuggestions = emptyList(),
            )
        }
        refreshSuggestions()
    }

    /**
     * Chip's open button: drop the recognised text (the tool is about to
     * type its own result there) and load it into the tool as a prefill.
     * The caller then taps the tool the normal way, so panel routing stays
     * in one place.
     */
    fun onSmartSuggestionOpen() {
        val hit = _uiState.value.smart ?: return
        val ic = currentInputConnection
        if (ic != null) {
            ic.beginBatchEdit()
            commitComposing(ic, autocorrect = false)
            if (hit.replaceSpan > 0) ic.deleteSurroundingText(hit.replaceSpan, 0)
            ic.endBatchEdit()
        }
        composing = StringBuilder()
        _uiState.update {
            it.copy(
                composingPreview = "", smart = null, toolPrefill = hit.prefill,
                suggestions = emptyList(), emojiSuggestions = emptyList(),
            )
        }
    }

    /** A panel has loaded its prefill; drop it so reopening starts clean. */
    fun onToolPrefillConsumed() {
        if (_uiState.value.toolPrefill != null) _uiState.update { it.copy(toolPrefill = null) }
    }

    /**
     * True when the email-field contact-email path should drive the strip:
     * an email field, the feature and its email-field override both on, the
     * master strip on, not a password box, and some addresses to offer. This
     * deliberately ignores [KeyboardUiState.fieldNoSuggestions] — the whole
     * point is to complete addresses even where the field asked for a silent
     * strip (which email fields do).
     */
    private fun emailFieldForceActive(state: KeyboardUiState = _uiState.value): Boolean =
        state.fieldKind == FieldKind.EMAIL &&
            !state.secureField &&
            state.settings.suggestions &&
            state.settings.contactEmailSuggestions &&
            state.settings.contactEmailSuggestionsInEmailFields &&
            !contactEmails.isEmpty

    /** The email-address token immediately before the cursor (may be empty). */
    private fun emailTokenBeforeCursor(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(EMAIL_FIELD_LOOKBEHIND, 0)?.toString() ?: return ""
        return before.takeLastWhile { it.isLetterOrDigit() || it in EMAIL_TOKEN_EXTRA }
    }

    /**
     * Email-field completion: contact emails whose address starts with the
     * token before the cursor, pushed into the strip even though the field
     * suppresses the normal one. Email fields keep no composing buffer, so the
     * token is read straight from the connection.
     */
    private fun refreshEmailFieldSuggestions() {
        suggestionJob?.cancel()
        val ic = currentInputConnection
        val token = ic?.let { emailTokenBeforeCursor(it) }.orEmpty().lowercase()
        if (token.length < EMAIL_FIELD_MIN_PREFIX) {
            _uiState.update {
                if (it.suggestions.isEmpty()) it
                else it.copy(suggestions = emptyList(), emojiSuggestions = emptyList())
            }
            return
        }
        suggestionJob = serviceScope.launch {
            val results = withContext(Dispatchers.Default) {
                contactEmails.complete(token, EMAIL_FIELD_SUGGESTION_LIMIT)
            }
            _uiState.update { it.copy(suggestions = results, emojiSuggestions = emptyList()) }
        }
    }

    private fun refreshSuggestions() {
        val state = _uiState.value
        if (emailFieldForceActive(state)) {
            refreshEmailFieldSuggestions()
            return
        }
        refreshSmartSuggestion()
        val engine = suggestionEngine ?: return
        if (!state.settings.suggestions || state.secureField || state.fieldNoSuggestions) return

        // Inline emoji search takes over the strip entirely: word suggestions
        // for ":smi" would be noise. A bare ":" shows nothing until there is
        // something to search for.
        inlineEmojiQuery()?.let { query ->
            suggestionJob?.cancel()
            suggestionJob = serviceScope.launch {
                delay(EMOJI_SEARCH_DEBOUNCE_MS)
                val results = if (query.length < 2) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        emojiSearch?.search(query, limit = INLINE_EMOJI_LIMIT)
                            .orEmpty()
                            .map { it.emoji }
                    }
                }
                _uiState.update {
                    it.copy(
                        suggestions = results,
                        emojiSuggestions = emptyList(),
                        punctuationSuggestions = emptyList(),
                    )
                }
            }
            return
        }

        val typed = composing.toString()
        suggestionJob?.cancel()
        suggestionJob = serviceScope.launch {
            // Short adaptive debounce: fast bursts of keystrokes cancel the
            // job while it still sleeps here, so only the final state is
            // computed. The window tracks half the average compute cost,
            // clamped so it never becomes perceptible.
            delay((suggestionCostMs / 2).coerceIn(16L, 40L))
            val started = SystemClock.uptimeMillis()
            val (results, emojis) = withContext(Dispatchers.Default) {
                val words = engine.suggest(
                    composing = typed,
                    previousWord = previousWord,
                    avroMode = state.composer.isBengaliPhonetic,
                )
                // Precompute what a space/enter commit of this exact word would
                // resolve to, so the commit need not run the edit-distance
                // search (English) or transliteration ranking (Bengali) on the
                // main thread. commitComposing consumes it only on a typed match.
                commitResolution = when {
                    typed.isEmpty() -> null
                    state.composer.isBengaliPhonetic -> CommitResolution(
                        typed = typed,
                        isBengali = true,
                        bengaliTop = words.firstOrNull(),
                        correction = null,
                    )
                    state.settings.autocorrect && state.allowsTypingIntelligence -> CommitResolution(
                        typed = typed,
                        isBengali = false,
                        bengaliTop = null,
                        correction = engine.shouldAutocorrect(typed)?.takeIf { it != typed },
                    )
                    else -> null
                }
                if (typed.isNotEmpty()) {
                    val emojis = if (state.settings.emojiPrediction) {
                        emojiSuggester?.suggest(typed).orEmpty()
                    } else {
                        emptyList()
                    }
                    words to emojis
                } else {
                    // Next-word prediction: learned bigrams can end in an
                    // emoji ("you" → ❤️). Those belong in the emoji slot of
                    // the strip, not among the word chips.
                    val (emojiNext, wordNext) = words.partition { isEmojiCandidate(it) }
                    wordNext to if (state.settings.emojiPrediction) emojiNext else emptyList()
                }
            }
            suggestionCostMs = (suggestionCostMs + (SystemClock.uptimeMillis() - started)) / 2
            // Drop emoji the device can't draw, then apply the default skin tone
            // (filter first — the hidden set is keyed by the neutral base).
            val hidden = _uiState.value.hiddenEmoji
            val shownEmojis = emojis
                .let { list -> if (hidden.isEmpty()) list else list.filterNot { it in hidden } }
                .map { applyEmojiTone(it) }
            // Quick-punctuation rides the tail beside the word candidates, but
            // only when there are candidates to ride (otherwise the strip is
            // idle and flips to the toolbar) and no emoji prediction is actually
            // drawn in the tail (gate on the shown set, not the raw one).
            val punct = if (
                state.settings.suggestionStrip.punctuation &&
                results.isNotEmpty() &&
                shownEmojis.isEmpty()
            ) {
                PUNCTUATION_SUGGESTIONS
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    suggestions = results,
                    emojiSuggestions = shownEmojis,
                    punctuationSuggestions = punct,
                )
            }
        }
    }

    /**
     * A quick-punctuation chip in the suggestion strip was tapped. Routed
     * through the ordinary text path so it is indistinguishable from typing
     * that punctuation key — the composing word commits, auto-capitalise and
     * pending auto-space fire, and contextual-vowel handling all apply.
     */
    fun onPunctuationSuggestionTapped(mark: String) {
        onText(mark)
    }

    fun onSuggestionTapped(suggestion: String) {
        stopVoiceForManualInput()
        vibrate()
        val ic = currentInputConnection ?: return
        // Email-field completion: no composing region backs the tapped address,
        // so the partial token the user typed is removed by hand before the full
        // address is committed. Not learned — an address is not a dictionary word,
        // and no trailing space, since an email is usually the whole field.
        if (emailFieldForceActive() && '@' in suggestion) {
            val token = emailTokenBeforeCursor(ic)
            if (token.isNotEmpty()) ic.deleteSurroundingText(token.length, 0)
            ic.commitText(suggestion, 1)
            lastGestureWord = null
            lastAutocorrect = null
            _uiState.update {
                it.copy(
                    composingPreview = "",
                    suggestions = emptyList(),
                    emojiSuggestions = emptyList(),
                )
            }
            return
        }
        // After a swipe, the alternates replace the committed gesture word.
        val gestureWord = lastGestureWord
        if (composing.isEmpty() && gestureWord != null) {
            val before = ic.getTextBeforeCursor(gestureWord.length, 0)?.toString()
            if (before == gestureWord) ic.deleteSurroundingText(gestureWord.length, 0)
        }
        lastGestureWord = null
        lastAutocorrect = null

        // An emoji picked from inline search replaces the ":query" buffer
        // outright: no trailing space (emoji rarely start a new word) and
        // nothing learned, since the emoji is not a word the user typed.
        if (inlineEmojiQuery() != null) {
            ic.commitText(suggestion, 1)
            composing = StringBuilder()
            _uiState.update {
                it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList())
            }
            return
        }

        // Normally the pick lands at the end of the text and earns a trailing
        // space to start the next word. But a word resumed mid-sentence (the
        // caret moved back onto it) already has a space after it — appending
        // another would leave a double gap, so skip it when one is there.
        val nextChar = ic.getTextAfterCursor(1, 0)
        val tail = if (nextChar.isNullOrEmpty() || !nextChar[0].isWhitespace()) " " else ""
        // Commit in the case the strip is showing: a shift held over the strip
        // capitalizes the word the user is about to pick, matching the chip.
        val committed = displayCaseForShift(suggestion, _uiState.value.shiftState)
        ic.commitText(committed + tail, 1)
        // A one-shot shift is spent by the pick, the same as by a typed letter.
        consumeShift()
        // Deliberately picked from the strip — a stronger signal than a
        // word that merely got committed. Learn the base word, not the
        // shift-cased form, so caps lock never teaches "HELLO" to the lexicon.
        // A contact email is the exception: it is never learned, so it stays
        // memory-only and off the disk-backed personal dictionary even when
        // tapped from an ordinary text field.
        if ('@' !in suggestion) learn(suggestion, reinforcement = 2)
        composing = StringBuilder()
        _uiState.update { it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList()) }
        maybeAutoCapitalize()
        refreshSuggestions()
    }

    /** Spacebar drag: move the cursor one position left (-1) or right (+1). */
    fun onCursorMove(delta: Int) {
        val ic = currentInputConnection ?: return
        vibrate()
        // Mark the scrub so the caret's landing spot doesn't resume-compose the
        // word under it mid-drag (this same commit would then churn it).
        lastCaretScrubMs = SystemClock.uptimeMillis()
        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        sendDownUpKeyEvents(
            if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        )
    }

    /**
     * 2-D spacebar touchpad: move the cursor one line up (-1) or down (+1).
     * Mirrors [onCursorMove] but on the vertical axis.
     */
    fun onCursorMoveVertical(delta: Int) {
        val ic = currentInputConnection ?: return
        vibrate()
        lastCaretScrubMs = SystemClock.uptimeMillis()
        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        sendDownUpKeyEvents(
            if (delta < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        )
    }

    // ---- gesture typing ----

    /**
     * The gesture decoder's word list: bundled English plus the user's weighted
     * personal words. Cached because it only changes when a word is learned or
     * the settings app edits the lexicon, whereas a single swipe queries it many
     * times (one preview event per few pointer samples).
     */
    @Synchronized
    private fun gestureDecodeLexicon(): List<Pair<String, Int>> {
        cachedGestureLexicon?.let { return it }
        val personal = userLexicon.allWords().map { (word, count) -> word to count * 500 }
        val combined = gestureLexicon + personal
        cachedGestureLexicon = combined
        return combined
    }

    /** Drop the merged-lexicon cache after a learn or a lexicon reload. */
    @Synchronized
    private fun invalidateGestureLexicon() {
        cachedGestureLexicon = null
    }

    /** Mid-swipe: show the current best candidates without committing. */
    fun onGesturePreview(points: List<GesturePoint>, keys: List<KeyCenter>, keyWidthPx: Float) {
        val state = _uiState.value
        if (!state.settings.gestureTyping || !state.allowsTypingIntelligence) return
        if (!state.language.isEnglish || state.typingTestActive) return
        val lexicon = gestureLexicon
        if (lexicon.isEmpty() || keys.isEmpty()) return
        previewJob?.cancel()
        previewJob = serviceScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                // Same lexicon as the final decode, so the previewed word
                // never differs from the one that commits on finger-up.
                GestureDecoder(keys, keyWidthPx).decode(points, gestureDecodeLexicon())
            }
            if (candidates.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        suggestions = candidates.map { candidate -> candidate.word },
                        glideWord = candidates.first().word,
                    )
                }
            }
        }
    }

    /**
     * Decodes a swipe drawn over the letter keys and commits the best word.
     * Alternates go to the suggestion bar; tapping one replaces the word.
     */
    fun onGesture(points: List<GesturePoint>, keys: List<KeyCenter>, keyWidthPx: Float) {
        stopVoiceForManualInput()
        val state = _uiState.value
        if (!state.settings.gestureTyping || !state.allowsTypingIntelligence) return
        if (!state.language.isEnglish || state.typingTestActive) return
        val lexicon = gestureLexicon
        if (lexicon.isEmpty() || keys.isEmpty()) return

        val shiftAtGesture = state.shiftState
        previewJob?.cancel()
        suggestionJob?.cancel()
        _uiState.update { it.copy(glideWord = null) }
        suggestionJob = serviceScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                GestureDecoder(keys, keyWidthPx).decode(points, gestureDecodeLexicon())
            }
            // Debug builds only: typed content must never be logged in release.
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                android.util.Log.d(
                    "WMKeyboard",
                    "gesture shift=$shiftAtGesture candidates=${candidates.map { it.word }}",
                )
            }
            if (candidates.isEmpty()) return@launch
            val ic = currentInputConnection ?: return@launch

            commitComposing(ic, autocorrect = false)
            val word = when (shiftAtGesture) {
                ShiftState.CAPS_LOCK -> candidates.first().word.uppercase()
                ShiftState.ON -> candidates.first().word.replaceFirstChar { it.uppercase() }
                ShiftState.OFF -> candidates.first().word
            }
            // Auto-space between consecutive swiped words.
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            if (before.isNotEmpty() && !before.last().isWhitespace()) {
                ic.commitText(" ", 1)
            }
            ic.commitText(word, 1)
            learn(word)
            lastGestureWord = word
            consumeShift()
            _uiState.update {
                it.copy(suggestions = candidates.map { candidate -> candidate.word })
            }
        }
    }

    /**
     * Multi-word glide: one continuous stroke that crossed the spacebar was
     * split into a word segment per crossing. Decodes and commits them in
     * order, spacing between them, so the whole phrase lands from one swipe.
     * Only the first word honours a held shift; the alternates of the last
     * word go to the suggestion bar, so tapping one fixes the final word — the
     * same as a single glide.
     */
    fun onGestureWords(segments: List<List<GesturePoint>>, keys: List<KeyCenter>, keyWidthPx: Float) {
        stopVoiceForManualInput()
        val state = _uiState.value
        if (!state.settings.gestureTyping || !state.allowsTypingIntelligence) return
        if (!state.language.isEnglish || state.typingTestActive) return
        val lexicon = gestureLexicon
        if (lexicon.isEmpty() || keys.isEmpty() || segments.isEmpty()) return

        val shiftAtGesture = state.shiftState
        previewJob?.cancel()
        suggestionJob?.cancel()
        _uiState.update { it.copy(glideWord = null) }
        suggestionJob = serviceScope.launch {
            val decoder = GestureDecoder(keys, keyWidthPx)
            val lex = gestureDecodeLexicon()
            val ic = currentInputConnection ?: return@launch
            // Flush any composing text before the first glided word.
            commitComposing(ic, autocorrect = false)
            var lastWords: List<String> = emptyList()
            var committedAny = false
            segments.forEachIndexed { index, segment ->
                val candidates = withContext(Dispatchers.Default) { decoder.decode(segment, lex) }
                if (candidates.isEmpty()) return@forEachIndexed
                val word = if (index == 0) {
                    when (shiftAtGesture) {
                        ShiftState.CAPS_LOCK -> candidates.first().word.uppercase()
                        ShiftState.ON -> candidates.first().word.replaceFirstChar { it.uppercase() }
                        ShiftState.OFF -> candidates.first().word
                    }
                } else {
                    candidates.first().word
                }
                // Auto-space between consecutive words.
                val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                if (before.isNotEmpty() && !before.last().isWhitespace()) {
                    ic.commitText(" ", 1)
                }
                ic.commitText(word, 1)
                learn(word)
                lastGestureWord = word
                lastWords = candidates.map { it.word }
                committedAny = true
            }
            if (committedAny) {
                consumeShift()
                _uiState.update { it.copy(suggestions = lastWords) }
            }
        }
    }

    // ---- panels ----

    fun onPanelChange(panel: PanelMode) {
        // Strip mode reroutes the voice tool: no panel, just the compact
        // bar over the keys. A voice panel already open (setting flipped
        // mid-session) still closes normally below.
        if (panel == PanelMode.VOICE && _uiState.value.settings.voiceStripMode &&
            _uiState.value.panel != PanelMode.VOICE
        ) {
            toggleVoiceStrip()
            return
        }
        // Opening anything else dismisses the dictation strip.
        if (_uiState.value.voice.strip) closeVoiceStrip()
        // Panels have their own key semantics — the panel would eat the
        // modified key — so a pending latch does not survive opening one.
        clearModifiers()
        vibrate()
        // The settings app edits snippets in the same file; re-read on open.
        if (panel == PanelMode.SNIPPETS) snippetStore.reload()
        if (panel == PanelMode.CLIPBOARD) fetchLinkPreviews()
        _uiState.update {
            val closing = it.panel == panel
            val next = if (closing) PanelMode.NONE else panel
            it.copy(
                panel = next,
                // The strip is hidden behind the panel; a stale chip would
                // reappear on close pointing at text that has since moved.
                smart = null,
                textEditSelecting = false,
                emojiSearchActive = false,
                emojiQuery = "",
                emojiResults = emptyList(),
                emojiRecents = emojiUsage.recents(),
                clipboardItems = clipboardStore.items(),
                snippets = snippetStore.items(),
                dictionarySearchActive = false,
                mediaQuery = "",
                // Web/image search and translate open straight into their
                // search box (there is nothing to show yet); gif/sticker
                // open on trending. Wikipedia keeps a previous
                // article/results if it has one.
                mediaSearchActive = next == PanelMode.WEB_SEARCH || next == PanelMode.IMAGE_SEARCH ||
                    next == PanelMode.TRANSLATE || next == PanelMode.QR_GEN ||
                    (next == PanelMode.WIKIPEDIA && it.wiki !is WikiUi.Article && it.wiki !is WikiUi.SearchResults),
                mediaDownloadingId = null,
                translate = TranslateUi(),
                grammar = GrammarUi(available = GrammarChecker.available),
            )
        }
        translateJob?.cancel()
        grammarJob?.cancel()
        mediaFetchJob?.cancel()
        mediaLiveSearchJob?.cancel()
        when (_uiState.value.panel) {
            PanelMode.WEATHER -> refreshWeather()
            PanelMode.DICTIONARY -> openDictionary()
            PanelMode.GIF, PanelMode.STICKER -> refreshMedia(query = "")
            PanelMode.WEB_SEARCH -> _uiState.update {
                it.copy(webSearch = if (hasSearchKey()) WebSearchUi.Idle else WebSearchUi.NeedKey)
            }
            PanelMode.IMAGE_SEARCH -> _uiState.update {
                it.copy(imageSearch = if (hasSearchKey()) ImageSearchUi.Idle else ImageSearchUi.NeedKey)
            }
            PanelMode.GRAMMAR -> {
                currentInputConnection?.let { commitComposing(it, autocorrect = false) }
                scheduleGrammarCheck(immediate = true)
            }
            PanelMode.CURRENCY -> refreshCurrencyRates()
            PanelMode.QR_GEN -> {
                currentInputConnection?.let { commitComposing(it, autocorrect = false) }
                // Seed the editable buffer with the field text as a convenience,
                // but from here the user edits it freely — the QR no longer
                // tracks the field.
                _uiState.update { it.copy(mediaQuery = extractFieldText().trim()) }
            }
            PanelMode.AI -> _uiState.update { it.copy(ai = aiInitialState(it.settings)) }
            PanelMode.TYPING_TEST -> {
                // Flush the half-typed word first: the test swallows every
                // key from here, so a composing word would otherwise hang
                // uncommitted until the panel closed.
                currentInputConnection?.let { commitComposing(it, autocorrect = false) }
                startTypingTest()
            }
            else -> {}
        }
        if (_uiState.value.panel != PanelMode.TYPING_TEST) stopTypingTest()
        if (_uiState.value.panel == PanelMode.HANDWRITING) {
            // Flush any half-typed word so handwriting appends after it.
            currentInputConnection?.let { commitComposing(it, autocorrect = false) }
            refreshHandwritingStatus()
        } else if (hwJob != null || _uiState.value.handwriting.strokes.isNotEmpty()) {
            // Leaving the panel abandons pending ink and recognition.
            hwJob?.cancel()
            hwJob = null
            hwGeneration++
            _uiState.update {
                it.copy(handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false))
            }
        }
        if (_uiState.value.panel == PanelMode.VOICE) {
            // Opening the tool is the intent to speak: listen right away.
            startVoice()
        } else {
            cancelVoice()
        }
    }

    // ---- voice input ----

    /** Recognition language follows the input mode, like handwriting. */
    private fun voiceLanguageTag(): String =
        _uiState.value.language.localeTag

    /** Mic button on the voice panel/strip: start, or finish the session. */
    fun onVoiceToggle() {
        vibrate()
        when (_uiState.value.voice.status) {
            VoiceStatus.LISTENING -> {
                voiceStopRequested = true
                _uiState.update { it.copy(voice = it.voice.copy(status = VoiceStatus.FINISHING)) }
                voiceEngine.finish()
            }
            VoiceStatus.FINISHING -> {}
            else -> {
                voiceSilentRetries = 0
                startVoice()
            }
        }
    }

    /** IMEs cannot show permission dialogs; bounce through the trampoline. */
    fun onVoicePermissionRequest() {
        startActivity(
            Intent(this, MicPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Starts one dictation session. Partial results stream into the editor
     * as composing text; the final result commits and is learned like a
     * typed word. Dictated text arrives in final script, so the Avro
     * transliteration pipeline is bypassed entirely.
     */
    /** Whether a dictation surface is still up to receive the next utterance. */
    private fun voiceSessionAlive(): Boolean =
        _uiState.value.panel == PanelMode.VOICE || _uiState.value.voice.strip

    private fun startVoice() {
        cancelVoice()
        voiceStopRequested = false
        val tag = voiceLanguageTag()
        fun fail(status: VoiceStatus, message: String? = null) {
            _uiState.update {
                it.copy(
                    voice = it.voice.copy(
                        status = status, languageTag = tag, errorMessage = message,
                        partial = "", level = 0f,
                    ),
                )
            }
        }
        if (_uiState.value.secureField) {
            // The panel shows its own notice; never open the mic here.
            fail(VoiceStatus.IDLE)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail(VoiceStatus.NEED_PERMISSION)
            return
        }
        if (!voiceEngine.isAvailable()) {
            fail(VoiceStatus.UNAVAILABLE)
            return
        }
        val ic = currentInputConnection ?: return
        // Flush the half-typed word so dictation appends after it.
        commitComposing(ic, autocorrect = false)
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        voiceNeedsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val generation = ++voiceGeneration
        // Offline-model chip: check once per language, not per utterance
        // (continuous mode restarts sessions constantly).
        val modelKnown = _uiState.value.voice.modelState != VoiceModelState.UNKNOWN &&
            _uiState.value.voice.languageTag == tag
        _uiState.update {
            it.copy(
                voice = it.voice.copy(
                    status = VoiceStatus.LISTENING, languageTag = tag,
                    partial = "", level = 0f, errorMessage = null,
                ),
            )
        }
        if (!modelKnown) refreshVoiceModelState(tag)
        voiceEngine.start(
            tag,
            object : VoiceInputEngine.Listener {
                override fun onListening() {}

                override fun onLevel(level: Float) {
                    if (generation != voiceGeneration) return
                    // Quantized so the pulse ring doesn't force a state
                    // update (and recomposition) per rms callback.
                    val quantized = (level * 8).toInt() / 8f
                    _uiState.update {
                        if (it.voice.level == quantized) it
                        else it.copy(voice = it.voice.copy(level = quantized))
                    }
                }

                override fun onPartial(text: String) {
                    if (generation != voiceGeneration) return
                    currentInputConnection?.setComposingText(spacedVoiceText(text), 1)
                    _uiState.update { it.copy(voice = it.voice.copy(partial = text)) }
                }

                override fun onFinal(text: String) {
                    if (generation != voiceGeneration) return
                    voiceGeneration++
                    val settings = _uiState.value.settings
                    val processed = if (settings.voiceSpokenPunctuation) {
                        VoicePunctuation.apply(text, tag)
                    } else {
                        text
                    }
                    val spaced = spacedVoiceText(processed)
                    currentInputConnection?.let { connection ->
                        connection.commitText(spaced, 1)
                        learn(processed)
                        lastVoiceCommit = spaced
                    }
                    voiceSilentRetries = 0
                    if (settings.voiceContinuous && !voiceStopRequested && voiceSessionAlive()) {
                        // Continuous dictation: chain straight into the next
                        // utterance until the user stops or leaves. Deferred
                        // to the next looper tick — starting a new recognizer
                        // synchronously from inside the old one's onResults
                        // callback races its teardown and spuriously fires
                        // onError (ERROR_CLIENT) on some OEM builds even
                        // though this utterance already succeeded.
                        _uiState.update { it.copy(voice = it.voice.copy(partial = "", level = 0f, canUndo = true)) }
                        serviceScope.launch(Dispatchers.Main) { startVoice() }
                    } else {
                        _uiState.update {
                            it.copy(
                                voice = it.voice.copy(
                                    status = VoiceStatus.IDLE, partial = "", level = 0f, canUndo = true,
                                ),
                            )
                        }
                    }
                }

                override fun onError(kind: VoiceInputEngine.ErrorKind) {
                    if (generation != voiceGeneration) return
                    voiceGeneration++
                    // A network drop mid-utterance keeps whatever was heard.
                    currentInputConnection?.finishComposingText()
                    // Silence in continuous mode restarts quietly — but not
                    // forever, so an abandoned open mic winds down.
                    if (kind == VoiceInputEngine.ErrorKind.NO_SPEECH &&
                        _uiState.value.settings.voiceContinuous &&
                        !voiceStopRequested && voiceSessionAlive() &&
                        voiceSilentRetries < 2
                    ) {
                        voiceSilentRetries++
                        serviceScope.launch(Dispatchers.Main) { startVoice() }
                        return
                    }
                    val (status, message) = when (kind) {
                        VoiceInputEngine.ErrorKind.NO_SPEECH -> VoiceStatus.IDLE to null
                        VoiceInputEngine.ErrorKind.PERMISSION -> VoiceStatus.NEED_PERMISSION to null
                        VoiceInputEngine.ErrorKind.NETWORK ->
                            VoiceStatus.ERROR to "Network problem — check your connection and try again."
                        VoiceInputEngine.ErrorKind.BUSY ->
                            VoiceStatus.ERROR to "The microphone is busy — close other apps using it and try again."
                        VoiceInputEngine.ErrorKind.LANGUAGE ->
                            VoiceStatus.ERROR to "Speech recognition doesn't support this language on this device."
                        VoiceInputEngine.ErrorKind.OTHER ->
                            VoiceStatus.ERROR to "Speech recognition failed — try again."
                    }
                    _uiState.update {
                        it.copy(
                            voice = it.voice.copy(
                                status = status, languageTag = tag, errorMessage = message,
                                partial = "", level = 0f,
                            ),
                        )
                    }
                }
            },
        )
    }

    /** Leading space when dictation starts mid-text, so words never glue on. */
    private fun spacedVoiceText(text: String): String =
        if (voiceNeedsSpace && text.firstOrNull()?.isLetterOrDigit() == true) " $text" else text

    /**
     * Abandons any running dictation: the mic is released and the partial
     * already on screen stays as committed text (the user said it — losing
     * it on a panel switch would be worse than keeping it).
     */
    private fun cancelVoice() {
        val status = _uiState.value.voice.status
        voiceGeneration++
        if (status != VoiceStatus.LISTENING && status != VoiceStatus.FINISHING) return
        voiceEngine.cancel()
        currentInputConnection?.finishComposingText()
        _uiState.update {
            it.copy(voice = it.voice.copy(status = VoiceStatus.IDLE, partial = "", level = 0f))
        }
    }

    /**
     * Manual input (keys, swipes, suggestion taps) during dictation ends the
     * utterance: partial results are cumulative, so keystrokes woven into
     * the composing region would corrupt it. The partial is kept; the
     * panel/strip stays open to resume with a mic tap.
     */
    private fun stopVoiceForManualInput() {
        val status = _uiState.value.voice.status
        if (status == VoiceStatus.LISTENING || status == VoiceStatus.FINISHING) {
            voiceStopRequested = true
            cancelVoice()
        }
    }

    /** Voice tool tap in strip mode: dictate over the keys, no panel. */
    private fun toggleVoiceStrip() {
        if (_uiState.value.voice.strip) {
            closeVoiceStrip()
            return
        }
        vibrate()
        if (_uiState.value.secureField) {
            Toast.makeText(this, "Voice typing is unavailable in password fields", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.update { it.copy(voice = it.voice.copy(strip = true)) }
        voiceSilentRetries = 0
        startVoice()
    }

    private fun closeVoiceStrip() {
        if (!_uiState.value.voice.strip) return
        vibrate()
        cancelVoice()
        _uiState.update { it.copy(voice = it.voice.copy(strip = false, canUndo = false)) }
    }

    /**
     * Asks the on-device recognizer where [tag]'s model stands, for the
     * panel's offline-model chip. UNKNOWN (chip hidden) below API 33 or
     * when the language can't run on-device at all.
     */
    private fun refreshVoiceModelState(tag: String) {
        voiceEngine.checkOnDeviceModel(tag) { result ->
            val state = when (result) {
                VoiceInputEngine.ModelCheckResult.INSTALLED -> VoiceModelState.INSTALLED
                VoiceInputEngine.ModelCheckResult.DOWNLOADABLE -> VoiceModelState.DOWNLOADABLE
                VoiceInputEngine.ModelCheckResult.PENDING -> VoiceModelState.DOWNLOADING
                VoiceInputEngine.ModelCheckResult.UNSUPPORTED -> VoiceModelState.UNKNOWN
            }
            _uiState.update {
                if (it.voice.languageTag != tag) it
                else it.copy(voice = it.voice.copy(modelState = state, modelProgress = -1))
            }
        }
    }

    /** Offline-model chip on the voice panel: download the active language. */
    fun onVoiceModelDownload() {
        vibrate()
        val tag = _uiState.value.voice.languageTag
        _uiState.update {
            it.copy(voice = it.voice.copy(modelState = VoiceModelState.DOWNLOADING, modelProgress = -1))
        }
        voiceEngine.downloadModel(
            tag,
            object : VoiceInputEngine.ModelDownloadCallback {
                override fun onProgress(percent: Int) {
                    _uiState.update {
                        if (it.voice.languageTag != tag) it
                        else it.copy(voice = it.voice.copy(modelProgress = percent))
                    }
                }

                override fun onSuccess() {
                    _uiState.update {
                        if (it.voice.languageTag != tag) it
                        else it.copy(voice = it.voice.copy(modelState = VoiceModelState.INSTALLED, modelProgress = -1))
                    }
                }

                override fun onScheduled() {
                    // Queued by the system (Wi-Fi / idle) or fire-and-forget
                    // on API 33. Stays "downloading"; reopening the panel
                    // re-checks and settles the state.
                }

                override fun onError() {
                    Toast.makeText(
                        this@WMKeyboardService,
                        "Offline model download failed — try again later",
                        Toast.LENGTH_SHORT,
                    ).show()
                    _uiState.update {
                        if (it.voice.languageTag != tag) it
                        else it.copy(voice = it.voice.copy(modelState = VoiceModelState.DOWNLOADABLE, modelProgress = -1))
                    }
                }
            },
        )
    }

    /** Undo chip: removes the last dictated utterance if still at the cursor. */
    fun onVoiceUndo() {
        vibrate()
        val last = lastVoiceCommit
        lastVoiceCommit = null
        _uiState.update { it.copy(voice = it.voice.copy(canUndo = false)) }
        if (last == null) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(last.length, 0)?.toString()
        if (before == last) ic.deleteSurroundingText(last.length, 0)
    }

    // ---- handwriting ----

    private fun hwLanguageTag(): String = HandwritingModels.tagForLangId(_uiState.value.language.id)

    /**
     * Letter-area swipes are drawing handwriting (rather than gliding a word):
     * full build, gesture typing on with the swipe action set to HANDWRITE, on
     * the letter layer with no panel open. Model readiness is checked
     * separately so this can also gate the "download the model" hint.
     */
    private fun keyboardHandwriteActive(state: KeyboardUiState): Boolean =
        BuildConfig.ENABLE_ML_KIT_HANDWRITING &&
            state.settings.gestureTyping &&
            state.settings.letterSwipeAction == LetterSwipeAction.HANDWRITE &&
            state.layoutMode == LayoutMode.LETTERS &&
            state.panel == PanelMode.NONE

    /**
     * A swipe finished on the key grid while [keyboardHandwriteActive]. With
     * the model ready it feeds the same pipeline as the handwriting panel;
     * otherwise it points the user at the model download (once) instead of
     * silently gliding a word they didn't ask for.
     */
    fun onKeyboardHandwritingStroke(stroke: HwStroke, canvasSize: IntSize) {
        val state = _uiState.value
        if (!keyboardHandwriteActive(state)) return
        if (state.handwriting.status != HandwritingStatus.READY) {
            // CHECKING/DOWNLOADING resolve on their own; only nag once the
            // absence is confirmed.
            if (state.handwriting.status == HandwritingStatus.NEED_MODEL ||
                state.handwriting.status == HandwritingStatus.ERROR
            ) {
                if (!hwModelHintShown) {
                    hwModelHintShown = true
                    Toast.makeText(
                        this,
                        "Download a handwriting model in Settings → Handwriting to write on the keyboard.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                refreshHandwritingStatus()
            }
            return
        }
        onHandwritingStroke(stroke, canvasSize)
    }

    /**
     * Throw away handwriting ink drawn on the keys — used when the letter
     * layer (and with it the on-keyboard writing surface) goes away, so the
     * strokes don't reappear when the user comes back to the letters.
     */
    private fun dropKeyboardHandwritingInk() {
        if (_uiState.value.handwriting.strokes.isEmpty()) return
        hwJob?.cancel()
        hwGeneration++
        _uiState.update {
            it.copy(handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false))
        }
    }

    /**
     * Re-checks whether the active language's recognition model is on the
     * device, resetting the panel (ink, errors) in the process.
     */
    private fun refreshHandwritingStatus() {
        hwJob?.cancel()
        hwGeneration++
        val tag = hwLanguageTag()
        _uiState.update {
            it.copy(handwriting = HandwritingUi(status = HandwritingStatus.CHECKING, languageTag = tag))
        }
        serviceScope.launch {
            val downloaded = HandwritingModels.isDownloaded(tag)
            _uiState.update {
                if (it.handwriting.languageTag != tag) return@update it
                it.copy(
                    handwriting = it.handwriting.copy(
                        status = if (downloaded) HandwritingStatus.READY else HandwritingStatus.NEED_MODEL,
                    ),
                )
            }
        }
    }

    /** Download button on the panel: fetch the active language's model. */
    fun onHandwritingDownload() {
        vibrate()
        val tag = _uiState.value.handwriting.languageTag
        _uiState.update {
            it.copy(handwriting = it.handwriting.copy(status = HandwritingStatus.DOWNLOADING, errorMessage = null))
        }
        serviceScope.launch {
            val result = runCatching { HandwritingModels.download(tag) }
            _uiState.update {
                if (it.handwriting.languageTag != tag) return@update it
                it.copy(
                    handwriting = if (result.isSuccess) {
                        it.handwriting.copy(status = HandwritingStatus.READY)
                    } else {
                        it.handwriting.copy(
                            status = HandwritingStatus.ERROR,
                            errorMessage = "Download failed — check your connection and try again.",
                        )
                    },
                )
            }
        }
    }

    /** A stroke was finished on the canvas; recognize after a short pause. */
    fun onHandwritingStroke(stroke: HwStroke, canvasSize: IntSize) {
        val state = _uiState.value
        if ((state.panel != PanelMode.HANDWRITING && !keyboardHandwriteActive(state)) ||
            state.handwriting.status != HandwritingStatus.READY
        ) {
            return
        }
        hwCanvasSize = canvasSize
        _uiState.update {
            it.copy(
                handwriting = it.handwriting.copy(strokes = it.handwriting.strokes + stroke, recognizing = false),
                // Stale candidates from the previous word must not be
                // tappable while new ink is on the canvas.
                suggestions = emptyList(),
                emojiSuggestions = emptyList(),
            )
        }
        scheduleHandwritingRecognition()
    }

    /** Undo button: drop the last stroke and re-recognize what remains. */
    fun onHandwritingUndo() {
        vibrate()
        hwJob?.cancel()
        hwGeneration++
        _uiState.update {
            it.copy(handwriting = it.handwriting.copy(strokes = it.handwriting.strokes.dropLast(1), recognizing = false))
        }
        if (_uiState.value.handwriting.strokes.isNotEmpty()) scheduleHandwritingRecognition()
    }

    private fun scheduleHandwritingRecognition() {
        hwJob?.cancel()
        val generation = ++hwGeneration
        hwJob = serviceScope.launch {
            delay(handwritingRecognitionDelayMs())
            recognizeAndCommitHandwriting(generation)
        }
    }

    /**
     * Quiet time after the last stroke before recognizing and committing.
     * Bengali glyphs are built from several strokes — conjuncts, the matra,
     * vowel signs — and the writer lifts the finger between them; the global
     * default is short enough that a natural mid-glyph pause commits a
     * half-written character. Give Bengali a higher floor so a comfortable
     * inter-stroke pause never triggers an early commit, while still honouring
     * a longer pause the user set for themselves.
     */
    private fun handwritingRecognitionDelayMs(): Long {
        val base = _uiState.value.settings.handwritingCommitDelayMs.toLong()
        return if (_uiState.value.handwriting.languageTag == "bn") {
            maxOf(base, BENGALI_HW_MIN_COMMIT_DELAY_MS)
        } else {
            base
        }
    }

    /**
     * Runs ML Kit recognition over the accumulated ink and commits the top
     * candidate. Alternates go to the suggestion strip; tapping one
     * replaces the committed word (same mechanics as gesture typing).
     * A [generation] mismatch afterwards means new ink arrived or the
     * panel closed while recognizing — the result is stale, drop it.
     */
    private suspend fun recognizeAndCommitHandwriting(generation: Int) {
        val state = _uiState.value
        val strokes = state.handwriting.strokes
        if (strokes.isEmpty()) return
        val tag = state.handwriting.languageTag
        _uiState.update { it.copy(handwriting = it.handwriting.copy(recognizing = true)) }
        val ic = currentInputConnection
        val preContext = ic?.getTextBeforeCursor(20, 0)?.toString().orEmpty()
        val result = runCatching {
            hwRecognizer.recognize(
                tag = tag,
                strokes = strokes,
                preContext = preContext,
                writingAreaWidth = hwCanvasSize.width.toFloat(),
                writingAreaHeight = hwCanvasSize.height.toFloat(),
            )
        }
        if (generation != hwGeneration ||
            (_uiState.value.panel != PanelMode.HANDWRITING && !keyboardHandwriteActive(_uiState.value))
        ) {
            return
        }

        val candidates = result.getOrNull()
        if (candidates == null) {
            // Model gone mid-session (deleted from settings) or ML Kit
            // failure: re-check instead of silently eating ink forever.
            refreshHandwritingStatus()
            return
        }
        if (candidates.isEmpty()) {
            _uiState.update {
                it.copy(handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false))
            }
            return
        }

        var word = candidates.first()
        val settings = state.settings
        // Sentence-start capitalization, English only — Bengali has no case.
        if (tag == "en-US" && settings.autoCapitalize && !state.secureField &&
            word.firstOrNull()?.isLowerCase() == true && shouldAutoCapitalize()
        ) {
            word = word.replaceFirstChar { it.uppercase() }
        }
        // Space between consecutively written words, but never before
        // punctuation ("," "." "?" …).
        val needsSpace = settings.handwritingAutoSpace &&
            word.firstOrNull()?.isLetterOrDigit() == true &&
            preContext.isNotEmpty() && !preContext.last().isWhitespace()
        val connection = currentInputConnection ?: run {
            // Input connection lost between recognition and commit. Clear the
            // spinner and drop the ink instead of leaving the panel stuck in
            // "recognizing" with stale strokes that would be re-recognized
            // together with the next glyph.
            _uiState.update {
                it.copy(handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false))
            }
            return
        }
        connection.commitText(if (needsSpace) " $word" else word, 1)
        learn(word)
        lastGestureWord = word
        _uiState.update {
            it.copy(
                handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false),
                suggestions = if (state.secureField) emptyList() else candidates,
            )
        }
    }

    // ---- tools: flashlight, undo/redo, weather ----

    /** Camera with a flash unit, or null when the device has none. */
    private var torchCameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _uiState.update { it.copy(torchOn = enabled) }
            }
        }
    }

    fun onFlashlightToggle() {
        vibrate()
        if (torchCameraId == null) {
            Toast.makeText(this, "This device has no flashlight", Toast.LENGTH_SHORT).show()
            return
        }
        setTorch(!_uiState.value.torchOn)
    }

    private fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        // Fails if another app holds the camera; the torch callback keeps
        // the icon truthful either way.
        runCatching {
            (getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, on)
        }
    }

    /**
     * Sends the editor's undo/redo keyboard shortcut (Ctrl+Z / Ctrl+Shift+Z
     * or Ctrl+Y per settings). Editors without shortcut support ignore it —
     * the IME has no way to reach their private undo stacks.
     */
    fun onUndoRedo(redo: Boolean) {
        val ic = currentInputConnection ?: return
        vibrate()
        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        val settings = _uiState.value.settings
        val code = if (redo && settings.redoUsesCtrlY) KeyEvent.KEYCODE_Y else KeyEvent.KEYCODE_Z
        var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (redo && !settings.redoUsesCtrlY) {
            meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        val time = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** Incognito tool: pause learning + clipboard capture with one tap. */
    fun onIncognitoToggle() {
        vibrate()
        val state = _uiState.value
        // The field itself asked for incognito, so the switch has nothing to
        // turn off — say so instead of leaving the tool looking stuck on.
        if (state.fieldIncognito) {
            Toast.makeText(
                this,
                "This field is always incognito",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val next = !state.settings.incognito
        Toast.makeText(
            this,
            if (next) "Incognito on — typing is not learned" else "Incognito off",
            Toast.LENGTH_SHORT,
        ).show()
        serviceScope.launch { settingsRepository.setIncognito(next) }
    }

    fun onAutocorrectToggle() {
        vibrate()
        val next = !_uiState.value.settings.autocorrect
        Toast.makeText(
            this,
            if (next) "Autocorrect on" else "Autocorrect off",
            Toast.LENGTH_SHORT,
        ).show()
        serviceScope.launch { settingsRepository.setAutocorrect(next) }
    }

    fun onThemeSelect(id: String) {
        vibrate()
        serviceScope.launch { settingsRepository.setKeyboardThemeId(id) }
    }

    /** Sound & haptics quick panel writes straight into the shared settings. */
    fun onSoundHaptic(action: SoundHapticAction) {
        serviceScope.launch {
            when (action) {
                is SoundHapticAction.Haptics -> settingsRepository.setHapticFeedback(action.on)
                is SoundHapticAction.HapticStyleChange -> settingsRepository.setHapticStyle(action.style)
                is SoundHapticAction.HapticAmplitude -> settingsRepository.setHapticAmplitude(action.amplitude)
                is SoundHapticAction.HapticDuration -> settingsRepository.setHapticStrengthMs(action.durationMs)
                is SoundHapticAction.Sound -> settingsRepository.setKeySound(action.on)
                is SoundHapticAction.SoundStyleChange -> settingsRepository.setKeySoundStyle(action.style)
                is SoundHapticAction.SoundVolume -> settingsRepository.setKeySoundVolume(action.volume)
            }
        }
        // Preview the result right away so the user can dial it in by feel.
        // The DataStore write above lands asynchronously, so previews pass
        // the new value explicitly instead of re-reading settings. The
        // player's debounce keeps slider drags from buzz-sawing the motor.
        val settings = _uiState.value.settings
        when (action) {
            is SoundHapticAction.Haptics -> if (action.on) {
                HapticPlayer.preview(
                    this, settings.hapticStyle, settings.hapticAmplitude, settings.hapticStrengthMs,
                    inputRootView,
                )
            }
            is SoundHapticAction.HapticStyleChange -> HapticPlayer.preview(
                this, action.style, settings.hapticAmplitude, settings.hapticStrengthMs, inputRootView,
            )
            is SoundHapticAction.HapticAmplitude -> HapticPlayer.preview(
                this, settings.hapticStyle, action.amplitude, settings.hapticStrengthMs, inputRootView,
            )
            is SoundHapticAction.HapticDuration -> HapticPlayer.preview(
                this, settings.hapticStyle, settings.hapticAmplitude, action.durationMs, inputRootView,
            )
            is SoundHapticAction.Sound -> if (action.on) playKeySound(force = true)
            is SoundHapticAction.SoundStyleChange -> playKeySound(style = action.style, force = true)
            is SoundHapticAction.SoundVolume -> playKeySound(volume = action.volume, force = true)
        }
    }

    private var weatherJob: Job? = null

    /**
     * Fetches current conditions when the weather panel opens; cached for
     * 15 minutes unless [force]d from the refresh button.
     */
    private fun refreshWeather(force: Boolean = false) {
        val settings = _uiState.value.settings
        val latitude = settings.weatherLatitude
        val longitude = settings.weatherLongitude
        if (latitude == null || longitude == null) {
            _uiState.update { it.copy(weather = WeatherUi.NoLocation) }
            return
        }
        val cached = (_uiState.value.weather as? WeatherUi.Ready)?.info
        if (!force && cached != null &&
            System.currentTimeMillis() - cached.fetchedAtMillis < WEATHER_CACHE_MS
        ) {
            return
        }
        weatherJob?.cancel()
        _uiState.update { it.copy(weather = WeatherUi.Loading) }
        weatherJob = serviceScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { WeatherClient.fetch(latitude, longitude) }.getOrNull()
            }
            _uiState.update {
                it.copy(weather = if (info != null) WeatherUi.Ready(info) else WeatherUi.Error)
            }
        }
    }

    // ---- wikipedia tool ----

    private var wikiJob: Job? = null

    /** The results an open article came from, for the back arrow. */
    private var wikiLastResults: WikiUi.SearchResults? = null

    private fun runWikiSearch(query: String) {
        if (query.isBlank()) return
        wikiJob?.cancel()
        _uiState.update { it.copy(wiki = WikiUi.Loading) }
        wikiJob = serviceScope.launch {
            val lang = _uiState.value.settings.wikiLanguage
            val result = withContext(Dispatchers.IO) {
                runCatching { WikipediaClient.search(query, lang) }
            }
            _uiState.update {
                it.copy(
                    wiki = result.fold(
                        onSuccess = { r -> WikiUi.SearchResults(r, query) },
                        onFailure = { e -> WikiUi.Error(e.message ?: "Search failed") },
                    ),
                )
            }
        }
    }

    /** Search result or article link tapped: load that article's summary. */
    fun onWikiOpen(title: String) {
        vibrate()
        (_uiState.value.wiki as? WikiUi.SearchResults)?.let { wikiLastResults = it }
        wikiJob?.cancel()
        _uiState.update { it.copy(wiki = WikiUi.Loading) }
        wikiJob = serviceScope.launch {
            val lang = _uiState.value.settings.wikiLanguage
            val result = withContext(Dispatchers.IO) {
                runCatching { WikipediaClient.summary(title, lang) }
            }
            _uiState.update {
                it.copy(
                    wiki = result.fold(
                        onSuccess = { s ->
                            WikiUi.Article(s, canGoBack = wikiLastResults != null)
                        },
                        onFailure = { e -> WikiUi.Error(e.message ?: "Couldn't load the article") },
                    ),
                )
            }
        }
    }

    fun onWikiBack() {
        vibrate()
        _uiState.update { it.copy(wiki = wikiLastResults ?: WikiUi.Idle) }
    }

    /** Links tab opened for the first time: fetch the article's links. */
    fun onWikiLoadLinks() {
        val article = _uiState.value.wiki as? WikiUi.Article ?: return
        if (article.links != null || article.loadingExtra) return
        _uiState.update { it.copy(wiki = article.copy(loadingExtra = true)) }
        serviceScope.launch {
            val lang = _uiState.value.settings.wikiLanguage
            val result = withContext(Dispatchers.IO) {
                runCatching { WikipediaClient.links(article.summary.title, lang) }
            }
            _uiState.update { state ->
                val current = state.wiki as? WikiUi.Article ?: return@update state
                if (current.summary.title != article.summary.title) return@update state
                state.copy(
                    wiki = current.copy(
                        links = result.getOrDefault(emptyList()),
                        loadingExtra = false,
                    ),
                )
            }
        }
    }

    /** Full-article tab opened for the first time: fetch the plain text. */
    fun onWikiLoadFull() {
        val article = _uiState.value.wiki as? WikiUi.Article ?: return
        if (article.fullText != null || article.loadingExtra) return
        _uiState.update { it.copy(wiki = article.copy(loadingExtra = true)) }
        serviceScope.launch {
            val lang = _uiState.value.settings.wikiLanguage
            val result = withContext(Dispatchers.IO) {
                runCatching { WikipediaClient.fullText(article.summary.title, lang) }
            }
            _uiState.update { state ->
                val current = state.wiki as? WikiUi.Article ?: return@update state
                if (current.summary.title != article.summary.title) return@update state
                state.copy(
                    wiki = current.copy(
                        fullText = result.getOrDefault(""),
                        loadingExtra = false,
                    ),
                )
            }
        }
    }

    // ---- currency tool ----

    private var currencyJob: Job? = null

    /** Rates refresh at most every cache-TTL setting (they update daily upstream anyway). */
    private fun refreshCurrencyRates(force: Boolean = false) {
        val current = _uiState.value.currency
        val ttlMs = _uiState.value.settings.currencyCacheHours * 60L * 60L * 1000L
        if (!force && current is CurrencyUi.Ready &&
            System.currentTimeMillis() - current.fetchedAtMs < ttlMs
        ) {
            return
        }
        currencyJob?.cancel()
        _uiState.update { it.copy(currency = CurrencyUi.Loading) }
        currencyJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CurrencyClient.fetchRates() }
            }
            _uiState.update {
                it.copy(
                    currency = result.fold(
                        onSuccess = { r -> CurrencyUi.Ready(r, System.currentTimeMillis()) },
                        onFailure = {
                            CurrencyUi.Error("Couldn't fetch exchange rates — check your connection.")
                        },
                    ),
                )
            }
            // A "150 usd" chip may be sitting on the strip waiting for these
            // rates to arrive before it can show an amount.
            if (_uiState.value.smart?.pending == true) refreshSmartSuggestion()
        }
    }

    fun onCurrencyPairChange(from: String, to: String) {
        vibrate()
        serviceScope.launch { settingsRepository.setCurrencyPair(from, to) }
    }

    // ---- QR generator tool ----

    /** Renders the panel's typed QR content at the configured size and commits the PNG. */
    fun onQrSend() {
        val state = _uiState.value
        val content = state.mediaQuery
        if (content.isBlank()) return
        vibrate()
        serviceScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = QrCodeGen.bitmap(
                        content, state.settings.qrSizePx, state.settings.qrEcc.name,
                    ) ?: error("Too much text for one QR code")
                    val dir = File(cacheDir, "media").apply { mkdirs() }
                    val target = File(dir, "qr_${content.hashCode().toUInt()}.png")
                    target.outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    target
                }.getOrNull()
            }
            if (file == null) {
                Toast.makeText(
                    this@WMKeyboardService,
                    "Couldn't generate the QR code",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            saveToGalleryIfEnabled(
                file,
                MediaMime.PNG,
                state.settings.qrSaveToGallery,
                "QR",
            )
            commitImageFile(file, MediaMime.PNG, state.settings.qrSendMode)
        }
    }

    /**
     * Copies a produced image into Pictures/WM Keyboard when the tool's
     * save option is on. Runs off the main thread; a failure only costs the
     * gallery copy, never the send that follows.
     */
    private fun saveToGalleryIfEnabled(
        file: File,
        mimeType: String,
        enabled: Boolean,
        namePrefix: String,
    ) {
        if (!enabled) return
        if (!GallerySaver.canSave(this)) {
            // Pre-Q needs WRITE_EXTERNAL_STORAGE, which an IME cannot ask
            // for itself — bounce through the trampoline and let the user
            // retry once it is granted.
            startActivity(
                Intent(this, StoragePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val name = "${namePrefix}_$stamp.${MediaMime.extension(mimeType)}"
        serviceScope.launch(Dispatchers.IO) {
            val saved = GallerySaver.save(this@WMKeyboardService, file, mimeType, name) != null
            if (!saved) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@WMKeyboardService,
                        "Couldn't save to the gallery",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // ---- symbols / calculator / converter inserts ----

    /**
     * Modes panel: apply a mode manually (null = back to automatic). The
     * pick sticks for the current app and resets on the next app switch.
     */
    fun onModeSelect(id: String?) {
        vibrate()
        manualModeId = id
        val base = baseSettings ?: return
        val mode = resolveKeyboardMode(
            base.keyboardModes, currentPackage, currentModeFields, manualModeId,
        )
        _uiState.update {
            it.copy(
                settings = base.applyMode(mode),
                activeModeId = mode?.id,
                // The mode brings its own default set; drop the session pick.
                activeSymbolSetId = null,
            )
        }
    }

    /** Symbol row's picker chip: switch the visible set. */
    fun onSymbolSetSelect(id: String) {
        vibrate()
        val state = _uiState.value
        _uiState.update { it.copy(activeSymbolSetId = id) }
        // While a mode prescribes its own set list the pick is session-only —
        // it shouldn't rewrite the global row's default set.
        val modeSets = baseSettings?.keyboardModes
            ?.firstOrNull { it.id == state.activeModeId }?.symbolSetIds
        if (modeSets == null) {
            serviceScope.launch { settingsRepository.setSymbolRowActiveSet(id) }
        }
    }

    /** Symbol cell tapped: type it and remember it under Recents. */
    fun onSymbolInsert(symbol: String) {
        vibrate()
        val ic = currentInputConnection ?: return
        commitComposing(ic, autocorrect = false)
        ic.commitText(symbol, 1)
        serviceScope.launch { settingsRepository.addSymbolRecent(symbol) }
    }

    /** Insert chip on the calculator/converter/generator panels. */
    fun onToolTextInsert(text: String) {
        if (text.isEmpty()) return
        vibrate()
        val ic = currentInputConnection ?: return
        commitComposing(ic, autocorrect = false)
        ic.commitText(text, 1)
    }

    // ---- password generator tool ----

    /** Panel controls persist straight into settings (they're the defaults). */
    fun onPwSetting(action: PwSettingAction) {
        vibrate()
        serviceScope.launch {
            when (action) {
                is PwSettingAction.PassphraseMode -> settingsRepository.setPwPassphraseMode(action.on)
                is PwSettingAction.Length -> settingsRepository.setPwLength(action.value)
                is PwSettingAction.Upper -> settingsRepository.setPwUppercase(action.on)
                is PwSettingAction.Digits -> settingsRepository.setPwDigits(action.on)
                is PwSettingAction.Symbols -> settingsRepository.setPwSymbols(action.on)
                is PwSettingAction.ExcludeAmbiguous ->
                    settingsRepository.setPwExcludeAmbiguous(action.on)
                is PwSettingAction.Words -> settingsRepository.setPpWordCount(action.value)
                is PwSettingAction.Separator -> settingsRepository.setPpSeparator(action.value)
                is PwSettingAction.Capitalize -> settingsRepository.setPpCapitalize(action.on)
                is PwSettingAction.IncludeDigit -> settingsRepository.setPpIncludeDigit(action.on)
            }
        }
    }

    // ---- typing speed test ----

    /**
     * Drives the elapsed clock and the once-a-second sampler while a run is
     * live. The panel renders [TypingTestUi.elapsedMs] rather than reading
     * the system clock itself, so a recomposition can never disagree with
     * the score.
     */
    private var typingTestJob: Job? = null

    /** Deals a fresh prompt from the current settings and arms the run. */
    private fun startTypingTest() {
        typingTestJob?.cancel()
        typingTestJob = null
        val settings = _uiState.value.settings
        val words = buildTypingPrompt(
            mode = settings.typingTestMode,
            duration = settings.typingTestDuration,
            wordCount = settings.typingTestWordCount,
            punctuation = settings.typingTestPunctuation,
            numbers = settings.typingTestNumbers,
        )
        // Force shift off: the prompt is lowercase, and a field's auto-cap
        // would otherwise uppercase the first keystroke into a miss.
        _uiState.update {
            it.copy(typingTest = TypingTestUi(words = words), shiftState = ShiftState.OFF)
        }
    }

    /** Cancels the clock; used when the panel closes mid-run. */
    private fun stopTypingTest() {
        typingTestJob?.cancel()
        typingTestJob = null
    }

    /**
     * Starts the clock on the first keystroke — not when the panel opens.
     * Otherwise the seconds spent reading the prompt would count against
     * the score.
     */
    private fun armTypingClock() {
        if (typingTestJob != null) return
        val startedAt = System.currentTimeMillis()
        _uiState.update { it.copy(typingTest = it.typingTest.copy(startedAtMs = startedAt)) }
        typingTestJob = serviceScope.launch {
            var nextSecond = 1
            while (isActive) {
                delay(100)
                val state = _uiState.value
                val test = state.typingTest
                if (state.panel != PanelMode.TYPING_TEST || test.result != null) return@launch
                val elapsed = System.currentTimeMillis() - startedAt
                val limit = state.settings.typingTestDuration * 1000L
                val timed = state.settings.typingTestMode == TypingTestMode.TIME
                val capped = if (timed) elapsed.coerceAtMost(limit) else elapsed

                // One sample per whole second, catching up if a frame was
                // dropped, so the result graph never has holes in it.
                val samples = test.samples.toMutableList()
                while (capped >= nextSecond * 1000L) {
                    samples += typingSample(test, nextSecond)
                    nextSecond++
                }
                _uiState.update {
                    it.copy(typingTest = it.typingTest.copy(elapsedMs = capped, samples = samples))
                }
                if (timed && elapsed >= limit) {
                    finishTypingTest()
                    return@launch
                }
            }
        }
    }

    /**
     * A speed reading for the second that just ended: cumulative correct
     * characters over cumulative time. Cumulative rather than per-interval
     * because a one-second window is too short to be anything but noise.
     */
    private fun typingSample(test: TypingTestUi, second: Int): WpmSample {
        var correct = 0
        var typedWrong = 0
        var missed = 0
        for (word in test.typedWords) {
            for (state in compareWord(word.expected, word.typed, live = false)) {
                when (state) {
                    CharState.CORRECT -> correct++
                    CharState.WRONG, CharState.EXTRA -> typedWrong++
                    CharState.MISSING -> missed++
                    CharState.PENDING -> Unit
                }
            }
            if (word.typed == word.expected) correct++
        }
        val minutes = second / 60.0
        // Raw counts characters that ended up in the prompt, the same set
        // scoreTypingTest measures. Using the keystroke counter instead
        // would include corrected typing and leave the graph disagreeing
        // with the headline figure it sits under.
        val typed = correct + typedWrong
        return WpmSample(
            second = second,
            wpm = if (minutes > 0) (correct / 5.0) / minutes else 0.0,
            raw = if (minutes > 0) (typed / 5.0) / minutes else 0.0,
            errors = typedWrong + missed,
        )
    }

    /** One character key, scored against the letter the prompt expects. */
    private fun typingTestType(text: String) {
        if (text.isEmpty()) return
        armTypingClock()
        _uiState.update { state ->
            val test = state.typingTest
            val expected = test.words.getOrNull(test.wordIndex).orEmpty()
            // Right first time only if it lands on the position it was typed
            // at; anything past the end of the word is an overshoot.
            val hit = expected.getOrNull(test.current.length)?.toString() == text
            state.copy(
                typingTest = test.copy(
                    current = test.current + text,
                    totalKeystrokes = test.totalKeystrokes + 1,
                    correctKeystrokes = test.correctKeystrokes + if (hit) 1 else 0,
                ),
            )
        }
        // Word and quote runs finish on the last word without waiting for a
        // trailing space: once its final letter lands and the word matches,
        // there is nothing left to type. The closing space never counted for
        // the last word anyway (scoreTypingTest only credits it for earlier
        // words), so ending here costs the run nothing.
        val test = _uiState.value.typingTest
        if (_uiState.value.settings.typingTestMode != TypingTestMode.TIME &&
            test.wordIndex == test.words.lastIndex &&
            test.current == test.words.getOrNull(test.wordIndex)
        ) {
            finishTypingTest()
        }
    }

    /**
     * Backspace inside the current word. It deliberately does not walk back
     * into a finished word: reopening one would mean re-scoring keystrokes
     * already counted, and the accuracy figure is meant to remember the
     * mistakes rather than let them be edited away.
     */
    private fun typingTestBackspace() {
        _uiState.update { state ->
            val test = state.typingTest
            if (test.current.isEmpty()) state
            else state.copy(typingTest = test.copy(current = test.current.dropLast(1)))
        }
    }

    /** Space closes the current word and moves the caret to the next one. */
    private fun typingTestSpace() {
        val state = _uiState.value
        val test = state.typingTest
        // Leading spaces would silently score an empty word as wrong.
        if (test.current.isEmpty()) return
        armTypingClock()

        val expected = test.words.getOrNull(test.wordIndex).orEmpty()
        // The space is a correct keystroke only when it closed a word that
        // was actually right — matching how scoreTypingTest credits it.
        // Comparing lengths instead would score "teh" for "the" as a hit.
        val hit = test.current == expected
        val typedWords = test.typedWords + TypedWord(expected, test.current)
        _uiState.update {
            it.copy(
                typingTest = it.typingTest.copy(
                    typedWords = typedWords,
                    current = "",
                    totalKeystrokes = it.typingTest.totalKeystrokes + 1,
                    correctKeystrokes = it.typingTest.correctKeystrokes + if (hit) 1 else 0,
                ),
            )
        }
        // Word and quote runs end on the last word rather than on a clock.
        if (state.settings.typingTestMode != TypingTestMode.TIME &&
            typedWords.size >= test.words.size
        ) {
            finishTypingTest()
        }
    }

    /**
     * Scores the run, stores the result, and files it against the personal
     * bests. A run with no keystrokes is thrown away instead of recorded —
     * an accidental panel open should not land a zero in the history.
     */
    private fun finishTypingTest() {
        typingTestJob?.cancel()
        typingTestJob = null
        val state = _uiState.value
        val test = state.typingTest
        if (test.result != null) return

        // Whatever is half-typed still counts; the clock stopped mid-word.
        val words = if (test.current.isEmpty()) {
            test.typedWords
        } else {
            test.typedWords + TypedWord(test.words.getOrNull(test.wordIndex).orEmpty(), test.current)
        }
        val elapsed = test.startedAtMs?.let { test.elapsedMs.coerceAtLeast(1) } ?: 0L
        if (words.isEmpty() || elapsed <= 0) {
            _uiState.update { it.copy(panel = PanelMode.NONE, typingTest = TypingTestUi()) }
            return
        }

        val settings = state.settings
        val configKey = typingConfigKey(
            settings.typingTestMode, settings.typingTestDuration, settings.typingTestWordCount,
        )
        val result = scoreTypingTest(
            words = words,
            elapsedMs = elapsed,
            totalKeystrokes = test.totalKeystrokes,
            correctKeystrokes = test.correctKeystrokes,
            samples = test.samples,
            mode = settings.typingTestMode,
            configKey = configKey,
        )
        val improved = TypingBests.improve(settings.typingTestBests, configKey, result.wpm)
        _uiState.update {
            it.copy(typingTest = it.typingTest.copy(result = result, personalBest = improved != null))
        }
        serviceScope.launch {
            settingsRepository.recordTypingResult(
                history = TypingHistory.append(settings.typingTestHistory, result.wpm),
                bests = improved?.let { TypingBests.encode(it) },
            )
        }
    }

    /** Panel controls. Everything here persists — the panel is the settings. */
    fun onTypingTestAction(action: TypingTestAction) {
        vibrate()
        when (action) {
            TypingTestAction.Restart -> {
                startTypingTest()
                return
            }
            TypingTestAction.InsertResult -> {
                val result = _uiState.value.typingTest.result ?: return
                onToolTextInsert(typingResultText(result))
                // Closing the panel puts the user back in the field they
                // just wrote the score into.
                onPanelChange(PanelMode.TYPING_TEST)
                return
            }
            else -> Unit
        }
        // A settings change invalidates the prompt in front of the user, so
        // persist first and re-deal from the saved value rather than racing
        // the settings flow back into the panel.
        serviceScope.launch {
            when (action) {
                is TypingTestAction.Mode -> settingsRepository.setTypingTestMode(action.value)
                is TypingTestAction.Duration -> settingsRepository.setTypingTestDuration(action.seconds)
                is TypingTestAction.WordCount -> settingsRepository.setTypingTestWordCount(action.value)
                is TypingTestAction.Punctuation -> settingsRepository.setTypingTestPunctuation(action.on)
                is TypingTestAction.Numbers -> settingsRepository.setTypingTestNumbers(action.on)
                else -> return@launch
            }
            // settingsRepository.settings has already pushed the new value
            // into _uiState by the time the edit completes.
            settingsRepository.settings.first()
            startTypingTest()
        }
    }

    /** The shareable one-liner the "Insert" chip writes into the field. */
    private fun typingResultText(result: TypingResult): String {
        val settings = _uiState.value.settings
        val config = typingConfigLabel(
            result.mode, settings.typingTestDuration, settings.typingTestWordCount,
        )
        return "${result.wpm.roundToInt()} WPM · ${result.accuracy.roundToInt()}% accuracy ($config)"
    }

    // ---- AI tool ----

    private var aiJob: Job? = null

    /**
     * Identifies the latest [runAi] call. On-device streaming callbacks
     * arrive from a blocking native call that outlives job cancellation, so
     * stale runs must be ignored rather than relied on to stop.
     */
    private var aiRunSeq = 0

    /**
     * The last instruction the user ran the Custom action with. Held here
     * rather than on [AiUi] states so retry ([onAiRetry]) can rebuild the
     * same prompt without threading it through Loading/Ready/Error.
     */
    private var aiCustomInstruction = ""

    /**
     * The on-device model to run: the explicit selection, or — when nothing
     * (valid) is selected — the only model on disk. So the first download
     * just works without a selection step, and a deleted selection heals
     * itself while a sole alternative exists.
     */
    private fun effectiveLocalModelId(settings: KeyboardSettings): String? =
        settings.aiLocalModelId
            .takeIf { LocalLlmStore.selectedModelFile(filesDir, it) != null }
            ?: LocalLlmStore.soleDownloadedId(filesDir)

    private fun effectiveLocalModelFile(settings: KeyboardSettings): java.io.File? =
        effectiveLocalModelId(settings)?.let { LocalLlmStore.selectedModelFile(filesDir, it) }

    /**
     * Whether the model streams Qwen3-style implicit reasoning (no opening
     * tag, just bare thought ending in `</think>`). Catalog models declare
     * it; imported files fall back to a name sniff.
     */
    private fun isReasoningModel(modelId: String?): Boolean {
        if (modelId == null) return false
        LocalLlmCatalog.byId(modelId)?.let { return it.reasoning }
        val name = modelId.removePrefix(LocalLlmStore.CUSTOM_PREFIX).lowercase()
        return "qwen3" in name || "deepseek" in name
    }

    /** What the AI panel should show before any action runs. */
    private fun aiInitialState(settings: KeyboardSettings): AiUi = when {
        settings.aiProvider == AiProvider.ON_DEVICE &&
            effectiveLocalModelFile(settings) == null -> AiUi.NeedModel
        !AiClient.isConfigured(settings) &&
            settings.aiProvider != AiProvider.ON_DEVICE -> AiUi.NeedSetup
        else -> AiUi.Idle
    }

    /** Model-picker row on the AI panel: switch provider (and local model). */
    fun onAiPickModel(provider: AiProvider, localModelId: String?) {
        vibrate()
        serviceScope.launch {
            if (localModelId != null) settingsRepository.setAiLocalModelId(localModelId)
            settingsRepository.setAiProvider(provider)
            // Re-derive the panel state for the new choice; the settings flow
            // update races this tap, so compute from the edited values.
            val updated = _uiState.value.settings.copy(
                aiProvider = provider,
                aiLocalModelId = localModelId ?: _uiState.value.settings.aiLocalModelId,
            )
            _uiState.update { it.copy(ai = aiInitialState(updated)) }
        }
    }

    /** Selection first; whole field otherwise (text before cursor for Continue). */
    private fun aiInputText(action: AiAction): String {
        val ic = currentInputConnection ?: return ""
        ic.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        return if (action == AiAction.CONTINUE) {
            ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        } else {
            extractFieldText()
        }
    }

    fun onAiAction(action: AiAction) {
        vibrate()
        val initial = aiInitialState(_uiState.value.settings)
        if (initial is AiUi.NeedModel || initial is AiUi.NeedSetup) {
            _uiState.update { it.copy(ai = initial) }
            return
        }
        // Custom has no fixed prompt: open its input box and let the key rows
        // compose the instruction; the run happens on Enter / the Run chip.
        if (action == AiAction.CUSTOM) {
            _uiState.update { it.copy(ai = AiUi.CustomInput(aiCustomInstruction)) }
            return
        }
        currentInputConnection?.let { commitComposing(it, autocorrect = false) }
        val source = aiInputText(action).trim()
        if (source.isEmpty()) {
            _uiState.update {
                it.copy(ai = AiUi.Error(action, "Nothing to work on — type some text first."))
            }
            return
        }
        runAi(action, source)
    }

    /** Backspace/character edits to the Custom-action instruction buffer. */
    private fun aiCustomInputEdit(transform: (String) -> String) {
        val ai = _uiState.value.ai as? AiUi.CustomInput ?: return
        _uiState.update { it.copy(ai = AiUi.CustomInput(transform(ai.instruction))) }
    }

    /** Run the Custom action with the typed instruction over the field text. */
    fun onAiRunCustom() {
        val instruction = (_uiState.value.ai as? AiUi.CustomInput)?.instruction?.trim().orEmpty()
        if (instruction.isEmpty()) return
        vibrate()
        currentInputConnection?.let { commitComposing(it, autocorrect = false) }
        val source = aiInputText(AiAction.CUSTOM).trim()
        if (source.isEmpty()) {
            _uiState.update {
                it.copy(ai = AiUi.Error(AiAction.CUSTOM, "Nothing to work on — type some text first."))
            }
            return
        }
        aiCustomInstruction = instruction
        runAi(AiAction.CUSTOM, source)
    }

    private fun runAi(action: AiAction, source: String) {
        aiJob?.cancel()
        val seq = ++aiRunSeq
        _uiState.update { it.copy(ai = AiUi.Loading(action)) }
        aiJob = serviceScope.launch {
            val settings = _uiState.value.settings
            val system = if (action == AiAction.CUSTOM) {
                AiPrompts.customPrompt(aiCustomInstruction)
            } else {
                AiPrompts.systemPrompt(action, settings)
            }
            val config = AiClient.config(settings)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (config.provider == AiProvider.ON_DEVICE) {
                        runAiOnDevice(seq, action, source, system, settings)
                    } else {
                        AiClient.complete(
                            config, system, source, AiClient.effectiveMaxTokens(settings),
                        )
                    }
                }
            }
            if (seq != aiRunSeq) return@launch
            _uiState.update {
                it.copy(
                    ai = result.fold(
                        onSuccess = { raw ->
                            val text =
                                if (settings.aiShowThinking) raw.trim()
                                else AiThinking.stripped(raw)
                            when {
                                text.isNotBlank() -> AiUi.Ready(
                                    action, text, source,
                                    stripMarkdown = aiStripMarkdownDefault(),
                                )
                                raw.isBlank() -> AiUi.Error(action, "The model returned nothing.")
                                else -> AiUi.Error(
                                    action,
                                    "The model spent its whole response reasoning — " +
                                        "try again, or turn on “Show reasoning” in settings.",
                                )
                            }
                        },
                        onFailure = { e -> AiUi.Error(action, e.message ?: "Request failed") },
                    ),
                )
            }
        }
    }

    /**
     * Blocking on-device generation, streaming partial text into
     * [AiUi.Ready] with [AiUi.Ready.generating] set so the panel can render
     * the response as it forms. Call under [Dispatchers.IO].
     */
    private fun runAiOnDevice(
        seq: Int,
        action: AiAction,
        source: String,
        system: String,
        settings: KeyboardSettings,
    ): String {
        val modelId = effectiveLocalModelId(settings)
        val modelFile = effectiveLocalModelFile(settings)
            ?: throw IOException("The selected model is gone — download it again in settings")
        val implicitThink = isReasoningModel(modelId)
        var lastPartialAt = 0L
        return LocalLlmEngine.generate(
            context = applicationContext,
            modelFile = modelFile,
            backend = settings.aiLocalBackend,
            system = system,
            user = source,
        ) { raw ->
            val now = SystemClock.uptimeMillis()
            if (seq != aiRunSeq || now - lastPartialAt < 120) return@generate
            lastPartialAt = now
            // Reasoning models: keep the spinner (marked "thinking") until
            // real output starts, unless the user wants the raw stream.
            val shown = if (settings.aiShowThinking) {
                AiThinking.Split(raw, thinking = false)
            } else {
                AiThinking.split(raw, implicitThink)
            }
            _uiState.update {
                it.copy(
                    ai = when {
                        shown.output.isBlank() && shown.thinking ->
                            AiUi.Loading(action, thinking = true)
                        shown.output.isBlank() -> it.ai // nothing visible yet
                        else -> AiUi.Ready(
                            action, shown.output, source,
                            generating = true,
                            stripMarkdown = (it.ai as? AiUi.Ready)?.stripMarkdown ?: true,
                        )
                    },
                )
            }
        }
    }

    /** Re-runs the last action on the text it originally saw. */
    fun onAiRetry() {
        when (val ai = _uiState.value.ai) {
            is AiUi.Ready -> { vibrate(); runAi(ai.action, ai.sourceText) }
            // A failed Custom run reopens its input prefilled so the user can
            // tweak the instruction; onAiAction(CUSTOM) does exactly that.
            is AiUi.Error -> onAiAction(ai.action)
            else -> {}
        }
    }

    /**
     * Replaces the field with the result — except for Continue, where
     * "replace" would delete the text being continued; that appends.
     */
    fun onAiReplace() {
        val ai = _uiState.value.ai as? AiUi.Ready ?: return
        vibrate()
        if (ai.action == AiAction.CONTINUE) {
            currentInputConnection?.commitText(aiInsertableText(ai), 1)
            return
        }
        replaceFieldText(aiInsertableText(ai))
    }

    fun onAiInsert() {
        val ai = _uiState.value.ai as? AiUi.Ready ?: return
        vibrate()
        currentInputConnection?.commitText(aiInsertableText(ai), 1)
    }

    /**
     * Carries the panel's "plain text" checkbox across runs: on by default,
     * but a user who turned it off means it for the next result too.
     */
    private fun aiStripMarkdownDefault(): Boolean =
        (_uiState.value.ai as? AiUi.Ready)?.stripMarkdown ?: true

    /** Panel's "plain text" checkbox. */
    fun onAiToggleStripMarkdown() {
        val ai = _uiState.value.ai as? AiUi.Ready ?: return
        vibrate()
        _uiState.update { it.copy(ai = ai.copy(stripMarkdown = !ai.stripMarkdown)) }
    }

    /**
     * What Replace/Insert actually commit: even when the panel shows a
     * reasoning model's think block (verbose mode), only the trimmed answer
     * belongs in the text field — with markdown syntax removed unless the
     * user unchecked it.
     */
    private fun aiInsertableText(ai: AiUi.Ready): String {
        val answer = AiThinking.stripped(ai.result).ifBlank { ai.result.trim() }
        return if (ai.stripMarkdown) AiMarkdown.strip(answer) else answer
    }

    // ---- tools: dictionary & camera ----

    private var dictionaryJob: Job? = null

    /**
     * Dictionary panel just opened: look up the selected word (or the word
     * around the cursor) per the auto-lookup setting; with nothing to look
     * up, drop straight into the search field so typing starts a query.
     */
    private fun openDictionary() {
        val word = if (_uiState.value.settings.dictionaryAutoLookup) currentWordForLookup() else null
        if (word != null) {
            onDictionaryLookup(word)
        } else if (_uiState.value.dictionary is DictionaryUi.Ready) {
            // No word at the cursor but a previous lookup is still on
            // screen — keep it; the search chip is one tap away.
        } else {
            _uiState.update { it.copy(dictionarySearchActive = true, dictionaryQuery = "") }
        }
    }

    /** Selection first; else the run of word characters around the cursor. */
    private fun currentWordForLookup(): String? {
        val ic = currentInputConnection ?: return null
        fun sanitize(raw: String): String? {
            val word = raw.trim().trim('\'', '-', '“', '”', '"')
            return word.takeIf {
                it.isNotEmpty() && it.length <= 40 &&
                    it.any(Char::isLetter) &&
                    // The API is English-only; skip Bengali (or any
                    // non-Latin) words instead of showing "not found".
                    it.all { ch -> ch.code < 0x250 || ch == '’' }
            }
        }
        ic.getSelectedText(0)?.toString()?.let { return sanitize(it) }
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(48, 0)?.toString().orEmpty()
        fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’' || c == '-'
        return sanitize(before.takeLastWhile(::isWordChar) + after.takeWhile(::isWordChar))
    }

    fun onDictionaryLookup(rawWord: String) {
        val word = rawWord.trim()
        if (word.isEmpty()) {
            _uiState.update { it.copy(dictionarySearchActive = false) }
            return
        }
        dictionaryJob?.cancel()
        _uiState.update {
            it.copy(
                dictionaryQuery = word,
                dictionarySearchActive = false,
                dictionary = DictionaryUi.Loading(word),
            )
        }
        dictionaryJob = serviceScope.launch {
            val ui = try {
                val entries = withContext(Dispatchers.IO) { DictionaryClient.lookup(word) }
                if (entries.isEmpty()) DictionaryUi.NotFound(word) else DictionaryUi.Ready(entries)
            } catch (e: DictionaryClient.NotFoundException) {
                DictionaryUi.NotFound(word)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DictionaryUi.Error(word)
            }
            _uiState.update { it.copy(dictionary = ui) }
        }
    }

    fun onDictionarySearchToggle() {
        vibrate()
        _uiState.update { it.copy(dictionarySearchActive = !it.dictionarySearchActive) }
    }

    /** Insert chip on a dictionary entry: type the word into the editor. */
    fun onDictionaryInsert(word: String) {
        vibrate()
        val ic = currentInputConnection ?: return
        commitComposing(ic, autocorrect = false)
        ic.commitText(word, 1)
    }

    /** Camera tool captured a photo: send it into the editor as an image. */
    fun onCameraSend(file: File) {
        vibrate()
        saveToGalleryIfEnabled(
            file,
            MediaMime.JPEG,
            _uiState.value.settings.camera.saveToGallery,
            "IMG",
        )
        commitImageFile(file, MediaMime.JPEG)
        // The photo is on its way (or on the clipboard) — the tool's job is
        // done, give the keys back.
        _uiState.update { it.copy(panel = PanelMode.NONE) }
    }

    /** IMEs cannot show permission dialogs; bounce through the trampoline. */
    fun onCameraPermissionRequest() {
        startActivity(
            Intent(this, CameraPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Calendar tool's READ_CALENDAR request, via the same trampoline pattern. */
    fun onCalendarPermissionRequest() {
        startActivity(
            Intent(this, CalendarPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Doc-scan tool: hand off to ML Kit's full-screen scanner activity
     * (edge detection, crop, filters — all Google's UI). The scanned pages
     * come back through [DocScanActivity.consumePendingPages] in
     * [onStartInputView], once the target app has focus again.
     */
    fun onDocScanStart() {
        if (!BuildConfig.ENABLE_ML_KIT_SCANNERS) return
        vibrate()
        _uiState.update { it.copy(panel = PanelMode.NONE) }
        startActivity(
            Intent(this, DocScanActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** OCR/QR tools: insert recognized text like a (long) key press. */
    fun onScannedTextInsert(text: String) {
        vibrate()
        val ic = currentInputConnection ?: return
        commitComposing(ic, autocorrect = false)
        ic.commitText(text, 1)
    }

    /** Open a scanned QR/barcode URL in the browser (leaves the keyboard). */
    fun onScannedUrlOpen(url: String) {
        vibrate()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    // ---- translate / gif / sticker / web & image search tools ----

    /** Whether the web/image search backend (Brave) is keyed. */
    private fun hasSearchKey(): Boolean =
        ToolApiKeys.hasSearchProvider(_uiState.value.settings)

    /** Search-bar tap on a media panel: toggle typing-into-the-query mode. */
    fun onMediaQueryTap() {
        vibrate()
        _uiState.update { it.copy(mediaSearchActive = !it.mediaSearchActive) }
    }

    /**
     * GIF/sticker searches are cheap on KLIPY/GIPHY, so results follow the
     * query live. Web/image search waits for enter — the free Programmable
     * Search tier is 100 queries a day.
     */
    private fun scheduleMediaLiveSearch() {
        val state = _uiState.value
        // Translate is free-tier friendly too: its result follows the typed
        // query live, on its own (400 ms) debounce.
        if (state.panel == PanelMode.TRANSLATE) {
            scheduleTranslate()
            return
        }
        if (state.panel != PanelMode.GIF && state.panel != PanelMode.STICKER) return
        mediaLiveSearchJob?.cancel()
        val query = state.mediaQuery.trim()
        mediaLiveSearchJob = serviceScope.launch {
            delay(450)
            refreshMedia(query)
        }
    }

    /** Enter (or the search action) in a media panel's search box. */
    private fun runMediaSearch() {
        val state = _uiState.value
        val query = state.mediaQuery.trim()
        _uiState.update { it.copy(mediaSearchActive = false) }
        when (state.panel) {
            PanelMode.GIF, PanelMode.STICKER -> {
                mediaLiveSearchJob?.cancel()
                refreshMedia(query)
            }
            PanelMode.WEB_SEARCH -> runWebSearch(query)
            PanelMode.IMAGE_SEARCH -> runImageSearch(query)
            PanelMode.WIKIPEDIA -> runWikiSearch(query)
            PanelMode.TRANSLATE -> scheduleTranslate(immediate = true)
            else -> {}
        }
    }

    /** Retry button on media/search panels: re-run whatever failed. */
    fun onMediaRetry() {
        vibrate()
        when (_uiState.value.panel) {
            PanelMode.GIF, PanelMode.STICKER -> refreshMedia(_uiState.value.mediaQuery.trim())
            PanelMode.WEB_SEARCH -> runWebSearch(_uiState.value.mediaQuery.trim())
            PanelMode.IMAGE_SEARCH -> runImageSearch(_uiState.value.mediaQuery.trim())
            PanelMode.WIKIPEDIA -> {
                val query = _uiState.value.mediaQuery.trim()
                if (query.isNotEmpty()) runWikiSearch(query)
                else _uiState.update { it.copy(wiki = WikiUi.Idle, mediaSearchActive = true) }
            }
            PanelMode.TRANSLATE -> scheduleTranslate(immediate = true)
            else -> {}
        }
    }

    /**
     * Fetches GIFs or stickers for [query] from every active provider
     * (blank query = trending).
     */
    private fun refreshMedia(query: String) {
        val state = _uiState.value
        val sticker = state.panel == PanelMode.STICKER
        if (!sticker && state.panel != PanelMode.GIF) return
        val setUi: (MediaUi) -> Unit = { ui ->
            _uiState.update { if (sticker) it.copy(sticker = ui) else it.copy(gif = ui) }
        }
        val settings = state.settings
        val sources = ToolApiKeys.gifSources(settings)
        if (sources.isEmpty()) {
            setUi(MediaUi.NeedKey)
            return
        }
        val targets = if (settings.gifSourceMode == GifSourceMode.TABS) {
            val selected = state.mediaSource.takeIf { it in sources } ?: sources.first()
            if (selected != state.mediaSource) _uiState.update { it.copy(mediaSource = selected) }
            listOf(selected)
        } else {
            sources
        }
        mediaFetchJob?.cancel()
        setUi(MediaUi.Loading)
        val panel = state.panel
        mediaFetchJob = serviceScope.launch {
            val results = withContext(Dispatchers.IO) {
                targets.map { source ->
                    async { runCatching { fetchGifs(source, query, sticker, settings) } }
                }.awaitAll()
            }
            if (_uiState.value.panel != panel) return@launch
            val successes = results.mapNotNull { it.getOrNull() }
            setUi(
                if (successes.isEmpty()) {
                    MediaUi.Error(
                        results.firstNotNullOfOrNull { it.exceptionOrNull() }
                            ?.let { ToolHttp.friendlyMessage(it) }
                            ?: "Couldn't fetch results",
                    )
                } else {
                    MediaUi.Ready(GifSources.interleave(successes), query)
                },
            )
        }
    }

    /** Blocking provider dispatch; call on an IO dispatcher. */
    private fun fetchGifs(
        source: GifSource,
        query: String,
        sticker: Boolean,
        settings: com.wasimaster.wmkeyboard.core.settings.KeyboardSettings,
    ): List<GifItem> = when (source) {
        GifSource.KLIPY ->
            KlipyClient.search(query, ToolApiKeys.klipy(settings), sticker, settings.gifContentFilter)
        GifSource.GIPHY ->
            GiphyClient.search(query, ToolApiKeys.giphy(settings), sticker, settings.gifContentFilter)
    }

    /** Provider chip on the GIF/sticker panel (tabs mode). */
    fun onGifSourceSelect(source: GifSource) {
        vibrate()
        if (_uiState.value.mediaSource == source) return
        _uiState.update { it.copy(mediaSource = source) }
        refreshMedia(_uiState.value.mediaQuery.trim())
    }

    private fun runWebSearch(query: String) {
        if (query.isBlank()) return
        val settings = _uiState.value.settings
        if (!ToolApiKeys.hasSearchProvider(settings)) {
            _uiState.update { it.copy(webSearch = WebSearchUi.NeedKey) }
            return
        }
        webSearchJob?.cancel()
        _uiState.update { it.copy(webSearch = WebSearchUi.Loading) }
        webSearchJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BraveSearchClient.webSearch(
                        query,
                        ToolApiKeys.brave(settings),
                        settings.searchResultCount,
                        settings.searchSafe,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    webSearch = result.fold(
                        onSuccess = { r -> WebSearchUi.Ready(r, query) },
                        onFailure = { e -> WebSearchUi.Error(e.message ?: "Search failed") },
                    ),
                )
            }
        }
    }

    private fun runImageSearch(query: String) {
        if (query.isBlank()) return
        val settings = _uiState.value.settings
        if (!ToolApiKeys.hasSearchProvider(settings)) {
            _uiState.update { it.copy(imageSearch = ImageSearchUi.NeedKey) }
            return
        }
        imageSearchJob?.cancel()
        _uiState.update { it.copy(imageSearch = ImageSearchUi.Loading) }
        imageSearchJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BraveSearchClient.imageSearch(
                        query,
                        ToolApiKeys.brave(settings),
                        settings.searchResultCount,
                        settings.searchSafe,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    imageSearch = result.fold(
                        onSuccess = { r -> ImageSearchUi.Ready(r, query) },
                        onFailure = { e -> ImageSearchUi.Error(e.message ?: "Search failed") },
                    ),
                )
            }
        }
    }

    /**
     * Tapped a GIF/sticker cell: download the full file and commit it. The
     * two panels share this path, so which send mode applies depends on
     * which one is open.
     */
    fun onGifSelect(item: GifItem) {
        val settings = _uiState.value.settings
        val sendMode = if (_uiState.value.panel == PanelMode.STICKER) {
            settings.stickerSendMode
        } else {
            settings.gifSendMode
        }
        insertDownloadedImage(item.id, item.fullUrl, item.mime, sendMode)
    }

    /** Tapped an image-search cell: download the full image and commit it. */
    fun onImageResultSelect(result: ImageResult) {
        val mime = when (result.mime) {
            "image/gif", "image/png", "image/webp", "image/jpeg" -> result.mime
            else -> "image/jpeg"
        }
        insertDownloadedImage(result.imageUrl, result.imageUrl, mime)
    }

    /** Long-pressed an image-search cell: insert the image's URL as text. */
    fun onImageResultLink(result: ImageResult) {
        vibrate()
        currentInputConnection?.commitText(result.imageUrl, 1)
    }

    /** Tapped a web result: insert its URL at the cursor. */
    fun onWebResultSelect(result: WebResult) {
        vibrate()
        currentInputConnection?.commitText(result.url, 1)
    }

    /** Open a web result in the browser (leaves the keyboard). */
    fun onWebResultOpen(result: WebResult) {
        vibrate()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(result.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /**
     * Downloads a remote image into the media cache and commits it through
     * the same commitContent path as clipboard images. One insert at a
     * time; the panel shows a spinner over the tapped cell meanwhile.
     */
    private fun insertDownloadedImage(
        id: String,
        url: String,
        mime: String,
        sendMode: MediaSendMode = MediaSendMode.IMAGE,
    ) {
        if (_uiState.value.mediaDownloadingId != null) return
        vibrate()
        _uiState.update { it.copy(mediaDownloadingId = id) }
        mediaInsertJob = serviceScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val extension = MediaMime.extension(mime)
                    val dir = File(cacheDir, "media").apply { mkdirs() }
                    pruneMediaCache(dir)
                    // Name is stable per item so re-inserting the same GIF
                    // reuses the already-downloaded file.
                    val target = File(dir, "media_${url.hashCode().toUInt()}.$extension")
                    if (!target.exists() || target.length() == 0L) ToolHttp.download(url, target)
                    target
                }.getOrNull()
            }
            _uiState.update { it.copy(mediaDownloadingId = null) }
            if (file == null) {
                Toast.makeText(
                    this@WMKeyboardService,
                    "Download failed — check your connection",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            commitImageFile(file, mime, sendMode)
        }
    }

    /** Keeps the media cache bounded (newest ~30 files). */
    private fun pruneMediaCache(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(30).forEach { it.delete() }
    }

    // ---- translate tool ----

    /** Everything in the focused field, for the grammar strip. */
    private fun extractFieldText(): String {
        val ic = currentInputConnection ?: return ""
        val extracted = runCatching {
            ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
        }.getOrNull()
        if (extracted != null) return extracted
        // Some editors don't implement extraction; stitch around the cursor.
        val before = ic.getTextBeforeCursor(TranslateClient.MAX_CHARS, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(TranslateClient.MAX_CHARS, 0)?.toString().orEmpty()
        return before + after
    }

    /**
     * Translates the panel's typed query after a short debounce, so the
     * result follows the typing without a request per keystroke. The query
     * lives in [KeyboardUiState.mediaQuery] — the panel is its own window
     * and never reads the focused field.
     */
    private fun scheduleTranslate(immediate: Boolean = false, targetOverride: String? = null) {
        translateJob?.cancel()
        translateJob = serviceScope.launch {
            if (!immediate) delay(400)
            val state = _uiState.value
            if (state.panel != PanelMode.TRANSLATE) return@launch
            val source = state.mediaQuery.trim()
            if (source.isEmpty()) {
                _uiState.update { it.copy(translate = TranslateUi()) }
                return@launch
            }
            if (source == state.translate.sourceText &&
                state.translate.translated.isNotEmpty() && state.translate.error == null
            ) {
                return@launch
            }
            _uiState.update {
                it.copy(translate = it.translate.copy(sourceText = source, translating = true, error = null))
            }
            val target = targetOverride ?: state.settings.translateTargetLang
            val key = ToolApiKeys.translate(state.settings)
            val result = withContext(Dispatchers.IO) {
                runCatching { TranslateClient.translate(source, target, key) }
            }
            if (_uiState.value.panel != PanelMode.TRANSLATE) return@launch
            _uiState.update {
                it.copy(
                    translate = result.fold(
                        onSuccess = { t ->
                            TranslateUi(sourceText = source, translated = t.text, detectedSource = t.detectedSource)
                        },
                        onFailure = { e ->
                            it.translate.copy(translating = false, error = e.message ?: "Translation failed")
                        },
                    ),
                )
            }
        }
    }

    fun onTranslateTargetChange(code: String) {
        vibrate()
        serviceScope.launch { settingsRepository.setTranslateTargetLang(code) }
        _uiState.update { it.copy(translate = TranslateUi()) }
        // The settings flow updates asynchronously; pass the new target
        // directly so this retranslate can't race it.
        scheduleTranslate(immediate = true, targetOverride = code)
    }

    /** Replaces the whole field with the translation. */
    fun onTranslateReplace() {
        val translated = _uiState.value.translate.translated
        if (translated.isEmpty()) return
        vibrate()
        val ic = currentInputConnection ?: return
        composing = StringBuilder()
        ic.beginBatchEdit()
        val length = runCatching {
            ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.length
        }.getOrNull()
        if (length != null) {
            ic.setSelection(0, length)
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
        ic.commitText(translated, 1)
        ic.endBatchEdit()
    }

    /** Inserts the translation at the cursor, keeping the original text. */
    fun onTranslateInsert() {
        val translated = _uiState.value.translate.translated
        if (translated.isEmpty()) return
        vibrate()
        currentInputConnection?.commitText(translated, 1)
    }

    // ---- grammar tool (offline, Harper via JNI) ----

    /**
     * Re-extracts the field and lints it after a short debounce. Linting is
     * local and fast, but the debounce keeps the strip from churning on
     * every keystroke mid-word.
     */
    private fun scheduleGrammarCheck(immediate: Boolean = false) {
        grammarJob?.cancel()
        grammarJob = serviceScope.launch {
            if (!immediate) delay(_uiState.value.settings.grammarDebounceMs.toLong())
            val state = _uiState.value
            if (state.panel != PanelMode.GRAMMAR) return@launch
            if (!GrammarChecker.available) {
                _uiState.update { it.copy(grammar = GrammarUi(available = false)) }
                return@launch
            }
            val source = extractFieldText()
            if (source.isBlank()) {
                _uiState.update { it.copy(grammar = GrammarUi()) }
                return@launch
            }
            if (source == state.grammar.sourceText && state.grammar.checkedOnce) return@launch
            _uiState.update {
                it.copy(grammar = it.grammar.copy(sourceText = source, checking = true))
            }
            val dialect = _uiState.value.settings.grammarDialect
            val lints = GrammarChecker.check(source, dialect.ordinal)
            if (_uiState.value.panel != PanelMode.GRAMMAR) return@launch
            _uiState.update {
                it.copy(
                    grammar = GrammarUi(
                        sourceText = source,
                        lints = lints,
                        checking = false,
                        checkedOnce = true,
                    ),
                )
            }
        }
    }

    /** Replaces the whole field with [newText] (same mechanics as translate). */
    private fun replaceFieldText(newText: String) {
        val ic = currentInputConnection ?: return
        composing = StringBuilder()
        ic.beginBatchEdit()
        val length = runCatching {
            ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.length
        }.getOrNull()
        if (length != null) {
            ic.setSelection(0, length)
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    /**
     * Re-lints [text] directly, without the InputConnection round-trip.
     * Used right after a fix is applied: extracting the field again races the
     * batch edit (the editor may still report the old text, which matches
     * [GrammarUi.sourceText] and skips the check), so lint the string we just
     * committed instead. checkedOnce stays false until this completes so the
     * selection-update recheck re-lints rather than skipping if this job gets
     * cancelled mid-check.
     */
    private fun relintAfterFix(text: String) {
        grammarJob?.cancel()
        grammarJob = serviceScope.launch {
            _uiState.update {
                it.copy(
                    grammar = it.grammar.copy(
                        sourceText = text,
                        checking = true,
                        checkedOnce = false,
                    ),
                )
            }
            val lints = GrammarChecker.check(text, _uiState.value.settings.grammarDialect.ordinal)
            if (_uiState.value.panel != PanelMode.GRAMMAR) return@launch
            _uiState.update {
                it.copy(
                    grammar = GrammarUi(
                        sourceText = text,
                        lints = lints,
                        checking = false,
                        checkedOnce = true,
                    ),
                )
            }
        }
    }

    /** Tapped one fix chip: apply it and re-lint the result. */
    fun onGrammarFix(lint: GrammarLint, fix: GrammarFix) {
        vibrate()
        val source = _uiState.value.grammar.sourceText
        val fixed = GrammarChecker.apply(source, lint, fix)
        if (fixed == source) return
        replaceFieldText(fixed)
        relintAfterFix(fixed)
    }

    /**
     * Tapped a card body (not a fix chip): jump the field cursor to the issue.
     * A multi-word span (sentence-level lint) parks the cursor at its start; a
     * word that needs swapping gets selected ready to overtype; a small fix
     * (add punctuation, recase) parks the cursor at the word's end. Offsets are
     * UTF-16 into [GrammarUi.sourceText] = the full field text base 0, so they
     * map straight onto the InputConnection.
     */
    fun onGrammarFocus(lint: GrammarLint) {
        vibrate()
        val ic = currentInputConnection ?: return
        val source = _uiState.value.grammar.sourceText
        val start = lint.start.coerceIn(0, source.length)
        val end = lint.end.coerceIn(start, source.length)
        val span = source.substring(start, end)
        val hasReplacement = lint.suggestions.any { fix ->
            fix.kind == "replace" && !fix.text.isNullOrEmpty() &&
                !fix.text.equals(span, ignoreCase = true)
        }
        val multiWord = span.trim().any { it.isWhitespace() }
        when {
            multiWord -> ic.setSelection(start, start)
            hasReplacement -> ic.setSelection(start, end)
            else -> ic.setSelection(end, end)
        }
    }

    /** Tapped "Fix all": apply every lint's top suggestion. */
    fun onGrammarFixAll() {
        vibrate()
        val source = _uiState.value.grammar.sourceText
        val fixed = GrammarChecker.applyAll(source, _uiState.value.grammar.lints)
        if (fixed == source) return
        replaceFieldText(fixed)
        relintAfterFix(fixed)
    }

    /** Dismissed a card: hide the lint until the field text changes again. */
    fun onGrammarDismiss(lint: GrammarLint) {
        vibrate()
        _uiState.update { it.copy(grammar = it.grammar.copy(lints = it.grammar.lints - lint)) }
    }

    fun onGrammarDialectChange(dialect: GrammarDialect) {
        vibrate()
        serviceScope.launch { settingsRepository.setGrammarDialect(dialect) }
        // Force a fresh lint: clear checkedOnce so the same text re-checks
        // under the new dialect (the settings flow updates asynchronously,
        // so check directly with the new value).
        _uiState.update { it.copy(grammar = it.grammar.copy(checkedOnce = false, checking = true)) }
        grammarJob?.cancel()
        grammarJob = serviceScope.launch {
            val source = extractFieldText()
            if (source.isBlank()) {
                _uiState.update { it.copy(grammar = GrammarUi()) }
                return@launch
            }
            val lints = GrammarChecker.check(source, dialect.ordinal)
            if (_uiState.value.panel != PanelMode.GRAMMAR) return@launch
            _uiState.update {
                it.copy(
                    grammar = GrammarUi(
                        sourceText = source,
                        lints = lints,
                        checking = false,
                        checkedOnce = true,
                    ),
                )
            }
        }
    }

    /**
     * Text-editing panel buttons. Cursor moves go through the editor as key
     * events so apps handle them natively; while selection mode is on (or
     * right after Select all) the moves carry shift and extend the selection.
     */
    fun onTextEdit(action: TextEditAction) {
        val ic = currentInputConnection ?: return
        vibrate()
        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        val selecting = _uiState.value.textEditSelecting
        when (action) {
            TextEditAction.LEFT -> sendEditorKey(KeyEvent.KEYCODE_DPAD_LEFT, selecting)
            TextEditAction.RIGHT -> sendEditorKey(KeyEvent.KEYCODE_DPAD_RIGHT, selecting)
            TextEditAction.UP -> sendEditorKey(KeyEvent.KEYCODE_DPAD_UP, selecting)
            TextEditAction.DOWN -> sendEditorKey(KeyEvent.KEYCODE_DPAD_DOWN, selecting)
            TextEditAction.HOME -> sendEditorKey(KeyEvent.KEYCODE_MOVE_HOME, selecting)
            TextEditAction.END -> sendEditorKey(KeyEvent.KEYCODE_MOVE_END, selecting)
            TextEditAction.PAGE_UP -> sendEditorKey(KeyEvent.KEYCODE_PAGE_UP, selecting)
            TextEditAction.PAGE_DOWN -> sendEditorKey(KeyEvent.KEYCODE_PAGE_DOWN, selecting)
            // Ctrl+Arrow is the editor's move-by-word shortcut; with the panel's
            // select mode on it carries shift too and extends word by word.
            TextEditAction.WORD_LEFT ->
                sendEditorKey(KeyEvent.KEYCODE_DPAD_LEFT, selecting, ctrl = true)
            TextEditAction.WORD_RIGHT ->
                sendEditorKey(KeyEvent.KEYCODE_DPAD_RIGHT, selecting, ctrl = true)
            TextEditAction.SELECT_WORD -> selectWordAtCursor(ic)
            TextEditAction.SELECT ->
                _uiState.update { it.copy(textEditSelecting = !selecting) }
            TextEditAction.SELECT_ALL -> {
                ic.performContextMenuAction(android.R.id.selectAll)
                _uiState.update { it.copy(textEditSelecting = true) }
            }
            TextEditAction.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                maybeToastCopied()
                _uiState.update { it.copy(textEditSelecting = false) }
            }
            TextEditAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            TextEditAction.BACKSPACE -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    /**
     * Toast confirming text landed on the clipboard, for the users who opt in
     * (some fields give no copy feedback of their own). Off by default.
     */
    private fun maybeToastCopied() {
        if (_uiState.value.settings.feedback.toastOnCopy) {
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Clipboard shortcuts fired by long-pressing A/C/V/X. Copy and cut act on
     * the current selection when one exists; with nothing selected they select
     * all first, so a bare long press copies or cuts the whole field.
     */
    fun onClipboardKey(action: ClipboardKeyAction) {
        val ic = currentInputConnection ?: return
        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        val hasSelection = ic.getSelectedText(0)?.isNotEmpty() == true
        when (action) {
            ClipboardKeyAction.SELECT_ALL -> {
                ic.performContextMenuAction(android.R.id.selectAll)
                _uiState.update { it.copy(textEditSelecting = true) }
            }
            ClipboardKeyAction.COPY -> {
                if (!hasSelection) ic.performContextMenuAction(android.R.id.selectAll)
                ic.performContextMenuAction(android.R.id.copy)
                maybeToastCopied()
                _uiState.update { it.copy(textEditSelecting = false) }
            }
            ClipboardKeyAction.CUT -> {
                if (!hasSelection) ic.performContextMenuAction(android.R.id.selectAll)
                ic.performContextMenuAction(android.R.id.cut)
                _uiState.update { it.copy(textEditSelecting = false) }
            }
            ClipboardKeyAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
        }
    }

    /**
     * DPAD/home/end navigation; with [shift] the move extends the selection,
     * and with [ctrl] it moves by whole words (the editor's Ctrl+Arrow binding).
     */
    private fun sendEditorKey(code: Int, shift: Boolean, ctrl: Boolean = false) {
        if (!shift && !ctrl) {
            sendDownUpKeyEvents(code)
            return
        }
        val ic = currentInputConnection ?: return
        val time = android.os.SystemClock.uptimeMillis()
        var meta = 0
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        // A bare meta flag isn't enough for every editor: TextView tracks
        // modifier state from the modifier keys' own down/up events, so wrap
        // the arrow in real shift/ctrl presses like a hardware keyboard would.
        if (shift) {
            ic.sendKeyEvent(
                KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta)
            )
        }
        if (ctrl) {
            ic.sendKeyEvent(
                KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta)
            )
        }
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, meta))
        if (ctrl) {
            ic.sendKeyEvent(
                KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta)
            )
        }
        if (shift) {
            ic.sendKeyEvent(
                KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta)
            )
        }
    }

    /**
     * Selects the word straddling the cursor: walks out to the word boundaries
     * on either side of the caret and sets the selection to span them. A caret
     * on whitespace or punctuation (no word to grab) is left untouched. The
     * offsets from [getExtractedText] are used directly as document positions,
     * matching how the rest of this service treats them.
     */
    private fun selectWordAtCursor(ic: InputConnection) {
        val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val text = et.text ?: return
        val n = text.length
        // Anchor at the caret (selection end); clamp defensively.
        val caret = et.selectionEnd.let { if (it in 0..n) it else et.selectionStart }
        if (caret !in 0..n) return
        fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '_'
        var start = caret
        var end = caret
        while (start > 0 && isWord(text[start - 1])) start--
        while (end < n && isWord(text[end])) end++
        if (start == end) return
        ic.setSelection(start, end)
        _uiState.update { it.copy(textEditSelecting = true) }
    }

    fun onSnippetTapped(snippet: Snippet) {
        vibrate()
        val ic = currentInputConnection
        val expanded = SnippetStore.expandWithCursor(
            text = snippet.text,
            context = snippetContext(ic),
        )
        if (ic != null) {
            // Committing with newCursorPosition = 1 leaves the cursor at the
            // end; {cursor} then walks it back to the marked spot.
            ic.commitText(expanded.text, 1)
            val trailing = expanded.text.length - expanded.cursorOffset
            if (trailing > 0) {
                val end = ic.getExtractedText(ExtractedTextRequest(), 0)?.selectionEnd
                if (end != null && end >= trailing) ic.setSelection(end - trailing, end - trailing)
            }
        }
        _uiState.update { it.copy(panel = PanelMode.NONE) }
    }

    /** The app, clipboard and selection a snippet's variables expand against. */
    private fun snippetContext(ic: InputConnection?): SnippetStore.Companion.Context {
        val pkg = currentPackage
        return SnippetStore.Companion.Context(
            clipboard = clipboardStore.latestText(),
            appName = pkg?.let(::appLabel),
            packageName = pkg,
            selection = ic?.getSelectedText(0)?.toString(),
        )
    }

    /** Human-readable label for [pkg], falling back to the package name. */
    private fun appLabel(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

    /**
     * Resolves the face to show for a suggested/searched emoji: the global
     * default skin tone, unless the "override with last used" option is on and
     * a variant was picked for this base (see [KeyboardSettings.emoji]).
     */
    private fun applyEmojiTone(emoji: String): String {
        val emojiSettings = _uiState.value.settings.emoji
        return _uiState.value.emojiVariants.tonedDisplay(
            base = emoji,
            tone = emojiSettings.defaultSkinTone.toneIndex,
            preferred = if (emojiSettings.toneOverrideByLastUsed) {
                emojiUsage.preferredVariant(emoji)
            } else {
                null
            },
            overrideWithPreferred = emojiSettings.toneOverrideByLastUsed,
        )
    }

    /**
     * Recomputes [KeyboardUiState.hiddenEmoji] — the emoji the active font
     * can't draw — for the current font and toggle. A no-op that just clears
     * the set when the feature is off or the catalog hasn't loaded yet.
     */
    private fun recomputeHiddenEmoji(settings: KeyboardSettings) {
        val catalog = emojiEntries
        if (!settings.emoji.hideUnrenderable || catalog.isEmpty()) {
            if (_uiState.value.hiddenEmoji.isNotEmpty()) {
                _uiState.update { it.copy(hiddenEmoji = emptySet()) }
            }
            return
        }
        serviceScope.launch {
            val hidden = withContext(Dispatchers.Default) {
                val typeface = KeyboardFonts.emojiTypeface(this@WMKeyboardService, settings.emojiFont)
                EmojiRenderCheck.unrenderable(catalog.map { it.emoji }, typeface)
            }
            _uiState.update { it.copy(hiddenEmoji = hidden) }
        }
    }

    fun onEmojiTapped(emoji: String) {
        vibrate()
        currentInputConnection?.commitText(emoji, 1)
        learnEmoji(emoji)
        emojiUsage.record(emoji)
        // "Return to keyboard after emoji": one insertion from the panel drops
        // straight back to the keys instead of keeping the panel open for a run.
        val closeAfter = _uiState.value.settings.emoji.closeAfterInsert &&
            _uiState.value.panel == PanelMode.EMOJI
        _uiState.update {
            it.copy(
                emojiRecents = emojiUsage.recents(),
                emojiFrequents = emojiUsage.frequents(),
                panel = if (closeAfter) PanelMode.NONE else it.panel,
                emojiSearchActive = if (closeAfter) false else it.emojiSearchActive,
                emojiQuery = if (closeAfter) "" else it.emojiQuery,
                emojiResults = if (closeAfter) emptyList() else it.emojiResults,
            )
        }
    }

    /**
     * A variant picked from the long-press popup: commit it and remember it
     * as the preferred face of [base] so the grid shows it from now on.
     * Picking the plain base resets the preference.
     */
    fun onEmojiVariantPicked(base: String, variant: String) {
        emojiUsage.setPreferredVariant(base, variant)
        _uiState.update { it.copy(emojiVariantPrefs = emojiUsage.variantPrefs()) }
        onEmojiTapped(variant)
    }

    fun onEmojiFavouriteToggled(emoji: String) {
        vibrate()
        emojiUsage.toggleFavourite(emoji)
        emojiUsage.save()
        _uiState.update {
            it.copy(
                emojiFavourites = emojiUsage.favourites(),
                emojiRecents = emojiUsage.recents(),
                emojiFrequents = emojiUsage.frequents(),
            )
        }
    }

    fun onEmojiFavouritesReordered(order: List<String>) {
        vibrate()
        emojiUsage.reorderFavourites(order)
        emojiUsage.save()
        _uiState.update {
            it.copy(
                emojiFavourites = emojiUsage.favourites(),
                emojiRecents = emojiUsage.recents(),
                emojiFrequents = emojiUsage.frequents(),
            )
        }
    }

    fun onEmojiRecentsClear() {
        vibrate()
        emojiUsage.clearRecents()
        emojiUsage.save()
        _uiState.update { it.copy(emojiRecents = emojiUsage.recents()) }
    }

    /** Long-press "remove" on a history cell: the emoji leaves recents,
     * most-used counts, and favourites in one go. */
    fun onEmojiRecentRemoved(emoji: String) {
        vibrate()
        emojiUsage.removeFromHistory(emoji)
        emojiUsage.save()
        _uiState.update {
            it.copy(
                emojiFavourites = emojiUsage.favourites(),
                emojiRecents = emojiUsage.recents(),
                emojiFrequents = emojiUsage.frequents(),
            )
        }
    }

    /**
     * The emoji search bar's backspace: while search is active the keys
     * edit the query, so this is the only way to delete the emoji (or any
     * text) just committed to the real field without leaving search.
     */
    fun onEmojiSearchFieldDelete() {
        vibrate()
        deleteFromField()
    }

    /**
     * Whether a backspace press would still delete anything, honoring
     * whatever the backspace key currently edits (an active search query,
     * or the field). Held-repeat loops poll this to stop at empty.
     */
    fun canDelete(): Boolean {
        val state = _uiState.value
        return when {
            (state.panel == PanelMode.HANDWRITING || keyboardHandwriteActive(state)) &&
                state.handwriting.strokes.isNotEmpty() -> true
            state.emojiSearchActive -> state.emojiQuery.isNotEmpty()
            state.dictionarySearchActive -> state.dictionaryQuery.isNotEmpty()
            state.mediaSearchActive && state.panel.hasMediaSearch -> state.mediaQuery.isNotEmpty()
            else -> canDeleteField()
        }
    }

    /** [canDelete] scoped to the real field only, for field-direct controls. */
    fun canDeleteField(): Boolean {
        if (composing.isNotEmpty()) return true
        val ic = currentInputConnection ?: return false
        if (hasSelection(ic)) return true
        // A null answer means the editor can't say — keep deleting rather
        // than stopping a working backspace; only a definite "" stops it.
        val before = ic.getTextBeforeCursor(1, 0) ?: return true
        return before.isNotEmpty()
    }

    /**
     * An emoji candidate from the suggestion strip. In [EmojiInsertMode.REPLACE]
     * (Gboard semantics) committing over the active composing region swaps
     * the typed word for the emoji; in [EmojiInsertMode.APPEND] the word is
     * kept ("birthday 🎂") and learned like a normal commit.
     */
    fun onEmojiSuggestionTapped(emoji: String) {
        vibrate()
        val ic = currentInputConnection ?: return
        lastGestureWord = null
        val word = composing.toString()
        if (_uiState.value.settings.emojiInsertMode == EmojiInsertMode.APPEND && word.isNotEmpty()) {
            ic.finishComposingText()
            ic.commitText(" $emoji", 1)
            learn(word)
        } else {
            ic.commitText(emoji, 1)
        }
        learnEmoji(emoji)
        emojiUsage.record(emoji)
        composing = StringBuilder()
        _uiState.update {
            it.copy(
                composingPreview = "",
                suggestions = emptyList(),
                emojiSuggestions = emptyList(),
                emojiRecents = emojiUsage.recents(),
                emojiFrequents = emojiUsage.frequents(),
            )
        }
        refreshSuggestions()
    }

    fun onEmojiSearchToggled() {
        _uiState.update { it.copy(emojiSearchActive = !it.emojiSearchActive) }
    }

    private fun refreshEmojiResults() {
        val search = emojiSearch ?: return
        val query = _uiState.value.emojiQuery
        serviceScope.launch {
            val hidden = _uiState.value.hiddenEmoji
            val results = withContext(Dispatchers.Default) { search.search(query) }
            val shown = if (hidden.isEmpty()) results else results.filterNot { it.emoji in hidden }
            _uiState.update { it.copy(emojiResults = shown) }
        }
    }

    fun onClipboardItemTapped(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        vibrate()
        when (item.kind) {
            ClipKind.IMAGE -> commitImageClip(item)
            ClipKind.FILE -> commitFileClip(item)
            // A folder is a container, not content — there is nothing to attach
            // to a text field, so insert its name and hand the URI back to the
            // system clipboard for a file manager to paste.
            ClipKind.FOLDER -> {
                currentInputConnection?.commitText(item.fileName.orEmpty(), 1)
                item.uriString?.let { copyUriToSystemClipboard(Uri.parse(it), item.fileName.orEmpty()) }
                Toast.makeText(
                    this,
                    "Folders can't be inserted — name typed, folder copied for pasting elsewhere",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            else -> currentInputConnection?.commitText(item.text, 1)
        }
        // Whether tapped from the panel or the strip chip, the recent-copy chip
        // has served its purpose once something was pasted.
        clearClipboardSuggestion()
    }

    /**
     * Shows [item] as the recently-copied paste chip on the suggestion strip and
     * arms its auto-hide timer, replacing any chip already up.
     */
    private fun showClipboardSuggestion(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        clipboardSuggestionJob?.cancel()
        _uiState.update { it.copy(clipboardSuggestion = item) }
        clipboardSuggestionJob = serviceScope.launch {
            delay(CLIPBOARD_SUGGESTION_TIMEOUT_MS)
            _uiState.update { it.copy(clipboardSuggestion = null) }
        }
    }

    /** Drops the recently-copied strip chip (pasted, dismissed, or feature off). */
    private fun clearClipboardSuggestion() {
        clipboardSuggestionJob?.cancel()
        clipboardSuggestionJob = null
        if (_uiState.value.clipboardSuggestion != null) {
            _uiState.update { it.copy(clipboardSuggestion = null) }
        }
    }

    /** The user swiped away the recently-copied strip chip. */
    fun onClipboardSuggestionDismiss() {
        clearClipboardSuggestion()
    }

    /**
     * Attaches a copied file via commitContent when the editor accepts its
     * MIME type. Unlike image clips we don't own these bytes, so the URI grant
     * may already be gone; either way the fallback puts the file back on the
     * system clipboard so a long-press paste still works.
     */
    private fun commitFileClip(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        val uri = item.uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        val label = item.fileName.orEmpty()
        val ic = currentInputConnection
        val editorInfo = currentInputEditorInfo
        val supported = editorInfo != null &&
            EditorInfoCompat.getContentMimeTypes(editorInfo)
                .any { ClipDescription.compareMimeTypes(item.mimeType, it) }

        if (ic != null && editorInfo != null && supported) {
            val committed = runCatching {
                InputConnectionCompat.commitContent(
                    ic,
                    editorInfo,
                    InputContentInfoCompat(
                        uri,
                        ClipDescription(label, arrayOf(item.mimeType)),
                        null,
                    ),
                    0,
                    null,
                )
            }.getOrDefault(false)
            if (committed) return
        }
        copyUriToSystemClipboard(uri, label)
        Toast.makeText(
            this,
            "This app doesn't accept files here — file copied, paste it instead",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun copyUriToSystemClipboard(uri: Uri, label: String) {
        runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newUri(contentResolver, label, uri))
        }
    }

    /**
     * Inserts an image clip with the commitContent API. Editors advertise the
     * MIME types they accept in EditorInfo; when the current one doesn't take
     * images, put the image back on the system clipboard so a long-press
     * paste still works.
     */
    private fun commitImageClip(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        val file = item.imagePath?.let(::File)?.takeIf { it.exists() } ?: run {
            clipboardStore.remove(item.id)
            clipboardStore.save()
            _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
            return
        }
        commitImageFile(file, item.mimeType)
    }

    /**
     * Commits any image file (clipboard clip, camera capture, downloaded
     * GIF/sticker/search result) via commitContent.
     *
     * The target field advertises what it takes in EditorInfo, so rather
     * than guessing per app we offer [MediaMime.candidates] in preference
     * order and send the first one it accepts — WhatsApp gets a real
     * sticker, everything else degrades to a plain image on its own. When
     * nothing matches we try a PNG re-encode (WhatsApp accepts image/png
     * but not image/webp, so a WebP would otherwise never arrive), and
     * only then fall back to the system clipboard.
     */
    private fun commitImageFile(
        file: File,
        mimeType: String,
        sendMode: MediaSendMode = MediaSendMode.IMAGE,
    ) {
        val editorInfo = currentInputEditorInfo
        val accepted = editorInfo
            ?.let { EditorInfoCompat.getContentMimeTypes(it).toList() }
            .orEmpty()

        val chosen = MediaMime.candidates(mimeType, sendMode).firstOrNull { candidate ->
            accepted.any { ClipDescription.compareMimeTypes(candidate, it) }
        }
        if (chosen != null && tryCommit(file, chosen)) return

        // Nothing matched. A WebP the field won't take can usually go
        // through as PNG; animated WebP would lose its animation that way,
        // so leave those for the clipboard instead of silently flattening.
        if (chosen == null && mimeType == MediaMime.WEBP &&
            accepted.any { ClipDescription.compareMimeTypes(MediaMime.PNG, it) }
        ) {
            val png = transcodeToPng(file)
            if (png != null && tryCommit(png, MediaMime.PNG)) return
        }

        val contentUri = runCatching {
            FileProvider.getUriForFile(this, clipboardFileProviderAuthority, file)
        }.getOrNull() ?: return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newUri(contentResolver, "image", contentUri))
        Toast.makeText(this, "This app doesn't accept images here — image copied, paste it instead", Toast.LENGTH_SHORT).show()
    }

    /** One commitContent attempt with a settled MIME type. */
    private fun tryCommit(file: File, mimeType: String): Boolean {
        val ic = currentInputConnection ?: return false
        val editorInfo = currentInputEditorInfo ?: return false
        val contentUri = runCatching {
            FileProvider.getUriForFile(this, clipboardFileProviderAuthority, file)
        }.getOrNull() ?: return false

        return runCatching {
            InputConnectionCompat.commitContent(
                ic,
                editorInfo,
                InputContentInfoCompat(
                    contentUri,
                    ClipDescription("image", arrayOf(mimeType)),
                    null,
                ),
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
        }.getOrDefault(false)
    }

    /**
     * Re-encodes a still image as PNG in the media cache. Returns null for
     * animated sources (their animation would be lost) and anything that
     * won't decode.
     */
    private fun transcodeToPng(file: File): File? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(file)
            val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
            if (drawable is android.graphics.drawable.AnimatedImageDrawable) return null
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val dir = File(cacheDir, "media").apply { mkdirs() }
        val target = File(dir, "${file.nameWithoutExtension}_png.png")
        target.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        target
    }.getOrNull()

    fun onClipboardPin(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        clipboardStore.setPinned(item.id, !item.pinned)
        clipboardStore.save()
        _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
    }

    fun onClipboardDelete(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        clipboardStore.remove(item.id)
        clipboardStore.save()
        _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
    }

    fun onOneHandedChange(mode: OneHandedMode) {
        vibrate()
        serviceScope.launch { settingsRepository.setOneHandedMode(mode) }
    }

    fun onOneHandedSideChange(landscape: Boolean, side: OneHandedSide) {
        serviceScope.launch { settingsRepository.setOneHandedSide(landscape, side) }
    }

    fun onToggleSplit() {
        vibrate()
        val current = _uiState.value.settings.splitKeyboard
        serviceScope.launch { settingsRepository.setSplitKeyboard(!current) }
    }

    /**
     * The mode whose tool arrangement a drag should be written into, or null
     * to write the global one. A mode that prescribes its own tools wins over
     * the global lists while it is active, so storing the drag globally would
     * be silently overwritten — the tool would spring straight back.
     */
    private fun toolOrderOwner(): KeyboardMode? {
        val settings = _uiState.value.settings
        if (!settings.modeToolOrderEdits) return null
        return settings.keyboardModes
            .firstOrNull { it.id == _uiState.value.activeModeId }
            ?.takeIf { it.ownsToolOrder }
    }

    /**
     * One-off heads-up the first time a drag is stored against a mode: the
     * same keyboard will look different in an app that resolves to another
     * mode, and without this that reads as the change having been lost.
     */
    private fun noteModeToolOrder(mode: KeyboardMode) {
        if (_uiState.value.settings.modeToolOrderHintSeen) return
        Toast.makeText(
            this,
            "Saved for ${mode.name} mode — other apps keep their own tool order.",
            Toast.LENGTH_LONG,
        ).show()
        serviceScope.launch { settingsRepository.setModeToolOrderHintSeen(true) }
    }

    fun onToolbarToolsChange(tools: List<ToolbarTool>) {
        vibrate()
        val mode = toolOrderOwner()
        serviceScope.launch {
            if (mode != null) settingsRepository.setModeToolbarTools(mode.id, tools)
            else settingsRepository.setToolbarTools(tools)
        }
        if (mode != null) noteModeToolOrder(mode)
    }

    fun onToolboxOrderChange(order: List<ToolbarTool>) {
        vibrate()
        val mode = toolOrderOwner()
        serviceScope.launch {
            if (mode != null) settingsRepository.setModeToolboxOrder(mode.id, order)
            else settingsRepository.setToolboxOrder(order)
        }
        if (mode != null) noteModeToolOrder(mode)
    }

    /**
     * On Android 13+ the IME's back handling goes through the
     * OnBackInvokedDispatcher, never [onKeyDown] — register a callback while
     * a panel is open so back closes the panel; unregister when none is,
     * letting the system's default callback hide the keyboard as usual.
     */
    private var panelBackCallback: android.window.OnBackInvokedCallback? = null

    private fun updatePanelBackCallback(panelOpen: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = window?.window?.onBackInvokedDispatcher ?: return
        if (panelOpen && panelBackCallback == null) {
            val callback = android.window.OnBackInvokedCallback {
                if (_uiState.value.voice.strip) {
                    closeVoiceStrip()
                } else {
                    val panel = _uiState.value.panel
                    if (panel != PanelMode.NONE) onPanelChange(panel)
                }
            }
            dispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            panelBackCallback = callback
        } else if (!panelOpen && panelBackCallback != null) {
            panelBackCallback?.let { dispatcher.unregisterOnBackInvokedCallback(it) }
            panelBackCallback = null
        }
    }

    /**
     * Back with a tool panel (emoji, clipboard, snippets, toolbox) open
     * returns to the plain keyboard instead of hiding the IME (pre-T path).
     * Consume the DOWN too so the app underneath never sees half an event
     * stream.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown &&
            (_uiState.value.panel != PanelMode.NONE || _uiState.value.voice.strip)
        ) {
            return true
        }
        if (volumeCursorDelta(keyCode) != 0) {
            // Auto-repeat rides along for free: holding the key repeats DOWN.
            onCursorMove(volumeCursorDelta(keyCode))
            return true
        }
        if (handleHardwareKeyDown(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Swallow the UP of any DOWN this IME consumed, so the focused app
        // never sees half a physical keypress. Checked before the BACK/volume
        // handling, which never registers its keys here.
        if (consumedHardwareKeys.remove(keyCode)) return true
        val panel = _uiState.value.panel
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown &&
            (panel != PanelMode.NONE || _uiState.value.voice.strip)
        ) {
            if (_uiState.value.voice.strip) closeVoiceStrip() else onPanelChange(panel)
            return true
        }
        // Swallow the UP too, so the system never sees half a volume event.
        if (volumeCursorDelta(keyCode) != 0) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Physical keycodes whose DOWN this IME consumed, so the matching UP is
     * swallowed too. A set rather than a flag because auto-repeat and modifier
     * chords can leave several keys down at once.
     */
    private val consumedHardwareKeys = HashSet<Int>()

    /**
     * Routes a physical-keyboard press through the same pipeline as the
     * on-screen keys — transliteration, the composing buffer, suggestions,
     * autocorrect — so a hardware keyboard is a first-class input source rather
     * than a raw bypass of the IME.
     *
     * Returns true when the event was consumed. False hands the key back to the
     * system unchanged — cursor and function keys, shortcuts, and every key at
     * all when the field or the [KeyboardSettings.hardwareKeyboardInput] setting
     * doesn't want IME processing — after first committing any composing text so
     * it is never stranded by the cursor move or shortcut about to run.
     */
    private fun handleHardwareKeyDown(event: KeyEvent): Boolean {
        val ic = currentInputConnection ?: return false
        val keyCode = event.keyCode

        // Bare modifier presses latch in the system (Shift for a capital, Ctrl
        // to open a chord). Never consumed, and never a commit trigger — a
        // Ctrl+Shift+Arrow selection opens with a lone modifier down.
        if (KeyEvent.isModifierKey(keyCode)) return false

        val state = _uiState.value

        // A modifier-driven shortcut belongs to the app (Ctrl+C, Meta+Space).
        // AltGr arrives as Ctrl+Alt *together* and is not a shortcut — it
        // produces characters (German AltGr+Q = @), so it falls through to the
        // text path where getUnicodeChar decodes it against the held meta state.
        val ctrl = event.metaState and KeyEvent.META_CTRL_ON != 0
        val alt = event.metaState and KeyEvent.META_ALT_ON != 0
        val meta = event.metaState and KeyEvent.META_META_ON != 0
        val shortcut = meta || (ctrl && !alt)

        if (!state.settings.hardwareKeyboardInput || shortcut || !hardwareIntercepts(state)) {
            // Handing the key back: finish composing first so a moving cursor or
            // a shortcut never strands the buffer. Skipped for the bare modifier
            // case above, which returned before reaching here.
            if (composing.isNotEmpty()) commitComposing(ic, autocorrect = false)
            return false
        }

        stopVoiceForManualInput()
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                clearForHardwareTyping()
                onSpace()
                consumedHardwareKeys.add(keyCode)
                true
            }
            KeyEvent.KEYCODE_DEL -> {
                // Routed through onDelete so it edits the composing buffer, undoes
                // a swipe/autocorrect, and deletes whole grapheme clusters — all
                // of which a raw backspace against the field would get wrong.
                onDelete()
                consumedHardwareKeys.add(keyCode)
                true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // The soft Enter's own handler: commits the buffer, then runs the
                // field's editor action (or a real newline), and — the reason it
                // can't just pass through — runs an active panel/dictionary search
                // instead of dropping a newline into the app behind the panel.
                onEnter()
                consumedHardwareKeys.add(keyCode)
                true
            }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_FORWARD_DEL,
            -> {
                // The system owns these (field navigation, forward delete). Commit
                // the buffer so it lands before them.
                if (composing.isNotEmpty()) commitComposing(ic, autocorrect = false)
                false
            }
            else -> {
                val unicode = event.unicodeChar
                if (unicode == 0 || unicode and KeyCharacterMap.COMBINING_ACCENT != 0) {
                    // A non-printing key (arrows, F-keys, Home/End) or a physical
                    // dead key the framework will compose: hand it back, buffer
                    // committed first.
                    if (composing.isNotEmpty()) commitComposing(ic, autocorrect = false)
                    false
                } else {
                    clearForHardwareTyping()
                    // Literal: the char already carries the physical layout's
                    // shift/AltGr, so the soft shift state must not re-case it.
                    processTypedText(unicode.toChar().toString(), applyDeadKeys = false)
                    consumedHardwareKeys.add(keyCode)
                    true
                }
            }
        }
    }

    /**
     * The IME should own physical input right now: a transliterating or
     * suggestion-composing field, or an open panel search / typing test whose
     * keys feed a query rather than the app behind. Mirrors the soft keyboard's
     * own composing gate ([onTextKey]'s `composingMode`) so the two stay in step
     * — a field that composes taps composes hardware keys the same way.
     */
    private fun hardwareIntercepts(state: KeyboardUiState): Boolean {
        val composingMode = !state.composer.isClusterShaping &&
            (
                state.composer.isTransliterating ||
                    (state.allowsTypingIntelligence && state.settings.suggestions)
                )
        return composingMode || state.emojiSearchActive ||
            (state.mediaSearchActive && state.panel.hasMediaSearch) ||
            state.dictionarySearchActive || state.typingTestActive
    }

    /**
     * The [onKey] preamble a hardware character or space needs: drop the recent-
     * copy paste chip, the swipe-undo word, and any armed auto-space, exactly as
     * a soft key does. Backspace is deliberately excluded — it keeps the swipe
     * word so one press can undo a whole glide.
     */
    private fun clearForHardwareTyping() {
        clearClipboardSuggestion()
        lastGestureWord = null
        pendingAutoSpace = false
    }

    /**
     * Cursor step a volume key should produce right now, or 0 to let the key
     * through to the system. Down is left and up is right, matching the way
     * the keys sit on the phone when it is held upright.
     *
     * The media-aware option re-checks playback on every event rather than
     * latching, so starting or stopping a song swaps the behaviour instantly.
     */
    private fun volumeCursorDelta(keyCode: Int): Int {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            else -> return 0
        }
        val settings = _uiState.value.settings
        if (!settings.volumeCursor || !isInputViewShown) return 0
        if (settings.volumeCursorMediaAware &&
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        ) {
            return 0
        }
        return delta
    }

    /** Re-reads every imported word list from disk into per-language tries. */
    private fun loadCustomDictionaries(): Map<String, WordSource> {
        CustomDictionaries.migrateLegacyFolders(filesDir)
        return LanguageRegistry.all.associate { it.id to CustomDictionaries.trie(filesDir, it.id) }
    }

    /**
     * Bengali index over the bundled list plus any imported Bengali list, so
     * imported words are reachable by transliteration and not only by prefix.
     */
    private fun buildBengaliIndex(): BengaliPhoneticIndex =
        BengaliPhoneticIndex(
            bengaliAssetEntries + CustomDictionaries.entries(filesDir, "bn"),
        )

    fun openSettings() {
        vibrate()
        if (currentInputEditorInfo?.packageName == packageName) {
            Toast.makeText(this, "Already in settings", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Long-press on a tool during customization: its settings page. */
    fun openToolSettings(tool: ToolbarTool) {
        vibrate()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_OPEN_TOOL, tool.name)
            }
        )
    }

    // ---- helpers ----

    private fun hasSelection(ic: InputConnection): Boolean =
        !ic.getSelectedText(0).isNullOrEmpty()

    /**
     * Re-evaluates the one-shot shift state after the cursor moved or text
     * changed: turns shift on at a sentence start, off when no longer at
     * one. Caps lock is never touched.
     */
    private fun refreshShiftForContext() {
        val target = autoCapitalizeShift()
        _uiState.update {
            when {
                it.shiftState == ShiftState.CAPS_LOCK -> it
                it.shiftState == ShiftState.OFF && target != ShiftState.OFF ->
                    it.copy(shiftState = target)
                it.shiftState == ShiftState.ON && it.settings.autoCapitalize &&
                    target == ShiftState.OFF -> it.copy(shiftState = ShiftState.OFF)
                else -> it
            }
        }
    }

    /**
     * The shift state the focused field's caps mode calls for right now.
     *
     * TYPE_TEXT_FLAG_CAP_CHARACTERS means the whole field is upper case
     * (licence plates, coupon codes), so it maps to CAPS_LOCK — a one-shot
     * shift would capitalize the first letter and drop off. The word- and
     * sentence-level modes are one-shot by nature and come back through
     * [InputConnection.getCursorCapsMode], which weighs the flags against
     * the text actually before the cursor.
     *
     * EditorInfo.initialCapsMode is the fallback for the window between
     * onStartInput and a live connection: the framework computed it for
     * exactly this purpose.
     */
    private fun autoCapitalizeShift(): ShiftState {
        val state = _uiState.value
        if (!state.settings.autoCapitalize) return ShiftState.OFF
        // Sentence capitalization applies to every Latin-script language;
        // Bengali has no letter case.
        if (!state.script.hasLetterCase) return ShiftState.OFF
        val info = currentInputEditorInfo ?: return ShiftState.OFF
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) {
            return ShiftState.OFF
        }
        if (info.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
            return ShiftState.CAPS_LOCK
        }
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType)
            ?: info.initialCapsMode
        return if (caps != 0) ShiftState.ON else ShiftState.OFF
    }

    private fun shouldAutoCapitalize(): Boolean = autoCapitalizeShift() != ShiftState.OFF

    private fun maybeAutoCapitalize() {
        val target = autoCapitalizeShift()
        _uiState.update {
            if (target != ShiftState.OFF && it.shiftState == ShiftState.OFF) {
                it.copy(shiftState = target)
            } else it
        }
    }

    // A vibrate() call while the previous effect is still playing cancels it,
    // so two presses landing within a few tens of ms (rollover typing, burst
    // double-taps) collapse into what feels like a single buzz. Enforce a
    // minimum spacing: the second buzz is deferred just enough to be felt as
    // its own click. Bursts coalesce to at most one pending buzz.
    private var lastVibrateAt = 0L
    private var vibratePending = false

    private fun vibrate() {
        // Key sound rides along with every feedback point; it has no
        // interference problem, so it skips the haptic coalescing below.
        playKeySound()
        vibrateOnly()
    }

    /**
     * Haptic without the key sound — for cues that are not a keypress, like
     * an emoji long-press opening its popup. The click sound there would say
     * "emoji inserted", which is exactly what did not happen.
     */
    private fun vibrateOnly() {
        val now = SystemClock.uptimeMillis()
        val wait = MIN_HAPTIC_GAP_MS - (now - lastVibrateAt)
        if (wait <= 0) {
            lastVibrateAt = now
            doVibrate()
        } else if (!vibratePending) {
            vibratePending = true
            serviceScope.launch {
                delay(wait)
                vibratePending = false
                lastVibrateAt = SystemClock.uptimeMillis()
                doVibrate()
            }
        }
    }

    /**
     * Plays the key-press sound via [KeySoundPlayer]. [force] previews even
     * while the setting is off (the quick panel's toggle fires before the
     * DataStore write lands).
     */
    private fun playKeySound(
        style: com.wasimaster.wmkeyboard.core.settings.KeySoundStyle? = null,
        volume: Float? = null,
        force: Boolean = false,
    ) {
        val settings = _uiState.value.settings
        if (!force && !settings.keySound) return
        KeySoundPlayer.play(this, style ?: settings.keySoundStyle, volume ?: settings.keySoundVolume)
    }

    private fun doVibrate() {
        val settings = _uiState.value.settings
        if (!settings.hapticFeedback) return
        if (settings.feedback.hapticsRespectDnd && isDndActive()) return
        HapticPlayer.play(
            this,
            settings.hapticStyle,
            settings.hapticAmplitude,
            settings.hapticStrengthMs,
            inputRootView,
        )
    }

    /**
     * Whether the system is currently in Do Not Disturb. Reads the `zen_mode`
     * global first — it's non-zero for every DND flavour and needs no
     * permission, unlike [NotificationManager.getCurrentInterruptionFilter]
     * which reports UNKNOWN without notification-policy access on some OEMs.
     * Falls back to the interruption filter where the global is unreadable.
     */
    private fun isDndActive(): Boolean {
        val zen = runCatching {
            android.provider.Settings.Global.getInt(contentResolver, "zen_mode", 0)
        }.getOrDefault(0)
        if (zen != 0) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = nm.currentInterruptionFilter
            return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        return false
    }

    companion object {
        /** Minimum spacing between haptic clicks so rapid presses stay distinct. */
        private const val MIN_HAPTIC_GAP_MS = 45L

        /**
         * A caret still being dragged along by a spacebar/volume scrub does not
         * resume the word it passes over: each drag step commits the composing
         * buffer first, so resuming a word one step only to re-commit it the
         * next would churn the field (and re-learn the word) on every step.
         * Longer than the gap between drag steps, so a continuous scrub stays
         * suppressed; a genuine settle re-reads on the next tap or keystroke.
         */
        private const val CARET_SCRUB_WINDOW_MS = 250L

        /** Height offered to autofill chips, matching the suggestion strip. */
        private const val INLINE_CHIP_HEIGHT_DP = 44

        /** Inline emoji search is a local index lookup — no network wait. */
        private const val EMOJI_SEARCH_DEBOUNCE_MS = 24L
        private const val INLINE_EMOJI_LIMIT = 12

        /** How many contact-email completions the email-field strip may show. */
        private const val EMAIL_FIELD_SUGGESTION_LIMIT = 5

        /** Shortest token before the cursor that triggers an email completion. */
        private const val EMAIL_FIELD_MIN_PREFIX = 2

        /** How far back to read the token being completed in an email field. */
        private const val EMAIL_FIELD_LOOKBEHIND = 96

        /** Non-alphanumeric characters that are part of an email token. */
        private const val EMAIL_TOKEN_EXTRA = "._%+-@"

        /**
         * Floor for the handwriting recognition pause in Bengali. Its
         * multi-stroke conjuncts need more finger-up time between strokes than
         * the Latin default, so recognition doesn't fire mid-glyph.
         */
        private const val BENGALI_HW_MIN_COMMIT_DELAY_MS = 1200L
        private const val WEATHER_CACHE_MS = 15L * 60 * 1000
        private val SENTENCE_ENDERS = charArrayOf('.', '!', '?', '।')
        private const val SHIFT_DOUBLE_TAP_MS = 350L

        /**
         * Quick-insert punctuation offered in the tail of the suggestion strip
         * when the "Punctuation suggestions" setting is on — the marks people
         * reach for mid-sentence, kept short so they don't crowd the word
         * candidates. Tapping one behaves exactly like typing its key.
         */
        private val PUNCTUATION_SUGGESTIONS = listOf(".", ",", "?", "!", "'")

        /**
         * Opening bracket/brace/quote → its closer. Typing one of these with a
         * selection wraps the selected text in the pair. Symmetric quotes map
         * to themselves; closers are deliberately absent so pressing ")" over a
         * selection still just replaces it, as every keyboard does.
         */
        private val WRAP_PAIRS: Map<Char, String> = mapOf(
            '(' to ")", '[' to "]", '{' to "}", '<' to ">",
            '"' to "\"", '\'' to "'", '`' to "`",
            '“' to "”", '‘' to "’", '«' to "»", '｢' to "｣",
        )
        /** Cap on files recorded from one multi-select copy. */
        private const val MAX_FILE_CLIPS_PER_COPY = 20
        /** How long the recently-copied paste chip lingers on the strip before auto-hiding. */
        private const val CLIPBOARD_SUGGESTION_TIMEOUT_MS = 60_000L
        /** Links fetched per panel open, so a history of links isn't a request storm. */
        private const val MAX_LINK_PREVIEWS = 8

        /**
         * What the enter key should do and show. IME_FLAG_NO_ENTER_ACTION
         * means the app wants a literal newline no matter what action it
         * declared, so it wins outright — that is the flag multi-line fields
         * carry, and honouring it is what keeps Enter from sending a
         * half-written message.
         *
         * A non-null actionLabel is the app asking for its own wording
         * ("Reply", "Post"); it is paired with [EditorInfo.actionId] rather
         * than a standard action, so it is reported separately.
         */
        private fun EditorInfo?.enterAction(): EnterAction {
            val info = this ?: return EnterAction.DEFAULT
            val options = info.imeOptions
            if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.DEFAULT
            if (!info.actionLabel.isNullOrBlank()) return EnterAction.CUSTOM
            return when (options and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
                EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
                EditorInfo.IME_ACTION_GO -> EnterAction.GO
                EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
                EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
                EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
                else -> EnterAction.DEFAULT
            }
        }

        /**
         * The editor action to fire, or null when Enter should type a
         * newline instead. Mirrors [enterAction] so the key does what it
         * draws: a custom label fires the app's own [EditorInfo.actionId],
         * everything else the masked standard action.
         */
        private fun EditorInfo?.editorActionId(): Int? {
            val info = this ?: return null
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
            if (!info.actionLabel.isNullOrBlank()) return info.actionId
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action == EditorInfo.IME_ACTION_NONE ||
                action == EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                return null
            }
            return action
        }

        /**
         * IME_FLAG_FORCE_ASCII: the field can only take ASCII (a server-side
         * username, a coupon code). Bengali modes — including Avro, whose
         * roman keys still commit Bengali — would put characters in it the
         * app cannot use, so the field is typed in a Latin mode instead.
         * Prefers one the user actually enabled over hard-coding English.
         */
        private fun EditorInfo?.forcesAscii(): Boolean =
            this != null && imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0

        /**
         * EditorInfo.hintLocales: the app naming the language it expects
         * (a "translate to French" box, a per-language form field). Honoured
         * only when the user has a mode for that language enabled — it is a
         * hint, not a licence to switch to a layout they never set up.
         */
        private fun EditorInfo?.hintedLanguage(enabled: List<LanguageDef>): LanguageDef? {
            val hints = this?.hintLocales ?: return null
            for (i in 0 until hints.size()) {
                val lang = LanguageRegistry.byLocale(hints[i].language) ?: continue
                enabled.firstOrNull { it.id == lang.id }?.let { return it }
            }
            return null
        }

        private fun EditorInfo?.fieldKind(): FieldKind {
            val inputType = this?.inputType ?: return FieldKind.TEXT
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> FieldKind.NUMBER
                InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
                InputType.TYPE_CLASS_DATETIME ->
                    when (inputType and InputType.TYPE_MASK_VARIATION) {
                        InputType.TYPE_DATETIME_VARIATION_DATE -> FieldKind.DATE
                        InputType.TYPE_DATETIME_VARIATION_TIME -> FieldKind.TIME
                        else -> FieldKind.DATETIME
                    }
                else -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    -> FieldKind.EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URI
                    else -> FieldKind.TEXT
                }
            }
        }

        /**
         * The field asked the IME not to personalize from it. Chrome sets
         * this on every input in an incognito tab, and Firefox, Samsung
         * Internet and a few password managers do the same for their private
         * surfaces; it is the only signal Android gives us, since an IME
         * cannot see what tab or mode the host app is in.
         *
         * [EditorInfo.privateImeOptions] is checked too because some apps
         * still only send the pre-Oreo Gboard-era string.
         */
        private fun EditorInfo?.requestsNoPersonalizedLearning(): Boolean {
            val info = this ?: return false
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
            return info.privateImeOptions
                ?.split(',')
                ?.any { it.trim().endsWith("noPersonalizedLearning", ignoreCase = true) }
                ?: false
        }

        /**
         * Whether to hide the *suggestion strip* for this field. This governs
         * the strip only — autocorrect, gesture typing, phonetic composing and
         * learning are gated separately on the field kind
         * ([KeyboardUiState.allowsTypingIntelligence]), so silencing the strip
         * never disables them.
         *
         * @param overrideAppRequest the "Suggestions in every field" setting.
         * When on, the field's plea for a silent strip (the NO_SUGGESTIONS
         * flag, email/URI/filter variations) is ignored and the strip shows
         * anyway. Two things are never overridden: password variations (always
         * secret) and non-text classes, whose keypads have no words to offer.
         */
        private fun EditorInfo?.suppressesSuggestions(overrideAppRequest: Boolean): Boolean {
            val inputType = this?.inputType ?: return false
            if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
            val isPassword = when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> true
                else -> false
            }
            if (isPassword) return true
            if (overrideAppRequest) return false
            if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return true
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_FILTER,
                -> true
                else -> false
            }
        }

        private fun EditorInfo?.isSecureField(): Boolean {
            val inputType = this?.inputType ?: return false
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val typeClass = inputType and InputType.TYPE_MASK_CLASS
            return typeClass == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                ) || (
                typeClass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                )
        }
    }
}
