package com.wasimaster.wmkeyboard.app

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.addons.AddonDownloadManager
import com.wasimaster.wmkeyboard.core.addons.AddonEntry
import com.wasimaster.wmkeyboard.core.addons.AddonRepoCodec
import com.wasimaster.wmkeyboard.core.addons.AddonRepoInfo
import com.wasimaster.wmkeyboard.core.addons.AddonRepoManifest
import com.wasimaster.wmkeyboard.core.addons.AddonRepoRef
import com.wasimaster.wmkeyboard.core.addons.AddonStore
import com.wasimaster.wmkeyboard.core.addons.AddonType
import com.wasimaster.wmkeyboard.ime.ui.rememberMediaImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The addon store: repositories the user added, what each one offers, and what
 * is installed from them.
 *
 * Three screens — the repository list, one repository's catalogue, and a single
 * addon's detail page. The detail route is keyed by the repository's *URL*
 * rather than its position in the list, because a `wmkeyboard://` deep link
 * carries a URL and has no idea what order anyone's repositories are in.
 */

// ---- routes ----------------------------------------------------------

/** `addon_repo/{repoUrl}` with the URL percent-encoded into the path. */
internal fun addonRepoRoute(manifestUrl: String): String =
    "addon_repo/${java.net.URLEncoder.encode(manifestUrl, "UTF-8")}"

/** The Addons screen with the add-repository dialog pre-filled from a link. */
internal fun addonsAddRoute(manifestUrl: String): String =
    "addons?add=${java.net.URLEncoder.encode(manifestUrl, "UTF-8")}"

/** `addon/{repoUrl}/{addonId}`, both segments percent-encoded. */
internal fun addonDetailRoute(manifestUrl: String, addonId: String): String =
    "addon/${java.net.URLEncoder.encode(manifestUrl, "UTF-8")}/" +
        java.net.URLEncoder.encode(addonId, "UTF-8")

internal fun decodeRouteArg(value: String?): String =
    runCatching { java.net.URLDecoder.decode(value.orEmpty(), "UTF-8") }.getOrDefault("")

// ---- repository list -------------------------------------------------

/**
 * [prefillUrl] comes from a `wmkeyboard://repo?url=…` link: it opens the add
 * dialog with the address filled in, so the user still sees what they are
 * trusting and still has to confirm.
 */
