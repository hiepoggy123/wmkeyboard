package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.os.Build
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings app: setup wizard plus every keyboard option, Material 3 +
 * dynamic color, all state backed by DataStore via [SettingsRepository].
 */
class MainActivity : ComponentActivity() {

    private lateinit var repository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(applicationContext)
        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle(KeyboardSettings())
            AppTheme(settings) {
                SettingsNavHost(repository, settings)
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
private fun SettingsNavHost(repository: SettingsRepository, settings: KeyboardSettings) {
    val navController = rememberNavController()
    // Quick shared-axis slide instead of the sluggish default cross-fade.
    val spec = tween<androidx.compose.ui.unit.IntOffset>(220)
    val fadeSpec = tween<Float>(220)
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideInHorizontally(spec) { it / 5 } + fadeIn(fadeSpec) },
        exitTransition = { slideOutHorizontally(spec) { -it / 5 } + fadeOut(fadeSpec) },
        popEnterTransition = { slideInHorizontally(spec) { -it / 5 } + fadeIn(fadeSpec) },
        popExitTransition = { slideOutHorizontally(spec) { it / 5 } + fadeOut(fadeSpec) },
    ) {
        composable("home") {
            HomeScreen(
                settings = settings,
                onNavigate = { navController.navigate(it) },
            )
        }
        composable("typing") {
            SettingsScreen("Typing", { navController.popBackStack() }) {
                TypingSettings(repository, settings)
            }
        }
        composable("appearance") {
            SettingsScreen("Appearance & themes", { navController.popBackStack() }) {
                AppearanceSettings(repository, settings)
            }
        }
        composable("languages") {
            SettingsScreen("Languages", { navController.popBackStack() }) {
                LanguageSettings(repository, settings)
            }
        }
        composable("clipboard") {
            SettingsScreen("Clipboard & emoji", { navController.popBackStack() }) {
                ClipboardEmojiSettings(repository, settings)
            }
        }
        composable("snippets") {
            SettingsScreen("Snippets", { navController.popBackStack() }) {
                SnippetSettings()
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
            SectionItem("Typing", "Autocorrect, suggestions, key behavior") { onNavigate("typing") }
            SectionItem("Appearance & themes", "Material You, AMOLED, key size, split & resize") { onNavigate("appearance") }
            SectionItem("Languages", "English, বাংলা (Avro phonetic, প্রভাত)") { onNavigate("languages") }
            SectionItem("Clipboard & emoji", "History, expiry, toolbar") { onNavigate("clipboard") }
            SectionItem("Snippets", "Reusable text with {date}, {time}, {clip} variables") { onNavigate("snippets") }
            SectionItem("Privacy", "On-device learning, incognito") { onNavigate("privacy") }
        }
    }
}

@Composable
private fun SetupCard(context: Context) {
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
private fun SectionItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
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

// ---- sections ----

@Composable
private fun TypingSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SectionHeader("Corrections")
    ToggleSetting(
        "Autocorrect", "Fix typos automatically when you press space", settings.autocorrect,
        info = "When you press space, the word you just typed is checked against the " +
            "dictionary. If it looks like a slip of an obviously more common word, it is " +
            "replaced. Words you have taught the keyboard are never \"corrected\" away, " +
            "and autocorrect stays off in password fields.",
    ) { scope.launch { repository.setAutocorrect(it) } }
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
    ToggleSetting(
        "Spacebar cursor control", "Slide on the spacebar to move the cursor", settings.spacebarCursor,
        info = "Press and drag horizontally on the spacebar to move the text cursor one " +
            "character at a time — far more precise than tapping in the text. A tap " +
            "without movement still types a space.",
    ) { scope.launch { repository.setSpacebarCursor(it) } }

    SectionHeader("Layout")
    ToggleSetting(
        "Number row", "Show a dedicated digit row above the letters", settings.numberRow,
        info = "Adds a 1–0 row on top of the letter layout so you never long-press for " +
            "digits. Costs one extra row of height.",
    ) { scope.launch { repository.setNumberRow(it) } }

    SectionHeader("Feedback")
    ToggleSetting(
        "Key press haptics", "Vibrate on every key press", settings.hapticFeedback,
        info = "A short vibration confirms each key press, including spacebar cursor " +
            "movement steps. Style and strength are adjustable below.",
    ) { scope.launch { repository.setHapticFeedback(it) } }
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
                "(Android 10+) — the same crisp feedback stock keyboards use, and " +
                "usually the strongest-feeling option. Custom drives the vibration " +
                "motor directly using the duration and intensity sliders. On older " +
                "devices Click and Heavy click fall back to Custom.",
        )
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        HapticStyle.entries.forEachIndexed { index, style ->
            SegmentedButton(
                selected = settings.hapticStyle == style,
                onClick = { scope.launch { repository.setHapticStyle(style) } },
                shape = SegmentedButtonDefaults.itemShape(index, HapticStyle.entries.size),
            ) {
                Text(
                    when (style) {
                        HapticStyle.CUSTOM -> "Custom"
                        HapticStyle.CLICK -> "Click"
                        HapticStyle.HEAVY_CLICK -> "Heavy click"
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
        ) { scope.launch { repository.setHapticStrengthMs(it.toInt()) } }
        SliderSetting(
            "Haptic intensity",
            subtitle = "Vibration amplitude per key press",
            value = settings.hapticAmplitude.toFloat(),
            range = 1f..255f,
            display = "${settings.hapticAmplitude * 100 / 255}%",
            info = "How hard the vibration motor is driven (1–255). Only takes effect on " +
                "devices whose vibrator supports amplitude control; on others only the " +
                "duration above matters. The system-wide \"Touch feedback\" vibration " +
                "setting still scales the final strength on top of this.",
        ) { scope.launch { repository.setHapticAmplitude(it.toInt()) } }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SectionHeader("Theme")
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.themeMode == mode,
                onClick = { scope.launch { repository.setThemeMode(mode) } },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
    ToggleSetting(
        "Material You colors", "Use wallpaper-based dynamic color", settings.dynamicColor,
        info = "On Android 12 and newer, the keyboard picks up the accent palette " +
            "generated from your wallpaper. Turn off for the standard Material palette.",
    ) { scope.launch { repository.setDynamicColor(it) } }

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
private fun ClipboardEmojiSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SectionHeader("Clipboard")
    ToggleSetting(
        "Clipboard history", "Save copied text for quick paste", settings.clipboardHistory,
        info = "Everything you copy is kept in the keyboard's clipboard panel for quick " +
            "pasting. Nothing is captured from password fields or while incognito mode " +
            "is on, and history never leaves your device.",
    ) { scope.launch { repository.setClipboardHistory(it) } }
    SliderSetting(
        "Clipboard expiry",
        subtitle = "Remove unpinned items after this long",
        value = settings.clipboardExpiryHours.toFloat(),
        range = 0f..168f,
        display = if (settings.clipboardExpiryHours == 0) "never" else "${settings.clipboardExpiryHours} h",
        info = "Unpinned clipboard items are deleted automatically after this many " +
            "hours. Pinned items never expire. Set to \"never\" (all the way left) to " +
            "keep items until you delete them yourself.",
    ) { scope.launch { repository.setClipboardExpiryHours(it.toInt()) } }
    SectionHeader("Emoji")
    ToggleSetting(
        "Emoji button in toolbar", "One-tap emoji access from the top bar", settings.emojiToolbar,
        info = "Keeps the emoji button visible in the top bar even while suggestions " +
            "are showing. The emoji panel itself has tabs per category, search in " +
            "English and Bengali, and skin-tone variants on long-press.",
    ) { scope.launch { repository.setEmojiToolbar(it) } }
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
        "WM Keyboard works fully offline: dictionaries, Bengali transliteration and " +
            "emoji search are all bundled. There is no telemetry.",
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
