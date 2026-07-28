package com.wasimaster.wmkeyboard.core.util

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream

/**
 * [ContentResolver.openInputStream] with the null case turned into a typed,
 * described failure.
 *
 * The platform returns null when the provider is gone, the grant has lapsed, or
 * the document was deleted between the picker and the read — all routine on a
 * keyboard that imports themes, dictionaries and icon packs from other apps.
 * Every caller already runs inside a `runCatching`/`try` that shows an import
 * error, so the only thing `!!` bought was a NullPointerException with no URI
 * in it.
 */
fun ContentResolver.requireInputStream(uri: Uri): InputStream =
    openInputStream(uri) ?: throw IOException("Cannot open $uri for reading")
