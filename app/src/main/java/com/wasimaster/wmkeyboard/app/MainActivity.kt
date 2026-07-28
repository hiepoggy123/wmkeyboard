package com.wasimaster.wmkeyboard.app

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.view.KeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.wasimaster.wmkeyboard.core.settings.SuggestionHotkeyMode
import com.wasimaster.wmkeyboard.core.tools.CheatSheetLetter
import com.wasimaster.wmkeyboard.core.tools.DefaultLeader
import com.wasimaster.wmkeyboard.core.tools.DefaultToolLetters
import com.wasimaster.wmkeyboard.core.tools.KeyChord
import com.wasimaster.wmkeyboard.core.tools.LeaderTrigger
import com.wasimaster.wmkeyboard.core.tools.ReservedChords
import com.wasimaster.wmkeyboard.core.tools.ReservedLetters
import com.wasimaster.wmkeyboard.core.tools.TapModifier
import com.wasimaster.wmkeyboard.core.tools.ToolboxLetter
import com.wasimaster.wmkeyboard.core.tools.describeChord
import com.wasimaster.wmkeyboard.core.tools.describeLeader
import com.wasimaster.wmkeyboard.core.tools.formatChord
import com.wasimaster.wmkeyboard.core.tools.formatLeader
import com.wasimaster.wmkeyboard.core.tools.parseLeader
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.ui.toolAccentColor
import com.wasimaster.wmkeyboard.core.ui.toolAccentColorArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import android.provider.OpenableColumns
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.os.Build
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.feedback.KeySoundPlayer
import com.wasimaster.wmkeyboard.core.handwriting.HandwritingModels
import com.wasimaster.wmkeyboard.core.settings.EmojiBarContent
import com.wasimaster.wmkeyboard.core.settings.EmojiBarCountRange
import com.wasimaster.wmkeyboard.core.settings.BottomRowHeightRange
import com.wasimaster.wmkeyboard.core.settings.SidePadScaleRange
import com.wasimaster.wmkeyboard.core.settings.ShiftCapsLockMsRange
import com.wasimaster.wmkeyboard.core.settings.DefaultCurrencyKeys
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.core.settings.EmojiSkinTone
import com.wasimaster.wmkeyboard.core.icons.IconPackStore
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.core.util.requireInputStream
import com.wasimaster.wmkeyboard.core.util.runCancellable
import com.wasimaster.wmkeyboard.ime.ui.IconDefaults
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import com.wasimaster.wmkeyboard.ime.ui.LocalIconSet
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import com.wasimaster.wmkeyboard.ime.ui.rememberIconSet
import com.wasimaster.wmkeyboard.ime.ui.ModeIcons
import com.wasimaster.wmkeyboard.core.settings.EmojiInsertMode
import com.wasimaster.wmkeyboard.core.settings.EmojiTabMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.GifContentFilter
import com.wasimaster.wmkeyboard.core.settings.GifSourceMode
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.core.settings.MediaSendMode
import com.wasimaster.wmkeyboard.core.settings.QrEccLevel
import com.wasimaster.wmkeyboard.core.tools.AiClient
import com.wasimaster.wmkeyboard.core.tools.AltCalendar
import com.wasimaster.wmkeyboard.core.tools.AiPrompts
import com.wasimaster.wmkeyboard.core.tools.GeoPlace
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.core.tools.TypingBests
import com.wasimaster.wmkeyboard.core.tools.TypingHistory
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import com.wasimaster.wmkeyboard.core.tools.ToolHttp
import com.wasimaster.wmkeyboard.core.tools.TranslateClient
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wasimaster.wmkeyboard.core.settings.BarRow
import com.wasimaster.wmkeyboard.core.settings.CursorTools
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.NumeralCommitScope
import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import com.wasimaster.wmkeyboard.core.input.composer.CjkLearning
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.settings.ConfigBackup
import com.wasimaster.wmkeyboard.core.settings.SettingsBackup
import com.wasimaster.wmkeyboard.core.settings.KeyboardMode
import com.wasimaster.wmkeyboard.core.settings.DefaultKeyboardModes
import com.wasimaster.wmkeyboard.core.settings.DefaultToolbarTools
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool
import com.wasimaster.wmkeyboard.core.settings.ModeField
import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import com.wasimaster.wmkeyboard.core.tools.resolveSymbolSets
import com.wasimaster.wmkeyboard.core.tools.SymbolSet
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.OneHandedSide
import com.wasimaster.wmkeyboard.core.settings.PowerSavingTrigger
import com.wasimaster.wmkeyboard.core.settings.ScreenVariant
import com.wasimaster.wmkeyboard.core.settings.SensitiveClipHandling
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.sizingValuesFor
import com.wasimaster.wmkeyboard.core.settings.LetterSwipeAction
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.SpacebarDisplay
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictStore
import com.wasimaster.wmkeyboard.core.emoji.EmojiFontShaping
import com.wasimaster.wmkeyboard.core.emoji.EmojiKeywordPack
import com.wasimaster.wmkeyboard.core.emoji.EmojiKeywordPacks
import com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.feedback.SoundFile
import com.wasimaster.wmkeyboard.core.feedback.SoundImportResult
import com.wasimaster.wmkeyboard.core.feedback.SoundStore
import com.wasimaster.wmkeyboard.core.fonts.FontFile
import com.wasimaster.wmkeyboard.core.fonts.FontImportResult
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.fonts.InstalledFont
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetFile
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import com.wasimaster.wmkeyboard.core.snippets.SnippetVariable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Settings app: setup wizard plus every keyboard option, Material 3 +
 * dynamic color, all state backed by DataStore via [SettingsRepository].
 */
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Intent extra with a [ToolbarTool] name: the keyboard uses it
         * (tool long-press, "needs an API key" panels) to jump straight
         * to that tool's settings page.
         */
        const val EXTRA_OPEN_TOOL = "open_tool"
        /**
         * Intent extra with a specific settings route string (e.g., "themes").
         */
        const val EXTRA_OPEN_ROUTE = "open_route"
    }

    private lateinit var repository: SettingsRepository

    /**
     * Where the intent that started (or re-entered) this activity wants to go.
     *
     * A flow rather than a value read once in [onCreate]: the activity is
     * `singleTop`, so a second `wmkeyboard://` link — or a second "open
     * settings" from the keyboard — arrives at [onNewIntent] on the instance
     * that is already running. Reading `intent` once would silently drop it.
     */
    private val pendingNav = MutableStateFlow<PendingNav?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shares a process with the keyboard service when both are running, so
        // this is often a no-op — but the settings app can be opened first.
        DebugLog.attach(applicationContext)
        repository = SettingsRepository(applicationContext)
        // The JSON asset layouts back the tail of the language list; load them
        // before the first settings emission so an enabled asset layout resolves
        // to its real language here (a few small files, parsed once).
        AssetLayouts.load(applicationContext.assets)
        // Modes added since this install was first seeded — the settings
        // screen should list them even if the keyboard has not run yet.
        lifecycleScope.launch { repository.seedNewDefaultModes() }
        pendingNav.value = navFor(intent)
        setContent {
            // Null until DataStore's first emission: rendering nothing for a
            // frame beats flashing onboarding at users who finished it.
            val settings by repository.settings
                .collectAsStateWithLifecycle(null as KeyboardSettings?)
            val pending by pendingNav.collectAsStateWithLifecycle()
            settings?.let { loaded ->
                AppTheme(loaded) {
                    SettingsNavHost(
                        repository = repository,
                        settings = loaded,
                        pending = pending,
                        onPendingHandled = { pendingNav.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navFor(intent)?.let { pendingNav.value = it }
    }

    /**
     * What an intent asks for: a `wmkeyboard://` deep link, or one of the
     * extras the keyboard uses to jump into a tool's page.
     */
    private fun navFor(intent: Intent?): PendingNav? {
        if (intent == null) return null
        AddonDeepLink.routeFor(intent.data)?.let { return PendingNav(route = it) }
        SettingsShortcuts.routeFor(intent.data)?.let { return PendingNav(route = it) }
        intent.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { it.isNotEmpty() }
            ?.let { return PendingNav(route = it) }
        val tool = intent.getStringExtra(EXTRA_OPEN_TOOL)
            ?.let { name -> ToolbarTool.entries.find { it.name == name } }
        return tool?.let { PendingNav(tool = it) }
    }
}

/** A navigation an incoming intent asked for; exactly one field is set. */
internal data class PendingNav(
    val route: String? = null,
    val tool: ToolbarTool? = null,
)

@Composable
internal fun AppTheme(settings: KeyboardSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val supportsDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = when {
        supportsDynamic && dark -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (settings.themeMode == ThemeMode.AMOLED) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    }
    // Every settings surface draws tool icons, so the user's icon set is
    // provided here rather than per screen — the Tools list and the keyboard
    // must not disagree about what a tool looks like.
    val iconSet by rememberIconSet(settings.icons)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalIconSet provides iconSet, content = content)
    }
}

@Composable
private fun SettingsNavHost(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    pending: PendingNav? = null,
    onPendingHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    // A pack downloaded from these screens has to reach the running keyboard,
    // which holds its merged emoji catalogue in memory. Bumping the counter it
    // watches is that message. Collected here rather than on the screen that
    // started the download, because navigating away mid-download must not lose
    // the notification.
    LaunchedEffect(Unit) {
        EmojiDictDownloadManager.completions.collect {
            repository.bumpEmojiKeywordPackVersion()
        }
    }
    // Where an incoming intent wants to go: a wmkeyboard:// link, or the
    // keyboard's own "open settings" (tool long-press, a panel's link). Cleared
    // once handled, so returning to this screen doesn't navigate again.
    LaunchedEffect(pending) {
        if (pending == null || !settings.onboardingDone) return@LaunchedEffect
        when {
            !pending.route.isNullOrEmpty() -> navController.navigate(pending.route)
            pending.tool != null -> {
                navController.navigate("tools")
                navController.navigate("tool/${pending.tool.name}")
            }
        }
        onPendingHandled()
    }
    // Quick shared-axis slide instead of the sluggish default cross-fade;
    // collapsed to an instant cut when the user has asked for reduced motion.
    val navMs = if (settings.reduceMotion) 0 else 220
    val spec = tween<androidx.compose.ui.unit.IntOffset>(navMs)
    val fadeSpec = tween<Float>(navMs)
    // Frozen at first composition: completing onboarding navigates away
    // explicitly, it must not yank the graph out from under the NavHost.
    val startDestination = remember { if (settings.onboardingDone) "home" else "onboarding" }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(spec) { it / 5 } + fadeIn(fadeSpec) },
        exitTransition = { slideOutHorizontally(spec) { -it / 5 } + fadeOut(fadeSpec) },
        popEnterTransition = { slideInHorizontally(spec) { -it / 5 } + fadeIn(fadeSpec) },
        popExitTransition = { slideOutHorizontally(spec) { it / 5 } + fadeOut(fadeSpec) },
    ) {
        composable("onboarding") {
            OnboardingScreen(
                repository = repository,
                settings = settings,
                onFinished = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable("home") {
            HomeScreen(
                settings = settings,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable("search") {
            SettingsSearchScreen(
                onBack = { navController.popBackStack() },
                onOpen = { result ->
                    // Arm the flash before navigating: the destination's rows
                    // read it during their first composition.
                    SettingsHighlight.request(result.title)
                    // The search screen itself is dropped from the back stack,
                    // so backing out of the setting lands on the home list.
                    navController.popBackStack()
                    navController.navigate(result.route)
                },
            )
        }
        composable("typing") {
            SettingsScreen("Typing", { navController.popBackStack() }) {
                TypingSettings(
                    repository, settings,
                    onOpenDictionary = { navController.navigate("dictionary") },
                    onOpenCustomDictionaries = { navController.navigate("customdictionaries") },
                    onOpenBlacklist = { navController.navigate("blacklist") },
                    onOpenHardwareShortcuts = { navController.navigate("hwshortcuts") },
                )
            }
        }
        composable("keypress") {
            SettingsScreen("Key press", { navController.popBackStack() }) {
                KeyPressSettings(repository, settings)
            }
        }
        composable("dictionary") {
            SettingsScreen("Personal dictionary", { navController.popBackStack() }) {
                DictionarySettings(repository)
            }
        }
        composable("backup") {
            SettingsScreen("Backup & restore", { navController.popBackStack() }) {
                BackupSettings(repository)
            }
        }
        composable("customdictionaries") {
            SettingsScreen("Custom dictionaries", { navController.popBackStack() }) {
                CustomDictionarySettings(repository, settings)
            }
        }
        composable("emojikeywords") {
            SettingsScreen("Emoji keywords", { navController.popBackStack() }) {
                EmojiKeywordSettings(repository, settings)
            }
        }
        composable("blacklist") {
            SettingsScreen("Suggestion blacklist", { navController.popBackStack() }) {
                BlacklistSettings(repository, settings)
            }
        }
        composable("hwshortcuts") {
            SettingsScreen("Tool shortcuts list", { navController.popBackStack() }) {
                HardwareShortcutsSettings(repository, settings)
            }
        }
        composable("appearance") {
            SettingsScreen("Appearance", { navController.popBackStack() }) {
                AppearanceSettings(
                    repository, settings,
                    onOpenThemes = { navController.navigate("themes") },
                    onOpenFonts = { navController.navigate("fonts") },
                    onOpenIcons = { navController.navigate("icons") },
                )
            }
        }
        composable("layout") {
            SettingsScreen("Layout & size", { navController.popBackStack() }) {
                LayoutSettings(repository, settings)
            }
        }
        composable("fonts") {
            SettingsScreen("Keyboard font", { navController.popBackStack() }) {
                FontSettings(repository, settings)
            }
        }
        composable("icons") {
            SettingsScreen("Icons", { navController.popBackStack() }) {
                IconsScreen(repository, settings)
            }
        }
        composable("themes") {
            SettingsScreen("Keyboard themes", { navController.popBackStack() }) {
                ThemesScreen(repository, settings) { id -> navController.navigate("theme_edit/$id") }
            }
        }
        composable("theme_edit/{themeId}") { backStackEntry ->
            val themeId = backStackEntry.arguments?.getString("themeId").orEmpty()
            SettingsScreen("Edit theme", { navController.popBackStack() }) {
                ThemeEditorScreen(repository, settings, themeId)
            }
        }
        composable("keymaps") {
            SettingsScreen("Key layouts", { navController.popBackStack() }) {
                KeyLayoutsScreen(repository, settings) { route -> navController.navigate(route) }
            }
        }
        composable("sticker_packs") {
            SettingsScreen("Sticker packs", { navController.popBackStack() }) {
                StickerPacksScreen { route -> navController.navigate(route) }
            }
        }
        composable("plugins") {
            SettingsScreen("Plugins", { navController.popBackStack() }) {
                PluginsScreen { route -> navController.navigate(route) }
            }
        }
        composable("plugin/{pluginId}") { entry ->
            val pluginId = entry.arguments?.getString("pluginId").orEmpty()
            SettingsScreen("Plugin", { navController.popBackStack() }) {
                PluginDetailScreen(pluginId) { navController.popBackStack() }
            }
        }
        // The optional `add` argument carries a repository URL from a
        // wmkeyboard://repo link; it pre-fills the add dialog, which is still
        // where the user confirms.
        composable(
            "addons?add={add}",
            arguments = listOf(
                navArgument("add") { defaultValue = ""; type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val prefill = decodeRouteArg(backStackEntry.arguments?.getString("add"))
            SettingsScreen("Addons", { navController.popBackStack() }) {
                AddonsScreen(prefill) { route -> navController.navigate(route) }
            }
        }
        // The repository URL travels in the path, percent-encoded — a deep link
        // names a repository by address, not by its position in the user's list.
        composable("addon_repo/{repoUrl}") { backStackEntry ->
            val url = decodeRouteArg(backStackEntry.arguments?.getString("repoUrl"))
            SettingsScreen("Browse addons", { navController.popBackStack() }) {
                AddonRepoScreen(url) { route -> navController.navigate(route) }
            }
        }
        composable("addon/{repoUrl}/{addonId}") { backStackEntry ->
            val url = decodeRouteArg(backStackEntry.arguments?.getString("repoUrl"))
            val addonId = decodeRouteArg(backStackEntry.arguments?.getString("addonId"))
            SettingsScreen("Addon", { navController.popBackStack() }) {
                AddonDetailScreen(url, addonId) { route -> navController.navigate(route) }
            }
        }
        composable("sticker_pack/{packId}") { backStackEntry ->
            val packId = backStackEntry.arguments?.getString("packId").orEmpty()
            SettingsScreen("Edit sticker pack", { navController.popBackStack() }) {
                StickerPackScreen(packId)
            }
        }
        composable("keymap_edit/{layoutId}") { backStackEntry ->
            val layoutId = backStackEntry.arguments?.getString("layoutId").orEmpty()
            SettingsScreen("Edit layout", { navController.popBackStack() }) {
                KeyLayoutEditorScreen(repository, settings, layoutId) { route ->
                    navController.navigate(route)
                }
            }
        }
        composable("keymap_json/{layoutId}") { backStackEntry ->
            val layoutId = backStackEntry.arguments?.getString("layoutId").orEmpty()
            SettingsScreen("Layout JSON", { navController.popBackStack() }) {
                KeyLayoutJsonScreen(repository, settings, layoutId) { navController.popBackStack() }
            }
        }
        composable("languages") {
            SettingsScreen("Languages", { navController.popBackStack() }) {
                LanguageSettings(repository, settings) { route -> navController.navigate(route) }
            }
        }
        composable("add_language") {
            SettingsScreen("Add language", { navController.popBackStack() }) {
                AddLanguageScreen(repository, settings) { langId ->
                    navController.navigate("language/$langId")
                }
            }
        }
        composable("language/{langId}") { backStackEntry ->
            val langId = backStackEntry.arguments?.getString("langId").orEmpty()
            SettingsScreen(LanguageRegistry.byId(langId).displayName, { navController.popBackStack() }) {
                LanguageDetailScreen(
                    langId, repository, settings,
                    onNavigate = { route -> navController.navigate(route) },
                    onRemoved = { navController.popBackStack() },
                )
            }
        }
        composable("emoji") {
            SettingsScreen("Emoji", { navController.popBackStack() }) {
                EmojiSettings(repository, settings) { navController.navigate(it) }
            }
        }
        composable("tools") {
            SettingsScreen("Tools", { navController.popBackStack() }) {
                ToolsSettings(repository, settings) { tool -> navController.navigate("tool/${tool.name}") }
            }
        }
        composable("tool/{toolName}") { backStackEntry ->
            val tool = backStackEntry.arguments?.getString("toolName")
                ?.let { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            if (tool != null) {
                SettingsScreen(toolTitle(tool), { navController.popBackStack() }) {
                    ToolDetailSettings(repository, settings, tool) { route ->
                        navController.navigate(route)
                    }
                }
            }
        }
        composable("accessibility") {
            SettingsScreen("Accessibility", { navController.popBackStack() }) {
                AccessibilitySettings(
                    repository, settings,
                    onOpenFonts = { navController.navigate("fonts") },
                    onOpenLayout = { navController.navigate("layout") },
                    onOpenKeyPress = { navController.navigate("keypress") },
                )
            }
        }
        composable("privacy") {
            SettingsScreen("Privacy", { navController.popBackStack() }) {
                PrivacySettings(repository, settings)
            }
        }
        composable("rows") {
            SettingsScreen("Rows & bars", { navController.popBackStack() }) {
                RowsSettings(repository, settings) { navController.navigate(it) }
            }
        }
        composable("symbol_set_edit/{setId}") { backStackEntry ->
            val setId = backStackEntry.arguments?.getString("setId").orEmpty()
            SettingsScreen("Edit symbol set", { navController.popBackStack() }) {
                SymbolSetEditor(repository, settings, setId) { navController.popBackStack() }
            }
        }
        composable("modes") {
            SettingsScreen("Keyboard modes", { navController.popBackStack() }) {
                ModesSettings(repository, settings) { navController.navigate(it) }
            }
        }
        composable("mode_edit/{modeId}") { backStackEntry ->
            val modeId = backStackEntry.arguments?.getString("modeId").orEmpty()
            SettingsScreen("Edit mode", { navController.popBackStack() }) {
                ModeEditor(repository, settings, modeId) { navController.popBackStack() }
            }
        }
        composable("about") {
            SettingsScreen("About", { navController.popBackStack() }) {
                AboutSettings(
                    onOpenLicenses = { navController.navigate("licenses") },
                    onOpenLicenseText = { navController.navigate("license_text/$it") },
                    onOpenDebugLog = { navController.navigate("debug_log") },
                )
            }
        }
        composable("debug_log") {
            SettingsScreen("Diagnostics", { navController.popBackStack() }) {
                DebugLogScreen()
            }
        }
        composable("licenses") {
            SettingsScreen("Open-source licences", { navController.popBackStack() }) {
                LicensesScreen { navController.navigate("license_text/$it") }
            }
        }
        composable("license_text/{asset}") { backStackEntry ->
            val asset = backStackEntry.arguments?.getString("asset").orEmpty()
            SettingsScreen("Licence", { navController.popBackStack() }) {
                LicenseTextScreen(asset)
            }
        }
    }
}

// ---- home / setup ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(settings: KeyboardSettings, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("WM Keyboard") },
                actions = {
                    IconButton(onClick = { onNavigate("search") }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SetupCard(context)
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup("Typing") {
                item {
                    HomeItem(
                        Icons.Outlined.Keyboard, "Typing",
                        "Autocorrect, suggestions, gestures",
                    ) { onNavigate("typing") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.TouchApp, "Key press",
                        "Haptics, key popup, long-press shortcuts",
                    ) { onNavigate("keypress") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Language, "Languages",
                        "English, বাংলা (Avro phonetic, প্রভাত, জাতীয়)",
                    ) { onNavigate("languages") }
                }
            }
            SettingsGroup("Keyboard") {
                item {
                    HomeItem(
                        Icons.Outlined.Palette, "Appearance",
                        "Themes, fonts, toolbar style",
                    ) { onNavigate("appearance") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.AspectRatio, "Layout & size",
                        "Key size, number row, one-handed, split & floating",
                    ) { onNavigate("layout") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.GridOn, "Key layouts",
                        "Design your own key grid, or start from a built-in one",
                    ) { onNavigate("keymaps") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.ViewAgenda, "Rows & bars",
                        "Symbol row, emoji row, row order & symbol sets",
                    ) { onNavigate("rows") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Tune, "Keyboard modes",
                        "Per-app setups: email, browser, coding, passwords",
                    ) { onNavigate("modes") }
                }
            }
            SettingsGroup("Features") {
                item {
                    HomeItem(
                        Icons.Outlined.EmojiEmotions, "Emoji",
                        "Suggestions, emoji row, emoji style, favourites",
                    ) { onNavigate("emoji") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Widgets, "Tools",
                        "Flashlight, compass, snippets, calendar & more",
                    ) { onNavigate("tools") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Extension, "Addons",
                        "Install themes, layouts, fonts and more from the web",
                    ) { onNavigate("addons") }
                }
            }
            SettingsGroup("Accessibility") {
                item {
                    HomeItem(
                        Icons.Outlined.Accessibility, "Accessibility",
                        "Contrast, colour vision, TalkBack, reduced motion",
                    ) { onNavigate("accessibility") }
                }
            }
            SettingsGroup("Data") {
                item {
                    HomeItem(
                        Icons.Outlined.Security, "Privacy",
                        "On-device learning, incognito",
                    ) { onNavigate("privacy") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Save, "Backup & restore",
                        "Export your settings to a file, or restore them",
                    ) { onNavigate("backup") }
                }
            }
            SettingsGroup("About") {
                item {
                    HomeItem(
                        Icons.Outlined.Info, "About",
                        "Version, licence, open-source notices",
                    ) { onNavigate("about") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun SetupCard(context: Context, onReady: (() -> Unit)? = null) {
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    // The IME picker is a system dialog, so the activity never pauses or
    // resumes when the user switches keyboards — poll while visible to
    // keep the card's state honest.
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            refresh++
            delay(1000)
        }
    }
    val enabled = remember(refresh) {
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }
    val selected = remember(refresh) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/') == context.packageName
    }
    // Fires once per transition into the ready state, so the caller can
    // advance onboarding without the user tapping Next after returning
    // from Settings — mirrors how other keyboard apps auto-continue.
    LaunchedEffect(enabled, selected) {
        if (enabled && selected) onReady?.invoke()
    }

    if (enabled && selected) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "WM Keyboard is your active keyboard.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Setup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (enabled) "WM Keyboard is enabled. Use the button below to switch to it."
                else "Enable WM Keyboard in system settings, then select it as your input method.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                if (!enabled) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }) { Text("Enable keyboard") }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = { imm.showInputMethodPicker() }) {
                    Text("Switch keyboard")
                }
            }
        }
    }
}

@Composable
private fun HomeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

// ---- shared scaffold & group card system ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    // A highlight that found no matching row on this screen (the searched
    // entry was the screen itself, or its row is conditionally hidden) must
    // not survive to flash something unrelated on the next screen — unless it
    // was armed *from* this screen on the way out, which is what an addon's
    // Use button does.
    val highlightSerial = remember(title) { SettingsHighlight.serial }
    DisposableEffect(title) { onDispose { SettingsHighlight.clearIfUnchanged(highlightSerial) } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Collects the rows of one visually grouped card stack — the modern
 * Material settings look: each row sits on its own surface with tiny
 * gaps between rows, large rounded corners at the group's ends and
 * small ones inside.
 */
internal class SettingsGroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
internal fun SettingsGroup(
    title: String? = null,
    builder: SettingsGroupScope.() -> Unit,
) {
    // The builder runs during composition, so rows may be added
    // conditionally on snapshot state (e.g. sliders that appear only
    // while their feature's toggle is on).
    val scope = SettingsGroupScope().apply(builder)
    if (scope.items.isEmpty()) return
    // A named group is a scroll target in its own right. Some things the user
    // arrives at from search — or from an addon's Use button — are a whole
    // section rather than one row: "Icon pack", "Your packs", "Installed
    // fonts". Unnamed groups have nothing to match on and stay plain.
    MaybeHighlightable(title) {
        if (title != null) SectionHeader(title)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            scope.items.forEachIndexed { index, row ->
                val top = if (index == 0) 24.dp else 6.dp
                val bottom = if (index == scope.items.lastIndex) 24.dp else 6.dp
                Surface(
                    shape = RoundedCornerShape(
                        topStart = top, topEnd = top,
                        bottomStart = bottom, bottomEnd = bottom,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) { row() }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** [HighlightableRow] when there is a title to match on, the content otherwise. */
@Composable
private fun MaybeHighlightable(title: String?, content: @Composable () -> Unit) {
    if (title == null) content() else HighlightableRow(title, content)
}

/** ListItem colors that let the group card's surface show through. */
@Composable
internal fun transparentListColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** "?" affordance that opens a dialog with the full explanation of a setting. */
@Composable
internal fun InfoButton(title: String, detail: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "More about $title",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/**
 * A permission's grant state, re-read every time the settings screen comes
 * back to the foreground. Both the runtime permissions and the special ones
 * (Usage Access) are granted on a system screen we leave the app for, so a
 * plain read during composition stays stale until something unrelated
 * recomposes — which is what made the "permission required" rows outlive the
 * grant.
 */
@Composable
internal fun rememberGrantState(check: (Context) -> Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(check(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = check(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** The permission that lets the clipboard read the user's screenshots. */
private val ImagesPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasImagesPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, ImagesPermission) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether the user granted Usage Access — a special permission, so it is an
 * app-op rather than a runtime grant. Mirrors the IME's own check.
 */
private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return false
    val mode = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
            )
        }
    }.getOrDefault(AppOpsManager.MODE_ERRORED)
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        // Aligns with the text inside group rows: 16dp group margin
        // plus the rows' own 16dp content inset.
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** Free-standing explanatory text aligned with group content. */
@Composable
internal fun CaptionText(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

/** A navigation row: title, optional subtitle, optional current value, chevron. */
@Composable
internal fun NavRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    HighlightableRow(title) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it) } },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value != null) {
                        Text(
                            value,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = transparentListColors(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
    }
}

@Composable
internal fun ToggleSetting(
    title: String,
    subtitle: String?,
    checked: Boolean,
    info: String? = null,
    onChange: (Boolean) -> Unit,
) {
    HighlightableRow(title) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it) } },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (info != null) InfoButton(title, info)
                    Switch(checked = checked, onCheckedChange = onChange)
                }
            },
            colors = transparentListColors(),
        )
    }
}

/**
 * How often a drag in progress is pushed through [SliderSetting]'s `onChange`,
 * i.e. written to DataStore. Live previews still follow the finger closely, but
 * a 100-pixel drag no longer queues 100 preference writes (each of which
 * recomposes the whole settings screen).
 */
private const val SLIDER_WRITE_INTERVAL_MS = 40L

/**
 * The live position of a settings slider, held locally so the thumb follows the
 * finger instead of the stored value: routing every touch event through a
 * DataStore write and waiting for the settings flow to come back made the thumb
 * visibly trail. Create one with [rememberLiveSlider], read [value] for both the
 * thumb and the readout, and hand [onDrag]/[onRelease] to the `Slider`.
 */
@Stable
internal class LiveSliderState(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    internal var dragging by mutableStateOf(false)
        private set

    /** Replaced on every composition so the latest lambda is always called. */
    internal var commit: (Float) -> Unit = {}

    internal fun adopt(external: Float) {
        if (!dragging) value = external
    }

    fun onDrag(next: Float) {
        dragging = true
        value = next
    }

    fun onRelease() {
        dragging = false
        commit(value)
    }
}

/**
 * A [LiveSliderState] wired to [value] and [onChange]. Writes are throttled to
 * one per [SLIDER_WRITE_INTERVAL_MS] while dragging — enough for anything
 * previewing the setting to keep up, without queueing a preference write (and a
 * recomposition of the whole screen) per touch event — with a final write when
 * the finger lifts. [value] is adopted only while no drag is in progress, so an
 * edit from elsewhere (a reset, another screen showing the same setting) still
 * moves the thumb but the user's own drag is never fought.
 */
@Composable
internal fun rememberLiveSlider(value: Float, onChange: (Float) -> Unit): LiveSliderState {
    val state = remember { LiveSliderState(value) }
    state.commit = onChange
    LaunchedEffect(value) { state.adopt(value) }
    LaunchedEffect(state) {
        snapshotFlow { state.value }
            .conflate()
            .collect {
                if (state.dragging) state.commit(it)
                delay(SLIDER_WRITE_INTERVAL_MS)
            }
    }
    return state
}

/**
 * A labelled slider row. [display] formats the *live* value rather than taking a
 * pre-rendered string, so the readout tracks the thumb instead of the stored
 * setting; see [rememberLiveSlider] for the rest.
 */
@Composable
internal fun SliderSetting(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    info: String? = null,
    onChange: (Float) -> Unit,
) {
    val slider = rememberLiveSlider(value, onChange)
    HighlightableRow(title) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (info != null) InfoButton(title, info)
                Spacer(Modifier.weight(1f))
                Text(display(slider.value), style = MaterialTheme.typography.labelLarge)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = slider.value,
                onValueChange = slider::onDrag,
                onValueChangeFinished = slider::onRelease,
                valueRange = range,
            )
        }
    }
}

/**
 * Restores the toolbar's default pins ([DefaultToolbarTools]) from Settings —
 * the global set. A mode's own pinned toolbar is reset from that mode's
 * editor (Keyboard modes → the mode → turn off "Custom pinned tools").
 * Confirms first, since it discards whatever the user dragged onto the bar.
 */
