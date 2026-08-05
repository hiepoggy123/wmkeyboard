package com.wasimaster.wmkeyboard.core.dictionaries

import com.wasimaster.wmkeyboard.core.prediction.NgramPack
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.PackedTrieCodec
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches [NgramPackCatalog] packs and compiles them on device into
 * `dict/<langId>/bigrams.wmdict` + `trigrams.wmdict`, next to that
 * language's downloaded wordlist.
 *
 * Pipeline per file: HTTP stream -> gzip inflate -> parse `words count`
 * lines (count-descending, so the read simply stops at the cap) -> join the
 * words with NUL into one trie key -> `PackedTrie.of` -> codec write ->
 * `.part` -> atomic rename. Presence == valid, exactly like the wordlists.
 *
 * Modeled on `EmojiDictDownloadManager`'s automatic pass: [ensure] is
 * silent, queues one language at a time, and a language that fails is
 * remembered for the process rather than retried on every settings emission.
 * There is no per-row UI yet, so there is no status flow — [completions] is
 * the one signal, telling the IME to re-map the freshly landed pack.
 */
object NgramPackDownloadManager {

    private const val USER_AGENT = "WMKeyboard ngram pack downloader"

    /** Compile caps: the lists are count-descending, so taking the head
     * keeps the mass that matters. Sized so the on-device compile stays in
     * tens of MB of transient heap and the mapped file in the tens of MB. */
    private const val MAX_BIGRAMS = 150_000
    private const val MAX_TRIGRAMS = 75_000

    /** Words longer than this are junk lines, not vocabulary. */
    private const val MAX_WORD_LENGTH = 48

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val jobs = HashMap<String, Job>()
    private val givenUp = HashSet<String>()

    private val _completions = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Emits a language id once both of its pack files are on disk. */
    val completions: SharedFlow<String> = _completions.asSharedFlow()

    fun bigramFile(filesDir: File, langId: String): File =
        File(File(File(filesDir, "dict"), langId), "bigrams.wmdict")

    fun trigramFile(filesDir: File, langId: String): File =
        File(File(File(filesDir, "dict"), langId), "trigrams.wmdict")

    fun isDownloaded(filesDir: File, langId: String): Boolean =
        bigramFile(filesDir, langId).isFile || trigramFile(filesDir, langId).isFile

    /**
     * Queues every catalogued language in [langIds] the device does not
     * already have. Silent by design: the user did not ask, so a failure is
     * remembered ([givenUp]) rather than surfaced, until the next process.
     */
    fun ensure(filesDir: File, langIds: Collection<String>) {
        val wanted = langIds.distinct()
        scope.launch {
            for (langId in wanted) {
                val entry = NgramPackCatalog.forLanguage(langId) ?: continue
                synchronized(jobs) {
                    if (langId in givenUp || jobs[langId]?.isActive == true) return@synchronized
                    if (isDownloaded(filesDir, langId)) return@synchronized
                    jobs[langId] = scope.launch {
                        gate.withLock { download(filesDir, entry) }
                    }
                }
            }
        }
    }

    private suspend fun download(filesDir: File, entry: NgramPackEntry) {
        val langId = entry.languageId
        if (isDownloaded(filesDir, langId)) return
        val ok = runCatching {
            compileOne(entry.bigramUrl(), bigramFile(filesDir, langId), MAX_BIGRAMS, parts = 2)
            compileOne(entry.trigramUrl(), trigramFile(filesDir, langId), MAX_TRIGRAMS, parts = 3)
        }.isSuccess
        if (ok) {
            _completions.tryEmit(langId)
        } else {
            // Half a pack is worse than none: a bigram file without its
            // trigram sibling would look downloaded forever.
            bigramFile(filesDir, langId).delete()
            trigramFile(filesDir, langId).delete()
            synchronized(jobs) { givenUp.add(langId) }
        }
    }

    private fun compileOne(url: String, target: File, cap: Int, parts: Int) {
        val entries = ArrayList<Pair<String, Int>>(cap)
        openStream(url).use { raw ->
            GZIPInputStream(raw).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (entries.size >= cap) break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val separator = trimmed.lastIndexOf(' ')
                    if (separator <= 0) continue
                    val count = trimmed.substring(separator + 1).toIntOrNull() ?: continue
                    val words = trimmed.substring(0, separator).trim().split(' ')
                    if (words.size != parts) continue
                    if (words.any { it.isEmpty() || it.length > MAX_WORD_LENGTH }) continue
                    entries.add(NgramPack.key(*words.toTypedArray()) to count)
                }
            }
        }
        if (entries.isEmpty()) throw IOException("empty ngram list at $url")
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.outputStream().use { out ->
            PackedTrieCodec.write(PackedTrie.of(entries), out)
        }
        if (!part.renameTo(target)) throw IOException("rename failed for $target")
    }

    private fun openStream(url: String): InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream
    }
}
