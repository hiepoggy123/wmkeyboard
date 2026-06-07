package com.wasimaster.wmkeyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntRect
import android.content.ClipDescription
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.wasimaster.wmkeyboard.app.MainActivity
import com.wasimaster.wmkeyboard.core.clipboard.ClipKind
import com.wasimaster.wmkeyboard.core.clipboard.ClipboardStore
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiSearch
import com.wasimaster.wmkeyboard.core.emoji.EmojiUsage
import com.wasimaster.wmkeyboard.core.gesture.GestureDecoder
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.EnglishBengaliMap
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.isFixedBengali
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import com.wasimaster.wmkeyboard.ime.layout.Key
import com.wasimaster.wmkeyboard.ime.layout.KeyAction
import com.wasimaster.wmkeyboard.ime.ui.KeyboardScreen
import android.inputmethodservice.InputMethodService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** English word list used by the gesture decoder (bundled dictionary). */
    private var gestureLexicon: List<Pair<String, Int>> = emptyList()

    /** Last word committed by a swipe, so tapping an alternate replaces it. */
    private var lastGestureWord: String? = null
    private var previewJob: Job? = null

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
            settingsRepository.settings.collect { settings ->
                clipboardStore.expiryMillis = settings.clipboardExpiryHours * 60L * 60 * 1000
                if (!settings.floatingKeyboard) floatingPanelBounds = null
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
            val loanwords = withContext(Dispatchers.Default) {
                assets.open("dictionaries/en_bn.tsv").use { EnglishBengaliMap.load(it) }
            }
            val english = Trie().apply { for ((word, freq) in englishEntries) insert(word, freq) }
            gestureLexicon = englishEntries
            suggestionEngine = SuggestionEngine(english, BengaliPhoneticIndex(bengaliEntries), userLexicon, loanwords)
            emojiEntries = catalog
            emojiSearch = EmojiSearch(catalog)
            _uiState.update { it.copy(emojiRecents = emojiUsage.recents(), emojiCatalog = catalog) }
        }

        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .addPrimaryClipChangedListener(clipboardListener)
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
                onSuggestion = ::onSuggestionTapped,
                onEmoji = ::onEmojiTapped,
                onEmojiQueryTap = ::onEmojiSearchToggled,
                onEmojiRecentsClear = ::onEmojiRecentsClear,
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

    fun onFloatingResized(widthDp: Int) {
        serviceScope.launch { settingsRepository.setFloatingWidthDp(widthDp) }
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
        val secure = info.isSecureField()
        _uiState.update {
            it.copy(
                panel = PanelMode.NONE,
                emojiSearchActive = false,
                emojiQuery = "",
                composingPreview = "",
                suggestions = emptyList(),
                secureField = secure,
                shiftState = if (shouldAutoCapitalize()) ShiftState.ON else ShiftState.OFF,
                clipboardItems = clipboardStore.items(),
                enterAction = info.enterAction(),
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
            _uiState.update { it.copy(composingPreview = "", suggestions = emptyList()) }
        }
        refreshShiftForContext()
        refreshKarContext()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleOwner.onPause()
        userLexicon.save()
        emojiUsage.save()
    }

    override fun onDestroy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .removePrimaryClipChangedListener(clipboardListener)
        userLexicon.save()
        emojiUsage.save()
        clipboardStore.save()
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- key handling ----

    // No vibrate() here: press-time haptics fire from the UI's pointer-down
    // callback (onKeyPressed) so feedback lands on touch, not on release.
    fun onKey(key: Key) {
        if (key.action != KeyAction.Shift) lastGestureWord = null
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

        if (state.emojiSearchActive) {
            text = fixedLayoutContextualVowel(text, state.emojiQuery.lastOrNull())
            _uiState.update { it.copy(emojiQuery = it.emojiQuery + text) }
            refreshKarContext()
            refreshEmojiResults()
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
            _uiState.update { it.copy(composingPreview = "", suggestions = emptyList()) }
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
        if (state.emojiSearchActive) {
            if (state.emojiQuery.isNotEmpty()) {
                _uiState.update { it.copy(emojiQuery = it.emojiQuery.dropLast(1)) }
                refreshKarContext()
                refreshEmojiResults()
            }
            return
        }
        val ic = currentInputConnection ?: return
        // Deleting with an active selection removes the selected text only.
        if (hasSelection(ic)) {
            ic.commitText("", 1)
            return
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

        // Space over a selection replaces it; skip autocorrect/double-space.
        if (hasSelection(ic)) {
            ic.commitText(" ", 1)
            lastSpaceTime = 0
            maybeAutoCapitalize()
            return
        }

        val committed = commitComposing(ic, autocorrect = state.settings.autocorrect)

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
    }

    private fun onEnter() {
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
        val next = modes[(modes.indexOf(state.inputMode) + 1).mod(modes.size)]
        currentInputConnection?.let { commitComposing(it, autocorrect = false) }
        _uiState.update { it.copy(inputMode = next, layoutMode = LayoutMode.LETTERS) }
        refreshKarContext()
        serviceScope.launch { settingsRepository.setInputMode(next) }
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
    private fun commitComposing(ic: InputConnection, autocorrect: Boolean): Boolean {
        if (composing.isEmpty()) return false
        val typed = composing.toString()
        val state = _uiState.value
        val output = when {
            state.inputMode == InputMode.AVRO ->
                _uiState.value.suggestions.firstOrNull() ?: AvroPhonetic.transliterate(typed)
            autocorrect && !state.secureField ->
                suggestionEngine?.shouldAutocorrect(typed) ?: typed
            else -> typed
        }
        ic.commitText(output, 1)
        learn(output)
        composing = StringBuilder()
        _uiState.update { it.copy(composingPreview = "", suggestions = emptyList()) }
        return true
    }

    private fun learn(word: String) {
        val state = _uiState.value
        if (!state.settings.learnFromTyping || state.settings.incognito || state.secureField) {
            previousWord = word
            return
        }
        val cleaned = word.trim().trim { !it.isLetter() }
        if (cleaned.isNotEmpty()) {
            userLexicon.learnWord(cleaned)
            previousWord?.let { userLexicon.learnBigram(it, cleaned) }
        }
        previousWord = cleaned.ifEmpty { null }
    }

    private fun refreshSuggestions() {
        val engine = suggestionEngine ?: return
        val state = _uiState.value
        if (!state.settings.suggestions || state.secureField) return
        val typed = composing.toString()
        suggestionJob?.cancel()
        suggestionJob = serviceScope.launch {
            val results = withContext(Dispatchers.Default) {
                engine.suggest(
                    composing = typed,
                    previousWord = previousWord,
                    avroMode = state.inputMode == InputMode.AVRO,
                )
            }
            _uiState.update { it.copy(suggestions = results) }
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
        ic.commitText("$suggestion ", 1)
        learn(suggestion)
        composing = StringBuilder()
        _uiState.update { it.copy(composingPreview = "", suggestions = emptyList()) }
        maybeAutoCapitalize()
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
                GestureDecoder(keys, keyWidthPx).decode(points, lexicon)
            }
            if (candidates.isNotEmpty()) {
                _uiState.update { it.copy(suggestions = candidates.map { candidate -> candidate.word }) }
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
            it.copy(
                panel = if (closing) PanelMode.NONE else panel,
                emojiSearchActive = false,
                emojiQuery = "",
                emojiResults = emptyList(),
                emojiRecents = emojiUsage.recents(),
                clipboardItems = clipboardStore.items(),
                snippets = snippetStore.items(),
            )
        }
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
        emojiUsage.record(emoji)
        _uiState.update { it.copy(emojiRecents = emojiUsage.recents()) }
    }

    fun onEmojiSearchToggled() {
        _uiState.update { it.copy(emojiSearchActive = !it.emojiSearchActive) }
    }

    fun onEmojiRecentsClear() {
        vibrate()
        emojiUsage.clearRecents()
        emojiUsage.save()
        _uiState.update { it.copy(emojiRecents = emptyList()) }
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
        val contentUri = runCatching {
            FileProvider.getUriForFile(this, clipboardFileProviderAuthority, file)
        }.getOrNull() ?: return

        val ic = currentInputConnection
        val editorInfo = currentInputEditorInfo
        val supported = editorInfo != null &&
            EditorInfoCompat.getContentMimeTypes(editorInfo)
                .any { ClipDescription.compareMimeTypes(item.mimeType, it) }

        if (ic != null && editorInfo != null && supported) {
            val info = InputContentInfoCompat(
                contentUri,
                ClipDescription("image", arrayOf(item.mimeType)),
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

    fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val settings = _uiState.value.settings
        if (!settings.hapticFeedback) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (settings.hapticStyle != HapticStyle.CUSTOM &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            // Hardware-tuned click effects — crisper than a raw one-shot and
            // what most stock keyboards use.
            vibrator.vibrate(
                android.os.VibrationEffect.createPredefined(
                    when (settings.hapticStyle) {
                        HapticStyle.HEAVY_CLICK -> android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                        else -> android.os.VibrationEffect.EFFECT_CLICK
                    }
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Explicit amplitude: DEFAULT_AMPLITUDE defers to a (often weak)
            // device default. Devices without amplitude control ignore the
            // value, so fall back to the default constant there.
            val amplitude = if (vibrator.hasAmplitudeControl()) {
                settings.hapticAmplitude.coerceIn(1, 255)
            } else {
                android.os.VibrationEffect.DEFAULT_AMPLITUDE
            }
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    settings.hapticStrengthMs.toLong(),
                    amplitude,
                )
            )
        } else {
            vibrator.vibrate(settings.hapticStrengthMs.toLong())
        }
    }

    companion object {
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
