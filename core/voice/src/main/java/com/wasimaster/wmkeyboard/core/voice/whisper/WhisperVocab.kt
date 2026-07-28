package com.wasimaster.wmkeyboard.core.voice.whisper

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses a Whisper `filters_vocab_*.bin` (the whisper.cpp/nyadla format used by
 * the DocWolle tflite models). One binary carries both the mel filterbank and
 * the byte-level BPE vocabulary:
 *
 * ```
 * int32 magic == 0x5553454e ("USEN")
 * int32 nMel, int32 nFft            // filterbank dims (80 x 201)
 * float32[nMel*nFft] filters        // row-major [mel][fft]
 * int32 nVocab
 * repeat nVocab: int32 len, byte[len] token   // raw byte-level BPE bytes
 * ```
 *
 * Special tokens (EOT/SOT/…) are appended after the file's tokens and never
 * stored; only their ids matter and [eot] is all decoding needs. The
 * multilingual vocab shifts every special id up by one versus English-only.
 *
 * Tokens are kept as raw bytes and concatenated as bytes before a single UTF-8
 * decode, so a multibyte character split across two BPE tokens (common in CJK)
 * reassembles correctly — decoding each token on its own would corrupt it.
 */
class WhisperVocab private constructor(
    val filters: FloatArray,
    val nMel: Int,
    val nFft: Int,
    val eot: Int,
    private val tokens: Array<ByteArray?>,
) {
    /**
     * Turns a model's `sequences` output into text: stop at end-of-transcript,
     * skip every special/task/language token (id >= [eot]), concatenate the
     * rest as bytes, then decode once.
     */
    fun decode(ids: IntArray): String {
        val out = ByteArrayOutputStreamFast()
        for (id in ids) {
            if (id == eot) break
            if (id in 0 until eot) tokens.getOrNull(id)?.let { out.write(it) }
        }
        return out.toUtf8()
    }

    /** Minimal growable byte buffer — avoids java.io import churn and is cheap. */
    private class ByteArrayOutputStreamFast {
        private var buf = ByteArray(256)
        private var size = 0
        fun write(bytes: ByteArray) {
            if (size + bytes.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + bytes.size))
            System.arraycopy(bytes, 0, buf, size, bytes.size)
            size += bytes.size
        }
        fun toUtf8(): String = String(buf, 0, size, Charsets.UTF_8)
    }

    companion object {
        private const val MAGIC = 0x5553454e

        /**
         * @param multilingual true for `filters_vocab_multilingual.bin` (99-lang
         * tokenizer, special ids +1), false for `filters_vocab_en.bin`.
         */
        fun load(vocabFile: File, multilingual: Boolean): WhisperVocab {
            val buf = ByteBuffer.wrap(vocabFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.int
            require(magic == MAGIC) { "bad vocab magic 0x${magic.toString(16)} in ${vocabFile.name}" }

            val nMel = buf.int
            val nFft = buf.int
            val filters = FloatArray(nMel * nFft)
            for (i in filters.indices) filters[i] = buf.float

            val nVocab = buf.int
            val tokens = arrayOfNulls<ByteArray>(nVocab)
            for (i in 0 until nVocab) {
                val len = buf.int
                val word = ByteArray(len)
                buf.get(word)
                tokens[i] = word
            }
            // English base EOT = 50256; multilingual shifts every special +1.
            val eot = if (multilingual) 50257 else 50256
            return WhisperVocab(filters, nMel, nFft, eot, tokens)
        }
    }
}
