package com.wasimaster.wmkeyboard.core.dictionaries

import com.wasimaster.wmkeyboard.core.prediction.NgramPackBuilder
import com.wasimaster.wmkeyboard.core.prediction.NgramPackCodec
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
 * `dict/<langId>/ngrams.wmng`, next to that language's downloaded wordlist.
 *
 * Pipeline: two HTTP streams -> gzip inflate -> parse `words count` lines
 * (count-descending, so each read simply stops at its cap) -> intern the words
 * into one shared vocabulary -> [NgramPackCodec] write -> `.part` -> atomic
 * rename. Presence == valid, exactly like the wordlists.
 *
 * Both lists land in a single file. They share a vocabulary, so splitting them
 * would store it twice, and one atomic rename makes a half-installed pack
 * impossible rather than something to clean up after.
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

    /** Emits a language id once its pack is on disk. */
    val completions: SharedFlow<String> = _completions.asSharedFlow()

    fun packFile(filesDir: File, langId: String): File =
        File(langDir(filesDir, langId), "ngrams.wmng")

    private fun langDir(filesDir: File, langId: String) =
        File(File(filesDir, "dict"), langId)

    fun isDownloaded(filesDir: File, langId: String): Boolean =
        packFile(filesDir, langId).isFile

    /**
     * Removes the two-file `.wmdict` packs of the format this replaced.
     *
     * Eagerly, before the presence check rather than after a successful
     * download: they are some 17 MB per language of a format nothing reads any
     * more, and leaving them until the new pack lands is exactly the case where
     * a full disk stops the new pack from landing at all.
     */
    private fun deleteLegacy(filesDir: File, langId: String) {
        File(langDir(filesDir, langId), "bigrams.wmdict").delete()
        File(langDir(filesDir, langId), "trigrams.wmdict").delete()
    }

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
                    deleteLegacy(filesDir, langId)
                    if (isDownloaded(filesDir, langId)) return@synchronized
                    jobs[langId] = scope.launch {
                        gate.withLock { download(filesDir, entry) }
                    }
                }
            }
        }
    }

    private fun download(filesDir: File, entry: NgramPackEntry) {
        val langId = entry.languageId
        if (isDownloaded(filesDir, langId)) return
        val ok = runCatching { compile(filesDir, entry) }.isSuccess
        if (ok) {
            _completions.tryEmit(langId)
        } else {
            synchronized(jobs) { givenUp.add(langId) }
        }
    }

    private fun compile(filesDir: File, entry: NgramPackEntry) {
        val builder = NgramPackBuilder()
        read(entry.bigramUrl(), MAX_BIGRAMS, parts = 2) { words, count ->
            builder.addBigram(words[0], words[1], count)
        }
        read(entry.trigramUrl(), MAX_TRIGRAMS, parts = 3) { words, count ->
            builder.addTrigram(words[0], words[1], words[2], count)
        }
        if (builder.isEmpty) throw IOException("empty ngram lists for ${entry.languageId}")
        val target = packFile(filesDir, entry.languageId)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.outputStream().use { out -> NgramPackCodec.write(builder.build(), out) }
        if (!part.renameTo(target)) throw IOException("rename failed for $target")
    }

    private inline fun read(url: String, cap: Int, parts: Int, accept: (List<String>, Int) -> Unit) {
        var taken = 0
        openStream(url).use { raw ->
            GZIPInputStream(raw).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (taken >= cap) break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val separator = trimmed.lastIndexOf(' ')
                    if (separator <= 0) continue
                    val count = trimmed.substring(separator + 1).toIntOrNull() ?: continue
                    val words = trimmed.substring(0, separator).trim().split(' ')
                    if (words.size != parts) continue
                    if (words.any { it.isEmpty() || it.length > MAX_WORD_LENGTH }) continue
                    accept(words.map { it.lowercase() }, count)
                    taken++
                }
            }
        }
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