@Composable
internal fun AddonsScreen(prefillUrl: String = "", onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AddonStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val repos = remember(revision) { store.repos() }
    val installed = remember(revision) { store.installed() }

    var showAdd by remember { mutableStateOf(prefillUrl.isNotBlank()) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Seed the sample repository the first time this screen is opened rather
    // than at startup: it costs nothing until someone actually looks for
    // addons, and it keeps the seeding decision next to the UI that explains it.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { store.seedIfNeeded() }
    }

    fun refreshAll() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            withContext(Dispatchers.IO) {
                for (ref in store.repos()) {
                    AddonDownloadManager.fetchManifest(store, ref, context.cacheDir)
                }
            }
            refreshing = false
        }
    }

    // One fetch per visit, so a repository that published something new shows
    // it without the user having to know to pull to refresh.
    LaunchedEffect(Unit) { refreshAll() }

    if (showAdd) {
        AddRepositoryDialog(
            initialUrl = prefillUrl,
            onDismiss = { showAdd = false },
            onAdd = { pasted ->
                showAdd = false
                scope.launch {
                    val ref = store.addRepo(pasted)
                    if (ref == null) {
                        message = "That doesn't look like a repository URL. It has to be an " +
                            "https link — a GitHub repository, or a direct link to a " +
                            "${AddonRepoCodec.MANIFEST_NAME} file."
                        return@launch
                    }
                    val manifest = withContext(Dispatchers.IO) {
                        AddonDownloadManager.fetchManifest(store, ref, context.cacheDir)
                    }
                    if (manifest == null) {
                        store.removeRepo(ref.manifestUrl)
                        message = "Couldn't read an addon repository at that address."
                    } else {
                        onNavigate(addonRepoRoute(ref.manifestUrl))
                    }
                }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    CaptionText(
        "Addon repositories are ordinary web pages listing themes, layouts, " +
            "dictionaries, snippets, sticker packs, icon packs, fonts and key " +
            "sounds. Everything they hold is plain data — installing an addon " +
            "never runs code.",
    )

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { showAdd = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add repository")
        }
        OutlinedButton(onClick = ::refreshAll, enabled = !refreshing && repos.isNotEmpty()) {
            Text(if (refreshing) "Refreshing…" else "Refresh")
        }
    }

    if (repos.isEmpty()) {
        CaptionText("No repositories yet. Add one with its URL to browse what it offers.")
    }

    SettingsGroup(if (repos.isEmpty()) null else "Repositories") {
        for (ref in repos) {
            item { RepositoryRow(ref, store, onNavigate) }
        }
    }

    if (installed.isNotEmpty()) {
        SettingsGroup("Installed") {
            for ((key, record) in installed) {
                item {
                    ListItem(
                        headlineContent = { Text(record.name.ifBlank { key }) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(record.type.label.removeSuffix("s"))
                                    if (record.version.isNotBlank()) append(" · ${record.version}")
                                    if (record.repoName.isNotBlank()) append(" · ${record.repoName}")
                                },
                            )
                        },
                        colors = transparentListColors(),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RepositoryRow(
    ref: AddonRepoRef,
    store: AddonStore,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manifest = remember(ref.cachedManifest) { AddonDownloadManager.cachedManifest(ref) }
    var menu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(manifest?.repo?.name?.ifBlank { null } ?: ref.url) },
        supportingContent = {
            Text(
                when {
                    manifest == null && ref.fetchedAt == 0L -> "Not loaded yet"
                    manifest == null -> "Couldn't be read"
                    else -> buildString {
                        append("${manifest.addons.size} addons")
                        if (manifest.repo.author.isNotBlank()) append(" · ${manifest.repo.author}")
                    }
                },
            )
        },
        leadingContent = manifest?.repo?.icon?.let { icon ->
            AddonRepoCodec.resolveAsset(ref.manifestUrl, icon)?.let { url ->
                {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        imageLoader = rememberMediaImageLoader(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Repository options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            menu = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AddonDownloadManager.fetchManifest(store, ref, context.cacheDir)
                                }
                            }
                        },
                    )
                    val homepage = manifest?.repo?.homepage.orEmpty()
                    if (homepage.startsWith("https://")) {
                        DropdownMenuItem(
                            text = { Text("Open homepage") },
                            onClick = {
                                menu = false
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, homepage.toUri()),
                                    )
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            menu = false
                            store.removeRepo(ref.manifestUrl)
                        },
                    )
                }
            }
        },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = manifest != null) { onNavigate(addonRepoRoute(ref.manifestUrl)) },
    )
}

