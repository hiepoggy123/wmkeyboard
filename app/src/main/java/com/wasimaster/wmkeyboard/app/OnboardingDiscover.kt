package com.wasimaster.wmkeyboard.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.OnboardingSettings
import com.wasimaster.wmkeyboard.core.settings.PersonaDepth
import com.wasimaster.wmkeyboard.core.settings.PersonaPrivacy
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool
import kotlinx.coroutines.launch

/** How a discover card behaves. */
internal enum class DiscoverKind {
    /** A switch on the card turns the feature on or off right here. */
    TOGGLE,

    /** Nothing to flip mid-wizard; the card says what it is and where it lives. */
    EXPLORE,
}

/**
 * One feature worth showing off during setup. The catalog deliberately skips
 * what every keyboard has (glide, plain voice input, one-handed mode): a
 * discovery page full of table stakes teaches nobody anything.
 */
internal data class DiscoverFeature(
    val id: String,
    @StringRes val title: Int,
    @StringRes val pitch: Int,
    /** Extra caption: a permission note, or where an EXPLORE feature lives. */
    @StringRes val hint: Int = 0,
    val icon: ImageVector,
    val accent: Color,
    val kind: DiscoverKind,
    /** Filtered out where the tool is not in this build flavour. */
    val requiresTool: ToolbarTool? = null,
    val fullFlavorOnly: Boolean = false,
    val isOn: (KeyboardSettings) -> Boolean = { false },
    val setOn: (suspend (SettingsRepository, Boolean) -> Unit)? = null,
)

private val Chips = DiscoverFeature(
    id = "chips",
    title = R.string.onboarding_discover_chips_title,
    pitch = R.string.onboarding_discover_chips_pitch,
    icon = Icons.Outlined.Calculate,
    accent = Color(0xFF42A5F5),
    kind = DiscoverKind.TOGGLE,
    isOn = { it.smartSuggestions },
    setOn = { repo, on -> repo.setSmartSuggestions(on) },
)

private val Hotwords = DiscoverFeature(
    id = "hotwords",
    title = R.string.onboarding_discover_hotwords_title,
    pitch = R.string.onboarding_discover_hotwords_pitch,
    icon = Icons.Outlined.Bolt,
    accent = Color(0xFF26C6DA),
    kind = DiscoverKind.TOGGLE,
    isOn = { it.smartToolKeywords },
    setOn = { repo, on -> repo.setSmartToolKeywords(on) },
)

private val Toolbox = DiscoverFeature(
    id = "toolbox",
    title = R.string.onboarding_discover_toolbox_title,
    pitch = R.string.onboarding_discover_toolbox_pitch,
    hint = R.string.onboarding_discover_toolbox_hint,
    icon = Icons.Outlined.Widgets,
    accent = Color(0xFFFF7043),
    kind = DiscoverKind.EXPLORE,
)

private val Photos = DiscoverFeature(
    id = "photos",
    title = R.string.onboarding_discover_photos_title,
    pitch = R.string.onboarding_discover_photos_pitch,
    hint = R.string.onboarding_discover_photos_hint,
    icon = Icons.Outlined.Wallpaper,
    accent = Color(0xFFEC407A),
    kind = DiscoverKind.EXPLORE,
)

private val Clipboard = DiscoverFeature(
    id = "clipboard",
    title = R.string.onboarding_discover_clipboard_title,
    pitch = R.string.onboarding_discover_clipboard_pitch,
    icon = Icons.Outlined.ContentPaste,
    accent = Color(0xFF66BB6A),
    kind = DiscoverKind.TOGGLE,
    isOn = { it.clipboard.history },
    setOn = { repo, on -> repo.setClipboardHistory(on) },
)

private val Snippets = DiscoverFeature(
    id = "snippets",
    title = R.string.onboarding_discover_snippets_title,
    pitch = R.string.onboarding_discover_snippets_pitch,
    hint = R.string.onboarding_discover_snippets_hint,
    icon = Icons.AutoMirrored.Outlined.TextSnippet,
    accent = Color(0xFF7E57C2),
    kind = DiscoverKind.EXPLORE,
    requiresTool = ToolbarTool.SNIPPETS,
)

private val Otp = DiscoverFeature(
    id = "otp",
    title = R.string.onboarding_discover_otp_title,
    pitch = R.string.onboarding_discover_otp_pitch,
    hint = R.string.onboarding_discover_otp_hint,
    icon = Icons.Outlined.Pin,
    accent = Color(0xFFEF5350),
    kind = DiscoverKind.TOGGLE,
    isOn = { it.otp.enabled },
    setOn = { repo, on -> repo.setOtpChipEnabled(on) },
)

private val Fancy = DiscoverFeature(
    id = "fancy",
    title = R.string.onboarding_discover_fancy_title,
    pitch = R.string.onboarding_discover_fancy_pitch,
    icon = Icons.Outlined.TextFormat,
    accent = Color(0xFFFFB300),
    kind = DiscoverKind.TOGGLE,
    requiresTool = ToolbarTool.FANCY,
    isOn = { ToolbarTool.FANCY in it.enabledTools },
    setOn = { repo, on -> repo.setToolEnabled(ToolbarTool.FANCY, on) },
)

