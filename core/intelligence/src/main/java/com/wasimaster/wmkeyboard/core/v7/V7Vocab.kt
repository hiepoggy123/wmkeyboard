package com.wasimaster.wmkeyboard.core.v7

import android.content.Context
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Fast in-memory vocabulary and syllable metadata for the V7 model.
 * Loaded from compact binary format (v7_vocab.bin) in assets.
 */
class V7Vocab private constructor(
    val words: Array<String>,
    val consonants: ByteArray,
    val tones: ByteArray,
    val consonantTable: Array<String>,
    private val wordToIdMap: Map<String, Int>
) {
    val size: Int get() = words.size

    fun getWord(id: Int): String? {
        if (id in words.indices) return words[id]
        return null
    }

    fun getConsonant(id: Int): String {
        if (id in consonants.indices) {
            val cid = consonants[id].toInt() and 0xFF
            if (cid in consonantTable.indices) return consonantTable[cid]
        }
        return "0"
    }

    fun getTone(id: Int): Int {
        if (id in tones.indices) {
            return tones[id].toInt() and 0xFF
        }
        return 0
    }

    fun getId(word: String): Int {
        return wordToIdMap[word] ?: 0
    }

    companion object {
        private const val ASSET_NAME = "v7/v7_vocab.bin"

        @Volatile
        private var instance: V7Vocab? = null

        fun getInstance(context: Context): V7Vocab {
            return instance ?: synchronized(this) {
                instance ?: load(context.assets.open(ASSET_NAME)).also { instance = it }
            }
        }

        fun load(inputStream: InputStream): V7Vocab {
            DataInputStream(BufferedInputStream(inputStream)).use { dis ->
                val totalTokens = dis.readInt()
                val numConsonants = dis.readUnsignedByte()
                val consonantTable = Array(numConsonants) {
                    val len = dis.readUnsignedByte()
                    val bytes = ByteArray(len)
                    dis.readFully(bytes)
                    String(bytes, StandardCharsets.UTF_8)
                }

                val words = Array(totalTokens) { "" }
                val consonants = ByteArray(totalTokens)
                val tones = ByteArray(totalTokens)
                val wordToId = HashMap<String, Int>(totalTokens)

                val strBuffer = ByteArray(256)
                for (i in 0 until totalTokens) {
                    consonants[i] = dis.readByte()
                    tones[i] = dis.readByte()
                    val wordLen = dis.readUnsignedShort()
                    if (wordLen > 0) {
                        dis.readFully(strBuffer, 0, wordLen)
                        val word = String(strBuffer, 0, wordLen, StandardCharsets.UTF_8)
                        words[i] = word
                        wordToId[word] = i
                    }
                }

                return V7Vocab(words, consonants, tones, consonantTable, wordToId)
            }
        }
    }
}
