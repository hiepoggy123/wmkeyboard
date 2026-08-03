package com.wasimaster.wmkeyboard.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.R

/**
 * The settings app's own type scale.
 *
 * Stock Material 3 is Roboto at Material's sizes, which is the loudest "this is
 * an AOSP settings screen" tell the app has. The scale below replaces both
 * halves of that: two bundled families instead of the system face, and a
 * hierarchy built for a dense list of rows rather than for Material's sample
 * screens.
 *
 * The families are split by job:
 * - **Manrope** carries anything that announces a page or a group — the
 *   collapsing heading, dialog titles, section headers. Its semi-rounded
 *   geometry is what gives the app a voice, and at heading sizes there is room
 *   for that character to read.
 * - **Inter** carries everything that is read rather than looked at: row names,
 *   subtitles, captions, button labels. It stays legible down to 11sp, which is
 *   where a display face would start costing the user something.
 *
 * Both are subsetted static weights in `res/font` rather than variable fonts,
 * because variation settings need API 26 and this app still runs on 24, where a
 * variable file would collapse every weight onto its default instance and
 * flatten the hierarchy. They are bundled rather than fetched through the
 * downloadable-fonts provider (which the keyboard's own font picker uses, see
 * [com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts]) because chrome cannot wait
 * for a download: an async face means a Roboto first frame and a reflow, and
 * nothing at all on a device without Play Services.
 *
 * Glyphs neither family carries — the native language names on the language
 * screen, a CJK sample in a font preview — fall back to the system face per
 * glyph, the same way the keyboard's Latin display faces do.
 */

/** Row names, subtitles, captions, buttons: the text that is actually read. */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Headings and section titles: the text that announces where you are. */
private val Manrope = FontFamily(
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    // Nothing heavier is bundled, so a Black request resolves to Bold rather
    // than to a synthesised weight.
    Font(R.font.manrope_bold, FontWeight.ExtraBold),
)

/**
 * Tracking is negative on the display sizes and positive on the small ones, for
 * the usual reason: letterforms set large look loose at the spacing that keeps
 * them readable small. Material's scale tracks everything slightly positive,
 * which is part of why its headings read as generic.
 */
private fun display(size: Float, lineHeight: Float, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private fun text(size: Float, lineHeight: Float, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * The scale itself, in the order Material names it.
 *
 * Sizes are one step tighter than Material's below `titleLarge` and one step
 * larger above it: a settings row is a name and a subtitle repeated fifty
 * times, so it wants less air, while the page heading is the one place the app
 * gets to make an impression, so it wants more.
 *
 * Only the roles the app and the Material components it uses actually reach for
 * are tuned by hand; the display sizes are there because `DatePicker` and
 * friends read them, and follow the same rules.
 */
internal val WmTypography = Typography(
    displayLarge = display(46f, 52f, FontWeight.Bold, -1.0f),
    displayMedium = display(38f, 44f, FontWeight.Bold, -0.8f),
    displaySmall = display(32f, 38f, FontWeight.Bold, -0.6f),

    // The expanded heading of every screen. `WmCollapsingTopBar` overrides the
    // size (it picks one that keeps a long title on a single line) and inherits
    // the family, weight and tracking from here.
    headlineLarge = display(33f, 40f, FontWeight.Bold, -0.6f),
    headlineMedium = display(29f, 36f, FontWeight.Bold, -0.5f),
    // Dialog titles, and the headings inside the addon screens.
    headlineSmall = display(23f, 29f, FontWeight.SemiBold, -0.3f),

    titleLarge = display(20f, 26f, FontWeight.Bold, -0.2f),
    titleMedium = text(16f, 22f, FontWeight.SemiBold, -0.05f),
    // `SectionHeader` and its siblings: small, but a heading, so it stays in the
    // display face and takes the tracking that goes with being set small.
    titleSmall = display(15f, 20f, FontWeight.Bold, 0.1f),

    // A row's name. Medium rather than Normal because a list of names reads as
    // a list of targets, and 15.5sp because 16 leaves too little room for the
    // subtitle under it at the row height Material gives us.
    bodyLarge = text(15.5f, 21f, FontWeight.Medium, 0f),
    // A row's subtitle, and the body of a dialog.
    bodyMedium = text(13.5f, 19f, FontWeight.Normal, 0.05f),
    // `CaptionText`: paragraphs of explanation, so Normal weight and a line
    // height that stays comfortable over several lines.
    bodySmall = text(12.5f, 17.5f, FontWeight.Normal, 0.1f),

    labelLarge = text(14f, 18f, FontWeight.SemiBold, 0.05f),
    labelMedium = text(12f, 16f, FontWeight.SemiBold, 0.1f),
    labelSmall = text(11f, 15f, FontWeight.SemiBold, 0.15f),
)
