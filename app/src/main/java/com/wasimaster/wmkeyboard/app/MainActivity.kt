package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.OpenableColumns
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.os.Build
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.handwriting.HandwritingModels
import com.wasimaster.wmkeyboard.core.settings.EmojiBarContent
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import com.wasimaster.wmkeyboard.core.settings.EmojiInsertMode
import com.wasimaster.wmkeyboard.core.settings.EmojiTabMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import com.wasimaster.wmkeyboard.core.settings.GifContentFilter
import com.wasimaster.wmkeyboard.core.settings.GifSourceMode
import com.wasimaster.wmkeyboard.core.tools.GeoPlace
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.core.tools.TranslateClient
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import kotlin.math.roundToInt
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings app: setup wizard plus every keyboard option, Material 3 +
 * dynamic color, all state backed by DataStore via [SettingsRepository].
 */
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Intent extra with a [ToolbarTool] name: the keyboard's "needs an
         * API key" panels use it to jump straight to that tool's settings.
         */
        const val EXTRA_OPEN_TOOL = "open_tool"
    }

    private lateinit var repository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(applicationContext)
        val openTool = intent?.getStringExtra(EXTRA_OPEN_TOOL)
            ?.let { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
        setContent {
            // Null until DataStore's first emission: rendering nothing for a
            // frame beats flashing onboarding at users who finished it.
            val settings by repository.settings
                .collectAsStateWithLifecycle(null as KeyboardSettings?)
            settings?.let { loaded ->
                AppTheme(loaded) {
                    SettingsNavHost(repository, loaded, openTool)
                }
            }
        }
    }
}

@Composable
private fun AppTheme(settings: KeyboardSettings, content: @Composable () -> Unit) {
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
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SettingsNavHost(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    openTool: ToolbarTool? = null,
) {
    val navController = rememberNavController()
    // Launched from a keyboard panel's "Open settings": go straight to the
    // tool's page (with Tools beneath it so back behaves normally).
    LaunchedEffect(openTool) {
        if (openTool != null && settings.onboardingDone) {
            navController.navigate("tools")
            navController.navigate("tool/${openTool.name}")
        }
    }
    // Quick shared-axis slide instead of the sluggish default cross-fade.
    val spec = tween<androidx.compose.ui.unit.IntOffset>(220)
    val fadeSpec = tween<Float>(220)
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
                onNavigate = { navController.navigate(it) },
            )
        }
        composable("typing") {
            SettingsScreen("Typing", { navController.popBackStack() }) {
                TypingSettings(repository, settings) { navController.navigate("dictionary") }
            }
        }
        composable("dictionary") {
            SettingsScreen("Personal dictionary", { navController.popBackStack() }) {
                DictionarySettings(repository)
            }
        }
        composable("appearance") {
            SettingsScreen("Appearance & themes", { navController.popBackStack() }) {
                AppearanceSettings(
                    repository, settings,
                    onOpenThemes = { navController.navigate("themes") },
                    onOpenFonts = { navController.navigate("fonts") },
                )
            }
        }
        composable("fonts") {
            SettingsScreen("Keyboard font", { navController.popBackStack() }) {
                FontSettings(repository, settings)
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
        composable("languages") {
            SettingsScreen("Languages", { navController.popBackStack() }) {
                LanguageSettings(repository, settings)
            }
        }
        composable("emoji") {
            SettingsScreen("Emoji", { navController.popBackStack() }) {
                EmojiSettings(repository, settings)
            }
        }
        composable("tools") {
            SettingsScreen("Tools", { navController.popBackStack() }) {
                ToolsSettings(settings) { tool -> navController.navigate("tool/${tool.name}") }
            }
        }
        composable("tool/{toolName}") { backStackEntry ->
            val tool = backStackEntry.arguments?.getString("toolName")
                ?.let { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            if (tool != null) {
                SettingsScreen(toolTitle(tool), { navController.popBackStack() }) {
                    ToolDetailSettings(repository, settings, tool) {
                        navController.navigate("emoji")
                    }
                }
            }
        }
        composable("privacy") {
            SettingsScreen("Privacy", { navController.popBackStack() }) {
                PrivacySettings(repository, settings)
            }
        }
    }
}

// ---- home / setup ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(settings: KeyboardSettings, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("WM Keyboard") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SetupCard(context)
            Spacer(Modifier.height(16.dp))
            SectionItem(Icons.Outlined.Keyboard, "Typing", "Autocorrect, suggestions, key behavior") { onNavigate("typing") }
            SectionItem(Icons.Outlined.Palette, "Appearance & themes", "Material You, fonts, key size, split & resize") { onNavigate("appearance") }
            SectionItem(Icons.Outlined.Language, "Languages", "English, বাংলা (Avro phonetic, প্রভাত)") { onNavigate("languages") }
            SectionItem(Icons.Outlined.EmojiEmotions, "Emoji", "Suggestions, emoji font, emoji row, favourites") { onNavigate("emoji") }
            SectionItem(Icons.Outlined.Widgets, "Tools", "Flashlight, compass, snippets, calendar & more") { onNavigate("tools") }
            SectionItem(Icons.Outlined.Security, "Privacy", "On-device learning, incognito") { onNavigate("privacy") }
        }
    }
}