private val Ai = DiscoverFeature(
    id = "ai",
    title = R.string.onboarding_discover_ai_title,
    pitch = R.string.onboarding_discover_ai_pitch,
    hint = R.string.onboarding_discover_ai_hint,
    icon = Icons.Outlined.AutoAwesome,
    accent = Color(0xFF00ACC1),
    kind = DiscoverKind.TOGGLE,
    requiresTool = ToolbarTool.AI,
    isOn = { ToolbarTool.AI in it.enabledTools },
    setOn = { repo, on -> repo.setToolEnabled(ToolbarTool.AI, on) },
)

private val Whisper = DiscoverFeature(
    id = "whisper",
    title = R.string.onboarding_discover_whisper_title,
    pitch = R.string.onboarding_discover_whisper_pitch,
    hint = R.string.onboarding_discover_whisper_hint,
    icon = Icons.Outlined.Mic,
    accent = Color(0xFF26A69A),
    kind = DiscoverKind.EXPLORE,
    fullFlavorOnly = true,
)

private val Modes = DiscoverFeature(
    id = "modes",
    title = R.string.onboarding_discover_modes_title,
    pitch = R.string.onboarding_discover_modes_pitch,
    hint = R.string.onboarding_discover_modes_hint,
    icon = Icons.Outlined.Apps,
    accent = Color(0xFF7E57C2),
    kind = DiscoverKind.EXPLORE,
)

/**
 * Incognito and the autocorrect toggle in one card: the point is not either
 * switch but that both live on the toolbar, flippable while typing, without a
 * settings trip.
 */
private val QuickToggles = DiscoverFeature(
    id = "quick_toggles",
    title = R.string.onboarding_discover_quick_toggles_title,
    pitch = R.string.onboarding_discover_quick_toggles_pitch,
    icon = Icons.Outlined.ToggleOn,
    accent = Color(0xFFEF5350),
    kind = DiscoverKind.TOGGLE,
    isOn = {
        ToolbarTool.INCOGNITO in it.enabledTools && ToolbarTool.AUTOCORRECT in it.enabledTools
    },
    setOn = { repo, on ->
        repo.setToolEnabled(ToolbarTool.INCOGNITO, on)
        repo.setToolEnabled(ToolbarTool.AUTOCORRECT, on)
    },
)

/** How many cards each depth answer gets; enough to be a tour, never a wall. */
private const val DISCOVER_CAP_MINIMAL = 5
private const val DISCOVER_CAP_BALANCED = 7

/**
 * The card list for a persona, in pitch order. Power users see everything,
 * strict-privacy users see the keyboard-side toggles first (theirs is the
 * persona that reaches for incognito) with the network-adjacent AI card moved
 * last, and everyone else gets the head of the list.
 */
internal fun discoverFeatures(
    persona: OnboardingSettings,
    whisperAvailable: Boolean,
    isToolSupported: (ToolbarTool) -> Boolean,
): List<DiscoverFeature> {
    val base = mutableListOf(
        Chips, Hotwords, Toolbox, Photos, Clipboard, Snippets, Otp, Fancy, Ai,
    )
    if (persona.personaDepth == PersonaDepth.POWER) {
        base += Whisper
        base += Modes
    }
    if (persona.personaPrivacy == PersonaPrivacy.STRICT) {
        base.remove(Ai)
        base += Ai
        base.add(0, QuickToggles)
    }
    val available = base.filter { feature ->
        (feature.requiresTool?.let(isToolSupported) != false) &&
            (!feature.fullFlavorOnly || whisperAvailable)
    }
    return when (persona.personaDepth) {
        PersonaDepth.MINIMAL -> available.take(DISCOVER_CAP_MINIMAL)
        PersonaDepth.POWER -> available
        PersonaDepth.BALANCED, PersonaDepth.UNSET -> available.take(DISCOVER_CAP_BALANCED)
    }
}

/**
 * The feature tour. Cards either flip a feature on the spot or, for the ones
 * with nothing to flip mid-wizard, say where to find them later; nothing here
 * navigates away, because there is nowhere to come back to mid-setup.
 */
@Composable
internal fun DiscoverPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val features = remember(settings.onboarding) {
        discoverFeatures(
            persona = settings.onboarding,
            whisperAvailable = BuildConfig.ENABLE_WHISPER,
            isToolSupported = ::isSupportedTool,
        )
    }
    for (feature in features) {
        DiscoverCard(feature, settings) { on ->
            feature.setOn?.let { write -> scope.launch { write(repository, on) } }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DiscoverCard(
    feature: DiscoverFeature,
    settings: KeyboardSettings,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            leadingContent = { WmIconTile(feature.icon, feature.accent) },
            headlineContent = { Text(stringResource(feature.title)) },
            supportingContent = {
                Column {
                    Text(stringResource(feature.pitch))
                    if (feature.hint != 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(feature.hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            trailingContent = when (feature.kind) {
                DiscoverKind.TOGGLE -> {
                    {
                        Switch(
                            checked = feature.isOn(settings),
                            onCheckedChange = onToggle,
                        )
                    }
                }
                DiscoverKind.EXPLORE -> null
            },
            colors = transparentListColors(),
        )
    }
}