@Composable
private fun ResetPinnedToolsSetting(repository: SettingsRepository, scope: CoroutineScope) {
    var confirm by remember { mutableStateOf(false) }
    HighlightableRow("Reset pinned tools") {
        ListItem(
            headlineContent = { Text("Reset pinned tools") },
            supportingContent = { Text("Restore the default toolbar tools") },
            trailingContent = {
                OutlinedButton(onClick = { confirm = true }) { Text("Reset") }
            },
            colors = transparentListColors(),
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset pinned tools?") },
            text = {
                Text(
                    "The toolbar goes back to its default tools. Tools you pinned or " +
                        "removed by hand are forgotten. This affects the global toolbar; a " +
                        "mode's own toolbar is reset from that mode's editor.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { repository.setToolbarTools(DefaultToolbarTools) }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** A titled single-choice row of segmented buttons over [options]. */
@Composable
internal fun <T> ChoiceSetting(
    title: String,
    subtitle: String? = null,
    info: String? = null,
    options: List<Pair<T, String>>,
    selected: T,
    onChange: (T) -> Unit,
) {
    HighlightableRow(title) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (info != null) InfoButton(title, info)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = selected == option,
                        onClick = { onChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One spacebar-swipe slot (quick or hold+swipe): nothing / language / cursor. */
@Composable
private fun SpaceSwipeSetting(
    title: String,
    subtitle: String,
    info: String,
    value: SpaceSwipeAction,
    onChange: (SpaceSwipeAction) -> Unit,
) {
    ChoiceSetting(
        title = title,
        subtitle = subtitle,
        info = info,
        options = SpaceSwipeAction.entries.map { action ->
            action to when (action) {
                SpaceSwipeAction.NONE -> "Nothing"
                SpaceSwipeAction.LANGUAGE -> "Language"
                SpaceSwipeAction.CURSOR -> "Cursor"
                SpaceSwipeAction.NUMPAD -> "Numpad"
            }
        },
        selected = value,
        onChange = onChange,
    )
}

// ---- typing ----

@Composable
private fun TypingSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenDictionary: () -> Unit,
    onOpenCustomDictionaries: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenHardwareShortcuts: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Automatic corrections") {
        item {
            ToggleSetting(
                "Autocorrect", "Fix typos automatically when you press space", settings.autocorrect,
                info = "When you press space, the word you just typed is checked against the " +
                    "dictionary. If it looks like a slip of an obviously more common word, it is " +
                    "replaced. Words you have taught the keyboard are never \"corrected\" away, " +
                    "and autocorrect stays off in password fields.",
            ) { scope.launch { repository.setAutocorrect(it) } }
        }
        if (settings.autocorrect) {
            item {
                SliderSetting(
                    "Autocorrect confidence",
                    subtitle = "How sure a correction must be before it is applied",
                    value = settings.autocorrectConfidence,
                    range = 1.5f..10f,
                    display = { "×%.1f".format(it) },
                    info = "A correction only fires when the best candidate outscores the " +
                        "runner-up by this factor. Low corrects eagerly and catches more " +
                        "typos, but guesses wrong more often; high only corrects when the " +
                        "word is nearly unambiguous. Corrections that two sources agree on " +
                        "are applied regardless.",
                ) { scope.launch { repository.setAutocorrectConfidence(it) } }
            }
            item {
                ToggleSetting(
                    "Undo autocorrect with backspace",
                    "Backspace right after a correction restores what you typed",
                    settings.revertAutocorrectOnBackspace,
                    info = "Pressing backspace immediately after autocorrect changed a word " +
                        "puts your original spelling back and teaches it to the keyboard, so " +
                        "it is not corrected again. Turn this off to have backspace always " +
                        "just delete a character.",
                ) { scope.launch { repository.setRevertAutocorrectOnBackspace(it) } }
            }
            item {
                ToggleSetting(
                    "Skip all-caps words",
                    "Leave words typed in capitals alone",
                    settings.autocorrectSkipAllCaps,
                    info = "Acronyms and shouting (ASAP, OFC, NOOO) are usually typed in " +
                        "capitals on purpose, so autocorrect leaves any word in all caps " +
                        "alone. Turn this off to have such words corrected like any other.",
                ) { scope.launch { repository.setAutocorrectSkipAllCaps(it) } }
            }
            item {
                ToggleSetting(
                    "Block offensive words",
                    "Keep profanity and slurs out of suggestions",
                    settings.suggestionStrip.blockOffensiveWords,
                    info = "Potentially offensive words are never offered in the suggestion " +
                        "strip and a neutral typo is never autocorrected into one. You can " +
                        "still type and commit any word yourself — this only stops the " +
                        "keyboard from suggesting them.",
                ) { scope.launch { repository.setBlockOffensiveWords(it) } }
            }
        }
        item {
            ToggleSetting(
                "Fix missing apostrophes", "arent → aren't, im → I'm, dont → don't",
                settings.autoApostrophe,
                info = "When you press space after a contraction typed without its " +
                    "apostrophe (arent, isnt, youre, oclock…), the apostrophe is put " +
                    "back — and a lone \"i\" becomes \"I\". Words that are also real " +
                    "English words without the apostrophe (its, well, ill, shell…) are " +
                    "deliberately left alone. Works independently of autocorrect.",
            ) { scope.launch { repository.setAutoApostrophe(it) } }
        }
        item {
            ToggleSetting(
                "Auto-capitalize", "Capitalize the first letter of sentences", settings.autoCapitalize,
                info = "Shift turns on by itself at the start of a text field and after " +
                    "sentence-ending punctuation (. ! ? ।). It only applies in fields that ask " +
                    "for sentence capitalization, and only in English mode. Caps lock is never " +
                    "changed automatically.",
            ) { scope.launch { repository.setAutoCapitalize(it) } }
        }
        item {
            ToggleSetting(
                "Double-space period", "Double-tapping space inserts “. ”", settings.doubleSpacePeriod,
                info = "Tapping space twice quickly at the end of a word replaces the first " +
                    "space with a period, so you can end sentences without visiting the symbols " +
                    "layout.",
            ) { scope.launch { repository.setDoubleSpacePeriod(it) } }
        }
        item {
            ToggleSetting(
                "Double-space tab", "Double-tapping space inserts a tab", settings.doubleSpaceTab,
                info = "Tapping space twice quickly replaces the first space with a tab " +
                    "character — handy for indentation and forms. While this is on it takes " +
                    "priority over double-space period.",
            ) { scope.launch { repository.setDoubleSpaceTab(it) } }
        }
        item {
            ToggleSetting(
                "Auto-space after punctuation",
                "Typing . , ? ! ; or : adds the space after it",
                settings.autoSpaceAfterPunctuation,
                info = "Saves the spacebar trip at the end of every clause: \"hello,\" " +
                    "becomes \"hello, \" on its own. Runs of marks (\"...\", \"?!\") stay " +
                    "together, pressing space yourself does not double the space up, and " +
                    "shift right afterwards takes it back. Structured fields — passwords, " +
                    "email addresses, web addresses, number and phone pads — are left alone, " +
                    "since a space there is a typo rather than a courtesy.",
            ) { scope.launch { repository.setAutoSpaceAfterPunctuation(it) } }
        }
        item {
            ToggleSetting(
                "Space after a suggestion",
                "Add a space when you pick a word from the strip",
                settings.suggestionStrip.autoSpaceAfterSuggestion,
                info = "On (the default), tapping a suggestion commits the word and a trailing " +
                    "space so the next word starts cleanly. Turn it off to commit the word bare " +
                    "— for languages or fields where a trailing space is wrong more often than " +
                    "right. A word you go back and resume never gets a doubled space either way.",
            ) { scope.launch { repository.setAutoSpaceAfterSuggestion(it) } }
        }
        item {
            ToggleSetting(
                "Wrap selection with brackets",
                "Typing ( [ { < \" ' or ` around selected text wraps it",
                settings.textEditing.wrapSelectionWithPair,
                info = "With text selected, pressing a bracket, brace or quote key surrounds " +
                    "the selection with the pair — select \"foo\", press ( and you get " +
                    "\"(foo)\" — instead of replacing it. The wrapped text stays selected so " +
                    "you can wrap it again. Turn off to have those keys always replace the " +
                    "selection, like any other character.",
            ) { scope.launch { repository.setWrapSelectionWithPair(it) } }
        }
        item {
            ToggleSetting(
                "Shift re-cases selection",
                "Shift with text selected cycles lowercase, Title, UPPERCASE",
                settings.textEditing.recapitalizeSelectionWithShift,
                info = "With text selected, tapping shift changes its case instead of arming " +
                    "shift for the next letter — pressing it repeatedly cycles lowercase → " +
                    "Title Case → UPPERCASE. Nothing changes for caseless scripts like " +
                    "Bengali. Turn off to keep shift meaning \"capitalize the next character\" " +
                    "even while text is selected.",
            ) { scope.launch { repository.setRecapitalizeSelectionWithShift(it) } }
        }
    }

    SettingsGroup("Suggestions") {
        item {
            ToggleSetting(
                "Suggestions", "Show word predictions above the keyboard", settings.suggestions,
                info = "Shows up to three candidates above the keys while you type: completions, " +
                    "corrections, and next-word predictions learned from your typing. Tap one to " +
                    "insert it followed by a space.",
            ) { scope.launch { repository.setSuggestions(it) } }
        }
        item {
            ToggleSetting(
                "Punctuation suggestions",
                "Quick . , ? ! ' chips beside the word candidates",
                settings.suggestionStrip.punctuation,
                info = "Adds a short row of common punctuation to the end of the suggestion " +
                    "strip while candidates are showing, so a full stop or comma is one tap " +
                    "away without switching to the symbols layout. Tapping one behaves exactly " +
                    "like typing that key. When an emoji prediction is offered it takes the " +
                    "tail instead.",
            ) { scope.launch { repository.setPunctuationSuggestions(it) } }
        }
        item {
            ToggleSetting(
                "Suggestions in every field",
                "Show the suggestion strip even where apps ask it hidden",
                settings.showSuggestionsInAllFields,
                info = "Some apps — Instagram, Google Keep and others — tell the keyboard to " +
                    "hide the suggestion strip on ordinary text fields. On (the default), the " +
                    "strip is shown anyway, the way most keyboards quietly do; turn off to " +
                    "respect the app and hide it. Either way, autocorrect, gesture typing and " +
                    "Bengali (Avro) composing keep working — those are no longer tied to the " +
                    "strip. Password fields and number pads never show suggestions.",
            ) { scope.launch { repository.setShowSuggestionsInAllFields(it) } }
        }
        item {
            ToggleSetting(
                "Suggestions bar always visible",
                "Keep the suggestion strip up even before you type",
                settings.suggestionStrip.suggestionsFirst,
                info = "Normally the top bar rests on the toolbar and only switches to " +
                    "suggestions while candidates exist. With this on, the suggestion strip is " +
                    "the resting state instead — next-word predictions are always one glance " +
                    "away — and the chevron on its left opens the toolbar when you need a tool.",
            ) { scope.launch { repository.setSuggestionsFirst(it) } }
        }
        item {
            ToggleSetting(
                "Best suggestion in the middle",
                "Show the top candidate in the center slot",
                settings.suggestionStrip.suggestionPrimaryCenter,
                info = "The strongest candidate (the one autocorrect would pick) sits in the " +
                    "middle of the strip with the runner-up on its left — the layout most " +
                    "keyboards use. Turn off to rank candidates left to right instead.",
            ) { scope.launch { repository.setSuggestionPrimaryCenter(it) } }
        }
        item {
            val context = LocalContext.current
            val contactsPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) scope.launch { repository.setContactSuggestions(true) }
            }
            ToggleSetting(
                "Suggest contact names",
                "Complete names from your contacts as you type",
                settings.contactSuggestions,
                info = "Words from your contacts' names complete like dictionary words " +
                    "(\"was\" → Wasi) and chain onto each other (after Wasi, the surname is " +
                    "offered next). Names are read into memory only — nothing is stored or " +
                    "sent anywhere, and autocorrect will never \"fix\" a name it knows. " +
                    "Needs the Contacts permission.",
            ) { enabled ->
                when {
                    !enabled -> scope.launch { repository.setContactSuggestions(false) }
                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED ->
                        scope.launch { repository.setContactSuggestions(true) }
                    else -> contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }
        item {
            val context = LocalContext.current
            val emailPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) scope.launch { repository.setContactEmailSuggestions(true) }
            }
            ToggleSetting(
                "Suggest contact emails",
                "Complete a contact's email as you type the start of it",
                settings.contactEmailSuggestions,
                info = "Type the start of a contact's email address (\"john\") and their full " +
                    "address (john.doe@gmail.com) is offered in the strip to complete. " +
                    "Addresses are read into memory only — nothing is stored or sent " +
                    "anywhere, and autocorrect will never touch them. Needs the Contacts " +
                    "permission.",
            ) { enabled ->
                when {
                    !enabled -> scope.launch { repository.setContactEmailSuggestions(false) }
                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED ->
                        scope.launch { repository.setContactEmailSuggestions(true) }
                    else -> emailPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }
        if (settings.contactEmailSuggestions) {
            item {
                ToggleSetting(
                    "Contact emails in email fields too",
                    "Show them even where the app hides suggestions",
                    settings.contactEmailSuggestionsInEmailFields,
                    info = "Email fields normally tell the keyboard to hide the suggestion " +
                        "strip, which would suppress these completions just where they are " +
                        "most useful. With this on, contact-email completions still appear in " +
                        "email fields; other suggestions stay hidden there as before. Turn " +
                        "off to respect the field and only complete emails in ordinary text.",
                ) { scope.launch { repository.setContactEmailSuggestionsInEmailFields(it) } }
            }
        }
        item {
            ToggleSetting(
                "Suggest app names",
                "Complete the names of installed apps as you type",
                settings.appNameSuggestions,
                info = "Words from the names of your installed apps complete like dictionary " +
                    "words (\"sign\" → Signal), and autocorrect will never \"fix\" one. They " +
                    "rank below contact names, since ordinary words like Files and Clock are " +
                    "app names too. Read into memory only — nothing is stored or sent " +
                    "anywhere, and no permission is needed.",
            ) { scope.launch { repository.setAppNameSuggestions(it) } }
        }
        item {
            ToggleSetting(
                "Inline emoji search",
                "Type \":\" then a word to find emoji — :smi → 😄",
                settings.inlineEmojiSearch,
                info = "Typing a colon at the start of a word turns the suggestion strip into " +
                    "an emoji search: \":cat\" offers 🐱, and tapping one replaces what you " +
                    "typed. Backspacing over the colon returns to normal word suggestions, " +
                    "and pressing space leaves the text exactly as typed.",
            ) { scope.launch { repository.setInlineEmojiSearch(it) } }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            item {
                ToggleSetting(
                    "Password manager suggestions",
                    "Show saved logins from your autofill service in the strip",
                    settings.inlineAutofill,
                    info = "When a login field is focused, your password manager can offer " +
                        "saved entries as chips in the suggestion strip instead of a separate " +
                        "popup. The chips are drawn by the manager itself — the keyboard is " +
                        "only given their size, and never sees the username or password " +
                        "inside them. Turned off automatically in incognito mode. " +
                        "Requires Android 11 or newer and a password manager set as your " +
                        "system autofill service.",
                ) { scope.launch { repository.setInlineAutofill(it) } }
            }
        }
        item {
            ToggleSetting(
                "Smart key-hit detection",
                "Nudge boundary taps toward the letter you likely meant",
                settings.layoutBehavior.smartHitDetection,
                info = "As you type a word, the dictionary predicts which letters are likely to " +
                    "come next, and the touch target of each key is quietly widened toward them. " +
                    "A tap that lands just inside a neighbouring key still commits the letter you " +
                    "meant — but only near the shared edge; a deliberate press in the middle of a " +
                    "key is never changed. Only affects the letters layer, and needs Suggestions " +
                    "on so the prediction has something to work with.",
            ) { scope.launch { repository.setSmartHitDetection(it) } }
        }
        item {
            NavRow(
                "Personal dictionary",
                "Words the keyboard has learned — review, remove, add your own",
                onClick = onOpenDictionary,
            )
        }
        item {
            NavRow(
                "Custom dictionaries",
                "Import your own word lists, per language",
                onClick = onOpenCustomDictionaries,
            )
        }
        item {
            val count = settings.suggestionBlacklist.size
            NavRow(
                "Suggestion blacklist",
                if (count == 0) {
                    "Words to never suggest or autocorrect to"
                } else {
                    "$count word${if (count == 1) "" else "s"} never suggested"
                },
                onClick = onOpenBlacklist,
            )
        }
    }

    SettingsGroup("Smart chips") {
        item {
            ToggleSetting(
                "Smart chips",
                "Answer sums, conversions and tool keywords in the strip",
                settings.smartSuggestions,
                info = "When what you have typed is something a tool can answer, the " +
                    "suggestion strip offers the answer instead of word candidates — " +
                    "tap it to type the result, or use the button on its right to open " +
                    "the full tool with the same numbers already loaded. Everything is " +
                    "recognised on-device; only exchange rates are fetched, and only " +
                    "once you type an amount in a currency.",
            ) { scope.launch { repository.setSmartSuggestions(it) } }
        }
        if (settings.smartSuggestions) {
            item {
                ToggleSetting(
                    "Calculate as you type",
                    "\"12*4\" offers 48",
                    settings.smartCalc,
                ) { scope.launch { repository.setSmartCalc(it) } }
            }
            item {
                ToggleSetting(
                    "Convert currencies",
                    "\"150 usd\", \"150$\" or \"150 dollars\" offers the amount in ${settings.currencyTo}",
                    settings.smartCurrency,
                ) { scope.launch { repository.setSmartCurrency(it) } }
            }
            item {
                ToggleSetting(
                    "Convert units",
                    "\"1 ft\" offers the same length in metres",
                    settings.smartUnits,
                ) { scope.launch { repository.setSmartUnits(it) } }
            }
            item {
                ToggleSetting(
                    "Tool keywords",
                    "Typing \"wiki\" offers to open Wikipedia",
                    settings.smartToolKeywords,
                    info = "Each tool answers to a few words; type one on its own and " +
                        "the strip offers to open that tool, dropping the word you " +
                        "typed. The words are listed under each tool's own settings, " +
                        "where you can change or clear them.",
                ) { scope.launch { repository.setSmartToolKeywords(it) } }
            }
        }
    }

    SettingsGroup("Gestures") {
        item {
            ToggleSetting(
                "Gesture typing", "Swipe across letters to type a word", settings.gestureTyping,
                info = "Slide your finger from letter to letter without lifting; the word is " +
                    "committed when you lift. Alternate interpretations appear in the suggestion " +
                    "bar, so a wrong guess is one tap away from fixed. English only for now.",
            ) { scope.launch { repository.setGestureTyping(it) } }
        }
        // What a letter swipe does — glide a word or handwrite it. Full builds
        // only (needs the ML Kit handwriting model), and only relevant once
        // letter swipes are switched on above.
        if (BuildConfig.ENABLE_ML_KIT_HANDWRITING && settings.gestureTyping) {
            item {
                ChoiceSetting(
                    title = "Handwrite with swipes",
                    subtitle = "Draw letters over the keys instead of gliding",
                    info = "With this set to Handwrite, a swipe across the keys is treated as " +
                        "handwriting: draw a letter or word and it is recognized on a short " +
                        "pause and inserted, with other readings offered in the suggestion bar. " +
                        "Needs a handwriting model (Settings → Handwriting). A plain tap still " +
                        "types its key.",
                    options = listOf(
                        LetterSwipeAction.TYPE_WORDS to "Type words",
                        LetterSwipeAction.HANDWRITE to "Handwrite",
                    ),
                    selected = settings.letterSwipeAction,
                    onChange = { scope.launch { repository.setLetterSwipeAction(it) } },
                )
            }
        }
        if (settings.gestureTyping) {
            // Glide-word only: crossing the spacebar to chain words has no
            // meaning when a swipe draws handwriting instead.
            if (settings.letterSwipeAction == LetterSwipeAction.TYPE_WORDS) {
                item {
                    ToggleSetting(
                        "Glide across spacebar",
                        "Swipe over space to keep gliding the next word",
                        settings.gesture.spaceGlideMultiWord,
                        info = "Without lifting your finger, glide a word, slide across the spacebar, " +
                            "then glide the next — each crossing commits the word so far and a space, " +
                            "so a whole phrase lands in one stroke. Off treats a swipe that crosses " +
                            "the spacebar as a single word.",
                    ) { scope.launch { repository.setGestureSpaceMultiWord(it) } }
                }
            }
            item {
                SliderSetting(
                    "Swipe start distance",
                    subtitle = "How far to move before a glide begins — lower is more sensitive",
                    value = settings.gesture.startThresholdSlop,
                    range = 0.5f..4f,
                    display = { "${"%.1f".format(it)}×" },
                    info = "The finger travel that turns a press into a glide, as a multiple of " +
                        "the system's touch slop. Lower starts gliding sooner (more sensitive) but " +
                        "can trip on a stationary tap; higher needs a more deliberate swipe.",
                ) { scope.launch { repository.setGestureStartThresholdSlop(it) } }
            }
            // Glide-word only: the guard raises the swipe-start bar, which never
            // runs in handwrite mode (there is no word glide to suppress).
            if (settings.letterSwipeAction == LetterSwipeAction.TYPE_WORDS) {
                item {
                    SliderSetting(
                        "Cooldown after typing",
                        subtitle = "Briefly resist starting a glide right after a tap",
                        value = settings.gesture.postTypeCooldownMs.toFloat(),
                        range = 0f..500f,
                        display = { if (it.roundToInt() == 0) "Off" else "${it.roundToInt()} ms" },
                        info = "Just after you tap a key, a stray slide off it can be misread as a " +
                            "swipe-word. During this window a glide has to travel further before it " +
                            "takes over, and the extra distance fades away across the window, so fast " +
                            "tapping stays clean while a deliberate swipe still starts. Higher is " +
                            "safer against accidents but makes gliding right after typing slower to " +
                            "begin; 0 turns the guard off.",
                    ) { scope.launch { repository.setGesturePostTypeCooldownMs(it.roundToInt()) } }
                }
            }
            // Handwrite-with-swipes only: window after a drawn stroke in which a
            // tap is grabbed as an ink dot rather than typing.
            if (BuildConfig.ENABLE_ML_KIT_HANDWRITING &&
                settings.letterSwipeAction == LetterSwipeAction.HANDWRITE
            ) {
                item {
                    SliderSetting(
                        "Dot leeway after drawing",
                        subtitle = "Time to tap a dot or cross before it types instead",
                        value = settings.gesture.handwriteDotCooldownMs.toFloat(),
                        range = 0f..1500f,
                        display = { if (it.roundToInt() == 0) "Off" else "${it.roundToInt()} ms" },
                        info = "Letters like i, j and t need a separate mark after the main stroke. " +
                            "For this long after you draw a stroke, a tap over the letters is added " +
                            "to the same character as another stroke (the dot or cross) instead of " +
                            "typing that key. A tap after the window types as normal; 0 turns the " +
                            "leeway off.",
                    ) { scope.launch { repository.setGestureHandwriteDotCooldownMs(it.roundToInt()) } }
                }
            }
            item {
                SliderSetting(
                    "Trail width",
                    subtitle = "Thickness of the glide trail",
                    value = settings.gesture.trailWidthDp,
                    range = 2f..24f,
                    display = { "${it.roundToInt()} dp" },
                ) { scope.launch { repository.setGestureTrailWidthDp(it) } }
            }
            item {
                SliderSetting(
                    "Trail length",
                    subtitle = "How long the trail lingers behind your finger",
                    value = settings.gesture.trailDurationMs.toFloat(),
                    range = 100f..1200f,
                    display = { "${it.roundToInt()} ms" },
                ) { scope.launch { repository.setGestureTrailDurationMs(it.roundToInt()) } }
            }
            item {
                SliderSetting(
                    "Trail opacity",
                    value = settings.gesture.trailOpacity,
                    range = 0.1f..1f,
                    display = { "${(it * 100).roundToInt()}%" },
                ) { scope.launch { repository.setGestureTrailOpacity(it) } }
            }
        }
        item {
            SpaceSwipeSetting(
                title = "Quick swipe on spacebar",
                subtitle = "A swipe that starts moving right away",
                info = "Slide horizontally on the spacebar without pausing. \"Language\" cycles " +
                    "your enabled input modes with a live preview above the spacebar — release " +
                    "to switch, and holding the spacebar just past a tap shows the picker even " +
                    "before you swipe. \"Cursor\" moves the text cursor one character per step. " +
                    "A tap without movement always types a space.",
                value = settings.spaceShortSwipe,
            ) { scope.launch { repository.setSpaceShortSwipe(it) } }
        }
        item {
            SpaceSwipeSetting(
                title = "Hold + swipe on spacebar",
                subtitle = "Hold the spacebar briefly, then swipe",
                info = "Hold the spacebar past the long-press delay first, then slide. This " +
                    "gives the spacebar a second, independent swipe action — for example a " +
                    "quick swipe to switch language and a hold + swipe to move the cursor. " +
                    "Both may also be set to the same action.",
                value = settings.spaceLongSwipe,
            ) { scope.launch { repository.setSpaceLongSwipe(it) } }
        }
        // 2-D cursor pad only makes sense once a slide is set to cursor control.
        if (settings.spaceShortSwipe == SpaceSwipeAction.CURSOR ||
            settings.spaceLongSwipe == SpaceSwipeAction.CURSOR
        ) {
            item {
                ToggleSetting(
                    "2-D cursor touchpad",
                    "Cursor slide also moves up and down, not just left and right",
                    settings.layoutBehavior.spaceCursor2d,
                    info = "Turns the spacebar cursor slide into a touchpad: as well as moving " +
                        "left and right, dragging up or down moves the cursor between lines. " +
                        "Only applies to the swipe slot(s) set to \"Cursor\" above.",
                ) { scope.launch { repository.setSpaceCursor2d(it) } }
            }
        }
        item {
            ToggleSetting(
                "Swipe down to hide",
                "A downward swipe on the spacebar dismisses the keyboard",
                settings.layoutBehavior.spaceSwipeDownHide,
                info = "Slide straight down on the spacebar to close the keyboard. Off by " +
                    "default so a stray vertical drag never dismisses it. When the 2-D cursor " +
                    "touchpad is on, downward drags move the cursor instead, so that takes " +
                    "precedence.",
            ) { scope.launch { repository.setSpaceSwipeDownHide(it) } }
        }
        if (settings.spaceShortSwipe == SpaceSwipeAction.LANGUAGE ||
            settings.spaceLongSwipe == SpaceSwipeAction.LANGUAGE
        ) {
            item {
                ToggleSetting(
                    "Arrows on spacebar",
                    "Hint that a swipe switches language",
                    settings.spacebarLanguageArrows,
                    info = "Draws a small ◀ and ▶ either side of the language name on the " +
                        "spacebar. They are only a hint — the swipe works either way — and stay " +
                        "hidden while a single input mode is enabled.",
                ) { scope.launch { repository.setSpacebarLanguageArrows(it) } }
            }
        }
        item {
            ChoiceSetting(
                "Spacebar shows",
                subtitle = "What the resting spacebar label displays",
                info = "Language shows the current language name, Layout the current layout name, " +
                    "Both shows \"Language (Layout)\". When one language has more than one layout " +
                    "enabled, the layout name is added anyway so you can tell them apart.",
                options = listOf(
                    SpacebarDisplay.LANGUAGE to "Language",
                    SpacebarDisplay.LAYOUT to "Layout",
                    SpacebarDisplay.BOTH to "Both",
                ),
                selected = settings.layoutBehavior.spacebarDisplay,
            ) { scope.launch { repository.setSpacebarDisplay(it) } }
        }
        item {
            TextFieldSetting(
                label = "Spacebar text",
                value = settings.spacebarLabel,
                hint = "Blank = current spacebar label. %s inserts it, e.g. \"— %s —\".",
            ) { repository.setSpacebarLabel(it) }
        }
    }

    SettingsGroup("Backspace") {
        item {
            ToggleSetting(
                "Swipe to delete words",
                "Drag sideways on backspace to delete whole words",
                settings.backspaceSwipeDelete,
                info = "Press backspace and slide left: each step of travel deletes one more " +
                    "word instead of one character. A plain tap or hold still deletes " +
                    "character by character.",
            ) { scope.launch { repository.setBackspaceSwipeDelete(it) } }
        }
    }

    SettingsGroup("Enter key") {
        item {
            ToggleSetting(
                "Shift + Enter types a newline",
                "Add a line break in a chat app instead of sending the message",
                settings.layoutBehavior.shiftEnterNewline,
                info = "Chat and search fields tell the keyboard that Enter means Send, Go or " +
                    "Search, so there is normally no way to put a line break in a message " +
                    "without sending it. With this on, pressing shift first — or holding shift " +
                    "on a physical keyboard — makes Enter type a real newline instead. The " +
                    "Enter key changes to the newline symbol while shift is armed, so you can " +
                    "see which one you are about to get.\n\n" +
                    "Only a shift you pressed yourself counts: the one auto-capitalize arms at " +
                    "the start of a message is ignored, so the first line of a message still " +
                    "sends. Caps lock is ignored too.\n\n" +
                    "Off by default — following the app's own Enter action is the standard " +
                    "behaviour.",
            ) { scope.launch { repository.setShiftEnterNewline(it) } }
        }
    }

    SettingsGroup("Volume keys") {
        item {
            ToggleSetting(
                "Volume cursor control",
                "Volume up and down move the text cursor",
                settings.volumeCursor,
                info = "While the keyboard is open, volume down moves the cursor one character " +
                    "left and volume up moves it one character right; hold a key to repeat. " +
                    "The volume keys behave normally everywhere else, and as soon as the " +
                    "keyboard closes.",
            ) { scope.launch { repository.setVolumeCursor(it) } }
        }
        if (settings.volumeCursor) {
            item {
                ToggleSetting(
                    "Release while audio plays",
                    "Keep volume control when something is playing",
                    settings.volumeCursorMediaAware,
                    info = "With music, a video or a podcast playing, the volume keys go back to " +
                        "changing the volume even with the keyboard open — so typing a reply " +
                        "never costs you the ability to turn the sound down. Cursor control " +
                        "returns once playback stops.\n\n" +
                        "Turn this off if you want the volume keys to always move the cursor " +
                        "while the keyboard is showing.",
                ) { scope.launch { repository.setVolumeCursorMediaAware(it) } }
            }
        }
    }

    SettingsGroup("Physical keyboard") {
        item {
            ToggleSetting(
                "Process hardware keys",
                "Transliterate, correct and suggest as you type on a physical keyboard",
                settings.hardwareKeyboardInput,
                info = "With a physical keyboard attached, keys flow through the same engine as " +
                    "the on-screen keyboard: phonetic layouts transliterate (typing \"ami\" gives " +
                    "\"আমি\"), words compose for suggestions, and autocorrect fires on space — the " +
                    "word you are typing shows underlined in the field until you finish it.\n\n" +
                    "Shortcuts (Ctrl+C, Ctrl+Z), the arrow and function keys, and everything in " +
                    "password fields always go straight to the app, whichever way this is set.\n\n" +
                    "Turn this off to type the raw characters straight into the field and let the " +
                    "physical keyboard's own layout own input entirely.",
            ) { scope.launch { repository.setHardwareKeyboardInput(it) } }
        }
        val hw = settings.hardwareKeyboard
        item {
            ToggleSetting(
                "Tool shortcuts",
                "Open tools from a physical keyboard without touching the screen",
                hw.shortcutsEnabled,
                info = "Press the shortcut key, then a letter: the letter opens that tool. " +
                    "T opens the full toolbox and ? shows the list of letters.\n\n" +
                    "The shortcut key is a double-tapped Ctrl by default, which no app uses — a " +
                    "lone Ctrl produces no character, and it is still passed through either way. " +
                    "Both the shortcut key and every letter can be changed below.",
            ) { scope.launch { repository.setHwShortcutsEnabled(it) } }
        }
        if (hw.shortcutsEnabled) {
            item {
                NavRow(
                    "Tool shortcuts list",
                    "Which letter opens which tool",
                    value = describeLeader(parseLeader(hw.leader) ?: DefaultLeader),
                    onClick = onOpenHardwareShortcuts,
                )
            }
        }
        item {
            ToggleSetting(
                "Arrow keys move a highlight",
                "Arrows move a highlight in tool panels, Enter picks it, Esc closes",
                hw.panelNavigation,
                info = "Inside a tool, the arrow keys move a highlight over the emojis, clips or " +
                    "results, Enter uses the highlighted one, and Tab moves between the search box, " +
                    "the category chips and the results.\n\n" +
                    "The highlight only appears once you press a key — it never shows up while " +
                    "you are using the keyboard by touch.",
            ) { scope.launch { repository.setHwPanelNavigation(it) } }
        }
        item {
            ToggleSetting(
                "Esc closes the tool",
                "Escape shuts an open tool instead of going to the app",
                hw.escClosesPanel,
                info = "Escape only ever closes something the keyboard itself has open. With no " +
                    "tool open it goes straight to the app, so it still stops a page loading or " +
                    "leaves insert mode in an editor.",
            ) { scope.launch { repository.setHwEscClosesPanel(it) } }
        }
        item {
            ChoiceSetting(
                "Number keys pick suggestions",
                subtitle = "Commit a suggestion by its number in the strip",
                info = "The suggestion strip cannot be tapped while you type on a physical " +
                    "keyboard, so a number can commit one instead.\n\n" +
                    "\"After the shortcut key\" is the safe choice: nothing else uses it. " +
                    "\"Alt + number\" is one keystroke fewer, but browsers, editors and chat apps " +
                    "use modifier+number to switch tabs and workspaces.",
                options = SuggestionHotkeyMode.entries.map { it to it.label },
                selected = hw.suggestionHotkeys,
            ) { scope.launch { repository.setHwSuggestionHotkeys(it) } }
        }
        item {
            ToggleSetting(
                "Show the keyboard for shortcuts",
                "A shortcut that opens a tool also brings the keyboard up",
                hw.autoShowUi,
                info = "A physical keyboard normally means no on-screen keyboard at all, which " +
                    "leaves a tool nowhere to draw. With this on, opening a tool by shortcut shows " +
                    "the keyboard, and closing the tool hides it again.",
            ) { scope.launch { repository.setHwAutoShowUi(it) } }
        }
    }
}

/**
 * The letter that opens each tool from a physical keyboard, plus the shortcut key
 * that arms them.
 *
 * The rows are every supported tool rather than a list the user builds, so there
 * is no "add" — a tool either has a letter or it does not, and the unbound ones
 * are still reachable through the toolbox.
 */
@Composable
private fun HardwareShortcutsSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val hw = settings.hardwareKeyboard
    val leader = parseLeader(hw.leader) ?: DefaultLeader
    var editingLeader by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ToolbarTool?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val tools = remember(hw.toolByLetter, settings.enabledTools) {
        val letterOf = hw.toolByLetter.entries.associate { (letter, tool) -> tool to letter }
        // Bound tools first, in letter order, so the table reads as "what my
        // keyboard does" before "what else could be bound".
        ToolbarTool.entries.filter(::isSupportedTool)
            .sortedWith(compareBy({ letterOf[it] == null }, { letterOf[it] ?: ' ' }, { it.name }))
    }
    val letterOf = hw.toolByLetter.entries.associate { (letter, tool) -> tool to letter }

    Column {
        CaptionText(
            "Press the shortcut key, then a letter. $ToolboxLetter opens the toolbox and " +
                "$CheatSheetLetter shows this list on the keyboard.",
        )
        SettingsGroup("Shortcut key") {
            item {
                NavRow(
                    "Shortcut key",
                    "What arms the tool letters",
                    value = describeLeader(leader),
                    onClick = { editingLeader = true },
                )
            }
        }
        SettingsGroup("Tools") {
            for (tool in tools) {
                item {
                    val letter = letterOf[tool]
                    ListItem(
                        colors = transparentListColors(),
                        headlineContent = { Text(toolTitle(tool)) },
                        supportingContent = if (tool !in settings.enabledTools) {
                            { CaptionText("Turned off in Tools") }
                        } else null,
                        leadingContent = {
                            SlotIcon(IconSlots.forTool(tool), contentDescription = null)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    letter?.toString() ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (letter == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                if (letter != null) {
                                    IconButton(onClick = {
                                        scope.launch { repository.setHwToolLetter(letter, null) }
                                    }) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Unbind ${toolTitle(tool)}",
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { editing = tool },
                    )
                }
            }
        }
        TextButton(
            onClick = { confirmReset = true },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Text("Reset to defaults") }
    }

    if (editingLeader) {
        LeaderCaptureDialog(
            current = leader,
            onDismiss = { editingLeader = false },
            onPick = { picked ->
                editingLeader = false
                scope.launch { repository.setHwLeader(formatLeader(picked)) }
            },
        )
    }
    editing?.let { tool ->
        LetterCaptureDialog(
            tool = tool,
            current = letterOf[tool],
            takenBy = { letter -> hw.toolByLetter[letter] },
            onDismiss = { editing = null },
            onPick = { letter ->
                editing = null
                scope.launch { repository.setHwToolLetter(letter, tool) }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset tool shortcuts?") },
            text = { Text("Every letter goes back to its default tool.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        repository.setHwToolLetters(DefaultToolLetters)
                        repository.setHwLeader(formatLeader(DefaultLeader))
                    }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Picks the shortcut key: a double-tapped modifier, or a chord pressed on an
 * attached keyboard.
 *
 * The double-tap choices matter more than the capture field — most people
 * editing this screen are holding a phone with no keyboard plugged in, and a
 * "press a key" prompt would leave them stuck.
 */
@Composable
private fun LeaderCaptureDialog(
    current: LeaderTrigger,
    onDismiss: () -> Unit,
    onPick: (LeaderTrigger) -> Unit,
) {
    var captured by remember { mutableStateOf<KeyChord?>(null) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shortcut key") },
        text = {
            Column {
                CaptionText("Double-tap a modifier — nothing else uses it.")
                for (modifier in TapModifier.entries) {
                    val trigger = LeaderTrigger.DoubleTap(modifier)
                    ListItem(
                        colors = transparentListColors(),
                        headlineContent = { Text("Double-tap ${modifier.label}") },
                        trailingContent = {
                            if (current == trigger && captured == null) {
                                Icon(Icons.Outlined.Check, contentDescription = "Current")
                            }
                        },
                        modifier = Modifier.clickable { onPick(trigger) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                CaptionText("…or press a shortcut on the attached keyboard.")
                // A real focusable window, unlike the keyboard's own, so Compose
                // focus is the right tool here.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .focusRequester(requester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val native = event.nativeKeyEvent
                            // Wait for the key the modifiers are qualifying.
                            if (KeyEvent.isModifierKey(native.keyCode)) return@onPreviewKeyEvent true
                            val chord = KeyChord(
                                keyCode = native.keyCode,
                                ctrl = native.isCtrlPressed,
                                alt = native.isAltPressed,
                                shift = native.isShiftPressed,
                                meta = native.isMetaPressed,
                            )
                            // A bare key would swallow ordinary typing, and a
                            // chord this app cannot name cannot be stored.
                            captured = chord.takeIf { it.hasModifier && formatChord(it) != null }
                            true
                        },
                ) {
                    Text(
                        captured?.let(::describeChord) ?: "Waiting for a key…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                captured?.let { chord ->
                    if (chord in ReservedChords) {
                        CaptionText(
                            "${describeChord(chord)} is usually the app's own shortcut.",
                            error = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = captured != null,
                onClick = { captured?.let { onPick(LeaderTrigger.Chord(it)) } },
            ) { Text("Use shortcut") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Picks the letter for one tool. Typed rather than captured: this is a single
 * character, and a text field works with or without a keyboard attached.
 */
@Composable
private fun LetterCaptureDialog(
    tool: ToolbarTool,
    current: Char?,
    takenBy: (Char) -> ToolbarTool?,
    onDismiss: () -> Unit,
    onPick: (Char) -> Unit,
) {
    var text by remember { mutableStateOf(current?.toString().orEmpty()) }
    val letter = text.trim().uppercase().firstOrNull()
    val valid = letter != null && (letter in 'A'..'Z' || letter in '0'..'9') &&
        letter !in ReservedLetters
    val clash = letter?.let(takenBy)?.takeIf { it != tool }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(toolTitle(tool)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.takeLast(1) },
                    label = { Text("Letter") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    letter in ReservedLetters -> CaptionText(
                        "$letter is reserved: $ToolboxLetter opens the toolbox and " +
                            "$CheatSheetLetter shows the list.",
                        error = true,
                    )
                    clash != null -> CaptionText(
                        "$letter currently opens ${toolTitle(clash)}, which will lose its letter.",
                    )
                    else -> CaptionText("A single letter or digit.")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { letter?.let(onPick) }) {
                Text(if (clash != null) "Reassign" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- key press ----

/**
 * Key-press sound controls, shared by the Key press settings screen and the
 * sound & haptics tool's detail page. Changes preview immediately through
 * [KeySoundPlayer]. [trailing] appends extra rows to the same card group.
 */
@Composable
private fun KeySoundGroup(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    trailing: (SettingsGroupScope.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SettingsGroup("Key press sound") {
        item {
            ToggleSetting(
                "Key sound", "Play a sound on every key press", settings.keySound,
            ) {
                scope.launch { repository.setKeySound(it) }
                if (it) {
                    KeySoundPlayer.preview(context, settings.keySoundStyle, settings.keySoundVolume)
                }
            }
        }
        item {
            // Hand-built rather than a ChoiceSetting (the chips need their own
            // row), so the highlight wrapper every other control gets for free
            // is spelled out here — this is where the Sound addon's Use button
            // lands.
            HighlightableRow("Sound style") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sound style", style = MaterialTheme.typography.bodyLarge)
                    InfoButton(
                        "Sound style",
                        "Click and Standard come from the device's system sound pack, so " +
                            "they match the stock keyboard: Click is the classic key tick, " +
                            "Standard the softer AOSP key press. Pop, Thock and Chime are " +
                            "WMKeyboard's own sounds — a soft bubble pop, a deep mechanical " +
                            "bottom-out, and a small bell — identical on every device. " +
                            "Custom plays a sound file: pick Custom to see the sounds " +
                            "you have installed from Addons and to import your own MP3.",
                    )
                }
                // Custom is a segment like any other, so the styles read as one
                // choice rather than five here and a sixth hidden in a list. It
                // names a file rather than a fixed waveform, so it needs a sound
                // installed before it can be picked — the list below is where that
                // sound is chosen and where the "install one" note lives.
                val soundStore = remember { SoundStore.get(context) }
                val soundRevision by soundStore.revision.collectAsStateWithLifecycle()
                val installedSounds = remember(soundRevision) { soundStore.sounds() }
                // Chips rather than a segmented row. Six equal segments across a
                // phone leave ~55dp of label each, which truncated "Chime" to
                // "Chim" and "Custom" to "Custo"; a segmented row set to scroll is
                // worse still, since SegmentedButton has a wide minimum and only
                // three and a half fit. Chips size to their own text, so every
                // style keeps its real name, and the row scrolls only as far as it
                // has to. Same control the addon type filter uses.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (style in KeySoundStyle.entries) {
                        val custom = style == KeySoundStyle.CUSTOM
                        FilterChip(
                            selected = settings.keySoundStyle == style,
                            onClick = {
                                scope.launch {
                                    if (custom) {
                                        // Falls back to the first installed sound
                                        // when none has been chosen yet, so the
                                        // chip always makes a sound. With nothing
                                        // installed it still selects — the section
                                        // it reveals is where a sound is imported,
                                        // so a disabled chip would hide its own
                                        // remedy.
                                        val id = settings.keySoundCustom.customId
                                            .takeIf { id -> installedSounds.any { it.id == id } }
                                            ?: installedSounds.firstOrNull()?.id
                                        if (id == null) {
                                            repository.setKeySoundStyle(style)
                                        } else {
                                            repository.setKeySoundCustomId(id)
                                            KeySoundPlayer.preview(
                                                context, style, settings.keySoundVolume, id,
                                            )
                                        }
                                    } else {
                                        repository.setKeySoundStyle(style)
                                        // Sound the freshly picked style so the user
                                        // hears the choice immediately.
                                        KeySoundPlayer.preview(context, style, settings.keySoundVolume)
                                    }
                                }
                            },
                            label = {
                                Text(
                                    when (style) {
                                        KeySoundStyle.CLICK -> "Click"
                                        KeySoundStyle.STANDARD -> "Standard"
                                        KeySoundStyle.POP -> "Pop"
                                        KeySoundStyle.THOCK -> "Thock"
                                        KeySoundStyle.CHIME -> "Chime"
                                        KeySoundStyle.CUSTOM -> "Custom"
                                    },
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }
        // Only under Custom. The sound library and its import button are what
        // Custom *means*; showing them under Click is offering a choice that
        // has no effect until the style changes too.
        if (settings.keySoundStyle == KeySoundStyle.CUSTOM) {
            item { InstalledSoundSection(repository, settings) }
        }
        item {
            SliderSetting(
                "Sound volume",
                subtitle = "Relative to the system media volume",
                value = settings.keySoundVolume,
                range = 0.05f..1f,
                display = { "${(it * 100).roundToInt()}%" },
            ) {
                scope.launch { repository.setKeySoundVolume(it) }
                // Debounced inside the player, so dragging previews smoothly.
                KeySoundPlayer.preview(context, settings.keySoundStyle, it)
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * The installed key sounds — whatever came from an addon repository, plus
 * anything imported here — and the import button.
 *
 * Picking one also switches the style to [KeySoundStyle.CUSTOM]; choosing a
 * sound and then finding the keyboard still clicking would be baffling.
 */
@Composable
private fun InstalledSoundSection(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { SoundStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val sounds = remember(revision) { store.sounds() }
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri).use {
                        SoundFile.import(it, store, name = fontFileLabel(context, uri))
                    }
                }.getOrElse { SoundImportResult.Failed(it.message ?: "Couldn't read that file") }
            }
            when (result) {
                is SoundImportResult.Imported -> {
                    repository.setKeySoundCustomId(result.sound.id)
                    KeySoundPlayer.preview(
                        context, KeySoundStyle.CUSTOM, settings.keySoundVolume, result.sound.id,
                    )
                }
                is SoundImportResult.NotASound -> message = result.message
                SoundImportResult.TooManySounds ->
                    message = "You already have ${SoundStore.MAX_SOUNDS} sounds. Remove one first."
                is SoundImportResult.Failed -> message = result.message
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        if (sounds.isEmpty()) {
            CaptionText(
                "No installed sounds yet. Install some from Addons, or import an " +
                    "MP3 of your own.",
            )
        }
        for (sound in sounds) {
            val selected = settings.keySoundStyle == KeySoundStyle.CUSTOM &&
                settings.keySoundCustom.customId == sound.id
            ListItem(
                headlineContent = { Text(sound.name) },
                supportingContent = sound.author.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (selected) repository.setKeySoundStyle(KeySoundStyle.CLICK)
                                withContext(Dispatchers.IO) { store.delete(sound.id) }
                                // The pool keeps its decoded copy independently
                                // of the file, so it has to be told too.
                                KeySoundPlayer.forgetCustom(sound.id)
                            }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${sound.name}")
                        }
                    }
                },
                colors = transparentListColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { repository.setKeySoundCustomId(sound.id) }
                        KeySoundPlayer.preview(
                            context, KeySoundStyle.CUSTOM, settings.keySoundVolume, sound.id,
                        )
                    },
            )
        }
        OutlinedButton(
            onClick = { importLauncher.launch(SoundFile.IMPORT_MIME_TYPES) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("Import sound file (.mp3)") }
    }
}

@Composable
private fun KeyPressSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Lets the SYSTEM_* preview fire through the real platform key haptic.
    val view = LocalView.current
    SettingsGroup("Haptic feedback") {
        item {
            ToggleSetting(
                "Key press haptics", "Vibrate on every key press", settings.hapticFeedback,
                info = "A short vibration confirms each key press, including spacebar cursor " +
                    "movement steps. Style and strength are adjustable below.",
            ) {
                scope.launch { repository.setHapticFeedback(it) }
                if (it) {
                    HapticPlayer.preview(
                        context, settings.hapticStyle, settings.hapticAmplitude, settings.hapticStrengthMs,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Haptic style", style = MaterialTheme.typography.bodyLarge)
                InfoButton(
                    "Haptic style",
                    "Key and Tap are the recommended styles: they hand the buzz to the " +
                        "platform's own key haptic (the same call stock keyboards use), so on " +
                        "tuned phones you get the vendor's crafted click and it follows the " +
                        "system haptic-intensity setting. Key asks for the \"virtual key\" feel " +
                        "(what Gboard and SwiftKey use); Tap asks for the softer \"keyboard tap\" " +
                        "feel (Samsung's own keyboard). " +
                        "Click and Heavy click use the device's hardware-tuned haptic effects " +
                        "(Android 10+). Sharp plays the hardware click primitive (Android 11+) — " +
                        "a short, hard thump whose strength follows the intensity slider. " +
                        "Custom drives the vibration motor directly using the duration and " +
                        "intensity sliders — without the hardware's overdrive and braking it " +
                        "feels softer. When a style isn't available it falls back to a hardware " +
                        "Click, then Custom.",
                )
            }
            // Six styles overflow a segmented row; wrapping chips give each a
            // full, readable label. Ordered best-to-worst via HapticStyle.entries.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                HapticStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.hapticStyle == style,
                        onClick = {
                            scope.launch { repository.setHapticStyle(style) }
                            // Fire the motor with the freshly picked style so the
                            // user feels the choice immediately.
                            HapticPlayer.preview(
                                context, style, settings.hapticAmplitude, settings.hapticStrengthMs, view,
                            )
                        },
                        label = { Text(style.label, maxLines = 1) },
                    )
                }
            }
        }
        if (settings.hapticStyle == HapticStyle.CUSTOM) {
            item {
                SliderSetting(
                    "Haptic strength",
                    subtitle = "Vibration length per key press",
                    value = settings.hapticStrengthMs.toFloat(),
                    range = 5f..60f,
                    display = { "${it.roundToInt()} ms" },
                    info = "Duration of the vibration pulse in milliseconds. Longer pulses feel " +
                        "stronger on most phones.",
                ) {
                    scope.launch { repository.setHapticStrengthMs(it.toInt()) }
                    // Debounced inside the player, so dragging previews smoothly.
                    HapticPlayer.preview(context, settings.hapticStyle, settings.hapticAmplitude, it.toInt(), view)
                }
            }
        }
        if (settings.hapticStyle == HapticStyle.CUSTOM || settings.hapticStyle == HapticStyle.SHARP) {
            item {
                SliderSetting(
                    "Haptic intensity",
                    subtitle = "Vibration amplitude per key press",
                    value = settings.hapticAmplitude.toFloat(),
                    range = 1f..255f,
                    display = { "${it.roundToInt() * 100 / 255}%" },
                    info = "How hard the vibration motor is driven (1–255). For Sharp this scales " +
                        "the hardware click primitive; the length stays fixed, only the punch " +
                        "changes. For Custom it only takes effect on devices whose vibrator " +
                        "supports amplitude control; on others only the duration above matters. " +
                        "The system-wide \"Touch feedback\" vibration setting still scales the " +
                        "final strength on top of this.",
                ) {
                    scope.launch { repository.setHapticAmplitude(it.toInt()) }
                    HapticPlayer.preview(context, settings.hapticStyle, it.toInt(), settings.hapticStrengthMs, view)
                }
            }
        }
        item {
            ToggleSetting(
                "Long-press haptics", "Vibrate when a long press registers", settings.hapticOnLongPress,
                info = "A second buzz the moment a long press kicks in — when the alternate-" +
                    "character popup opens, or a long-press action fires — telling your finger " +
                    "it can let go. Delete and space are unaffected; their key-repeat already " +
                    "vibrates on every repeat.",
            ) { scope.launch { repository.setHapticOnLongPress(it) } }
        }
        item {
            ToggleSetting(
                "Long-press release haptics", "Vibrate on release after a long press",
                settings.hapticOnLongPressRelease,
                info = "An extra buzz when you lift your finger at the end of a long press, " +
                    "closing the press-hold-release loop. Off by default; some find the third " +
                    "vibration excessive.",
            ) { scope.launch { repository.setHapticOnLongPressRelease(it) } }
        }
        // Per-event gates: only meaningful while the master switch above is on,
        // so they fold away when it is off.
        if (settings.hapticFeedback) {
            item {
                ToggleSetting(
                    "Vibrate on space", "Buzz when you press the space bar",
                    settings.feedback.vibrateOnSpace,
                    info = "Silences the space bar's press buzz on its own — useful if the long " +
                        "space bar feels heavy under the thumb — while every other key keeps " +
                        "vibrating. The key press sound, if on, still plays. On by default.",
                ) { scope.launch { repository.setVibrateOnSpace(it) } }
            }
            item {
                ToggleSetting(
                    "Vibrate on delete swipe", "Buzz on each word a backspace swipe removes",
                    settings.feedback.vibrateOnDeleteSwipe,
                    info = "When you swipe left on the backspace key to delete word by word, each " +
                        "word buzzes. Off makes clearing a sentence one smooth pull with no " +
                        "buzz-saw. The plain backspace tap is unaffected. On by default.",
                ) { scope.launch { repository.setVibrateOnDeleteSwipe(it) } }
            }
            item {
                ToggleSetting(
                    "Vibrate on key repeat", "Buzz on every auto-repeat while a key is held",
                    settings.feedback.vibrateOnRepeat,
                    info = "Holding backspace or space auto-repeats; by default every repeat buzzes. " +
                        "Off keeps only the first press buzzing and lets the repeats run silent " +
                        "(their key sound, if on, still plays). On by default.",
                ) { scope.launch { repository.setVibrateOnRepeat(it) } }
            }
            item {
                ToggleSetting(
                    "Mute haptics in Do Not Disturb",
                    "Stop all keyboard vibration while Do Not Disturb is on",
                    settings.feedback.hapticsRespectDnd,
                    info = "By default the keyboard keeps buzzing in Do Not Disturb — DND targets " +
                        "notifications, not touch feedback. Turn this on to fall fully silent " +
                        "while DND is active. Off by default.",
                ) { scope.launch { repository.setHapticsRespectDnd(it) } }
            }
        }
    }

    KeySoundGroup(repository, settings)

    SettingsGroup("Key popup") {
        item {
            ToggleSetting(
                "Key popup", "Show a character bubble above the pressed key", settings.popup.enabled,
                info = "While a key is held, its character floats in a bubble above your finger " +
                    "so you can see what you hit.",
            ) { scope.launch { repository.setKeyPopup(it) } }
        }
        if (settings.popup.enabled) {
            item {
                ToggleSetting(
                    "Popup on number pads",
                    "Also show the bubble on number, phone, and date fields",
                    settings.popup.inNumericFields,
                    info = "Number, phone, and date/time fields use a keypad, where the " +
                        "floating character adds little — and over a PIN it echoes each " +
                        "digit large enough to be read over your shoulder. Off hides the " +
                        "bubble on those keypads; regular text fields are unaffected.",
                ) { scope.launch { repository.setKeyPopupInNumericFields(it) } }
            }
            item {
                SliderSetting(
                    "Minimum popup duration",
                    subtitle = "How long the bubble stays up even on a fast tap",
                    value = settings.popup.minDurationMs.toFloat(),
                    range = 0f..300f,
                    display = { "${it.toInt()} ms" },
                    info = "On a quick tap the key is released almost instantly, which can make " +
                        "the bubble a barely-visible flicker. The bubble lingers after release " +
                        "until it has been shown for at least this long. 0 hides it the moment " +
                        "you let go.",
                ) { scope.launch { repository.setKeyPopupMinDurationMs(it.toInt()) } }
            }
            item {
                SliderSetting(
                    "Maximum popup duration",
                    subtitle = "Safety cap that clears a bubble stuck by lag",
                    value = settings.popup.maxDurationMs.toFloat(),
                    range = 400f..2000f,
                    display = { "${it.toInt()} ms" },
                    info = "The bubble normally disappears when you lift your finger. If the " +
                        "keyboard lags — most often as a new line is inserted — the release can " +
                        "be missed and the bubble strands on screen. This is the hard ceiling on " +
                        "its life, measured from the press: past it the bubble hides no matter " +
                        "what. Unlike the minimum, this isn't about feel — it only exists to " +
                        "recover from a dropped release, so leave it high unless you still see a " +
                        "bubble lingering.",
                ) { scope.launch { repository.setKeyPopupMaxDurationMs(it.toInt()) } }
            }
        }
        item {
            ToggleSetting(
                "Popup on key",
                "Grow the bubble upward from the pressed key itself",
                settings.popup.onKey,
                info = "On: the preview bubble sits on the pressed key and stretches upward, " +
                    "key-wide with a large character near its top — the stock-keyboard look. " +
                    "Off: a compact bubble floats above your fingertip with a gap.",
            ) { scope.launch { repository.setKeyPopupOnKey(it) } }
        }
        item {
            SliderSetting(
                "Popup font size",
                subtitle = "Scale of the key preview bubble and long-press alternates",
                value = settings.popup.fontScale,
                range = 0.7f..1.6f,
                display = { "×%.2f".format(it) },
                info = "Multiplies the text size inside the character bubble shown while a key " +
                    "is pressed and in the long-press alternates popup, independently of the " +
                    "key label size.",
            ) { scope.launch { repository.setPopupFontScale(it) } }
        }
        item {
            SliderSetting(
                "Popup height",
                subtitle = "Height of the key preview bubble",
                value = settings.popup.heightDp.toFloat(),
                range = 32f..160f,
                display = { "${it.toInt()} dp" },
                info = "Height of the character bubble. With \"Popup on key\" enabled this is " +
                    "measured from the bottom of the pressed key, so anything taller than the " +
                    "key extends above it and stays visible past your finger.",
            ) { scope.launch { repository.setKeyPopupHeightDp(it.toInt()) } }
        }
    }

    SettingsGroup("Timing") {
        item {
            SliderSetting(
                "Long-press delay",
                subtitle = "Hold time before alternate characters appear",
                value = settings.longPressDelayMs.toFloat(),
                range = 150f..700f,
                display = { "${it.toInt()} ms" },
                info = "How long a key must be held before its long-press alternates (accents, " +
                    "digits, symbols) pop up. Lower is faster but easier to trigger by accident.",
            ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Key repeat interval",
                subtitle = "Speed of repeated delete while held",
                value = settings.keyRepeatIntervalMs.toFloat(),
                range = 20f..200f,
                display = { "${it.toInt()} ms" },
                info = "While delete (or space) is held it repeats at this interval. Lower " +
                    "values delete faster.",
            ) { scope.launch { repository.setKeyRepeatIntervalMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Caps-lock double-tap",
                subtitle = "How fast a second shift tap turns on caps lock",
                value = settings.layoutBehavior.shiftCapsLockMs.toFloat(),
                range = ShiftCapsLockMsRange.first.toFloat()..ShiftCapsLockMsRange.last.toFloat(),
                display = { "${it.toInt()} ms" },
                info = "Two shift taps within this window lock caps. Shorter makes caps lock " +
                    "quicker but easier to trigger by accident; longer is more forgiving of a " +
                    "slow double-tap. 350 ms is the default.",
            ) { scope.launch { repository.setShiftCapsLockMs(it.toInt()) } }
        }
    }

    SettingsGroup("Long-press shortcuts") {
        item {
            ToggleSetting(
                "Long-press hints", "Show each key's long-press character in its corner",
                settings.longPressHints,
                info = "A small label in the top-right corner of each key previews its first " +
                    "long-press character — the digit, symbol or accent the popup leads with. " +
                    "Keys running a clipboard shortcut below show no hint.",
            ) { scope.launch { repository.setLongPressHints(it) } }
        }
        item {
            ToggleSetting(
                "All accents on long-press",
                "Fill every letter's popup with its full set of accents",
                settings.layoutBehavior.showAllPopupKeys,
                info = "Adds the complete accent set for each Latin letter (à á â ä ã å ā …) to " +
                    "its long-press popup, on top of whatever the layout already lists. Off by " +
                    "default: the built-in popups are deliberately short, and the full set is a " +
                    "lot of glyphs. Letters only.",
            ) { scope.launch { repository.setShowAllPopupKeys(it) } }
        }
        item {
            ToggleSetting(
                "Hold ?123 for numpad",
                "Long-press the symbols key to open the number pad",
                settings.layoutBehavior.symbolsLongPressNumpad,
                info = "Normally the ?123 key only switches to the symbol layer. With this on, " +
                    "holding it opens the numeric keypad panel over any field — a full number " +
                    "pad without leaving the current text box. A quick tap still switches " +
                    "layers as usual.",
            ) { scope.launch { repository.setSymbolsLongPressNumpad(it) } }
        }
        item {
            // A44: the $ key's long-press currency glyphs, space-separated. Blank
            // restores the built-in set. Mirrors the layout editor's alternates field.
            var currencyText by remember(settings.layoutBehavior.currencyKeys) {
                mutableStateOf(
                    settings.layoutBehavior.currencyKeys
                        .ifEmpty { DefaultCurrencyKeys }
                        .joinToString(" "),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Currency keys", style = MaterialTheme.typography.bodyLarge)
                    InfoButton(
                        "Currency keys",
                        "The glyphs offered on the \$ key's long-press popup, in order — put " +
                            "your own currency first. Separate them with spaces. Leave it at the " +
                            "default (৳ € £ ¥ ₹ ₿) or clear it to restore the built-in set.",
                    )
                }
                OutlinedTextField(
                    value = currencyText,
                    onValueChange = {
                        currencyText = it
                        scope.launch {
                            repository.setCurrencyKeys(it.trim().split(" ").filter { s -> s.isNotBlank() })
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            ToggleSetting(
                "Ctrl shortcuts as raw key events",
                "For terminals; off means Ctrl+A/C/V/X use the clipboard directly",
                settings.rawClipboardShortcuts,
                info = "A Ctrl key on a custom layout normally runs A, C, V and X through " +
                    "Android's own clipboard actions, which work everywhere including web " +
                    "pages and apps built with Compose. A terminal needs the raw key event " +
                    "instead — Ctrl+C there means interrupt, not copy. There is no way for " +
                    "the keyboard to tell the two apart, so this is a switch rather than a " +
                    "guess.",
            ) { scope.launch { repository.setRawClipboardShortcuts(it) } }
        }
        item {
            ToggleSetting(
                "Hold A to select all", "Long-pressing A selects all text",
                settings.longPressLetterActions.selectAll,
                info = "Replaces the A key's accent popup with a select-all shortcut. Turn it " +
                    "off to get the accents (à á â ä å) back.",
            ) { scope.launch { repository.setLongPressASelectAll(it) } }
        }
        item {
            ToggleSetting(
                "Hold C to copy", "Copies the selection, or everything if nothing is selected",
                settings.longPressLetterActions.copy,
                info = "With text selected, a long press on C copies just that selection. With " +
                    "no selection it selects all first, so one hold copies the whole field. " +
                    "Replaces the C key's accent popup (ç ć) while enabled.",
            ) { scope.launch { repository.setLongPressCCopy(it) } }
        }
        item {
            ToggleSetting(
                "Hold X to cut", "Cuts the selection, or everything if nothing is selected",
                settings.longPressLetterActions.cut,
                info = "With text selected, a long press on X cuts just that selection. With " +
                    "no selection it selects all first, so one hold cuts the whole field.",
            ) { scope.launch { repository.setLongPressXCut(it) } }
        }
        item {
            ToggleSetting(
                "Hold V to paste", "Long-pressing V pastes the clipboard",
                settings.longPressLetterActions.paste,
                info = "Pastes the current clipboard content at the cursor, replacing any " +
                    "selection — the classic Ctrl+V, one hold away.",
            ) { scope.launch { repository.setLongPressVPaste(it) } }
        }
        item {
            ToggleSetting(
                "Hold Z to undo", "Long-pressing Z undoes the last edit",
                settings.longPressLetterActions.undo,
                info = "Sends the same undo shortcut as the toolbar's Undo tool. Replaces the " +
                    "Z key's accent popup while enabled.",
            ) { scope.launch { repository.setLongPressZUndo(it) } }
        }
        item {
            ToggleSetting(
                "Hold Y to redo", "Long-pressing Y redoes the last undone edit",
                settings.longPressLetterActions.redo,
                info = "Sends the same redo shortcut as the toolbar's Redo tool " +
                    "(Ctrl+Y or Ctrl+Shift+Z, per the redo shortcut setting).",
            ) { scope.launch { repository.setLongPressYRedo(it) } }
        }
    }
}

// ---- appearance ----

@Composable
private fun AppearanceSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenThemes: () -> Unit,
    onOpenFonts: () -> Unit,
    onOpenIcons: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Turning the toolbar off is guarded — it hides suggestions and every tool.
    var confirmDisableToolbar by remember { mutableStateOf(false) }
    SettingsGroup("Style") {
        item {
            val selected = settings.customThemes.find { it.id == settings.keyboardThemeId }
                ?: com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
                    .find { it.id == settings.keyboardThemeId }
            NavRow(
                "Keyboard themes",
                "Light/dark/AMOLED, colors, background images, import/export",
                value = selected?.name ?: "Default",
                onClick = onOpenThemes,
            )
        }
        item {
            NavRow(
                "Keyboard font",
                "Google Fonts, or import your own font file",
                value = KeyboardFonts.displayName(settings.keyFontId, settings.customFontName),
                onClick = onOpenFonts,
            )
        }
        item {
            val active = settings.icons.activePackId
            val changed = settings.icons.overrides.size
            NavRow(
                "Icons",
                "Swap any tool or key icon, or install an icon pack",
                value = when {
                    active.isNotEmpty() ->
                        IconPackStore.get(LocalContext.current).pack(active)?.name ?: "Default"
                    changed > 0 -> "$changed changed"
                    else -> "Default"
                },
                onClick = onOpenIcons,
            )
        }
    }

    SettingsGroup("Keys") {
        item {
            SliderSetting(
                "Key corner radius",
                subtitle = "Roundness of the key corners",
                value = settings.keyCornerRadiusDp.toFloat(),
                range = 0f..28f,
                display = { "${it.toInt()} dp" },
                info = "0 gives square keys, 28 gives fully pill-shaped keys.",
            ) { scope.launch { repository.setKeyCornerRadiusDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Key label font size",
                subtitle = "Scale of the labels printed on the keys",
                value = settings.fontScale,
                range = 0.7f..1.5f,
                display = { "×%.2f".format(it) },
                info = "Multiplies the size of every label on the keys themselves. Popup " +
                    "bubbles have their own font size under Key press → Key popup.",
            ) { scope.launch { repository.setFontScale(it) } }
        }
        item {
            SliderSetting(
                "Key hint font size",
                subtitle = "Scale of the small corner hint character on each key",
                value = settings.layoutBehavior.hintFontScale,
                range = 0.5f..2.0f,
                display = { "×%.2f".format(it) },
                info = "Resizes the little long-press hint printed in the corner of a key " +
                    "(shown when \"Long-press hints\" is on). Larger values make the hints " +
                    "easier to read; ×1.00 is the default.",
            ) { scope.launch { repository.setHintFontScale(it) } }
        }
    }

    SettingsGroup("Toolbar") {
        item {
            ToggleSetting(
                "Show the toolbar",
                "The strip above the keys that carries suggestions and tools",
                settings.toolbarBehavior.enabled,
                info = "The toolbar is the row above the keys — it shows word suggestions " +
                    "while you type and your pinned tools (emoji, clipboard, cursor keys …) " +
                    "otherwise. Turn it off to reclaim its height for the keys; you'll be " +
                    "asked to confirm, because it also hides suggestions and every tool.",
            ) { on ->
                // Enabling is harmless; disabling loses real features, so confirm.
                if (on) scope.launch { repository.setToolbarEnabled(true) }
                else confirmDisableToolbar = true
            }
        }
        item {
            ToggleSetting(
                "Swipe down to hide",
                "A downward flick on the toolbar dismisses the keyboard",
                settings.toolbarBehavior.swipeDownHide,
                info = "When on, flicking down anywhere on the toolbar strip closes the " +
                    "keyboard. Off by default so the gesture never fires while you scroll " +
                    "or rearrange the bar. Reordering a tool is a press-and-hold, so it " +
                    "won't trigger this.",
            ) { scope.launch { repository.setToolbarSwipeDownHide(it) } }
        }
        item {
            ToggleSetting(
                "Only toolbar with hardware keyboard",
                "When a physical keyboard is attached, show just the toolbar",
                settings.toolbarBehavior.onlyWithHardwareKeyboard,
                info = "With a Bluetooth or dock keyboard connected, the on-screen keys step " +
                    "aside and only the toolbar stays — so emoji, clipboard and the other " +
                    "tools remain one tap away while you type on the hardware keyboard. Off " +
                    "by default.",
            ) { scope.launch { repository.setToolbarOnlyWithHardwareKeyboard(it) } }
        }
        item {
            ToggleSetting(
                "Reverse order for RTL languages",
                "Mirror the tool order when typing a right-to-left script",
                settings.toolbarBehavior.reverseForRtl,
                info = "For right-to-left scripts (Arabic, Hebrew …) the pinned tools read " +
                    "right-to-left too, so the bar flows with the text. On by default. The " +
                    "toolbox grid is unaffected.",
            ) { scope.launch { repository.setReverseToolbarForRtl(it) } }
        }
        item {
            ToggleSetting(
                "Spread tools across the bar",
                "Toolbar tools split the available width evenly",
                settings.toolbarBehavior.greedy,
                info = "On: the tools on the top toolbar greedily share the whole bar, like " +
                    "the suggestion candidates do. Off: they pack to the left at a fixed " +
                    "size. Which tools appear there is customized from the keyboard itself: " +
                    "open the toolbox (grid button on the toolbar), then hold and drag tools " +
                    "between the toolbar and the toolbox.",
            ) { scope.launch { repository.setToolbarGreedy(it) } }
        }
        item {
            SliderSetting(
                "Toolbar height",
                subtitle = "Height of the top toolbar / suggestion strip",
                value = settings.toolbarHeightDp.toFloat(),
                range = 32f..80f,
                display = { "${it.roundToInt()} dp" },
                info = "The default is 44 dp. Taller gives bigger tap targets and room for " +
                    "tool labels; shorter reclaims screen height. This is the strip that " +
                    "carries both the word suggestions and the toolbar.",
            ) { scope.launch { repository.setToolbarHeightDp(it.roundToInt()) } }
        }
        item {
            ToggleSetting(
                "Scroll the toolbar",
                "Swipe the tools sideways instead of shrinking them to fit",
                settings.toolbarBehavior.scrollable,
                info = "When you pin more tools than fit the bar, this keeps each at a " +
                    "comfortable size and lets you scroll through them. It packs the tools " +
                    "to the left (overriding \"Spread tools across the bar\"). Reorder from " +
                    "the toolbox; dragging within a scrolling bar is fiddly.",
            ) { scope.launch { repository.setToolbarScrollable(it) } }
        }
        item {
            ToggleSetting(
                "Hide toolbar & clipboard on lock screen",
                "Drop the top strip and block the clipboard while the device is locked",
                settings.toolbarBehavior.hideWhenLocked,
                info = "When the keyboard comes up over your lock screen — replying to a " +
                    "notification, a lock-screen search box — this hides the whole top strip " +
                    "(word suggestions and every pinned tool, so the clipboard tool and its " +
                    "paste chip go too) and blocks the clipboard panel. Copied text like " +
                    "one-time codes and passwords stays off a screen anyone can wake. Off by " +
                    "default; unlocked, the keyboard is unchanged.",
            ) { scope.launch { repository.setToolbarHideWhenLocked(it) } }
        }
        item {
            ToggleSetting(
                "Tool labels",
                "Show each tool's name under its icon on the toolbar",
                settings.toolbarLabels,
                info = "Draws a small caption beneath every pinned tool. You'll likely want " +
                    "to raise the toolbar height to give the labels room.",
            ) { scope.launch { repository.setToolbarLabels(it) } }
        }
        if (settings.toolbarLabels) {
            item {
                SliderSetting(
                    "Label text size",
                    subtitle = "Font size of the toolbar tool labels",
                    value = settings.toolbarLabelSize.toFloat(),
                    range = 7f..14f,
                    display = { "${it.roundToInt()} sp" },
                ) { scope.launch { repository.setToolbarLabelSize(it.roundToInt()) } }
            }
        }
        item {
            ResetPinnedToolsSetting(repository, scope)
        }
        item {
            SliderSetting(
                "Tool circle radius",
                subtitle = "Roundness of the circle behind each toolbar tool",
                value = settings.toolCircleRadiusDp.toFloat(),
                range = 0f..20f,
                display = { if (it.toInt() == 0) "off" else "${it.toInt()} dp" },
                info = "20 draws a full circle behind every tool icon (Gboard style), " +
                    "smaller values give rounded squares, and 0 removes the background " +
                    "entirely, leaving bare icons.",
            ) { scope.launch { repository.setToolCircleRadiusDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Toolbox grid size",
                subtitle = "Tools per row in the toolbox grid",
                value = settings.toolboxColumns.toFloat(),
                range = 3f..6f,
                display = { "${it.roundToInt()} per row" },
                info = "The toolbox is the grid behind the toolbar's grid button. " +
                    "Fewer per row makes each tool bigger and easier to hit; more " +
                    "per row fits more tools without scrolling.",
            ) { scope.launch { repository.setToolboxColumns(it.roundToInt()) } }
        }
    }

    if (confirmDisableToolbar) {
        AlertDialog(
            onDismissRequest = { confirmDisableToolbar = false },
            title = { Text("Disable the toolbar?") },
            text = {
                Text(
                    "The whole top strip goes away — you'll lose word suggestions and " +
                        "quick access to every pinned tool (emoji, clipboard, cursor keys, " +
                        "and the rest). The keys claim the reclaimed height. You can turn " +
                        "it back on here any time.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisableToolbar = false
                    scope.launch { repository.setToolbarEnabled(false) }
                }) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisableToolbar = false }) { Text("Cancel") }
            },
        )
    }
}

// ---- layout & size ----

@Composable
private fun LayoutSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Number row") {
        item {
            ToggleSetting(
                "Number row", "Show a dedicated digit row above the letters", settings.numberRow,
                info = "Adds a 1–0 row on top of the letter layout so you never long-press for " +
                    "digits. The digits normally on the top letter row's long press are dropped " +
                    "while this is on. Costs one extra row of height.",
            ) { scope.launch { repository.setNumberRow(it) } }
        }
        if (settings.numberRow) {
            item {
                SliderSetting(
                    "Number row height",
                    subtitle = "Height of the digit row, independent of the letter keys",
                    value = settings.numberRowHeightDp.toFloat(),
                    range = 32f..100f,
                    display = { "${it.toInt()} dp" },
                    info = "A shorter digit row keeps quick number access without costing a " +
                        "full row of extra keyboard height.",
                ) { scope.launch { repository.setNumberRowHeightDp(it.toInt()) } }
            }
            item {
                ToggleSetting(
                    "Symbols on shift",
                    "Hold shift to turn the digit row into symbols",
                    settings.layoutBehavior.numberRowShiftSymbols,
                    info = "While shift is held on the letter layout, the number row shows the " +
                        "symbol layer's fill row (= \\ < > [ ] { } | ~) instead of digits, so those " +
                        "symbols are one shift away without switching to the symbols layer. The " +
                        "number row already becomes extra arrow and comparison symbols on the " +
                        "second symbols layer.",
                ) { scope.launch { repository.setNumberRowShiftSymbols(it) } }
            }
            item {
                ToggleSetting(
                    "Number row in symbols",
                    "Also keep the digit row on the ?123 symbols layer",
                    settings.layoutBehavior.numberRowInSymbols,
                    info = "On (the default), the digit row stays put when you switch to the " +
                        "symbols layer. Turn it off to drop the number row from ?123 — where the " +
                        "symbols already carry their own top row — while keeping it on the letters. " +
                        "The keyboard shrinks by a row on the symbols layer when this is off.",
                ) { scope.launch { repository.setNumberRowInSymbols(it) } }
            }
        }
    }

    SettingsGroup("Numerals") {
        item {
            ChoiceSetting(
                "Type native digits in",
                subtitle = "Where the native digits are actually inserted",
                info = "Which glyphs the digit keys show is picked per language, in Languages → " +
                    "the language → Numerals. This setting decides where those glyphs are also " +
                    "typed. Text fields only (default) keeps plain 0-9 in number, phone, date " +
                    "and time fields so they stay machine-readable, and types native digits " +
                    "everywhere else. Everywhere types native digits in those fields too. " +
                    "Display only shows the glyphs on the keys but always inserts 0-9.",
                options = NumeralCommitScope.entries.map { it to it.label },
                selected = settings.layoutBehavior.numeralCommitScope,
            ) { scope.launch { repository.setNumeralCommitScope(it) } }
        }
        item {
            CaptionText(
                "The digits each language draws — Latin 0-9, Arabic ٠-٩, Persian ۰-۹, " +
                    "Bengali ০-৯, Devanagari ०-९ — are set per language under Languages.",
            )
        }
    }

    SettingsGroup("Size & position") {
        item {
            SliderSetting(
                "Key height",
                subtitle = "Height of each key row — sets the overall input height",
                value = settings.keyHeightDp.toFloat(),
                range = 32f..100f,
                display = { "${it.toInt()} dp" },
                info = "Taller keys are easier to hit but the keyboard covers more of the " +
                    "screen. The emoji, clipboard and snippet panels scale with this value too.",
            ) { scope.launch { repository.setKeyHeightDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Bottom row height",
                subtitle = "Height of the space / enter row, on its own",
                value = settings.layoutBehavior.bottomRowHeightDp.toFloat(),
                range = 0f..BottomRowHeightRange.last.toFloat(),
                display = { if (it < 1f) "Follow keys" else "${it.toInt()} dp" },
                info = "Give the bottom row — spacebar and enter — its own height, taller or " +
                    "shorter than the letter keys, for an easier spacebar without growing the " +
                    "whole keyboard. \"Follow keys\" (the default) keeps it the same as the rest. " +
                    "Custom layouts that set their own row heights ignore this.",
            ) { scope.launch { repository.setBottomRowHeightDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Side padding",
                subtitle = "Shave the keyboard's left and right edges toward the centre",
                value = settings.layoutBehavior.sidePadScale,
                range = SidePadScaleRange.start..SidePadScaleRange.endInclusive,
                display = { "${(it * 100).toInt()}%" },
                info = "Adds an equal margin on both sides, narrowing the keys toward the middle " +
                    "for thumb reach — without docking to one edge the way one-handed mode does. " +
                    "0% is the default. Stacks on top of the keyboard width above.",
            ) { scope.launch { repository.setSidePadScale(it) } }
        }
        item {
            SliderSetting(
                "Key spacing",
                subtitle = "Gap between the keys",
                value = settings.keyGapScale,
                range = 0f..2f,
                display = { "${(it * 100).toInt()}%" },
                info = "Adjusts the space around every key. Higher spreads the keys apart (and " +
                    "makes the keyboard a little taller, since the gap is part of each row); " +
                    "lower packs them tighter. 100% is the default.",
            ) { scope.launch { repository.setKeyGapScale(it) } }
        }
        item {
            SliderSetting(
                "Bottom padding",
                subtitle = "Extra space below the keys, above the navigation bar",
                value = settings.bottomPaddingDp.toFloat(),
                range = 0f..40f,
                display = { "${it.toInt()} dp" },
                info = "Raises the whole keyboard away from the bottom edge and the gesture " +
                    "navigation bar. Increase it if the bottom row feels cramped against the " +
                    "edge of the screen or you keep triggering system navigation.",
            ) { scope.launch { repository.setBottomPaddingDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Keyboard width",
                subtitle = "Shrink the keyboard horizontally",
                value = settings.keyboardWidthPercent.toFloat(),
                range = 50f..100f,
                display = { "${it.toInt()}%" },
                info = "Below 100% the keyboard no longer spans the whole screen; choose which " +
                    "edge it sits at below. Handy on very wide screens. One-handed mode " +
                    "(below) is a quick preset that overrides this while active.",
            ) { scope.launch { repository.setKeyboardWidthPercent(it.toInt()) } }
        }
        if (settings.keyboardWidthPercent < 100) {
            item {
                ChoiceSetting(
                    title = "Keyboard position",
                    info = "Where the narrowed keyboard sits: hugging the left edge, centered, " +
                        "or hugging the right edge.",
                    options = KeyboardAlignment.entries.map { alignment ->
                        alignment to alignment.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    selected = settings.keyboardAlignment,
                ) { scope.launch { repository.setKeyboardAlignment(it) } }
            }
        }
    }

    var expandedVariant by remember { mutableStateOf<ScreenVariant?>(null) }
    SettingsGroup("Per-screen sizing") {
        item {
            CaptionText(
                "The sizes above are your portrait (folded) sizes. Landscape and unfolded " +
                    "screens can override any of them, or scale the whole keyboard at once — " +
                    "handy on a foldable, where the roomy inner display often wants a smaller " +
                    "keyboard than the cover screen. Anything you leave untouched follows " +
                    "portrait.",
            )
        }
        for (variant in ScreenVariant.entries.filter { it.isOverride }) {
            val override = settings.sizingOverrides[variant]
            val values = settings.sizingValuesFor(variant)
            item {
                NavRow(
                    variant.label,
                    if (override == null || override.isEmpty) {
                        "Following portrait"
                    } else {
                        "${values.keyHeightDp} dp keys · ${values.keyboardWidthPercent}% wide"
                    },
                    onClick = {
                        expandedVariant = if (expandedVariant == variant) null else variant
                    },
                )
            }
            if (expandedVariant == variant) {
                item {
                    SliderSetting(
                        "Keyboard scale",
                        subtitle = "Shrink or grow the whole keyboard for this screen",
                        value = values.keyboardScale ?: 1f,
                        range = 0.5f..1.5f,
                        display = { "${(it * 100).toInt()}%" },
                    ) { scope.launch { repository.setVariantKeyboardScale(variant, it) } }
                }
                item {
                    SliderSetting(
                        "Key height",
                        value = (values.keyHeightDp ?: settings.keyHeightDp).toFloat(),
                        range = 32f..100f,
                        display = { "${it.toInt()} dp" },
                    ) { scope.launch { repository.setVariantKeyHeightDp(variant, it.toInt()) } }
                }
                if (settings.numberRow) {
                    item {
                        SliderSetting(
                            "Number row height",
                            value = (values.numberRowHeightDp ?: settings.numberRowHeightDp).toFloat(),
                            range = 32f..100f,
                            display = { "${it.toInt()} dp" },
                        ) {
                            scope.launch {
                                repository.setVariantNumberRowHeightDp(variant, it.toInt())
                            }
                        }
                    }
                }
                item {
                    SliderSetting(
                        "Bottom padding",
                        value = (values.bottomPaddingDp ?: settings.bottomPaddingDp).toFloat(),
                        range = 0f..40f,
                        display = { "${it.toInt()} dp" },
                    ) { scope.launch { repository.setVariantBottomPaddingDp(variant, it.toInt()) } }
                }
                item {
                    SliderSetting(
                        "Keyboard width",
                        value = (values.keyboardWidthPercent ?: settings.keyboardWidthPercent).toFloat(),
                        range = 50f..100f,
                        display = { "${it.toInt()}%" },
                    ) { scope.launch { repository.setVariantWidthPercent(variant, it.toInt()) } }
                }
                item {
                    SliderSetting(
                        "Font size",
                        value = values.fontScale ?: settings.fontScale,
                        range = 0.7f..1.5f,
                        display = { "×%.2f".format(it) },
                    ) { scope.launch { repository.setVariantFontScale(variant, it) } }
                }
                if ((values.keyboardWidthPercent ?: settings.keyboardWidthPercent) < 100) {
                    item {
                        ChoiceSetting(
                            title = "Keyboard position",
                            options = KeyboardAlignment.entries.map { alignment ->
                                alignment to alignment.name.lowercase()
                                    .replaceFirstChar { it.uppercase() }
                            },
                            selected = values.keyboardAlignment ?: settings.keyboardAlignment,
                        ) { scope.launch { repository.setVariantAlignment(variant, it) } }
                    }
                }
                if (override != null && !override.isEmpty) {
                    item {
                        NavRow(
                            "Follow portrait again",
                            "Clear the overrides set for ${variant.label.lowercase()}",
                            onClick = { scope.launch { repository.clearVariantSizing(variant) } },
                        )
                    }
                }
            }
        }
    }

    SettingsGroup("One-handed, split & floating") {
        item {
            ChoiceSetting(
                title = "One-handed mode",
                subtitle = "Shrink the keyboard toward one edge",
                options = OneHandedMode.entries.map { mode ->
                    mode to mode.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                selected = settings.oneHandedMode,
            ) { scope.launch { repository.setOneHandedMode(it) } }
        }
        item {
            CaptionText(
                "Tune one-handed mode separately for each orientation. In the keyboard, the " +
                    "rail's arrow flips the side and remembers it here for that orientation.",
            )
        }
        for ((landscape, orientationLabel) in listOf(false to "Portrait", true to "Landscape")) {
            val profile = settings.oneHanded.forLandscape(landscape)
            item {
                SliderSetting(
                    "$orientationLabel · width",
                    subtitle = "How wide the keyboard is in $orientationLabel",
                    value = profile.widthPercent.toFloat(),
                    range = SettingsRepository.ONE_HANDED_WIDTH_MIN.toFloat()..
                        SettingsRepository.ONE_HANDED_WIDTH_MAX.toFloat(),
                    display = { "${it.toInt()}%" },
                    info = "The keyboard's share of the screen width while one-handed is active. " +
                        "The rest holds the rail and empty space toward the centre.",
                ) { scope.launch { repository.setOneHandedWidthPercent(landscape, it.toInt()) } }
            }
            item {
                SliderSetting(
                    "$orientationLabel · height",
                    subtitle = "Shrink the keys vertically for reach",
                    value = profile.heightScale.toFloat(),
                    range = SettingsRepository.ONE_HANDED_HEIGHT_SCALE_MIN.toFloat()..
                        SettingsRepository.ONE_HANDED_HEIGHT_SCALE_MAX.toFloat(),
                    display = { "${it.toInt()}%" },
                    info = "Scales the key height while one-handed is active, bringing the top " +
                        "rows down into thumb reach. 100% keeps the normal height.",
                ) { scope.launch { repository.setOneHandedHeightScale(landscape, it.toInt()) } }
            }
            item {
                ChoiceSetting(
                    title = "$orientationLabel · side",
                    subtitle = "Which edge it docks to in $orientationLabel",
                    options = OneHandedSide.entries.map { side ->
                        side to side.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    selected = profile.side,
                ) { scope.launch { repository.setOneHandedSide(landscape, it) } }
            }
        }
        item {
            ToggleSetting(
                "Split keyboard", "Divide the keys into left and right halves", settings.splitKeyboard,
                info = "Splits every row down the middle with a gap between the halves, so " +
                    "your thumbs travel less on wide screens — most useful on tablets, " +
                    "foldables and phones in landscape. The spacebar is divided between the " +
                    "two halves.",
            ) { scope.launch { repository.setSplitKeyboard(it) } }
        }
        if (settings.splitKeyboard) {
            item {
                SliderSetting(
                    "Split gap",
                    subtitle = "Width of the gap between the halves",
                    value = settings.splitGapPercent.toFloat(),
                    range = 5f..40f,
                    display = { "${it.toInt()}%" },
                    info = "The center gap, as a percentage of the keyboard width. Bigger gaps " +
                        "push the halves further toward the edges but make each key narrower.",
                ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
            }
        }
        item {
            ToggleSetting(
                "Floating keyboard", "Detach the keyboard into a movable panel", settings.floatingKeyboard,
                info = "The keyboard becomes a compact floating panel that hovers over apps " +
                    "instead of docking to the bottom of the screen. Drag the pill at the top " +
                    "of the panel to move it, drag the corner grip to resize it, and tap the " +
                    "dock button to return to the normal keyboard. Apps are no longer resized " +
                    "while it floats, and touches outside the panel go straight to the app.",
            ) { scope.launch { repository.setFloatingKeyboard(it) } }
        }
        if (settings.floatingKeyboard) {
            item {
                SliderSetting(
                    "Floating keyboard width",
                    subtitle = "Also adjustable by dragging the panel's corner grip",
                    value = settings.floatingWidthDp.toFloat(),
                    range = 240f..500f,
                    display = { "${it.toInt()} dp" },
                    info = "Width of the floating panel. Key heights still follow the sliders " +
                        "above.",
                ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
            }
        }
    }

    SettingsGroup("Bottom-row keys") {
        item {
            ToggleSetting(
                "Comma key opens emoji",
                "Replace the comma key with an emoji key",
                settings.commaAsEmoji,
                info = "The bottom-row comma key becomes an emoji-panel key; comma moves " +
                    "into its long-press alternates. Turning this on also removes the " +
                    "emoji tool from the toolbar since the key replaces it — drag it back " +
                    "out of the toolbox if you want both.",
            ) { scope.launch { repository.setCommaAsEmoji(it) } }
        }
        item {
            ToggleSetting(
                "Emoji key instead of 🌐",
                "Replace the language key with an emoji key",
                settings.globeAsEmoji,
                info = "The bottom-row 🌐 key opens the emoji panel instead of switching " +
                    "language. Language switching stays available on the spacebar: set a " +
                    "swipe to \"Language\" under Typing → Gestures (a quick swipe does it " +
                    "by default). Turn this off to get the 🌐 key back.",
            ) { scope.launch { repository.setGlobeAsEmoji(it) } }
        }
    }
}

// ---- languages ----

@Composable
private fun LanguageSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    CaptionText(
        "Every enabled layout is its own input mode; cycle between them with " +
            "the 🌐 key or a spacebar swipe.",
    )
    // "Your languages" is the enabled set (deduped, in switch order); each opens
    // its detail. Adding one is a search over the whole registry.
    SettingsGroup("Your languages") {
        for (language in settings.enabledLanguages) {
            item {
                val names = settings.enabledLayoutIds
                    .filter { resolveLayout(settings.customLayouts, it).language().id == language.id }
                    .joinToString { resolveLayout(settings.customLayouts, it).name }
                NavRow(language.displayName, subtitle = names.ifBlank { null }) {
                    onNavigate("language/${language.id}")
                }
            }
        }
        item {
            NavRow(
                "Add language",
                subtitle = "Type in any of ${LanguageRegistry.all.size} languages",
            ) { onNavigate("add_language") }
        }
    }
    // Reorder the switch ring (spacebar swipe / 🌐 cycle) across every enabled
    // layout, not just languages, so AZERTY and QWERTY keep distinct slots.
    if (settings.enabledLayoutIds.size > 1) {
        SettingsGroup {
            item {
                ReorderSetting(
                    "Switch order",
                    "Reorder input languages",
                    settings.enabledLayoutIds,
                    label = { resolveLayout(settings.customLayouts, it).name },
                    onReordered = { scope.launch { repository.setEnabledLayoutIds(it) } },
                )
            }
        }
    }
    // Custom layouts get their own group after the languages: they are the
    // user's own grids (edited on the Key layouts screen), not a language to add.
    // Only the user's own grids. An override of a shipped layout — built-in or
    // JSON asset — is an edit of that layout, not a layout of their own, and
    // listing it here would show the same name twice: once as the language's
    // layout above, once as if they had made it.
    val customs = settings.customLayouts
        .filter {
            com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts.byId(it.id) == null &&
                com.wasimaster.wmkeyboard.core.layout.AssetLayouts.byId(it.id) == null
        }
        .sortedBy { it.name.lowercase() }
    SettingsGroup("Your layouts") {
        for (layout in customs) {
            item {
                ToggleSetting(
                    layout.name,
                    "Types with the ${baseModeTitle(layout)} dictionary",
                    layout.id in settings.enabledLayoutIds,
                ) { enable ->
                    scope.launch {
                        val next =
                            if (enable) settings.enabledLayoutIds + layout.id
                            else settings.enabledLayoutIds - layout.id
                        if (next.isNotEmpty()) repository.setEnabledLayoutIds(next.distinct())
                    }
                }
            }
        }
        item {
            NavRow(
                "Key layouts",
                subtitle = if (customs.isEmpty()) {
                    "Design your own key grid, or start from a built-in one"
                } else {
                    "Edit, add and remove layouts"
                },
            ) { onNavigate("keymaps") }
        }
    }
    SettingsGroup("Per-app language") {
        item {
            ToggleSetting(
                "Remember language per app",
                "Reopen each app in the layout you last used there",
                settings.perAppLanguage.enabled,
                info = "When you switch language (🌐 key, spacebar swipe or the picker) while " +
                    "typing in an app, the keyboard remembers that choice against the app and " +
                    "restores it the next time you type there. Apps you haven't picked a " +
                    "language in follow your last-used one. A field that requires Latin (like a " +
                    "password box) or advertises its own language still overrides this.",
            ) { scope.launch { repository.setRememberLayoutPerApp(it) } }
        }
    }
    SettingsGroup("System language switcher") {
        item {
            ToggleSetting(
                "List languages in Android's switcher",
                "Add each language to the system input-method switcher",
                settings.osLanguageSwitcher,
                info = "Registers every enabled layout as an Android input-method subtype, so " +
                    "the system's \"Choose input method\" sheet lists them and can switch " +
                    "between them. Turn this off to keep language switching entirely inside " +
                    "the keyboard (🌐 key, spacebar swipe, or the picker) and expose no " +
                    "subtypes to the system.",
            ) { scope.launch { repository.setOsLanguageSwitcher(it) } }
        }
        if (settings.osLanguageSwitcher) {
            item {
                ToggleSetting(
                    "Show app name first",
                    "Label reads \"WM Keyboard · <language>\"",
                    settings.subtypeAppNameFirst,
                    info = "Puts the app name ahead of the language in the switcher's label. " +
                        "Android itself decides how the label and the app name are styled " +
                        "(which one is bold or greyed), so this changes only the label text, " +
                        "not that styling.",
                ) { scope.launch { repository.setSubtypeAppNameFirst(it) } }
            }
        }
    }
    // Only shown when a conjunct-forming (Indic/Brahmic) script is enabled — the
    // setting drives cluster-aware deletion for every INDIC_CLUSTER script
    // (Bengali, Devanagari, Tamil, …), not Bengali alone.
    if (settings.enabledLanguages.any {
            ScriptRegistry[it.script].composer == ComposerType.INDIC_CLUSTER
        }
    ) {
        SettingsGroup("Complex scripts") {
            item {
                ToggleSetting(
                    "Conjunct-aware backspace",
                    "Delete a whole conjunct (যুক্তবর্ণ like ক্ষ, or क्ष) as one unit",
                    settings.conjunctBackspace,
                    info = "Normally backspace removes one code point at a time, which can leave " +
                        "half-formed conjuncts. With this on, a conjunct cluster like স্ত্রী or " +
                        "क्ष is deleted in a single press.",
                ) {
                    scope.launch { repository.setConjunctBackspace(it) }
                }
            }
        }
    }
}

// ---- emoji ----

@Composable
private fun EmojiSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Access") {
        item {
            ToggleSetting(
                "Emoji button in toolbar", "One-tap emoji access from the top bar", settings.emojiToolbar,
                info = "Keeps the emoji button visible in the top bar even while suggestions " +
                    "are showing. The emoji panel itself has tabs per category, search in " +
                    "English and Bengali, and skin-tone variants on long-press.",
            ) { scope.launch { repository.setEmojiToolbar(it) } }
        }
        item {
            ToggleSetting(
                "Full-screen emoji panel",
                "Hide the toolbar and move the category tabs up next to a back button",
                settings.emojiFullBleed,
                info = "The emoji panel takes over the whole keyboard: the toolbar, emoji " +
                    "row and symbol row step aside and the category tabs move into the row " +
                    "they leave behind, next to a back button. Turn this off to keep the " +
                    "toolbar within reach while picking emoji.",
            ) { scope.launch { repository.setEmojiFullBleed(it) } }
        }
    }
    SettingsGroup("Suggestions") {
        item {
            ToggleSetting(
                "Emoji suggestions",
                "Offer emojis while typing — birthday suggests 🎂 🎉 🥳",
                settings.emojiPrediction,
                info = "Matching emojis appear at the end of the suggestion strip while you " +
                    "type, in English or Bengali (জন্মদিন also suggests 🎂).",
            ) { scope.launch { repository.setEmojiPrediction(it) } }
        }
        if (settings.emojiPrediction) {
            item {
                ChoiceSetting(
                    title = "Emoji suggestion tap",
                    subtitle = "What happens to the word you typed",
                    info = "\"Replace word\" swaps the typed word for the emoji (typing " +
                        "birthday and tapping 🎂 leaves just 🎂, like Gboard). \"Keep word\" " +
                        "adds the emoji after it: birthday 🎂.",
                    options = listOf(
                        EmojiInsertMode.REPLACE to "Replace word",
                        EmojiInsertMode.APPEND to "Keep word",
                    ),
                    selected = settings.emojiInsertMode,
                ) { scope.launch { repository.setEmojiInsertMode(it) } }
            }
        }
    }
    SettingsGroup("Skin tone") {
        item {
            ChoiceSetting(
                title = "Default skin tone",
                subtitle = "For toned emoji in suggestions and emoji search",
                info = "Toned emoji (👍, 🙏, 🧑…) are shown with this Fitzpatrick tone in the " +
                    "suggestion strip and the emoji search results, and inserted that way. " +
                    "The emoji panel's own grid still follows the tone you last picked per " +
                    "emoji. \"None\" keeps the neutral yellow base.",
                options = listOf(
                    EmojiSkinTone.NONE to "✋",
                    EmojiSkinTone.LIGHT to "✋🏻",
                    EmojiSkinTone.MEDIUM_LIGHT to "✋🏼",
                    EmojiSkinTone.MEDIUM to "✋🏽",
                    EmojiSkinTone.MEDIUM_DARK to "✋🏾",
                    EmojiSkinTone.DARK to "✋🏿",
                ),
                selected = settings.emoji.defaultSkinTone,
            ) { scope.launch { repository.setEmojiDefaultSkinTone(it) } }
        }
        item {
            ToggleSetting(
                "Override with last used",
                "Prefer the tone you last picked for an emoji over the default",
                settings.emoji.toneOverrideByLastUsed,
                info = "When on, an emoji you have already picked a tone for (from its " +
                    "long-press popup on the panel) shows that tone in suggestions and search " +
                    "instead of the default above. Off (the default) means the default skin " +
                    "tone always wins in those two places.",
            ) { scope.launch { repository.setEmojiToneOverrideByLastUsed(it) } }
        }
    }
    SettingsGroup("Emoji panel") {
        item {
            ToggleSetting(
                "Return to keyboard after inserting",
                "Close the panel the moment you insert one emoji or paste one clip",
                settings.emoji.closeAfterInsert,
                info = "By default the emoji and clipboard panels stay open so you can pick " +
                    "several items in a row. Turn this on to jump straight back to the keys " +
                    "after a single emoji or clipboard paste.",
            ) { scope.launch { repository.setEmojiCloseAfterInsert(it) } }
        }
        item {
            ChoiceSetting(
                title = "History tab",
                subtitle = "What the first emoji-panel tab shows",
                info = "\"Recent\" lists emojis in the order you last used them; \"Most used\" " +
                    "ranks them by how often you use them. Favourited emojis are always " +
                    "pinned to the front of either list.",
                options = listOf(
                    EmojiTabMode.RECENTS to "Recent",
                    EmojiTabMode.MOST_USED to "Most used",
                ),
                selected = settings.emojiTabMode,
            ) { scope.launch { repository.setEmojiTabMode(it) } }
        }
        item {
            ToggleSetting(
                "Clear recents button",
                "A button on the Recent tab to wipe the recents list",
                settings.emojiClearRecentsButton,
                info = "Adds a \"Clear recents\" button to the emoji panel's Recent tab. " +
                    "Off by default so the tab stays uncluttered.",
            ) { scope.launch { repository.setEmojiClearRecentsButton(it) } }
        }
        item {
            ToggleSetting(
                "Kaomoji and Emoticons",
                "Two extra tabs: ¯\\_(ツ)_/¯ and :-)",
                settings.emoji.kaomojiTabs,
                info = "Adds a Kaomoji tab (Japanese-style faces like (╯°□°）╯︵ ┻━┻) and an " +
                    "Emoticons tab (Western ASCII ones like :-D and <3) to the end of the " +
                    "emoji panel's tabs, each grouped by mood. Tapping one types it as plain " +
                    "text — it isn't added to your emoji history.",
            ) { scope.launch { repository.setEmojiKaomojiTabs(it) } }
        }
        item {
            ToggleSetting(
                "Emoji descriptions",
                "Name the emoji at the top of its long-press popup",
                settings.emojiLongPressName,
                info = "Long-pressing an emoji shows its Unicode name (\"skull and " +
                    "crossbones\") above the favourite and variant controls, so you can " +
                    "tell near-identical emojis apart.",
            ) { scope.launch { repository.setEmojiLongPressName(it) } }
        }
        item {
            NavRow(
                "Emoji keywords",
                "Download or import keywords to search emoji in another language",
            ) { onNavigate("emojikeywords") }
        }
    }
    SettingsGroup("Emoji row") {
        item {
            ChoiceSetting(
                title = "Emoji row",
                subtitle = "A dedicated row of your emojis, like Gboard",
                info = "\"Own row\" keeps a persistent emoji row between the toolbar and the " +
                    "keys. \"Button\" adds a toggle at the right edge of the toolbar that " +
                    "swaps the strip for the emoji row. \"Off\" hides both.",
                options = listOf(
                    EmojiBarMode.OFF to "Off",
                    EmojiBarMode.BUTTON to "Button",
                    EmojiBarMode.ALWAYS to "Own row",
                ),
                selected = settings.emojiBarMode,
            ) { scope.launch { repository.setEmojiBarMode(it) } }
        }
        if (settings.emojiBarMode != EmojiBarMode.OFF) {
            item {
                ChoiceSetting(
                    title = "Emoji row content",
                    subtitle = "Favourites always come first",
                    options = listOf(
                        EmojiBarContent.MOST_USED to "Most used",
                        EmojiBarContent.RECENTS to "Recent",
                        EmojiBarContent.FAVOURITES to "Favourites",
                    ),
                    selected = settings.emojiBarContent,
                ) { scope.launch { repository.setEmojiBarContent(it) } }
            }
            item {
                SliderSetting(
                    title = "Emojis in the row",
                    subtitle = "How many fit across — higher packs them tighter",
                    value = settings.emoji.barCount.toFloat(),
                    range = EmojiBarCountRange.first.toFloat()..EmojiBarCountRange.last.toFloat(),
                    display = { "${it.roundToInt()}" },
                    info = "The row splits its width into this many slots and shrinks the " +
                        "emoji to fit them, so a higher number means smaller, more tightly " +
                        "packed emoji. With scrolling off, emoji past the last slot are not " +
                        "shown at all; with it on, they are a swipe away.",
                ) { scope.launch { repository.setEmojiBarCount(it.roundToInt()) } }
            }
            item {
                ToggleSetting(
                    "Scroll the emoji row",
                    "Swipe sideways for the emoji past the visible ones",
                    settings.emoji.barScrollable,
                    info = "Off (the default) the row is a fixed set of taps: it shows only " +
                        "as many emoji as fit and never moves, so a sideways swipe can't " +
                        "slide it out from under your finger. On, the extras stay reachable " +
                        "by scrolling.",
                ) { scope.launch { repository.setEmojiBarScrollable(it) } }
            }
        }
    }
    if (settings.emojiBarMode == EmojiBarMode.ALWAYS) {
        CaptionText(
            "Where the emoji row sits relative to the toolbar and symbol row " +
                "is set in Rows & bars on the settings home screen.",
        )
    }
    SettingsGroup("Emoji style") {
        item {
            val context = LocalContext.current
            // Bumped after an import so the preview re-resolves the (same-named) file.
            var fontRefresh by remember { mutableIntStateOf(0) }
            ChoiceSetting(
                title = "Emoji font",
                subtitle = "How emojis look on the keyboard itself",
                info = "\"System\" uses your phone's emoji pack — on Samsung phones " +
                    "that is Samsung's own set. \"Google\" fetches Noto Color Emoji " +
                    "(the stock-Android look) once through the system font provider " +
                    "and caches it on the device. \"Installed\" uses an emoji font " +
                    "from Addons, such as Twemoji or OpenMoji. \"Custom\" uses a " +
                    "single emoji font file you import below. Text you send is plain " +
                    "Unicode either way — other apps and other phones still draw it " +
                    "with their own emoji font.",
                options = listOf(
                    EmojiFontChoice.SYSTEM to "System",
                    EmojiFontChoice.NOTO to "Google",
                    EmojiFontChoice.INSTALLED to "Installed",
                    EmojiFontChoice.CUSTOM to "Custom",
                ),
                selected = settings.emojiFont,
            ) { scope.launch { repository.setEmojiFont(it) } }
            val previewFamily = remember(
                settings.emojiFont,
                settings.emojiFontInstalled.installedId,
                fontRefresh,
            ) {
                KeyboardFonts.emojiFamily(
                    context,
                    settings.emojiFont,
                    settings.emojiFontInstalled.installedId,
                )
            }
            // Shaped exactly as the keyboard shapes them, so the preview shows
            // what the panel will show — ❤️ in particular is the one that
            // silently comes from the system font in a font without a
            // variation-selector table (see EmojiFontShaping).
            // Read off the main thread, so the row draws unshaped for a frame
            // rather than stalling the screen on a megabyte of font tables.
            val previewShaper by produceState(
                EmojiFontShaping.Identity,
                settings.emojiFont,
                settings.emojiFontInstalled.installedId,
                fontRefresh,
            ) {
                val file = KeyboardFonts.emojiFontFile(
                    context,
                    settings.emojiFont,
                    settings.emojiFontInstalled.installedId,
                )
                withContext(Dispatchers.Default) { EmojiFontShaping.warm(file) }
                value = EmojiFontShaping.forFontFile(file)
            }
            // One Text per emoji: emoji fonts often have no space glyph, so drawing
            // them as a single spaced string makes the glyphs overlap.
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                for (emoji in listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "❤️", "🎉")) {
                    val spelling = previewShaper.spelling(emoji)
                    Text(
                        spelling.text,
                        fontSize = 24.sp,
                        fontFamily = if (spelling.systemFont) null else previewFamily,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            if (settings.emojiFont == EmojiFontChoice.INSTALLED) {
                InstalledEmojiFontList(repository, settings)
            }
            if (settings.emojiFont == EmojiFontChoice.CUSTOM) {
                val importEmojiFont = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            importFontFile(context, uri, KeyboardFonts.customEmojiFontFile(context))
                        }
                        fontRefresh++
                    }
                }
                val imported = KeyboardFonts.customEmojiFontFile(context).exists()
                if (!imported) {
                    Text(
                        "No emoji font imported yet — the system font is used until then.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importEmojiFont.launch(FONT_MIME_TYPES) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(if (imported) "Replace emoji font file" else "Import emoji font file") }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            // The phone is always the one to blame here: an emoji the chosen
            // font is missing is drawn in the phone's own emoji font instead,
            // so the only emoji that stay blank are the ones neither has.
            val ownFont = settings.emojiFont != EmojiFontChoice.SYSTEM
            ToggleSetting(
                "Hide emoji this phone can't display",
                "Skip emoji that show as a blank box in the panel, search and suggestions",
                settings.emoji.hideUnrenderable,
                info = "Older phones (and some brands) ship an emoji font that predates the " +
                    "newest emoji, which then render as an empty \"tofu\" box. This hides " +
                    "any emoji your phone's emoji font can't draw. To see them all instead, " +
                    "set the emoji font above to \"Google\" (Noto Color Emoji), or install a " +
                    "complete emoji font (such as Twemoji or OpenMoji) from Addons — WM " +
                    "Keyboard ships no emoji font of its own, it uses the one you choose " +
                    "here." + if (ownFont) {
                        "\n\nThe font chosen above only covers the emoji it was built " +
                            "with, and the newest ones are usually missing from anything " +
                            "but the latest release. Those are not hidden: each one is " +
                            "drawn in the phone's own emoji font instead, so it still " +
                            "appears — in a different style."
                    } else {
                        ""
                    },
            ) { scope.launch { repository.setHideUnrenderableEmoji(it) } }
        }
    }
    CaptionText(
        "Tip: long-press any emoji in the panel to favourite it or pick skin tones — " +
            "two-person emojis like 🤝 let you set each person's tone separately.",
    )
}

/**
 * The emoji faces in the font library, with the one in use ticked.
 *
 * Emoji fonts live in the same [FontStore] as the text faces — same files, same
 * lifecycle — but are listed apart, since drawing key labels in a colour emoji
 * font is not a choice anyone makes on purpose.
 */
@Composable
private fun InstalledEmojiFontList(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { FontStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val fonts = remember(revision) { store.emojiFonts() }

    if (fonts.isEmpty()) {
        CaptionText(
            "No emoji fonts installed. Install one from Addons — Twemoji, OpenMoji " +
                "and the like — or import a single file under \"Custom\".",
        )
        return
    }
    for (font in fonts) {
        val selected = settings.emojiFontInstalled.installedId == font.id
        ListItem(
            headlineContent = { Text(font.name) },
            supportingContent = font.author.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            repository.forgetInstalledEmojiFont(font.id)
                            withContext(Dispatchers.IO) { store.delete(font.id) }
                        }
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove ${font.name}")
                    }
                }
            },
            colors = transparentListColors(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { scope.launch { repository.setInstalledEmojiFont(font.id) } },
        )
    }
}

// ---- personal dictionary ----

/**
 * The learned-words file, edited directly from the settings app. Every
 * change bumps the DataStore lexicon version so the IME (which holds its
 * own in-memory copy) reloads from disk instead of clobbering the edit.
 */
@Composable
private fun DictionarySettings(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = remember { java.io.File(context.filesDir, "learning/user_lexicon.json") }
    // UserLexicon's constructor reads and JSON-parses the whole learned-words
    // file, so it (and every save) runs on Dispatchers.IO, never in composition
    // or on a click handler. The list draws empty for a moment then fills in.
    var lexicon by remember { mutableStateOf<UserLexicon?>(null) }
    var words by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val lex = withContext(Dispatchers.IO) { UserLexicon(file) }
        words = lex.allWords().sortedByDescending { it.second }
        lexicon = lex
    }

    fun persist(mutate: (UserLexicon) -> Unit) {
        val lex = lexicon ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                mutate(lex)
                lex.save()
            }
            words = lex.allWords().sortedByDescending { it.second }
            repository.bumpLexiconVersion()
        }
    }

    Text(
        "Words the keyboard has learned from your typing, plus any you add " +
            "yourself. They are suggested while typing and never autocorrected " +
            "away. Everything stays on this device.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Add word") }
    Spacer(Modifier.height(12.dp))
    if (words.isEmpty()) {
        CaptionText(
            "Nothing here yet — words appear as you type (with \"Learn from " +
                "typing\" on under Privacy), or add one above.",
        )
    }
    SettingsGroup {
        for ((word, count) in words) {
            item {
                ListItem(
                    headlineContent = { Text(word) },
                    supportingContent = {
                        Text(if (count >= 200) "Added by you" else "Seen $count×")
                    },
                    trailingContent = {
                        IconButton(onClick = { persist { it.forget(word) } }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove $word")
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }

    if (showAdd) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add word") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Word") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        persist { it.addWord(input.trim()) }
                        showAdd = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ---- suggestion blacklist ----

/**
 * The never-suggest word list, stored in settings. A blacklisted word is kept
 * out of the suggestion strip and never used as an autocorrect target, but can
 * still be typed and committed normally. Matched case-insensitively.
 */
@Composable
private fun BlacklistSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val words = remember(settings.suggestionBlacklist) {
        settings.suggestionBlacklist.sorted()
    }
    var showAdd by remember { mutableStateOf(false) }

    Text(
        "Words the keyboard should never suggest or autocorrect to. You can " +
            "still type and send them — they just stay out of the suggestion " +
            "strip. Matching ignores capitalization.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Add word") }
    Spacer(Modifier.height(12.dp))
    if (words.isEmpty()) {
        CaptionText("Nothing blacklisted yet — add a word above.")
    }
    SettingsGroup {
        for (word in words) {
            item {
                ListItem(
                    headlineContent = { Text(word) },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch { repository.removeSuggestionBlacklistWord(word) }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove $word")
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }

    if (showAdd) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add word") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Word") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        scope.launch { repository.addSuggestionBlacklistWord(input) }
                        showAdd = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ---- backup ----

/** Human name for a bundle section, used in toggles and the import dialog. */
internal fun sectionLabel(section: ConfigBackup.Section): String = when (section) {
    ConfigBackup.Section.SETTINGS -> "Settings"
    ConfigBackup.Section.THEMES -> "Themes"
    ConfigBackup.Section.DICTIONARY -> "Dictionary"
    ConfigBackup.Section.CLIPBOARD -> "Clipboard"
    ConfigBackup.Section.SNIPPETS -> "Snippets"
    ConfigBackup.Section.STICKERS -> "Sticker packs"
    ConfigBackup.Section.ICONS -> "Icon packs"
    ConfigBackup.Section.WORDLISTS -> "Custom word lists"
    ConfigBackup.Section.ADDONS -> "Addon repositories"
}

/** "3 themes", "1 snippet" — the count line shown per section on import. */
internal fun sectionSummary(section: ConfigBackup.Section, count: Int): String = when (section) {
    ConfigBackup.Section.SETTINGS -> "$count settings"
    ConfigBackup.Section.THEMES -> if (count == 1) "1 custom theme" else "$count custom themes"
    ConfigBackup.Section.DICTIONARY -> if (count == 1) "1 learned word" else "$count learned words"
    ConfigBackup.Section.CLIPBOARD -> if (count == 1) "1 clip" else "$count clips"
    ConfigBackup.Section.SNIPPETS -> if (count == 1) "1 snippet" else "$count snippets"
    ConfigBackup.Section.STICKERS -> if (count == 1) "1 sticker" else "$count stickers"
    ConfigBackup.Section.ICONS -> if (count == 1) "1 icon" else "$count icons"
    ConfigBackup.Section.WORDLISTS -> if (count == 1) "1 word list" else "$count word lists"
    ConfigBackup.Section.ADDONS -> if (count == 1) "1 repository" else "$count repositories"
}

/** A file picked for import, once we know which of the two formats it is. */
private sealed interface PendingImport {
    val text: String
    data class Config(override val text: String) : PendingImport
    data class Legacy(override val text: String) : PendingImport
}

@Composable
private fun BackupSettings(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // What the export file will contain. Configuration-like parts default on;
    // the personal ones (learned words) and the private, short-lived one
    // (clipboard) default off, so a backup shared or synced by habit doesn't
    // carry them without a deliberate opt-in.
    var includeSettings by remember { mutableStateOf(true) }
    var includeThemes by remember { mutableStateOf(true) }
    var includeSnippets by remember { mutableStateOf(true) }
    // Off by default only because the images make the file large, not because
    // there's anything private about them.
    var includeStickers by remember { mutableStateOf(false) }
    var includeIcons by remember { mutableStateOf(false) }
    var includeWordlists by remember { mutableStateOf(true) }
    // Just the list of repository addresses — small, and the one part of the
    // addon setup a restore would otherwise lose.
    var includeAddons by remember { mutableStateOf(true) }
    var includeDictionary by remember { mutableStateOf(false) }
    var includeClipboard by remember { mutableStateOf(false) }
    var includeSecrets by remember { mutableStateOf(false) }

    var message by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<PendingImport?>(null) }

    fun selectedSections(): Set<ConfigBackup.Section> = buildSet {
        if (includeSettings) add(ConfigBackup.Section.SETTINGS)
        if (includeThemes) add(ConfigBackup.Section.THEMES)
        if (includeDictionary) add(ConfigBackup.Section.DICTIONARY)
        if (includeClipboard) add(ConfigBackup.Section.CLIPBOARD)
        if (includeSnippets) add(ConfigBackup.Section.SNIPPETS)
        if (includeStickers) add(ConfigBackup.Section.STICKERS)
        if (includeIcons) add(ConfigBackup.Section.ICONS)
        if (includeWordlists) add(ConfigBackup.Section.WORDLISTS)
        if (includeAddons) add(ConfigBackup.Section.ADDONS)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigBackup.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = selectedSections()
        scope.launch {
            val ok = runCancellable {
                val text = repository.exportConfig(
                    sections = sections,
                    includeSecrets = includeSecrets,
                    appVersion = BuildConfig.VERSION_CODE,
                    appVersionName = BuildConfig.VERSION_NAME,
                )
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    } ?: error("no stream")
                }
            }.isSuccess
            message = when {
                !ok -> "Could not write that file."
                includeSettings && includeSecrets ->
                    "Backup exported, API keys included. Treat that file as a password."
                else -> "Backup exported."
            }
        }
    }

    // Import reads the file first and asks before writing: restoring is not
    // something to discover you have done. Both the full-config bundle and the
    // older settings-only file are accepted.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri)
                        .use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            confirmImport = when {
                text == null -> { message = "Could not read that file."; null }
                ConfigBackup.decode(text) != null -> PendingImport.Config(text)
                SettingsBackup.decode(text) != null -> PendingImport.Legacy(text)
                else -> { message = "That file is not a WMKeyboard backup."; null }
            }
        }
    }

    Text(
        "Save your keyboard to a file you can keep, move to another phone, or " +
            "restore after a reinstall. Choose what goes in it below — settings, " +
            "themes, your learned dictionary, clipboard history and snippets can " +
            "each be included or left out.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    SettingsGroup("Include in export") {
        item {
            ToggleSetting(
                "Settings",
                "Every keyboard preference",
                includeSettings,
            ) { includeSettings = it }
        }
        if (includeSettings) {
            item {
                ToggleSetting(
                    "Include API keys",
                    "Translate, GIF, search and AI keys",
                    includeSecrets,
                    info = "Off by default, because a backup file is easy to mail to " +
                        "yourself or drop in a shared folder, and these keys spend real " +
                        "money on your accounts.\n\n" +
                        "Turn it on only for a backup you keep to yourself — anyone who " +
                        "opens that file can use your keys.",
                ) { includeSecrets = it }
            }
        }
        item {
            ToggleSetting(
                "Themes",
                "Your custom themes",
                includeThemes,
                info = "Colours, gradients and layout of your saved themes. A theme's " +
                    "background image doesn't travel to another phone — only the theme " +
                    "itself does.",
            ) { includeThemes = it }
        }
        item {
            ToggleSetting(
                "Dictionary",
                "Words the keyboard learned from you",
                includeDictionary,
                info = "Your personal vocabulary and next-word patterns. Off by default " +
                    "since it's personal typing data — turn it on for a backup you keep " +
                    "to yourself.",
            ) { includeDictionary = it }
        }
        item {
            ToggleSetting(
                "Clipboard",
                "Saved clipboard history",
                includeClipboard,
                info = "Your pinned and recent text clips. Images and files are left out " +
                    "because they live on this device and wouldn't open elsewhere.",
            ) { includeClipboard = it }
        }
        item {
            ToggleSetting(
                "Snippets",
                "Your saved text snippets",
                includeSnippets,
            ) { includeSnippets = it }
        }
        item {
            ToggleSetting(
                "Sticker packs",
                "Your own sticker packs, images and all",
                includeStickers,
                info = "The images travel inside the backup, so a restore on another " +
                    "phone gets working packs — but it can add megabytes to the file. " +
                    "For one pack, exporting a .wmstickers file is smaller.",
            ) { includeStickers = it }
        }
        item {
            ToggleSetting(
                "Icon packs",
                "Your own icon packs, images and all",
                includeIcons,
                info = "The SVGs travel inside the backup, so a restore on another " +
                    "phone gets working packs — but it can add to the file size.",
            ) { includeIcons = it }
        }
        item {
            ToggleSetting(
                "Custom word lists",
                "Word lists you imported for any language",
                includeWordlists,
            ) { includeWordlists = it }
        }
        item {
            ToggleSetting(
                "Addon repositories",
                "The addon sources you added, so you can reinstall from them",
                includeAddons,
                info = "Just the list of addresses. The addons themselves ride along " +
                    "in the sections above — an installed theme is a custom theme, an " +
                    "installed pack is an icon pack.",
            ) { includeAddons = it }
        }
    }

    SettingsGroup {
        item {
            OutlinedButton(
                enabled = selectedSections().isNotEmpty(),
                onClick = {
                    // Datestamp the default name so successive backups don't
                    // overwrite each other and each file self-labels when it was made.
                    val stamp = java.time.format.DateTimeFormatter
                        .ofPattern("yyyyMMdd-HHmmss")
                        .format(java.time.LocalDateTime.now())
                    exportLauncher.launch(
                        "wmkeyboard-backup-$stamp.${ConfigBackup.FILE_EXTENSION}",
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Export backup") }
        }
    }

    SettingsGroup("Import") {
        item {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Import backup") }
        }
    }
    CaptionText(
        "Importing settings overwrites only the preferences named in the file " +
            "and leaves the rest as they are. Dictionary, clipboard and snippets " +
            "in the file replace what's on this device.",
    )
    Spacer(Modifier.height(16.dp))

    when (val pending = confirmImport) {
        is PendingImport.Config -> {
            val parsed = remember(pending.text) { ConfigBackup.decode(pending.text) }
            val counts = remember(pending.text) { parsed?.let { repository.describeConfig(it) }.orEmpty() }
            val hasSecrets = remember(pending.text) { parsed?.let { repository.configContainsSecrets(it) } ?: false }
            AlertDialog(
                onDismissRequest = { confirmImport = null },
                title = { Text("Import backup?") },
                text = {
                    Text(
                        buildString {
                            append("This file contains:\n")
                            for ((section, count) in counts) {
                                append("\n• ${sectionLabel(section)}: ${sectionSummary(section, count)}")
                            }
                            append("\n\nSettings merge into your current ones; dictionary, ")
                            append("clipboard and snippets replace what's on this device.")
                            if (hasSecrets) {
                                append("\n\nThe file includes API keys, which will replace the ")
                                append("ones set here.")
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmImport = null
                        scope.launch {
                            message = when (val result = repository.importConfig(pending.text)) {
                                is SettingsRepository.ConfigImportResult.Applied -> buildString {
                                    if (result.restored.isEmpty()) {
                                        append("Nothing to restore from that file.")
                                    } else {
                                        append("Restored ")
                                        append(result.restored.joinToString { sectionLabel(it).lowercase() })
                                        append(".")
                                    }
                                    if (result.settingsFailed) {
                                        append("\n\nThe settings couldn't be applied and were " +
                                            "left unchanged.")
                                    }
                                }
                                SettingsRepository.ConfigImportResult.NotABackup ->
                                    "That file is not a WMKeyboard backup."
                            }
                        }
                    }) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmImport = null }) { Text("Cancel") }
                },
            )
        }
        is PendingImport.Legacy -> {
            val parsed = remember(pending.text) { SettingsBackup.decode(pending.text) }
            AlertDialog(
                onDismissRequest = { confirmImport = null },
                title = { Text("Import settings?") },
                text = {
                    Text(
                        buildString {
                            append("This will overwrite ${parsed?.entries?.size ?: 0} settings ")
                            append("with the values in that file.")
                            if (parsed?.containsSecrets == true) {
                                append("\n\nThe file includes API keys, which will replace the ones ")
                                append("set here.")
                            }
                            if ((parsed?.skipped ?: 0) > 0) {
                                append("\n\n${parsed?.skipped} entries could not be read and will ")
                                append("be skipped.")
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmImport = null
                        scope.launch {
                            message = when (val result = repository.importSettings(pending.text)) {
                                is SettingsRepository.ImportResult.Applied ->
                                    "Restored ${result.settings} settings."
                                SettingsRepository.ImportResult.RolledBack ->
                                    "That backup could not be applied — your settings are unchanged."
                                SettingsRepository.ImportResult.NotABackup ->
                                    "That file is not a WMKeyboard settings backup."
                            }
                        }
                    }) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmImport = null }) { Text("Cancel") }
                },
            )
        }
        null -> {}
    }

    val messageText = message
    if (messageText != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(messageText) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

// ---- custom dictionaries ----

/** Human name for a language id, used as the word-list group header. */
private fun languageLabel(langId: String): String =
    LanguageRegistry.byId(langId).englishName

/** One imported list: the file plus how many words it parsed to. */
private data class WordListEntry(val file: java.io.File, val words: Int)

@Composable
private fun CustomDictionarySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lists by remember {
        mutableStateOf<Map<String, List<WordListEntry>>>(emptyMap())
    }
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var urlDialogFor by remember { mutableStateOf<String?>(null) }

    // Counting words means reading every list, so it never runs on the main
    // thread — the screen draws empty for a moment and fills in.
    suspend fun refresh() {
        lists = withContext(Dispatchers.IO) {
            settings.enabledLanguages.associate { language ->
                language.id to CustomDictionaries.lists(context.filesDir, language.id).map { file ->
                    val words = runCatching {
                        file.inputStream().use { DictionaryLoader.loadEntries(it).size }
                    }.getOrDefault(0)
                    WordListEntry(file, words)
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun importFromUrl(langId: String, url: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(url.trim())
                    if (uri.scheme != "http" && uri.scheme != "https") {
                        return@runCatching -2
                    }
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
                        ?: "wordlist"
                    val temp = java.io.File.createTempFile("dict_url_", ".tmp", context.cacheDir)
                    try {
                        ToolHttp.download(url.trim(), temp, maxBytes = CustomDictionaries.MAX_BYTES)
                        temp.inputStream().use { CustomDictionaries.import(context.filesDir, langId, name, it) }
                    } finally {
                        temp.delete()
                    }
                }.getOrElse { -1 }
            }
            busy = false
            message = when {
                result == -2 -> "Only http:// and https:// links are supported."
                result < 0 -> "Could not download that list — check the URL and try again."
                result == 0 -> "No words found in that file — is it a word list?"
                else -> "Added $result words to ${languageLabel(langId)}."
            }
            if (result > 0) {
                refresh()
                repository.bumpCustomDictVersion()
            }
        }
    }

    val importList = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val language = pending
        pending = null
        if (uri == null || language == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        } ?: "wordlist"
                    val size = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
                        } ?: -1L
                    if (size > CustomDictionaries.MAX_BYTES) return@runCatching -1
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching 0
                    stream.use { CustomDictionaries.import(context.filesDir, language, name, it) }
                }.getOrDefault(0)
            }
            busy = false
            message = when {
                result < 0 -> "That file is too large to import."
                result == 0 -> "No words found in that file — is it a word list?"
                else -> "Added $result words to ${languageLabel(language)}."
            }
            if (result > 0) {
                refresh()
                repository.bumpCustomDictVersion()
            }
        }
    }

    Text(
        "Import your own word lists so the keyboard can complete and correct " +
            "words it does not ship with. Most languages have no bundled " +
            "dictionary, so a list here — imported, or downloaded from " +
            "Settings → Languages — is what gives them suggestions; where one " +
            "is bundled, your lists stack on top of it.\n\n" +
            "Format: one word per line, optionally followed by a space and a " +
            "frequency number. Lines starting with # are ignored. Imported " +
            "words are never autocorrected away.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    for (language in settings.enabledLanguages) {
        val entries = lists[language.id].orEmpty()
        SettingsGroup(language.englishName) {
            for (entry in entries) {
                item {
                    ListItem(
                        headlineContent = { Text(entry.file.nameWithoutExtension) },
                        supportingContent = { Text("${entry.words} words") },
                        trailingContent = {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            CustomDictionaries.remove(entry.file)
                                        }
                                        refresh()
                                        repository.bumpCustomDictVersion()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription =
                                        "Remove ${entry.file.nameWithoutExtension}",
                                )
                            }
                        },
                        colors = transparentListColors(),
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            pending = language.id
                            importList.launch(arrayOf("*/*"))
                        },
                    ) { Text(if (entries.isEmpty()) "Import word list" else "Import another") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { urlDialogFor = language.id },
                    ) { Text("From URL") }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    val messageText = message
    if (messageText != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(messageText) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    val urlLanguage = urlDialogFor
    if (urlLanguage != null) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialogFor = null },
            title = { Text("Load dictionary from URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("https://…") },
                    placeholder = { Text("Link to a plain-text word list") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        urlDialogFor = null
                        importFromUrl(urlLanguage, url)
                    },
                ) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { urlDialogFor = null }) { Text("Cancel") } },
        )
    }
}

// ---- emoji keyword packs ----

/** One imported emoji pack: the file plus how many emoji it names. */
private data class EmojiPackEntry(val file: java.io.File, val emoji: Int)

/**
 * Per-language emoji keyword packs: the downloadable dictionaries from the
 * data repo, and the user's own imports.
 *
 * Deliberately the same shape as [CustomDictionarySettings] — per-language
 * groups, a download row, import from a file or a URL, delete a row — because
 * it solves the same problem: the app can only bundle so many languages, and
 * everything past that has to arrive from somewhere else.
 */
@Composable
private fun EmojiKeywordSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var packs by remember { mutableStateOf<Map<String, List<EmojiPackEntry>>>(emptyMap()) }
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var urlDialogFor by remember { mutableStateOf<String?>(null) }

    // Enabled languages are the ones worth *offering* an import for, but a pack
    // can arrive for a language that isn't enabled — an addon repository
    // installs by langId, and languages get turned off again. Those groups
    // still have to appear or the pack would be uninstallable from here.
    val languageIds = remember(settings.enabledLanguages, packs.keys) {
        (settings.enabledLanguages.map { it.id } + packs.keys).distinct()
    }

    // Counting emoji means parsing every pack, so it never runs on the main
    // thread — the screen draws empty for a moment and fills in.
    suspend fun refresh() {
        packs = withContext(Dispatchers.IO) {
            // Enabled languages are the ones worth offering a download for,
            // but a pack can outlive the language being on — an addon repo
            // installs by langId, and languages get turned off again. Those
            // groups still have to appear or the pack is unreachable.
            val ids = (
                settings.enabledLanguages.map { it.id } +
                    EmojiKeywordPacks.languages(context.filesDir) +
                    EmojiDictStore.downloadedLanguageIds(context.filesDir)
                ).distinct()
            ids.associateWith { id ->
                EmojiKeywordPacks.packs(context.filesDir, id).map { file ->
                    val count = runCatching {
                        file.inputStream().use { EmojiKeywordPack.load(it).size }
                    }.getOrDefault(0)
                    EmojiPackEntry(file, count)
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    suspend fun finish(langId: String, result: Int) {
        busy = false
        message = when {
            result == -2 -> "Only http:// and https:// links are supported."
            result == -1 -> "That file is too large to import."
            result == 0 -> "No emoji found in that file — is it a keyword pack?"
            else -> "Added keywords for $result emoji to ${languageLabel(langId)}."
        }
        if (result > 0) {
            refresh()
            repository.bumpEmojiKeywordPackVersion()
        }
    }

    fun importFromUrl(langId: String, url: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(url.trim())
                    if (uri.scheme != "http" && uri.scheme != "https") {
                        return@runCatching -2
                    }
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
                        ?: "emoji"
                    val temp = java.io.File.createTempFile("emoji_url_", ".tmp", context.cacheDir)
                    try {
                        ToolHttp.download(url.trim(), temp, maxBytes = EmojiKeywordPack.MAX_BYTES)
                        temp.inputStream().use {
                            EmojiKeywordPacks.import(context.filesDir, langId, name, it)
                        }
                    } finally {
                        temp.delete()
                    }
                }.getOrElse { -1 }
            }
            finish(langId, result)
        }
    }

    val importPack = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val language = pending
        pending = null
        if (uri == null || language == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        } ?: "emoji"
                    val size = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
                        } ?: -1L
                    if (size > EmojiKeywordPack.MAX_BYTES) return@runCatching -1
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching 0
                    stream.use {
                        EmojiKeywordPacks.import(context.filesDir, language, name, it)
                    }
                }.getOrDefault(0)
            }
            finish(language, result)
        }
    }

    Text(
        "Emoji search ships with English and Bengali keywords. A pack adds " +
            "another language, so টাকা, dinero or お金 all find 💰 — and its " +
            "words show up as emoji suggestions while you type.\n\n" +
            "Packs stack on top of the bundled keywords rather than replacing " +
            "them, and a language may have several.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    SettingsGroup("Downloads") {
        item {
            ToggleSetting(
                "Download automatically",
                "Fetch keywords for the languages you type",
                settings.emoji.autoDownloadKeywords,
                info = "When you turn a language on, its emoji keywords are fetched in " +
                    "the background — around 100 KB, once. Off means the Download " +
                    "buttons below are the only way to get them.",
            ) { scope.launch { repository.setEmojiAutoDownloadKeywords(it) } }
        }
    }

    for (languageId in languageIds) {
        val entries = packs[languageId].orEmpty()
        val dict = EmojiDictCatalog.forLanguage(languageId)
        SettingsGroup(languageLabel(languageId)) {
            if (dict != null) {
                item { EmojiDictRow(dict) }
            }
            for (entry in entries) {
                item {
                    ListItem(
                        headlineContent = { Text(entry.file.nameWithoutExtension) },
                        supportingContent = { Text("${entry.emoji} emoji") },
                        trailingContent = {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            EmojiKeywordPacks.remove(entry.file)
                                        }
                                        refresh()
                                        repository.bumpEmojiKeywordPackVersion()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription =
                                        "Remove ${entry.file.nameWithoutExtension}",
                                )
                            }
                        },
                        colors = transparentListColors(),
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            pending = languageId
                            importPack.launch(arrayOf("*/*"))
                        },
                    ) { Text(if (entries.isEmpty()) "Import pack" else "Import another") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { urlDialogFor = languageId },
                    ) { Text("From URL") }
                }
            }
        }
    }

    Text(
        "Import format: a tab-separated file, one emoji per line, then its " +
            "comma-separated keywords, and optionally another tab and a display " +
            "name. Lines starting with \"# \" are ignored. The JSON emoji " +
            "dictionaries from the wmkeyboard-data repository import as they are.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Spacer(Modifier.height(16.dp))

    val messageText = message
    if (messageText != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(messageText) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    val urlLanguage = urlDialogFor
    if (urlLanguage != null) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialogFor = null },
            title = { Text("Load emoji pack from URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("https://…") },
                    placeholder = { Text("Link to an emoji keyword TSV") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        urlDialogFor = null
                        importFromUrl(urlLanguage, url)
                    },
                ) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { urlDialogFor = null }) { Text("Cancel") } },
        )
    }
}

// ---- fonts ----

/** Mime types SAF offers when picking a font; octet-stream covers file managers that don't tag fonts. */
private val FONT_MIME_TYPES = arrayOf(
    "font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream",
)

/**
 * Font picker: separate English and Bengali choices, each offering the
 * system default, the installed-font library, curated Google Fonts (every row
 * rendered in its own face as a live preview — faces download on first view and
 * are cached by the system provider), plus the legacy single imported file per
 * script.
 *
 * Importing a file here fills the library rather than overwriting one fixed
 * slot, so importing a second font no longer evicts the first. The two old
 * single-slot files still render and stay selectable for anyone who set one
 * before the library existed; nothing migrates and nothing is lost.
 */
@Composable
private fun FontSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fontStore = remember { FontStore.get(context) }
    val fontRevision by fontStore.revision.collectAsStateWithLifecycle()
    // Text faces only: an emoji font is chosen on the Emoji screen, and picking
    // one for the key labels would draw the alphabet as coloured pictograms.
    val installedFonts = remember(fontRevision) { fontStore.textFonts() }
    var fontMessage by remember { mutableStateOf<String?>(null) }

    fun importIntoLibrary(uri: android.net.Uri, apply: suspend (String) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri).use {
                        FontFile.import(it, fontStore, name = fontFileLabel(context, uri))
                    }
                }.getOrElse { FontImportResult.Failed(it.message ?: "Couldn't read that file") }
            }
            when (result) {
                is FontImportResult.Imported -> apply(FontStore.fontIdFor(result.font.id))
                is FontImportResult.NotAFont -> fontMessage = result.message
                FontImportResult.TooManyFonts ->
                    fontMessage = "You already have ${FontStore.MAX_FONTS} fonts. Remove one first."
                is FontImportResult.Failed -> fontMessage = result.message
            }
        }
    }

    fun deleteInstalled(font: InstalledFont) {
        scope.launch {
            val id = FontStore.fontIdFor(font.id)
            // Drop the selection first, so the keyboard never renders against a
            // file that is about to disappear.
            if (settings.keyFontId == id) repository.setKeyFontId(KeyboardFonts.DEFAULT_ID)
            if (settings.bengaliFontId == id) repository.setBengaliFontId(KeyboardFonts.DEFAULT_ID)
            withContext(Dispatchers.IO) { fontStore.delete(font.id) }
        }
    }

    fontMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { fontMessage = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { fontMessage = null }) { Text("OK") } },
        )
    }

    Text(
        "Applies to key labels, suggestions and the keyboard's panels. The " +
            "English font is used in English mode, the Bengali font in Avro, " +
            "প্রভাত and জাতীয় modes. Google fonts are fetched once through the " +
            "system font provider and cached on-device; missing glyphs fall " +
            "back to the system font automatically.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    FontPickerSection(
        header = "English font",
        sample = "The quick brown fox jumps over the lazy dog",
        selectedId = settings.keyFontId,
        googleNames = KeyboardFonts.googleFonts,
        customId = KeyboardFonts.CUSTOM_ID,
        customFile = KeyboardFonts.customFontFile(context),
        customName = settings.customFontName,
        onSelect = { id -> scope.launch { repository.setKeyFontId(id) } },
        onImport = { uri -> importIntoLibrary(uri) { repository.setKeyFontId(it) } },
        installedFonts = installedFonts,
        installedTitle = "Installed fonts",
        // The English picker also drives Cyrillic and Greek, which have no
        // picker of their own — a font claiming any of the three belongs here.
        scripts = setOf(ScriptId.LATIN, ScriptId.CYRILLIC, ScriptId.GREEK),
        onDeleteInstalled = ::deleteInstalled,
    )
    // The Bengali font picker is the one script-specific face the user chooses by
    // hand; every other non-Latin script uses its Noto face automatically. Only
    // show it when a Bengali-script language is enabled.
    if (settings.enabledLanguages.any { it.script == ScriptId.BENGALI }) {
        FontPickerSection(
            header = "Bengali font",
            sample = "আমি ভালো আছি · কখগঘঙ চছজঝঞ",
            selectedId = settings.bengaliFontId,
            googleNames = KeyboardFonts.bengaliGoogleFonts,
            customId = KeyboardFonts.CUSTOM_BENGALI_ID,
            customFile = KeyboardFonts.customBengaliFontFile(context),
            customName = settings.customBengaliFontName,
            onSelect = { id -> scope.launch { repository.setBengaliFontId(id) } },
            onImport = { uri -> importIntoLibrary(uri) { repository.setBengaliFontId(it) } },
            installedFonts = installedFonts,
            installedTitle = "Installed Bengali fonts",
            scripts = setOf(ScriptId.BENGALI),
            onDeleteInstalled = ::deleteInstalled,
        )
    }
    // Curated font pickers for the other non-Latin scripts, each shown only while
    // a language using that script is enabled. These offer the script's automatic
    // Noto face plus a few alternatives (no custom import — that stays English/
    // Bengali-only). Latin/Cyrillic/Greek follow the English font above.
    val enabledScripts = settings.enabledLanguages.mapTo(mutableSetOf()) { it.script }
    for (choices in KeyboardFonts.scriptFontChoices) {
        if (choices.script !in enabledScripts) continue
        FontPickerSection(
            header = "${choices.label} font",
            sample = choices.sample,
            selectedId = settings.scriptFontIds[choices.script.name] ?: KeyboardFonts.DEFAULT_ID,
            googleNames = choices.fonts,
            defaultLabel = "Automatic (Noto)",
            onSelect = { id -> scope.launch { repository.setScriptFontId(choices.script.name, id) } },
        )
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * One script's font list: the default row, curated Google faces, and — for the
 * scripts that support it (English/Bengali) — the single imported file and an
 * import button. Scripts that only offer curated faces pass [customFile] null;
 * their default row is relabelled via [defaultLabel] since it is the script's
 * automatic Noto face rather than the raw system font.
 *
 * [installedFonts] is the library filled by the Addons screen and by importing a
 * file here. It gets its own section above the curated list: it is a short,
 * personal list next to twenty stock faces, and burying it inside them makes a
 * font the user deliberately installed harder to find than one they didn't.
 *
 * [scripts] is what the picker is for. A font that declares which languages it
 * covers is only offered where it covers something — plenty of display faces are
 * Latin-only, and offering one for Bengali offers a keyboard of empty boxes. A
 * font that declares nothing makes no claim and is offered everywhere.
 */
@Composable
private fun FontPickerSection(
    header: String,
    sample: String,
    selectedId: String,
    googleNames: List<String>,
    onSelect: (String) -> Unit,
    defaultLabel: String = "System default",
    customId: String = KeyboardFonts.CUSTOM_ID,
    customFile: java.io.File? = null,
    customName: String = "",
    onImport: ((android.net.Uri) -> Unit)? = null,
    installedFonts: List<InstalledFont> = emptyList(),
    installedTitle: String = "Installed fonts",
    scripts: Set<ScriptId> = emptySet(),
    onDeleteInstalled: ((InstalledFont) -> Unit)? = null,
) {
    val context = LocalContext.current
    val importFont = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport?.invoke(uri) }
    val relevant = remember(installedFonts, scripts) {
        installedFonts.filter { font ->
            font.langIds.isEmpty() || scripts.isEmpty() ||
                font.langIds.any { LanguageRegistry.byId(it).script in scripts }
        }
    }
    if (relevant.isNotEmpty()) {
        SettingsGroup(installedTitle) {
            for (font in relevant) {
                item {
                    val id = FontStore.fontIdFor(font.id)
                    FontChoiceRow(
                        label = font.name,
                        family = remember(id) { KeyboardFonts.family(context, id) },
                        sample = sample,
                        selected = selectedId == id,
                        onDelete = onDeleteInstalled?.let { delete -> { delete(font) } },
                    ) { onSelect(id) }
                }
            }
        }
    }
    SettingsGroup(header) {
        item {
            FontChoiceRow(
                label = defaultLabel,
                family = null,
                sample = sample,
                selected = selectedId == KeyboardFonts.DEFAULT_ID,
            ) { onSelect(KeyboardFonts.DEFAULT_ID) }
        }
        for (name in googleNames) {
            item {
                val id = KeyboardFonts.googleId(name)
                FontChoiceRow(
                    label = name,
                    family = remember(id) { KeyboardFonts.family(context, id) },
                    sample = sample,
                    selected = selectedId == id,
                ) { onSelect(id) }
            }
        }
        if (customFile?.exists() == true) {
            item {
                FontChoiceRow(
                    label = customName.ifBlank { "Imported font" },
                    family = remember(customName) { KeyboardFonts.family(context, customId) },
                    sample = sample,
                    selected = selectedId == customId,
                ) { onSelect(customId) }
            }
        }
        if (onImport != null) {
            item {
                OutlinedButton(
                    onClick = { importFont.launch(FONT_MIME_TYPES) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Import font file (.ttf / .otf)") }
            }
        }
    }
}

/** One selectable font row, its label and sample line drawn in the font itself. */
@Composable
private fun FontChoiceRow(
    label: String,
    family: FontFamily?,
    sample: String,
    selected: Boolean,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, fontFamily = family, fontSize = 18.sp) },
        supportingContent = {
            Text(
                sample,
                fontFamily = family,
                maxLines = 1,
            )
        },
        trailingContent = if (selected || onDelete != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove $label")
                        }
                    }
                }
            }
        } else {
            null
        },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/**
 * Copies a picked font into private storage and returns its display name,
 * or null when the stream can't be read or the platform can't parse the
 * file (the bad copy is deleted so it never sticks as the "custom font").
 */
