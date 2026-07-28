package com.wasimaster.wmkeyboard.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.wasimaster.wmkeyboard.core.settings.DevicePowerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The device's power situation, as a flow the keyboard can combine with its
 * settings.
 *
 * Deliberately *not* a receiver for `ACTION_BATTERY_CHANGED`: that broadcast
 * fires on every percentage point and every small current change, and a power
 * saver that wakes the process a hundred times an hour to find out whether it
 * should be saving power has already lost. Instead:
 *
 *  * the coarse, rare broadcasts are subscribed to — battery low/okay, charger
 *    in/out, and the system battery-saver toggle. Each of those genuinely
 *    changes the answer.
 *  * the exact level is read from the *sticky* `ACTION_BATTERY_CHANGED` intent
 *    with [Context.registerReceiver] and a null receiver, which registers
 *    nothing and simply returns the last broadcast. [refresh] does that, and
 *    the keyboard calls it each time it comes on screen — which is the only
 *    moment the answer can matter.
 *
 * `ACTION_BATTERY_LOW` fires at the platform's own threshold (15% on most
 * devices), so it is a wake-up rather than the condition itself; the user's
 * chosen percentage is always evaluated against the level [refresh] read.
 */
class PowerSaver(private val context: Context) {

    private val _state = MutableStateFlow(DevicePowerState())

    /** The last known power state. Starts unknown (`batteryPercent = -1`). */
    val state: StateFlow<DevicePowerState> = _state.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    /** Subscribes to the coarse power broadcasts and takes a first reading. */
    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Exported: these come from the system, and RECEIVER_NOT_EXPORTED would
        // also refuse them on API 34+.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        refresh()
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    /**
     * Re-reads the level, the charger and the system saver. Cheap: the battery
     * read is the sticky broadcast rather than a new registration, and the
     * saver flag is a single binder call.
     */
    fun refresh() {
        val battery = runCatching {
            // A null receiver registers nothing: this returns the last sticky
            // ACTION_BATTERY_CHANGED and hands back no subscription.
            ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        // BATTERY_STATUS_FULL counts as charging: a phone sitting at 100% on
        // the charger has nothing to save either.
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _state.value = DevicePowerState(
            batteryPercent = percent,
            charging = charging,
            systemSaver = power?.isPowerSaveMode == true,
        )
    }
}