@Composable
private fun AddRepositoryDialog(
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialUrl) }
    val resolved = remember(text) { AddonRepoCodec.resolveManifestUrl(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add repository") },
        text = {
            Column {
                Text(
                    "Paste the repository's address — a GitHub link, or a direct " +
                        "link to its ${AddonRepoCodec.MANIFEST_NAME}.",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Repository URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    // Show what will actually be fetched, so a lookalike host is
                    // visible before it is trusted.
                    Text(
                        resolved?.let { "Will read: $it" }
                            ?: "That isn't an https address this app can read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (resolved == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = resolved != null) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- one repository --------------------------------------------------

@Composable
internal fun AddonRepoScreen(manifestUrl: String, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { AddonStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val ref = remember(revision, manifestUrl) { store.repo(manifestUrl) }
    val manifest = remember(ref?.cachedManifest) { ref?.let { AddonDownloadManager.cachedManifest(it) } }

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<AddonType?>(null) }

    if (ref == null || manifest == null) {
        CaptionText("This repository couldn't be read. Try refreshing it from the Addons list.")
        return
    }

    LaunchedEffect(manifest) {
        AddonDownloadManager.refresh(store, manifest.repo.id, manifest)
    }

    if (manifest.repo.description.isNotBlank()) CaptionText(manifest.repo.description)

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        label = { Text("Search") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val presentTypes = remember(manifest) { manifest.addons.map { it.type }.distinct() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = typeFilter == null,
            onClick = { typeFilter = null },
            label = { Text("All") },
        )
        for (type in presentTypes) {
            FilterChip(
                selected = typeFilter == type,
                onClick = { typeFilter = if (typeFilter == type) null else type },
                label = { Text(type.label) },
            )
        }
    }

    val shown = remember(manifest, query, typeFilter) {
        manifest.addons.filter { entry ->
            (typeFilter == null || entry.type == typeFilter) && entry.matches(query)
        }
    }

    if (shown.isEmpty()) CaptionText("Nothing here matches that.")

    SettingsGroup {
        for (entry in shown) {
            item {
                AddonRow(entry, manifest.repo) {
                    onNavigate(addonDetailRoute(manifestUrl, entry.id))
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

private fun AddonEntry.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return name.contains(needle, ignoreCase = true) ||
        description.contains(needle, ignoreCase = true) ||
        author.contains(needle, ignoreCase = true) ||
        tags.any { it.contains(needle, ignoreCase = true) }
}

@Composable
private fun AddonRow(entry: AddonEntry, repo: AddonRepoInfo, onClick: () -> Unit) {
    val states by AddonDownloadManager.states.collectAsStateWithLifecycle()
    val status = states[entry.key(repo.id)] ?: AddonDownloadManager.AddonStatus.NotInstalled

    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                buildString {
                    append(entry.type.label.removeSuffix("s"))
                    if (entry.version.isNotBlank()) append(" · ${entry.version}")
                    entry.sizeBytes?.let { append(" · ${formatBytes(it)}") }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { StatusBadge(status) },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun StatusBadge(status: AddonDownloadManager.AddonStatus) {
    when (status) {
        is AddonDownloadManager.AddonStatus.Installed ->
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Installed",
                tint = MaterialTheme.colorScheme.primary,
            )
        is AddonDownloadManager.AddonStatus.UpdateAvailable ->
            AssistChip(onClick = {}, label = { Text("Update") })
        is AddonDownloadManager.AddonStatus.Downloading,
        AddonDownloadManager.AddonStatus.Verifying,
        AddonDownloadManager.AddonStatus.Installing,
        -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
        is AddonDownloadManager.AddonStatus.Failed ->
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Failed",
                tint = MaterialTheme.colorScheme.error,
            )
        AddonDownloadManager.AddonStatus.NotInstalled ->
            Icon(Icons.Outlined.Download, contentDescription = "Not installed")
    }
}

// ---- one addon -------------------------------------------------------

@Composable
internal fun AddonDetailScreen(manifestUrl: String, addonId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AddonStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val ref = remember(revision, manifestUrl) { store.repo(manifestUrl) }
    var manifest by remember(ref?.cachedManifest) {
        mutableStateOf(ref?.let { AddonDownloadManager.cachedManifest(it) })
    }

    // A deep link can point at a repository the user has not added. Fetch its
    // manifest so the addon can be shown, but do NOT add the repository — a
    // link must not be able to change the user's repository list on its own.
    // Installing is what adds it, and that is the user's own tap.
    LaunchedEffect(manifestUrl) {
        if (manifest != null) return@LaunchedEffect
        manifest = withContext(Dispatchers.IO) {
            AddonDownloadManager.fetchManifest(manifestUrl, context.cacheDir)
        }
    }

    val loaded = manifest
    val entry = loaded?.addons?.firstOrNull { it.id == addonId }
    if (loaded == null || entry == null) {
        CaptionText("That addon couldn't be found. The repository may have changed.")
        return
    }

    val states by AddonDownloadManager.states.collectAsStateWithLifecycle()
    val key = entry.key(loaded.repo.id)
    val status = states[key] ?: AddonDownloadManager.AddonStatus.NotInstalled
    LaunchedEffect(loaded) { AddonDownloadManager.refresh(store, loaded.repo.id, loaded) }

    val previews = remember(entry, manifestUrl) {
        entry.previews.mapNotNull { AddonRepoCodec.resolveAsset(manifestUrl, it) }
    }
    if (previews.isNotEmpty()) {
        val loader = rememberMediaImageLoader()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (url in previews) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    imageLoader = loader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(entry.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append(entry.type.label.removeSuffix("s"))
                if (entry.version.isNotBlank()) append(" · ${entry.version}")
                if (entry.author.isNotBlank()) append(" · ${entry.author}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(entry.description, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (entry.tags.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (tag in entry.tags) AssistChip(onClick = {}, label = { Text(tag) })
        }
    }

    SettingsGroup("Details") {
        item {
            // The host is the security-relevant fact — a repository can call
            // itself anything, so show where the file actually comes from.
            val host = remember(manifestUrl) {
                runCatching { manifestUrl.toUri().host }.getOrNull().orEmpty()
            }
            DetailRow(
                "Repository",
                buildString {
                    append(loaded.repo.name.ifBlank { "Unnamed repository" })
                    if (host.isNotBlank()) append("\n$host")
                    if (ref == null) append("\nNot in your list — installing adds it")
                },
            )
        }
        entry.sizeBytes?.let { item { DetailRow("Size", formatBytes(it)) } }
        entry.langId?.let { item { DetailRow("Language", it) } }
        item {
            DetailRow(
                "Integrity",
                if (entry.sha256 != null) {
                    "Checksum published — verified before installing"
                } else {
                    // Not a warning. The field is optional by design and most
                    // hand-written manifests won't have it.
                    "No checksum published — downloaded over https"
                },
            )
        }
    }

    val tooOld = entry.minAppVersion != null && entry.minAppVersion > BuildConfig.VERSION_CODE
    if (tooOld) {
        CaptionText("This addon needs a newer version of WM Keyboard.")
    }

    when (status) {
        is AddonDownloadManager.AddonStatus.Downloading -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (status.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (status.bytes.toFloat() / status.totalBytes).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (status.totalBytes > 0) {
                        "${formatBytes(status.bytes)} of ${formatBytes(status.totalBytes)}"
                    } else {
                        formatBytes(status.bytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { AddonDownloadManager.cancel() }) { Text("Cancel") }
            }
        }

        AddonDownloadManager.AddonStatus.Verifying,
        AddonDownloadManager.AddonStatus.Installing,
        -> CaptionText(
            if (status == AddonDownloadManager.AddonStatus.Verifying) {
                "Checking the download…"
            } else {
                "Installing…"
            },
        )

        else -> {
            val installed = status is AddonDownloadManager.AddonStatus.Installed
            val updatable = status is AddonDownloadManager.AddonStatus.UpdateAvailable
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        // Following a link doesn't add the repository; choosing
                        // to install from it does. No-op when it is already there.
                        store.addRepo(manifestUrl)
                        AddonDownloadManager.install(
                            context = context,
                            store = store,
                            manifestUrl = manifestUrl,
                            repo = loaded.repo,
                            entry = entry,
                            appVersionCode = BuildConfig.VERSION_CODE,
                        )
                    },
                    enabled = !tooOld && !installed,
                ) {
                    Text(
                        when {
                            updatable -> "Update"
                            installed -> "Installed"
                            else -> "Install"
                        },
                    )
                }
                if (installed || updatable) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            AddonDownloadManager.uninstall(context, store, key, entry)
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall")
                    }
                }
            }
            if (status is AddonDownloadManager.AddonStatus.Failed) {
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
}

@Composable
private fun DetailRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        colors = transparentListColors(),
    )
}