/**
 * A human-readable name for a picked font file: the provider's display name
 * with the extension stripped, since "Inter-Regular" reads better in the picker
 * than "Inter-Regular.ttf".
 */
private fun fontFileLabel(context: Context, uri: android.net.Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    return name?.substringBeforeLast('.')?.trim().orEmpty().ifBlank { "Imported font" }
}

private fun importFontFile(context: Context, uri: android.net.Uri, dest: java.io.File): String? {
    return runCatching {
        dest.parentFile?.mkdirs()
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (copied == null) return null
        val parsed = runCatching { android.graphics.Typeface.createFromFile(dest) }.getOrNull()
        if (parsed == null || parsed == android.graphics.Typeface.DEFAULT) {
            dest.delete()
            return null
        }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: dest.name
    }.getOrNull()
}

// ---- tools ----

/**
 * Whether a tool's settings page offers anything beyond the enable switch —
 * drives the "has more settings" marker on the tools list. Kept as the
 * caption-only exceptions so a new tool with options is marked by default.
 */
/**
 * Whether the tool's detail page offers anything beyond the enable switch —
 * gates the "has more settings" affordance in the tools list. Keep in sync
 * with [ToolDetailSettings]'s `when`: a tool whose page is just the toggle
 * (or a caption) doesn't earn the icon.
 */
