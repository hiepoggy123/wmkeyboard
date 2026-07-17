package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as DownloadableFont
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.script.ScriptId
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
    const val CUSTOM_BENGALI_ID = "custom_bn"
    private const val GOOGLE_PREFIX = "google:"

    /**
     * Curated Google Fonts choices for the English font: workhorse sans
     * faces, a few serifs, monos and display faces.
     */
    val googleFonts: List<String> = listOf(
        "Roboto", "Inter", "Open Sans", "Lato", "Montserrat", "Poppins",
        "Nunito", "Rubik", "Ubuntu", "Source Sans 3",
        "Merriweather", "Lora", "Playfair Display", "Roboto Slab",
        "Roboto Mono", "JetBrains Mono",
        "Comfortaa", "Pacifico", "Caveat", "Orbitron",
    )

    /**
     * Bengali-script Google Fonts families, used while a Bengali input
     * mode is active. All of these also carry Latin glyphs, so mixed
     * strings (suggestion strip in Avro mode) stay in one face.
     */
    val bengaliGoogleFonts: List<String> = listOf(
        "Hind Siliguri", "Noto Sans Bengali", "Noto Serif Bengali",
        "Anek Bangla", "Baloo Da 2", "Tiro Bangla", "Atma", "Mina", "Galada",
    )

    /**
     * The Noto face each non-Latin script draws with, so a script always gets a
     * correct glyph even when the user's key font is a Latin-only display face.
     * Latin, Cyrillic and Greek are absent — they ride the user's [keyFontId]
     * choice (Noto Sans, the system default, covers all three) — and Bengali has
     * its own dedicated [bengaliFontId] picker, so it is resolved before this map.
     * A name the Google Fonts provider does not recognise simply falls back to
     * the system face, which carries the glyph anyway.
     */
    private val scriptGoogleFonts: Map<ScriptId, String> = mapOf(
        ScriptId.ARMENIAN to "Noto Sans Armenian",
        ScriptId.GEORGIAN to "Noto Sans Georgian",
        ScriptId.ARABIC to "Noto Naskh Arabic",
        ScriptId.HEBREW to "Noto Sans Hebrew",
        ScriptId.THAANA to "Noto Sans Thaana",
        ScriptId.DEVANAGARI to "Noto Sans Devanagari",
        ScriptId.GURMUKHI to "Noto Sans Gurmukhi",
        ScriptId.GUJARATI to "Noto Sans Gujarati",
        ScriptId.ORIYA to "Noto Sans Oriya",
        ScriptId.TAMIL to "Noto Sans Tamil",
        ScriptId.TELUGU to "Noto Sans Telugu",
        ScriptId.KANNADA to "Noto Sans Kannada",
        ScriptId.MALAYALAM to "Noto Sans Malayalam",
        ScriptId.SINHALA to "Noto Sans Sinhala",
        ScriptId.THAI to "Noto Sans Thai",
        ScriptId.LAO to "Noto Sans Lao",
        ScriptId.KHMER to "Noto Sans Khmer",
        ScriptId.MYANMAR to "Noto Sans Myanmar",
        ScriptId.ETHIOPIC to "Noto Sans Ethiopic",
        ScriptId.HANGUL to "Noto Sans KR",
        ScriptId.JAPANESE to "Noto Sans JP",
        ScriptId.HAN to "Noto Sans SC",
    )

    /**
     * The [FontFamily] a [scriptId] wants, or null for the scripts that follow the
     * user's own font choice (Latin/Cyrillic/Greek) or have their own picker
     * (Bengali). Used by the keyboard theme to pick a face per active script.
     */
    fun scriptFamily(scriptId: ScriptId): FontFamily? =
        scriptGoogleFonts[scriptId]?.let { googleFamily(it) }

    fun googleId(name: String): String = GOOGLE_PREFIX + name

    fun displayName(id: String, customName: String = ""): String = when {
        id == CUSTOM_ID || id == CUSTOM_BENGALI_ID -> customName.ifBlank { "Custom font" }
        id.startsWith(GOOGLE_PREFIX) -> id.removePrefix(GOOGLE_PREFIX)
        else -> "System default"
    }

    fun customFontFile(context: Context): File =
        File(context.filesDir, "fonts/custom_font.ttf")

    fun customBengaliFontFile(context: Context): File =
        File(context.filesDir, "fonts/custom_font_bn.ttf")

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
        id == CUSTOM_BENGALI_ID -> fileFamily(customBengaliFontFile(context))
        id.startsWith(GOOGLE_PREFIX) -> googleFamily(id.removePrefix(GOOGLE_PREFIX))
        else -> null
    }

    /** The family emojis render with, or null for the system emoji font. */
    fun emojiFamily(context: Context, choice: EmojiFontChoice): FontFamily? = when (choice) {
        EmojiFontChoice.SYSTEM -> null
        EmojiFontChoice.NOTO -> googleFamily("Noto Color Emoji")
        EmojiFontChoice.CUSTOM -> fileFamily(customEmojiFontFile(context))
    }

    /**
     * The [android.graphics.Typeface] to test emoji glyph coverage against for
     * the "hide unrenderable emoji" feature. Only a [EmojiFontChoice.CUSTOM]
     * font resolves to a concrete typeface here; [EmojiFontChoice.SYSTEM] and
     * [EmojiFontChoice.NOTO] return null so the caller tests against the system
     * emoji font — Noto is fetched asynchronously and can't be loaded as a
     * blocking typeface, and it exists precisely to fill the system font's gaps.
     */
    fun emojiTypeface(context: Context, choice: EmojiFontChoice): android.graphics.Typeface? =
        when (choice) {
            EmojiFontChoice.SYSTEM, EmojiFontChoice.NOTO -> null
            EmojiFontChoice.CUSTOM -> customEmojiFontFile(context)
                .takeIf { it.exists() }
                ?.let { runCatching { android.graphics.Typeface.createFromFile(it) }.getOrNull() }
                ?.takeIf { it != android.graphics.Typeface.DEFAULT }
        }

    /** Family for any Google Fonts name (also used directly by tool panels). */
    fun googleFamily(name: String): FontFamily = cache.getOrPut("google:$name") {
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
