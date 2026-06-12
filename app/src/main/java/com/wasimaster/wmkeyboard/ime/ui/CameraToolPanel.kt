package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaActionSound
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A photo sitting in the confirm step: the file on disk, its preview, and
 * where the visible region sat in panel coordinates so the confirm step
 * can draw it exactly where the viewfinder showed it.
 */
private class PendingCapture(
    val file: File,
    val bitmap: Bitmap,
    val visibleOffsetY: Int,
    val visibleHeight: Int,
)

/**
 * Where the panel box sits on screen. The viewfinder overflows the box
 * (width-filling FILL_CENTER spill), so both the capture crop and the
 * overlay positions are computed against the window, not the box.
 */
private class PanelGeometry(
    val boxTopWindow: Float,
    val boxTopRoot: Float,
    val width: Int,
    val height: Int,
    val windowHeight: Int,
)

/**
 * In-keyboard camera. The live preview fills the tool viewbox and the
 * captured photo is centre-cropped to that exact aspect ratio — what the
 * viewfinder frames is what gets sent. Controls: shutter, front/back
 * switch, flash mode, self-timer; after a capture, retake or send (via
 * commitContent, like clipboard images). Shutter sound and haptics are
 * per-tool settings.
 */
@Composable
internal fun CameraPanel(
    state: KeyboardUiState,
    onSend: (File) -> Unit,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    val height = keyRowsHeight(state.settings)
    val kb = LocalKbTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    // The permission dialog lives in a trampoline activity; re-check when
    // the keyboard comes back to the foreground afterwards.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Old captures pile up only if the process dies mid-confirm; keep the
    // last few so recently sent URIs stay resolvable for the target app.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            captureDir(context).listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(4)
                ?.forEach { it.delete() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (hasPermission) {
            CameraContent(state = state, onSend = onSend, onClose = onClose)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "The camera tool needs permission to use the camera. " +
                        "Photos are only taken when you tap the shutter.",
                    color = kb.toolbarIcon,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(kb.keyRadiusDp.dp))
                        .background(kb.toolCircleActive)
                        .pointerInput(Unit) { detectTapGestures { onRequestPermission() } }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Allow camera", color = kb.toolCircleActiveIcon,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        // Back chip for the permission screen; with the camera running,
        // CameraContent draws its own at the top of the visible viewfinder.
        if (!hasPermission) {
            val keyFeedback = LocalKeyPressFeedback.current
            Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                CameraChipButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    description = "Close camera",
                    active = false,
                ) {
                    if (state.settings.cameraHaptics) keyFeedback()
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun CameraContent(
    state: KeyboardUiState,
    onSend: (File) -> Unit,
    onClose: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val provider by produceState<ProcessCameraProvider?>(null) {
        value = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
    }
    var frontFacing by remember { mutableStateOf(state.settings.cameraPreferFront) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var geometry by remember { mutableStateOf<PanelGeometry?>(null) }
    val windowHeight = LocalView.current.rootView.height

    // Haptics on controls, ticks and shutter — the tool setting gates it;
    // the global haptic settings still shape the actual vibration.
    val keyFeedback = LocalKeyPressFeedback.current
    val feedback = { if (state.settings.cameraHaptics) keyFeedback() }
    val shutterSound = remember {
        MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    }
    DisposableEffect(Unit) { onDispose { shutterSound.release() } }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // The default SurfaceView mode ignores Compose bounds inside the
            // IME window — the viewfinder painted over the whole keyboard
            // (toolbar included) and looked bigger than the actual capture
            // box. TextureView composits like a normal view.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // Chat photos don't need the sensor's full 12+ MP — a bounded
            // resolution captures and processes noticeably faster.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1600, 1600),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
            .build()
    }
    imageCapture.flashMode = flashMode

    val hasFront = provider?.hasCameraSafe(CameraSelector.DEFAULT_FRONT_CAMERA) == true
    val hasBack = provider?.hasCameraSafe(CameraSelector.DEFAULT_BACK_CAMERA) == true
    val selector = when {
        frontFacing && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
        hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
        hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> null
    }
    val usingFront = selector == CameraSelector.DEFAULT_FRONT_CAMERA

    // (Re)bind on open and on lens switch; release the camera as soon as
    // the panel closes so other apps can use it. No binding while the
    // confirm step is up — the frozen photo is the whole UI.
    DisposableEffect(provider, selector, pending == null) {
        val cameraProvider = provider
        if (cameraProvider != null && selector != null && pending == null) {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            camera = runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }.getOrNull()
        }
        onDispose {
            camera = null
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    fun takePhoto() {
        if (capturing) return
        feedback()
        capturing = true
        scope.launch {
            if (timerSeconds > 0) {
                for (second in timerSeconds downTo 1) {
                    countdown = second
                    feedback()
                    delay(1000)
                }
                countdown = 0
            }
            if (state.settings.cameraShutterSound) {
                shutterSound.play(MediaActionSound.SHUTTER_CLICK)
            }
            val mirror = usingFront && state.settings.cameraMirrorFront
            val geo = geometry
            val capture = withContext(Dispatchers.IO) {
                runCatching {
                    val proxy = imageCapture.awaitCapture(context)
                    val upright = proxy.use { it.toFramedBitmap(mirror) }
                    // Crop to the part of the width-filling viewfinder that
                    // was actually on screen — what you saw is what you get.
                    val (bitmap, visibleTop, visibleHeight) =
                        upright.cropToVisible(geo)
                    val file = File(captureDir(context), "IMG_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    PendingCapture(file, bitmap, visibleTop, visibleHeight)
                }.getOrNull()
            }
            if (capture != null) pending = capture
            capturing = false
        }
    }

    // Top of the visible viewfinder in panel-local px: the preview spills
    // above the panel box, so overlays anchored here sit on the picture's
    // real top edge (clamped to the compose root — touches above it die).
    val viewfinderTop = geometry?.let { g ->
        val contentTop = g.boxTopWindow + g.height / 2f - (g.width * 4f / 3f) / 2f
        val rootTop = g.boxTopWindow - g.boxTopRoot
        (max(max(contentTop, rootTop), 0f) - g.boxTopWindow).roundToInt()
    } ?: 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                geometry = PanelGeometry(
                    boxTopWindow = coords.positionInWindow().y,
                    boxTopRoot = coords.positionInRoot().y,
                    width = coords.size.width,
                    height = coords.size.height,
                    windowHeight = windowHeight,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val captured = pending
        if (captured != null) {
            // Drawn exactly where the viewfinder showed this region, so the
            // confirm step is indistinguishable from the live preview. The
            // slice is taller than the panel box (the preview spills over
            // it), so measure at its real size via a layout lambda — a
            // plain height() would be coerced to the box constraints and
            // FillBounds would squish the picture into the box.
            Image(
                bitmap = captured.bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, captured.visibleHeight)
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(0, captured.visibleOffsetY)
                        }
                    },
                contentScale = ContentScale.FillBounds,
            )
            BackChip(offsetY = viewfinderTop, feedback = feedback, onClose = onClose)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptureActionButton(
                    icon = Icons.Outlined.Refresh,
                    label = "Retake",
                    accent = false,
                ) {
                    feedback()
                    scope.launch(Dispatchers.IO) { captured.file.delete() }
                    pending = null
                }
                CaptureActionButton(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    label = "Send",
                    accent = true,
                ) {
                    // Send's vibration comes from the service handler.
                    pending = null
                    onSend(captured.file)
                }
            }
            return@Box
        }

        if (selector == null) {
            PanelCenteredMessage(if (provider == null) "Starting camera…" else "This device has no camera.")
            return@Box
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                // Tap to focus, like every camera app.
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        val cam = camera ?: return@detectTapGestures
                        val point = previewView.meteringPointFactory
                            .createPoint(offset.x, offset.y)
                        cam.cameraControl.startFocusAndMetering(
                            androidx.camera.core.FocusMeteringAction.Builder(point).build()
                        )
                    }
                },
        )

        BackChip(offsetY = viewfinderTop, feedback = feedback, onClose = onClose)

        if (countdown > 0) {
            Text(
                countdown.toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                // Glyph shadow for contrast on bright scenes — a layer
                // shadow here draws an ugly rectangle around the digit.
                style = androidx.compose.ui.text.TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        blurRadius = 12f,
                    ),
                ),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Control rail: flash and timer left, shutter centre, lens right.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    CameraChipButton(
                        icon = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
                            else -> Icons.Outlined.FlashOff
                        },
                        description = "Flash mode",
                        active = flashMode != ImageCapture.FLASH_MODE_OFF,
                    ) {
                        feedback()
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                CameraChipButton(
                    icon = if (timerSeconds == 0) Icons.Outlined.TimerOff else Icons.Outlined.Timer,
                    description = "Self-timer",
                    active = timerSeconds > 0,
                    label = if (timerSeconds > 0) "${timerSeconds}s" else null,
                ) {
                    feedback()
                    timerSeconds = when (timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                }
            }
            // Shutter: white ring with a fill that dims while busy.
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(if (capturing) Color.LightGray else Color.White)
                    .pointerInput(capturing) { detectTapGestures { takePhoto() } }
                    .semantics { contentDescription = "Take photo" },
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                if (hasFront && hasBack) {
                    CameraChipButton(
                        icon = Icons.Outlined.Cameraswitch,
                        description = "Switch camera",
                        active = usingFront,
                    ) {
                        feedback()
                        frontFacing = !usingFront
                    }
                }
            }
        }
    }
}