private fun toolHasOptions(tool: ToolbarTool): Boolean =
    tool !in setOf(
        ToolbarTool.UNIT_CONVERT, ToolbarTool.SETTINGS,
        // Quick toggles/panels, not tools with settings of their own —
        // their options all live elsewhere (Appearance, Typing).
        ToolbarTool.THEMES, ToolbarTool.SOUND_HAPTICS, ToolbarTool.ONE_HANDED,
        // Just an enable toggle — the transport lives on the keyboard panel,
        // there is nothing to configure here.
        ToolbarTool.MEDIA_CONTROL,
    )

internal fun toolTitle(tool: ToolbarTool): String = when (tool) {
    ToolbarTool.EMOJI -> "Emoji"
    ToolbarTool.CLIPBOARD -> "Clipboard"
    ToolbarTool.SNIPPETS -> "Snippets"
    ToolbarTool.TEXT_EDIT -> "Text editing"
    ToolbarTool.ONE_HANDED -> "One-handed mode"
    ToolbarTool.SPLIT -> "Split keyboard"
    ToolbarTool.FLOATING -> "Floating keyboard"
    ToolbarTool.SETTINGS -> "Settings shortcut"
    ToolbarTool.FLASHLIGHT -> "Flashlight"
    ToolbarTool.COMPASS -> "Compass"
    ToolbarTool.LEVEL -> "Bubble level"
    ToolbarTool.UNDO -> "Undo"
    ToolbarTool.REDO -> "Redo"
    ToolbarTool.MOON_PHASE -> "Moon phase"
    ToolbarTool.WEATHER -> "Weather"
    ToolbarTool.CALENDAR -> "Calendar"
    ToolbarTool.INCOGNITO -> "Incognito"
    ToolbarTool.POWER_SAVING -> "Power saving"
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
    ToolbarTool.WEB_SEARCH -> "Web search"
    ToolbarTool.IMAGE_SEARCH -> "Image search"
    ToolbarTool.OCR -> "Text scan (OCR)"
    ToolbarTool.QR_SCAN -> "QR & barcode scanner"
    ToolbarTool.DOC_SCAN -> "Document scanner"
    ToolbarTool.VOICE -> "Voice typing"
    ToolbarTool.GRAMMAR -> "Grammar check"
    ToolbarTool.WIKIPEDIA -> "Wikipedia"
    ToolbarTool.SYMBOLS -> "Special symbols"
    ToolbarTool.CALCULATOR -> "Calculator"
    ToolbarTool.UNIT_CONVERT -> "Unit converter"
    ToolbarTool.CURRENCY -> "Currency converter"
    ToolbarTool.QR_GEN -> "QR code generator"
    ToolbarTool.PASSWORD_GEN -> "Password generator"
    ToolbarTool.TYPING_TEST -> "Typing speed test"
    ToolbarTool.MEDIA_CONTROL -> "Media controls"
    ToolbarTool.PLUGINS -> "Plugins"
    ToolbarTool.AI -> "AI writing tools"
    ToolbarTool.MODES -> "Keyboard modes"
    ToolbarTool.CURSOR_LEFT -> "Cursor left"
    ToolbarTool.CURSOR_RIGHT -> "Cursor right"
    ToolbarTool.CURSOR_WORD_LEFT -> "Word left"
    ToolbarTool.CURSOR_WORD_RIGHT -> "Word right"
    ToolbarTool.CURSOR_UP -> "Cursor up"
    ToolbarTool.CURSOR_DOWN -> "Cursor down"
    ToolbarTool.CURSOR_HOME -> "Line start"
    ToolbarTool.CURSOR_END -> "Line end"
    ToolbarTool.PAGE_UP -> "Page up"
    ToolbarTool.PAGE_DOWN -> "Page down"
    ToolbarTool.SELECT_WORD -> "Select word"
    ToolbarTool.SELECT_LINE -> "Select line"
    ToolbarTool.HIDE_KEYBOARD -> "Hide keyboard"
}

