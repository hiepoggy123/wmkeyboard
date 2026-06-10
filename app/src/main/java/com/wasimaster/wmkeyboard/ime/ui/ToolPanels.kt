package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.tools.CalendarSystems
import com.wasimaster.wmkeyboard.core.tools.MoonPhase
import com.wasimaster.wmkeyboard.core.tools.Qibla
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.SoundHapticAction
import com.wasimaster.wmkeyboard.ime.WeatherUi
import com.wasimaster.wmkeyboard.ime.layout.Key
import com.wasimaster.wmkeyboard.ime.layout.KeyAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---- shared bits ----

/** Panel-sized key in the theme's modifier-key colors (same look as TextEditPanel). */
@Composable
private fun ToolPanelKey(
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    repeatable: Boolean = false,
    onAction: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(kb.keyRadiusDp.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(kb.modifierKey, shape)
            .pointerInput(repeatable) {
                detectTapGestures(
                    onPress = {
                        onAction()
                        var repeat: Job? = null
                        if (repeatable) {
                            repeat = scope.launch {
                                delay(400)
                                while (true) {
                                    onAction()
                                    delay(120)
                                }
                            }
                        }
                        tryAwaitRelease()
                        repeat?.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = if (label == null) description else null,
                    modifier = Modifier.size(22.dp), tint = kb.modifierKeyText)
            }
            if (label != null) {
                Text(label, color = kb.modifierKeyText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PanelMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = LocalKbTheme.current.toolbarIcon,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

// ---- compass ----

/**
 * Live compass from the rotation-vector sensor: a rotating rose with a
 * fixed needle at the top, a degree/cardinal readout, and optionally the
 * qibla direction computed from the saved weather location.
 */
@Composable
internal fun CompassPanel(state: KeyboardUiState) {
    val height = keyRowsHeight(state.settings)
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    // Raw sensor heading; the displayed azimuth chases it per frame below.
    var target by remember { mutableFloatStateOf(0f) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasReading by remember { mutableStateOf(false) }

    DisposableEffect(sensor) {
        if (sensor == null) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                target = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                hasReading = true
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Sensor events land at irregular ~20-60ms steps, which reads as jitter
    // when the rose snaps to each one. Instead the display chases the latest
    // reading with a time-based exponential pull evaluated every frame, so
    // the rotation glides at the display's refresh rate. Wraparound-aware
    // (359°→1° takes the short way).
    LaunchedEffect(sensor, hasReading) {
        if (sensor == null || !hasReading) return@LaunchedEffect
        azimuth = target
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    val diff = ((target - azimuth + 540f) % 360f) - 180f
                    azimuth = (azimuth + diff * (1f - exp(-dt * 10f)) + 360f) % 360f
                }
                last = now
            }
        }
    }

    if (sensor == null) {
        Box(Modifier.fillMaxWidth().height(height)) {
            PanelMessage("This device has no orientation sensor.")
        }
        return
    }

    val latitude = state.settings.weatherLatitude
    val longitude = state.settings.weatherLongitude
    val qiblaBearing = if (state.settings.compassShowQibla && latitude != null && longitude != null) {
        Qibla.bearing(latitude.toDouble(), longitude.toDouble()).toFloat()
    } else null

    val kb = LocalKbTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val radius = min(size.width, size.height) / 2f - 8.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(kb.modifierKey, radius, center)
            rotate(-azimuth, center) {
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 13.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                for (deg in 0 until 360 step 15) {
                    val major = deg % 90 == 0
                    val angle = Math.toRadians(deg.toDouble())
                    val dir = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                    val outer = center + dir * radius
                    val inner = center + dir * (radius - if (major) 12.dp.toPx() else 6.dp.toPx())
                    drawLine(
                        color = if (deg == 0) kb.accent else kb.toolbarIcon,
                        start = inner,
                        end = outer,
                        strokeWidth = if (major) 3f else 1.5f,
                    )
                    if (major) {
                        val label = when (deg) {
                            0 -> "N"; 90 -> "E"; 180 -> "S"; else -> "W"
                        }
                        textPaint.color = (if (deg == 0) kb.accent else kb.modifierKeyText).toArgb()
                        val pos = center + dir * (radius - 22.dp.toPx())
                        drawContext.canvas.nativeCanvas.apply {
                            save()
                            // Keep letters upright: undo the rose rotation at
                            // the letter's own position.
                            rotate(azimuth, pos.x, pos.y)
                            drawText(label, pos.x, pos.y + textPaint.textSize / 3f, textPaint)
                            restore()
                        }
                    }
                }
                // Qibla marker rotates with the rose so it points at the
                // real-world bearing.
                if (qiblaBearing != null) {
                    val angle = Math.toRadians(qiblaBearing.toDouble())
                    val dir = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                    val pos = center + dir * (radius - 34.dp.toPx())
                    drawLine(kb.accent, center + dir * (radius - 12.dp.toPx()), center + dir * radius, 5f)
                    val qiblaPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 14.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.apply {
                        save()
                        rotate(azimuth, pos.x, pos.y)
                        drawText("🕋", pos.x, pos.y + qiblaPaint.textSize / 3f, qiblaPaint)
                        restore()
                    }
                }
            }
            // Fixed needle marking the device's facing direction.
            val needle = Path().apply {
                moveTo(center.x, center.y - radius + 2.dp.toPx())
                lineTo(center.x - 6.dp.toPx(), center.y - radius + 16.dp.toPx())
                lineTo(center.x + 6.dp.toPx(), center.y - radius + 16.dp.toPx())
                close()
            }
            drawPath(needle, kb.accent)
            drawCircle(kb.accent, 4.dp.toPx(), center)
        }
        if (state.settings.compassShowDegrees) {
            val shown = azimuth.roundToInt() % 360
            Text(
                if (hasReading) "$shown°  ${cardinal(shown)}" else "Calibrating…",
                color = kb.modifierKeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.settings.compassShowQibla) {
            Text(
                if (qiblaBearing != null) {
                    "Qibla ${qiblaBearing.roundToInt()}° ${cardinal(qiblaBearing.roundToInt())}"
                } else {
                    "Qibla needs a saved location (weather tool settings)"
                },
                color = kb.toolbarIcon,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

private fun cardinal(degrees: Int): String {
    val names = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )
    return names[((degrees % 360 + 360) % 360 + 11) / 22 % 16]
}

// ---- bubble level ----

/** Bubble level from the accelerometer; turns accent-colored when flat. */
@Composable
internal fun LevelPanel(state: KeyboardUiState) {
    val height = keyRowsHeight(state.settings)
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    // Raw sensor gravity; the displayed values chase it per frame below.
    var rawX by remember { mutableFloatStateOf(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }
    var rawZ by remember { mutableFloatStateOf(9.81f) }
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var gz by remember { mutableFloatStateOf(9.81f) }

    DisposableEffect(sensor) {
        if (sensor == null) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                rawX = event.values[0]
                rawY = event.values[1]
                rawZ = event.values[2]
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Same trick as the compass: a per-frame, time-based low-pass instead of
    // per-event smoothing, so the bubble drifts smoothly at the display's
    // refresh rate rather than twitching on every sensor sample.
    LaunchedEffect(sensor) {
        if (sensor == null) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    val k = 1f - exp(-dt * 8f)
                    gx += (rawX - gx) * k
                    gy += (rawY - gy) * k
                    gz += (rawZ - gz) * k
                }
                last = now
            }
        }
    }

    if (sensor == null) {
        Box(Modifier.fillMaxWidth().height(height)) {
            PanelMessage("This device has no accelerometer.")
        }
        return
    }

    val kb = LocalKbTheme.current
    val pitch = Math.toDegrees(atan2(gy.toDouble(), sqrt((gx * gx + gz * gz).toDouble())))
    val roll = Math.toDegrees(atan2(gx.toDouble(), sqrt((gy * gy + gz * gz).toDouble())))
    val flat = abs(pitch) < 1.0 && abs(roll) < 1.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val radius = min(size.width, size.height) / 2f - 8.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(kb.modifierKey, radius, center)
            drawCircle(kb.toolbarIcon, radius * 0.25f, center, style = Stroke(1.5f))
            drawCircle(kb.toolbarIcon, radius * 0.65f, center, style = Stroke(1.5f))
            drawLine(kb.toolbarIcon, center - Offset(radius, 0f), center + Offset(radius, 0f), 1f)
            drawLine(kb.toolbarIcon, center - Offset(0f, radius), center + Offset(0f, radius), 1f)
            // The bubble floats toward the raised side, like a real vial.
            val range = radius - 10.dp.toPx()
            val bx = (-gx / 9.81f).coerceIn(-1f, 1f) * range
            val by = (-gy / 9.81f).coerceIn(-1f, 1f) * range
            val length = sqrt(bx * bx + by * by)
            val clamped = if (length > range) Offset(bx / length * range, by / length * range)
                else Offset(bx, by)
            drawCircle(
                if (flat) kb.accent else kb.modifierKeyText,
                10.dp.toPx(),
                center + Offset(-clamped.x, clamped.y),
            )
        }
        if (state.settings.levelShowAngles) {
            Text(
                String.format(Locale.US, "↕ %.1f°   ↔ %.1f°", pitch, roll),
                color = if (flat) kb.accent else kb.modifierKeyText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

// ---- moon phase ----

/** Current moon phase, drawn from arithmetic — nothing fetched. */
@Composable
internal fun MoonPhasePanel(state: KeyboardUiState) {
    val height = keyRowsHeight(state.settings)
    val kb = LocalKbTheme.current
    val info = remember { MoonPhase.at(System.currentTimeMillis()) }
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.8f),
        ) {
            val radius = min(size.width, size.height) / 2f - 4.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val dark = kb.modifierKey
            val lit = Color(0xFFE8E2D0)
            drawCircle(dark, radius, center)
            // Waxing lights the right limb (northern view); waning and the
            // southern-hemisphere setting each mirror the drawing.
            val waning = info.cycleFraction > 0.5
            val f = if (waning) 1.0 - info.cycleFraction else info.cycleFraction
            val mirrored = waning != state.settings.moonSouthernHemisphere
            val term = cos(2.0 * Math.PI * f).toFloat() // 1 new → -1 full
            val discRect = Rect(center - Offset(radius, radius), Size(radius * 2, radius * 2))
            val ellipseHalf = radius * abs(term)
            val ellipseRect = Rect(
                center - Offset(ellipseHalf, radius),
                Size(ellipseHalf * 2, radius * 2),
            )
            val path = Path().apply {
                arcTo(discRect, -90f, 180f, forceMoveTo = true)
                if (ellipseHalf > 0.5f) {
                    arcTo(ellipseRect, 90f, if (term > 0) -180f else 180f, forceMoveTo = false)
                }
                close()
            }
            scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f, pivot = center) {
                drawPath(path, lit)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                MoonPhase.phaseName(info.phaseIndex),
                color = kb.modifierKeyText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${(info.illumination * 100).roundToInt()}% illuminated · " +
                    "day ${info.ageDays.roundToInt()} of 30",
                color = kb.toolbarIcon,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Full moon: ${dateFormat.format(Date(info.nextFullMoonMillis))}",
                color = kb.modifierKeyText,
                fontSize = 13.sp,
            )
            Text(
                "New moon: ${dateFormat.format(Date(info.nextNewMoonMillis))}",
                color = kb.modifierKeyText,
                fontSize = 13.sp,
            )
        }
    }
}

// ---- weather ----

/** One statistic in the weather detail grid: small icon, value, caption. */
@Composable
private fun WeatherStat(
    icon: ImageVector,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val kb = LocalKbTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(kb.chip)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = kb.toolbarIcon)
        Text(
            value,
            color = kb.modifierKeyText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(caption, color = kb.toolbarIcon, fontSize = 9.sp, maxLines = 1)
    }
}

/** Current conditions from the service-side fetch; °C/°F per tool setting. */
@Composable
internal fun WeatherPanel(
    state: KeyboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        when (val weather = state.weather) {
            WeatherUi.NoLocation -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No location set. Search for your city in the weather tool's settings.",
                    color = kb.toolbarIcon,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(10.dp))
                ToolPanelKey(
                    description = "Open settings",
                    label = "Open settings",
                    modifier = Modifier.height(40.dp).width(160.dp),
                ) { onOpenSettings() }
            }
            WeatherUi.Loading -> PanelMessage("Fetching weather…")
            WeatherUi.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Couldn't fetch the weather. Check your connection.",
                    color = kb.toolbarIcon,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                ToolPanelKey(
                    description = "Retry",
                    label = "Retry",
                    modifier = Modifier.height(40.dp).width(120.dp),
                ) { onRefresh() }
            }
            is WeatherUi.Ready -> {
                val info = weather.info
                val fahrenheit = state.settings.weatherFahrenheit
                val unit = if (fahrenheit) "°F" else "°C"
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(WeatherClient.emoji(info.weatherCode, info.isDay), fontSize = 40.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${WeatherClient.toDisplay(info.temperatureC, fahrenheit)}$unit · " +
                                    WeatherClient.describe(info.weatherCode),
                                color = kb.modifierKeyText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val place = state.settings.weatherPlaceName.ifBlank { null }
                            Text(
                                listOfNotNull(
                                    place,
                                    "H ${WeatherClient.toDisplay(info.highC, fahrenheit)}° " +
                                        "L ${WeatherClient.toDisplay(info.lowC, fahrenheit)}°",
                                ).joinToString(" · "),
                                color = kb.toolbarIcon,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh weather",
                                tint = kb.toolbarIcon,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // All stats visible at once in a wrapping grid — the
                    // panel has spare height, so use it instead of making
                    // the user scroll sideways through a strip.
                    val stats = buildList {
                        add(
                            Triple(
                                Icons.Outlined.Thermostat,
                                "${WeatherClient.toDisplay(info.feelsLikeC, fahrenheit)}$unit",
                                "feels like",
                            )
                        )
                        add(Triple(Icons.Outlined.WaterDrop, "${info.humidityPercent}%", "humidity"))
                        add(
                            Triple(
                                Icons.Outlined.Air,
                                "${info.windKmh.roundToInt()} km/h " +
                                    WeatherClient.windCardinal(info.windDirectionDeg),
                                "wind",
                            )
                        )
                        if (info.precipProbabilityPercent >= 0) {
                            add(Triple(Icons.Outlined.Umbrella, "${info.precipProbabilityPercent}%", "rain chance"))
                        }
                        if (info.cloudCoverPercent >= 0) {
                            add(Triple(Icons.Outlined.Cloud, "${info.cloudCoverPercent}%", "clouds"))
                        }
                        if (info.pressureHpa >= 0) {
                            add(Triple(Icons.Outlined.Compress, "${info.pressureHpa.roundToInt()}", "hPa"))
                        }
                        if (info.uvIndexMax >= 0) {
                            add(Triple(Icons.Outlined.WbSunny, "%.1f".format(info.uvIndexMax), "UV max"))
                        }
                        if (info.sunrise.isNotEmpty()) {
                            add(Triple(Icons.Outlined.WbTwilight, info.sunrise, "sunrise"))
                        }
                        if (info.sunset.isNotEmpty()) {
                            add(Triple(Icons.Outlined.WbTwilight, info.sunset, "sunset"))
                        }
                    }
                    for (row in stats.chunked(3)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for ((icon, value, caption) in row) {
                                WeatherStat(icon, value, caption, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

// ---- calendar ----

/**
 * Month calendar: Gregorian grid with the Bengali (revised Bangladeshi)
 * date inside each cell, month spans for the enabled calendars in the
 * header, a Today shortcut, and insert chips for the selected date.
 */
@Composable
internal fun CalendarPanel(
    state: KeyboardUiState,
    onInsert: (String) -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    val kb = LocalKbTheme.current
    val today = remember {
        Calendar.getInstance().let {
            CalendarSystems.SimpleDate(
                it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
    var shownYear by remember { mutableStateOf(today.year) }
    var shownMonth by remember { mutableStateOf(today.month) }
    var selected by remember { mutableStateOf(today) }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthLabel = remember(shownYear, shownMonth) {
        monthFormat.format(
            Calendar.getInstance().apply { set(shownYear, shownMonth - 1, 1) }.time
        )
    }
    val showBengali = state.settings.calendarShowBengali
    val showHijri = state.settings.calendarShowHijri
    val hijriAdjust = state.settings.hijriAdjustDays
    val daysInMonth = CalendarSystems.gregorianMonthLength(shownYear, shownMonth)

    // Which Bengali / Hijri months this Gregorian month spans, for the header.
    val spanLabel = remember(shownYear, shownMonth, showBengali, showHijri, hijriAdjust) {
        val parts = mutableListOf<String>()
        if (showBengali) {
            val first = CalendarSystems.toBengali(shownYear, shownMonth, 1)
            val last = CalendarSystems.toBengali(shownYear, shownMonth, daysInMonth)
            val months = if (first.month == last.month) {
                CalendarSystems.bengaliMonths[first.month - 1]
            } else {
                CalendarSystems.bengaliMonths[first.month - 1] + "–" +
                    CalendarSystems.bengaliMonths[last.month - 1]
            }
            parts += "$months ${CalendarSystems.bengaliDigits(last.year)}"
        }
        if (showHijri) {
            val first = CalendarSystems.toHijri(shownYear, shownMonth, 1, hijriAdjust)
            val last = CalendarSystems.toHijri(shownYear, shownMonth, daysInMonth, hijriAdjust)
            val months = if (first.month == last.month) {
                CalendarSystems.hijriMonths[first.month - 1]
            } else {
                CalendarSystems.hijriMonths[first.month - 1].substringBefore(' ') + "–" +
                    CalendarSystems.hijriMonths[last.month - 1].substringBefore(' ')
            }
            parts += "$months ${last.year}"
        }
        parts.joinToString("  ·  ")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        // Header: month navigation + Today shortcut.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (shownMonth == 1) { shownMonth = 12; shownYear-- } else shownMonth--
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous month", tint = kb.toolbarIcon)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    monthLabel,
                    color = kb.modifierKeyText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (spanLabel.isNotEmpty()) {
                    Text(
                        spanLabel,
                        color = kb.toolbarIcon,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (shownYear != today.year || shownMonth != today.month) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(kb.chip)
                        .clickable {
                            shownYear = today.year
                            shownMonth = today.month
                            selected = today
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Today", color = kb.modifierKeyText, fontSize = 11.sp)
                }
            }
            IconButton(
                onClick = {
                    if (shownMonth == 12) { shownMonth = 1; shownYear++ } else shownMonth++
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month", tint = kb.toolbarIcon)
            }
        }
        // Weekday initials; Friday tinted (weekend in Bangladesh).
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, initial ->
                Text(
                    initial,
                    color = if (index == 5) kb.accent else kb.toolbarIcon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Day grid.
        val firstJdn = CalendarSystems.gregorianToJdn(shownYear, shownMonth, 1)
        val firstDow = CalendarSystems.dayOfWeek(firstJdn)
        val weeks = (firstDow + daysInMonth + 6) / 7
        Column(modifier = Modifier.weight(1f)) {
            for (week in 0 until weeks) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    for (dow in 0 until 7) {
                        val day = week * 7 + dow - firstDow + 1
                        if (day < 1 || day > daysInMonth) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val isToday = today.year == shownYear &&
                                today.month == shownMonth && today.day == day
                            val isSelected = selected.year == shownYear &&
                                selected.month == shownMonth && selected.day == day
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) kb.toolCircleActive else Color.Transparent)
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(1.dp, kb.accent, RoundedCornerShape(6.dp))
                                        } else Modifier
                                    )
                                    .clickable {
                                        selected = CalendarSystems.SimpleDate(shownYear, shownMonth, day)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                val primary = when {
                                    isSelected -> kb.toolCircleActiveIcon
                                    isToday -> kb.accent
                                    else -> kb.modifierKeyText
                                }
                                if (showBengali) {
                                    val bengaliDay = CalendarSystems
                                        .toBengali(shownYear, shownMonth, day).day
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            day.toString(),
                                            color = primary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold
                                                else FontWeight.Normal,
                                        )
                                        Text(
                                            CalendarSystems.bengaliDigits(bengaliDay),
                                            color = if (isSelected) kb.toolCircleActiveIcon.copy(alpha = 0.7f)
                                                else kb.toolbarIcon,
                                            fontSize = 8.sp,
                                            modifier = Modifier.padding(start = 2.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        day.toString(),
                                        color = primary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold
                                            else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Selected date across the enabled calendars, with insert chips.
        val gregorianText = remember(selected) {
            SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(
                Calendar.getInstance().apply {
                    set(selected.year, selected.month - 1, selected.day)
                }.time
            )
        }
        val bengali = CalendarSystems.toBengali(selected.year, selected.month, selected.day)
        val bengaliText = "${CalendarSystems.bengaliDigits(bengali.day)} " +
            "${CalendarSystems.bengaliMonths[bengali.month - 1]} " +
            CalendarSystems.bengaliDigits(bengali.year)
        val hijri = CalendarSystems.toHijri(selected.year, selected.month, selected.day, hijriAdjust)
        val hijriText = "${hijri.day} ${CalendarSystems.hijriMonths[hijri.month - 1]} ${hijri.year} AH"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val parts = buildList {
                add(gregorianText)
                if (showBengali) add(bengaliText)
                if (showHijri) add(hijriText)
            }
            Text(
                parts.joinToString(" · "),
                color = kb.modifierKeyText,
                fontSize = 11.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            InsertChip("Insert") { onInsert(gregorianText) }
            if (showBengali) InsertChip("বাং") { onInsert(bengaliText) }
            if (showHijri) InsertChip("هـ") { onInsert(hijriText) }
        }
    }
}

/** Small tap target that types a date representation into the editor. */
@Composable
private fun InsertChip(label: String, onClick: () -> Unit) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(kb.toolCircleActive)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = kb.toolCircleActiveIcon, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- themes ----

/**
 * Quick theme switcher: swatches for the default (dynamic) theme, every
 * built-in and every custom theme. Tap to apply immediately.
 */
@Composable
internal fun ThemesPanel(
    state: KeyboardUiState,
    onThemeSelect: (String) -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    val kb = LocalKbTheme.current
    val selectedId = state.settings.keyboardThemeId
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 8.dp),
    ) {
        Text(
            "Tap a theme to apply it. Create and edit themes in Settings → Appearance.",
            color = kb.toolbarIcon,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSwatch(
                name = "Auto",
                spec = null,
                selected = selectedId == DEFAULT_THEME_ID,
            ) { onThemeSelect(DEFAULT_THEME_ID) }
            for (theme in BuiltInThemes + state.settings.customThemes) {
                ThemeSwatch(
                    name = theme.name,
                    spec = theme,
                    selected = selectedId == theme.id,
                ) { onThemeSelect(theme.id) }
            }
        }
    }
}

/** Mini preview card: board color with two key rects and the accent dot. */
@Composable
private fun ThemeSwatch(
    name: String,
    spec: ThemeSpec?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val board = spec?.let { Color(it.boardBackground.toInt()) } ?: kb.board
    val key = spec?.let { Color(it.keyBackground.toInt()) } ?: kb.key
    val accent = spec?.let { Color(it.accent.toInt()) } ?: kb.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(board)
                .then(
                    if (selected) Modifier.border(2.dp, kb.accent, RoundedCornerShape(10.dp))
                    else Modifier.border(1.dp, kb.divider, RoundedCornerShape(10.dp))
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(width = 18.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(key))
                Box(Modifier.size(width = 18.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(key))
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
            }
        }
        Text(
            name,
            color = kb.modifierKeyText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp).width(76.dp),
            textAlign = TextAlign.Center,
        )
    }
}

// ---- sound & haptics ----

/** Small selectable pill for the sound/haptic style rows. */
@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) kb.toolCircleActive else kb.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = if (selected) kb.toolCircleActiveIcon else kb.modifierKeyText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Quick sound & haptics controls without leaving the keyboard. Every change
 * lands in the same DataStore settings the full settings app edits.
 */
@Composable
internal fun SoundHapticsPanel(
    state: KeyboardUiState,
    onAction: (SoundHapticAction) -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    val kb = LocalKbTheme.current
    val settings = state.settings
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Haptics", color = kb.modifierKeyText, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.hapticFeedback,
                onCheckedChange = { onAction(SoundHapticAction.Haptics(it)) },
            )
        }
        if (settings.hapticFeedback) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (style in HapticStyle.entries) {
                    StyleChip(
                        label = when (style) {
                            HapticStyle.CUSTOM -> "Custom"
                            HapticStyle.CLICK -> "Click"
                            HapticStyle.HEAVY_CLICK -> "Heavy"
                            HapticStyle.SHARP -> "Sharp"
                        },
                        selected = settings.hapticStyle == style,
                    ) { onAction(SoundHapticAction.HapticStyleChange(style)) }
                }
            }
            if (settings.hapticStyle == HapticStyle.CUSTOM || settings.hapticStyle == HapticStyle.SHARP) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Intensity", color = kb.toolbarIcon, fontSize = 11.sp,
                        modifier = Modifier.width(60.dp))
                    Slider(
                        value = settings.hapticAmplitude.toFloat(),
                        onValueChange = { onAction(SoundHapticAction.HapticAmplitude(it.toInt())) },
                        valueRange = 1f..255f,
                        modifier = Modifier.weight(1f).height(28.dp),
                    )
                    Text(
                        "${settings.hapticAmplitude * 100 / 255}%",
                        color = kb.toolbarIcon, fontSize = 11.sp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Key sound", color = kb.modifierKeyText, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.keySound,
                onCheckedChange = { onAction(SoundHapticAction.Sound(it)) },
            )
        }
        if (settings.keySound) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (style in KeySoundStyle.entries) {
                    StyleChip(
                        label = when (style) {
                            KeySoundStyle.CLICK -> "Click"
                            KeySoundStyle.STANDARD -> "Std"
                            KeySoundStyle.POP -> "Pop"
                            KeySoundStyle.THOCK -> "Thock"
                            KeySoundStyle.CHIME -> "Chime"
                        },
                        selected = settings.keySoundStyle == style,
                    ) { onAction(SoundHapticAction.SoundStyleChange(style)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", color = kb.toolbarIcon, fontSize = 11.sp,
                    modifier = Modifier.width(60.dp))
                Slider(
                    value = settings.keySoundVolume,
                    onValueChange = { onAction(SoundHapticAction.SoundVolume(it)) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f).height(28.dp),
                )
                Text(
                    "${(settings.keySoundVolume * 100).roundToInt()}%",
                    color = kb.toolbarIcon, fontSize = 11.sp,
                )
            }
        }
    }
}

// ---- numpad ----

/**
 * Dedicated number pad: phone-style digit grid with the common numeric
 * punctuation, backspace (repeating) and enter.
 */
@Composable
internal fun NumpadPanel(
    state: KeyboardUiState,
    onText: (String) -> Unit,
    onKey: (Key) -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        val rows = listOf(
            listOf("7", "8", "9", "⌫"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "-"),
            listOf(".", "0", ",", "⏎"),
        )
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                NumpadRow(row, onText, onKey)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NumpadRow(
    keys: List<String>,
    onText: (String) -> Unit,
    onKey: (Key) -> Unit,
) {
    val feedback = LocalKeyPressFeedback.current
    for (label in keys) {
        when (label) {
            "⌫" -> ToolPanelKey(
                description = "Delete",
                icon = Icons.AutoMirrored.Outlined.Backspace,
                repeatable = true,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp),
            ) {
                feedback()
                onKey(Key("⌫", action = KeyAction.Delete))
            }
            "⏎" -> ToolPanelKey(
                description = "Enter",
                icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp),
            ) {
                feedback()
                onKey(Key("⏎", action = KeyAction.Enter))
            }
            else -> ToolPanelKey(
                description = label,
                label = label,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp),
            ) { onText(label) }
        }
    }
}
