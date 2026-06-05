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
import com.wasimaster.wmkeyboard.app.MainActivity
import com.wasimaster.wmkeyboard.core.clipboard.ClipboardStore
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiSearch
import com.wasimaster.wmkeyboard.core.emoji.EmojiUsage
import com.wasimaster.wmkeyboard.core.gesture.GestureDecoder
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
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

    private var composing = StringBuilder()
    private var previousWord: String? = null
    private var lastSpaceTime = 0L
    private var suggestionJob: Job? = null

    /** English word list used by the gesture decoder (bundled dictionary). */
    private var gestureLexicon: List<Pair<String, Int>> = emptyList()

    /** Last word committed by a swipe, so tapping an alternate replaces it. */
    private var lastGestureWord: String? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val state = _uiState.value
        if (!state.settings.clipboardHistory || state.settings.incognito || state.secureField) return@OnPrimaryClipChangedListener
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
        clipboardStore.add(text)
        clipboardStore.save()
        _uiState.update { it.copy(clipboardItems = clipboardStore.items()) }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = KeyboardViewLifecycleOwner()
        lifecycleOwner.onCreate()

        settingsRepository = SettingsRepository(this)
        userLexicon = UserLexicon(File(filesDir, "learning/user_lexicon.json"))
        emojiUsage = EmojiUsage(File(filesDir, "learning/emoji_usage.json"))
        clipboardStore = ClipboardStore(File(filesDir, "clipboard/history.json"))

        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                clipboardStore.expiryMillis = settings.clipboardExpiryHours * 60L * 60 * 1000
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
            val english = Trie().apply { for ((word, freq) in englishEntries) insert(word, freq) }
            gestureLexicon = englishEntries
            suggestionEngine = SuggestionEngine(english, BengaliPhoneticIndex(bengaliEntries), userLexicon)
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
                onText = ::onText,
                onGesture = ::onGesture,
                onSuggestion = ::onSuggestionTapped,
                onEmoji = ::onEmojiTapped,
                onEmojiQueryTap = ::onEmojiSearchToggled,
                onPanelChange = ::onPanelChange,
                onClipboardItem = ::onClipboardItemTapped,
                onClipboardPin = ::onClipboardPin,
                onClipboardDelete = ::onClipboardDelete,
                onOpenSettings = ::openSettings,
            )
        }
        return view
    }

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
            )
        }
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

    fun onKey(key: Key) {
        vibrate()
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
        val text = keyOutput(key, state)

        if (state.emojiSearchActive) {
            _uiState.update { it.copy(emojiQuery = it.emojiQuery + text) }
            refreshEmojiResults()
            return
        }

        val ic = currentInputConnection ?: return
        val isWordChar = text.length == 1 && (text[0].isLetter() || text[0] == '\'')
        val composingMode = state.inputMode != InputMode.PROBHAT && !state.secureField && state.settings.suggestions

        if (isWordChar && composingMode) {
            composing.append(text)
            updateComposingText(ic)
            refreshSuggestions()
        } else {
            commitComposing(ic, autocorrect = false)
            ic.commitText(text, 1)
            if (text.length == 1 && text[0] in SENTENCE_ENDERS) {
                maybeAutoCapitalize()
            }
        }
        consumeShift()
    }

    private fun keyOutput(key: Key, state: KeyboardUiState): String {
        val base = key.output ?: key.label
        return when {
            state.shiftState != ShiftState.OFF && key.shiftLabel != null -> key.shiftLabel
            state.shiftState != ShiftState.OFF && state.inputMode != InputMode.PROBHAT ->
                base.uppercase()
            else -> base
        }
    }

    private fun onShift() {
        _uiState.update {
            it.copy(
                shiftState = when (it.shiftState) {
                    ShiftState.OFF -> ShiftState.ON
                    ShiftState.ON -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
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
                refreshEmojiResults()
            }
            return
        }
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            updateComposingText(ic)
            refreshSuggestions()
        } else {
            // Delete a full surrogate pair / grapheme where possible.
            val before = ic.getTextBeforeCursor(2, 0)
            val deleteLength = if (before != null && before.length >= 2 &&
                Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])
            ) 2 else 1
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

    // ---- gesture typing ----

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
        suggestionJob?.cancel()
        suggestionJob = serviceScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                val personal = userLexicon.allWords().map { (word, count) -> word to count * 500 }
                GestureDecoder(keys, keyWidthPx).decode(points, lexicon + personal)
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
        _uiState.update {
            val closing = it.panel == panel
            it.copy(
                panel = if (closing) PanelMode.NONE else panel,
                emojiSearchActive = false,
                emojiQuery = "",
                emojiResults = emptyList(),
                emojiRecents = emojiUsage.recents(),
                clipboardItems = clipboardStore.items(),
            )
        }
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
        currentInputConnection?.commitText(item.text, 1)
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

    fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ---- helpers ----

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    settings.hapticStrengthMs.toLong(),
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                )
            )
        } else {
            vibrator.vibrate(settings.hapticStrengthMs.toLong())
        }
    }

    companion object {
        private val SENTENCE_ENDERS = charArrayOf('.', '!', '?', '।')

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
