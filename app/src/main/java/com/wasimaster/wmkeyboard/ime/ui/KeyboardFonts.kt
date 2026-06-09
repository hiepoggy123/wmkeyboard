package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as DownloadableFont
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import java.io.File

/**
 * Resolves the user's font choices into Compose [FontFamily]s.
 *
 * Google Fonts families are fetched through the Google Play Services font
 * provider (the system-level downloadable-fonts mechanism, cached
 * on-device after the first use); if the provider is missing or offline
 * the async font simply falls back to the platform default. Custom fonts
 * are files the user imported, copied into the app's private storage.
 *
 * Missing glyphs — a Latin display font on Bengali keys, for example —
 * fall back to the system font per-glyph, so exotic choices never leave
 * keys blank.
 */
object KeyboardFonts {

    const val DEFAULT_ID = "default"
    const val CUSTOM_ID = "custom"
    private const val GOOGLE_PREFIX = "google:"

    /**
     * Curated Google Fonts choices: workhorse sans faces, a few serifs,
     * monos and display faces, plus Bengali-script families (Hind
     * Siliguri, Baloo Da 2) that cover both scripts this keyboard types.
     */
    val googleFonts: List<String> = listOf(
        "Roboto", "Inter", "Open Sans", "Lato", "Montserrat", "Poppins",
        "Nunito", "Rubik", "Ubuntu", "Source Sans 3",
        "Merriweather", "Lora", "Playfair Display", "Roboto Slab",
        "Roboto Mono", "JetBrains Mono",
        "Comfortaa", "Pacifico", "Caveat", "Orbitron",
        "Hind Siliguri", "Baloo Da 2",
    )

    fun googleId(name: String): String = GOOGLE_PREFIX + name

    fun displayName(id: String, customName: String = ""): String = when {
        id == CUSTOM_ID -> customName.ifBlank { "Custom font" }
        id.startsWith(GOOGLE_PREFIX) -> id.removePrefix(GOOGLE_PREFIX)
        else -> "System default"
    }

    fun customFontFile(context: Context): File =
        File(context.filesDir, "fonts/custom_font.ttf")

    fun customEmojiFontFile(context: Context): File =
        File(context.filesDir, "fonts/custom_emoji.ttf")

    private val provider by lazy {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs,
        )
    }

    private val cache = HashMap<String, FontFamily>()

    /** The family for a stored font id, or null for the system default. */
    fun family(context: Context, id: String): FontFamily? = when {
        id == CUSTOM_ID -> fileFamily(customFontFile(context))
        id.startsWith(GOOGLE_PREFIX) -> googleFamily(id.removePrefix(GOOGLE_PREFIX))
        else -> null
    }

    /** The family emojis render with, or null for the system emoji font. */
    fun emojiFamily(context: Context, choice: EmojiFontChoice): FontFamily? = when (choice) {
        EmojiFontChoice.SYSTEM -> null
        EmojiFontChoice.NOTO -> googleFamily("Noto Color Emoji")
        EmojiFontChoice.CUSTOM -> fileFamily(customEmojiFontFile(context))
    }

    private fun googleFamily(name: String): FontFamily = cache.getOrPut("google:$name") {
        val font = GoogleFont(name)
        FontFamily(
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.Normal),
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.Medium),
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.SemiBold),
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.Bold),
        )
    }

    private fun fileFamily(file: File): FontFamily? {
        if (!file.exists()) return null
        // Key on the modification time so re-importing a different file
        // under the same fixed name is picked up immediately.
        val key = "file:${file.path}:${file.lastModified()}"
        cache[key]?.let { return it }
        // Reject files Android cannot parse up front; otherwise the lazy
        // resolver would throw mid-frame instead.
        val parsed = runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull()
            ?: return null
        if (parsed == android.graphics.Typeface.DEFAULT) return null
        return FontFamily(Font(file)).also { cache[key] = it }
    }
}
