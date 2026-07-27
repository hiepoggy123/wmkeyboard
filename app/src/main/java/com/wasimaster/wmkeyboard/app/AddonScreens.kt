package com.wasimaster.wmkeyboard.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.addons.AddonDownloadManager
import com.wasimaster.wmkeyboard.core.addons.AddonEntry
import com.wasimaster.wmkeyboard.core.addons.AddonPreviewContent
import com.wasimaster.wmkeyboard.core.addons.AddonPreviewReader
import com.wasimaster.wmkeyboard.core.addons.AddonReconciler
import com.wasimaster.wmkeyboard.core.addons.AddonRepoCodec
import com.wasimaster.wmkeyboard.core.addons.AddonRepoInfo
import com.wasimaster.wmkeyboard.core.addons.AddonRepoRef
import com.wasimaster.wmkeyboard.core.addons.AddonStore
import com.wasimaster.wmkeyboard.core.addons.AddonType
import com.wasimaster.wmkeyboard.core.addons.InstalledAddon
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
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

/**
 * Stands in for the repository URL when an installed addon has none.
 *
 * Records written before the URL was stored don't have one, and neither does
 * one whose repository has been removed — or, as happened here, renamed, since
 * the id in the install key then matches no repository at all. The addon is
 * still installed and still has to be manageable, so the route carries this
 * instead and the page resolves the record by addon id. Not a valid URL by
 * construction: nothing will try to fetch it.
 */
private const val NO_REPO = "-"

// ---- per-type identity -----------------------------------------------

/**
 * Each addon type's glyph and colour.
 *
 * A catalogue is a wall of cards that all look alike, and "Icon pack" reads the
 * same as "Sticker pack" at a glance. A consistent glyph and hue per type makes
 * the wall scannable — and lets the filter chips, the cards and the detail page
 * agree on what a theme looks like.
 */
private val AddonType.icon
    get() = when (this) {
        AddonType.Theme -> Icons.Outlined.Palette
        AddonType.Layout -> Icons.Outlined.Keyboard
        AddonType.Dictionary -> Icons.AutoMirrored.Outlined.MenuBook
        AddonType.Snippets -> Icons.Outlined.Description
        AddonType.Stickers -> Icons.Outlined.EmojiEmotions
        AddonType.IconPack -> Icons.Outlined.Category
        AddonType.Font -> Icons.Outlined.TextFields
        AddonType.EmojiFont -> Icons.Outlined.Mood
        AddonType.Sound -> Icons.Outlined.GraphicEq
        AddonType.Unknown -> Icons.Outlined.Extension
    }

/**
 * The type's hue, before it is adapted to the theme.
 *
 * Fixed rather than derived from the Material scheme: the point is that the
 * types are told apart from each other, which a single accent hue can't do.
 * [tintFor] is what makes each one legible in the current theme.
 */
private val AddonType.seed: Color
    get() = when (this) {
        AddonType.Theme -> Color(0xFF7E57C2)
        AddonType.Layout -> Color(0xFF3B82F6)
        AddonType.Dictionary -> Color(0xFF14B8A6)
        AddonType.Snippets -> Color(0xFFF59E0B)
        AddonType.Stickers -> Color(0xFFEC4899)
        AddonType.IconPack -> Color(0xFF22A559)
        AddonType.Font -> Color(0xFF6366F1)
        AddonType.EmojiFont -> Color(0xFFEAB308)
        AddonType.Sound -> Color(0xFFEF4444)
        AddonType.Unknown -> Color(0xFF6B7280)
    }

/**
 * The type's hue pulled toward legibility on the current surface: darkened on a
 * light theme, lifted on a dark one. Amber on white and indigo on near-black are
 * both unreadable untreated.
 */
@Composable
private fun tintFor(type: AddonType): Color {
    val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()
    return remember(type, dark) {
        if (dark) lerp(type.seed, Color.White, 0.28f) else lerp(type.seed, Color.Black, 0.3f)
    }
}