@Composable
internal fun SetupCard(context: Context) {
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
private fun SectionItem(
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
    HorizontalDivider()
}

// ---- shared scaffold ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // No horizontal padding here: ListItem pads its own content 16dp,
        // and the non-ListItem rows (sliders, headers) match it explicitly,
        // so every setting aligns to the same left edge.
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

/** "?" affordance that opens a dialog with the full explanation of a setting. */
@Composable
private fun InfoButton(title: String, detail: String) {
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleSetting(
    title: String,
    subtitle: String?,
    checked: Boolean,
    info: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info != null) InfoButton(title, info)
                Switch(checked = checked, onCheckedChange = onChange)
            }
        },
    )
}

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    info: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (info != null) InfoButton(title, info)
            Spacer(Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** A titled single-choice row of segmented buttons over [options]. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    subtitle: String? = null,
    info: String? = null,
    options: List<Pair<T, String>>,
    selected: T,
    onChange: (T) -> Unit,
) {
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

/** One spacebar-swipe slot (quick or hold+swipe): nothing / language / cursor. */
@Composable
private fun SpaceSwipeSetting(
    title: String,
    subtitle: String,
    info: String,
    value: SpaceSwipeAction,
    onChange: (SpaceSwipeAction) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            InfoButton(title, info)
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)) {
            SpaceSwipeAction.entries.forEachIndexed { index, action ->
                SegmentedButton(
                    selected = value == action,
                    onClick = { onChange(action) },
                    shape = SegmentedButtonDefaults.itemShape(index, SpaceSwipeAction.entries.size),
                ) {
                    Text(
                        when (action) {
                            SpaceSwipeAction.NONE -> "Nothing"
                            SpaceSwipeAction.LANGUAGE -> "Language"
                            SpaceSwipeAction.CURSOR -> "Cursor"
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---- sections ----

@Composable
private fun TypingSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenDictionary: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SectionHeader("Corrections")
    ToggleSetting(
        "Autocorrect", "Fix typos automatically when you press space", settings.autocorrect,
        info = "When you press space, the word you just typed is checked against the " +
            "dictionary. If it looks like a slip of an obviously more common word, it is " +
            "replaced. Words you have taught the keyboard are never \"corrected\" away, " +
            "and autocorrect stays off in password fields.",
    ) { scope.launch { repository.setAutocorrect(it) } }
    ToggleSetting(
        "Fix missing apostrophes", "arent → aren't, im → I'm, dont → don't",
        settings.autoApostrophe,
        info = "When you press space after a contraction typed without its " +
            "apostrophe (arent, isnt, youre, oclock…), the apostrophe is put " +
            "back — and a lone \"i\" becomes \"I\". Words that are also real " +
            "English words without the apostrophe (its, well, ill, shell…) are " +
            "deliberately left alone. Works independently of autocorrect.",
    ) { scope.launch { repository.setAutoApostrophe(it) } }
    ListItem(
        headlineContent = { Text("Personal dictionary") },
        supportingContent = { Text("Words the keyboard has learned — review, remove, add your own") },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDictionary),
    )
    ToggleSetting(
        "Suggestions", "Show word predictions above the keyboard", settings.suggestions,
        info = "Shows up to three candidates above the keys while you type: completions, " +
            "corrections, and next-word predictions learned from your typing. Tap one to " +
            "insert it followed by a space.",
    ) { scope.launch { repository.setSuggestions(it) } }
    ToggleSetting(
        "Auto-capitalize", "Capitalize the first letter of sentences", settings.autoCapitalize,
        info = "Shift turns on by itself at the start of a text field and after " +
            "sentence-ending punctuation (. ! ? ।). It only applies in fields that ask " +
            "for sentence capitalization, and only in English mode. Caps lock is never " +
            "changed automatically.",
    ) { scope.launch { repository.setAutoCapitalize(it) } }
    ToggleSetting(
        "Double-space period", "Double-tapping space inserts “. ”", settings.doubleSpacePeriod,
        info = "Tapping space twice quickly at the end of a word replaces the first " +
            "space with a period, so you can end sentences without visiting the symbols " +
            "layout.",
    ) { scope.launch { repository.setDoubleSpacePeriod(it) } }

    SectionHeader("Gestures")
    ToggleSetting(
        "Gesture typing", "Swipe across letters to type a word", settings.gestureTyping,
        info = "Slide your finger from letter to letter without lifting; the word is " +
            "committed when you lift. Alternate interpretations appear in the suggestion " +
            "bar, so a wrong guess is one tap away from fixed. English only for now.",
    ) { scope.launch { repository.setGestureTyping(it) } }
    SpaceSwipeSetting(
        title = "Quick swipe on spacebar",
        subtitle = "A swipe that starts moving right away",
        info = "Slide horizontally on the spacebar without pausing. \"Language\" cycles " +
            "your enabled input modes with a live preview above the spacebar — release " +
            "to switch. \"Cursor\" moves the text cursor one character per step. A tap " +
            "without movement always types a space.",
        value = settings.spaceShortSwipe,
    ) { scope.launch { repository.setSpaceShortSwipe(it) } }
    SpaceSwipeSetting(
        title = "Hold + swipe on spacebar",
        subtitle = "Hold the spacebar briefly, then swipe",
        info = "Hold the spacebar past the long-press delay first, then slide. This " +
            "gives the spacebar a second, independent swipe action — for example a " +
            "quick swipe to switch language and a hold + swipe to move the cursor. " +
            "Both may also be set to the same action.",
        value = settings.spaceLongSwipe,
    ) { scope.launch { repository.setSpaceLongSwipe(it) } }

    SectionHeader("Layout")
    ToggleSetting(
        "Number row", "Show a dedicated digit row above the letters", settings.numberRow,
        info = "Adds a 1–0 row on top of the letter layout so you never long-press for " +
            "digits. The digits normally on the top letter row's long press are dropped " +
            "while this is on. Costs one extra row of height.",
    ) { scope.launch { repository.setNumberRow(it) } }

    SectionHeader("Feedback")
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Haptic style", style = MaterialTheme.typography.bodyLarge)
        InfoButton(
            "Haptic style",
            "Click and Heavy click use the device's hardware-tuned haptic effects " +
                "(Android 10+). Sharp plays the hardware click primitive (Android 11+) — " +
                "a short, hard thump whose strength follows the intensity slider; " +
                "this is how stock keyboards get a powerful buzz that stays crisp. " +
                "Custom drives the vibration motor directly using the duration and " +
                "intensity sliders — without the hardware's overdrive and braking it " +
                "feels softer. On devices without these effects, styles fall back to " +
                "Click, then Custom.",
        )
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        HapticStyle.entries.forEachIndexed { index, style ->
            SegmentedButton(
                selected = settings.hapticStyle == style,
                onClick = {
                    scope.launch { repository.setHapticStyle(style) }
                    // Fire the motor with the freshly picked style so the
                    // user feels the choice immediately.
                    HapticPlayer.preview(
                        context, style, settings.hapticAmplitude, settings.hapticStrengthMs,
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index, HapticStyle.entries.size),
            ) {
                Text(
                    when (style) {
                        HapticStyle.CUSTOM -> "Custom"
                        HapticStyle.CLICK -> "Click"
                        HapticStyle.HEAVY_CLICK -> "Heavy"
                        HapticStyle.SHARP -> "Sharp"
                    },
                    maxLines = 1,
                )
            }
        }
    }
    if (settings.hapticStyle == HapticStyle.CUSTOM) {
        SliderSetting(
            "Haptic strength",
            subtitle = "Vibration length per key press",
            value = settings.hapticStrengthMs.toFloat(),
            range = 5f..60f,
            display = "${settings.hapticStrengthMs} ms",
            info = "Duration of the vibration pulse in milliseconds. Longer pulses feel " +
                "stronger on most phones.",
        ) {
            scope.launch { repository.setHapticStrengthMs(it.toInt()) }
            // Debounced inside the player, so dragging previews smoothly.
            HapticPlayer.preview(context, settings.hapticStyle, settings.hapticAmplitude, it.toInt())
        }
    }
    if (settings.hapticStyle == HapticStyle.CUSTOM || settings.hapticStyle == HapticStyle.SHARP) {
        SliderSetting(
            "Haptic intensity",
            subtitle = "Vibration amplitude per key press",
            value = settings.hapticAmplitude.toFloat(),
            range = 1f..255f,
            display = "${settings.hapticAmplitude * 100 / 255}%",
            info = "How hard the vibration motor is driven (1–255). For Sharp this scales " +
                "the hardware click primitive; the length stays fixed, only the punch " +
                "changes. For Custom it only takes effect on devices whose vibrator " +
                "supports amplitude control; on others only the duration above matters. " +
                "The system-wide \"Touch feedback\" vibration setting still scales the " +
                "final strength on top of this.",
        ) {
            scope.launch { repository.setHapticAmplitude(it.toInt()) }
            HapticPlayer.preview(context, settings.hapticStyle, it.toInt(), settings.hapticStrengthMs)
        }
    }
    ToggleSetting(
        "Long-press haptics", "Vibrate when a long press registers", settings.hapticOnLongPress,
        info = "A second buzz the moment a long press kicks in — when the alternate-" +
            "character popup opens, or a long-press action fires — telling your finger " +
            "it can let go. Delete and space are unaffected; their key-repeat already " +
            "vibrates on every repeat.",
    ) { scope.launch { repository.setHapticOnLongPress(it) } }
    ToggleSetting(
        "Long-press release haptics", "Vibrate on release after a long press",
        settings.hapticOnLongPressRelease,
        info = "An extra buzz when you lift your finger at the end of a long press, " +
            "closing the press-hold-release loop. Off by default; some find the third " +
            "vibration excessive.",
    ) { scope.launch { repository.setHapticOnLongPressRelease(it) } }
    ToggleSetting(
        "Key popup", "Show a character bubble above the pressed key", settings.keyPopup,
        info = "While a key is held, its character floats in a bubble above your finger " +
            "so you can see what you hit.",
    ) { scope.launch { repository.setKeyPopup(it) } }
    if (settings.keyPopup) {
        SliderSetting(
            "Minimum popup duration",
            subtitle = "How long the bubble stays up even on a fast tap",
            value = settings.keyPopupMinDurationMs.toFloat(),
            range = 0f..300f,
            display = "${settings.keyPopupMinDurationMs} ms",
            info = "On a quick tap the key is released almost instantly, which can make " +
                "the bubble a barely-visible flicker. The bubble lingers after release " +
                "until it has been shown for at least this long. 0 hides it the moment " +
                "you let go.",
        ) { scope.launch { repository.setKeyPopupMinDurationMs(it.toInt()) } }
    }

    SectionHeader("Timing")
    SliderSetting(
        "Long-press delay",
        subtitle = "Hold time before alternate characters appear",
        value = settings.longPressDelayMs.toFloat(),
        range = 150f..700f,
        display = "${settings.longPressDelayMs} ms",
        info = "How long a key must be held before its long-press alternates (accents, " +
            "digits, symbols) pop up. Lower is faster but easier to trigger by accident.",
    ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
    SliderSetting(
        "Key repeat interval",
        subtitle = "Speed of repeated delete while held",
        value = settings.keyRepeatIntervalMs.toFloat(),
        range = 20f..200f,
        display = "${settings.keyRepeatIntervalMs} ms",
        info = "While delete (or space) is held it repeats at this interval. Lower " +
            "values delete faster.",
    ) { scope.launch { repository.setKeyRepeatIntervalMs(it.toInt()) } }

    SectionHeader("Long press")
    ToggleSetting(
        "Long-press hints", "Show each key's long-press character in its corner",
        settings.longPressHints,
        info = "A small label in the top-right corner of each key previews its first " +
            "long-press character — the digit, symbol or accent the popup leads with. " +
            "Keys running a clipboard shortcut below show no hint.",
    ) { scope.launch { repository.setLongPressHints(it) } }
    ToggleSetting(
        "Hold A to select all", "Long-pressing A selects all text",
        settings.longPressASelectAll,
        info = "Replaces the A key's accent popup with a select-all shortcut. Turn it " +
            "off to get the accents (à á â ä å) back.",
    ) { scope.launch { repository.setLongPressASelectAll(it) } }
    ToggleSetting(
        "Hold C to copy", "Copies the selection, or everything if nothing is selected",
        settings.longPressCCopy,
        info = "With text selected, a long press on C copies just that selection. With " +
            "no selection it selects all first, so one hold copies the whole field. " +
            "Replaces the C key's accent popup (ç ć) while enabled.",
    ) { scope.launch { repository.setLongPressCCopy(it) } }
    ToggleSetting(
        "Hold X to cut", "Cuts the selection, or everything if nothing is selected",
        settings.longPressXCut,
        info = "With text selected, a long press on X cuts just that selection. With " +
            "no selection it selects all first, so one hold cuts the whole field.",
    ) { scope.launch { repository.setLongPressXCut(it) } }
    ToggleSetting(
        "Hold V to paste", "Long-pressing V pastes the clipboard",
        settings.longPressVPaste,
        info = "Pastes the current clipboard content at the cursor, replacing any " +
            "selection — the classic Ctrl+V, one hold away.",
    ) { scope.launch { repository.setLongPressVPaste(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenThemes: () -> Unit,
    onOpenFonts: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    SectionHeader("Theme")
    ListItem(
        headlineContent = { Text("Keyboard themes") },
        supportingContent = {
            Text("Light/dark/AMOLED, color themes, custom colors & background images, import/export")
        },
        trailingContent = {
            val selected = settings.customThemes.find { it.id == settings.keyboardThemeId }
                ?: com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
                    .find { it.id == settings.keyboardThemeId }
            Text(selected?.name ?: "Default")
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenThemes),
    )
    HorizontalDivider()

    SectionHeader("Font")
    ListItem(
        headlineContent = { Text("Keyboard font") },
        supportingContent = { Text("Google Fonts, or import your own font file") },
        trailingContent = {
            Text(KeyboardFonts.displayName(settings.keyFontId, settings.customFontName))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFonts),
    )
    HorizontalDivider()

    SectionHeader("One-handed mode")
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        OneHandedMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.oneHandedMode == mode,
                onClick = { scope.launch { repository.setOneHandedMode(mode) } },
                shape = SegmentedButtonDefaults.itemShape(index, OneHandedMode.entries.size),
            ) {
                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }

    SectionHeader("Size & shape")
    SliderSetting(
        "Key height",
        subtitle = "Height of each key row — sets the overall input height",
        value = settings.keyHeightDp.toFloat(),
        range = 32f..100f,
        display = "${settings.keyHeightDp} dp",
        info = "Taller keys are easier to hit but the keyboard covers more of the " +
            "screen. The emoji, clipboard and snippet panels scale with this value too.",
    ) { scope.launch { repository.setKeyHeightDp(it.toInt()) } }
    SliderSetting(
        "Number row height",
        subtitle = "Height of the digit row, independent of the letter keys",
        value = settings.numberRowHeightDp.toFloat(),
        range = 32f..100f,
        display = "${settings.numberRowHeightDp} dp",
        info = "Only applies while the number row (Typing → Layout) is enabled. A " +
            "shorter digit row keeps quick number access without costing a full row " +
            "of extra keyboard height.",
    ) { scope.launch { repository.setNumberRowHeightDp(it.toInt()) } }
    SliderSetting(
        "Bottom padding",
        subtitle = "Extra space below the keys, above the navigation bar",
        value = settings.bottomPaddingDp.toFloat(),
        range = 0f..40f,
        display = "${settings.bottomPaddingDp} dp",
        info = "Raises the whole keyboard away from the bottom edge and the gesture " +
            "navigation bar. Increase it if the bottom row feels cramped against the " +
            "edge of the screen or you keep triggering system navigation.",
    ) { scope.launch { repository.setBottomPaddingDp(it.toInt()) } }
    SliderSetting(
        "Key corner radius",
        subtitle = "Roundness of the key corners",
        value = settings.keyCornerRadiusDp.toFloat(),
        range = 0f..28f,
        display = "${settings.keyCornerRadiusDp} dp",
        info = "0 gives square keys, 28 gives fully pill-shaped keys.",
    ) { scope.launch { repository.setKeyCornerRadiusDp(it.toInt()) } }
    SliderSetting(
        "Key label font size",
        subtitle = "Scale of the labels printed on the keys",
        value = settings.fontScale,
        range = 0.7f..1.5f,
        display = "×%.2f".format(settings.fontScale),
        info = "Multiplies the size of every label on the keys themselves. Popup " +
            "bubbles have their own font size below.",
    ) { scope.launch { repository.setFontScale(it) } }

    SectionHeader("Split & resize")
    ToggleSetting(
        "Split keyboard", "Divide the keys into left and right halves", settings.splitKeyboard,
        info = "Splits every row down the middle with a gap between the halves, so " +
            "your thumbs travel less on wide screens — most useful on tablets, " +
            "foldables and phones in landscape. The spacebar is divided between the " +
            "two halves.",
    ) { scope.launch { repository.setSplitKeyboard(it) } }
    if (settings.splitKeyboard) {
        SliderSetting(
            "Split gap",
            subtitle = "Width of the gap between the halves",
            value = settings.splitGapPercent.toFloat(),
            range = 5f..40f,
            display = "${settings.splitGapPercent}%",
            info = "The center gap, as a percentage of the keyboard width. Bigger gaps " +
                "push the halves further toward the edges but make each key narrower.",
        ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
    }
    ToggleSetting(
        "Floating keyboard", "Detach the keyboard into a movable panel", settings.floatingKeyboard,
        info = "The keyboard becomes a compact floating panel that hovers over apps " +
            "instead of docking to the bottom of the screen. Drag the pill at the top " +
            "of the panel to move it, drag the corner grip to resize it, and tap the " +
            "dock button to return to the normal keyboard. Apps are no longer resized " +
            "while it floats, and touches outside the panel go straight to the app.",
    ) { scope.launch { repository.setFloatingKeyboard(it) } }
    if (settings.floatingKeyboard) {
        SliderSetting(
            "Floating keyboard width",
            subtitle = "Also adjustable by dragging the panel's corner grip",
            value = settings.floatingWidthDp.toFloat(),
            range = 240f..500f,
            display = "${settings.floatingWidthDp} dp",
            info = "Width of the floating panel. Key heights still follow the sliders " +
                "above.",
        ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
    }
    SliderSetting(
        "Keyboard width",
        subtitle = "Shrink the keyboard horizontally",
        value = settings.keyboardWidthPercent.toFloat(),
        range = 50f..100f,
        display = "${settings.keyboardWidthPercent}%",
        info = "Below 100% the keyboard no longer spans the whole screen; choose which " +
            "edge it sits at below. Handy on very wide screens. One-handed mode " +
            "(above) is a quick preset that overrides this while active.",
    ) { scope.launch { repository.setKeyboardWidthPercent(it.toInt()) } }
    if (settings.keyboardWidthPercent < 100) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keyboard position", style = MaterialTheme.typography.bodyLarge)
            InfoButton(
                "Keyboard position",
                "Where the narrowed keyboard sits: hugging the left edge, centered, " +
                    "or hugging the right edge.",
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            KeyboardAlignment.entries.forEachIndexed { index, alignment ->
                SegmentedButton(
                    selected = settings.keyboardAlignment == alignment,
                    onClick = { scope.launch { repository.setKeyboardAlignment(alignment) } },
                    shape = SegmentedButtonDefaults.itemShape(index, KeyboardAlignment.entries.size),
                ) {
                    Text(alignment.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    SectionHeader("Toolbar")
    ToggleSetting(
        "Spread tools across the bar",
        "Toolbar tools split the available width evenly",
        settings.toolbarGreedy,
        info = "On: the tools on the top toolbar greedily share the whole bar, like " +
            "the suggestion candidates do. Off: they pack to the left at a fixed " +
            "size. Which tools appear there is customized from the keyboard itself: " +
            "open the toolbox (grid button on the toolbar), then hold and drag tools " +
            "between the toolbar and the toolbox.",
    ) { scope.launch { repository.setToolbarGreedy(it) } }
    SliderSetting(
        "Tool circle radius",
        subtitle = "Roundness of the circle behind each toolbar tool",
        value = settings.toolCircleRadiusDp.toFloat(),
        range = 0f..20f,
        display = if (settings.toolCircleRadiusDp == 0) "off" else "${settings.toolCircleRadiusDp} dp",
        info = "20 draws a full circle behind every tool icon (Gboard style), " +
            "smaller values give rounded squares, and 0 removes the background " +
            "entirely, leaving bare icons.",
    ) { scope.launch { repository.setToolCircleRadiusDp(it.toInt()) } }
    ToggleSetting(
        "Comma key opens emoji",
        "Replace the comma key with an emoji key",
        settings.commaAsEmoji,
        info = "The bottom-row comma key becomes an emoji-panel key; comma moves " +
            "into its long-press alternates. Turning this on also removes the " +
            "emoji tool from the toolbar since the key replaces it — drag it back " +
            "out of the toolbox if you want both.",
    ) { scope.launch { repository.setCommaAsEmoji(it) } }
    ToggleSetting(
        "Emoji key instead of 🌐",
        "Replace the language key with an emoji key",
        settings.globeAsEmoji,
        info = "The bottom-row 🌐 key opens the emoji panel instead of switching " +
            "language. Language switching stays available on the spacebar: set a " +
            "swipe to \"Language\" under Typing → Gestures (a quick swipe does it " +
            "by default). Turn this off to get the 🌐 key back.",
    ) { scope.launch { repository.setGlobeAsEmoji(it) } }

    SectionHeader("Popups")
    ToggleSetting(
        "Popup on key",
        "Grow the bubble upward from the pressed key itself",
        settings.keyPopupOnKey,
        info = "On: the preview bubble sits on the pressed key and stretches upward, " +
            "key-wide with a large character near its top — the stock-keyboard look. " +
            "Off: a compact bubble floats above your fingertip with a gap.",
    ) { scope.launch { repository.setKeyPopupOnKey(it) } }
    SliderSetting(
        "Popup font size",
        subtitle = "Scale of the key preview bubble and long-press alternates",
        value = settings.popupFontScale,
        range = 0.7f..1.6f,
        display = "×%.2f".format(settings.popupFontScale),
        info = "Multiplies the text size inside the character bubble shown while a key " +
            "is pressed and in the long-press alternates popup, independently of the " +
            "key label size.",
    ) { scope.launch { repository.setPopupFontScale(it) } }
    SliderSetting(
        "Popup height",
        subtitle = "Height of the key preview bubble",
        value = settings.keyPopupHeightDp.toFloat(),
        range = 32f..160f,
        display = "${settings.keyPopupHeightDp} dp",
        info = "Height of the character bubble. With \"Popup on key\" enabled this is " +
            "measured from the bottom of the pressed key, so anything taller than the " +
            "key extends above it and stays visible past your finger.",
    ) { scope.launch { repository.setKeyPopupHeightDp(it.toInt()) } }
}

@Composable
private fun LanguageSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SectionHeader("Enabled input modes (cycle with the 🌐 key)")
    val labels = mapOf(
        InputMode.ENGLISH to Triple(
            "English", "QWERTY with suggestions",
            "Standard QWERTY layout with autocorrect, predictions and gesture typing.",
        ),
        InputMode.AVRO to Triple(
            "বাংলা — Avro phonetic", "Type \"ami valo achi\", get আমি ভালো আছি",
            "Type Bengali phonetically with Latin letters; the transliteration happens " +
                "live as you type, and the suggestion bar offers dictionary spellings.",
        ),
        InputMode.PROBHAT to Triple(
            "বাংলা — প্রভাত (Probhat)", "Fixed Bengali layout",
            "The fixed Probhat layout familiar from Linux: vowel signs on the home row, " +
                "consonants by frequency, aspirates on shift.",
        ),
        InputMode.JATIYA to Triple(
            "বাংলা — জাতীয় (National)", "Bangladesh standard fixed layout",
            "The National (Jatiya) fixed layout standardized in Bangladesh; the same " +
                "arrangement used by Bijoy-style keyboards, with aspirates on shift and " +
                "independent vowels on long-press.",
        ),
    )
    for (mode in InputMode.entries) {
        val (title, subtitle, info) = labels.getValue(mode)
        ToggleSetting(title, subtitle, mode in settings.enabledModes, info = info) { enable ->
            scope.launch {
                val next = if (enable) settings.enabledModes + mode else settings.enabledModes - mode
                if (next.isNotEmpty()) repository.setEnabledModes(next.distinct())
            }
        }
    }
    SectionHeader("Bengali")
    ToggleSetting(
        "Conjunct-aware backspace",
        "Delete a whole যুক্তবর্ণ (like ক্ষ or স্ত্রী) as one unit",
        settings.conjunctBackspace,
        info = "Normally backspace removes one code point at a time, which can leave " +
            "half-formed conjuncts. With this on, a conjunct cluster like স্ত্রী is " +
            "deleted in a single press.",
    ) {
        scope.launch { repository.setConjunctBackspace(it) }
    }
}

@Composable
private fun EmojiSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    ToggleSetting(
        "Emoji button in toolbar", "One-tap emoji access from the top bar", settings.emojiToolbar,
        info = "Keeps the emoji button visible in the top bar even while suggestions " +
            "are showing. The emoji panel itself has tabs per category, search in " +
            "English and Bengali, and skin-tone variants on long-press.",
    ) { scope.launch { repository.setEmojiToolbar(it) } }
    ToggleSetting(
        "Emoji suggestions",
        "Offer emojis while typing — birthday suggests 🎂 🎉 🥳",
        settings.emojiPrediction,
        info = "Matching emojis appear at the end of the suggestion strip while you " +
            "type, in English or Bengali (জন্মদিন also suggests 🎂).",
    ) { scope.launch { repository.setEmojiPrediction(it) } }
    if (settings.emojiPrediction) {
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
    if (settings.emojiBarMode != EmojiBarMode.OFF) {
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
    SectionHeader("Emoji style")
    val context = LocalContext.current
    // Bumped after an import so the preview re-resolves the (same-named) file.
    var fontRefresh by remember { mutableIntStateOf(0) }
    ChoiceSetting(
        title = "Emoji font",
        subtitle = "How emojis look on the keyboard itself",
        info = "\"System\" uses your phone's emoji pack — on Samsung phones " +
            "that is Samsung's own set. \"Google\" fetches Noto Color Emoji " +
            "(the stock-Android look) once through the system font provider " +
            "and caches it on the device. \"Custom\" uses an emoji font file " +
            "you import below, such as a Twemoji or OpenMoji build. Text you " +
            "send is plain Unicode either way — other apps and other phones " +
            "still draw it with their own emoji font.",
        options = listOf(
            EmojiFontChoice.SYSTEM to "System",
            EmojiFontChoice.NOTO to "Google",
            EmojiFontChoice.CUSTOM to "Custom",
        ),
        selected = settings.emojiFont,
    ) { scope.launch { repository.setEmojiFont(it) } }
    val previewFamily = remember(settings.emojiFont, fontRefresh) {
        KeyboardFonts.emojiFamily(context, settings.emojiFont)
    }
    // One Text per emoji: emoji fonts often have no space glyph, so drawing
    // them as a single spaced string makes the glyphs overlap.
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        for (emoji in listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "❤️", "🎉")) {
            Text(
                emoji,
                fontSize = 24.sp,
                fontFamily = previewFamily,
                maxLines = 1,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
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
    }
    Text(
        "Tip: long-press any emoji in the panel to favourite it or pick skin tones — " +
            "two-person emojis like 🤝 let you set each person's tone separately.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    val lexicon = remember {
        UserLexicon(java.io.File(context.filesDir, "learning/user_lexicon.json"))
    }
    var words by remember { mutableStateOf(lexicon.allWords().sortedByDescending { it.second }) }
    var showAdd by remember { mutableStateOf(false) }

    fun persist() {
        lexicon.save()
        words = lexicon.allWords().sortedByDescending { it.second }
        scope.launch { repository.bumpLexiconVersion() }
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
    Spacer(Modifier.height(8.dp))
    if (words.isEmpty()) {
        Text(
            "Nothing here yet — words appear as you type (with \"Learn from " +
                "typing\" on under Privacy), or add one above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    for ((word, count) in words) {
        ListItem(
            headlineContent = { Text(word) },
            supportingContent = {
                Text(if (count >= 200) "Added by you" else "Seen $count×")
            },
            trailingContent = {
                IconButton(onClick = {
                    lexicon.forget(word)
                    persist()
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove $word")
                }
            },
        )
        HorizontalDivider()
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
                        lexicon.addWord(input.trim())
                        persist()
                        showAdd = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
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
 * system default, curated Google Fonts (every row rendered in its own face
 * as a live preview — faces download on first view and are cached by the
 * system provider), plus an imported custom font file per script.
 */
@Composable
private fun FontSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
        onImport = { uri ->
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    importFontFile(context, uri, KeyboardFonts.customFontFile(context))
                }
                if (name != null) repository.setCustomFont(name)
            }
        },
    )
    FontPickerSection(
        header = "Bengali font",
        sample = "আমি ভালো আছি · কখগঘঙ চছজঝঞ",
        selectedId = settings.bengaliFontId,
        googleNames = KeyboardFonts.bengaliGoogleFonts,
        customId = KeyboardFonts.CUSTOM_BENGALI_ID,
        customFile = KeyboardFonts.customBengaliFontFile(context),
        customName = settings.customBengaliFontName,
        onSelect = { id -> scope.launch { repository.setBengaliFontId(id) } },
        onImport = { uri ->
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    importFontFile(context, uri, KeyboardFonts.customBengaliFontFile(context))
                }
                if (name != null) repository.setCustomBengaliFont(name)
            }
        },
    )
    Spacer(Modifier.height(16.dp))
}

/** One script's font list: default, Google faces, the imported file, import button. */
@Composable
private fun FontPickerSection(
    header: String,
    sample: String,
    selectedId: String,
    googleNames: List<String>,
    customId: String,
    customFile: java.io.File,
    customName: String,
    onSelect: (String) -> Unit,
    onImport: (android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    SectionHeader(header)
    val importFont = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport(uri) }
    FontChoiceRow(
        label = "System default",
        family = null,
        sample = sample,
        selected = selectedId == KeyboardFonts.DEFAULT_ID,
    ) { onSelect(KeyboardFonts.DEFAULT_ID) }
    for (name in googleNames) {
        val id = KeyboardFonts.googleId(name)
        FontChoiceRow(
            label = name,
            family = remember(id) { KeyboardFonts.family(context, id) },
            sample = sample,
            selected = selectedId == id,
        ) { onSelect(id) }
    }
    if (customFile.exists()) {
        FontChoiceRow(
            label = customName.ifBlank { "Imported font" },
            family = remember(customName) { KeyboardFonts.family(context, customId) },
            sample = sample,
            selected = selectedId == customId,
        ) { onSelect(customId) }
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(
        onClick = { importFont.launch(FONT_MIME_TYPES) },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Import font file (.ttf / .otf)") }
}

/** One selectable font row, its label and sample line drawn in the font itself. */
@Composable
private fun FontChoiceRow(
    label: String,
    family: FontFamily?,
    sample: String,
    selected: Boolean,
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
        trailingContent = if (selected) {
            {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
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

private fun toolTitle(tool: ToolbarTool): String = when (tool) {
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
    ToolbarTool.THEMES -> "Themes"
    ToolbarTool.AUTOCORRECT -> "Autocorrect"
    ToolbarTool.SOUND_HAPTICS -> "Sound & haptics"
    ToolbarTool.NUMPAD -> "Numpad"
    ToolbarTool.HANDWRITING -> "Handwriting"
    ToolbarTool.TRANSLATE -> "Translate"
    ToolbarTool.GIF -> "GIFs"
    ToolbarTool.STICKER -> "Stickers"
    ToolbarTool.WEB_SEARCH -> "Web search"
    ToolbarTool.IMAGE_SEARCH -> "Image search"
}

private fun toolDescription(tool: ToolbarTool): String = when (tool) {
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
    ToolbarTool.CALENDAR -> "Month view with Bengali and Hijri dates"
    ToolbarTool.INCOGNITO -> "One tap pauses learning and clipboard capture"
    ToolbarTool.THEMES -> "Quick theme switcher on the keyboard"
    ToolbarTool.AUTOCORRECT -> "One tap turns autocorrect on or off"
    ToolbarTool.SOUND_HAPTICS -> "Adjust key sound and vibration from the keyboard"
    ToolbarTool.NUMPAD -> "Dedicated number pad layout"
    ToolbarTool.HANDWRITING -> "Write words by hand — finger or S Pen — with on-device recognition"
    ToolbarTool.TRANSLATE -> "Translate what you type, live, into any language"
    ToolbarTool.GIF -> "Search GIFs (Klipy, GIPHY, Google) and send them without leaving the keyboard"
    ToolbarTool.STICKER -> "Search stickers — transparent, chat-ready"
    ToolbarTool.WEB_SEARCH -> "Google a query and insert a result's link"
    ToolbarTool.IMAGE_SEARCH -> "Google Images from the keyboard; tap to send an image"
}

private fun toolIconFor(tool: ToolbarTool): androidx.compose.ui.graphics.vector.ImageVector = when (tool) {
    ToolbarTool.EMOJI -> Icons.Outlined.EmojiEmotions
    ToolbarTool.CLIPBOARD -> Icons.Outlined.ContentPaste
    ToolbarTool.SNIPPETS -> Icons.Outlined.TextSnippet
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
    ToolbarTool.TRANSLATE -> Icons.Outlined.Translate
    ToolbarTool.GIF -> Icons.Outlined.GifBox
    ToolbarTool.STICKER -> Icons.Outlined.AutoAwesome
    ToolbarTool.WEB_SEARCH -> Icons.Outlined.TravelExplore
    ToolbarTool.IMAGE_SEARCH -> Icons.Outlined.ImageSearch
}

/**
 * The tool menu: just icon + name per tool. Everything else — the enable
 * switch and the tool's own options — lives one level down.
 */
@Composable
private fun ToolsSettings(settings: KeyboardSettings, onOpenTool: (ToolbarTool) -> Unit) {
    Text(
        "Tools live on the keyboard's toolbar and in the toolbox (grid button on " +
            "the toolbar). Tap a tool to enable or disable it and change its settings.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    for (tool in ToolbarTool.entries) {
        ListItem(
            leadingContent = {
                Icon(
                    toolIconFor(tool),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text(toolTitle(tool)) },
            trailingContent = if (tool !in settings.enabledTools) {
                { Text("Off", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTool(tool) },
        )
        HorizontalDivider()
    }
}

/** One tool's screen: the enable switch plus every setting the tool has. */
@Composable
private fun ToolDetailSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    tool: ToolbarTool,
    onOpenEmojiSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Text(
        toolDescription(tool),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    ToggleSetting(
        "Enabled",
        "Show this tool on the toolbar and in the toolbox",
        tool in settings.enabledTools,
    ) { scope.launch { repository.setToolEnabled(tool, it) } }
    HorizontalDivider()
    when (tool) {
        ToolbarTool.EMOJI -> {
            ToggleSetting(
                "Emoji button in toolbar",
                "Keep the emoji button visible next to suggestions",
                settings.emojiToolbar,
            ) { scope.launch { repository.setEmojiToolbar(it) } }
            ListItem(
                headlineContent = { Text("All emoji settings") },
                supportingContent = {
                    Text("Suggestions, history tab, emoji row, skin tones & favourites")
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenEmojiSettings),
            )
        }
        ToolbarTool.SNIPPETS -> SnippetSettings()
        ToolbarTool.CLIPBOARD -> {
            ToggleSetting(
                "Clipboard history", "Save copied text for quick paste",
                settings.clipboardHistory,
            ) { scope.launch { repository.setClipboardHistory(it) } }
            SliderSetting(
                "Clipboard expiry",
                subtitle = "Remove unpinned items after this long",
                value = settings.clipboardExpiryHours.toFloat(),
                range = 0f..168f,
                display = if (settings.clipboardExpiryHours == 0) "never"
                    else "${settings.clipboardExpiryHours} h",
            ) { scope.launch { repository.setClipboardExpiryHours(it.toInt()) } }
        }
        ToolbarTool.SPLIT -> SliderSetting(
            "Split gap",
            subtitle = "Width of the gap between the halves",
            value = settings.splitGapPercent.toFloat(),
            range = 5f..40f,
            display = "${settings.splitGapPercent}%",
        ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
        ToolbarTool.FLOATING -> SliderSetting(
            "Floating keyboard width",
            subtitle = "Also adjustable by dragging the panel's corner grip",
            value = settings.floatingWidthDp.toFloat(),
            range = 240f..500f,
            display = "${settings.floatingWidthDp} dp",
        ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
        ToolbarTool.FLASHLIGHT -> ToggleSetting(
            "Auto-off with keyboard",
            "Turn the torch off when the keyboard is dismissed",
            settings.flashlightAutoOff,
            info = "On: closing the keyboard (or switching apps) switches the " +
                "torch off with it, so it is never left burning in your " +
                "pocket. Off: the torch stays on until toggled again — from " +
                "the tool or from the system quick-settings tile.",
        ) { scope.launch { repository.setFlashlightAutoOff(it) } }
        ToolbarTool.COMPASS -> {
            ToggleSetting(
                "Degree readout",
                "Show the numeric heading under the compass rose",
                settings.compassShowDegrees,
            ) { scope.launch { repository.setCompassShowDegrees(it) } }
            ToggleSetting(
                "Show qibla",
                "Mark the direction of the Kaaba on the compass",
                settings.compassShowQibla,
                info = "The qibla bearing is computed from the location saved in the " +
                    "weather tool's settings (the two tools share it). Everything is " +
                    "calculated on-device; the compass never touches the network.",
            ) { scope.launch { repository.setCompassShowQibla(it) } }
            if (settings.compassShowQibla && settings.weatherLatitude == null) {
                Text(
                    "No location saved yet — set one under Tools → Weather.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        ToolbarTool.LEVEL -> ToggleSetting(
            "Angle readout",
            "Show pitch and roll in degrees under the bubble",
            settings.levelShowAngles,
        ) { scope.launch { repository.setLevelShowAngles(it) } }
        ToolbarTool.REDO -> ToggleSetting(
            "Redo sends Ctrl+Y",
            "Instead of the default Ctrl+Shift+Z",
            settings.redoUsesCtrlY,
            info = "Both are standard redo shortcuts; which one works depends " +
                "on the app you are typing in. If redo does nothing, try " +
                "the other one.",
        ) { scope.launch { repository.setRedoUsesCtrlY(it) } }
        ToolbarTool.MOON_PHASE -> ToggleSetting(
            "Southern hemisphere",
            "Mirror the moon the way it appears south of the equator",
            settings.moonSouthernHemisphere,
        ) { scope.launch { repository.setMoonSouthernHemisphere(it) } }
        ToolbarTool.WEATHER -> {
            WeatherLocationSetting(repository, settings)
            ToggleSetting(
                "Fahrenheit", "Show temperatures in °F instead of °C",
                settings.weatherFahrenheit,
            ) { scope.launch { repository.setWeatherFahrenheit(it) } }
            Text(
                "Weather and place search both use Open-Meteo, only when you use " +
                    "them — the keyboard makes no other network requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        ToolbarTool.CALENDAR -> {
            ToggleSetting(
                "Bengali calendar",
                "বঙ্গাব্দ (revised Bangladeshi calendar) alongside dates",
                settings.calendarShowBengali,
            ) { scope.launch { repository.setCalendarShowBengali(it) } }
            ToggleSetting(
                "Hijri calendar",
                "Islamic (tabular) calendar alongside dates",
                settings.calendarShowHijri,
            ) { scope.launch { repository.setCalendarShowHijri(it) } }
            if (settings.calendarShowHijri) {
                SliderSetting(
                    "Hijri day adjustment",
                    subtitle = "Shift the computed Hijri date to match local moon sighting",
                    value = settings.hijriAdjustDays.toFloat(),
                    range = -2f..2f,
                    display = if (settings.hijriAdjustDays > 0) "+${settings.hijriAdjustDays} d"
                        else "${settings.hijriAdjustDays} d",
                    info = "The tool uses the arithmetic (tabular) Hijri calendar. " +
                        "Real Islamic months begin at the sighting of the crescent, " +
                        "which can differ from the tables by a day or two either " +
                        "way — set the offset that matches your local authority.",
                ) { scope.launch { repository.setHijriAdjustDays(it.roundToInt()) } }
            }
        }
        ToolbarTool.INCOGNITO -> Text(
            "Tapping the tool pauses on-device learning and clipboard capture; " +
                "tapping again resumes them. Same switch as Settings → Privacy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        ToolbarTool.AUTOCORRECT -> ToggleSetting(
            "Autocorrect",
            "The tool flips this same switch (also under Typing)",
            settings.autocorrect,
        ) { scope.launch { repository.setAutocorrect(it) } }
        ToolbarTool.SOUND_HAPTICS -> {
            SectionHeader("Key press sound")
            ToggleSetting(
                "Key sound", "Play a sound on every key press", settings.keySound,
            ) { scope.launch { repository.setKeySound(it) } }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sound style", style = MaterialTheme.typography.bodyLarge)
                InfoButton(
                    "Sound style",
                    "All five come from the device's system sound pack, so they match " +
                        "the stock keyboard's palette: Click is the classic key tick, " +
                        "Standard the softer AOSP key press, Pop the spacebar thump, " +
                        "Thock the deeper delete sound, and Chime the return-key sound.",
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)) {
                KeySoundStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = settings.keySoundStyle == style,
                        onClick = { scope.launch { repository.setKeySoundStyle(style) } },
                        shape = SegmentedButtonDefaults.itemShape(index, KeySoundStyle.entries.size),
                    ) {
                        Text(
                            when (style) {
                                KeySoundStyle.CLICK -> "Click"
                                KeySoundStyle.STANDARD -> "Std"
                                KeySoundStyle.POP -> "Pop"
                                KeySoundStyle.THOCK -> "Thock"
                                KeySoundStyle.CHIME -> "Chime"
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            SliderSetting(
                "Sound volume",
                subtitle = "Relative to the system media volume",
                value = settings.keySoundVolume,
                range = 0.05f..1f,
                display = "${(settings.keySoundVolume * 100).roundToInt()}%",
            ) { scope.launch { repository.setKeySoundVolume(it) } }
            SectionHeader("Haptics")
            Text(
                "Haptic style and strength live under Typing → Feedback; the tool's " +
                    "panel changes the same settings from the keyboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        ToolbarTool.HANDWRITING -> {
            ToggleSetting(
                "Stylus only",
                "Only an S Pen or other stylus draws; finger touches are ignored",
                settings.handwritingStylusOnly,
                info = "Useful for palm rejection while writing with a stylus. Even " +
                    "with this off, finger touches are briefly ignored right after " +
                    "stylus strokes, so a resting palm doesn't scribble.",
            ) { scope.launch { repository.setHandwritingStylusOnly(it) } }
            ToggleSetting(
                "Auto space",
                "Insert a space between consecutively written words",
                settings.handwritingAutoSpace,
            ) { scope.launch { repository.setHandwritingAutoSpace(it) } }
            SliderSetting(
                "Recognition pause",
                subtitle = "How long after the last stroke before the word is recognized",
                value = settings.handwritingCommitDelayMs.toFloat(),
                range = 300f..2000f,
                display = "${settings.handwritingCommitDelayMs} ms",
                info = "Shorter feels snappier but can cut multi-stroke letters and " +
                    "Bengali conjuncts in half; longer gives you more time between " +
                    "strokes. Gboard uses roughly half a second.",
            ) { scope.launch { repository.setHandwritingCommitDelayMs(it.roundToInt()) } }
            SectionHeader("Recognition models")
            Text(
                "Recognition runs fully on-device with Google ML Kit. Each language " +
                    "needs a one-time model download (about 20 MB); after that, " +
                    "handwriting works offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HandwritingModelManager()
        }
        ToolbarTool.TRANSLATE -> {
            TranslateLanguageSetting(repository, settings)
            SectionHeader("API key")
            ApiKeyField(
                label = "Cloud Translation API key (optional)",
                value = settings.translateApiKey,
                builtInAvailable = ToolApiKeys.builtInTranslate,
                emptyHint = "Without a key, translation uses Google's free public endpoint",
            ) { repository.setTranslateApiKey(it) }
            Text(
                "Text you translate is sent to Google either way — only while the " +
                    "translate panel is open. The free endpoint is unofficial and " +
                    "rate-limited; a Cloud Translation key makes it official and reliable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        ToolbarTool.GIF, ToolbarTool.STICKER -> {
            SectionHeader("Sources & API keys")
            ApiKeyField(
                label = "Klipy API key",
                value = settings.klipyApiKey,
                builtInAvailable = ToolApiKeys.builtInKlipy,
                emptyHint = "Free from partner.klipy.com (Tenor's API was retired mid-2026)",
            ) { repository.setKlipyApiKey(it) }
            ApiKeyField(
                label = "GIPHY API key",
                value = settings.giphyApiKey,
                builtInAvailable = ToolApiKeys.builtInGiphy,
                emptyHint = "Free from developers.giphy.com",
            ) { repository.setGiphyApiKey(it) }
            ToggleSetting(
                "Google Images as a source",
                "Animated GIFs (transparent PNGs for stickers) via web/image search's keys",
                settings.gifUseGoogle,
                info = "Needs the Google Programmable Search key and engine id from " +
                    "the web search tool's settings. Google only searches when you " +
                    "press enter (or pick its chip) — never per keystroke — because " +
                    "its free tier is 100 requests a day, shared with the web and " +
                    "image search tools. Google previews are static; the inserted " +
                    "GIF still animates.",
            ) { scope.launch { repository.setGifUseGoogle(it) } }
            Text(
                "The GIF and sticker tools share all of this, including the content " +
                    "filter below. Any one key is enough; every configured source " +
                    "shows up in the panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
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
            Text(
                "Tabs: a chip per source on the panel. Mixed: one grid with results " +
                    "from every source interleaved evenly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
            Text(
                "High hides the most; Off hides nothing. Maps to Klipy's and " +
                    "GIPHY's rating (High = G … Off = R); for Google, SafeSearch " +
                    "follows the web search tool's setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH -> {
            SectionHeader("Google Programmable Search")
            ApiKeyField(
                label = "API key",
                value = settings.googleSearchApiKey,
                builtInAvailable = ToolApiKeys.builtInGoogleSearch,
                emptyHint = "From Google Cloud — enable the Custom Search API",
            ) { repository.setGoogleSearchApiKey(it) }
            ApiKeyField(
                label = "Search engine ID (cx)",
                value = settings.googleSearchCx,
                builtInAvailable = ToolApiKeys.builtInGoogleSearch,
                emptyHint = "From programmablesearchengine.google.com",
            ) { repository.setGoogleSearchCx(it) }
            Text(
                "Web and image search share these. Create an engine at " +
                    "programmablesearchengine.google.com with “Search the entire web” " +
                    "and image search turned on; the free tier allows 100 searches a day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SectionHeader("Results")
            ToggleSetting(
                "SafeSearch", "Filter explicit results",
                settings.searchSafe,
            ) { scope.launch { repository.setSearchSafe(it) } }
            SliderSetting(
                "Results per search",
                subtitle = "Each search uses one API request either way",
                value = settings.searchResultCount.toFloat(),
                range = 1f..10f,
                display = "${settings.searchResultCount}",
            ) { scope.launch { repository.setSearchResultCount(it.roundToInt()) } }
        }
        else -> {}
    }
}

/**
 * One API-key input. Saves as you type (it's a paste, in practice). The
 * user's key always beats any key baked into the build via
 * local.properties — leaving the field blank falls back to the built-in
 * key when the build has one.
 */
@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    builtInAvailable: Boolean,
    emptyHint: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(label) { mutableStateOf(value) }
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

/** "Translate into" row with a full-language-list dialog. */
@Composable
private fun TranslateLanguageSetting(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Translate into") },
        supportingContent = { Text(TranslateClient.languageName(settings.translateTargetLang)) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true },
    )
    Text(
        "The source language is always auto-detected; this is also changeable " +
            "from the panel itself.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
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
 * Download/delete state for each supported handwriting model. Status is
 * re-read from ML Kit's model manager after every action.
 */
@Composable
private fun HandwritingModelManager() {
    val scope = rememberCoroutineScope()
    // tag -> "checking" | "missing" | "downloaded" | "downloading" | "error"
    val statuses = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) {
        for (language in HandwritingModels.supported) {
            statuses[language.tag] =
                if (HandwritingModels.isDownloaded(language.tag)) "downloaded" else "missing"
        }
    }
    for (language in HandwritingModels.supported) {
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
                            val ok = runCatching { HandwritingModels.download(language.tag) }.isSuccess
                            statuses[language.tag] = if (ok) "downloaded" else "error"
                        }
                    }) { Text("Download") }
                }
            },
        )
    }
}

/** Weather location: place label plus coordinates, edited in a dialog. */
@Composable
private fun WeatherLocationSetting(repository: SettingsRepository, settings: KeyboardSettings) {
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

@Composable
private fun PrivacySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ToggleSetting(
        "Learn from typing",
        "Personalize suggestions on-device. Nothing ever leaves your phone.",
        settings.learnFromTyping,
        info = "Words and word pairs you type are stored in a private on-device " +
            "dictionary to improve suggestions and gesture typing. Learning is skipped " +
            "in password fields and while incognito mode is on, and can be wiped below " +
            "at any time.",
    ) { scope.launch { repository.setLearnFromTyping(it) } }
    ToggleSetting(
        "Incognito mode",
        "Pause learning and clipboard capture",
        settings.incognito,
        info = "While on, the keyboard learns nothing from your typing and clipboard " +
            "history is not recorded. Existing learned words are untouched.",
    ) { scope.launch { repository.setIncognito(it) } }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = {
            java.io.File(context.filesDir, "learning/user_lexicon.json").delete()
            java.io.File(context.filesDir, "learning/emoji_usage.json").delete()
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Clear learned words") }
    Spacer(Modifier.height(4.dp))
    Text(
        "WM Keyboard works offline: dictionaries, Bengali transliteration and " +
            "emoji search are all bundled, and there is no telemetry. The one " +
            "exception is the optional weather tool, which fetches conditions from " +
            "Open-Meteo only when you open it (and can be disabled under Tools).",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SnippetSettings() {
    val context = LocalContext.current
    val store = remember {
        SnippetStore(java.io.File(context.filesDir, "snippets/snippets.json"))
    }
    var snippets by remember { mutableStateOf(store.items()) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var showAdd by remember { mutableStateOf(false) }

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
            // Live examples: expand the actual templates so the preview
            // always matches what an insertion would produce right now.
            VariableRow("{date}", "today's date", SnippetStore.expand("{date}"))
            VariableRow("{time}", "current time", SnippetStore.expand("{time}"))
            VariableRow("{datetime}", "date and time", SnippetStore.expand("{datetime}"))
            VariableRow("{clip}", "latest clipboard entry", "whatever you copied last")
            Spacer(Modifier.height(8.dp))
            Text(
                "Variables expand at the moment the snippet is inserted, not when it " +
                    "is saved — so {date} always produces the current date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Add snippet") }
    Spacer(Modifier.height(8.dp))
    for (snippet in snippets) {
        ListItem(
            headlineContent = { Text(snippet.label) },
            supportingContent = {
                Column {
                    Text(snippet.text, maxLines = 2)
                    if (snippet.text != SnippetStore.expand(snippet.text, clipboard = "…")) {
                        Text(
                            "Inserts as: " + SnippetStore.expand(snippet.text, clipboard = "…"),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { editing = snippet }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = {
                        store.remove(snippet.id)
                        store.save()
                        snippets = store.items()
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                }
            },
        )
        HorizontalDivider()
    }

    if (showAdd || editing != null) {
        SnippetDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { label, text ->
                val current = editing
                if (current == null) store.add(label, text) else store.update(current.id, label, text)
                store.save()
                snippets = store.items()
                showAdd = false
                editing = null
            },
        )
    }
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
    onSave: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
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
                    label = { Text("Text — supports {date} {time} {datetime} {clip}") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(label.trim(), text) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