internal fun toolDescription(tool: ToolbarTool): String = when (tool) {
    ToolbarTool.EMOJI -> "Emoji panel with search and skin tones"
    ToolbarTool.CLIPBOARD -> "Paste from clipboard history"
    ToolbarTool.SNIPPETS -> "Insert saved text snippets"
    ToolbarTool.TEXT_EDIT -> "Cursor, selection and clipboard controls"
    ToolbarTool.ONE_HANDED -> "Shrink the keyboard toward one edge"
    ToolbarTool.SPLIT -> "Split the keys into two halves"
    ToolbarTool.FLOATING -> "Detach the keyboard into a movable panel"
    ToolbarTool.SETTINGS -> "Open this settings app"
    ToolbarTool.FLASHLIGHT -> "Toggle the torch from the keyboard"
    ToolbarTool.COMPASS -> "Live compass with degree readout and optional qibla"
    ToolbarTool.LEVEL -> "Bubble level using the accelerometer"
    ToolbarTool.UNDO -> "One tap sends the editor's undo shortcut (Ctrl+Z)"
    ToolbarTool.REDO -> "One tap sends the editor's redo shortcut"
    ToolbarTool.MOON_PHASE -> "Current phase, illumination, next full/new moon"
    ToolbarTool.WEATHER -> "Current conditions for a saved location"
    ToolbarTool.CALENDAR -> "Month view with your events and two calendars of your choice"
    ToolbarTool.INCOGNITO -> "One tap pauses learning and clipboard capture"
    ToolbarTool.POWER_SAVING -> "Drop features that cost battery, on demand or when it runs low"
    ToolbarTool.THEMES -> "Quick theme switcher on the keyboard"
    ToolbarTool.AUTOCORRECT -> "One tap turns autocorrect on or off"
    ToolbarTool.SOUND_HAPTICS -> "Adjust key sound and vibration from the keyboard"
    ToolbarTool.NUMPAD -> "Dedicated number pad layout"
    ToolbarTool.HANDWRITING -> "Write words by hand — finger or S Pen — with on-device recognition"
    ToolbarTool.CAMERA -> "Take a photo and send it without leaving the keyboard"
    ToolbarTool.DICTIONARY -> "English definitions, pronunciation and synonyms"
    ToolbarTool.TRANSLATE -> "Translate what you type, live, into any language"
    ToolbarTool.GIF -> "Search GIFs (Klipy, GIPHY) and send them without leaving the keyboard"
    ToolbarTool.STICKER -> "Search stickers — transparent, chat-ready"
    ToolbarTool.WEB_SEARCH -> "Search the web (Brave) and insert a result's link"
    ToolbarTool.IMAGE_SEARCH -> "Image search from the keyboard; tap to send an image"
    ToolbarTool.OCR -> "Point the camera at printed text and type it — pick just the words you need"
    ToolbarTool.QR_SCAN -> "Scan a QR code or barcode and insert its text"
    ToolbarTool.DOC_SCAN -> "Scan a document with Google's scanner and send it as an image"
    ToolbarTool.VOICE -> "Dictate text with the device's speech recognizer — any language it supports"
    ToolbarTool.GRAMMAR -> "Check the text you're writing for grammar issues — fully offline (Harper)"
    ToolbarTool.WIKIPEDIA -> "Search Wikipedia, read summaries and insert text or links"
    ToolbarTool.SYMBOLS -> "Fractions, math, Greek, arrows and more — one tap to type"
    ToolbarTool.CALCULATOR -> "Scientific calculator; insert the result at the cursor"
    ToolbarTool.UNIT_CONVERT -> "Convert length, mass, temperature, data and 10+ more categories"
    ToolbarTool.CURRENCY -> "Live exchange rates from free APIs — no key needed"
    ToolbarTool.QR_GEN -> "Turn the text in the field into a QR code and send it as an image"
    ToolbarTool.PASSWORD_GEN -> "Strong passwords and passphrases, generated on-device"
    ToolbarTool.TYPING_TEST -> "Time your typing on this keyboard and track your best scores"
    ToolbarTool.MEDIA_CONTROL -> "Play, pause and skip whatever's playing — with album art and a seek bar"
    ToolbarTool.PLUGINS -> "Run installed plugins — small sandboxed tools you add yourself"
    ToolbarTool.AI -> "Rewrite, summarize, translate and more — your own API key or local server"
    ToolbarTool.MODES -> "Switch between per-app setups: emoji row, pinned tools, symbol sets"
    ToolbarTool.CURSOR_LEFT -> "Move the cursor one character left"
    ToolbarTool.CURSOR_RIGHT -> "Move the cursor one character right"
    ToolbarTool.CURSOR_WORD_LEFT -> "Move the cursor one word left"
    ToolbarTool.CURSOR_WORD_RIGHT -> "Move the cursor one word right"
    ToolbarTool.CURSOR_UP -> "Move the cursor one line up"
    ToolbarTool.CURSOR_DOWN -> "Move the cursor one line down"
    ToolbarTool.CURSOR_HOME -> "Jump to the start of the line"
    ToolbarTool.CURSOR_END -> "Jump to the end of the line"
    ToolbarTool.PAGE_UP -> "Scroll the cursor up a page"
    ToolbarTool.PAGE_DOWN -> "Scroll the cursor down a page"
    ToolbarTool.SELECT_WORD -> "Select the word at the cursor"
    ToolbarTool.SELECT_LINE -> "Select the entire line at the cursor"
    ToolbarTool.HIDE_KEYBOARD -> "Dismiss the keyboard in one tap"
}

