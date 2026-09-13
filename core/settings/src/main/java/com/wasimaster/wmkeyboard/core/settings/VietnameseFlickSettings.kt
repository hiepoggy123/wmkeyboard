package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.layout.FlickDirection
import kotlinx.serialization.Serializable

/**
 * Settings for Vietnamese Flick gesture typing and direction mapping.
 *
 * Left-hand keys default to UP / LEFT / DOWN:
 * - `a`: UP -> â, LEFT -> ă
 * - `e`: UP -> ê
 * - `d`: LEFT -> đ
 * - `s`: UP -> Sắc (acute)
 * - `f`: LEFT -> Huyền (grave)
 * - `r`: UP -> Hỏi (hook)
 * - `x`: DOWN -> Ngã (tilde)
 *
 * Right-hand keys default to UP / RIGHT / DOWN:
 * - `o`: UP -> ô, RIGHT -> ơ
 * - `u`: RIGHT -> ư
 * - `j`: DOWN -> Nặng (dot)
 */
@Serializable
data class VietnameseFlickSettings(
    val enabled: Boolean = true,
    val pureFlickMode: Boolean = true,

    // Vowel and consonant diacritics
    val dirA_Circumflex: FlickDirection = FlickDirection.UP,
    val dirA_Breve: FlickDirection = FlickDirection.LEFT,
    val dirE_Circumflex: FlickDirection = FlickDirection.UP,
    val dirD_Stroke: FlickDirection = FlickDirection.LEFT,
    val dirO_Circumflex: FlickDirection = FlickDirection.UP,
    val dirO_Horn: FlickDirection = FlickDirection.RIGHT,
    val dirU_Horn: FlickDirection = FlickDirection.RIGHT,

    // Tone diacritics
    val dirTone_Acute: FlickDirection = FlickDirection.UP,
    val dirTone_Grave: FlickDirection = FlickDirection.LEFT,
    val dirTone_Hook: FlickDirection = FlickDirection.UP,
    val dirTone_Tilde: FlickDirection = FlickDirection.DOWN,
    val dirTone_Dot: FlickDirection = FlickDirection.DOWN,
)
