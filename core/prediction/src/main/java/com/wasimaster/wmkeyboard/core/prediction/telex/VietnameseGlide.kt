package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.wasimaster.wmkeyboard.core.gesture.RomanizedIndex
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Provides unaccented-to-accented Vietnamese syllable and word mapping for Glide typing.
 * Builds a PackedTrie of unaccented stems and resolves gesture readings into native
 * Vietnamese syllables ranked by frequency and context.
 */
object VietnameseGlide {
    private const val ASSET_PATH = "telex/vietnamese_glide_syllables.tsv"
    private const val VIETNAMESE_KEYS = "abcdefghijklmnopqrstuvwxyz"

    @Volatile
    private var cachedIndex: RomanizedIndex? = null
    @Volatile
    private var syllableMap: Map<String, List<String>>? = null

    /**
     * Loads the Vietnamese unaccented trie and builds the RomanizedIndex.
     */
    fun getIndex(assets: AssetManager): RomanizedIndex? {
        cachedIndex?.let { return it }
        synchronized(this) {
            cachedIndex?.let { return it }
            return try {
                assets.open(ASSET_PATH).use { stream ->
                    loadFromStream(stream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Internal loader from input stream.
     */
    fun loadFromStream(stream: InputStream): RomanizedIndex {
        val map = HashMap<String, List<String>>(25000)
        val entries = ArrayList<Pair<String, Int>>(25000)

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val unaccented = line.substring(0, tab)
                val forms = line.substring(tab + 1).split(',')
                if (forms.isNotEmpty()) {
                    map[unaccented] = forms
                    entries.add(unaccented to 1000)
                }
            }
        }

        syllableMap = map
        val alphabet = VIETNAMESE_KEYS.mapTo(HashSet()) { it.code }
        val trie = PackedTrie.of(entries)
        val index = RomanizedIndex.multiMap(
            curated = trie,
            alphabet = alphabet,
            resolve = { unaccented ->
                map[unaccented] ?: emptyList()
            }
        )
        cachedIndex = index
        return index
    }

    fun lookup(unaccented: String): List<String> {
        return syllableMap?.get(unaccented) ?: emptyList()
    }

    @VisibleForTesting
    fun resetForTesting() {
        cachedIndex = null
        syllableMap = null
    }
}