internal fun toolIconFor(tool: ToolbarTool): androidx.compose.ui.graphics.vector.ImageVector =
    IconDefaults.forTool(tool)

/**
 * The tool menu, grouped by what the tools do. Everything else — the
 * enable switch and the tool's own options — lives one level down.
 */
@Composable
private fun ToolsSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenTool: (ToolbarTool) -> Unit,
) {
    val scope = rememberCoroutineScope()
    CaptionText(
        "Tools live on the keyboard's toolbar and in the toolbox (grid button on " +
            "the toolbar). The switch enables a tool; tap a row for its settings — " +
            "the ⚙ marks tools with options of their own.",
    )
    ToggleSetting(
        title = "Colorful tool icons",
        subtitle = "Tint each tool its own accent colour here and in the toolbox. " +
            "Open a tool to recolour just that icon.",
        checked = settings.coloredToolIcons,
        onChange = { scope.launch { repository.setColoredToolIcons(it) } },
    )
    if (settings.coloredToolIcons && settings.toolColorOverrides.isNotEmpty()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { scope.launch { repository.clearToolColors() } }) {
                Text("Reset custom colours")
            }
        }
    }
    val groups = listOf(
        "Panels" to listOf(
            ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS,
            ToolbarTool.TEXT_EDIT, ToolbarTool.NUMPAD, ToolbarTool.HANDWRITING,
            ToolbarTool.VOICE, ToolbarTool.CAMERA, ToolbarTool.DICTIONARY,
            ToolbarTool.GRAMMAR,
        ),
        "Scanners" to listOf(
            ToolbarTool.OCR, ToolbarTool.QR_SCAN, ToolbarTool.DOC_SCAN,
        ),
        "Online tools" to listOf(
            ToolbarTool.TRANSLATE, ToolbarTool.GIF, ToolbarTool.STICKER,
            ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH,
            ToolbarTool.WIKIPEDIA, ToolbarTool.CURRENCY, ToolbarTool.AI,
        ),
        "Create & convert" to listOf(
            ToolbarTool.SYMBOLS, ToolbarTool.CALCULATOR, ToolbarTool.UNIT_CONVERT,
            ToolbarTool.QR_GEN, ToolbarTool.PASSWORD_GEN, ToolbarTool.TYPING_TEST,
        ),
        "Keyboard modes" to listOf(
            ToolbarTool.MODES, ToolbarTool.ONE_HANDED, ToolbarTool.SPLIT, ToolbarTool.FLOATING,
        ),
        "Cursor" to (CursorTools + ToolbarTool.HIDE_KEYBOARD),
        "Quick actions" to listOf(
            ToolbarTool.UNDO, ToolbarTool.REDO, ToolbarTool.AUTOCORRECT,
            ToolbarTool.INCOGNITO, ToolbarTool.SOUND_HAPTICS, ToolbarTool.THEMES,
            ToolbarTool.POWER_SAVING, ToolbarTool.SETTINGS,
        ),
        "Utilities" to listOf(
            ToolbarTool.FLASHLIGHT, ToolbarTool.COMPASS, ToolbarTool.LEVEL,
            ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.MOON_PHASE,
        ),
    )
    // Safety net: a tool added to the enum but forgotten here still gets a
    // settings entry (this menu is the only path to a tool's options).
    val grouped = groups.flatMap { it.second }.toSet()
    val ungrouped = ToolbarTool.entries.filterNot { it in grouped }
    val allGroups = (if (ungrouped.isEmpty()) groups else groups + ("Other" to ungrouped))
        // Tools this build can't provide (lite flavor) get no settings entry.
        .map { (title, tools) -> title to tools.filter(::isSupportedTool) }
        .filter { it.second.isNotEmpty() }
    for ((groupTitle, tools) in allGroups) {
        SettingsGroup(groupTitle) {
            for (tool in tools) {
                item {
                    ListItem(
                        leadingContent = {
                            SlotIcon(
                                IconSlots.forTool(tool),
                                contentDescription = null,
                                tint = if (settings.coloredToolIcons)
                                    toolAccentColor(tool, settings.toolColorOverrides)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        headlineContent = { Text(toolTitle(tool)) },
                        supportingContent = { Text(toolDescription(tool)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (toolHasOptions(tool)) {
                                    Icon(
                                        Icons.Outlined.Tune,
                                        contentDescription = "Has more settings",
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = tool in settings.enabledTools,
                                    onCheckedChange = { enabled ->
                                        scope.launch { repository.setToolEnabled(tool, enabled) }
                                    },
                                )
                            }
                        },
                        colors = transparentListColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTool(tool) },
                    )
                }
            }
        }
    }
}

/** One tool's screen: the enable switch plus every setting the tool has. */
@Composable
private fun ToolDetailSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    tool: ToolbarTool,
    onNavigate: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    CaptionText(toolDescription(tool))
    SettingsGroup {
        item {
            ToggleSetting(
                "Enabled",
                "Show this tool on the toolbar and in the toolbox",
                tool in settings.enabledTools,
            ) { scope.launch { repository.setToolEnabled(tool, it) } }
        }
        // Recolour just this tool's icon. Only meaningful while the global
        // "Colorful tool icons" switch is on, since it's what paints them.
        if (settings.coloredToolIcons) {
            item {
                var showPicker by remember { mutableStateOf(false) }
                val override = settings.toolColorOverrides[tool]
                val resolved = override ?: toolAccentColorArgb(tool)
                ListItem(
                    leadingContent = { Swatch(resolved) },
                    headlineContent = { Text("Icon colour") },
                    supportingContent = {
                        Text(if (override != null) "Custom — tap to change" else "Default — tap to customise")
                    },
                    colors = transparentListColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPicker = true },
                )
                if (showPicker) {
                    ColorPickerDialog(
                        title = "${toolTitle(tool)} icon colour",
                        initial = resolved,
                        supportsAlpha = false,
                        showReset = override != null,
                        onPick = {
                            scope.launch { repository.setToolColor(tool, it) }
                            showPicker = false
                        },
                        onReset = {
                            scope.launch { repository.setToolColor(tool, null) }
                            showPicker = false
                        },
                        onDismiss = { showPicker = false },
                    )
                }
            }
        }
    }
    ToolKeywordSetting(repository, settings, tool)
    when (tool) {
        ToolbarTool.PLUGINS -> SettingsGroup("Plugins") {
            item {
                ListItem(
                    headlineContent = { Text("Manage plugins") },
                    supportingContent = {
                        Text("Turn plugins on, see what's installed, and what each one can do")
                    },
                    colors = transparentListColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("plugins") },
                )
            }
        }
        ToolbarTool.EMOJI -> SettingsGroup("Emoji") {
            item {
                ToggleSetting(
                    "Emoji button in toolbar",
                    "Keep the emoji button visible next to suggestions",
                    settings.emojiToolbar,
                ) { scope.launch { repository.setEmojiToolbar(it) } }
            }
            item {
                NavRow(
                    "All emoji settings",
                    "Suggestions, history tab, emoji row, skin tones & favourites",
                    onClick = { onNavigate("emoji") },
                )
            }
        }
        ToolbarTool.SNIPPETS -> SnippetSettings()
        ToolbarTool.CLIPBOARD -> {
            // Both grants happen on a system screen, so they are read through
            // rememberGrantState: the rows below disappear as soon as we come back
            // with the permission in hand, instead of on the next unrelated redraw.
            val screenshotsGranted = rememberGrantState(::hasImagesPermission)
            val usageAccessGranted = rememberGrantState(::hasUsageAccess)
            SettingsGroup("History") {
                item {
                    ToggleSetting(
                        "Clipboard history", "Save copied text for quick paste",
                        settings.clipboard.history,
                    ) { scope.launch { repository.setClipboardHistory(it) } }
                }
                item {
                    ToggleSetting(
                        "Suggest recent copy",
                        "Show the last copied text as a chip on the suggestion strip, " +
                            "one tap from pasting it.",
                        settings.clipboard.suggestRecent,
                    ) { scope.launch { repository.setClipboardSuggestRecent(it) } }
                }
                item {
                    ToggleSetting(
                        "Toast on copy",
                        "Show a brief \"Copied\" pop-up when you copy or cut text from the keyboard.",
                        settings.feedback.toastOnCopy,
                        info = "Fires for the keyboard's own copy actions — the A/C/V/X clipboard " +
                            "shortcuts and the text-editing panel's copy button — for fields that " +
                            "give no copy feedback of their own. Off by default.",
                    ) { scope.launch { repository.setToastOnCopy(it) } }
                }
                item {
                    SliderSetting(
                        "Clipboard expiry",
                        subtitle = "Remove unpinned items after this long",
                        value = settings.clipboard.expiryHours.toFloat(),
                        range = 0f..168f,
                        display = { if (it.toInt() == 0) "never" else "${it.toInt()} h" },
                    ) { scope.launch { repository.setClipboardExpiryHours(it.toInt()) } }
                }
                item {
                    SliderSetting(
                        "Maximum entries",
                        subtitle = "How many unpinned clips history keeps",
                        value = settings.clipboard.maxItems.toFloat(),
                        range = 5f..500f,
                        display = { "${it.toInt()}" },
                        info = "The other half of the bound clipboard expiry sets — a busy " +
                            "day of copying can pile up hundreds of clips well inside the " +
                            "expiry window. Once the panel is full, the oldest unpinned clip " +
                            "drops off with each new copy. Pinned entries never count against " +
                            "this and never fall off.",
                    ) { scope.launch { repository.setClipboardMaxItems(it.toInt()) } }
                }
                item {
                    ToggleSetting(
                        "Bottom control row",
                        "Show an abc, space and backspace row at the bottom of the " +
                            "clipboard panel, like the emoji panel.",
                        settings.clipboard.bottomRow,
                    ) { scope.launch { repository.setClipboardBottomRow(it) } }
                }
                item {
                    ToggleSetting(
                        "Pinned entries last",
                        "List pinned clips at the end of the panel instead of the top.",
                        settings.clipboard.pinnedLast,
                    ) { scope.launch { repository.setClipboardPinnedLast(it) } }
                }
                item {
                    ToggleSetting(
                        "Search bar",
                        "Show a search bar at the top of the clipboard panel to filter " +
                            "history as you type.",
                        settings.clipboard.search,
                    ) { scope.launch { repository.setClipboardSearch(it) } }
                }
                item {
                    ToggleSetting(
                        "Detect codes, numbers & links",
                        "Pull one-time codes, phone numbers and links out of your clips " +
                            "and offer each one as its own chip.",
                        settings.clipboard.detectEntities,
                        info = "The chips sit above the history in dashed outlines, so they " +
                            "read as parts of a clip rather than clips of their own — " +
                            "press and hold one to see it highlighted inside the entry it " +
                            "came from. Tapping pastes only that fragment; the clip itself " +
                            "stays put. Detection runs entirely on the device.",
                    ) { scope.launch { repository.setClipboardDetectEntities(it) } }
                }
                item {
                    ToggleSetting(
                        "Forget after pasting a password",
                        "Delete a clip from history and from the system clipboard as " +
                            "soon as it is pasted into a password field.",
                        settings.clipboard.clearAfterPasswordPaste,
                        info = "Every app on the device can read the system clipboard, so a " +
                            "password pasted out of a manager would otherwise sit there " +
                            "readable until it expired. Applies to pastes made with the " +
                            "keyboard — the clipboard panel, the paste chip, hold-V and " +
                            "Ctrl+V — into a password field. On by default.",
                    ) { scope.launch { repository.setClipboardClearAfterPasswordPaste(it) } }
                }
                item {
                    ToggleSetting(
                        "Link previews",
                        "Fetch the page title of copied links and show it in the panel. " +
                            "This contacts the linked site.",
                        settings.clipboard.linkPreviews,
                    ) { scope.launch { repository.setClipboardLinkPreviews(it) } }
                }
                item {
                    val context = LocalContext.current
                    ToggleSetting(
                        "User screenshots",
                        "Show user screenshots in the clipboard alongside copied text and images.",
                        settings.clipboard.userScreenshots,
                    ) { on ->
                        scope.launch { repository.setClipboardUserScreenshots(on) }
                        if (on && !hasImagesPermission(context)) {
                            runCatching {
                                context.startActivity(Intent(context, ImagesPermissionActivity::class.java))
                            }
                        }
                    }
                }
                // The guard sits outside item {} on purpose: an item whose body
                // draws nothing still gets its own card, which showed up as a
                // sliver of empty surface once the permission was granted.
                if (settings.clipboard.userScreenshots && !screenshotsGranted) {
                    item {
                        val context = LocalContext.current
                        NavRow(
                            "Storage permission required",
                            "Open system settings to grant it",
                        ) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
                item {
                    val context = LocalContext.current
                    ToggleSetting(
                        "Show source app",
                        "Record which app a clip was copied from, shown when you press " +
                            "and hold an entry. Needs Usage Access permission.",
                        settings.clipboard.trackSource,
                        info = "Best-effort: it reads the foreground app at copy time via " +
                            "Usage Access. Some copies (e.g. from background sync) may have no " +
                            "source. Nothing about your app usage leaves the device.",
                    ) { on ->
                        scope.launch { repository.setClipboardTrackSource(on) }
                        // Sending the user to grant the permission the first time they
                        // switch it on — but not when it is already granted, which is
                        // the common case for a toggle flipped off and on again.
                        if (on && !hasUsageAccess(context)) runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                if (settings.clipboard.trackSource && !usageAccessGranted) {
                    item {
                        val context = LocalContext.current
                        NavRow(
                            "Usage Access permission required",
                            "Without it, clips are saved with no source app",
                        ) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
            }
            SettingsGroup("Passwords & codes") {
                item {
                    ChoiceSetting(
                        title = "Sensitive clips",
                        subtitle = "What to do with a copied password or one-time code",
                        info = "Android lets an app mark what it puts on the clipboard as " +
                            "sensitive, which is what a password manager does when you copy " +
                            "a login. Nothing else on the phone acts on that mark, so this " +
                            "is where it is honoured. Hidden clips show as dots in the " +
                            "panel, are never offered as a paste chip, are left out of " +
                            "settings backups, and delete themselves on the short timer " +
                            "below instead of the history expiry. Pinning one overrides all " +
                            "of that — pinning is an explicit \"keep this\".",
                        options = SensitiveClipHandling.entries.map { it to it.label },
                        selected = settings.clipboard.sensitiveHandling,
                    ) { scope.launch { repository.setClipboardSensitiveHandling(it) } }
                }
                if (settings.clipboard.sensitiveHandling != SensitiveClipHandling.KEEP) {
                    item {
                        ToggleSetting(
                            "Recognise them yourself",
                            "Also treat clips that look like a password or a bare " +
                                "verification code as sensitive, not only the ones the " +
                                "copying app marks.",
                            settings.clipboard.detectSensitive,
                            info = "Most password managers still predate Android's sensitive " +
                                "flag, and a code copied by hand out of a message carries no " +
                                "flag at all. Detection runs entirely on the device and is " +
                                "deliberately narrow: a clip qualifies only when the whole " +
                                "of it is one token — a short run of capitals and digits, or " +
                                "a long mix of cases, digits and symbols. Sentences, links " +
                                "and email addresses never match.",
                        ) { scope.launch { repository.setClipboardDetectSensitive(it) } }
                    }
                }
                if (settings.clipboard.sensitiveHandling == SensitiveClipHandling.SHORT_LIVED) {
                    item {
                        SliderSetting(
                            "Forget sensitive clips after",
                            subtitle = "Independent of the history expiry above",
                            value = settings.clipboard.sensitiveExpiryMinutes.toFloat(),
                            range = 1f..120f,
                            display = { "${it.toInt()} min" },
                        ) {
                            scope.launch { repository.setClipboardSensitiveExpiryMinutes(it.toInt()) }
                        }
                    }
                }
            }
        }
        ToolbarTool.SPLIT -> SettingsGroup("Options") {
            item {
                SliderSetting(
                    "Split gap",
                    subtitle = "Width of the gap between the halves",
                    value = settings.splitGapPercent.toFloat(),
                    range = 5f..40f,
                    display = { "${it.toInt()}%" },
                ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
            }
            item {
                NavRow(
                    "All layout & size settings",
                    "Keyboard height, width, alignment and split",
                    onClick = { onNavigate("layout") },
                )
            }
        }
        ToolbarTool.FLOATING -> SettingsGroup("Options") {
            item {
                SliderSetting(
                    "Floating keyboard width",
                    subtitle = "Also adjustable by dragging the panel's corner grip",
                    value = settings.floatingWidthDp.toFloat(),
                    range = 240f..500f,
                    display = { "${it.toInt()} dp" },
                ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
            }
            item {
                NavRow(
                    "All layout & size settings",
                    "Keyboard height, width, alignment and floating mode",
                    onClick = { onNavigate("layout") },
                )
            }
        }
        ToolbarTool.FLASHLIGHT -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Auto-off with keyboard",
                    "Turn the torch off when the keyboard is dismissed",
                    settings.flashlightAutoOff,
                    info = "On: closing the keyboard (or switching apps) switches the " +
                        "torch off with it, so it is never left burning in your " +
                        "pocket. Off: the torch stays on until toggled again — from " +
                        "the tool or from the system quick-settings tile.",
                ) { scope.launch { repository.setFlashlightAutoOff(it) } }
            }
        }
        ToolbarTool.COMPASS -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Degree readout",
                        "Show the numeric heading under the compass rose",
                        settings.compassShowDegrees,
                    ) { scope.launch { repository.setCompassShowDegrees(it) } }
                }
                item {
                    ToggleSetting(
                        "Show qibla",
                        "Mark the direction of the Kaaba on the compass",
                        settings.compassShowQibla,
                        info = "The qibla bearing is computed from the location saved in the " +
                            "weather tool's settings (the two tools share it). Everything is " +
                            "calculated on-device; the compass never touches the network.",
                    ) { scope.launch { repository.setCompassShowQibla(it) } }
                }
            }
            if (settings.compassShowQibla && settings.weatherLatitude == null) {
                CaptionText(
                    "No location saved yet — set one under Tools → Weather.",
                    error = true,
                )
            }
        }
        ToolbarTool.LEVEL -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Angle readout",
                    "Show pitch and roll in degrees under the bubble",
                    settings.levelShowAngles,
                ) { scope.launch { repository.setLevelShowAngles(it) } }
            }
        }
        ToolbarTool.UNDO, ToolbarTool.REDO -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Redo sends Ctrl+Y",
                    "Instead of the default Ctrl+Shift+Z",
                    settings.redoUsesCtrlY,
                    info = "Both are standard redo shortcuts; which one works depends " +
                        "on the app you are typing in. If redo does nothing, try " +
                        "the other one.",
                ) { scope.launch { repository.setRedoUsesCtrlY(it) } }
            }
        }
        ToolbarTool.MOON_PHASE -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Southern hemisphere",
                    "Mirror the moon the way it appears south of the equator",
                    settings.moonSouthernHemisphere,
                ) { scope.launch { repository.setMoonSouthernHemisphere(it) } }
            }
        }
        ToolbarTool.WEATHER -> {
            SettingsGroup("Options") {
                item { WeatherLocationSetting(repository, settings) }
                item {
                    ToggleSetting(
                        "Fahrenheit", "Show temperatures in °F instead of °C",
                        settings.weatherFahrenheit,
                    ) { scope.launch { repository.setWeatherFahrenheit(it) } }
                }
            }
            CaptionText(
                "Weather and place search both use Open-Meteo, only when you use " +
                    "them — the keyboard makes no other network requests.",
            )
        }
        ToolbarTool.CALENDAR -> {
            val showsHijri = settings.calendarAltOne == AltCalendar.HIJRI ||
                settings.calendarAltTwo == AltCalendar.HIJRI
            SettingsGroup("Alongside the Gregorian calendar") {
                item {
                    AltCalendarSetting(
                        title = "First calendar",
                        subtitle = "Its day number also rides inside each day cell",
                        selected = settings.calendarAltOne,
                        onChange = { scope.launch { repository.setCalendarAltOne(it) } },
                    )
                }
                item {
                    AltCalendarSetting(
                        title = "Second calendar",
                        subtitle = "Shown in the header and under the selected day",
                        selected = settings.calendarAltTwo,
                        onChange = { scope.launch { repository.setCalendarAltTwo(it) } },
                    )
                }
                if (showsHijri) {
                    item {
                        SliderSetting(
                            "Hijri day adjustment",
                            subtitle = "Shift the computed Hijri date to match local moon sighting",
                            value = settings.hijriAdjustDays.toFloat(),
                            range = -2f..2f,
                            display = { days ->
                                val d = days.roundToInt()
                                if (d > 0) "+$d d" else "$d d"
                            },
                            info = "The tool uses the arithmetic (tabular) Hijri calendar. " +
                                "Real Islamic months begin at the sighting of the crescent, " +
                                "which can differ from the tables by a day or two either " +
                                "way — set the offset that matches your local authority.",
                        ) { scope.launch { repository.setHijriAdjustDays(it.roundToInt()) } }
                    }
                }
            }
            CaptionText(
                "Pick any two calendars to show next to the Gregorian one. Chinese dates " +
                    "are computed astronomically; Hebrew, Persian, Hindu (Saka), Buddhist " +
                    "and Japanese are exact arithmetic; Hijri is the tabular calendar, so " +
                    "it has the day offset above.",
            )
            CaptionText(
                "Tapping a day shows its events from your device calendar. The keyboard " +
                    "asks for calendar access the first time you open the tool; it only " +
                    "reads events, never changes them.",
            )
        }
        ToolbarTool.CAMERA -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Start with the selfie camera",
                        "Open the tool on the front camera instead of the back one",
                        settings.camera.preferFront,
                    ) { scope.launch { repository.setCameraPreferFront(it) } }
                }
                item {
                    ToggleSetting(
                        "Mirror selfies",
                        "Save front-camera photos the way the preview shows them",
                        settings.camera.mirrorFront,
                        info = "Camera sensors record selfies un-mirrored (text reads " +
                            "correctly, but the photo looks flipped compared to the " +
                            "preview). On: the saved photo matches what you saw while " +
                            "framing. Off: keep the sensor's true orientation.",
                    ) { scope.launch { repository.setCameraMirrorFront(it) } }
                }
                item {
                    ToggleSetting(
                        "Save to gallery",
                        "Also keep captures in Pictures/WM Keyboard",
                        settings.camera.saveToGallery,
                        info = "Off by default: photos taken here are normally " +
                            "one-shot sends, not keepsakes. On: every capture is " +
                            "copied into the gallery as well as sent.",
                    ) { scope.launch { repository.setCameraSaveToGallery(it) } }
                }
            }
            SettingsGroup("Feedback") {
                item {
                    ToggleSetting(
                        "Shutter sound",
                        "Play the camera click when a photo is taken",
                        settings.camera.shutterSound,
                    ) { scope.launch { repository.setCameraShutterSound(it) } }
                }
                item {
                    ToggleSetting(
                        "Haptics",
                        "Vibrate on the shutter, controls and timer countdown",
                        settings.camera.haptics,
                        info = "Uses the keyboard's haptic style and strength " +
                            "(Typing → Feedback). If keyboard haptics are off " +
                            "entirely, the camera tool stays silent too.",
                    ) { scope.launch { repository.setCameraHaptics(it) } }
                }
            }
            CaptionText(
                "Photos are cropped to what the viewfinder shows, saved in the " +
                    "app's private storage and sent straight into the chat. " +
                    "Nothing is added to your gallery, and the camera runs only " +
                    "while the tool is open.",
            )
        }
        ToolbarTool.DICTIONARY -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Look up the word at the cursor",
                        "Opening the tool searches the selected or current word",
                        settings.dictionaryAutoLookup,
                    ) { scope.launch { repository.setDictionaryAutoLookup(it) } }
                }
            }
            CaptionText(
                "Definitions come from the Free Dictionary API " +
                    "(dictionaryapi.dev). The word you look up is sent to that " +
                    "service — only when you use the tool.",
            )
        }
        ToolbarTool.TEXT_EDIT -> SettingsGroup("Options") {
            item {
                SliderSetting(
                    "Key repeat interval",
                    subtitle = "Pause between repeats while holding an arrow or " +
                        "backspace — lower is faster",
                    value = settings.textEditing.repeatMs.toFloat(),
                    range = 30f..200f,
                    display = { "${it.toInt()} ms" },
                ) { scope.launch { repository.setTextEditRepeatMs(it.toInt()) } }
            }
        }
        ToolbarTool.NUMPAD -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Phone-style layout",
                    "1 2 3 on the top row, like a dialer. Off puts 7 8 9 on " +
                        "top, like a calculator.",
                    settings.numpadPhoneLayout,
                ) { scope.launch { repository.setNumpadPhoneLayout(it) } }
            }
        }
        ToolbarTool.INCOGNITO -> {
            SettingsGroup("While incognito") {
                item {
                    ToggleSetting(
                        "Pause learning",
                        "No words or emoji habits are learned from typing",
                        settings.incognitoPausesLearning,
                    ) { scope.launch { repository.setIncognitoPausesLearning(it) } }
                }
                item {
                    ToggleSetting(
                        "Pause clipboard capture",
                        "Copies don't join the clipboard tool's history",
                        settings.incognitoPausesClipboard,
                    ) { scope.launch { repository.setIncognitoPausesClipboard(it) } }
                }
            }
            SettingsGroup("Automatic") {
                item {
                    ToggleSetting(
                        "Follow private browsing",
                        "Switch on by itself in incognito tabs and private fields",
                        settings.autoIncognito,
                        info = AUTO_INCOGNITO_INFO,
                    ) { scope.launch { repository.setAutoIncognito(it) } }
                }
            }
            CaptionText(
                "Tapping the tool turns incognito on; tapping again resumes " +
                    "normal typing. Same switch as Settings → Privacy.",
            )
        }
        ToolbarTool.POWER_SAVING -> {
            val ps = settings.powerSaving
            SettingsGroup("Power saving") {
                item {
                    ToggleSetting(
                        "Power saving now",
                        "The same switch the toolbar tool flips",
                        ps.manual,
                        info = "Drops the features below until you turn it back off. It stays " +
                            "on across restarts and whatever the battery is doing — a full " +
                            "battery does not switch it off again.\n\n" +
                            "Nothing is saved over: the features come back exactly as you had " +
                            "them, because power saving never rewrites your settings, it only " +
                            "hides them while it is on.",
                    ) { scope.launch { repository.setPowerSavingManual(it) } }
                }
                item {
                    ChoiceSetting(
                        "Switch on by itself",
                        subtitle = "As well as the switch above",
                        info = "\"Android's battery saver\" follows the system switch, so the " +
                            "keyboard economizes exactly when you have already asked the phone " +
                            "to. \"Battery is low\" uses your own percentage below instead.",
                        options = PowerSavingTrigger.entries.map { it to it.label },
                        selected = ps.trigger,
                    ) { scope.launch { repository.setPowerSavingTrigger(it) } }
                }
                if (ps.trigger == PowerSavingTrigger.LOW_BATTERY ||
                    ps.trigger == PowerSavingTrigger.EITHER
                ) {
                    item {
                        SliderSetting(
                            "Low battery is",
                            subtitle = "Power saving starts at or below this level",
                            value = ps.batteryPercent.toFloat(),
                            range = 5f..50f,
                            display = { "${it.toInt()}%" },
                        ) { scope.launch { repository.setPowerSavingBatteryPercent(it.toInt()) } }
                    }
                }
                if (ps.trigger != PowerSavingTrigger.OFF) {
                    item {
                        ToggleSetting(
                            "Off while charging",
                            "Ignore the automatic triggers with the charger in",
                            ps.offWhileCharging,
                            info = "There is nothing to save while the battery is filling, so " +
                                "the automatic triggers stand down. The switch above is " +
                                "unaffected — turning power saving on by hand means it.",
                        ) { scope.launch { repository.setPowerSavingOffWhileCharging(it) } }
                    }
                }
            }
            SettingsGroup("What to drop") {
                item {
                    ToggleSetting(
                        "Key vibration",
                        "Silence the vibration motor",
                        ps.dropHaptics,
                    ) { scope.launch { repository.setPowerSavingDropHaptics(it) } }
                }
                item {
                    ToggleSetting(
                        "Key sounds",
                        "Stop playing the key click",
                        ps.dropKeySound,
                    ) { scope.launch { repository.setPowerSavingDropKeySound(it) } }
                }
                item {
                    ToggleSetting(
                        "Animations",
                        "Cut transitions and motion, as reduced motion does",
                        ps.dropAnimations,
                    ) { scope.launch { repository.setPowerSavingDropAnimations(it) } }
                }
                item {
                    ToggleSetting(
                        "Glide trail",
                        "Stop drawing the trail behind a swiped word",
                        ps.dropGlideTrail,
                        info = "Only the trail — swiping to type still works. The trail is " +
                            "redrawn every frame of a gesture, so it costs more than the " +
                            "decode it decorates.",
                    ) { scope.launch { repository.setPowerSavingDropGlideTrail(it) } }
                }
                item {
                    ToggleSetting(
                        "Key popup",
                        "Stop showing the character bubble over each key",
                        ps.dropKeyPopup,
                    ) { scope.launch { repository.setPowerSavingDropKeyPopup(it) } }
                }
                item {
                    ToggleSetting(
                        "Gesture typing",
                        "Turn swipe-to-type off entirely",
                        ps.dropGestureTyping,
                        info = "Decoding a swipe is the most expensive thing the keyboard " +
                            "does, but it is also why many people use it — so this is off by " +
                            "default and only the trail is dropped.",
                    ) { scope.launch { repository.setPowerSavingDropGestureTyping(it) } }
                }
                item {
                    ToggleSetting(
                        "Emoji suggestions",
                        "Stop scanning what you type for emoji to offer",
                        ps.dropEmojiPrediction,
                    ) { scope.launch { repository.setPowerSavingDropEmojiPrediction(it) } }
                }
                item {
                    ToggleSetting(
                        "Smart chips",
                        "Stop matching sums, conversions and tool keywords as you type",
                        ps.dropSmartChips,
                    ) { scope.launch { repository.setPowerSavingDropSmartChips(it) } }
                }
                item {
                    ToggleSetting(
                        "Background network",
                        "No link previews or automatic look-ups",
                        ps.dropBackgroundNetwork,
                        info = "Stops the fetches that happen without being asked: previews " +
                            "for copied links and scanned QR codes, and the dictionary's " +
                            "look-up when you select a word. Tools you open yourself — " +
                            "translate, search, GIFs — still work.",
                    ) { scope.launch { repository.setPowerSavingDropBackgroundNetwork(it) } }
                }
                item {
                    ToggleSetting(
                        "Screenshot watching",
                        "Stop watching for new screenshots to offer in the clipboard",
                        ps.dropScreenshotWatch,
                    ) { scope.launch { repository.setPowerSavingDropScreenshotWatch(it) } }
                }
                item {
                    ToggleSetting(
                        "On-device models",
                        "Dictate through the system recognizer, and swipe to type not to write",
                        ps.dropOnDeviceModels,
                        info = "Offline dictation and handwriting both run a neural model on " +
                            "the CPU, which is as expensive as the keyboard gets. Dictation " +
                            "falls back to the system recognizer and the letter swipe goes " +
                            "back to gliding words.",
                    ) { scope.launch { repository.setPowerSavingDropOnDeviceModels(it) } }
                }
            }
            CaptionText(
                "Power saving is a view of your settings, never a rewrite of them: " +
                    "everything switched off here comes back exactly as you had it the " +
                    "moment power saving ends.",
            )
        }
        ToolbarTool.AUTOCORRECT -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Autocorrect",
                    "The tool flips this same switch (also under Typing)",
                    settings.autocorrect,
                ) { scope.launch { repository.setAutocorrect(it) } }
            }
            item {
                NavRow(
                    "All typing settings",
                    "Suggestions, autocorrect, capitalization and more",
                    onClick = { onNavigate("typing") },
                )
            }
        }
        ToolbarTool.SOUND_HAPTICS -> {
            KeySoundGroup(repository, settings) {
                item {
                    NavRow(
                        "All key press settings",
                        "Haptic style and strength, key preview, long-press",
                        onClick = { onNavigate("keypress") },
                    )
                }
            }
            CaptionText(
                "Haptic style and strength live under Key press → Haptic feedback; " +
                    "the tool's panel changes the same settings from the keyboard.",
            )
        }
        ToolbarTool.HANDWRITING -> {
            SettingsGroup("Input") {
                item {
                    ToggleSetting(
                        "Stylus only",
                        "Only an S Pen or other stylus draws; finger touches are ignored",
                        settings.handwritingStylusOnly,
                        info = "Useful for palm rejection while writing with a stylus. Even " +
                            "with this off, finger touches are briefly ignored right after " +
                            "stylus strokes, so a resting palm doesn't scribble.",
                    ) { scope.launch { repository.setHandwritingStylusOnly(it) } }
                }
                item {
                    ToggleSetting(
                        "Auto space",
                        "Insert a space between consecutively written words",
                        settings.handwritingAutoSpace,
                    ) { scope.launch { repository.setHandwritingAutoSpace(it) } }
                }
                item {
                    SliderSetting(
                        "Recognition pause",
                        subtitle = "How long after the last stroke before the word is recognized",
                        value = settings.handwritingCommitDelayMs.toFloat(),
                        range = 300f..2000f,
                        display = { "${it.roundToInt()} ms" },
                        info = "Shorter feels snappier but can cut multi-stroke letters and " +
                            "Bengali conjuncts in half; longer gives you more time between " +
                            "strokes. Gboard uses roughly half a second.",
                    ) { scope.launch { repository.setHandwritingCommitDelayMs(it.roundToInt()) } }
                }
            }
            SectionHeader("Recognition models")
            CaptionText(
                "One model per language you type in. Recognition runs fully on-device " +
                    "with Google ML Kit; each language needs a one-time download (about " +
                    "20 MB), and after that handwriting works offline.",
            )
            HandwritingModelManager(settings)
            SettingsGroup {
                item {
                    NavRow(
                        "Languages & layouts",
                        "Add a language to get its handwriting model here",
                        onClick = { onNavigate("languages") },
                    )
                }
            }
        }
        ToolbarTool.THEMES -> SettingsGroup("Options") {
            item {
                NavRow(
                    "All theme settings",
                    "Create, edit, import and export keyboard themes",
                    onClick = { onNavigate("themes") },
                )
            }
        }
        ToolbarTool.ONE_HANDED -> SettingsGroup("Options") {
            item {
                NavRow(
                    "All layout & size settings",
                    "Keyboard height, width, alignment and one-handed mode",
                    onClick = { onNavigate("layout") },
                )
            }
        }
        ToolbarTool.TRANSLATE -> {
            SettingsGroup("Options") {
                item { TranslateLanguageSetting(repository, settings) }
            }
            SettingsGroup("API key") {
                item {
                    ApiKeyField(
                        label = "Cloud Translation API key (optional)",
                        value = settings.translateApiKey,
                        builtInAvailable = ToolApiKeys.builtInTranslate,
                        emptyHint = "Without a key, translation uses Google's free public endpoint",
                    ) { repository.setTranslateApiKey(it) }
                }
            }
            CaptionText(
                "Text you translate is sent to Google either way — only while the " +
                    "translate panel is open. The free endpoint is unofficial and " +
                    "rate-limited; a Cloud Translation key makes it official and reliable.",
            )
        }
        ToolbarTool.GIF, ToolbarTool.STICKER -> {
            if (tool == ToolbarTool.STICKER) {
                SettingsGroup("Your stickers") {
                    item {
                        NavRow(
                            "Sticker packs",
                            "Make, edit, import and export packs of your own",
                            onClick = { onNavigate("sticker_packs") },
                        )
                    }
                }
            }
            SettingsGroup("Layout") {
                item {
                    ToggleSetting(
                        "Full-screen picker",
                        "Hide the toolbar and move search up next to a back button",
                        settings.mediaFullBleed,
                        info = "The GIF and sticker panels take over the whole keyboard: " +
                            "the toolbar, emoji row and symbol row step aside and the search " +
                            "box moves into the row they leave behind, so the grid gets every " +
                            "pixel. Applies to both tools.",
                    ) { scope.launch { repository.setMediaFullBleed(it) } }
                }
            }
            SettingsGroup("Sources & API keys") {
                item {
                    ApiKeyField(
                        label = "Klipy API key",
                        value = settings.klipyApiKey,
                        builtInAvailable = ToolApiKeys.builtInKlipy,
                        emptyHint = "Free from partner.klipy.com (Tenor's API was retired mid-2026)",
                    ) { repository.setKlipyApiKey(it) }
                }
                item {
                    ApiKeyField(
                        label = "GIPHY API key",
                        value = settings.giphyApiKey,
                        builtInAvailable = ToolApiKeys.builtInGiphy,
                        emptyHint = "Free from developers.giphy.com",
                    ) { repository.setGiphyApiKey(it) }
                }
            }
            CaptionText(
                "The GIF and sticker tools share all of this, including the content " +
                    "filter below. Any one key is enough; every configured source " +
                    "shows up in the panel.",
            )
            SettingsGroup("Sending") {
                item {
                    ChoiceSetting(
                        title = "Send stickers as",
                        subtitle = "What the sticker tool hands the chat app",
                        info = "Android has no sticker flag — the only signal is the " +
                            "file's MIME type, and the receiving app decides. " +
                            "Sticker: offer WhatsApp's sticker type first, so " +
                            "stickers arrive as real stickers there; apps that " +
                            "don't support it get a normal image instead. " +
                            "Image: always send as a plain image.",
                        options = listOf(
                            MediaSendMode.STICKER to "Sticker",
                            MediaSendMode.IMAGE to "Image",
                        ),
                        selected = settings.stickerSendMode,
                    ) { scope.launch { repository.setStickerSendMode(it) } }
                }
                item {
                    ChoiceSetting(
                        title = "Send GIFs as",
                        subtitle = "Images by default — most chat apps animate them",
                        info = "Sticker mode only takes effect for GIFs the source " +
                            "provides in WebP form. Android ships no animated-WebP " +
                            "encoder, so a real animated GIF cannot be converted " +
                            "into a sticker — those keep sending as images no " +
                            "matter what this is set to.",
                        options = listOf(
                            MediaSendMode.IMAGE to "Image",
                            MediaSendMode.STICKER to "Sticker",
                        ),
                        selected = settings.gifSendMode,
                    ) { scope.launch { repository.setGifSendMode(it) } }
                }
            }
            SectionHeader("Multiple sources")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GifSourceMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.gifSourceMode == mode,
                        onClick = { scope.launch { repository.setGifSourceMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, GifSourceMode.entries.size),
                    ) {
                        Text(
                            when (mode) {
                                GifSourceMode.TABS -> "Tabs"
                                GifSourceMode.MIX -> "Mixed"
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            CaptionText(
                "Tabs: a chip per source on the panel. Mixed: one grid with results " +
                    "from every source interleaved evenly.",
            )
            SectionHeader("Content filter")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GifContentFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = settings.gifContentFilter == filter,
                        onClick = { scope.launch { repository.setGifContentFilter(filter) } },
                        shape = SegmentedButtonDefaults.itemShape(index, GifContentFilter.entries.size),
                    ) {
                        Text(
                            when (filter) {
                                GifContentFilter.OFF -> "Off"
                                GifContentFilter.LOW -> "Low"
                                GifContentFilter.MEDIUM -> "Med"
                                GifContentFilter.HIGH -> "High"
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            CaptionText(
                "High hides the most; Off hides nothing. Maps to Klipy's and " +
                    "GIPHY's rating (High = G … Off = R).",
            )
        }
        ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH -> {
            SettingsGroup("Brave Search") {
                item {
                    ApiKeyField(
                        label = "Brave API key",
                        value = settings.braveApiKey,
                        builtInAvailable = ToolApiKeys.builtInBrave,
                        emptyHint = "From api-dashboard.search.brave.com — monthly free credit",
                    ) { repository.setBraveApiKey(it) }
                }
            }
            CaptionText(
                "Web and image search share everything here. Searches the whole " +
                    "web. Brave's plan includes a monthly free credit (roughly a " +
                    "thousand searches) and asks for attribution — the panel shows " +
                    "“via Brave”.",
            )
            SettingsGroup("Results") {
                item {
                    ToggleSetting(
                        "SafeSearch", "Filter explicit results",
                        settings.searchSafe,
                    ) { scope.launch { repository.setSearchSafe(it) } }
                }
                item {
                    SliderSetting(
                        "Results per search",
                        subtitle = "Each search uses one API request either way",
                        value = settings.searchResultCount.toFloat(),
                        range = 1f..10f,
                        display = { "${it.roundToInt()}" },
                    ) { scope.launch { repository.setSearchResultCount(it.roundToInt()) } }
                }
            }
        }
        ToolbarTool.OCR -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Start with everything selected",
                        "Deselect words to trim the capture. Off starts empty " +
                            "and words are picked one by one.",
                        settings.ocrAutoSelectWords,
                    ) { scope.launch { repository.setOcrAutoSelectWords(it) } }
                }
            }
            CaptionText(
                "Recognition runs on this device with ML Kit — no photo or text " +
                    "leaves the phone, and it works offline. Reads Latin-script " +
                    "text (English etc.); Bengali isn't supported by ML Kit's " +
                    "text recognizer yet. After a capture, tap words to choose " +
                    "exactly what gets inserted or copied.",
            )
        }
        ToolbarTool.QR_SCAN -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Insert automatically",
                        "Type the code's text the moment one is spotted, no " +
                            "confirm tap",
                        settings.qrScanAutoInsert,
                    ) { scope.launch { repository.setQrScanAutoInsert(it) } }
                }
                item {
                    ToggleSetting(
                        "Vibrate on detection",
                        "A short buzz when a code is spotted",
                        settings.qrScanHaptics,
                    ) { scope.launch { repository.setQrScanHaptics(it) } }
                }
                item {
                    ToggleSetting(
                        "Load link details",
                        "When a code is a web link, fetch the page title and " +
                            "description to show above it (needs internet)",
                        settings.qrScanLinkPreviews,
                    ) { scope.launch { repository.setQrScanLinkPreviews(it) } }
                }
            }
            CaptionText(
                "Decoding runs on this device with ML Kit — offline, nothing is " +
                    "uploaded. Reads QR codes plus the common product barcode " +
                    "formats (EAN, UPC, Code 128 …). Insert types the code's " +
                    "text at the cursor; a link also gets an Open button.",
            )
        }
        ToolbarTool.DOC_SCAN -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Save to gallery",
                        "Also keep scanned pages in Pictures/WM Keyboard",
                        settings.docScanSaveToGallery,
                    ) { scope.launch { repository.setDocScanSaveToGallery(it) } }
                }
            }
            CaptionText(
            "Opens Google's document scanner (part of Google Play services) " +
                "with edge detection, crop and shadow cleanup. Scanned pages " +
                "come back as images and are inserted into the chat like a " +
                "camera photo, once the keyboard reopens. Processing is " +
                "on-device.",
            )
        }
        ToolbarTool.VOICE -> {
            val whisperEnabled = com.wasimaster.wmkeyboard.core.settings.isWhisperEnabled()
            val usingWhisper = whisperEnabled && settings.whisper.engine == "whisper"
            if (whisperEnabled) {
                SettingsGroup("Engine") {
                    item {
                        ChoiceSetting(
                            "Recognition engine",
                            subtitle = "How speech is turned into text",
                            info = "The system recognizer is fast and streams words as " +
                                "you speak, but depends on the OS and its languages. " +
                                "Offline Whisper runs entirely on this device across many " +
                                "languages and never sends audio anywhere — it transcribes " +
                                "each phrase after you stop speaking.",
                            options = listOf(
                                "system" to "System recognizer",
                                "whisper" to "Offline Whisper",
                            ),
                            selected = settings.whisper.engine,
                        ) { scope.launch { repository.setVoiceEngine(it) } }
                    }
                }
            }
            SettingsGroup("Dictation") {
                item {
                    ToggleSetting(
                        "Compact bar",
                        "Dictate over the keys instead of a full panel",
                        settings.voiceStripMode,
                    ) { scope.launch { repository.setVoiceStripMode(it) } }
                }
                item {
                    ToggleSetting(
                        "Keep listening",
                        "Start the next sentence automatically after each one commits",
                        settings.voiceContinuous,
                    ) { scope.launch { repository.setVoiceContinuous(it) } }
                }
                item {
                    ToggleSetting(
                        "Spoken punctuation",
                        "Saying \"comma\", \"question mark\" or \"দাঁড়ি\" types the mark",
                        settings.voiceSpokenPunctuation,
                    ) { scope.launch { repository.setVoiceSpokenPunctuation(it) } }
                }
            }
            if (usingWhisper) {
                SettingsGroup("Offline transcription") {
                    item {
                        ToggleSetting(
                            "Translate to English",
                            "Speak any language and type its English translation",
                            settings.whisper.translate,
                        ) { scope.launch { repository.setWhisperTranslate(it) } }
                    }
                }
                WhisperModelManager(repository, settings)
            } else {
                CaptionText(
                    "Recognition uses the device's speech recognizer. On Android " +
                        "12+ it runs on-device when the language model is " +
                        "installed; otherwise audio goes to the recognizer " +
                        "service while you dictate. Long-press the mic to " +
                        "dictate walkie-talkie style — it stops when you let go.",
                )
            }
        }
        ToolbarTool.GRAMMAR -> {
            SettingsGroup("Options") {
                item {
                    ChoiceSetting(
                        "English dialect",
                        subtitle = "Spelling and style conventions to lint against",
                        options = GrammarDialect.entries.map { it to it.label },
                        selected = settings.grammarDialect,
                    ) { scope.launch { repository.setGrammarDialect(it) } }
                }
                item {
                    SliderSetting(
                        "Re-check delay",
                        subtitle = "Pause after typing stops before issues refresh — " +
                            "lower feels snappier, higher churns less",
                        value = settings.grammarDebounceMs.toFloat(),
                        range = 100f..1500f,
                        display = { "${it.toInt()} ms" },
                    ) { scope.launch { repository.setGrammarDebounceMs(it.toInt()) } }
                }
            }
            if (BuildConfig.ENABLE_GRAMMAR) {
                val context = LocalContext.current
                SettingsGroup("System-wide") {
                    item {
                        NavRow(
                            "Use Harper everywhere",
                            "Set WM Keyboard as Android's spell checker",
                            onClick = { openSpellCheckerSettings(context) },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                            ToggleSetting(
                                "Underline only, no fix popup",
                                "Mark misspellings but don't offer corrections",
                                settings.spellCheckerNoSuggestions,
                            ) {
                                scope.launch {
                                    repository.setSpellCheckerNoSuggestions(it)
                                }
                            }
                        }
                    }
                }
            }
            CaptionText(
                "Grammar checking runs on this device with the Harper engine " +
                    "(the same one behind harper-ls) — offline, nothing you " +
                    "type is uploaded. Open the tool while writing to see " +
                    "issues with one-tap fixes; the dialect chip on the panel " +
                    "switches dialects too.\n\n" +
                    "Harper can also act as Android's system spell checker, so " +
                    "the underlines and correction menus inside other apps come " +
                    "from it as well. The button above opens the system Spell " +
                    "checker screen — pick WM Keyboard there; it follows the " +
                    "dialect chosen above. If your device hides that screen, it " +
                    "falls back to the input-method settings.",
            )
        }
        ToolbarTool.WIKIPEDIA -> {
            SettingsGroup("Options") {
                item {
                    TextFieldSetting(
                        label = "Wikipedia language",
                        value = settings.wikiLanguage,
                        hint = "Subdomain code: en, bn, de, fr, es …",
                    ) { repository.setWikiLanguage(it) }
                }
                item {
                    ToggleSetting(
                        "Markdown links",
                        "Insert links as [Title](url) instead of the bare URL",
                        settings.wikiLinksMarkdown,
                    ) { scope.launch { repository.setWikiLinksMarkdown(it) } }
                }
            }
            CaptionText(
                "Searches and article text come from wikipedia.org's free APIs — " +
                    "only while you use the tool.",
            )
        }
        ToolbarTool.SYMBOLS -> {
            SettingsGroup("Recents") {
                item {
                    ListItem(
                        headlineContent = { Text("Clear recent symbols") },
                        supportingContent = {
                            Text(
                                if (settings.symbolRecents.isEmpty()) "No recents yet"
                                else "${settings.symbolRecents.size} symbols remembered",
                            )
                        },
                        colors = transparentListColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { repository.clearSymbolRecents() } },
                    )
                }
            }
            CaptionText(
                "Fractions, math operators, Greek letters, arrows, currency signs, " +
                    "super/subscripts and typographic marks — everything types " +
                    "locally, like a regular key.",
            )
        }
        ToolbarTool.CALCULATOR -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Calculate as you type",
                    "Offer the result on the strip when you type a sum",
                    settings.smartCalc,
                    info = "Typing \"12*4\" puts 48 on the suggestion strip; tapping it " +
                        "replaces the expression with the answer. End with \"=\" and the " +
                        "answer is appended instead, leaving the sum in place. Ambiguous " +
                        "runs like dates (12/04) and phone numbers are ignored unless you " +
                        "type the \"=\" yourself. Needs \"Smart chips\" on, under Typing.",
                ) { scope.launch { repository.setSmartCalc(it) } }
            }
            item {
                ToggleSetting(
                    "Degrees",
                    "Trig functions use degrees; off = radians",
                    settings.calcDegrees,
                ) { scope.launch { repository.setCalcDegrees(it) } }
            }
            item {
                SliderSetting(
                    "Result precision",
                    subtitle = "Maximum decimal places (also used by the unit converter)",
                    value = settings.calcPrecision.toFloat(),
                    range = 0f..12f,
                    display = { "${it.roundToInt()}" },
                ) { scope.launch { repository.setCalcPrecision(it.roundToInt()) } }
            }
        }
        ToolbarTool.UNIT_CONVERT -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Convert as you type",
                        "Offer the conversion on the strip when you type a measurement",
                        settings.smartUnits,
                        info = "Typing \"1 ft\" (or \"1ft\") puts the same length in metres " +
                            "on the suggestion strip; the button on the chip opens the " +
                            "converter on that category with the pair and amount already " +
                            "filled in. The unit it converts into is whatever you last " +
                            "paired it with here. One-letter abbreviations only count " +
                            "when written against the number (\"30c\", not \"30 c\"), so " +
                            "ordinary sentences are left alone. " +
                            "Needs \"Smart chips\" on, under Typing.",
                    ) { scope.launch { repository.setSmartUnits(it) } }
                }
            }
            CaptionText(
                "14 categories — length, mass, temperature, area, volume, speed, " +
                "time, data, energy, power, pressure, angle, frequency and fuel " +
                "economy. All conversions run on-device; result precision " +
                "follows the calculator's setting. The panel reopens on the " +
                    "category and units you used last.",
            )
        }
        ToolbarTool.CURRENCY -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Convert as you type",
                        "Offer the amount in ${settings.currencyTo} when you type one in another currency",
                        settings.smartCurrency,
                        info = "\"150 usd\", \"150usd\", \"150$\" and \"150 dollars\" all put " +
                            "the converted amount on the suggestion strip. It converts into " +
                            "the \"to\" currency of the pair below — or the \"from\" one when " +
                            "you type an amount that is already in the target. Typing an " +
                            "amount is what triggers the rate fetch; nothing is requested " +
                            "before that.",
                    ) { scope.launch { repository.setSmartCurrency(it) } }
                }
                item {
                    SliderSetting(
                        "Decimal places",
                        subtitle = "Rounding of the converted amount",
                        value = settings.currencyDecimals.toFloat(),
                        range = 0f..6f,
                        display = { "${it.toInt()}" },
                    ) { scope.launch { repository.setCurrencyDecimals(it.toInt()) } }
                }
                item {
                    SliderSetting(
                        "Refresh rates every",
                        subtitle = "How long fetched exchange rates stay fresh",
                        value = settings.currencyCacheHours.toFloat(),
                        range = 1f..48f,
                        display = { "${it.toInt()} h" },
                        info = "Upstream rates update about once a day, so " +
                            "anything below 24 hours mostly affects how soon " +
                            "a failed fetch is retried.",
                    ) { scope.launch { repository.setCurrencyCacheHours(it.toInt()) } }
                }
            }
            CaptionText(
                "Rates come from open.er-api.com (about 160 currencies, updated " +
                    "daily), with frankfurter.app (European Central Bank) as a " +
                    "fallback — both free, no API key. The from/to pair you pick " +
                    "on the panel is remembered.",
            )
        }
        ToolbarTool.QR_GEN -> {
            SettingsGroup("Options") {
                item {
                    SliderSetting(
                        "Image size",
                        subtitle = "Side length of the inserted PNG",
                        value = settings.qrSizePx.toFloat(),
                        range = 256f..2048f,
                        display = { "${it.roundToInt()} px" },
                    ) { scope.launch { repository.setQrSizePx(it.roundToInt()) } }
                }
                item {
                    ChoiceSetting(
                        title = "Send as",
                        subtitle = "QR codes go out as images by default",
                        info = "Some chat apps render a bare incoming image with no " +
                            "bubble, which can look like a sticker even though it " +
                            "was sent as an image. Sticker mode offers WhatsApp's " +
                            "sticker type instead, where supported.",
                        options = listOf(
                            MediaSendMode.IMAGE to "Image",
                            MediaSendMode.STICKER to "Sticker",
                        ),
                        selected = settings.qrSendMode,
                    ) { scope.launch { repository.setQrSendMode(it) } }
                }
                item {
                    ToggleSetting(
                        "Save to gallery",
                        "Also keep generated codes in Pictures/WM Keyboard",
                        settings.qrSaveToGallery,
                    ) { scope.launch { repository.setQrSaveToGallery(it) } }
                }
            }
            SectionHeader("Error correction")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                QrEccLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = settings.qrEcc == level,
                        onClick = { scope.launch { repository.setQrEcc(level) } },
                        shape = SegmentedButtonDefaults.itemShape(index, QrEccLevel.entries.size),
                    ) { Text(level.name, maxLines = 1) }
                }
            }
            CaptionText(
                "Higher levels survive more smudging and damage but fit less " +
                    "text. The code is generated on-device and follows whatever " +
                    "the text field contains.",
            )
        }
        ToolbarTool.PASSWORD_GEN -> {
            SettingsGroup("Password") {
                item {
                    SliderSetting(
                        "Length",
                        value = settings.passwordGenerator.pwLength.toFloat(),
                        range = 4f..64f,
                        display = { "${it.roundToInt()}" },
                    ) { scope.launch { repository.setPwLength(it.roundToInt()) } }
                }
                item {
                    ToggleSetting("Uppercase letters", "A–Z", settings.passwordGenerator.pwUppercase) {
                        scope.launch { repository.setPwUppercase(it) }
                    }
                }
                item {
                    ToggleSetting("Digits", "0–9", settings.passwordGenerator.pwDigits) {
                        scope.launch { repository.setPwDigits(it) }
                    }
                }
                item {
                    ToggleSetting("Symbols", "!@#\$%…", settings.passwordGenerator.pwSymbols) {
                        scope.launch { repository.setPwSymbols(it) }
                    }
                }
                item {
                    ToggleSetting(
                        "Exclude look-alikes",
                        "Skip Il1O0o5S8B and similar",
                        settings.passwordGenerator.pwExcludeAmbiguous,
                    ) { scope.launch { repository.setPwExcludeAmbiguous(it) } }
                }
            }
            SettingsGroup("Passphrase") {
                item {
                    SliderSetting(
                        "Words",
                        value = settings.passwordGenerator.ppWordCount.toFloat(),
                        range = 2f..10f,
                        display = { "${it.roundToInt()}" },
                    ) { scope.launch { repository.setPpWordCount(it.roundToInt()) } }
                }
                item {
                    TextFieldSetting(
                        label = "Separator",
                        value = settings.passwordGenerator.ppSeparator,
                        hint = "Between words, e.g. - or . (blank = none)",
                    ) { repository.setPpSeparator(it) }
                }
                item {
                    ToggleSetting("Capitalize words", "correct-Horse → Correct-Horse", settings.passwordGenerator.ppCapitalize) {
                        scope.launch { repository.setPpCapitalize(it) }
                    }
                }
                item {
                    ToggleSetting("Include a digit", "Appended to a random word", settings.passwordGenerator.ppIncludeDigit) {
                        scope.launch { repository.setPpIncludeDigit(it) }
                    }
                }
            }
            CaptionText(
                "Everything is generated on this device with a cryptographic " +
                    "random source, never stored or logged. Passphrase words come " +
                    "from the keyboard's bundled English dictionary.",
            )
        }
        ToolbarTool.TYPING_TEST -> TypingTestToolSettings(repository, settings)
        ToolbarTool.AI -> AiToolSettings(repository, settings)
        ToolbarTool.MODES -> SettingsGroup("Modes") {
            item {
                NavRow(
                    "Edit keyboard modes",
                    "Per-app and per-field setups, and what each one changes",
                    value = "${settings.keyboardModes.size}",
                ) { onNavigate("modes") }
            }
        }
        else -> {}
    }
}

