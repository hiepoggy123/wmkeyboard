package com.wasimaster.wmkeyboard.core.content

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * One line of user-facing text a file reader in this module produces, as a
 * resource plus the values that fill its placeholders.
 *
 * Nothing here holds a `Context`. The readers run off the main thread and unit
 * tests drive them with no Android around them, so a line put into words that
 * early would keep the language the device had when the file was read. The
 * screen that shows the line calls [resolve], and the words follow the language
 * the user reads them in.
 *
 * Set [stringRes] for a plain line, or [pluralsRes] together with [quantity]
 * when the wording depends on a count. [args] fills the placeholders in order,
 * and for a plural line the count is normally its first entry as well. Two
 * arguments is the whole budget: a line that needs more is a line that is too
 * long to read.
 *
 * :core:icons has its own `IconText` and :core:plugins its own `PluginText`,
 * both the same shape. They stay separate because the modules do not depend on
 * each other; this one is shared by every reader inside :core:content.
 */
data class ContentText(
    @get:StringRes val stringRes: Int = 0,
    @get:PluralsRes val pluralsRes: Int = 0,
    val quantity: Int = 0,
    val args: List<Any> = emptyList(),
) {

    /**
     * The finished line, in the language [context] is configured for.
     *
     * The arguments go in one by one rather than spread out of [args]: a spread
     * copies the array on every call, and the build reports that as a
     * performance finding.
     */
    fun resolve(context: Context): String {
        val a = args
        if (pluralsRes != 0) {
            return when (a.size) {
                0 -> context.resources.getQuantityString(pluralsRes, quantity)
                1 -> context.resources.getQuantityString(pluralsRes, quantity, a[0])
                else -> context.resources.getQuantityString(pluralsRes, quantity, a[0], a[1])
            }
        }
        return when (a.size) {
            0 -> context.getString(stringRes)
            1 -> context.getString(stringRes, a[0])
            else -> context.getString(stringRes, a[0], a[1])
        }
    }
}
