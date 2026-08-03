package com.wasimaster.wmkeyboard.app

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.mlkit.MlKitInit
import com.wasimaster.wmkeyboard.core.stickers.OutlineSpec
import com.wasimaster.wmkeyboard.core.stickers.ProcessedSticker
import com.wasimaster.wmkeyboard.core.stickers.StickerAddResult
import com.wasimaster.wmkeyboard.core.stickers.StickerImage
import com.wasimaster.wmkeyboard.core.stickers.StickerOutline
import com.wasimaster.wmkeyboard.core.stickers.StickerPackStore
import com.wasimaster.wmkeyboard.core.stickers.SubjectCutout
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wasimaster.wmkeyboard.common.R as CommonR

/** Settings route hosting the sticker editor. */
internal const val STICKER_EDITOR_ROUTE = "sticker_editor"

/**
 * What the editor is about to edit, and where the result goes.
 *
 * Both entry points reduce to bytes: the photo picker has already read them,
 * and a re-edit reads the stored WebP. One code path, and no `content://`
 * permission to keep alive across a navigation.
 */
internal class StickerEditRequest(
    val packId: String,
    val bytes: ByteArray,
    /** Non-null when replacing an existing sticker in place. */
    val stickerId: String?,
)

/**
 * The pending edit. Process-level for the same reason [PhotoSelection] is: a
 * route argument is a string in a URL, this is a megabyte of image, and the
 * app carries no view models. The screen captures it once, so clearing it on
 * the way out cannot make the destination pop itself twice.
 */
internal object StickerEditHandoff {
    @Volatile
    var current: StickerEditRequest? = null
}

/** The sticker canvas, and so the size everything in the editor works at. */
private const val CANVAS = StickerImage.TARGET_SIZE

/** How deep undo goes. Each step is one alpha snapshot, 256 KB. */
private const val UNDO_DEPTH = 12

private const val MIN_BRUSH_PX = 8f
private const val MAX_BRUSH_PX = 96f

/** What the editor is doing to the picture right now. */
private enum class EditorMode { CROP, ERASE, RESTORE, BORDER }

/**
 * Crop, background removal and a border, for one sticker.
 *
 * Everything happens at 512×512, the sticker canvas: the crop is applied
 * first and the result letterboxed into that square, so from then on a brush
 * pixel, a mask pixel and an output pixel are the same pixel and nothing has
 * to be projected between coordinate systems.
 *
 * The mask is a bitmap and not a list of strokes because it has two authors —
 * the brush and the segmenter — and they have to compose. Undo covers raster
 * work only: the border is a parameter, so putting it back is its own undo.
 *
 * Edits are destructive. No copy of the source is kept, which is why a second
 * crop asks first, and why an empty segmentation mask is never applied.
 */
