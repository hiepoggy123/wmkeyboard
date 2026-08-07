package com.wasimaster.wmkeyboard.ime.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.tasks.Task
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.clipboard.ClipLinks
import com.wasimaster.wmkeyboard.core.clipboard.LinkPreview
import com.wasimaster.wmkeyboard.core.tools.EggLinks
import com.wasimaster.wmkeyboard.core.tools.LinkPreviewClient
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wasimaster.wmkeyboard.core.util.runCancellable
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.R
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// ---- OCR (text recognition) tool ----

/** One recognized word, tappable in the result view. [id] is global. */
private class OcrWord(val id: Int, val text: String)

/** A frozen capture with its recognized text, as lines of words. */
private class OcrResult(val bitmap: Bitmap, val lines: List<List<OcrWord>>) {
    val wordCount = lines.sumOf { it.size }
}

private sealed interface OcrStage {
    data object Viewfinder : OcrStage
    data class Recognizing(val bitmap: Bitmap) : OcrStage
    data class Done(val result: OcrResult) : OcrStage
}

/**
 * OCR tool: point the camera at printed text, capture, and get the words
 * back as tappable chips — deselect the parts you don't want, then insert
 * or copy just the rest. Runs ML Kit's on-device Latin text recognizer, so
 * nothing leaves the phone. Unlike the other tool panels this one covers
 * the toolbar too ([keyRowsHeight] + [TopBarHeight]): reading text off a
 * photo needs all the room the keyboard has.
 */
@Composable
internal fun OcrPanel(
    state: KeyboardUiState,
    onInsert: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    val height = keyRowsHeight(state) + fullBleedHiddenRows(state)
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    PermissionRecheck { hasPermission = hasCameraPermission(context) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (hasPermission) {
            OcrContent(
                onInsert = onInsert,
                onClose = onClose,
                autoSelect = state.settings.ocrAutoSelectWords,
            )
        } else {
            CameraPermissionPrompt(
                stringResource(R.string.ime_scanner_ocr_permission_body),
                onRequestPermission,
            )
        }
        if (!hasPermission) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                CameraChipButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    description = stringResource(R.string.ime_scanner_ocr_close_desc),
                    active = false,
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
private fun OcrContent(
    onInsert: (String) -> Unit,
    onClose: () -> Unit,
    autoSelect: Boolean,
) {
    val kb = LocalKbTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val feedback = LocalKeyPressFeedback.current

    var stage by remember { mutableStateOf<OcrStage>(OcrStage.Viewfinder) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val provider by produceState<ProcessCameraProvider?>(null) {
        value = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
    }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // See CameraPanel: SurfaceView ignores Compose bounds in the IME
            // window; TextureView composits like a normal view.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // Small print needs more pixels than chat photos, but the full
            // sensor is still overkill and slow to process.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(2048, 2048),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
            .build()
    }

    // Bind only while the viewfinder is up; the frozen capture and its
    // words are the whole UI afterwards, so release the camera.
    val scanning = stage is OcrStage.Viewfinder
    DisposableEffect(provider, scanning, viewSize) {
        val cameraProvider = provider
        if (cameraProvider != null && scanning && viewSize != IntSize.Zero) {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            // Shared ViewPort = WYSIWYG: the capture is cropped to exactly
            // what the viewfinder framed.
            val viewPort = ViewPort.Builder(
                Rational(viewSize.width, viewSize.height),
                previewView.display?.rotation ?: Surface.ROTATION_0,
            ).build()
            val group = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .build()
            camera = runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, backOrFrontSelector(cameraProvider) ?: return@runCatching null, group,
                )
            }.getOrNull()
            camera?.cameraControl?.enableTorch(torchOn)
        }
        onDispose {
            camera = null
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    fun capture() {
        if (stage !is OcrStage.Viewfinder || viewSize == IntSize.Zero) return
        feedback()
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCancellable {
                    imageCapture.awaitCapture(context).use { it.toFramedBitmap(mirror = false) }
                }.getOrNull()
            } ?: return@launch
            stage = OcrStage.Recognizing(bitmap)
            val lines = withContext(Dispatchers.Default) {
                runCancellable {
                    var id = 0
                    recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                        .textBlocks
                        .flatMap { block ->
                            block.lines.map { line ->
                                line.elements.map { OcrWord(id++, it.text) }
                            }
                        }
                }.getOrDefault(emptyList())
            }
            stage = OcrStage.Done(OcrResult(bitmap, lines))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it },
    ) {
        when (val current = stage) {
            is OcrStage.Done -> {
                OcrResultView(
                    result = current.result,
                    autoSelect = autoSelect,
                    onInsert = onInsert,
                    onRescan = { stage = OcrStage.Viewfinder },
                    onClose = onClose,
                )
                return@Box
            }
            is OcrStage.Recognizing -> {
                Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.ime_scanner_ocr_capture_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ime_scanner_ocr_reading_progress),
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                return@Box
            }
            OcrStage.Viewfinder -> Unit
        }

        val activeProvider = provider
        if (activeProvider != null && backOrFrontSelector(activeProvider) == null) {
            PanelCenteredMessage(stringResource(R.string.ime_scanner_no_camera))
            return@Box
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Text(
            stringResource(R.string.ime_scanner_ocr_hint),
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )

        Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            CameraChipButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                description = stringResource(R.string.ime_scanner_ocr_close_desc),
                active = false,
            ) {
                feedback()
                onClose()
            }
        }
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CameraChipButton(
                    icon = if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    description = stringResource(R.string.ime_scanner_torch_desc),
                    active = torchOn,
                ) {
                    feedback()
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }
            }
        }
        // Shutter, same look as the camera tool's.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
                .padding(5.dp)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(Unit) { detectTapGestures { capture() } },
        )
    }
}

