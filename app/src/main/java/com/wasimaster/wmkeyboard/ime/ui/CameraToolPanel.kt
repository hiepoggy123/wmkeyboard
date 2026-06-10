package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** A photo sitting in the confirm step: the file on disk plus its preview. */
private class PendingCapture(val file: File, val bitmap: Bitmap)

/**
 * In-keyboard camera. The live preview fills the tool viewbox
 * (centre-cropped, like a viewfinder) but the captured photo keeps the
 * camera's full frame — nothing is cut off the sent image. Controls:
 * shutter, front/back switch, flash mode, self-timer; after a capture,
 * retake or send (via commitContent, like clipboard images).
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
            CameraContent(state = state, onSend = onSend)
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
        // Back to the keys, styled like the viewfinder controls.
        Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            CameraChipButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                description = "Close camera",
                active = false,
            ) { onClose() }
        }
    }
}

@Composable
private fun CameraContent(
    state: KeyboardUiState,
    onSend: (File) -> Unit,
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

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
        capturing = true
        scope.launch {
            if (timerSeconds > 0) {
                for (second in timerSeconds downTo 1) {
                    countdown = second
                    delay(1000)
                }
                countdown = 0
            }
            val mirror = usingFront && state.settings.cameraMirrorFront
            val capture = withContext(Dispatchers.IO) {
                runCatching {
                    val proxy = imageCapture.awaitCapture(context)
                    // Full sensor frame — the viewfinder is a centre crop,
                    // but the photo itself keeps everything.
                    val bitmap = proxy.use { it.toUprightBitmap() }
                        .let { if (mirror) it.mirrored() else it }
                    val file = File(captureDir(context), "IMG_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    PendingCapture(file, bitmap)
                }.getOrNull()
            }
            if (capture != null) pending = capture
            capturing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val captured = pending
        if (captured != null) {
            // Fit, not crop: the confirm step shows exactly what will be
            // sent, letterboxed on black like a photo viewer.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
            Image(
                bitmap = captured.bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
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
                    scope.launch(Dispatchers.IO) { captured.file.delete() }
                    pending = null
                }
                CaptureActionButton(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    label = "Send",
                    accent = true,
                ) {
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
                    ) { frontFacing = !usingFront }
                }
            }
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

/** Decodes and rotates so the bitmap is upright regardless of sensor mount. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun Bitmap.mirrored(): Bitmap {
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