private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

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
        withContext(Dispatchers.IO) {
            store.seedIfNeeded()
            // Anything uninstalled from its own settings screen since the last
            // visit stops claiming to be installed here.
            AddonReconciler.reconcile(context, store)
        }
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
            "dictionaries, snippets, sticker packs, icon packs, fonts, emoji " +
            "fonts and key sounds. Everything they hold is plain data — " +
            "installing an addon never runs code.",
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
        // A record stores the manifest it came from; ones written before that
        // field existed don't, so fall back to matching the repository by the
        // id embedded in the install key.
        val urlByRepoId = remember(repos) {
            repos.mapNotNull { ref ->
                AddonDownloadManager.cachedManifest(ref)?.repo?.id?.let { it to ref.manifestUrl }
            }.toMap()
        }
        SettingsGroup("Installed") {
            for ((key, record) in installed) {
                item {
                    val url = record.manifestUrl
                        .ifBlank { urlByRepoId[key.substringBeforeLast('/')].orEmpty() }
                        .ifBlank { NO_REPO }
                    ListItem(
                        headlineContent = { Text(record.name.ifBlank { key }) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(record.type.singularLabel)
                                    if (record.version.isNotBlank()) append(" · ${record.version}")
                                    if (record.repoName.isNotBlank()) append(" · ${record.repoName}")
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                record.type.icon,
                                contentDescription = null,
                                tint = tintFor(record.type),
                            )
                        },
                        colors = transparentListColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigate(addonDetailRoute(url, key.substringAfterLast('/')))
                            },
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

    // rememberSaveable, not remember: opening an addon and coming back should
    // land on the same filtered list you left. Navigation keeps a route's
    // saveable state while it sits on the back stack, so this survives the trip
    // where a plain remember is thrown away with the composition.
    var query by rememberSaveable(manifestUrl) { mutableStateOf("") }
    // Stored by name rather than as the enum — Bundle can hold a String.
    var typeFilterName by rememberSaveable(manifestUrl) { mutableStateOf("") }
    val typeFilter = remember(typeFilterName) {
        typeFilterName.takeIf { it.isNotEmpty() }
            ?.let { name -> AddonType.entries.firstOrNull { it.name == name } }
    }

    if (ref == null || manifest == null) {
        CaptionText("This repository couldn't be read. Try refreshing it from the Addons list.")
        return
    }

    LaunchedEffect(manifest) {
        // Reconcile before recomputing statuses, so a theme deleted from the
        // Themes screen shows as available again rather than installed.
        withContext(Dispatchers.IO) { AddonReconciler.reconcile(context, store) }
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

    val presentTypes = remember(manifest) {
        // Catalogue order, not manifest order, so the chip row doesn't reshuffle
        // between two repositories that list the same types.
        AddonType.entries.filter { type -> manifest.addons.any { it.type == type } }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = typeFilter == null,
            onClick = { typeFilterName = "" },
            label = { Text("All") },
        )
        for (type in presentTypes) {
            val tint = tintFor(type)
            FilterChip(
                selected = typeFilter == type,
                onClick = { typeFilterName = if (typeFilter == type) "" else type.name },
                label = { Text(type.label) },
                leadingIcon = {
                    Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tint.copy(alpha = 0.18f),
                    selectedLabelColor = tint,
                    selectedLeadingIconColor = tint,
                    iconColor = tint,
                ),
            )
        }
    }

    val shown = remember(manifest, query, typeFilter) {
        manifest.addons.filter { entry ->
            (typeFilter == null || entry.type == typeFilter) && entry.matches(query)
        }
    }

    if (shown.isEmpty()) CaptionText("Nothing here matches that.")

    // Two per row, the same shape the themes gallery uses. A card can show the
    // addon's first screenshot, which a list row cannot, and screenshots are
    // most of what tells two themes apart.
    Spacer(Modifier.height(8.dp))
    for (row in shown.chunked(2)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            for (entry in row) {
                Box(modifier = Modifier.weight(1f)) {
                    AddonCard(entry, manifest.repo, manifestUrl) {
                        onNavigate(addonDetailRoute(manifestUrl, entry.id))
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
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

/**
 * One addon in the catalogue grid: its first screenshot (or its type's glyph
 * when it has none), then the name and the metadata under it.
 */
@Composable
private fun AddonCard(
    entry: AddonEntry,
    repo: AddonRepoInfo,
    manifestUrl: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AddonStore.get(context) }
    val states by AddonDownloadManager.states.collectAsStateWithLifecycle()
    val status = states[entry.key(repo.id)] ?: AddonDownloadManager.AddonStatus.NotInstalled
    val tint = tintFor(entry.type)
    val preview = remember(entry, manifestUrl) {
        entry.previews.firstNotNullOfOrNull { AddonRepoCodec.resolveAsset(manifestUrl, it) }
    }

    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    imageLoader = rememberMediaImageLoader(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    entry.type.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(40.dp),
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                StatusBadge(status, hasPreview = preview != null) {
                    // Following a link doesn't add the repository; choosing to
                    // install from it does. No-op when it is already there.
                    store.addRepo(manifestUrl)
                    AddonDownloadManager.install(
                        context = context,
                        store = store,
                        manifestUrl = manifestUrl,
                        repo = repo,
                        entry = entry,
                        appVersionCode = BuildConfig.VERSION_CODE,
                    )
                }
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Text(
            entry.type.singularLabel,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            buildString {
                if (entry.version.isNotBlank()) append("v${entry.version}")
                entry.sizeBytes?.let {
                    if (isNotEmpty()) append(" · ")
                    append(formatBytes(it))
                }
                if (entry.author.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(entry.author)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

/**
 * The card's corner control: what this addon's state is, and — when there is
 * something to do about it — the tap that does it.
 *
 * [onInstall] runs on the download arrow and on Update, so the grid installs
 * without a trip through the detail page. It sits on top of a screenshot, so
 * with [hasPreview] it gets an opaque disc behind it; the same glyph over a
 * pale illustration is invisible.
 */
@Composable
private fun StatusBadge(
    status: AddonDownloadManager.AddonStatus,
    hasPreview: Boolean,
    onInstall: () -> Unit,
) {
    val scrim: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (hasPreview) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
                content = { content() },
            )
        } else {
            content()
        }
    }

    when (status) {
        is AddonDownloadManager.AddonStatus.Installed -> scrim {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Installed",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        is AddonDownloadManager.AddonStatus.UpdateAvailable ->
            AssistChip(onClick = onInstall, label = { Text("Update") })
        is AddonDownloadManager.AddonStatus.Downloading,
        AddonDownloadManager.AddonStatus.Verifying,
        AddonDownloadManager.AddonStatus.Installing,
        -> scrim { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
        is AddonDownloadManager.AddonStatus.Failed -> scrim {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Failed",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        AddonDownloadManager.AddonStatus.NotInstalled -> scrim {
            IconButton(onClick = onInstall, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = "Install")
            }
        }
    }
}

// ---- one addon -------------------------------------------------------

/**
 * The settings screen that owns an installed addon of this type — where Use
 * sends the user once it is installed.
 *
 * Every type lands somewhere it can actually be selected; installing a font
 * doesn't pick it for anything on its own, and neither does a layout or a
 * dictionary, so the route is how the install gets finished.
 */
private val AddonType.settingsRoute: String
    get() = when (this) {
        AddonType.Theme -> "themes"
        AddonType.Layout -> "keymaps"
        AddonType.Dictionary -> "customdictionaries"
        AddonType.Snippets -> "tool/SNIPPETS"
        AddonType.Stickers -> "sticker_packs"
        AddonType.IconPack -> "icons"
        AddonType.Font -> "fonts"
        AddonType.EmojiFont -> "emoji"
        AddonType.Sound -> "keypress"
        AddonType.Unknown -> "home"
    }

/**
 * The row or section on [settingsRoute] that actually chooses an addon of this
 * type, matched by title through [SettingsHighlight].
 *
 * Landing on the right screen is only half of Use: "Emoji" is a long screen and
 * the emoji font sits a third of the way down it. Naming the control scrolls to
 * it and flashes it, exactly as picking a search result does.
 *
 * Null where the screen *is* the control — the themes gallery and the layout
 * list are nothing but the choice, so there is nothing to single out.
 */
private val AddonType.settingsAnchor: String?
    get() = when (this) {
        AddonType.Theme -> null
        AddonType.Layout -> null
        AddonType.Dictionary -> null
        AddonType.Snippets -> null
        AddonType.Stickers -> "Your packs"
        AddonType.IconPack -> "Icon pack"
        AddonType.Font -> "Installed fonts"
        AddonType.EmojiFont -> "Emoji font"
        AddonType.Sound -> "Sound style"
        AddonType.Unknown -> null
    }

/**
 * Opens the screen that owns this type, scrolled to the control that picks one.
 *
 * The anchor is armed before navigating because the destination's rows read it
 * during their first composition — the same order the search screen uses.
 */
private fun AddonType.openSettings(onNavigate: (String) -> Unit) {
    settingsAnchor?.let(SettingsHighlight::request)
    onNavigate(settingsRoute)
}

/** What the Use button promises, in the language of the screen it opens. */
private val AddonType.useLabel: String
    get() = when (this) {
        AddonType.Theme -> "Themes"
        AddonType.Layout -> "Key layouts"
        AddonType.Dictionary -> "Custom dictionaries"
        AddonType.Snippets -> "Snippets"
        AddonType.Stickers -> "Sticker packs"
        AddonType.IconPack -> "Icons"
        AddonType.Font -> "Fonts"
        AddonType.EmojiFont -> "Emoji"
        AddonType.Sound -> "Key press"
        AddonType.Unknown -> "Settings"
    }

@Composable
internal fun AddonDetailScreen(
    manifestUrl: String,
    addonId: String,
    onNavigate: (String) -> Unit,
) {
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
        if (manifest != null || !manifestUrl.startsWith("https://")) return@LaunchedEffect
        manifest = withContext(Dispatchers.IO) {
            AddonDownloadManager.fetchManifest(manifestUrl, context.cacheDir)
        }
    }

    val loaded = manifest
    val entry = loaded?.addons?.firstOrNull { it.id == addonId }
    if (loaded == null || entry == null) {
        // No manifest means offline, or a repository that dropped the addon,
        // or one the user removed. If it is installed none of that matters —
        // the record holds everything this page needs, and "manage the thing
        // you installed" should not require a working connection.
        val local = remember(revision, manifestUrl, addonId) {
            store.installedFor(manifestUrl, addonId)
        }
        // Uninstalling from the offline page empties the record out from under
        // it; "that addon couldn't be found" would be a strange thing to say
        // about something the user just removed on this screen.
        var hadLocal by remember(manifestUrl, addonId) { mutableStateOf(false) }
        LaunchedEffect(local != null) { if (local != null) hadLocal = true }
        when {
            local != null -> InstalledAddonDetail(local.first, local.second, store, onNavigate)
            hadLocal -> CaptionText("Uninstalled.")
            else -> CaptionText("That addon couldn't be found. The repository may have changed.")
        }
        return
    }

    val states by AddonDownloadManager.states.collectAsStateWithLifecycle()
    val key = entry.key(loaded.repo.id)
    val status = states[key] ?: AddonDownloadManager.AddonStatus.NotInstalled
    LaunchedEffect(loaded) {
        withContext(Dispatchers.IO) { AddonReconciler.reconcile(context, store) }
        AddonDownloadManager.refresh(store, loaded.repo.id, loaded)
    }

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

    val tint = tintFor(entry.type)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(entry.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                entry.type.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                buildString {
                    append(entry.type.singularLabel)
                    if (entry.version.isNotBlank()) append(" · ${entry.version}")
                    if (entry.author.isNotBlank()) append(" · ${entry.author}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

    if (entry.type.previewable) {
        AddonPreviewSection(manifestUrl, entry)
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
        val languages = entry.languages
        if (languages.isNotEmpty()) {
            item {
                DetailRow(
                    if (languages.size == 1) "Language" else "Languages",
                    languages.joinToString { LanguageRegistry.byId(it).displayName },
                )
            }
        }
        if (entry.hasLicense) {
            item { LicenseRow(manifestUrl, entry) }
        }
    }

    val tooOld = entry.minAppVersion != null && entry.minAppVersion > BuildConfig.VERSION_CODE
    if (tooOld) {
        CaptionText("This addon needs a newer version of WM Keyboard.")
    }

    AddonActions(
        status = status,
        entry = entry,
        repo = loaded.repo,
        manifestUrl = manifestUrl,
        store = store,
        tooOld = tooOld,
        onUninstall = {
            scope.launch { AddonDownloadManager.uninstall(context, store, key, entry) }
        },
        onNavigate = onNavigate,
    )

    Spacer(Modifier.height(24.dp))
}

/**
 * The same page for an addon whose manifest we can't read — offline, repository
 * removed, or the addon delisted — built entirely from what the install
 * recorded.
 *
 * Update isn't offered here: without a manifest there is no version to compare
 * against. Everything else an installed addon's page is for — what it is, where
 * it came from, going to it, removing it — needs no network at all.
 */
@Composable
private fun InstalledAddonDetail(
    key: String,
    record: InstalledAddon,
    store: AddonStore,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tint = tintFor(record.type)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            record.name.ifBlank { key.substringAfterLast('/') },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                record.type.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                buildString {
                    append(record.type.singularLabel)
                    if (record.version.isNotBlank()) append(" · ${record.version}")
                    if (record.author.isNotBlank()) append(" · ${record.author}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (record.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(record.description, style = MaterialTheme.typography.bodyMedium)
        }
    }

    SettingsGroup("Details") {
        item { DetailRow("Status", "Installed") }
        if (record.repoName.isNotBlank()) item { DetailRow("Repository", record.repoName) }
    }
    CaptionText(
        "Showing what was saved when this was installed — the repository " +
            "couldn't be read just now, so there is nothing to check for updates " +
            "against.",
    )

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UninstallButton {
            scope.launch { AddonDownloadManager.uninstall(context, store, key, entry = null) }
        }
    }
    OutlinedButton(
        onClick = { record.type.openSettings(onNavigate) },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Use — open ${record.type.useLabel}")
    }
    Spacer(Modifier.height(24.dp))
}

/** Install / Update / Uninstall / Use, and the progress the transfer reports. */
@Composable
private fun AddonActions(
    status: AddonDownloadManager.AddonStatus,
    entry: AddonEntry,
    repo: AddonRepoInfo,
    manifestUrl: String,
    store: AddonStore,
    tooOld: Boolean,
    onUninstall: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
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

        AddonDownloadManager.AddonStatus.Verifying ->
            CaptionText("Checking the download…")

        AddonDownloadManager.AddonStatus.Installing ->
            CaptionText("Installing…")

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
                            repo = repo,
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
                if (installed || updatable) UninstallButton(onUninstall)
            }
            // Installing puts the file on the device; for most types choosing it
            // is a second step on another screen. This is the way there.
            if (installed || updatable) {
                OutlinedButton(
                    onClick = { entry.type.openSettings(onNavigate) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Use — open ${entry.type.useLabel}")
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
}

/**
 * Uninstall, in the error colour.
 *
 * It sits next to Install and Use and is the only one of the three that takes
 * something away; an outlined button in the default tint reads as one more
 * neutral option.
 */
@Composable
private fun UninstallButton(onClick: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = 0.5f)),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Uninstall")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        colors = transparentListColors(),
    )
}

/**
 * The addon's licence. An identifier shows inline; full text — declared in the
 * manifest or living in a file beside it — opens in a dialog, which is the only
 * honest way to show something that can run to hundreds of lines.
 */
@Composable
private fun LicenseRow(manifestUrl: String, entry: AddonEntry) {
    val context = LocalContext.current
    var showing by remember { mutableStateOf(false) }
    var text by remember(entry.id) { mutableStateOf(entry.licenseText.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    val hasFile = !entry.licenseFile.isNullOrBlank()
    val canShowText = text.isNotBlank() || hasFile

    LaunchedEffect(showing) {
        if (!showing || text.isNotBlank() || !hasFile) return@LaunchedEffect
        loading = true
        text = withContext(Dispatchers.IO) {
            AddonDownloadManager.fetchText(manifestUrl, entry.licenseFile.orEmpty(), context.cacheDir)
        }.orEmpty()
        loading = false
    }

    ListItem(
        headlineContent = { Text("Licence") },
        supportingContent = {
            Text(
                entry.license?.takeIf { it.isNotBlank() }
                    ?: if (canShowText) "Tap to read the licence" else "Not stated",
            )
        },
        leadingContent = { Icon(Icons.Outlined.Gavel, contentDescription = null) },
        trailingContent = if (canShowText) {
            { Icon(Icons.Outlined.Description, contentDescription = null) }
        } else {
            null
        },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canShowText) { showing = true },
    )

    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(entry.license?.takeIf { it.isNotBlank() } ?: "Licence") },
            text = {
                // A licence runs to hundreds of lines; the dialog body scrolls
                // rather than pushing its own buttons off the screen.
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val body = when {
                        loading -> "Loading…"
                        text.isBlank() -> "The licence text couldn't be fetched."
                        else -> text
                    }
                    Text(withLinks(body), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showing = false }) { Text("Close") } },
        )
    }
}

/**
 * Bare URLs in plain text, made tappable.
 *
 * A licence is mostly a pointer: half of them are three lines of attribution
 * and a link to the deed that says what you may actually do. Leaving that as
 * dead text in a dialog nobody can copy out of makes the licence unreadable in
 * the only sense that matters.
 */
private val URL_PATTERN = Regex("""https?://[^\s<>"')\]]+""")

/** Trailing punctuation belongs to the sentence, not to the address. */
private const val URL_TRAILING = ".,;:!?"

@Composable
private fun withLinks(text: String): AnnotatedString {
    // Keyed on the colour, not on the TextLinkStyles: a fresh instance every
    // composition would make the remember do nothing.
    val accent = MaterialTheme.colorScheme.primary
    return remember(text, accent) {
        val style = TextLinkStyles(
            style = SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
        )
        buildAnnotatedString {
            var at = 0
            for (match in URL_PATTERN.findAll(text)) {
                val url = match.value.trimEnd { it in URL_TRAILING }
                if (url.isEmpty()) continue
                append(text.substring(at, match.range.first))
                withLink(LinkAnnotation.Url(url, style)) { append(url) }
                at = match.range.first + url.length
            }
            append(text.substring(at))
        }
    }
}

// ---- payload preview -------------------------------------------------

/**
 * "Show me what's actually in this before I install it."
 *
 * Only offered for the types where the content *is* the choice — the words in a
 * dictionary, the snippets in a pack, the sound itself, the sticker images. It
 * downloads the payload to the cache and reads it; nothing is installed and no
 * setting changes.
 */
@Composable
private fun AddonPreviewSection(manifestUrl: String, entry: AddonEntry) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember(entry.id) { mutableStateOf<AddonPreviewContent?>(null) }
    var loading by remember(entry.id) { mutableStateOf(false) }
    var failed by remember(entry.id) { mutableStateOf(false) }

    if (content == null) {
        OutlinedButton(
            onClick = {
                if (loading) return@OutlinedButton
                loading = true
                failed = false
                scope.launch {
                    val read = withContext(Dispatchers.IO) {
                        AddonDownloadManager.fetchPayload(manifestUrl, entry, context.cacheDir)
                            ?.let { AddonPreviewReader.read(entry, it) }
                    }
                    loading = false
                    if (read == null) failed = true else content = read
                }
            },
            enabled = !loading,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Loading preview…" else "Preview")
        }
        if (failed) {
            Text(
                "The preview couldn't be downloaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        return
    }

    when (val shown = content) {
        is AddonPreviewContent.Snippets -> SettingsGroup("Preview") {
            for (snippet in shown.entries) {
                item {
                    ListItem(
                        headlineContent = { Text(snippet.label) },
                        supportingContent = {
                            Text(snippet.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = snippet.trigger.takeIf { it.isNotBlank() }?.let {
                            { Text(it, style = MaterialTheme.typography.labelSmall) }
                        },
                        colors = transparentListColors(),
                    )
                }
            }
            if (shown.total > shown.entries.size) {
                item { CaptionText("…and ${shown.total - shown.entries.size} more") }
            }
        }

        is AddonPreviewContent.Dictionary -> DictionaryPreview(shown)

        is AddonPreviewContent.Sound -> SettingsGroup("Preview") {
            item {
                ListItem(
                    headlineContent = { Text("Play the sound") },
                    leadingContent = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { AddonSoundPreview.play(shown.file) },
                )
            }
        }

        is AddonPreviewContent.Stickers -> {
            SettingsGroup("Preview") {
                item { CaptionText("${shown.total} stickers") }
            }
            val loader = rememberMediaImageLoader()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (image in shown.images) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        imageLoader = loader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }

        is AddonPreviewContent.Unreadable -> CaptionText(shown.message)
        null -> Unit
    }
}

/** How many words the panel itself shows before the dialog takes over. */
private const val INLINE_WORDS = 60

/**
 * A word list: a taste of it inline, the whole thing in a dialog.
 *
 * "Which words are in here" is the only question a dictionary raises, and a
 * sample can't answer it — the point of installing one is usually a specific
 * vocabulary. The dialog is a real scrolling list rather than more running
 * text, so a long list stays readable.
 */
@Composable
private fun DictionaryPreview(shown: AddonPreviewContent.Dictionary) {
    var listing by remember(shown) { mutableStateOf(false) }

    SettingsGroup("Preview") {
        item {
            CaptionText(
                buildString {
                    append(if (shown.truncated) "Over " else "")
                    append("${shown.total} words")
                },
            )
        }
        item {
            Text(
                shown.words.take(INLINE_WORDS).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            OutlinedButton(
                onClick = { listing = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (shown.partial) "Show ${shown.words.size} words" else "Show all words")
            }
        }
    }

    if (!listing) return
    AlertDialog(
        onDismissRequest = { listing = false },
        title = { Text("${shown.words.size} words") },
        text = {
            Column {
                if (shown.partial) {
                    CaptionText(
                        "The first ${shown.words.size} of ${if (shown.truncated) "over " else ""}" +
                            "${shown.total} — the rest install normally.",
                    )
                }
                // One Text of newline-joined words, not a lazy list. A
                // LazyColumn inside AlertDialog's text slot lays out at its
                // maximum height and then will not scroll — it showed the
                // first fifteen words and ate every drag. This is also cheaper:
                // one composable instead of up to ten thousand.
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        shown.words.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { listing = false }) { Text("Close") } },
    )
}

/**
 * Plays a preview sound off a cache file.
 *
 * `MediaPlayer` rather than the keyboard's own `SoundPool`: the pool is keyed by
 * installed-sound id and exists to fire the same short clip on every keystroke,
 * which is not what this is. One player, released as soon as it finishes.
 */
private object AddonSoundPreview {
    fun play(file: java.io.File) {
        runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release() }
                setOnErrorListener { player, _, _ -> player.release(); true }
                prepare()
                start()
            }
        }
    }
}
