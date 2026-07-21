package com.wasimaster.wmkeyboard.ime.ui

import android.graphics.BitmapFactory
import android.view.WindowManager
import com.wasimaster.wmkeyboard.BuildConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.automirrored.outlined.LastPage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import android.content.res.Configuration
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import com.wasimaster.wmkeyboard.core.settings.ScreenVariant
import com.wasimaster.wmkeyboard.core.settings.resolvedFor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import android.os.Build
import android.os.SystemClock
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.wasimaster.wmkeyboard.core.settings.ScreenReaderMode
import kotlinx.coroutines.delay
import com.wasimaster.wmkeyboard.core.clipboard.ClipItem
import com.wasimaster.wmkeyboard.core.clipboard.ClipKind
import com.wasimaster.wmkeyboard.core.clipboard.ClipLinks
import com.wasimaster.wmkeyboard.core.emoji.EmojiNames
import com.wasimaster.wmkeyboard.core.emoji.EmojiVariantIndex
import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.ui.toolAccentColor
import com.wasimaster.wmkeyboard.core.grammar.GrammarFix
import com.wasimaster.wmkeyboard.core.grammar.GrammarLint
import com.wasimaster.wmkeyboard.core.gesture.KeyCenter
import com.wasimaster.wmkeyboard.core.handwriting.HwPoint
import com.wasimaster.wmkeyboard.core.handwriting.HwStroke
import com.wasimaster.wmkeyboard.core.script.TextDirection
import com.wasimaster.wmkeyboard.core.script.mapDigits
import com.wasimaster.wmkeyboard.core.script.resolveNumeralDigits
import com.wasimaster.wmkeyboard.core.settings.BarRow
import com.wasimaster.wmkeyboard.core.settings.EmojiBarContent
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.core.settings.KeyboardMode
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiTabMode
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.OneHandedSide
import com.wasimaster.wmkeyboard.core.settings.LetterSwipeAction
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.SpacebarDisplay
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import com.wasimaster.wmkeyboard.core.tools.resolveSymbolSets
import com.wasimaster.wmkeyboard.core.tools.GifItem
import com.wasimaster.wmkeyboard.core.tools.GifSource
import com.wasimaster.wmkeyboard.core.tools.symbolChipLabel
import com.wasimaster.wmkeyboard.core.tools.ImageResult
import com.wasimaster.wmkeyboard.core.tools.WebResult
import com.wasimaster.wmkeyboard.ime.AiUi
import com.wasimaster.wmkeyboard.ime.EnterAction
import com.wasimaster.wmkeyboard.ime.FieldKind
import com.wasimaster.wmkeyboard.ime.isNumericPad
import com.wasimaster.wmkeyboard.ime.hasMediaSearch
import com.wasimaster.wmkeyboard.ime.HandwritingStatus
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.ModifierState
import com.wasimaster.wmkeyboard.ime.authoredNumberRow
import com.wasimaster.wmkeyboard.ime.LayoutMode
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.SymbolCatalog
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.ime.PwSettingAction
import com.wasimaster.wmkeyboard.ime.TypingTestAction
import com.wasimaster.wmkeyboard.ime.SoundHapticAction
import com.wasimaster.wmkeyboard.ime.TextEditAction
import com.wasimaster.wmkeyboard.ime.ShiftState
import com.wasimaster.wmkeyboard.ime.displayCaseForShift
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.ClipboardKeyAction
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyRole
import com.wasimaster.wmkeyboard.core.layout.ModifierKey
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.gridWeightOf
import com.wasimaster.wmkeyboard.core.layout.sidePadFor
import com.wasimaster.wmkeyboard.core.layout.Layouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fired at pointer-down on any key so feedback (haptics) lands on press,
 * not on release when the key's action commits.
 */
internal val LocalKeyPressFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Haptic only, no key sound — for cues that are not a keypress. The emoji
 * long-press popup uses it: the click sound would announce an insertion the
 * long press deliberately did not make.
 */
internal val LocalHapticFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Key sound only, no haptic — the sound-preserving fallback for events whose
 * vibration a fine-grained toggle has turned off (space press, backspace-swipe
 * word delete, held-key repeat). Firing this instead of the full press feedback
 * keeps the click sound while dropping just the buzz.
 */
internal val LocalKeySound = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Sink for the A/C/V/X clipboard long-press shortcuts, provided once at the
 * root so it does not have to thread through every key-grid layer.
 */
internal val LocalClipboardKeyAction = staticCompositionLocalOf<(ClipboardKeyAction) -> Unit> { {} }

/**
 * Whether a backspace press would still delete anything (text before the
 * cursor, a selection, or an active search query). Held-backspace repeat
 * loops poll this and stop once the field is empty, instead of buzzing
 * away at nothing.
 */
internal val LocalCanDelete = staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * Like [LocalCanDelete] but always about the real text field, even while a
 * panel search is active and the backspace key is editing a query — for
 * controls that delete from the field directly (the emoji search bar's
 * backspace).
 */
internal val LocalCanDeleteField = staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * Deletes the word before the cursor. Fired per step of a sideways drag on
 * the backspace key; provided at the root like [LocalCanDelete] so it does
 * not have to thread through every key-grid layer.
 */
internal val LocalDeleteWord = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Steps the text cursor vertically (sign = direction, magnitude = steps).
 * Fired by the spacebar's optional 2-D touchpad slide; provided at the root
 * like [LocalDeleteWord] so it does not thread through every key-grid layer.
 */
internal val LocalCursorMoveVertical = staticCompositionLocalOf<(Int) -> Unit> { {} }

/**
 * Dismisses the keyboard. Provided at the root so the spacebar's optional
 * swipe-down-to-hide gesture can reach it without threading a callback down
 * through the key grid.
 */
internal val LocalHideKeyboard = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Whether TalkBack (or another explore-by-touch service) is currently
 * driving the screen. Resolved once at the root rather than per key —
 * every key would otherwise register its own listener.
 */
internal val LocalTouchExploration = staticCompositionLocalOf { false }

/**
 * Live touch-exploration state. The keyboard has to react to this while
 * running, not only at start: users toggle TalkBack with a shortcut mid-task
 * and the key gesture handling has to swap over with it.
 */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        enabled = manager.isTouchExplorationEnabled
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * What a screen reader should call this key. Punctuation and whitespace get
 * spoken names because TalkBack either skips them or reads them as silence,
 * which makes a symbol layout unusable by ear.
 */
private fun spokenLabel(key: Key, state: KeyboardUiState): String = when (key.action) {
    KeyAction.Space -> "Space"
    KeyAction.Delete -> "Delete"
    KeyAction.Enter -> when (state.enterAction) {
        EnterAction.SEARCH -> "Search"
        EnterAction.SEND -> "Send"
        EnterAction.GO -> "Go"
        EnterAction.NEXT -> "Next"
        EnterAction.PREVIOUS -> "Previous"
        EnterAction.DONE -> "Done"
        EnterAction.CUSTOM -> state.enterActionLabel ?: "Enter"
        EnterAction.DEFAULT -> "Enter"
    }
    KeyAction.Shift -> when (state.shiftState) {
        ShiftState.CAPS_LOCK -> "Caps lock on"
        ShiftState.ON -> "Shift on"
        ShiftState.OFF -> "Shift"
    }
    KeyAction.LanguageSwitch -> "Switch language"
    KeyAction.Emoji -> "Emoji"
    is KeyAction.Mod -> {
        val name = when ((key.action as KeyAction.Mod).key) {
            ModifierKey.CTRL -> "Control"
            ModifierKey.ALT -> "Alt"
            ModifierKey.META -> "Meta"
        }
        when (state.modifiers[(key.action as KeyAction.Mod).key]) {
            ModifierState.LOCKED -> "$name locked"
            ModifierState.ARMED -> "$name on"
            ModifierState.OFF -> name
        }
    }
    is KeyAction.SendKey -> key.label.ifBlank { "Key" }
    else -> {
        val label = displayLabel(key, state)
        punctuationNames[label] ?: label
    }
}

private val punctuationNames = mapOf(
    "." to "Period", "," to "Comma", "?" to "Question mark", "!" to "Exclamation mark",
    "'" to "Apostrophe", "\"" to "Quote", ";" to "Semicolon", ":" to "Colon",
    "-" to "Hyphen", "_" to "Underscore", "/" to "Slash", "\\" to "Backslash",
    "(" to "Left parenthesis", ")" to "Right parenthesis", "[" to "Left bracket",
    "]" to "Right bracket", "{" to "Left brace", "}" to "Right brace",
    "@" to "At sign", "#" to "Hash", "$" to "Dollar sign", "%" to "Percent",
    "&" to "Ampersand", "*" to "Asterisk", "+" to "Plus", "=" to "Equals",
    "<" to "Less than", ">" to "Greater than", "|" to "Vertical bar",
    "~" to "Tilde", "^" to "Caret", "`" to "Backtick",
)

