package com.wasimaster.wmkeyboard.core.mlkit

import android.content.Context

/** Lite-flavor stand-in: no ML Kit in the build, so nothing to initialize. */
object MlKitInit {

    fun ensure(context: Context) = Unit
}
