package com.wasimaster.wmkeyboard.core.stickers

import android.graphics.Color
import kotlinx.serialization.Serializable

/**
 * What an edit looked like, kept beside the picture it was made from so that
 * re-opening a sticker resumes that edit rather than starting it again.
 *
 * The crop is stored as fractions of the source picture and not as the
 * editor's own pan and zoom, which are measured in the pixels of whatever
 * frame the screen gave the canvas that day. Fractions survive a different
 * screen, a different window size and a source decoded at a different scale.
 *
 * The erased parts are not in here: a mask is a picture, and it travels as a
 * lossless PNG beside this file ([hasMask] says whether there is one). See
 * [StickerPackStore] for where both live and why neither is ever exported.
 */
@Serializable
data class StickerEditState(
    /** Left edge of the kept rectangle, as a fraction of the source width. */
    val cropLeft: Float = 0f,
    /** Top edge, as a fraction of the source height. */
    val cropTop: Float = 0f,
    /** Width of the kept rectangle, as a fraction of the source width. */
    val cropWidth: Float = 1f,
    /** Height of the kept rectangle, as a fraction of the source height. */
    val cropHeight: Float = 1f,
    val borderWidthPx: Float = 0f,
    val borderColor: Int = Color.WHITE,
    /** The brush the user was last painting with, in canvas pixels. */
    val brushPx: Float = DEFAULT_BRUSH_PX,
    /** Whether a mask PNG was written beside this. */
    val hasMask: Boolean = false,
) {
    val border: OutlineSpec get() = OutlineSpec(borderWidthPx, borderColor)

    /** True when this describes an edit worth restoring at all. */
    val meaningful: Boolean
        get() = hasMask || border.visible ||
            cropWidth < FULL_FRAME || cropHeight < FULL_FRAME

    companion object {
        /** The brush a sticker that has never been painted on starts with. */
        const val DEFAULT_BRUSH_PX = 32f

        /** A crop that kept everything, allowing for rounding. */
        private const val FULL_FRAME = 0.999f
    }
}
