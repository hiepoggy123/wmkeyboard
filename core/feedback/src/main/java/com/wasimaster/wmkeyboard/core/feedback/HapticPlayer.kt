package com.wasimaster.wmkeyboard.core.feedback

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.wasimaster.wmkeyboard.core.settings.HapticStyle

/**
 * Plays the key-press haptic effect. Lives outside the IME service so the
 * settings app and the sound & haptics tool can preview exactly what a key
 * press will feel like while the user is still adjusting the sliders.
 */
object HapticPlayer {

    /** Minimum spacing between previews so slider drags don't buzz-saw. */
    private const val PREVIEW_GAP_MS = 150L

    private var lastPreviewAt = 0L

    /**
     * Held rather than looked up per press: [play] runs inside the pointer-down
     * handler, and resolving the vibrator there put a service lookup on the
     * touch path of every keystroke. Built from the application context so the
     * singleton never pins an activity or the IME's own context.
     */
    @Volatile
    private var vibrator: Vibrator? = null

    /** Primes [vibrator] off the touch path, so the first press doesn't pay for it. */
    fun warmUp(context: Context) {
        vibratorFor(context)
    }

    @Suppress("DEPRECATION")
    private fun vibratorFor(context: Context): Vibrator {
        vibrator?.let { return it }
        val app = context.applicationContext
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        return v
    }

    /**
     * The style to start a device on: the platform's own key haptic where the
     * motor can actually play it, and a heavy click where it cannot.
     *
     * [HapticStyle.SYSTEM_KEY] is the better answer wherever it works — it is
     * the effect the phone's own keyboard uses, tuned per device by the
     * vendor. It falls through to the predefined `EFFECT_CLICK` family, so a
     * device that reports those as unsupported plays nothing recognisable as a
     * key press through the system route; there [HapticStyle.HEAVY_CLICK] at
     * least drives the coil hard enough to be felt.
     *
     * Support is only knowable from API 30, where `areAllEffectsSupported`
     * exists. Below it the predefined effects are still the best guess, and an
     * older device is also the one most likely to have a mushy motor — so the
     * cut-off doubles as the version check.
     */
    fun bestSupportedStyle(context: Context): HapticStyle {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return HapticStyle.HEAVY_CLICK
        val vibrator = vibratorFor(context)
        if (!vibrator.hasVibrator()) return HapticStyle.SYSTEM_KEY
        val supported = vibrator.areAllEffectsSupported(
            VibrationEffect.EFFECT_CLICK,
            VibrationEffect.EFFECT_HEAVY_CLICK,
        ) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        return if (supported) HapticStyle.SYSTEM_KEY else HapticStyle.HEAVY_CLICK
    }

    /**
     * Rate-limited [play] for settings UIs: fires the motor with the values
     * being edited (not yet necessarily persisted), at most once per
     * [PREVIEW_GAP_MS].
     */
    fun preview(
        context: Context,
        style: HapticStyle,
        amplitude: Int,
        durationMs: Int,
        view: View? = null,
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewAt < PREVIEW_GAP_MS) return
        lastPreviewAt = now
        play(context, style, amplitude, durationMs, view)
    }

    /**
     * Delegates to the platform key haptic. Returns false when the motor was
     * not driven (no such effect, or the view is detached) so the caller can
     * fall back to a direct vibrator effect. The IGNORE flags fire the buzz
     * even when the system-wide "Touch feedback" switch is off — the keyboard
     * gates haptics with its own toggle — while the OS still scales the
     * strength by the haptic-intensity setting.
     */
    @Suppress("DEPRECATION")
    private fun playSystem(view: View, style: HapticStyle): Boolean {
        val constant = when (style) {
            HapticStyle.SYSTEM_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        return view.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    @Suppress("DEPRECATION")
    fun play(context: Context, style: HapticStyle, amplitude: Int, durationMs: Int, view: View? = null) {
        // System styles ride the platform's tuned key haptic. If it actually
        // played we're done; otherwise fall through to a hardware click so
        // there's always feedback (e.g. preview before the view is attached).
        if (style == HapticStyle.SYSTEM_KEY || style == HapticStyle.SYSTEM_TAP) {
            if (view != null && playSystem(view, style)) return
        }
        val vibrator = vibratorFor(context)
        if (style == HapticStyle.SHARP &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            // Composed primitive: the HAL overdrives the motor on attack and
            // actively brakes it, so the click is both short (~20ms) and
            // strong — the crisp thump stock keyboards have. A raw one-shot
            // can't do this: it just drives the coil at constant amplitude,
            // which feels mushy. Scale reuses the intensity slider (0..1).
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        amplitude.coerceIn(1, 255) / 255f,
                    )
                    .compose()
            )
        } else if (style != HapticStyle.CUSTOM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Hardware-tuned click effects — crisper than a raw one-shot and
            // what most stock keyboards use.
            vibrator.vibrate(
                VibrationEffect.createPredefined(
                    when (style) {
                        HapticStyle.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                        else -> VibrationEffect.EFFECT_CLICK
                    }
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Explicit amplitude: DEFAULT_AMPLITUDE defers to a (often weak)
            // device default. Devices without amplitude control ignore the
            // value, so fall back to the default constant there.
            val oneShotAmplitude = if (vibrator.hasAmplitudeControl()) {
                amplitude.coerceIn(1, 255)
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), oneShotAmplitude))
        } else {
            vibrator.vibrate(durationMs.toLong())
        }
    }
}
