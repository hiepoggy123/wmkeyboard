package com.wasimaster.wmkeyboard.core.util

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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

/**
 * [ContentResolver.openOutputStream] with the null case turned into a typed,
 * described failure, and with a mode that truncates.
 *
 * The default `"w"` does **not** imply `O_TRUNC` on every provider. Writing a
 * short document over a longer one then leaves the tail of the old one behind,
 * which for anything structured means a file that is the right size, has a
 * plausible name, and does not parse. `"wt"` is the mode that means what
 * everybody assumes `"w"` means.
 */
fun ContentResolver.requireOutputStream(uri: Uri, mode: String = "wt"): OutputStream =
    openOutputStream(uri, mode) ?: throw IOException("Cannot open $uri for writing")
