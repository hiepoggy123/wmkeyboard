package com.wasimaster.wmkeyboard.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays the key-press sound. Click and Standard come from the device's system
 * sound-effect pack, matching the stock keyboard. Pop, Thock and Chime are
 * synthesized in-app and played through a [SoundPool]: many devices map the
 * spacebar, delete and return system effects to the same audio file, which
 * made the three styles indistinguishable. Lives outside the IME service so
 * the settings app and the sound & haptics tool can preview the sound being
 * adjusted.
 */
object KeySoundPlayer {

    /** Minimum spacing between previews so slider drags don't machine-gun. */
    private const val PREVIEW_GAP_MS = 150L

    private const val SAMPLE_RATE = 44100

    /** Bump when the synthesis recipes change to regenerate cached files. */
    private const val CACHE_VERSION = 1

    private var lastPreviewAt = 0L

    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<KeySoundStyle, Int>()
    private val loadedIds = mutableSetOf<Int>()

    /** Builds the pool and starts decoding so the first key press isn't late. */
    fun warmUp(context: Context) {
        ensurePool(context)
    }

    /**
     * Rate-limited [play] for settings UIs: sounds the values being edited
     * (not yet necessarily persisted), at most once per [PREVIEW_GAP_MS].
     */
    fun preview(context: Context, style: KeySoundStyle, volume: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewAt < PREVIEW_GAP_MS) return
        lastPreviewAt = now
        play(context, style, volume)
    }

    fun play(context: Context, style: KeySoundStyle, volume: Float) {
        val vol = volume.coerceIn(0.05f, 1f)
        when (style) {
            KeySoundStyle.CLICK -> systemFx(context, AudioManager.FX_KEY_CLICK, vol)
            KeySoundStyle.STANDARD -> systemFx(context, AudioManager.FX_KEYPRESS_STANDARD, vol)
            else -> {
                val id = synchronized(this) {
                    ensurePool(context)
                    soundIds[style]?.takeIf { it in loadedIds }
                }
                if (id != null) {
                    pool?.play(id, vol, vol, 1, 0, 1f)
                } else {
                    // Still decoding (a press right after IME start): fall
                    // back to the nearest system effect rather than silence.
                    systemFx(context, AudioManager.FX_KEYPRESS_STANDARD, vol)
                }
            }
        }
    }

    private fun systemFx(context: Context, effect: Int, volume: Float) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.playSoundEffect(effect, volume)
    }

    @Synchronized
    private fun ensurePool(context: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) { loadedIds.add(sampleId) }
        }
        val dir = File(context.cacheDir, "keysounds").apply { mkdirs() }
        for (style in listOf(KeySoundStyle.POP, KeySoundStyle.THOCK, KeySoundStyle.CHIME)) {
            val file = File(dir, "${style.name.lowercase()}_v$CACHE_VERSION.wav")
            if (!file.exists()) writeWav(file, synthesize(style))
            soundIds[style] = p.load(file.path, 1)
        }
        pool = p
        return p
    }

    // ---- synthesis ----

    /** One sine component: frequency sweeps [startHz] → [endHz] over the sound. */
    private class Partial(val startHz: Double, val endHz: Double, val gain: Double)

    private fun synthesize(style: KeySoundStyle): ShortArray = when (style) {
        // Low sine with a quick downward pitch bend: a soft bubble-pop thump.
        KeySoundStyle.POP -> render(
            durationMs = 55, decayMs = 16.0,
            partials = listOf(Partial(175.0, 110.0, 1.0)),
        )
        // Deeper fundamental plus a quiet octave, slower decay: the woody
        // bottomed-out sound of a lubed mechanical board.
        KeySoundStyle.THOCK -> render(
            durationMs = 120, decayMs = 38.0,
            partials = listOf(
                Partial(92.0, 80.0, 1.0),
                Partial(184.0, 160.0, 0.35),
            ),
        )
        // Two high inharmonic sines ringing out: a small bell strike.
        KeySoundStyle.CHIME -> render(
            durationMs = 150, decayMs = 50.0,
            partials = listOf(
                Partial(1568.0, 1568.0, 1.0),
                Partial(2093.0, 2093.0, 0.6),
            ),
        )
        else -> error("$style uses the system sound pack")
    }

    private fun render(durationMs: Int, decayMs: Double, partials: List<Partial>): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val attack = SAMPLE_RATE * 2 / 1000 // 2 ms fade-in kills the onset click
        val phases = DoubleArray(partials.size)
        val mix = DoubleArray(n)
        var peak = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var env = exp(-t * 1000.0 / decayMs)
            if (i < attack) env *= i.toDouble() / attack
            var sample = 0.0
            partials.forEachIndexed { j, part ->
                val hz = part.startHz + (part.endHz - part.startHz) * i / n
                phases[j] += 2.0 * PI * hz / SAMPLE_RATE
                sample += part.gain * sin(phases[j])
            }
            mix[i] = sample * env
            if (abs(mix[i]) > peak) peak = abs(mix[i])
        }
        val norm = if (peak > 0) 0.85 / peak else 0.0
        return ShortArray(n) { (mix[it] * norm * Short.MAX_VALUE).toInt().toShort() }
    }

    /** Minimal 16-bit mono PCM WAV container around [samples]. */
    private fun writeWav(file: File, samples: ShortArray) {
        val dataSize = samples.size * 2
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)               // PCM chunk size
        header.putShort(1)              // PCM format
        header.putShort(1)              // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)  // byte rate
        header.putShort(2)              // block align
        header.putShort(16)             // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataSize)
        val pcm = java.nio.ByteBuffer.allocate(dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort(it) }
        file.outputStream().use {
            it.write(header.array())
            it.write(pcm.array())
        }
    }
}