/**
 * The recognized words as chips, grouped by line. With [autoSelect] on
 * (the default setting) everything starts selected and tapping a word
 * toggles it off, so copying "only that one number" is a couple of taps
 * instead of a fiddly text selection; with it off the words start blank
 * and get picked one by one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OcrResultView(
    result: OcrResult,
    autoSelect: Boolean,
    onInsert: (String) -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val context = LocalContext.current
    val feedback = LocalKeyPressFeedback.current
    var selected by remember(result, autoSelect) {
        mutableStateOf(
            if (autoSelect) result.lines.flatten().map { it.id }.toSet() else emptySet()
        )
    }

    fun selectedText(): String = result.lines
        .map { line -> line.filter { it.id in selected }.joinToString(" ") { it.text } }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CameraChipButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                description = stringResource(R.string.ime_scanner_ocr_close_desc),
                active = false,
            ) {
                feedback()
                onClose()
            }
            CameraChipButton(
                icon = Icons.Outlined.Refresh,
                description = stringResource(R.string.ime_scanner_scan_again_action),
                active = false,
            ) {
                feedback()
                onRescan()
            }
            Text(
                if (result.wordCount == 0) {
                    stringResource(R.string.ime_scanner_ocr_no_text_label)
                } else {
                    pluralStringResource(
                        R.plurals.ime_scanner_ocr_word_count,
                        result.wordCount,
                        selected.size,
                        result.wordCount,
                    )
                },
                color = kb.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (result.wordCount > 0) {
                val allSelected = selected.size == result.wordCount
                CameraChipButton(
                    icon = if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                    description = if (allSelected) {
                        stringResource(R.string.ime_scanner_ocr_deselect_all_desc)
                    } else {
                        stringResource(CommonR.string.common_select_all)
                    },
                    active = false,
                ) {
                    feedback()
                    selected =
                        if (allSelected) emptySet()
                        else result.lines.flatten().map { it.id }.toSet()
                }
            }
        }

        if (result.wordCount == 0) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.ime_scanner_ocr_empty),
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (line in result.lines) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (word in line) {
                            val on = word.id in selected
                            Text(
                                word.text,
                                color = if (on) kb.toolCircleActiveIcon else kb.suggestionText,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) kb.toolCircleActive else kb.chip)
                                    .pointerInput(word.id) {
                                        detectTapGestures {
                                            feedback()
                                            selected = if (on) selected - word.id else selected + word.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                CaptureActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(CommonR.string.common_copy),
                    accent = false,
                ) {
                    feedback()
                    val text = selectedText()
                    if (text.isNotEmpty()) {
                        copyPlainText(context, text)
                        Toast.makeText(
                            context,
                            R.string.ime_scanner_copied_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                CaptureActionButton(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    label = stringResource(R.string.ime_scanner_insert_action),
                    accent = true,
                ) {
                    val text = selectedText()
                    if (text.isNotEmpty()) onInsert(text)
                }
            }
        }
    }
}

// ---- barcode / QR scanner tool ----

/** A decoded barcode frozen on screen, awaiting insert/copy/rescan. */
private class ScannedCode(val value: String, @StringRes val formatLabelRes: Int)

/**
 * Barcode & QR scanner: a live viewfinder that decodes continuously
 * (ML Kit, on-device); the first hit freezes into a result card with
 * insert and copy actions. Insert types the barcode's text into the
 * editor, like any other key press.
 */