@Composable
internal fun StickerEditorScreen(request: StickerEditRequest, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { StickerPackStore.get(context) }

    val source by produceState<Bitmap?>(initialValue = null, request) {
        value = withContext(Dispatchers.IO) {
            runCatching { StickerImage.decodeForEditing(request.bytes) }.getOrNull()
        }
    }
    val bitmap = source
    if (bitmap == null) {
        CaptionText(stringResource(CommonR.string.common_loading))
        return
    }

    val state = remember(bitmap) { EditorState(bitmap) }
    DisposableEffect(state) { onDispose { state.recycle() } }

    var mode by remember { mutableStateOf(EditorMode.CROP) }
    var brushPx by remember { mutableFloatStateOf(32f) }
    var recropAsk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Whether the one-tap cutout is even a thing in this build. False in lite,
    // where the brushes are the whole story and a disabled button would only
    // raise a question the screen cannot answer.
    val cutoutSupported = SubjectCutout.supported
    var modelReady by remember { mutableStateOf(false) }
    LaunchedEffect(cutoutSupported) {
        if (cutoutSupported) {
            MlKitInit.ensure(context.applicationContext)
            modelReady = SubjectCutout.modelReady(context)
        }
    }

    fun enterMode(next: EditorMode) {
        if (next == EditorMode.CROP && mode != EditorMode.CROP && state.masked) {
            recropAsk = true
        } else {
            if (mode == EditorMode.CROP && next != EditorMode.CROP) state.applyCrop()
            mode = next
        }
    }

    fun runCutout() {
        val message = context.getString(R.string.import_sticker_editor_cutout_progress)
        scope.launch {
            error = null
            if (!modelReady) {
                busy = context.getString(R.string.import_sticker_editor_cutout_download_progress)
                downloadProgress = 0f
                val installed = SubjectCutout.ensureModel(context) { downloadProgress = it }
                downloadProgress = -1f
                busy = null
                if (!installed) {
                    error = context.getString(R.string.import_sticker_editor_cutout_error)
                    return@launch
                }
                modelReady = true
            }
            busy = message
            val result = SubjectCutout.cutOut(context, state.base)
            busy = null
            when (result) {
                is SubjectCutout.Result.Ok -> {
                    state.pushUndo()
                    state.applyAlphaMask(result.alpha)
                    result.alpha.recycle()
                }
                SubjectCutout.Result.NoSubject ->
                    error = context.getString(R.string.import_sticker_editor_cutout_none_error)
                else -> error = context.getString(R.string.import_sticker_editor_cutout_error)
            }
        }
    }

    fun save() {
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val flat = state.render()
                val processed: ProcessedSticker? = StickerImage.encodeStill(flat)
                flat.recycle()
                processed?.let {
                    if (request.stickerId == null) {
                        store.addSticker(request.packId, it)
                    } else {
                        store.replaceStickerImage(request.packId, request.stickerId, it)
                    }
                }
            }
            saving = false
            when (result) {
                is StickerAddResult.Added -> onDone()
                StickerAddResult.PackFull ->
                    error = context.getString(R.string.import_sticker_pack_full)
                else -> error = context.getString(R.string.import_sticker_editor_save_error)
            }
        }
    }

    CaptionText(stringResource(R.string.import_sticker_editor_caption))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (mode == EditorMode.CROP) {
            CropCanvas(state)
        } else {
            EditorCanvas(state, mode, brushPx)
        }
        if (busy != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (downloadProgress >= 0f) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    )
                } else {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(8.dp))
                Text(busy.orEmpty(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    ChoiceControl(
        options = buildList {
            add(EditorMode.CROP to stringResource(R.string.import_sticker_editor_crop_action))
            add(EditorMode.ERASE to stringResource(R.string.import_sticker_editor_erase_action))
            add(EditorMode.RESTORE to stringResource(R.string.import_sticker_editor_restore_action))
            add(EditorMode.BORDER to stringResource(R.string.import_sticker_editor_border_action))
        },
        selected = mode,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { enterMode(it) }

    when (mode) {
        EditorMode.CROP -> CaptionText(stringResource(R.string.import_sticker_editor_crop_hint))
        EditorMode.ERASE, EditorMode.RESTORE -> {
            SliderRow(
                title = stringResource(R.string.import_sticker_editor_brush_size_label),
                value = brushPx,
                range = MIN_BRUSH_PX..MAX_BRUSH_PX,
                display = { "${it.roundToInt()}" },
            ) { brushPx = it }
        }
        EditorMode.BORDER -> {
            SliderRow(
                title = stringResource(R.string.import_sticker_editor_border_width_label),
                value = state.border.widthPx,
                range = 0f..OutlineSpec.MAX_WIDTH_PX,
                display = { "${it.roundToInt()}" },
            ) { state.changeBorder(state.border.copy(widthPx = it)) }
            ChoiceControl(
                options = listOf(
                    AndroidColor.WHITE to stringResource(R.string.import_sticker_editor_border_white_option),
                    AndroidColor.BLACK to stringResource(R.string.import_sticker_editor_border_black_option),
                ),
                selected = state.border.color,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { state.changeBorder(state.border.copy(color = it)) }
        }
    }

    if (cutoutSupported && mode != EditorMode.CROP) {
        SettingsGroup {
            item {
                WmRow(
                    title = stringResource(R.string.import_sticker_editor_cutout_action),
                    subtitle = if (modelReady) {
                        stringResource(R.string.import_sticker_editor_cutout_subtitle)
                    } else {
                        stringResource(R.string.import_sticker_editor_cutout_download_subtitle)
                    },
                    enabled = busy == null,
                    onClick = { runCutout() },
                )
            }
        }
    }

    error?.let { CaptionText(it, error = true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { state.undo() },
            enabled = state.undoDepth > 0 && busy == null,
        ) { Text(stringResource(R.string.import_sticker_editor_undo_action)) }
        Spacer(Modifier.fillMaxWidth().weight(1f))
        TextButton(onClick = onDone) { Text(stringResource(CommonR.string.common_cancel)) }
        Button(onClick = { save() }, enabled = !saving && busy == null) {
            Text(stringResource(CommonR.string.common_save))
        }
    }

    if (recropAsk) {
        AlertDialog(
            onDismissRequest = { recropAsk = false },
            title = { Text(stringResource(R.string.import_sticker_editor_recrop_title)) },
            text = { Text(stringResource(R.string.import_sticker_editor_recrop_body)) },
            confirmButton = {
                TextButton(onClick = {
                    recropAsk = false
                    state.resetMask()
                    mode = EditorMode.CROP
                }) { Text(stringResource(CommonR.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { recropAsk = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

/** The crop step: the picture pans and zooms under a square frame. */
@Composable
private fun CropCanvas(state: EditorState) {
    val source = state.source
    var frame by remember { mutableStateOf(IntSize.Zero) }

    fun coverScale(): Float =
        if (frame == IntSize.Zero) 1f
        else max(frame.width / source.width.toFloat(), frame.height / source.height.toFloat())

    fun clamp(offset: Offset, zoom: Float): Offset {
        val scale = coverScale() * zoom
        val maxX = ((source.width * scale - frame.width) / 2f).coerceAtLeast(0f)
        val maxY = ((source.height * scale - frame.height) / 2f).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned {
                if (frame != it.size) {
                    frame = it.size
                    state.cropFrame = it.size
                    state.cropOffset = clamp(state.cropOffset, state.cropZoom)
                }
            }
            .pointerInput(source) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    state.cropZoom = (state.cropZoom * gestureZoom).coerceIn(1f, 6f)
                    state.cropOffset = clamp(state.cropOffset + pan, state.cropZoom)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val scale = coverScale() * state.cropZoom
            val width = (source.width * scale).roundToInt()
            val height = (source.height * scale).roundToInt()
            drawImage(
                image = source.asImageBitmap(),
                dstOffset = IntOffset(
                    ((size.width - width) / 2f + state.cropOffset.x).roundToInt(),
                    ((size.height - height) / 2f + state.cropOffset.y).roundToInt(),
                ),
                dstSize = IntSize(width, height),
            )
        }
    }
}

/**
 * The cut-out picture over a checkerboard, with the brush on top.
 *
 * Strokes are consumed on the Initial pass, the way the handwriting panel
 * does it: the screen scrolls, and a brush that let the drag through would
 * scroll the page instead of erasing anything.
 */
@Composable
private fun EditorCanvas(state: EditorState, mode: EditorMode, brushPx: Float) {
    val erasing = mode == EditorMode.ERASE
    val painting = mode == EditorMode.ERASE || mode == EditorMode.RESTORE
    var side by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned { side = it.size.width }
            .then(
                if (!painting) Modifier else Modifier.pointerInput(mode, brushPx, side) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        down.consume()
                        if (side <= 0) return@awaitEachGesture
                        val scale = CANVAS.toFloat() / side
                        state.pushUndo()
                        var last = down.position * scale
                        state.paint(last, last, brushPx, erasing)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val next = change.position * scale
                            state.paint(last, next, brushPx, erasing)
                            last = next
                            change.consume()
                        }
                    }
                },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            // Transparency has to look like transparency, or an erased
            // background reads as a white one.
            val cell = size.width / 16f
            var row = 0
            while (row * cell < size.height) {
                var column = 0
                while (column * cell < size.width) {
                    val dark = (row + column) % 2 == 0
                    drawRect(
                        color = if (dark) Color(0xFFE0E0E0) else Color(0xFFF5F5F5),
                        topLeft = Offset(column * cell, row * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                    column++
                }
                row++
            }
            // Read the counter so a mutation of the bitmaps in place still
            // redraws; Compose cannot see inside them.
            @Suppress("UNUSED_EXPRESSION")
            state.version
            val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
            state.outline?.let {
                drawImage(it.asImageBitmap(), dstOffset = IntOffset.Zero, dstSize = target)
            }
            drawImage(
                state.subject.asImageBitmap(),
                dstOffset = IntOffset.Zero,
                dstSize = target,
            )
        }
    }
}

/**
 * The editor's pixels: the decoded source, the square it is cropped into, the
 * mask over it and the border under it.
 *
 * The bitmaps are mutated in place, so Compose cannot diff them; [version] is
 * the signal instead, read inside every draw and bumped by every change.
 */
private class EditorState(val source: Bitmap) {

    /** The cropped picture, letterboxed into the sticker canvas. */
    val base: Bitmap = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)

    /** Opaque white keeps a pixel; cleared pixels are erased. */
    private val mask: Bitmap = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)

    /** [base] with [mask] applied — what the user is looking at. */
    val subject: Bitmap = Bitmap.createBitmap(CANVAS, CANVAS, Bitmap.Config.ARGB_8888)

    /** The border layer, drawn behind [subject]; null when there is none. */
    var outline: Bitmap? = null
        private set

    var version by mutableIntStateOf(0)
        private set

    var border by mutableStateOf(OutlineSpec())
        private set

    var undoDepth by mutableIntStateOf(0)
        private set

    /** Whether anything has been erased yet; a re-crop would throw it away. */
    var masked = false
        private set

    var cropZoom by mutableFloatStateOf(1f)
    var cropOffset by mutableStateOf(Offset.Zero)
    var cropFrame: IntSize = IntSize.Zero

    private val undo = ArrayDeque<Bitmap>()

    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val restorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = AndroidColor.WHITE
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    init {
        resetMask()
        applyCrop()
    }

    /** Bakes the current pan and zoom into [base]. */
    fun applyCrop() {
        val frame = cropFrame
        val canvas = AndroidCanvas(base)
        canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val src = if (frame == IntSize.Zero) {
            Rect(0, 0, source.width, source.height)
        } else {
            val scale = max(
                frame.width / source.width.toFloat(),
                frame.height / source.height.toFloat(),
            ) * cropZoom
            val width = (frame.width / scale).roundToInt().coerceIn(1, source.width)
            val height = (frame.height / scale).roundToInt().coerceIn(1, source.height)
            val left = ((source.width - width) / 2f - cropOffset.x / scale)
                .roundToInt().coerceIn(0, source.width - width)
            val top = ((source.height - height) / 2f - cropOffset.y / scale)
                .roundToInt().coerceIn(0, source.height - height)
            Rect(left, top, left + width, top + height)
        }
        // Letterboxed, not stretched: a crop frame is square but a source
        // that was never cropped is whatever shape it arrived in.
        val scale = CANVAS.toFloat() / maxOf(src.width(), src.height())
        val outWidth = (src.width() * scale).roundToInt().coerceIn(1, CANVAS)
        val outHeight = (src.height() * scale).roundToInt().coerceIn(1, CANVAS)
        val left = (CANVAS - outWidth) / 2
        val top = (CANVAS - outHeight) / 2
        canvas.drawBitmap(
            source,
            src,
            Rect(left, top, left + outWidth, top + outHeight),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        recompose()
    }

    /** Clears every erased pixel and the undo history with it. */
    fun resetMask() {
        AndroidCanvas(mask).drawColor(AndroidColor.WHITE, PorterDuff.Mode.SRC)
        masked = false
        undo.forEach { it.recycle() }
        undo.clear()
        undoDepth = 0
        recompose()
    }

    /** One undo step per stroke, and one before a cutout. */
    fun pushUndo() {
        undo.addLast(mask.extractAlpha())
        if (undo.size > UNDO_DEPTH) undo.removeFirst().recycle()
        undoDepth = undo.size
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        undoDepth = undo.size
        val canvas = AndroidCanvas(mask)
        canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(previous, 0f, 0f, Paint().apply { color = AndroidColor.WHITE })
        previous.recycle()
        masked = undo.isNotEmpty() || masked
        recompose()
    }

    /** Draws one segment of a stroke into the mask. */
    fun paint(from: Offset, to: Offset, widthPx: Float, erase: Boolean) {
        val paint = if (erase) erasePaint else restorePaint
        paint.strokeWidth = widthPx
        AndroidCanvas(mask).drawLine(from.x, from.y, to.x, to.y, paint)
        if (erase) masked = true
        recompose()
    }

    /** Replaces the mask with a segmenter's own, keeping nothing else. */
    fun applyAlphaMask(alpha: Bitmap) {
        val canvas = AndroidCanvas(mask)
        canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(alpha, null, Rect(0, 0, CANVAS, CANVAS), Paint().apply {
            color = AndroidColor.WHITE
        })
        masked = true
        recompose()
    }

    fun changeBorder(spec: OutlineSpec) {
        border = spec
        recompose()
    }

    /** The flattened sticker: border under subject, on transparency. */
    fun render(): Bitmap = StickerOutline.render(subject, border)

    fun recycle() {
        base.recycle()
        mask.recycle()
        subject.recycle()
        outline?.recycle()
        undo.forEach { it.recycle() }
        undo.clear()
    }

    /** Rebuilds what is drawn: the masked subject, then its border. */
    private fun recompose() {
        val canvas = AndroidCanvas(subject)
        canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(base, 0f, 0f, null)
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
        outline?.recycle()
        outline = StickerOutline.renderLayer(subject, border)
        version++
    }
}