/**
 * The typing test's settings. These are the same values the panel's own
 * chip row edits — this screen is the slower way round to them, plus the
 * records the panel only shows one config of at a time.
 */
@Composable
private fun TypingTestToolSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val bests = remember(settings.typingTestBests) { TypingBests.decode(settings.typingTestBests) }
    val history = remember(settings.typingTestHistory) {
        TypingHistory.decode(settings.typingTestHistory)
    }

    SectionHeader("Default test")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        for (mode in TypingTestMode.entries) {
            FilterChip(
                selected = settings.typingTestMode == mode,
                onClick = { scope.launch { repository.setTypingTestMode(mode) } },
                label = {
                    Text(
                        when (mode) {
                            TypingTestMode.TIME -> "Timed"
                            TypingTestMode.WORDS -> "Word count"
                            TypingTestMode.QUOTE -> "Quote"
                        },
                    )
                },
            )
        }
    }

    SettingsGroup("Length") {
        when (settings.typingTestMode) {
            TypingTestMode.TIME -> item {
                SliderSetting(
                    "Seconds",
                    value = settings.typingTestDuration.toFloat(),
                    range = 15f..120f,
                    display = { "${it.roundToInt()}s" },
                ) { scope.launch { repository.setTypingTestDuration(it.roundToInt()) } }
            }
            TypingTestMode.WORDS -> item {
                SliderSetting(
                    "Words",
                    value = settings.typingTestWordCount.toFloat(),
                    range = 10f..100f,
                    display = { "${it.roundToInt()}" },
                ) { scope.launch { repository.setTypingTestWordCount(it.roundToInt()) } }
            }
            // Quotes come at whatever length they were written.
            TypingTestMode.QUOTE -> item {
                CaptionText("Quote tests run to the end of the quotation.")
            }
        }
    }

    if (settings.typingTestMode != TypingTestMode.QUOTE) {
        SettingsGroup("Difficulty") {
            item {
                ToggleSetting(
                    "Punctuation",
                    "Capitals, commas and full stops in the prompt",
                    settings.typingTestPunctuation,
                ) { scope.launch { repository.setTypingTestPunctuation(it) } }
            }
            item {
                ToggleSetting(
                    "Numbers",
                    "Mixes numerals into the word list",
                    settings.typingTestNumbers,
                ) { scope.launch { repository.setTypingTestNumbers(it) } }
            }
        }
    }

    SettingsGroup("Records") {
        item {
            ListItem(
                headlineContent = { Text("Tests completed") },
                trailingContent = { Text("${settings.typingTestsCompleted}") },
                colors = transparentListColors(),
            )
        }
        if (history.isNotEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("Recent average") },
                    supportingContent = { Text("Across the last ${history.size} runs") },
                    trailingContent = { Text("${history.average().roundToInt()} wpm") },
                    colors = transparentListColors(),
                )
            }
        }
        // One row per config the user has actually run, best first.
        for ((key, wpm) in bests.entries.sortedByDescending { it.value }) {
            item {
                ListItem(
                    headlineContent = { Text(typingBestLabel(key)) },
                    colors = transparentListColors(),
                    trailingContent = { Text("${wpm.roundToInt()} wpm") },
                )
            }
        }
        if (bests.isNotEmpty() || settings.typingTestsCompleted > 0) {
            item {
                NavRow("Clear records", "Deletes every best score and the run history") {
                    scope.launch { repository.clearTypingStats() }
                }
            }
        }
    }

    CaptionText(
        "The test runs on the keyboard itself, so it measures the layout, " +
            "the key sizes and the gestures you actually type with. Nothing " +
            "you type during a test reaches the text field, and no scores " +
            "leave the device.",
    )
}

/** Turns a stored best's key ("time30", "quote") back into a heading. */
private fun typingBestLabel(key: String): String = when {
    key == "quote" -> "Quote"
    key.startsWith("time") -> "${key.removePrefix("time")} seconds"
    key.startsWith("words") -> "${key.removePrefix("words")} words"
    else -> key
}

/** The AI tool's settings: provider, credentials, output and prompts. */
@Composable
private fun AiToolSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SectionHeader("Provider")
    // Six providers no longer fit a segmented row; chips wrap instead.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val providers = AiProvider.entries.filter {
            it != AiProvider.ON_DEVICE || BuildConfig.ENABLE_LOCAL_LLM
        }
        for (provider in providers) {
            FilterChip(
                selected = settings.aiProvider == provider,
                onClick = { scope.launch { repository.setAiProvider(provider) } },
                label = { Text(provider.label, maxLines = 1) },
            )
        }
    }
    when (settings.aiProvider) {
        AiProvider.ANTHROPIC -> SettingsGroup("Claude (Anthropic)") {
            item {
                ApiKeyField(
                    label = "Anthropic API key",
                    value = settings.aiAnthropicKey,
                    builtInAvailable = false,
                    emptyHint = "From console.anthropic.com → API keys",
                ) { repository.setAiAnthropicKey(it) }
            }
            item {
                TextFieldSetting(
                    label = "Model",
                    value = settings.aiAnthropicModel,
                    hint = "Blank = ${AiClient.DefaultModels.ANTHROPIC}",
                ) { repository.setAiAnthropicModel(it) }
            }
        }
        AiProvider.OPENAI -> SettingsGroup("OpenAI") {
            item {
                ApiKeyField(
                    label = "OpenAI API key",
                    value = settings.aiOpenAiKey,
                    builtInAvailable = false,
                    emptyHint = "From platform.openai.com → API keys",
                ) { repository.setAiOpenAiKey(it) }
            }
            item {
                TextFieldSetting(
                    label = "Model",
                    value = settings.aiOpenAiModel,
                    hint = "Blank = ${AiClient.DefaultModels.OPENAI}",
                ) { repository.setAiOpenAiModel(it) }
            }
        }
        AiProvider.GEMINI -> SettingsGroup("Gemini (Google)") {
            item {
                ApiKeyField(
                    label = "Gemini API key",
                    value = settings.aiGeminiKey,
                    builtInAvailable = false,
                    emptyHint = "Free tier from aistudio.google.com",
                ) { repository.setAiGeminiKey(it) }
            }
            item {
                TextFieldSetting(
                    label = "Model",
                    value = settings.aiGeminiModel,
                    hint = "Blank = ${AiClient.DefaultModels.GEMINI}",
                ) { repository.setAiGeminiModel(it) }
            }
        }
        AiProvider.OLLAMA -> SettingsGroup("Ollama server") {
            item {
                TextFieldSetting(
                    label = "Server address",
                    value = settings.aiOllamaUrl,
                    hint = "e.g. http://192.168.0.10:11434 (your computer's LAN IP)",
                ) { repository.setAiOllamaUrl(it) }
            }
            item {
                TextFieldSetting(
                    label = "Model",
                    value = settings.aiOllamaModel,
                    hint = "Blank = ${AiClient.DefaultModels.OLLAMA}",
                ) { repository.setAiOllamaModel(it) }
            }
        }
        AiProvider.LM_STUDIO -> SettingsGroup("LM Studio server") {
            item {
                TextFieldSetting(
                    label = "Server address",
                    value = settings.aiLmStudioUrl,
                    hint = "e.g. http://192.168.0.10:1234 (enable the local server in LM Studio)",
                ) { repository.setAiLmStudioUrl(it) }
            }
            item {
                TextFieldSetting(
                    label = "Model",
                    value = settings.aiLmStudioModel,
                    hint = "Blank = whatever model the server has loaded",
                ) { repository.setAiLmStudioModel(it) }
            }
        }
        AiProvider.ON_DEVICE -> LocalLlmModelManager(repository, settings)
    }
    if (settings.aiProvider == AiProvider.OLLAMA || settings.aiProvider == AiProvider.LM_STUDIO) {
        CaptionText(
            "Start Ollama with OLLAMA_HOST=0.0.0.0 (or enable “serve on local " +
                "network” in LM Studio) so the phone can reach it. Plain-HTTP " +
                "traffic stays on your network.",
        )
    }
    SettingsGroup("Output") {
        if (settings.aiProvider != AiProvider.ON_DEVICE) {
            item {
                SliderSetting(
                    "Max response length",
                    subtitle = "Upper bound in tokens (≈ ¾ of a word each). " +
                        "Reasoning models automatically get 4× this, since their " +
                        "thinking is spent from the same budget.",
                    value = settings.aiMaxTokens.toFloat(),
                    range = 256f..8192f,
                    display = { "${it.roundToInt()}" },
                ) { scope.launch { repository.setAiMaxTokens(it.roundToInt()) } }
            }
        }
        item {
            TextFieldSetting(
                label = "Translate action's target language",
                value = settings.aiTranslateTo,
                hint = "e.g. English, Bengali, Japanese",
            ) { repository.setAiTranslateTo(it) }
        }
        item {
            ToggleSetting(
                "Show model reasoning",
                "Stream reasoning models' <think> passages instead of a progress bar",
                settings.aiShowThinking,
            ) { scope.launch { repository.setAiShowThinking(it) } }
        }
        item {
            ToggleSetting(
                "Model picker on the panel",
                "Switch between configured providers and downloaded models right on the keyboard",
                settings.aiPanelModelPicker,
            ) { scope.launch { repository.setAiPanelModelPicker(it) } }
        }
    }
    SectionHeader("Prompts")
    CaptionText(
        "Each action's system prompt, editable. Clearing a field restores " +
            "the built-in prompt.",
    )
    for (action in AiAction.entries) {
        // Custom has no stored prompt — its instruction is typed per run.
        if (action == AiAction.CUSTOM) continue
        val current = when (action) {
            AiAction.REWRITE -> settings.aiPromptRewrite
            AiAction.SUMMARIZE -> settings.aiPromptSummarize
            AiAction.TRANSLATE -> settings.aiPromptTranslate
            AiAction.IMPROVE -> settings.aiPromptImprove
            AiAction.FIX_GRAMMAR -> settings.aiPromptFixGrammar
            AiAction.EXPLAIN -> settings.aiPromptExplain
            AiAction.CONTINUE -> settings.aiPromptContinue
            AiAction.CUSTOM -> ""
        }
        val builtIn = AiPrompts.defaultPrompt(action, settings.aiTranslateTo)
        PromptFieldSetting(
            label = action.label,
            // Pre-filled with the built-in prompt so editing starts from the
            // real text instead of a blank field; saving identical text is a
            // no-op override.
            value = current.ifBlank { builtIn },
            defaultPrompt = builtIn,
        ) { repository.setAiPrompt(action, it) }
    }
    CaptionText(
        if (settings.aiProvider == AiProvider.ON_DEVICE) {
            "On-device models run entirely on this phone — the text you run " +
                "an action on never leaves it. Response length is bounded by " +
                "the model's context window."
        } else {
            "The text you run an action on is sent to the selected provider, " +
                "only when you tap the action. Keys are stored on this device."
        },
    )
}

/**
 * The words that make this tool offer itself on the suggestion strip.
 * Only tools that ship a default get the row — a keyword for "Undo" would
 * fire on prose and there is nothing to open anyway.
 */