/** Root composable for the IME. Renders [KeyboardUiState] and forwards input. */
@Composable
fun KeyboardScreen(
    stateFlow: StateFlow<KeyboardUiState>,
    onKey: (Key) -> Unit,
    onKeyPressed: () -> Unit = {},
    onHaptic: () -> Unit = onKeyPressed,
    onKeySound: () -> Unit = {},
    onText: (String) -> Unit = {},
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGestureWords: (List<List<GesturePoint>>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onCursorMove: (Int) -> Unit = {},
    onCursorMoveVertical: (Int) -> Unit = {},
    onLayoutSelect: (String) -> Unit = {},
    onClipboardKey: (ClipboardKeyAction) -> Unit = {},
    canDelete: () -> Boolean = { true },
    canDeleteField: () -> Boolean = { true },
    onDeleteWord: () -> Unit = {},
    onSuggestion: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onEmojiVariant: (String, String) -> Unit = { _, v -> onEmoji(v) },
    onEmojiFavourite: (String) -> Unit = {},
    onEmojiSuggestion: (String) -> Unit = onEmoji,
    onPunctuation: (String) -> Unit = {},
    onEmojiQueryTap: () -> Unit,
    onEmojiRecentsClear: () -> Unit = {},
    onEmojiRecentRemove: (String) -> Unit = {},
    onEmojiFavouritesReorder: (List<String>) -> Unit = {},
    onEmojiSearchFieldDelete: () -> Unit = {},
    onTextEdit: (TextEditAction) -> Unit = {},
    onPanelChange: (PanelMode) -> Unit,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onClipboardSearchToggle: () -> Unit = {},
    onClipboardSuggestionDismiss: () -> Unit = {},
    onSnippet: (Snippet) -> Unit = {},
    onOneHanded: (OneHandedMode) -> Unit = {},
    /** Persists the dock side for one orientation (landscape flag, side). */
    onOneHandedSide: (Boolean, OneHandedSide) -> Unit = { _, _ -> },
    onFloatingChange: (Boolean) -> Unit = {},
    onFloatingMoved: (Float, Float) -> Unit = { _, _ -> },
    onFloatingResized: (Int, Float) -> Unit = { _, _ -> },
    onFloatingBounds: (IntRect) -> Unit = {},
    onToggleSplit: () -> Unit = {},
    onToolbarToolsChange: (List<ToolbarTool>) -> Unit = {},
    onToolboxOrderChange: (List<ToolbarTool>) -> Unit = {},
    onToolSettings: (ToolbarTool) -> Unit = {},
    onToolboxHintDismiss: () -> Unit = {},
    onFlashlightToggle: () -> Unit = {},
    onUndoRedo: (Boolean) -> Unit = {},
    onWeatherRefresh: () -> Unit = {},
    onCameraSend: (java.io.File) -> Unit = {},
    onCameraPermissionRequest: () -> Unit = {},
    onCalendarPermissionRequest: () -> Unit = {},
    onScannedInsert: (String) -> Unit = {},
    onScannedUrlOpen: (String) -> Unit = {},
    onDocScan: () -> Unit = {},
    onVoiceToggle: () -> Unit = {},
    onVoicePermissionRequest: () -> Unit = {},
    onVoiceUndo: () -> Unit = {},
    onVoiceModelDownload: () -> Unit = {},
    onWhisperTranslateToggle: () -> Unit = {},
    onOpenVoiceSettings: () -> Unit = {},
    onMediaPlayPause: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onMediaPrevious: () -> Unit = {},
    onMediaSeek: (Long) -> Unit = {},
    onMediaAccessRequest: () -> Unit = {},
    onMediaResume: () -> Unit = {},
    onDictionaryLookup: (String) -> Unit = {},
    onDictionarySearchToggle: () -> Unit = {},
    onDictionaryInsert: (String) -> Unit = {},
    onIncognitoToggle: () -> Unit = {},
    onAutocorrectToggle: () -> Unit = {},
    onThemeSelect: (String) -> Unit = {},
    onSoundHaptic: (SoundHapticAction) -> Unit = {},
    onHandwritingStroke: (HwStroke, IntSize) -> Unit = { _, _ -> },
    onKeyboardHandwritingStroke: (HwStroke, IntSize) -> Unit = { _, _ -> },
    onHandwritingUndo: () -> Unit = {},
    onHandwritingDownload: () -> Unit = {},
    onMediaQueryTap: () -> Unit = {},
    onMediaRetry: () -> Unit = {},
    onGifSelect: (GifItem) -> Unit = {},
    onGifSourceSelect: (GifSource) -> Unit = {},
    onWebResult: (WebResult) -> Unit = {},
    onWebResultOpen: (WebResult) -> Unit = {},
    onImageResult: (ImageResult) -> Unit = {},
    onImageResultLink: (ImageResult) -> Unit = {},
    onTranslateTarget: (String) -> Unit = {},
    onTranslateReplace: () -> Unit = {},
    onTranslateInsert: () -> Unit = {},
    onGrammarFix: (GrammarLint, GrammarFix) -> Unit = { _, _ -> },
    onGrammarFixAll: () -> Unit = {},
    onGrammarDismiss: (GrammarLint) -> Unit = {},
    onGrammarDialect: (GrammarDialect) -> Unit = {},
    onGrammarFocus: (GrammarLint) -> Unit = {},
    onWikiOpen: (String) -> Unit = {},
    onWikiBack: () -> Unit = {},
    onWikiLoadLinks: () -> Unit = {},
    onWikiLoadFull: () -> Unit = {},
    onSymbolInsert: (String) -> Unit = {},
    onSymbolSetSelect: (String) -> Unit = {},
    onModeSelect: (String?) -> Unit = {},
    onToolInsert: (String) -> Unit = {},
    onUnitSelection: (String) -> Unit = {},
    onCurrencyPairChange: (String, String) -> Unit = { _, _ -> },
    onCurrencyRefresh: () -> Unit = {},
    onPwSetting: (PwSettingAction) -> Unit = {},
    onTypingTestAction: (TypingTestAction) -> Unit = {},
    onQrSend: () -> Unit = {},
    onAiAction: (com.wasimaster.wmkeyboard.core.settings.AiAction) -> Unit = {},
    onAiReplace: () -> Unit = {},
    onAiInsert: () -> Unit = {},
    onAiRetry: () -> Unit = {},
    onAiRunCustom: () -> Unit = {},
    onAiPickModel: (com.wasimaster.wmkeyboard.core.settings.AiProvider, String?) -> Unit = { _, _ -> },
    onAiToggleStripMarkdown: () -> Unit = {},
    onOpenToolSettings: (ToolbarTool) -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    onDismissInlineSuggestions: () -> Unit = {},
    /** Smart chip tapped: type the answer over the text that triggered it. */
    onSmartAccept: () -> Unit = {},
    /** Smart chip's tool button: clear the trigger and stage the prefill. */
    onSmartOpen: () -> Unit = {},
    /** A tool panel has read [KeyboardUiState.toolPrefill]. */
    onToolPrefillConsumed: () -> Unit = {},
    /** Dismiss the keyboard — the hide-keyboard tool and the toolbar swipe-down. */
    onHideKeyboard: () -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    val rawState by stateFlow.collectAsState()

    // Sizing is resolved once, here, for the screen shape we are actually
    // drawing on: a folded phone in landscape can want a shorter key than
    // the same phone upright, and a tablet wants neither. Everything below
    // reads `state.settings.keyHeightDp` as before and never learns that
    // screen variants exist.
    val configuration = LocalConfiguration.current
    val variant = ScreenVariant.of(
        landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        // Smallest dimension, not the current width: a phone turned sideways
        // is wide but still a phone, while an opened foldable is wide either
        // way round.
        unfolded = configuration.smallestScreenWidthDp >= ScreenVariant.UNFOLDED_MIN_DP,
    )
    val state = remember(rawState, variant) {
        rawState.copy(settings = rawState.settings.resolvedFor(variant))
    }

    // One entry point for every toolbar/toolbox tool.
    val onToolTap: (ToolbarTool) -> Unit = { tool ->
        when (tool) {
            ToolbarTool.EMOJI -> onPanelChange(PanelMode.EMOJI)
            ToolbarTool.CLIPBOARD -> onPanelChange(PanelMode.CLIPBOARD)
            ToolbarTool.SNIPPETS -> onPanelChange(PanelMode.SNIPPETS)
            ToolbarTool.TEXT_EDIT -> onPanelChange(PanelMode.TEXT_EDIT)
            ToolbarTool.SETTINGS -> onOpenSettings()
            ToolbarTool.ONE_HANDED -> onOneHanded(
                if (state.settings.oneHandedMode == OneHandedMode.OFF) {
                    // Enable on this orientation's preferred side.
                    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    state.settings.oneHanded.forLandscape(landscape).side.toMode()
                } else OneHandedMode.OFF
            )
            ToolbarTool.SPLIT -> onToggleSplit()
            ToolbarTool.FLOATING -> onFloatingChange(!state.settings.floatingKeyboard)
            ToolbarTool.FLASHLIGHT -> onFlashlightToggle()
            ToolbarTool.COMPASS -> onPanelChange(PanelMode.COMPASS)
            ToolbarTool.LEVEL -> onPanelChange(PanelMode.LEVEL)
            ToolbarTool.UNDO -> onUndoRedo(false)
            ToolbarTool.REDO -> onUndoRedo(true)
            ToolbarTool.MOON_PHASE -> onPanelChange(PanelMode.MOON_PHASE)
            ToolbarTool.WEATHER -> onPanelChange(PanelMode.WEATHER)
            ToolbarTool.CALENDAR -> onPanelChange(PanelMode.CALENDAR)
            ToolbarTool.INCOGNITO -> onIncognitoToggle()
            ToolbarTool.THEMES -> onPanelChange(PanelMode.THEMES)
            ToolbarTool.AUTOCORRECT -> onAutocorrectToggle()
            ToolbarTool.SOUND_HAPTICS -> onPanelChange(PanelMode.SOUND_HAPTICS)
            ToolbarTool.NUMPAD -> onPanelChange(PanelMode.NUMPAD)
            ToolbarTool.HANDWRITING ->
                if (BuildConfig.ENABLE_ML_KIT_HANDWRITING) onPanelChange(PanelMode.HANDWRITING)
            ToolbarTool.CAMERA -> onPanelChange(PanelMode.CAMERA)
            ToolbarTool.DICTIONARY -> onPanelChange(PanelMode.DICTIONARY)
            ToolbarTool.TRANSLATE -> onPanelChange(PanelMode.TRANSLATE)
            ToolbarTool.GIF -> onPanelChange(PanelMode.GIF)
            ToolbarTool.STICKER -> onPanelChange(PanelMode.STICKER)
            ToolbarTool.WEB_SEARCH -> onPanelChange(PanelMode.WEB_SEARCH)
            ToolbarTool.IMAGE_SEARCH -> onPanelChange(PanelMode.IMAGE_SEARCH)
            ToolbarTool.OCR -> if (BuildConfig.ENABLE_ML_KIT_SCANNERS) onPanelChange(PanelMode.OCR)
            ToolbarTool.QR_SCAN -> if (BuildConfig.ENABLE_ML_KIT_SCANNERS) onPanelChange(PanelMode.QR_SCAN)
            // Not a panel: the scanner is a full-screen Google activity.
            ToolbarTool.DOC_SCAN -> if (BuildConfig.ENABLE_ML_KIT_SCANNERS) onDocScan()
            ToolbarTool.VOICE -> onPanelChange(PanelMode.VOICE)
            ToolbarTool.GRAMMAR -> if (BuildConfig.ENABLE_GRAMMAR) onPanelChange(PanelMode.GRAMMAR)
            ToolbarTool.WIKIPEDIA -> onPanelChange(PanelMode.WIKIPEDIA)
            ToolbarTool.SYMBOLS -> onPanelChange(PanelMode.SYMBOLS)
            ToolbarTool.CALCULATOR -> onPanelChange(PanelMode.CALCULATOR)
            ToolbarTool.UNIT_CONVERT -> onPanelChange(PanelMode.UNIT_CONVERT)
            ToolbarTool.CURRENCY -> onPanelChange(PanelMode.CURRENCY)
            ToolbarTool.QR_GEN -> onPanelChange(PanelMode.QR_GEN)
            ToolbarTool.PASSWORD_GEN -> onPanelChange(PanelMode.PASSWORD_GEN)
            ToolbarTool.TYPING_TEST -> onPanelChange(PanelMode.TYPING_TEST)
            ToolbarTool.MEDIA_CONTROL -> onPanelChange(PanelMode.MEDIA_CONTROL)
            ToolbarTool.AI -> onPanelChange(PanelMode.AI)
            ToolbarTool.MODES -> onPanelChange(PanelMode.MODES)
            // Same moves the text-editing panel offers, one tap deep instead
            // of two. Selection still extends when the panel's select mode is
            // on, since onTextEdit reads that state itself.
            ToolbarTool.CURSOR_LEFT -> onTextEdit(TextEditAction.LEFT)
            ToolbarTool.CURSOR_RIGHT -> onTextEdit(TextEditAction.RIGHT)
            ToolbarTool.CURSOR_WORD_LEFT -> onTextEdit(TextEditAction.WORD_LEFT)
            ToolbarTool.CURSOR_WORD_RIGHT -> onTextEdit(TextEditAction.WORD_RIGHT)
            ToolbarTool.CURSOR_UP -> onTextEdit(TextEditAction.UP)
            ToolbarTool.CURSOR_DOWN -> onTextEdit(TextEditAction.DOWN)
            ToolbarTool.CURSOR_HOME -> onTextEdit(TextEditAction.HOME)
            ToolbarTool.CURSOR_END -> onTextEdit(TextEditAction.END)
            ToolbarTool.PAGE_UP -> onTextEdit(TextEditAction.PAGE_UP)
            ToolbarTool.PAGE_DOWN -> onTextEdit(TextEditAction.PAGE_DOWN)
            ToolbarTool.SELECT_WORD -> onTextEdit(TextEditAction.SELECT_WORD)
            ToolbarTool.HIDE_KEYBOARD -> onHideKeyboard()
        }
    }

    val body: @Composable ColumnScope.(KeyboardUiState) -> Unit = { bodyState ->
        CompositionLocalProvider(
            LocalKeyPressFeedback provides onKeyPressed,
            LocalHapticFeedback provides onHaptic,
            LocalKeySound provides onKeySound,
            LocalClipboardKeyAction provides onClipboardKey,
            LocalCanDelete provides canDelete,
            LocalCanDeleteField provides canDeleteField,
            LocalDeleteWord provides onDeleteWord,
            LocalCursorMoveVertical provides onCursorMoveVertical,
            LocalHideKeyboard provides onHideKeyboard,
            LocalTouchExploration provides rememberTouchExploration(),
        ) {
            KeyboardBody(
                state = bodyState,
                onDismissInlineSuggestions = onDismissInlineSuggestions,
                onSmartAccept = onSmartAccept,
                onSmartOpen = onSmartOpen,
                onToolPrefillConsumed = onToolPrefillConsumed,
                onHideKeyboard = onHideKeyboard,
                onKey = onKey,
                onText = onText,
                onGesture = onGesture,
                onGesturePreview = onGesturePreview,
                onGestureWords = onGestureWords,
                onCursorMove = onCursorMove,
                onLayoutSelect = onLayoutSelect,
                onSuggestion = onSuggestion,
                onEmoji = onEmoji,
                onEmojiVariant = onEmojiVariant,
                onEmojiFavourite = onEmojiFavourite,
                onEmojiSuggestion = onEmojiSuggestion,
                onPunctuation = onPunctuation,
                onEmojiQueryTap = onEmojiQueryTap,
                onEmojiRecentsClear = onEmojiRecentsClear,
                onEmojiRecentRemove = onEmojiRecentRemove,
                onEmojiFavouritesReorder = onEmojiFavouritesReorder,
                onEmojiSearchFieldDelete = onEmojiSearchFieldDelete,
                onTextEdit = onTextEdit,
                onPanelChange = onPanelChange,
                onClipboardItem = onClipboardItem,
                onClipboardPin = onClipboardPin,
                onClipboardDelete = onClipboardDelete,
                onClipboardSearchToggle = onClipboardSearchToggle,
                onClipboardSuggestionDismiss = onClipboardSuggestionDismiss,
                onSnippet = onSnippet,
                onToolTap = onToolTap,
                onToolbarToolsChange = onToolbarToolsChange,
                onToolboxOrderChange = onToolboxOrderChange,
                onToolSettings = onToolSettings,
                onToolboxHintDismiss = onToolboxHintDismiss,
                onWeatherRefresh = onWeatherRefresh,
                onCameraSend = onCameraSend,
                onCameraPermissionRequest = onCameraPermissionRequest,
                onCalendarPermissionRequest = onCalendarPermissionRequest,
                onScannedInsert = onScannedInsert,
                onScannedUrlOpen = onScannedUrlOpen,
                onVoiceToggle = onVoiceToggle,
                onVoicePermissionRequest = onVoicePermissionRequest,
                onVoiceUndo = onVoiceUndo,
                onVoiceModelDownload = onVoiceModelDownload,
                onWhisperTranslateToggle = onWhisperTranslateToggle,
                onOpenVoiceSettings = onOpenVoiceSettings,
                onMediaPlayPause = onMediaPlayPause,
                onMediaNext = onMediaNext,
                onMediaPrevious = onMediaPrevious,
                onMediaSeek = onMediaSeek,
                onMediaAccessRequest = onMediaAccessRequest,
                onMediaResume = onMediaResume,
                onDictionaryLookup = onDictionaryLookup,
                onDictionarySearchToggle = onDictionarySearchToggle,
                onDictionaryInsert = onDictionaryInsert,
                onThemeSelect = onThemeSelect,
                onSoundHaptic = onSoundHaptic,
                onHandwritingStroke = onHandwritingStroke,
                onKeyboardHandwritingStroke = onKeyboardHandwritingStroke,
                onHandwritingUndo = onHandwritingUndo,
                onHandwritingDownload = onHandwritingDownload,
                onMediaQueryTap = onMediaQueryTap,
                onMediaRetry = onMediaRetry,
                onGifSelect = onGifSelect,
                onGifSourceSelect = onGifSourceSelect,
                onWebResult = onWebResult,
                onWebResultOpen = onWebResultOpen,
                onImageResult = onImageResult,
                onImageResultLink = onImageResultLink,
                onTranslateTarget = onTranslateTarget,
                onTranslateReplace = onTranslateReplace,
                onTranslateInsert = onTranslateInsert,
                onGrammarFix = onGrammarFix,
                onGrammarFixAll = onGrammarFixAll,
                onGrammarDismiss = onGrammarDismiss,
                onGrammarDialect = onGrammarDialect,
                onGrammarFocus = onGrammarFocus,
                onWikiOpen = onWikiOpen,
                onWikiBack = onWikiBack,
                onWikiLoadLinks = onWikiLoadLinks,
                onWikiLoadFull = onWikiLoadFull,
                onSymbolInsert = onSymbolInsert,
                onSymbolSetSelect = onSymbolSetSelect,
                onModeSelect = onModeSelect,
                onToolInsert = onToolInsert,
                onUnitSelection = onUnitSelection,
                onCurrencyPairChange = onCurrencyPairChange,
                onCurrencyRefresh = onCurrencyRefresh,
                onPwSetting = onPwSetting,
                onTypingTestAction = onTypingTestAction,
                onQrSend = onQrSend,
                onAiAction = onAiAction,
                onAiReplace = onAiReplace,
                onAiInsert = onAiInsert,
                onAiRetry = onAiRetry,
                onAiRunCustom = onAiRunCustom,
                onAiPickModel = onAiPickModel,
                onAiToggleStripMarkdown = onAiToggleStripMarkdown,
                onOpenToolSettings = onOpenToolSettings,
                onOpenRoute = onOpenRoute,
            )
        }
    }

    KeyboardThemeProvider(settings = state.settings) {
        if (state.settings.floatingKeyboard) {
            // Floating mode: the compose root spans the whole IME window with
            // no background; the service restricts the touchable region to
            // the panel so everything else falls through to the app behind.
            FloatingKeyboardFrame(
                state = state,
                onDock = { onFloatingChange(false) },
                onMoved = onFloatingMoved,
                onResized = onFloatingResized,
                onBounds = onFloatingBounds,
                content = { heightScale ->
                    // Key height carries the whole layout (panels included),
                    // so scaling it scales the keyboard's height.
                    val scaled = if (heightScale == 1f) state else state.copy(
                        settings = state.settings.copy(
                            keyHeightDp = (state.settings.keyHeightDp * heightScale).roundToInt(),
                            numberRowHeightDp = (state.settings.numberRowHeightDp * heightScale).roundToInt(),
                        ),
                    )
                    body(scaled)
                },
            )
            return@KeyboardThemeProvider
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            BoardBackground(LocalKbTheme.current)
            // navigationBarsPadding keeps the bottom key row clear of the
            // gesture-navigation bar on edge-to-edge (SDK 35+) IME windows.
            val oneHanded = state.settings.oneHandedMode
            val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val ohProfile = state.settings.oneHanded.forLandscape(landscape)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // Extra breathing room above the gesture bar, adjustable
                    // in Settings → Appearance.
                    .padding(bottom = state.settings.bottomPaddingDp.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Flip to the other side: update the live mode and remember the
                // new side as this orientation's default.
                val flipSide: () -> Unit = {
                    val next =
                        if (oneHanded == OneHandedMode.LEFT) OneHandedSide.RIGHT
                        else OneHandedSide.LEFT
                    onOneHandedSide(landscape, next)
                    onOneHanded(next.toMode())
                }
                if (oneHanded == OneHandedMode.OFF) {
                    // Resizable width: below 100% the keyboard shrinks and sits
                    // at the chosen edge (or centered).
                    val widthFraction = state.settings.keyboardWidthPercent / 100f
                    val slack = 1f - widthFraction
                    val leftSlack = when (state.settings.keyboardAlignment) {
                        KeyboardAlignment.LEFT -> 0f
                        KeyboardAlignment.CENTER -> slack / 2f
                        KeyboardAlignment.RIGHT -> slack
                    }
                    if (leftSlack > 0.001f) Spacer(modifier = Modifier.weight(leftSlack))
                    Column(modifier = Modifier.weight(widthFraction)) { body(state) }
                    val rightSlack = slack - leftSlack
                    if (rightSlack > 0.001f) Spacer(modifier = Modifier.weight(rightSlack))
                } else {
                    // One-handed: dock to the live side with this orientation's
                    // width and height scale. The weights sum to 1 so the body
                    // is exactly `widthFraction` of the screen and any leftover
                    // beyond the rail becomes centre-ward slack.
                    val widthFraction = (ohProfile.widthPercent / 100f).coerceIn(0.30f, 0.90f)
                    val leftover = 1f - widthFraction
                    val railWeight = ONE_HANDED_RAIL_WEIGHT.coerceAtMost(leftover)
                    val slack = (leftover - railWeight).coerceAtLeast(0f)
                    val ohState = if (ohProfile.heightScale >= 100) state else state.copy(
                        settings = state.settings.copy(
                            keyHeightDp =
                                (state.settings.keyHeightDp * ohProfile.heightScale / 100).coerceAtLeast(1),
                            numberRowHeightDp =
                                (state.settings.numberRowHeightDp * ohProfile.heightScale / 100).coerceAtLeast(1),
                        ),
                    )
                    val rail = @Composable {
                        OneHandedRail(
                            current = oneHanded,
                            onFlip = flipSide,
                            onExit = { onOneHanded(OneHandedMode.OFF) },
                            modifier = Modifier.weight(railWeight),
                        )
                    }
                    if (oneHanded == OneHandedMode.RIGHT) {
                        if (slack > 0.001f) Spacer(modifier = Modifier.weight(slack))
                        rail()
                        Column(modifier = Modifier.weight(widthFraction)) { body(ohState) }
                    } else {
                        Column(modifier = Modifier.weight(widthFraction)) { body(ohState) }
                        rail()
                        if (slack > 0.001f) Spacer(modifier = Modifier.weight(slack))
                    }
                }
            }
        }
    }
}

/**
 * Floating mode chrome: a detached, elevated panel holding the regular
 * keyboard body, movable by its drag handle and resizable from the corner
 * handle. Position is kept as fractions of the free space so it survives
 * rotation; width in dp. Both persist via the callbacks on gesture end.
 */
@Composable
private fun FloatingKeyboardFrame(
    state: KeyboardUiState,
    onDock: () -> Unit,
    onMoved: (Float, Float) -> Unit,
    onResized: (Int, Float) -> Unit,
    onBounds: (IntRect) -> Unit,
    content: @Composable ColumnScope.(Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxWidthPx = constraints.maxWidth
        val boxHeightPx = constraints.maxHeight
        val maxWidthDp = with(density) { boxWidthPx.toDp().value } - 16f
        var liveWidthDp by remember(state.settings.floatingWidthDp) {
            mutableFloatStateOf(state.settings.floatingWidthDp.toFloat())
        }
        var liveHeightScale by remember(state.settings.floatingHeightScale) {
            mutableFloatStateOf(state.settings.floatingHeightScale)
        }
        val panelWidthDp = liveWidthDp.coerceIn(FLOATING_MIN_WIDTH_DP, maxWidthDp.coerceAtLeast(FLOATING_MIN_WIDTH_DP))

        var panelSize by remember { mutableStateOf(IntSize.Zero) }
        // Live drag position in px; null = follow the persisted fractions.
        // Reset when the window size changes (rotation) so the fractions
        // re-anchor the panel.
        var dragOffset by remember(boxWidthPx, boxHeightPx) { mutableStateOf<Offset?>(null) }
        fun slackX() = (boxWidthPx - panelSize.width).coerceAtLeast(0).toFloat()
        fun slackY() = (boxHeightPx - panelSize.height).coerceAtLeast(0).toFloat()
        val offset = dragOffset ?: Offset(
            state.settings.floatingXFraction * slackX(),
            state.settings.floatingYFraction * slackY(),
        )

        Surface(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .width(panelWidthDp.dp)
                // Invisible for the first frame, before the panel has been
                // measured and placed from real sizes — avoids a flash at a
                // wrong position.
                .alpha(if (panelSize == IntSize.Zero) 0f else 1f)
                .onGloballyPositioned { coords ->
                    panelSize = coords.size
                    val position = coords.positionInWindow()
                    onBounds(
                        IntRect(
                            offset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
                            size = coords.size,
                        )
                    )
                },
            shape = RoundedCornerShape(18.dp),
            // The theme paints the panel (color + optional image); Surface
            // just supplies the shape, clip and shadow.
            color = Color.Transparent,
            shadowElevation = 10.dp,
        ) {
            Box {
                BoardBackground(LocalKbTheme.current)
                Column {
                    FloatingHandleBar(
                        onDock = onDock,
                        onDragBy = { delta ->
                            val current = dragOffset ?: offset
                            dragOffset = Offset(
                                (current.x + delta.x).coerceIn(0f, slackX()),
                                (current.y + delta.y).coerceIn(0f, slackY()),
                            )
                        },
                        onDragEnd = {
                            val end = dragOffset ?: return@FloatingHandleBar
                            onMoved(
                                if (slackX() > 0f) end.x / slackX() else 0.5f,
                                if (slackY() > 0f) end.y / slackY() else 0.5f,
                            )
                        },
                        onResizeBy = { delta ->
                            liveWidthDp = (liveWidthDp + with(density) { delta.x.toDp().value })
                                .coerceIn(FLOATING_MIN_WIDTH_DP, maxWidthDp.coerceAtLeast(FLOATING_MIN_WIDTH_DP))
                            // Height resizes too: the drag is normalized by the
                            // panel's unscaled height, so the grip tracks the
                            // finger no matter how tall the content already is.
                            // The grip sits on the panel's TOP bar, so dragging
                            // up (negative y) grows the panel — hence the minus.
                            val baseHeightPx = if (liveHeightScale > 0f) panelSize.height / liveHeightScale else 0f
                            if (baseHeightPx > 0f) {
                                liveHeightScale = (liveHeightScale - delta.y / baseHeightPx)
                                    .coerceIn(FLOATING_MIN_HEIGHT_SCALE, FLOATING_MAX_HEIGHT_SCALE)
                            }
                        },
                        onResizeEnd = { onResized(panelWidthDp.roundToInt(), liveHeightScale) },
                    )
                    content(liveHeightScale)
                }
            }
        }
    }
}

// The one-handed rail's share of the screen width. Kept small so the body
// gets its full configured width; shrinks only if the width leaves less room.
private const val ONE_HANDED_RAIL_WEIGHT = 0.16f
private const val FLOATING_MIN_WIDTH_DP = 240f
private const val FLOATING_MIN_HEIGHT_SCALE = 0.6f
private const val FLOATING_MAX_HEIGHT_SCALE = 1.6f

/** Handle row on top of the floating panel: dock button, drag pill, resize grip. */
@Composable
private fun FloatingHandleBar(
    onDock: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeBy: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Docking returns the full-width keyboard, so a fullscreen glyph reads
        // right; the old down-arrow looked like a download button.
        IconButton(onClick = onDock, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Outlined.Fullscreen,
                contentDescription = "Dock keyboard",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            onDragBy(amount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            onResizeBy(amount)
                        },
                        onDragEnd = onResizeEnd,
                        onDragCancel = onResizeEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // Classic grip lines instead of OpenInFull, whose diagonal
            // arrows read as a "go fullscreen" button rather than a
            // drag-to-resize handle.
            val gripColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(
                modifier = Modifier
                    .size(14.dp)
                    .semantics { contentDescription = "Resize keyboard" },
            ) {
                val stroke = 1.5.dp.toPx()
                drawLine(
                    gripColor,
                    Offset(size.width * 0.15f, size.height),
                    Offset(size.width, size.height * 0.15f),
                    stroke, cap = StrokeCap.Round,
                )
                drawLine(
                    gripColor,
                    Offset(size.width * 0.6f, size.height),
                    Offset(size.width, size.height * 0.6f),
                    stroke, cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Side rail shown in one-handed mode: swap sides or return to full width. */
@Composable
private fun OneHandedRail(
    current: OneHandedMode,
    onFlip: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onFlip) {
            Icon(
                if (current == OneHandedMode.LEFT) {
                    Icons.AutoMirrored.Outlined.ArrowForward
                } else {
                    Icons.AutoMirrored.Outlined.ArrowBack
                },
                contentDescription = "Move keyboard to the other side",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onExit) {
            Icon(
                Icons.Outlined.Fullscreen,
                contentDescription = "Exit one-handed mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- top bar: suggestions or toolbar ----

/**
 * One duration for every toolbar transition: the strip/toolbar chevron
 * rotation, the back chevron's enter and exit, and the icon slides.
 *
 * They all fire off the same two taps — flip the bar, open a panel — so any
 * two that disagree read as one animation lagging the other rather than as
 * two separate animations. The icon slides used to run on a much softer
 * spring and were still travelling long after the chevron had settled.
 */
private const val ToolbarMotionMs = 140

/**
 * The matching spring for anything that slides within the toolbar. Tuned to
 * settle in about [ToolbarMotionMs] so it lands with the fades.
 */
private val ToolbarSlideSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = IntOffset(1, 1),
)

/**
 * The strip's candidates fade in when they arrive and out when they leave,
 * rather than snapping. The fade-in is held a beat behind the emoji's slide
 * (the icon leads the eye into the strip) and runs long enough to read as a
 * fade rather than a flash; the fade-out is quicker, since it has to finish
 * inside the settle beat before the toolbar takes the row (see emptySettled).
 */
private const val StripContentStaggerMs = 45
private const val StripContentFadeInMs = 200
private const val StripContentFadeOutMs = 110

/**
 * How long the strip waits, after the candidates go empty, before it starts
 * hiding them. Fast typing empties the strip for a frame or two between
 * keystrokes (the engine clears then refills across the async compute), and
 * hiding on the first empty made the candidates pulse out and back in on every
 * keystroke. The hide is deferred by this beat; a fresh candidate landing
 * inside it cancels the pending hide, so a continuous typing burst never
 * flickers. Sized under the space of one relaxed keystroke so a genuine stop
 * still clears promptly.
 */
private const val StripHideDebounceMs = 180

/**
 * Issue-A tools fade: the toolbox and pinned tools materialise as the toolbar
 * takes over from the strip. Held back a beat so the emoji — which slides
 * across rather than fading — clears the toolbox slot first, instead of the
 * two overlapping mid-animation.
 */
private const val ToolbarToolsStaggerMs = 55

/**
 * Full-bleed return fade: the whole toolbar (emoji included) fades in when a
 * full-bleed panel closes and rebuilds the bar. Slower than the in-place
 * [ToolbarMotionMs] slide — nothing is moving to carry the eye, so a brisk
 * fade reads as an instant pop; a longer one lets the bar settle in.
 */
private const val FullBleedReturnFadeMs = 260

@Composable
private fun TopBar(
    state: KeyboardUiState,
    onSuggestion: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onEmojiSuggestion: (String) -> Unit,
    onPunctuation: (String) -> Unit = {},
    onPanelChange: (PanelMode) -> Unit,
    onToolTap: (ToolbarTool) -> Unit,
    drag: ToolDragController,
    onVoiceToggle: () -> Unit = {},
    onVoiceUndo: () -> Unit = {},
    onVoicePermissionRequest: () -> Unit = {},
    onDismissInlineSuggestions: () -> Unit = {},
    onSmartAccept: () -> Unit = {},
    onSmartOpen: () -> Unit = {},
    onClipboardSuggestion: (ClipItem) -> Unit = {},
    onClipboardSuggestionDismiss: () -> Unit = {},
    /** Downward flick on the strip: dismiss the keyboard (opt-in). */
    onSwipeDownHide: () -> Unit = {},
) {
    // "Show the toolbar instead" while suggestions are up; resets once the
    // suggestions go away so the bar returns to candidates next time.
    var toolbarOverride by remember { mutableStateOf(false) }
    // Button-mode emoji row: a toolbar toggle swaps the strip for emojis.
    var emojiBarOpen by remember { mutableStateOf(false) }
    // A smart chip counts as strip content: without it the bar would flip
    // to the toolbar the moment word candidates ran out, taking the answer
    // to what was just typed with it. The recently-copied paste chip counts
    // the same way, so an idle strip holds it instead of flipping to the tools.
    val recentClipChip = state.settings.clipboard.suggestRecent && state.clipboardSuggestion != null
    val hasSuggestions = state.suggestions.isNotEmpty() ||
        state.emojiSuggestions.isNotEmpty() || state.smart != null || recentClipChip
    // Suggestions-first mode keeps the strip as the resting state (an empty
    // strip plus the chevron into the toolbar); the override then survives
    // idle gaps and instead resets when fresh candidates arrive.
    val suggestionsFirst = state.settings.suggestionStrip.suggestionsFirst && state.settings.suggestions
    // The emoji panel is already all emojis — showing the row too would be
    // redundant, so opening the panel folds the row away.
    //
    // The fold is a derived flag read in the same pass, and the stored one is
    // cleared in an effect. Assigning to it inline instead wrote state that
    // this same composable reads further down, which forces a second
    // composition on the frame the panel opens — the row drew one frame at
    // its old size before folding, which is the flicker seen when the emoji
    // panel comes up.
    val emojiRowSuppressed =
        state.settings.emojiBarMode != EmojiBarMode.BUTTON || state.panel == PanelMode.EMOJI
    LaunchedEffect(emojiRowSuppressed) {
        if (emojiRowSuppressed) emojiBarOpen = false
    }
    // Committing a word can empty the strip for the moment it takes the
    // next-word predictions to land; flipping to the toolbar for that gap
    // makes the whole bar flicker on every space. The toolbar only takes
    // over once the strip has stayed empty for a beat.
    //
    // The beat is the hide debounce (which absorbs the typing-burst gaps, see
    // [StripHideDebounceMs]) plus the content fade-out, so the candidates
    // finish fading before the toolbar takes the row rather than being cut
    // mid-fade. During the debounce the strip still shows the last candidates
    // (held behind alpha 1), not an empty bar, so this no longer reads as the
    // keyboard stalling on a blank strip.
    var emptySettled by remember { mutableStateOf(true) }
    // One effect owns both the settle beat and the override reset, so the
    // override's live value can be read before it is cleared — two effects
    // racing on the same key left showToolbar reading a half-updated pair.
    LaunchedEffect(hasSuggestions) {
        if (hasSuggestions) {
            emptySettled = false
            // Suggestions-first rests on the strip, so a hand-opened toolbar
            // override only clears once fresh candidates actually arrive.
            if (suggestionsFirst) toolbarOverride = false
        } else {
            // Is the hand-opened toolbar the surface right now? Captured before
            // the reset just below clears it.
            val leavingOverrideToolbar = toolbarOverride && !suggestionsFirst
            // Candidates left: drop the override so the next ones show as
            // candidates again instead of staying hidden behind the toolbar.
            if (!suggestionsFirst) toolbarOverride = false
            if (leavingOverrideToolbar) {
                // Already on the toolbar the user opened by hand, and with no
                // suggestions the resting surface is the toolbar too — so hand
                // straight across, no settle gap. Delaying instead collapsed
                // the bar to an empty strip for that beat and bounced back: the
                // flip-flop that flung the emoji out to the row edge and popped
                // every other tool.
                emptySettled = true
            } else {
                delay((StripHideDebounceMs + StripContentFadeOutMs).toLong())
                emptySettled = true
            }
        }
    }
    val showToolbar = state.panel != PanelMode.NONE || toolbarOverride ||
        (!hasSuggestions && emptySettled && !suggestionsFirst)

    // Previous toolbar state, advanced after each frame commits; drives the
    // synchronous reveal blank for the tools fade below.
    var prevShowToolbar by remember { mutableStateOf(showToolbar) }
    SideEffect { prevShowToolbar = showToolbar }

    // The strip renders [shownSuggestions]/[shownEmojiSuggestions] — the last
    // non-empty candidates — rather than the live state, so a cleared field
    // fades the old candidates out over [StripContentFadeOutMs] instead of
    // blanking them the instant state empties. They refresh whenever real
    // candidates arrive and are held (behind alpha 0) once they leave.
    val suggestionsShowing = state.suggestions.isNotEmpty() || state.emojiSuggestions.isNotEmpty()
    // Candidates are on screen only while the strip itself holds the row — not
    // when the toolbar or a panel does. The fade keys on this, not on the
    // candidates alone: candidates can already exist, hidden behind the
    // toolbar, so when the strip retakes the row (a toolbox close, the chevron
    // toggled back) they never "arrive" — a presence-keyed fade wouldn't fire
    // and they'd snap on at full strength over the still-sliding emoji.
    val stripContentVisible = !showToolbar && suggestionsShowing
    var shownSuggestions by remember { mutableStateOf(state.suggestions) }
    var shownEmojiSuggestions by remember { mutableStateOf(state.emojiSuggestions) }
    // Punctuation chips are held alongside the words so they fade out with them
    // rather than blanking. The service only fills them when word candidates
    // are present, so they follow the same non-empty gate.
    var shownPunctuation by remember { mutableStateOf(state.punctuationSuggestions) }
    LaunchedEffect(state.suggestions, state.emojiSuggestions) {
        if (state.suggestions.isNotEmpty() || state.emojiSuggestions.isNotEmpty()) {
            shownSuggestions = state.suggestions
            shownEmojiSuggestions = state.emojiSuggestions
            shownPunctuation = state.punctuationSuggestions
        }
    }
    // Fade in when the strip shows its candidates, out when it stops. Keyed on
    // strip visibility (see [stripContentVisible]), so it fires both when
    // candidates land while the strip is up and when the strip retakes the row
    // from the toolbar with candidates already present — the latter used to
    // snap them on over the sliding emoji. It never re-fires mid-word, since
    // the engine updates candidates in place without emptying first. Initialised
    // to the current state so a strip that opens already showing doesn't fade in
    // from nothing.
    val stripContentAlpha = remember { Animatable(if (stripContentVisible) 1f else 0f) }
    LaunchedEffect(stripContentVisible, state.settings.reduceMotion) {
        if (state.settings.reduceMotion) {
            // No fade, but the hide still debounces so a typing-burst gap
            // doesn't blink the strip off and back on. Deferring a snap adds no
            // motion, so reduce-motion is honoured.
            if (stripContentVisible) {
                stripContentAlpha.snapTo(1f)
            } else {
                delay(StripHideDebounceMs.toLong())
                stripContentAlpha.snapTo(0f)
            }
        } else if (stripContentVisible) {
            // Let the emoji lead into the strip before the words follow. Linear
            // rather than the default eased curve, which front-loads the ramp
            // and made even a long fade read as an instant pop.
            delay(StripContentStaggerMs.toLong())
            stripContentAlpha.animateTo(1f, tween(StripContentFadeInMs, easing = LinearEasing))
        } else {
            // Defer the hide: the effect is keyed on visibility, so candidates
            // landing (or the strip retaking the row) inside the debounce cancel
            // this and restart the fade-in branch — the pulse fast typing caused.
            delay(StripHideDebounceMs.toLong())
            stripContentAlpha.animateTo(0f, tween(StripContentFadeOutMs, easing = LinearEasing))
        }
    }

    // The toolbar's tools are freshly composed when it takes over from the
    // strip, so [animatePlacement] settles them in place with no motion — they
    // would pop while the emoji (which hands its position across) slides. So
    // they fade in instead. Two shapes:
    //
    //  - In-place strip→toolbar flip: the emoji slides its position across (its
    //    throughline), and the other tools fade in held a beat behind so the
    //    icon clears the toolbox slot first (see [ToolbarToolsStaggerMs]). The
    //    surviving node keeps its old alpha of 1, so [toolbarJustRevealed]
    //    blanks its first frame rather than the initial value below.
    //
    //  - Fresh mount (returning from a full-bleed gif/emoji panel disposes and
    //    rebuilds the whole bar): nothing is sliding, everything simply
    //    appears, so the whole toolbar — emoji included — fades in together, no
    //    stagger. [toolsFade] starts at 0 so it fades from blank instead of
    //    painting one frame opaque, snapping to 0, and refading (the jitter).
    val toolsFade = remember {
        Animatable(if (showToolbar && !state.settings.reduceMotion) 0f else 1f)
    }
    // Whether the emoji joins the tools' fade (fresh mount) or sits it out and
    // slides (in-place flip). Seeded for a mount that opens on the toolbar.
    var toolsFadeMounted by remember { mutableStateOf(false) }
    var emojiFadesWithTools by remember {
        mutableStateOf(showToolbar && !state.settings.reduceMotion)
    }
    val toolbarJustRevealed = !prevShowToolbar && showToolbar && !state.settings.reduceMotion
    LaunchedEffect(showToolbar, state.settings.reduceMotion) {
        val freshMount = !toolsFadeMounted
        toolsFadeMounted = true
        if (!showToolbar || state.settings.reduceMotion) {
            emojiFadesWithTools = false
            toolsFade.snapTo(1f)
        } else {
            // A mount fades the emoji in with the tools; a later in-place flip
            // lets it slide instead and trails the tools behind it. The mount
            // fade is slower ([FullBleedReturnFadeMs]) since nothing slides to
            // carry the eye; the in-place one matches the emoji's slide.
            emojiFadesWithTools = freshMount
            toolsFade.snapTo(0f)
            if (!freshMount) delay(ToolbarToolsStaggerMs.toLong())
            toolsFade.animateTo(1f, tween(if (freshMount) FullBleedReturnFadeMs else ToolbarMotionMs))
        }
    }
    val toolContentAlpha = { if (toolbarJustRevealed) 0f else toolsFade.value }

    // The suggestion strip (and the toolbar that shares its bar) lays out
    // right-to-left for RTL scripts — Arabic, Hebrew, Persian, Urdu, Thaana —
    // so the first/best candidate sits on the right, where an RTL reader's eye
    // starts. Only this bar is flipped; the key grid is a sibling composable
    // and stays left-to-right.
    val stripDirection = if (state.script.direction == TextDirection.RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(LocalLayoutDirection provides stripDirection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(topBarHeight(state.settings))
            // A deliberate downward flick anywhere on the strip dismisses the
            // keyboard. A tool's own reorder is a hold-then-drag, so it fires
            // its long-press first and never reaches this detector; a quick
            // flick never trips the long-press, so the two don't collide.
            .then(
                if (state.settings.toolbarBehavior.swipeDownHide) {
                    Modifier.pointerInput(onSwipeDownHide) {
                        val threshold = ToolbarSwipeHideThreshold.toPx()
                        var travelled = 0f
                        var fired = false
                        detectVerticalDragGestures(
                            onDragStart = { travelled = 0f; fired = false },
                            onDragEnd = { travelled = 0f; fired = false },
                            onDragCancel = { travelled = 0f; fired = false },
                        ) { _, dragAmount ->
                            travelled += dragAmount
                            if (!fired && travelled > threshold) {
                                fired = true
                                onSwipeDownHide()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val feedback = LocalKeyPressFeedback.current
        // Compact dictation bar takes over the whole strip while active;
        // the keys below stay usable for fixing recognition errors.
        if (state.voice.strip) {
            VoiceStripBar(
                state = state,
                onToggle = onVoiceToggle,
                onUndo = onVoiceUndo,
                onRequestPermission = onVoicePermissionRequest,
                // The tool tap toggles the strip, so it also closes it.
                onClose = { onToolTap(ToolbarTool.VOICE) },
                modifier = Modifier.weight(1f),
            )
            return@Row
        }
        if (emojiBarOpen && !emojiRowSuppressed && !hasSuggestions) {
            EmojiBarStrip(
                state = state,
                onEmoji = onEmoji,
                onOpenPanel = { onPanelChange(PanelMode.EMOJI) },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    feedback()
                    emojiBarOpen = false
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close emoji row",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Row
        }
        val motionMs = if (state.settings.reduceMotion) 0 else ToolbarMotionMs
        // One chevron that spins between the two directions, rather than
        // two icons swapped instantly: the rotation is what tells the
        // user the bar flipped, since the strip and the toolbar look
        // nothing alike and a hard cut reads as a redraw.
        //
        // Both the rotation state and the visibility gate live out here,
        // above the guard. Inside it they were useless: the bar auto-flips
        // to the toolbar at the moment the strip runs dry, which is the same
        // moment `hasSuggestions` goes false and the guard drops the button.
        // The node died on the very frame the rotation was meant to play, so
        // the chevron vanished mid-turn instead of turning.
        val chevronTurn by animateFloatAsState(
            targetValue = if (showToolbar) 180f else 0f,
            animationSpec = if (state.settings.reduceMotion) snap() else tween(ToolbarMotionMs),
            label = "chevronTurn",
        )
        // While any panel is open the toolbar is forced on and shows its own
        // back chevron, so the suggestions-toggle chevron would sit next to
        // it doing nothing — tools don't need suggestions. Hide it.
        //
        // Fade only, never expandHorizontally/shrinkHorizontally. A width
        // animation here re-measures the whole toolbar on every frame of the
        // transition, and the pinned icons track their position through
        // [animatePlacement], which reads its target in onPlaced — so each of
        // those frames handed every icon a new target and restarted its
        // spring. That is what made opening the bar look like the icons were
        // shivering. Fading keeps the layout change atomic: one reflow, one
        // spring per icon.
        // The exit releases the slot at once instead of fading. AnimatedVisibility
        // holds a child's space for the whole exit, so a fade here meant the
        // strip swapped for the toolbar immediately, the chevron then sat
        // fading in a 36dp gap, and only 140ms later did that gap close and
        // shove everything sideways a second time. Two layout jumps around one
        // decision is the jitter. One jump, and the icons spring into it.
        //
        // Tied to the strip being up, not to there being suggestions. Those
        // are not the same instant: the strip stays for a beat after the last
        // suggestion goes (see emptySettled), and keying on suggestions alone
        // pulled the chevron out at the front of that beat. Its 36dp then
        // vanished from the middle of a strip that was still on screen, so
        // everything left of it — the emoji icon included — slid across
        // before the handoff to the toolbar had even begun, and the icon
        // started its slide from a position it had already been shoved out of.
        // Now the chevron leaves on the same frame the toolbar arrives.
        AnimatedVisibility(
            visible = state.panel == PanelMode.NONE &&
                (hasSuggestions || suggestionsFirst || !showToolbar),
            enter = fadeIn(tween(motionMs)),
            exit = ExitTransition.None,
        ) {
            IconButton(
                onClick = {
                    feedback()
                    toolbarOverride = !toolbarOverride
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = if (showToolbar) "Show suggestions" else "Show toolbar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronTurn },
                )
            }
        }
        if (showToolbar) {
            ToolbarRow(state, onPanelChange, onToolTap, drag, toolContentAlpha, emojiFadesWithTools)
            if (state.settings.emojiBarMode == EmojiBarMode.BUTTON) {
                IconButton(
                    onClick = {
                        feedback()
                        emojiBarOpen = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        // Fades in with the rest of the tools on strip→toolbar.
                        .graphicsLayer { alpha = toolContentAlpha() },
                ) {
                    Icon(
                        Icons.Outlined.EmojiEmotions,
                        contentDescription = "Show emoji row",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            if (state.settings.emojiToolbar && ToolbarTool.EMOJI in state.settings.enabledTools) {
                ToolCircle(
                    icon = toolIcon(ToolbarTool.EMOJI),
                    description = "Emoji",
                    active = false,
                    // Same icon the toolbar pins: it slides between the two
                    // spots instead of vanishing here and reappearing there.
                    modifier = Modifier.animateSharedPlacement(
                        drag.emojiPlacement,
                        enabled = !state.settings.reduceMotion,
                    ) { drag.bodyCoords },
                    longPressLabel = "Emoji",
                ) { onToolTap(ToolbarTool.EMOJI) }
            }
            // Autofill chips take the whole strip while they are up: they
            // answer the field directly ("use this saved login"), which beats
            // any word the dictionary could offer, and they are transient —
            // dismissed, or gone as soon as the field is left.
            if (state.inlineSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (chip in state.inlineSuggestions) {
                        AndroidView(
                            factory = { chip },
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onDismissInlineSuggestions() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss autofill suggestions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                return@Row
            }
            // A dead key is armed: show which accent the next letter will
            // take, otherwise the keyboard looks like it swallowed a press.
            state.pendingDeadKey?.let { accent ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = accent,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // A recognised sum/conversion answers the text directly, so it
            // takes the whole strip the way autofill chips do. A keyword
            // chip ("wiki" → open Wikipedia) only claims the space it needs,
            // because the word being typed may simply be that word.
            val smart = state.smart
            val keywordChip = smart != null && smart.kind == SmartSuggest.Kind.TOOL
            if (smart != null) {
                // Opening runs in two halves: the service clears the trigger
                // text and stages the prefill, then the tool is tapped the
                // ordinary way so panel routing stays in one place.
                val open = {
                    onSmartOpen()
                    onToolTap(smart.tool)
                }
                SmartSuggestionChip(
                    hit = smart,
                    reduceMotion = state.settings.reduceMotion,
                    icon = toolIcon(smart.tool),
                    modifier = if (keywordChip) {
                        Modifier.padding(start = 4.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    },
                    onAccept = { if (keywordChip) open() else onSmartAccept() },
                    onOpen = open,
                )
            }
            if (smart != null && !keywordChip) return@Row
            // Recently-copied paste chip (Gboard style): takes the idle strip
            // when there are no candidates, one tap from pasting the last copy.
            // Word candidates always win the row, so it never hides a suggestion.
            val recentClip = state.clipboardSuggestion
            if (recentClipChip && smart == null) {
                ClipboardSuggestionChip(
                    clip = recentClip,
                    onPaste = { onClipboardSuggestion(recentClip) },
                    onDismiss = onClipboardSuggestionDismiss,
                    stretch = !suggestionsShowing,
                    modifier = if (suggestionsShowing) {
                        Modifier.widthIn(max = 160.dp).padding(horizontal = 4.dp)
                    } else {
                        Modifier.weight(1f).padding(horizontal = 4.dp)
                    },
                )
                if (!suggestionsShowing) return@Row
            }
            // The top candidates split the whole bar evenly (Gboard style),
            // so each one gets the largest possible tap target.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Fades in a beat behind the emoji's slide as candidates
                    // arrive, and out as they leave (see [stripContentAlpha]).
                    .graphicsLayer { alpha = stripContentAlpha.value },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The held set, so a cleared field fades the last words out
                // rather than blanking them; taps are gated to the live ones.
                val ranked = shownSuggestions.take(3)
                // Gboard convention: the primary candidate sits in the middle
                // slot with the runner-up on its left. The commit path still
                // uses the engine's order — this is display-only.
                val centerPrimary = state.settings.suggestionStrip.suggestionPrimaryCenter && ranked.size >= 2
                val shown = if (centerPrimary) {
                    listOf(ranked[1], ranked[0]) + ranked.drop(2)
                } else {
                    ranked
                }
                val primaryIndex = if (centerPrimary) 1 else 0
                shown.forEachIndexed { index, suggestion ->
                    if (index > 0) {
                        VerticalDivider(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = suggestionsShowing) { onSuggestion(suggestion) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            // Follows the live shift state, so pressing shift
                            // re-cases the strip (matching the committed word).
                            text = displayCaseForShift(suggestion, state.shiftState),
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (index == primaryIndex) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Emoji candidates ride along after the words: typing "birthday"
            // puts 🎂 🎉 🥳 🎁 one tap away. Held set, so they fade out with
            // the words rather than vanishing; taps gated to the live ones.
            for (emoji in shownEmojiSuggestions.take(4)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .graphicsLayer { alpha = stripContentAlpha.value }
                        .clickable(enabled = suggestionsShowing) { onEmojiSuggestion(emoji) }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 22.sp, fontFamily = LocalEmojiFontFamily.current)
                }
            }
            // Quick-punctuation chips ride the tail (the service leaves the list
            // empty whenever an emoji prediction claimed it, so the two never
            // fight for the row). A leading divider sets them off from the words.
            if (shownPunctuation.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .graphicsLayer { alpha = stripContentAlpha.value },
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                for (mark in shownPunctuation) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .graphicsLayer { alpha = stripContentAlpha.value }
                            .clickable(enabled = suggestionsShowing) { onPunctuation(mark) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mark,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * The recently-copied paste chip shown on the suggestion strip: an accent pill
 * that pastes the last copied text on tap, with a trailing dismiss button. Styled
 * to match [SmartSuggestionChip] so the two chips read as one family.
 */
@Composable
private fun ClipboardSuggestionChip(
    clip: ClipItem,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val tint = kb.accent
    val fill = tint.copy(alpha = if (kb.dark) 0.20f else 0.11f)
    
    val bitmap by produceState<ImageBitmap?>(initialValue = null, clip.imagePath) {
        if (clip.kind == ClipKind.IMAGE && clip.imagePath != null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(clip.imagePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 64 &&
                        bounds.outHeight / (sample * 2) >= 64
                    ) {
                        sample *= 2
                    }
                    BitmapFactory.decodeFile(clip.imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.asImageBitmap()
                }.getOrNull()
            }
        } else {
            value = null
        }
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = stretch)
                .fillMaxHeight()
                .clickable {
                    feedback()
                    onPaste()
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (clip.kind == ClipKind.IMAGE && bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.22f)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (clip.kind == ClipKind.IMAGE) Icons.Outlined.Image else Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            val textToDisplay = when {
                clip.kind == ClipKind.IMAGE -> if (clip.sourceApp == "System UI") "Screenshot" else "Copied image"
                clip.text.isNotBlank() -> clip.text
                else -> "Copied item"
            }
            Text(
                text = textToDisplay,
                color = kb.keyText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable {
                    feedback()
                    onDismiss()
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Dismiss copied text",
                tint = tint.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Fallback content for the dedicated emoji row before any usage exists. */
private val DEFAULT_BAR_EMOJIS = listOf(
    "😂", "❤️", "😊", "👍", "🙏", "😭", "🎉", "🥰", "😅", "🔥", "🤔", "👏",
)

/**
 * Height of the dedicated emoji row. The emoji panel absorbs it while
 * open (the row hides there), so the keyboard's total height never
 * changes when switching between keys and the emoji panel.
 */
private val EmojiBarHeight = 40.dp

/**
 * The dedicated emoji row (Gboard style): favourites and/or most-used
 * emojis one tap from any screen, with a launcher into the full panel.
 * Used as its own row (ALWAYS) or swapped into the strip (BUTTON).
 */
@Composable
private fun EmojiBarStrip(
    state: KeyboardUiState,
    onEmoji: (String) -> Unit,
    onOpenPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Favourites already lead the recents/frequents lists (EmojiUsage pins
    // them), so each content mode is a straight pick.
    val emojis = when (state.settings.emojiBarContent) {
        EmojiBarContent.MOST_USED -> state.emojiFrequents
        EmojiBarContent.RECENTS -> state.emojiRecents
        EmojiBarContent.FAVOURITES -> state.emojiFavourites
    }.ifEmpty { DEFAULT_BAR_EMOJIS }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EmojiBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenPanel, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.EmojiEmotions,
                contentDescription = "Open emoji panel",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // SpaceEvenly kicks in while the content is narrower than the row,
        // so a handful of emojis spread across the full width instead of
        // huddling left; once there are enough to overflow it scrolls.
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            lazyRowItems(emojis) { emoji ->
                Text(
                    text = emoji,
                    modifier = Modifier
                        .clickable { onEmoji(emoji) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    fontSize = 24.sp,
                    fontFamily = LocalEmojiFontFamily.current,
                )
            }
        }
    }
}

/** Height of the dedicated symbol row (chips are text, not emoji). */
internal val SymbolRowHeight = 40.dp

/**
 * The dedicated symbol row: one symbol set's characters and snippets a tap
 * away, with a picker chip on the left switching between the enabled sets
 * (or the sets the active keyboard mode prescribes).
 */
@Composable
private fun SymbolRowStrip(
    state: KeyboardUiState,
    onInsert: (String) -> Unit,
    onSetSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    // An edited built-in is stored as a custom set under the built-in's id;
    // resolveSymbolSets shadows the shipped one rather than listing both.
    val allSets = resolveSymbolSets(settings.customSymbolSets)
    val enabledSets = settings.symbolRowSetIds
        .mapNotNull { id -> allSets.firstOrNull { it.id == id } }
        .ifEmpty { BuiltInSymbolSets.sets }
    val activeId = state.activeSymbolSetId ?: settings.symbolRowActiveSetId
    val active = enabledSets.firstOrNull { it.id == activeId } ?: enabledSets.first()
    var pickerOpen by remember { mutableStateOf(false) }
    val feedback = LocalKeyPressFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SymbolRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only offer the picker when there is something to switch to.
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = enabledSets.size > 1) {
                        feedback()
                        pickerOpen = true
                    }
                    .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    active.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (enabledSets.size > 1) {
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = "Switch symbol set",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                for (set in enabledSets) {
                    DropdownMenuItem(
                        text = { Text(set.name) },
                        trailingIcon = if (set.id == active.id) {
                            {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            pickerOpen = false
                            onSetSelect(set.id)
                        },
                    )
                }
            }
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            lazyRowItems(active.chars) { symbol ->
                Text(
                    text = symbolChipLabel(symbol),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onInsert(symbol) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The Modes panel: pick which keyboard mode is active. "Automatic" follows
 * the per-app and per-field bindings; picking a mode by hand overrides them
 * until the user switches to another app. Modes are created and edited in
 * the settings app (long-press the tool).
 */
@Composable
private fun ModesPanel(
    state: KeyboardUiState,
    onModeSelect: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val height = keyRowsHeight(state)
    val modes = state.settings.keyboardModes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (modes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No modes yet — create one in Settings → Modes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ToolPanelChip("Mode settings", selected = true, onClick = onOpenSettings)
            }
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "A manual pick lasts until you switch apps.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ToolCircle(
                icon = Icons.Outlined.Settings,
                description = "Mode settings",
                active = false,
                onClick = onOpenSettings,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                ModeRow(
                    title = "Automatic",
                    subtitle = "Follow each mode's app and field bindings",
                    icon = Icons.Outlined.AutoAwesome,
                    selected = state.activeModeId == null,
                ) { onModeSelect(null) }
            }
            lazyRowItems(modes) { mode ->
                ModeRow(
                    title = mode.name,
                    subtitle = modeSummary(mode),
                    icon = ModeIcons.icon(mode.icon),
                    selected = state.activeModeId == mode.id,
                ) { onModeSelect(mode.id) }
            }
        }
    }
}

/** One-line recap of what a mode changes and when it kicks in. */
private fun modeSummary(mode: KeyboardMode): String {
    val parts = mutableListOf<String>()
    mode.emojiBarMode?.let {
        parts += when (it) {
            EmojiBarMode.OFF -> "emoji row off"
            EmojiBarMode.BUTTON -> "emoji button"
            EmojiBarMode.ALWAYS -> "emoji row"
        }
    }
    mode.toolbarTools?.let {
        parts += if (mode.toolbarToolsAppend) "+${it.size} pinned tools" else "${it.size} pinned tools"
    }
    mode.symbolRowEnabled?.let { parts += if (it) "symbol row" else "symbol row off" }
    if (mode.apps.isNotEmpty()) {
        parts += if (mode.apps.size == 1) "1 app" else "${mode.apps.size} apps"
    }
    if (mode.fieldKinds.isNotEmpty()) {
        parts += mode.fieldKinds.joinToString(", ") { it.name.lowercase() } + " fields"
    }
    return if (parts.isEmpty()) "No overrides" else parts.joinToString(" · ")
        .replaceFirstChar { it.uppercase() }
}

@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 14.dp)
                .size(22.dp),
            // The active mode's icon carries the accent; the rest stay quiet
            // so the list reads as names first, icons second.
            tint = if (selected) kb.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Active",
                modifier = Modifier.size(20.dp),
                tint = kb.accent,
            )
        }
    }
}

// ---- customizable toolbar & toolbox ----

internal fun toolIcon(tool: ToolbarTool): ImageVector = when (tool) {
    ToolbarTool.EMOJI -> Icons.Outlined.EmojiEmotions
    ToolbarTool.CLIPBOARD -> Icons.Outlined.ContentPaste
    ToolbarTool.SNIPPETS -> Icons.AutoMirrored.Outlined.TextSnippet
    ToolbarTool.TEXT_EDIT -> Icons.Outlined.EditNote
    ToolbarTool.ONE_HANDED -> Icons.Outlined.Smartphone
    ToolbarTool.SPLIT -> Icons.Outlined.VerticalSplit
    ToolbarTool.FLOATING -> Icons.Outlined.PictureInPictureAlt
    ToolbarTool.SETTINGS -> Icons.Outlined.Settings
    ToolbarTool.FLASHLIGHT -> Icons.Outlined.FlashlightOn
    ToolbarTool.COMPASS -> Icons.Outlined.Explore
    ToolbarTool.LEVEL -> Icons.Outlined.Straighten
    ToolbarTool.UNDO -> Icons.AutoMirrored.Outlined.Undo
    ToolbarTool.REDO -> Icons.AutoMirrored.Outlined.Redo
    ToolbarTool.MOON_PHASE -> Icons.Outlined.DarkMode
    ToolbarTool.WEATHER -> Icons.Outlined.WbSunny
    ToolbarTool.CALENDAR -> Icons.Outlined.CalendarMonth
    ToolbarTool.INCOGNITO -> Icons.Outlined.VisibilityOff
    ToolbarTool.THEMES -> Icons.Outlined.Palette
    ToolbarTool.AUTOCORRECT -> Icons.Outlined.Spellcheck
    ToolbarTool.SOUND_HAPTICS -> Icons.Outlined.Vibration
    ToolbarTool.NUMPAD -> Icons.Outlined.Dialpad
    ToolbarTool.HANDWRITING -> Icons.Outlined.Draw
    ToolbarTool.CAMERA -> Icons.Outlined.PhotoCamera
    ToolbarTool.DICTIONARY -> Icons.AutoMirrored.Outlined.MenuBook
    ToolbarTool.TRANSLATE -> Icons.Outlined.Translate
    ToolbarTool.GIF -> Icons.Outlined.GifBox
    ToolbarTool.STICKER -> Icons.AutoMirrored.Outlined.StickyNote2
    ToolbarTool.WEB_SEARCH -> Icons.Outlined.TravelExplore
    ToolbarTool.IMAGE_SEARCH -> Icons.Outlined.ImageSearch
    ToolbarTool.OCR -> Icons.Outlined.TextFields
    ToolbarTool.QR_SCAN -> Icons.Outlined.QrCodeScanner
    ToolbarTool.DOC_SCAN -> Icons.Outlined.DocumentScanner
    ToolbarTool.VOICE -> Icons.Outlined.Mic
    ToolbarTool.GRAMMAR -> Icons.AutoMirrored.Outlined.FactCheck
    ToolbarTool.WIKIPEDIA -> Icons.Outlined.Public
    ToolbarTool.SYMBOLS -> Icons.Outlined.Functions
    ToolbarTool.CALCULATOR -> Icons.Outlined.Calculate
    ToolbarTool.UNIT_CONVERT -> Icons.Outlined.SwapHoriz
    ToolbarTool.CURRENCY -> Icons.Outlined.CurrencyExchange
    ToolbarTool.QR_GEN -> Icons.Outlined.QrCode2
    ToolbarTool.PASSWORD_GEN -> Icons.Outlined.Password
    ToolbarTool.TYPING_TEST -> Icons.Outlined.Speed
    ToolbarTool.MEDIA_CONTROL -> Icons.Outlined.MusicNote
    ToolbarTool.AI -> Icons.Outlined.AutoAwesome
    ToolbarTool.MODES -> Icons.Outlined.Tune
    ToolbarTool.CURSOR_LEFT -> Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    ToolbarTool.CURSOR_RIGHT -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
    ToolbarTool.CURSOR_WORD_LEFT -> Icons.Outlined.KeyboardDoubleArrowLeft
    ToolbarTool.CURSOR_WORD_RIGHT -> Icons.Outlined.KeyboardDoubleArrowRight
    ToolbarTool.CURSOR_UP -> Icons.Outlined.KeyboardArrowUp
    ToolbarTool.CURSOR_DOWN -> Icons.Outlined.KeyboardArrowDown
    ToolbarTool.CURSOR_HOME -> Icons.Outlined.FirstPage
    ToolbarTool.CURSOR_END -> Icons.AutoMirrored.Outlined.LastPage
    ToolbarTool.HIDE_KEYBOARD -> Icons.Outlined.KeyboardHide
    ToolbarTool.PAGE_UP -> Icons.Outlined.KeyboardDoubleArrowUp
    ToolbarTool.PAGE_DOWN -> Icons.Outlined.KeyboardDoubleArrowDown
    ToolbarTool.SELECT_WORD -> Icons.Outlined.HighlightAlt
}

internal fun toolLabel(tool: ToolbarTool): String = when (tool) {
    ToolbarTool.EMOJI -> "Emoji"
    ToolbarTool.CLIPBOARD -> "Clipboard"
    ToolbarTool.SNIPPETS -> "Snippets"
    ToolbarTool.TEXT_EDIT -> "Text editing"
    ToolbarTool.ONE_HANDED -> "One-handed"
    ToolbarTool.SPLIT -> "Split"
    ToolbarTool.FLOATING -> "Floating"
    ToolbarTool.SETTINGS -> "Settings"
    ToolbarTool.FLASHLIGHT -> "Flashlight"
    ToolbarTool.COMPASS -> "Compass"
    ToolbarTool.LEVEL -> "Level"
    ToolbarTool.UNDO -> "Undo"
    ToolbarTool.REDO -> "Redo"
    ToolbarTool.MOON_PHASE -> "Moon"
    ToolbarTool.WEATHER -> "Weather"
    ToolbarTool.CALENDAR -> "Calendar"
    ToolbarTool.INCOGNITO -> "Incognito"
    ToolbarTool.THEMES -> "Themes"
    ToolbarTool.AUTOCORRECT -> "Autocorrect"
    ToolbarTool.SOUND_HAPTICS -> "Sound & haptics"
    ToolbarTool.NUMPAD -> "Numpad"
    ToolbarTool.HANDWRITING -> "Handwriting"
    ToolbarTool.CAMERA -> "Camera"
    ToolbarTool.DICTIONARY -> "Dictionary"
    ToolbarTool.TRANSLATE -> "Translate"
    ToolbarTool.GIF -> "GIFs"
    ToolbarTool.STICKER -> "Stickers"
    ToolbarTool.WEB_SEARCH -> "Search"
    ToolbarTool.IMAGE_SEARCH -> "Images"
    ToolbarTool.OCR -> "Scan text"
    ToolbarTool.QR_SCAN -> "QR scan"
    ToolbarTool.DOC_SCAN -> "Doc scan"
    ToolbarTool.VOICE -> "Voice"
    ToolbarTool.GRAMMAR -> "Grammar"
    ToolbarTool.WIKIPEDIA -> "Wikipedia"
    ToolbarTool.SYMBOLS -> "Symbols"
    ToolbarTool.CALCULATOR -> "Calculator"
    ToolbarTool.UNIT_CONVERT -> "Units"
    ToolbarTool.CURRENCY -> "Currency"
    ToolbarTool.QR_GEN -> "QR code"
    ToolbarTool.PASSWORD_GEN -> "Password"
    ToolbarTool.TYPING_TEST -> "Typing speed"
    ToolbarTool.MEDIA_CONTROL -> "Media"
    ToolbarTool.AI -> "AI"
    ToolbarTool.MODES -> "Modes"
    ToolbarTool.CURSOR_LEFT -> "Left"
    ToolbarTool.CURSOR_RIGHT -> "Right"
    ToolbarTool.CURSOR_WORD_LEFT -> "Word left"
    ToolbarTool.CURSOR_WORD_RIGHT -> "Word right"
    ToolbarTool.CURSOR_UP -> "Up"
    ToolbarTool.CURSOR_DOWN -> "Down"
    ToolbarTool.CURSOR_HOME -> "Line start"
    ToolbarTool.CURSOR_END -> "Line end"
    ToolbarTool.HIDE_KEYBOARD -> "Hide"
    ToolbarTool.PAGE_UP -> "Page up"
    ToolbarTool.PAGE_DOWN -> "Page down"
    ToolbarTool.SELECT_WORD -> "Select word"
}

private fun toolActive(tool: ToolbarTool, state: KeyboardUiState): Boolean = when (tool) {
    ToolbarTool.EMOJI -> state.panel == PanelMode.EMOJI
    ToolbarTool.CLIPBOARD -> state.panel == PanelMode.CLIPBOARD
    ToolbarTool.SNIPPETS -> state.panel == PanelMode.SNIPPETS
    ToolbarTool.TEXT_EDIT -> state.panel == PanelMode.TEXT_EDIT
    ToolbarTool.ONE_HANDED -> state.settings.oneHandedMode != OneHandedMode.OFF
    ToolbarTool.SPLIT -> state.settings.splitKeyboard
    ToolbarTool.FLOATING -> state.settings.floatingKeyboard
    ToolbarTool.SETTINGS -> false
    ToolbarTool.FLASHLIGHT -> state.torchOn
    ToolbarTool.COMPASS -> state.panel == PanelMode.COMPASS
    ToolbarTool.LEVEL -> state.panel == PanelMode.LEVEL
    ToolbarTool.UNDO -> false
    ToolbarTool.REDO -> false
    ToolbarTool.MOON_PHASE -> state.panel == PanelMode.MOON_PHASE
    ToolbarTool.WEATHER -> state.panel == PanelMode.WEATHER
    ToolbarTool.CALENDAR -> state.panel == PanelMode.CALENDAR
    ToolbarTool.INCOGNITO -> state.incognitoOn
    ToolbarTool.THEMES -> state.panel == PanelMode.THEMES
    ToolbarTool.AUTOCORRECT -> state.settings.autocorrect
    ToolbarTool.SOUND_HAPTICS -> state.panel == PanelMode.SOUND_HAPTICS
    ToolbarTool.NUMPAD -> state.panel == PanelMode.NUMPAD
    ToolbarTool.HANDWRITING -> state.panel == PanelMode.HANDWRITING
    ToolbarTool.CAMERA -> state.panel == PanelMode.CAMERA
    ToolbarTool.DICTIONARY -> state.panel == PanelMode.DICTIONARY
    ToolbarTool.TRANSLATE -> state.panel == PanelMode.TRANSLATE
    ToolbarTool.GIF -> state.panel == PanelMode.GIF
    ToolbarTool.STICKER -> state.panel == PanelMode.STICKER
    ToolbarTool.WEB_SEARCH -> state.panel == PanelMode.WEB_SEARCH
    ToolbarTool.IMAGE_SEARCH -> state.panel == PanelMode.IMAGE_SEARCH
    ToolbarTool.OCR -> state.panel == PanelMode.OCR
    ToolbarTool.QR_SCAN -> state.panel == PanelMode.QR_SCAN
    ToolbarTool.DOC_SCAN -> false
    ToolbarTool.VOICE -> state.panel == PanelMode.VOICE || state.voice.strip
    ToolbarTool.GRAMMAR -> state.panel == PanelMode.GRAMMAR
    ToolbarTool.WIKIPEDIA -> state.panel == PanelMode.WIKIPEDIA
    ToolbarTool.SYMBOLS -> state.panel == PanelMode.SYMBOLS
    ToolbarTool.CALCULATOR -> state.panel == PanelMode.CALCULATOR
    ToolbarTool.UNIT_CONVERT -> state.panel == PanelMode.UNIT_CONVERT
    ToolbarTool.CURRENCY -> state.panel == PanelMode.CURRENCY
    ToolbarTool.QR_GEN -> state.panel == PanelMode.QR_GEN
    ToolbarTool.PASSWORD_GEN -> state.panel == PanelMode.PASSWORD_GEN
    ToolbarTool.TYPING_TEST -> state.panel == PanelMode.TYPING_TEST
    ToolbarTool.MEDIA_CONTROL -> state.panel == PanelMode.MEDIA_CONTROL
    ToolbarTool.AI -> state.panel == PanelMode.AI
    ToolbarTool.MODES -> state.panel == PanelMode.MODES || state.activeModeId != null
    // Stateless one-shot moves, like undo/redo: nothing to stay lit for.
    ToolbarTool.CURSOR_LEFT, ToolbarTool.CURSOR_RIGHT,
    ToolbarTool.CURSOR_WORD_LEFT, ToolbarTool.CURSOR_WORD_RIGHT,
    ToolbarTool.CURSOR_UP, ToolbarTool.CURSOR_DOWN,
    ToolbarTool.CURSOR_HOME, ToolbarTool.CURSOR_END,
    ToolbarTool.PAGE_UP, ToolbarTool.PAGE_DOWN,
    ToolbarTool.SELECT_WORD,
    // A one-shot action too — it hides the keyboard, nothing to keep lit.
    ToolbarTool.HIDE_KEYBOARD -> false
}

/**
 * Live state of a toolbar-customization drag. Bounds and positions are all
 * in window-root coordinates; the ghost is drawn relative to the keyboard
 * body's origin. Drops on the toolbar insert at the slot under the finger,
 * drops on the toolbox grid reorder the toolbox (unpinning first when the
 * tool came off the bar), drops anywhere else send a toolbar tool back to
 * the toolbox at its remembered rank.
 *
 * [barSlot] and [boxSlot] are the live drop preview: whichever is non-null
 * is where the tool would land right now, and the owning row/grid renders
 * a ghost stand-in there so the surrounding icons make room ahead of the
 * drop.
 */
private class ToolDragController {
    var dragging by mutableStateOf<ToolbarTool?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    /** Toolbar insertion slot under the finger, or null when off the bar. */
    var barSlot by mutableStateOf<Int?>(null)
        private set
    /** Toolbox grid slot under the finger, or null when off the grid. */
    var boxSlot by mutableStateOf<Int?>(null)
        private set
    private var fromToolbar = false
    var toolbarBounds: Rect? = null
    /** Keyboard-body coordinates; the anchor for tool placement animations. */
    var bodyCoords: LayoutCoordinates? = null
    /**
     * Shared home of the emoji icon. It rides here rather than in [TopBar]
     * because the strip's copy and the toolbar's pinned copy are different
     * nodes: the handoff needs a holder that outlives both.
     */
    val emojiPlacement = SharedPlacement()
    var currentTools: List<ToolbarTool> = emptyList()
    var onCommit: (List<ToolbarTool>) -> Unit = {}
    /** Haptic tick when the drop target changes: slot to slot, or on/off the bar. */
    var onSnap: () -> Unit = {}
    /** Hold without dragging past the slop: open the tool's settings page. */
    var onOpenSettings: (ToolbarTool) -> Unit = {}

    // Toolbox geometry and data, registered by ToolboxPanel while it is
    // open (a drag can only happen with the toolbox open). The viewport is
    // the visible panel box; content coords are the scrolling grid column,
    // so slot math follows the scroll position for free.
    var toolboxViewport: Rect? = null
    var toolboxContentCoords: LayoutCoordinates? = null
    var toolboxCellSize: Size = Size.Zero
    var toolboxColumns: Int = 1
    /** The tools the toolbox grid is showing, in toolbox order. */
    var toolboxTools: List<ToolbarTool> = emptyList()
    /** Complete ordering over every tool; reorders rewrite this. */
    var toolboxOrder: List<ToolbarTool> = emptyList()
    var onOrderCommit: (List<ToolbarTool>) -> Unit = {}

    fun start(tool: ToolbarTool, fromBar: Boolean, at: Offset) {
        dragging = tool
        fromToolbar = fromBar
        position = at
        barSlot = slotAt(at)
        boxSlot = if (barSlot == null) toolboxSlotAt(at) else null
    }

    fun move(to: Offset) {
        position = to
        val bar = slotAt(to)
        val box = if (bar == null) toolboxSlotAt(to) else null
        if (bar != barSlot || box != boxSlot) {
            barSlot = bar
            boxSlot = box
            onSnap()
        }
    }

    fun cancel() {
        dragging = null
        barSlot = null
        boxSlot = null
    }

    fun end() {
        val tool = dragging ?: return
        val bar = slotAt(position)
        val box = if (bar == null) toolboxSlotAt(position) else null
        cancel()
        if (bar != null) {
            val without = currentTools - tool
            onCommit(without.toMutableList().apply { add(bar, tool) })
        } else if (box != null) {
            // Dropped on the grid: place it at that spot in the toolbox
            // order — and off the bar first, when that's where it came from.
            if (fromToolbar) onCommit(currentTools - tool)
            onOrderCommit(orderWith(tool, box))
        } else if (fromToolbar && toolboxViewport != null) {
            // Off-bar drops unpin only while the toolbox is open (its
            // viewport is registered) — a reorder drag that wanders off the
            // bar with no toolbox in sight just snaps back.
            onCommit(currentTools - tool)
        }
    }

    /**
     * Insertion slot under [at], or null when off the toolbar. The bar's hit
     * box is inflated so a drop just above/below it still counts.
     */
    private fun slotAt(at: Offset): Int? {
        val tool = dragging ?: return null
        val bar = toolbarBounds?.inflate(30f) ?: return null
        if (!bar.contains(at)) return null
        val without = currentTools - tool
        if (without.isEmpty()) return 0
        return (((at.x - bar.left) / bar.width) * (without.size + 1))
            .toInt()
            .coerceIn(0, without.size)
    }

    /**
     * Toolbox grid slot under [at], or null when off the visible panel. The
     * grid scrolls, so the position converts through the content column's
     * live coordinates rather than a captured rect.
     */
    private fun toolboxSlotAt(at: Offset): Int? {
        val tool = dragging ?: return null
        val viewport = toolboxViewport ?: return null
        if (!viewport.contains(at)) return null
        val coords = toolboxContentCoords?.takeIf { it.isAttached } ?: return null
        val cell = toolboxCellSize
        if (cell.width <= 0f || cell.height <= 0f) return null
        val origin = coords.positionInRoot()
        val columns = toolboxColumns.coerceAtLeast(1)
        val count = (toolboxTools - tool).size
        val col = ((at.x - origin.x) / cell.width).toInt().coerceIn(0, columns - 1)
        val row = ((at.y - origin.y) / cell.height).toInt().coerceAtLeast(0)
        return (row * columns + col).coerceIn(0, count)
    }

    /** The full tool order with [tool] moved to display slot [slot] of the grid. */
    private fun orderWith(tool: ToolbarTool, slot: Int): List<ToolbarTool> {
        // The grid previews the drop with the dragged tool removed, so the
        // slot indexes that list; the displayed tool it lands in front of
        // anchors the position in the complete order.
        val displayed = toolboxTools - tool
        val successor = displayed.getOrNull(slot.coerceIn(0, displayed.size))
        val order = toolboxOrder.toMutableList().apply { remove(tool) }
        val at = successor?.let { order.indexOf(it) }?.takeIf { it >= 0 } ?: order.size
        order.add(at, tool)
        return order
    }
}

/**
 * Distance a held tool has to travel before the gesture counts as a move
 * rather than a stationary hold. Generous on purpose: a hold meant to open
 * the tool's settings drifts a few pixels under any real thumb, and landing
 * in "moved the tool" because of that is the more annoying misfire.
 */
private val ToolDragSlop = 24.dp

/**
 * Wires long-press-drag onto a tool. Three outcomes from one gesture: a tap
 * runs [onTap]; a hold that never travels past [ToolDragSlop] opens the
 * tool's settings page; a hold that does travel picks the tool up and drops
 * it wherever it lands (reorder, pin, or unpin).
 *
 * The tap is dispatched from here rather than from a `clickable` on the tool
 * itself: a `clickable` sits deeper in the modifier chain, so it saw the
 * release first and fired its own click on top of every hold.
 */
@Composable
private fun DraggableTool(
    tool: ToolbarTool,
    fromToolbar: Boolean,
    enabled: Boolean,
    drag: ToolDragController,
    onTap: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val feedback = LocalKeyPressFeedback.current
    val scope = rememberCoroutineScope()
    // Read through a holder rather than keying pointerInput on the lambda:
    // a fresh lambda every recomposition would restart the handler, and the
    // drop preview recomposes this row on every frame of a drag.
    val tapAction by rememberUpdatedState(onTap)
    content(
        Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(enabled, tool) {
                if (!enabled) return@pointerInput
                // Raw press-and-hold, mirroring the key rows' handler, instead
                // of detectDragGesturesAfterLongPress: its long-press never
                // fired inside the IME window on device, so tools could not
                // be dragged at all. An external timer plus a plain event
                // loop is the pattern already proven by the repeat keys.
                val dragSlop = ToolDragSlop.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Travel is measured in root coordinates, not from raw
                    // node-relative deltas: the row reflows around the drop
                    // preview while the finger is down, and a node that moves
                    // under a still finger reports deltas of its own.
                    val downRoot = origin + down.position
                    var rootPos = downRoot
                    var longPressed = false
                    var dragged = false
                    var released = false
                    var scrolled = false
                    val timer = scope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        // The pick-up is invisible until the first move; the
                        // buzz tells the user the long-press registered.
                        feedback()
                        longPressed = true
                        drag.start(tool, fromToolbar, rootPos)
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                // Swallow the release once the hold is ours so
                                // nothing downstream treats it as a tap too.
                                if (longPressed) change.consume()
                                break
                            }
                            rootPos = origin + change.position
                            val travel = (rootPos - downRoot).getDistance()
                            if (!longPressed) {
                                // Real drift before the hold registers is a
                                // scroll (toolbox grid) — hand the gesture back.
                                if (travel > viewConfiguration.touchSlop) {
                                    scrolled = true
                                    break
                                }
                            } else {
                                change.consume()
                                if (travel > dragSlop) dragged = true
                                drag.move(rootPos)
                            }
                        }
                    } finally {
                        timer.cancel()
                        when {
                            !longPressed -> {
                                drag.cancel()
                                if (released && !scrolled) tapAction()
                            }
                            dragged -> drag.end()
                            // A hold that never travelled past the slop is a
                            // distinct gesture: open the tool's settings page.
                            else -> {
                                drag.cancel()
                                drag.onOpenSettings(tool)
                            }
                        }
                    }
                }
            }
    )
}

/**
 * Slides this element to its new position when layout around it changes —
 * pinned tools shuffle smoothly instead of jumping when the toolbox chevron
 * shows up or a tool is (un)pinned. Fast, no-bounce spring: quick but not
 * sudden.
 *
 * The position is measured against [anchor] (the keyboard body) rather than
 * the immediate parent: the toolbar nests rows inside rows and its weighted
 * cells resize, so an icon's parent-relative position barely moves while
 * its on-screen position shifts a lot. Anchoring at the body captures the
 * whole motion in one spring, so nothing snaps at animation start. Falls
 * back to parent-relative when no anchor is available.
 *
 * [enabled] is the reduce-motion switch. It has to snap rather than skip the
 * modifier: opening a panel adds the back chevron, which shifts every pinned
 * icon, so a disabled slide still needs to track the new position or the
 * icons would sit at their old offsets.
 */
private fun Modifier.animatePlacement(
    enabled: Boolean = true,
    anchor: () -> LayoutCoordinates? = { null },
): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val animatable = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
        // The displacement drawn right now, set synchronously from onPlaced.
        // Everything below hangs on this: onPlaced runs in the layout phase,
        // by which point a layout-phase offset for this frame has already
        // been decided, and the coroutine that starts the spring does not run
        // until the next one. So the icon was drawn once at its destination,
        // then jumped back to where it came from and slid in — a one-frame
        // flash on every icon, every time the bar changed. Draw runs after
        // layout in the same frame, so a draw-phase displacement written here
        // lands before the icon is ever painted undisplaced.
        var immediate by remember { mutableStateOf<IntOffset?>(null) }
        var lastTarget by remember { mutableStateOf<IntOffset?>(null) }
        this
            .onPlaced { coords ->
                val anchorCoords = anchor()?.takeIf { it.isAttached }
                val target = (
                    anchorCoords?.localPositionOf(coords, Offset.Zero)
                        ?: coords.positionInParent()
                    ).round()
                val previous = lastTarget
                lastTarget = target
                // First placement settles in place: a fresh icon has nowhere
                // to have travelled from.
                if (previous == null || previous == target) return@onPlaced
                val delta = previous - target
                val jump = delta.toOffset().getDistance()
                // Deadband, and the big-jump escape. The toolbar's cells are
                // weighted, so their widths land on fractions that round
                // differently between passes; nothing travels a single pixel
                // on purpose here, so a move that small is rounding, not
                // motion. Anything over an icon-or-two is a layout jump
                // (scroll, panel resize) and animating it reads as lag.
                if (!enabled || jump < 2f || jump > 160f) {
                    immediate = null
                    scope.launch { animatable.snapTo(IntOffset.Zero) }
                    return@onPlaced
                }
                // Carry any displacement still in flight, so a change that
                // lands mid-slide continues from where the icon actually is
                // rather than restarting from the new delta alone.
                val start = delta + (immediate ?: animatable.value)
                immediate = start
                scope.launch {
                    animatable.snapTo(start)
                    immediate = null
                    animatable.animateTo(IntOffset.Zero, ToolbarSlideSpring)
                }
            }
            .graphicsLayer {
                val shift = immediate ?: animatable.value
                translationX = shift.x.toFloat()
                translationY = shift.y.toFloat()
            }
    }

/**
 * Where an icon that lives in more than one branch of the tree last sat.
 *
 * [animatePlacement] can only animate a node that survives the layout
 * change, and the emoji icon does not: the strip draws its own copy and
 * the toolbar draws another as a pinned tool, so flipping between them
 * disposes one node and composes a different one. Parking the last
 * body-relative position outside both lets the arriving node start from
 * where the leaving one stood, which is the whole illusion.
 */
private class SharedPlacement {
    var last: IntOffset? = null

    /**
     * When the node holding [last] was disposed, or 0 while one still holds it.
     *
     * This is what separates a handoff from a reappearance. A handoff is one
     * node being disposed and another composed inside a single frame, so the
     * arriving node finds a stamp a frame or two old. A reappearance — the
     * icon's whole host went away, because a full-bleed panel takes the top
     * bar with it, and came back seconds later — finds a stale one, and
     * sliding in from a position that old is a phantom: the icon flies in
     * from wherever it happened to sit before the panel opened.
     *
     * It has to be dispose time, not the time [last] was written. Writing it
     * on placement looks equivalent and is not: a node is only re-placed when
     * layout invalidates, so the strip's emoji icon stamps once when it
     * appears and then sits untouched for as long as the user types. Every
     * real handoff then read as ancient and refused to animate, which is
     * exactly the icon snapping into place instead of sliding.
     */
    var vacatedAtNanos: Long = 0L
}

/** Two frames at 60Hz, the window a real strip/toolbar handoff lands in. */
private const val SharedPlacementMaxAgeNanos = 33_000_000L

/**
 * Slides this element in from wherever [shared] was last seen, then keeps
 * [shared] pointing at its own position. The counterpart to
 * [animatePlacement] for an icon that changes parents rather than moving
 * within one.
 */
private fun Modifier.animateSharedPlacement(
    shared: SharedPlacement,
    enabled: Boolean = true,
    anchor: () -> LayoutCoordinates? = { null },
): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
        var immediate by remember { mutableStateOf<IntOffset?>(null) }
        // Stamp the moment this node leaves, so whichever node replaces it can
        // tell a handoff from a reappearance. onDispose runs while changes are
        // applied, ahead of the layout pass that places the arriving node, so
        // the stamp is always there in time.
        DisposableEffect(shared) {
            onDispose { shared.vacatedAtNanos = System.nanoTime() }
        }
        // This node's own last position, so a re-placement within one parent
        // (the bar reflowing under it) animates like [animatePlacement] does,
        // not just the cross-parent handoff. Without this the emoji snapped
        // whenever the toolbar reshuffled — every other icon slid but it.
        var lastTarget by remember { mutableStateOf<IntOffset?>(null) }
        this
            .onPlaced { coords ->
                val anchorCoords = anchor()?.takeIf { it.isAttached }
                val position = (
                    anchorCoords?.localPositionOf(coords, Offset.Zero)
                        ?: coords.positionInParent()
                    ).round()
                if (lastTarget == position) return@onPlaced
                val ownPrevious = lastTarget
                val sharedPrevious = shared.last
                val vacatedAt = shared.vacatedAtNanos
                val first = ownPrevious == null
                lastTarget = position
                shared.last = position
                // Claim the slot: while this node lives there is no vacancy.
                shared.vacatedAtNanos = 0L
                if (!enabled) return@onPlaced
                // A cross-parent handoff: this node just appeared and the
                // sibling it replaces vacated within the last frame or two
                // (see [SharedPlacement.vacatedAtNanos]). It slides in from
                // wherever that sibling stood, however far along the row.
                val handoff = first && sharedPrevious != null && vacatedAt != 0L &&
                    System.nanoTime() - vacatedAt <= SharedPlacementMaxAgeNanos
                // Where this placement travelled *from*: the vacated slot for a
                // handoff, else this node's own previous spot for an ordinary
                // reflow. A fresh node with neither has nowhere to come from.
                val previous = when {
                    handoff -> sharedPrevious!!
                    !first -> ownPrevious!!
                    else -> return@onPlaced
                }
                if (previous == position) return@onPlaced
                val delta = previous - position
                // A slide along the bar never changes height; a vertical move
                // means the rows themselves shifted (a panel opened, the emoji
                // row appeared), which animating reads as lag — snap it.
                if (delta.x == 0 || kotlin.math.abs(delta.y) > 4) {
                    immediate = null
                    scope.launch { offset.snapTo(IntOffset.Zero) }
                    return@onPlaced
                }
                // A reflow nudge is small; a jump of more than an icon or two is
                // a layout change (scroll, panel resize), not motion. The
                // handoff is exempt — it is deliberately a long slide.
                val jump = delta.toOffset().getDistance()
                if (!handoff && (jump < 2f || jump > 160f)) {
                    immediate = null
                    scope.launch { offset.snapTo(IntOffset.Zero) }
                    return@onPlaced
                }
                // Carry any displacement still in flight so a change that lands
                // mid-slide continues from where the icon actually is. This is
                // what keeps the drawn position continuous when a handoff is
                // followed a frame later by the layout settling to its final
                // slot: the seed offset re-anchors to the new target instead of
                // snapping the icon out to the row's edge.
                val start = delta + (immediate ?: offset.value)
                // Drawn this frame, not next; see the note in animatePlacement.
                immediate = start
                scope.launch {
                    offset.snapTo(start)
                    immediate = null
                    offset.animateTo(IntOffset.Zero, ToolbarSlideSpring)
                }
            }
            .graphicsLayer {
                val shift = immediate ?: offset.value
                translationX = shift.x.toFloat()
                translationY = shift.y.toFloat()
            }
    }

/**
 * One round tool button; the circle radius comes from the theme (0 = bare
 * icon). With [longPressLabel] set, holding the button pops the tool's name
 * above it — the toolbar shows bare icons, so this is how a user finds out
 * what one does without tapping it.
 */
@Composable
private fun ToolCircle(
    icon: ImageVector,
    description: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    longPressLabel: String? = null,
    onLongPress: (() -> Unit)? = null,
    // False when an ancestor owns the whole gesture (see DraggableTool):
    // a clickable here would sit deeper in the chain and steal the release.
    interactive: Boolean = true,
    // Inactive-icon tint override (the tool's accent colour). Null keeps the
    // theme's toolbar-icon colour; the active state always wins over this.
    tint: Color? = null,
    // When set, the tool's name is drawn under the icon (toolbar labels). The
    // long-press tooltip is then redundant and suppressed.
    label: String? = null,
    labelSizeSp: Int = 9,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val shape = RoundedCornerShape(kb.toolRadiusDp.dp)
    val background = when {
        active -> kb.toolCircleActive
        kb.toolRadiusDp > 0 -> kb.toolCircle
        else -> Color.Transparent
    }
    var showLabel by remember { mutableStateOf(false) }
    val feedback = LocalKeyPressFeedback.current
    val click = if (!interactive) {
        Modifier
    } else if (longPressLabel == null && onLongPress == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.pointerInput(longPressLabel, onLongPress != null) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = {
                    feedback()
                    if (onLongPress != null) onLongPress() else showLabel = true
                },
            )
        }
    }
    val iconTint = if (active) kb.toolCircleActiveIcon else (tint ?: kb.toolbarIcon)
    if (label != null) {
        // Labelled variant (toolbar labels): icon in its circle, name beneath.
        Column(
            modifier = modifier.then(click),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(shape)
                    .background(background, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = description,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint,
                )
            }
            Text(
                label,
                fontSize = labelSizeSp.sp,
                lineHeight = (labelSizeSp + 1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = iconTint,
                modifier = Modifier.padding(top = 1.dp, start = 1.dp, end = 1.dp),
            )
        }
        return
    }
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(shape)
            .background(background, shape)
            .then(click),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = iconTint,
        )
        if (showLabel && longPressLabel != null) {
            LaunchedEffect(Unit) {
                delay(1200)
                showLabel = false
            }
            Popup(
                popupPositionProvider = rememberAboveAnchorPopup(),
                onDismissRequest = { showLabel = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        longPressLabel,
                        color = kb.popupText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The drop-preview stand-in rendered at the slot a dragged tool would land
 * in: same footprint as [ToolCircle], drawn washed out so it reads as
 * "will go here" rather than "is here". Keyed placement animation makes it
 * slide from slot to slot as the finger moves.
 */
@Composable
private fun GhostToolCircle(tool: ToolbarTool, modifier: Modifier = Modifier) {
    val kb = LocalKbTheme.current
    val shape = RoundedCornerShape(kb.toolRadiusDp.dp)
    Box(
        modifier = modifier
            .size(38.dp)
            .background(kb.toolCircleActive.copy(alpha = 0.22f), shape)
            .border(1.dp, kb.toolbarIcon.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            toolIcon(tool),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = kb.toolbarIcon.copy(alpha = 0.45f),
        )
    }
}

/**
 * The toolbar itself: fixed toolbox launcher, then the user's tools —
 * spread across the free space when the greedy setting is on, packed to
 * the left otherwise.
 */
@Composable
private fun RowScope.ToolbarRow(
    state: KeyboardUiState,
    onPanelChange: (PanelMode) -> Unit,
    onToolTap: (ToolbarTool) -> Unit,
    drag: ToolDragController,
    // Opacity for the tools while the toolbar fades in. On an in-place
    // strip→toolbar flip the emoji is exempt — it hands its position across and
    // slides instead of fading — so this rides the toolbox launcher and pinned
    // tools only. On a fresh mount nothing slides, so [fadeEmoji] folds the
    // emoji into the same fade and the whole bar comes up together.
    contentAlpha: () -> Float = { 1f },
    fadeEmoji: Boolean = false,
) {
    val customizing = state.panel == PanelMode.TOOLBOX
    // Scrolling wants the tools at their natural width, so it overrides the
    // greedy even-spread (which would keep them all on screen and shrinking).
    val scrollable = state.settings.toolbarBehavior.scrollable
    val greedy = state.settings.toolbarBehavior.greedy && !scrollable
    val labels = state.settings.toolbarLabels
    val labelSize = state.settings.toolbarLabelSize
    val motion = !state.settings.reduceMotion
    // Enter and exit share one duration so the back chevron takes the same
    // time to leave as it took to arrive; a shorter exit made closing a panel
    // finish ahead of the icons still sliding back into the freed slot.
    val enterMs = if (motion) ToolbarMotionMs else 0
    val exitMs = enterMs
    // RTL scripts read the bar right-to-left, so the pinned tools mirror. The
    // drag controller mirrors its copy in lockstep (see KeyboardBody), so slot
    // hit-testing stays aligned with what's drawn.
    val tools = state.settings.toolbarTools
        .filter { it in state.settings.enabledTools && isSupportedTool(it) }
        .let { if (toolbarReadsRtl(state)) it.reversed() else it }
    // While a drag is live the bar previews the drop: the dragged tool's
    // cell leaves the row and a null entry (the ghost) occupies the slot
    // under the finger, so the pinned icons slide out of the way before
    // anything is committed.
    // The dragged tool's own cell has to STAY composed: it hosts the pointer
    // handler driving the drag, and dropping it from the row disposed that
    // handler the instant the hold registered — which cancelled the gesture
    // and sent every long-press down the "open settings" path instead. It
    // renders invisible in place; the ghost marks where the drop would land.
    val dragTool = drag.dragging
    val ghostSlot = drag.barSlot
    val displayTools: List<ToolbarTool?> = ArrayList<ToolbarTool?>(tools).apply {
        if (dragTool == null || ghostSlot == null) return@apply
        val source = tools.indexOf(dragTool)
        val at = ghostSlot.coerceIn(0, (tools - dragTool).size)
        // Landing back where it started needs no ghost — the held cell's own
        // gap already marks the spot. Drawing one there would shove the held
        // cell sideways under a still finger, which reads as a drag.
        if (at == source) return@apply
        // The slot indexes the row without the dragged tool, so a slot past
        // the (still present) source cell shifts one to the right.
        add(if (source >= 0 && at > source) at + 1 else at, null)
    }
    val panelOpen = state.panel != PanelMode.NONE

    // In greedy mode every button — chevron, toolbox and tools alike — is an
    // equal-weight cell, so the whole bar is one evenly spaced grid instead
    // of fixed buttons on the left with the tools spread over the leftover.
    val leading: @Composable (Modifier) -> Unit = { cell ->
        // With any tool panel open, one tap on the chevron returns to the keys.
        // The transition state keeps the cell composed until the exit
        // animation finishes, then drops it entirely so a weighted cell
        // doesn't linger as an invisible gap.
        val chevronVisible = remember { MutableTransitionState(false) }
        chevronVisible.targetState = panelOpen
        if (chevronVisible.currentState || chevronVisible.targetState) {
            AnimatedVisibility(
                visibleState = chevronVisible,
                modifier = if (greedy) cell else Modifier,
                // Reduce motion collapses the durations to zero rather than
                // dropping AnimatedVisibility: the transition state still has
                // to run its lifecycle or the weighted cell never releases.
                // Both branches animate paint only, never measured size. The
                // greedy one already did (its slot is weighted, so the chevron
                // scales inside a cell that appears at full width); the other
                // used to expand/shrink its width, which re-measured the row
                // on every frame and made the pinned icons chase a target that
                // moved under them for the whole transition. See the matching
                // note on the suggestions chevron in TopBar.
                enter = if (greedy) {
                    // The weighted slot can't grow, so the chevron itself
                    // scales and fades into it.
                    scaleIn(tween(enterMs)) + fadeIn(tween(enterMs))
                } else {
                    fadeIn(tween(enterMs))
                },
                // Instant, for the same reason as the suggestions chevron: an
                // exit transition holds this cell's width (its whole weighted
                // slot, in greedy mode) until it finishes, so closing a tool
                // played the chevron out, paused, and only then let the icons
                // move — which is why closing felt broken while opening, where
                // the slot is claimed on the first frame, felt fine. Dropping
                // it at once makes the icons' spring the closing animation.
                exit = ExitTransition.None,
            ) {
                Box(
                    if (greedy) Modifier.fillMaxSize() else cell,
                    contentAlignment = Alignment.Center,
                ) {
                    ToolCircle(
                        icon = Icons.Outlined.ChevronLeft,
                        description = "Back to keyboard",
                        active = false,
                        longPressLabel = "Back to keyboard",
                    ) { onPanelChange(state.panel) }
                }
            }
        }
        Box(cell, contentAlignment = Alignment.Center) {
            ToolCircle(
                icon = Icons.Outlined.GridView,
                description = "Toolbox",
                active = customizing,
                modifier = Modifier
                    .graphicsLayer { alpha = contentAlpha() }
                    .animatePlacement(enabled = motion) { drag.bodyCoords },
                longPressLabel = "Toolbox",
            ) { onPanelChange(PanelMode.TOOLBOX) }
        }
    }
    val toolCells: @Composable RowScope.() -> Unit = {
        for (tool in displayTools) {
            key(tool ?: "bar-ghost") {
                val cell = if (greedy) {
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                } else {
                    Modifier.padding(horizontal = 3.dp)
                }
                // On an in-place flip the emoji slides and stays opaque while
                // the rest fade (see [contentAlpha]); on a fresh mount ([fadeEmoji])
                // nothing slides, so it fades in with them.
                val fadedCell = if (tool == ToolbarTool.EMOJI && !fadeEmoji) {
                    cell
                } else {
                    cell.graphicsLayer { alpha = contentAlpha() }
                }
                Box(fadedCell, contentAlignment = Alignment.Center) {
                    if (tool == null) {
                        // The drop preview; dragTool is never null when a
                        // ghost entry exists.
                        GhostToolCircle(
                            dragTool ?: return@Box,
                            modifier = Modifier.animatePlacement(enabled = motion) { drag.bodyCoords },
                        )
                    } else {
                        // Drag is always live: hold-and-drag reorders the bar
                        // (or unpins into an open toolbox); a hold that never
                        // moves opens the tool's settings page instead.
                        DraggableTool(
                            tool,
                            fromToolbar = true,
                            enabled = true,
                            drag = drag,
                            onTap = { onToolTap(tool) },
                        ) { dragModifier ->
                            ToolCircle(
                                icon = toolIcon(tool),
                                description = toolLabel(tool),
                                active = toolActive(tool, state),
                                label = if (labels) toolLabel(tool) else null,
                                labelSizeSp = labelSize,
                                // The icon itself animates, anchored at the
                                // keyboard body: cells are weighted so their
                                // widths snap, and only body-relative tracking
                                // sees the true on-screen motion. While this is
                                // the tool being dragged the cell holds its
                                // place but shows nothing — the floating icon
                                // under the finger is the one to look at.
                                // The emoji tool also exists on the
                                // suggestion strip, so it hands its position
                                // across that swap instead of tracking only
                                // its own node.
                                modifier = dragModifier
                                    .then(
                                        if (tool == ToolbarTool.EMOJI) {
                                            Modifier.animateSharedPlacement(
                                                drag.emojiPlacement,
                                                enabled = motion,
                                            ) { drag.bodyCoords }
                                        } else {
                                            Modifier.animatePlacement(enabled = motion) { drag.bodyCoords }
                                        },
                                    )
                                    .alpha(if (tool == dragTool) 0f else 1f),
                                interactive = false,
                            ) {}
                        }
                    }
                }
            }
        }
    }
    if (greedy) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            // The tools sub-row carries a weight equal to its cell count, so
            // its cells end up exactly as wide as the leading buttons' cells.
            // It still exists (zero tools aside) as the drag-drop target.
            Row(
                modifier = Modifier
                    .weight(displayTools.size.coerceAtLeast(1).toFloat())
                    .fillMaxHeight()
                    .onGloballyPositioned { drag.toolbarBounds = it.boundsInRoot() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolCells()
            }
        }
    } else {
        leading(Modifier.padding(horizontal = 3.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Weighted so it claims exactly the free width; the scroll then
                // lets the pinned tools overflow that width instead of packing
                // to fit. Reordering by drag still works — the toolbox is the
                // simpler place to rearrange a long, scrolling bar.
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .onGloballyPositioned { drag.toolbarBounds = it.boundsInRoot() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            toolCells()
            // A weighted spacer can't live inside a horizontal scroll (infinite
            // width); packed-to-fit mode still needs it to left-align the tools.
            if (!scrollable) Spacer(modifier = Modifier.weight(1f))
        }
    }
    if (state.incognitoOn) {
        Icon(
            KeyboardIcons.Incognito,
            contentDescription = "Incognito is on",
            modifier = Modifier
                .padding(end = 6.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Gboard-style toolbox: every tool that is not on the toolbar, shown in a
 * labeled grid ordered by [KeyboardSettings.toolboxOrder] (most-used-first
 * until the user rearranges it). Tap to use a tool in place; hold and drag
 * it up onto the toolbar to pin it, or around the grid to reorder. Toolbar
 * tools drag down here to unpin — at the spot they're dropped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolboxPanel(
    state: KeyboardUiState,
    onToolTap: (ToolbarTool) -> Unit,
    onHintDismiss: () -> Unit,
    drag: ToolDragController,
) {
    val height = keyRowsHeight(state)
    // First open: always show the drag hint. After it was dismissed once,
    // resurface it only rarely as a reminder. Rolled once per panel open.
    val rareReminder = remember { Random.nextFloat() < 0.03f }
    var hintVisible by remember(state.settings.toolboxHintDismissed) {
        mutableStateOf(!state.settings.toolboxHintDismissed || rareReminder)
    }
    // The registered geometry outlives the panel unless cleared, and a
    // stale viewport would let a later drag "drop on the toolbox" with the
    // panel long gone.
    DisposableEffect(drag) {
        onDispose {
            drag.toolboxViewport = null
            drag.toolboxContentCoords = null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { drag.toolboxViewport = it.boundsInRoot() },
    ) {
        // A slim header carrying the drag hint until it's dismissed. Resetting
        // the pinned tools now lives in Settings → Appearance ("Reset pinned
        // tools"), so the toolbox no longer shows its own reset control.
        if (hintVisible) {
            val activeMode = state.settings.keyboardModes
                .firstOrNull { it.id == state.activeModeId }
                ?.takeIf { state.settings.modeToolOrderEdits && it.ownsToolOrder }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // With a mode on, the arrangement being edited is that mode's
                // own — say so, or the same keyboard looking different in the
                // next app reads as the drag being lost.
                Text(
                    if (activeMode != null) {
                        "${activeMode.name} mode is on, so this arrangement is saved for it — " +
                            "other apps keep their own. Hold and drag a tool onto the toolbar to " +
                            "pin it, around this grid to reorder, or down here to remove it."
                    } else {
                        "Hold and drag a tool onto the toolbar to pin it, around this grid to reorder — or drag a toolbar tool down here to remove it."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        hintVisible = false
                        onHintDismiss()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss hint",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Toolbox order is a complete ranking over every tool; the grid
        // shows the available subset in that order.
        val available = state.settings.toolboxOrder.filter {
            it !in state.settings.toolbarTools && it in state.settings.enabledTools && isSupportedTool(it)
        }
        val columns = state.settings.toolboxColumns.coerceAtLeast(1)
        drag.toolboxTools = available
        drag.toolboxOrder = state.settings.toolboxOrder
        drag.toolboxColumns = columns
        // Drop preview: the dragged tool LEAVES the list and a ghost marks the
        // slot it would land in. That is one hole, not two, and — the reason
        // it has to be done this way — it is a removal plus an insertion.
        //
        // A permutation does not work here. Keeping the tool in the list and
        // moving it to the target slot reads identically in the composition,
        // but Compose moves the composition groups without re-placing the
        // laid-out children, so the grid never reflowed: verified on device,
        // where a whole drag produced exactly one layout pass and every cell
        // kept its original x. The old code only appeared to shuffle because
        // inserting a ghost was a structural change.
        //
        // Removing the tool means its cell is disposed mid-gesture, which is
        // why the drag is no longer hosted there — see the pointerInput on the
        // grid below. The cells are pure visuals now.
        val dragTool = drag.dragging
        val boxSlot = drag.boxSlot
        val display: List<ToolbarTool?> = if (dragTool != null && boxSlot != null) {
            // `available - dragTool` is a no-op when the drag came from the
            // toolbar, so both origins land on one ghost and one hole.
            val without = available - dragTool
            ArrayList<ToolbarTool?>(without).apply {
                add(boxSlot.coerceIn(0, without.size), null)
            }
        } else {
            available
        }
        if (display.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Every tool is on the toolbar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        // More tools than fit the panel height now — the grid scrolls.
        // The placement anchor is this scrolling content column, NOT the
        // keyboard body: relative to the body every icon "moves" on every
        // scroll frame, which made each one restart its placement spring
        // per frame (a coroutine-and-relayout storm that tanked scroll fps).
        // Relative to the content, scrolling is a no-op for the animation.
        var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var gridOrigin by remember { mutableStateOf(Offset.Zero) }
        val feedback = LocalKeyPressFeedback.current
        val scope = rememberCoroutineScope()
        val tapTool by rememberUpdatedState(onToolTap)
        // Read through holders, and key the handler on nothing. `available` is
        // a fresh list on every recomposition and the drop preview recomposes
        // this panel on every frame of a drag, so keying pointerInput on it
        // would tear down and restart the very gesture it is running — the
        // same trap the per-cell handler documented.
        val toolsNow by rememberUpdatedState(available)
        val columnsNow by rememberUpdatedState(columns)
        // One FlowRow, so a part-full last line still lines up with the ones
        // above it, and one gesture handler for the whole grid rather than one
        // per cell. Hoisting it is what lets a cell be disposed mid-drag: the
        // handler outlives any cell, so the dragged tool can leave the list
        // and the rest can close up behind it.
        FlowRow(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .onGloballyPositioned {
                    gridCoords = it
                    gridOrigin = it.positionInRoot()
                    drag.toolboxContentCoords = it
                }
                .pointerInput(Unit) {
                    val dragSlop = ToolDragSlop.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Which tool was grabbed is resolved once, from where
                        // the finger landed. The grid is uniform, so this is
                        // the same arithmetic the drop target uses. Nothing has
                        // moved yet at this point: a drag is not live, so the
                        // list on screen is still the plain one.
                        val cell = drag.toolboxCellSize
                        if (cell.width <= 0f || cell.height <= 0f) return@awaitEachGesture
                        val cols = columnsNow
                        val col = (down.position.x / cell.width).toInt()
                        val row = (down.position.y / cell.height).toInt()
                        val tool = toolsNow
                            .getOrNull(row * cols + col)
                            ?.takeIf { col in 0 until cols && down.position.x >= 0f }
                            ?: return@awaitEachGesture
                        // Root coordinates throughout: the grid reflows around
                        // the drop preview while the finger is down, and a
                        // node that moves under a still finger reports deltas
                        // of its own.
                        val downRoot = gridOrigin + down.position
                        var rootPos = downRoot
                        var longPressed = false
                        var dragged = false
                        var released = false
                        var scrolled = false
                        val timer = scope.launch {
                            delay(viewConfiguration.longPressTimeoutMillis)
                            feedback()
                            longPressed = true
                            drag.start(tool, false, rootPos)
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    if (longPressed) change.consume()
                                    break
                                }
                                rootPos = gridOrigin + change.position
                                val travel = (rootPos - downRoot).getDistance()
                                if (!longPressed) {
                                    // Drift before the hold registers is a
                                    // scroll — hand the gesture back.
                                    if (travel > viewConfiguration.touchSlop) {
                                        scrolled = true
                                        break
                                    }
                                } else {
                                    change.consume()
                                    if (travel > dragSlop) dragged = true
                                    drag.move(rootPos)
                                }
                            }
                        } finally {
                            timer.cancel()
                            when {
                                !longPressed -> {
                                    drag.cancel()
                                    if (released && !scrolled) tapTool(tool)
                                }
                                dragged -> drag.end()
                                // A hold that never travelled past the slop is
                                // a distinct gesture: open the tool's settings.
                                else -> {
                                    drag.cancel()
                                    drag.onOpenSettings(tool)
                                }
                            }
                        }
                    }
                },
            maxItemsInEachRow = columns,
        ) {
            for (tool in display) {
                // The ghost's key encodes its slot so a move is a structural
                // remove+add, not a same-key reorder. FlowRow re-measures and
                // re-places its children on a structural change, but on a pure
                // reorder it moves the composition groups while leaving every
                // laid-out child at its old position — verified on device, where
                // the grid reflowed once (the dragged tool leaving) and then
                // froze for the rest of the drag: the ghost never moved and the
                // icons never made room. Re-keying per slot forces the reflow on
                // every step. (The toolbar is a Row, which re-places on reorder,
                // so its ghost keeps one stable key.)
                key(tool ?: "box-ghost-$boxSlot") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(1f / columns)
                            // Every cell is the same size; whichever
                            // reported last feeds the slot math.
                            .onGloballyPositioned { drag.toolboxCellSize = it.size.toSize() },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Pure visuals — the grid's own pointerInput owns the
                        // gesture, so nothing here has to survive the dragged
                        // tool leaving the list.
                        //
                        // Anchored at the scrolling content (see gridCoords),
                        // NOT the keyboard body: body-relative, every scroll
                        // frame moved every icon and restarted its spring — a
                        // per-frame coroutine storm that tanked scroll fps.
                        // Content-relative, scrolling is a no-op; reorders
                        // still slide.
                        val ghost = tool == null
                        // dragTool is never null when a ghost entry exists.
                        val shown = tool ?: dragTool ?: return@Box
                        Column(
                            modifier = Modifier
                                .animatePlacement(
                                    enabled = !state.settings.reduceMotion,
                                ) { gridCoords }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (ghost) {
                                GhostToolCircle(shown)
                            } else {
                                ToolCircle(
                                    icon = toolIcon(shown),
                                    description = toolLabel(shown),
                                    active = toolActive(shown, state),
                                    interactive = false,
                                    tint = if (state.settings.coloredToolIcons)
                                        toolAccentColor(shown, state.settings.toolColorOverrides)
                                    else null,
                                ) {}
                            }
                            Text(
                                toolLabel(shown),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (ghost) 0.5f else 1f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 4.dp, start = 2.dp, end = 2.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            }
        }
    }

/**
 * Panels that take over the whole keyboard: the toolbar (plus any emoji or
 * symbol row) hides while they're open and the panel absorbs that height.
 * The right fit for tools that want the room and never involve typing or
 * hopping to another tool mid-use — sensors, reference views, converters.
 */
private val FullBleedPanels = setOf(
    PanelMode.OCR, PanelMode.QR_SCAN, PanelMode.CALCULATOR, PanelMode.CURRENCY,
    PanelMode.UNIT_CONVERT, PanelMode.CALENDAR, PanelMode.AI,
    PanelMode.TRANSLATE, PanelMode.WEB_SEARCH, PanelMode.IMAGE_SEARCH,
    PanelMode.DICTIONARY, PanelMode.SYMBOLS, PanelMode.MEDIA_CONTROL,
)

/**
 * Whether [panel] takes the whole keyboard right now. Emoji and the media
 * panels (GIF, stickers) are full-bleed by choice rather than by nature:
 * they can pay for the toolbar's row with their own header — category tabs
 * for emoji, the search box for media — so the setting is on by default but
 * can be turned off by anyone who wants the toolbar within reach.
 */
private fun isFullBleedPanel(panel: PanelMode, settings: KeyboardSettings): Boolean = when (panel) {
    PanelMode.EMOJI -> settings.emojiFullBleed
    PanelMode.GIF, PanelMode.STICKER -> settings.mediaFullBleed
    else -> panel in FullBleedPanels
}

/**
 * Height of everything a full-bleed panel hides (toolbar plus any emoji or
 * symbol row) — the panel absorbs it so opening one never resizes the
 * keyboard window. Shared with the scanner panels, which draw their own
 * chrome instead of using [FullBleedTool].
 */
internal fun fullBleedHiddenRows(settings: KeyboardSettings): Dp =
    topBarHeight(settings) +
        (if (settings.emojiBarMode == EmojiBarMode.ALWAYS) EmojiBarHeight else 0.dp) +
        (if (settings.symbolRowEnabled) SymbolRowHeight else 0.dp)

/**
 * Chrome for a full-bleed tool: a slim header (back button + tool name)
 * standing in for the hidden toolbar, then the tool filling everything
 * else. The wrapper's height is the key rows plus every row the full-bleed
 * mode hid, so opening one never resizes the keyboard window — the tool
 * gets the reclaimed space instead.
 */
@Composable
private fun FullBleedTool(
    state: KeyboardUiState,
    title: String,
    onClose: () -> Unit,
    // Grows the keyboard window upward beyond the normal keyboard height —
    // for tools (AI, converters) whose content is worth more vertical room.
    extraHeight: Dp = 0.dp,
    // While the tool's search box is being typed into, the key rows render
    // below the panel; the panel collapses to [compactHeight] so the two
    // fit together (same trick as the media panels' search mode).
    compact: Boolean = false,
    compactHeight: Dp = 132.dp,
    // Fills the header's free width (after the back button + title) with the
    // tool's own controls, so the reclaimed toolbar row does real work.
    // With an empty [title] the actions own the whole row — search bars and
    // tab strips sit right next to the back button.
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val kb = LocalKbTheme.current
    val height = if (compact) {
        compactHeight
    } else {
        keyRowsHeight(state) + fullBleedHiddenRows(state.settings) + extraHeight
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolCircle(
                icon = Icons.Outlined.ChevronLeft,
                description = "Back to keyboard",
                active = false,
                onClick = onClose,
            )
            if (title.isNotEmpty()) {
                Text(
                    title,
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (headerActions != null) {
                if (title.isNotEmpty()) Spacer(Modifier.weight(1f))
                headerActions()
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { content() }
    }
}

/**
 * Toolbar + panels + key rows, wrapped in a Box so the tool-drag ghost can
 * float over everything while the toolbox is open.
 */
@Composable
private fun KeyboardBody(
    state: KeyboardUiState,
    onDismissInlineSuggestions: () -> Unit,
    onSmartAccept: () -> Unit,
    onSmartOpen: () -> Unit,
    onToolPrefillConsumed: () -> Unit,
    onHideKeyboard: () -> Unit,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit,
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit,
    onGestureWords: (List<List<GesturePoint>>, List<KeyCenter>, Float) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLayoutSelect: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onEmojiVariant: (String, String) -> Unit,
    onEmojiFavourite: (String) -> Unit,
    onEmojiSuggestion: (String) -> Unit,
    onPunctuation: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onEmojiRecentsClear: () -> Unit,
    onEmojiRecentRemove: (String) -> Unit,
    onEmojiFavouritesReorder: (List<String>) -> Unit,
    onEmojiSearchFieldDelete: () -> Unit,
    onTextEdit: (TextEditAction) -> Unit,
    onPanelChange: (PanelMode) -> Unit,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onClipboardSearchToggle: () -> Unit,
    onClipboardSuggestionDismiss: () -> Unit,
    onSnippet: (Snippet) -> Unit,
    onToolTap: (ToolbarTool) -> Unit,
    onToolbarToolsChange: (List<ToolbarTool>) -> Unit,
    onToolboxOrderChange: (List<ToolbarTool>) -> Unit,
    onToolSettings: (ToolbarTool) -> Unit,
    onToolboxHintDismiss: () -> Unit,
    onWeatherRefresh: () -> Unit,
    onCameraSend: (java.io.File) -> Unit,
    onCameraPermissionRequest: () -> Unit,
    onCalendarPermissionRequest: () -> Unit,
    onScannedInsert: (String) -> Unit,
    onScannedUrlOpen: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onVoicePermissionRequest: () -> Unit,
    onVoiceUndo: () -> Unit,
    onVoiceModelDownload: () -> Unit,
    onWhisperTranslateToggle: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaSeek: (Long) -> Unit,
    onMediaAccessRequest: () -> Unit,
    onMediaResume: () -> Unit,
    onDictionaryLookup: (String) -> Unit,
    onDictionarySearchToggle: () -> Unit,
    onDictionaryInsert: (String) -> Unit,
    onThemeSelect: (String) -> Unit,
    onSoundHaptic: (SoundHapticAction) -> Unit,
    onHandwritingStroke: (HwStroke, IntSize) -> Unit,
    onKeyboardHandwritingStroke: (HwStroke, IntSize) -> Unit,
    onHandwritingUndo: () -> Unit,
    onHandwritingDownload: () -> Unit,
    onMediaQueryTap: () -> Unit,
    onMediaRetry: () -> Unit,
    onGifSelect: (GifItem) -> Unit,
    onGifSourceSelect: (GifSource) -> Unit,
    onWebResult: (WebResult) -> Unit,
    onWebResultOpen: (WebResult) -> Unit,
    onImageResult: (ImageResult) -> Unit,
    onImageResultLink: (ImageResult) -> Unit,
    onTranslateTarget: (String) -> Unit,
    onTranslateReplace: () -> Unit,
    onTranslateInsert: () -> Unit,
    onGrammarFix: (GrammarLint, GrammarFix) -> Unit,
    onGrammarFixAll: () -> Unit,
    onGrammarDismiss: (GrammarLint) -> Unit,
    onGrammarDialect: (GrammarDialect) -> Unit,
    onGrammarFocus: (GrammarLint) -> Unit,
    onWikiOpen: (String) -> Unit,
    onWikiBack: () -> Unit,
    onWikiLoadLinks: () -> Unit,
    onWikiLoadFull: () -> Unit,
    onSymbolInsert: (String) -> Unit,
    onSymbolSetSelect: (String) -> Unit,
    onModeSelect: (String?) -> Unit,
    onToolInsert: (String) -> Unit,
    onUnitSelection: (String) -> Unit,
    onCurrencyPairChange: (String, String) -> Unit,
    onCurrencyRefresh: () -> Unit,
    onPwSetting: (PwSettingAction) -> Unit,
    onTypingTestAction: (TypingTestAction) -> Unit,
    onQrSend: () -> Unit,
    onAiAction: (com.wasimaster.wmkeyboard.core.settings.AiAction) -> Unit,
    onAiReplace: () -> Unit,
    onAiInsert: () -> Unit,
    onAiRetry: () -> Unit,
    onAiRunCustom: () -> Unit,
    onAiPickModel: (com.wasimaster.wmkeyboard.core.settings.AiProvider, String?) -> Unit,
    onAiToggleStripMarkdown: () -> Unit,
    onOpenToolSettings: (ToolbarTool) -> Unit,
    onOpenRoute: (String) -> Unit = {},
) {
    val drag = remember { ToolDragController() }
    // Mirror the drag's view of the bar when the tools read RTL, then flip the
    // committed order back to storage order — the bar is drawn reversed but the
    // saved list is always left-to-right.
    val readsRtl = toolbarReadsRtl(state)
    drag.currentTools =
        if (readsRtl) state.settings.toolbarTools.reversed() else state.settings.toolbarTools
    drag.onCommit =
        if (readsRtl) { tools -> onToolbarToolsChange(tools.reversed()) } else onToolbarToolsChange
    drag.onOrderCommit = onToolboxOrderChange
    drag.onSnap = LocalKeyPressFeedback.current
    drag.onOpenSettings = onToolSettings
    var bodyOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                bodyOrigin = it.positionInRoot()
                drag.bodyCoords = it
            },
    ) {
        Column {
            // The dedicated always-on emoji row (Gboard style) sits between
            // the strip and the keys — or on top of everything, per setting;
            // the emoji panel already is emojis, so it yields there.
            // Full-bleed panels swallow the toolbar row (and any emoji or
            // symbol row) too: the tool absorbs those rows' height, so it
            // gets every pixel the keyboard owns. OCR draws its own chrome;
            // the rest get the [FullBleedTool] back-header wrapper.
            val fullBleed = isFullBleedPanel(state.panel, state.settings)
            val emojiRowVisible = !fullBleed &&
                state.settings.emojiBarMode == EmojiBarMode.ALWAYS && state.panel != PanelMode.EMOJI
            // The symbols panel already is special characters — the row
            // would be redundant there, so it yields like the emoji row.
            val symbolRowVisible = !fullBleed &&
                state.settings.symbolRowEnabled && state.panel != PanelMode.SYMBOLS
            // The rows stack in the user's chosen order (Rows settings).
            // While an emoji search is typing, the toolbar is dead weight —
            // hide it and let the panel spend the height on result rows.
            val emojiSearching = state.panel == PanelMode.EMOJI && state.emojiSearchActive
            // Lock-screen privacy: with the setting on and the keyguard up, drop
            // the whole top strip (suggestions + toolbar, so the clipboard tool
            // and paste chip go with it) and block the clipboard panel, keeping
            // copied text and pinned tools off a screen anyone can wake.
            val lockHidden = state.deviceLocked && state.settings.toolbarBehavior.hideWhenLocked
            for (row in state.settings.barOrder) {
                when (row) {
                    // Disabling the toolbar drops the whole strip — suggestions
                    // and tools alike — so the keys claim its height.
                    BarRow.TOPBAR -> if (state.settings.toolbarBehavior.enabled && !fullBleed && !emojiSearching && !lockHidden) {
                        TopBar(
                            state, onSuggestion, onEmoji, onEmojiSuggestion,
                            onPunctuation = onPunctuation,
                            onPanelChange = onPanelChange,
                            onToolTap = onToolTap,
                            drag = drag,
                            onVoiceToggle = onVoiceToggle,
                            onVoiceUndo = onVoiceUndo,
                            onVoicePermissionRequest = onVoicePermissionRequest,
                            onDismissInlineSuggestions = onDismissInlineSuggestions,
                            onSmartAccept = onSmartAccept,
                            onSmartOpen = onSmartOpen,
                            onClipboardSuggestion = onClipboardItem,
                            onClipboardSuggestionDismiss = onClipboardSuggestionDismiss,
                            onSwipeDownHide = onHideKeyboard,
                        )
                    }
                    BarRow.EMOJI -> if (emojiRowVisible) {
                        EmojiBarStrip(
                            state = state,
                            onEmoji = onEmoji,
                            onOpenPanel = { onPanelChange(PanelMode.EMOJI) },
                        )
                    }
                    BarRow.SYMBOL -> if (symbolRowVisible) {
                        SymbolRowStrip(
                            state = state,
                            onInsert = onToolInsert,
                            onSetSelect = onSymbolSetSelect,
                        )
                    }
                }
            }
            // Deliberately NOT animated. A fade here was tried and reverted:
            // an alpha on this subtree covers the key rows as well as the
            // panels, so every panel close briefly rendered a translucent
            // keyboard with the app's own text field showing through it, and
            // the alpha had to be applied a frame after the new content was
            // already on screen, which flashed it at full strength first.
            // Animating the swap needs the panels to be layered rather than
            // exchanged; until then the cut is the honest option.
        when (if (lockHidden && state.panel == PanelMode.CLIPBOARD) PanelMode.NONE else state.panel) {
                PanelMode.EMOJI -> EmojiPanel(
                    state, onEmoji, onEmojiVariant, onEmojiFavourite, onEmojiQueryTap, onEmojiRecentsClear,
                    onRecentRemove = onEmojiRecentRemove,
                    onFavouritesReorder = onEmojiFavouritesReorder,
                    onSearchFieldDelete = onEmojiSearchFieldDelete,
                    onKey = onKey,
                    // Toggling the open panel closes it — back to the keys.
                    onClose = { onPanelChange(PanelMode.EMOJI) },
                )
                PanelMode.CLIPBOARD -> ClipboardPanel(
                    state, onClipboardItem, onClipboardPin, onClipboardDelete,
                    onClipboardSearchToggle = onClipboardSearchToggle,
                    onKey = onKey,
                    // Toggling the open panel closes it — back to the keys.
                    onClose = { onPanelChange(PanelMode.CLIPBOARD) },
                )
                PanelMode.SNIPPETS -> SnippetsPanel(
                    state, onSnippet,
                    onOpenSettings = { onOpenToolSettings(ToolbarTool.SNIPPETS) },
                )
                PanelMode.TEXT_EDIT -> TextEditPanel(state, onTextEdit)
                PanelMode.TOOLBOX -> ToolboxPanel(state, onToolTap, onToolboxHintDismiss, drag)
                // Regular panels (toolbar stays visible): the sensors read
                // fine at keyboard height and the toolbar keeps tool-hopping
                // one tap away.
                PanelMode.COMPASS -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(keyRowsHeight(state)),
                ) { CompassPanel(state) }
                PanelMode.LEVEL -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(keyRowsHeight(state)),
                ) { LevelPanel(state) }
                PanelMode.MOON_PHASE -> MoonPhasePanel(state)
                PanelMode.WEATHER -> WeatherPanel(
                    state = state,
                    onRefresh = onWeatherRefresh,
                    onOpenSettings = { onToolTap(ToolbarTool.SETTINGS) },
                )
                // No extraHeight: the reclaimed toolbar/emoji/symbol rows are
                // already enough for the grid, and growing the window past
                // keyboard height pushed the app's content out of view.
                PanelMode.CALENDAR -> FullBleedTool(
                    state, "Calendar",
                    onClose = { onPanelChange(PanelMode.CALENDAR) },
                ) { CalendarPanel(state, onRequestPermission = onCalendarPermissionRequest) }
                PanelMode.THEMES -> ThemesPanel(
                    state,
                    onThemeSelect,
                    onOpenSettings = { onOpenRoute("themes") },
                )
                PanelMode.SOUND_HAPTICS -> SoundHapticsPanel(state, onSoundHaptic)
                PanelMode.NUMPAD -> NumpadPanel(state, onText, onKey)
                PanelMode.HANDWRITING -> if (BuildConfig.ENABLE_ML_KIT_HANDWRITING) {
                    HandwritingPanel(
                        state = state,
                        onStroke = onHandwritingStroke,
                        onUndoStroke = onHandwritingUndo,
                        onDownloadModel = onHandwritingDownload,
                        onKey = onKey,
                        onLayoutSelect = onLayoutSelect,
                        onClose = { onPanelChange(PanelMode.HANDWRITING) },
                    )
                } else {
                    onPanelChange(PanelMode.SNIPPETS)
                }
                PanelMode.CAMERA -> CameraPanel(
                    state = state,
                    onSend = onCameraSend,
                    onRequestPermission = onCameraPermissionRequest,
                    // Toggling the open panel closes it.
                    onClose = { onPanelChange(PanelMode.CAMERA) },
                )
                PanelMode.OCR -> if (BuildConfig.ENABLE_ML_KIT_SCANNERS) {
                    OcrPanel(
                        state = state,
                        onInsert = onScannedInsert,
                        onRequestPermission = onCameraPermissionRequest,
                        onClose = { onPanelChange(PanelMode.OCR) },
                    )
                } else {
                    onPanelChange(PanelMode.SNIPPETS)
                }
                PanelMode.QR_SCAN -> if (BuildConfig.ENABLE_ML_KIT_SCANNERS) {
                    QrScanPanel(
                        state = state,
                        onInsert = onScannedInsert,
                        onOpenUrl = onScannedUrlOpen,
                        onRequestPermission = onCameraPermissionRequest,
                        onClose = { onPanelChange(PanelMode.QR_SCAN) },
                    )
                } else {
                    onPanelChange(PanelMode.SNIPPETS)
                }
                PanelMode.VOICE -> VoicePanel(
                    state = state,
                    onToggle = onVoiceToggle,
                    onUndo = onVoiceUndo,
                    onRequestPermission = onVoicePermissionRequest,
                    onDownloadModel = onVoiceModelDownload,
                    onToggleTranslate = onWhisperTranslateToggle,
                    onOpenVoiceSettings = onOpenVoiceSettings,
                    onKey = onKey,
                    onLayoutSelect = onLayoutSelect,
                    onClose = { onPanelChange(PanelMode.VOICE) },
                )
                PanelMode.MEDIA_CONTROL -> FullBleedTool(
                    state,
                    title = "Media",
                    onClose = { onPanelChange(PanelMode.MEDIA_CONTROL) },
                ) {
                    MediaControlPanel(
                        state = state,
                        onPlayPause = onMediaPlayPause,
                        onNext = onMediaNext,
                        onPrevious = onMediaPrevious,
                        onSeek = onMediaSeek,
                        onRequestAccess = onMediaAccessRequest,
                        onResume = onMediaResume,
                    )
                }
                PanelMode.DICTIONARY -> FullBleedTool(
                    state, title = "",
                    onClose = { onPanelChange(PanelMode.DICTIONARY) },
                    // While the query types on the key rows below, only the
                    // header (with its search bar) needs to stay visible.
                    compact = state.dictionarySearchActive,
                    compactHeight = 44.dp,
                    headerActions = {
                        DictionaryHeaderSearchBar(
                            state = state,
                            onSearchToggle = onDictionarySearchToggle,
                            onLookup = onDictionaryLookup,
                        )
                    },
                ) {
                    DictionaryPanel(
                        state = state,
                        onLookup = onDictionaryLookup,
                        onInsert = onDictionaryInsert,
                    )
                }
                PanelMode.TRANSLATE -> FullBleedTool(
                    state, title = "",
                    onClose = { onPanelChange(PanelMode.TRANSLATE) },
                    compact = state.mediaSearchActive,
                    // Translations run long; give the result more room to breathe
                    // than the media panels' default compact height.
                    compactHeight = 180.dp,
                    headerActions = {
                        MediaHeaderSearchBar(
                            state = state,
                            placeholder = "Type text to translate…",
                            activePlaceholder = "Type text to translate…",
                            onQueryTap = onMediaQueryTap,
                        )
                    },
                ) {
                    TranslatePanel(
                        state = state,
                        onTarget = onTranslateTarget,
                        onReplace = onTranslateReplace,
                        onInsert = onTranslateInsert,
                    )
                }
                PanelMode.GRAMMAR -> if (BuildConfig.ENABLE_GRAMMAR) {
                    GrammarPanel(
                        state = state,
                        onFix = onGrammarFix,
                        onFixAll = onGrammarFixAll,
                        onDismiss = onGrammarDismiss,
                        onDialect = onGrammarDialect,
                        onFocus = onGrammarFocus,
                    )
                } else {
                    onPanelChange(PanelMode.SNIPPETS)
                }
                PanelMode.GIF, PanelMode.STICKER -> {
                    val stickers = state.panel == PanelMode.STICKER
                    if (state.settings.mediaFullBleed) {
                        // Search moves up into the reclaimed toolbar row, next
                        // to the back button — same shape as the dictionary.
                        FullBleedTool(
                            state,
                            title = "",
                            onClose = { onPanelChange(state.panel) },
                            // Search collapses the panel so the key rows fit
                            // below it, keeping a band of live results up.
                            compact = state.mediaSearchActive,
                            headerActions = {
                                GifHeaderSearchBar(state, stickers, onMediaQueryTap)
                            },
                        ) {
                            GifPanel(
                                state = state,
                                stickers = stickers,
                                onQueryTap = onMediaQueryTap,
                                onRetry = onMediaRetry,
                                onSelect = onGifSelect,
                                onSourceSelect = onGifSourceSelect,
                                onOpenToolSettings = onOpenToolSettings,
                                fullBleed = true,
                            )
                        }
                    } else {
                        GifPanel(
                            state = state,
                            stickers = stickers,
                            onQueryTap = onMediaQueryTap,
                            onRetry = onMediaRetry,
                            onSelect = onGifSelect,
                            onSourceSelect = onGifSourceSelect,
                            onOpenToolSettings = onOpenToolSettings,
                        )
                    }
                }
                PanelMode.WEB_SEARCH -> FullBleedTool(
                    state, title = "",
                    onClose = { onPanelChange(PanelMode.WEB_SEARCH) },
                    compact = state.mediaSearchActive,
                    headerActions = {
                        MediaHeaderSearchBar(
                            state = state,
                            placeholder = "Search the web",
                            onQueryTap = onMediaQueryTap,
                            attribution = "via Brave"
                                .takeIf { ToolApiKeys.hasSearchProvider(state.settings) },
                        )
                    },
                ) {
                    WebSearchPanel(
                        state = state,
                        onRetry = onMediaRetry,
                        onResult = onWebResult,
                        onOpen = onWebResultOpen,
                        onOpenToolSettings = onOpenToolSettings,
                    )
                }
                PanelMode.IMAGE_SEARCH -> FullBleedTool(
                    state, title = "",
                    onClose = { onPanelChange(PanelMode.IMAGE_SEARCH) },
                    compact = state.mediaSearchActive,
                    headerActions = {
                        MediaHeaderSearchBar(
                            state = state,
                            placeholder = "Search images",
                            onQueryTap = onMediaQueryTap,
                            attribution = "via Brave"
                                .takeIf { ToolApiKeys.hasSearchProvider(state.settings) },
                        )
                    },
                ) {
                    ImageSearchPanel(
                        state = state,
                        onRetry = onMediaRetry,
                        onResult = onImageResult,
                        onResultLink = onImageResultLink,
                        onOpenToolSettings = onOpenToolSettings,
                    )
                }
                PanelMode.WIKIPEDIA -> WikipediaPanel(
                    state = state,
                    onQueryTap = onMediaQueryTap,
                    onRetry = onMediaRetry,
                    onOpen = onWikiOpen,
                    onBack = onWikiBack,
                    onLoadLinks = onWikiLoadLinks,
                    onLoadFull = onWikiLoadFull,
                    onInsert = onToolInsert,
                )
                PanelMode.SYMBOLS -> {
                    // Category selection lives up here so the header chips
                    // and the grid share it.
                    val recents = state.settings.symbolRecents
                    var symbolCategory by rememberSaveable(recents.isNotEmpty()) {
                        mutableStateOf(
                            if (recents.isNotEmpty()) "Recents"
                            else SymbolCatalog.categories.first().name
                        )
                    }
                    FullBleedTool(
                        state, title = "",
                        onClose = { onPanelChange(PanelMode.SYMBOLS) },
                        headerActions = {
                            SymbolCategoryChips(
                                state = state,
                                selected = symbolCategory,
                                onSelect = { symbolCategory = it },
                            )
                        },
                    ) { SymbolsPanel(state, onSymbolInsert, symbolCategory) }
                }
                PanelMode.CALCULATOR -> FullBleedTool(
                    state, "Calculator",
                    onClose = { onPanelChange(PanelMode.CALCULATOR) },
                ) { CalculatorPanel(state, onToolInsert, onToolPrefillConsumed) }
                PanelMode.UNIT_CONVERT -> FullBleedTool(
                    state, "Unit converter",
                    onClose = { onPanelChange(PanelMode.UNIT_CONVERT) },
                    extraHeight = 120.dp,
                ) { UnitConverterPanel(state, onToolInsert, onUnitSelection, onToolPrefillConsumed) }
                PanelMode.CURRENCY -> FullBleedTool(
                    state, "Currency",
                    onClose = { onPanelChange(PanelMode.CURRENCY) },
                    extraHeight = 120.dp,
                ) {
                    CurrencyPanel(
                        state = state,
                        onPairChange = onCurrencyPairChange,
                        onRefresh = onCurrencyRefresh,
                        onInsert = onToolInsert,
                        onPrefillConsumed = onToolPrefillConsumed,
                    )
                }
                PanelMode.QR_GEN -> QrGeneratorPanel(state, onQrSend)
                PanelMode.PASSWORD_GEN -> FullBleedTool(
                    state, title = "",
                    onClose = { onPanelChange(PanelMode.PASSWORD_GEN) },
                    // Not full-bleed: the toolbar stays reachable above the
                    // panel, so it collapses to the key-rows height instead of
                    // swallowing the toolbar's rows.
                    compact = true,
                    compactHeight = keyRowsHeight(state),
                    headerActions = {
                        val passphraseMode = state.settings.pwPassphraseMode
                        Spacer(Modifier.width(4.dp))
                        ToolPanelChip("Password", selected = !passphraseMode) {
                            onPwSetting(PwSettingAction.PassphraseMode(false))
                        }
                        Spacer(Modifier.width(6.dp))
                        ToolPanelChip("Passphrase", selected = passphraseMode) {
                            onPwSetting(PwSettingAction.PassphraseMode(true))
                        }
                        Spacer(Modifier.weight(1f))
                    },
                ) { PasswordPanel(state, onPwSetting, onToolInsert) }
                PanelMode.TYPING_TEST -> FullBleedTool(
                    state = state,
                    title = "Typing speed",
                    onClose = { onPanelChange(PanelMode.TYPING_TEST) },
                    // A running test shares the window with the key rows —
                    // the user is typing on them — so the panel collapses
                    // the way the media search boxes do. The results screen
                    // needs no keys and takes the full height back.
                    compact = state.typingTest.result == null,
                    compactHeight = 156.dp,
                    headerActions = {
                        typingHeaderBest(state.settings)?.let { best ->
                            Text(
                                best,
                                color = LocalKbTheme.current.secondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        ToolPanelChip("Restart") { onTypingTestAction(TypingTestAction.Restart) }
                    },
                ) { TypingTestPanel(state, onTypingTestAction) }
                PanelMode.AI -> FullBleedTool(
                    state = state,
                    title = "AI",
                    onClose = { onPanelChange(PanelMode.AI) },
                    // Reasoning models stream their think block into the same
                    // box as the answer, so that mode — and only that mode —
                    // needs the taller window; otherwise the panel stays at
                    // the normal keyboard height.
                    extraHeight = if (state.settings.aiShowThinking) 160.dp else 0.dp,
                    // The Custom instruction types on the key rows, so the
                    // panel collapses to leave room for them below.
                    compact = state.aiCustomInputActive,
                    compactHeight = 132.dp,
                    headerActions = {
                        val ai = state.ai
                        if (ai is AiUi.Ready && !ai.generating) {
                            ToolPanelChip("Replace", selected = true) { onAiReplace() }
                            Spacer(Modifier.width(5.dp))
                            ToolPanelChip("Insert") { onAiInsert() }
                            Spacer(Modifier.width(5.dp))
                            ToolPanelChip("↻") { onAiRetry() }
                            Spacer(Modifier.width(5.dp))
                        }
                        ToolCircle(
                            icon = Icons.Outlined.Settings,
                            description = "AI settings",
                            active = false,
                        ) { onOpenToolSettings(ToolbarTool.AI) }
                    },
                ) {
                    AiPanel(
                        state = state,
                        onAction = onAiAction,
                        onReplace = onAiReplace,
                        onInsert = onAiInsert,
                        onRetry = onAiRetry,
                        onRunCustom = onAiRunCustom,
                        onPickModel = onAiPickModel,
                        onToggleStripMarkdown = onAiToggleStripMarkdown,
                        onOpenToolSettings = onOpenToolSettings,
                    )
                }
                PanelMode.MODES -> ModesPanel(
                    state, onModeSelect,
                    onOpenSettings = { onOpenToolSettings(ToolbarTool.MODES) },
                )
                // With a hardware keyboard and toolbar-only mode on, the keys
                // step aside and just the toolbar remains — tools stay one tap
                // away while the physical keyboard does the typing.
                PanelMode.NONE -> if (
                    !(state.hardwareKeyboardPresent && state.settings.toolbarBehavior.onlyWithHardwareKeyboard)
                ) {
                    KeyRows(
                        state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLayoutSelect,
                        onGestureWords = onGestureWords,
                        onKeyboardHandwritingStroke = onKeyboardHandwritingStroke,
                    )
                }
            }
            // In emoji search mode the letters stay visible for typing the query.
            if (state.panel == PanelMode.EMOJI && state.emojiSearchActive) {
                KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLayoutSelect)
            }
            // The AI Custom instruction types on the key rows under its panel.
            if (state.aiCustomInputActive) {
                KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLayoutSelect)
            }
            // Same for a dictionary search: the query types on the key rows.
            if (state.panel == PanelMode.DICTIONARY && state.dictionarySearchActive) {
                KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLayoutSelect)
            }
            // Same for a media panel's search box (translate is one now —
            // its query types into the panel), and always under the grammar
            // strip (it follows the field live).
            // A typing test is nothing but the key rows — they are what the
            // user is being timed on. They go away on the results screen.
            if ((state.panel.hasMediaSearch && state.mediaSearchActive) ||
                state.panel == PanelMode.GRAMMAR ||
                state.typingTestActive
            ) {
                KeyRows(state, onKey, onText, onGesture, onGesturePreview, onCursorMove, onLayoutSelect)
            }
        }
        drag.dragging?.let { tool ->
            val kb = LocalKbTheme.current
            val ghost = drag.position - bodyOrigin
            Box(
                modifier = Modifier
                    .offset { IntOffset((ghost.x - 22.dp.toPx()).roundToInt(), (ghost.y - 22.dp.toPx()).roundToInt()) }
                    .size(44.dp)
                    .background(kb.toolCircleActive, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    toolIcon(tool),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = kb.toolCircleActiveIcon,
                )
            }
        }
    }
}

// ---- key grid ----

/** One sampled point of the glide trail, timestamped for age-based fading. */
private data class TrailPoint(val position: Offset, val timeMs: Long)

/**
 * Takes the place of the `?123` layer's own digit row when the number row is
 * on and already supplies those digits one row above. Carries the symbols
 * that layer has nowhere else to put.
 */
private val SymbolsFillRow = listOf("=", "\\", "<", ">", "[", "]", "{", "}", "|", "~")
    .map { Key(it) }

/**
 * Replaces the digit number row while the symbols-2 (`=\<`) layer is showing.
 * The digits are one tap away on the symbols-1 layer, so this slot carries an
 * extra set of arrow and comparison symbols the symbol layers have no room for
 * rather than a second copy of the numbers.
 */
private val SymbolsShiftedFillRow = listOf(
    Key("←", longPress = listOf("⟵", "↔")),
    Key("→", longPress = listOf("⟶", "↦")),
    Key("↑", longPress = listOf("↕")),
    Key("↓"),
    Key("±", longPress = listOf("∓")),
    Key("∞"),
    Key("≈", longPress = listOf("≅", "≡")),
    Key("≠"),
    Key("≤", longPress = listOf("≪")),
    Key("≥", longPress = listOf("≫")),
)

@Composable
private fun KeyRows(
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onGesture: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onGesturePreview: (List<GesturePoint>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onCursorMove: (Int) -> Unit = {},
    onLayoutSelect: (String) -> Unit = {},
    onGestureWords: (List<List<GesturePoint>>, List<KeyCenter>, Float) -> Unit = { _, _, _ -> },
    onKeyboardHandwritingStroke: (HwStroke, IntSize) -> Unit = { _, _ -> },
) {
    val layout = currentLayout(state)
    // Letter-area swipes are drawing handwriting rather than gliding a word
    // (full builds only). Capture arms whenever the mode is selected; the
    // service decides whether the drawn ink recognizes or prompts a download.
    val handwriteSwipe = BuildConfig.ENABLE_ML_KIT_HANDWRITING &&
        state.settings.gestureTyping &&
        state.settings.letterSwipeAction == LetterSwipeAction.HANDWRITE &&
        state.layoutMode == LayoutMode.LETTERS &&
        state.panel == PanelMode.NONE
    val gestureEnabled = !handwriteSwipe &&
        state.settings.gestureTyping &&
        state.layoutMode == LayoutMode.LETTERS &&
        state.language.gestureLexicon &&
        state.panel == PanelMode.NONE &&
        // A layout missing letters would still satisfy every check above, and
        // the decoder scores against whatever centres it has — so it would
        // return confident nonsense rather than nothing. Switch it off instead.
        state.layouts.lettersHaveFullAlphabet

    // Letter-key centres and width, captured from layout in this Box's space.
    // Keyed on the layout: the map is written by onGloballyPositioned per key,
    // so a layout with fewer letters than the last one would otherwise keep the
    // previous grid's centres and anchor swipes on keys that are not on screen.
    // A LaunchedEffect that cleared it would race those positioning callbacks.
    val keyCenters = remember(layout) { mutableStateMapOf<Char, Offset>() }
    // Spacebar bounds in this Box's space, for the multi-word glide split.
    // Reset per layout alongside the key centres. A split keyboard positions
    // two half-spacebars into this one slot; the last one measured wins, which
    // covers the common (non-split) case and degrades to one half for split.
    val spaceRect = remember(layout) { mutableStateOf<Rect?>(null) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Rows narrower than the grid (e.g. the 9-key QWERTY home row) keep the
    // standard key width and are centred with side gaps, instead of stretching
    // their keys to fill the full width.
    //
    // The grid is the width the most rows share, not the first row's — see
    // gridWeightOf. That keeps a lone narrow row (one inserted at the top) from
    // hijacking the reference and filling the width, and a lone wide row
    // (Dvorak's third) from padding every other row.
    //
    // A layout can arrive with no rows at all — the editor allows deleting them
    // and an imported file is untrusted — so gridWeightOf returns 0 for empty.
    val gridWeight = gridWeightOf(layout.rows).takeIf { it > 0f } ?: 10f
    // One key's width, for the gesture decoder's distance normalisation.
    // Derived from the grid rather than recorded by whichever letter key
    // happened to measure last, which made decoding depend on where a wide key
    // sat in the layout. The column's own horizontal padding is taken off first
    // so this is the real cell width rather than an approximation of it.
    //
    // Held in a State because the gesture detector below is keyed on
    // `gestureEnabled` and would otherwise capture the first frame's value,
    // before the box has been measured at all.
    val rowInsetPx = with(LocalDensity.current) { KeyRowsPadHorizontal.toPx() * 2 }
    val keyWidth = rememberUpdatedState(
        if (boxSize.width > 0) ((boxSize.width - rowInsetPx) / gridWeight).coerceAtLeast(0f) else 0f,
    )
    // Smart key-hit detection: a boundary tap can be claimed by a likelier
    // neighbour. Only the letters layer, and only while the field is composing.
    val smartHit = state.settings.layoutBehavior.smartHitDetection &&
        state.layoutMode == LayoutMode.LETTERS &&
        state.panel == PanelMode.NONE
    // Live next-letter distribution; read fresh inside the down-observer, which
    // must not restart on every keystroke.
    val nextBias = rememberUpdatedState(if (smartHit) state.nextLetterBias else emptyMap())
    // Pointer → the letter its down chose to remap to, set at down time by the
    // observer and consumed by the owning key on release.
    val hitRemap = remember { HashMap<PointerId, Char>() }
    // Current-layer letter keys by lowercase char, so a remap resolves to the
    // correctly-cased Key to commit. Keyed on layout: rebuilt when it changes.
    val letterKeys = remember(layout) {
        buildMap<Char, Key> {
            for (row in layout.rows) {
                for (key in row) {
                    val ch = key.label.singleOrNull()
                        ?.takeIf { key.action == KeyAction.Text && it.isLetter() }
                        ?: continue
                    put(ch.lowercaseChar(), key)
                }
            }
        }
    }
    // Substitutes the committed key when the owning letter's down was remapped
    // toward a likelier neighbour. Stable across keystrokes (depends only on the
    // layout), so it never restarts a key's pointerInput.
    val smartResolve = remember(letterKeys) {
        { key: Key, id: PointerId ->
            val target = hitRemap[id]
            val own = key.label.singleOrNull()
                ?.takeIf { key.action == KeyAction.Text && it.isLetter() }
                ?.lowercaseChar()
            if (target != null && own != null && target != own) {
                letterKeys[target] ?: key
            } else {
                key
            }
        }
    }
    // Drop any in-flight remap when the feature switches off or the layout
    // changes: a release arriving after such a change must not apply a decision
    // made against the old grid (the down-observer that would have cleared it is
    // gone once smartHit is false).
    LaunchedEffect(smartHit, layout) { hitRemap.clear() }
    var trail by remember { mutableStateOf<List<TrailPoint>>(emptyList()) }
    var trailReleased by remember { mutableStateOf(false) }
    // Frame clock driving the fade; points older than trailMs vanish.
    var trailNow by remember { mutableLongStateOf(0L) }
    val trailColor = LocalKbTheme.current.gestureTrail
    // Customisable glide-trail + start-sensitivity knobs (Settings → Gestures).
    val gesture = state.settings.gesture
    val trailMs = gesture.trailDurationMs.toLong()
    val trailOpacity = gesture.trailOpacity
    val trailHeadWidth = gesture.trailWidthDp
    val startSlop = gesture.startThresholdSlop
    val cooldownMs = gesture.postTypeCooldownMs
    val spaceGlide = gesture.spaceGlideMultiWord
    // Stamp of the last tap-typed key (uptime ms). A glide starting within
    // [cooldownMs] of it has to travel further before it takes over, so a stray
    // slide off a key during fast tapping is not misread as a swipe-word. Held
    // in a State so the tap handlers below can write it without restarting the
    // gesture detector, which reads it live inside its pointer loop.
    val lastKeyPressTime = remember { mutableLongStateOf(0L) }
    val stampedOnKey = remember(onKey) {
        { k: Key -> lastKeyPressTime.longValue = SystemClock.uptimeMillis(); onKey(k) }
    }
    val stampedOnText = remember(onText) {
        { t: String -> lastKeyPressTime.longValue = SystemClock.uptimeMillis(); onText(t) }
    }
    val dotCooldownMs = gesture.handwriteDotCooldownMs
    // Uptime of the last *drawn* handwriting stroke. For [dotCooldownMs] after
    // it, a tap over the letters is grabbed as an ink dot (the mark on an i/j/t)
    // instead of typing, so a two-part character can be completed without the
    // dot committing a letter.
    val lastHwStrokeTime = remember { mutableLongStateOf(0L) }
    // Points of the handwriting stroke being drawn right now (box space); the
    // finished strokes waiting for recognition come back from service state.
    var hwActiveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    LaunchedEffect(trail.isNotEmpty()) {
        while (trail.isNotEmpty()) {
            withFrameMillis { now ->
                trailNow = now
                // After finger-up the trail is left in place to fade out on
                // its own; drop it once every point has expired.
                if (trailReleased && trail.all { now - it.timeMs > trailMs }) {
                    trail = emptyList()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                boxOrigin = it.positionInRoot()
                boxSize = it.size
            }
            .pointerInput(gestureEnabled, spaceGlide, startSlop, cooldownMs, trailMs) {
                if (!gestureEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    // Post-typing cooldown: right after a tap, hold the glide back
                    // by requiring more travel, fading out across the window. A
                    // finger that lands long after the last keypress swipes as
                    // normal (boost 1×).
                    val sinceTap = down.uptimeMillis - lastKeyPressTime.longValue
                    val cooldownBoost = if (cooldownMs > 0 && sinceTap in 0 until cooldownMs.toLong()) {
                        1f + POST_TYPE_SLOP_BOOST * (1f - sinceTap.toFloat() / cooldownMs)
                    } else {
                        1f
                    }
                    val effectiveSlop = startSlop * cooldownBoost
                    var isGesture = false
                    // Completed word segments (multi-word glide) plus the one
                    // being drawn now. With spaceGlide off there is only ever
                    // one segment — the whole stroke, spacebar points included.
                    val segments = ArrayList<List<GesturePoint>>()
                    var seg = ArrayList<GesturePoint>()
                    var wasOverSpace = false
                    var samples = 0
                    val trailPoints = ArrayList<TrailPoint>()
                    seg.add(GesturePoint(down.position.x, down.position.y))
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (isGesture) change.consume()
                            break
                        }
                        if (!isGesture && keyWidth.value > 0f &&
                            (change.position - down.position).getDistance() > slop * effectiveSlop &&
                            nearLetterKey(down.position, keyCenters, keyWidth.value)
                        ) {
                            isGesture = true
                            trailReleased = false
                        }
                        if (isGesture) {
                            change.consume()
                            samples++
                            // Crossing the spacebar ends the current word and
                            // begins the next, so a stroke can chain words
                            // without lifting. Spacebar points anchor no letter,
                            // so they are dropped from the word's shape rather
                            // than added to either side.
                            // spaceRect is in root space; lift the box-local
                            // touch point into root space to test it.
                            val overSpace = spaceGlide &&
                                spaceRect.value?.contains(change.position + boxOrigin) == true
                            if (overSpace) {
                                if (!wasOverSpace && seg.size >= 3) {
                                    segments.add(seg)
                                    seg = ArrayList()
                                }
                            } else {
                                seg.add(GesturePoint(change.position.x, change.position.y))
                            }
                            wasOverSpace = overSpace
                            trailPoints.add(TrailPoint(change.position, change.uptimeMillis))
                            // Long swipes keep only the still-visible tail.
                            while (trailPoints.size > 1 &&
                                change.uptimeMillis - trailPoints.first().timeMs > trailMs
                            ) {
                                trailPoints.removeAt(0)
                            }
                            trailNow = change.uptimeMillis
                            trail = trailPoints.toList()
                            // Live preview of the word being drawn now, every
                            // few samples.
                            if (samples % 6 == 0 && seg.size >= 3) {
                                onGesturePreview(
                                    seg.toList(),
                                    keyCenters.map { (char, center) -> KeyCenter(char, center.x, center.y) },
                                    keyWidth.value,
                                )
                            }
                        }
                    }
                    if (isGesture) {
                        if (seg.size >= 4) segments.add(seg)
                        val words = segments.filter { it.size >= 4 }
                        if (words.isNotEmpty()) {
                            val keyList = keyCenters.map { (char, center) ->
                                KeyCenter(char, center.x, center.y)
                            }
                            if (words.size > 1) {
                                onGestureWords(words, keyList, keyWidth.value)
                            } else {
                                onGesture(words.first(), keyList, keyWidth.value)
                            }
                        }
                    }
                    trailReleased = true
                }
            }
            // Handwriting: a drag over the keys is one ink stroke instead of a
            // glide. Only one of the two detectors is ever live — glide's
            // `gestureEnabled` is false whenever `handwriteSwipe` is true. A
            // press that never travels past the slop stays unconsumed and
            // falls through to the key, so taps still type.
            .pointerInput(handwriteSwipe, dotCooldownMs) {
                if (!handwriteSwipe) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    // Dot window: for a short spell after a drawn stroke, a tap
                    // over the letters is taken as another stroke of the same
                    // character (the dot/cross) rather than typing a key. Only
                    // over a letter, so space/enter still work; captured from the
                    // down so even a stationary tap is grabbed as ink.
                    val dotWindow = dotCooldownMs > 0 && keyWidth.value > 0f &&
                        down.uptimeMillis - lastHwStrokeTime.longValue in 0 until dotCooldownMs.toLong() &&
                        nearLetterKey(down.position, keyCenters, keyWidth.value)
                    var isStroke = dotWindow
                    // Whether the finger actually travelled — only a real drawn
                    // stroke reopens the dot window, so a dot-tap does not keep
                    // swallowing later taps.
                    var moved = false
                    val pts = ArrayList<HwPoint>()
                    val live = ArrayList<Offset>()
                    pts.add(HwPoint(down.position.x, down.position.y, down.uptimeMillis))
                    live.add(down.position)
                    if (isStroke) down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (isStroke) change.consume()
                            break
                        }
                        if ((change.position - down.position).getDistance() > slop * 2) {
                            isStroke = true
                            moved = true
                        }
                        if (isStroke) {
                            change.consume()
                            pts.add(HwPoint(change.position.x, change.position.y, change.uptimeMillis))
                            live.add(change.position)
                            hwActiveStroke = live.toList()
                        }
                    }
                    // A dot tap is a single-point stroke; a drawn stroke needs at
                    // least two points to have a shape.
                    if (isStroke && pts.size >= (if (moved) 2 else 1)) {
                        onKeyboardHandwritingStroke(HwStroke(pts.toList()), boxSize)
                        // Only a drawn stroke arms the dot window for the next tap.
                        if (moved) lastHwStrokeTime.longValue = SystemClock.uptimeMillis()
                    }
                    hwActiveStroke = emptyList()
                }
            }
            // Smart key-hit detection: watch every pointer-down on the Initial
            // pass (before the keys see it), and if a likelier neighbour should
            // claim this touch, record the remap for the owning key to consume
            // on release. Never consumes — taps, glides and long-presses are all
            // untouched; only which letter finally commits can change.
            .pointerInput(smartHit) {
                if (!smartHit) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            when {
                                change.changedToDownIgnoreConsumed() -> {
                                    val target = smartHitTarget(
                                        change.position, keyCenters, keyWidth.value, nextBias.value,
                                    )
                                    if (target != null) {
                                        hitRemap[change.id] = target
                                    } else {
                                        hitRemap.remove(change.id)
                                    }
                                }
                                // Any lift OR cancel (glide steals the pointer):
                                // once it is no longer pressed the remap is spent.
                                !change.pressed -> hitRemap.remove(change.id)
                            }
                        }
                    }
                }
            },
    ) {
        // No spacing between cells: each key's touch target fills its whole
        // grid cell (gaps included) so a press landing between two keys
        // still hits the nearest one. The visual gap comes from per-key
        // padding inside KeyButton.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KeyRowsPadHorizontal, vertical = KeyRowsPadVertical),
        ) {
            val onLetterPositioned: (Char, LayoutCoordinates) -> Unit = { letter, coords ->
                val topLeft = coords.positionInRoot() - boxOrigin
                keyCenters[letter] = Offset(
                    topLeft.x + coords.size.width / 2f,
                    topLeft.y + coords.size.height / 2f,
                )
            }
            // Records the spacebar's rect in *root* coordinates for the
            // multi-word split. Stored root-relative rather than box-relative on
            // purpose: this callback can fire before the Box's own
            // onGloballyPositioned has set `boxOrigin`, and unlike the letter
            // centres (a state map the keys keep re-reporting) it fires only
            // once, so subtracting a still-zero `boxOrigin` here would leave the
            // rect stuck in absolute space and the containment test — which runs
            // in box space — would never hit. The gesture loop adds the live
            // `boxOrigin` at test time instead, when the Box is laid out.
            val onSpacePositioned: (LayoutCoordinates) -> Unit = { coords ->
                spaceRect.value = coords.boundsInRoot()
            }
            val split = state.settings.splitKeyboard
            val splitGapPercent = state.settings.splitGapPercent
            val mode = state.layoutMode
            // The digits keep the same slot on every layer, so switching
            // layers moves neither the row nor the pad below it. The `?123`
            // layer leads with its own digit row, which would be a second
            // copy directly underneath — `bodyRows` swaps that one out for
            // the symbols the layer otherwise has no room for.
            val bodyRows = remember(layout, mode, state.settings.numberRow) {
                // Only when that first row really is the digits. A custom
                // symbols layer that leads with something else would otherwise
                // lose its top row outright, with nothing on screen to explain
                // where it went.
                val leadsWithDigits = layout.rows.firstOrNull()
                    ?.all { it.action == KeyAction.Text && (it.output ?: it.label).isSingleDigit() }
                    ?: false
                if (state.settings.numberRow && mode == LayoutMode.SYMBOLS && leadsWithDigits) {
                    listOf(SymbolsFillRow) + layout.rows.drop(1)
                } else {
                    layout.rows
                }
            }
            if (state.settings.numberRow) {
                // Follows the same guard as the pad itself, so a search box
                // opened over a number field gets its digit row back.
                val kind = if (numericPadActive(state)) state.fieldKind else FieldKind.TEXT
                // A layout can supply its own row for this layer; the built-in
                // choices below are the fallback rather than the rule.
                val authored = state.authoredNumberRow(state.layoutMode)
                // The digit row tracks the active layer (and, optionally, shift)
                // so the same slot serves more symbols the deeper the user goes:
                // digits on letters/symbols-1, extra symbols on symbols-2, and —
                // when the option is on — the symbol fill row while shift is held
                // on the letters layer.
                val shiftSymbols = state.settings.layoutBehavior.numberRowShiftSymbols
                val extraRow = remember(kind, authored, state.layoutMode, state.shiftState, shiftSymbols) {
                    authored ?: when {
                        // A keypad already leads with digits, so the row
                        // carries what the pad lacks rather than a second set
                        // of the same numbers.
                        kind == FieldKind.PHONE ->
                            listOf("+", "*", "#", ",", ";", "(", ")", "-", "/", ".")
                                .map { Key(it) }
                        kind.isNumericPad ->
                            listOf("+", "-", "*", "/", "=", "(", ")", "%", ":", ".")
                                .map { Key(it) }
                        // Symbols-2 reuses the number-row slot for the arrow and
                        // comparison symbols it has nowhere else to put.
                        state.layoutMode == LayoutMode.SYMBOLS_SHIFTED -> SymbolsShiftedFillRow
                        // Opt-in: holding shift on the letters layer turns the
                        // digits into the symbol layer's bracket/math fill row,
                        // so symbols are reachable without switching layers.
                        state.layoutMode == LayoutMode.LETTERS &&
                            state.shiftState != ShiftState.OFF && shiftSymbols -> SymbolsFillRow
                        // Borrowed from the symbol layer so the digits carry
                        // their fraction and superscript long-presses here too.
                        else -> Layouts.SYMBOLS.rows.first()
                    }
                }
                KeyRow(
                    keys = extraRow,
                    gridWeight = extraRow.size.toFloat(),
                    split = split,
                    splitGapPercent = splitGapPercent,
                    keyHeightDp = state.settings.numberRowHeightDp,
                    state = state,
                    onKey = stampedOnKey,
                    onText = stampedOnText,
                    onCursorMove = onCursorMove,
                    onLayoutSelect = onLayoutSelect,
                    onLetterPositioned = onLetterPositioned,
                    smartResolve = smartResolve,
                )
            }
            // Layers shorter than the reserved span pad at the top rather than
            // stretching. The bottom row — space and enter — has to stay under
            // the thumb at the same height on every layer, and growing the keys
            // to fill instead would change a target size the user has learned,
            // mid-sentence. Without this the panels, sized to rowSpan, would be
            // taller than the keys.
            val padRows = state.layouts.rowSpan - bodyRows.size
            if (padRows > 0) {
                Spacer(
                    modifier = Modifier.height(
                        (state.settings.keyHeightDp.dp + keyGapV(state.settings) * 2) * padRows,
                    ),
                )
            }
            bodyRows.forEachIndexed { index, row ->
                // Per-row height multiplier from the layout, if any. Rounded to
                // whole dp so the rendered height matches keyRowsHeight exactly
                // (which sums the same rounded values).
                val rowHeightDp = rowScaledKeyHeight(
                    state.settings.keyHeightDp, layout.rowHeights?.getOrNull(index),
                )
                KeyRow(
                    keys = row,
                    gridWeight = gridWeight,
                    split = split,
                    splitGapPercent = splitGapPercent,
                    keyHeightDp = rowHeightDp,
                    state = state,
                    onKey = stampedOnKey,
                    onText = stampedOnText,
                    onCursorMove = onCursorMove,
                    onLayoutSelect = onLayoutSelect,
                    onLetterPositioned = onLetterPositioned,
                    onSpacePositioned = onSpacePositioned,
                    smartResolve = smartResolve,
                )
            }
        }

        if (trail.size > 1) {
            Canvas(modifier = Modifier.matchParentSize()) {
                // Comet-style trail: each segment fades and thins with age,
                // so the tail dissolves behind the finger instead of leaving
                // the whole path on screen. Head width, life span and peak
                // opacity all come from the gesture settings.
                val headWidth = trailHeadWidth.dp.toPx()
                val tailWidth = headWidth * 0.3f
                for (i in 1 until trail.size) {
                    val point = trail[i]
                    val life =
                        (1f - (trailNow - point.timeMs) / trailMs.toFloat()).coerceIn(0f, 1f)
                    if (life == 0f) continue
                    drawLine(
                        color = trailColor.copy(alpha = trailOpacity * life),
                        start = trail[i - 1].position,
                        end = point.position,
                        strokeWidth = tailWidth + (headWidth - tailWidth) * life,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        // Handwriting ink drawn over the keys: the finished strokes still
        // waiting to be recognized (service state) plus the one under the
        // finger. Unlike the glide trail these stay put until they commit,
        // so the user can see the whole letter or word taking shape.
        if (handwriteSwipe && state.handwriting.status == HandwritingStatus.READY) {
            val inkColor = LocalKbTheme.current.accent
            Canvas(modifier = Modifier.matchParentSize()) {
                val inkWidth = 5.dp.toPx()
                for (stroke in state.handwriting.strokes) {
                    val pts = stroke.points
                    for (i in 1 until pts.size) {
                        drawLine(
                            color = inkColor,
                            start = Offset(pts[i - 1].x, pts[i - 1].y),
                            end = Offset(pts[i].x, pts[i].y),
                            strokeWidth = inkWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                for (i in 1 until hwActiveStroke.size) {
                    drawLine(
                        color = inkColor,
                        start = hwActiveStroke[i - 1],
                        end = hwActiveStroke[i],
                        strokeWidth = inkWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        // Floating preview of the word the swipe currently decodes to,
        // hovering above the finger like a key popup.
        val glideWord = state.glideWord
        if (glideWord != null && trail.isNotEmpty() && !trailReleased) {
            val theme = LocalKbTheme.current
            val display = when (state.shiftState) {
                ShiftState.CAPS_LOCK -> glideWord.uppercase()
                ShiftState.ON -> glideWord.replaceFirstChar { it.uppercase() }
                ShiftState.OFF -> glideWord
            }
            var pillSize by remember { mutableStateOf(IntSize.Zero) }
            val head = trail.last().position
            val gapPx = with(LocalDensity.current) { 56.dp.roundToPx() }
            Surface(
                modifier = Modifier
                    .offset {
                        val x = (head.x - pillSize.width / 2f).toInt()
                            .coerceIn(0, (boxSize.width - pillSize.width).coerceAtLeast(0))
                        val y = (head.y - gapPx - pillSize.height).toInt().coerceAtLeast(0)
                        IntOffset(x, y)
                    }
                    .onGloballyPositioned { pillSize = it.size },
                color = theme.popup,
                contentColor = theme.popupText,
                shape = RoundedCornerShape(theme.popupRadiusDp.dp),
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = display,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One row of keys. In split mode the row is cut near its midpoint and the
 * halves are pushed apart by a center gap sized as a percentage of the
 * keyboard width; a spacebar straddling the cut is divided between the halves.
 */
@Composable
private fun KeyRow(
    keys: List<Key>,
    gridWeight: Float,
    split: Boolean,
    splitGapPercent: Int,
    keyHeightDp: Int,
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLayoutSelect: (String) -> Unit,
    onLetterPositioned: (Char, LayoutCoordinates) -> Unit,
    onSpacePositioned: (LayoutCoordinates) -> Unit = {},
    smartResolve: (Key, PointerId) -> Key = { k, _ -> k },
) {
    val sidePad = sidePadFor(keys, gridWeight)
    Row {
        if (sidePad > 0.01f) Spacer(modifier = Modifier.weight(sidePad))
        if (split) {
            val (left, right) = remember(keys) { splitKeys(keys) }
            for (key in left) {
                KeyCell(key, keyHeightDp, state, onKey, onText, onCursorMove, onLayoutSelect, onLetterPositioned, onSpacePositioned, smartResolve)
            }
            Spacer(modifier = Modifier.weight(gridWeight * splitGapPercent / 100f))
            for (key in right) {
                KeyCell(key, keyHeightDp, state, onKey, onText, onCursorMove, onLayoutSelect, onLetterPositioned, onSpacePositioned, smartResolve)
            }
        } else {
            for (key in keys) {
                KeyCell(key, keyHeightDp, state, onKey, onText, onCursorMove, onLayoutSelect, onLetterPositioned, onSpacePositioned, smartResolve)
            }
        }
        if (sidePad > 0.01f) Spacer(modifier = Modifier.weight(sidePad))
    }
}

@Composable
private fun RowScope.KeyCell(
    key: Key,
    keyHeightDp: Int,
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit,
    onLayoutSelect: (String) -> Unit,
    onLetterPositioned: (Char, LayoutCoordinates) -> Unit,
    onSpacePositioned: (LayoutCoordinates) -> Unit = {},
    smartResolve: (Key, PointerId) -> Key = { k, _ -> k },
) {
    val letter = key.label.singleOrNull()?.takeIf {
        key.action == KeyAction.Text && it.isLetter()
    }
    KeyButton(
        key = key,
        state = state,
        modifier = if (letter != null) {
            Modifier
                .weight(key.width)
                .onGloballyPositioned { onLetterPositioned(letter.lowercaseChar(), it) }
        } else if (key.action == KeyAction.Space) {
            Modifier
                .weight(key.width)
                .onGloballyPositioned { onSpacePositioned(it) }
        } else {
            Modifier.weight(key.width)
        },
        heightDp = keyHeightDp,
        onKey = onKey,
        onText = onText,
        onCursorMove = onCursorMove,
        onLayoutSelect = onLayoutSelect,
        smartResolve = smartResolve,
    )
}

/**
 * Cuts a row for split mode. A spacebar spanning the midpoint is divided
 * into a half per side (the left half loses its label so the language name
 * is not shown twice); otherwise the cut lands on the key boundary nearest
 * the midpoint, with ties going right so QWERTY splits asdfg | hjkl.
 */
internal fun splitKeys(keys: List<Key>): Pair<List<Key>, List<Key>> {
    // A custom layout can hand us an empty or one-key row: the cut search below
    // starts at index 1, and subList would throw on an empty one — a deleted row
    // would take the whole keyboard down in split mode.
    if (keys.size < 2) return keys to emptyList()
    val boundaries = FloatArray(keys.size + 1)
    for (i in keys.indices) boundaries[i + 1] = boundaries[i] + keys[i].width
    val mid = boundaries[keys.size] / 2f
    for (i in keys.indices) {
        if (keys[i].action == KeyAction.Space &&
            boundaries[i] < mid - 0.01f && boundaries[i + 1] > mid + 0.01f
        ) {
            val left = keys.subList(0, i) + keys[i].copy(label = "", width = mid - boundaries[i])
            val right = listOf(keys[i].copy(width = boundaries[i + 1] - mid)) +
                keys.subList(i + 1, keys.size)
            return left to right
        }
    }
    var cut = 1
    for (b in 2 until keys.size) {
        if (abs(boundaries[b] - mid) <= abs(boundaries[cut] - mid) + 0.001f) cut = b
    }
    return keys.subList(0, cut) to keys.subList(cut, keys.size)
}

/** True when [position] falls within roughly one key of a tracked letter key. */
private fun nearLetterKey(position: Offset, centers: Map<Char, Offset>, keyWidth: Float): Boolean =
    centers.values.any { (it - position).getDistance() < keyWidth }

/** How hard a likely next letter pulls a boundary tap. Higher = wider steal. */
private const val SMART_HIT_STRENGTH = 0.5f

/** A favoured letter never claims a tap more than this many key-widths away. */
private const val SMART_HIT_MAX_REACH = 1.3f

/**
 * Smart key-hit detection. Given a touch [pos], the tracked letter [centers]
 * and the live next-letter [bias] (0..1 per letter), returns the letter whose
 * hitbox should claim this touch, or null to leave the plain-nearest key alone.
 *
 * Each centre's effective distance is shortened in proportion to how likely
 * that letter is next, so a likely neighbour can win a touch that landed just
 * inside the nominal key. The shortening is a fixed fraction of distance, so it
 * only ever flips the outcome near a shared edge — a press deep inside a key
 * stays with that key — and a favoured letter out of reach is ignored outright.
 */
private fun smartHitTarget(
    pos: Offset,
    centers: Map<Char, Offset>,
    keyWidth: Float,
    bias: Map<Char, Float>,
): Char? {
    if (keyWidth <= 0f || centers.isEmpty() || bias.isEmpty()) return null
    var nominal: Char? = null
    var nominalDist = Float.MAX_VALUE
    var best: Char? = null
    var bestScore = Float.MAX_VALUE
    for ((ch, center) in centers) {
        val d = (center - pos).getDistance()
        if (d < nominalDist) {
            nominalDist = d
            nominal = ch
        }
        val score = d / (1f + SMART_HIT_STRENGTH * (bias[ch] ?: 0f))
        if (score < bestScore) {
            bestScore = score
            best = ch
        }
    }
    // The plain-nearest key already wins: nothing to remap.
    if (best == null || best == nominal) return null
    // Never yank a tap onto a key the finger is nowhere near.
    val target = centers[best] ?: return null
    if ((target - pos).getDistance() > keyWidth * SMART_HIT_MAX_REACH) return null
    return best
}

internal fun currentLayout(state: KeyboardUiState): KeyboardLayout {
    if (numericPadActive(state)) {
        state.layouts.numeric?.let { return it }
    }
    val base = when (state.layoutMode) {
        LayoutMode.SYMBOLS -> state.layouts.symbols
        LayoutMode.SYMBOLS_SHIFTED -> state.layouts.symbolsShifted
        // Falls back to the letters when the layout has no Fn layer: a stored
        // Fn key can outlive the layer it points at, and onFn already refuses to
        // switch, so this only ever fires on a state built out of order.
        LayoutMode.FN -> state.layouts.fn ?: state.layouts.letters
        LayoutMode.LETTERS -> state.layouts.letters
    }
    // Email and URI fields keep the letter layouts but trade the bottom-row
    // comma — punctuation neither field uses — for the character they are
    // full of, and put domain endings on the period key's long press. Both
    // are otherwise a trip through the symbols layer for every address.
    val lettersLayer = state.layoutMode == LayoutMode.LETTERS
    val fieldKey = when {
        !lettersLayer -> null
        state.fieldKind == FieldKind.EMAIL -> Key("@")
        state.fieldKind == FieldKind.URI -> Key("/", longPress = listOf("?", "#", "&", "="))
        else -> null
    }
    val domainAlternates = when {
        !lettersLayer -> emptyList()
        state.fieldKind == FieldKind.EMAIL -> listOf(".com", ".net", ".org", ".edu", ".co")
        state.fieldKind == FieldKind.URI ->
            listOf(".com", ".org", ".net", "www.", "https://", "/")
        else -> emptyList()
    }
    // Optional Gboard-style emoji key: the letter layouts' comma key becomes
    // an emoji-panel key, with comma demoted to its long-press alternates.
    val commaAsEmoji = state.settings.commaAsEmoji && state.layoutMode == LayoutMode.LETTERS
    // 🌐 → emoji key: language switching lives on spacebar swipes instead.
    val globeAsEmoji = state.settings.globeAsEmoji
    // With the dedicated number row on, the digits duplicated on the top-row
    // letters' long press are redundant — drop them so those keys go straight
    // to their accents (or lose their popup entirely).
    val stripDigits = state.settings.numberRow && state.layoutMode == LayoutMode.LETTERS
    // A/C/V/X/Z/Y clipboard/undo/redo shortcuts only make sense on Latin letter keys.
    val clipboardKeys: Map<String, ClipboardKeyAction> =
        if (state.layoutMode == LayoutMode.LETTERS && !state.composer.isClusterShaping) {
            val longPress = state.settings.longPressLetterActions
            buildMap {
                if (longPress.selectAll) put("a", ClipboardKeyAction.SELECT_ALL)
                if (longPress.copy) put("c", ClipboardKeyAction.COPY)
                if (longPress.paste) put("v", ClipboardKeyAction.PASTE)
                if (longPress.cut) put("x", ClipboardKeyAction.CUT)
                if (longPress.undo) put("z", ClipboardKeyAction.UNDO)
                if (longPress.redo) put("y", ClipboardKeyAction.REDO)
            }
        } else {
            emptyMap()
        }
    if (!commaAsEmoji && !globeAsEmoji && !stripDigits && clipboardKeys.isEmpty() &&
        fieldKey == null && domainAlternates.isEmpty()
    ) {
        return base
    }
    val bottom = base.rows.lastIndex
    // copy rather than KeyboardLayout(name, rows): a positional rebuild
    // silently drops any field later added to the class.
    return base.copy(
        rows = base.rows.mapIndexed { rowIndex, row ->
            row.map { key ->
                val role = key.roleIn(rowIndex, bottom)
                var mapped = when {
                    // Field adaptation outranks the emoji-key preference: an
                    // email box needs its @ more than a shortcut to emoji.
                    fieldKey != null && role == KeyRole.Comma -> fieldKey
                    domainAlternates.isNotEmpty() && role == KeyRole.Period ->
                        key.copy(longPress = domainAlternates + key.longPress)
                    commaAsEmoji && role == KeyRole.Comma ->
                        // copy, not a fresh Key: building one from scratch here
                        // discarded a custom width, so the bottom row jumped
                        // whenever the preference was on.
                        key.copy(
                            action = KeyAction.Emoji,
                            longPress = listOf(key.output ?: key.label) + key.longPress,
                        )
                    globeAsEmoji && key.action == KeyAction.LanguageSwitch ->
                        key.copy(action = KeyAction.Emoji)
                    else -> key
                }
                if (stripDigits && mapped.longPress.any { it.isSingleDigit() }) {
                    mapped = mapped.copy(longPress = mapped.longPress.filterNot { it.isSingleDigit() })
                }
                // Keyed on what the key types, not what it is labelled: a layout
                // that shows "A" and outputs "a" was silently skipped. A value
                // already set on the key wins, so a layout can put a clipboard
                // shortcut somewhere other than a/c/v/x.
                if (mapped.action == KeyAction.Text && mapped.clipboardAction == null) {
                    clipboardKeys[mapped.output ?: mapped.label]?.let {
                        mapped = mapped.copy(clipboardAction = it)
                    }
                }
                mapped
            }
        },
    )
}

/**
 * What a key means to field adaptation: its explicit tag, or the old label match
 * as a fallback.
 *
 * The fallback stays rather than being dropped so layouts written before roles
 * existed — and anything imported from a build that predates them — keep their
 * email and URI adaptation instead of silently losing it. It remains scoped to
 * the bottom row for the reason it always was: Dvorak's *top* row has real "."
 * and "," letter keys, which must not be rewritten into an @ key.
 */
internal fun Key.roleIn(rowIndex: Int, lastRow: Int): KeyRole? = when {
    role != null -> role
    action != KeyAction.Text || rowIndex != lastRow -> null
    label == "," -> KeyRole.Comma
    label == "." -> KeyRole.Period
    else -> null
}

/**
 * Whether the focused field's keypad should be showing.
 *
 * A numeric field gets its keypad whatever the layout mode says — the pads
 * have no ?123 key to leave by, so the letter/symbol cycle does not apply.
 * The exception is anything that reroutes keystrokes away from the editor:
 * emoji, media and dictionary search boxes and the typing test all need
 * letters, and a digits-only pad would leave them impossible to type in.
 */
private fun numericPadActive(state: KeyboardUiState): Boolean =
    state.fieldKind.isNumericPad &&
        !state.emojiSearchActive &&
        !state.dictionarySearchActive &&
        !(state.mediaSearchActive && state.panel.hasMediaSearch) &&
        !state.typingTestActive


private fun String.isSingleDigit(): Boolean = length == 1 && this[0].isDigit()

/**
 * Places a popup centered above its anchor with a clear gap, so the
 * character bubble and long-press alternates are not hidden under the
 * pressing finger.
 */
private class AboveAnchorPopupPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun rememberAboveAnchorPopup(): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density) {
        AboveAnchorPopupPositionProvider(with(density) { 10.dp.roundToPx() })
    }
}

/**
 * Places the popup so its bottom edge lines up with the pressed key's
 * bottom, growing upward from the key itself — the tall stock-keyboard
 * style where the bubble visually replaces the key.
 */
private object OnKeyPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.bottom - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** Visual gap between keys, provided as padding inside each touch cell. */
private val KeyGapHorizontal = 2.5.dp
private val KeyGapVertical = 4.dp

/**
 * The gaps above, scaled by the user's key-spacing setting. Every gap-consuming
 * site reads these so the touch-cell padding and the height math in
 * [keyRowsHeight] scale together — the keyboard's height and the panels that
 * mirror it stay in step when the spacing changes.
 */
private fun keyGapH(settings: KeyboardSettings): Dp = KeyGapHorizontal * settings.keyGapScale
private fun keyGapV(settings: KeyboardSettings): Dp = KeyGapVertical * settings.keyGapScale

/**
 * Peak extra glide-start slop applied the instant after a tap, on top of the
 * user's start-distance multiplier, decaying to 0 across the post-typing
 * cooldown window. 2.5 means a glide starting right after a keypress must travel
 * 3.5× as far (1 + 2.5) as it normally would before it is read as a swipe-word.
 */
private const val POST_TYPE_SLOP_BOOST = 2.5f

/** Vertical padding of the [KeyRows] column, mirrored into [keyRowsHeight]. */
private val KeyRowsPadVertical = 2.dp

/**
 * Horizontal padding of the [KeyRows] column. Named because the gesture
 * decoder's key-width derivation has to subtract it to get the real cell width.
 */
private val KeyRowsPadHorizontal = 1.5.dp

/**
 * Default height of [TopBar] (suggestions/toolbar row), and the fallback when
 * no settings are on hand. The live height is [topBarHeight], which honours
 * the user's `toolbarHeightDp` — prefer it wherever settings are available so
 * a taller bar and its full-bleed accounting stay in step.
 */
internal val TopBarHeight = 44.dp

/** Downward travel on the strip that counts as a "hide the keyboard" flick. */
private val ToolbarSwipeHideThreshold = 48.dp

/**
 * Whether the pinned tools should read right-to-left: the setting is on and
 * the active layout's script runs RTL. Both the display order and the drag
 * hit-testing key off this, so they stay in step during a reorder.
 */
private fun toolbarReadsRtl(state: KeyboardUiState): Boolean =
    state.settings.toolbarBehavior.reverseForRtl && state.script.direction == TextDirection.RTL

/**
 * The top strip's height for the current settings (see [TopBarHeight]).
 * Collapses to zero when the toolbar is disabled, so every height-accounting
 * caller (full-bleed absorption, emoji-search sizing) drops the strip with it.
 */
internal fun topBarHeight(settings: KeyboardSettings): Dp =
    if (settings.toolbarBehavior.enabled) settings.toolbarHeightDp.dp else 0.dp

/**
 * Exact height of [KeyRows]: [LayoutSet.rowSpan] key rows (each key height plus
 * its vertical gaps), the optional number row, and the column padding. Every
 * panel sizes itself with this so opening a tool or switching layers never
 * changes the keyboard's height under the user's fingers.
 *
 * Takes the whole state rather than the settings because the row count is a
 * property of the active layout set — a custom layout may be three rows or six.
 * Threading the resolved [LayoutSet] through as its own parameter was the
 * alternative and was rejected: every call site already has the state in scope,
 * so it would have touched every panel signature and bought nothing.
 */
internal fun keyRowsHeight(state: KeyboardUiState): Dp {
    val settings = state.settings
    val rowSpan = state.layouts.rowSpan
    val layout = currentLayout(state)
    val rowHeights = layout.rowHeights
    var height = if (rowHeights == null) {
        (settings.keyHeightDp.dp + keyGapV(settings) * 2) * rowSpan
    } else {
        // A layout with per-row heights: sum its body rows at their scaled
        // heights and pad the rest at the base height, mirroring the render
        // loop key for key so the reserved height matches what is drawn.
        val bodyRowCount = layout.rows.size.coerceAtMost(rowSpan)
        var sum = 0.dp
        for (i in 0 until bodyRowCount) {
            sum += rowScaledKeyHeight(settings.keyHeightDp, rowHeights.getOrNull(i)).dp +
                keyGapV(settings) * 2
        }
        sum + (settings.keyHeightDp.dp + keyGapV(settings) * 2) * (rowSpan - bodyRowCount)
    }
    height += KeyRowsPadVertical * 2
    if (settings.numberRow) {
        height += settings.numberRowHeightDp.dp + keyGapV(settings) * 2
    }
    return height
}

/**
 * A row's key height in dp after applying its optional [scale] multiplier,
 * clamped to a sane range and rounded to whole dp. A null or 1.0 scale (the
 * common case) returns [baseKeyHeightDp] untouched. Shared by the [KeyRows]
 * render loop and [keyRowsHeight] so the reserved and drawn heights agree to
 * the pixel.
 */
private fun rowScaledKeyHeight(baseKeyHeightDp: Int, scale: Float?): Int =
    if (scale == null || scale == 1f) {
        baseKeyHeightDp
    } else {
        (baseKeyHeightDp * scale.coerceIn(0.4f, 2.5f)).roundToInt().coerceAtLeast(1)
    }

@Composable
private fun KeyButton(
    key: Key,
    state: KeyboardUiState,
    modifier: Modifier,
    onKey: (Key) -> Unit,
    onText: (String) -> Unit,
    onCursorMove: (Int) -> Unit = {},
    onLayoutSelect: (String) -> Unit = {},
    heightDp: Int? = null,
    smartResolve: (Key, PointerId) -> Key = { k, _ -> k },
) {
    var pressed by remember { mutableStateOf(false) }
    var showAlternates by remember { mutableStateOf(false) }
    // Full tappable language list: opened by a long-press on the globe key or
    // by holding the spacebar when more than two languages are enabled (a
    // swipe through a long ring is tedious). Independent of languagePreview.
    var showLanguagePicker by remember { mutableStateOf(false) }
    // Language the spacebar swipe currently has selected, shown in a tooltip
    // popup above the spacebar while the finger is still down.
    var languagePreview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val onKeyPress = LocalKeyPressFeedback.current
    val onKeySound = LocalKeySound.current
    val onClipboardKey = LocalClipboardKeyAction.current
    val canDelete = LocalCanDelete.current
    val onDeleteWord = LocalDeleteWord.current
    val onCursorMoveVertical = LocalCursorMoveVertical.current
    val onHideKeyboard = LocalHideKeyboard.current

    // The preview bubble outlives the physical press by up to the minimum
    // popup duration, so a fast tap still shows a readable bubble instead
    // of a single-frame flash.
    var previewVisible by remember { mutableStateOf(false) }
    var previewShownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pressed) {
        if (pressed) {
            previewShownAt = SystemClock.uptimeMillis()
            previewVisible = true
        } else if (previewVisible) {
            val remaining = settings.popup.minDurationMs -
                (SystemClock.uptimeMillis() - previewShownAt)
            if (remaining > 0) delay(remaining)
            previewVisible = false
        }
    }
    // Absolute ceiling on the bubble's life, measured from when it appeared
    // and re-armed on every press (previewShownAt changes). Kept in its own
    // effect rather than the pressed branch above: keying it on `pressed`
    // meant a release cancelled the cap before it could fire, so it only ever
    // ran when the release never arrived — the rare case. The common strand is
    // a release delivered late under commit lag (a new line inserting is the
    // usual cause), which routes through the else branch and never hit the
    // old cap. Here the cap fires no matter how the press ends, so a dropped
    // or late release can't strand the bubble and the Maximum popup duration
    // slider has real effect.
    LaunchedEffect(previewShownAt) {
        if (previewShownAt == 0L) return@LaunchedEffect
        val cap = settings.popup.maxDurationMs -
            (SystemClock.uptimeMillis() - previewShownAt)
        if (cap > 0) delay(cap.toLong())
        previewVisible = false
    }

    // Samsung-style contrast: letter keys clearly lighter than the board,
    // modifier keys a shade darker than the letters.
    val kb = LocalKbTheme.current
    // A latched modifier has to look held: it changes what the *next* key
    // does, so with no visible state the user finds out by pressing one.
    val latch = (key.action as? KeyAction.Mod)?.let { state.modifiers[it.key] }
    val background = when {
        pressed -> kb.pressedKey
        latch == ModifierState.LOCKED -> kb.accent
        latch == ModifierState.ARMED -> kb.pressedKey
        key.action == KeyAction.Enter -> kb.enterKey
        key.action != KeyAction.Text -> kb.modifierKey
        else -> kb.key
    }
    val contentColor = when {
        key.action == KeyAction.Enter && !pressed -> kb.enterKeyText
        key.action != KeyAction.Text -> kb.modifierKeyText
        else -> kb.keyText
    }
    val keyShape = kb.keyShape()

    // Outer box = full grid cell and the touch target; inner box = the
    // visible key, inset by the gap. Presses in the gap between keys land
    // on whichever cell they fall in, so there are no dead zones.
    val density = LocalDensity.current
    var keyWidthPx by remember { mutableIntStateOf(0) }

    // Tremor filter: drop a second contact on the same key that lands
    // within the debounce window. Scoped per key, so alternating keys
    // (typing "aa" vs "ab") are never affected — only a bouncing repeat is.
    var lastAcceptedAt by remember { mutableLongStateOf(0L) }
    val debounced: (Key) -> Unit = remember(onKey, settings.keyDebounceMs) {
        { pressedKey ->
            val now = SystemClock.uptimeMillis()
            if (settings.keyDebounceMs <= 0 || now - lastAcceptedAt >= settings.keyDebounceMs) {
                lastAcceptedAt = now
                onKey(pressedKey)
            }
        }
    }

    // Under an explore-by-touch service the accessibility framework owns the
    // touch stream, so the custom press/long-press/swipe detector never sees
    // a coherent gesture. Hand the key over to semantics instead: TalkBack
    // announces on hover and commits on the activation tap.
    val touchExploration = LocalTouchExploration.current
    val screenReaderKeys = settings.screenReaderMode != ScreenReaderMode.OFF
    val semanticsDriven =
        touchExploration && settings.screenReaderMode == ScreenReaderMode.EXPLORE
    val label = spokenLabel(key, state)

    Box(
        modifier = modifier
            .height((heightDp ?: settings.keyHeightDp).dp + keyGapV(settings) * 2)
            .then(
                if (screenReaderKeys) {
                    Modifier.semantics {
                        contentDescription = label
                        role = Role.Button
                        if (semanticsDriven) {
                            onClick(label = "Type $label") { debounced(key); true }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (semanticsDriven) Modifier
                else Modifier.pointerInputKey(
                    key, settings.longPressDelayMs, settings.keyRepeatIntervalMs,
                    spaceShortSwipe = settings.spaceShortSwipe,
                    spaceLongSwipe = settings.spaceLongSwipe,
                    enabledLayoutIds = settings.enabledLayoutIds.ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) },
                    currentLayoutId = state.layoutId,
                    setPressed = { pressed = it },
                    onKeyPress = onKeyPress,
                    onKeySound = onKeySound,
                    vibrateOnSpace = settings.feedback.vibrateOnSpace,
                    vibrateOnDeleteSwipe = settings.feedback.vibrateOnDeleteSwipe,
                    vibrateOnRepeat = settings.feedback.vibrateOnRepeat,
                    hapticOnLongPress = settings.hapticOnLongPress,
                    hapticOnLongPressRelease = settings.hapticOnLongPressRelease,
                    openAlternates = { showAlternates = true },
                    onKey = debounced,
                    // Repeat ticks bypass the debounce (raw onKey), taps don't.
                    onKeyRepeat = onKey,
                    onClipboardKey = onClipboardKey,
                    onCursorMove = onCursorMove,
                    onCursorMoveVertical = onCursorMoveVertical,
                    onHideKeyboard = onHideKeyboard,
                    spaceCursor2d = settings.layoutBehavior.spaceCursor2d,
                    spaceSwipeDownHide = settings.layoutBehavior.spaceSwipeDownHide,
                    symbolsLongPressNumpad = settings.layoutBehavior.symbolsLongPressNumpad,
                    onLayoutSelect = onLayoutSelect,
                    openLanguagePicker = { showLanguagePicker = true },
                    setLanguagePreview = { languagePreview = it },
                    canDelete = canDelete,
                    onDeleteWord = onDeleteWord,
                    backspaceSwipeDelete = settings.backspaceSwipeDelete,
                    scope = scope,
                    smartResolve = smartResolve,
                )
            )
            .padding(horizontal = keyGapH(settings), vertical = keyGapV(settings))
            .background(background, keyShape)
            .then(
                // Sheen over letter keys only; pressed/enter/modifier states
                // keep their solid colors so state changes stay legible.
                if (kb.keyGradient != null && !pressed && key.action == KeyAction.Text) {
                    Modifier.background(kb.keyGradient.brush(), keyShape)
                } else {
                    Modifier
                }
            )
            .then(
                if (kb.keyBorder != null && kb.keyBorderWidthDp > 0f) {
                    Modifier.border(kb.keyBorderWidthDp.dp, kb.keyBorder, keyShape)
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { keyWidthPx = it.size.width },
        contentAlignment = Alignment.Center,
    ) {
        KeyContent(key, state, contentColor)
        val popupPosition = rememberAboveAnchorPopup()

        if (showAlternates && key.longPress.isNotEmpty()) {
            Popup(
                popupPositionProvider = popupPosition,
                onDismissRequest = { showAlternates = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (alternate in key.longPress) {
                            Text(
                                text = alternate,
                                modifier = Modifier
                                    .clickable {
                                        showAlternates = false
                                        onText(alternate)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                fontSize = (18 * settings.popup.fontScale).sp,
                                color = kb.popupText,
                            )
                        }
                    }
                }
            }
        }

        // Key preview bubble while pressed. In on-key mode the bubble
        // grows upward from the pressed key itself (stock-keyboard style,
        // key-wide with a large label near the top, clear of the finger);
        // otherwise it floats above the fingertip.
        if (previewVisible && settings.popup.enabled && key.action == KeyAction.Text && !showAlternates) {
            val onKeyStyle = settings.popup.onKey
            Popup(
                popupPositionProvider = if (onKeyStyle) OnKeyPopupPositionProvider else popupPosition,
                properties = PreviewPopupProperties,
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .height(settings.popup.heightDp.dp)
                            .widthIn(min = if (onKeyStyle) with(density) { keyWidthPx.toDp() } + 8.dp else 0.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = if (onKeyStyle) Alignment.TopCenter else Alignment.Center,
                    ) {
                        Text(
                            text = displayLabel(key, state),
                            modifier = if (onKeyStyle) Modifier.padding(top = 8.dp) else Modifier,
                            fontSize = ((if (onKeyStyle) 34 else 22) * settings.popup.fontScale).sp,
                            color = kb.popupText,
                        )
                    }
                }
            }
        }

        // Tooltip above the spacebar while a swipe is cycling languages:
        // every enabled mode in a row, the live selection highlighted.
        languagePreview?.let { previewMode ->
            val enabledLayoutIds = settings.enabledLayoutIds.ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) }
            Popup(
                popupPositionProvider = popupPosition,
                properties = PreviewPopupProperties,
            ) {
                Surface(
                    shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                    color = kb.popup,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (layoutId in enabledLayoutIds) {
                            val selected = layoutId == previewMode
                            Text(
                                text = layoutSwitchLabel(
                                    layoutId,
                                    enabledLayoutIds,
                                    settings.customLayouts,
                                    settings.layoutBehavior.spacebarDisplay,
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .background(
                                        if (selected) kb.pressedKey else Color.Transparent,
                                        RoundedCornerShape(kb.popupRadiusDp.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = (14 * settings.popup.fontScale).sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) kb.popupText else kb.popupText.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }

        if (showLanguagePicker) {
            LanguagePickerPopup(
                popupPosition = popupPosition,
                enabledLayoutIds = settings.enabledLayoutIds.ifEmpty { listOf(BuiltInLayouts.DEFAULT_ID) },
                currentLayoutId = state.layoutId,
                customLayouts = settings.customLayouts,
                displayMode = settings.layoutBehavior.spacebarDisplay,
                onPick = {
                    showLanguagePicker = false
                    if (it != state.layoutId) onLayoutSelect(it)
                },
                onDismiss = { showLanguagePicker = false },
            )
        }
    }
}

/**
 * A tappable list of every enabled layout, current one highlighted. Opened by
 * a long-press on the globe key, or a spacebar hold when more than four layouts
 * are enabled or the language swipe is off; picking one switches to it. Rows
 * read like the spacebar (language, with the layout in parentheses when a
 * language has several enabled layouts). Non-focusable like the other key
 * popups so it never steals the edited field's input connection.
 */
@Composable
private fun LanguagePickerPopup(
    popupPosition: PopupPositionProvider,
    enabledLayoutIds: List<String>,
    currentLayoutId: String,
    customLayouts: List<LayoutSpec>,
    displayMode: SpacebarDisplay,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Popup(
        popupPositionProvider = popupPosition,
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(kb.popupRadiusDp.dp),
            color = kb.popup,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 240.dp)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                for (layoutId in enabledLayoutIds) {
                    val selected = layoutId == currentLayoutId
                    Text(
                        text = layoutSwitchLabel(layoutId, enabledLayoutIds, customLayouts, displayMode),
                        color = if (selected) kb.accent else kb.popupText,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) kb.pressedKey else Color.Transparent)
                            .clickable { onPick(layoutId) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Label for an enabled layout in the spacebar language switcher (tooltip and
 * picker). Follows the same rule as the spacebar label: the language name, the
 * layout name, or "Language (Layout)" — and always the combined form when more
 * than one enabled layout shares the language, so the rows stay distinct.
 */
private fun layoutSwitchLabel(
    layoutId: String,
    enabledLayoutIds: List<String>,
    customLayouts: List<LayoutSpec>,
    mode: SpacebarDisplay,
): String {
    val spec = resolveLayout(customLayouts, layoutId)
    val lang = spec.language().displayName
    val layout = spec.name
    val sameLangCount = enabledLayoutIds.count {
        resolveLayout(customLayouts, it).langId == spec.langId
    }
    return when {
        mode == SpacebarDisplay.LAYOUT -> layout
        mode == SpacebarDisplay.BOTH || sameLangCount > 1 -> "$lang ($layout)"
        else -> lang
    }
}

@Composable
private fun KeyContent(key: Key, state: KeyboardUiState, contentColor: Color) {
    val fontScale = state.settings.fontScale
    when (key.action) {
        KeyAction.Shift -> Icon(
            when (state.shiftState) {
                ShiftState.CAPS_LOCK -> KeyboardIcons.ShiftLock
                ShiftState.ON -> KeyboardIcons.ShiftFilled
                ShiftState.OFF -> KeyboardIcons.Shift
            },
            contentDescription = when (state.shiftState) {
                ShiftState.CAPS_LOCK -> "Caps lock on"
                ShiftState.ON -> "Shift on"
                ShiftState.OFF -> "Shift"
            },
            tint = if (state.shiftState != ShiftState.OFF) MaterialTheme.colorScheme.primary else contentColor,
        )
        KeyAction.Delete -> Icon(
            Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = "Delete",
            tint = contentColor,
        )
        // An app-supplied actionLabel is drawn as text — that is the whole
        // point of it, and no icon can stand in for wording the app chose.
        // It is clipped to one line so a long label cannot blow up the row.
        KeyAction.Enter -> if (state.enterAction == EnterAction.CUSTOM &&
            state.enterActionLabel != null
        ) {
            Text(
                text = state.enterActionLabel,
                fontSize = (13 * fontScale).sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            Icon(
                when (state.enterAction) {
                    EnterAction.SEARCH -> Icons.Outlined.Search
                    EnterAction.SEND -> Icons.AutoMirrored.Outlined.Send
                    EnterAction.GO -> Icons.AutoMirrored.Outlined.ArrowForward
                    EnterAction.NEXT -> Icons.AutoMirrored.Outlined.KeyboardTab
                    EnterAction.PREVIOUS -> Icons.AutoMirrored.Outlined.ArrowBack
                    EnterAction.DONE -> Icons.Outlined.Check
                    // CUSTOM without a usable label falls back to a newline
                    // glyph, matching what onEnter does with a blank one.
                    EnterAction.DEFAULT, EnterAction.CUSTOM ->
                        Icons.AutoMirrored.Outlined.KeyboardReturn
                },
                contentDescription = "Enter",
                tint = contentColor,
            )
        }
        KeyAction.LanguageSwitch -> Icon(
            Icons.Outlined.Language,
            contentDescription = "Switch language",
            tint = contentColor,
        )
        KeyAction.Emoji -> Icon(
            Icons.Outlined.EmojiEmotions,
            contentDescription = "Emoji",
            tint = contentColor,
        )
        KeyAction.Space -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Split-spacebar left halves carry an empty label: no language name.
            if (key.label.isNotEmpty()) {
                // The arrows only mean something when a swipe actually cycles
                // languages and there is more than one language to cycle.
                val swipeSwitchesLanguage =
                    state.settings.spaceShortSwipe == SpaceSwipeAction.LANGUAGE ||
                        state.settings.spaceLongSwipe == SpaceSwipeAction.LANGUAGE
                val showArrows = state.settings.spacebarLanguageArrows &&
                    swipeSwitchesLanguage && state.settings.enabledLayoutIds.size > 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showArrows) Text(
                        text = "◀",
                        fontSize = (8 * fontScale).sp,
                        color = contentColor.copy(alpha = 0.35f),
                    )
                    // A custom label replaces the language name; %s inside it
                    // puts the name back, so "— %s —" keeps tracking the mode.
                    Text(
                        text = spacebarText(state),
                        fontSize = (11 * fontScale).sp,
                        color = contentColor.copy(alpha = 0.5f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showArrows) Text(
                        text = "▶",
                        fontSize = (8 * fontScale).sp,
                        color = contentColor.copy(alpha = 0.35f),
                    )
                }
            }
        }
        else -> Box(modifier = Modifier.fillMaxSize()) {
            // A key may draw a named icon in place of its glyph; an unknown name
            // resolves to null and falls through to the text label below.
            val mainIcon = KeyIcons.byName(key.icon)
            if (mainIcon != null) {
                Icon(
                    mainIcon,
                    contentDescription = key.label.ifBlank { key.icon ?: "" },
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((22f * fontScale).dp),
                )
            } else {
                // Multi-character mode labels (?123, ABC, =\<) read as labels,
                // not characters — render them clearly smaller than letters.
                val isModeLabel = key.action != KeyAction.Text && key.label.length > 1
                Text(
                    text = displayLabel(key, state),
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = ((if (isModeLabel) 15.6f else 23f) * fontScale).sp,
                    fontWeight = if (state.settings.boldKeyLabels) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                )
            }
            // Corner hint: a named icon if the key carries one, otherwise the
            // key's first long-press alternate. Keys whose long press runs a
            // clipboard shortcut show no character hint — the popup never opens
            // there — but an explicit icon hint is an authored annotation, so it
            // stands regardless of the alternates popup.
            val hintIcon = KeyIcons.byName(key.iconHint)
            val hint = key.longPress.firstOrNull()
            when {
                state.settings.longPressHints && hintIcon != null -> Icon(
                    hintIcon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 4.dp)
                        .size((11f * fontScale * state.settings.layoutBehavior.hintFontScale).dp),
                )
                state.settings.longPressHints && key.action == KeyAction.Text &&
                    key.clipboardAction == null && hint != null -> Text(
                    text = hint,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 4.dp),
                    fontSize = (10 * fontScale * state.settings.layoutBehavior.hintFontScale).sp,
                    color = contentColor.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/**
 * Text drawn on the spacebar: the live language name, or the user's custom
 * [SettingsRepository.spacebarLabel] with %s standing in for that name.
 * Shared by the main keyboard's spacebar and the emoji panel's spacebar so
 * both read the same.
 */
private fun spacebarText(state: KeyboardUiState): String {
    val name = layoutSwitchLabel(
        state.layoutId,
        state.settings.enabledLayoutIds,
        state.settings.customLayouts,
        state.settings.layoutBehavior.spacebarDisplay,
    )
    val label = state.settings.spacebarLabel
    return if (label.isEmpty()) name else label.replace("%s", name)
}

private fun displayLabel(key: Key, state: KeyboardUiState): String {
    // Digit keys draw the chosen numeral system's glyphs (in every commit
    // scope, including display-only). The layout data stays ASCII; the swap
    // happens here at draw time. Non-digit labels pass through untouched.
    val digits = resolveNumeralDigits(state.settings.layoutBehavior.numeralSystem, state.language)
    val raw = when {
        state.shiftState != ShiftState.OFF && key.shiftLabel != null -> key.shiftLabel
        // Cased-script letter labels track the live shift state: lowercase
        // normally, uppercase while shift or caps lock is active (Latin,
        // Cyrillic, Greek — not Bengali/Arabic/Hangul, which have no case).
        state.shiftState != ShiftState.OFF && key.action == KeyAction.Text &&
            !state.composer.isClusterShaping && state.script.hasLetterCase &&
            key.label.singleOrNull()?.isLetter() == true ->
            key.label.uppercase()
        else -> key.label
    }
    // Fixed Bengali layouts: vowel keys track the cursor context — the
    // independent letter (আ, ই …) at a word start, the kar (া, ি …) after a
    // consonant, the য়-glide (য়া) after a vowel — matching what the key
    // will actually commit.
    if (state.composer.isClusterShaping && key.action == KeyAction.Text &&
        state.vowelForm != BengaliGraphemes.VowelKeyForm.KAR
    ) {
        raw.singleOrNull()
            ?.let { BengaliGraphemes.vowelKeyText(it, state.vowelForm) }
            ?.let { return it }
    }
    return mapDigits(raw, digits)
}

/**
 * The preview bubble is a separate window that lingers briefly after release
 * and, in on-key mode, covers the key itself plus part of the row above. It
 * must never intercept touches, or rapid re-taps land on the bubble window
 * and get dropped before the keyboard sees them.
 */
private val PreviewPopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
)

/**
 * Hold time after which a spacebar press (with language switching on the
 * short-swipe slot) shows the language picker: longer than any normal tap,
 * shorter than the long-press delay so the picker feels immediate.
 */
private const val SpaceHoldPickerMs = 250

/**
 * Press handling: tap commits, long-press opens alternates (or begins
 * repeating for delete), release cancels. The spacebar instead supports
 * horizontal swipes: a swipe that starts moving right away performs
 * [spaceShortSwipe], one that begins after holding the spacebar past the
 * long-press delay performs [spaceLongSwipe] — cursor movement steps the
 * text cursor, language switching cycles the enabled input modes with a
 * live tooltip preview and commits on release. Implemented with raw press
 * detection so repeat, popup and drag can share the gesture.
 */
private fun Modifier.pointerInputKey(
    key: Key,
    longPressDelayMs: Int,
    repeatIntervalMs: Int,
    spaceShortSwipe: SpaceSwipeAction,
    spaceLongSwipe: SpaceSwipeAction,
    enabledLayoutIds: List<String>,
    currentLayoutId: String,
    setPressed: (Boolean) -> Unit,
    onKeyPress: () -> Unit,
    onKeySound: () -> Unit,
    vibrateOnSpace: Boolean,
    vibrateOnDeleteSwipe: Boolean,
    vibrateOnRepeat: Boolean,
    hapticOnLongPress: Boolean,
    hapticOnLongPressRelease: Boolean,
    openAlternates: () -> Unit,
    onKey: (Key) -> Unit,
    /**
     * Un-debounced sink for auto-repeat ticks (held backspace/space). The
     * software repeat is deterministic, so it must bypass the tremor debounce
     * that [onKey] carries — otherwise repeats landing inside the debounce
     * window are dropped and the repeat rate is silently capped.
     */
    onKeyRepeat: (Key) -> Unit,
    onClipboardKey: (ClipboardKeyAction) -> Unit,
    onCursorMove: (Int) -> Unit,
    onCursorMoveVertical: (Int) -> Unit,
    onHideKeyboard: () -> Unit,
    spaceCursor2d: Boolean,
    spaceSwipeDownHide: Boolean,
    symbolsLongPressNumpad: Boolean,
    onLayoutSelect: (String) -> Unit,
    openLanguagePicker: () -> Unit,
    setLanguagePreview: (String?) -> Unit,
    canDelete: () -> Boolean,
    onDeleteWord: () -> Unit,
    backspaceSwipeDelete: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    smartResolve: (Key, PointerId) -> Key = { k, _ -> k },
): Modifier = this.then(
    if (key.action == KeyAction.Space &&
        (spaceShortSwipe != SpaceSwipeAction.NONE || spaceLongSwipe != SpaceSwipeAction.NONE ||
            spaceSwipeDownHide)
    ) {
        Modifier.pointerInput(
            key, spaceShortSwipe, spaceLongSwipe, enabledLayoutIds, currentLayoutId, longPressDelayMs,
            hapticOnLongPress, vibrateOnSpace, spaceCursor2d, spaceSwipeDownHide,
        ) {
            val slopPx = 12.dp.toPx()
            val cursorStepPx = 16.dp.toPx()
            val langStepPx = 44.dp.toPx()
            // A swipe-down must clear this before it dismisses the keyboard —
            // well past the slop so a diagonal cursor drag never trips it.
            val hideThresholdPx = 40.dp.toPx()
            // Extra travel demanded before the language list wraps around at
            // either end — the boundary acts like a detent, not a wall.
            val langWrapPx = langStepPx * 2.5f
            awaitEachGesture {
                val down = awaitFirstDown()
                setPressed(true)
                // Space press feedback: sound stays, buzz is gated on its toggle.
                if (vibrateOnSpace) onKeyPress() else onKeySound()
                // Resolved on the first movement past the slop; null until
                // then (and forever for a plain tap).
                var action: SpaceSwipeAction? = null
                var accumulated = 0f
                var lastX = down.position.x
                // Vertical accumulator for the 2-D cursor pad, and a latch set
                // once a swipe-down has dismissed the keyboard (so release does
                // not also type a space).
                var accumulatedY = 0f
                var lastY = down.position.y
                var hidden = false
                var langIndex = enabledLayoutIds.indexOf(currentLayoutId).coerceAtLeast(0)
                // With exactly two languages a swipe direction means "the
                // other language", so a single run of travel toggles at most
                // once: runDir is the direction of the current run and
                // runSwitched whether it already toggled. Continued travel in
                // the same direction must never cycle back to the language the
                // user deliberately swiped away from; only reversing direction
                // switches back.
                val twoModes = enabledLayoutIds.size == 2
                var runDir = 0
                var runSwitched = false
                // With language switching on the short-swipe slot, holding
                // the spacebar just past a normal tap shows the language
                // picker without needing any initial swipe. The action is
                // not locked in — a drag afterwards still resolves short vs
                // long normally, so a hold + swipe cursor action survives —
                // but a release with the picker up must not type a space.
                var holdPreviewShown = false
                // With more than two languages the swipe ring is long, so a
                // hold opens the full tappable picker instead of the inline
                // preview. Once open, the picker owns the selection: the rest
                // of this gesture goes inert and release types nothing.
                var pickerOpened = false
                // Arm the hold-to-switch gesture whenever there is more than one
                // layout to switch between — independent of the swipe setting, so
                // the tappable picker stays reachable even when the language swipe
                // is off. A single-layout user gets nothing (holding space would
                // otherwise show a pointless one-item picker and swallow the
                // space). The picker only opens on a still-hold (action == null);
                // a drag sets action first and still runs the swipe/cursor gesture.
                val holdOpensSwitcher = enabledLayoutIds.size > 1
                val holdJob = if (holdOpensSwitcher) {
                    scope.launch {
                        delay(minOf(longPressDelayMs, SpaceHoldPickerMs).toLong())
                        if (action == null) {
                            // List for a long ring (> 4) or when the swipe can't
                            // cycle languages; otherwise the inline swipe preview.
                            val useList = enabledLayoutIds.size > 4 ||
                                spaceShortSwipe != SpaceSwipeAction.LANGUAGE
                            if (useList) {
                                pickerOpened = true
                                openLanguagePicker()
                            } else {
                                holdPreviewShown = true
                                setLanguagePreview(enabledLayoutIds[langIndex])
                            }
                            if (hapticOnLongPress) onKeyPress()
                        }
                    }
                } else {
                    null
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    // Picker is up: consume everything, resolve nothing.
                    if (pickerOpened) { change.consume(); continue }
                    // The action this gesture would resolve to right now (short
                    // vs long by hold time). Used to decide whether the 2-D pad
                    // owns the vertical axis for this drag.
                    val candidate = if (change.uptimeMillis - down.uptimeMillis < longPressDelayMs) {
                        spaceShortSwipe
                    } else {
                        spaceLongSwipe
                    }
                    val cursorOwnsVertical = spaceCursor2d &&
                        (action == SpaceSwipeAction.CURSOR ||
                            (action == null && candidate == SpaceSwipeAction.CURSOR))
                    // Swipe straight down to dismiss the keyboard — unless the
                    // 2-D pad is claiming vertical for cursor movement.
                    if (spaceSwipeDownHide && !cursorOwnsVertical) {
                        val totalDy = change.position.y - down.position.y
                        val totalDx = change.position.x - down.position.x
                        if (totalDy > hideThresholdPx && totalDy > abs(totalDx)) {
                            change.consume()
                            hidden = true
                            onHideKeyboard()
                            break
                        }
                    }
                    if (action == null) {
                        val totalDx = change.position.x - down.position.x
                        val totalDy = change.position.y - down.position.y
                        // The pad may also resolve on a vertical drag, so a
                        // straight up/down slide starts moving the cursor; the
                        // language and horizontal-cursor paths still need
                        // horizontal slop, which keeps their flick direction sane.
                        val vertForCursor = spaceCursor2d &&
                            candidate == SpaceSwipeAction.CURSOR && abs(totalDy) > slopPx
                        if (abs(totalDx) > slopPx || vertForCursor) {
                            // Short vs long is decided by hold time, not travel
                            // distance — a fast flick covers more ground than a
                            // careful drag, so distance can't tell them apart.
                            action = candidate
                            // The hold picker was only a preview; a drag that
                            // resolves to another action dismisses it.
                            if (holdPreviewShown && action != SpaceSwipeAction.LANGUAGE) {
                                setLanguagePreview(null)
                            }
                            lastX = change.position.x
                            lastY = change.position.y
                            accumulated = 0f
                            accumulatedY = 0f
                            if (action == SpaceSwipeAction.LANGUAGE) {
                                // The movement that crossed the slop already
                                // counts: a quick flick switches one language.
                                // At a list end the flick parks on the boundary
                                // — wrapping needs a continued drag past the
                                // langWrapPx detent below. With two languages
                                // either direction simply toggles to the other.
                                val dir = if (totalDx > 0) 1 else -1
                                val flicked = if (twoModes) {
                                    1 - langIndex
                                } else {
                                    (langIndex + dir).coerceIn(0, enabledLayoutIds.size - 1)
                                }
                                if (flicked != langIndex) {
                                    langIndex = flicked
                                    onKeyPress()
                                }
                                runDir = dir
                                runSwitched = true
                                setLanguagePreview(enabledLayoutIds[langIndex])
                            }
                            change.consume()
                        }
                        continue
                    }
                    // 2-D touchpad: while sliding the cursor, a vertical drag
                    // steps the caret up and down as well. Runs alongside the
                    // horizontal step below, so a diagonal drag moves both axes.
                    if (spaceCursor2d && action == SpaceSwipeAction.CURSOR) {
                        accumulatedY += change.position.y - lastY
                        lastY = change.position.y
                        var movedV = false
                        while (accumulatedY > cursorStepPx) {
                            onCursorMoveVertical(1); accumulatedY -= cursorStepPx; movedV = true
                        }
                        while (accumulatedY < -cursorStepPx) {
                            onCursorMoveVertical(-1); accumulatedY += cursorStepPx; movedV = true
                        }
                        if (movedV) change.consume()
                    }
                    accumulated += change.position.x - lastX
                    lastX = change.position.x
                    when (action) {
                        SpaceSwipeAction.CURSOR -> {
                            var moved = false
                            while (accumulated > cursorStepPx) {
                                onCursorMove(1); accumulated -= cursorStepPx; moved = true
                            }
                            while (accumulated < -cursorStepPx) {
                                onCursorMove(-1); accumulated += cursorStepPx; moved = true
                            }
                            if (moved) change.consume()
                        }
                        SpaceSwipeAction.LANGUAGE -> {
                            if (twoModes) {
                                // One toggle per run of travel: piling on more
                                // distance in the same direction never wraps
                                // back to the starting language — the user
                                // swiped away from it on purpose. Reversing
                                // direction starts a new run and toggles back.
                                val dir = when {
                                    accumulated > langStepPx -> 1
                                    accumulated < -langStepPx -> -1
                                    else -> 0
                                }
                                if (dir != 0) {
                                    if (dir != runDir) {
                                        runDir = dir
                                        runSwitched = false
                                    }
                                    if (!runSwitched) {
                                        langIndex = 1 - langIndex
                                        runSwitched = true
                                        setLanguagePreview(enabledLayoutIds[langIndex])
                                        onKeyPress()
                                    }
                                    // Drain the overshoot so a reversal only
                                    // needs one step of travel to respond.
                                    accumulated = 0f
                                }
                                change.consume()
                                continue
                            }
                            // The list ends put up resistance instead of
                            // wrapping immediately: a wrap costs langWrapPx of
                            // travel (vs langStepPx per normal step), so the
                            // selection parks on the boundary language first
                            // and only cycles around on a deliberate pull.
                            val last = enabledLayoutIds.size - 1
                            var stepped = false
                            while (true) {
                                if (accumulated > langStepPx && langIndex < last) {
                                    langIndex++
                                    accumulated -= langStepPx
                                } else if (accumulated > langWrapPx && langIndex == last && last > 0) {
                                    langIndex = 0
                                    accumulated -= langWrapPx
                                } else if (accumulated < -langStepPx && langIndex > 0) {
                                    langIndex--
                                    accumulated += langStepPx
                                } else if (accumulated < -langWrapPx && langIndex == 0 && last > 0) {
                                    langIndex = last
                                    accumulated += langWrapPx
                                } else {
                                    break
                                }
                                stepped = true
                            }
                            if (stepped) {
                                setLanguagePreview(enabledLayoutIds[langIndex])
                                onKeyPress()
                            }
                            change.consume()
                        }
                        // NONE: the swipe is deliberately inert — swallow it
                        // so release does not type a space.
                        else -> change.consume()
                    }
                }
                holdJob?.cancel()
                setPressed(false)
                setLanguagePreview(null)
                when {
                    // A swipe-down already dismissed the keyboard: the finger
                    // lifting must not also type a space.
                    hidden -> {}
                    // The tappable picker is up and owns the choice: release
                    // just lifts the finger, it must not type a space.
                    action == null && pickerOpened -> {}
                    // Releasing with the hold preview up commits whatever it
                    // showed (usually the current language — a no-op) and
                    // must not type a space.
                    action == null && holdPreviewShown -> {
                        val selected = enabledLayoutIds[langIndex]
                        if (selected != currentLayoutId) onLayoutSelect(selected)
                    }
                    action == null -> onKey(key)
                    action == SpaceSwipeAction.LANGUAGE -> {
                        val selected = enabledLayoutIds[langIndex]
                        if (selected != currentLayoutId) onLayoutSelect(selected)
                    }
                    else -> {}
                }
            }
        }
    } else if (key.action == KeyAction.Delete && backspaceSwipeDelete) {
        // Backspace owns its whole gesture rather than bolting a drag onto
        // the shared press handler: tap, hold-to-repeat and word-swipe are
        // one state machine, so a drag can cleanly take over from the repeat
        // loop mid-press and the move events are consumed while it does.
        Modifier.pointerInput(key, longPressDelayMs, repeatIntervalMs, hapticOnLongPress,
            hapticOnLongPressRelease, vibrateOnRepeat, vibrateOnDeleteSwipe) {
            val slopPx = 10.dp.toPx()
            // The first word costs a deliberate drag; later ones get cheaper,
            // down to a floor, so clearing a sentence is one long pull but a
            // flick can never take more than a word or two.
            val firstStepPx = 72.dp.toPx()
            val nextStepPx = 56.dp.toPx()
            val stepShrinkPx = 6.dp.toPx()
            val minStepPx = 28.dp.toPx()
            fun wordStepPx(deleted: Int): Float = when (deleted) {
                0 -> firstStepPx
                else -> (nextStepPx - (deleted - 1) * stepShrinkPx).coerceAtLeast(minStepPx)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                setPressed(true)
                onKeyPress()
                var swiping = false
                var deleted = 0
                // X the next step is measured from: the press point until the
                // first word goes, then walked left one step at a time.
                var anchorX = down.position.x
                var longPressFired = false
                val repeat = scope.launch {
                    delay(longPressDelayMs.toLong())
                    longPressFired = true
                    while (canDelete()) {
                        if (vibrateOnRepeat) onKeyPress() else onKeySound()
                        onKeyRepeat(key)
                        delay(repeatIntervalMs.toLong())
                    }
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    if (!swiping && abs(change.position.x - down.position.x) > slopPx) {
                        swiping = true
                        repeat.cancel()
                        anchorX = down.position.x
                    }
                    if (swiping) {
                        // Claim the drag so nothing upstream reinterprets it.
                        change.consume()
                        while (anchorX - change.position.x >= wordStepPx(deleted)) {
                            anchorX -= wordStepPx(deleted)
                            if (!canDelete()) break
                            deleted++
                            if (vibrateOnDeleteSwipe) onKeyPress() else onKeySound()
                            onDeleteWord()
                        }
                        // Dragging back to the right re-anchors and resets the
                        // acceleration: a reversal stops the run, never replays it.
                        if (change.position.x > anchorX) {
                            anchorX = change.position.x
                            deleted = 0
                        }
                    }
                }
                repeat.cancel()
                setPressed(false)
                when {
                    // The swipe already did the deleting.
                    swiping -> Unit
                    !longPressFired -> onKey(key)
                    hapticOnLongPressRelease -> onKeyPress()
                }
            }
        }
    } else {
        // Settings are part of the pointerInput keys: pointerInput only
        // restarts when its keys change, so leaving them out would keep a
        // stale closure alive (e.g. release haptics still firing after the
        // toggle was turned off).
        Modifier.pointerInput(key, spaceShortSwipe, spaceLongSwipe, longPressDelayMs, repeatIntervalMs,
            hapticOnLongPress, hapticOnLongPressRelease, vibrateOnSpace, vibrateOnRepeat) {
            // Raw per-pointer tracking rather than detectTapGestures, which
            // handles one gesture at a time per key: a second finger landing
            // on the same key before the first lifts (burst double-taps) was
            // swallowed. Here every pointer gets its own press lifecycle.
            class Press {
                var longPressFired = false
                var job: Job? = null
            }
            val presses = HashMap<PointerId, Press>()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        val press = presses[change.id]
                        when {
                            press == null && change.changedToDown() -> {
                                val p = Press()
                                presses[change.id] = p
                                setPressed(true)
                                // Space press buzz is gated on its own toggle;
                                // the key sound (if on) still plays either way.
                                if (key.action == KeyAction.Space && !vibrateOnSpace) {
                                    onKeySound()
                                } else {
                                    onKeyPress()
                                }
                                p.job = scope.launch {
                                    delay(longPressDelayMs.toLong())
                                    p.longPressFired = true
                                    if (key.action == KeyAction.Delete || key.action == KeyAction.Space) {
                                        // Held backspace stops once there is
                                        // nothing left to delete — no point
                                        // buzzing against an empty field.
                                        while (key.action != KeyAction.Delete || canDelete()) {
                                            if (vibrateOnRepeat) onKeyPress() else onKeySound()
                                            onKeyRepeat(key)
                                            delay(repeatIntervalMs.toLong())
                                        }
                                    } else if (key.clipboardAction != null) {
                                        // Clipboard shortcut replaces the alternates popup
                                        // on this key; the action fires immediately.
                                        if (hapticOnLongPress) onKeyPress()
                                        onClipboardKey(key.clipboardAction)
                                    } else if (key.longPress.isNotEmpty()) {
                                        // Tactile cue that the long press registered and the
                                        // finger can be released (alternates are open / the
                                        // long-press action fired). Delete/space skip it:
                                        // their repeat loop already buzzes per repeat.
                                        if (hapticOnLongPress) onKeyPress()
                                        openAlternates()
                                    } else if (key.action == KeyAction.Symbols &&
                                        symbolsLongPressNumpad
                                    ) {
                                        // Opt-in: long-pressing ?123 opens the
                                        // numpad panel instead of acting like a tap.
                                        if (hapticOnLongPress) onKeyPress()
                                        onKey(key.copy(action = KeyAction.Numpad))
                                    } else if (key.action == KeyAction.LanguageSwitch) {
                                        // Tap cycles to the next language; the
                                        // long press opens the full picker.
                                        if (hapticOnLongPress) onKeyPress()
                                        openLanguagePicker()
                                    } else {
                                        // No alternates: long press behaves like a tap.
                                        if (hapticOnLongPress) onKeyPress()
                                        onKey(key)
                                    }
                                }
                            }
                            // Another handler claimed the pointer (glide typing
                            // consumed the move/up on the Initial pass): the
                            // press must not commit.
                            press != null && change.isConsumed -> {
                                press.job?.cancel()
                                presses.remove(change.id)
                                if (presses.isEmpty()) setPressed(false)
                            }
                            press != null && change.changedToUp() -> {
                                change.consume()
                                press.job?.cancel()
                                presses.remove(change.id)
                                if (presses.isEmpty()) setPressed(false)
                                // Forgiving bounds: a sloppy fast tap that drifts
                                // slightly off the cell still commits; a deliberate
                                // slide well away (≥ half a key beyond the edge)
                                // cancels, preserving slide-off-to-cancel.
                                val inBounds =
                                    change.position.x > -size.width * 0.5f &&
                                        change.position.x < size.width * 1.5f &&
                                        change.position.y > -size.height * 0.5f &&
                                        change.position.y < size.height * 1.5f
                                if (!press.longPressFired) {
                                    // Smart key-hit may swap in a likelier
                                    // neighbour chosen when this pointer went down.
                                    if (inBounds) onKey(smartResolve(key, change.id))
                                } else if (hapticOnLongPressRelease) {
                                    onKeyPress()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
)

// ---- emoji panel ----

/** Sentinel tab id for the history tab; ★ avoids clashing with catalog categories. */
private const val RECENT_TAB = "★recent"

/** Category → tab icon; falls back to the smiley for unknown categories. */
private fun emojiTabIcon(tab: String): ImageVector = when (tab) {
    RECENT_TAB -> Icons.Outlined.Schedule
    "smileys" -> Icons.Outlined.EmojiEmotions
    "people" -> Icons.Outlined.EmojiPeople
    "animals" -> Icons.Outlined.Pets
    "nature" -> Icons.Outlined.EmojiNature
    "food" -> Icons.Outlined.Fastfood
    "travel" -> Icons.Outlined.DirectionsCar
    "activities" -> Icons.Outlined.SportsSoccer
    "objects" -> Icons.Outlined.EmojiObjects
    "symbols" -> Icons.Outlined.EmojiSymbols
    "flags" -> Icons.Outlined.EmojiFlags
    else -> Icons.Outlined.EmojiEmotions
}

/**
 * One compact emoji tab: a 20dp icon over a 2dp selection bar, in a plain
 * weighted cell so search + every category share the row evenly.
 */
@Composable
private fun RowScope.EmojiTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

/**
 * The panel's search box: a tappable pill showing the live query, with a
 * hold-to-repeat backspace that edits the real text field while search mode
 * stays up. Shared by the in-panel layout and the full-bleed header.
 */
@Composable
private fun EmojiSearchField(
    state: KeyboardUiState,
    onEmojiQueryTap: () -> Unit,
    onSearchFieldDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalKeyPressFeedback.current
    val keySound = LocalKeySound.current
    val canDeleteField = LocalCanDeleteField.current
    val scope = rememberCoroutineScope()
    val vibrateOnRepeat = state.settings.feedback.vibrateOnRepeat
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .clickable { onEmojiQueryTap() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.width(8.dp))
        SearchQueryText(
            query = state.emojiQuery,
            placeholder = "Type to search…",
            active = state.emojiSearchActive,
            textColor = MaterialTheme.colorScheme.onSurface,
            placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        // While searching, the keys type into the query — so an emoji just
        // inserted from the results can't be deleted from the field with
        // them. This backspace edits the real text field (with
        // hold-to-repeat), keeping search mode up.
        if (state.emojiSearchActive) {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete from text field",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .pointerInput(
                        state.settings.longPressDelayMs,
                        state.settings.keyRepeatIntervalMs,
                        vibrateOnRepeat,
                    ) {
                        detectTapGestures(
                            onPress = {
                                feedback()
                                onSearchFieldDelete()
                                val repeat = scope.launch {
                                    delay(state.settings.longPressDelayMs.toLong())
                                    while (canDeleteField()) {
                                        if (vibrateOnRepeat) feedback() else keySound()
                                        onSearchFieldDelete()
                                        delay(state.settings.keyRepeatIntervalMs.toLong())
                                    }
                                }
                                tryAwaitRelease()
                                repeat.cancel()
                            },
                        )
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The face to show for a searched emoji [base]: the global default skin tone,
 * or the last-used variant when that override is enabled. Mirrors the IME's
 * `applyEmojiTone`, so what search shows is what a tap commits.
 */
private fun emojiSearchDisplay(state: KeyboardUiState, base: String): String {
    val emoji = state.settings.emoji
    return state.emojiVariants.tonedDisplay(
        base = base,
        tone = emoji.defaultSkinTone.toneIndex,
        preferred = state.emojiVariantPrefs[base],
        overrideWithPreferred = emoji.toneOverrideByLastUsed,
    )
}

@Composable
private fun EmojiPanel(
    state: KeyboardUiState,
    onEmoji: (String) -> Unit,
    onEmojiVariant: (String, String) -> Unit,
    onEmojiFavourite: (String) -> Unit,
    onEmojiQueryTap: () -> Unit,
    onClearRecents: () -> Unit,
    onRecentRemove: (String) -> Unit,
    onFavouritesReorder: (List<String>) -> Unit,
    onSearchFieldDelete: () -> Unit,
    onKey: (Key) -> Unit,
    onClose: () -> Unit,
) {
    // Gender/role variants (🏃‍♀️, 👨‍⚕️…) collapse under their base emoji;
    // the popup offers them, the grid stays tidy.
    val variantChildren = remember(state.emojiCatalog) {
        state.emojiCatalog.filter { it.parent != null }.groupBy({ it.parent!! }, { it.emoji })
    }
    val historyMode = state.settings.emojiTabMode
    val history = (if (historyMode == EmojiTabMode.MOST_USED) state.emojiFrequents else state.emojiRecents)
        .let { if (state.hiddenEmoji.isEmpty()) it else it.filterNot { e -> e in state.hiddenEmoji } }
    // Reorder is reached from any favourited emoji's long-press popup, and is
    // only meaningful once there are two favourites to shuffle.
    var reorderOpen by remember { mutableStateOf(false) }
    val onReorderFavourite: (() -> Unit)? =
        if (state.emojiFavourites.size >= 2) ({ reorderOpen = true }) else null
    // The always-on emoji row hides while this panel is open; absorbing its
    // height here keeps the keyboard from resizing on panel switches.
    val barCompensation =
        if (state.settings.emojiBarMode == EmojiBarMode.ALWAYS) EmojiBarHeight else 0.dp
    // Full-bleed hides the toolbar and the symbol row as well, and spends
    // the reclaimed row on a back button plus the category tabs — the panel
    // absorbs all of it so the keyboard never resizes on a panel switch.
    // Search mode hides the toolbar row too (see KeyboardBody), so the same
    // accounting applies with fewer rows to reclaim.
    val fullBleed = state.settings.emojiFullBleed
    val height = when {
        state.emojiSearchActive && fullBleed -> 120.dp + fullBleedHiddenRows(state.settings)
        state.emojiSearchActive -> 120.dp + topBarHeight(state.settings) + barCompensation
        fullBleed -> keyRowsHeight(state) + fullBleedHiddenRows(state.settings)
        else -> keyRowsHeight(state) + barCompensation
    }
    // One category rendered at a time behind tabs: the full catalog in a
    // single grid was a composition/measure hog. Hoisted above everything
    // else so the full-bleed header can host the strip.
    val categories = remember(state.emojiCatalog) {
        state.emojiCatalog.map { it.category }.distinct()
    }
    val hasHistory = history.isNotEmpty()
    val tabs = remember(categories, hasHistory) {
        buildList {
            if (hasHistory) add(RECENT_TAB)
            addAll(categories)
        }
    }
    val scope = rememberCoroutineScope()
    // A pager, not a swapped-in single grid: horizontal swipes cross
    // categories and every tab switch slides across. currentPage drives the
    // underline, updating live as a drag passes the halfway point; each page
    // keeps its own scroll offset via the stable key on the pager below.
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val selectedTab = tabs.getOrElse(pagerState.currentPage) { tabs.firstOrNull().orEmpty() }
    // Compact icon strip: search plus every category, split evenly across
    // the width so everything fits with no scrolling — Material's Tab has a
    // 90dp min width that forced a ScrollableTabRow here before.
    val tabStrip: @Composable RowScope.() -> Unit = {
        EmojiTab(
            icon = Icons.Outlined.Search,
            description = "Search emoji",
            selected = false,
            onClick = onEmojiQueryTap,
        )
        tabs.forEachIndexed { index, tab ->
            EmojiTab(
                icon = if (tab == RECENT_TAB && historyMode == EmojiTabMode.MOST_USED) {
                    Icons.Outlined.BarChart
                } else {
                    emojiTabIcon(tab)
                },
                description = when {
                    tab != RECENT_TAB -> tab.replaceFirstChar { it.uppercase() }
                    historyMode == EmojiTabMode.MOST_USED -> "Most used"
                    else -> "Recent"
                },
                selected = tab == selectedTab,
                onClick = {
                    // Tapping a tab slides there too, matching the swipe;
                    // reduce-motion jumps instead.
                    scope.launch {
                        if (state.settings.reduceMotion) pagerState.scrollToPage(index)
                        else pagerState.animateScrollToPage(index)
                    }
                },
            )
        }
    }
    val searching = state.emojiSearchActive || state.emojiQuery.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // Full-bleed header, standing in for the toolbar it replaced: back to
        // the keys, then whichever control the panel is currently driven by.
        if (fullBleed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolCircle(
                    icon = Icons.Outlined.ChevronLeft,
                    description = "Back to keyboard",
                    active = false,
                    onClick = onClose,
                )
                if (searching) {
                    EmojiSearchField(
                        state = state,
                        onEmojiQueryTap = onEmojiQueryTap,
                        onSearchFieldDelete = onSearchFieldDelete,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp, end = 2.dp),
                    )
                } else if (tabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabStrip,
                    )
                }
            }
        }
        // The grids fill whatever the bottom control bar leaves over.
        Column(modifier = Modifier.weight(1f)) {
        // The search field only shows while a search is underway; idle, the
        // entry point is the first icon of the tab strip below, so the panel
        // doesn't spend a whole bar of vertical space on it.
        if (!fullBleed && searching) {
            EmojiSearchField(
                state = state,
                onEmojiQueryTap = onEmojiQueryTap,
                onSearchFieldDelete = onSearchFieldDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (state.emojiQuery.isNotEmpty()) {
            // Memoized, and distinct so it is safe to key by: mapping inline
            // in the items() call rebuilt the list on every recomposition,
            // and every emoji tap emits fresh state, so the whole result grid
            // was thrown away and rebuilt on each keystroke.
            val results = remember(state.emojiResults) {
                state.emojiResults.map { it.emoji }.distinct()
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(results, key = { it }) { emoji ->
                    EmojiCell(
                        base = emoji,
                        // Search honours the global default skin tone (and the
                        // last-used variant when that override is on).
                        display = emojiSearchDisplay(state, emoji),
                        state = state,
                        genderVariants = variantChildren[emoji].orEmpty(),
                        onTap = onEmoji,
                        onPick = { variant -> onEmojiVariant(emoji, variant) },
                        onFavourite = onEmojiFavourite,
                        onReorderFavourites = onReorderFavourite,
                    )
                }
            }
            return@Column
        }

        if (!fullBleed && !state.emojiSearchActive && tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabStrip,
            )
        }

        if (tabs.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // The default already limits composition to the visible page
                // plus whatever a swipe drags into view, so a deep catalog
                // never all mounts at once — the reason a single grid was too
                // heavy in the first place.
                beyondViewportPageCount = 0,
                // Stable per-tab key: a page keeps its own scroll offset even
                // as history appears/disappears and shifts the indices, and a
                // cell's open long-press popup rides with its tab, not a slot.
                key = { tabs[it] },
            ) { page ->
                val tab = tabs[page]
                if (tab == RECENT_TAB) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Kept inside the page so the pager height stays fixed
                        // across a swipe — a row that appeared or vanished
                        // mid-drag would jolt the grid.
                        if (state.settings.emojiClearRecentsButton &&
                            historyMode == EmojiTabMode.RECENTS
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f))
                                TextButton(onClick = onClearRecents) {
                                    Icon(
                                        Icons.Outlined.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Box(modifier = Modifier.width(4.dp))
                                    Text("Clear recents", fontSize = 12.sp)
                                }
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 44.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            // Keyed by emoji: this list reorders on every tap
                            // (the tapped emoji jumps to the front), so on a
                            // positional key a cell's open popup stayed behind
                            // with the slot and reappeared over a different
                            // emoji. EmojiUsage.pinned() is distinct(), so the
                            // key is unique.
                            items(history, key = { it }) { emoji ->
                                // History cells are exact sequences: no variant
                                // pref to remember, taps in the popup commit
                                // directly.
                                EmojiCell(
                                    base = emoji,
                                    display = emoji,
                                    state = state,
                                    genderVariants = emptyList(),
                                    onTap = onEmoji,
                                    onPick = onEmoji,
                                    onFavourite = onEmojiFavourite,
                                    onReorderFavourites = onReorderFavourite,
                                    onRemove = onRecentRemove,
                                )
                            }
                        }
                    }
                } else {
                    val emojis = remember(state.emojiCatalog, tab, state.hiddenEmoji) {
                        state.emojiCatalog
                            .filter {
                                it.category == tab && it.parent == null &&
                                    it.emoji !in state.hiddenEmoji
                            }
                            .map { it.emoji }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        // Keyed by emoji, not by slot: a cell owns the open
                        // state of its long-press popup, and on a positional
                        // key that state stays with the slot while the list
                        // under it changes — the popup jumped to whatever
                        // emoji landed in that position.
                        items(emojis, key = { it }) { emoji ->
                            EmojiCell(
                                base = emoji,
                                display = state.emojiVariantPrefs[emoji] ?: emoji,
                                state = state,
                                genderVariants = variantChildren[emoji].orEmpty(),
                                onTap = onEmoji,
                                onPick = { variant -> onEmojiVariant(emoji, variant) },
                                onFavourite = onEmojiFavourite,
                                onReorderFavourites = onReorderFavourite,
                            )
                        }
                    }
                }
            }
        }
        }
        // In search mode the key rows sit right below the panel, so the
        // control bar would be redundant chrome.
        if (!state.emojiSearchActive) {
            EmojiBottomBar(state = state, onKey = onKey, onClose = onClose)
        }
    }
    // A Popup overlay, so opening it never reflows the fixed-height panel.
    if (reorderOpen) {
        FavouritesReorderPopup(
            favourites = state.emojiFavourites,
            onConfirm = {
                reorderOpen = false
                onFavouritesReorder(it)
            },
            onDismiss = { reorderOpen = false },
        )
    }
}

/**
 * Bottom control row of the emoji panel (Gboard style): back to the keys
 * on the left, a spacebar in the middle, and a repeating backspace on the
 * right — a quick emoji run never needs a detour through the letter keys.
 * Sized to the real bottom key row: same 10-unit grid (abc and ⌫ at the
 * ?123 key's 1.5 width), same key height and gaps.
 */
@Composable
private fun EmojiBottomBar(
    state: KeyboardUiState,
    onKey: (Key) -> Unit,
    onClose: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    val keySound = LocalKeySound.current
    val canDelete = LocalCanDelete.current
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val shape = kb.keyShape()
    // Cell = touch target spanning the gap, like KeyButton: the input
    // modifier sits outside the padding so presses between keys still land.
    val cell: @Composable RowScope.(Float, Modifier, @Composable () -> Unit) -> Unit =
        { weight, input, content ->
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .then(input)
                    .padding(horizontal = keyGapH(settings), vertical = keyGapV(settings)),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(settings.keyHeightDp.dp + keyGapV(settings) * 2)
            .padding(horizontal = 1.5.dp),
    ) {
        cell(
            1.5f,
            Modifier.clickable {
                feedback()
                onClose()
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(kb.modifierKey, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "abc",
                    color = kb.modifierKeyText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        cell(
            7f,
            Modifier.clickable {
                if (settings.feedback.vibrateOnSpace) feedback() else keySound()
                onKey(Key(" ", action = KeyAction.Space))
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(kb.key, shape),
                contentAlignment = Alignment.Center,
            ) {
                // The emoji panel's spacebar shows "Space", not the language
                // name: emoji picking is language-agnostic. A custom label
                // still applies, with %s standing in for "Space".
                val custom = settings.spacebarLabel
                Text(
                    text = if (custom.isEmpty()) "Space" else custom.replace("%s", "Space"),
                    fontSize = 11.sp,
                    color = kb.keyText.copy(alpha = 0.5f),
                )
            }
        }
        cell(
            1.5f,
            Modifier.pointerInput(
                settings.longPressDelayMs,
                settings.keyRepeatIntervalMs,
                settings.feedback.vibrateOnRepeat,
            ) {
                detectTapGestures(
                    onPress = {
                        feedback()
                        onKey(Key("⌫", action = KeyAction.Delete))
                        // Same hold-to-repeat cadence as the real backspace,
                        // buzzing on every repeat — and the same stop once
                        // the field has nothing left to delete.
                        val repeat = scope.launch {
                            delay(settings.longPressDelayMs.toLong())
                            while (canDelete()) {
                                if (settings.feedback.vibrateOnRepeat) feedback() else keySound()
                                onKey(Key("⌫", action = KeyAction.Delete))
                                delay(settings.keyRepeatIntervalMs.toLong())
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    },
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(kb.modifierKey, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.size(20.dp),
                    tint = kb.modifierKeyText,
                )
            }
        }
    }
}

/**
 * One emoji in the grid. Tap commits [display] (the user's preferred
 * variant of [base]); long-press opens the variant popup with the
 * favourite toggle, gender variants, skin tones, and — for two-person
 * emojis like the handshake — a per-person tone selector.
 */
@Composable
private fun EmojiCell(
    base: String,
    display: String,
    state: KeyboardUiState,
    genderVariants: List<String>,
    onTap: (String) -> Unit,
    onPick: (String) -> Unit,
    onFavourite: (String) -> Unit,
    onReorderFavourites: (() -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
) {
    var showVariants by remember { mutableStateOf(false) }
    val onHaptic = LocalHapticFeedback.current
    Box {
        Text(
            text = display,
            modifier = Modifier
                .pointerInput(base, display) {
                    detectTapGestures(
                        onTap = { onTap(display) },
                        onLongPress = {
                            // Haptic only: the key sound would read as "emoji
                            // inserted", which a long press does not do.
                            if (state.settings.hapticOnLongPress) onHaptic()
                            showVariants = true
                        },
                    )
                }
                .padding(6.dp),
            fontSize = 26.sp,
            fontFamily = LocalEmojiFontFamily.current,
        )
        if (showVariants) {
            EmojiVariantPopup(
                base = base,
                display = display,
                name = if (state.settings.emojiLongPressName) {
                    EmojiNames.of(state.emojiCatalog, display, base)
                } else {
                    null
                },
                index = state.emojiVariants,
                genderVariants = genderVariants,
                favourite = display in state.emojiFavourites,
                onDismiss = { showVariants = false },
                onPick = {
                    showVariants = false
                    onPick(it)
                },
                onFavourite = onFavourite,
                onReorderFavourites = onReorderFavourites?.let { reorder ->
                    {
                        showVariants = false
                        reorder()
                    }
                },
                onRemove = onRemove?.let { remove ->
                    {
                        showVariants = false
                        remove(display)
                    }
                },
            )
        }
    }
}

/** Fitzpatrick swatches for the two-person tone selector: neutral + 🏻..🏿. */
private val TONE_SWATCHES = listOf(
    Color(0xFFFFCC4D), Color(0xFFF7DECE), Color(0xFFF3D2A2),
    Color(0xFFD5AB88), Color(0xFFAF7E57), Color(0xFF7C533E),
)

@Composable
private fun EmojiVariantPopup(
    base: String,
    display: String,
    name: String?,
    index: EmojiVariantIndex,
    genderVariants: List<String>,
    favourite: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onFavourite: (String) -> Unit,
    onReorderFavourites: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val kb = LocalKbTheme.current
    Popup(
        popupPositionProvider = rememberAboveAnchorPopup(),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(kb.popupRadiusDp.dp),
            color = kb.popup,
            shadowElevation = 8.dp,
        ) {
            // Roomy by design: these rows are the only way to favourite or
            // forget an emoji, and at the old 26dp height they were easy to
            // miss with the same finger that just long-pressed.
            Column(modifier = Modifier.padding(8.dp)) {
                if (name != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = display,
                            fontSize = 20.sp,
                            fontFamily = LocalEmojiFontFamily.current,
                        )
                        Box(modifier = Modifier.width(10.dp))
                        // Long names wrap rather than stretch the popup: the
                        // whole thing is sized by its widest row, and a
                        // one-line "person with white cane facing right" would
                        // leave every row below it in empty space.
                        Text(
                            text = name.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.widthIn(max = 180.dp),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
                // Favourite pins this emoji to the top of the history tab
                // and the favourites row.
                // Re-seeded when the real flag changes, not only when the
                // emoji does: keyed on display alone the local mirror went
                // stale the moment the store echoed back, so reopening the
                // popup could show an unstarred emoji as favourited.
                var starred by remember(display, favourite) { mutableStateOf(favourite) }
                Row(
                    modifier = Modifier
                        .clickable {
                            starred = !starred
                            onFavourite(display)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (starred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Box(modifier = Modifier.width(10.dp))
                    Text(
                        if (starred) "Favourited" else "Favourite",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Only favourites can be reordered, and only when there are at
                // least two (the caller passes null otherwise).
                if (favourite && onReorderFavourites != null) {
                    Row(
                        modifier = Modifier
                            .clickable { onReorderFavourites() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Box(modifier = Modifier.width(10.dp))
                        Text(
                            "Reorder favourites",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                // History cells only: drop this emoji from recents/most-used.
                if (onRemove != null) {
                    Row(
                        modifier = Modifier
                            .clickable { onRemove() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Box(modifier = Modifier.width(10.dp))
                        Text(
                            "Remove from recents",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                val members = remember(base, genderVariants) { listOf(base) + genderVariants }
                if (index.hasDualTones(base) || genderVariants.any { index.hasDualTones(it) }) {
                    DualTonePicker(members = members, index = index, onPick = onPick)
                } else {
                    // One row per gender/role member, six cells when toned;
                    // toneless combination groups (families) just flow.
                    val cells = remember(members) { members.flatMap { index.popupVariants(it) } }
                    // A lone cell is the emoji itself — already shown in the
                    // name header, and a grid of one is just a second way to
                    // commit what a plain tap commits.
                    if (cells.size > 1 || name == null) Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        for (row in cells.chunked(6)) {
                            Row {
                                for (variant in row) {
                                    Text(
                                        text = variant,
                                        modifier = Modifier
                                            .clickable { onPick(variant) }
                                            .padding(horizontal = 9.dp, vertical = 9.dp),
                                        fontSize = 26.sp,
                                        fontFamily = LocalEmojiFontFamily.current,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Fixed row height inside [FavouritesReorderPopup], so drags map to slots. */
private val FavouriteReorderRowHeight = 48.dp

/**
 * A modal drag-to-reorder list for the favourites, opened from a favourited
 * emoji's long-press popup. Rendered as a [Popup] over the whole IME window
 * (a scrim swallows stray taps and doubles as tap-to-dismiss) rather than a
 * Compose Dialog, which needs a window token the IME does not hand out.
 *
 * The drag mechanic mirrors the settings-side ReorderDialog: each row carries
 * a handle, and dragging one past the next row's height swaps the two so the
 * item tracks the finger. The working copy only reaches the caller through
 * [onConfirm]; cancelling leaves the stored order alone.
 */
@Composable
private fun FavouritesReorderPopup(
    favourites: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = LocalKbTheme.current
    var working by remember { mutableStateOf(favourites) }
    // -1 = nothing being dragged.
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { FavouriteReorderRowHeight.toPx() }

    Popup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                // Tap the scrim to dismiss; the surface below swallows its own.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(kb.popupRadiusDp.dp),
                color = kb.popup,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.92f)
                    // Don't let taps inside the card fall through to the scrim.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Reorder favourites",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Text(
                        "Drag the handles. Top of the list comes first.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        working.forEachIndexed { index, emoji ->
                            val dragging = index == dragIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(FavouriteReorderRowHeight)
                                    // The dragged row rides above its neighbours.
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (dragging) dragOffset else 0f
                                    },
                            ) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp,
                                    fontFamily = LocalEmojiFontFamily.current,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Outlined.DragHandle,
                                    contentDescription = "Reorder $emoji",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(28.dp)
                                        // Keyed on Unit so a swap mid-drag never
                                        // restarts the gesture: slot `index` is
                                        // fixed for the life of the row, only the
                                        // item in it moves. `dragIndex` is live.
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragIndex = index
                                                    dragOffset = 0f
                                                },
                                                onDragEnd = {
                                                    dragIndex = -1
                                                    dragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    dragIndex = -1
                                                    dragOffset = 0f
                                                },
                                            ) { change, drag ->
                                                change.consume()
                                                dragOffset += drag.y
                                                val from = dragIndex
                                                val to = from + (dragOffset / rowPx).roundToInt()
                                                if (from >= 0 && to != from && to in working.indices) {
                                                    working = working.toMutableList().apply {
                                                        add(to, removeAt(from))
                                                    }
                                                    dragIndex = to
                                                    // Keep the offset relative to
                                                    // the row's new home, or the
                                                    // item would jump a full row.
                                                    dragOffset -= (to - from) * rowPx
                                                }
                                            }
                                        },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = { onConfirm(working) }) { Text("Save") }
                    }
                }
            }
        }
    }
}

/**
 * Gboard-style two-slot skin-tone selector for emojis where each person
 * has an independent tone (🤝, couples, holding hands…). The top row picks
 * the gender/role combination; the two swatch rows pick each person's
 * tone; tapping the live preview commits the exact RGI sequence.
 */
@Composable
private fun DualTonePicker(
    members: List<String>,
    index: EmojiVariantIndex,
    onPick: (String) -> Unit,
) {
    var member by remember { mutableStateOf(members.first()) }
    var first by remember { mutableStateOf(0) }
    var second by remember { mutableStateOf(0) }
    // Not every combination is RGI (a toned person can't shake a neutral
    // hand), so a pick on one side seeds the other side too.
    val preview = index.tonedPair(member, first, second) ?: member

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (members.size > 1) {
            Row {
                for (candidate in members) {
                    Text(
                        text = candidate,
                        modifier = Modifier
                            .background(
                                if (candidate == member) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { member = candidate }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        fontSize = 22.sp,
                        fontFamily = LocalEmojiFontFamily.current,
                    )
                }
            }
        }
        Text(
            text = preview,
            modifier = Modifier
                .clickable { onPick(preview) }
                .padding(6.dp),
            fontSize = 34.sp,
            fontFamily = LocalEmojiFontFamily.current,
        )
        for (slot in 0..1) {
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (tone in 0..5) {
                    val selected = tone == if (slot == 0) first else second
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(26.dp)
                            .background(TONE_SWATCHES[tone], CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp, MaterialTheme.colorScheme.primary, CircleShape,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                if (tone == 0) {
                                    first = 0
                                    second = 0
                                } else if (slot == 0) {
                                    first = tone
                                    if (second == 0) second = tone
                                } else {
                                    second = tone
                                    if (first == 0) first = tone
                                }
                            },
                    )
                }
            }
        }
    }
}

// ---- snippets panel ----

@Composable
private fun SnippetsPanel(
    state: KeyboardUiState,
    onSnippet: (Snippet) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val height = keyRowsHeight(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (state.snippets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No snippets yet.\nVariables: {date} {time} {datetime} {clip}",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ToolPanelChip("Snippet settings", selected = true, onClick = onOpenSettings)
            }
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Variables: {date} {time} {datetime} {clip}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ToolCircle(
                icon = Icons.Outlined.Settings,
                description = "Snippet settings",
                active = false,
                onClick = onOpenSettings,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.snippets, key = { it.id }) { snippet ->
                Column(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tween(160),
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                            fadeOutSpec = tween(140),
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                        .clickable { onSnippet(snippet) }
                        .padding(10.dp),
                ) {
                    Text(
                        text = snippet.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = snippet.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ---- clipboard panel ----

@Composable
private fun ClipboardPanel(
    state: KeyboardUiState,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onClipboardSearchToggle: () -> Unit,
    onKey: (Key) -> Unit,
    onClose: () -> Unit,
) {
    val showBottomRow = state.settings.clipboard.bottomRow
    // The control row is carved out of the panel's own height (same size as the
    // emoji panel's), so the total stays exactly the key area's height and the
    // keyboard never grows when the row is on.
    val barHeight = state.settings.keyHeightDp.dp + keyGapV(state.settings) * 2
    val contentHeight = keyRowsHeight(state) - if (showBottomRow) barHeight else 0.dp
    Column {
        ClipboardPanelContent(
            state, onClipboardItem, onClipboardPin, onClipboardDelete,
            onClipboardSearchToggle = onClipboardSearchToggle,
            height = contentHeight,
        )
        if (showBottomRow) {
            EmojiBottomBar(state = state, onKey = onKey, onClose = onClose)
        }
    }
}

/**
 * Whether a clip matches the panel's search query — the same rule as
 * [com.wasimaster.wmkeyboard.core.clipboard.ClipboardStore.search]: textual
 * clips match on their text, files/folders on their name, others never.
 */
private fun clipMatchesQuery(item: ClipItem, query: String): Boolean = when {
    item.kind.isTextual -> item.text.contains(query, ignoreCase = true)
    item.kind == ClipKind.FILE || item.kind == ClipKind.FOLDER ->
        item.fileName.orEmpty().contains(query, ignoreCase = true)
    else -> false
}

/**
 * Search pill at the top of the clipboard panel. Tapping it routes the keys
 * into [KeyboardUiState.clipboardQuery] (like emoji/dictionary search) so the
 * IME can filter its own history without a focusable text field.
 */
@Composable
private fun ClipboardSearchField(
    state: KeyboardUiState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.clipboardSearchActive
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        SearchQueryText(
            query = state.clipboardQuery,
            placeholder = "Search clipboard…",
            active = active,
            textColor = MaterialTheme.colorScheme.onSurface,
            placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        // Only up while searching (typing is the only way to fill the query,
        // and closing clears it). Clears the filter and hands the keys back.
        if (active) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Clear search",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .clickable { onToggle() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClipboardPanelContent(
    state: KeyboardUiState,
    onClipboardItem: (ClipItem) -> Unit,
    onClipboardPin: (ClipItem) -> Unit,
    onClipboardDelete: (ClipItem) -> Unit,
    onClipboardSearchToggle: () -> Unit,
    height: Dp,
) {
    // The search bar is only offered once there is history to filter and the
    // feature is on; an empty panel just shows the placeholder.
    val showSearch = state.settings.clipboard.search && state.clipboardItems.isNotEmpty()
    if (state.clipboardItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Clipboard history is empty.\nCopied text will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val query = state.clipboardQuery.trim()
    val shownItems = if (!showSearch || query.isEmpty()) {
        state.clipboardItems
    } else {
        state.clipboardItems.filter { clipMatchesQuery(it, query) }
    }
    Column(modifier = Modifier.height(height)) {
        if (showSearch) {
            ClipboardSearchField(
                state = state,
                onToggle = onClipboardSearchToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
            )
        }
        if (shownItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No clips match “$query”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shownItems, key = { it.id }) { item ->
            // Deleting fades the card out and slides the survivors up into the
            // gap; pinning re-sorts the list, so the card glides to the front
            // instead of teleporting there.
            SwipeToDeleteCard(
                onDelete = { onClipboardDelete(item) },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(160),
                    placementSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset.VisibilityThreshold,
                    ),
                    fadeOutSpec = tween(140),
                ),
            ) {
                var showInfo by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                        .pointerInput(item.id) {
                            detectTapGestures(
                                onTap = { onClipboardItem(item) },
                                onLongPress = { showInfo = true },
                            )
                        }
                        .padding(10.dp),
                ) {
                    if (showInfo) {
                        ClipInfoPopup(item, onDismiss = { showInfo = false })
                    }
                    when (item.kind) {
                        ClipKind.IMAGE -> ClipThumbnail(item)
                        ClipKind.FILE, ClipKind.FOLDER -> ClipFileBody(item)
                        ClipKind.LINK -> ClipLinkBody(item)
                        else -> Text(
                            text = item.text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (item.kind == ClipKind.HTML) {
                            Text(
                                "Rich text",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        ClipActionCircle(
                            icon = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            description = if (item.pinned) "Unpin" else "Pin",
                            tint = if (item.pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { onClipboardPin(item) }
                        ClipActionCircle(
                            icon = Icons.Outlined.Delete,
                            description = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { onClipboardDelete(item) }
                    }
                }
            }
            }
        }
    }
}

/** Small round action button on a clipboard card. */
@Composable
private fun ClipActionCircle(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = tint,
        )
    }
}

/**
 * Press-and-hold details for a clip: when it was copied (relative + exact),
 * which app it came from (when source tracking is on), its type, and a size or
 * length. Anchored above the card; dismissed by tapping elsewhere.
 */
@Composable
private fun ClipInfoPopup(item: ClipItem, onDismiss: () -> Unit) {
    val kb = LocalKbTheme.current
    val now = System.currentTimeMillis()
    val relative = android.text.format.DateUtils.getRelativeTimeSpanString(
        item.timestamp, now, android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val exact = remember(item.timestamp) {
        java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(item.timestamp))
    }
    val typeLabel = when (item.kind) {
        ClipKind.TEXT -> "Text"
        ClipKind.HTML -> "Rich text"
        ClipKind.LINK -> "Link"
        ClipKind.IMAGE -> "Image"
        ClipKind.FILE -> "File"
        ClipKind.FOLDER -> "Folder"
    }
    val sizeLabel = when (item.kind) {
        ClipKind.IMAGE -> item.mimeType.substringAfterLast('/').takeIf { it.isNotBlank() }?.uppercase()
        ClipKind.FILE -> formatFileSize(item.fileSize)
        ClipKind.FOLDER -> null
        else -> {
            val chars = item.text.length
            "$chars character" + if (chars == 1) "" else "s"
        }
    }
    Popup(
        popupPositionProvider = rememberAboveAnchorPopup(),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(kb.popupRadiusDp.dp),
            color = kb.popup,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 240.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ClipInfoRow("Copied", "$relative\n$exact", kb.popupText)
                item.sourceApp?.let { ClipInfoRow("From", it, kb.popupText) }
                ClipInfoRow("Type", typeLabel, kb.popupText)
                sizeLabel?.let { ClipInfoRow("Size", it, kb.popupText) }
            }
        }
    }
}

/** One "Label: value" line in the clipboard info popup. */
@Composable
private fun ClipInfoRow(label: String, value: String, textColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.width(44.dp),
        )
        Text(
            value,
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Horizontal swipe-to-dismiss for a grid card: the card follows the finger,
 * fades as it travels, and a release past 40% of its width deletes it —
 * otherwise it springs back. Vertical scrolling is untouched (only
 * horizontal drags are claimed).
 */
@Composable
private fun SwipeToDeleteCard(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .onGloballyPositioned { width = it.size.width }
            .graphicsLayer {
                translationX = offset.value
                alpha = if (width == 0) 1f
                else (1f - abs(offset.value) / width).coerceIn(0.2f, 1f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + delta) }
                    },
                    onDragEnd = {
                        scope.launch {
                            val threshold = width * 0.4f
                            if (width > 0 && abs(offset.value) > threshold) {
                                // Finish the slide off-screen, then delete.
                                offset.animateTo(
                                    if (offset.value > 0) width.toFloat() else -width.toFloat(),
                                    tween(120),
                                )
                                onDelete()
                                offset.snapTo(0f)
                            } else {
                                offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(0f) }
                    },
                )
            },
    ) { content() }
}

/**
 * A copied file or folder: type icon, name, and either the file size or a
 * "Folder" label. Folders are marked plainly because tapping one can't attach
 * it anywhere — it types the name instead.
 */
@Composable
private fun ClipFileBody(item: ClipItem) {
    val isFolder = item.kind == ClipKind.FOLDER
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isFolder) Icons.Outlined.Folder else fileIconFor(item.mimeType),
            contentDescription = if (isFolder) "Folder" else "File",
            modifier = Modifier
                .size(28.dp)
                .padding(end = 6.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = item.fileName.orEmpty().ifBlank { item.text },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isFolder) "Folder" else listOfNotNull(
                    formatFileSize(item.fileSize),
                    item.mimeType.substringAfterLast('/').takeIf { it.isNotBlank() }?.uppercase(),
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A copied link, tinted and underlined so it reads as one. When link previews
 * are on and the fetch found something, the page title and description replace
 * the raw URL, which drops to a host line underneath.
 */
@Composable
private fun ClipLinkBody(item: ClipItem) {
    val preview = item.linkPreview?.takeIf { !it.failed && !it.isEmpty }
    val linkColor = MaterialTheme.colorScheme.primary
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Link,
                contentDescription = "Link",
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 4.dp),
                tint = linkColor,
            )
            Text(
                text = preview?.title?.takeIf { it.isNotBlank() } ?: item.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = if (preview != null) FontWeight.Medium else FontWeight.Normal,
                color = if (preview != null) MaterialTheme.colorScheme.onSurface else linkColor,
                textDecoration = if (preview != null) null else TextDecoration.Underline,
            )
        }
        if (preview != null && preview.description.isNotBlank()) {
            Text(
                text = preview.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val host = ClipLinks.host(ClipLinks.asUrl(item.text) ?: item.text)
        if (host.isNotBlank()) {
            Text(
                text = preview?.siteName?.takeIf { it.isNotBlank() } ?: host,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                color = linkColor,
            )
        }
    }
}

private fun fileIconFor(mimeType: String) = when {
    mimeType.startsWith("audio/") -> Icons.Outlined.AudioFile
    mimeType.startsWith("video/") -> Icons.Outlined.VideoFile
    mimeType.startsWith("image/") -> Icons.Outlined.Image
    mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf
    mimeType.startsWith("text/") -> Icons.Outlined.Description
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** Human file size, or null when the provider didn't report one. */
private fun formatFileSize(bytes: Long): String? {
    if (bytes < 0) return null
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 10) "${value.toInt()} ${units[unit]}"
    else String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unit])
}

/** Decodes a downsampled preview of an image clip off the main thread. */
@Composable
private fun ClipThumbnail(item: ClipItem) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.imagePath) {
        value = withContext(Dispatchers.IO) {
            val path = item.imagePath ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= THUMBNAIL_TARGET_PX &&
                    bounds.outHeight / (sample * 2) >= THUMBNAIL_TARGET_PX
                ) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    bitmap?.let {
        // Fit, not crop: tall screenshots show whole, letterboxed against
        // the card color, instead of a 64dp slice off the top.
        Image(
            bitmap = it,
            contentDescription = "Copied image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 160.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                .clip(shape),
            contentScale = ContentScale.Fit,
        )
    } ?: Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
    )
}

private const val THUMBNAIL_TARGET_PX = 256