/** Back-to-keys chip pinned to the top-left of the visible viewfinder. */
@Composable
private fun BoxScope.BackChip(offsetY: Int, feedback: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(0, offsetY) }
            .padding(8.dp),
    ) {
        CameraChipButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            description = "Close camera",
            active = false,
        ) {
            feedback()
            onClose()
        }
    }
}

/** Round translucent control over the viewfinder (flash, timer, lens). */
@Composable
private fun CameraChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    label: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    icon,
                    contentDescription = description,
                    modifier = Modifier.size(if (label == null) 20.dp else 16.dp),
                    tint = if (active) Color(0xFFFFD54F) else Color.White,
                )
                if (label != null) {
                    Text(label, color = Color.White, fontSize = 9.sp, lineHeight = 9.sp)
                }
            }
        }
    }
}

/** Retake/Send pill under the frozen capture. */
@Composable
private fun CaptureActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (accent) kb.accent else Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PanelCenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = LocalKbTheme.current.toolbarIcon,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun captureDir(context: Context): File =
    File(context.filesDir, "camera").apply { mkdirs() }

private fun ProcessCameraProvider.hasCameraSafe(selector: CameraSelector): Boolean =
    runCatching { hasCamera(selector) }.getOrDefault(false)

/** Suspends over ImageCapture's callback API. */
private suspend fun ImageCapture.awaitCapture(context: Context): ImageProxy =
    suspendCancellableCoroutine { continuation ->
        takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    continuation.resume(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.cancel(exception)
                    }
                }
            },
        )
    }

