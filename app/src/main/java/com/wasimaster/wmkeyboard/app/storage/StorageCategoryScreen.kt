package com.wasimaster.wmkeyboard.app.storage

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.app.CaptionText
import com.wasimaster.wmkeyboard.app.SettingsGroup
import com.wasimaster.wmkeyboard.app.WmRow
import com.wasimaster.wmkeyboard.app.formatBytes
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One category, item by item.
 *
 * Deliberately generic: every category gets the same screen rather than
 * twenty-odd bespoke ones, because the question is always the same — what is in
 * here, how big is each of them, and can this one go. Where a category already
 * has a proper management screen (voice models, sticker packs, plugins) this
 * one links across to it instead of trying to be it.
 */
@Composable
internal fun StorageCategoryScreen(
    categoryId: String,
    repository: SettingsRepository,
    onOpenRoute: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val category = remember(categoryId) { StorageCategories.byId(categoryId) } ?: return
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmItem by remember { mutableStateOf<StorageItem?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    val items by produceState(emptyList<StorageItem>(), categoryId, revision) {
        value = withContext(Dispatchers.IO) {
            category.items(StorageEnv(context, repository, storageRoots(context)))
                .sortedByDescending { it.bytes }
        }
    }

    fun perform(work: suspend (StorageEnv) -> Unit) {
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) {
                work(StorageEnv(context, repository, storageRoots(context)))
            }
            busy = false
            revision++
        }
    }

    CaptionText(stringResource(category.subtitle))
    if (category.note != 0) CaptionText(stringResource(category.note))

    if (items.isEmpty()) {
        CaptionText(stringResource(R.string.storage_detail_empty))
    } else {
        SettingsGroup {
            for (item in items) {
                item {
                    StorageItemRow(item, enabled = !busy) { confirmItem = item }
                }
            }
        }
    }

    category.manageRoute?.let { route ->
        SettingsGroup {
            item {
                WmRow(
                    title = stringResource(R.string.storage_detail_manage_action),
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    accent = category.accent,
                    flightTo = route,
                    onClick = { onOpenRoute(route) },
                )
            }
        }
    }

    if (category.clearable && items.any { it.deletable }) {
        TextButton(
            onClick = { confirmAll = true },
            enabled = !busy,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.storage_delete_all_desc, stringResource(category.title)),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    confirmItem?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.storage_confirm_item_title, item.label),
            body = stringResource(bodyForItem(category.danger), formatBytes(item.bytes)),
            onDismiss = { confirmItem = null },
            onConfirm = {
                confirmItem = null
                perform { env -> category.delete(env, item) }
            },
        )
    }

    if (confirmAll) {
        val total = items.filter { it.deletable }.sumOf { it.bytes }
        ConfirmDeleteDialog(
            title = stringResource(R.string.storage_confirm_title, stringResource(category.title)),
            body = stringResource(bodyForItem(category.danger), formatBytes(total)),
            onDismiss = { confirmAll = false },
            onConfirm = {
                confirmAll = false
                perform { env -> category.clear(env) }
            },
        )
    }
}

@Composable
private fun StorageItemRow(item: StorageItem, enabled: Boolean, onDelete: () -> Unit) {
    val deleteDesc = stringResource(R.string.storage_delete_item_desc, item.label)
    WmRow(
        title = item.label.ifBlank { stringResource(R.string.storage_item_unknown_label) },
        subtitle = item.detail,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatBytes(item.bytes), style = MaterialTheme.typography.labelLarge)
                if (item.deletable) {
                    IconButton(onClick = onDelete, enabled = enabled) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = deleteDesc,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

private fun bodyForItem(danger: Danger): Int = when (danger) {
    Danger.NONE -> R.string.storage_confirm_free_body
    Danger.REDOWNLOAD -> R.string.storage_confirm_redownload_body
    Danger.PERSONAL, Danger.RESET -> R.string.storage_confirm_personal_body
}
