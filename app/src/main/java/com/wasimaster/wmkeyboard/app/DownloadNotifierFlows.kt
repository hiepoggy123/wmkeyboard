package com.wasimaster.wmkeyboard.app

import android.content.Context
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.core.dictionaries.WordlistDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictDownloadManager
import com.wasimaster.wmkeyboard.core.fonts.EmojiFontDownload
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictDownloadManager
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager
import com.wasimaster.wmkeyboard.core.notify.DownloadProgress
import com.wasimaster.wmkeyboard.core.vocab.VocabDownloadManager
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperDownloadManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Each download manager's own status, flattened into the three things the
 * notification shade can say.
 *
 * Every one of these managers publishes a state map keyed by the thing being
 * downloaded, in the same shape — bytes and total while running, a done case,
 * and a failure that carries a resource id because the manager has no Context
 * to resolve it with. The mapping is per manager rather than shared because
 * the cases differ at the edges: one has a queue, one a pause, one a
 * post-processing step, and each of those means something different to
 * someone reading a notification.
 *
 * The stages that are *not* a download — packing a trie, unzipping a pack —
 * deliberately report as running with no size rather than as finished. The
 * file is not usable until they are done, and a notification saying "ready to
 * use" before it is would be a lie the user acts on.
 */
internal object DownloadProgressFlows {

    fun wordlist(context: Context, id: String): Flow<DownloadProgress> =
        WordlistDownloadManager.states.map { states ->
            when (val status = states[id]) {
                is WordlistDownloadManager.DownloadStatus.Downloading ->
                    DownloadProgress.Running(status.bytes, status.totalBytes)
                WordlistDownloadManager.DownloadStatus.Processing ->
                    DownloadProgress.Running(0, 0)
                is WordlistDownloadManager.DownloadStatus.Downloaded -> DownloadProgress.Done
                is WordlistDownloadManager.DownloadStatus.Failed ->
                    DownloadProgress.Failed(context.reason(status.messageRes, status.messageArg))
                WordlistDownloadManager.DownloadStatus.NotDownloaded, null ->
                    DownloadProgress.Gone
            }
        }

    fun emojiDict(context: Context, id: String): Flow<DownloadProgress> =
        EmojiDictDownloadManager.states.map { states ->
            when (val status = states[id]) {
                EmojiDictDownloadManager.DownloadStatus.Queued -> DownloadProgress.Running(0, 0)
                is EmojiDictDownloadManager.DownloadStatus.Downloading ->
                    DownloadProgress.Running(status.bytes, status.totalBytes)
                is EmojiDictDownloadManager.DownloadStatus.Downloaded -> DownloadProgress.Done
                is EmojiDictDownloadManager.DownloadStatus.Failed ->
                    DownloadProgress.Failed(context.reason(status.messageRes, status.messageArg))
                EmojiDictDownloadManager.DownloadStatus.NotDownloaded, null ->
                    DownloadProgress.Gone
            }
        }

    fun cjkDict(context: Context, id: String): Flow<DownloadProgress> =
        CjkDictDownloadManager.states.map { states ->
            when (val status = states[id]) {
                is CjkDictDownloadManager.DownloadStatus.Downloading ->
                    DownloadProgress.Running(status.bytes, status.total)
                CjkDictDownloadManager.DownloadStatus.Downloaded -> DownloadProgress.Done
                is CjkDictDownloadManager.DownloadStatus.Failed ->
                    DownloadProgress.Failed(
                        context.reason(status.messageRes, status.messageArg.orEmpty()),
                    )
                // A paused download is resumable and the user paused it, so the
                // shade has nothing to add.
                is CjkDictDownloadManager.DownloadStatus.Paused,
                CjkDictDownloadManager.DownloadStatus.NotDownloaded,
                null,
                -> DownloadProgress.Gone
            }
        }

    fun whisper(context: Context, id: String): Flow<DownloadProgress> =
        WhisperDownloadManager.states.map { states ->
            when (val status = states[id]) {
                is WhisperDownloadManager.DownloadStatus.Downloading ->
                    DownloadProgress.Running(status.bytes, status.total)
                WhisperDownloadManager.DownloadStatus.Downloaded -> DownloadProgress.Done
                is WhisperDownloadManager.DownloadStatus.Failed ->
                    DownloadProgress.Failed(context.reason(status.messageRes, status.messageArg))
                // Paused is resumable and the user paused it: the shade has
                // nothing to add, exactly as for a paused dictionary pack.
                is WhisperDownloadManager.DownloadStatus.Paused,
                WhisperDownloadManager.DownloadStatus.NotDownloaded,
                null,
                -> DownloadProgress.Gone
            }
        }

    fun localLlm(context: Context, id: String): Flow<DownloadProgress> =
        LocalLlmDownloadManager.states.map { states ->
            when (val status = states[id]) {
                is LocalLlmDownloadManager.DownloadStatus.Downloading ->
                    DownloadProgress.Running(status.bytes, status.total)
                LocalLlmDownloadManager.DownloadStatus.Downloaded -> DownloadProgress.Done
                is LocalLlmDownloadManager.DownloadStatus.Failed ->
                    DownloadProgress.Failed(context.reason(status.messageRes, status.messageArg))
                // Paused is resumable and the user paused it: the shade has
                // nothing to add, exactly as for a paused dictionary pack.
                is LocalLlmDownloadManager.DownloadStatus.Paused,
                LocalLlmDownloadManager.DownloadStatus.NotDownloaded,
                null,
                -> DownloadProgress.Gone
            }
        }

    fun vocab(context: Context, key: String): Flow<DownloadProgress> =
        VocabDownloadManager.states.map { states ->
            when (val status = states[key]) {
                VocabDownloadManager.DownloadStatus.Queued -> DownloadProgress.Running(0, 0)
                is VocabDownloadManager.DownloadStatus.Downloading ->
                    DownloadProgress.Running(status.bytes, status.totalBytes)
                is VocabDownloadManager.DownloadStatus.Downloaded -> DownloadProgress.Done
                is VocabDownloadManager.DownloadStatus.Failed ->
                    DownloadProgress.Failed(context.reason(status.messageRes, status.messageArg))
                VocabDownloadManager.DownloadStatus.NotDownloaded, null -> DownloadProgress.Gone
            }
        }

    fun emojiFont(context: Context): Flow<DownloadProgress> =
        EmojiFontDownload.state.map { status ->
            when (status) {
                is EmojiFontDownload.Status.Downloading ->
                    DownloadProgress.Running(status.bytes, status.total)
                is EmojiFontDownload.Status.Installed -> DownloadProgress.Done
                is EmojiFontDownload.Status.Failed ->
                    DownloadProgress.Failed(context.getString(status.messageRes))
                EmojiFontDownload.Status.Idle -> DownloadProgress.Gone
            }
        }

    /** The failure sentence, in the shape every one of these managers carries it. */
    private fun Context.reason(@StringRes messageRes: Int, messageArg: String): String =
        if (messageArg.isEmpty()) getString(messageRes) else getString(messageRes, messageArg)
}
