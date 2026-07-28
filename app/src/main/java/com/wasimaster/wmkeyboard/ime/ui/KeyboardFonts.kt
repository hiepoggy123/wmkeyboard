package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as DownloadableFont
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.fonts.FontStore
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
     * The default (automatic) Noto face each non-Latin script draws with, so a
     * script always gets a correct glyph even when the user's key font is a
     * Latin-only display face. Latin, Cyrillic and Greek are absent — they ride
     * the user's [keyFontId] choice (Noto Sans, the system default, covers all
     * three) — and Bengali has its own dedicated [bengaliFontId] picker, so it is
     * resolved before this map. Scripts here may additionally expose a handful of
     * pickable alternatives via [scriptFontChoices]; when the user has not chosen
     * one this face is used. A name the Google Fonts provider does not recognise
     * simply falls back to the system face, which carries the glyph anyway.
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
        // Notations rather than languages, but the same problem: device fonts
        // rarely carry the SMP Musical Symbols block, and braille dot-cells
        // deserve a face that has all 256 patterns. Missing glyphs still fall
        // back to the system font per-glyph, so a GMS-less device degrades to
        // whatever coverage it has rather than blank keys.
        ScriptId.MUSIC to "Noto Music",
        ScriptId.BRAILLE to "Noto Sans Symbols 2",
    )

    /**
     * A curated set of pickable font alternatives for one non-Latin script, shown
     * in settings only while a language using that script is enabled. [fonts] are
     * Google Fonts *alternatives* to the script's automatic [scriptGoogleFonts]
     * face — the picker lists that automatic face first, so these never repeat it.
     * All carry the script's glyphs; anything missing falls back to the system
     * font per-glyph. Scripts absent from this list (e.g. Thaana, which has no
     * second good face) still render with their automatic Noto face — they simply
     * get no picker. Bengali is absent too: it keeps its own [bengaliGoogleFonts]
     * picker, which also allows importing a custom file.
     */
    data class ScriptFontChoices(
        val script: ScriptId,
        val label: String,
        val sample: String,
        val fonts: List<String>,
    )

    val scriptFontChoices: List<ScriptFontChoices> = listOf(
        ScriptFontChoices(
            ScriptId.ARABIC, "Arabic", "السلام عليكم · ابجد",
            listOf("Noto Sans Arabic", "Noto Kufi Arabic", "Amiri", "Cairo", "Tajawal", "Markazi Text", "Reem Kufi"),
        ),
        ScriptFontChoices(
            ScriptId.HEBREW, "Hebrew", "שלום · אבגד הוז",
            listOf("Noto Serif Hebrew", "Rubik", "Heebo", "Assistant", "Frank Ruhl Libre", "David Libre", "Secular One"),
        ),
        ScriptFontChoices(
            ScriptId.ARMENIAN, "Armenian", "Բարեւ · Աբգդ",
            listOf("Noto Serif Armenian"),
        ),
        ScriptFontChoices(
            ScriptId.GEORGIAN, "Georgian", "გამარჯობა · აბგდ",
            listOf("Noto Serif Georgian"),
        ),
        ScriptFontChoices(
            ScriptId.DEVANAGARI, "Devanagari", "नमस्ते · कखगघ",
            listOf("Noto Serif Devanagari", "Hind", "Mukta", "Baloo 2", "Tiro Devanagari Hindi", "Rozha One", "Kalam"),
        ),
        ScriptFontChoices(
            ScriptId.GURMUKHI, "Gurmukhi", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ · ਕਖਗ",
            listOf("Noto Serif Gurmukhi", "Mukta Mahee", "Baloo Paaji 2"),
        ),
        ScriptFontChoices(
            ScriptId.GUJARATI, "Gujarati", "નમસ્તે · કખગઘ",
            listOf("Noto Serif Gujarati", "Mukta Vaani", "Hind Vadodara", "Baloo Bhai 2", "Shrikhand"),
        ),
        ScriptFontChoices(
            ScriptId.ORIYA, "Odia", "ନମସ୍କାର · କଖଗ",
            listOf("Noto Serif Oriya", "Baloo Bhaina 2"),
        ),
        ScriptFontChoices(
            ScriptId.TAMIL, "Tamil", "வணக்கம் · கஙச",
            listOf("Noto Serif Tamil", "Hind Madurai", "Mukta Malar", "Baloo Thambi 2", "Catamaran", "Pavanam"),
        ),
        ScriptFontChoices(
            ScriptId.TELUGU, "Telugu", "నమస్కారం · కఖగ",
            listOf("Noto Serif Telugu", "Hind Guntur", "Mallanna", "Mandali", "Ramabhadra", "Baloo Tammudu 2", "Suranna"),
        ),
        ScriptFontChoices(
            ScriptId.KANNADA, "Kannada", "ನಮಸ್ಕಾರ · ಕಖಗ",
            listOf("Noto Serif Kannada", "Baloo Tamma 2", "Benne", "Akaya Kanadaka"),
        ),
        ScriptFontChoices(
            ScriptId.MALAYALAM, "Malayalam", "നമസ്കാരം · കഖഗ",
            listOf("Noto Serif Malayalam", "Manjari", "Baloo Chettan 2", "Gayathri", "Anek Malayalam", "Chilanka"),
        ),
        ScriptFontChoices(
            ScriptId.SINHALA, "Sinhala", "ආයුබෝවන් · කඛග",
            listOf("Noto Serif Sinhala", "Abhaya Libre", "Yaldevi", "Gemunu Libre"),
        ),
        ScriptFontChoices(
            ScriptId.THAI, "Thai", "สวัสดี · กขคง",
            listOf("Noto Serif Thai", "Sarabun", "Kanit", "Prompt", "Mitr", "Pridi", "Bai Jamjuree", "K2D", "Mali"),
        ),
        ScriptFontChoices(
            ScriptId.LAO, "Lao", "ສະບາຍດີ · ກຂຄ",
            listOf("Noto Serif Lao", "Noto Sans Lao Looped"),
        ),
        ScriptFontChoices(
            ScriptId.KHMER, "Khmer", "សួស្តី · កខគ",
            listOf("Noto Serif Khmer", "Battambang", "Hanuman", "Suwannaphum", "Koulen", "Moul", "Content", "Kantumruy Pro"),
        ),
        ScriptFontChoices(
            ScriptId.MYANMAR, "Myanmar", "မင်္ဂလာပါ · ကခဂ",
            listOf("Noto Serif Myanmar", "Padauk"),
        ),
        ScriptFontChoices(
            ScriptId.ETHIOPIC, "Ethiopic", "ሰላም · ሀለሐመ",
            listOf("Noto Serif Ethiopic", "Abyssinica SIL"),
        ),
        ScriptFontChoices(
            ScriptId.HANGUL, "Korean", "안녕하세요 · 가나다라",
            listOf(
                "Noto Serif KR",
                "Nanum Gothic",
                "Nanum Myeongjo",
                "Gowun Dodum",
                "Do Hyeon",
                "Jua",
                "Gothic A1",
                "Black Han Sans",
                "Sunflower",
            ),
        ),
        ScriptFontChoices(
            ScriptId.JAPANESE, "Japanese", "こんにちは · 日本語 あいう",
            listOf(
                "Noto Serif JP",
                "M PLUS 1p",
                "M PLUS Rounded 1c",
                "Sawarabi Mincho",
                "Sawarabi Gothic",
                "Kosugi Maru",
                "Zen Maru Gothic",
                "Shippori Mincho",
                "Dela Gothic One",
            ),
        ),
        ScriptFontChoices(
            ScriptId.HAN, "Chinese", "你好 · 汉字 中文",
            listOf(
                "Noto Serif SC",
                "Noto Sans TC",
                "Noto Serif TC",
                "ZCOOL XiaoWei",
                "ZCOOL QingKe HuangYou",
                "ZCOOL KuaiLe",
                "Ma Shan Zheng",
                "Long Cang",
                "Zhi Mang Xing",
            ),
        ),
    )

    private val scriptFontChoicesById: Map<ScriptId, ScriptFontChoices> =
        scriptFontChoices.associateBy { it.script }

    fun scriptFontChoices(scriptId: ScriptId): ScriptFontChoices? = scriptFontChoicesById[scriptId]

    /**
     * The [FontFamily] a [scriptId] wants, honouring the user's per-script pick
     * ([selectedId], a `google:<Name>` id or [DEFAULT_ID]) and otherwise the
     * script's automatic Noto face. Null for the scripts that follow the user's
     * own font choice (Latin/Cyrillic/Greek) or have their own picker (Bengali).
     * Used by the keyboard theme to pick a face per active script.
     */
    fun scriptFamily(scriptId: ScriptId, selectedId: String = DEFAULT_ID): FontFamily? {
        val name = selectedId.takeIf { it.startsWith(GOOGLE_PREFIX) }?.removePrefix(GOOGLE_PREFIX)
            ?: scriptGoogleFonts[scriptId]
        return name?.let { googleFamily(it) }
    }

    fun googleId(name: String): String = GOOGLE_PREFIX + name

    fun displayName(id: String, customName: String = ""): String = when {
        id == CUSTOM_ID || id == CUSTOM_BENGALI_ID -> customName.ifBlank { "Custom font" }
        id.startsWith(GOOGLE_PREFIX) -> id.removePrefix(GOOGLE_PREFIX)
        // Without a Context the store can't be asked for the real name; the
        // context-aware overload below is what the settings screens use.
        FontStore.storeIdOf(id) != null -> "Installed font"
        else -> "System default"
    }

    /**
     * [displayName] with the installed-font library available, so a font from
     * the library reads as its own name rather than a generic label.
     */
    fun displayName(context: Context, id: String, customName: String = ""): String {
        val storeId = FontStore.storeIdOf(id) ?: return displayName(id, customName)
        return FontStore.get(context).font(storeId)?.name ?: "Installed font"
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
        // A font installed into the library, from a repository or the user's
        // own file. Null when it has since been deleted, which falls back to
        // the system face rather than leaving keys blank.
        else -> installedFontFile(context, id)?.let { fileFamily(it) }
    }

    /** The file behind an `installed:<id>` font id, if it is still there. */
    private fun installedFontFile(context: Context, id: String): File? {
        val storeId = FontStore.storeIdOf(id) ?: return null
        return FontStore.get(context).existingFileFor(storeId)
    }

    /**
     * The family emojis render with, or null for the system emoji font.
     *
     * [installedId] is the [FontStore] id behind [EmojiFontChoice.INSTALLED] —
     * an emoji face from an addon repository or the font library. A blank id, or
     * one whose file has since gone, falls back to the system font rather than
     * leaving the panel empty.
     */
    fun emojiFamily(
        context: Context,
        choice: EmojiFontChoice,
        installedId: String = "",
    ): FontFamily? = when (choice) {
        EmojiFontChoice.SYSTEM -> null
        EmojiFontChoice.NOTO -> googleFamily("Noto Color Emoji")
        EmojiFontChoice.CUSTOM -> fileFamily(customEmojiFontFile(context))
        EmojiFontChoice.INSTALLED -> installedEmojiFile(context, installedId)?.let { fileFamily(it) }
    }

    /** The file behind an installed emoji font id, if it is still there. */
    private fun installedEmojiFile(context: Context, installedId: String): File? =
        installedId.takeIf { it.isNotBlank() }
            ?.let { FontStore.get(context).existingFileFor(it) }

    /**
     * The font file emojis are drawn from, whose tables say which emoji it can
     * actually draw and how they have to be spelled — see
     * [com.wasimaster.wmkeyboard.core.emoji.EmojiFontCoverage].
     *
     * Only a chosen file resolves here. [EmojiFontChoice.SYSTEM] and
     * [EmojiFontChoice.NOTO] return null, meaning "the system emoji font": Noto
     * is fetched asynchronously and has no file to read, and it exists
     * precisely to fill the system font's gaps.
     */
    fun emojiFontFile(
        context: Context,
        choice: EmojiFontChoice,
        installedId: String = "",
    ): File? = when (choice) {
        EmojiFontChoice.SYSTEM, EmojiFontChoice.NOTO -> null
        EmojiFontChoice.CUSTOM -> customEmojiFontFile(context)
        EmojiFontChoice.INSTALLED -> installedEmojiFile(context, installedId)
    }?.takeIf { it.exists() }

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