@Composable
private fun ToolKeywordSetting(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    tool: ToolbarTool,
) {
    val defaults = SmartSuggest.defaultKeywords[tool] ?: return
    val scope = rememberCoroutineScope()
    val saved = SmartSuggest.keywordsFor(tool, settings.toolKeywords)
    var text by remember(tool) { mutableStateOf(saved.joinToString(", ")) }
    SettingsGroup("Keyword shortcut") {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    scope.launch { repository.setToolKeywords(tool, it.split(',')) }
                },
                label = { Text("Trigger words") },
                singleLine = true,
                supportingText = {
                    Text(
                        if (saved.isEmpty()) {
                            "No trigger words — this tool never offers itself."
                        } else {
                            "Type one of these on its own and the suggestion strip " +
                                "offers to open ${toolTitle(tool)}, dropping the word. " +
                                "Separate several with commas."
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (saved != defaults) {
            item {
                ListItem(
                    headlineContent = { Text("Reset to default") },
                    supportingContent = { Text(defaults.joinToString(", ")) },
                    colors = transparentListColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            text = defaults.joinToString(", ")
                            scope.launch { repository.setToolKeywords(tool, defaults) }
                        },
                )
            }
        }
    }
    if (!settings.smartSuggestions || !settings.smartToolKeywords) {
        CaptionText("Tool keywords are currently off — turn them back on under Typing → Smart chips.")
    }
}

/** A plain saved-as-you-type text setting (same mechanics as ApiKeyField). */
@Composable
private fun TextFieldSetting(
    label: String,
    value: String,
    hint: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(label) { mutableStateOf(value) }
    HighlightableRow(label) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                scope.launch { onSave(it) }
            },
            label = { Text(label) },
            singleLine = true,
            supportingText = { Text(hint) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** Multi-line prompt override; the built-in prompt shows as the hint. */
@Composable
private fun PromptFieldSetting(
    label: String,
    value: String,
    defaultPrompt: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Re-init when the built-in prompt changes (e.g. the Translate target
    // language changed under us), so the field doesn't keep showing — and
    // mislabel as "Custom" — the previous language's prompt. Keyed on
    // defaultPrompt, not value: value echoes the user's own keystrokes back
    // asynchronously, and keying on it would drop fast typing.
    var text by remember(label, defaultPrompt) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            scope.launch { onSave(it) }
        },
        label = { Text(label) },
        minLines = 1,
        maxLines = 4,
        supportingText = {
            Text(
                when {
                    text.isBlank() -> defaultPrompt
                    text == defaultPrompt -> "Built-in prompt"
                    else -> "Custom prompt (clear to restore the default)"
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * One API-key input. Saves as you type (it's a paste, in practice). The
 * user's key always beats any key baked into the build via
 * local.properties — leaving the field blank falls back to the built-in
 * key when the build has one.
 */
@Composable
internal fun ApiKeyField(
    label: String,
    value: String,
    builtInAvailable: Boolean,
    emptyHint: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(label) { mutableStateOf(value) }
    HighlightableRow(label) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                scope.launch { onSave(it) }
            },
            label = { Text(label) },
            singleLine = true,
            supportingText = {
                Text(
                    when {
                        text.isNotBlank() -> "Using your key"
                        builtInAvailable -> "Blank — using the key built into this app"
                        else -> emptyHint
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * One of the calendar tool's two alternate-calendar slots. A dialog rather
 * than a segmented row: nine choices never fit side by side, and the tool's
 * settings and the onboarding page both ask the same question.
 */
@Composable
internal fun AltCalendarSetting(
    title: String,
    subtitle: String,
    selected: AltCalendar,
    onChange: (AltCalendar) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    NavRow(
        title,
        subtitle = subtitle,
        value = if (selected == AltCalendar.NONE) "None" else selected.label.substringBefore(" ·"),
        onClick = { dialogOpen = true },
    )
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(AltCalendar.entries) { calendar ->
                        ListItem(
                            headlineContent = { Text(calendar.label) },
                            trailingContent = if (calendar == selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                            } else null,
                            modifier = Modifier.clickable {
                                dialogOpen = false
                                onChange(calendar)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) { Text("Close") }
            },
        )
    }
}

/** "Translate into" row with a full-language-list dialog. */
@Composable
private fun TranslateLanguageSetting(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    NavRow(
        "Translate into",
        subtitle = "Source language is auto-detected; also changeable from the panel",
        value = TranslateClient.languageName(settings.translateTargetLang),
        onClick = { dialogOpen = true },
    )
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("Translate into") },
            text = {
                LazyColumn {
                    items(TranslateClient.languages) { (code, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = if (code == settings.translateTargetLang) {
                                { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                            } else null,
                            modifier = Modifier.clickable {
                                dialogOpen = false
                                scope.launch { repository.setTranslateTargetLang(code) }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) { Text("Close") }
            },
        )
    }
}

/**
 * Open Android's Spell checker settings screen — where WM Keyboard's Harper
 * service can be picked as the system checker.
 *
 * There is no public [Settings] action for this screen, so we aim the direct
 * AOSP Settings component first and only fall back to the input-method
 * settings page (its parent) when that component is missing or hidden, as it
 * is on some OEM builds. Resolving before launching keeps a stock ROM that
 * renamed the activity from throwing an [android.content.ActivityNotFoundException].
 */
private fun openSpellCheckerSettings(context: Context) {
    val direct = Intent(Intent.ACTION_MAIN).setComponent(
        ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$SpellCheckersSettingsActivity",
        )
    )
    val resolves = context.packageManager.resolveActivity(direct, 0) != null
    val launched = resolves && runCatching { context.startActivity(direct) }.isSuccess
    if (!launched) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }
}

/**
 * Download/delete state for the handwriting model of every language the user
 * types in — drawn from ML Kit's full ink catalogue, then narrowed to the
 * enabled languages so the list is only ever as long as it is useful. Status
 * is re-read from ML Kit's model manager after every action.
 */
@Composable
private fun HandwritingModelManager(settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val languages = remember(settings.enabledLanguages) {
        HandwritingModels.modelsFor(settings.enabledLanguages)
    }
    val missing = remember(settings.enabledLanguages, languages) {
        settings.enabledLanguages.distinctBy { it.id }
            .filter { HandwritingModels.tagFor(it) == null }
    }
    // tag -> "checking" | "missing" | "downloaded" | "downloading" | "error"
    val statuses = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(languages) {
        for (language in languages) {
            statuses[language.tag] =
                if (HandwritingModels.isDownloaded(language.tag)) "downloaded" else "missing"
        }
    }
    if (languages.isEmpty()) {
        CaptionText(
            "None of your enabled languages has an ML Kit handwriting model. " +
                "Add a language under Languages & layouts and its model appears here.",
        )
        return
    }
    SettingsGroup {
        for (language in languages) {
            item {
                val status = statuses[language.tag] ?: "checking"
                ListItem(
                    headlineContent = { Text(language.displayName) },
                    supportingContent = {
                        Text(
                            when (status) {
                                "checking" -> "Checking…"
                                "downloaded" -> "Downloaded — works offline"
                                "downloading" -> "Downloading…"
                                "error" -> "Download failed — check your connection"
                                else -> "Not downloaded"
                            },
                        )
                    },
                    trailingContent = {
                        when (status) {
                            "downloading", "checking" -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            "downloaded" -> IconButton(onClick = {
                                scope.launch {
                                    HandwritingModels.delete(language.tag)
                                    statuses[language.tag] =
                                        if (HandwritingModels.isDownloaded(language.tag)) "downloaded" else "missing"
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete ${language.displayName} model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> TextButton(onClick = {
                                statuses[language.tag] = "downloading"
                                scope.launch {
                                    val ok = runCancellable { HandwritingModels.download(language.tag) }.isSuccess
                                    statuses[language.tag] = if (ok) "downloaded" else "error"
                                }
                            }) { Text("Download") }
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }
    if (missing.isNotEmpty()) {
        CaptionText(
            "No handwriting model exists for " +
                missing.joinToString(", ") { it.englishName } +
                " — writing switches to the nearest language that has one.",
        )
    }
}

/**
 * Weather location: place label plus coordinates, edited in a dialog. Shared
 * with the onboarding tool-setup page, which asks the same question.
 */
@Composable
internal fun WeatherLocationSetting(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val summary = if (settings.weatherLatitude != null && settings.weatherLongitude != null) {
        val place = settings.weatherPlaceName.ifBlank { "Unnamed location" }
        "%s — %.3f, %.3f".format(place, settings.weatherLatitude, settings.weatherLongitude)
    } else {
        "Not set — tap to add coordinates"
    }
    ListItem(
        headlineContent = { Text("Location") },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.Outlined.Edit, contentDescription = "Edit location") },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true },
    )
    if (!editing) return

    var place by remember { mutableStateOf(settings.weatherPlaceName) }
    var lat by remember { mutableStateOf(settings.weatherLatitude?.toString().orEmpty()) }
    var lon by remember { mutableStateOf(settings.weatherLongitude?.toString().orEmpty()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val parsedLat = lat.trim().toFloatOrNull()?.takeIf { it in -90f..90f }
    val parsedLon = lon.trim().toFloatOrNull()?.takeIf { it in -180f..180f }

    fun search() {
        if (query.isBlank() || searching) return
        searching = true
        searchFailed = false
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { WeatherClient.geocode(query) }.getOrNull()
            }
            searching = false
            if (found == null) {
                searchFailed = true
            } else {
                results = found
                searchFailed = found.isEmpty()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { editing = false },
        // Typing in the search/coordinate fields moves the dialog around the
        // keyboard, so a tap can land on the scrim where the dialog just was
        // and silently swallow the half-entered location. Explicit
        // Cancel/Save only; back still dismisses.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Weather location") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search city or place") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { search() }, enabled = query.isNotBlank() && !searching) {
                        Text(if (searching) "…" else "Search")
                    }
                }
                if (searchFailed) {
                    Text(
                        if (results.isEmpty() && !searching) "No matches — try another spelling."
                        else "Search failed — check your connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                for (result in results) {
                    ListItem(
                        headlineContent = { Text(result.name) },
                        supportingContent = {
                            Text(
                                "%s · %.3f, %.3f".format(
                                    result.region.ifBlank { "—" },
                                    result.latitude, result.longitude,
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                place = result.name
                                lat = result.latitude.toString()
                                lon = result.longitude.toString()
                                results = emptyList()
                            },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Or enter coordinates yourself (decimal degrees; south and " +
                        "west are negative).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Name (shown on the panel)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude, e.g. 23.81") },
                    singleLine = true,
                    isError = lat.isNotBlank() && parsedLat == null,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitude, e.g. 90.41") },
                    singleLine = true,
                    isError = lon.isNotBlank() && parsedLon == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedLat != null && parsedLon != null,
                onClick = {
                    scope.launch {
                        repository.setWeatherLocation(parsedLat, parsedLon, place.trim())
                    }
                    editing = false
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (settings.weatherLatitude != null) {
                    TextButton(onClick = {
                        scope.launch { repository.setWeatherLocation(null, null, "") }
                        editing = false
                    }) { Text("Clear") }
                }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        },
    )
}

// ---- privacy ----

private const val AUTO_INCOGNITO_INFO =
    "Apps can mark a text field as one the keyboard should not learn from. Chrome does " +
        "this for every field in an incognito tab, and other browsers and password " +
        "managers do the same for their private screens. While such a field has focus, " +
        "incognito switches on for it and the incognito badge appears next to the " +
        "toolbar; leaving the field restores normal typing. Apps that never send the " +
        "flag can't be detected, so this can't cover private modes that don't use it."

@Composable
private fun PrivacySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SettingsGroup("On-device learning") {
        item {
            ToggleSetting(
                "Learn from typing",
                "Personalize suggestions on-device. Nothing ever leaves your phone.",
                settings.learnFromTyping,
                info = "Words and word pairs you type are stored in a private on-device " +
                    "dictionary to improve suggestions and gesture typing. Learning is skipped " +
                    "in password fields and while incognito mode is on, and can be wiped below " +
                    "at any time.",
            ) { scope.launch { repository.setLearnFromTyping(it) } }
        }
        item {
            ToggleSetting(
                "Add words to the system dictionary",
                "Share learned words with Android's personal dictionary",
                settings.addWordsToSystemDictionary,
                info = "Also saves words you type into Android's system personal dictionary, " +
                    "so other keyboards and the platform spell checker recognize them too — " +
                    "the same list you can edit under System Settings → Languages & input → " +
                    "Dictionary. Off by default: this keyboard's own learning already covers " +
                    "it, and this writes outside the app. Follows \"Learn from typing\" and " +
                    "incognito, and existing entries are left alone when you turn it off.",
            ) { scope.launch { repository.setAddWordsToSystemDictionary(it) } }
        }
        item {
            ToggleSetting(
                "Expand dictionary shortcuts",
                "Type a shortcut, get its full phrase as a suggestion",
                settings.suggestionStrip.expandUserDictShortcuts,
                info = "Android's personal dictionary lets each entry carry a shortcut — the same " +
                    "list under System Settings → Languages & input → Dictionary. With this on, " +
                    "typing a shortcut (say \"omw\") offers its full phrase (\"on my way\") as the " +
                    "top suggestion. Off by default. Reloads when the keyboard next opens, so a " +
                    "shortcut you just added shows up after switching apps.",
            ) { scope.launch { repository.setExpandUserDictShortcuts(it) } }
        }
        item {
            ToggleSetting(
                "Incognito mode",
                "Pause learning and clipboard capture",
                settings.incognito,
                info = "While on, the keyboard learns nothing from your typing and clipboard " +
                    "history is not recorded. Existing learned words are untouched.",
            ) { scope.launch { repository.setIncognito(it) } }
        }
        item {
            ToggleSetting(
                "Follow private browsing",
                "Turn incognito on by itself in private tabs and fields",
                settings.autoIncognito,
                info = AUTO_INCOGNITO_INFO,
            ) { scope.launch { repository.setAutoIncognito(it) } }
        }
    }
    SettingsGroup("Your data") {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedButton(onClick = {
                    java.io.File(context.filesDir, "learning/user_lexicon.json").delete()
                    java.io.File(context.filesDir, "learning/emoji_usage.json").delete()
                    // Chinese/Japanese/Cantonese picks live apart from the Latin
                    // lexicon, so clearing has to name them or they survive it.
                    java.io.File(context.filesDir, "learning/cjk_history.json").delete()
                    CjkLearning.store?.clear()
                }) { Text("Clear learned words") }
            }
        }
    }
    CaptionText(
        "WM Keyboard works offline: dictionaries, Bengali transliteration and " +
            "emoji search are all bundled, and there is no telemetry. The one " +
            "exception is the optional weather tool, which fetches conditions from " +
            "Open-Meteo only when you open it (and can be disabled under Tools).",
    )
}

// ---- snippets ----

@Composable
private fun SnippetSettings() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = remember { java.io.File(context.filesDir, "snippets/snippets.json") }
    // SnippetStore's constructor reads and JSON-parses the file, so it (and
    // every save) runs on Dispatchers.IO, not during composition or on a click.
    var store by remember { mutableStateOf<SnippetStore?>(null) }
    var snippets by remember { mutableStateOf<List<Snippet>>(emptyList()) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { SnippetStore(file) }
        snippets = s.items()
        store = s
    }

    fun mutate(block: (SnippetStore) -> Unit) {
        val s = store ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                block(s)
                s.save()
            }
            snippets = s.items()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SnippetFile.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = snippets
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(
                            SnippetFile.encode(
                                current,
                                appVersion = BuildConfig.VERSION_CODE,
                                appVersionName = BuildConfig.VERSION_NAME,
                            ).toByteArray(),
                        )
                    } ?: error("no stream")
                }.isSuccess
            }
            message = if (ok) "Saved ${current.size} snippets." else "Could not write that file."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val s = store ?: return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri)
                        .use { SnippetFile.decode(it.readBytes().decodeToString()) }
                }.getOrNull()
            }
            message = if (imported == null) {
                "That file is not a WMKeyboard snippet pack."
            } else {
                // Added alongside what's already there, with fresh ids — an
                // import should never quietly replace snippets someone wrote.
                withContext(Dispatchers.IO) {
                    for (snippet in imported.snippets) {
                        s.add(snippet.label, snippet.text, snippet.trigger)
                    }
                    // add() is in-memory only; save() is what writes the file.
                    s.save()
                }
                snippets = s.items()
                buildString {
                    append("Imported ${imported.snippets.size} snippets.")
                    if (imported.repairs.isNotEmpty()) {
                        append("\n\nChanged on the way in:")
                        for (line in imported.repairs) append("\n• $line")
                    }
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    Text(
        "Snippets are reusable pieces of text — an address, an email sign-off, a " +
            "canned reply — inserted from the keyboard's snippet panel with one tap.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Template variables", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            // Live examples: expand the actual templates so the preview always
            // matches what an insertion would produce right now. The variables
            // the IME alone can fill in get a stand-in example instead.
            for (variable in SnippetVariable.entries) {
                VariableRow(variable.token, variable.description, sampleFor(variable))
            }
            VariableRow(
                "{date:…}", "any date format you like",
                SnippetStore.expand("{date:EEE d MMM}"),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Variables expand at the moment the snippet is inserted, not when it " +
                    "is saved — so {date} always produces the current date. " +
                    "{date:…} takes a pattern, e.g. {date:EEEE} or {date:dd/MM/yy}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { showAdd = true }) { Text("Add snippet") }
        OutlinedButton(
            onClick = { importLauncher.launch(SnippetFile.IMPORT_MIME_TYPES) },
        ) { Text("Import") }
        OutlinedButton(
            onClick = { exportLauncher.launch(SnippetFile.fileName()) },
            enabled = snippets.isNotEmpty(),
        ) { Text("Export") }
    }
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        for (snippet in snippets) {
            item {
                ListItem(
                    headlineContent = { Text(snippet.label) },
                    supportingContent = {
                        Column {
                            Text(snippet.text, maxLines = 2)
                            val preview = SnippetStore.expandWithCursor(
                                snippet.text,
                                context = SNIPPET_PREVIEW_CONTEXT,
                            ).text
                            if (snippet.text != preview) {
                                Text(
                                    "Inserts as: $preview",
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (snippet.trigger != null) {
                                Text(
                                    "Auto-expands from: ${snippet.trigger}",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = snippet }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { mutate { it.remove(snippet.id) } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }

    if (showAdd || editing != null) {
        SnippetDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { label, text, trigger ->
                val current = editing
                mutate { s ->
                    if (current == null) s.add(label, text, trigger) else s.update(current.id, label, text, trigger)
                }
                showAdd = false
                editing = null
            },
        )
    }
}

/** Stand-in values so the settings preview shows a realistic expansion. */
private val SNIPPET_PREVIEW_CONTEXT = SnippetStore.Companion.Context(
    clipboard = "…",
    appName = "this app",
    packageName = "com.example.app",
    selection = "…",
)

/**
 * Example value for the reference card. Most variables can be expanded for
 * real; the ones that depend on the keyboard's live context (clipboard, app,
 * selection) get a description of what they'd produce instead.
 */
private fun sampleFor(variable: SnippetVariable): String = when (variable) {
    SnippetVariable.CLIP -> "whatever you copied last"
    SnippetVariable.SELECTION -> "the text you had selected"
    SnippetVariable.APP -> "Messages"
    SnippetVariable.PACKAGE -> "com.google.android.apps.messaging"
    SnippetVariable.CURSOR -> "nothing — it only moves the cursor"
    else -> SnippetStore.expand(variable.token)
}

/** One row in the template-variable reference card. */
@Composable
private fun VariableRow(variable: String, meaning: String, example: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            variable,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(96.dp),
        )
        Column {
            Text(meaning, style = MaterialTheme.typography.bodyMedium)
            Text(
                "e.g. $example",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SnippetDialog(
    initial: Snippet?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var trigger by remember { mutableStateOf(initial?.trigger.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New snippet" else "Edit snippet") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text — supports {date} {time} {clip} {app} {cursor} …") },
                    minLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Trigger word (optional)") },
                    singleLine = true,
                )
                Text(
                    "Typing this word on its own auto-expands it to the text above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(label.trim(), text, trigger.trim().ifBlank { null }) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- rows & bars ----

private fun barRowTitle(row: BarRow): String = when (row) {
    BarRow.TOPBAR -> "Suggestions & toolbar"
    BarRow.EMOJI -> "Emoji row"
    BarRow.SYMBOL -> "Symbol row"
}

private fun barRowSubtitle(row: BarRow, settings: KeyboardSettings): String = when (row) {
    BarRow.TOPBAR -> "Always shown"
    BarRow.EMOJI -> when (settings.emojiBarMode) {
        EmojiBarMode.OFF -> "Off — enable it in Emoji settings"
        EmojiBarMode.BUTTON -> "Behind a toolbar button"
        EmojiBarMode.ALWAYS -> "Own row"
    }
    BarRow.SYMBOL -> if (settings.symbolRowEnabled) "On" else "Off"
}

/** Row layout above the keys: symbol row, row order and symbol sets. */
@Composable
private fun RowsSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Symbol row") {
        item {
            ToggleSetting(
                "Symbol row",
                "A row of special characters and snippets above the keys",
                settings.symbolRowEnabled,
                info = "Shows one symbol set at a time — @gmail.com and friends for " +
                    "email, https:// for browsing, brackets for coding. The chip at " +
                    "the row's left edge switches sets; keyboard modes can pick a " +
                    "set per app automatically.",
            ) { scope.launch { repository.setSymbolRowEnabled(it) } }
        }
    }
    SettingsGroup("Row order") {
        val order = settings.barOrder
        order.forEachIndexed { index, row ->
            item {
                ListItem(
                    headlineContent = { Text(barRowTitle(row)) },
                    supportingContent = { Text(barRowSubtitle(row, settings)) },
                    trailingContent = {
                        if (row != BarRow.TOPBAR) {
                            Row {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val next = order.toMutableList()
                                        next[index] = next[index - 1].also { next[index - 1] = next[index] }
                                        scope.launch { repository.setBarOrder(next) }
                                    },
                                ) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up")
                                }
                                IconButton(
                                    enabled = index < order.lastIndex,
                                    onClick = {
                                        val next = order.toMutableList()
                                        next[index] = next[index + 1].also { next[index + 1] = next[index] }
                                        scope.launch { repository.setBarOrder(next) }
                                    },
                                ) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down")
                                }
                            }
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }
    CaptionText("Rows are stacked top to bottom in this order. Hidden rows keep their slot.")
    SettingsGroup("Symbol sets") {
        val allSets = resolveSymbolSets(settings.customSymbolSets)
        for (set in allSets) {
            item {
                val enabled = set.id in settings.symbolRowSetIds
                val edited = settings.customSymbolSets.any { it.id == set.id }
                val builtIn = BuiltInSymbolSets.byId(set.id) != null
                ListItem(
                    headlineContent = { Text(set.name) },
                    supportingContent = {
                        Text(
                            set.chars.take(8).joinToString(" ") + if (set.chars.size > 8) " …" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { on ->
                                val next = if (on) {
                                    settings.symbolRowSetIds + set.id
                                } else {
                                    settings.symbolRowSetIds - set.id
                                }
                                // At least one set stays enabled — an empty row
                                // would have nothing to show.
                                if (next.isNotEmpty()) {
                                    scope.launch { repository.setSymbolRowSetIds(next) }
                                }
                            },
                        )
                    },
                    // Every set is editable now, built-ins included: editing
                    // one stores an override under the same id, so modes that
                    // reference it keep working and "Reset" brings it back.
                    trailingContent = {
                        IconButton(onClick = { onNavigate("symbol_set_edit/${set.id}") }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = if (builtIn && !edited) {
                                    "Edit built-in set"
                                } else {
                                    "Edit set"
                                },
                            )
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text("New symbol set") },
                supportingContent = { Text("Your own characters and snippets") },
                colors = transparentListColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNavigate("symbol_set_edit/custom_${System.currentTimeMillis()}")
                    },
            )
        }
    }
    CaptionText(
        "Checked sets appear in the symbol row's picker. Editing a built-in set " +
            "keeps its name in modes that use it; reset one to get the shipped " +
            "characters back.",
    )
}

/**
 * Create or edit one symbol set, built-ins included. Editing a built-in
 * saves an override stored under the same id, so anything referencing that
 * id (a mode's pinned sets, the row's active set) keeps pointing at it and
 * "Reset" simply drops the override to bring the shipped set back.
 */
@Composable
private fun SymbolSetEditor(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    setId: String,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val override = settings.customSymbolSets.firstOrNull { it.id == setId }
    val builtIn = BuiltInSymbolSets.byId(setId)
    val existing = override ?: builtIn
    var name by remember(setId) { mutableStateOf(existing?.name.orEmpty()) }
    var charsText by remember(setId) { mutableStateOf(existing?.chars?.joinToString(" ").orEmpty()) }
    if (builtIn != null) {
        CaptionText(
            "This is a built-in set. Your changes shadow it — everything already " +
                "using “${builtIn.name}” picks them up, and Reset restores the original.",
        )
    }
    SettingsGroup {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            OutlinedTextField(
                value = charsText,
                onValueChange = { charsText = it },
                label = { Text("Characters & snippets") },
                supportingText = {
                    Text("Separate entries with spaces — single characters (© § →) or whole snippets (@gmail.com https://)")
                },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Only an existing stored set can be removed — and for a built-in
        // that removal is a reset, not a delete.
        if (override != null) {
            TextButton(onClick = {
                scope.launch {
                    repository.deleteSymbolSet(setId)
                }
                onDone()
            }) {
                Icon(
                    if (builtIn != null) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (builtIn != null) "Reset set" else "Delete set")
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            enabled = charsText.isNotBlank(),
            onClick = {
                val chars = charsText.split(Regex("\\s+")).filter { it.isNotEmpty() }
                scope.launch {
                    repository.upsertSymbolSet(
                        SymbolSet(
                            setId,
                            name.trim().ifEmpty { builtIn?.name ?: "My set" },
                            chars,
                        ),
                    )
                    // A new set should show up in the row right away.
                    if (setId !in settings.symbolRowSetIds) {
                        repository.setSymbolRowSetIds(settings.symbolRowSetIds + setId)
                    }
                }
                onDone()
            },
        ) { Text("Save") }
    }
}

// ---- keyboard modes ----

/** One-line recap of a mode's bindings for the list screen. */
private fun modeBindingsSummary(mode: KeyboardMode): String {
    val parts = mutableListOf<String>()
    if (mode.apps.isNotEmpty()) {
        parts += if (mode.apps.size == 1) "1 app" else "${mode.apps.size} apps"
    }
    if (mode.fieldKinds.isNotEmpty()) {
        parts += mode.fieldKinds.joinToString(", ") { modeFieldLabel(it).lowercase() } + " fields"
    }
    // " + " rather than " · ": with both set, both have to match.
    return if (parts.isEmpty()) "Manual only (Modes tool)" else "Auto: " + parts.joinToString(" + ")
}

private fun modeFieldLabel(field: ModeField): String = when (field) {
    ModeField.PASSWORD -> "Password"
    ModeField.EMAIL -> "Email"
    ModeField.URL -> "URL"
    ModeField.NUMBER -> "Number"
    ModeField.PHONE -> "Phone"
    ModeField.TEXT -> "Text"
}

/** Row height inside [ReorderDialog] — fixed, so drags map to index shifts. */
private val ReorderRowHeight = 52.dp

/**
 * Drags a list into the order the user wants. Rows carry a handle on the
 * right; dragging one past the next row's height swaps the two, so the item
 * tracks the finger and the list settles as it goes.
 *
 * The working copy only reaches the caller through [onConfirm] — backing out
 * leaves the stored order alone.
 */
@Composable
internal fun <T> ReorderDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onConfirm: (List<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(items) }
    // -1 = nothing being dragged.
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ReorderRowHeight.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CaptionText("Drag the handles to reorder. Top of the list comes first.")
                // Deliberately not a LazyColumn: every row has to stay
                // composed for a drag to swap past it, and these lists are
                // short enough that laying them all out is free.
                working.forEachIndexed { index, item ->
                    val dragging = index == dragIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ReorderRowHeight)
                            // The dragged row rides above its neighbours.
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            label(item),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.DragHandle,
                            contentDescription = "Reorder ${label(item)}",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(28.dp)
                                // Keyed on Unit so a swap mid-drag never
                                // restarts the gesture: slot `index` is fixed
                                // for the life of the row, only the item in it
                                // moves. `dragIndex` is the live position.
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
                                            // Keep the offset relative to the
                                            // row's new home, or the item
                                            // would jump a full row.
                                            dragOffset -= (to - from) * rowPx
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A "Reorder…" row that opens a [ReorderDialog]. Disabled with a nudge when
 * there is nothing to reorder yet.
 */
@Composable
internal fun <T> ReorderSetting(
    title: String,
    dialogTitle: String,
    items: List<T>,
    label: (T) -> String,
    onReordered: (List<T>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val enabled = items.size > 1
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (enabled) {
                    items.joinToString(" · ", limit = 4) { label(it) }
                } else {
                    "Pick at least two above to set an order"
                },
            )
        },
        trailingContent = { Icon(Icons.Outlined.DragHandle, contentDescription = null) },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { open = true },
    )
    if (open) {
        ReorderDialog(
            title = dialogTitle,
            items = items,
            label = label,
            onConfirm = {
                open = false
                onReordered(it)
            },
            onDismiss = { open = false },
        )
    }
}

/** A wrapping row of tool chips, used for a mode's pins and toolbox order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolChips(
    tools: List<ToolbarTool>,
    selected: List<ToolbarTool>,
    onToggle: (ToolbarTool) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (tool in tools) {
            FilterChip(
                selected = tool in selected,
                onClick = { onToggle(tool) },
                label = { Text(toolTitle(tool), maxLines = 1) },
            )
        }
    }
}

/** The modes list: tap to edit, plus creating a new mode. */
@Composable
private fun ModesSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    CaptionText(
        "A mode is a bundle of overrides — emoji row, pinned tools, symbol sets — " +
            "that switches on automatically for chosen apps or field types " +
            "(passwords, email boxes…), or manually from the Modes tool on the keyboard.",
    )
    SettingsGroup("Modes") {
        for (mode in settings.keyboardModes) {
            item {
                ListItem(
                    leadingContent = {
                        Icon(ModeIcons.icon(mode.icon), contentDescription = null)
                    },
                    headlineContent = { Text(mode.name) },
                    supportingContent = { Text(modeBindingsSummary(mode)) },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch { repository.deleteKeyboardMode(mode.id) }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete mode")
                        }
                    },
                    colors = transparentListColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("mode_edit/${mode.id}") },
                )
            }
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text("New mode") },
                colors = transparentListColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("mode_edit/mode_custom_${System.currentTimeMillis()}") },
            )
        }
    }
    SettingsGroup("Rearranging tools") {
        item {
            ToggleSetting(
                "Drags edit the active mode",
                "Hold-and-drag on the keyboard saves into the mode that is on",
                settings.modeToolOrderEdits,
                info = "A mode that prescribes its own pinned tools or toolbox order wins " +
                    "over the global lists while it is active. With this on, rearranging " +
                    "tools on the keyboard is written back into that mode, so the change " +
                    "sticks where you made it — and apps on a different mode keep their own " +
                    "arrangement. With it off, drags always edit the global order and the " +
                    "mode's list keeps overriding it.",
            ) { scope.launch { repository.setModeToolOrderEdits(it) } }
        }
    }
    CaptionText(
        "Tool order can differ per app: whichever mode an app resolves to decides " +
            "the arrangement you see there.",
    )
}

/** Everything one mode overrides, and when it activates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeEditor(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    modeId: String,
    onDeleted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val mode = settings.keyboardModes.firstOrNull { it.id == modeId }
        ?: KeyboardMode(modeId, "New mode")
    // A brand-new mode is only persisted on its first edit — backing out of
    // an untouched editor leaves nothing behind.
    val save: (KeyboardMode) -> Unit = { scope.launch { repository.upsertKeyboardMode(it) } }
    // Only the shipped modes can be reset — a user-made mode has no default to
    // fall back to. Matched by id so an edited built-in still offers it.
    val builtInDefault = DefaultKeyboardModes.firstOrNull { it.id == modeId }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsGroup {
        item {
            TextFieldSetting(
                label = "Name",
                value = mode.name,
                hint = "Shown in the Modes tool",
            ) { repository.upsertKeyboardMode(mode.copy(name = it.trim().ifEmpty { "Mode" })) }
        }
        item {
            var pickerOpen by remember { mutableStateOf(false) }
            ListItem(
                leadingContent = {
                    Icon(ModeIcons.icon(mode.icon), contentDescription = null)
                },
                headlineContent = { Text("Icon") },
                supportingContent = { Text("Shown beside the name in the Modes tool") },
                colors = transparentListColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickerOpen = true },
            )
            if (pickerOpen) {
                ModeIconPickerDialog(
                    selected = mode.icon,
                    onPick = { id ->
                        pickerOpen = false
                        save(mode.copy(icon = id))
                    },
                    onDismiss = { pickerOpen = false },
                )
            }
        }
    }
    SettingsGroup("What this mode changes") {
        item {
            ChoiceSetting(
                title = "Emoji row",
                subtitle = "While this mode is active",
                options = listOf(
                    null to "Inherit",
                    EmojiBarMode.OFF to "Off",
                    EmojiBarMode.BUTTON to "Button",
                    EmojiBarMode.ALWAYS to "Row",
                ),
                selected = mode.emojiBarMode,
            ) { save(mode.copy(emojiBarMode = it)) }
        }
        item {
            ChoiceSetting(
                title = "Symbol row",
                options = listOf(
                    null to "Inherit",
                    true to "On",
                    false to "Off",
                ),
                selected = mode.symbolRowEnabled,
            ) { save(mode.copy(symbolRowEnabled = it)) }
        }
        item {
            var themePickerOpen by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Text(
                        mode.themeId?.let { themeDisplayName(settings, it) }
                            ?: "Inherit — whatever theme is set",
                    )
                },
                trailingContent = {
                    if (mode.themeId != null) {
                        TextButton(onClick = { save(mode.copy(themeId = null)) }) { Text("Clear") }
                    }
                },
                colors = transparentListColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { themePickerOpen = true },
            )
            if (themePickerOpen) {
                ModeThemePickerDialog(
                    settings = settings,
                    selectedId = mode.themeId,
                    onPick = { id ->
                        themePickerOpen = false
                        save(mode.copy(themeId = id))
                    },
                    onDismiss = { themePickerOpen = false },
                )
            }
        }
        if (mode.themeId != null) {
            item {
                CaptionText(
                    "While this mode is active the keyboard wears this theme, whatever is " +
                        "set elsewhere — including an auto light/dark pair, which stands " +
                        "down for the mode's lifetime. Your own choice is untouched and " +
                        "comes straight back when the mode ends.",
                )
            }
        }
        item {
            ToggleSetting(
                "Custom pinned tools",
                "Change the toolbar's pinned tools while active",
                mode.toolbarTools != null,
            ) { on ->
                save(
                    mode.copy(
                        // Appending starts from nothing (the user's own pins
                        // are already there); replacing starts from a copy of
                        // the current toolbar to edit down.
                        toolbarTools = if (on) {
                            if (mode.toolbarToolsAppend) emptyList() else settings.toolbarTools
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        val pinned = mode.toolbarTools
        if (pinned != null) {
            item {
                ChoiceSetting(
                    title = "Pinned tools behaviour",
                    subtitle = if (mode.toolbarToolsAppend) {
                        "Added after the tools you pinned yourself"
                    } else {
                        "Only these tools are pinned while active"
                    },
                    options = listOf(true to "Add to mine", false to "Replace mine"),
                    selected = mode.toolbarToolsAppend,
                ) { append ->
                    // Switching to append: the copied-in global pins would
                    // duplicate what is already on the toolbar, so drop them.
                    save(
                        mode.copy(
                            toolbarToolsAppend = append,
                            toolbarTools = if (append) pinned - settings.toolbarTools.toSet() else pinned,
                        ),
                    )
                }
            }
            item {
                ToolChips(
                    tools = ToolbarTool.entries
                        .filter { it in settings.enabledTools && isSupportedTool(it) },
                    selected = pinned,
                ) { tool ->
                    save(
                        mode.copy(
                            toolbarTools = if (tool in pinned) pinned - tool else pinned + tool,
                        ),
                    )
                }
            }
            item {
                ReorderSetting(
                    title = "Modify pinned tool order",
                    dialogTitle = "Pinned tool order",
                    items = pinned,
                    label = ::toolTitle,
                ) { save(mode.copy(toolbarTools = it)) }
            }
        }
        item {
            ToggleSetting(
                "Custom toolbox order",
                "Float this mode's tools to the front of the toolbox panel",
                mode.toolboxOrder != null,
            ) { on ->
                save(mode.copy(toolboxOrder = if (on) emptyList() else null))
            }
        }
        val order = mode.toolboxOrder
        if (order != null) {
            item {
                ToolChips(
                    tools = settings.toolboxOrder.filter {
                        it in settings.enabledTools && isSupportedTool(it)
                    },
                    selected = order,
                ) { tool ->
                    save(
                        mode.copy(
                            toolboxOrder = if (tool in order) order - tool else order + tool,
                        ),
                    )
                }
            }
            item {
                ReorderSetting(
                    title = "Modify toolbox order",
                    dialogTitle = "Toolbox order",
                    items = order,
                    label = ::toolTitle,
                ) { save(mode.copy(toolboxOrder = it)) }
            }
            item {
                CaptionText(
                    "Picked tools lead the toolbox; everything else keeps its " +
                        "usual place behind them.",
                )
            }
        }
        item {
            ToggleSetting(
                "Custom symbol sets",
                "The symbol row offers only this mode's sets while active",
                mode.symbolSetIds != null,
            ) { on ->
                save(
                    mode.copy(
                        symbolSetIds = if (on) {
                            settings.symbolRowSetIds.ifEmpty { BuiltInSymbolSets.defaultEnabledIds }
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        val modeSets = mode.symbolSetIds
        if (modeSets != null) {
            item {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (set in resolveSymbolSets(settings.customSymbolSets)) {
                        FilterChip(
                            selected = set.id in modeSets,
                            onClick = {
                                val next =
                                    if (set.id in modeSets) modeSets - set.id else modeSets + set.id
                                if (next.isNotEmpty()) save(mode.copy(symbolSetIds = next))
                            },
                            label = { Text(set.name, maxLines = 1) },
                        )
                    }
                }
            }
            item {
                val allSets = resolveSymbolSets(settings.customSymbolSets)
                val setName = { id: String ->
                    allSets.firstOrNull { it.id == id }?.name ?: id
                }
                ReorderSetting(
                    title = "Modify symbol set order",
                    dialogTitle = "Symbol set order",
                    items = modeSets,
                    label = setName,
                ) { save(mode.copy(symbolSetIds = it)) }
            }
            item {
                CaptionText("The first set in the order is what the row opens on.")
            }
        }
    }
    SettingsGroup("Activate automatically for") {
        item {
            Text(
                "Field types",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (field in ModeField.entries) {
                    FilterChip(
                        selected = field in mode.fieldKinds,
                        onClick = {
                            save(
                                mode.copy(
                                    fieldKinds =
                                        if (field in mode.fieldKinds) mode.fieldKinds - field
                                        else mode.fieldKinds + field,
                                ),
                            )
                        },
                        label = { Text(modeFieldLabel(field), maxLines = 1) },
                    )
                }
            }
            if (mode.apps.isNotEmpty() && mode.fieldKinds.isNotEmpty()) {
                CaptionText(
                    "Both have to match: this mode switches on only for these " +
                        "field types inside the apps listed below.",
                )
            }
        }
        for (pkg in mode.apps) {
            item {
                val context = LocalContext.current
                val label = remember(pkg) {
                    runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0),
                        ).toString()
                    }.getOrDefault(pkg)
                }
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = if (label != pkg) {
                        { Text(pkg) }
                    } else null,
                    trailingContent = {
                        IconButton(onClick = { save(mode.copy(apps = mode.apps - pkg)) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove app")
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
        item {
            var pickerOpen by remember { mutableStateOf(false) }
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text("Add app") },
                supportingContent = {
                    Text(
                        if (mode.fieldKinds.isEmpty()) {
                            "This mode switches on when the app's fields are focused"
                        } else {
                            "Plus one of the field types above"
                        },
                    )
                },
                colors = transparentListColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickerOpen = true },
            )
            if (pickerOpen) {
                AppPickerDialog(
                    exclude = mode.apps,
                    onPick = { pkg ->
                        pickerOpen = false
                        save(mode.copy(apps = mode.apps + pkg))
                    },
                    onDismiss = { pickerOpen = false },
                )
            }
        }
    }
    CaptionText(
        "Setting apps and field types together means both must match — bind a mode " +
            "to your chat apps and the Text field type and it stays off their search " +
            "boxes. Setting only one matches on that alone, and setting neither makes " +
            "the mode manual-only. When several modes match, one that names field " +
            "types beats one that only names apps — a password box in a code editor " +
            "still gets the password mode. A mode picked by hand from the Modes tool " +
            "wins until you switch apps.",
    )
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (builtInDefault != null) {
            TextButton(onClick = { confirmReset = true }) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Reset to default")
            }
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = {
            scope.launch { repository.deleteKeyboardMode(modeId) }
            onDeleted()
        }) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Delete mode")
        }
    }
    if (confirmReset && builtInDefault != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ${builtInDefault.name} to default?") },
            text = {
                Text(
                    "This built-in mode goes back to the pinned tools, toolbox order, " +
                        "symbol row, apps and field types it ships with. Your edits to it " +
                        "are discarded.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { repository.resetKeyboardModeToDefault(modeId) }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Picks a mode's icon from [ModeIcons.catalog]. Chips rather than a grid of
 * bare icons: the selected state comes styled and the touch targets land on
 * the same size the rest of the settings use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeIconPickerDialog(
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode icon") },
        text = {
            FlowRow(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for ((id, vector) in ModeIcons.catalog) {
                    FilterChip(
                        selected = id == selected,
                        onClick = { onPick(id) },
                        label = {
                            Icon(vector, contentDescription = id, modifier = Modifier.size(22.dp))
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks one installed app (launcher activities) for a mode binding. */
@Composable
private fun AppPickerDialog(
    exclude: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    val shown = apps.filter { (pkg, label) ->
        pkg !in exclude &&
            (query.isBlank() || label.contains(query, ignoreCase = true) ||
                pkg.contains(query, ignoreCase = true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.first }) { (pkg, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(pkg) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(pkg) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
