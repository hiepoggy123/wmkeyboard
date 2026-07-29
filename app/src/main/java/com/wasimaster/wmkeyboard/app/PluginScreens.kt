package com.wasimaster.wmkeyboard.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasimaster.wmkeyboard.core.plugins.PluginFile
import com.wasimaster.wmkeyboard.core.plugins.PluginImportResult
import com.wasimaster.wmkeyboard.core.plugins.PluginLog
import com.wasimaster.wmkeyboard.core.plugins.PluginManifestResult
import com.wasimaster.wmkeyboard.core.plugins.PluginStorage
import com.wasimaster.wmkeyboard.core.plugins.PluginStore
import com.wasimaster.wmkeyboard.core.util.requireInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Managing plugins: the master switch, what is installed, and what each one is
 * allowed to do.
 *
 * The whole subsystem is **off until asked for**. Nothing installs and nothing
 * runs before the user turns it on, and the explainer beside the switch says in
 * plain words what a plugin can and cannot reach — which is a short list,
 * because the sandbox has no API for reading text, the clipboard or the network.
 */
@Composable
internal fun PluginsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { PluginStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val enabled = remember(revision) { store.subsystemEnabled() }
    val plugins = remember(revision) { store.plugins() }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pending = uri }

    // No scroller of its own: SettingsScreen already wraps this content in a
    // Column(verticalScroll), and a scrollable inside a scrollable is measured
    // with an infinite maximum height, which Compose refuses outright.
    SettingsGroup {
        item {
            // Highlightable by name: an addon page that refused to install a
            // plugin sends the user straight to this switch.
            HighlightableRow("Allow plugins") {
                WmRow(
                    title = "Allow plugins",
                    subtitle = "Plugins are small tools written by other people that run inside " +
                        "a sandbox on this keyboard. Separate from the Plugins tool on " +
                        "the toolbar, which only decides where the panel appears.",
                    trailing = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { store.setSubsystemEnabled(it) },
                        )
                    },
                )
            }
        }
    }

    SettingsGroup("What a plugin can and can't do") {
        item { PluginFact("Can't see what you type", allowed = false) }
        item { PluginFact("Can't read the text you're writing in", allowed = false) }
        item { PluginFact("Can't read your clipboard", allowed = false) }
        item { PluginFact("Can't use the internet", allowed = false) }
        item { PluginFact("Can draw its own panel and do its own maths", allowed = true) }
        item { PluginFact("Can save its own data, if it says so before you install it", allowed = true) }
        item {
            CaptionText(
                "These aren't settings — there is no way for a plugin to ask for any of " +
                    "them. You give a plugin text by typing or pasting into its own box, " +
                    "and its results reach you when you tap Insert.",
            )
        }
    }

    if (enabled) {
        SettingsGroup("Installed") {
            if (plugins.isEmpty()) {
                item { CaptionText("No plugins installed yet.") }
            } else {
                for (plugin in plugins) {
                    item {
                        WmRow(
                            title = plugin.name,
                            subtitle = buildString {
                                append("Version ${plugin.version}")
                                if (plugin.author.isNotBlank()) append(" · ${plugin.author}")
                                if (!plugin.enabled) append(" · turned off")
                            },
                            trailing = {
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                            },
                            // The arrow promises a tap opens it. It used to sit
                            // above a separate "Manage" button and do nothing.
                            onClick = { onNavigate("plugin/${plugin.id}") },
                        )
                    }
                }
            }
        }

        SettingsGroup("Add") {
            item {
                WmRow(
                    title = "Install from a file",
                    subtitle = "Choose a .wmplugin file",
                    onClick = { picker.launch(PluginFile.IMPORT_MIME_TYPES) },
                )
            }
            item {
                CaptionText(
                    "You can also install plugins from an addon repository, under Addons.",
                )
            }
        }
    }

    // The same disclosure the file-manager path shows: what the plugin says it
    // is and what it may do, before a line of its code is on the device.
    pending?.let { uri ->
        PluginInstallDialog(
            uri = uri,
            onDismiss = { pending = null },
            onDone = { message ->
                pending = null
                importMessage = message
            },
            scope = scope,
        )
    }

    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            title = { Text("Plugins") },
            text = { Text(message) },
            confirmButton = { TextButton({ importMessage = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun PluginFact(text: String, allowed: Boolean) {
    WmRow(
        title = text,
        leading = {
            Text(
                if (allowed) "✓" else "✕",
                color = if (allowed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
    )
}

/** One plugin: what it may do, what it has stored, its log, and how to remove it. */
@Composable
internal fun PluginDetailScreen(pluginId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PluginStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val plugin = remember(revision) { store.plugin(pluginId) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmUninstall by remember { mutableStateOf(false) }

    if (plugin == null) {
        Column { CaptionText("That plugin is no longer installed.") }
        return
    }

    val storage = remember(pluginId, refresh) { PluginStorage(store.storageFile(pluginId)) }
    val usedBytes = remember(pluginId, refresh) { storage.usedBytes() }
    val log = remember(pluginId, refresh) {
        PluginLog(store.logFile(pluginId)).also { it.restore() }.lines()
    }

    // No scroller of its own: SettingsScreen already wraps this content in a
    // Column(verticalScroll), and a scrollable inside a scrollable is measured
    // with an infinite maximum height, which Compose refuses outright.
    SettingsGroup {
        item {
            WmRow(
                title = plugin.name,
                subtitle = buildString {
                    append(plugin.description.ifBlank { "No description." })
                    append("\n\nVersion ${plugin.version}")
                    if (plugin.author.isNotBlank()) append(" by ${plugin.author}")
                },
            )
        }
        item {
            WmRow(
                title = "Enabled",
                subtitle = if (plugin.abandonedCount >= PluginStore.MAX_ABANDONS) {
                    "Turned off because it stopped responding twice."
                } else {
                    "Show this plugin in the Plugins panel"
                },
                trailing = {
                    Switch(
                        checked = plugin.enabled,
                        onCheckedChange = { store.setEnabled(pluginId, it) },
                    )
                },
            )
        }
    }

    SettingsGroup("What it can do") {
        if (plugin.grantedPermissions.isEmpty()) {
            item {
                WmRow(title = "Nothing outside its own panel")
            }
        } else {
            for (permission in plugin.grantedPermissions) {
                item {
                    WmRow(title = permission.label)
                }
            }
        }
        item {
            CaptionText(
                "Declared when the plugin was installed. A plugin cannot ask for anything " +
                    "else while it runs, because there is nothing else to ask for.",
            )
        }
    }

    SettingsGroup("Stored data") {
        item {
            WmRow(
                title = "Using ${usedBytes / 1024} KB of ${PluginStorage.MAX_TOTAL_BYTES / 1024} KB",
                subtitle = "Saved on this device only",
            )
        }
        item {
            TextButton(onClick = { storage.clear(); refresh++ }) { Text("Clear stored data") }
        }
    }

    if (log.isNotEmpty()) {
        SettingsGroup("Log") {
            item {
                CaptionText(
                    "Anything the plugin printed, plus errors. Useful when writing one.",
                )
            }
            for (line in log.takeLast(LOG_LINES)) {
                item {
                    WmRow(
                        title = line,
                        titleStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                TextButton(onClick = { PluginLog(store.logFile(pluginId)).clear(); refresh++ }) {
                    Text("Clear log")
                }
            }
        }
    }

    SettingsGroup {
        item {
            TextButton(onClick = { confirmUninstall = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text("  Uninstall")
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Uninstall ${plugin.name}?") },
            text = { Text("Its script and anything it saved are deleted from this device.") },
            confirmButton = {
                TextButton({
                    store.delete(pluginId)
                    confirmUninstall = false
                    onBack()
                }) { Text("Uninstall") }
            },
            dismissButton = { TextButton({ confirmUninstall = false }) { Text("Cancel") } },
        )
    }
}

/** Log lines shown before the list is more scrolling than reading. */
private const val LOG_LINES = 40

/**
 * Confirms installing a picked `.wmplugin`, showing what it claims to be and
 * what it would be allowed to do.
 *
 * Deliberately the same disclosure the file-manager path produces. There is no
 * route into the store that skips it — a plugin whose capabilities the user
 * never saw is one they never agreed to.
 */
@Composable
private fun PluginInstallDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val read = remember(uri) {
        runCatching {
            context.contentResolver.requireInputStream(uri).use { PluginFile.readManifest(it) }
        }.getOrNull()
    }
    val ok = read as? PluginManifestResult.Ok

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ok?.let { "Install “${it.manifest.name}”?" } ?: "Can't read that plugin") },
        text = {
            Text(
                ok?.let { manifest ->
                    buildString {
                        append(manifest.manifest.description.ifBlank { "A WM Keyboard plugin." })
                        append("\n\nVersion ${manifest.manifest.pluginVersion}")
                        if (manifest.manifest.author.isNotBlank()) {
                            append(" by ${manifest.manifest.author}")
                        }
                        append("\n\n")
                        if (manifest.permissions.isEmpty()) {
                            append("It doesn't ask for anything beyond its own panel.")
                        } else {
                            append("It can:")
                            for (permission in manifest.permissions) append("\n• ${permission.label}")
                        }
                    }
                } ?: (read as? PluginManifestResult.Rejected)?.reason
                    ?: "That file isn't a WM Keyboard plugin.",
            )
        },
        confirmButton = {
            if (ok != null) {
                TextButton(onClick = {
                    scope.launch {
                        val store = PluginStore.get(context)
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.requireInputStream(uri)
                                    .use { PluginFile.import(it, store) }
                            }.getOrDefault(PluginImportResult.Failed)
                        }
                        onDone(
                            when (result) {
                                is PluginImportResult.Imported ->
                                    "Installed ${result.plugin.name}."

                                is PluginImportResult.Rejected -> result.reason
                                PluginImportResult.NotAPlugin ->
                                    "That file isn't a WM Keyboard plugin."

                                PluginImportResult.TooManyPlugins ->
                                    "You already have ${PluginStore.MAX_PLUGINS} plugins."

                                PluginImportResult.Failed -> "That plugin could not be installed."
                            },
                        )
                    }
                }) { Text("Install") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
