package com.wasimaster.wmkeyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
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
import com.wasimaster.wmkeyboard.app.CameraPermissionActivity
import com.wasimaster.wmkeyboard.app.MainActivity
import com.wasimaster.wmkeyboard.core.clipboard.ClipKind
import com.wasimaster.wmkeyboard.core.clipboard.ClipboardStore
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiSearch
import com.wasimaster.wmkeyboard.core.emoji.EmojiSuggester
import com.wasimaster.wmkeyboard.core.emoji.EmojiUsage
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariantIndex
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
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
import com.wasimaster.wmkeyboard.core.prediction.ContactNames
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.EnglishBengaliMap
import com.wasimaster.wmkeyboard.core.prediction.SeedBigrams
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.settings.EmojiInsertMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.isFixedBengali
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import com.wasimaster.wmkeyboard.core.settings.GifSourceMode
import com.wasimaster.wmkeyboard.core.settings.WebSearchProvider
import com.wasimaster.wmkeyboard.core.tools.BraveSearchClient
import com.wasimaster.wmkeyboard.core.tools.DictionaryClient
import com.wasimaster.wmkeyboard.core.tools.GifItem
import com.wasimaster.wmkeyboard.core.tools.GifSource
import com.wasimaster.wmkeyboard.core.tools.GifSources
import com.wasimaster.wmkeyboard.core.tools.GiphyClient
import com.wasimaster.wmkeyboard.core.tools.GoogleSearchClient
import com.wasimaster.wmkeyboard.core.tools.ImageResult
import com.wasimaster.wmkeyboard.core.tools.KlipyClient
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.core.tools.ToolHttp
import com.wasimaster.wmkeyboard.core.tools.TranslateClient
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import com.wasimaster.wmkeyboard.core.tools.WebResult
import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import com.wasimaster.wmkeyboard.ime.layout.ClipboardKeyAction
import com.wasimaster.wmkeyboard.ime.layout.Key
import com.wasimaster.wmkeyboard.ime.layout.KeyAction
import com.wasimaster.wmkeyboard.ime.ui.KeyboardScreen
import android.inputmethodservice.InputMethodService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var snippetStore: SnippetStore

    private var composing = StringBuilder()
    private var previousWord: String? = null
    private var lastSpaceTime = 0L
    private var lastShiftTapTime = 0L
    private var suggestionJob: Job? = null

    /** Rolling average of one suggestion computation, drives the debounce. */
    private var suggestionCostMs = 0L

    /**
     * The last commit autocorrect changed, as typed-to-committed, so an
     * immediate backspace can undo the correction. Any other input clears it.
     */
    private var lastAutocorrect: Pair<String, String>? = null

    /** Contact-name words for suggestions, when the setting + permission allow. */
    private var contactNames: ContactNames = ContactNames.EMPTY

    /** English word list used by the gesture decoder (bundled dictionary). */
    private var gestureLexicon: List<Pair<String, Int>> = emptyList()

    /** Last word committed by a swipe, so tapping an alternate replaces it. */
    private var lastGestureWord: String? = null
    private var previewJob: Job? = null

    // ---- network tool state (translate, gif/sticker, web/image search) ----
    private var translateJob: Job? = null
    private var mediaFetchJob: Job? = null
    private var mediaLiveSearchJob: Job? = null
    private var mediaInsertJob: Job? = null
    private var webSearchJob: Job? = null
    private var imageSearchJob: Job? = null

    // ---- handwriting recognition state ----
    private val hwRecognizer = HandwritingRecognizerCache()
    private var hwJob: Job? = null
    /** Bumped on every stroke/undo/clear so in-flight recognitions go stale. */
    private var hwGeneration = 0
    private var hwCanvasSize = IntSize.Zero

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val state = _uiState.value
        if (!state.settings.clipboardHistory || state.settings.incognito || state.secureField) return@OnPrimaryClipChangedListener
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return@OnPrimaryClipChangedListener
        val item = clip.getItemAt(0) ?: return@OnPrimaryClipChangedListener

        val uri = item.uri
        // Skip clips we set ourselves (pasting an image re-copies it to the
        // system clipboard as a fallback); re-adding would duplicate it.
        if (uri != null && uri.authority == clipboardFileProviderAuthority) return@OnPrimaryClipChangedListener

        val imageMime = uri?.let { u ->
            runCatching { contentResolver.getType(u) }.getOrNull()?.takeIf { it.startsWith("image/") }
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
                    clipboardStore.addImage(copied, imageMime)
                    clipboardStore.save()
                    _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
                }
            }
            return@OnPrimaryClipChangedListener
        }

        val text = item.coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
        val html = item.htmlText
        if (html != null) clipboardStore.addHtml(text, html) else clipboardStore.add(text)
        clipboardStore.save()
        _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
    }

    private val clipboardFileProviderAuthority: String
        get() = "$packageName.clipboard"

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = KeyboardViewLifecycleOwner()
        lifecycleOwner.onCreate()

        settingsRepository = SettingsRepository(this)
        userLexicon = UserLexicon(File(filesDir, "learning/user_lexicon.json"))
        emojiUsage = EmojiUsage(File(filesDir, "learning/emoji_usage.json"))
        clipboardStore = ClipboardStore(
            File(filesDir, "clipboard/history.json"),
            imagesDir = File(filesDir, "clipboard/images"),
        )
        snippetStore = SnippetStore(File(filesDir, "snippets/snippets.json"))

        serviceScope.launch {
            var lexiconVersion = -1
            var contactsEnabled: Boolean? = null
            settingsRepository.settings.collect { settings ->
                clipboardStore.expiryMillis = settings.clipboardExpiryHours * 60L * 60 * 1000
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
                // The settings app edited the learned-words file (personal
                // dictionary): drop the in-memory copy for the disk state,
                // otherwise the next save here would clobber those edits.
                if (lexiconVersion != -1 && settings.lexiconVersion != lexiconVersion) {
                    withContext(Dispatchers.Default) { userLexicon.reload() }
                }
                lexiconVersion = settings.lexiconVersion
                _uiState.update { it.copy(settings = settings, inputMode = settings.inputMode) }
            }
        }

        // Dictionaries and the emoji catalog load off the main thread; the
        // keyboard is usable immediately and suggestions appear when ready.
        serviceScope.launch {
            val loaded = withContext(Dispatchers.Default) {
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
            val english = Trie().apply { for ((word, freq) in englishEntries) insert(word, freq) }
            gestureLexicon = englishEntries
            suggestionEngine = SuggestionEngine(
                english,
                BengaliPhoneticIndex(bengaliEntries),
                userLexicon,
                loanwords,
                seedBigrams,
            ).apply { contacts = contactNames }
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
        }

        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .addPrimaryClipChangedListener(clipboardListener)

        serviceScope.launch {
            uiState
                .map { it.panel != PanelMode.NONE }
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

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        lifecycleOwner.attachTo(window.window!!.decorView)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            KeyboardScreen(
                stateFlow = uiState,
                onKey = ::onKey,
                onKeyPressed = ::vibrate,
                onText = ::onText,
                onGesture = ::onGesture,
                onGesturePreview = ::onGesturePreview,
                onCursorMove = ::onCursorMove,
                onLanguageSelect = ::onLanguageSelected,
                onClipboardKey = ::onClipboardKey,
                onSuggestion = ::onSuggestionTapped,
                onEmoji = ::onEmojiTapped,
                onEmojiVariant = ::onEmojiVariantPicked,
                onEmojiFavourite = ::onEmojiFavouriteToggled,
                onEmojiSuggestion = ::onEmojiSuggestionTapped,
                onEmojiQueryTap = ::onEmojiSearchToggled,
                onEmojiRecentsClear = ::onEmojiRecentsClear,
                onTextEdit = ::onTextEdit,
                onPanelChange = ::onPanelChange,
                onClipboardItem = ::onClipboardItemTapped,
                onClipboardPin = ::onClipboardPin,
                onClipboardDelete = ::onClipboardDelete,
                onSnippet = ::onSnippetTapped,
                onOneHanded = ::onOneHandedChange,
                onFloatingChange = ::onFloatingChange,
                onFloatingMoved = ::onFloatingMoved,
                onFloatingResized = ::onFloatingResized,
                onFloatingBounds = ::onFloatingBounds,
                onToggleSplit = ::onToggleSplit,
                onToolbarToolsChange = ::onToolbarToolsChange,
                onToolSettings = ::openToolSettings,
                onToolboxHintDismiss = {
                    serviceScope.launch { settingsRepository.setToolboxHintDismissed(true) }
                },
                onFlashlightToggle = ::onFlashlightToggle,
                onUndoRedo = ::onUndoRedo,
                onWeatherRefresh = { refreshWeather(force = true) },
                onCameraSend = ::onCameraSend,
                onCameraPermissionRequest = ::onCameraPermissionRequest,
                onDictionaryLookup = ::onDictionaryLookup,
                onDictionarySearchToggle = ::onDictionarySearchToggle,
                onDictionaryInsert = ::onDictionaryInsert,
                onIncognitoToggle = ::onIncognitoToggle,
                onAutocorrectToggle = ::onAutocorrectToggle,
                onThemeSelect = ::onThemeSelect,
                onSoundHaptic = ::onSoundHaptic,
                onHandwritingStroke = ::onHandwritingStroke,
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
                onOpenToolSettings = ::openToolSettings,
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onResume()
        composing = StringBuilder()
        previousWord = null
        lastGestureWord = null
        lastAutocorrect = null
        // Covers the permission being granted after the setting was on.
        if (_uiState.value.settings.contactSuggestions && contactNames.isEmpty) {
            loadContactNames()
        }
        hwJob?.cancel()
        hwGeneration++
        val secure = info.isSecureField()
        _uiState.update {
            it.copy(
                panel = PanelMode.NONE,
                emojiSearchActive = false,
                emojiQuery = "",
                dictionarySearchActive = false,
                mediaSearchActive = false,
                mediaQuery = "",
                mediaDownloadingId = null,
                translate = TranslateUi(),
                composingPreview = "",
                suggestions = emptyList(),
                emojiSuggestions = emptyList(),
                secureField = secure,
                shiftState = if (shouldAutoCapitalize()) ShiftState.ON else ShiftState.OFF,
                clipboardItems = clipboardStore.items(),
                enterAction = info.enterAction(),
                handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false),
            )
        }
        refreshKarContext()
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
        refreshShiftForContext()
        refreshKarContext()
        // The translate strip follows the field: any text or cursor change
        // while it is open re-extracts and re-translates (debounced).
        if (_uiState.value.panel == PanelMode.TRANSLATE) scheduleTranslate()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleOwner.onPause()
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
        hwRecognizer.close()
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- key handling ----

    // No vibrate() here: press-time haptics fire from the UI's pointer-down
    // callback (onKeyPressed) so feedback lands on touch, not on release.
    fun onKey(key: Key) {
        // Shift keeps the gesture word so alternates can be re-cased;
        // Delete keeps it so one backspace can undo the whole swipe.
        if (key.action != KeyAction.Shift && key.action != KeyAction.Delete) {
            lastGestureWord = null
        }
        when (key.action) {
            KeyAction.Text -> onTextKey(key)
            KeyAction.Shift -> onShift()
            KeyAction.Delete -> onDelete()
            KeyAction.Space -> onSpace()
            KeyAction.Enter -> onEnter()
            KeyAction.Symbols -> toggleSymbols()
            KeyAction.Letters -> _uiState.update { it.copy(layoutMode = LayoutMode.LETTERS) }
            KeyAction.LanguageSwitch -> switchLanguage()
            KeyAction.Emoji -> onPanelChange(PanelMode.EMOJI)
        }
    }

    /** Called from the popup with an alternate character. */
    fun onText(text: String) {
        vibrate()
        onTextKey(Key(label = text))
    }

    private fun onTextKey(key: Key) {
        val state = _uiState.value
        var text = keyOutput(key, state)
        // Any new input ends the window in which backspace reverts the
        // previous autocorrect.
        lastAutocorrect = null

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
            scheduleMediaLiveSearch()
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
            composing = StringBuilder()
            ic.commitText(text, 1)
            consumeShift()
            _uiState.update { it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList()) }
            if (text.length == 1 && text[0] in SENTENCE_ENDERS) maybeAutoCapitalize()
            return
        }

        val isWordChar = text.length == 1 && (text[0].isLetter() || text[0] == '\'')
        val composingMode = !state.inputMode.isFixedBengali && !state.secureField && state.settings.suggestions

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
        }
    }

    private fun keyOutput(key: Key, state: KeyboardUiState): String {
        val base = key.output ?: key.label
        return when {
            state.shiftState != ShiftState.OFF && key.shiftLabel != null -> key.shiftLabel
            state.shiftState != ShiftState.OFF && !state.inputMode.isFixedBengali ->
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
    private fun fixedLayoutContextualVowel(text: String, previous: Char?): String {
        if (!_uiState.value.inputMode.isFixedBengali) return text
        val kar = text.singleOrNull() ?: return text
        val form = BengaliGraphemes.vowelFormAfter(previous)
        return BengaliGraphemes.vowelKeyText(kar, form) ?: text
    }

    /**
     * Recomputes [KeyboardUiState.vowelForm] from the character before the
     * cursor (or the emoji query) so the fixed-layout vowel keys track the
     * word position both in output and on the key labels.
     */
    private fun refreshKarContext() {
        if (!_uiState.value.inputMode.isFixedBengali) return
        val previous = if (_uiState.value.emojiSearchActive) {
            _uiState.value.emojiQuery.lastOrNull()
        } else {
            currentInputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        }
        val form = BengaliGraphemes.vowelFormAfter(previous)
        _uiState.update { if (it.vowelForm == form) it else it.copy(vowelForm = form) }
    }

    private fun onShift() {
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

    private fun onDelete() {
        val state = _uiState.value
        // Backspace while handwritten ink is waiting for recognition throws
        // the ink away instead of deleting committed text — the natural
        // "no, not that" while writing.
        if (state.panel == PanelMode.HANDWRITING && state.handwriting.strokes.isNotEmpty()) {
            hwJob?.cancel()
            hwGeneration++
            _uiState.update {
                it.copy(handwriting = it.handwriting.copy(strokes = emptyList(), recognizing = false))
            }
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
                    _uiState.update { it.copy(suggestions = emptyList()) }
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
            if (composing.isEmpty()) {
                val expected = "$corrected "
                val before = ic.getTextBeforeCursor(expected.length, 0)?.toString()
                if (before == expected) {
                    ic.deleteSurroundingText(expected.length, 0)
                    ic.commitText("$typed ", 1)
                    if (state.settings.learnFromTyping && !state.settings.incognito &&
                        !state.secureField
                    ) {
                        userLexicon.addWord(typed, boost = 5)
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
            // Bengali conjunct cluster as one unit.
            val before = ic.getTextBeforeCursor(12, 0)
            val deleteLength = when {
                before.isNullOrEmpty() -> 1
                state.settings.conjunctBackspace ->
                    BengaliGraphemes.clusterDeleteLength(before).coerceAtLeast(1)
                before.length >= 2 &&
                    Character.isSurrogatePair(before[before.length - 2], before[before.length - 1]) -> 2
                else -> 1
            }
            ic.deleteSurroundingText(deleteLength, 0)
        }
    }

    private fun onSpace() {
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
            scheduleMediaLiveSearch()
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

        // Double-space inserts ". "
        if (!committed && state.settings.doubleSpacePeriod && now - lastSpaceTime < 400) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.endsWith(" ") && before.length == 2 && !before[0].isWhitespace()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceTime = 0
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
        if (state.dictionarySearchActive) {
            onDictionaryLookup(state.dictionaryQuery)
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
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            maybeAutoCapitalize()
        }
    }

    private fun toggleSymbols() {
        _uiState.update {
            it.copy(
                layoutMode = when (it.layoutMode) {
                    LayoutMode.LETTERS -> LayoutMode.SYMBOLS
                    LayoutMode.SYMBOLS -> LayoutMode.SYMBOLS_SHIFTED
                    LayoutMode.SYMBOLS_SHIFTED -> LayoutMode.SYMBOLS
                },
            )
        }
    }

    private fun switchLanguage() {
        val state = _uiState.value
        val modes = state.settings.enabledModes.ifEmpty { listOf(InputMode.ENGLISH) }
        onLanguageSelected(modes[(modes.indexOf(state.inputMode) + 1).mod(modes.size)])
    }

    /** Spacebar swipe (or 🌐 cycle): switch to an explicit input mode. */
    fun onLanguageSelected(mode: InputMode) {
        currentInputConnection?.let { commitComposing(it, autocorrect = false) }
        _uiState.update { it.copy(inputMode = mode, layoutMode = LayoutMode.LETTERS) }
        refreshKarContext()
        // The handwriting model follows the input language; a switch while
        // the panel is open re-checks the new model and drops pending ink.
        if (_uiState.value.panel == PanelMode.HANDWRITING) refreshHandwritingStatus()
        serviceScope.launch { settingsRepository.setInputMode(mode) }
    }

    // ---- composing & suggestions ----

    private fun updateComposingText(ic: InputConnection) {
        val state = _uiState.value
        val preview = if (state.inputMode == InputMode.AVRO) {
            AvroPhonetic.transliterate(composing.toString())
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
        // Apostrophe restoration outranks autocorrect: "dont" is a known
        // contraction slip, not a typo for "font"/"done" to be guessed at.
        val apostrophized =
            if (fixApostrophes && !state.secureField && state.inputMode == InputMode.ENGLISH) {
                Apostrophes.fix(typed)
            } else {
                null
            }
        var corrected: String? = null
        val output = when {
            // Computed fresh rather than read from the strip: the async
            // suggestion job may not have caught up with the last keystroke
            // (especially within the debounce window), and a commit must
            // never use stale results.
            state.inputMode == InputMode.AVRO ->
                suggestionEngine?.suggest(typed, previousWord = null, avroMode = true)
                    ?.firstOrNull() ?: AvroPhonetic.transliterate(typed)
            apostrophized != null -> apostrophized
            autocorrect && !state.secureField -> {
                corrected = suggestionEngine?.shouldAutocorrect(typed)?.takeIf { it != typed }
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
        _uiState.update { it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList()) }
        return true
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
        if (!state.settings.learnFromTyping || state.settings.incognito || state.secureField) {
            previousWord = word
            return
        }
        var previous = previousWord
        var lastLearned: String? = null
        for (part in word.split(' ')) {
            val cleaned = part.trim { !it.isLetter() }
            if (cleaned.isEmpty()) continue
            userLexicon.learnWord(cleaned, reinforcement)
            previous?.let { userLexicon.learnBigram(it, cleaned) }
            previous = cleaned
            lastLearned = cleaned
        }
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
        if (!state.settings.learnFromTyping || state.settings.incognito || state.secureField) {
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

    /** Heuristic: a learned bigram successor that is an emoji, not a word. */
    private fun isEmojiCandidate(text: String): Boolean =
        text.isNotBlank() && text.none { it.isLetterOrDigit() } && text.any { it.code > 0x2000 }

    private fun refreshSuggestions() {
        val engine = suggestionEngine ?: return
        val state = _uiState.value
        if (!state.settings.suggestions || state.secureField) return
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
                    avroMode = state.inputMode == InputMode.AVRO,
                )
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
            _uiState.update { it.copy(suggestions = results, emojiSuggestions = emojis) }
        }
    }

    fun onSuggestionTapped(suggestion: String) {
        vibrate()
        val ic = currentInputConnection ?: return
        // After a swipe, the alternates replace the committed gesture word.
        val gestureWord = lastGestureWord
        if (composing.isEmpty() && gestureWord != null) {
            val before = ic.getTextBeforeCursor(gestureWord.length, 0)?.toString()
            if (before == gestureWord) ic.deleteSurroundingText(gestureWord.length, 0)
        }
        lastGestureWord = null
        lastAutocorrect = null
        ic.commitText("$suggestion ", 1)
        // Deliberately picked from the strip — a stronger signal than a
        // word that merely got committed.
        learn(suggestion, reinforcement = 2)
        composing = StringBuilder()
        _uiState.update { it.copy(composingPreview = "", suggestions = emptyList(), emojiSuggestions = emptyList()) }
        maybeAutoCapitalize()
        refreshSuggestions()
    }

    /** Spacebar drag: move the cursor one position left (-1) or right (+1). */
    fun onCursorMove(delta: Int) {
        val ic = currentInputConnection ?: return
        vibrate()
        commitComposing(ic, autocorrect = false)
        lastGestureWord = null
        sendDownUpKeyEvents(
            if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        )
    }

    // ---- gesture typing ----

    /** Mid-swipe: show the current best candidates without committing. */
    fun onGesturePreview(points: List<GesturePoint>, keys: List<KeyCenter>, keyWidthPx: Float) {
        val state = _uiState.value
        if (!state.settings.gestureTyping || state.secureField) return
        if (state.inputMode != InputMode.ENGLISH) return
        val lexicon = gestureLexicon
        if (lexicon.isEmpty() || keys.isEmpty()) return
        previewJob?.cancel()
        previewJob = serviceScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                // Same lexicon as the final decode, so the previewed word
                // never differs from the one that commits on finger-up.
                val personal = userLexicon.allWords().map { (word, count) -> word to count * 500 }
                GestureDecoder(keys, keyWidthPx).decode(points, lexicon + personal)
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
        val state = _uiState.value
        if (!state.settings.gestureTyping || state.secureField) return
        if (state.inputMode != InputMode.ENGLISH) return
        val lexicon = gestureLexicon
        if (lexicon.isEmpty() || keys.isEmpty()) return

        val shiftAtGesture = state.shiftState
        previewJob?.cancel()
        suggestionJob?.cancel()
        _uiState.update { it.copy(glideWord = null) }
        suggestionJob = serviceScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                val personal = userLexicon.allWords().map { (word, count) -> word to count * 500 }
                GestureDecoder(keys, keyWidthPx).decode(points, lexicon + personal)
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

    // ---- panels ----

    fun onPanelChange(panel: PanelMode) {
        vibrate()
        // The settings app edits snippets in the same file; re-read on open.
        if (panel == PanelMode.SNIPPETS) snippetStore.reload()
        _uiState.update {
            val closing = it.panel == panel
            val next = if (closing) PanelMode.NONE else panel
            it.copy(
                panel = next,
                textEditSelecting = false,
                emojiSearchActive = false,
                emojiQuery = "",
                emojiResults = emptyList(),
                emojiRecents = emojiUsage.recents(),
                clipboardItems = clipboardStore.items(),
                snippets = snippetStore.items(),
                dictionarySearchActive = false,
                mediaQuery = "",
                // Web/image search open straight into the search box (there
                // is nothing to show yet); gif/sticker open on trending.
                mediaSearchActive = next == PanelMode.WEB_SEARCH || next == PanelMode.IMAGE_SEARCH,
                mediaDownloadingId = null,
                translate = TranslateUi(),
            )
        }
        translateJob?.cancel()
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
            PanelMode.TRANSLATE -> {
                // Flush any half-typed word so the strip sees the whole field.
                currentInputConnection?.let { commitComposing(it, autocorrect = false) }
                scheduleTranslate(immediate = true)
            }
            else -> {}
        }
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
    }

    // ---- handwriting ----

    private fun hwLanguageTag(): String = HandwritingModels.tagForMode(_uiState.value.inputMode)

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
        if (state.panel != PanelMode.HANDWRITING || state.handwriting.status != HandwritingStatus.READY) return
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
            delay(_uiState.value.settings.handwritingCommitDelayMs.toLong())
            recognizeAndCommitHandwriting(generation)
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
        if (generation != hwGeneration || _uiState.value.panel != PanelMode.HANDWRITING) return

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
        val connection = currentInputConnection ?: return
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
        val next = !_uiState.value.settings.incognito
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
                )
            }
            is SoundHapticAction.HapticStyleChange -> HapticPlayer.preview(
                this, action.style, settings.hapticAmplitude, settings.hapticStrengthMs,
            )
            is SoundHapticAction.HapticAmplitude -> HapticPlayer.preview(
                this, settings.hapticStyle, action.amplitude, settings.hapticStrengthMs,
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
        commitImageFile(file, "image/jpeg")
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

    // ---- translate / gif / sticker / web & image search tools ----

    /** Whether any web/image search backend (Brave or Google) is keyed. */
    private fun hasSearchKey(): Boolean =
        ToolApiKeys.activeSearchProvider(_uiState.value.settings) != null

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
        if (state.panel != PanelMode.GIF && state.panel != PanelMode.STICKER) return
        mediaLiveSearchJob?.cancel()
        val query = state.mediaQuery.trim()
        mediaLiveSearchJob = serviceScope.launch {
            delay(450)
            refreshMedia(query, live = true)
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
            else -> {}
        }
    }

    /**
     * Google GIF results per query, so re-selecting the Google chip or
     * re-running a search never re-spends the tiny daily search quota.
     */
    private val googleGifCache = object : LinkedHashMap<String, List<GifItem>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<GifItem>>) =
            size > 20
    }

    /**
     * Fetches GIFs or stickers for [query] from every active provider
     * (blank query = trending; Google has no trending and sits out).
     * [live] marks a per-keystroke search — those skip Google entirely so
     * typing can't drain the 100-requests/day Programmable Search quota;
     * Google joins on enter, retry, or its chip being selected.
     */
    private fun refreshMedia(query: String, live: Boolean = false) {
        val state = _uiState.value
        val sticker = state.panel == PanelMode.STICKER
        if (!sticker && state.panel != PanelMode.GIF) return
        val setUi: (MediaUi) -> Unit = { ui ->
            _uiState.update { if (sticker) it.copy(sticker = ui) else it.copy(gif = ui) }
        }
        val settings = state.settings
        val sources = ToolApiKeys.gifSources(settings, sticker)
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
        val effective = if (live) targets - GifSource.GOOGLE else targets
        // Google-only view while typing: keep what's shown, search on enter.
        if (effective.isEmpty()) return
        mediaFetchJob?.cancel()
        setUi(MediaUi.Loading)
        val panel = state.panel
        mediaFetchJob = serviceScope.launch {
            val results = withContext(Dispatchers.IO) {
                effective.map { source ->
                    async { runCatching { fetchGifs(source, query, sticker, settings) } }
                }.awaitAll()
            }
            if (_uiState.value.panel != panel) return@launch
            val successes = results.mapNotNull { it.getOrNull() }
            setUi(
                if (successes.isEmpty()) {
                    MediaUi.Error(
                        results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
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
        GifSource.GOOGLE -> {
            val cacheKey = "$sticker|${query.lowercase()}"
            synchronized(googleGifCache) { googleGifCache[cacheKey] }
                ?: GoogleSearchClient.gifSearch(
                    query,
                    ToolApiKeys.googleSearch(settings),
                    if (sticker) ToolApiKeys.googleSearchCxStickers(settings)
                    else ToolApiKeys.googleSearchCxGifs(settings),
                    settings.searchSafe,
                    sticker,
                ).also { synchronized(googleGifCache) { googleGifCache[cacheKey] = it } }
        }
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
        val provider = ToolApiKeys.activeSearchProvider(settings)
        if (provider == null) {
            _uiState.update { it.copy(webSearch = WebSearchUi.NeedKey) }
            return
        }
        webSearchJob?.cancel()
        _uiState.update { it.copy(webSearch = WebSearchUi.Loading) }
        webSearchJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (provider) {
                        WebSearchProvider.BRAVE -> BraveSearchClient.webSearch(
                            query,
                            ToolApiKeys.brave(settings),
                            settings.searchResultCount,
                            settings.searchSafe,
                        )
                        WebSearchProvider.GOOGLE -> GoogleSearchClient.webSearch(
                            query,
                            ToolApiKeys.googleSearch(settings),
                            ToolApiKeys.googleSearchCx(settings),
                            settings.searchResultCount,
                            settings.searchSafe,
                        )
                    }
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
        val provider = ToolApiKeys.activeSearchProvider(settings)
        if (provider == null) {
            _uiState.update { it.copy(imageSearch = ImageSearchUi.NeedKey) }
            return
        }
        imageSearchJob?.cancel()
        _uiState.update { it.copy(imageSearch = ImageSearchUi.Loading) }
        imageSearchJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (provider) {
                        WebSearchProvider.BRAVE -> BraveSearchClient.imageSearch(
                            query,
                            ToolApiKeys.brave(settings),
                            settings.searchResultCount,
                            settings.searchSafe,
                        )
                        WebSearchProvider.GOOGLE -> GoogleSearchClient.imageSearch(
                            query,
                            ToolApiKeys.googleSearch(settings),
                            ToolApiKeys.googleSearchCxImages(settings),
                            settings.searchResultCount,
                            settings.searchSafe,
                        )
                    }
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

    /** Tapped a GIF/sticker cell: download the full file and commit it. */
    fun onGifSelect(item: GifItem) {
        insertDownloadedImage(item.id, item.fullUrl, item.mime)
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
    private fun insertDownloadedImage(id: String, url: String, mime: String) {
        if (_uiState.value.mediaDownloadingId != null) return
        vibrate()
        _uiState.update { it.copy(mediaDownloadingId = id) }
        mediaInsertJob = serviceScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val extension = when (mime) {
                        "image/gif" -> "gif"
                        "image/webp" -> "webp"
                        "image/png" -> "png"
                        else -> "jpg"
                    }
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
            commitImageFile(file, mime)
        }
    }

    /** Keeps the media cache bounded (newest ~30 files). */
    private fun pruneMediaCache(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(30).forEach { it.delete() }
    }

    // ---- translate tool ----

    /** Everything in the focused field, for the translate strip. */
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
     * Re-extracts the field and translates it after a short debounce, so the
     * strip follows the user's typing without a request per keystroke.
     */
    private fun scheduleTranslate(immediate: Boolean = false, targetOverride: String? = null) {
        translateJob?.cancel()
        translateJob = serviceScope.launch {
            if (!immediate) delay(400)
            val state = _uiState.value
            if (state.panel != PanelMode.TRANSLATE) return@launch
            val source = extractFieldText().trim()
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
            TextEditAction.SELECT ->
                _uiState.update { it.copy(textEditSelecting = !selecting) }
            TextEditAction.SELECT_ALL -> {
                ic.performContextMenuAction(android.R.id.selectAll)
                _uiState.update { it.copy(textEditSelecting = true) }
            }
            TextEditAction.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                _uiState.update { it.copy(textEditSelecting = false) }
            }
            TextEditAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            TextEditAction.BACKSPACE -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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

    /** DPAD/home/end navigation; with [shift] the move extends the selection. */
    private fun sendEditorKey(code: Int, shift: Boolean) {
        if (!shift) {
            sendDownUpKeyEvents(code)
            return
        }
        val ic = currentInputConnection ?: return
        val time = android.os.SystemClock.uptimeMillis()
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        // A bare meta flag isn't enough for every editor: TextView tracks
        // modifier state from the shift key's own down/up events, so wrap
        // the arrow in a real shift press like a hardware keyboard would.
        ic.sendKeyEvent(
            KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta)
        )
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, meta))
        ic.sendKeyEvent(
            KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta)
        )
    }

    fun onSnippetTapped(snippet: Snippet) {
        vibrate()
        val expanded = SnippetStore.expand(
            text = snippet.text,
            clipboard = clipboardStore.latestText(),
        )
        currentInputConnection?.commitText(expanded, 1)
        _uiState.update { it.copy(panel = PanelMode.NONE) }
    }

    fun onEmojiTapped(emoji: String) {
        vibrate()
        currentInputConnection?.commitText(emoji, 1)
        learnEmoji(emoji)
        emojiUsage.record(emoji)
        _uiState.update {
            it.copy(emojiRecents = emojiUsage.recents(), emojiFrequents = emojiUsage.frequents())
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

    fun onEmojiRecentsClear() {
        vibrate()
        emojiUsage.clearRecents()
        emojiUsage.save()
        _uiState.update { it.copy(emojiRecents = emojiUsage.recents()) }
    }

    private fun refreshEmojiResults() {
        val search = emojiSearch ?: return
        val query = _uiState.value.emojiQuery
        serviceScope.launch {
            val results = withContext(Dispatchers.Default) { search.search(query) }
            _uiState.update { it.copy(emojiResults = results) }
        }
    }

    fun onClipboardItemTapped(item: com.wasimaster.wmkeyboard.core.clipboard.ClipItem) {
        vibrate()
        if (item.kind == ClipKind.IMAGE) {
            commitImageClip(item)
            return
        }
        currentInputConnection?.commitText(item.text, 1)
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
     * GIF/sticker/search result) via commitContent, falling back to the
     * system clipboard when the editor doesn't accept that MIME type.
     */
    private fun commitImageFile(file: File, mimeType: String) {
        val contentUri = runCatching {
            FileProvider.getUriForFile(this, clipboardFileProviderAuthority, file)
        }.getOrNull() ?: return

        val ic = currentInputConnection
        val editorInfo = currentInputEditorInfo
        val supported = editorInfo != null &&
            EditorInfoCompat.getContentMimeTypes(editorInfo)
                .any { ClipDescription.compareMimeTypes(mimeType, it) }

        if (ic != null && editorInfo != null && supported) {
            val info = InputContentInfoCompat(
                contentUri,
                ClipDescription("image", arrayOf(mimeType)),
                null,
            )
            val committed = InputConnectionCompat.commitContent(
                ic,
                editorInfo,
                info,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
            if (committed) return
        }

        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newUri(contentResolver, "image", contentUri))
        Toast.makeText(this, "This app doesn't accept images here — image copied, paste it instead", Toast.LENGTH_SHORT).show()
    }

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

    fun onToggleSplit() {
        vibrate()
        val current = _uiState.value.settings.splitKeyboard
        serviceScope.launch { settingsRepository.setSplitKeyboard(!current) }
    }

    fun onToolbarToolsChange(tools: List<ToolbarTool>) {
        vibrate()
        serviceScope.launch { settingsRepository.setToolbarTools(tools) }
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
                val panel = _uiState.value.panel
                if (panel != PanelMode.NONE) onPanelChange(panel)
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
            _uiState.value.panel != PanelMode.NONE
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val panel = _uiState.value.panel
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown && panel != PanelMode.NONE) {
            onPanelChange(panel)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    fun openSettings() {
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
        _uiState.update {
            when {
                it.shiftState == ShiftState.CAPS_LOCK -> it
                it.shiftState == ShiftState.OFF && shouldAutoCapitalize() ->
                    it.copy(shiftState = ShiftState.ON)
                it.shiftState == ShiftState.ON && it.settings.autoCapitalize && !shouldAutoCapitalize() ->
                    it.copy(shiftState = ShiftState.OFF)
                else -> it
            }
        }
    }

    private fun shouldAutoCapitalize(): Boolean {
        val state = _uiState.value
        if (!state.settings.autoCapitalize) return false
        if (state.inputMode != InputMode.ENGLISH) return false
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return ic.getCursorCapsMode(info.inputType) != 0
    }

    private fun maybeAutoCapitalize() {
        _uiState.update {
            if (shouldAutoCapitalize() && it.shiftState == ShiftState.OFF) {
                it.copy(shiftState = ShiftState.ON)
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
     * Plays the key-press sound through the system UI sound effects.
     * [force] previews even while the setting is off (the quick panel's
     * toggle fires before the DataStore write lands).
     */
    private fun playKeySound(
        style: com.wasimaster.wmkeyboard.core.settings.KeySoundStyle? = null,
        volume: Float? = null,
        force: Boolean = false,
    ) {
        val settings = _uiState.value.settings
        if (!force && !settings.keySound) return
        val audio = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val effect = when (style ?: settings.keySoundStyle) {
            com.wasimaster.wmkeyboard.core.settings.KeySoundStyle.CLICK ->
                android.media.AudioManager.FX_KEY_CLICK
            com.wasimaster.wmkeyboard.core.settings.KeySoundStyle.STANDARD ->
                android.media.AudioManager.FX_KEYPRESS_STANDARD
            com.wasimaster.wmkeyboard.core.settings.KeySoundStyle.POP ->
                android.media.AudioManager.FX_KEYPRESS_SPACEBAR
            com.wasimaster.wmkeyboard.core.settings.KeySoundStyle.THOCK ->
                android.media.AudioManager.FX_KEYPRESS_DELETE
            com.wasimaster.wmkeyboard.core.settings.KeySoundStyle.CHIME ->
                android.media.AudioManager.FX_KEYPRESS_RETURN
        }
        audio.playSoundEffect(effect, (volume ?: settings.keySoundVolume).coerceIn(0.05f, 1f))
    }

    private fun doVibrate() {
        val settings = _uiState.value.settings
        if (!settings.hapticFeedback) return
        HapticPlayer.play(
            this,
            settings.hapticStyle,
            settings.hapticAmplitude,
            settings.hapticStrengthMs,
        )
    }

    companion object {
        /** Minimum spacing between haptic clicks so rapid presses stay distinct. */
        private const val MIN_HAPTIC_GAP_MS = 45L
        private const val WEATHER_CACHE_MS = 15L * 60 * 1000
        private val SENTENCE_ENDERS = charArrayOf('.', '!', '?', '।')
        private const val SHIFT_DOUBLE_TAP_MS = 350L

        private fun EditorInfo?.enterAction(): EnterAction {
            val options = this?.imeOptions ?: return EnterAction.DEFAULT
            if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.DEFAULT
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