/**
 * Crops an upright capture to the part of the viewfinder that was on
 * screen. The preview fills the panel width, overflowing vertically; the
 * visible slice runs from the window top (or the picture's own top) down
 * to the window bottom. Returns the cropped bitmap plus the slice's
 * position and height in panel-local px, for the confirm overlay. Without
 * geometry (never laid out — shouldn't happen) the full frame passes
 * through, centred.
 */
private fun Bitmap.cropToVisible(geo: PanelGeometry?): Triple<Bitmap, Int, Int> {
    if (geo == null || width == 0 || geo.width == 0) {
        return Triple(this, 0, height)
    }
    val scale = geo.width.toFloat() / width
    val contentHeight = height * scale
    val contentTop = geo.boxTopWindow + geo.height / 2f - contentHeight / 2f
    val visibleTop = max(contentTop, 0f)
    val visibleBottom = min(contentTop + contentHeight, geo.windowHeight.toFloat())
    if (visibleBottom <= visibleTop) return Triple(this, 0, height)
    val sourceTop = ((visibleTop - contentTop) / scale).roundToInt().coerceIn(0, height - 1)
    val sourceHeight = ((visibleBottom - visibleTop) / scale).roundToInt()
        .coerceIn(1, height - sourceTop)
    val cropped = if (sourceTop == 0 && sourceHeight == height) this else {
        Bitmap.createBitmap(this, 0, sourceTop, width, sourceHeight).also {
            if (it != this) recycle()
        }
    }
    return Triple(
        cropped,
        (visibleTop - geo.boxTopWindow).roundToInt(),
        (visibleBottom - visibleTop).roundToInt(),
    )
}

/**
 * Decodes, rotates upright and optionally mirrors in one createBitmap
 * call. The crop rect is normally the full frame — the tool sends what the
 * sensor saw, matching the width-filling viewfinder.
 */
private fun ImageProxy.toFramedBitmap(mirror: Boolean): Bitmap {
    val source = toBitmap()
    // cropRect is in buffer coordinates (pre-rotation). Fall back to the
    // full frame if it is degenerate or stale for this buffer.
    val rect = android.graphics.Rect(cropRect)
    if (rect.isEmpty || rect.left < 0 || rect.top < 0 ||
        rect.right > source.width || rect.bottom > source.height
    ) {
        rect.set(0, 0, source.width, source.height)
    }
    val rotation = imageInfo.rotationDegrees
    val matrix = Matrix().apply {
        if (rotation != 0) postRotate(rotation.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    val framed = Bitmap.createBitmap(
        source, rect.left, rect.top, rect.width(), rect.height(), matrix, true,
    )
    if (framed != source) source.recycle()
    return framed
}
