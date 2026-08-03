package com.wasimaster.wmkeyboard.core.voice.whisper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Log-mel spectrogram matching Whisper's preprocessing (a direct port of the
 * whisper.cpp / vilassn reference, verified end-to-end against the tflite
 * models). Output is the `[nMel][nLen]` matrix flattened mel-major
 * (`data[mel * nLen + frame]`), exactly the layout the `input_features`
 * `[1, nMel, 3000]` tensor expects.
 *
 * The band count comes from the filterbank rather than being fixed at 80: the
 * large-v3 generation (large-v3 and turbo) wants 128 bands. Only the filterbank
 * differs — window, hop, FFT size and the log/clamp/normalize steps are the same
 * for both, so one routine covers them.
 */
object WhisperMel {

    const val SAMPLE_RATE = 16_000
    const val N_FFT = 400

    /** Band count of every Whisper model before large-v3, and the usual case. */
    const val N_MEL = 80

    /** Band count large-v3 and turbo want instead. */
    const val N_MEL_V3 = 128
    const val HOP_LENGTH = 160
    const val CHUNK_SECONDS = 30
    /** Fixed Whisper window: 16 kHz * 30 s = 480000 samples → 3000 frames. */
    const val N_SAMPLES = SAMPLE_RATE * CHUNK_SECONDS
    const val MEL_LEN = N_SAMPLES / HOP_LENGTH

    /**
     * @param samples 16 kHz mono PCM as floats in [-1, 1]. Shorter input is
     *   zero-padded to 30 s; longer is truncated to the first 30 s.
     * @param filters the `[nMel * nFftCols]` filterbank from [WhisperVocab].
     * @param nFftCols the filterbank's column count (1 + N_FFT/2 = 201).
     * @param nMel the filterbank's row count — [N_MEL] or [N_MEL_V3].
     */
    fun compute(
        samples: FloatArray,
        filters: FloatArray,
        nFftCols: Int,
        nMel: Int = N_MEL,
    ): FloatArray {
        val padded = FloatArray(N_SAMPLES)
        System.arraycopy(samples, 0, padded, 0, minOf(samples.size, N_SAMPLES))

        val nLen = MEL_LEN
        val mel = FloatArray(nMel * nLen)

        val hann = FloatArray(N_FFT) { (0.5 * (1.0 - cos(2.0 * PI * it / N_FFT))).toFloat() }

        val fftIn = FloatArray(N_FFT)
        val fftOut = FloatArray(N_FFT * 2)

        for (i in 0 until nLen) {
            val offset = i * HOP_LENGTH
            for (j in 0 until N_FFT) {
                fftIn[j] = if (offset + j < N_SAMPLES) hann[j] * padded[offset + j] else 0f
            }

            fft(fftIn, fftOut)
            // magnitude^2
            for (j in 0 until N_FFT) {
                fftOut[j] = fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
            }
            // fold the mirror image onto the first half
            for (j in 1 until N_FFT / 2) fftOut[j] += fftOut[N_FFT - j]

            for (j in 0 until nMel) {
                var sum = 0.0
                val base = j * nFftCols
                for (k in 0 until nFftCols) sum += fftOut[k] * filters[base + k]
                if (sum < 1e-10) sum = 1e-10
                mel[j * nLen + i] = log10(sum).toFloat()
            }
        }

        // clamp to a 8-decade floor below the max, then scale into ~[0, 1]
        var mmax = -1e20
        for (v in mel) if (v > mmax) mmax = v.toDouble()
        mmax -= 8.0
        for (idx in mel.indices) {
            var v = mel[idx].toDouble()
            if (v < mmax) v = mmax
            mel[idx] = ((v + 4.0) / 4.0).toFloat()
        }
        return mel
    }

    /** Recursive radix-2 FFT with a DFT fallback for odd sizes (N_FFT = 400 → 25). */
    private fun fft(input: FloatArray, output: FloatArray) {
        val n = input.size
        if (n == 1) {
            output[0] = input[0]
            output[1] = 0f
            return
        }
        if (n % 2 == 1) {
            dft(input, output)
            return
        }
        val even = FloatArray(n / 2)
        val odd = FloatArray(n / 2)
        var ei = 0
        var oi = 0
        for (i in 0 until n) {
            if (i % 2 == 0) even[ei++] = input[i] else odd[oi++] = input[i]
        }
        val evenFft = FloatArray(n)
        val oddFft = FloatArray(n)
        fft(even, evenFft)
        fft(odd, oddFft)
        for (k in 0 until n / 2) {
            val theta = 2 * PI * k / n
            val re = cos(theta).toFloat()
            val im = (-sin(theta)).toFloat()
            val reOdd = oddFft[2 * k]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k] = evenFft[2 * k] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + n / 2)] = evenFft[2 * k] - re * reOdd + im * imOdd
            output[2 * (k + n / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }

    private fun dft(input: FloatArray, output: FloatArray) {
        val n = input.size
        for (k in 0 until n) {
            var re = 0f
            var im = 0f
            for (t in 0 until n) {
                val angle = 2 * PI * k * t / n
                re += (input[t] * cos(angle)).toFloat()
                im -= (input[t] * sin(angle)).toFloat()
            }
            output[k * 2] = re
            output[k * 2 + 1] = im
        }
    }
}
