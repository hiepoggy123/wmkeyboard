package com.wasimaster.wmkeyboard.ime

import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.script
import com.wasimaster.wmkeyboard.core.script.ScriptId

/**
 * Bridges the app's layouts to Android's [InputMethodSubtype] system so the OS
 * language switcher (the "Choose input method" sheet, the launcher globe on some
 * devices) lists every enabled layout and hands selection back to us.
 *
 * `activeLayoutId` stays the single source of truth; the OS is mirrored
 * best-effort — see `WMKeyboardService.registerSubtypes` /
 * `mirrorSubtypeToOs` / `onCurrentInputMethodSubtypeChanged`. The subtype↔layout
 * bridge is the [LAYOUT_ID_KEY] entry in the subtype's extra-value string.
 */

/** Extra-value key carrying the layout id a subtype stands for. */
private const val LAYOUT_ID_KEY = "layoutId"

/** The extra-value string encoding [layoutId] for a subtype (`layoutId=<id>`). */
internal fun layoutExtraValue(layoutId: String): String = "$LAYOUT_ID_KEY=$layoutId"

/**
 * A 31-bit id derived only from the layout id, so a layout keeps the same
 * subtype identity across process restarts and app updates. Android persists the
 * user's enabled-subtype choice by this int — a value that shifted when, say, a
 * display name changed would silently disable the user's picks. Same algorithm
 * as [String.hashCode], forced non-negative (the framework rejects some negative
 * ids).
 */
internal fun stableSubtypeId(layoutId: String): Int {
    var h = 0
    for (c in layoutId) h = 31 * h + c.code
    return h and 0x7fffffff
}

/**
 * Reads the layout id out of a subtype's raw extra-value string, or null when the
 * subtype is not one of ours (e.g. the static bootstrap subtype from method.xml,
 * which carries no layout id). Pure so it is unit-testable without the Android
 * framework's [InputMethodSubtype].
 */
internal fun layoutIdFromExtraValue(extraValue: String?): String? {
    if (extraValue.isNullOrEmpty()) return null
    for (pair in extraValue.split(',')) {
        val eq = pair.indexOf('=')
        if (eq > 0 && pair.substring(0, eq) == LAYOUT_ID_KEY) {
            return pair.substring(eq + 1).ifEmpty { null }
        }
    }
    return null
}

/**
 * One OS subtype describing [spec]: locale + language tag for the switcher's
 * label, the layout id in the extra value so [layoutIdOf] can map a selection
 * back. Both `subtypeLocale` (legacy underscore form) and `languageTag` are set
 * — the tag is the modern field, but some OEM switchers still read the locale.
 *
 * [nameResId] chooses the label the switcher shows: 0 (the default) lets the
 * framework derive it from the locale (the plain language name); a string
 * resource containing `%s` is formatted with that locale name, e.g. a
 * "WM Keyboard · %s" resource yields an app-name-first label.
 */
fun subtypeFor(spec: LayoutSpec, nameResId: Int = 0): InputMethodSubtype {
    val lang = spec.language()
    val asciiCapable = spec.script().id == ScriptId.LATIN
    return InputMethodSubtypeBuilder()
        .setSubtypeMode("keyboard")
        .setSubtypeNameResId(nameResId)
        .setSubtypeLocale(lang.localeTag.replace('-', '_'))
        .setLanguageTag(lang.localeTag)
        .setSubtypeExtraValue(layoutExtraValue(spec.id))
        .setIsAsciiCapable(asciiCapable)
        .setOverridesImplicitlyEnabledSubtype(false)
        .setSubtypeId(stableSubtypeId(spec.id))
        .build()
}

/** The layout id a subtype stands for, or null if it is not one of ours. */
fun layoutIdOf(subtype: InputMethodSubtype?): String? =
    layoutIdFromExtraValue(subtype?.extraValue)
