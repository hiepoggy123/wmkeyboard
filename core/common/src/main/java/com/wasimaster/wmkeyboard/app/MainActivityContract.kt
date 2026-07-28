package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.Intent

/**
 * How library modules (the IME service in particular) launch the settings
 * activity without a compile-time dependency on the :app module above them.
 * The component is named as a string; the manifest entry and MainActivity's
 * companion constants both key off these values, so they cannot drift apart
 * without one of them failing to resolve.
 */
object MainActivityContract {
    const val CLASS_NAME = "com.wasimaster.wmkeyboard.app.MainActivity"

    /** A [com.wasimaster.wmkeyboard.core.settings.ToolbarTool].name to open the settings page for. */
    const val EXTRA_OPEN_TOOL = "open_tool"

    /** A settings navigation route to open directly (e.g. "themes"). */
    const val EXTRA_OPEN_ROUTE = "open_route"

    fun intent(context: Context): Intent =
        Intent()
            .setClassName(context, CLASS_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