@Composable
internal fun QrScanPanel(
    state: KeyboardUiState,
    onInsert: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    // Full-bleed like the OCR tool: the toolbar hides, the viewfinder
    // absorbs its height and the back button sits at the very top instead
    // of below a blank strip.
    val height = keyRowsHeight(state) + fullBleedHiddenRows(state)
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    PermissionRecheck { hasPermission = hasCameraPermission(context) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (hasPermission) {
            QrScanContent(
                onInsert = onInsert,
                onOpenUrl = onOpenUrl,
                haptics = state.settings.qrScanHaptics,
                autoInsert = state.settings.qrScanAutoInsert,
                linkPreviews = state.settings.qrScanLinkPreviews,
            )
        } else {
            CameraPermissionPrompt(
                stringResource(R.string.ime_scanner_qr_permission_body),
                onRequestPermission,
            )
        }
        val feedback = LocalKeyPressFeedback.current
        Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            CameraChipButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                description = stringResource(R.string.ime_scanner_qr_close_desc),
                active = false,
            ) {
                feedback()
                onClose()
            }
        }
    }
}

@Composable
private fun QrScanContent(
    onInsert: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    haptics: Boolean,
    autoInsert: Boolean,
    linkPreviews: Boolean,
) {
    val kb = LocalKbTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedback = LocalKeyPressFeedback.current

    var result by remember { mutableStateOf<ScannedCode?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    // Current zoom, driven by pinch and shown as a pill. Zooming into a
    // small or distant code is often the difference between a decode and
    // not. Resets to 1x whenever the camera rebinds (e.g. after Rescan).
    var zoomRatio by remember(camera) { mutableFloatStateOf(1f) }

    val provider by produceState<ProcessCameraProvider?>(null) {
        value = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
    }
    val scanner = remember { BarcodeScanning.getClient() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Decode frames only while there's no result on screen. A frame is
    // skipped when the previous one is still being decoded (busy flag)
    // instead of queueing — barcodes don't need every frame.
    val scanning = result == null
    DisposableEffect(provider, scanning) {
        val cameraProvider = provider
        var busy = false
        if (cameraProvider != null && scanning) {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            )
                        )
                        .build()
                )
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                if (busy || result != null) {
                    proxy.close()
                    return@setAnalyzer
                }
                busy = true
                // toBitmap + rotation instead of the mediaImage path: one
                // copy per analyzed frame, but no experimental CameraX API.
                val bitmap = proxy.toBitmap()
                val rotation = proxy.imageInfo.rotationDegrees
                proxy.close()
                scanner.process(InputImage.fromBitmap(bitmap, rotation))
                    .addOnSuccessListener { barcodes ->
                        val hit = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                        val raw = hit?.rawValue
                        if (hit != null && raw != null && result == null) {
                            if (haptics) feedback()
                            // Freeze into the result card either way — with
                            // auto-insert the text is already committed and
                            // the card shows what went in (Rescan re-arms).
                            result = ScannedCode(raw, barcodeFormatLabelRes(hit.format))
                            if (autoInsert) onInsert(raw)
                        }
                    }
                    .addOnCompleteListener { busy = false }
            }
            camera = runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    backOrFrontSelector(cameraProvider) ?: return@runCatching null,
                    preview,
                    analysis,
                )
            }.getOrNull()
            camera?.cameraControl?.enableTorch(torchOn)
        }
        onDispose {
            camera = null
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val code = result
        if (code != null) {
            // A scanned code that is a bare URL gets an Open button and,
            // when link previews are on, its page title/description below.
            val url = remember(code) { ClipLinks.asUrl(code.value) }
            var preview by remember(code) { mutableStateOf<LinkPreview?>(null) }
            if (linkPreviews && url != null) {
                LaunchedEffect(url) {
                    preview = withContext(Dispatchers.IO) { LinkPreviewClient.fetch(url) }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LinkPreviewCard(linkPreviews && url != null, preview)
                // Easter egg: the one link every QR scanner eventually meets.
                // Matched on the raw payload, not on `url` — ClipLinks.asUrl
                // only accepts a bare URL and a rickroll deserves recognition
                // even when it travels with company.
                if (remember(code) { EggLinks.isRickroll(code.value) }) {
                    Text(
                        stringResource(R.string.ime_qr_rickroll_info),
                        color = kb.secondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    code.value,
                    color = kb.suggestionText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(code.formatLabelRes),
                    color = kb.secondaryText,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                // Open (URLs only) gets its own row above the rest, so the
                // primary action isn't crowded in with Rescan/Copy/Insert.
                if (url != null) {
                    CaptureActionButton(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = stringResource(R.string.ime_scanner_qr_open_action),
                        accent = false,
                    ) {
                        feedback()
                        onOpenUrl(url)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp, Alignment.CenterHorizontally,
                    ),
                ) {
                    CaptureActionButton(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(R.string.ime_scanner_scan_again_action),
                        accent = false,
                    ) {
                        feedback()
                        result = null
                    }
                    CaptureActionButton(
                        icon = Icons.Outlined.ContentCopy,
                        label = stringResource(CommonR.string.common_copy),
                        accent = false,
                    ) {
                        feedback()
                        copyPlainText(context, code.value)
                        Toast.makeText(
                            context,
                            R.string.ime_scanner_copied_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    CaptureActionButton(
                        icon = Icons.AutoMirrored.Outlined.Send,
                        label = stringResource(R.string.ime_scanner_insert_action),
                        accent = true,
                    ) { onInsert(code.value) }
                }
            }
            return@Box
        }

        val activeProvider = provider
        if (activeProvider != null && backOrFrontSelector(activeProvider) == null) {
            PanelCenteredMessage(stringResource(R.string.ime_scanner_no_camera))
            return@Box
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                // Pinch to zoom, clamped to what the lens supports.
                .pointerInput(camera) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val info = cam.cameraInfo.zoomState.value
                        val minZoom = info?.minZoomRatio ?: 1f
                        val maxZoom = info?.maxZoomRatio ?: 1f
                        val current = info?.zoomRatio ?: zoomRatio
                        val next = (current * gestureZoom).coerceIn(minZoom, maxZoom)
                        cam.cameraControl.setZoomRatio(next)
                        zoomRatio = next
                    }
                },
        )

        if (zoomRatio > 1.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    stringResource(R.string.ime_scanner_qr_zoom_label, zoomRatio),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Text(
            stringResource(R.string.ime_scanner_qr_hint),
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CameraChipButton(
                    icon = if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    description = stringResource(R.string.ime_scanner_torch_desc),
                    active = torchOn,
                ) {
                    feedback()
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }
            }
        }
    }
}

/**
 * The fetched page title/site/description for a scanned link, shown above
 * the raw URL. A small spinner stands in while the fetch is in flight;
 * a failed or empty fetch renders nothing (the URL alone still shows).
 */
@Composable
private fun LinkPreviewCard(enabled: Boolean, preview: LinkPreview?) {
    if (!enabled) return
    val kb = LocalKbTheme.current
    if (preview == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircularProgressIndicator(
                color = kb.secondaryText,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.ime_scanner_qr_link_progress),
                color = kb.secondaryText,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        return
    }
    if (preview.failed || preview.isEmpty) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (preview.title.isNotBlank()) {
            Text(
                preview.title,
                color = kb.suggestionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (preview.siteName.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(preview.siteName, color = kb.secondaryText, fontSize = 11.sp)
        }
        if (preview.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                preview.description,
                color = kb.secondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ---- shared bits ----

/**
 * Re-runs [onResume] when the keyboard comes back to the foreground — the
 * camera-permission dialog lives in a trampoline activity, so the panel
 * has to re-check the permission once it regains focus.
 */
@Composable
private fun PermissionRecheck(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** The camera-permission explainer + "Allow the camera" button. */
@Composable
private fun CameraPermissionPrompt(message: String, onRequestPermission: () -> Unit) {
    val kb = LocalKbTheme.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            color = kb.toolbarIcon,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
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
            Text(
                stringResource(R.string.ime_scanner_permission_action),
                color = kb.toolCircleActiveIcon,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Back camera, else front, else null — scanners never need a lens switch. */
private fun backOrFrontSelector(provider: ProcessCameraProvider): CameraSelector? = when {
    provider.hasCameraSafe(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
    provider.hasCameraSafe(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
    else -> null
}

private fun copyPlainText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("scanned text", text))
}

@StringRes
private fun barcodeFormatLabelRes(format: Int): Int = when (format) {
    Barcode.FORMAT_QR_CODE -> R.string.ime_scanner_format_qr_code
    Barcode.FORMAT_AZTEC -> R.string.ime_scanner_format_aztec
    Barcode.FORMAT_CODABAR -> R.string.ime_scanner_format_codabar
    Barcode.FORMAT_CODE_39 -> R.string.ime_scanner_format_code_39
    Barcode.FORMAT_CODE_93 -> R.string.ime_scanner_format_code_93
    Barcode.FORMAT_CODE_128 -> R.string.ime_scanner_format_code_128
    Barcode.FORMAT_DATA_MATRIX -> R.string.ime_scanner_format_data_matrix
    Barcode.FORMAT_EAN_8 -> R.string.ime_scanner_format_ean_8
    Barcode.FORMAT_EAN_13 -> R.string.ime_scanner_format_ean_13
    Barcode.FORMAT_ITF -> R.string.ime_scanner_format_itf
    Barcode.FORMAT_PDF417 -> R.string.ime_scanner_format_pdf417
    Barcode.FORMAT_UPC_A -> R.string.ime_scanner_format_upc_a
    Barcode.FORMAT_UPC_E -> R.string.ime_scanner_format_upc_e
    else -> R.string.ime_scanner_format_generic
}

/** Suspends over a Play Services [Task] (same trick as Handwriting.kt). */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.cancel(it) }
}
